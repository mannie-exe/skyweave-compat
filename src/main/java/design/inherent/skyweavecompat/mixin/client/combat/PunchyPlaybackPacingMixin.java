package design.inherent.skyweavecompat.mixin.client.combat;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import design.inherent.skyweavecompat.client.combat.ClientAttackBridge;
import design.inherent.skyweavecompat.client.combat.PunchyAttackBridge;
import design.inherent.skyweavecompat.client.combat.PunchyPlaybackBinding;
import design.inherent.skyweavecompat.combat.AttackPacing;
import design.inherent.skyweavecompat.combat.AttackSnapshot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import punchy.client.animation.data.AnimationClip;

@Mixin(targets = "punchy.client.animation.PoseHandler", remap = false)
public abstract class PunchyPlaybackPacingMixin implements PunchyPlaybackBinding {
    @Shadow public abstract long getPlaybackId();
    @Shadow public abstract AnimationClip getCurrentClip();
    @Unique private AttackSnapshot skyweave$attack;
    @Unique private long skyweave$playback = -1;
    @Override public void skyweave$bindAttack(AttackSnapshot attack, long playback) {
        skyweave$attack = attack; skyweave$playback = playback;
    }
    @WrapMethod(method = "resolveTimelineSpeed")
    private float skyweave$attackSpeed(AnimationClip clip, float time, Operation<Float> original) {
        var context = PunchyAttackBridge.speedContext();
        String previousClip = context.clip(); float previousRate = context.rate();
        float rate = 1;
        if (skyweave$attack != null) {
            if (skyweave$playback != getPlaybackId() || skyweave$attack.session() != ClientAttackBridge.session()) skyweave$attack = null;
            else if (clip != null && clip == getCurrentClip()) rate = AttackPacing.firstPerson(skyweave$attack, clip.getName(), clip.getLength(), time);
        }
        context.set(clip == null ? null : clip.getName(), rate);
        try { return original.call(clip, time); }
        finally { context.set(previousClip, previousRate); }
    }
}
