package design.inherent.skyweavecompat.terrain.structure;

import java.lang.reflect.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.*;

/** Qualify the complete pinned per-block processor graph before previewing any output. */
public final class VillageProcessors {
    private static final String LITHO="dev.worldgen.lithostitched.impl.worldgen.processor.";
    private static final String CTOV="net.choicetheorem.ctov.worldgen.processor.ModularCompatProcessor";
    private static final Set<String> LEAVES=Set.of(BlockIgnoreProcessor.class.getName(),JigsawReplacementProcessor.class.getName(),
            RuleProcessor.class.getName(),GravityProcessor.class.getName(),NopProcessor.class.getName(),
            LITHO+"BlockSwapStructureProcessor",LITHO+"SetBlockProcessor");
    private static final int MAX_DEPTH=16,MAX_VISITS=4096,MAX_LIST=256;
    private final RegistryAccess registries;
    private final Set<StructureProcessor> active=Collections.newSetFromMap(new IdentityHashMap<>());
    private int visits;
    private VillageProcessors(RegistryAccess registries) { this.registries=registries; }
    public static String qualify(List<StructureProcessor> processors,RegistryAccess registries) {
        try {new VillageProcessors(registries).list(processors,0);return null;}
        catch(Unsupported unsupported) {return unsupported.getMessage();}
        catch(ReflectiveOperationException|RuntimeException changed) {return "processor_api:"+changed.getClass().getSimpleName();}
    }
    private static final class Unsupported extends RuntimeException {
        Unsupported(String reason) {super(reason);}
    }
    private void list(List<StructureProcessor> processors,int depth) throws ReflectiveOperationException {
        if(processors.size()>MAX_LIST)throw new Unsupported("processor_list_budget");
        for(var processor:processors)visit(processor,depth);
    }
    private void holders(Iterable<?> holders,int depth) throws ReflectiveOperationException {
        int count=0;
        for(var value:holders) {
            if(++count>MAX_LIST)throw new Unsupported("processor_choice_budget");
            var holder=(Holder<?>)value;var list=(StructureProcessorList)holder.value();list(list.list(),depth);
        }
    }
    @SuppressWarnings("unchecked")
    private void visit(StructureProcessor processor,int depth) throws ReflectiveOperationException {
        if(depth>MAX_DEPTH || ++visits>MAX_VISITS)throw new Unsupported("processor_graph_budget");
        if(!active.add(processor))throw new Unsupported("processor_cycle");
        try {
            var type=processor.getClass();String name=type.getName();
            var finalizeMethod=type.getMethod("finalizeProcessing",ServerLevelAccessor.class,BlockPos.class,BlockPos.class,List.class,List.class,StructurePlaceSettings.class);
            if(finalizeMethod.getDeclaringClass()!=StructureProcessor.class)throw new Unsupported("list_finalizer");
            if(LEAVES.contains(name)) {
                if(type==RuleProcessor.class && ((List<?>)field(processor,"rules")).size()>MAX_LIST)throw new Unsupported("rule_budget");
                return;
            }
            switch(name) {
                case LITHO+"ConditionProcessor" -> {
                    list((List<StructureProcessor>)field(processor,"thenRun"),depth+1);
                    list((List<StructureProcessor>)field(processor,"elseRun"),depth+1);
                }
                case LITHO+"ReferenceStructureProcessor" -> holders((HolderSet<?>)type.getMethod("processorLists").invoke(processor),depth+1);
                case LITHO+"ApplyRandomStructureProcessor" -> {
                    var choices=type.getMethod("processorLists").invoke(processor);
                    var weighted=((java.util.function.Supplier<?>)field(choices,"weightedList")).get();
                    var entries=(List<?>)weighted.getClass().getMethod("unwrap").invoke(weighted);
                    if(entries.size()>MAX_LIST)throw new Unsupported("processor_choice_budget");
                    var holders=new ArrayList<Holder<?>>();
                    for(var entry:entries)holders.add((Holder<?>)entry.getClass().getMethod("value").invoke(entry));
                    holders(holders,depth+1);
                }
                case CTOV -> {
                    var id=(ResourceLocation)field(processor,"compatProcessorListId");
                    var referenced=registries.registryOrThrow(Registries.PROCESSOR_LIST).get(id);
                    // An absent optional list has no children. All present alternatives are
                    // qualified even when the corresponding content mod is currently absent.
                    if(referenced!=null)list(referenced.list(),depth+1);
                }
                default -> throw new Unsupported("processor:"+name);
            }
        } finally {active.remove(processor);}
    }
    private static Object field(Object value,String name) throws ReflectiveOperationException {
        var field=value.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(value);
    }
}
