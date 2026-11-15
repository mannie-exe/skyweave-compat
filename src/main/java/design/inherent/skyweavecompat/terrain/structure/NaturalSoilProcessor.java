package design.inherent.skyweavecompat.terrain.structure;

import com.mojang.serialization.MapCodec;
import design.inherent.skyweavecompat.Feature;
import design.inherent.skyweavecompat.OptionalIntegrations;
import design.inherent.skyweavecompat.SkyweaveCompat;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.*;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Explicit authored ambient soil only; paths, paving, crops and wet walkways keep their native output. */
public final class NaturalSoilProcessor extends StructureProcessor {
    public static final NaturalSoilProcessor INSTANCE = new NaturalSoilProcessor();
    public static final MapCodec<NaturalSoilProcessor> CODEC = MapCodec.unit(INSTANCE);
    private static final DeferredRegister<StructureProcessorType<?>> TYPES = DeferredRegister.create(Registries.STRUCTURE_PROCESSOR, SkyweaveCompat.ID);
    private static final Supplier<StructureProcessorType<NaturalSoilProcessor>> TYPE = TYPES.register("natural_soil", () -> () -> CODEC);
    private NaturalSoilProcessor() {}
    public static void register(IEventBus bus) { TYPES.register(bus); }

    public static BlockState material(BlockState output, BlockState natural) {
        if (natural == null || !output.is(Blocks.GRASS_BLOCK) && !output.is(Blocks.DIRT)) return output;
        return natural.is(Blocks.MUD) || natural.is(Blocks.CLAY) || natural.is(Blocks.MOSS_BLOCK) || natural.is(Blocks.SCULK)
                ? natural : output;
    }

    @Override public StructureTemplate.StructureBlockInfo processBlock(LevelReader level, BlockPos origin, BlockPos reference,
            StructureTemplate.StructureBlockInfo raw, StructureTemplate.StructureBlockInfo current, StructurePlaceSettings settings) {
        if (!OptionalIntegrations.enabled(Feature.GROUND_SUPPORT) || current.nbt() != null
                || raw.pos().getY() != 0
                || !current.state().is(Blocks.GRASS_BLOCK) && !current.state().is(Blocks.DIRT)) return current;
        var state = material(current.state(), GroundVillageTerrain.naturalSoil(level, current.pos()));
        return state == current.state() ? current : new StructureTemplate.StructureBlockInfo(current.pos(), state, current.nbt());
    }
    @Override protected StructureProcessorType<?> getType() { return TYPE.get(); }
}
