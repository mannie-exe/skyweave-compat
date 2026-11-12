# Lighting review

Use only in the disposable `lighting-review` save created by the client probe. Copy this datapack into that save, run `/reload`, then `/function compat_lighting_review:cabin` after the automatic lifecycle checks. It assembles a connected stone cabin with a supported lantern. Physics remains paused. Do not run the function repeatedly without removing its previous sublevel.

The exterior camera is set by the function. Use `/tp @s 12.5 -54 2.5 -45 38` for the interior. Compare shaders on/off through Iris's configured toggle. These captures supplement the automatic moving-source, source-removal and dimension checks; they do not establish complete occlusion. No personal world or resource-review save is used.

`/function compat_lighting_review:light_bank` adds a connected assembly with 24 glowstone blocks. `/function compat_lighting_review:modded_lamp` adds a separate Macaw covered-lantern assembly and requires Macaw's Lights and Lamps. Each function is a one-shot visual fixture; remove the previous sublevel before repeating it.

For an isolated minimum-installation client, use the same probe with `-Dskyweave_compat.probe.lighting.minimum=true`. It opens a fresh flat fixture and records a loaded player/level without querying SDL APIs or building a scene. For Colorwheel, also enable `-Dskyweave_compat.probe.colorwheel=true` with the supported renderer installed. `colorwheel-state.json` records the actual backend, Iris sampler limit and shader-on native terrain/emission bytes. The extended-vertex probe is intentionally skipped when shaders are off. Neither probe is shipped.
