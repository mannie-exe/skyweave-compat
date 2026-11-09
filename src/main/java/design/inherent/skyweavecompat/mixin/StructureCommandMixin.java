package design.inherent.skyweavecompat.mixin;

import design.inherent.skyweavecompat.terrain.GeologicalGenerator;
import design.inherent.skyweavecompat.terrain.LocatorText;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.ResourceOrTagKeyArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.commands.LocateCommand;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Preserve native validation, then stop only the synchronous structure command. */
@Mixin(LocateCommand.class)
public abstract class StructureCommandMixin {
    @Inject(method = "locateStructure", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/level/chunk/ChunkGenerator;findNearestMapStructure(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/HolderSet;Lnet/minecraft/core/BlockPos;IZ)Lcom/mojang/datafixers/util/Pair;"),
            cancellable = true, require = 1, allow = 1)
    private static void skyweave$boundedGuidance(CommandSourceStack source,
            ResourceOrTagKeyArgument.Result<Structure> selector, CallbackInfoReturnable<Integer> result) {
        if (!(source.getLevel().getChunkSource().getGenerator() instanceof GeologicalGenerator generator)
                || !generator.skyweave$isEnabled()) return;
        String command = "/skyweavecompat structures search 12800 structure=" + selector.asPrintable();
        var link = Component.literal(command).withStyle(style -> style.withColor(ChatFormatting.AQUA)
                .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, command)));
        source.sendFailure(LocatorText.text("structures", "native_guidance", link));
        result.setReturnValue(0);
    }
}
