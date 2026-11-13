# Hoofprint map zoom

`hoofprint.zoom` is an optional client adaptation for the pinned Hoofprint, Surveyor and Connector stack. It changes map projection and controls, not terrain discovery or server generation. Native qualification belongs to the client probe evidence.

## Configuration limits

Skyweave pins [Hoofprint 1.3.0+1.21](https://modrinth.com/mod/hoofprint/version/YQxLELnA) and Surveyor 1.2.4+1.21, using Connector and Forgified Fabric API on NeoForge. The inspected Hoofprint JAR has SHA-256 `9078d6e61a0730712607f0791fc2243517c95d742134b6dc0b1d2912513f30cb`. Its SHA-1 matches the pack pin, `d2395ad821239db36507fc6946d185fd10e81d3c`.

The pack already sets `defaultScale = -1` in `config/hoofprint.toml`. The field's declared integer range is -1 through 10. It controls opening zoom, not the scroll limits. With `G` as the window GUI scale, initialization selects:

| Configuration    | Internal map scale `p` | Meaning                                      |
| ---------------- | ---------------------- | -------------------------------------------- |
| `-1`             | `ceil(G / 2)`          | Approximately half the GUI scale, rounded up |
| `0`              | `ceil(G)`              | Match GUI scale                              |
| `1` through `10` | The configured integer | Fixed display pixels per block               |

For example, at GUI scale 4 the existing default starts at 2 display pixels per block; `defaultScale = 1` starts twice as far out without changing GUI scale. At GUI scale 1 or 2, `-1` already selects 1, so that change buys nothing. `defaultScale = 10` starts at the closest existing zoom. Neither setting expands the range available by scrolling.

`HoofprintScreen` stores `guiScale` as an integer. Its vertical scroll handler increments/decrements it by one and stops at 1 and 10. `getScaleFactor()` returns `p / G`; Minecraft's GUI transform then restores `G`, making the effective terrain scale `p` display pixels per block. The terrain span at a fixed window size is therefore independent of the selected GUI scale at either endpoint. These are Hoofprint limits, not a vanilla camera/FOV limit.

`-2` is outside the declared config range. Even if forced past validation, the initialization expression treats every negative value other than `-1` like `0`; it does not interpret successive negatives as more zoom-out. Allowing zero as the internal map scale would cause divisions by zero in the coordinate helpers. `dimensions.scales` controls interdimension coordinate ratios and player markers; it is not a minimum/maximum zoom setting.

Evidence: upstream revision [`78225fdb0bd99eb32fe90f55c75df42f7216a2bd`](https://github.com/HestiMae/hoofprint/tree/78225fdb0bd99eb32fe90f55c75df42f7216a2bd), whose metadata identifies 1.3.0 on branch 1.21. Inspect [`HoofprintConfig.java`](https://github.com/HestiMae/hoofprint/blob/78225fdb0bd99eb32fe90f55c75df42f7216a2bd/src/main/java/garden/hestia/hoofprint/HoofprintConfig.java#L20) and [`HoofprintScreen.java`](https://github.com/HestiMae/hoofprint/blob/78225fdb0bd99eb32fe90f55c75df42f7216a2bd/src/main/java/garden/hestia/hoofprint/HoofprintScreen.java) (`init`, `changeDim`, `mouseScrolled`, `getScaleFactor`, and coordinate helpers). The released JAR's annotations and those scale paths were also inspected with `javap -p -c` / `javap -p -c -v`; conclusions do not rely on branch names alone.

## Adapter and configuration

`HoofprintZoomMixin` owns a separate positive fractional scale. Its exact mapped boundaries are `init()V`, `getScaleFactor()F`, `mouseScrolled(DDDD)Z`, `changeDim` and `onClose()V`. The drawing, pan, inspection and waypoint helpers consume the same float scale getter. Do not change the upstream integer field type or the window GUI scale to extend map range.

`config/skyweave/skyweave_compat-hoofprint.json` is editable under **Maps → Hoofprint → Map zoom**. Verified local Save or explicit Reload publishes bounds and step without restarting or reopening the map. An open view clamps to new bounds without recentering; initial zoom applies only to a new view. Defaults are 1/8..32 display pixels per block, initial 1/8 and a 1.25 multiplicative scroll step. Initial zero selects Hoofprint's native opening scale. Validation requires finite positive endpoints within 1/64..128, an initial value within the interval or zero, and a step within 1.01..2. Invalid external files are preserved; the client retains its last valid settings and logs the error.

Scrolling preserves the world point beneath the pointer by adding `before - after` to the center after updating scale. Tiny trackpad deltas accumulate multiplicatively; horizontal-only and nonfinite input are ignored. Resize preserves the current view; reopening restores the view within the same connection. A new connection uses opening defaults. Native positive interdimension ratios convert the center and inversely scale zoom within configured bounds; zero ratios retain zoom.

Selection is physical-client-only and independent of the terrain preset. Gate foreign classes/mixins through metadata before loading them. Connector reports Hoofprint and Surveyor versions as `1.3.0_1.21` and `1.2.4_1.21`, while their published artifacts use `+1.21`. Connector's runtime version is `2.0.0-beta.17+1.21.1`; `-full` is an artifact classifier, not part of that version. Inspect actual loader metadata when updating, as well as the original and Connector-transformed method descriptors. Disabled or absent targets must impose no foreign-class resolution or version constraint. The ModDev limitation is recorded in [client probes](testing.md#client-probes).

## Verification requirements

Implementation checks must cover applied mixins in a disposable Connector client, both zoom endpoints at multiple unchanged GUI scales, pointer anchoring, dragging, waypoint placement/selection, block inspection, cave mode, dimension round trips, resize and reopening. Verify minimum installation, absent target, disabled target, unsupported-but-disabled target and dedicated-server isolation, plus the required `./gradlew check build` on the implemented snapshot.

Extreme zoom-out also needs a representative large explored-map performance check. The inspected screen scans stored region textures, culls nonvisible rectangles, and draws visible 512-by-512 region textures; it has no multiresolution terrain selection. A wider view can draw many more regions and overlapping markers. See [`HoofprintMapStorage.java`](https://github.com/HestiMae/hoofprint/blob/78225fdb0bd99eb32fe90f55c75df42f7216a2bd/src/main/java/garden/hestia/hoofprint/HoofprintMapStorage.java). A zoom adapter should only project existing Surveyor data, without generating chunks, changing map discovery, or promising that server pregeneration makes all terrain available to Hoofprint.
