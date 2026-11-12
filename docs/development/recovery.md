# Construct grave recovery

`yigd.construct_recovery` requires Sable 2.0.6 and YiGD 2.0.13. Its server callbacks also run on integrated servers; Create, Aeronautics and island generation are not prerequisites. YiGD item and XP storage must both be enabled for adapted deaths.

YiGD owns inventories, experience, grave identity, ownership, and claim state. Skyweave: Compatibility Patches records support and construct references, not another copy of grave contents. Normal world deaths retain YiGD placement. Deaths on a supported Sable deck and void deaths following a verified deck departure use construct-aware placement.

## Placement contract

A support record contains the dimension, stable construct UUID, deck position relative to the plot origin, and last observed world position. Grounded collision support is required; a tracking reference, flight, or proximity alone is insufficient. A record from a different dimension is invalid. Resolve active constructs by UUID and current logical pose on the server thread.

Prefer a replaceable supported position within four blocks horizontally and three vertically of the deck anchor. Search closest first, within the owning loaded plot. Never replace a block entity, machine, container, liquid, or occupied solid block. Validate both the physical plot target and its projected world location against build bounds and protection before placement. Revalidate in YiGD's deferred placement operation.

Place the actual YiGD grave in the construct plot. Keep plot coordinates for storage and block operations; resolve current world coordinates separately for player-facing locations and interaction distance. A plot address alone is not a stable construct identity.

## Loss and availability

If the construct is gone or cannot provide a valid placement, search the same bounded neighborhood around the last verified world-space deck position. A single support block may be placed beneath a grave in otherwise empty permitted space. This preserves a local recovery journey even when the deck has disappeared.

Unloaded constructs remain distinct from removed constructs. A placed grave on an unloaded construct retains its saved identity and inventory without forcing the construct to load. Before initial placement, an unavailable construct may use the world-space fallback; it must not also receive a second grave when it reloads.

If all bounded candidates are unavailable or protected, retain the same YiGD recovery record as pending and notify the owner. Retry at a bounded frequency without generating or loading arbitrary chunks. Never replace failure with loose drops into the void. A later permitted placement must consume the same recovery identity once.

Pending placement retries once per second while the owner is online. A native generation veto remains blocked rather than being bypassed on the next retry. YiGD's saved record remains available for operator recovery. No remote item claim or automatic teleport is added. Spawn protection is checked at both the physical and projected positions. `RecoveryPlacementEvent` allows other protection integrations to veto the candidate; this does not establish compatibility with every third-party claim system.

Repeated failures send one pending notice until placement succeeds. Notification suppression resets with the server; it is separate from saved recovery custody. Anchor sampling requires an upward-sturdy support face. Partial or unusual collision-only footholds need separate native coverage.

Construct removal after placement must preserve the unclaimed recovery record and relocate it once. Claiming, native relocation, and disassembly must keep the physical grave and saved record aligned. Reject stale physical graves or recycled plots. Do not revive a claimed inventory.

Linked compasses follow the current world position once per second. Closest-grave lookup, grave-list payloads and location notices project construct graves into world coordinates while block operations retain plot coordinates. Native grave position results are clamped above the actual dimension floor so a supporting block has space. Configured minimum Y still defines ordinary void recovery.

Keep this adapter enabled while using its saved construct graves. Disabling its mixins removes its metadata serialization and tracking as well as its placement behavior; it is not a migration of those graves into stationary world storage.

## Maintenance

Review YiGD's placement events, deferred `placeAndLoad` operation, grave serialization, block-entity relocation, tracking payloads, compass helpers, and claim paths together. Review Sable's grounded collision, stable UUID lookup, plot allocation, transforms, unload/removal reasons, and disassembly order. Test exact released artifacts; a loader version match does not prove these private contracts remain compatible.

See [verification](verification.md) for the native fixtures, tested artifacts and remaining limits. YiGD remains the only inventory and experience store.
