# Animation adapters

The modpack's [animation compatibility contract](https://github.com/mannie-exe/create-skyweave/blob/main/docs/development/animation-compatibility.md) owns resource fitting, original action/render providers, and the remaining audit. Compat owns the independently selectable runtime repairs below. These adapters retain public re:form 0.4.0 and the native gameplay, animation clocks, and resource-reload owners.

## Moves overlay bone filters

Moves 1.0.9b normalizes overlay bone filters with its action-name normalizer. That removes underscores from `left_item`, `right_item`, and arm names, while the resolved overlay matcher uses canonical names with underscores. An overlay can therefore resolve its animation without applying to its intended bone.

`punchy_moves.overlay_bones` changes only that one normalization call in `ItemAnimationBindings.parseOverlays`. It trims and lowercases the authored bone name while preserving punctuation. Action and group normalization, clip ownership, overlay selection, and application stay native. No animations or upstream assets are included. Missing Moves and explicit opt-out select no hook; enabled unsupported versions are rejected by the normal dependency contract. Retire the adapter when the released parser and matcher agree on bone identities.

## Better Combat upward attack pitch

Better Combat 2.4.0 adds 75% of look pitch to the third-person body and legs. The same excessive backward bend reproduces without Moves or Touch’n Motion. `better_combat.attack_pitch` requires exact Better Combat 2.4.0+1.21.1 and Player Animator 2.0.4+1.21.1 on a physical client. It does not require Moves.

The adapter wraps only the source already supplied to Better Combat's native `AttackAnimationSubStack` adjustment modifier. Added upward body and leg pitch is limited to 20 degrees; level, downward, first-person, other rotation axes, scale, and translation retain their native values. The original modifier object, fade, attack state, and clock remain owned by Better Combat. No input, reach, target, server hit, or damage code changes.

A scalar transfer into the arms does not preserve a twisting swing. At the actual Player Animator `AnimationApplier.updatePart` rotation query, the adapter instead compares the original and adjusted body rotations and compensates the full composed arm basis. Original reads use a nested, exception-safe scope that bypasses only this adapter's source wrapper. The native incoming arm pose is preserved as the original basis. With native body rotation `B`, model reflection `S`, and local arm rotation `A`, compensation preserves `B_new * S * A_new = B_old * S * A_old`. Position, scale, arbitrary library queries, and other model-part rotation queries remain native. The implementation uses the pinned native call site rather than stack inspection, a replacement animation, or a second animation clock.

Retire the source and compensation hooks together when the upstream pitch policy preserves the corresponding posture and arm composition. The private audit's scalar prototype and instrumented stack walker are not shipping implementations. Native pose, final attachment, moving attacks, gameplay, and other-player presentation require their own scoped qualification; algebraic tests alone do not establish those journeys.

## Managed lantern grip resources

`punchy_moves.lantern_grip` requires the overlay-bone and item-definition adapters, exact Moves 1.0.9b, Punchy 3.2b, and Modefite 1.0.1+1.21.1. Its separately authored modpack ZIP lives at `config/skyweave/animation-packs/lantern-grip.zip`, outside the ordinary resource-pack directory. Compat does not ship the pack's models, profiles, textures, or animations.

Only the matching qualified ZIP digest is admitted as native client pack `skyweave_compat/lantern-grip`. Disabling either prerequisite or the lantern switch admits neither its hook nor its pack finder. Missing providers select no integration. A missing bundle does nothing; a mismatched bundle is refused with a diagnostic. Admission and startup switches require a restart. Native resource reload still owns opening and replacing the pack data. The pack owner retains its static generation and distribution.

The runtime correction targets only a delivered vanilla lantern or soul lantern whose actual item-definition resource and marker come from that admitted pack. It reads the same parent's final native humanoid arm at the original Moves item-bone application call. The correction changes only the item rotation; the native hand origin, position, equip scale, player arm, and original draw continuation stay native. Spatial GUI ownership is excluded. First-person Punchy fitting belongs to the pack's profile data, not this third-person hook.

The native hand basis is `K = Rx(-90°) * Ry(180°)`. For the actual arm rotation `R`, the item correction is `K^-1 * R^-1 * K`, converted to the native `HeldItemAnimation` degree convention. This computes from the current pose rather than sampling or redistributing Fluid & Foolish keyframes. It retains the native equip scale directly. The private prototype's copied animation curves and scale tracks are excluded from both shipping artifacts. Resource-off, native equip transitions, reload, movement, and both hands require actual consumer controls before adoption; the settled prototype is only the originating evidence.

## Deferred neighboring work

The audit also identifies native-use arm, Exposure selfie, Tavern shaker, and Cookery carry conflicts. Their different session, model, and custom first-person owners do not establish a safe common arm-reset repair. Exposure's modifier collision is a separate input boundary. None is implemented or implicitly covered by the overlay, attack-pitch, or lantern adapters. Keep their original failures and owner-specific acceptance with the ongoing audit.
