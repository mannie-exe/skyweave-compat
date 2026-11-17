package design.inherent.skyweavecompat.encounters;

import com.faboslav.variantsandventures.common.VariantsAndVentures;

/** Resolved only with the exact optional V&V adaptation active. Never changes its config. */
public final class VariantsPolicy {
    public static void verify(){
        if(EncounterConfiguration.profile().spawners().mode()!=EncounterProfile.Mode.PER_EMISSION)return;
        var c=VariantsAndVentures.getConfig();
        if(c.huskSpawnerChance!=0||c.gelidSpawnerChance!=0||c.thicketSpawnerChance!=0
                ||c.straySpawnerChance!=0||c.boggedSpawnerChance!=0||c.verdantSpawnerChance!=0)
            throw new IllegalStateException("PER_EMISSION requires all six V&V *SpawnerChance values to be 0 in config/variantsandventures.json. "
                    +"Review those managed rules or select NATIVE_TYPE in config/skyweave/"+EncounterConfiguration.FILE
                    +"; whole-spawner conversion and per-emission replacement cannot be combined.");
    }
    public static boolean enabled(SpawnerSelection.Variant variant){
        var c=VariantsAndVentures.getConfig();
        return switch(variant){
            case HUSK->c.enableHuskSpawners;
            case GELID->c.enableGelid&&c.enableGelidSpawners;
            case THICKET->c.enableThicket&&c.enableThicketSpawners;
            case STRAY->c.enableStraySpawners;
            case BOGGED->c.enableBoggedSpawners;
            case VERDANT->c.enableVerdant&&c.enableVerdantSpawners;
        };
    }
    private VariantsPolicy(){}
}
