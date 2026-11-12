# Native rendering fixture

Copy this datapack only into a marked disposable rendering world. Never install it in a play world: the ship setup replaces x=288..344, y=180..202, z=-12..20 and pauses Sable physics in that test world. It is not included in the mod JAR.

Enable commands in the disposable copy. Teleport to `311 194 22` first and wait for nearby chunks to load. Run `/reload`, then `/function compat_render_review:setup`. This builds two matching heated wool envelopes and material rows, plus two gold Copycat panels. Inspect the static controls before `/function compat_render_review:assemble` turns the left envelope and one panel into constructs. Use the same camera/time for enabled and disabled-adapter comparisons. Restart the client when changing feature switches.

Observe balloon fill and the actual shader output; successful commands alone do not establish a valid balloon or material correction. Record shader versions, artifact hashes, viewing angles, and native logs with the result. Unpause physics only when deliberately testing movement in this disposable scene.

Optional telemetry: build `clientProbeJar`, install only its fixture JAR in the test client, and launch with `-Dskyweave_compat.probe.rendering=true`. `CLIENT_FLIGHT_PROBE` records valid balloon capacity, predicted volume, render-region presence and construct bounds. Remove the probe after testing.

`compat_render_review:glass` creates assembled/static glass Copycat and ordinary glass controls and sets spectator mode so the comparison camera does not fall. Teleport to `335 191 10` and wait for chunks before running it. Use a fresh scene or remove its previous constructs first. Physics pause is not retained across client restarts: pause again and rebuild floating controls before comparing, or confirm their current bounds in telemetry. Use `/compatprobe_stop` before closing the test client. It explicitly halts the integrated test server; offline quickplay sessions opened to LAN can otherwise wait indefinitely for owner-disconnect shutdown. This command exists only in the opt-in client probe and is never installed in play instances.

## World-text shadows

`compat_render_review:ui_shadows` is an independent vanilla scene. It replaces x/z=-16..16 at y=100, creates tagged text, a named cow, item/block displays and a sign, and sets a fixed afternoon camera. It does not pause physics or assemble ships. Run it only in a marked disposable flat world; wait for the nearby chunks first. Its text is full-bright so lightmap changes cannot explain shadow changes.

Compare the same saved scene after restarting with each combination of `rendering.text_display_shadow_pass` and `rendering.nametag_shadow_pass`. Keep ordinary GUI visibility on when inspecting names. Check native text and entity bodies as well as projected shadows; include shaders-off and shader-on controls. Shader packs can already omit particular text shadows independently. Release temporary force-loads with `forceload remove -16 -16 16 16` when finished.

Keep the HUD visible when checking nametags; F1 hides them. Use a clear sky and a floor without overhead fixtures when comparing cast shadows. Full-bright text can still receive shader lighting, fog and color processing; cast-shadow exclusion does not preserve its displayed color.

## World-text lighting

`compat_render_review:text_lighting` owns x/z=-16..16 at y=280..296 in a marked flat review world. It sets spectator mode, fixed daylight/weather, four tagged billboard displays, a named cow and ordinary sign/block/item controls. The displays cover colored glyphs, black/white/translucent backgrounds, full-bright and native brightness, glyph shadows and explicit see-through visibility. Equal-distance cameras on either side compare sun direction; retain the HUD for the name.

Restart for each combination of `rendering.text_display_lighting` and `rendering.nametag_lighting`. Check opaque-wall occlusion separately from the explicit see-through display. Keep cast-shadow switches independent. Shader reload, resource reload, shaders off/on and leaving/reopening the fixture exercise pipeline ownership. The resource probe's extra lifecycle observations require a disposable `world-text-review.json` marker and Iris; they are absent from the published mod.

A temporary font pack with a licensed local TTF provider can exercise the `TEXT_INTENSITY` route: assign its font ID to a fixture component, verify the provider loaded, then remove the temporary pack. The Unicode option alone does not establish which native shader key a glyph uses. Never distribute a system font with the fixture or infer all-font coverage from one resource reload.

## Distant construct rendering

The `distant_materials` function replaces x=0..23, y=80..96, z=0..15 with a glass shell, leaf/cutout and solid panels, and three block-entity controls. Visit `12 88 64` and inspect the static result before running `distant_assemble` once. This requires Create, Sable and SSRD; the latter's force-load cap must permit the new construct. Confirm the assembled ID, bounds and actual tracking.

`distant_occluder` replaces x=-64..-16, y=64..112, z=95..101 with an opaque wall. `distant_water` uses the same bounded area for a contained water barrier; its invisible barrier-block shell prevents fluid spread. Visit the area before invoking either function, and verify the resulting DH capture rather than assuming command acknowledgement proves that its LOD has updated.

Use native terrain distance eight chunks and a recorded SSRD range for a controlled comparison. View beyond native distance, near the subjects, and across the chunk/LOD transition. Include an opaque construct in front of and behind the wall, plus partial overlap and an unobstructed camera. Compare shader-off, disabled adaptations, distance-only and both adaptations. Keep shader option files paired with their shader archives. Disable unrelated background world generation in this disposable fixture.

The opt-in client distance probe records actual draw uniforms and their restored values; its `nestedFailure` control exercises restoration after a bounded nested exception without aborting SSRD's own outer render cleanup. Never install this instrumentation or these fixtures into a personal instance.
