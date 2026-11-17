package design.inherent.skyweavecompat.encounters;

import java.util.function.Predicate;

/** Select one eligible mapping before one probability roll; overlapping tags never multiply chances. */
public final class SpawnerSelection {
    public enum Variant {
        HUSK("minecraft:zombie","minecraft:husk"), GELID("minecraft:zombie","variantsandventures:gelid"),
        THICKET("minecraft:zombie","variantsandventures:thicket"), STRAY("minecraft:skeleton","minecraft:stray"),
        BOGGED("minecraft:skeleton","minecraft:bogged"), VERDANT("minecraft:skeleton","variantsandventures:verdant");
        public final String source,target;
        Variant(String source,String target){this.source=source;this.target=target;}
        public EncounterProfile.Rule rule(EncounterProfile.Rules rules){
            return switch(this){case HUSK->rules.husk();case GELID->rules.gelid();case THICKET->rules.thicket();
                case STRAY->rules.stray();case BOGGED->rules.bogged();case VERDANT->rules.verdant();};
        }
    }
    public static boolean base(String id){return id.equals("minecraft:zombie")||id.equals("minecraft:skeleton");}
    public static boolean sameFamily(String base,String id){
        if(base.equals(id))return true;
        for(var variant:Variant.values())if(variant.source.equals(base)&&variant.target.equals(id))return true;
        return false;
    }
    public static Variant select(EncounterProfile.Rules rules,String source,Predicate<Variant> enabled,Predicate<String> biomeTag){
        Variant selected=null;int priority=Integer.MAX_VALUE;
        for(var variant:Variant.values()){
            var rule=variant.rule(rules);
            if(variant.source.equals(source)&&rule.enabled()&&enabled.test(variant)&&rule.priority()<priority
                    &&rule.biomeTags().stream().anyMatch(biomeTag)){
                selected=variant;priority=rule.priority();
            }
        }
        return selected;
    }
    private SpawnerSelection(){}
}
