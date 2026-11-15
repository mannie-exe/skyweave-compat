# Rendering adapters

These optional physical-client adapters do not require Sky Archipelago or the Skyweave modpack. [Configuration](../configuration.md#adaptation-switches) owns activation and versions. No renderer classes load on dedicated-server paths.

## Main-menu branding

The optional physical-client `MainMenuBrandingMixin` wraps the single immutable-list build in NeoForge 21.1.265's `BrandingControl.computeBranding`. It retains the completed native/third-party contributions and inserts one localized line before their last line. Keeping the original bottom line in place avoids adding a new collision with the native copyright row. Original relative order, reverse/no-Minecraft views, rendering, spacing and fade alpha remain owned by NeoForge. Its ordinary resource reload listener invalidates both branding views; there is no per-frame metadata/file parsing or separate version configuration.

`MainMenuIdentity` reads the actual loaded Compat version and the Resource Patches `IModInfo.getModProperties()` map. `PackIdentity` accepts the versioned pack contract and falls back on absent or malformed metadata. The pack's [identity builder](https://github.com/mannie-exe/create-skyweave/blob/main/docs/development/build.md#installed-pack-identity) owns the property schema and DEV/release stamping. This adapter does not alter early loading, network brand identifiers or dedicated-server behavior.

At title initialization and resize, `MainMenuLayoutMixin` reserves two pixels above the footer by closing excess vertical gaps between existing button rows. It preserves their X positions, sizes, actions and native copyright button, and never moves a row down or across the native logo. Spacious layouts remain exact. If third-party rows cannot fit between the logo and footer, their native layout remains intact. Native count/copyright text can already exceed the width of a very narrow window; neither line is truncated or hidden here.

For NeoForge/FML updates, inspect the exact branding producer, its one build invocation, both cached views, reload invalidation and native title rendering. Check custom-property typing, ModernFix's builder contribution, title initialization/button rows and the logo/edition bounds before changing hooks. Retain minimum/disabled/server absence controls and actual menu/reload/scale captures; a parsed metadata map alone does not establish the visible result. An equivalent native branding extension may retire these hooks after the same controls pass.

## Glass backfaces

The default-on glass adaptations retain native CT, visibility and render layers while adding inward-facing artwork. Window-material and glass-axis switches still decide whether an interface is visible; backface adapters decide how an admitted surface renders from behind. `create.glass_backfaces` and `fusion.glass_backfaces` retain their existing owners. `create.copycat_backfaces` qualifies Create 6.0.10 panel/step assembly independently of Fusion and Copycats+. `copycats.glass_backfaces` qualifies Copycats+ `3.0.9+mc.1.21.1-neoforge` with Create 6.0.10. All are client-only startup switches; explicit false prevents their optional target hooks from loading.

### Source, assembly and finalization

Static models use `PlainGlassModels`; Create CT and Fusion use small final-output adapters. Create's CT tile selection and Fusion's block processors run before reversal. Fusion installs its vanilla pane-edge modifier after NeoForge's baking event, so pane wrappers are installed after `applyModelModifiers`, preserving native hidden horizontal edges. Create's Tiled Glass remains on the plain-model path. Inventory models are unchanged.

Copycat material sampling enters `GeometryScope.source()`, suppressing only Compat's additions. Native CT, material mapping, crop/rotation, part occlusion and always-render handling remain active. Create receives backfaces after its final five-argument `getQuads`. Copycats+ tags the actual mapped material and part at its crop seam and finalizes its protected assembled output after native occlusion, before its public cull-group conversion. Supported Simulated models inherit that qualified provider; this does not qualify every specialized block or moving renderer.

Copycats+ RenderSession normally caches by state, RandomSource identity, ModelData identity and layer. A source read calls its native renderer without reading or replacing that finalized cache. The exception-safe, nested source scope therefore separates source and final output even for identical native cache keys. There is no persistent Compat mesh/world cache. Existing two-sided artwork is preserved; deduplication compares owner/material/part/layer, sprite and packed attributes rather than positions alone or a marker surviving foreign geometry clones.

### Contact snapshots and geometry

`GlassSurfaceContacts` reads six immediate neighbors from the supplied mesh view, including its native block-entity ModelData. Qualified Copycat providers expose original assembled faces; explicitly admitted static wrappers and native simple/weighted/multipart models supply static contacts. Empty dynamic material data and unknown dynamic providers retain a bounded fallback. Sodium 0.8.13's NeoForge LevelSlice mixin already supplies its copied platform ModelData through this interface; no global ClientLevel lookup or additional Sodium bridge is used. Captures retain immutable geometry and material/part identities, never views, entities or levels, and never request chunks.

Contacts use the origin block's local coordinates and include internal part planes. `SurfaceRegions` partitions rectangular surfaces; `PlanarSurface` clips the finite convex triangles/quads needed by supported slopes and partial forms. Bounding rectangles do not fill diagonal holes. Original faces, source cull groups, UVs, tint, shade, AO, color, packed light and normals remain intact. Only newly added reverse overlap moves 1/256 block into its final owning solid, limited to a quarter of a known thinner parallel span. Exposed reverse areas keep their position. This is render geometry, not collision or physics authority; native translucent blending/sorting remains authoritative.

Budgets admit at most 1,024 source faces per assembled output/neighbor layer, 2,048 contacts and 4,096 finalized faces. Polygon partitioning admits at most 512 contacts and 256 pieces; rectangle partitioning retains its bounded grid. Nonplanar, excessively subdivided or unsupported inputs retain original or unsplit backface behavior with once-per-reason resource-generation debug diagnostics. Those fallbacks do not establish repaired contact separation. Arbitrary intersecting meshes, fluids, BER-only models, DH geometry and unqualified moving Sable/contraption paths are outside this contract.

### Resource profiles

`assets/<namespace>/transparency/*.json` supplies data-only schema-1 profiles. Native resource priority replaces the same ID; a second profile selecting the same state/provider creates a conflict and retains original rendering for that decision, rather than silently unioning rules. Explicit exclusions and `enabled=false` are supported. Providers are the fixed implemented IDs `plain`, `create_ct`, `fusion` and `material`; resources cannot name executable classes or add another adapter. A material rule also respects the owning family's explicit startup false selection, even when that family renderer is absent.

```json
{
  "schema": 1,
  "enabled": true,
  "feature": "fusion.glass_backfaces",
  "providers": ["material"],
  "targets": [{ "block": "minecraft:blue_stained_glass" }],
  "exclusions": []
}
```

Targets select an explicit block or one implemented family, optionally restricted by exact native state properties; exclusions use the same form. Unknown properties/values, duplicate JSON keys, excessive nesting, unrecognized fields/providers and malformed types reject that profile. Absent optional block IDs select nothing. Limits are 128 profiles, 64 KiB per file, 128 selectors, 16 properties per selector, 32,768 states per profile and 131,072 compiled decisions. Meshing reads compiled immutable state decisions; it does not parse JSON or scan profiles per quad.

Profiles are prepared at native model-reload entry, before wrappers bind their immutable generation. Model publication temporarily closes contact admission, then publishes that generation at native apply return. Contact data is admitted only when the model/profile/contact generations agree with the published generation. Reload recreates wrappers without nesting them or retaining old sprites. Startup adapter changes require restart; profile/resource changes use native reload.

Builtin profiles cover vanilla full glass/panes/tinted glass, Create CT/windows/Tiled Glass, Rechiseled pillars, Macaw mosaic blocks/panes, caramel bricks, bubble gum, ornate grate and industrial grating. Create's general family excludes its Tiled family to retain one provider owner. Macaw's ordinary/barred/Gothic artwork is already two-sided; one-way glass and scaffolding remain excluded. The pack's Resource Patches supplies an exact registered-ID replacement for the Macaw mosaic profile. Changed topology, CT declarations, modifier order or layers require new qualification; a texture-only replacement can retain the contract. Static authoring belongs to the pack, shader edits to shader-lab, and runtime geometry to Compat.

### Updating providers

For Create/Catnip, recheck five-argument output/gather descriptors, crop/clone and always-render behavior, material data, CT selection, source cull groups and native panel/step/bar specializations. For Copycats+/Simulated/Connected, recheck mapped ModelEntry/property identity, completed MATERIAL-to-MATERIALS gathering, final assembly/cull/conversion order, RenderSession keys and its thread-local lifecycle; independently review acquisition/visibility and physics policy. Do not reverse specialized bar extrusion merely because a facade is present.

For Fusion, preserve the later pane modifier boundary, processor dispatch and model-publication order. For Sodium, verify actual mesh-view data snapshots, BLOCK vertex format/compact position precision, batching, native visibility and worker rebuilds. For Iris/shaders, qualify native material classification, CT, pane seams and sorting separately; these adapters do not rewrite shader materials. Decorative updates need actual registry/state/model and intentional two-sided/one-way checks. Update exact pins, gates, metadata, package checks and native evidence together; retire hooks only after a released upstream implementation passes equivalent controls. [Verification](verification.md) records actual supported paths and their limits.

## Window faces

`create.window_culling` selects `CreateWindowCullingMixin` for Create's `ConnectedGlassBlock` and `WindowBlock`. Native `skipRendering` decisions remain unchanged for matching blocks. A different window material no longer suppresses the boundary face through these overrides. Minecraft still performs ordinary shape occlusion, including opaque neighbors; native models keep their `cullface` declarations.

Create's `CTModel.createCTData` normally skips hidden directions. Exposing the boundary through the native visibility decision also lets Create calculate its existing CT context. Removing model culling declarations alone cannot ensure that context exists. The adapter does not replace CT textures, force calculations for every hidden face, change render layers or process other namespaces' models.

Fusion and Rechiseled use independent rendering paths. Rechiseled's connecting models use Fusion predicates, including state/axis conditions, rather than Create's CT model. Different-material boundaries already work in the tested variants.

`rechiseled.glass_culling` independently selects `RechiseledGlassCullingMixin`. The vertical `RechiseledGlassPillarBlock` path hides matching shapes even when the neighbor has another axis; the horizontal path already preserves that boundary. The adapter retains a face when the same block has a different axis, making the two directions consistent. Matching-axis decisions and native slab/stair shape handling remain unchanged. It uses Minecraft's AXIS property, which is the property used by the pinned Rechiseled implementation.

This second correction requires Rechiseled, without linking Create or Fusion APIs. Rechiseled's own required dependencies still apply. No Fusion model or texture processor is replaced. Its pane-culling option addresses pane geometry, not these full-window boundaries. When updating Rechiseled, inspect both axis paths and property identity. Retire this hook when upstream respects differently oriented glass on both sides while retaining its native shape rules.

When updating Create, inspect both block overrides and `CTModel` against the released artifact. Retire this adapter when upstream preserves visible mixed boundaries and their CT context. Recheck horizontal/vertical matching windows, mixed corners, opaque neighbors, native axis variants, shader rendering and resource reload. Do not replace a material-specific rule with a blanket policy for all transparent blocks.

Upstream context: [Create 6.0.10 CT model](https://github.com/Creators-of-Create/Create/blob/mc1.21.1-6.0.10/src/main/java/com/simibubi/create/foundation/block/connected/CTModel.java) and [Fusion issue 148](https://github.com/SuperMartijn642/Fusion/issues/148). The latter explains the separation between texture connections and block culling; it is not an exact upstream report of this Create defect. Native results and remaining limits belong in [verification](verification.md).

## Distant constructs with shaders

`rendering.construct_distance` requires SSRD, Sable and Iris on the physical client. It scopes Iris's `far` uniform to the greater of its current value and SSRD's effective, server-capped range. It does not change view distance, tracking, simulation or the projection. `rendering.construct_occlusion` independently adds DH opaque-terrain depth testing; DH is not required by the distance correction. Both switches default on and require a game restart when changed.

`ConstructDrawMixin` wraps the actual chunked draw after shader application and SSRD projection setup. For single-block meshes it carries a bounded classification through the immediate uploader; `ConstructBufferMixin` applies the uniform scope only after that uploader reapplies its shader. Nested and exceptional exits restore the classification. A scope around `RenderType.draw` alone is too early. Sable 2.0.6 selects this dispatcher through its Sodium reach-around renderer. `ConstructUniforms` admits only a bound Iris `ExtendedShader`, an active world pipeline with its main target bound and a perspective projection, outside the shadow pass. Locations belong to that shader instance, never a global GL-ID cache. It restores uniform values and the final program binding on nested and exceptional exits. Missing uniforms leave the corresponding responsibility inactive.

The DH adapter injects a dormant fragment guard into Iris's legacy and core terrain transformations. Only the construct draw enables it. It leaves original outputs, alpha tests and material logic intact, skips programs writing custom fragment depth, and reserves one dynamic sampler only if Iris has a spare unit. No copied shader ZIP, upstream runtime library or additional framebuffer is shipped. Iris's own GLSL transformer is a compile/test dependency.

DH's opaque-pass event authorizes depth for the current Iris frame and pipeline. Before DH's translucent pass, sampling uses its live depth texture; afterwards it uses Iris's preserved opaque snapshot. A foreign frame/pipeline, inactive DH integration, missing texture or matching draw-depth attachment disables the guard. Pipeline destruction clears ownership. The adapter owns no GL textures. Each fragment uses the actual construct projection and viewport and Iris's matching DH projection to compare view-space distances. Cleared depths do not occlude. The comparison moves each depth two floating-point steps toward visibility and adds 1/64 block of view-space allowance, avoiding an arbitrary large world-space bias. This is conservative around precision limits; it does not repair DH's terrain approximation or supply missing LODs.

Opaque DH depth deliberately excludes water as an opaque blocker. Native and DH water retain their shader's absorption, sorting and composition. This adapter does not introduce order-independent transparency. Later shader fog and color processing remain authoritative: Complementary's final fog can still hide an opaque distant construct when DH rendering is disabled. BSL and Complementary must therefore be qualified separately rather than promising every shader draws beyond its native fog range.

Block entities use Sable's separate buffered entity-renderer path. Flywheel and Colorwheel use their own embedded visual programs; neither is implicitly covered by these terrain-program hooks. Existing material, model-data, single-block, framebuffer and Colorwheel adaptations remain independent. Do not identify a construct through the mere declaration of `SableEnableNormalLighting`; Iris does not wire that uniform automatically.

On updates, inspect Sable's selected dispatcher, both draw boundaries and Minecraft's immediate uploader apply/draw order, SSRD's range/cap and projection scopes, Iris's `ExtendedShader` construction, both GLSL transformers, sampler allocator, frame counter and DH texture-copy timing. Recheck actual DH draw/reconstruction matrices, viewport/render scale, depth conventions, custom fragment depth, texture-unit limits and pipeline destruction. Retire a hook only when a released upstream repair covers the same responsibility and passes the corresponding native controls.

Upstream tracks [shader visibility](https://github.com/RanoldStranold/SSRD/issues/50), [DH/shader interaction](https://github.com/RanoldStranold/SSRD/issues/68), and [distance fading](https://github.com/RanoldStranold/SSRD/issues/64). See [verification](verification.md#ssrd-shader-adaptations) for tested scope.

## Framebuffer state

`ShaderWrapperMixin` incorporates Veil Iris Fix 0.1.0. It wraps the complete Veil shader apply/clear methods, retaining the shipped Veil Iris injection inside the wrapper. It saves separate read/draw targets, uses Iris's active world target only when appropriate, and restores prior bindings for offscreen/shadow contexts and on normal or exceptional cleanup. Repeated apply calls retain the first target. The exact Veil 4.3.2 nested artifact comes from Sable 2.0.6; Iris is pinned in `build.gradle`. The standalone patch must not activate alongside this adapter.

Upstream reference: [Veil PR #213](https://github.com/FoundryMC/Veil/pull/213). Retire when the selected released Veil artifact contains complete framebuffer and Iris pass handling. Review both shipped Veil injections and Iris `bindDefault`, `shouldOverrideShaders`, and shadow-pass state before updating either dependency.

## World-text shadows

`rendering.text_display_shadow_pass` and `rendering.nametag_shadow_pass` are independent, default-on physical-client policies requiring only the pinned Iris API. They do not depend on terrain, Veil, Sable or Colorwheel. Metadata selection omits their mixins entirely when unavailable, disabled or on a dedicated server.

`TextDisplayShadowMixin` cancels the concrete `TextDisplayRenderer.renderInner` overload during [`IrisApi.isRenderingShadowPass()`](https://github.com/IrisShaders/Iris/blob/1.21.1/common/src/api/java/net/irisshaders/iris/api/v0/IrisApi.java). It skips glyphs and background together without also injecting into its generic bridge. Item/block display renderers are untouched.

`NametagShadowMixin` guards the base `EntityRenderer.renderNameTag` method. Its priority is 1100, before Iris's priority-1000 nametag material setup. Cancelling after that setup would skip Iris's RETURN restoration and leak material state. The transformed-mixin test includes the exact Iris mixin and checks this ordering. Ordinary nametag rendering retains Iris's setup/restoration, and entity-body rendering is not cancelled. Custom renderers bypassing the base method remain outside coverage.

These policies affect shader shadow-map geometry, not native full-bright display lighting, glyph drop shadows, entity blob shadows or post-processing. They do not classify entities by server tags or introduce a synchronization protocol.

When updating Minecraft or Iris, review concrete/bridge descriptors, base-renderer overrides, Iris material setup/reset hooks and priorities, and the public shadow API. Recheck transformed injection count/order, normal/shadow behavior and both physical sides. Retire or revise a hook only after confirming equivalent behavior in the selected upstream version. The optional native scene is owned by `tools/fixtures/rendering`; dated results belong in [verification](verification.md).

## World-text lighting

`rendering.text_display_lighting` and `rendering.nametag_lighting` independently route selected glyphs and backgrounds through Iris's native fallback programs. Both default on and require only the pinned Iris client. They are separate from cast-shadow exclusions. Ordinary brightness, depth, alpha, billboarding, text contents and native visibility remain with Minecraft.

Full-bright display data only overrides packed light. The selected Complementary/Euphoria entity program additionally applies directional lighting and fog and caps block-light input. Nametags retain subsequent shader processing even in their special material branch. The fallback route avoids that entity program; later exposure, grading and compositing can still affect displayed color. It does not promise exact RGB output or identical results with every shader.

The concrete text-display renderer and base `EntityRenderer.renderNameTag` receive a buffer-source adapter before emitting vertices. `WorldTextRenderType` keeps those batches distinct from ordinary font rendering, forwards native render states and Iris transparency metadata, and marks setup/draw rather than just the vertex-emission call. This matters because Iris defers and groups entity buffers. It deliberately does not subclass Iris's `OuterWrappedRenderType`, which `wrapExactlyOnce` would unwrap and thereby erase the classification.

`WorldTextScope` restores nested state through exceptions. `WorldTextShaderMixin` selects a fallback only for the current pipeline's `TEXT`, `TEXT_INTENSITY` and `TEXT_BG` lookup during that scope. Shared shader-map slots are never replaced. Signs/block-entity font programs, hand text, shadow programs, entity bodies, item/block displays and ordinary world/HUD text retain their owners. Custom nametag renderers bypassing the base method are outside coverage.

Fallbacks are created on the render thread through Iris's own factory, which registers them with `loadedShaders` and owns their destruction. Each pipeline caches at most three programs and 256 recent font-type wrappers. Destruction clears Skyweave: Compatibility Patches' references without double-closing Iris resources. Shaders off and non-Iris pipelines return the original buffer source.

Before updating Minecraft or Iris, recheck concrete/bridge descriptors, font render types, base-renderer overrides, deferred batch setup/draw and merge mode, transparency/depth state, shader keys, fallback output attachments and `loadedShaders` cleanup. Test reload, shader switches and world transitions; never infer lifecycle safety from a successful first frame. Shader updates also need native glyph/background and unaffected-geometry comparisons. Dated results and remaining limits belong in [verification](verification.md).

## Balloon overlay

`BalloonShadowMixin` skips Aeronautics' hot-air effect callback when Iris reports its shadow pass. Aeronautics 1.3.2 otherwise creates an overlay framebuffer and runs screen-space post-processing during that pass. The ordinary render-stage callback is preserved, as is actual ship geometry in the shadow map.

Upstream reference: [Simulated PR #1298](https://github.com/Creators-of-Aeronautics/Simulated-Project/pull/1298). This is separate from framebuffer restoration. The PR's End Sea and physics-staff guards are not included: the reported symptom and inspected balloon callback justify this bounded adaptation, while those additional effects lack equivalent pack-specific evidence. Review their own callbacks if symptoms appear. Retire when released Aeronautics excludes the hot-air composite from shadow passes.

## Construct materials and single-block models

`ConstructMaterialMixin` brackets both NeoForge AO and non-AO tessellation paths only for Sable construct views and an Iris block-sensitive buffer with a material map. Ordinary world meshes and Sodium's own annotation path retain their owners. `BlockMaterialScope` balances begin/end through exceptions and restores the outer annotation during recursive rendering on the same buffer. Shader block IDs and emission describe existing blocks; this adapter changes no physics or weights.

`SingleBlockModelDataMixin` independently makes render-layer selection query the same level model data used by Sable's tessellation path. It does not require Iris.

Upstream reference: [Sable PR #1417](https://github.com/ryanhcode/sable/pull/1417). Review Sable's reacharound dispatcher, construct view wrappers, Iris's block-ID producer/consumer, and NeoForge tessellation overloads before updating any target. An upstream fix may add its own begin/end calls; remove this adapter before that would double-annotate geometry. Retire the model-data hook independently when Sable's released layer-selection implementation uses the matching data.

The earlier Sable matrix-refresh and directional-shading corrections are already present in 2.0.6 and are not duplicated here. This material correction does not establish correct Ponder previews, Copycat normals, or shadow-map projection.

## Colorwheel

The 1.3.2 buffer/DSA repairs do not replace the adapters below. Its inspected `ClrwlProgram` sampler registration and `GlCompat` capability policy retain the 1.3.0 behavior; recheck those owners before retiring either adaptation.

Colorwheel 1.3.2+mc1.21.1 supplies shader-aware Flywheel backends. The optional `rendering.colorwheel_materials` bridge requires the existing construct-material adaptation and is selected only on physical clients. Its compile artifact is Modrinth `lV874ox8`, SHA-256 `6f881290ff7f71e36bba189d87c31fb104c668f01aac19ac0d003fac3cc1cd5f`.

Colorwheel wraps NeoForge `ModelBlockRenderer.tesselateBlock`, but Sable 2.0.6's platform calls `tesselateWithoutAO` directly. The existing Skyweave: Compatibility Patches scope reaches that path; plain Iris `beginBlock` alone does not set Colorwheel's terrain and emission fields. Colorwheel stores that emission in the fourth byte of Iris's mid-block vertex attribute. `ColorwheelMaterialMixin` selects a writer for Skyweave: Compatibility Patches' own scope, using `clrwlBeginBlock`/`clrwlEndBlock` where supported and retaining the Iris protocol on plain mesh emitters. Nested scopes restore their parent writer and material through failures. No upstream mixin is cancelled.

`rendering.colorwheel_samplers` is independent of Sable and the material bridge. Colorwheel reserves slots 0–6 for base rendering, 7–8 for OIT depth/noise and 9+ for its coefficient textures. Its Apple renderer guard disables OIT drawing, but `ClrwlProgram.postLink` still reserves and registers all those OIT slots. With Complementary/Euphoria, the native embedded translucent program then exhausts Iris's sampler allocation at `shadowtex0`.

`ColorwheelSamplerMixin` retains all base slots and omits OIT reservations and external registrations only when Colorwheel's own `SUPPORTS_OIT` is false. Unsupported OIT already falls back to ordinary translucent drawing upstream; the adaptation enables no hardware feature and changes no shader setting. On supported renderers the original sets and registrations pass through unchanged. Review Colorwheel's slot layout, OIT guard and binding order, and Iris's reserved/external sampler contract before updating. Retire when upstream skips unsupported OIT's reservations itself. This is not a general sampler-limit bypass.

This bridge does not take ownership of ordinary world tessellation, mesh storage, Flywheel engines, Sable transforms or shader programs. It does not claim to repair all nested upstream annotation paths. The other rendering adaptations remain independent; disabling construct material IDs also disables this dependent bridge. Missing/disabled Colorwheel loads no Colorwheel API through Skyweave: Compatibility Patches.

Review Colorwheel's buffer API and mid-block encoding, its tessellation wrappers, Sable's direct flat/AO calls and Flywheel mesh emitters together before upgrading. Remove this bridge if Sable or Colorwheel covers direct construct tessellation with complete material metadata; do not retain duplicate annotation owners. Recheck Sable light-storage/embedded-environment APIs and supported shader backend/GL requirements independently. Version metadata alone does not establish these.

Colorwheel's [backend documentation](https://djefrey.github.io/colorwheel/whatis/colorwheel/) explains shader integration. Complementary 5.7+ and Euphoria 1.7+ include native support; Patcher is unnecessary for the tested newer selection. Apple OpenGL 4.1 uses the supported instancing path, with indirect rendering and OIT unavailable. This is not a promise of a measured performance improvement.

## Single-block render passes

`SingleBlockPassMixin` flushes Sable's single-block queue at the end of each `VanillaSubLevelRenderDispatcher.renderSectionLayer` call while Iris shaders are active. Sable 2.0.6 otherwise flushes before entities, after opaque terrain. A translucent layer queued later is drawn in a subsequent opaque pass, where Iris cannot composite it correctly. Ordinary glass and glass Copycat single-block constructs disappeared in the native 0.6.0 fixture while stationary controls remained visible. The material-ID switch did not resolve this discrepancy.

The adaptation reuses Sable's renderer and queue clearing. It changes no mesh, physics, or material classification, and leaves shader-off scheduling unchanged. It is independent of the model-data and material-ID switches. Review Sable's queue ownership/call sites and Iris opaque, translucent, and shadow-pass order when updating either renderer. Retire when upstream draws single-block geometry within its matching pass; do not retain a second flush after that correction.

## Verification boundary and unresolved shadows

`./gradlew check build` transforms the released Veil and Aeronautics targets, NeoForge ModelBlockRenderer, and Sable's platform class. Controlled execution checks framebuffer restoration and shadow-pass cancellation; material-scope tests exercise recursion and exceptions. These tests require no window or GL context. They do not prove native shader output. [Verification](verification.md) records the tested artifact and current startup checks.

Native checks cover the released 0.6.0 and the 0.6.1 correction on macOS arm64, with the pack's Complementary Unbound/Euphoria shader selection. Ponder, shader toggling/reload, valid heated balloons, materials, and single-block glass controls are recorded in [verification](verification.md). These are bounded observations, not validation of every driver, shader pack, or special block model.

Airship shadows flickering or appearing separated remain unresolved. There is no source evidence here establishing whether precision/filtering, culling, or moving construct transforms cause that symptom. A future authorized comparison should use ordinary blocks versus special models, balloon inflated/deflated, stationary versus moving ship, and camera movement. Compare shader settings only where they distinguish causes. Neither the material-ID nor balloon guard is claimed to fix geometric shadow continuity. Sable's separate default-off skylight-shadow setting is not Iris's shadow enable switch and is not changed.

## Shader assembly

`rendering.shader_patches` is a default-on physical-client adaptation for Iris 1.8.14-beta.1+mc1.21.1. Without recipes it performs no assembly. Recipes live in `config/skyweave/shaders/recipes/`; the file-only engine is also exposed through `ShaderCli` for authoring builds. It requires Java 21 and Gson already supplied by Minecraft. No Python, shell hook, source compiler or launcher integration runs on recipients.

`ShaderAssemblyMixin` prepares outputs at the beginning of Iris `loadShaderpack`, before the selected archive opens. Native `reload` closes its ZIP filesystem and pipeline first. The configuration screen's **Rebuild shaders** action uses that reload path; it does not overwrite an archive held by the active pipeline. Unsupported recipes fail individually. If the selected recipe fails, Iris does not load its old or unpatched file; other shader selections remain usable.

For an enabled Euphoria recipe and the exact supported Euphoria Patcher version, three additional hooks prepare files before its constructor starts scanning, recognize receipt-verified Unbound output in its existing detection result, and prevent `BootShaderModificator` from rewriting an owned archive. Native runtime uniforms and unrelated user shaders remain with Euphoria. Its normal watcher is not disabled. Colorwheel Patcher supplies an overlay input outside `mods/` and therefore starts no competing patcher. Colorwheel itself remains a runtime mod.

### Recipes and ownership

Schema 1 defines `id`, `version`, `output`, `base`, `inputs`, ordered `steps`, optional `enabled`, exact runtime `requires`, a qualified payload-tree `expected` digest, and optional `settings`. Disabled recipes do not read inputs or constrain runtime versions. Inputs specify an instance-relative path and SHA-256. They may read verified archives from `shader-inputs/` or runtime JARs from `mods/`.

Supported steps are deliberately bounded:

- `overlay`: read an input archive or its named `member`; require the preceding payload-tree `before` digest, then overlay only `shaders/` files.
- `euphoria-1.10.5`: require `before`, normalize the Complementary TAR, invoke the exact digest-qualified shaded BSDIFF engine, validate its output and restore Unbound style. Recipes cannot select a Java class or another executable engine.
- `text`: require a file SHA-256, an exact `find` occurrence `count`, and `replace`. An empty find creates an absent file. Targets are shader sources or the two Skyweave provenance files. Unknown operations and fields stop the recipe.

The recipe owns patch versions and ordered source corrections. SHA-256 checks on Create's input JAR qualify the material assumptions; the shader lab additionally rechecks texture bindings and alpha classification. Changing resource-pack window textures requires material review even when Create's JAR itself is unchanged.

Empack owns source inputs and authored recipes. The runner owns its recorded ZIPs in `shaderpacks/`, plus intermediates and receipts in `.cache/skyweave-shaders/`. It never moves managed inputs, edits unrelated packs or overwrites user presets. Only finished shaders enter Iris's list. Original shader and patcher binaries are not bundled in Compat.

An instance lock serializes assembly. Sorted stored ZIP entries and fixed timestamps make authoring and recipient bytes reproducible. The key includes canonical recipe contents, actual verified input digests and engine revision; exact requirements are part of that recipe. Cached outputs are checked against their receipt. Missing or corrupt outputs can be restored from the verified cache. A forced rebuild still uses the same verification. A failed build keeps the last verified bytes; those bytes are eligible only under the current key and qualified environment.

Publication uses temporary files and atomic replacement, with a pending receipt for recovery between output and receipt publication. A name collision without an ownership record is an error. A corrupt receipt is not authority to remove files. Completed output-name changes retire only a previously recorded, unchanged ZIP. `settings.compatible` permits copying a previous preset only when the destination has none or matches an explicitly recorded `settings.seedSha256`; optional `settings.renames` maps exact option names. Iris selection moves only from the previous output of that recipe. Enablement and other selections are preserved. Keep instance-local cache directories when moving the installation if its generated shaders should remain runner-managed.

### Updating

Recheck Iris initial load, archive closing and reload call paths. For Euphoria, inspect constructor ordering, `ShaderDetector`'s result fields, style detection, watcher behavior, `BootShaderModificator`, shaded TAR normalization and BSDIFF API. The reviewed engine JAR digest is a code allowlist, separate from recipe input hashes. Updating it requires code review and a new handler qualification.

Recheck Colorwheel's embedded overlay/configuration and shader version, qualify Create texture bindings and render layers, update source/text preconditions and expected payload hashes, then compare authoring/runtime output bytes. Increment the engine revision when handler behavior changes; increment the independent Skyweave patch version when its authored behavior changes. Retire a correction only after the chosen upstream release covers it. Shader redistribution terms remain separate from technical patch generation.
