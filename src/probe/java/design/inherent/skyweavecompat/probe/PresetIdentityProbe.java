package design.inherent.skyweavecompat.probe;

import com.google.gson.JsonObject;
import com.mojang.serialization.Lifecycle;
import design.inherent.skyweavecompat.terrain.GeologicalGenerator;
import java.lang.management.ManagementFactory;
import net.minecraft.core.Holder;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import org.sathrek.sky_archipelago.worldgen.generator.core.SkyIslandChunkGenerator;

/** Exercise the transformed native method; local holders never enter the live registry. */
final class PresetIdentityProbe {
    private static volatile int observed;
    private PresetIdentityProbe() {}

    static JsonObject run(MinecraftServer server, SkyIslandChunkGenerator owned) {
        var registry = new MappedRegistry<NoiseGeneratorSettings>(Registries.NOISE_SETTINGS, Lifecycle.stable());
        var foreignKey = ResourceKey.create(Registries.NOISE_SETTINGS, ResourceLocation.parse("skyweave_probe:foreign"));
        var foreignHolder = registry.register(foreignKey, owned.generatorSettings().value(), RegistrationInfo.BUILT_IN);
        registry.freeze();
        var foreign = new SkyIslandChunkGenerator(owned.getBiomeSource(), foreignHolder, owned.embeddedSettings());
        var direct = new SkyIslandChunkGenerator(owned.getBiomeSource(), Holder.direct(owned.generatorSettings().value()), owned.embeddedSettings());
        var ordinary = (SkyIslandChunkGenerator)server.registryAccess().registryOrThrow(Registries.WORLD_PRESET)
                .get(ResourceLocation.parse("sky_archipelago:sky_islands")).createWorldDimensions().overworld();
        var generators = new GeologicalGenerator[] {(GeologicalGenerator)(Object)owned,
                (GeologicalGenerator)(Object)foreign, (GeologicalGenerator)(Object)direct, (GeologicalGenerator)(Object)ordinary};
        for (int i = 0; i < generators.length; i++)
            if (generators[i].skyweave$isEnabled() != (i == 0)) throw new AssertionError("Native preset eligibility " + i);
        if (foreignHolder.value() != owned.generatorSettings().value()) throw new AssertionError("Foreign-value control differs");
        var missing = ResourceKey.create(Registries.NOISE_SETTINGS, ResourceLocation.parse("skyweave_probe:missing"));
        boolean refused = false;
        try { registry.getHolderOrThrow(missing); } catch (IllegalStateException expected) { refused = true; }
        if (!refused) throw new AssertionError("Missing native registry key admitted");

        for (int i = 0; i < 100_000; i++) observed = generators[i & 3].skyweave$isEnabled() ? 1 : 0;
        var bean = ManagementFactory.getThreadMXBean();
        com.sun.management.ThreadMXBean allocation = bean instanceof com.sun.management.ThreadMXBean b
                && b.isThreadAllocatedMemorySupported() ? b : null;
        if (allocation != null && !allocation.isThreadAllocatedMemoryEnabled()) allocation.setThreadAllocatedMemoryEnabled(true);
        long thread = Thread.currentThread().threadId();
        long before = allocation == null ? -1 : allocation.getThreadAllocatedBytes(thread);
        long started = System.nanoTime();
        int hits = 0;
        for (int i = 0; i < 1_000_000; i++) hits += generators[i & 3].skyweave$isEnabled() ? 1 : 0;
        observed = hits;
        long elapsed = System.nanoTime() - started;
        long bytes = allocation == null ? -1 : allocation.getThreadAllocatedBytes(thread) - before;
        if (observed != 250_000) throw new AssertionError("Native eligibility changed during allocation control");
        var result = new JsonObject();
        result.addProperty("matchingKey", true); result.addProperty("equalForeignValueRejected", true);
        result.addProperty("directHolderRejected", true); result.addProperty("ordinaryPresetRejected", true);
        result.addProperty("missingRegistryKeyRefused", refused);
        result.addProperty("warmupCalls", 100_000); result.addProperty("measuredCalls", 1_000_000);
        result.addProperty("matchingCalls", hits); result.addProperty("allocatedBytes", bytes);
        result.addProperty("elapsedNanos", elapsed);
        result.addProperty("scope", "Transformed native eligibility loop; not whole-generation allocation or gameplay speed");
        return result;
    }
}
