# Encounter adaptations

Four independent startup switches own server decisions: `minecraft.patrol_probability`, `variantsandventures.spawner_emissions`, `variantsandventures.local_water` and `variantsandventures.lithostitched_trials`. The first needs only Minecraft; emission selection needs V&V 1.0.30; Murk additionally needs the active Compat Sky Archipelago 1.4 preset. Missing or explicitly disabled adapters select no foreign mixins or version checks. The trial-alias bridge needs V&V 1.0.30 and Lithostitched 1.8.1, independently of Sky Archipelago. The client does not supply authoritative encounter policy, and these adapters add no packets.

Encounter tuning is separate from the saved terrain fingerprint and does not require a world reset. Existing saved spawner identities and generated trial chambers are not migrated. Configuration and alias-resource changes require a full server restart; this bridge does not provide live registry rebuilding.

## Patrol probability

`config/skyweave/skyweave_compat-encounters.json` defaults to a 20% patrol check. That value calls the original `nextInt(5)` operation exactly. Other values replace only that gate with one percentage roll; 0 and 100 need no random draw. Native intervals, calendar/daytime gates, gamerules, player selection, village exclusions, loaded terrain, group size and leader handling remain native. A percentage is an attempt probability, not a realized per-player encounter rate.

`PatrolChanceMixin` slices between `ServerLevel.isDay` and `players`, then targets only its first integer draw. Recheck this slice, its injection count and a five-player control on Minecraft updates. Changing every draw with a bound of five would also corrupt player selection and later group movement.

## Ordinary spawner selection

The same JSON file defaults to `NATIVE_TYPE`, leaving native dungeon conversion and emissions unchanged. `PER_EMISSION` selects one enabled matching mapping by the lowest distinct priority within its zombie or skeleton family, then makes one configured roll. The default chance is 5%; editable rule tags default to V&V's six existing biome mappings. Unknown modes, duplicate keys, non-finite/out-of-range percentages, invalid tag IDs and conflicting enabled priorities fail validation. Settings are startup-owned and require a full game/server restart.

Per-emission mode requires all six native V&V `*SpawnerChance` values to be zero in `config/variantsandventures.json`. Startup refuses a mixed configuration with an explanation; Compat never silently writes that foreign file. Native 30% ordinary replacement and trial aliases are independent. When returning to native conversion, select `NATIVE_TYPE` and deliberately restore the desired foreign dungeon chances. Do not change these related policies live. Existing authored variant spawners stay variant spawners; no save migration guesses their provenance.

`VariantSpawnerMixin` retains the native baseline `SpawnData` and creates a transient copy only for a selected base-type emission. Every loop iteration resets that local baseline. Selection precedes collision/rule/entity work and uses the actual sampled position, including native seeded 3D biome zoom over existing chunks. A missing selected noise cell refuses replacement without a chunk request. One choice governs the bounding box, type, NBT, custom rules, initialization and equipment; native realization adds the mob once. Failure never rerolls or inserts a fallback mob. Names, equipment and applicable NBT survive; saved `SpawnData`, `SpawnPotentials` and preview identity are not rewritten.

Mixed base/variant identities share the native local nearby-count box. The zombie family is zombie/Husk/Gelid/Thicket; the skeleton family is skeleton/Stray/Bogged/Verdant. Other custom types, trial spawners, minecarts, eggs and passenger-bearing definitions retain native behavior. The adapter verifies the loaded block entity owns the exact `BaseSpawner`; sharing a position with a spawner is insufficient.

Recheck native local captures, `getSpawnAABB`, `SpawnPlacements`, `loadEntityRecursive`, NeoForge position/finalization events, equipment, nearby counting and `tryAddFreshEntityWithPassengers` together on updates. V&V is compile-only and hash checked. Recheck its dungeon conversion, entity-enable flags and biome tags before changing that pin; do not target another mod's mixin class.

## Murk water datum

`MurkDepthMixin` replaces only the native natural-depth sea-level read in marked worlds. `MurkGoalMixin` replaces the custom goal's constructor-captured water-height reads with the current loaded column datum. The datum is the first non-water height above the connected column, equivalent to last-water Y plus one; a solid cover does not imply an air opening. Separate layers at the same X/Z remain separate.

The original strict `Y < datum - 5` boundary, water-below check and one-in-forty roll remain intact. Weight four, group one, biome tags and non-natural/trial paths remain native. No darkness rule or population increase is added. Missing/dry/budget-exhausted water refuses the adapted depth/goal; foreign generators keep their original global sea level. `LocalWater` reads existing full chunks and scans at most 256 cells, without terrain or watershed planning. A goal samples once per method and shares that result across its reads.

Recheck `MurkEntity.isValidSpawnDepth`, natural versus other reasons, `TargetAboveWaterGoal`'s three height reads, native water-navigation lifecycle and the local datum boundary on V&V updates. Spawn correction alone cannot repair a goal still aiming at global sea level. This is habitat correctness, not natural population or full combat qualification.

## Trial alias interoperability

Lithostitched 1.8.1's `RandomEntries` replaces the native trial alias binding types. V&V 1.0.30's updater only extends native `Random`/`RandomGroup` bindings, so its four configured trial additions otherwise never join this combination. The independent `variantsandventures.lithostitched_trials` adapter normalizes only the known melee and paired ranged/slow-ranged extension slots after Lithostitched replaces those slots and before level creation, then replays V&V's original `init`. Only two fresh, unextended slots qualify for replay; partial/already extended inputs retain their original bindings with a diagnostic. The original updater retains ownership of all four native enable switches and additions; no duplicate updater or new encounter policy is introduced.

A qualified uniform row matrix becomes equivalent native bindings with the same row order, paired targets and one random draw. Unknown slots remain native. Small-melee and Breeze content are untouched. Empty, mismatched, inline/unregistered or over 256-row inputs retain their original binding with an explicit diagnostic; no partial row set is accepted. Normalization itself is idempotent; this does not change the upstream updater's once-per-start lifecycle.

Recheck LithostitchedInternalHooks.onServerAboutToStart after its modifier pass, both dedicated/integrated pre-loadLevel hooks, V&V’s earlier NeoForge event and the native trial registry consumer when either mod changes. `RandomEntries.allTargets()` returns an empty stream in this pin even though its resolver has choices, so reference audits must inspect its actual holder sets. Use registry-aware codec operations when retaining aliases with pool holders; plain JSON operations can recurse through inline fallback pools. Verify pre-extension seeded outcomes and subsequent RNG state, all four trial switches off, restored five-choice families, explicit adapter false and dedicated-server execution.

## Trial resources

Static trial repairs belong to the modpack's Resource Patches, independently of these runtime switches. Its two pool overrides reuse V&V's existing ranged templates and an owned native `append_static` processor; no third-party NBT is bundled here. The pack's [encounter guide](https://github.com/mannie-exe/create-skyweave/blob/main/docs/development/encounters.md) owns reference closure, cadence, alias policy and resource-update procedures. [Testing](testing.md#encounter-fixtures) owns native controls and qualification limits.
