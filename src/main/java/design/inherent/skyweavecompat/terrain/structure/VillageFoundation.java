package design.inherent.skyweavecompat.terrain.structure;

import design.inherent.skyweavecompat.terrain.IslandContext;
import design.inherent.skyweavecompat.terrain.NaturalMaterials;
import java.util.*;
import java.util.function.BiFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;

/** One bounded shallow-support decision, with separate planning and current-chunk realization. */
public final class VillageFoundation {
    public enum Outcome { CONTACT, SHALLOW_GAP, NATIVE_BASE, VOID, CAVITY, WATER, UNKNOWN, OCCUPIED,
        FOREIGN_SOURCE, CHANGED_FOOT, CHANGED_SOURCE, CHANGED_GAP, CONFLICT, AUTHORED_CLEAR }
    public record Fill(BlockPos position,BlockState original,BlockState prepared,BlockState target) {
        public Fill(BlockPos position,BlockState original,BlockState target) {this(position,original,original,target);}
        public Fill {
            position=position.immutable();Objects.requireNonNull(original);Objects.requireNonNull(prepared);
            if(!original.isAir() || !NaturalMaterials.skin(Objects.requireNonNull(target)) || !sturdy(target,Direction.UP))
                throw new IllegalArgumentException("Invalid shallow support cell");
            if(!prepared.isAir() && (!NaturalMaterials.skin(prepared) || !sturdy(prepared,Direction.UP)))
                throw new IllegalArgumentException("Invalid prepared support cell");
        }
    }
    public record Binding(VillageFootprint.Foot foot,Outcome outcome,BlockPos source,BlockState sourceState,
            IslandContext.Body owner,List<Fill> fills) {
        public Binding {
            Objects.requireNonNull(foot);Objects.requireNonNull(outcome);
            source=source==null?null:source.immutable();fills=List.copyOf(fills);
            if(outcome==Outcome.CONTACT || outcome==Outcome.SHALLOW_GAP) {
                if(source==null || sourceState==null || !sturdy(sourceState,Direction.UP)
                        || source.getX()!=foot.position().getX() || source.getZ()!=foot.position().getZ()
                        || (long)foot.position().getY()-source.getY()!=fills.size()+1 || fills.size()>8)
                    throw new IllegalArgumentException("Invalid structural source binding");
                if(outcome==Outcome.CONTACT && !fills.isEmpty() || outcome==Outcome.SHALLOW_GAP && (fills.isEmpty() || owner==null))
                    throw new IllegalArgumentException("Support outcome does not match its intent");
                for(int i=0;i<fills.size();i++)if(!fills.get(i).position().equals(source.above(i+1)))
                    throw new IllegalArgumentException("Support cells must form a bounded bottom-up column");
            } else if(source!=null || sourceState!=null || owner!=null || !fills.isEmpty())
                throw new IllegalArgumentException("Refused support retained write authority");
        }
        public boolean supported() { return outcome==Outcome.CONTACT || outcome==Outcome.SHALLOW_GAP; }
    }
    public record Plan(List<Binding> bindings) {
        public Plan {
            bindings=List.copyOf(bindings);
            if(bindings.size()>VillageFootprint.Budget.MAX_FEET
                    || bindings.stream().map(Binding::foot).distinct().count()!=bindings.size())
                throw new IllegalArgumentException("Invalid support plan cardinality");
        }
    }
    public record NativeOutput(String owner,int piece,BlockState state,boolean authoredClear) {
        public NativeOutput(String owner,int piece,BlockState state) {this(owner,piece,state,false);}
        public NativeOutput {Objects.requireNonNull(owner);Objects.requireNonNull(state);if(owner.isEmpty() || owner.length()>512
                || piece<0 || piece>=VillageFootprint.Budget.MAX_PIECES || authoredClear && !state.is(Blocks.AIR))
            throw new IllegalArgumentException("Invalid native output owner");}
    }
    public record Realization(Map<VillageFootprint.Foot,Outcome> outcomes,int writes,Map<VillageFootprint.Foot,BlockState> nativeSources,
            Map<VillageFootprint.Foot,BlockState> finalFeet) {
        public Realization { outcomes=Map.copyOf(outcomes);nativeSources=Map.copyOf(nativeSources);finalFeet=Map.copyOf(finalFeet); }
    }
    private record SourceWrite(BlockPos position,BlockState target) {}
    private VillageFoundation() {}
    private static boolean sturdy(BlockState state,Direction face) {
        return !state.hasBlockEntity() && !state.getBlock().hasDynamicShape() && state.getFluidState().isEmpty()
                && state.isFaceSturdy(EmptyBlockGetter.INSTANCE,BlockPos.ZERO,face);
    }
    private static Binding refused(VillageFootprint.Foot foot,Outcome reason) {
        return new Binding(foot,reason,null,null,null,List.of());
    }

    public static Plan plan(List<VillageFootprint.Foot> feet,StructureTerrainView terrain,
            BiFunction<Integer,Integer,IslandContext> contexts,int maximumGap) {
        if(maximumGap<0 || maximumGap>8 || feet.size()>VillageFootprint.Budget.MAX_FEET)
            throw new IllegalArgumentException("Invalid shallow-support work bounds");
        var bindings=new ArrayList<Binding>();
        for(var foot:feet) {
            var context=contexts.apply(foot.position().getX(),foot.position().getZ());
            if(context==null) { bindings.add(refused(foot,Outcome.UNKNOWN));continue; }
            try { bindings.add(column(foot,terrain,context,maximumGap)); }
            catch(StructureTerrainView.Unavailable unavailable) { bindings.add(refused(foot,Outcome.UNKNOWN)); }
        }
        return new Plan(bindings);
    }
    /** Keep the original natural anchor/palette; own grading may repaint that anchor, not replace it with a new roof. */
    public static Plan afterGrading(Plan natural,StructureTerrainView graded) {
        var bindings=new ArrayList<Binding>();
        for(var binding:natural.bindings()) {
            if(!binding.supported()) {bindings.add(binding);continue;}
            var state=graded.state(binding.source());
            var fills=binding.fills().stream().map(fill->new Fill(fill.position(),fill.original(),graded.state(fill.position()),fill.target())).toList();
            bindings.add(sturdy(state,Direction.UP)?new Binding(binding.foot(),binding.outcome(),binding.source(),state,binding.owner(),fills)
                    :refused(binding.foot(),Outcome.CHANGED_SOURCE));
        }
        return new Plan(bindings);
    }
    /** Only the actual same-start writer's compiled foot can replace an overlapping foot requirement. */
    public static Plan finalNativeFeet(Plan plan,Map<BlockPos,NativeOutput> outputs,java.util.function.IntFunction<String> owners) {
        record Address(int piece,BlockPos position) {}
        var feet=new HashMap<Address,VillageFootprint.Foot>();
        for(var binding:plan.bindings()) {
            var foot=binding.foot();var old=feet.putIfAbsent(new Address(foot.piece(),foot.position()),foot);
            if(old!=null && !old.equals(foot))throw new IllegalArgumentException("Conflicting native footprint identity");
        }
        var result=new ArrayList<Binding>();
        for(var binding:plan.bindings()) {
            var foot=binding.foot();var output=outputs.get(foot.position());
            if(output!=null && output.authoredClear() && output.piece()!=foot.piece()
                    && output.owner().equals(owners.apply(foot.piece())) && output.owner().equals(owners.apply(output.piece()))) {
                // A qualified later template deliberately removed this earlier requirement.
                // This disposition grants neither physical support credit nor fill authority.
                result.add(refused(foot,Outcome.AUTHORED_CLEAR));continue;
            }
            var winner=output==null?null:feet.get(new Address(output.piece(),foot.position()));
            if(binding.supported() && winner!=null && output.owner().equals(owners.apply(foot.piece()))
                    && output.owner().equals(owners.apply(winner.piece())) && winner.expected().equals(output.state())
                    && !foot.expected().equals(output.state())) {
                foot=new VillageFootprint.Foot(foot.template(),foot.piece(),foot.position(),foot.original(),output.state());
                binding=new Binding(foot,binding.outcome(),binding.source(),binding.sourceState(),binding.owner(),binding.fills());
            }
            result.add(binding);
        }
        return new Plan(result);
    }
    private static Binding column(VillageFootprint.Foot foot,StructureTerrainView terrain,IslandContext context,int maximumGap) {
        var gap=new ArrayList<BlockPos>();
        for(int depth=0;depth<=maximumGap;depth++) {
            var at=foot.position().below(depth+1);var state=terrain.state(at);
            if(!state.getFluidState().isEmpty())return refused(foot,Outcome.WATER);
            if(sturdy(state,Direction.UP)) {
                if(gap.isEmpty())return new Binding(foot,Outcome.CONTACT,at,state,context.solidOwner(at.getY()),List.of());
                var owner=context.solidOwner(at.getY());
                if(owner==null)return refused(foot,Outcome.UNKNOWN);
                // Ground villages may extend a natural roof. A cavity floor or a cut inside
                // another layer must not become a pillar merely because a solid block is nearby.
                var envelope=VillageSupportEnvelope.gap(context,at.getY(),foot.position().getY());
                switch(envelope) {
                    case WATER -> {return refused(foot,Outcome.WATER);}
                    case CAVITY -> {return refused(foot,Outcome.CAVITY);}
                    case OCCUPIED -> {return refused(foot,Outcome.OCCUPIED);}
                    case UNKNOWN -> {return refused(foot,Outcome.UNKNOWN);}
                    default -> {}
                }
                // Native cleanup can lower the actual captured cap below its intrinsic
                // envelope. Prove an open exterior through that owner's entire remaining
                // cap, rather than treating nominal rock as a cavity or filling under a roof.
                boolean trimmed=envelope==VillageSupportEnvelope.Gap.CAP && openCapturedCap(at,foot.position(),owner,terrain,context);
                if(envelope==VillageSupportEnvelope.Gap.CAP && !trimmed)return refused(foot,Outcome.UNKNOWN);
                if(!NaturalMaterials.skin(state))return refused(foot,Outcome.FOREIGN_SOURCE);
                var palette=new ArrayList<BlockState>();palette.add(state);
                for(int d=1;d<=2;d++) {
                    var below=at.below(d);var layer=terrain.state(below);
                    palette.add(context.naturalAt(owner,below.getY())==IslandContext.Occupancy.SOLID && NaturalMaterials.skin(layer)
                            && sturdy(layer,Direction.UP)?layer:palette.getLast());
                }
                var fills=new ArrayList<Fill>();
                for(int i=gap.size()-1;i>=0;i--) {
                    var target=gap.get(i);
                    int belowNewRoof=foot.position().getY()-1-target.getY();
                    fills.add(new Fill(target,terrain.state(target),palette.get(Math.min(2,belowNewRoof))));
                }
                return new Binding(foot,Outcome.SHALLOW_GAP,at,state,owner,fills);
            }
            if(!state.isAir())return refused(foot,Outcome.OCCUPIED);
            gap.add(at);
        }
        return refused(foot,Outcome.VOID);
    }
    private static boolean openCapturedCap(BlockPos source,BlockPos foot,IslandContext.Body owner,StructureTerrainView terrain,IslandContext context) {
        if((long)owner.exterior().topY()-source.getY()>1024)return false;
        for(int y=source.getY()+1;y<=owner.exterior().topY();y++) {
            var state=terrain.state(source.atY(y));
            // The native template replaces this exact footing cell. It may be the
            // retained one-block cap above a classic-carved shallow gap, not AIR.
            if(!state.isAir() && !(y==foot.getY() && NaturalMaterials.skin(state) && sturdy(state,Direction.UP)))return false;
            for(var contribution:context.contributions(y))
                if(!contribution.body().equals(owner) || contribution.occupancy()!=IslandContext.Occupancy.SOLID)return false;
        }
        return true;
    }

    public static Realization realize(Plan plan,ChunkAccess chunk) {
        return realize(plan,chunk,Map.of(),index->null);
    }
    public static Realization realize(Plan plan,ChunkAccess chunk,Map<BlockPos,NativeOutput> nativeOutputs,java.util.function.IntFunction<String> owners) {
        var outcomes=new LinkedHashMap<VillageFootprint.Foot,Outcome>();
        var nativeSources=new LinkedHashMap<VillageFootprint.Foot,BlockState>();
        var sourceChanges=new HashMap<VillageFootprint.Foot,SourceWrite>();
        var feetByPosition=new HashMap<BlockPos,List<VillageFootprint.Foot>>();
        for(var binding:plan.bindings())feetByPosition.computeIfAbsent(binding.foot().position(),key->new ArrayList<>()).add(binding.foot());
        var candidates=new ArrayList<Binding>();var writes=new TreeMap<BlockPos,Fill>();
        for(var binding:plan.bindings()) {
            var foot=binding.foot();if(!new ChunkPos(foot.position()).equals(chunk.getPos()))continue;
            var reason=binding.outcome();
            if(reason==Outcome.AUTHORED_CLEAR && !chunk.getBlockState(foot.position()).is(Blocks.AIR))reason=Outcome.CHANGED_FOOT;
            if(binding.supported()) {
                if(!chunk.getBlockState(foot.position()).equals(foot.expected()))reason=Outcome.CHANGED_FOOT;
                else if(!chunk.getBlockState(binding.source()).equals(binding.sourceState())) {
                    var current=chunk.getBlockState(binding.source());var written=nativeOutputs.get(binding.source());
                    boolean own=written!=null && written.owner().equals(owners.apply(foot.piece())) && current.equals(written.state());
                    if(own && sturdy(current,Direction.UP)) {
                        reason=Outcome.NATIVE_BASE;nativeSources.put(foot,current);
                    } else if(own && current.is(Blocks.DIRT_PATH)
                            && (binding.fills().isEmpty()?foot.expected():binding.fills().getFirst().target()).isSolid()
                            && feetByPosition.getOrDefault(binding.source(),List.of()).stream().allMatch(other->
                                    other.expected().is(Blocks.DIRT_PATH) && written.owner().equals(owners.apply(other.piece())))) {
                        // Native paths turn to dirt when covered by a solid block. Finish
                        // that exact shape consequence together with the covering support,
                        // instead of crediting a 15/16-height path as a sturdy source.
                        var dirt=Blocks.DIRT.defaultBlockState();sourceChanges.put(foot,new SourceWrite(binding.source(),dirt));
                        if(!dirt.equals(binding.sourceState())) {reason=Outcome.NATIVE_BASE;nativeSources.put(foot,dirt);}
                    } else reason=Outcome.CHANGED_SOURCE;
                } else if(!sturdy(chunk.getBlockState(binding.source()),Direction.UP))reason=Outcome.CHANGED_SOURCE;
                if(successful(reason))for(var fill:binding.fills()) {
                    if(!new ChunkPos(fill.position()).equals(chunk.getPos()))throw new IllegalStateException("Cross-chunk support write");
                    var current=chunk.getBlockState(fill.position());
                    if(!current.equals(fill.prepared()) && !current.equals(fill.target())
                            && !NaturalMaterials.clearableDecoration(current)) { reason=Outcome.CHANGED_GAP;break; }
                }
            }
            outcomes.put(foot,reason);
            if(successful(reason))candidates.add(binding);else {nativeSources.remove(foot);sourceChanges.remove(foot);}
        }
        // Validate all co-owners before making any physical write.
        var conflicts=new HashSet<BlockPos>();
        for(var binding:candidates)for(var fill:binding.fills()) {
            var old=writes.putIfAbsent(fill.position(),fill);
            if(old!=null && !old.equals(fill))conflicts.add(fill.position());
        }
        var bases=new TreeMap<BlockPos,BlockState>();
        for(var change:sourceChanges.values()) {
            var old=bases.putIfAbsent(change.position(),change.target());var fill=writes.get(change.position());
            if(old!=null && !old.equals(change.target()) || fill!=null && !fill.target().equals(change.target()))conflicts.add(change.position());
        }
        for(var binding:candidates)if(binding.fills().stream().anyMatch(fill->conflicts.contains(fill.position()))
                || sourceChanges.containsKey(binding.foot()) && conflicts.contains(sourceChanges.get(binding.foot()).position()))
            outcomes.put(binding.foot(),Outcome.CONFLICT);
        writes.clear();bases.clear();
        for(var binding:candidates)if(outcomes.get(binding.foot())!=Outcome.CONFLICT) {
            for(var fill:binding.fills())writes.putIfAbsent(fill.position(),fill);
            var change=sourceChanges.get(binding.foot());if(change!=null)bases.put(change.position(),change.target());
        }
        int changed=0;
        var finalFeet=new HashMap<VillageFootprint.Foot,BlockState>();
        for(var binding:candidates)if(successful(outcomes.get(binding.foot())) && bases.containsKey(binding.foot().position()))
            finalFeet.put(binding.foot(),bases.get(binding.foot().position()));
        for(var base:bases.entrySet())if(!chunk.getBlockState(base.getKey()).equals(base.getValue())) {
            chunk.setBlockState(base.getKey(),base.getValue(),false);changed++;
        }
        for(var fill:writes.values())if(!chunk.getBlockState(fill.position()).equals(fill.target())) {
            var previous=chunk.getBlockState(fill.position());
            chunk.setBlockState(fill.position(),fill.target(),false);changed++;
            if(!previous.isAir() && NaturalMaterials.clearableDecoration(previous) && fill.position().getY()+1<chunk.getMaxBuildHeight()
                    && chunk.getBlockState(fill.position().above()).is(previous.getBlock()))
                chunk.markPosForPostprocessing(fill.position().above());
        }
        if(!writes.isEmpty() || !bases.isEmpty()) {
            StructureHeights.refresh(chunk);chunk.setUnsaved(true);
        }
        nativeSources.keySet().removeIf(foot->!successful(outcomes.get(foot)));
        return new Realization(outcomes,changed,nativeSources,finalFeet);
    }
    public static boolean successful(Outcome outcome) {return outcome==Outcome.CONTACT || outcome==Outcome.SHALLOW_GAP || outcome==Outcome.NATIVE_BASE;}
}
