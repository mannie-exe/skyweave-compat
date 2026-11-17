package design.inherent.skyweavecompat.mixin.encounters;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import design.inherent.skyweavecompat.encounters.EncounterChance;
import design.inherent.skyweavecompat.encounters.EncounterConfiguration;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.PatrolSpawner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Slice;

@Mixin(PatrolSpawner.class)
public abstract class PatrolChanceMixin {
    @WrapOperation(method="tick",at=@At(value="INVOKE",target="Lnet/minecraft/util/RandomSource;nextInt(I)I",ordinal=0),
            slice=@Slice(from=@At(value="INVOKE",target="Lnet/minecraft/server/level/ServerLevel;isDay()Z"),
                    to=@At(value="INVOKE",target="Lnet/minecraft/server/level/ServerLevel;players()Ljava/util/List;")),require=1,expect=1)
    private int probability(RandomSource random,int bound,Operation<Integer> original){
        double percent=EncounterConfiguration.profile().patrolCheckPercent();
        return percent==20?original.call(random,bound):EncounterChance.patrol(random,bound,percent);
    }
}
