package design.inherent.skyweavecompat.water;

import com.AdamNew.dynamicwaters.HydrologyManager;
import design.inherent.skyweavecompat.climate.IslandClimate;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;

/** Only suppress the ocean-biome veto while evaluating an actual island stream's fluid flow. */
public final class IslandWaterFlow {
    private static final ThreadLocal<Boolean> STREAM = new ThreadLocal<>();
    private IslandWaterFlow() {}
    public static boolean stream() { return Boolean.TRUE.equals(STREAM.get()); }
    public static <T> T query(BlockGetter blocks, BlockPos pos, FluidState state, Supplier<T> operation) {
        boolean stream = false;
        if (blocks instanceof Level level && IslandClimate.applies(level) && state.is(FluidTags.WATER)) {
            var global = com.AdamNew.dynamicwaters.SableCompat.getGlobalBlockPos(blocks, pos);
            stream = HydrologyManager.getRiverFlow(global.getX(), global.getY(), global.getZ(), level).lengthSqr() > .0001;
        }
        Boolean previous = STREAM.get();
        STREAM.set(stream);
        try { return operation.get(); }
        finally { if (previous == null) STREAM.remove(); else STREAM.set(previous); }
    }
}
