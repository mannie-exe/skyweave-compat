# Skyweave: Compatibility Patches 0.38.0

Correct glass backfaces after native Create/Copycats+ shape assembly, including ordinary glass contacts and multi-material parts. Minecraft remains 1.21.1; NeoForge remains 21.1.265.

- Keep material-source reads separate from finalized Copycats+ cached geometry. Native CT, shape cropping, culling and render layers remain active.
- Add and separate backfaces on the actual final panel, step, slab, stair and supported slope surfaces. Preserve original geometry and vertex attributes, existing pane seams and intentional two-sided artwork.
- Add independent default-on client switches for native Create Copycats and Copycats+. Keep the existing glass-family switches and explicit opt-outs.
- Load typed, bounded transparency profiles through native resource priority, with exclusions, disablement, conflict fallback and consistent reload generations. Resources select implemented providers, not executable handlers.

Native checks cover stationary geometry with shaders off, patched Complementary/Euphoria and BSL, material/CT changes, resource reload and optional installations. Arbitrary meshes and moving Sable/contraption rendering are not newly qualified. Copied materials gain no new physics properties.

Terrain identity remains `islands_v1-skyarch-1.4-17`, with watersheds `island_watersheds_v7`; no world reset is required.
