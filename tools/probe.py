#!/usr/bin/env python3
"""Run the native placement fixture using an installed server's libraries and mods."""
import resource
import sys
import argparse
import hashlib
import time
import json
import shutil
import subprocess
import tempfile
import zipfile
import tomllib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--mod', type=Path, help='Exact Skyweave: Compatibility Patches JAR for an isolated comparison; defaults to the local build')
parser.add_argument('--server', type=Path, required=True, help='Installed game directory; read-only input')
parser.add_argument('--java', default='java')
parser.add_argument('--heap', choices=['4G', '6G', '8G'], default='4G', help='Maximum Java heap; distributed island scans need 6G')
parser.add_argument('--samples', type=int, default=12, help='Layouts per seed')
parser.add_argument('--reopen', action='store_true', help='Reopen generated samples and require unchanged ore counts or village starts')
parser.add_argument('--world-output', type=Path, help='Keep a successful disposable world at a new path for isolated client inspection')
parser.add_argument('--minimal', action='store_true', help='Generate with only Sky Archipelago and Skyweave: Compatibility Patches, plus the read-only probe')
parser.add_argument('--dh-stop-after', type=int, default=0, metavar='SECONDS', help='With --dh-pregen, stop the disposable server during generation (1 to 30 seconds)')
parser.add_argument('--dh-world-key', action='store_true', help='With --dh-pregen, verify opt-in per-save cache identity in the disposable world')
mode = parser.add_mutually_exclusive_group()
mode.add_argument('--variation', choices=['fractured', 'deep_dark'], help='Verify an explicit fractured geometry/ecology fixture through native fill and features')
mode.add_argument('--locator', action='store_true', help='Exercise the bounded metadata command without generating chunks')
mode.add_argument('--lookup', action='store_true', help='Check native presence rejection for unselected structure candidates without chunk generation')
mode.add_argument('--audio', choices=['minimal', 'create', 'analog', 'pair', 'full'], help='Check Analog Audio registration and optional dependency boundaries')
mode.add_argument('--structures', action='store_true', help='Survey natural underground structure starts and realize critical layouts')
mode.add_argument('--surface-structures', action='store_true', help='Survey vanilla surface and water structure families')
mode.add_argument('--coverage', action='store_true', help='Export resolved worldgen resources and bounded native eligibility checks')
mode.add_argument('--benchmark', action='store_true', help='Measure a fixed column and generated-chunk workload')
mode.add_argument('--generation', choices=['current', 'adapted', 'vanilla'], help='Generate and count bounded interior chunk samples')
mode.add_argument('--surface-climate', action='store_true', help='Inspect contiguous surface biomes and native snow placement')
mode.add_argument('--biomes', action='store_true', help='Inspect cave biome footprints and generated vegetation')
mode.add_argument('--mechanics', action='store_true', help='Exercise installed balloon searches across the expanded height')
mode.add_argument('--islands', action='store_true', help='Sample generated resources across three anchor footprints')
mode.add_argument('--chunk-futures', action='store_true', help='Compare background FULL requests with retained chunk tickets')
mode.add_argument('--dh-pregen', action='store_true', help='Complete a bounded 32-chunk-radius DH job in a disposable full-pack server')
mode.add_argument('--terrain', action='store_true', help='Bounded native density survey without generating island chunks')
mode.add_argument('--overlap', action='store_true', help='Compare VOID and OVERLAP exterior columns at matched seeds')
mode.add_argument('--interiors', action='store_true', help='Sample cavity sections and compare independent direct/snapshot query order')
mode.add_argument('--veins', action='store_true', help='Find and verify native raw iron/copper vein material witnesses')
mode.add_argument('--water', action='store_true', help='Check local water material, height queries and native fluid ticks')
mode.add_argument('--materials', action='store_true', help='Inspect completed biome-specific island surfaces')
mode.add_argument('--oceans', action='store_true', help='Check experimental ocean habitats with an explicit --ocean-config')
parser.add_argument('--fixed-biome', help='Isolated marked preset with one explicit biome, for deterministic rare-feature fixtures')
parser.add_argument('--city-witness', action='store_true', help='With seed-42 --lookup and a frozen shape profile, verify the retained 90-piece city control')
parser.add_argument('--lookup-radius', type=int, default=0, help='With --lookup, exercise native cold/warm city discovery from spawn (1..100 random-spread rings)')
parser.add_argument('--structure-id', help='One vanilla structure name for a focused --structures or --surface-structures run')
parser.add_argument('--structure-debug', action='store_true', help='Enable native decision logs only in the disposable structure fixture')
parser.add_argument('--structure-clusters', type=int, default=16, help='Accepted main-island clusters for structure sampling (1..64)')
parser.add_argument('--layouts-only', action='store_true', help='With structure checks, inspect canonical starts without generating their entire footprints')
parser.add_argument('--water-radius', type=int, default=8192, help='Bounded native river/ocean witness search radius in blocks (1..32768)')
parser.add_argument('--fluid-ticks', type=int, default=600, help='Native ticks for --water; 0 checks only initial material, maximum 3600')
parser.add_argument('--natural-profile', choices=['current', 'adapted'], default='current', help='Preset for the natural village survey')
mode.add_argument('--natural', action='store_true', help='Generate natural starts and complete two villages')
parser.add_argument('--generation-config', type=Path, help='Explicit candidate geometry profile; installed config remains read-only')
parser.add_argument('--shape-config', type=Path, help='Explicit horizontal shape profile in the disposable fixture only')
parser.add_argument('--interior-config', type=Path, help='Explicit candidate interiors; installed config remains read-only')
parser.add_argument('--river-config', type=Path, help='Explicit native river/lake candidate; installed config remains read-only')
parser.add_argument('--ocean-config', type=Path, help='Explicit ocean habitat candidate; installed config remains read-only')
parser.add_argument('--structure-config', type=Path, help='Explicit structure policy in the disposable fixture only')
parser.add_argument('--disable-structure-policy', action='store_true', help='Use native structure opportunities in the disposable fixture')
parser.add_argument('--reverse-structure-order', action='store_true', help='Visit the same bounded structure candidates in reverse order')
parser.add_argument('--far-structure-fixture', action='store_true', help='Use structure destinations at least 4096 blocks from spawn for fresh-order checks')
parser.add_argument('--empty-village-start', action='store_true', help='In a fixed-plains fixture, make village layouts fail with an empty start pool')
parser.add_argument('--seed', type=int, default=1)
parser.add_argument('--terrain-seed', type=int, help='Survey this exact seed instead of the five-seed terrain comparison')
parser.add_argument('--maximum-gap', type=int, default=512)
parser.add_argument('--disable-carver-adapter', action='store_true',
                    help='Disable the relative-carving adaptation in the disposable startup configuration')
args = parser.parse_args()
if args.city_witness and (not args.lookup or args.seed != 42 or not args.shape_config):
    parser.error('--city-witness requires --lookup --seed 42 and its explicit frozen --shape-config')
if not 0 <= args.lookup_radius <= 100 or args.lookup_radius and not args.lookup:
    parser.error('--lookup-radius requires --lookup and at most 100 rings')
if args.audio and args.minimal:
    parser.error('Use --audio minimal instead of combining --audio and --minimal')
java_path = shutil.which(args.java)
if not java_path:
    parser.error(f'Java executable not found: {args.java}')
args.java = str(Path(java_path).resolve())
if args.dh_stop_after and (not args.dh_pregen or not 1 <= args.dh_stop_after <= 30):
    parser.error('--dh-stop-after requires --dh-pregen and 1 to 30 seconds')
if args.dh_world_key and not args.dh_pregen:
    parser.error('--dh-world-key requires --dh-pregen')
if args.terrain_seed is not None and not -(1 << 63) <= args.terrain_seed < (1 << 63):
    parser.error('--terrain-seed must be a signed 64-bit integer')
if args.terrain_seed is not None and not args.terrain:
    parser.error('--terrain-seed requires --terrain')
if not 1 <= args.water_radius <= 32768:
    parser.error('--water-radius must be between 1 and 32768')
if args.fluid_ticks < 0 or args.fluid_ticks > 3600:
    parser.error('--fluid-ticks must be between 0 and 3600')
if args.oceans and not args.ocean_config:
    parser.error('--oceans requires --ocean-config')
if args.minimal and args.generation != 'adapted' and not any((args.variation, args.locator, args.lookup, args.coverage, args.structures, args.surface_structures, args.overlap, args.interiors, args.veins, args.water, args.materials, args.oceans, args.biomes, args.surface_climate, args.terrain, args.benchmark)):
    parser.error('--minimal requires a terrain, coverage, structure, interior, vein, water or material check')
if args.reopen and not (args.lookup or args.generation or args.natural or args.structures or args.surface_structures or args.water or args.oceans):
    parser.error('--reopen requires generation, natural villages, structures or water')
if args.natural_profile != 'current' and not args.natural:
    parser.error('--natural-profile requires --natural')
if (args.structure_id or args.layouts_only) and not (args.structures or args.surface_structures):
    parser.error('--structure-id and --layouts-only require a structure check')
if args.structure_debug and not (args.structures or args.surface_structures):
    parser.error('--structure-debug requires a structure check')
if not 1 <= args.structure_clusters <= 64:
    parser.error('--structure-clusters must be between 1 and 64')
if args.far_structure_fixture and not (args.structures or args.surface_structures):
    parser.error('--far-structure-fixture requires a structure check')
if args.empty_village_start and (not args.surface_structures or args.fixed_biome != 'minecraft:plains'):
    parser.error('--empty-village-start requires --surface-structures --fixed-biome minecraft:plains')
if args.fixed_biome:
    import re
    if not re.fullmatch(r'[a-z0-9_.-]+:[a-z0-9_/.-]+', args.fixed_biome):
        parser.error('--fixed-biome requires a namespaced biome ID')
if args.structure_id and not all(c in 'abcdefghijklmnopqrstuvwxyz0123456789_' for c in args.structure_id):
    parser.error('--structure-id requires a vanilla structure name without a namespace')
server = args.server.expanduser().resolve()
if args.world_output:
    args.world_output = args.world_output.expanduser().absolute()
    if args.world_output.exists() or args.world_output.is_symlink():
        parser.error('--world-output must name a new destination; existing worlds are never replaced')
version = next(line.split('=', 1)[1] for line in (ROOT / 'gradle.properties').read_text().splitlines() if line.startswith('mod_version='))
mod = ROOT / f'build/libs/skyweave-compat-{version}-mc1.21.1-neoforge.jar'
if args.mod:
    mod = args.mod.expanduser().resolve()
fixture = ROOT / 'build/probe/skyweave-compat-probe.jar'
output = ROOT / 'build/verification'
output.mkdir(parents=True, exist_ok=True)
name = f'islands-{args.seed}' if args.islands else f'mechanics-{args.seed}' if args.mechanics else f'biomes-{args.seed}' if args.biomes else f'generation-{args.generation}-{args.seed}' if args.generation else 'terrain' if args.terrain else f'natural-{args.natural_profile}-{args.seed}' if args.natural else 'placement'
if args.terrain_seed is not None: name = f'terrain-{args.terrain_seed}'
if args.structures: name = f'structures-{args.seed}'
if args.surface_structures: name = f'surface-structures-{args.seed}'
if args.coverage: name = f'coverage-{args.seed}'
if args.benchmark: name = f'benchmark-{args.seed}'
if args.lookup: name = f'lookup-{args.seed}'
if args.locator: name = f'locator-{args.seed}'
if args.variation: name = f'variation-{args.variation}-{args.seed}'
if args.overlap: name = 'overlap'
if args.interiors: name = f'interiors-{args.seed}'
if args.veins: name = f'veins-{args.seed}'
if args.water: name = f'water-{args.seed}-{args.fluid_ticks}'
if args.materials: name = f'materials-{args.seed}'
if args.oceans: name = f'oceans-{args.seed}-{args.fluid_ticks}'
if args.surface_climate: name = f'surface-climate-{args.seed}'
if args.chunk_futures: name = f'chunk-futures-{args.seed}'
if args.dh_pregen: name = f'dh-pregen-{args.seed}'
if args.dh_stop_after: name += '-shutdown'
if args.dh_world_key: name += '-world-key'
if args.disable_carver_adapter:
    name += '-native-carvers'
if args.minimal:
    name += '-minimal'
if args.audio: name = f'audio-{args.audio}'
if args.structure_id: name += '-' + args.structure_id
if args.layouts_only: name += '-layouts'
if args.disable_structure_policy: name += '-native-policy'
if args.reverse_structure_order: name += '-reverse'
if args.far_structure_fixture: name += '-far'
if args.empty_village_start: name += '-empty-village'
if args.structure_config: name += '-' + args.structure_config.stem
if args.fixed_biome: name += '-' + args.fixed_biome.replace(':', '-').replace('/', '-')
for label, profile in [('geometry', args.generation_config), ('shapes', args.shape_config),
                       ('interiors', args.interior_config), ('rivers', args.river_config), ('oceans', args.ocean_config)]:
    if profile: name += '-' + label + '-' + profile.stem
report = output / (name + '.json')
report.unlink(missing_ok=True)
with tempfile.TemporaryDirectory(prefix='skyweave-compat-') as directory:
    run = Path(directory)
    (run / 'libraries').symlink_to(server / 'libraries', target_is_directory=True)
    (run / 'mods').mkdir()
    for source in (server / 'mods').glob('*.jar'):
        with zipfile.ZipFile(source) as jar:
            metadata = (tomllib.loads(jar.read('META-INF/neoforge.mods.toml').decode())
                        if 'META-INF/neoforge.mods.toml' in jar.namelist() else {})
        ids = {mod['modId'] for mod in metadata.get('mods', [])}
        # Empack bindings may use unversioned filenames; identity comes from metadata.
        if ids & {'skyweave_compat', 'create_sky_compat', 'sky_village_neighbors', 'veil_iris_fix'}:
            continue
        if args.minimal or (args.audio and args.audio != 'full'):
            selected = {'sky_archipelago'}
            if args.audio in ('create', 'pair'): selected.add('create')
            if args.audio in ('analog', 'pair'): selected.add('analogaudio')
            if not ids & selected:
                continue
        (run / 'mods' / source.name).symlink_to(source)
    if args.minimal and len(list((run / 'mods').glob('*.jar'))) != 1:
        raise SystemExit('Minimal run requires exactly one installed Sky Archipelago JAR')
    if args.audio and args.audio != 'full':
        expected = {'minimal': 1, 'create': 2, 'analog': 2, 'pair': 3}[args.audio]
        if len(list((run / 'mods').glob('*.jar'))) != expected:
            raise SystemExit('Audio fixture dependencies missing from installed server')
    shutil.copy2(mod, run / 'mods' / mod.name)
    shutil.copy2(fixture, run / 'mods' / fixture.name)
    for folder in ['config', 'defaultconfigs']:
        if not args.minimal and args.audio in (None, 'full') and (server / folder).exists():
            shutil.copytree(server / folder, run / folder)
    (run / 'config').mkdir(exist_ok=True)
    feature_file = run / 'config/skyweave_compat-features.properties'
    feature_settings = {}
    if feature_file.exists():
        for line in feature_file.read_text().splitlines():
            if line.strip() and not line.lstrip().startswith('#'):
                key, value = line.split('=', 1)
                feature_settings[key.strip()] = value.strip()
    if args.disable_carver_adapter:
        feature_settings['terrain.relative_carvers'] = 'false'
    if args.disable_structure_policy:
        feature_settings['terrain.structure_distribution'] = 'false'
    if args.dh_pregen:
        feature_settings['distant_horizons.world_cache'] = str(args.dh_world_key).lower()
    if feature_settings:
        feature_file.write_text(''.join(f'{key}={value}\n' for key, value in sorted(feature_settings.items())))
    # Native probes do not serve maps or download/render map assets.
    # Only disposable configuration is edited; the integration still class-loads.
    bluemap = run / 'config/bluemap'
    if bluemap.is_dir():
        (bluemap / 'webserver.conf').write_text('enabled: false\n')
        (bluemap / 'core.conf').write_text('accept-download: false\nmetrics: false\n')

    (run / 'config/skyweave_compat-common.toml').write_text(
        f'minimumGap = 64\nmaximumGap = {args.maximum_gap}\nminimumIslandRadius = 96\n')
    if args.generation_config:
        shutil.copy2(args.generation_config.expanduser().resolve(), run / 'config/skyweave_compat-generation.json')
    if args.shape_config:
        shutil.copy2(args.shape_config.expanduser().resolve(), run / 'config/skyweave_compat-shapes.json')
    if args.interior_config:
        shutil.copy2(args.interior_config.expanduser().resolve(), run / 'config/skyweave_compat-interiors.json')
    if args.river_config:
        shutil.copy2(args.river_config.expanduser().resolve(), run / 'config/skyweave_compat-rivers.json')
    if args.ocean_config:
        shutil.copy2(args.ocean_config.expanduser().resolve(), run / 'config/skyweave_compat-oceans.json')
    if args.structure_config:
        shutil.copy2(args.structure_config.expanduser().resolve(), run / 'config/skyweave_compat-structures.json')
    # Reuse the owner's existing EULA acceptance; never create acceptance implicitly.
    shutil.copy2(server / 'eula.txt', run / 'eula.txt')
    preset = {'current': 'sky_archipelago:sky_islands', 'adapted': 'skyweave_compat:islands_v1', 'vanilla': 'minecraft:normal'}.get(args.generation, ('skyweave_compat:islands_v1' if args.natural_profile == 'adapted' else 'sky_archipelago:sky_islands') if args.natural else 'minecraft:flat')
    if args.variation or args.locator or args.lookup or args.structures or args.surface_structures or args.coverage or args.benchmark or args.biomes or args.surface_climate or args.mechanics or args.islands or args.chunk_futures or args.dh_pregen: preset = 'skyweave_compat:islands_v1'
    if args.interiors or args.veins or args.water or args.materials or args.oceans: preset = 'skyweave_compat:islands_v1'
    (run / 'server.properties').write_text('\n'.join([
        'server-ip=127.0.0.1', 'server-port=0', 'online-mode=false',
        'level-name=probe-world', f'level-type={preset}', f'level-seed={args.seed}',
        f'generate-structures={str(args.lookup or args.natural or args.chunk_futures or args.structures or args.surface_structures or args.dh_pregen or args.benchmark).lower()}', 'view-distance=2',
        'max-tick-time=0', '',
    ]))
    if args.fixed_biome:
        if preset != 'skyweave_compat:islands_v1':
            parser.error('--fixed-biome requires the adapted Skyweave preset')
        # Only this disposable world's data pack changes; normal pack resources stay untouched.
        fixture_pack = run / 'probe-world/datapacks/biome-fixture'
        target = fixture_pack / 'data/skyweave_compat/worldgen/world_preset/islands_v1.json'
        target.parent.mkdir(parents=True)
        with zipfile.ZipFile(mod) as jar:
            data = json.loads(jar.read('packs/terrain/data/skyweave_compat/worldgen/world_preset/islands_v1.json'))
            metadata = json.loads(jar.read('packs/terrain/pack.mcmeta'))
        data['dimensions']['minecraft:overworld']['generator']['biome_source'] = {
            'type': 'minecraft:fixed', 'biome': args.fixed_biome}
        metadata['pack']['description'] = 'Isolated native biome fixture'
        target.write_text(json.dumps(data, indent=2) + '\n')
        (fixture_pack / 'pack.mcmeta').write_text(json.dumps(metadata) + '\n')
        if args.empty_village_start:
            pool = fixture_pack / 'data/minecraft/worldgen/template_pool/village/plains/town_centers.json'
            pool.parent.mkdir(parents=True, exist_ok=True)
            pool.write_text(json.dumps({'name': 'minecraft:village/plains/town_centers', 'fallback': 'minecraft:empty', 'elements': []}) + '\n')
    if args.structure_debug:
        debug = run / 'probe-world/serverconfig/sky_archipelago-server.toml'
        debug.parent.mkdir(parents=True,exist_ok=True)
        debug.write_text('structureDebugEnabled=true\nstructureDebugBroadcastEnabled=false\nstructureAttemptPillarsEnabled=false\n')
    launch_args = next((server / 'libraries/net/neoforged/neoforge').glob('*/unix_args.txt'))
    command = [args.java, f'-Xmx{args.heap}',
               f'-Dneighbors.probe.structureId={args.structure_id or ""}',
               f'-Dneighbors.probe.previousStructures={run / "structure-witnesses.json"}',
               f'-Dneighbors.probe.layoutsOnly={str(args.layouts_only).lower()}',
               f'-Dneighbors.probe.reverseStructures={str(args.reverse_structure_order).lower()}',
               f'-Dneighbors.probe.farStructures={str(args.far_structure_fixture).lower()}',
               f'-Dskyarchipelago.structureBenchmark={str(args.structures or args.surface_structures or args.coverage).lower()}',
               f'-Dneighbors.probe.audio={str(bool(args.audio)).lower()}',
               f'-Dneighbors.probe.dhStopAfter={args.dh_stop_after}',
               f'-Dneighbors.probe.dhWorldKey={str(args.dh_world_key).lower()}',
               f'-Dneighbors.probe.generation={args.generation or ""}',
               f'-Dneighbors.probe.structures={str(args.structures or args.surface_structures).lower()}',
               f'-Dneighbors.probe.surfaceStructures={str(args.surface_structures).lower()}',
               f'-Dneighbors.probe.coverage={str(args.coverage).lower()}',
               f'-Dneighbors.probe.variation={str(bool(args.variation)).lower()}',
               f'-Dneighbors.probe.variationKind={(args.variation or 'fractured').upper()}',
               f'-Dneighbors.probe.locator={str(args.locator).lower()}',
               f'-Dneighbors.probe.cityWitness={str(args.city_witness).lower()}',
               f'-Dneighbors.probe.lookupRadius={args.lookup_radius}',
               f'-Dneighbors.probe.lookup={str(args.lookup).lower()}',
               f'-Dneighbors.probe.benchmark={str(args.benchmark).lower()}',
               f'-Dneighbors.probe.surfaceClimate={str(args.surface_climate).lower()}',
               f'-Dneighbors.probe.biomes={str(args.biomes).lower()}',
               f'-Dneighbors.probe.mechanics={str(args.mechanics).lower()}',
               f'-Dneighbors.probe.islands={str(args.islands).lower()}',
               f'-Dneighbors.probe.chunkFutures={str(args.chunk_futures).lower()}',
               f'-Dneighbors.probe.dhPregen={str(args.dh_pregen).lower()}',
               f'-Dneighbors.probe.terrain={str(args.terrain).lower()}',
               *([f'-Dneighbors.probe.terrainSeed={args.terrain_seed}'] if args.terrain_seed is not None else []),
               f'-Dneighbors.probe.overlap={str(args.overlap).lower()}',
               f'-Dneighbors.probe.interiors={str(args.interiors).lower()}',
               f'-Dneighbors.probe.veins={str(args.veins).lower()}',
               f'-Dneighbors.probe.water={str(args.water or args.oceans).lower()}',
               f'-Dneighbors.probe.oceans={str(args.oceans).lower()}',
               f'-Dneighbors.probe.materials={str(args.materials).lower()}',
               f'-Dneighbors.probe.waterRadius={args.water_radius}',
               f'-Dneighbors.probe.fluidTicks={args.fluid_ticks}',
               f'-Dneighbors.probe.expectedMaximumGap={args.maximum_gap}',
               f'-Dneighbors.probe.natural={str(args.natural).lower()}',
               f'-Dneighbors.probe.structureClusters={args.structure_clusters}',
               f'-Dneighbors.probe.samples={args.samples}', f'-Dneighbors.probe.output={report}',
               '@' + str(launch_args), 'nogui']
    began = time.monotonic()
    def launch(log_path):
        with log_path.open('w') as log:
            subprocess.run(command, cwd=run, stdout=log, stderr=subprocess.STDOUT, check=True, timeout=90 if args.dh_stop_after else 600)
        # Minecraft can exit zero after a shutdown exception, after writing the probe report.
        text = log_path.read_text(errors='replace')
        failures = [marker for marker in ['java.lang.OutOfMemoryError', 'Uncaught exception in server thread',
                    'Exception stopping the server', 'Encountered an unexpected exception'] if marker in text]
        if failures:
            if report.exists():
                data = json.loads(report.read_text())
                data.update(complete=False, runtimeFailures=failures, maximumHeap=args.heap)
                report.write_text(json.dumps(data, indent=2) + '\n')
            raise SystemExit(f'Native runtime failed: {", ".join(failures)}; inspect {log_path}')
    launch(output / (name + '.log'))
    if args.reopen and report.exists():
        first = json.loads(report.read_text())
        if first.get('complete') is False:
            raise SystemExit(f'Native check failed before reopening; diagnostics: {report}')
        if args.structures or args.surface_structures:
            (run / 'structure-witnesses.json').write_text(json.dumps(first))
        report.unlink()
        launch(output / (name + '-reopen.log'))
        if not report.exists():
            # Keep the first observations when the reopened runtime aborts before writing its report.
            first.update(complete=False, reopenFailure='Reopened runtime did not produce a report; inspect the reopen log')
            report.write_text(json.dumps(first, indent=2) + '\n')
        else:
            second = json.loads(report.read_text())
            def observed(data):
                if args.lookup:
                    witness = data.get('cityWitness', {})
                    return (data['candidates'], {key: value for key, value in witness.items() if key != 'presenceAfterPartialStart'})
                if args.water or args.oceans:
                    return [(site['mode'],site['source'],site['bedY'],site['bedSupported'])
                            for site in data['observations'][-1]['sites']]
                if args.structures or args.surface_structures:
                    return [(row['id'], [(sample['chunkX'], sample['chunkZ'], sample['bounds'], sample['piecesDigest'],
                            sample.get('blocks', {}).get('minecraft:reinforced_deepslate'))
                            for sample in row['candidates'] if sample['valid']]) for row in data['structures']]
                if args.natural:
                    return {key: data[key] for key in ['samples', 'villagePlan']} if 'villagePlan' in data else data['samples']
                return [(sample['x'], sample['z'], {key: value for key, value in sample['blocks'].items()
                        if key.endswith('_ore')}) for sample in data['samples']]
            match_key = ('reopenLookupMatch' if args.lookup else
                         'reopenWaterOwnershipMatch' if args.water or args.oceans else
                         'reopenStructuresMatch' if args.structures or args.surface_structures else
                         'reopenVillagesMatch' if args.natural else 'reopenOreCountsMatch')
            if observed(first) != observed(second):
                second[match_key] = False
                second['firstObservations'] = observed(first)
                report.write_text(json.dumps(second, indent=2) + '\n')
                raise SystemExit('Observations changed after reopening; inspect generation reports and logs')
            second[match_key] = True
            report.write_text(json.dumps(second, indent=2) + '\n')
    if report.exists():
        data = json.loads(report.read_text())
        data['wallSecondsIncludingStartup'] = time.monotonic() - began
        data['maximumHeap'] = args.heap
        if args.water or args.oceans: data['waterSearchRadius'] = args.water_radius
        data['peakResidentBytes'] = resource.getrusage(resource.RUSAGE_CHILDREN).ru_maxrss * (1 if sys.platform == 'darwin' else 1024)
        data['modSelection'] = args.audio or ('minimal' if args.minimal else 'full')
        data['installedJars'] = sorted(path.name for path in (run / 'mods').glob('*.jar'))
        data['carverAdapterDisabled'] = args.disable_carver_adapter
        data['fixedBiomeFixture'] = args.fixed_biome
        data['structureDebugFixture'] = args.structure_debug
        data['reverseStructureOrder'] = args.reverse_structure_order
        data['farStructureFixture'] = args.far_structure_fixture
        data['emptyVillageStartFixture'] = args.empty_village_start
        data['configuration'] = {path.name: json.loads(path.read_text()) for path in (run / 'config').glob('skyweave_compat-*.json')
                                 if path.name in {'skyweave_compat-generation.json', 'skyweave_compat-shapes.json', 'skyweave_compat-interiors.json',
                                                  'skyweave_compat-rivers.json', 'skyweave_compat-oceans.json',
                                                  'skyweave_compat-ancient-cities.json', 'skyweave_compat-structures.json'}}
        artifacts = list((run / 'mods').glob('*.jar')) if args.audio else [mod, fixture]
        data['artifacts'] = {path.name: hashlib.sha256((run / 'mods' / path.name).read_bytes()).hexdigest() for path in artifacts}
        report.write_text(json.dumps(data, indent=2) + '\n')

        if args.world_output and data.get('complete', True):
            shutil.copytree(run / 'probe-world', args.world_output)
            data['retainedWorld'] = str(args.world_output)
            report.write_text(json.dumps(data, indent=2) + '\n')

if not report.exists():
    raise SystemExit(f'Probe failed; inspect {output / (name + ".log")}')
data = json.loads(report.read_text())
if data.get('complete') is False:
    raise SystemExit(f'Native check failed; diagnostics: {report}')
if args.lookup:
    print('Native lookup presence:', report, 'rejected:', data['rejectedWithoutLoad'])
    raise SystemExit(0)
if args.audio:
    print('Analog Audio integration:', report)
    raise SystemExit(0)
if args.structures or args.surface_structures:
    print('Natural structure coverage:', report)
    raise SystemExit(0)
if args.coverage:
    print('Resolved worldgen coverage:', report)
    raise SystemExit(0)
if args.chunk_futures:
    print('Chunk future comparison:', report)
    raise SystemExit(0)
if args.dh_pregen:
    print('Disposable DH generation:', report)
    raise SystemExit(0)
if args.mechanics:
    print('Native mechanics survey:', report)
    raise SystemExit(0)
if args.islands:
    print('Island resource survey:', report)
    raise SystemExit(0)
if args.benchmark:
    print('Native bounded benchmark:', report)
    raise SystemExit(0)
if args.surface_climate:
    print('Surface climate survey:', report)
    raise SystemExit(0)
if args.biomes:
    print('Cave biome survey:', report)
    raise SystemExit(0)
if args.generation:
    print('Generated block survey:', report, 'seconds:', data['elapsedSeconds'])
    raise SystemExit(0)
if args.variation:
    print('Native fractured terrain:', report, 'seconds:', data['elapsedSeconds'])
    raise SystemExit(0)
if args.locator:
    print('Native metadata command:', report, 'seconds:', data['elapsedSeconds'])
    raise SystemExit(0)
if args.terrain:
    print('Native terrain survey:', report, 'seconds:', data['elapsedSeconds'])
    raise SystemExit(0)
if args.overlap:
    print('Native overlap comparison:', report, 'seconds:', data['elapsedSeconds'])
    raise SystemExit(0)
if args.interiors:
    print('Native interior sections:', report, 'seconds:', data['elapsedSeconds'])
    raise SystemExit(0)
if args.veins:
    print('Native vein witnesses:', report, 'seconds:', data['elapsedSeconds'])
    raise SystemExit(0)
if args.water or args.oceans:
    print('Native water check:', report, 'seconds:', data['elapsedSeconds'])
    raise SystemExit(0)
if args.materials:
    print('Native material check:', report)
    raise SystemExit(0)
if args.natural:
    print('Natural starts:', data['accepted'], 'Complete villages:', data['completed'])
    raise SystemExit(0)
print('Native accepted:', data['nativeAccepted'], 'Mixin rejections:', data['mixinRejections'])
for distance in [384, 512, 768]:
    rows = [v for row in data['samples'] for v in row['variants'] if v['maximumGap'] == distance]
    times = sorted(row['ms'] for row in rows)
    print(distance, {kind: sum(row['decision'] == kind for row in rows) for kind in ['ACCEPTED', 'TOO_CLOSE', 'NO_HOST']},
          'median ms', times[len(times)//2], 'max ms', max(times))
