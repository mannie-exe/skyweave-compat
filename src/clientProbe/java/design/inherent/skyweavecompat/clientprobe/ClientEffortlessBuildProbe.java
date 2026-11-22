package design.inherent.skyweavecompat.clientprobe;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.InputConstants;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.settings.KeyModifier;

/** One native placement packet and real server undo/redo in the disposable input world. */
final class ClientEffortlessBuildProbe {
    private static BlockPos target;
    private ClientEffortlessBuildProbe() {}

    static void act(Path root, Minecraft mc, String action, boolean configured) throws Exception {
        if (!Files.isRegularFile(root.resolve("modifier-review-fixture.json")) || mc.player == null || mc.getSingleplayerServer() == null)
            throw new IllegalStateException("Expected an owned integrated build fixture");
        var actor = mc.player.getUUID();
        if (action.equals("setup")) {
            target = mc.player.blockPosition().offset(2, 0, 0);
            mc.getSingleplayerServer().execute(() -> {
                var player = mc.getSingleplayerServer().getPlayerList().getPlayer(actor);
                player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
                player.serverLevel().setBlockAndUpdate(target.below(), Blocks.STONE.defaultBlockState());
                player.serverLevel().setBlockAndUpdate(target, Blocks.AIR.defaultBlockState());
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIRT, 64)); player.containerMenu.broadcastChanges();
                try { Class.forName("neoforge.nl.requios.effortlessbuilding.utilities.UndoManager").getMethod("clearPlayer", java.util.UUID.class).invoke(null, actor); }
                catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
            });
            return;
        }
        if (target == null) throw new IllegalStateException("Build fixture not prepared");
        if (action.equals("place")) {
            var mode = Class.forName("neoforge.nl.requios.effortlessbuilding.buildmode.BuildModeEnum");
            var option = Class.forName("neoforge.nl.requios.effortlessbuilding.buildmode.ModeOptions$ActionEnum");
            var replace = Class.forName("neoforge.nl.requios.effortlessbuilding.buildmode.BuildSettings$ReplaceMode");
            var packet = Class.forName("neoforge.nl.requios.effortlessbuilding.network.PlaceBuildModePacket");
            var value = packet.getConstructor(mode, BlockPos.class, BlockPos.class, BlockPos.class, Direction.class, Vec3.class,
                    option, option, option, option, replace, boolean.class).newInstance(mode.getField("LINE").get(null),
                            target, target, target, Direction.UP, Vec3.atCenterOf(target), option.getField("FULL").get(null),
                            option.getField("CUBE_FULL").get(null), option.getField("SHORT_EDGE").get(null),
                            option.getField("CIRCLE_START_CORNER").get(null), replace.getField("ONLY_AIR").get(null), true);
            Class.forName("neoforge.nl.requios.effortlessbuilding.network.PacketHandler").getMethod("sendToServer", packet).invoke(null, value);
            return;
        }
        if (action.equals("undo") || action.equals("redo")) {
            var mapping = (KeyMapping) Class.forName("neoforge.nl.requios.effortlessbuilding.utilities.KeyBindings").getField(action).get(null);
            var key = mapping.getKey(); var modifier = mapping.getKeyModifier();
            int held = configured ? 340 : 341;
            ClientModifierProbe.controlledInput = true;
            try {
                mapping.setKeyModifierAndCode(configured ? KeyModifier.SHIFT : KeyModifier.NONE, InputConstants.Type.KEYSYM.getOrCreate(302)); KeyMapping.resetMapping();
                event(mc, held, 1); event(mc, 302, 1);
                Class.forName("neoforge.nl.requios.effortlessbuilding.NeoForgeClientSetup$GameEvents").getMethod("onClientTick",
                        net.neoforged.neoforge.client.event.ClientTickEvent.Post.class).invoke(null, new net.neoforged.neoforge.client.event.ClientTickEvent.Post());
            } finally {
                event(mc, 302, 0); event(mc, held, 0); ClientModifierProbe.controlledInput = false;
                mapping.setKeyModifierAndCode(modifier, key); KeyMapping.resetMapping();
            }
            return;
        }
        if (!action.equals("state")) throw new IllegalArgumentException("Unknown build fixture action");
        mc.getSingleplayerServer().execute(() -> {
            var player = mc.getSingleplayerServer().getPlayerList().getPlayer(actor); var result = new JsonObject();
            result.addProperty("serverBlock", BuiltInRegistries.BLOCK.getKey(player.serverLevel().getBlockState(target).getBlock()).toString());
            result.addProperty("serverHeldCount", player.getMainHandItem().getCount());
            result.addProperty("serverCarriedEmpty", player.containerMenu.getCarried().isEmpty());
            mc.execute(() -> {
                result.addProperty("clientBlock", BuiltInRegistries.BLOCK.getKey(mc.level.getBlockState(target).getBlock()).toString());
                result.addProperty("clientHeldDirt", mc.player.getMainHandItem().is(Items.DIRT));
                try { ProbeStateFiles.write(root.resolve("effortless-build-proof.json"), result.toString()); }
                catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
            });
        });
    }

    private static void event(Minecraft mc, int code, int phase) {
        if (phase == 0) ClientModifierProbe.heldKeys.remove(code); else ClientModifierProbe.heldKeys.add(code);
        mc.keyboardHandler.keyPress(mc.getWindow().getWindow(), code, 0, phase, 0);
    }
}
