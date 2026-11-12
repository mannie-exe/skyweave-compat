package design.inherent.skyweavecompat.mixin;

import design.inherent.skyweavecompat.terrain.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.SculkShriekerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.sathrek.sky_archipelago.worldgen.generator.core.SkyIslandChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.*;

/** Bounded devices on the already-painted natural cap, never a spreading sculk patch. */
@Mixin(value = SkyIslandChunkGenerator.class, remap = false)
public abstract class ShapeDecorationMixin {
    @Inject(method = "applyBiomeDecoration", at = @At("TAIL"))
    private void sculkSurface(WorldGenLevel level, ChunkAccess chunk, StructureManager manager, CallbackInfo callback) {
        var generator = (SkyIslandChunkGenerator)(Object)this;
        var geological = (GeologicalGenerator)(Object)this;
        if (!geological.skyweave$isEnabled()) return;
        var field = geological.skyweave$field(level.getLevel().getChunkSource().randomState());
        var styles = ((StyledTerrain)(Object)field).skyweave$styles();
        if (styles == null || !styles.profile().enabled()) return;
        var settings = generator.resolvedSkyIslandSettings();
        // Unknown references suppress this small decoration pass, never request new chunks.
        var protectedPieces = skyweave$pieces(level,chunk);
        if (protectedPieces == null) return;
        long seed = level.getSeed() ^ chunk.getPos().toLong() ^ 0x37bdc591L;
        var profile = styles.profile().deepDark();
        var states = new BlockState[]{Blocks.SCULK_VEIN.defaultBlockState().setValue(MultifaceBlock.getFaceProperty(Direction.DOWN),true),
                Blocks.SCULK_SENSOR.defaultBlockState(),Blocks.SCULK_CATALYST.defaultBlockState(),
                Blocks.SCULK_SHRIEKER.defaultBlockState().setValue(SculkShriekerBlock.CAN_SUMMON,true)};
        double[] chances = {profile.veinChance(),profile.sensorChance(),profile.catalystChance(),profile.shriekerChance()};
        var snapshot = field.sampleChunkTerrainSnapshot(chunk.getPos(),settings);
        for (int kind=0;kind<states.length;kind++) {
            long salt=0x41cbL+kind*0x1893L;
            if (IslandStyles.unit(seed,salt)>=chances[kind]) continue;
            var random=RandomSource.create(seed^salt);
            for (int attempt=0;attempt<16;attempt++) {
                int x=chunk.getPos().getMinBlockX()+random.nextInt(16),z=chunk.getPos().getMinBlockZ()+random.nextInt(16);
                var context=((InteriorField)(Object)field).skyweave$contextAt(x,z,settings);
                var column=snapshot.columnDataAt(x,z);
                if (column.segments().isEmpty()) continue;
                // One selected exposed natural surface, independent of the sculk coverage draw.
                var segment=column.segments().get(random.nextInt(column.segments().size()));
                int floor=segment.topY();
                if (!DeepDarkMaterials.special(styles,context.solidOwner(floor))) continue;
                var position=new BlockPos(x,floor+1,z);
                var support=level.getBlockState(position.below());
                boolean eligibleSupport=kind==0 ? support.is(net.minecraft.tags.BlockTags.BASE_STONE_OVERWORLD)
                        || support.is(Blocks.CALCITE) || support.is(Blocks.TUFF) : support.is(Blocks.SCULK);
                if (protectedPieces.stream().anyMatch(box->box.isInside(position)||box.isInside(position.below()))
                        || !level.getBlockState(position).isAir() || !eligibleSupport
                        || !states[kind].canSurvive(level,position)) continue;
                // Current-chunk-only writes; native block entities/shape checks remain intact.
                level.setBlock(position,states[kind],2);
                break;
            }
        }
    }

    @org.spongepowered.asm.mixin.Unique
    private static List<BoundingBox> skyweave$pieces(WorldGenLevel level,ChunkAccess chunk) {
        var result=new ArrayList<BoundingBox>();int references=0;
        for (var entry:chunk.getAllReferences().entrySet()) for (long reference:entry.getValue()) {
            if (++references>1024) return null;
            var pos=new net.minecraft.world.level.ChunkPos(reference);
            if (!level.hasChunk(pos.x,pos.z)) return null;
            var owner=level.getChunk(pos.x,pos.z,ChunkStatus.STRUCTURE_STARTS,false);
            if (owner==null) return null;
            var start=owner.getStartForStructure(entry.getKey());
            if (start==null) return null;
            if (!start.isValid()) continue;
            if (start.getPieces().size()>8192-result.size()) return null;
            for(var piece:start.getPieces()) result.add(piece.getBoundingBox());
        }
        return result;
    }
}
