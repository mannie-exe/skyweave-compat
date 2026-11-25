/* SPDX-License-Identifier: Apache-2.0 */
package design.inherent.skyweavecompat.clientprobe;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Observes native light storage separately from injected public getters and Sodium samples. */
final class ClientLightCacheProbe {
    private static final BlockPos CORNER = new BlockPos(-26, -60, -61), SOURCE = new BlockPos(-25, -58, -53);
    private static final String[] CASES = {"empty", "held", "removed-held", "virtual", "removed-virtual"};
    private static final boolean OBSERVE_PAUSE=Boolean.getBoolean("skyweave_compat.probe.lightCache.observePause");
    private static final boolean REOPEN=Boolean.getBoolean("skyweave_compat.probe.lightCache.reopen");
    private static final boolean CAPTURE=Boolean.getBoolean("skyweave_compat.probe.lightCache.capture");
    private final JsonObject report = new JsonObject();
    private final JsonArray samples = new JsonArray();
    private final JsonArray screens = new JsonArray();
    private String lastScreen;
    private final Object virtualOwner = new Object();
    private CompletableFuture<JsonObject> pending;
    private volatile Runnable pendingServerAction;
    private volatile net.minecraft.server.MinecraftServer expectedServer;
    private long lastWorldTick=-1;
    private Object retainedInitialLight;
    private boolean opened, prepared, done, closing, reconnected;
    private int ticks, phase, settled;

    ClientLightCacheProbe() {
        if (!Files.isRegularFile(FMLPaths.GAMEDIR.get().resolve("light-cache-fixture.json")))
            throw new IllegalStateException("Missing owned light-cache fixture marker");
        report.add("samples", samples);
        report.add("screens", screens);
        report.addProperty("scope", "Actual isolated native client storage/getter/cache/AO diagnostics; not actual rendered mesh, a repaired pipeline, moving-frame, fullpack or performance acceptance");
        report.addProperty("sectionDataRead", "storedBlock is native getDataLayerData (queued first); visibleBlock/updatingBlock/queuedBlock read the separate native maps directly; -1 means absent");
        report.addProperty("observePause",OBSERVE_PAUSE);
        report.addProperty("reopen",REOPEN);
        NeoForge.EVENT_BUS.addListener(this::tick);
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.tick.ServerTickEvent.Pre event)->{
            if(event.getServer()!=expectedServer)return;var action=pendingServerAction;
            if(action!=null){pendingServerAction=null;action.run();}
        });
    }

    private void tick(ClientTickEvent.Post event) {
        if (done) return;
        var mc = Minecraft.getInstance();
        try {
            String screen=mc.screen==null?"none":mc.screen.getClass().getName()+":"+mc.screen.getTitle().getString();
            if(screens.size()<32 && !screen.equals(lastScreen)) {
                lastScreen=screen;var state=new JsonObject();state.addProperty("screen",screen);
                state.addProperty("ticks",ticks);state.addProperty("worldOpen",mc.level!=null);
                state.addProperty("playerPresent",mc.player!=null);state.addProperty("reconnected",reconnected);screens.add(state);
                Files.writeString(FMLPaths.GAMEDIR.get().resolve("light-cache-screen-state.json"),screens.toString());
            }
            if (Files.exists(FMLPaths.GAMEDIR.get().resolve("light-cache-cancel.json")))
                throw new java.util.concurrent.CancellationException("Owned light-cache fixture cancelled");
            if (++ticks > 2400) throw new AssertionError("Light-cache fixture timeout");
            if(opened && (REOPEN || reconnected) && mc.screen instanceof net.minecraft.client.gui.screens.BackupConfirmScreen
                    && Boolean.getBoolean("skyweave_compat.probe.lightCache.confirmReopen")) {
                if(!Files.isRegularFile(FMLPaths.GAMEDIR.get().resolve("saves/light-cache/level.dat")))
                    throw new AssertionError("Expected the saved owned light-cache world");
                for(var child:mc.screen.children())if(child instanceof net.minecraft.client.gui.components.Button button
                        && button.getMessage().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents text
                        && text.getKey().equals("selectWorld.backupJoinSkipButton")) {
                    report.addProperty("nativeReopenConfirmed",true);button.onPress();return;
                }
                throw new AssertionError("Native saved-world proceed button unavailable");
            }
            if (closing) {
                if(mc.level==null && mc.getSingleplayerServer()==null && mc.screen instanceof TitleScreen) {
                    if(Boolean.getBoolean("skyweave_compat.probe.lightCache.reconnect") && !reconnected) {
                        report.add("firstRetiredGate",gateSnapshot(retainedInitialLight));reconnected=true;closing=false;opened=false;prepared=true;phase=0;settled=0;lastWorldTick=-1;return;
                    }
                    finish(mc,null);
                }return;
            }
            if (!opened && mc.getOverlay() == null && mc.screen instanceof TitleScreen) {
                boolean saved=Files.exists(FMLPaths.GAMEDIR.get().resolve("saves/light-cache/level.dat"));
                if(saved!=(REOPEN || reconnected))throw new AssertionError(REOPEN?"Expected the retained owned world":"Expected a new owned world");
                opened = true; mc.options.hideGui = true;
                if(REOPEN || reconnected){prepared=true;mc.createWorldOpenFlows().openWorld("light-cache",()->mc.setScreen(new TitleScreen()));return;}
                mc.createWorldOpenFlows().createFreshLevel("light-cache",
                        new LevelSettings("Light cache", GameType.CREATIVE, false, Difficulty.PEACEFUL, true, new GameRules(), WorldDataConfiguration.DEFAULT),
                        new WorldOptions(42, false, false), registries -> registries.registryOrThrow(Registries.WORLD_PRESET)
                                .get(ResourceLocation.withDefaultNamespace("flat")).createWorldDimensions(), mc.screen);
                return;
            }
            if (mc.player == null || mc.level == null || mc.getSingleplayerServer() == null || mc.getOverlay() != null) return;
            if(!OBSERVE_PAUSE && mc.screen!=null){mc.setScreen(null);return;}
            if(!OBSERVE_PAUSE && mc.isPaused())return;
            if(mc.player.tickCount<20)return;
            if(CAPTURE){mc.player.setYRot(170);mc.player.setXRot(17);}
            if (pending != null) { if (!pending.isDone()) return; var value = pending.join(); pending = null;
                if (value.has("serverCells")) {
                    var row = sample(mc); row.add("serverCells", value.get("serverCells"));
                    row.add("serverPaused",value.get("serverPaused"));row.add("serverTick",value.get("serverTick"));row.add("worldTick",value.get("worldTick"));
                    long tick=value.get("worldTick").getAsLong();
                    if(!OBSERVE_PAUSE && (mc.isPaused() || value.get("serverPaused").getAsBoolean() || tick<=lastWorldTick))throw new AssertionError("Expected an advancing, unpaused world");
                    lastWorldTick=tick;samples.add(row); phase++; settled = 0;
                }
            }
            if (!prepared) {
                prepared = true; var data = JsonParser.parseString(Files.readString(FMLPaths.GAMEDIR.get().resolve("track-geometry.json"))).getAsJsonObject();
                pending = server(mc, player -> {
                    var level = player.serverLevel(); var server = level.getServer();
                    level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
                    level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false, server);
                    level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
                    level.setDayTime(18000); level.setWeatherParameters(6000, 0, false, false);
                    for (var entry : data.getAsJsonArray("blocks")) {
                        var block = entry.getAsJsonObject(); var p = block.getAsJsonArray("pos"); int x=p.get(0).getAsInt(), y=p.get(1).getAsInt(), z=p.get(2).getAsInt();
                        if (x < -34 || x > 2 || z < -68 || z > -40) continue;
                        var state = block.getAsJsonObject("state"); String text = state.get("Name").getAsString();
                        if (state.has("Properties")) text += "[" + String.join(",", state.getAsJsonObject("Properties").entrySet().stream().map(e -> e.getKey()+"="+e.getValue().getAsString()).toList()) + "]";
                        if(Boolean.getBoolean("skyweave_compat.probe.lightCache.vanillaOnly") && !text.startsWith("minecraft:"))continue;
                        try { level.setBlock(new BlockPos(x,y,z), BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK.asLookup(),text,false).blockState(),3); }
                        catch (com.mojang.brigadier.exceptions.CommandSyntaxException failure) { throw new IllegalStateException(failure); }
                    }
                    player.teleportTo(-24.5,-59,-52.5); player.getInventory().clearContent(); return new JsonObject();
                }); return;
            }
            if (phase == CASES.length) {
                if(Boolean.getBoolean("skyweave_compat.probe.lightCache.lifecycle"))report.add("lifecycleControls",InitialLightLifecycleProbe.run(mc));
                retainedInitialLight=initialLight(mc.level);
                if(Boolean.getBoolean("skyweave_compat.probe.lightCache.packets")) {
                    var packetReport=new JsonObject();report.add(reconnected?"reconnectedPacketControls":"packetControls",packetReport);
                    InitialLightPacketProbe.run(mc,packetReport);
                }
                mc.level.disconnect(); mc.disconnect(new TitleScreen()); mc.setScreen(new TitleScreen()); closing = true; return;
            }
            if (settled++ == 0) {
                LightCacheHandoffWitness.phase(phase);
                if(net.neoforged.fml.ModList.get().isLoaded("sabledynlights")) { var manager = Class.forName("fr0nick.sabledynlights.virtual.VirtualLightManager");
                manager.getMethod("syncExternalPositions",Object.class,Map.class).invoke(null,virtualOwner,phase==3?Map.of(SOURCE,15):Map.of()); }
                pending = server(mc, player -> { player.setItemSlot(EquipmentSlot.MAINHAND,phase==1?new ItemStack(Items.TORCH):ItemStack.EMPTY); return new JsonObject(); }); return;
            }
            if (settled < 100) return;
            pending = server(mc, player -> { var result=new JsonObject(); result.add("serverCells",cells(player.serverLevel(),false));
                result.addProperty("serverPaused",mc.getSingleplayerServer().isPaused());result.addProperty("serverTick",player.server.getTickCount());result.addProperty("worldTick",player.serverLevel().getGameTime());return result; });
        } catch (Throwable failure) { failure.printStackTrace(); finish(mc,failure); }
    }

    private JsonObject sample(Minecraft mc) throws Exception {
        var row = new JsonObject(); row.addProperty("case",CASES[phase]); row.addProperty("held",BuiltInRegistries.ITEM.getKey(mc.player.getMainHandItem().getItem()).toString());
        row.addProperty("levelId",System.identityHashCode(mc.level));row.addProperty("x",mc.player.getX());row.addProperty("y",mc.player.getY());row.addProperty("z",mc.player.getZ());
        row.addProperty("clientPaused",mc.isPaused());row.addProperty("screen",mc.screen==null?"none":mc.screen.getClass().getName());row.addProperty("windowActive",mc.isWindowActive());
        boolean shader=false;
        if(net.neoforged.fml.ModList.get().isLoaded("iris")) {
            var api=Class.forName("net.irisshaders.iris.api.v0.IrisApi");shader=(boolean)api.getMethod("isShaderPackInUse").invoke(api.getMethod("getInstance").invoke(null));
        }
        row.addProperty("shaderActive",shader);if(shader)throw new AssertionError("Light-cache diagnostic requires shaders off");
        if(CAPTURE){String name="light-cache-"+(REOPEN?"reopen-":"")+CASES[phase]+".png";
            net.minecraft.client.Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),ignored->{});row.addProperty("screenshot",name);}
        if(net.neoforged.fml.ModList.get().isLoaded("sabledynlights")) { var candidates=(java.util.List<?>)Class.forName("fr0nick.sabledynlights.virtual.VirtualLightManager").getMethod("collectActiveLightCandidates",Minecraft.class).invoke(null,mc);
        row.addProperty("nativeCandidates",candidates.size()); }
        row.addProperty("clientHasLightWork",mc.level.getLightEngine().hasLightWork()); row.addProperty("glError",org.lwjgl.opengl.GL11C.glGetError());
        row.add("initialLightGate",gateSnapshot(initialLight(mc.level)));
        row.add("clientCells",cells(mc.level,true));
        if(!net.neoforged.fml.ModList.get().isLoaded("sodium"))return row;
        var cacheType=Class.forName("net.caffeinemc.mods.sodium.client.model.light.data.SingleBlockLightDataCache");
        Object cache=cacheType.getConstructor().newInstance();
        try {
            cacheType.getMethod("reset",BlockPos.class,net.minecraft.world.level.BlockAndTintGetter.class).invoke(cache,CORNER,mc.level);
            var face=Class.forName("net.caffeinemc.mods.sodium.client.model.light.smooth.AoFaceData"); var constructor=face.getDeclaredConstructor(); constructor.setAccessible(true); Object value=constructor.newInstance();
            var init=face.getDeclaredMethod("initLightData",Class.forName("net.caffeinemc.mods.sodium.client.model.light.data.LightDataAccess"),BlockPos.class,Direction.class,boolean.class); init.setAccessible(true); init.invoke(value,cache,CORNER,Direction.UP,true);
            var field=face.getDeclaredField("lm");field.setAccessible(true);var corners=new JsonArray(); for(int light:(int[])field.get(value)) corners.add(light); row.add("nativeCornerLightmaps",corners);
            var faces=new JsonArray();
            for(int x=-28;x<=-24;x++)for(int y=-60;y<=-57;y++)for(int z=-63;z<=-59;z++) {
                var pos=new BlockPos(x,y,z);var state=mc.level.getBlockState(pos);
                if(!state.isSolidRender(mc.level,pos))continue;
                for(var direction:new Direction[]{Direction.UP,Direction.SOUTH}) {
                    var adjacent=pos.relative(direction);
                    if(mc.level.getBlockState(adjacent).isSolidRender(mc.level,adjacent))continue;
                    cacheType.getMethod("reset",BlockPos.class,net.minecraft.world.level.BlockAndTintGetter.class).invoke(cache,pos,mc.level);
                    var current=constructor.newInstance();init.invoke(current,cache,pos,direction,true);
                    var item=new JsonObject();item.addProperty("pos",pos.toShortString());item.addProperty("face",direction.toString());
                    var maps=new JsonArray();for(int light:(int[])field.get(current))maps.add(light);item.add("lightmaps",maps);faces.add(item);
                }
            }
            row.add("nativeExposedFaces",faces);
            var dataType=Class.forName("net.caffeinemc.mods.sodium.client.model.light.data.LightDataAccess");
            var unpackFO=dataType.getMethod("unpackFO",int.class);var unpackLU=dataType.getMethod("unpackLU",int.class);var unpackBL=dataType.getMethod("unpackBL",int.class);
            var words=new JsonArray();int violations=0,emitting=0,nonOpaqueLit=0;
            for(int x=-28;x<=-24;x++)for(int y=-60;y<=-57;y++)for(int z=-63;z<=-59;z++){
                var pos=new BlockPos(x,y,z);cacheType.getMethod("reset",BlockPos.class,net.minecraft.world.level.BlockAndTintGetter.class).invoke(cache,pos,mc.level);
                int word=(int)cacheType.getMethod("get",int.class,int.class,int.class).invoke(cache,x,y,z);
                boolean opaque=(boolean)unpackFO.invoke(null,word);int luminance=(int)unpackLU.invoke(null,word),block=(int)unpackBL.invoke(null,word);
                if(opaque && luminance==0 && block!=0)violations++;
                if(luminance>0)emitting++;if(!opaque && block>0)nonOpaqueLit++;
                var cellWord=new JsonObject();cellWord.addProperty("pos",pos.toShortString());cellWord.addProperty("packed",word);cellWord.addProperty("fullOpaque",opaque);cellWord.addProperty("luminance",luminance);cellWord.addProperty("block",block);words.add(cellWord);
            }
            row.add("nativeCacheWords",words);row.addProperty("opaqueZeroViolations",violations);row.addProperty("emittingSamples",emitting);row.addProperty("nonOpaqueLitSamples",nonOpaqueLit);
            if(Boolean.getBoolean("skyweave_compat.probe.lightCache.preserveOpaque") && violations!=0)throw new AssertionError("Sodium opaque zero sentinel was raised");
        } finally { cacheType.getMethod("release").invoke(cache); }
        return row;
    }

    private static JsonArray cells(Level level, boolean client) {
        var rows=new JsonArray();
        var engine=level.getLightEngine().getLayerListener(LightLayer.BLOCK);
        var storage=(design.inherent.skyweavecompat.clientprobe.mixin.LightCacheSectionAccess)
                ((design.inherent.skyweavecompat.clientprobe.mixin.LightCacheEngineAccess)engine).skyweave$storage();
        for(int x=-28;x<=-24;x++) for(int y=-60;y<=-57;y++) for(int z=-63;z<=-59;z++) {
            var pos=new BlockPos(x,y,z);var row=new JsonObject();row.addProperty("pos",pos.toShortString());
            var data=level.getLightEngine().getLayerListener(LightLayer.BLOCK).getDataLayerData(SectionPos.of(pos));
            row.addProperty("storedBlock",data==null?0:data.get(x&15,y&15,z&15));row.addProperty("layerPresent",data!=null);
            long section=SectionPos.of(pos).asLong();
            row.addProperty("visibleBlock",LightCacheHandoffWitness.value(LightCacheHandoffWitness.layer(storage.skyweave$visible(),section),pos));
            row.addProperty("updatingBlock",LightCacheHandoffWitness.value(LightCacheHandoffWitness.layer(storage.skyweave$updating(),section),pos));
            row.addProperty("queuedBlock",LightCacheHandoffWitness.value(storage.skyweave$queued().get(section),pos));
            row.addProperty("publicBlock",level.getBrightness(LightLayer.BLOCK,pos));row.addProperty("block",BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).toString());
            var sky=level.getLightEngine().getLayerListener(LightLayer.SKY).getDataLayerData(SectionPos.of(pos));
            row.addProperty("storedSky",sky==null?0:sky.get(x&15,y&15,z&15));row.addProperty("publicSky",level.getBrightness(LightLayer.SKY,pos));
            if(client) row.addProperty("rendererLightmap",LevelRenderer.getLightColor(level,level.getBlockState(pos),pos));rows.add(row);
        } return rows;
    }

    private CompletableFuture<JsonObject> server(Minecraft mc, java.util.function.Function<net.minecraft.server.level.ServerPlayer,JsonObject> action) {
        var server=mc.getSingleplayerServer();var id=mc.player.getUUID();var result=new CompletableFuture<JsonObject>();
        Runnable task=()->{try { if(!server.isSameThread())throw new AssertionError("Expected native server owner");result.complete(action.apply(server.getPlayerList().getPlayer(id))); } catch(Throwable failure){result.completeExceptionally(failure);} };
        if(OBSERVE_PAUSE)server.execute(task);
        else {if(pendingServerAction!=null)throw new AssertionError("Overlapping fixture actions");expectedServer=server;pendingServerAction=task;}
        return result;
    }
    private void finish(Minecraft mc,Throwable failure) {
        done=true; report.addProperty("passed",failure==null);if(failure!=null)report.addProperty("error",failure.toString());
        report.add("handoffs",LightCacheHandoffWitness.snapshot());
        report.add("initialOrderControl",LightCacheHandoffWitness.ordering());
        try { report.add("retiredInitialLightGate",gateSnapshot(retainedInitialLight)); }
        catch(Exception error){report.addProperty("gateObservationError",error.toString());report.addProperty("passed",false);}
        try { Files.writeString(FMLPaths.GAMEDIR.get().resolve("light-cache-proof.json"),report.toString()); }
        catch(java.io.IOException error){throw new IllegalStateException(error);}finally{pending=null;pendingServerAction=null;expectedServer=null;mc.stop();}
    }

    private static Object initialLight(net.minecraft.client.multiplayer.ClientLevel level) throws Exception {
        try { var owner=Class.forName("design.inherent.skyweavecompat.lighting.InitialClientLight$EngineOwner");
            return owner.isInstance(level.getLightEngine())?owner.getMethod("skyweave$existingInitialLight").invoke(level.getLightEngine()):null; }
        catch(ClassNotFoundException ignored){return null;}
    }
    private static JsonObject gateSnapshot(Object gate) throws Exception {
        var result=new JsonObject();result.addProperty("present",gate!=null);if(gate==null)return result;
        for(String name:new String[]{"states","ready","retired"}){
            var field=gate.getClass().getDeclaredField(name);field.setAccessible(true);var value=field.get(gate);
            if(value instanceof Map<?,?> map)result.addProperty(name,map.size());
            else if(value instanceof java.util.Collection<?> collection)result.addProperty(name,collection.size());
            else result.addProperty(name,(boolean)value);
        }
        return result;
    }
}
