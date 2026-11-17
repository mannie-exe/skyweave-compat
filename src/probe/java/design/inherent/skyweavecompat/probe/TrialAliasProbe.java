package design.inherent.skyweavecompat.probe;

import com.faboslav.variantsandventures.common.VariantsAndVentures;
import com.faboslav.variantsandventures.common.init.VariantsAndVenturesStructurePoolAliases;
import com.faboslav.variantsandventures.common.mixin.JigsawStructureAccessor;
import com.google.gson.JsonObject;
import design.inherent.skyweavecompat.encounters.TrialAliases;
import dev.worldgen.lithostitched.worldgen.poolalias.RandomEntries;
import java.util.*;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.structure.pools.alias.PoolAliasBinding;

/** Read-only pre-extension witness plus controlled, restored native updater invocations. */
public final class TrialAliasProbe {
    private static List<PoolAliasBinding> before;
    private static boolean beforeLevels;
    public static void capture(MinecraftServer server) {
        if(before!=null)return;
        var structure=server.registryAccess().registryOrThrow(Registries.STRUCTURE).get(EncounterProbe.id("minecraft:trial_chambers"));
        if(structure instanceof JigsawStructureAccessor access){before=List.copyOf(access.getPoolAliasBindings());beforeLevels=!server.getAllLevels().iterator().hasNext();}
    }
    static JsonObject run(MinecraftServer server) throws Exception {
        EncounterProbe.check(before!=null,"No native pre-extension alias witness");
        EncounterProbe.check(beforeLevels,"Trial correction ran after level creation");
        int comparisons=0,changed=0,negatives=0;
        for(var original:before) {
            var adapted=TrialAliases.normalize(original);
            if(original==adapted)continue;changed++;
            var entries=(RandomEntries)original;
            for(int seed=0;seed<64;seed++) {
                var a=RandomSource.create(seed);var b=RandomSource.create(seed);var first=new ArrayList<String>();var second=new ArrayList<String>();
                original.forEachResolved(a,(from,to)->first.add(from.location()+"->"+to.location()));
                adapted.forEachResolved(b,(from,to)->second.add(from.location()+"->"+to.location()));
                EncounterProbe.check(first.equals(second)&&a.nextLong()==b.nextLong(),"Alias normalization changed native selection/RNG");comparisons++;
            }
            EncounterProbe.check(TrialAliases.normalize(adapted)==adapted,"Alias normalization is not idempotent");
            var empty=new RandomEntries(entries.aliases(),Collections.nCopies(entries.aliases().size(),HolderSet.direct(List.of())));
            EncounterProbe.check(TrialAliases.normalize(empty)==empty,"Empty alias rows gained selection");negatives++;
            var large=HolderSet.direct(Collections.nCopies(257,entries.pools().getFirst().get(0)));
            var over=new RandomEntries(entries.aliases(),Collections.nCopies(entries.aliases().size(),large));
            EncounterProbe.check(TrialAliases.normalize(over)==over,"Over-budget alias rows gained partial selection");negatives++;
            var inline=HolderSet.direct(net.minecraft.core.Holder.direct(entries.pools().getFirst().get(0).value()));
            var unregistered=new RandomEntries(entries.aliases(),Collections.nCopies(entries.aliases().size(),inline));
            EncounterProbe.check(TrialAliases.normalize(unregistered)==unregistered,"Inline pool gained partial selection");negatives++;
            var unrelated=new RandomEntries(List.of(net.minecraft.resources.ResourceKey.create(Registries.TEMPLATE_POOL,EncounterProbe.id("foreign:unrelated"))),List.of(entries.pools().getFirst()));
            EncounterProbe.check(TrialAliases.normalize(unrelated)==unrelated,"Unrelated alias slot changed");negatives++;
            if(entries.aliases().size()==2) {
                var unequal=new RandomEntries(entries.aliases(),List.of(entries.pools().getFirst(),HolderSet.direct(List.of(entries.pools().get(1).get(0)))));
                EncounterProbe.check(TrialAliases.normalize(unequal)==unequal,"Unequal paired rows were partially accepted");negatives++;
            }
        }
        EncounterProbe.check(changed==2,"Expected exactly melee and paired-ranged normalization");
        var structure=server.registryAccess().registryOrThrow(Registries.STRUCTURE).get(EncounterProbe.id("minecraft:trial_chambers"));
        var access=(JigsawStructureAccessor)structure;var config=VariantsAndVentures.getConfig();
        var expanded=access.getPoolAliasBindings();TrialAliases.prepare(server);
        EncounterProbe.check(access.getPoolAliasBindings()==expanded,"Repeated reconciliation duplicated native additions");
        var names=List.of("enableGelidSpawnersInTrialChambers","enableThicketSpawnersInTrialChambers","enableMurkSpawnersInTrialChambers","enableVerdantSpawnersInTrialChambers");
        var values=new ArrayList<Boolean>();for(var name:names)values.add(config.getClass().getField(name).getBoolean(config));
        try {
            for(var name:names)config.getClass().getField(name).setBoolean(config,false);
            access.setPoolAliasBindings(before);TrialAliases.prepare(server);
            for(int i=0;i<before.size();i++) {
                var normalized=TrialAliases.normalize(before.get(i));if(normalized==before.get(i))continue;
                EncounterProbe.check(access.getPoolAliasBindings().get(i).allTargets().count()==normalized.allTargets().count(),"Native trial false switches were bypassed");
            }
        } finally {
            for(int i=0;i<names.size();i++)config.getClass().getField(names.get(i)).setBoolean(config,values.get(i));
            access.setPoolAliasBindings(before);TrialAliases.prepare(server);
        }
        EncounterProbe.check(access.getPoolAliasBindings().get(0).allTargets().count()==10&&access.getPoolAliasBindings().get(1).allTargets().count()==5,"Native five-choice trial families not restored");
        EncounterProbe.check(access.getPoolAliasBindings().get(2)==before.get(2),"Small-melee alias behavior changed");
        var result=new JsonObject();result.addProperty("nativeRngComparisons",comparisons);result.addProperty("refusalControls",negatives);result.addProperty("foreignTrialFlagsPreserved",true);result.addProperty("smallMeleeUnchanged",true);result.addProperty("beforeLevelCreation",beforeLevels);return result;
    }
}
