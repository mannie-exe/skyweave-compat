package design.inherent.skyweavecompat.client;

import java.util.EnumSet;

/** At most one diagnostic per reason/resource generation, with no retained world or model. */
public final class TransparencyDiagnostics {
    public enum Reason { MODEL_DATA_UNAVAILABLE, UNSUPPORTED_PROVIDER, FACE_BUDGET, NONPLANAR_FACE, PARTITION_BUDGET, THIN_SURFACE }
    private static final EnumSet<Reason> REPORTED = EnumSet.noneOf(Reason.class);
    private TransparencyDiagnostics() {}
    public static synchronized void reset() { REPORTED.clear(); }
    public static synchronized void fallback(Reason reason) {
        if (REPORTED.add(reason)) com.mojang.logging.LogUtils.getLogger().debug(
                "Transparency retained bounded fallback: {}", reason);
    }
    public static synchronized java.util.Set<Reason> reported() { return java.util.Set.copyOf(REPORTED); }
}
