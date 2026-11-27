package design.inherent.skyweavecompat.clientprobe;

import com.evandev.reliable_gliders.api.GlidingState;
import com.evandev.reliable_gliders.registry.ModItems;
import com.google.gson.*;
import com.mojang.blaze3d.vertex.PoseStack;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.joml.Vector3f;
import org.joml.Matrix4f;

/** Submitted native quads and tint, both arm sides/views, real reload and equipment retirement. */
public final class ClientGliderPresentationProbe {
    private static ClientGliderPresentationProbe active;
    private static final ThreadLocal<Boolean> FIRST=new ThreadLocal<>();
    private static final ThreadLocal<Vector3f> HEAD=new ThreadLocal<>();
    private static final ThreadLocal<Matrix4f> LAYER=new ThreadLocal<>();
    private static final ThreadLocal<Map<String,Integer>> PASS_ITEMS=new ThreadLocal<>();
    private static final ThreadLocal<Draw> DRAW=new ThreadLocal<>();
    private final Minecraft mc=Minecraft.getInstance();
    private final Path root=FMLPaths.GAMEDIR.get();
    private final JsonObject report=new JsonObject();
    private final Map<String,Stats> stats=new LinkedHashMap<>();
    private CompletableFuture<?> pending;
    private boolean opened,setup,closing,done,inTick,capturing,reloaded;
    private Boolean lastRemoteGliding;
    private boolean shaderSelected;
    private volatile JsonObject serverEquipmentSnapshot;
    private int totalTicks,phase,phaseTicks;
    private static final int DYE=0x3680c2;
    private final boolean remoteFixture;
    private final boolean expectGlider,diagnoseHands;
    private final Set<String> expectedRetiredModelItems;
    private final String expectedShader;
    private final String curioSlot;
    private final java.util.List<String> expectedFilePacks;
    private boolean resourceRosterChecked;
    private final java.util.UUID observedId=java.util.UUID.fromString("12953616-4570-43a9-82af-7bea3d5d0fd6");
    private net.minecraft.server.level.ServerPlayer observed;
    private net.minecraft.world.entity.player.Player watched(){return remoteFixture && mc.level!=null?mc.level.getPlayerByUUID(observedId):mc.player;}
    private boolean equipped(){return phase<(remoteFixture?2:4);}
    private static final class Stats {
        int models,quads,vertices,tinted,sodiumEncodedQuads,suppliedGliderAttempts,handPasses;
        final Map<String,Integer> handArguments=new TreeMap<>(),globalHandModels=new TreeMap<>(),passPatterns=new TreeMap<>();
        final JsonArray outsideHandScopeExamples=new JsonArray();
        final Map<String,Integer> items=new TreeMap<>();
        final Set<String> meshes=new TreeSet<>();
        String loadedShader,shaderPipeline;
        Boolean dynamicFpsLoaded,dynamicFpsEnabled,dynamicFpsDisabled,windowActive,noRender;
        Integer actualFps,frameCap;
        Boolean shaderActive;
        final JsonArray examples=new JsonArray();
    }
    private static final class Draw {
        final Stats stats;final ItemStack stack;final ItemDisplayContext display;final Vector3f head;
        final java.security.MessageDigest digest;
        final Matrix4f layer=LAYER.get()==null?null:new Matrix4f(LAYER.get());
        final float[] localLow={Float.POSITIVE_INFINITY,Float.POSITIVE_INFINITY,Float.POSITIVE_INFINITY};
        final float[] localHigh={Float.NEGATIVE_INFINITY,Float.NEGATIVE_INFINITY,Float.NEGATIVE_INFINITY};
        int quads,vertices,tinted;
        final float[] low={Float.POSITIVE_INFINITY,Float.POSITIVE_INFINITY,Float.POSITIVE_INFINITY};
        final float[] high={Float.NEGATIVE_INFINITY,Float.NEGATIVE_INFINITY,Float.NEGATIVE_INFINITY};
        Draw(Stats stats,ItemStack stack,ItemDisplayContext display,Vector3f head) {
            this.stats=stats;this.stack=stack;this.display=display;this.head=head;
            try{digest=java.security.MessageDigest.getInstance("SHA-256");}catch(Exception failure){throw new IllegalStateException(failure);}
        }
    }
    public ClientGliderPresentationProbe() {
        check(Files.isRegularFile(root.resolve("glider-presentation-fixture.json")),"Missing owned presentation marker");
        try{var marker=JsonParser.parseString(Files.readString(root.resolve("glider-presentation-fixture.json"))).getAsJsonObject();expectedRetiredModelItems=marker.has("expectedRetiredModelItems")?Set.copyOf(java.util.stream.StreamSupport.stream(marker.getAsJsonArray("expectedRetiredModelItems").spliterator(),false).map(JsonElement::getAsString).toList()):Set.of("minecraft:apple","minecraft:torch");if(marker.has("ordinaryHandBaseline"))report.add("ordinaryHandBaseline",marker.get("ordinaryHandBaseline").deepCopy());remoteFixture=marker.has("remote");expectGlider=!marker.has("expectGlider") || marker.get("expectGlider").getAsBoolean();diagnoseHands=marker.has("diagnoseHands");expectedShader=marker.has("expectedShader")?marker.get("expectedShader").getAsString():null;curioSlot=marker.has("curioSlot")?marker.get("curioSlot").getAsString():null;expectedFilePacks=marker.has("expectedFilePacks")?java.util.stream.StreamSupport.stream(marker.getAsJsonArray("expectedFilePacks").spliterator(),false).map(JsonElement::getAsString).toList():null;}
        catch(Exception failure){throw new IllegalStateException(failure);}
        report.addProperty("trackedRemoteFixture",remoteFixture);report.addProperty("expectGliderModel",expectGlider);
        check(curioSlot!=null || com.evandev.reliable_gliders.config.ModConfig.get().equipToChestplate,"Native chest admission required");
        report.addProperty("equipmentRoute",curioSlot==null?"native chest":"native Curios slot "+curioSlot);
        report.addProperty("scope","Actual bulk quad operands and native tint; not final pixels, clipping, hydraulic/flight acceptance, remote actors or shader/fullpack proof");
        active=this;NeoForge.EVENT_BUS.addListener(this::tick);
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    private HumanoidArm arm(){if(remoteFixture)return phase==0?HumanoidArm.RIGHT:HumanoidArm.LEFT;return phase==1 || phase==3 || phase==4?HumanoidArm.LEFT:HumanoidArm.RIGHT;}
    private boolean first(){return !remoteFixture && (phase<2 || phase==4);}
    private String key(){if(remoteFixture)return phase==0?"remote-right":phase==1?"remote-left":"remote-equipment-removed";return switch(phase){case 0->"first-right";case 1->reloaded?"first-left-after-reload":"first-left-before-reload";case 2->"third-right";case 3->"third-left";default->"equipment-removed";};}
    public static void firstPass(Runnable draw) {
        var self=active;
        if(self==null || self.done || !self.capturing || !self.first()){draw.run();return;}
        var prior=FIRST.get();var priorItems=PASS_ITEMS.get();var items=new TreeMap<String,Integer>();FIRST.set(true);PASS_ITEMS.set(items);
        try{draw.run();}finally{var value=self.stats.computeIfAbsent(self.key(),ignored->new Stats());value.handPasses++;value.passPatterns.merge(items.toString(),1,Integer::sum);if(priorItems==null)PASS_ITEMS.remove();else PASS_ITEMS.set(priorItems);if(prior==null)FIRST.remove();else FIRST.set(prior);}
    }
    public static void layer(LivingEntity entity,ItemStack stack,PoseStack pose,ModelPart head,Runnable draw) {
        var self=active;
        if(self==null || self.done || !self.capturing || self.first() || entity!=self.watched()){draw.run();return;}
        self.supplied(stack,"third");
        check(head!=null,"Expected actual humanoid layer head anchor");
        var scratch=new PoseStack();scratch.last().pose().set(pose.last().pose());head.translateAndRotate(scratch);
        var anchor=scratch.last().pose().transformPosition(new Vector3f());var prior=HEAD.get();var priorLayer=LAYER.get();HEAD.set(anchor);LAYER.set(new Matrix4f(pose.last().pose()).invert());
        try{draw.run();}finally{if(prior==null)HEAD.remove();else HEAD.set(prior);if(priorLayer==null)LAYER.remove();else LAYER.set(priorLayer);}
    }
    public static void handArgument(net.minecraft.client.player.AbstractClientPlayer player,InteractionHand hand,ItemStack stack) {
        var self=active;if(self==null || self.done || !self.capturing || !self.first() || player!=self.mc.player || !Boolean.TRUE.equals(FIRST.get()))return;
        self.supplied(stack,"first:"+hand.name());
    }
    private void supplied(ItemStack stack,String route) {
        var value=stats.computeIfAbsent(key(),ignored->new Stats());value.handArguments.merge(route+":"+BuiltInRegistries.ITEM.getKey(stack.getItem()),1,Integer::sum);
        if(stack.is(ModItems.GLIDER)){
            value.suppliedGliderAttempts++;if(expectedShader!=null && value.shaderActive==null){shaderWitness(value);windowWitness(value);}
        }
    }
    public static void model(ItemStack stack,ItemDisplayContext display,Runnable draw) {
        var self=active;
        if(self!=null && !self.done && self.capturing && self.phase==4 && self.diagnoseHands && (stack.is(Items.APPLE) || stack.is(Items.TORCH)) && (display==ItemDisplayContext.FIRST_PERSON_RIGHT_HAND || display==ItemDisplayContext.FIRST_PERSON_LEFT_HAND || display==ItemDisplayContext.THIRD_PERSON_RIGHT_HAND || display==ItemDisplayContext.THIRD_PERSON_LEFT_HAND)){
            var value=self.stats.computeIfAbsent(self.key(),ignored->new Stats());value.globalHandModels.merge(BuiltInRegistries.ITEM.getKey(stack.getItem())+":"+display.name()+":firstScope="+Boolean.TRUE.equals(FIRST.get()),1,Integer::sum);
            if(!Boolean.TRUE.equals(FIRST.get()) && value.outsideHandScopeExamples.size()<3){var row=new JsonObject();row.addProperty("item",BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());row.addProperty("display",display.name());row.add("nativeCallers",new Gson().toJsonTree(StackWalker.getInstance().walk(frames->frames.limit(14).map(frame->frame.getClassName()+"."+frame.getMethodName()).toList())));value.outsideHandScopeExamples.add(row);}
        }
        if(self==null || self.done || !self.capturing || !(Boolean.TRUE.equals(FIRST.get()) || HEAD.get()!=null)){draw.run();return;}
        var value=self.stats.computeIfAbsent(self.key(),ignored->new Stats());
        value.items.merge(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),1,Integer::sum);
        if(PASS_ITEMS.get()!=null)PASS_ITEMS.get().merge(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),1,Integer::sum);
        if(!stack.is(ModItems.GLIDER)){draw.run();return;}
        check(self.equipped(),"Glider mesh survived native equipment retirement");
        check(self.expectGlider,"Unexpected glider model in explicit-OFF control");
        if(self.expectedShader!=null && value.shaderActive==null){self.shaderWitness(value);self.windowWitness(value);}
        check(stack==GlidingState.getGliderStack(self.watched()),"Native glider draw changed equipment identity");
        check(com.evandev.reliable_gliders.item.GliderItem.getDyeColor(stack)==DYE,"Native dye was lost");
        var expected=self.first()?(self.arm()==HumanoidArm.RIGHT?ItemDisplayContext.FIRST_PERSON_RIGHT_HAND:ItemDisplayContext.FIRST_PERSON_LEFT_HAND)
                :(self.arm()==HumanoidArm.RIGHT?ItemDisplayContext.THIRD_PERSON_RIGHT_HAND:ItemDisplayContext.THIRD_PERSON_LEFT_HAND);
        check(display==expected,"Native hand display context changed");
        var scope=new Draw(value,stack,display,HEAD.get()==null?null:new Vector3f(HEAD.get()));var prior=DRAW.get();DRAW.set(scope);
        try{draw.run();}finally{if(prior==null)DRAW.remove();else DRAW.set(prior);}
        check(scope.vertices>0 && scope.quads>0 && scope.tinted>0,"Glider model did not submit actual tinted native quads");
        check(scope.high[0]>scope.low[0] || scope.high[1]>scope.low[1] || scope.high[2]>scope.low[2],"Empty native glider extent");
        value.models++;value.quads+=scope.quads;value.vertices+=scope.vertices;value.tinted+=scope.tinted;
        String mesh=java.util.HexFormat.of().formatHex(scope.digest.digest());value.meshes.add(mesh);
        if(value.examples.size()<3){var row=new JsonObject();row.addProperty("display",display.name());row.addProperty("vertices",scope.vertices);row.addProperty("quads",scope.quads);row.addProperty("tinted",scope.tinted);
            row.addProperty("geometrySha256",mesh);row.add("minimum",new Gson().toJsonTree(scope.low));row.add("maximum",new Gson().toJsonTree(scope.high));
            if(scope.head!=null){row.add("layerMinimum",new Gson().toJsonTree(scope.localLow));row.add("layerMaximum",new Gson().toJsonTree(scope.localHigh));row.add("headPivot",new Gson().toJsonTree(new float[]{scope.head.x,scope.head.y,scope.head.z}));var center=new Vector3f((scope.low[0]+scope.high[0])/2,(scope.low[1]+scope.high[1])/2,(scope.low[2]+scope.high[2])/2);row.addProperty("centerDistanceFromHeadPivot",center.distance(scope.head));}
            value.examples.add(row);}
    }
    public static void quad(PoseStack.Pose pose,BakedQuad quad,float r,float g,float b,float a) {
        var value=DRAW.get();if(value==null)return;value.quads++;
        if(quad.isTinted() && quad.getTintIndex()==0){
            check(Math.round(r*255)==(DYE>>16&255) && Math.round(g*255)==(DYE>>8&255) && Math.round(b*255)==(DYE&255) && Math.round(a*255)==255,"Actual native glider tint differs from equipped dye");value.tinted++;
        }
        int[] data=quad.getVertices();check(data.length%4==0 && data.length/4>=3,"Invalid original native quad layout");int stride=data.length/4;
        for(int vertex=0;vertex<4;vertex++)for(int axis=0;axis<3;axis++){int datum=data[vertex*stride+axis];value.digest.update((byte)(datum>>>24));value.digest.update((byte)(datum>>>16));value.digest.update((byte)(datum>>>8));value.digest.update((byte)datum);}
        for(int i=0;i<4;i++){int at=i*stride;var point=new Vector3f(Float.intBitsToFloat(data[at]),Float.intBitsToFloat(data[at+1]),Float.intBitsToFloat(data[at+2]));pose.pose().transformPosition(point);
            check(Float.isFinite(point.x) && Float.isFinite(point.y) && Float.isFinite(point.z),"Nonfinite native glider submission");
            for(int axis=0;axis<3;axis++){float coordinate=point.get(axis);value.low[axis]=Math.min(value.low[axis],coordinate);value.high[axis]=Math.max(value.high[axis],coordinate);}if(value.layer!=null){value.layer.transformPosition(point);for(int axis=0;axis<3;axis++){float coordinate=point.get(axis);value.localLow[axis]=Math.min(value.localLow[axis],coordinate);value.localHigh[axis]=Math.max(value.localHigh[axis],coordinate);}}value.vertices++;}
    }
    public static void sodiumQuad(PoseStack.Pose pose,Object nativeQuad,int abgr) {
        var value=DRAW.get();if(value==null)return;
        check(nativeQuad instanceof BakedQuad,"Expected original native item baked quad at Sodium encoder");
        value.stats.sodiumEncodedQuads++;
        quad(pose,(BakedQuad)nativeQuad,(abgr&255)/255f,(abgr>>>8&255)/255f,(abgr>>>16&255)/255f,(abgr>>>24&255)/255f);
    }
    private void resourceRoster() {
        var actual=mc.getResourceManager().listPacks().map(pack->pack.packId()).filter(id->id.startsWith("file/")).toList();
        var row=new JsonObject();row.add("expectedFilePacks",new Gson().toJsonTree(expectedFilePacks));row.add("actualFilePacks",new Gson().toJsonTree(actual));
        var definition=mc.getResourceManager().getResource(ResourceLocation.parse("punchy_moves:compat/develop.json"));
        row.addProperty("fluidFoolishDefinitionPresent",definition.isPresent());
        if(definition.isPresent())try(var input=definition.get().open()){
            row.addProperty("fluidFoolishSource",definition.get().sourcePackId());String digest=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(input.readAllBytes()));row.addProperty("fluidFoolishDefinitionSha256",digest);
            check(definition.get().sourcePackId().equals("file/Fluid & Foolish 1.0.1.zip") && digest.equals("00245f2766a74111a1577dc5410ce08c981d018debd0157ae56a087e80b65744"),"Native FF definition source or contents differ from exact publicpack");
        }catch(java.io.IOException|java.security.NoSuchAlgorithmException failure){throw new IllegalStateException("Native FF definition witness unavailable",failure);}
        check(definition.isPresent(),"Native FF definition missing from selected publicpack");
        report.add(reloaded?"resourcesAfterReload":"resourcesBeforeReload",row);
        check(actual.equals(expectedFilePacks),"Actual file resource packs or order differ from owned publicseed");
    }
    private static final class CurioEquipment {
        static void put(net.minecraft.server.level.ServerPlayer player,String slot,ItemStack stack) {
            var inventory=top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(player).orElseThrow().getStacksHandler(slot).orElseThrow().getStacks();
            inventory.extractItem(0,Integer.MAX_VALUE,false);
            if(!stack.isEmpty())check(inventory.insertItem(0,stack,false).isEmpty(),"Native Curios insertion refused owned glider fixture");
        }
    }
    private void windowWitness(Stats value) {
        value.actualFps=mc.getFps();value.frameCap=mc.options.framerateLimit().get();value.windowActive=mc.isWindowActive();value.noRender=mc.noRender;
        value.dynamicFpsLoaded=net.neoforged.fml.ModList.get().isLoaded("dynamic_fps");
        if(value.dynamicFpsLoaded)try{
            var config=Class.forName("dynamic_fps.impl.config.DynamicFPSConfig");value.dynamicFpsEnabled=(Boolean)config.getMethod("enabled").invoke(config.getField("INSTANCE").get(null));
            value.dynamicFpsDisabled=(Boolean)Class.forName("dynamic_fps.impl.DynamicFPSMod").getMethod("isDisabled").invoke(null);
            check(!value.dynamicFpsEnabled,"Actual native Dynamic FPS configuration remains enabled");
        }catch(ReflectiveOperationException failure){throw new IllegalStateException("Native window-policy witness unavailable",failure);}
    }
    private void selectShader() throws ReflectiveOperationException {
        var iris=Class.forName("net.irisshaders.iris.Iris");var config=iris.getMethod("getIrisConfig").invoke(null);boolean enabled=!expectedShader.equals("off");
        if(enabled){
            check(design.inherent.skyweavecompat.client.ShaderAssembly.owned(root.resolve("shaderpacks").resolve(expectedShader)),"Expected native runner-owned shader output before selection");
            config.getClass().getMethod("setShaderPackName",String.class).invoke(config,expectedShader);
        }
        config.getClass().getMethod("setShadersEnabled",boolean.class).invoke(config,enabled);config.getClass().getMethod("save").invoke(config);iris.getMethod("reload").invoke(null);
        report.addProperty("nativeShaderSelection",expectedShader);
    }
    private void shaderWitness(Stats value) {
        try{
            check(net.neoforged.fml.ModList.get().isLoaded("iris"),"Expected actual Iris provider");
            var iris=Class.forName("net.irisshaders.iris.Iris");boolean enabled=(boolean)iris.getMethod("isPackInUseQuick").invoke(null);String name=(String)iris.getMethod("getCurrentPackName").invoke(null);
            var manager=iris.getMethod("getPipelineManager").invoke(null);var pipeline=manager.getClass().getMethod("getPipelineNullable").invoke(manager);
            check(enabled!=expectedShader.equals("off"),"Actual shader enablement differs from owned fixture selection");
            check(pipeline!=null,"Native glider draw has no actual world pipeline");
            if(enabled){check(expectedShader.equals(name),"Actual shader name differs from selected archive");check(pipeline.getClass().getName().equals("net.irisshaders.iris.pipeline.IrisRenderingPipeline"),"Glider did not enter actual Iris world pipeline");}
            value.shaderActive=enabled;value.loadedShader=name;value.shaderPipeline=pipeline.getClass().getName();
        }catch(ReflectiveOperationException failure){throw new IllegalStateException("Actual Iris witness unavailable",failure);}
    }
    private void tick(ClientTickEvent.Post ignored) {
        if(done || closing)return;inTick=true;
        try{
            if(remoteFixture && totalTicks%20==0)remoteProgress();
            if(++totalTicks>2400 || Files.exists(root.resolve("glider-visual-cancel.json")))throw new AssertionError("Bounded presentation fixture cancelled/timed out");
            if(!opened && mc.getOverlay()==null && mc.screen instanceof TitleScreen){
                check(!Files.exists(root.resolve("saves/glider-presentation")),"Expected fresh owned world");opened=true;mc.options.pauseOnLostFocus=false;mc.options.renderDistance().set(3);mc.options.simulationDistance().set(5);
                mc.createWorldOpenFlows().createFreshLevel("glider-presentation",new LevelSettings("Glider presentation",GameType.SURVIVAL,false,Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT),new WorldOptions(42,false,false),
                        registries->registries.registryOrThrow(Registries.WORLD_PRESET).get(ResourceLocation.withDefaultNamespace("flat")).createWorldDimensions(),mc.screen);return;}
            if(mc.player==null || mc.level==null || mc.getSingleplayerServer()==null || mc.getOverlay()!=null)return;
            if(pending!=null){if(!pending.isDone())return;pending.join();pending=null;}
            if(mc.screen!=null)mc.setScreen(null);
            if(expectedFilePacks!=null && !resourceRosterChecked){resourceRoster();resourceRosterChecked=true;}
            if(expectedShader!=null && !shaderSelected){selectShader();shaderSelected=true;return;}
            if(remoteFixture){tickRemote();return;}
            if(!setup){
                setup=true;capturing=false;phaseTicks=0;mc.options.setCameraType(first()?CameraType.FIRST_PERSON:CameraType.THIRD_PERSON_BACK);mc.options.mainHand().set(arm());
                var server=mc.getSingleplayerServer();var id=mc.player.getUUID();var requestedArm=arm();boolean equipped=phase<4;
                pending=server.submit(()->{var player=server.getPlayerList().getPlayer(id);player.getInventory().clearContent();player.setMainArm(requestedArm);
                    player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.APPLE,8));player.setItemInHand(InteractionHand.OFF_HAND,new ItemStack(Items.TORCH,16));
                    var stack=new ItemStack(ModItems.GLIDER);stack.set(DataComponents.DYED_COLOR,new DyedItemColor(DYE,true));stack.set(DataComponents.CUSTOM_NAME,Component.literal("Native geometry witness"));
                    if(curioSlot==null)player.setItemSlot(EquipmentSlot.CHEST,equipped?stack:ItemStack.EMPTY);else CurioEquipment.put(player,curioSlot,equipped?stack:ItemStack.EMPTY);player.teleportTo(.5,equipped?400:-60,.5);player.setDeltaMovement(0,equipped?-.1:0,0);player.setYRot(180);player.inventoryMenu.broadcastChanges();});return;}
            if(mc.player.getMainArm()!=arm() || GlidingState.isGliding(mc.player)!=(phase<4))return;
            phaseTicks++;capturing=phaseTicks>=10;
            check(FIRST.get()==null && HEAD.get()==null && LAYER.get()==null && DRAW.get()==null && PASS_ITEMS.get()==null,"Presentation observer scope leaked outside draw");
            if(phase==1 && !reloaded && phaseTicks==25){capturing=false;resourceRosterChecked=false;pending=mc.reloadResourcePacks();reloaded=true;phaseTicks=0;return;}
            if(phaseTicks<40)return;capturing=false;
            var value=stats.get(key());check(value!=null,"No actual model observation for "+key());
            if(phase<4){if(expectGlider)check(value.models>=5,"No useful native glider submissions for "+key());else check(value.models==0 && value.suppliedGliderAttempts>=5,"ExplicitOFF lacks supplied-glider/no-model control");}
            else {
                // Authored packs may route an item through a different renderer or hide its hand.
                // A supplied baseline is an exact matched OFF observation, not permission to drop a missing draw.
                check(value.models==0 && value.items.keySet().equals(expectedRetiredModelItems)
                        && value.items.values().stream().allMatch(count->count>=5),"Equipment retirement changed the matched ordinary model entries");
                check(value.handArguments.getOrDefault("first:MAIN_HAND:minecraft:apple",0)>=5
                        && value.handArguments.getOrDefault("first:OFF_HAND:minecraft:torch",0)>=5,"Equipment retirement changed supplied ordinary hand operands");
            }
            check(mc.player.getMainHandItem().is(Items.APPLE) && mc.player.getMainHandItem().getCount()==8 && mc.player.getOffhandItem().is(Items.TORCH) && mc.player.getOffhandItem().getCount()==16,"Actual hand inventory changed");
            if(++phase==5){var before=stats.get("first-left-before-reload");var after=stats.get("first-left-after-reload");check(expectGlider?before.models>=5 && after.models>=5:before.models==0 && after.models==0 && before.suppliedGliderAttempts>=5 && after.suppliedGliderAttempts>=5,"Actual resource reload lacks the expected native control on both sides");finish(null);return;}setup=false;
        }catch(Throwable failure){finish(failure);}finally{inTick=false;}
    }
    private void remoteProgress() throws java.io.IOException {
        var state=new JsonObject();state.addProperty("totalTicks",totalTicks);state.addProperty("phase",phase);state.addProperty("phaseTicks",phaseTicks);
        state.addProperty("opened",opened);state.addProperty("setup",setup);state.addProperty("pending",pending!=null);state.addProperty("pendingDone",pending!=null && pending.isDone());
        state.addProperty("screen",mc.screen==null?"none":mc.screen.getClass().getName());state.addProperty("overlay",mc.getOverlay()!=null);state.addProperty("level",mc.level!=null);state.addProperty("self",mc.player!=null);
        var actor=watched();state.addProperty("actor",actor!=null);if(actor!=null){state.addProperty("id",actor.getId());state.addProperty("mainArm",actor.getMainArm().name());state.addProperty("equipment",BuiltInRegistries.ITEM.getKey(actor.getItemBySlot(EquipmentSlot.CHEST).getItem()).toString());
            state.addProperty("ground",actor.onGround());state.addProperty("flying",actor.getAbilities().flying);state.addProperty("spectator",actor.isSpectator());state.addProperty("deltaY",actor.getDeltaMovement().y);state.addProperty("lastNativeGliding",lastRemoteGliding);state.addProperty("x",actor.getX());state.addProperty("y",actor.getY());state.addProperty("z",actor.getZ());}
        report.add("lastRemoteProgress",state);ProbeStateFiles.write(root.resolve("glider-presentation-progress.json"),new GsonBuilder().setPrettyPrinting().create().toJson(state));
    }
    private void tickRemote() {
        if(!setup){
            setup=true;capturing=false;phaseTicks=0;mc.options.setCameraType(CameraType.FIRST_PERSON);
            var server=mc.getSingleplayerServer();var id=mc.player.getUUID();var requestedArm=arm();boolean equipped=equipped();
            pending=server.submit(()->{
                var self=server.getPlayerList().getPlayer(id);self.setGameMode(GameType.CREATIVE);
                self.getAbilities().flying=true;self.onUpdateAbilities();self.teleportTo(.5,-56,.5);self.setDeltaMovement(0,0,0);
                if(observed==null){
                    observed=net.neoforged.neoforge.common.util.FakePlayerFactory.get(self.serverLevel(),new com.mojang.authlib.GameProfile(observedId,"GliderObserved"));
                    observed.setGameMode(GameType.SURVIVAL);observed.moveTo(.5,-56,5.5,180,0);
                    server.getPlayerList().broadcastAll(net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(java.util.List.of(observed)));
                    self.serverLevel().addNewPlayer(observed);
                }
                observed.setMainArm(requestedArm);observed.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.APPLE,8));observed.setItemInHand(InteractionHand.OFF_HAND,new ItemStack(Items.TORCH,16));
                var stack=new ItemStack(ModItems.GLIDER);stack.set(DataComponents.DYED_COLOR,new DyedItemColor(DYE,true));stack.set(DataComponents.CUSTOM_NAME,Component.literal("Native remote geometry witness"));
                observed.setItemSlot(EquipmentSlot.CHEST,equipped?stack:ItemStack.EMPTY);observed.setOnGround(false);observed.setDeltaMovement(0,-.1,0);
                var equipment=java.util.List.of(EquipmentSlot.MAINHAND,EquipmentSlot.OFFHAND,EquipmentSlot.CHEST).stream()
                        .map(slot->com.mojang.datafixers.util.Pair.of(slot,observed.getItemBySlot(slot).copy())).toList();
                // FakePlayer does not supply an authenticated connection/tracking loop. Exercise
                // the native client packet consumer using copies of the actual server equipment.
                self.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket(observed.getId(),equipment));
                var nativeData=observed.getEntityData().getNonDefaultValues();
                if(nativeData!=null)self.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket(observed.getId(),nativeData));
                var supplied=new JsonObject();supplied.addProperty("actor",observed.getId());supplied.addProperty("chest",BuiltInRegistries.ITEM.getKey(observed.getItemBySlot(EquipmentSlot.CHEST).getItem()).toString());supplied.addProperty("mainArm",observed.getMainArm().name());
                supplied.addProperty("phase",phase);serverEquipmentSnapshot=supplied;
                self.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(observed));observed.inventoryMenu.broadcastChanges();
            });return;
        }
        var snapshot=serverEquipmentSnapshot;if(snapshot!=null)report.add("serverEquipmentPhase"+snapshot.get("phase").getAsInt(),snapshot.deepCopy());
        var actor=watched();if(actor==null)return;lastRemoteGliding=GlidingState.isGliding(actor);if(actor.getMainArm()!=arm() || lastRemoteGliding!=equipped())return;
        check(actor!=mc.player,"Remote actor aliased local player");phaseTicks++;capturing=phaseTicks>=10;
        check(FIRST.get()==null && HEAD.get()==null && LAYER.get()==null && DRAW.get()==null && PASS_ITEMS.get()==null,"Remote observer scope leaked outside draw");
        if(phaseTicks<40)return;capturing=false;var value=stats.get(key());check(value!=null,"No actual tracked actor draw for "+key());
        if(equipped())check(value.models>=5,"No useful remote glider submissions");
        else check(value.models==0 && value.items.getOrDefault("minecraft:apple",0)>=5 && value.items.getOrDefault("minecraft:torch",0)>=5,"Remote removal did not restore native actual hands");
        check(actor.getMainHandItem().is(Items.APPLE) && actor.getMainHandItem().getCount()==8 && actor.getOffhandItem().is(Items.TORCH) && actor.getOffhandItem().getCount()==16,"Remote hand inventory changed");
        if(++phase==3){report.addProperty("remoteNativeEquipmentAndMotionObserved",true);finish(null);return;}setup=false;
    }
    private void finish(Throwable failure) {
        if(done || closing)return;closing=true;capturing=false;report.addProperty("passed",failure==null);if(failure!=null){report.addProperty("error",failure.toString());failure.printStackTrace();}
        report.add("phases",new Gson().toJsonTree(stats));report.addProperty("actualReloadCompleted",reloaded && (pending==null || pending.isDone()));
        mc.tell(()->{try{check(!inTick,"Native close ran inside observer tick");if(mc.level!=null)mc.level.disconnect();mc.disconnect(new TitleScreen());mc.setScreen(new TitleScreen());report.addProperty("normalDisconnectComplete",mc.level==null && mc.getSingleplayerServer()==null);}
            catch(Throwable close){report.addProperty("passed",false);report.addProperty("closeError",close.toString());}
            finally{done=true;active=null;FIRST.remove();HEAD.remove();LAYER.remove();DRAW.remove();PASS_ITEMS.remove();try{ProbeStateFiles.write(root.resolve("glider-visual-proof.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));}catch(Exception save){throw new IllegalStateException(save);}mc.stop();}});
    }
}
