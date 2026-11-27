package design.inherent.skyweavecompat.clientprobe;

import com.evandev.reliable_gliders.api.GlidingState;
import com.evandev.reliable_gliders.config.ModConfig;
import com.google.gson.*;
import java.nio.file.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Actual falling/gliding, supplied third-person operand and final native item draw. Never shipped. */
public final class ClientGliderVisualProbe {
    private static ClientGliderVisualProbe active;
    private static final ThreadLocal<Operand> CURRENT=new ThreadLocal<>();
    private static final ThreadLocal<Boolean> FIRST_PASS=new ThreadLocal<>();
    private record Operand(LivingEntity entity,ItemStack stack,ItemDisplayContext display,HumanoidArm arm) {}
    private final Minecraft mc=Minecraft.getInstance();
    private final Path root=FMLPaths.GAMEDIR.get();
    private final JsonObject report=new JsonObject();
    private final JsonArray rows=new JsonArray();
    private CompletableFuture<?> pending;
    private boolean opened,prepared,closing,done,inTick;
    private int ticks,glidingTicks,supplied,draws,preserved;
    private int firstPasses,firstAttempts,firstGliderDraws,firstHeldDraws,firstNativeItemCalls;
    private int firstGliderObjectMatches,firstGliderComponentMatches,firstGliderDisplayMatches;
    private final java.util.Map<String,Integer> firstModelItems=new java.util.TreeMap<>();
    private final boolean expectPreserved;
    private final boolean firstPerson;
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    ClientGliderVisualProbe() {
        try {
            var marker=JsonParser.parseString(Files.readString(root.resolve("glider-visual-fixture.json"))).getAsJsonObject();
            expectPreserved=marker.get("expectOperandPreserved").getAsBoolean();
            firstPerson=marker.has("firstPerson") && marker.get("firstPerson").getAsBoolean();
        }catch(Exception failure){throw new IllegalStateException("Missing or invalid owned glider visual marker",failure);}
        check(ModConfig.get().equipToChestplate,"Fixture requires original chest equipment admission");
        active=this;report.addProperty("scope",firstPerson?"Original integrated gliding, first-person native hand attempts and actual item draws; no attachment geometry, TCP or complete presentation":"Original integrated gliding and third-person native item operand, not first-person attachment, TCP, physical flight qualification or complete presentation");
        report.addProperty("observerProtocol",2);
        report.addProperty("expectedOperandPreserved",expectPreserved);report.add("draws",rows);
        NeoForge.EVENT_BUS.addListener(this::tick);
    }
    public static void layer(LivingEntity entity,ItemStack stack,ItemDisplayContext display,HumanoidArm arm,Runnable draw) {
        var self=active;
        if(self==null || self.done || entity!=self.mc.player || !GlidingState.isGliding(self.mc.player)) {draw.run();return;}
        var prior=CURRENT.get();CURRENT.set(new Operand(entity,stack,display,arm));
        if(stack.is(com.evandev.reliable_gliders.registry.ModItems.GLIDER))self.supplied++;
        try{draw.run();}finally{if(prior==null)CURRENT.remove();else CURRENT.set(prior);}
    }
    public static void draw(LivingEntity entity,ItemStack stack,ItemDisplayContext display,boolean left) {
        var self=active;var operand=CURRENT.get();
        if(self!=null && !self.done && self.firstPerson && Boolean.TRUE.equals(FIRST_PASS.get()) && entity==self.mc.player) {
            self.firstNativeItemCalls++;
            if(self.rows.size()<128){var row=new JsonObject();row.addProperty("stage","native-hand-entry");row.addProperty("drawn",id(stack));row.addProperty("display",display.name());row.addProperty("left",left);
                var equipped=GlidingState.getGliderStack(self.mc.player);row.addProperty("sameEquippedStackReference",equipped==stack);row.addProperty("sameEquippedItemAndComponents",ItemStack.matches(equipped,stack));
                row.addProperty("nativeClientGliding",GlidingState.isGliding(self.mc.player));self.rows.add(row);}
        }
        if(self==null || self.done || operand==null || entity!=operand.entity() || !operand.stack().is(com.evandev.reliable_gliders.registry.ModItems.GLIDER))return;
        self.draws++;boolean same=ItemStack.matches(operand.stack(),stack);if(same)self.preserved++;
        if(self.rows.size()<128){var row=new JsonObject();row.addProperty("supplied",id(operand.stack()));row.addProperty("drawn",id(stack));
            row.addProperty("sameNativeOperandReference",operand.stack()==stack);row.addProperty("sameItemAndComponents",same);
            row.addProperty("display",display.name());row.addProperty("arm",operand.arm().name());row.addProperty("left",left);
            row.addProperty("actualMainHand",id(entity.getMainHandItem()));row.addProperty("actualOffHand",id(entity.getOffhandItem()));self.rows.add(row);}
    }
    public static void firstPersonModel(ItemStack stack,ItemDisplayContext display,boolean left,net.minecraft.client.resources.model.BakedModel model) {
        var self=active;
        if(self==null || self.done || !self.firstPerson || !Boolean.TRUE.equals(FIRST_PASS.get()))return;
        self.firstModelItems.merge(id(stack),1,Integer::sum);
        if(stack.is(com.evandev.reliable_gliders.registry.ModItems.GLIDER)) {
            self.firstGliderDraws++;var equipped=GlidingState.getGliderStack(self.mc.player);
            if(equipped==stack)self.firstGliderObjectMatches++;
            if(ItemStack.matches(equipped,stack))self.firstGliderComponentMatches++;
            var expected=self.mc.player.getMainArm()==HumanoidArm.RIGHT?ItemDisplayContext.FIRST_PERSON_RIGHT_HAND:ItemDisplayContext.FIRST_PERSON_LEFT_HAND;
            if(display==expected)self.firstGliderDisplayMatches++;
        }
        if(stack.is(Items.APPLE) || stack.is(Items.TORCH))self.firstHeldDraws++;
        if(self.rows.size()<128){var row=new JsonObject();row.addProperty("stage","model-entry");row.addProperty("drawn",id(stack));row.addProperty("display",display.name());row.addProperty("left",left);
            var equipped=GlidingState.getGliderStack(self.mc.player);row.addProperty("sameEquippedStackReference",equipped==stack);row.addProperty("sameEquippedItemAndComponents",ItemStack.matches(equipped,stack));
            row.addProperty("modelClass",model.getClass().getName());row.addProperty("nativeClientGliding",GlidingState.isGliding(self.mc.player));self.rows.add(row);}
    }
    public static void firstPersonPass(Runnable draw) {
        var self=active;
        if(self==null || self.done || !self.firstPerson || self.mc.player==null || !self.mc.options.getCameraType().isFirstPerson() || !GlidingState.isGliding(self.mc.player)){draw.run();return;}
        var prior=FIRST_PASS.get();FIRST_PASS.set(true);self.firstPasses++;
        try{draw.run();}finally{if(prior==null)FIRST_PASS.remove();else FIRST_PASS.set(prior);}
    }
    public static void firstPersonAttempt(net.minecraft.client.player.AbstractClientPlayer player,ItemStack stack) {
        var self=active;
        if(self!=null && !self.done && Boolean.TRUE.equals(FIRST_PASS.get()) && player==self.mc.player && stack.is(com.evandev.reliable_gliders.registry.ModItems.GLIDER))self.firstAttempts++;
    }
    private static String id(ItemStack stack){return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();}
    private void tick(ClientTickEvent.Post ignored) {
        if(done || closing)return;inTick=true;
        try {
            if(++ticks>1600)throw new AssertionError("Bounded glider visual observer timed out");
            if(Files.exists(root.resolve("glider-visual-cancel.json")))throw new java.util.concurrent.CancellationException("Owned fixture cancelled");
            if(!opened && mc.getOverlay()==null && mc.screen instanceof TitleScreen) {
                check(!Files.exists(root.resolve("saves/glider-visual")),"Expected a fresh disposable world");opened=true;
                mc.options.pauseOnLostFocus=false;mc.options.renderDistance().set(3);mc.options.simulationDistance().set(3);mc.options.setCameraType(firstPerson?CameraType.FIRST_PERSON:CameraType.THIRD_PERSON_BACK);
                mc.createWorldOpenFlows().createFreshLevel("glider-visual",new LevelSettings("Glider visual",GameType.SURVIVAL,false,Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT),new WorldOptions(42,false,false),
                        registries->registries.registryOrThrow(Registries.WORLD_PRESET).get(ResourceLocation.withDefaultNamespace("flat")).createWorldDimensions(),mc.screen);return;
            }
            if(mc.player==null || mc.level==null || mc.getSingleplayerServer()==null || mc.getOverlay()!=null)return;
            if(pending!=null){if(!pending.isDone())return;pending.join();pending=null;}
            if(mc.screen!=null)mc.setScreen(null);
            if(!prepared) {
                prepared=true;var server=mc.getSingleplayerServer();var playerId=mc.player.getUUID();
                pending=server.submit(()->{var player=server.getPlayerList().getPlayer(playerId);
                    player.getInventory().clearContent();player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.APPLE,8));player.setItemInHand(InteractionHand.OFF_HAND,new ItemStack(Items.TORCH,16));
                    var glider=new ItemStack(com.evandev.reliable_gliders.registry.ModItems.GLIDER);glider.set(DataComponents.CUSTOM_NAME,Component.literal("Native operand witness"));
                    player.setItemSlot(EquipmentSlot.CHEST,glider);player.teleportTo(.5,100,.5);player.setDeltaMovement(0,-.1,0);player.setYRot(180);player.inventoryMenu.broadcastChanges();});return;
            }
            check(CURRENT.get()==null && FIRST_PASS.get()==null,"Native draw observation leaked past its scope");
            if(!GlidingState.isGliding(mc.player))return;
            if(++glidingTicks<40)return;
            if(firstPerson) {
                report.addProperty("firstPersonPasses",firstPasses);report.addProperty("firstPersonNativeGliderAttempts",firstAttempts);
                report.addProperty("firstPersonActualGliderDraws",firstGliderDraws);report.addProperty("firstPersonActualHeldDraws",firstHeldDraws);
                report.addProperty("firstPersonNativeItemCalls",firstNativeItemCalls);
                report.addProperty("firstPersonGliderObjectMatches",firstGliderObjectMatches);report.addProperty("firstPersonGliderComponentMatches",firstGliderComponentMatches);
                report.addProperty("firstPersonGliderDisplayMatches",firstGliderDisplayMatches);report.add("firstPersonModelItems",new Gson().toJsonTree(firstModelItems));
                check(firstPasses>=10 && firstAttempts>=10,"Native first-person pass did not supply enough actual glider attempts");
                check(expectPreserved?firstGliderDraws>=10:firstGliderDraws==0,"First-person glider draw differs from the selected control");
                check(firstGliderObjectMatches==firstGliderDraws && firstGliderComponentMatches==firstGliderDraws && firstGliderDisplayMatches==firstGliderDraws,
                        "Actual glider model lost its original equipment object, components, or first-person context");
                if(net.neoforged.fml.ModList.get().isLoaded("punchy"))check(firstHeldDraws>=10,"Punchy actual hand items were not observed beside the original glider attempts");
            } else {
                check(supplied>=10 && draws>=10,"Native gliding did not produce enough actual third-person glider draw operands");
                check(expectPreserved?preserved==draws:preserved==0,"Native draw operand disagrees with original/repaired control");
            }
            check(mc.player.getMainHandItem().is(Items.APPLE) && mc.player.getMainHandItem().getCount()==8 && mc.player.getOffhandItem().is(Items.TORCH) && mc.player.getOffhandItem().getCount()==16,"Render observation changed actual hand inventory");
            report.addProperty("suppliedGliderOperands",supplied);report.addProperty("nativeItemDraws",draws);report.addProperty("preservedOperands",preserved);report.addProperty("nativeClientGliding",true);
            report.addProperty("actualHandsUnchanged",true);finish(null);
        }catch(Throwable failure){finish(failure);}finally{inTick=false;}
    }
    private void finish(Throwable failure) {
        if(closing || done)return;closing=true;report.addProperty("passed",failure==null);
        if(failure!=null){report.addProperty("error",failure.toString());failure.printStackTrace();}
        // Native disconnect pumps its own event loop; always leave the observer tick first.
        mc.tell(()->{
            try{check(!inTick,"Native close ran inside the observer tick");if(mc.level!=null)mc.level.disconnect();mc.disconnect(new TitleScreen());mc.setScreen(new TitleScreen());
                report.addProperty("normalDisconnectComplete",mc.level==null && mc.getSingleplayerServer()==null);
            }catch(Throwable close){report.addProperty("passed",false);report.addProperty("closeError",close.toString());}
            finally{done=true;active=null;CURRENT.remove();FIRST_PASS.remove();try{ProbeStateFiles.write(root.resolve("glider-visual-proof.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));}catch(Exception save){throw new IllegalStateException(save);}mc.stop();}
        });
    }
}
