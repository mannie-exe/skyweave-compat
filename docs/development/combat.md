# Attack presentation

The optional CN/Punchy and CN/Moves adapters follow admitted local native attacks. Combat Nouveau owns attack eligibility and synchronized combat rules; the server owns damage. These adapters send no attacks, change no cooldowns and perform no world or target searches. [Configuration](../configuration.md#attack-presentation) owns their controls.

## Admission and retained context

`NativeAttackScopeMixin` wraps `Minecraft.startAttack` with exception-safe context. It captures pre-attack item, charge, attack speed and movement state, refreshes the actual entity target before its native operation, and admits presentation at the native hand swing. Misses retain their pre-reset capture because CN's air-sweep handler can reset recharge before the ordinary native reset call. The method's boolean return only reports block destruction. Entry, a key edge or target damage does not establish admission.

CN's retained-energy miss calls `LivingEntity.swing(hand, false)` before an injected early return. `NativeRetainedMissMixin` admits that actual local miss before Punchy's original producer and delivers Moves once afterward. The ordinary native handoff already owns its swings and is not repeated. The first native air-block predicate marks a vanished block target as a miss before CN's early-return path; the adapter reuses that computed result without another block query. Foreign actors and server-origin swings remain unchanged.

Moves' early `triggerPrimaryAction` call and local `primarySwingStarted` key-edge path are suppressed within the qualified adapter. The completed native swing supplies one handoff with its pre-reset charge. Native mining, item use, remote swing detection and resource selection retain their owners. `MovesAttackBridge` mirrors the engine's shared pending-input fields; it adds no replay queue. Controller context is transferred at the real local `preparePlayer` queue operation and bound to its native producer counter. Foreign producers discard the old receipt. Invalidated owned handoffs are skipped once, while controller-local pending inputs lose their old session before selection.

Punchy's original producer records the native epoch and hand after its own input admission. `PendingAttack` retains one immutable snapshot. A later matching consumer takes it once; coalesced inputs retain the latest admitted snapshot. Rejection leaves the earlier admitted epoch intact. Foreign producers discard owned metadata. A blacklist discard, native clear or exceptional unwind cannot reuse it. Session invalidation clears a native queued trigger only while its exact retained epoch/hand still belongs to this adapter; foreign triggers remain native. A deferred owned runnable checks its session even when pacing is off.

The final returned NeoForge critical event in the same native local `Player.attack` supplies prediction. The bridge changes the inner stored-bit consumer's return after native clearing, before Punchy's critical clip selection and `played(critical_hit)` hook. It does not synthesize server confirmation, alter sprinting or recursively call Punchy's attack API. Misses and paths without a native critical decision remain ordinary.

Punchy's public `AttackRules.unthrottled` returns true only for the matching retained item/epoch/hand/session when charge is full and the currently synchronized CN minimum is at least one. Other policies return neutral. Native item/use restrictions remain active. This removes the redundant visual limiter for CN's four-per-second bare hands without making the generic Punchy limiter unconditional.

## Pacing

Correctness repairs and presentation preferences are independent. Pacing binds pre-attack attributes to an actual new playback, rather than reading changing hotbar state each frame. A changed item before Punchy's delayed consumption keeps native pacing. Neither adapter changes animation assets, clip selection, gameplay timing or recharge.

The first-person owner is Punchy's released attack-only `clipSpeed` API, scoped to its actual action handler and playback ID. The third-person owner is Moves' existing `timerControl` calculation for the `attack` one-shot clock. Its private immutable `TimerControl` has no public copy API in the pinned release; cached method handles preserve the original condition and modifier. No reflective lookup occurs per frame. Remote controllers, other timeline types, missing clips, changed clip lengths and unsupported items retain native clocks.

| Qualified family                                      | First-person strike boost | F&F third-person strike boost |
| ----------------------------------------------------- | ------------------------- | ----------------------------- |
| Vanilla swords                                        | 1.25×; critical 1.5×      | 4×                            |
| Vanilla axes using the qualified generic attack clips | 1.25×; critical 1.5×      | 1.5×                          |
| Empty hand                                            | 1.25×                     | 3×                            |

`AttackPacing` owns exact clip names, lengths and strike-phase endpoints. Its boost eases toward the attribute-adjusted recovery rate over the final fifth of the strike phase. A bounded square-root factor accounts for the captured attack-speed attribute relative to the vanilla item baseline; resulting clock factors stay between 0.5 and 4. Native recovery is not stretched to fill recharge. A newly admitted supported Moves action can interrupt an active qualified attack clip; an unknown or changed clip keeps its native retrigger gate. F&F has no separate critical clip, so none is invented. Trident, mace, mining and unknown resource clips retain their original pacing.

These profiles favor prompt visible strikes and separate recovery. They do not claim that a rendered blade intersects a target on Minecraft's instantaneous damage tick. Compare actual first-/third-person frames, target feedback and native event timing independently when changing them.

## Lifetime and cost

The native input scope releases live references in `finally`. Retained snapshots contain primitives and item IDs, with one native-capacity slot per owner. A weak player/level identity supplies the session. Death, player replacement, level/dimension changes, logout and resource reload invalidate owned bindings; playback also checks current session and native playback identity. Same-dimension teleport retains the current session and does not create another attack. Exceptional exits discard only their own sequence.

Hot paths do bounded local checks and arithmetic, with no disk, network or block reads. Known phase tables and private clock access are initialized once. Moves allocates its native immutable timer only when an admitted clock actually changes. There is no growing attack history, cross-player queue or global animation scheduler.

## Dependency updates

The selected contract is Minecraft 1.21.1, NeoForge 21.1.265, Combat Nouveau 21.1.2, Puzzles Lib 21.1.65, Punchy 3.2b, Moves 1.0.9 and F&F 1.0.1. Moves supplies Expressions through its declared nested JAR. F&F is a resource qualification, not a mandatory dependency. Compile-only artifacts and checksum checks do not bundle foreign binaries or assets.

For updates, inspect native `startAttack`, its first air-block predicate, both hand-swing overload paths, CN's retained-energy/air-sweep handlers, Puzzles' cancellation/swing-hand propagation, the final `CommonHooks.fireCriticalHit(Player,Entity,boolean,float)` call, both Moves producers and the local queue transfer. Check exact original producer/consumer descriptors, epoch/hand and coalescing semantics, deferred `AnimationIntentCoordinator` requests, native handler playback IDs, public API registrations/reload and private Moves clock structure before changing pins. Requalify new resource clip identities and phase/contact observations; do not carry timing numbers across changed artwork merely because names match.

Run `./gradlew --no-daemon check build clientProbeJar`, then the disposable fixture in [testing](testing.md#combat-presentation). Include disabled/enabled, relaxed/disabled CN admission, independent features, optional absence/unsupported/false and dedicated-server controls. Qualify actual TCP observation separately from integrated prediction. Preserve Iris hand effects, Touch'n Motion, equipment/skin layers, Carry On, Create use and representative moving Sable interactions. Retire each bridge only after a released upstream equivalent passes its corresponding negative and lifecycle controls. Removing a feature key also requires the normal configuration-key migration policy.
