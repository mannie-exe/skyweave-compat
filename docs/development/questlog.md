# Questlog integration

Questlog remains the authority for definitions, objectives, progress and reward collection. Skyweave: Compatibility Patches adapts its existing screen and packets; it adds no quest store, server handshake, item scanner or reward-grant implementation. This integration has no terrain dependency. [Configuration](../configuration.md#questlog) owns switches, preferences and authoring options.

## Upstream boundary

Compile against Questlog 3.5.0, Modrinth version `Pykla7tJ`, SHA-256 `a78b76ff010ea227fcfabc133689bffd24c76e1572d88ec26a26258ca1b96bd0`. The inspected [source revision](https://github.com/InfernalStudios/Questlog/tree/c77a41eb54e6fce28981e8d6694862863704268e) is `c77a41eb54e6fce28981e8d6694862863704268e`. Its Apache-2.0 source and binaries are not bundled. Questlog is compile-only; feature selection checks its version only for active adapters. Missing or disabled adapters resolve no Questlog classes. Client hooks are excluded from dedicated servers.

Minecraft/Questlog APIs supply text, item rendering, textures, inputs, narration and toasts. Cloth Config, YACL, Architectury and owo were considered; none removes this screen's coupled layout/input work enough to justify another UI dependency. Playground informed constrained sizing, shared hit geometry, proportional thumbs and cache invalidation, not a framework port.

## Details and input

`QuestDetailsMixin` replaces layout, action-state updates and panel rendering while retaining native rich-text links, navigation, collection, read/reset actions and quest-close behavior. `JournalRows` measures variable-height native display rows; the same bounds drive clipping, hit testing, keyboard selection and narration. Wrapped Components retain their styles. Layout caches are local to the screen and invalidate on content, width, language, image/symbol preferences and resource reload. Only visible rows paint, although the small row list is scanned for changed data.

The Settings bookmark is available on the main journal and entry details. It opens `JournalSettingsScreen` using the same presentation spec and save helpers as the main configuration screen. The originating screen, section and scroll state survive the visit. Questlog resets a completed repeatable quest in `QuestDetails.removed`; the adapter suppresses only that reset while visiting settings, retaining cursor cleanup and ordinary close/reset behavior. Review this lifecycle boundary when updating Questlog.

`JournalButton` measures labels and nine-slices the selected peripheral texture; keyboard focus uses the same highlighted surface as pointer hover. Settings content shares measured render/input bounds. `IconTextLayout` centers header icons and titles with integer offsets. `JournalIcon` uniformly bounds custom images to 32 GUI pixels while retaining native item rendering and the two-line row layout.

The description and details retain separate scroll offsets. Constrained windows switch among Description, Conditions and Rewards instead of reducing body text. Extremely long titles continue in the description when the heading cannot contain them. Panels use the quest's supplied texture paths and native nine-slice assets. Dimensions adapt to available space; authored absolute panel offsets are not a layout contract for this adapter. Compact mode does not restore truncated rows. An entry with its details button disabled and details closed by default keeps conditions and rewards hidden. Completion exposes rewards as in native Questlog; the adapter does not bypass that author-controlled visibility boundary.

`JournalScrollPane` reserves a 12-pixel internal hit gutter and draws the existing rail and thumb. Thumb travel and length share `ScrollGeometry` with drag mapping. Track clicks page; dragging retains the grab offset. Wheel input follows the pane under the pointer. Focused panes accept Home/End/Page Up/Page Down; row selection also accepts arrows and Enter/Space. Focus loss releases a drag.

Images use explicit objective icons and native reward ItemStacks, including their components and counts. No arbitrary matching item is guessed for tag predicates. Condition symbols supplement readable labels; unknown objective types keep their native text. Both aids can be disabled. No extra pack assets or localization keys are required in journal authoring data. Skyweave: Compatibility Patches' own interface strings are in its ordinary Minecraft language resources.

Reward previews are read-only until completion. Native choice groups are selected separately from collection; non-item and unknown reward displays retain native labels. Skyweave: Compatibility Patches does not simulate arbitrary reward implementations. Native inventory insertion/overflow remains owned by Questlog's `ItemReward` and `Util.giveToPlayer`.

## Notifications

`QuestSyncMixin` observes authoritative progress after native deserialization. Full synchronization establishes a silent baseline and removes stale notifications. Positive child progress inside AND/OR groups counts; hidden objectives and NOT conditions do not. Unlock events never count as first progress. Definitions marked out of notifications retain that exclusion.

`ProgressNoticeState` tracks a quest lifecycle. The client coordinator delays a notice eight ticks (about 400 ms at 20 TPS), coalesces by quest ID, prioritizes completion over start, and upgrades an already queued/visible start toast in place. The pending map is capped at 32; under overload it discards a start before a completion. Minecraft owns displayed-toast scheduling. No extra sounds or automatic journal opening accompany notices. Disconnect clears state. Full synchronization, language and resource reloads do not replay historical progress.

Monotonic persisted objectives, including Questlog's maximum-inventory-count objective, reconstruct a prior start on reconnect. A completed cycle with its completion flag reset can start again. A reset before completion cannot be distinguished reliably from a non-monotonic objective returning to zero; within that connection it stays quiet. A third-party objective that loses its positive progress before reconnect may announce another start later. There is no invented lifetime guarantee or new persistence protocol for those cases.

`JournalToast` uses the native queue, dimensions and quest-specific Questlog frame. A restrained background-only wash distinguishes it while leaving text, icons and frame untinted. Long localized headings and names use timed two-line pages. Completed entries with outstanding rewards say Reward ready; other completions say Journal completed. Notification settings apply to pending and visible notices.

## Authoritative reward validation

`RewardClaimMixin` validates the sender, quest, eligibility, reward index and already-claimed state before native code mutates a selection or grants anything. Choices require exactly the configured number of unique, in-range indices; plain rewards require an empty choice list. Native sequential server handling prevents repeat requests from granting again. Client installation alone cannot protect an unadapted server; the UI still uses its normal Questlog protocol there.

Upstream report candidates for 3.5.0:

- `QuestRewardCollectPacket.handle` accesses the reward index before checking bounds and does not check quest completion/trigger eligibility. Choice selection normalization does not enforce the required count at this boundary. The adapter rejects these requests before mutation; upstream can adopt equivalent checks.
- The NeoForge client references Cloth Config's `ConfigData` during startup but omits Cloth Config from its dependency metadata. A Questlog-only client fails with `NoClassDefFoundError`; the existing library resolves it.

No upstream report is submitted automatically. Nested choice-inside-choice rewards remain subject to Questlog's native selection protocol; this adapter does not add a recursive choice protocol or change quest definitions to work around one.

## Updating and verification

When updating Questlog, inspect `QuestDetails`, `QuestlogScreen`, `ScrollableComponent`, `ScrollableText`, objective/reward display models, `ClientPacketHandler`, `QuestlogClientEvents`, `QuestManager.sync`, completion/reset serialization and `QuestRewardCollectPacket`. Review every required injection against the artifact, then update the exact pin/hash and feature version gate together. Remove validation hooks when equivalent upstream checks cover the same negative cases. Do not infer continued compatibility from compilation or an unchanged public class name.

Run `./gradlew --no-daemon check build clientProbeJar`. Dedicated startup probes support `standalone`, `questlog`, `questlog-disabled`, `unsupported-disabled` and `full` with a read-only installed server as their library source. The separate client probe's `-Dskyweave_compat.probe.journal=true` mode opens the disposable `journal-review` flat save and `questlog:ux/long`; copy `tools/fixtures/questlog/quests` into that fixture's `config/questlog/quests` first. Never enable it in a personal instance. It changes fixture inventory/progress and writes claim/render reports. For an isolated remote fixture use `-Dskyweave_compat.probe.journal.server=host:port`. Use `-Dskyweave_compat.probe.journal.entry=questlog:ux/hidden` to inspect the disabled-details fixture. The optional fixture resource pack in `tools/fixtures/questlog/resources` supplies long English/German labels; copy it into the test client's resourcepacks directory and select it. The same probe JAR on a dedicated server, with the journal property enabled, runs claim checks when the first fixture player joins. Its checks intentionally reset fixture inventory and quest state. Use `/compatprobe_stop` to save and halt the probe's integrated server before closing the client, as with the existing rendering fixture. The harness requires desktop availability before input/captures.

`journal-claims.json` exercises the transformed handler with a real integrated server player, including invalid indices/selections and duplicate requests. `journal-render.json` samples 300 screen-render CPU intervals after 60 warmup frames; it is not GPU time or a whole-game FPS benchmark. Compare the same fixture with `questlog.interface=false` for a native-screen baseline. Visual checks must also cover actual pointer/keyboard collection, scaled layouts, reloads and native packet synchronization. Probes are separate artifacts, never release contents.

### Verification scope

[Verification](verification.md#release-070) records current artifacts, runtime checks and limits. The native 3.5.0 handler control accepted incomplete claims, invalid choice counts and duplicate choices, and failed invalid-index handling. Adapted dedicated/integrated checks reject these requests while retaining native grants and overflow. Keep that negative control when revisiting the validation hook.

Notification checks cover first progress, quiet later progress, immediate completion, replacing a visible start, completed-cycle reset, silent full sync/ reconnect and changing modes. Hidden-details fixtures retain their authoring gate. Narration text is exposed through native interfaces; spoken output and arbitrary third-party reward implementations are not exhaustively verified.
