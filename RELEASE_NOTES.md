# Skyweave: Compatibility Patches 0.24.0

The Skyweave preset distributes vanilla structure families across island clusters
instead of collecting every nearby source opportunity. Variants share an allowance;
eligible terrain and biome area contribute to it, while surrounding void does not.
Family rates, cluster limits, a global multiplier and final village/outpost distance
are editable in the existing configuration screen.

Multiple ordinary villages can share a cluster. Sky Villages keeps its separate
allowance. Portals and ordinary/beached wrecks each share a one-per-cluster limit.
Temples, huts, igloos, mansions and underground families retain native layouts,
loot and fitting. Unknown sets, strongholds, local placed features and ocean
monuments/ruins retain their existing controls.

Owned portals use island-relative height bounds. Igloos retain their top, ladder
and optional basement at the accepted island anchor. Desert pyramids retain their
accepted height, traps and archaeology, with complete body support beneath their
footprint. Native reservation and duplicate guards follow the generator's lifetime;
parallel layout planning initializes vanilla's lazy rotation tables before use.

Floating ocean habitats now default on. Existing explicit ocean opt-outs are
preserved; the continuous global ocean layer remains disabled.

This changes generation identity and requires a fresh world. Existing saves are
not silently converted. See [configuration](docs/configuration.md#structure-distribution)
for units and exceptions, and [verification](docs/development/verification.md) for
native coverage and limitations. Numeric rates describe allowances before fitting,
not guaranteed encounter probabilities.

Previous release: [0.23.1](docs/releases/0.23.1.md).
