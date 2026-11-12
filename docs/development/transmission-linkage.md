# Transmission & Linkage

The optional interaction adapter targets Transmission & Linkage 0.2.9 with Create 6.0.10, Sable 2.0.6 and Simulated 1.3.2. It does not require the terrain preset. [Configuration](../configuration.md#adaptation-switches) owns activation.

## Player mutations

`transmission_linkage.interactions` retains upstream targeting, movement remapping, physics and saved links. Visible rods detach through sneak-use with a Create wrench in the main hand. Ordinary attack input no longer sends an instant removal packet, and bare-hand sneak-use on a head no longer detaches it. Native sneak-wrench removal of an endpoint still recovers the block through upstream's drop path.

Server hooks validate rod removal, menu changes, endpoint use and the resolved selection when binding. Requests need a current editable endpoint; linked changes also require the opposite endpoint to be available and editable. Adventure and spectator mutation is rejected. Packet edits retain upstream menu and target checks and recheck menu validity before mutation. Physics breakage and automatic lifecycle cleanup remain upstream behavior and do not impersonate a player.

Checks include ordinary spawn protection at the Sable plot and projected world positions, and break events for deliberate removals. `LinkageEditEvent` provides a cancellable authorization hook for every affected endpoint; it exposes the player, endpoint, projected position and removal flag. Settings checks do not post synthetic right-click events, which could trigger unrelated interactions. Unloaded targets are not force-loaded to authorize a request. This is not a guarantee of compatibility with every protection mod: integrations that maintain additional ownership or custom claim APIs need their own verification. Protection cancellation leaves both ends and held rod quantities unchanged.

`transmission_linkage.jade` requires the interaction adapter and Jade 15.10.6+neoforge. It reports server link availability, endpoint-center separation and target length; details include giant-cylinder set pressure/flow and regulator control. These settings are not evidence of a supplied fluid network or measured fluid consumption. Conversion bearings expose their separate native output object's RPM and total SU, plus the actual cached suppression cause; reading those values does not run another structure scan.

## Upstream limits

The inspected source is [`bd1f0bf`](https://github.com/userenxv/aeronautics-utility-objects/tree/bd1f0bfe3a9cc2c8275eb44cd5883a16c854f9e3). The release artifact is pinned by version ID and SHA-256 in Gradle. Version 0.2.9 already repairs Ponder interference and rod targeting through physics structures; these adapters do not duplicate those changes.

The giant-rod visual helper caps visible extension at 18.0625 blocks. Stock maximum link length 15 and break distance 17 avoid the enlarged-configuration case described in [upstream issue 5](https://github.com/userenxv/aeronautics-utility-objects/issues/5). Do not enlarge the range without aligning rendering, selection bounds and physical length. No range extension is implemented here.

## Ponder wording

The six native tutorials use shared English keys under `aeronautics_utility_objects.ponder.shared`. Text corrections can be supplied by a resource pack or resource-only mod; they do not require a new optional runtime adapter. Keep them with the distributing pack's maintained resources rather than duplicating them in Skyweave: Compatibility Patches.

Review tutorial claims against the actual implementation: Expected Length is a distance, Stretch Resistance damps axial motion, and Return Force restores target length. The regulator requires kinetic input but its set pressure/flow does not establish fluid consumption. Brass strain uses absolute link length; andesite strain uses deviation from the connected rest length, including compression. Conversion bearings require physical rotation and expose a separate kinetic output; Resistance opposes that motion rather than setting its RPM.

The settings tutorial's initial pointer targets the lower head although normal rod settings open from the rod body. Correcting prose does not move that pointer. Preserve native scene timing and animations unless a separate demonstrated scene defect justifies a bounded runtime adaptation. Recheck source keys and native playback when updating upstream.

## Checks and updates

`tools/linear_probe.py --scenario linkage --linkage /path/to/0.2.9.jar` reuses the isolated machinery runner. Supply its normal read-only server, pack, Java and Linear Bearing inputs. `--minimal` selects the reduced Create/Sable runtime; `--linkage-baseline` disables these adapters for the original-behavior control. The fixture uses a real world-to-assembled-body rod and native packet handlers. It records artifact/source hashes and deletes its temporary world.

The baseline permits bare removal. The adapted fixture rejects bare removal, adventure/spectator removal, a protected opposite endpoint, unauthorized settings and a stale distant menu, while allowing authorized settings and sneak-wrench removal. This check does not establish interactive client input, every lifecycle transition, claim-mod coverage or multiplayer packet ordering.

`--scenario linkage-physics` is a bounded native observation of normal and giant actuators and an independently driven converter. It reports observations rather than asserting a balanced power economy or a closed-loop exploit. Read the recorded configuration and fixture alongside measurements.

Before updating upstream, inspect packet lambdas, menu checks, block/wrench use, binding call sites and remapped selection locals. The authorization injections depend on these exact released shapes. Update compile hashes, feature gates and native positive/denied controls together. Verify dedicated-server loading and client rod input separately; neither successful compilation nor one fixture proves both. Retire an individual hook only when the selected upstream release provides the complete corresponding behavior. Do not suppress an upstream repair merely to retain this adapter.

The lifecycle scene (`--scenario linkage-lifecycle`) saves and cold-reopens three real links: ordinary world-to-ship, reversed-order giant actuation, and a rotated off-center ship-to-ship pair. It also assembles an already linked endpoint and checks isolated endpoint removal. Each surviving pair must have one live rod constraint. This does not establish every hinge layout or mid-session chunk unload order. Stock actuation and powered-regulator semantics remain upstream behavior; the adapter introduces no fuel or kinetic-input requirement.
