/* SPDX-License-Identifier: Apache-2.0 */
package design.inherent.skyweavecompat.clientprobe;

import com.google.gson.*;
import java.nio.file.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Opens only its marked disposable world; common ADQ probes observe the real integrated server. */
public final class ClientAdqProbe {
    private final Path root=FMLPaths.GAMEDIR.get();
    private boolean opened,stopped;
    private boolean confirmedExperimentalCopy;
    private int ticks;
    ClientAdqProbe() {
        if(!Files.isRegularFile(root.resolve("adq-client-fixture.json")))throw new IllegalStateException("Missing disposable ADQ client marker");
        NeoForge.EVENT_BUS.addListener(this::tick);
    }
    private void tick(ClientTickEvent.Post event) {
        var mc=Minecraft.getInstance();if(stopped || ++ticks%5!=0 || mc.getOverlay()!=null)return;
        try {
            if(opened && !confirmedExperimentalCopy && mc.screen!=null && mc.getSingleplayerServer()==null) {
                String label=net.minecraft.network.chat.Component.translatable("selectWorld.backupJoinSkipButton").getString();
                for(var child:mc.screen.children()) {
                    if(child instanceof net.minecraft.client.gui.components.Button button && button.active && button.getMessage().getString().equals(label)) {
                        confirmedExperimentalCopy=true;
                        Files.writeString(root.resolve("adq-native-reopen-confirmation.json"),"{\"scope\":\"Actual native experimental-world confirmation for this marked disposable copy only\"}");
                        button.onPress();return;
                    }
                }
            }
            var state=new JsonObject();state.addProperty("screen",mc.screen==null?null:mc.screen.getClass().getName());
            state.addProperty("actualIntegratedServer",mc.getSingleplayerServer()!=null);state.addProperty("actualPlayerJoined",mc.player!=null);
            Files.writeString(root.resolve("adq-client-state.json"),new Gson().toJson(state));
            if(!opened && mc.screen instanceof TitleScreen) {
                opened=true;mc.options.pauseOnLostFocus=false;
                String world="adq-native-review";
                if(Files.isDirectory(root.resolve("saves/"+world)))mc.createWorldOpenFlows().openWorld(world,()->mc.setScreen(new TitleScreen()));
                else mc.createWorldOpenFlows().createFreshLevel(world,
                        new LevelSettings("ADQ native review",GameType.CREATIVE,false,Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT),
                        new WorldOptions(42,true,false),registries->registries.registryOrThrow(Registries.WORLD_PRESET)
                                .get(ResourceLocation.parse("skyweave_compat:islands_v1")).createWorldDimensions(),mc.screen);
            }
            var proof=Path.of(System.getProperty("neighbors.probe.output"));
            if(Files.isRegularFile(proof)) {
                var report=JsonParser.parseString(Files.readString(proof)).getAsJsonObject();
                if(report.has("terminal") && report.get("terminal").getAsBoolean()) {
                    var result=new JsonObject();result.addProperty("actualIntegratedServer",mc.getSingleplayerServer()!=null);
                    result.addProperty("actualPlayerJoined",mc.player!=null);result.addProperty("routeRegistered",report.has("routeRegistered") && report.get("routeRegistered").getAsBoolean());
                    result.addProperty("scope","Actual integrated ADQ route and joined client; no cargo acceptance/payment or visual acceptance claim");
                    Files.writeString(root.resolve("adq-client-result.json"),new GsonBuilder().setPrettyPrinting().create().toJson(result));
                    stopped=true;mc.stop();
                }
            }
        }catch(Exception error){throw new IllegalStateException("Disposable ADQ client observation failed",error);}
    }
}
