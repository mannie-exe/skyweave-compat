# BlueMap adaptation

The optional adapter changes BlueMap 5.7's browser presentation of saved Sky Archipelago terrain. It does not generate chunks, modify gameplay or write map tiles. BlueMap itself is not a required dependency and is not needed on clients. [Configuration](../configuration.md#bluemap-viewer) owns map selection and budgets.

## Loading and world selection

The entrypoint checks the physical dedicated-server side and exact BlueMap 5.7 loader metadata before resolving API-bearing classes. The dependency is compile-only. API enable/disable callbacks publish or clear an adapter manifest; server lifecycle callbacks capture the canonical Overworld save path and actual height bounds on Minecraft's thread. Expanded worlds must already have their verified generation marker. Only explicitly selected map IDs whose save path matches that Sky Archipelago world are published. Ordinary Sky Archipelago worlds use their own dimension bounds; foreign generators are excluded.

The adapter registers `skyweave-compat/viewer.js` through BlueMap's webapp API. Schema 2 settings include per-map exclusive height bounds. API reload and server shutdown clear selection. The original renderer returns when selection is empty, settings are invalid or the supported browser implementation is unavailable.

## Rendering and limits

The detailed render pass extends BlueMap's camera-distance cutoff while retaining its floating-origin transform. Loading uses the actual vertical bounds for frustum tests, a bounded tile radius and concurrent-request limit. Retained vertex/index buffer usage reduces the radius when the budget is reached. An individual oversized tile falls back to coarse rendering. This is not a total browser/GPU memory limit: textures, pending downloads and temporary allocations are additional costs.

The finest coarse layer removes triangles touching transparent samples. Alpha indicates occupancy; zero height does not. Coarser layers retain upstream geometry because removing every partially empty triangle can erase whole distant islands. Detailed geometry includes stacked surfaces and undersides only if BlueMap's server-side render settings retained them. Coarse data remains a height field. The adapter cannot reconstruct missing surfaces, and coarse sheets and distorted edges can remain visible from below or beyond the detailed area.

Detach restores methods and shader sources and disposes owned geometry. It keeps captured manager references because BlueMap clears its manager properties before notifying listeners of a map change. `window.skyweaveBlueMap.metrics` exposes activity, radius, retained geometry and fallback reason for browser inspection.

## Upstream boundaries

The API is public; the renderer hooks are version-specific internals. Java verifies the 5.7 web bundle's size and SHA-256 before publishing the adapter; the browser also checks the bundle URL, manager shape and shader signature. Compilation pins and the exact upstream JAR hash live in `build.gradle`. The adapted render loop retains BlueMap's MIT notice.

For an update, review upstream [MapViewer](https://github.com/BlueMap-Minecraft/BlueMap/blob/v5.7/common/webapp/src/js/MapViewer.js), TileManager, low-resolution loaders/shaders and API lifecycle. Update the metadata, artifact and bundle gates together only after native and browser checks. Confirm signed heights, occupancy, floating-origin movement, request/memory bounds, map switching, reload and detach. Do not raise memory defaults to hide fallback limitations or clear existing tiles for a browser-only change.

## Verification tools

Build with `./gradlew check build`. Verify the no-BlueMap boundary with the minimal native generation command in [development](README.md). Then run:

```sh
python3 tools/bluemap_probe.py --server /path/to/server/game --java /path/to/java
```

This uses disposable dedicated servers with only Sky Archipelago, Skyweave: Compatibility Patches and BlueMap. It checks expanded/ordinary bounds, foreign-generator exclusion, reload selection and shutdown cleanup. It reuses the installed libraries and accepted Minecraft resource download without editing the source instance.

For browser inspection against existing saved tiles:

```sh
python3 tools/bluemap_preview.py --web /path/to/game/bluemap/web --port 8101
```

The loopback preview is read-only and substitutes the candidate script/settings. It assumes expanded bounds by default; set `--min-y` and `--max-y` for another world. It bypasses native selection deliberately, so it cannot verify API loading. Stop it after inspection. Current observations are recorded in [verification](verification.md#bluemap-adapter).
