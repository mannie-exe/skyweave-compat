package design.inherent.skyweavecompat.mixin;

import com.mojang.datafixers.util.Either;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(SinglePoolElement.class)
public interface VillageTemplateAccess {
    @Accessor("template") Either<ResourceLocation, StructureTemplate> skyweave$template();
    @Invoker("getSettings") net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings skyweave$settings(
            net.minecraft.world.level.block.Rotation rotation, net.minecraft.world.level.levelgen.structure.BoundingBox bounds,
            net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings liquids, boolean keepJigsaws);
    @Invoker("getTemplate") StructureTemplate skyweave$template(StructureTemplateManager templates);
}
