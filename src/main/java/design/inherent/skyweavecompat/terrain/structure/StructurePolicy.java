package design.inherent.skyweavecompat.terrain.structure;

import design.inherent.skyweavecompat.configuration.SettingSpec;
import design.inherent.skyweavecompat.configuration.RecordSettings;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/** Rates describe eligible terrain area, not the surrounding void or registered variant count. */
public record StructurePolicy(
        @SettingSpec(minimum="0", maximum="8", unit="factor", step=".1") double frequencyMultiplier,
        @SettingSpec(minimum="0", maximum="64", unit="chunks") int outpostVillageSeparationChunks,
        Map<String, Rule> families,
        VillageFitSettings villageFitting) {
    public StructurePolicy(double frequencyMultiplier, int outpostVillageSeparationChunks, Map<String, Rule> families) {
        this(frequencyMultiplier, outpostVillageSeparationChunks, families, VillageFitSettings.DEFAULT);
    }
    public record Rule(
            @SettingSpec(minimum="0", maximum="16", unit="starts_per_1024_chunks", step=".01") double targetStartsPer1024Chunks,
            @SettingSpec(minimum="0", maximum="16", unit="per_cluster") int maximumPerCluster) {
        public Rule {
            RecordSettings.validate(Rule.class, targetStartsPer1024Chunks, maximumPerCluster);
        }
    }

    public static final StructurePolicy DEFAULT = defaults();

    public StructurePolicy {
        RecordSettings.validate(StructurePolicy.class, frequencyMultiplier, outpostVillageSeparationChunks, families, villageFitting);
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
