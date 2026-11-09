package design.inherent.skyweavecompat.access;

import design.inherent.skyweavecompat.terrain.GeologicalGenerator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.PortalShape;
import net.neoforged.neoforge.common.ItemAbilities;

/** One server-owned access policy; registered levels and unrelated fire remain intact. */
public final class DimensionAccess {
    private DimensionAccess() {}

    public static boolean active(MinecraftServer server) {
        return design.inherent.skyweavecompat.OptionalIntegrations.enabled(design.inherent.skyweavecompat.Feature.NETHER_ACCESS)
                && server.overworld().getChunkSource().getGenerator() instanceof GeologicalGenerator generator
                && generator.skyweave$isEnabled();
    }

    public static boolean denied(MinecraftServer server, ResourceKey<Level> destination) {
        return destination.equals(Level.NETHER) && active(server);
    }

    public static boolean denyIgnition(UseOnContext context) {
        if (!(context.getLevel() instanceof ServerLevel level) || !active(level.getServer())
                || !level.dimension().equals(Level.OVERWORLD) && !level.dimension().equals(Level.NETHER)) return false;
        var clicked = context.getClickedPos();
        // Lighting a campfire/candle or a foreign firestarter target is still ordinary use.
        if (level.getBlockState(clicked).getToolModifiedState(context, ItemAbilities.FIRESTARTER_LIGHT, true) != null) return false;
        var fire = clicked.relative(context.getClickedFace());
        if (PortalShape.findEmptyPortalShape(level, fire, Direction.Axis.X).isEmpty()
                && PortalShape.findEmptyPortalShape(level, fire, Direction.Axis.Z).isEmpty()) return false;
        deniedActivation(context, fire);
        return true;
    }

    /** Reserved for a future activation response; denial currently has no extra effect. */
    private static void deniedActivation(UseOnContext context, BlockPos position) { }
}
