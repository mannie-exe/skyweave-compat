/* SPDX-License-Identifier: Apache-2.0 */
package design.inherent.skyweavecompat.probe;

import com.google.gson.JsonObject;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import top.theillusivec4.curios.api.CuriosApi;

/** Original durability path and actual native notification boundaries in a disposable server. */
public final class CurioBreakProbe {
    private MinecraftServer pending;
    public static int accessoryPackets, curiosPackets;
    public CurioBreakProbe() {
        NeoForge.EVENT_BUS.addListener((ServerStartedEvent event) -> pending = event.getServer());
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Pre event) -> {
            if (pending != event.getServer()) return;
            var server = pending; pending = null; run(server);
        });
    }
    public static void accessory(Record packet) {
        if (packet.getClass().getName().equals("io.wispforest.accessories.networking.client.AccessoryBreak")) accessoryPackets++;
    }
    private static void run(MinecraftServer server) {
        var report = new JsonObject(); report.addProperty("passed", false);
        boolean repaired = System.getProperty("neighbors.probe.curioBreak").equals("repaired");
        report.addProperty("expectedRepair", repaired);
        String slot = System.getProperty("neighbors.probe.curioBreakSlot", "back"); report.addProperty("slot", slot);
        try {
            var player = FakePlayerFactory.getMinecraft(server.overworld()); player.getAbilities().instabuild = false;
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_INGOT, 7));
            var stack = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("reliable_gliders:glider")));
            check(stack.getMaxDamage() > 2, "Native glider durability is unavailable");
            stack.set(DataComponents.CUSTOM_NAME, Component.literal("Native break custody control"));
            stack.setDamageValue(stack.getMaxDamage() - 2);
            var slots = CuriosApi.getCuriosInventory(player).orElseThrow().getStacksHandler(slot).orElseThrow().getStacks();
            check(slots.insertItem(0, stack, false).isEmpty(), "Native selected slot rejected the glider");
            var damage = Class.forName("com.evandev.reliable_gliders.compat.CuriosCompat")
                    .getMethod("damageGlider", net.minecraft.server.level.ServerPlayer.class);
            damage.invoke(null, player);
            var equipped = slots.getStackInSlot(0);
            check(equipped.getCount() == 1 && equipped.getDamageValue() == equipped.getMaxDamage() - 1,
                    "Native nonbreaking damage changed custody");
            check(Component.literal("Native break custody control").equals(equipped.get(DataComponents.CUSTOM_NAME)),
                    "Native damage changed item components");
            check(accessoryPackets == 0 && curiosPackets == 0, "Nonbreaking damage emitted a break notification");
            boolean originalFailure = false;
            try { damage.invoke(null, player); }
            catch (java.lang.reflect.InvocationTargetException failure) {
                var cause = failure.getCause();
                if (!(cause instanceof org.spongepowered.asm.mixin.injection.callback.CancellationException)
                        || !cause.getMessage().contains("broadcastCurioBreakEvent")) throw failure;
                originalFailure = true;
            }
            report.addProperty("originalCancellationFailure", originalFailure);
            check(originalFailure != repaired, "Native callback outcome did not match the selected repair");
            check(slots.getStackInSlot(0).isEmpty(), "Broken glider remained equipped");
            check(player.getMainHandItem().is(Items.IRON_INGOT) && player.getMainHandItem().getCount() == 7,
                    "Equipment damage consumed unrelated held items");
            check(accessoryPackets == 1 && curiosPackets == 0, "Break notification ownership differs");
            report.addProperty("accessoryNotifications", accessoryPackets); report.addProperty("curiosNotifications", curiosPackets);
            report.addProperty("nativeDurabilityAndCustody", true); report.addProperty("passed", true);
            report.addProperty("scope", "Actual dedicated-server original glider damage and equipment notification boundaries; no real client presentation or full equipment compatibility claim");
        } catch (Throwable failure) { failure.printStackTrace(); report.addProperty("error", failure.toString()); }
        finally {
            try { Files.writeString(Path.of("curio-break-proof.json"), report.toString()); }
            catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
            server.halt(false);
        }
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
