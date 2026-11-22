package design.inherent.skyweavecompat.encounters;

import design.inherent.skyweavecompat.configuration.RecordSettings;
import design.inherent.skyweavecompat.configuration.SettingSpec;
import design.inherent.skyweavecompat.configuration.SettingIssue;
import java.util.List;

/** Server-owned encounter policy. Rule slots name the six existing V&V mappings. */
public record EncounterProfile(
        @SettingSpec(minimum="0", maximum="100", unit="percent") double patrolCheckPercent,
        Spawners spawners) {
    public enum Mode { NATIVE_TYPE, PER_EMISSION }
    public record Rule(boolean enabled,
            @SettingSpec(minimum="0", maximum="1000") int priority,
            @SettingSpec(maximumEntries=32, maximumLength=128, unique=true, nonBlank=true,
                    pattern="[a-z0-9_.-]+:[a-z0-9_./-]+") List<String> biomeTags) {
        public Rule {
            RecordSettings.validate(Rule.class, enabled, priority, biomeTags);
            biomeTags = List.copyOf(biomeTags);
            if (enabled && biomeTags.isEmpty()) throw new IllegalArgumentException("Enabled rule needs biome tags");
        }
    }
    public record Rules(Rule husk, Rule gelid, Rule thicket, Rule stray, Rule bogged, Rule verdant) {
        public Rules {
            if (husk==null || gelid==null || thicket==null || stray==null || bogged==null || verdant==null)
                throw new IllegalArgumentException("Missing spawner rule");
            unique(List.of("husk", "gelid", "thicket"), List.of(husk,gelid,thicket));
            unique(List.of("stray", "bogged", "verdant"), List.of(stray,bogged,verdant));
        }
        private static void unique(List<String> names, List<Rule> rules) {
            for (int i = 0; i < rules.size(); i++) for (int j = 0; j < i; j++)
                if (rules.get(i).enabled() && rules.get(j).enabled() && rules.get(i).priority() == rules.get(j).priority())
                    throw SettingIssue.invalid("encounter_priority_collision", "spawners.rules." + names.get(i) + ".priority",
                            "Enabled " + names.get(i) + " and " + names.get(j) + " rules need distinct priorities",
                            "spawners.rules." + names.get(j) + ".priority");
        }
    }
    public record Spawners(Mode mode,
            @SettingSpec(minimum="0", maximum="100", unit="percent") double replacementPercent,
            Rules rules) {
        public Spawners {
            RecordSettings.validate(Spawners.class, mode, replacementPercent, rules);
            if (rules==null) throw new IllegalArgumentException("Missing spawner rules");
        }
    }
    private static Rule rule(String name,int priority) {
        return new Rule(true,priority,List.of("variantsandventures:has_"+name));
    }
    public static final EncounterProfile DEFAULT = new EncounterProfile(20,
            new Spawners(Mode.NATIVE_TYPE,5,new Rules(rule("husk",10),rule("gelid",20),rule("thicket",30),
                    rule("stray",10),rule("bogged",20),rule("verdant",30))));
    public EncounterProfile {
        RecordSettings.validate(EncounterProfile.class,patrolCheckPercent,spawners);
        if(spawners==null)throw new IllegalArgumentException("Missing spawner settings");
    }
}
