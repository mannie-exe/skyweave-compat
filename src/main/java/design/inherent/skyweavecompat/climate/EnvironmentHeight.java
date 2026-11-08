package design.inherent.skyweavecompat.climate;

/** An atmospheric display height maps between build envelopes, independently of island geology. */
public final class EnvironmentHeight {
    private EnvironmentHeight() {}
    public static float cloudHeight(float vanilla, int minimumY, int height) {
        if (height <= 0) throw new IllegalArgumentException("Empty dimension envelope");
        return minimumY + (vanilla + 64) * (height / 384f);
    }
}
