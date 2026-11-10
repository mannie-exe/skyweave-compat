# Wayfinder

Wayfinder uses one server-authorized request per player. The G binding and
`/skyweavecompat wayfinder` enter the same path. Configuration and defaults live
in the [operator guide](../configuration.md#wayfinder).

## Targeting and authority

`SightRay` clips a normalized ray to the actual dimension build bounds, world
border and engine coordinate domain. Its resumable cursor has no distance cap.
`WayfinderTrace` consumes bounded cells or LOD bands per client tick and closes
its data reader on every terminal outcome. Native loaded collision geometry takes
priority; missing LOD columns terminate as missing data rather than empty space.
Use the chunk source's non-creating lookup for native availability.
`ClientLevel.hasChunk()` always returns true; using it here silently substitutes
empty client chunks for distant terrain and bypasses DH.

`Wayfinder` snapshots the authenticated player's eye, direction, dimension and
request lifetime. It checks native permission level 2 by default before admitting
work and again before teleporting. Legacy tags and scoreboard triggers have no
authority. Responses must match the current token and sight ray. Movement, view
changes, logout, lost permission and world changes invalidate pending work.
Session validation compares the current authenticated player object as well as
the UUID/token; packets from a retired connection cannot control its replacement.
The server can suppress hotkey permission feedback independently of command
feedback. This defaults on, without weakening admission or starting denied work.

A client hit remains a candidate. The server checks nearer loaded terrain, then
leases at most four destination chunks and resolves the real target shape and
body space. It invokes the native teleport-command event and rejects cancelled
or redirected destinations. Failed attempts do not provisionally move the player.
This does not prove line of sight through uncached or unobserved terrain.

`WayfinderGeometry` keeps hazard membership separate from collision support.
Optional absent block IDs must not resolve to the registry's default air block.
There is no support whitelist or upward search for another landing surface.
Placement uses the actual player bounding box, including native scale changes.
It bounds collision work to 512 shape pieces and 4,096 body cells per attempt;
oversized or overly complex landings fail rather than acquiring unbounded chunks.
The world-length surface check clips native AABBs directly: VoxelShape's interior
sample at 0.001 of the entire ray can otherwise misclassify a far surface.

When Sable is installed, the core checks its exact 2.0.6 coordinate-frame API.
Loaded construct envelopes are conservative unsupported-target boundaries;
reserved storage cells and destination chunks are never treated as ordinary world
terrain. A new envelope scan before landing rejects intervening constructs. This
does not add construct targeting or project a moving hull into terrain coordinates.

## DH boundary

The physical-client adapter targets DH **3.3.3**. Public synchronous terrain
raycasts and column APIs wait on data futures; wrapping a whole unbounded ray in
a worker would leave unbounded outstanding work. `DhWayfinderColumns` instead
uses the pinned provider's asynchronous read for one 64-block section at a time.
This path reads existing data and never requests DH generation or native chunks.

Each query owns one decoded source and one column. A shared read gate permits
only one actual database read, including after cancellation. Closing a query
retires it immediately; the completion callback closes a late pooled source.
Cancelling the upstream future would lose that ownership guarantee. Captured
world/level identities prevent reuse across sessions.

Raw band heights are relative to native minimum Y. Native Minecraft build bounds
are authoritative: DH's misleadingly named `getMaxHeight()` returns dimension
height. DH's native importer also includes one padding air sample at the exclusive
ceiling; clip that air to the legal world interval, and reject solid terrain
outside it. A missing column is not a known-air column.

Dedicated servers register only the common protocol, policy and destination
validation. They do not resolve client input or DH wrapper classes. A client
without the channel receives a native loaded-terrain command path; unavailable
terrain reports the compatible-client requirement explicitly.

## Maintenance and verification

When changing Minecraft or NeoForge, recheck collision clipping, player bounds,
teleport-command hooks, native chunk futures/tickets and payload registration.
When changing DH, inspect provider read-only behavior, section encoding, pooled
source ownership, raw height representation, wrapper identity and shutdown.
Requalify the exact optional dependency version before changing its admission pin.
When changing Sable, recheck container iteration, world-space bounds, removal and
reserved plot membership before admitting the new version.

Use the existing server and client probes. Native column readback alone does not
establish a client sight query, a network exchange or validated arrival. Keep those
results separate in [verification](verification.md). Fixtures may prepare real
corridor chunks and import their LOD data; product targeting must not do so.
