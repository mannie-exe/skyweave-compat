package design.inherent.skyweavecompat.encounters;

import com.faboslav.variantsandventures.common.mixin.JigsawStructureAccessor;
import com.faboslav.variantsandventures.common.init.VariantsAndVenturesStructurePoolAliases;
import com.mojang.logging.LogUtils;
import dev.worldgen.lithostitched.worldgen.poolalias.RandomEntries;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.random.SimpleWeightedRandomList;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.pools.alias.PoolAliasBinding;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;

/** Normalize only V&V's two native extension slots; the original V&V updater owns additions/flags. */
public final class TrialAliases {
    private static final String PREFIX="trial_chambers/spawner/contents/";
    public static void prepare(MinecraftServer server) {
        var structure=server.registryAccess().registryOrThrow(Registries.STRUCTURE)
                .get(ResourceLocation.withDefaultNamespace("trial_chambers"));
        if(!(structure instanceof JigsawStructure))return;
        var access=(JigsawStructureAccessor)structure;
        var before=access.getPoolAliasBindings();
        var after=before.stream().map(TrialAliases::normalize).toList();
        int changed=0;for(int i=0;i<before.size();i++)if(before.get(i)!=after.get(i))changed++;
        if(changed==0)return;
        // Replaying V&V is safe only for its two fresh, unextended native slots.
        if(changed!=2||after.stream().flatMap(PoolAliasBinding::allTargets)
                .anyMatch(key->java.util.Set.of("gelid","thicket","murk","verdant").stream()
                        .anyMatch(name->key.location().getPath().endsWith("/"+name)))) {
            refused(before.getFirst(),"partial or already extended trial slots");return;
        }
        access.setPoolAliasBindings(after);
        try { VariantsAndVenturesStructurePoolAliases.init(server); }
        catch(RuntimeException|Error failure) {access.setPoolAliasBindings(before);throw failure;}
    }
    public static PoolAliasBinding normalize(PoolAliasBinding binding) {
        if(!(binding instanceof RandomEntries entries))return binding;
        var names=entries.aliases().stream().map(ResourceKey::location).toList();
        boolean melee=names.equals(List.of(ResourceLocation.withDefaultNamespace(PREFIX+"melee")));
        boolean ranged=names.size()==2&&names.contains(ResourceLocation.withDefaultNamespace(PREFIX+"ranged"))
                &&names.contains(ResourceLocation.withDefaultNamespace(PREFIX+"slow_ranged"));
        if(!melee&&!ranged)return binding;
        int columns=names.size();
        if(entries.pools().size()!=columns)return refused(binding,"alias/pool count");
        int rows=entries.pools().getFirst().size();
        if(rows<1||rows>256||entries.pools().stream().anyMatch(pool->pool.size()!=rows))
            return refused(binding,"empty, unequal or over-budget rows");
        var keys=new ArrayList<List<ResourceKey<StructureTemplatePool>>>(rows);
        for(int row=0;row<rows;row++) {
            var values=new ArrayList<ResourceKey<StructureTemplatePool>>(columns);
            for(int column=0;column<columns;column++) {
                var key=entries.pools().get(column).get(row).unwrapKey().orElse(null);
                if(key==null)return refused(binding,"inline unregistered pool");
                values.add(key);
            }
            keys.add(List.copyOf(values));
        }
        if(melee) {
            var targets=SimpleWeightedRandomList.<ResourceKey<StructureTemplatePool>>builder();
            keys.forEach(row->targets.add(row.getFirst(),1));
            return PoolAliasBinding.random(entries.aliases().getFirst(),targets.build());
        }
        var groups=SimpleWeightedRandomList.<List<PoolAliasBinding>>builder();
        for(var row:keys) {
            var pairs=new ArrayList<PoolAliasBinding>(columns);
            for(int column=0;column<columns;column++)pairs.add(PoolAliasBinding.direct(entries.aliases().get(column),row.get(column)));
            groups.add(List.copyOf(pairs),1);
        }
        return PoolAliasBinding.randomGroup(groups.build());
    }
    private static PoolAliasBinding refused(PoolAliasBinding binding,String reason) {
        LogUtils.getLogger().warn("Retaining unqualified V&V/Lithostitched trial alias: {}",reason);return binding;
    }
    private TrialAliases(){}
}
