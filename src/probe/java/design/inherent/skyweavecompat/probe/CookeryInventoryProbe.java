package design.inherent.skyweavecompat.probe;

import com.github.ysbbbbbb.kaleidoscopecookery.blockentity.kitchen.MillstoneBlockEntity;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.items.ItemStackHandler;

/** Real native handler/recipes/state in an isolated server. No AE2 exploit claim. */
final class CookeryInventoryProbe {
    private MinecraftServer pending;
    CookeryInventoryProbe() {
        if (!Files.isRegularFile(FMLPaths.GAMEDIR.get().resolve("cookery-fixture.json")))
            throw new IllegalStateException("Missing owned Cookery fixture marker");
        NeoForge.EVENT_BUS.addListener((ServerStartedEvent event) -> pending = event.getServer());
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> {
            if (pending == null) return;
            var server = pending; pending = null;
            var report = new JsonObject();
            try { run(server.overworld(), report); report.addProperty("passed", true); }
            catch (Throwable failure) { report.addProperty("passed", false); report.addProperty("error", failure.toString()); failure.printStackTrace(); }
            try { Files.writeString(FMLPaths.GAMEDIR.get().resolve("cookery-proof.json"), report.toString()); }
            catch (Exception failure) { throw new IllegalStateException(failure); }
        });
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static MillstoneBlockEntity fresh(ServerLevel level) {
        var block = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(net.minecraft.resources.ResourceLocation.parse("kaleidoscope_cookery:millstone"));
        var machine = new MillstoneBlockEntity(new BlockPos(0, 96, 0), block.defaultBlockState());
        if (level != null) machine.setLevel(level);
        return machine;
    }
    private static void field(MillstoneBlockEntity machine, String name, Object value) throws Exception {
        var field = MillstoneBlockEntity.class.getDeclaredField(name); field.setAccessible(true); field.set(machine, value);
    }
    private static ItemStack wheat(int count) {
        var stack = new ItemStack(Items.WHEAT, count);
        if (!stack.isEmpty()) stack.set(DataComponents.CUSTOM_NAME, Component.literal("Cookery custody fixture"));
        return stack;
    }
    private static void run(ServerLevel level, JsonObject report) throws Exception {
        boolean repaired = System.getProperty("neighbors.probe.cookery").equals("repaired");
        report.addProperty("expected", repaired ? "repaired" : "original");
        report.addProperty("scope", "Actual Cookery native capability, recipe matching, insertion, serialized state and processing; not AE2 or whole-pack exploitation");
        var rows = new JsonArray(); report.add("counts", rows);
        for (int count : new int[]{0, 1, 8, 9, 64}) {
            var machine = fresh(level); var handler = machine.getInputHandler();
            var caller = wheat(count); var before = caller.copy(); var state = machine.saveWithoutMetadata(level.registryAccess());
            var simulated = handler.insertItem(0, caller, true);
            check(ItemStack.matches(caller, before), "Simulation changed caller at " + count);
            check(state.equals(machine.saveWithoutMetadata(level.registryAccess())), "Simulation changed serialized machine at " + count);
            var actual = handler.insertItem(0, caller, false);
            int accepted = Math.min(count, 8);
            check(actual.getCount() == count - accepted && machine.getInput().getCount() == accepted, "Commit capacity/remainder at " + count);
            check(simulated.getCount() == (repaired ? count - accepted : 0), "Simulation capacity at " + count);
            check(caller.getCount() == (repaired ? count : count - accepted), "Caller ownership at " + count);
            check(actual.getCount() + machine.getInput().getCount() == count, "Item conservation at " + count);
            if (accepted > 0) check(ItemStack.isSameItemSameComponents(before, machine.getInput()), "Input components lost");
            if (!actual.isEmpty()) check(ItemStack.isSameItemSameComponents(before, actual), "Remainder components lost");
            var row = new JsonObject(); row.addProperty("offered", count); row.addProperty("simulatedRemainder", simulated.getCount());
            row.addProperty("actualRemainder", actual.getCount()); row.addProperty("machineInput", machine.getInput().getCount());
            row.addProperty("callerAfter", caller.getCount()); row.addProperty("simulationUnchanged", true); rows.add(row);
        }
        var negatives = new JsonArray(); report.add("refusals", negatives);
        for (String condition : new String[]{"no-level", "no-recipe", "output-present", "output-full", "input-full", "busy", "resident-zero-progress", "slot-negative", "slot-overflow"}) {
            var machine = fresh(condition.equals("no-level") ? null : level);
            var caller = condition.equals("no-recipe") ? new ItemStack(Items.NETHER_STAR, 9) : wheat(9);
            int slot = condition.equals("slot-negative") ? -1 : condition.equals("slot-overflow") ? 1 : 0;
            if (condition.startsWith("output")) {
                var output = (ItemStackHandler)machine.getOutputs();
                for (int i = 0; i < (condition.equals("output-full") ? output.getSlots() : 1); i++) output.setStackInSlot(i, new ItemStack(Items.STONE, 64));
            }
            if (condition.equals("busy") || condition.equals("resident-zero-progress") || condition.equals("input-full")) {
                field(machine, "input", wheat(condition.equals("input-full") ? 8 : 3)); field(machine, "progress", condition.equals("busy") ? 10 : 0);
            }
            var state = machine.saveWithoutMetadata(level.registryAccess()); var before = caller.copy();
            var simulated = machine.getInputHandler().insertItem(slot, caller, true);
            check(ItemStack.matches(caller, before) && state.equals(machine.saveWithoutMetadata(level.registryAccess())), "Refused simulation mutation: " + condition);
            int simulatedCount = simulated.getCount();
            var actual = machine.getInputHandler().insertItem(slot, caller, false);
            boolean originalGap = !repaired && (condition.equals("resident-zero-progress") || condition.equals("input-full") || condition.startsWith("slot-"));
            if (!originalGap) check(ItemStack.matches(actual, before) && ItemStack.matches(caller, before)
                    && state.equals(machine.saveWithoutMetadata(level.registryAccess())), "Refusal failed: " + condition);
            var row = new JsonObject(); row.addProperty("condition", condition); row.addProperty("simulatedRemainder", simulatedCount);
            row.addProperty("actualRemainder", actual.getCount()); row.addProperty("callerAfter", caller.getCount());
            row.addProperty("machineInput", machine.getInput().getCount()); negatives.add(row);
        }
        var componentMachine = fresh(level);
        var componentStack = wheat(9); componentStack.set(DataComponents.MAX_STACK_SIZE, 4);
        var componentBefore = componentStack.copy();
        int componentSimulation = componentMachine.getInputHandler().insertItem(0, componentStack, true).getCount();
        var componentRemainder = componentMachine.getInputHandler().insertItem(0, componentStack, false);
        check(componentSimulation == (repaired ? 5 : 0), "Component stack limit simulation");
        check(componentRemainder.getCount() == (repaired ? 5 : 1) && componentMachine.getInput().getCount() == (repaired ? 4 : 8), "Component stack limit commit");
        check(componentStack.getCount() == (repaired ? 9 : 1) && ItemStack.isSameItemSameComponents(componentBefore, componentMachine.getInput()), "Component ownership");
        report.addProperty("componentMaxStackSize", 4); report.addProperty("componentInput", componentMachine.getInput().getCount());
        report.addProperty("componentCallerAfter", componentStack.getCount());
        var registered = fresh(level); level.setBlockAndUpdate(registered.getBlockPos(), registered.getBlockState());
        var actualMachine = (MillstoneBlockEntity) level.getBlockEntity(registered.getBlockPos());
        var capability = level.getCapability(Capabilities.ItemHandler.BLOCK, registered.getBlockPos(), Direction.UP);
        check(capability == actualMachine.getInputHandler(), "Actual capability registration does not expose native input handler");
        check(level.getCapability(Capabilities.ItemHandler.BLOCK, registered.getBlockPos(), Direction.DOWN) == null, "Output-side capability changed");
        var offered = wheat(64); var remainder = capability.insertItem(0, offered, true);
        check(remainder.getCount() == (repaired ? 56 : 0) && offered.getCount() == 64, "Registered capability simulation mismatch");
        var helperRemainder = net.neoforged.neoforge.items.ItemHandlerHelper.insertItemStacked(capability, offered, false);
        check(helperRemainder.getCount() == 56 && actualMachine.getInput().getCount() == 8
                && offered.getCount() == (repaired ? 64 : 56), "Native stacked-insertion helper custody");
        report.addProperty("nativeHelperRemainder", helperRemainder.getCount());
        report.addProperty("nativeHelperCallerAfter", offered.getCount());
        report.addProperty("registeredUpperCapability", true); report.addProperty("downSideUnchanged", true);
        // Drive the original processing method with a native bound animal; do not replace recipes or shorten progress.
        var processing = fresh(level); var animal = EntityType.COW.create(level);
        animal.moveTo(.5, 96, 2.5); animal.tickCount = 1; processing.bindEntity(animal);
        var playerHeld = wheat(9); check(processing.onPutItem(level, playerHeld), "Native player insertion refused wheat");
        check(playerHeld.getCount() == 1 && processing.getInput().getCount() == 8, "Native player insertion semantics changed");
        int ticks = 0;
        while (!processing.getInput().isEmpty() && ticks++ < 1200) processing.tick(level);
        check(processing.getInput().isEmpty(), "Native processing did not finish");
        int output = 0;
        for (int i = 0; i < processing.getOutputs().getSlots(); i++) {
            var stack = processing.getOutputs().getStackInSlot(i);
            if (!stack.isEmpty()) check(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals("kaleidoscope_cookery:flour"), "Native output changed");
            output += stack.getCount();
        }
        check(output == 8, "Native wheat recipe output changed: " + output);
        report.addProperty("nativePlayerCallerAfter", playerHeld.getCount()); report.addProperty("nativeProcessingCalls", ticks);
        report.addProperty("nativeOutputFlour", output);
    }
}
