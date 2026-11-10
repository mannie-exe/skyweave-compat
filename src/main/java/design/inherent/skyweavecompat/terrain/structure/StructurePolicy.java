package design.inherent.skyweavecompat.terrain.structure;

import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/** Rates describe eligible terrain area, not the surrounding void or registered variant count. */
public record StructurePolicy(double frequencyMultiplier, int outpostVillageSeparationChunks,
        Map<String, Rule> families, VillageFitSettings villageFitting) {
    public StructurePolicy(double frequencyMultiplier, int outpostVillageSeparationChunks, Map<String, Rule> families) {
        this(frequencyMultiplier, outpostVillageSeparationChunks, families, VillageFitSettings.DEFAULT);
    }
    public record Rule(double targetStartsPer1024Chunks, int maximumPerCluster) {
        public Rule {
            if (!Double.isFinite(targetStartsPer1024Chunks) || targetStartsPer1024Chunks < 0 || targetStartsPer1024Chunks > 16
                    || maximumPerCluster < 0 || maximumPerCluster > 16)
                throw new IllegalArgumentException("Structure rate must be 0..16 and maximum count 0..16");
        }
    }

    public static final StructurePolicy DEFAULT = defaults();

    public StructurePolicy {
        if (!Double.isFinite(frequencyMultiplier) || frequencyMultiplier < 0 || frequencyMultiplier > 8
                || outpostVillageSeparationChunks < 0 || outpostVillageSeparationChunks > 64)
            throw new IllegalArgumentException("Structure frequency must be 0..8 and outpost separation 0..64 chunks");
        var keys = Arrays.stream(StructureFamily.values()).map(family -> family.key).collect(Collectors.toSet());
        if (families == null || !families.keySet().equals(keys) || families.values().stream().anyMatch(java.util.Objects::isNull))
            throw new IllegalArgumentException("Expected exactly these structure families: " + new java.util.TreeSet<>(keys));
        families = java.util.Collections.unmodifiableMap(new TreeMap<>(families));
        java.util.Objects.requireNonNull(villageFitting, "Village fitting settings");
    }

    public Rule rule(StructureFamily family) { return families.get(family.key); }

    private static StructurePolicy defaults() {
        var rules = new TreeMap<String, Rule>();
        for (var family : StructureFamily.values()) rules.put(family.key, new Rule(family.defaultRate, family.defaultMaximum));
        return new StructurePolicy(1, 10, rules);
    }
}
