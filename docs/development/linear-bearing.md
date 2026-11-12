# Linear Bearing adaptations

The adaptations target Linear Bearing 1.3.5, Create 6.0.10, Sable 2.0.6 and Simulated 1.3.2. They are independent of island generation and client renderers. The four startup switches are listed in [configuration](../configuration.md). Client and dedicated installations use the same machinery rules; physics and network mutations run on the logical server.

## Repair ownership

`linear_bearing.bootstrap` removes only the released constructor's three client-registration sites on a dedicated server. Block, component, data-generation and creative-tab registration remain. Its built-in data pack supplies the missing bubble-gum block drop. With Linear Bearings Fix 1.0.3 present, Skyweave: Compatibility Patches defers both repairs and leaves its machinery adaptations active. An unfamiliar standalone-fix version requires review before enabling this switch.

Modrinth declares the standalone fix as a dependency of Linear Bearing; the released NeoForge metadata does not. Keep the catalogue dependency in distributions unless the package manager supports an explicit, reviewed reconciliation. Do not spoof the fix's mod ID. Manual installations can omit it when Skyweave: Compatibility Patches supplies the startup/drop repairs. Turning off those repairs without a replacement restores the original dedicated startup failure and missing drop.

The catalogue labels the projects Apache-2.0, while the selected JAR metadata says All Rights Reserved. Skyweave: Compatibility Patches implements its repairs independently; it does not redistribute upstream binaries or decompiled source. The small fix repository is not an original-source repository for the whole mod.

## Machinery contract

### Guide and brake

The bearing assembles blocks at its face into a Sable body and becomes a casing. Existing T/U collision shapes guide an unpowered carriage. This is a passive slide: no motor, hidden actuator or new travel limit is added. Wall orientation can permit gravity-driven travel. Match the casing orientation and slider side; use the upstream wrench to adjust them.

Power on the moving carriage engages a fixed constraint at its current pose. The brake neither snaps nor teleports the body. A casing aboard another body uses a relative constraint and reciprocal Sable actor links. The native docking handle owns disposal; normal docking behavior, connector pairing and inventory interfaces remain separate. Loss of alignment, owner, casing or power releases the brake. Relationships rebuild from geometry and power after loading; native constraint handles are not saved by Skyweave: Compatibility Patches.

Empty-hand use of the casing recovers a braked carriage. It requires exactly one engaged carriage and an aligned block grid. Destination cells must be loaded, inside build bounds, permitted and empty. Cargo and block-entity data transfer through Simulated's native disassembly. One casing becomes one bearing; the internal carriage is removed. A refused recovery keeps cargo and existing blocks. Recover before removing rails from a machine you intend to reuse.

Assembly uses typed Simulated APIs. A normal refusal preserves the source bearing and does not leave the temporary empty-front placeholder. Casing conversion requires a valid result and carriage position. This is not a transactional rollback system for arbitrary exceptions or another mod mutating the assembly halfway through.

### Magnetic and spring transmission

Magnetic ports discover opposite-facing ports in the same current level through a loaded, unobstructed 3.5-block ray from just beyond the front face. World/ship positions and shaft directions use Sable transforms. Discovery must be reciprocal; unloaded paths do not request chunks. A port pairs with one peer.

Torsional anchors use the real reciprocal Simulated spring pair, including its partner-body identity. Put each anchor behind its spring head, facing the head. The original local-facing spring sign convention is retained. No spring-distance stress multiplier or fake generator is added.

Both links are custom edges in Create's kinetic graph. Create owns source selection, speed propagation, stress, capacity and stalling. Endpoints supply no generated RPM or capacity. Remote loads count against the actual drive; reducing capacity stalls them. Incompatible driven speeds refuse a new link. Removing the drive cannot leave a self-powered endpoint. Moving out of eligibility removes the edge; returning allows rediscovery. Peer references are transient.

### Gyro sensor

The gyroscopic gearbox reports angular rate about its local shaft axis, using the released sampling and smoothing. It supplies **zero stress capacity**. Use a zero-load redstone converter to read it; it is not an engine or stabilizer. A loaded shaft still requires a real energy source. Physical rotation alone does not provide a power budget.

A narrow rounding correction lets a stationary positive sample decay to zero. The released smoothing otherwise retains 0.5 RPM because rounding +0.5 rounds upward on every subsequent tick. No reaction torque or automatic steering is added.

## Content and progression

Recipes, yields, masses and utility mechanics remain upstream. Equipment ownership does not establish that a working machine was built.

| Family                             | Lasting role and setup                                                                                                                                                                                                 | Disposition                                                                       |
| ---------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------- |
| Bearing, casing, internal carriage | Reusable passive guide and powered brake; first bearing uses 2 alloy, 1 slime ball, 1 iron sheet and 5 cobbled deepslate. Two casings use 2 alloy and 5 deepslate. Budget rails, payload, glue and control separately. | Assembly, brake, body links and recovery adapted.                                 |
| Magnetic port                      | Short-range rotary coupling without a rigid shaft across the gap. Each port uses an andesite casing, 2 electron tubes, 1 iron ingot and 1 shaft; a useful link needs two.                                              | Native network transmission replaces injected generation.                         |
| Torsional anchor                   | Flexible spring-linked rotary transmission. Each anchor uses 4 iron sheets, 2 springs, 2 casings and 1 cogwheel; add the linking springs and a real drive/load.                                                        | Native network transmission replaces reflection and injected generation.          |
| Gyroscopic gearbox                 | Local angular-rate signal from brass casing plus a Simulated gyroscopic mechanism.                                                                                                                                     | Zero-capacity sensor; no stabilization claim.                                     |
| Redstone converter                 | Continuous RPM-to-redstone signal; floor(abs(RPM)/8), capped at 15.                                                                                                                                                    | Retained; useful with real drives and the gyro.                                   |
| Andesite lamp                      | Redstone-controlled light, including inverted mode; 4 alloy around a redstone lamp.                                                                                                                                    | Retained.                                                                         |
| Slippery Copycat                   | Low-friction building surface; 4 zinc and 1 blue ice. It inherits Create material handling.                                                                                                                            | Retained; copied artwork does not change its fixed friction.                      |
| Bubble gum                         | Heated mixing uses sugar, purple dye and 250 mB honey for 2 blocks. Normal entities pass through; crouching gives collision. Movement/fall behavior differs from ordinary blocks.                                      | Missing loot repaired. Empty constraint callbacks do not establish physical glue. |

Natural costs already separate basic rails, quartz-dependent ports, spring transmission, blue-ice construction and brass-tier sensing. No arbitrary progression gates or free transmission capacity are justified. Do not describe the guide as a powered lift or gum as Sable glue. Journal additions should use verified item objectives, explain the actual setup, and keep successful operation as a suggested project unless a real event detects it. Do not use the upstream gyro acquisition advancement as evidence of a working sensor.

## Updating and removing

Review exact released constructor bytecode before changing the bootstrap pin. The patch checks its complete expected shape before editing. Check fix ownership and gum loot with the standalone fix both absent and present.

For Create updates, review custom propagation hooks, network invalidation, source/capacity accounting, overload and persistence. For Sable/Simulated updates, review pose/plot coordinates, spring partner APIs, constraint lifetime, actor traversal, native block-entity transfer and build-height guards. Recheck a moving parent and saved-world reload; a locked flag alone proves neither behavior. Review gyro sampling and the rounding call site separately from its capacity hook.

Compile dependencies, including nested Simulated/Companion and Ponder, are pinned and hash-checked. They are not bundled or made mandatory by these adapters. Do not retire a hook merely because an upstream version number changed; inspect and exercise the corresponding repair. Recover machinery before removing its providing mods. Disabling a switch restores upstream behavior on the next restart; it is not a live migration or a way to make unsafe original machinery safe.

## Bounded native checks

`tools/linear_probe.py` builds an opt-in probe against the selected runtime and runs a disposable, offline, loopback server. It copies selected mod/config bytes, uses the supplied server libraries read-only, disables BlueMap downloads/web access, and deletes its temporary save. `reopen` and `transmission` retain the same temporary save across two launches before deleting it. Output under `build/verification/linear` records exact hashes, inputs, results and logs. Nothing is sent to the source server.

```sh
python3 tools/linear_probe.py --server /path/to/server/game \
  --pack /path/to/create-skyweave --java /path/to/java21/bin/java \
  --linear /path/to/linearbearing-1.3.5.jar --scenario carriage
```

Use `--fix /path/to/linearbearingsfix-1.0.3.jar` for coexistence. Other finite scenes are `bootstrap`, `parent`, `reopen`, `disassembly` and `transmission`. Carriage orientations are `floor`, `ceiling`, `wall-front` and `wall-back`; disassembly also accepts `height` and `quarter`. `clientProbeJar` contains the opt-in `-Dskyweave_compat.probe.linear=true` integrated review scene. Its `compatprobe_linear_use` command sends a real empty-hand use packet; `compatprobe_linear_brake` changes fixture power. Neither probe ships in Skyweave: Compatibility Patches.

[Verification](verification.md) owns measured results and outstanding limits.

## Ponder and Jade

`linear_bearing.ponder` adds six client tutorials for assembly, external motion and braking, recovery, magnetic transmission, spring transmission, and angular-rate sensing. It requires the active carriage adaptation, Create 6.0.10 and Ponder 1.0.87+mc1.21.1. Transmission and gyro scenes appear only with their owning adaptations enabled. No client registration runs on a dedicated server.

The released upstream JAR contains one dormant tutorial: its service declaration is not discovered by this Ponder version, and its raw Item-array registration does not match the ResourceLocation-array API. Its kinetic-drive prose also differs from the passive guide. Skyweave: Compatibility Patches registers an independent plugin explicitly; it does not activate that plugin or duplicate an upstream scene that already works.

Scenes use normal Ponder section animations, staged reveals, control prompts, keyframes, and roughly four seconds of reading time per short explanation. They illustrate verified mechanics without starting a Sable physics simulation inside Ponder. The Java scene owns English prose; `python3 tools/generate_ponder.py` generates its language keys and bounded workbench structure. `--check` is part of Gradle verification. The NBT check compares decompressed content; committed compressed bytes are retained across Python/zlib versions. Other translations can override the normal Ponder language keys.

`linear_bearing.jade` adds optional inspection through Jade 15.10.6+neoforge. It also requires the active carriage adaptation. The server sends actual brake and transmission-link state; a client without that data does not invent a status. Hold Jade's detail key for assembly/recovery hints and mechanism distinctions. Gyro and transmission descriptions require their respective adaptations. Existing Create/Jade kinetic statistics remain owned by those integrations.

When updating Linear, Ponder or Jade, inspect scene registration, resource paths, blockstate properties, server-data providers and physical-side discovery. Check with each optional API absent and with guidance disabled. Review the tutorials in-game, including replay, later scenes, and the selected shader stack. Keep English internal-block names and any pack-owned advancement wording aligned with the shipping machinery contract.

## Upstream report candidates

The released carriage uses an obsolete physics lookup and a level-type guard that rejects the current server level; power detection alone therefore does not engage the lock. The original assembly handler can still convert the bearing after a caught assembly failure. A useful report should include preserved-source and successful-load controls, not only a stack trace.

Original magnetic discovery can select wrong-facing and other-dimension receivers. The replacement tests use a driven 32-RPM source to distinguish a real rejection from an uninitialized zero-speed endpoint. Report this separately from transmission accounting, whose approved contract changes the original generator-like behavior.

The stationary gyro rounding residue is independently reproducible by supplying a small positive rotation and then identical orientations repeatedly. It persists at 0.5 RPM. The physical stop/converter fixture confirms the same symptom. These are report candidates; no upstream issues have been submitted by this work.

## Distribution handoff

The supported upstream selections are Linear Bearing Modrinth version `8SJdnoXS` (project `ZTwCjE7O`) and, where required by the resolver, Linear Bearings Fix `MQ6XuOMQ` (project `xLxyRluH`). Their SHA-256 values are `29563cdb52a82ac7d8b3aca823cd47a15c443e06314df06ca1a635ae9e91a253` and `04afaaaf62951fc4cccfbb4468572fea2a876a6b5e872a6c60545d171fb21976`. Use a published, verified Skyweave: Compatibility Patches artifact before making a playable pack depend on these repairs. Keep all four switches enabled for this contract; defaults are true.

Apply the approved journal source changes together with the dependency selections, then run the owning journal generator, shared-fixes builder and change audit. Inventory objectives introduce equipment only; retain the separate native physics checks. Refresh the Atlas's runtime content after that selection is installed in an isolated exporter fixture. A world/ore resurvey is unnecessary for unchanged recipes and terrain. Static language resources remain pack-owned; no visual patch or new resource-only machinery implementation is needed.
