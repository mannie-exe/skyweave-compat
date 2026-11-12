package design.inherent.skyweavecompat.climate;

import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.*;

/** Private native tree with deterministic cold-tree ties, independent of original source history. */
public final class NativeClimateIndex {
    public static final int MAX_PARAMETERS=16384;
    private static final ThreadLocal<Boolean> CREATING=new ThreadLocal<>();
    private final Climate.ParameterList<Holder<Biome>> parameters;
    public NativeClimateIndex(Climate.ParameterList<Holder<Biome>> source) {
        var values=source.values();
        if(values.isEmpty() || values.size()>MAX_PARAMETERS)throw new IllegalArgumentException("Native climate parameter count exceeds 1.."+MAX_PARAMETERS);
        var copy=List.copyOf(values);var previous=CREATING.get();CREATING.set(Boolean.TRUE);
        try { parameters=new Climate.ParameterList<>(copy); }
        finally { if(previous==null)CREATING.remove();else CREATING.set(previous); }
    }
    /** Used only by the native tree constructor; there is no query-wide foreign-tree scope. */
    public static boolean creating() { return CREATING.get()==Boolean.TRUE; }
    public Holder<Biome> select(Climate.TargetPoint point) { return parameters.findValue(point); }
}
