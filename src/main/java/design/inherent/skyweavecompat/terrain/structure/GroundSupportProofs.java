package design.inherent.skyweavecompat.terrain.structure;

import design.inherent.skyweavecompat.SkyweaveCompat;
import design.inherent.skyweavecompat.terrain.GenerationGuard;
import java.util.*;
import java.util.function.Supplier;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.*;
import net.neoforged.neoforge.registries.*;

/** Per-foot evidence only: no whole-layout admission, repair authority or chunk-loading query. */
public final class GroundSupportProofs {
    private static final DeferredRegister<AttachmentType<?>> TYPES=DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES,SkyweaveCompat.ID);
    public static final Supplier<AttachmentType<Proof>> TYPE=TYPES.register("ground_support_proofs",()->
            AttachmentType.<Proof>builder(()->{throw new IllegalStateException("Ground support was not prepared");}).serialize(new Serializer()).build());
    public enum State { PENDING, VERIFIED, INVALIDATED, REFUSED, RETIRED }
    public record Expected(BlockPos position,BlockState state) {
        public Expected { position=position.immutable();Objects.requireNonNull(state); }
    }
    public record Entry(VillageFootprint.Foot foot,VillageFoundation.Outcome outcome,State state,List<Expected> required,BlockState naturalSource) {
        public Entry {
            Objects.requireNonNull(foot);Objects.requireNonNull(outcome);Objects.requireNonNull(state);required=List.copyOf(required);
            boolean supported=VillageFoundation.successful(outcome);
            if(outcome==VillageFoundation.Outcome.AUTHORED_CLEAR) {
                if(state!=State.PENDING && state!=State.RETIRED && state!=State.INVALIDATED
                        || naturalSource!=null || required.size()!=1 || !required.getFirst().position().equals(foot.position())
                        || !required.getFirst().state().is(net.minecraft.world.level.block.Blocks.AIR))
                    throw new IllegalArgumentException("Invalid authored-clear disposition");
            } else if(supported) {
                if(state==State.REFUSED || state==State.RETIRED || required.size()<2 || required.size()>10
                        || !required.getFirst().position().equals(foot.position()) || !required.getFirst().state().equals(foot.expected())
                        || required.stream().map(Expected::position).distinct().count()!=required.size())
                    throw new IllegalArgumentException("Invalid support verification requirements");
                if(naturalSource==null || naturalSource.hasBlockEntity() || naturalSource.getBlock().hasDynamicShape()
                        || !naturalSource.getFluidState().isEmpty() || !naturalSource.isFaceSturdy(EmptyBlockGetter.INSTANCE,BlockPos.ZERO,Direction.UP)
                        || outcome==VillageFoundation.Outcome.NATIVE_BASE && required.get(1).state().equals(naturalSource)
                        || outcome!=VillageFoundation.Outcome.NATIVE_BASE && !required.get(1).state().equals(naturalSource))
                    throw new IllegalArgumentException("Invalid natural/native source provenance");
                for(int i=1;i<required.size();i++) {
                    var expected=required.get(i);var at=expected.position();var material=expected.state();
                    if(at.getX()!=foot.position().getX() || at.getZ()!=foot.position().getZ()
                            || at.getY()>=foot.position().getY() || (long)foot.position().getY()-at.getY()>9
                            || material.hasBlockEntity() || material.getBlock().hasDynamicShape() || !material.getFluidState().isEmpty()
                            || !material.isFaceSturdy(EmptyBlockGetter.INSTANCE,at,Direction.UP))
                        throw new IllegalArgumentException("Invalid support verification column");
                }
                // Source and every fill form the entire immediate-contact column, with no missing cell.
                int depth=required.stream().mapToInt(e->foot.position().getY()-e.position().getY()).max().orElseThrow();
                if(required.size()!=depth+1)throw new IllegalArgumentException("Incomplete support verification column");
            } else if(state!=State.REFUSED || !required.isEmpty() || naturalSource!=null)throw new IllegalArgumentException("Refused foot retained verification credit");
        }
    }
    public record Proof(long seed,String generation,ResourceLocation dimension,ChunkPos chunk,int minY,int height,String layout,List<Entry> entries) {
        public Proof {
            Objects.requireNonNull(dimension);Objects.requireNonNull(chunk);entries=List.copyOf(entries);
            if(!generation.matches("[0-9a-f]{64}") || !layout.matches("[0-9a-f]{64}") || height<16 || height>1024
                    || minY%16!=0 || height%16!=0 || (long)minY+height>Integer.MAX_VALUE
                    || entries.size()>VillageFootprint.Budget.MAX_FEET
                    || entries.stream().map(Entry::foot).distinct().count()!=entries.size())throw new IllegalArgumentException("Invalid support proof header");
            for(var entry:entries) {
                if(!new ChunkPos(entry.foot().position()).equals(chunk) || entry.foot().position().getY()<minY
                        || (long)entry.foot().position().getY()>= (long)minY+height)throw new IllegalArgumentException("Support proof foot outside its chunk");
                for(var expected:entry.required())if(!new ChunkPos(expected.position()).equals(chunk)
                        || expected.position().getY()<minY || (long)expected.position().getY()>=(long)minY+height)
                    throw new IllegalArgumentException("Support proof requirement outside its chunk");
            }
        }
        boolean matches(ServerLevel level,ChunkAccess at) {
            return seed==level.getSeed() && generation.equals(GenerationGuard.fingerprint(level.getServer())) && dimension.equals(level.dimension().location())
                    && chunk.equals(at.getPos()) && minY==at.getMinBuildHeight() && height==at.getHeight();
        }
    }
    private GroundSupportProofs() {}
    public static void register(IEventBus bus) { TYPES.register(bus); }
    public static Proof existing(ServerLevel level,ChunkAccess chunk) {
        if(!NaturalStructureInputs.owned(level) || !chunk.hasData(TYPE))return null;
        var proof=chunk.getData(TYPE);return proof.matches(level,chunk)?proof:null;
    }
    public static void record(ServerLevel level,ChunkAccess chunk,VillageFoundation.Plan plan,VillageFoundation.Realization realized) {
        var source=NaturalStructureInputs.existing(level,chunk);
        if(source==null || source.grading()==null)throw new IllegalStateException("Support evidence has no retained source intent");
        var entries=new ArrayList<Entry>();
        for(var binding:plan.bindings()) {
            var foot=binding.foot();if(!new ChunkPos(foot.position()).equals(chunk.getPos()))continue;
            var outcome=realized.outcomes().get(foot);if(outcome==null)throw new IllegalStateException("Support realization omitted a prepared foot");
            var finalState=realized.finalFeet().get(foot);
            if(finalState!=null)foot=new VillageFootprint.Foot(foot.template(),foot.piece(),foot.position(),foot.original(),finalState);
            var required=new ArrayList<Expected>();boolean supported=VillageFoundation.successful(outcome);
            if(supported) {
                required.add(new Expected(foot.position(),foot.expected()));required.add(new Expected(binding.source(),realized.nativeSources().getOrDefault(binding.foot(),binding.sourceState())));
                for(var fill:binding.fills())required.add(new Expected(fill.position(),fill.target()));
            } else if(outcome==VillageFoundation.Outcome.AUTHORED_CLEAR) {
                required.add(new Expected(foot.position(),net.minecraft.world.level.block.Blocks.AIR.defaultBlockState()));
            }
            entries.add(new Entry(foot,outcome,supported || outcome==VillageFoundation.Outcome.AUTHORED_CLEAR?State.PENDING:State.REFUSED,required,supported?binding.sourceState():null));
        }
        var proof=new Proof(level.getSeed(),GenerationGuard.fingerprint(level.getServer()),level.dimension().location(),chunk.getPos(),
                chunk.getMinBuildHeight(),chunk.getHeight(),source.grading().layout(),entries);
        if(chunk.hasData(TYPE) && !chunk.getData(TYPE).equals(proof))throw new IllegalStateException("Support evidence differs on FEATURES replay");
        chunk.setData(TYPE,proof);chunk.setUnsaved(true);
    }
    /** Read-only current evaluation; stored failures never regain credit through a query. */
    public static State inspect(Entry entry,ChunkAccess chunk) {
        if(!new ChunkPos(entry.foot().position()).equals(chunk.getPos()))throw new IllegalArgumentException("Foreign support proof query");
        if(entry.state()!=State.VERIFIED && entry.state()!=State.PENDING && entry.state()!=State.RETIRED)return entry.state();
        for(var expected:entry.required())if(!chunk.getBlockState(expected.position()).equals(expected.state()))return State.INVALIDATED;
        return entry.state();
    }
    /** LIGHT has closed native radius-one FEATURES writers. No block or light writes occur here. */
    public static void verify(ServerLevel level,ChunkAccess chunk) {
        if(!NaturalStructureInputs.owned(level) || !chunk.hasData(TYPE))return;
        if(chunk.getPersistedStatus().isBefore(ChunkStatus.INITIALIZE_LIGHT))throw new IllegalStateException("Support verification before native feature closure");
        var proof=existing(level,chunk);if(proof==null)throw new IllegalStateException("Mismatched support evidence at LIGHT");
        var source=NaturalStructureInputs.existing(level,chunk);
        boolean authentic=source!=null && source.grading()!=null && source.grading().layout().equals(proof.layout());
        boolean survivingSource=chunk.hasData(NaturalStructureInputs.TYPE);
        var verified=new ArrayList<Entry>();
        for(var entry:proof.entries()) {
            var state=entry.state();
            if(state==State.PENDING || state==State.VERIFIED || state==State.RETIRED)
                state=survivingSource && !authentic || state==State.PENDING && !authentic || inspect(entry,chunk)==State.INVALIDATED
                        ?State.INVALIDATED:entry.outcome()==VillageFoundation.Outcome.AUTHORED_CLEAR?State.RETIRED:State.VERIFIED;
            verified.add(new Entry(entry.foot(),entry.outcome(),state,entry.required(),entry.naturalSource()));
        }
        chunk.setData(TYPE,new Proof(proof.seed(),proof.generation(),proof.dimension(),proof.chunk(),proof.minY(),proof.height(),proof.layout(),verified));
        chunk.setUnsaved(true);
    }
    private static final class Serializer implements IAttachmentSerializer<CompoundTag,Proof> {
        private static void fields(CompoundTag tag,String... names) {
            if(!tag.getAllKeys().equals(Set.of(names)))throw new IllegalArgumentException("Unexpected support proof fields");
        }
        private static CompoundTag compound(CompoundTag tag,String key) {
            if(!tag.contains(key,Tag.TAG_COMPOUND))throw new IllegalArgumentException("Invalid support proof compound: "+key);return tag.getCompound(key);
        }
        private static String string(CompoundTag tag,String key) {
            if(!tag.contains(key,Tag.TAG_STRING))throw new IllegalArgumentException("Invalid support proof string: "+key);return tag.getString(key);
        }
        private static int integer(CompoundTag tag,String key) {
            if(!tag.contains(key,Tag.TAG_INT))throw new IllegalArgumentException("Invalid support proof integer: "+key);return tag.getInt(key);
        }
        private static long number(CompoundTag tag,String key) {
            if(!tag.contains(key,Tag.TAG_LONG))throw new IllegalArgumentException("Invalid support proof long: "+key);return tag.getLong(key);
        }
        private static ListTag list(CompoundTag tag,String key,int maximum) {
            if(!(tag.get(key) instanceof ListTag list) || list.size()>maximum || !list.isEmpty() && list.getElementType()!=Tag.TAG_COMPOUND)
                throw new IllegalArgumentException("Invalid support proof list: "+key);return list;
        }
        private static BlockState state(CompoundTag tag,String key) { return BlockState.CODEC.parse(NbtOps.INSTANCE,compound(tag,key)).getOrThrow(); }
        @Override public Proof read(IAttachmentHolder holder,CompoundTag tag,HolderLookup.Provider provider) {
            fields(tag,"schema","seed","generation","dimension","x","z","minY","height","layout","entries");
            if(integer(tag,"schema")!=3 || !(holder instanceof ChunkAccess chunk))throw new IllegalArgumentException("Unsupported support proof schema/holder");
            var entries=new ArrayList<Entry>();
            for(var value:list(tag,"entries",VillageFootprint.Budget.MAX_FEET)) {
                var row=(CompoundTag)value;fields(row,"template","piece","position","original","expected","outcome","state","required","naturalSource");
                var foot=new VillageFootprint.Foot(ResourceLocation.parse(string(row,"template")),integer(row,"piece"),BlockPos.of(number(row,"position")),
                        BlockPos.of(number(row,"original")),state(row,"expected"));
                var required=new ArrayList<Expected>();
                for(var item:list(row,"required",10)) {
                    var expected=(CompoundTag)item;fields(expected,"position","state");required.add(new Expected(BlockPos.of(number(expected,"position")),state(expected,"state")));
                }
                var natural=compound(row,"naturalSource");
                entries.add(new Entry(foot,VillageFoundation.Outcome.valueOf(string(row,"outcome")),State.valueOf(string(row,"state")),required,
                        natural.isEmpty()?null:BlockState.CODEC.parse(NbtOps.INSTANCE,natural).getOrThrow()));
            }
            var proof=new Proof(number(tag,"seed"),string(tag,"generation"),ResourceLocation.parse(string(tag,"dimension")),
                    new ChunkPos(integer(tag,"x"),integer(tag,"z")),integer(tag,"minY"),integer(tag,"height"),string(tag,"layout"),entries);
            if(!proof.chunk().equals(chunk.getPos()) || proof.minY()!=chunk.getMinBuildHeight() || proof.height()!=chunk.getHeight())
                throw new IllegalArgumentException("Support proof holder mismatch");
            return proof;
        }
        @Override public CompoundTag write(Proof proof,HolderLookup.Provider provider) {
            var tag=new CompoundTag();tag.putInt("schema",3);tag.putLong("seed",proof.seed());tag.putString("generation",proof.generation());
            tag.putString("dimension",proof.dimension().toString());tag.putInt("x",proof.chunk().x);tag.putInt("z",proof.chunk().z);
            tag.putInt("minY",proof.minY());tag.putInt("height",proof.height());tag.putString("layout",proof.layout());var entries=new ListTag();
            for(var entry:proof.entries()) {
                var row=new CompoundTag();var foot=entry.foot();row.putString("template",foot.template().toString());row.putInt("piece",foot.piece());
                row.putLong("position",foot.position().asLong());row.putLong("original",foot.original().asLong());
                row.put("expected",BlockState.CODEC.encodeStart(NbtOps.INSTANCE,foot.expected()).getOrThrow());
                row.putString("outcome",entry.outcome().name());row.putString("state",entry.state().name());var required=new ListTag();
                row.put("naturalSource",entry.naturalSource()==null?new CompoundTag():BlockState.CODEC.encodeStart(NbtOps.INSTANCE,entry.naturalSource()).getOrThrow());
                for(var expected:entry.required()) {
                    var cell=new CompoundTag();cell.putLong("position",expected.position().asLong());cell.put("state",BlockState.CODEC.encodeStart(NbtOps.INSTANCE,expected.state()).getOrThrow());required.add(cell);
                }
                row.put("required",required);entries.add(row);
            }
            tag.put("entries",entries);return tag;
        }
    }
}
