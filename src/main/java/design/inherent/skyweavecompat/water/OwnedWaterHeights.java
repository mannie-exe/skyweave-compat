package design.inherent.skyweavecompat.water;

import com.AdamNew.dynamicwaters.FastConcurrentCache;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;

/** Native coarse storage, isolated by the actual query pair without retaining either world owner. */
public final class OwnedWaterHeights {
    private static final int MAX_CONTEXTS = 4, BLOCKS_PER_CONTEXT = 16384;
    private record Context(WeakReference<ChunkGenerator> generator, WeakReference<RandomState> random,
                           FastConcurrentCache<short[]> blocks) {}
    private static final ArrayList<Context> CONTEXTS = new ArrayList<>();
    private OwnedWaterHeights() {}

    public static synchronized FastConcurrentCache<short[]> cache(ChunkGenerator generator, RandomState random) {
        if (generator == null || random == null) throw new IllegalArgumentException("Owned height cache requires a generator and RandomState");
        CONTEXTS.removeIf(context -> context.generator().get() == null || context.random().get() == null);
        for (int i = 0; i < CONTEXTS.size(); i++) {
            var context = CONTEXTS.get(i);
            if (context.generator().get() == generator && context.random().get() == random) {
                CONTEXTS.remove(i); CONTEXTS.add(context);
                return context.blocks();
            }
        }
        if (CONTEXTS.size() == MAX_CONTEXTS) CONTEXTS.removeFirst();
        var blocks = new FastConcurrentCache<short[]>(BLOCKS_PER_CONTEXT);
        CONTEXTS.add(new Context(new WeakReference<>(generator), new WeakReference<>(random), blocks));
        return blocks;
    }

    /** In-flight slices may finish in their old cache but cannot republish it into the new namespace. */
    public static synchronized void reset() { CONTEXTS.clear(); }
}
