# Configuration interface

[Operator configuration](../configuration.md#configuration-interface) owns the navigation, controls, filenames and application timing. This guide owns the implementation boundaries and update checks.

## Navigation and layout

`SettingsCatalog` assigns each feature to one gameplay system and mod group. `Page` merges groups sharing a system and mod; the same mod can have a separate page in another system. Features and files retain one data owner. Each page declares one ordered list of purpose sections and typed items: fixes, preferences, file settings and shader actions. Navigation/search and content walk that same composition, so section count, anchors and ordering cannot drift through independent page builders. Old physical-side groups supply metadata only. A section is not necessarily file-backed. Purpose and application timing are separate; omit empty/unavailable sections rather than inventing headings for every page. The catalog contains no optional API classes. Add catalog metadata and English strings when introducing a setting; navigation must not introduce another activation switch.

`FeatureSelection` remains authoritative for dependencies, versions, sides and prerequisites. The interface translates its inactive reasons without repeating the selection logic. Physical side appears in Details, not as the primary tree.

`ConfigurationOverview` owns page routes, expanded groups, localized search, per-page bookmarks/focus, explicit selected destinations, and the staged save set. `SettingsSearch` matches resolved strings with case and diacritic normalization; IDs are secondary matches. JSON field descriptions and registered specs participate without searching file contents or values. Localized mod names determine page order inside each system; authored section order and owning schemas determine setting order. JSON field order comes from the owned definition, independent of saved key formatting. Registered scalar fields are real inline destinations. Journal controls keep their existing shared preference helpers; their file does not produce duplicate controls.

Search uses Minecraft's `EditBox`, retained across result filtering and layout rebuilds. Its responder also runs on cursor/selection movement; ignore unchanged text and replace only the navigation results when filtering. Recreating the input here loses selection, horizontal scrolling and clipboard-editing state. Respect native Command shortcuts on macOS and Control elsewhere; do not add another string editor or global key interception. Preserve a restored focus owner instead of advancing it again through initial Tab navigation after a rebuild.

A clicked destination uses vanilla `ChatFormatting.YELLOW` in the navigation and its content title. It remains the sole explicit content highlight until another destination is chosen; scroll/bookmark tracking remains separate. Ordinary titles use WHITE and descriptions/category text use GRAY. Feature details use a plain lowercase `i`, with `minecraft:uniform` applied only to that glyph. Its transparent button has a normal hit target, yellow hover/focus text, underline, localized tooltip and explicit narration. Other labels keep the ordinary Minecraft font.

`SettingsPanel` measures rows and optional two-column field pairs. Those same bounds drive drawing, clipping, hit testing, focus scrolling and section anchors. Ordinary native widgets retain keyboard behavior and narration. Columns appear only when both meet the minimum measured width; complex list rows span the pane. Purpose groups use unfilled, clipped DARK_GRAY outlines interrupted by their measured eyebrow headings. Group padding participates in the same column/anchor measurements; borders do not create another input or scroll owner.

`SectionNavigation` keeps an explicitly chosen clamped destination until scrolling moves. Bookmarks preserve section-relative offsets; an end bookmark stays at the end when columns or wrapped text change the document height. Stable route/control keys restore page focus after rebuilds and auxiliary screens.

Native pointer dispatch assigns focus after invoking a button callback. A callback can rebuild the widgets, leaving focus on a detached control. The overview repairs that root ownership after dispatch. Preserve this distinction: a widget's focused flag alone does not establish keyboard routing. `ClientResourceRevision` invalidates measurements after resource/font reloads without changing gameplay state.

## File ownership

| Owner                        | Files and authority                                                                                           |
| ---------------------------- | ------------------------------------------------------------------------------------------------------------- |
| `StartupEdits`               | Feature properties; immutable running selection plus next-start delta                                         |
| `JsonEdits` / `JsonSettings` | Cataloged strict JSON files; actual profile/settings parsers validate every candidate                         |
| `RegisteredEdits`            | Staged scalar fields in loaded local NeoForge CLIENT/COMMON TOMLs; native specs, serializer and reload events |
| `JournalPreferences`         | Immediate journal values shared with the journal bookmark                                                     |
| `ShaderAssembly`             | Existing recipe/rebuild workflow; not a general file editor                                                   |

`JsonSettings` uses only pure profile types and parsers. The generation JSON parser is separated into `ProfileJson` because the generator adapter's API signatures reference optional Sky Archipelago classes. Do not introduce such signatures into classes loaded by minimum installations.

Owned JSON supports nested groups, scalar numbers/booleans/strings and string lists. Native river/lake settings register their codec only after the optional terrain integration is admitted; minimum installation classes never reference foreign signatures. Dotted field paths identify nested values without introducing a second file owner. Numeric drafts retain exact text, including invalid values. Full profile constructors enforce integer types, bounds and cross-field invariants. The bounded JSON reader rejects duplicate keys, nesting beyond 16 levels and trailing documents. Owned strict files are pretty-printed on save; comments and unknown fields are not supported by their runtime schema. Missing or malformed files are distinct from defaults. Opening a screen never manufactures configuration.

`RecordSettings` derives owned field types, defaults and nested order from the actual records. `SettingSpec` declares scalar bounds, units, semantic groups and collection constraints used by both record constructors and `SettingDefinition`; the full runtime parser retains cross-field and generation checks. Integer and long drafts parse exactly, decimals retain their authored precision, and incomplete numeric input remains a draft. `SettingIssue` identifies field failures separately from document/cross-field failures. The optional native river codec remains its own authority. `SettingsPanel` pairs adjacent field rows when wide enough; these pairs are a layout convenience, not an authored semantic relationship.

JSON, registered TOML and startup properties share `EditSession`. A save captures immutable submitted values; acknowledgement accepts that saved baseline while preserving newer edits, including a Revert made during the save. Reload or another acknowledged submission invalidates older results. Backends retain their own validation, native serializers, file conflict checks and readback before acknowledgement.

Owned startup profiles and BlueMap settings use `ProfileJson.object`, which delegates to that same strict reader before validating their profile fields. Do not replace it with Gson's permissive object parsing: duplicate keys would then have different meanings at startup and in the editor.

`ConfigFile` provides bounded UTF-8 snapshots and trusted catalog filenames under the local config directory. It rejects traversal, symlinks, nonregular files and input above 128 KiB. There are no persistent handles, locks or watchers and no per-frame file parsing. Startup profiles and feature switches, map screen-open reads and BlueMap reloads use that same file reader before parsing. Native NeoForge specs retain their own loading and writing. Read at startup, open, explicit reload, registered readback and save.

Owned-file saves compare the captured baseline, validate output, write a same-dir temporary file, preserve POSIX permissions where supported, recheck the baseline, and atomically replace. Unsupported atomic moves fail without an in-place truncation fallback. Readback must match and validate before the draft clears. Writers from this implementation serialize briefly per canonical path. These comparisons cannot provide cross-process compare-and-swap against noncooperating external writers; a narrow final-check/rename race remains.

Properties serialization replaces only edited logical properties, preserving unrelated comments, escapes, continuations, ordering and line endings where possible. Explicit false values and omitted defaults keep their loader semantics.

`SettingsCommit` preflights the complete pending set before the first write. It then reports committed files and the first failure. Each successful owner clears its own draft; failed/unattempted drafts remain. It does not promise a multi-file transaction or roll files back over possible external edits.

## Registered scalar forms

Only cataloged, actually loaded CLIENT/COMMON files whose resolved local path matches the config root qualify. Remote SERVER config caches are never targets. `RegisteredEdits` discovers scalar bindings through `ModConfigSpec`; field labels reuse explicit spec translations or NeoForge's mod-ID/configuration fallback. Native validators/defaults/ranges and restart types remain authoritative. Single-field files render as their field rather than adding another file heading and sidebar destination. File inspection remains in Status & files. Village cross-field validation reuses the runtime `NeighborRule.Settings` invariant. Unsupported shapes are reported, not silently flattened or guessed.

Edits remain local drafts without mutating native values or writing per keystroke. They join the existing Save/Discard/Reload scope. A save validates the candidate, compares bounded disk and registered-value snapshots, then briefly applies changed bindings and calls the owning spec's `save()`. NeoForge keeps its serializer, comments, reload events, watcher and restart-cache contracts. These files are not serialized by the JSON writer. Readback must validate and match intended values.

If writing fails while the file remains at its baseline, restore only edited native bindings that still match this attempt; retain the draft. If disk contains the intended values but a later reload callback failed, report a written-file warning and keep those saved values rather than rolling them back. Unexpected external readback retains the draft for review. This is optimistic coordination, not a lock over native watchers or arbitrary external processes.

Registered immediate values apply after Save; world/game-restart values retain their native timing. Journal preferences still save immediately through their shared helpers. This difference is shown explicitly; putting a field under Preferences does not itself choose its persistence or restart policy.

Application timing describes the actual consumer. Search limits currently load during terrain integration registration; Wayfinder policy is captured per server, and Hoofprint preferences load on map initialization. Generation profiles and climate metadata remain bound to saved generation identity. Dynamic Waters mixes generation, gameplay and presentation fields in one common file; a file-level label alone cannot establish physical ownership or prove that a consumer refreshes live. A local common config or remote server cache is not an administrative connection.

The catalog's current generic TOML forms are embedded rather than redirecting to screens constructed solely for them. A genuine upstream screen can be a future explicit destination when needed; use an honest Edit label and preserve its owner. Do not create another generic modal merely to host a single field.

## Verification and updates

Run `./gradlew --no-daemon check build clientProbeJar`. Focused tests cover page ownership/search, anchor reflow, strict JSON, integer/cross-field validation, properties preservation, conflict retention, bounded/symlink/read-only refusal and partial multi-file outcomes. Source checks do not replace native execution.

The existing client probe enables `-Dskyweave_compat.probe.configuration=true` only in a disposable directory with `config-review-fixture.json`. Numbered commands in `config-review-control.json` operate real screen clicks, keys, fields, wheel, scrollbar dragging, GUI/window sizing and language reload. State records native widgets, routes, bookmarks, JSON/startup drafts and feedback. Clear control/state files between launches. The probe is not distributed with the mod.

Verify minimum/full client loading and dedicated-side exclusion; first/last anchors and reflow; empty/localized search and inline registered fields; keyboard/scrollbar input and narration structure; drafts across details/dialogs; real properties, JSON and TOML saves/readback; external conflicts and invalid input. Confirm desktop availability before focus/captures and retry interrupted attempts. Keep playing worlds, configs, servers and LODs outside fixtures.

For Minecraft/NeoForge updates, recheck `ScrollPanel` clipping/focus/narration, `EditBox` responder/caret/clipboard behavior, focus restoration, the uniform font resource for the information glyph, named palette colors, registered value/spec save/reload events, TOML parsing, watcher behavior and restart caches. For parser/profile updates, update JSON catalog forms/strings and shared validators together. Check atomic replacement on supported platforms, optional classloading, inline field ownership and journal preferences through both entrances. Keep startup selection immutable and generation identity checks authoritative; this UI is local configuration, not remote admin.

Native river codecs own values and validation; `RiverConfiguration` supplies an explicit presentation order for the file and shared form. Recheck that ordered field set when upstream adds or removes codec fields, including anchor/companion groups and optional defaults.

## Owned profile schema evolution

`JsonSettings.Definition` may normalize an explicitly supported old schema into the editor's in-memory saved view. `JsonEdits` retains the exact original bytes for conflict detection; opening or validating never writes a normalization. The ocean reader accepts only the exact released eight-field schema or the complete current schema, preserving old values and supplying new defaults. An actual edit saves the full validated candidate through `ConfigFile`; conflicts and failed writes retain the draft and original file. Other strict profiles retain exact-field parsing. This seam is not permission to merge arbitrary missing keys or silently discard unknown values.

Terrain schema acceptance and saved generation identity are independent. A readable older configuration can still produce an incompatible world identity; retain the new-world confirmation and guard rather than rewriting its saved marker.
