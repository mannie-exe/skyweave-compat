# Radio playback synchronization

`analog_audio.playback_timeline` is a physical-client adaptation for Analog Audio 0.1.5-hotfix.1 and its bundled AnalogLib 0.1.0. It does not require Create or the separate radio movement-registration repair. Other AnalogLib consumers retain their native playback behavior.

## Timeline and ownership

Analog Audio supplies a cassette identity, URL, start tick, duration and loop state. `RadioSessions` samples the current level's media time on the game thread. Emitters from the same cassette session share a clock; a changed URL or start tick gets a different session. The registry retains at most 512 emitters and clears on world teardown. Removing the last emitter invalidates its clock and callbacks.

`NativeTimelineMixin` compares that clock with queued audio and the OpenAL sample cursor. It discards stale queued sound when playback falls more than 250 ms behind, while allowing the existing decoder to catch up. Decoded audio still comes from AnalogLib's native FFmpeg/yt-dlp pipeline. An ahead-of-timeline source waits for the game clock rather than changing pitch or playback rate.

Standby streams bind to the current clock before resuming. AnalogLib's existing offset check decides whether to reuse a stream or load it again; loading time is then accounted for in either case. Replacement and resolver submission share a streamer lock so an obsolete request cannot start after a newer replacement or stop operation. Token, error and completion callbacks return to the game thread and check the current session before changing engine state.

Initial internet loading can remain silent. This joins the current position after loading; it does not promise identical first-sound timing or sample-perfect playback between computers. Native pause, playlist and loop authority remain in Analog Audio. Independent cassette start times no longer merge through the library's tolerance window.

## Range and sound controls

This adaptation does not change volume, attenuation, filtering, stereo mode, emitter positioning or OpenAL distance properties. Configure native radio and speaker ranges in Analog Audio's server configuration. A larger range extends the existing quieter tail; disabling spatial audio can retain native range limitations. Server view distance is not an attenuation control.

## Updating dependencies

Inspect the exact bundled AnalogLib JAR, not an older public playback backend. Update the outer and nested artifact hashes together. Review radio/client-hook signatures, stream clustering and standby ownership, token and completion callbacks, native queue accounting, sample rate/chunk size, pipeline generation checks and OpenAL context ownership before changing the version gate.

Run the delayed-load comparison, two clients with insertion roles swapped, range exit/re-entry, pause/resume, loops, replacement, reconnect and one supported online cassette. Measure audible queue positions; decoder progress and a `playing` flag alone are insufficient. Retire the corresponding hooks when a released upstream implementation preserves the same timeline and lifecycle.

See [verification](verification.md) for tested artifacts and limits.

## Create registration

`AnalogAudioCreateMixin` corrects one reflective lookup in Analog Audio 0.1.5-hotfix.1 with Create 6.0.10. Upstream asks the package-private registry implementation for `register(Block, MovementBehaviour)`. Java erases those generic parameters to `Object`; obtaining the method from Create's public `SimpleRegistry` interface also avoids illegal access to its implementation. The adapter preserves upstream setup timing, handler construction, registration and error handling.

Loader metadata selects the hook only for that exact pair. Neither mod becomes a required or compile-time dependency, and no client classes enter the dedicated server path. Disable this feature to retain upstream behavior with other versions; an enabled complete but unsupported pair is rejected with a configuration remedy. This adapter changes no recipes, resources, weights, radio controls or Sable coordinate handling.

Before changing either dependency, inspect `CreateCompat.lambda$onCommonSetup$1`, `SimpleRegistry.register` and the upstream `RadioMovementBehaviour`. Remove the hook if upstream fixes registration; otherwise update its exact gate only after rechecking the lookup and required injection count. Do not replace the upstream handler or broaden GUI behavior as part of this fix.

Build with `./gradlew check build probeJar`, then run `python3 tools/probe.py --audio SELECTION --server /path/to/server/game --java /path/to/java` for `minimal`, `create`, `analog`, `pair` and `full`. These modes check optional class loading, the actual radio registration, a control Create drill handler, and dedicated-server callbacks. Use `--mod /path/to/baseline.jar --audio pair` for the expected failing control. Reports include exact artifact hashes. Client playback, moving sound positions, stopping/disassembly and GUI controls require a separate interactive check; successful registration does not prove them.
