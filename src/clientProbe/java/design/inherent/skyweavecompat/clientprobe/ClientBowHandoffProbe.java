package design.inherent.skyweavecompat.clientprobe;

import com.google.gson.*;
import design.inherent.skyweavecompat.client.model.ItemDefinitions;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.*;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Original/repaired actual bow playback and effective model traces, never shipped. */
public final class ClientBowHandoffProbe {
    private static ClientBowHandoffProbe active;
    private final JsonArray draws = new JsonArray();
    private long lastDrawFrame = -1;
    private final Minecraft mc = Minecraft.getInstance();
    private final Path root = FMLPaths.GAMEDIR.get();
    private final JsonObject report = new JsonObject();
    private final JsonArray samples = new JsonArray();
    private CompletableFuture<?> pending;
    private boolean opened, prepared, started, stopped;
    private int ticks, before, after;
    private net.minecraft.world.entity.HumanoidArm mainArm = net.minecraft.world.entity.HumanoidArm.RIGHT;
    private InteractionHand hand = InteractionHand.MAIN_HAND;
    private boolean flame;
    private int releaseTick = 65, reloadTick = -1;
    private boolean noAnimation, reloadRequested;
    private long frame;
    private boolean beforeFrame, afterFrame, finishing;
    private String capture;
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    ClientBowHandoffProbe() {
        check(Files.isRegularFile(root.resolve("c1-bow-fixture.json")), "Missing owned bow marker");
        try {
            var settings = JsonParser.parseString(Files.readString(root.resolve("c1-bow-fixture.json"))).getAsJsonObject();
            if (settings.has("left")) mainArm = settings.get("left").getAsBoolean() ? net.minecraft.world.entity.HumanoidArm.LEFT : net.minecraft.world.entity.HumanoidArm.RIGHT;
            if (settings.has("offhand") && settings.get("offhand").getAsBoolean()) hand = InteractionHand.OFF_HAND;
            flame = settings.has("flame") && settings.get("flame").getAsBoolean();
            if (settings.has("releaseTick")) releaseTick = settings.get("releaseTick").getAsInt();
            noAnimation = !net.neoforged.fml.ModList.get().isLoaded("punchy")
                    || settings.has("noAnimation") && settings.get("noAnimation").getAsBoolean();
            if (settings.has("reloadTick")) reloadTick = settings.get("reloadTick").getAsInt();
            report.add("case", settings);
        } catch (Exception failure) { throw new IllegalStateException(failure); }
        active=this;report.add("actualDraws",draws);
        report.add("samples", samples);
        NeoForge.EVENT_BUS.addListener(this::tick);
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.RenderFrameEvent.Post event) -> {
            ++frame;
            if (capture != null && !stopped) {
                String name = capture; capture = null;
                net.minecraft.client.Screenshot.grab(root.toFile(), name, mc.getMainRenderTarget(), message -> {});
            }
        });
    }
    private void tick(ClientTickEvent.Post event) {
        if (stopped || mc.getOverlay() != null) return;
        try {
            if (!opened && mc.screen instanceof TitleScreen) {
                opened = true; mc.options.pauseOnLostFocus = false; mc.options.renderDistance().set(3); mc.options.simulationDistance().set(3);
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                check(!Files.exists(root.resolve("saves/c1-bow")), "Require a fresh owned world");
                mc.createWorldOpenFlows().createFreshLevel("c1-bow", new LevelSettings("Bow handoff", GameType.SURVIVAL, false, Difficulty.PEACEFUL,
                        true, new GameRules(), WorldDataConfiguration.DEFAULT), new WorldOptions(42,false,false),
                        registries -> registries.registryOrThrow(Registries.WORLD_PRESET).get(ResourceLocation.withDefaultNamespace("flat")).createWorldDimensions(), mc.screen);
                return;
            }
            if (mc.player == null || mc.level == null || mc.getSingleplayerServer() == null) return;
            if (pending != null) { if (!pending.isDone()) return; pending.join(); pending = null; }
            mc.setScreen(null);
            if (!prepared) {
                prepared = true;
                pending = mc.getSingleplayerServer().submit(() -> {
                    var player = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                    player.teleportTo(.5,-60,.5); player.setGameMode(GameType.SURVIVAL);
                    player.getInventory().clearContent(); player.getInventory().selected = 0;
                    player.setMainArm(mainArm);
                    var bow = new ItemStack(Items.BOW);
                    if (flame) bow.enchant(player.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                            .getOrThrow(net.minecraft.world.item.enchantment.Enchantments.FLAME),1);
                    player.setItemSlot(hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND, bow);
                    player.getInventory().setItem(9,new ItemStack(Items.ARROW,64)); player.inventoryMenu.broadcastChanges();
                });
                return;
            }
            check(design.inherent.skyweavecompat.client.model.BowModelContext.current() == null
                    && design.inherent.skyweavecompat.client.model.BowModelContext.origin() == null, "Bow model/origin scope leaked past draw");
            ++ticks;
            if (ticks == 20) {
                check(mc.player.getItemInHand(hand).is(Items.BOW), "Native inventory synchronization did not deliver bow");
                mc.options.keyUse.setDown(true); mc.player.setMainArm(mainArm); mc.gameMode.useItem(mc.player, hand); started = true;
            }
            if (started && mc.player.isUsingItem() && mc.player.getUseItem().is(Items.BOW)) {
                var stack = mc.player.getUseItem(); var clip = clip();
                if (noAnimation) check(!clip.bow(), "Expected native no-Punchy/excluded-item path");
                if (clip.bow() || noAnimation) {
                    boolean handoff = clip.handoff();
                    int used = stack.getUseDuration(mc.player) - mc.player.getUseItemRemainingTicks();
                    var arm = hand == InteractionHand.MAIN_HAND ? mainArm : mainArm.getOpposite();
                    var firstDisplay = arm == net.minecraft.world.entity.HumanoidArm.RIGHT ? ItemDisplayContext.FIRST_PERSON_RIGHT_HAND : ItemDisplayContext.FIRST_PERSON_LEFT_HAND;
                    var thirdDisplay = arm == net.minecraft.world.entity.HumanoidArm.RIGHT ? ItemDisplayContext.THIRD_PERSON_RIGHT_HAND : ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
                    var fp = models(stack,firstDisplay);
                    var third = models(stack,thirdDisplay);
                    var row = new JsonObject(); row.addProperty("tick",ticks); row.addProperty("frame",frame);
                    row.addProperty("usedTicks",used); row.addProperty("clip",clip.name()); row.addProperty("clipTime",clip.time());
                    row.addProperty("handoff",handoff); row.add("gui",new Gson().toJsonTree(models(stack,ItemDisplayContext.GUI))); row.add("ground",new Gson().toJsonTree(models(stack,ItemDisplayContext.GROUND))); row.add("firstPerson",new Gson().toJsonTree(fp)); row.add("thirdPerson",new Gson().toJsonTree(third)); samples.add(row);
                    check(!fp.isEmpty() && !third.isEmpty(), "Effective bow resource program not loaded");
                    check(fp.equals(third), "Unrelated first-person model query changed outside the actual draw");
                    if (used >= 3 && !handoff) {
                        before++;
                        if (!beforeFrame) { beforeFrame = true; capture = "bow-before-handoff.png"; }
                    }
                    if (handoff) {
                        after++;
                        if (!afterFrame) { afterFrame = true; capture = "bow-after-handoff.png"; }
                    }
                }
            }
            if (ticks == reloadTick) {reloadRequested=true;pending=mc.reloadResourcePacks();return;}
            if (ticks == releaseTick) {
                mc.options.keyUse.setDown(false); mc.gameMode.releaseUsingItem(mc.player);
            }
            if (ticks == releaseTick + 20) {
                finishing = true;
                pending = mc.getSingleplayerServer().submit(() -> {
                    var player = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                    var gameplay = new JsonObject();
                    gameplay.addProperty("arrowsRemaining", player.getInventory().countItem(Items.ARROW));
                    gameplay.addProperty("bowDamage", player.getItemInHand(hand).getDamageValue());
                    gameplay.addProperty("arrowEntities", player.serverLevel().getEntitiesOfClass(
                            net.minecraft.world.entity.projectile.AbstractArrow.class, player.getBoundingBox().inflate(200)).size());
                    report.add("gameplay", gameplay);
                });
                return;
            }
            if (finishing) {
                check(before > 0 && (noAnimation || releaseTick < 30 || after > 0), "Native playback did not cross the expected observation window");
                if (!noAnimation && net.neoforged.fml.ModList.get().isLoaded("modefite")) {
                    check(draws.size()>0,"Actual upstream bow draw was not observed");
                    int pre=0,post=0;
                    boolean repaired=design.inherent.skyweavecompat.OptionalIntegrations.enabled(design.inherent.skyweavecompat.Feature.BOW_MODEL_HANDOFF);
                    for(var element:draws) {
                        var draw=element.getAsJsonObject();int used=draw.get("usedTicks").getAsInt();
                        boolean handoff=draw.get("handoff").getAsBoolean();float value=draw.get("value").getAsFloat();
                        if(used>=2 && !handoff) {check(value==(repaired?0:used),"Actual native draw has wrong pre-handoff value");pre++;}
                        if(handoff) {check(value==used,"Actual native draw clock was rebased");post++;}
                    }
                    check(pre>0 && (releaseTick<30 || post>0),"Actual draw missed required handoff states");
                    report.addProperty("actualPreDrawChecks",pre);report.addProperty("actualPostDrawChecks",post);
                }
                report.addProperty("resourceReloadCompleted",reloadRequested);report.addProperty("noAnimation",noAnimation); report.addProperty("beforeSamples",before); report.addProperty("afterSamples",after);
                report.addProperty("passed",true); stop();
            }
            if (ticks > 200) throw new AssertionError("Bounded bow fixture did not finish");
        } catch (Throwable failure) {
            report.addProperty("passed",false); report.addProperty("error",failure.toString()); failure.printStackTrace(); stop();
        }
    }
    public static void observeDuration(net.minecraft.resources.ResourceLocation property,ItemStack stack,float value) {
        var probe=active;
        if (probe==null || probe.stopped || !property.toString().equals("minecraft:use_duration")
                || probe.mc.player==null || probe.mc.player.getUseItem()!=stack || probe.lastDrawFrame==probe.frame) return;
        var modelContext=design.inherent.skyweavecompat.client.model.BowModelContext.current();
        if(modelContext==null || !(modelContext.display()==ItemDisplayContext.FIRST_PERSON_LEFT_HAND
                || modelContext.display()==ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
                || modelContext.display()==ItemDisplayContext.THIRD_PERSON_LEFT_HAND
                || modelContext.display()==ItemDisplayContext.THIRD_PERSON_RIGHT_HAND)) return;
        // Observe the real upstream draw stack independently of the product's source marker.
        boolean actualDraw=StackWalker.getInstance().walk(frames->frames.anyMatch(frame->
                frame.getClassName().equals("punchy.client.render.PunchyArmRenderer") && frame.getMethodName().contains("renderItemInHand")));
        if (!actualDraw) return;
        try {
            var clip=probe.clip();if (!clip.bow()) return;
            var row=new JsonObject();row.addProperty("frame",probe.frame);row.addProperty("tick",probe.ticks);
            row.addProperty("usedTicks",stack.getUseDuration(probe.mc.player)-probe.mc.player.getUseItemRemainingTicks());
            row.addProperty("value",value);row.addProperty("clipTime",clip.time());row.addProperty("handoff",clip.handoff());
            var context=design.inherent.skyweavecompat.client.model.BowModelContext.current();
            row.addProperty("display",context==null?"unavailable":context.display().name());
            row.addProperty("productOrigin",design.inherent.skyweavecompat.client.model.BowModelContext.origin()!=null);
            probe.draws.add(row);probe.lastDrawFrame=probe.frame;
        } catch(Exception failure) {throw new IllegalStateException("Bow draw observer failed",failure);}
    }
    private record Clip(String name,float time,boolean bow,boolean handoff) {}
    private Clip clip() throws Exception {
        if (!net.neoforged.fml.ModList.get().isLoaded("punchy")) return new Clip("absent",0,false,false);
        var manager = Class.forName("punchy.client.animation.PunchyAnimationManager");
        var handler = manager.getField("POSE_HANDLER").get(null);
        Object clip = handler.getClass().getMethod("getCurrentClip").invoke(handler);
        if (clip == null) return new Clip("none",0,false,false);
        var type = Class.forName("punchy.client.animation.data.AnimationClip");
        boolean bow = (boolean)handler.getClass().getMethod("isPlaying").invoke(handler)
                && (boolean)manager.getMethod("isClipNamed",type,String.class).invoke(null,clip,"use_bow");
        float time = (float)handler.getClass().getMethod("getCurrentTime").invoke(handler);
        boolean handoff = false;
        for (String name : new String[]{"start_mesh_animation","loadstart","load_start"})
            handoff |= (boolean)type.getMethod("isEffectActive",String.class,float.class).invoke(clip,name,time);
        return new Clip((String)type.getMethod("getName").invoke(clip),time,bow,handoff);
    }
    private List<String> models(ItemStack stack,ItemDisplayContext display) {
        if (net.neoforged.fml.ModList.get().isLoaded("modefite")
                && design.inherent.skyweavecompat.OptionalIntegrations.enabled(design.inherent.skyweavecompat.Feature.ITEM_DEFINITIONS))
            return ItemDefinitions.selectedModels(ResourceLocation.withDefaultNamespace("bow"),stack,mc.player,display);
        var model = mc.getItemRenderer().getModel(stack,mc.level,mc.player,0);
        check(model != mc.getModelManager().getMissingModel(),"Native bow fallback is missing");
        return List.of("native:"+model.getClass().getName());
    }
    private void stop() {
        stopped=true;active=null; mc.options.keyUse.setDown(false);
        try { Files.writeString(root.resolve("bow-handoff-proof.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report)); }
        catch (Exception failure) { throw new IllegalStateException(failure); }
        mc.stop();
    }
}
