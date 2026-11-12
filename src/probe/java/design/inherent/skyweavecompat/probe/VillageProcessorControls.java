package design.inherent.skyweavecompat.probe;

import design.inherent.skyweavecompat.terrain.structure.VillageProcessors;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.world.level.levelgen.structure.templatesystem.*;

/** Native graph inputs only; no processor execution or world writes. */
public final class VillageProcessorControls {
    private static void check(boolean valid,String message) {if(!valid)throw new AssertionError(message);}
    public static int run(RegistryAccess registries) throws ReflectiveOperationException {
        var nop=NopProcessor.INSTANCE;int count=0;
        check(VillageProcessors.qualify(List.of(nop),registries)==null,"Native leaf refused");count++;
        check("processor_list_budget".equals(VillageProcessors.qualify(Collections.nCopies(257,nop),registries)),"Native root-list budget ignored");count++;
        if(!design.inherent.skyweavecompat.OptionalIntegrations.loaded("lithostitched"))return count;
        var constructor=Class.forName("dev.worldgen.lithostitched.impl.worldgen.processor.ReferenceStructureProcessor").getConstructor(HolderSet.class);
        java.util.function.Function<List<StructureProcessor>,StructureProcessor> reference=children->{
            try {return (StructureProcessor)constructor.newInstance(HolderSet.direct(Holder.direct(new StructureProcessorList(children))));}
            catch(ReflectiveOperationException failure) {throw new IllegalStateException(failure);}
        };
        check(VillageProcessors.qualify(List.of(reference.apply(List.of(nop))),registries)==null,"Known reference graph refused");count++;
        var setBlock=StructureProcessorType.SINGLE_CODEC.parse(net.minecraft.resources.RegistryOps.create(
                com.mojang.serialization.JsonOps.INSTANCE,registries),com.google.gson.JsonParser.parseString(
                "{\"processor_type\":\"lithostitched:set_block\",\"state_provider\":{\"type\":\"minecraft:simple_state_provider\",\"state\":{\"Name\":\"minecraft:stone\"}}}" )).getOrThrow();
        check(setBlock.getClass().getName().equals("dev.worldgen.lithostitched.impl.worldgen.processor.SetBlockProcessor")
                && VillageProcessors.qualify(List.of(reference.apply(List.of(setBlock))),registries)==null,
                "Actual Lithostitched set-block leaf was refused under a native reference");count++;
        var unknown=new StructureProcessor() {
            @Override protected StructureProcessorType<?> getType() {return StructureProcessorType.NOP;}
        };
        check(VillageProcessors.qualify(List.of(reference.apply(List.of(unknown))),registries).startsWith("processor:"),"Nested unknown processor passed a qualified outer wrapper");count++;
        var cycle=new ArrayList<StructureProcessor>();var recursive=reference.apply(cycle);cycle.add(recursive);
        check("processor_cycle".equals(VillageProcessors.qualify(List.of(recursive),registries)),"Reference cycle was not refused");count++;
        var deep=(StructureProcessor)nop;for(int i=0;i<18;i++)deep=reference.apply(List.of(deep));
        check("processor_graph_budget".equals(VillageProcessors.qualify(List.of(deep),registries)),"Reference depth budget ignored");count++;
        var repeated=reference.apply(Collections.nCopies(64,nop));var wide=reference.apply(Collections.nCopies(64,repeated));
        check("processor_graph_budget".equals(VillageProcessors.qualify(List.of(wide),registries)),"Shared-child revisits received free budget credit");count++;
        return count;
    }
}
