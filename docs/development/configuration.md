# Configuration interface

[Operator configuration](../configuration.md#configuration-interface) owns filenames, controls and application timing. This guide owns the consumer bindings, validation boundaries and dependency-update checks.

## Contexts and navigation

`ClientConfiguration` registers one native Configure entry. `ClientSettingsMenus` offers Local files and the running singleplayer server. The native chooser names these contexts; each opens a separate re:form `ClientEditingWorkflow`. Local preferences and next-server defaults never inherit server authority. Remote administration retains native operator commands and requires no client forms protocol.

`SettingsCatalog` owns concern → mod → purpose organization and stable routes. `ClientSettingsMenus` contributes typed categories, descriptions, placements, timing and actions. A document has one real binding and one retained editor in a workflow. Multiple placements focus the same draft; Iris world text and Island Sky preferences retain their original field IDs in one native rendering document. Navigation aliases use lowercase resource identities independently of camel-case configuration keys. Unavailable optional owners remain attributed destinations without opening foreign classes or inventing an editing baseline.

re:form owns forms, search, filters, focus, native text interaction, retained navigation, review and conflict workflows. Compat contributes labels and relations through `SettingsPresentation`, using existing translations, schema metadata and native spec comments. Field availability explains inactive hooks. Cloud-mode dependencies retain inactive values; changing their visibility does not mutate the document. Numeric units, exact input, bounded probability sliders, collections and native keybindings use shared controls. Do not introduce another widget draft or save path for an auxiliary view.

## Owned sources

| Consumer                  | Source and effect owner                                                                                  |
| ------------------------- | -------------------------------------------------------------------------------------------------------- |
| `StartupFeatureForm`      | Existing properties, explicit false and omitted defaults; immutable loader selection; next-process saves |
| `JsonPreferences`         | Existing admitted JSON profiles, staged for their declared startup, world or reload boundary             |
| `TerrainProfileForms`     | Exact generation/shape definitions and their required complete-pair review                               |
| `NativePreferences`       | Actual loaded NeoForge CLIENT or local COMMON configuration; native serializer and callbacks             |
| `HoofprintPreferences`    | Local map settings with actual immutable live publication and map-cache refresh                          |
| `IntegratedSettings.form` | The current native singleplayer host and its actual registered policy                                    |
| `ClientKeyBindings`       | Compat's registered keys in native Minecraft Options; no additional key registrar or file                |
| `ShaderOperation`         | Existing shader assembly and Iris reload through a bounded identified operation                          |

`JsonSettings.Definition` and the runtime codecs remain authoritative. `ReformJsonStore` derives typed map definitions from their declared fields or accepts an exact typed record definition. It validates original input with the existing full domain parser, then applies supported normalization only to the draft. Opening and no-op saving retain original bytes. A changed save preserves CRLF when present and validates the serialized candidate before persistence. Unsupported missing/unknown fields remain errors. An accepted older schema does not imply that its generation identity can open a current world.

`ConfigJson` provides strict object and text-array input independently of any editor. `ProfileJson.object`, terrain/city readers and transparency profiles use it. Preserve duplicate-key rejection, depth limits and exact scalar parsing when changing this seam. `ConfigFile` supplies bounded UTF-8, regular-file and trusted-path checks for runtime loaders. re:form's owned storage handles editing-source revisions, conditional persistence and readback. No render, generation or map callback reads an editor or file per frame.

The pre-migration editor/session/transaction helpers live only in `src/baseline/java` for retained comparison tests and explicit server probes. Their old GUI is removed. Product and sources JARs must exclude these helpers; `verify_package.py` checks retired class names as well as normal probe/upstream boundaries. A baseline-writer result is not evidence for a shared-owner save.

## Native configuration

`NativeFields` inspects actual `ModConfigSpec` bindings without reading a file. `NativePreferences` waits for registrations, then binds each admitted local CLIENT/COMMON owner before client setup. It reuses one cached `ClientRegisteredSettings` across forms and shortcuts. Remote SERVER caches and caller-selected paths are outside this adapter.

Foreign delegation is explicit. `NativeSettingsPolicy` pins Redstone Link GUI's CLIENT/COMMON fields and all supported Dynamic Waters COMMON fields by actual mod ID, version, filename, type and field path. Compat's navigation route remains independent of the native document ID. Native defaults, kinds, choices, ranges and mandatory restart declarations remain authoritative. Unsupported fields or versions fail admission; an absent or disabled integration does not resolve its target. Collection types are declared rather than inferred from an empty native list.

Whole-document validation is additional to native per-field checks. Sky Villages reuses `NeighborRule.Settings` so individually valid minimum/maximum gaps cannot bypass their coupled rule. `ReformDocuments.invalid` preserves structured primary and related field issues. Do not infer attribution from English exception text.

`SettingsCatalog` declares consumer timing. Dynamic Waters generation enters saved water identity; flow, visuals and audio have their inspected native readers, menu creation uses a new view, welcome text uses the next join, and the Tectonic option has a config-load effect on another mod's settings. Native save/readback does not certify arbitrary downstream effects or make callbacks transactional. Sky Villages values are captured by `NeighborConfig.beginServer` and belong to fresh-world identity. Preserve stronger native game/world restart requirements.

Journal-local buttons borrow a caller-closed `DocumentActions` actor from the same native binding. They serialize through its persistence lane, await its receipt and retain errors. Busy buttons cannot submit another action. A source change conflicts with an existing GUI draft; shortcuts do not copy that draft, write an independent file or bypass native validation.

## Reviews and application

`ProfileEditPlan.review` owns generation/shape compatibility. Both exact definitions participate even when one is clean or hidden. It validates the complete proposed pair and chooses a write order whose saved prefixes remain valid. re:form's required scope supplies fresh source checks before each effect, including after an earlier unrelated save. Expected state advances only after observed readback. A proposal with no safe order is refused before persistence. This is bounded conditional coordination, not cross-process atomicity or rollback over an external writer.

Saved bytes, staged requirements, effective values and failures are separate observations. Local startup/new-world/default files use staged owners without a runtime publication or apply-only operation. Hoofprint uses an actual live owner. Native registered configuration reports its native persistence and requirements. A successful save is not generic gameplay-application evidence. Unknown readback requires reconciliation; partial results keep their actual per-document outcomes and newer edits.

`PolicyDefinitions` supplies the exact Wayfinder/search definitions to server registration and integrated forms. `SettingsReload` binds their existing server-thread `SettingsPolicy` through `ServerSettings` and a verified live application. Native command branches and reload aliases share those owners. Authority is checked from the actual command source or current integrated player, connection, level and permission at execution. Stopped/replaced hosts and revoked permissions cannot reuse earlier admission. Save-success/apply-failure remains distinct, and apply-only retry uses qualified saved bytes without repeating the write.

Locator algorithms remain separate from settings infrastructure. `SearchRecipient`, `SearchReport` and shared work budgets bind replies, pages and cancellation to real requests and connections. [Wayfinder development](wayfinder.md) owns those semantics and their native lifecycle checks.

## Runtime consumers

`RenderingPreferences` retains one admitted native CLIENT spec. Island cloud height, void visuals and world-text hooks read its current native values. Cloud bounds use the pinned dimension codec domain, preserve the no-cloud sentinel and retain vanilla mesh invalidation. Text-display and nametag lighting/shadow hooks keep independent choices and native Iris pass/reset ordering. Shader cloud geometry remains outside native cloud-height authority.

`HoofprintPreferences.current()` projects the shared owner's immutable view on the client thread. The map refreshes bounds and step without disk access, clamps an existing scale and retains center/dimension. Initial zoom applies only when there is no remembered connection view. File inspection never replaces runtime state.

`ShaderOperation` calls the existing recipe assembler and `Iris.reload` on the render thread. It retains bounded busy/success/failure outcomes and permits an explicit retry. Failures do not claim rollback of already generated resources. Optional shader classes are reached only after their startup integration is admitted.

## Dependency changes

For a new or updated owner, reconcile `SettingsCatalog`, `ConfigLocations`, the actual codec/spec, declared fields, application timing, feature gate and package metadata together. Inspect native consumers and callbacks before altering timing. Recheck Dynamic Waters field/list-reference behavior and its Tectonic side effect, Redstone Link's exact registrations, Sky Villages' captured values, Iris pass ordering, and native key Options serialization when their dependencies change.

Keep `SettingsInventoryTest` complete over current JSON, properties and pinned native definitions. Native metadata inventories describe kinds and inspected readers; they do not establish live behavior. Qualify the changed binding through an actual Configure or command path, saved revision, real callback/consumer and relevant absent/disabled/wrong-version controls. Re-run the generation guard for saved-world boundaries. [Testing](testing.md) owns invocations; [verification](verification.md#shared-settings-owners) owns qualified artifact scope.
