#!/usr/bin/env python3
"""Run the native placement fixture using an installed server's libraries and mods."""
import resource
import sys
import argparse
import hashlib
import time
import json
import shutil
import sqlite3
import subprocess
import tempfile
import zipfile
import tomllib
import re
from contextlib import closing
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--mod', type=Path, help='Exact Skyweave: Compatibility Patches JAR for an isolated comparison; defaults to the local build')
parser.add_argument('--server', type=Path, required=True, help='Installed game directory; read-only input')
parser.add_argument('--java', default='java')
parser.add_argument('--heap', choices=['4G', '6G', '8G'], default='4G', help='Maximum Java heap; distributed island scans need 6G')
parser.add_argument('--samples', type=int, default=12, help='Layouts per seed')
parser.add_argument('--reopen', action='store_true', help='Reopen generated samples and require unchanged ore counts or village starts')
parser.add_argument('--ocean-fringes', action='store_true', help='With --perimeter, verify parent ocean materials through native surface/features')
parser.add_argument('--world-output', type=Path, help='Keep a successful disposable world at a new path for isolated client inspection')
parser.add_argument('--minimal', action='store_true', help='Generate with only Sky Archipelago and Skyweave: Compatibility Patches, plus the read-only probe')
parser.add_argument('--wayfinder-dh', action='store_true', help='With --wayfinder, also qualify native DH column reads beyond4096 and at large coordinates')
parser.add_argument('--wayfinder-high', action='store_true', help='With --wayfinder, use Skyweave build heights in the bounded flat fixture')
parser.add_argument('--wayfinder-public', action='store_true', help='With --wayfinder, verify explicit non-operator access from a server policy file')
parser.add_argument('--skylights-only', action='store_true', help='With --interiors, check bounded roof openings without repeating the volume survey')
parser.add_argument('--dh-stop-after', type=int, default=0, metavar='SECONDS', help='With --dh-pregen, stop the disposable server during generation (1 to 30 seconds)')
parser.add_argument('--dh-world-key', action='store_true', help='With --dh-pregen, verify opt-in per-save cache identity in the disposable world')
parser.add_argument('--dynamic-waters', type=Path, help='Exact Dynamic Waters 13.1.0 artifact for the disposable integration fixture')
mode = parser.add_mutually_exclusive_group()
mode.add_argument('--wayfinder', action='store_true', help='Exercise native Wayfinder commands, collision landings and server policy')
mode.add_argument('--perimeter', action='store_true', help='Check granular remnant bounds and native fill')
mode.add_argument('--hydrology', action='store_true', help='Check native Dynamic Waters planning on actual island terrain')
mode.add_argument('--variation', choices=['fractured', 'deep_dark'], help='Verify an explicit fractured geometry/ecology fixture through native fill and features')
mode.add_argument('--access', action='store_true', help='Check native portal/travel policy, initial companion spawn and structure-command guidance')
mode.add_argument('--quests', action='store_true', help='Check native FTB task, party and reward semantics in a disposable fixture book')
mode.add_argument('--structure-search', action='store_true', help='Exercise native asynchronous structure discovery across normal server ticks')
mode.add_argument('--locator', action='store_true', help='Exercise the bounded metadata command without generating chunks')
mode.add_argument('--lookup', action='store_true', help='Check native presence rejection for unselected structure candidates without chunk generation')
mode.add_argument('--audio', choices=['minimal', 'create', 'analog', 'pair', 'full'], help='Check Analog Audio registration and optional dependency boundaries')
mode.add_argument('--structures', action='store_true', help='Survey natural underground structure starts and realize critical layouts')
mode.add_argument('--surface-structures', action='store_true', help='Survey vanilla surface and water structure families')
mode.add_argument('--coverage', action='store_true', help='Export resolved worldgen resources and bounded native eligibility checks')
mode.add_argument('--village-pools', action='store_true', help='Verify repaired NPC pools through native template entity placement')
mode.add_argument('--yung', choices=['policy', 'frames', 'supports', 'hut-supports', 'temples'], help='Verify native replacement flags, support bounds, coordinate frames or temple restrictions')
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
mode.add_argument('--ecology', action='store_true', help='Inspect resolved Geophilic decoration and native stages across island interiors and fragments')
mode.add_argument('--oceans', action='store_true', help='Check experimental ocean habitats with an explicit --ocean-config')
parser.add_argument('--fixed-biome', help='Isolated marked preset with one explicit biome, for deterministic rare-feature fixtures')
parser.add_argument('--natural-shore-fixture', action='store_true', help='Allow shore hosts only in the disposable fixed-biome preset')
parser.add_argument('--city-witness', action='store_true', help='With seed-42 --lookup and a frozen shape profile, verify the retained 90-piece city control')
parser.add_argument('--lookup-radius', type=int, default=0, help='With --lookup, exercise native cold/warm structure discovery from spawn (1..100 random-spread rings)')
parser.add_argument('--search-radius', type=int, default=12800, help='Structure command radius in blocks')
parser.add_argument('--search-min-matches', type=int, default=0, help='Require at least this many real starts in both cold/warm search controls')
parser.add_argument('--search-target', default='structure=ctov:medium/village_plains', help='One family= or structure= selector')
parser.add_argument('--search-planning', action='store_true', help='With --structure-search, compare direct/grid candidates and actual eager/lazy/concurrent native plans')
parser.add_argument('--policy-disabled', action='store_true', help='With --access, disable only the access/command/initial-spawn adaptations for native controls')
parser.add_argument('--without-c2me', action='store_true', help='Omit C2ME only in the disposable scheduling fixture')
parser.add_argument('--lookup-structure', default='minecraft:ancient_city', help='Exact structure ID for the native lookup check')
parser.add_argument('--structure-id', help='Managed family key (such as villages), vanilla structure name, or exact namespaced supported ID')
parser.add_argument('--settlement-review', choices=['allowances', 'fits'], help='With --surface-structures, export bounded family allowance or village fit diagnostics')
parser.add_argument('--sky-neighborhood', action='store_true', help='With --surface-structures, check native close-range Sky Villages and actual host blocks')
parser.add_argument('--structure-debug', action='store_true', help='Enable native decision logs only in the disposable structure fixture')
parser.add_argument('--structure-clusters', type=int, default=16, help='Accepted main-island clusters for structure sampling (1..64)')
parser.add_argument('--layouts-only', action='store_true', help='With structure checks, inspect canonical starts without generating their entire footprints')
parser.add_argument('--water-radius', type=int, default=8192, help='Bounded native river/ocean witness search radius in blocks (1..32768)')
parser.add_argument('--fluid-ticks', type=int, default=600, help='Native ticks for --water; 0 checks only initial material, maximum 3600')
parser.add_argument('--drainage-only', action='store_true', help='With --hydrology and zero fluid ticks, inspect fine route continuity without creating chunks')
parser.add_argument('--water-witnesses', type=Path, help='With --hydrology, observe a bounded exact-seed water regression fixture through native chunk stages')
parser.add_argument('--climate-only', action='store_true', help='Check exact climate witnesses without requiring the unrelated channel-flow fixture')
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
parser.add_argument('--search-fixture', choices=['mixed', 'cap', 'lifecycle'], help='With --structure-search, test mixed placements, a candidate cap, or native caller/world lifecycle')
parser.add_argument('--terrain-seed', type=int, help='Survey this exact seed instead of the five-seed terrain comparison')
parser.add_argument('--maximum-gap', type=int, default=128)
parser.add_argument('--disable-climate-adapter', action='store_true', help='Disable only the climate adaptation in the disposable fixture')
parser.add_argument('--disable-carver-adapter', action='store_true',
                    help='Disable the relative-carving adaptation in the disposable startup configuration')
args = parser.parse_args()
if args.wayfinder_dh and (not args.wayfinder or args.minimal): parser.error('--wayfinder-dh requires --wayfinder with DH installed')
if args.wayfinder_high and not args.wayfinder: parser.error('--wayfinder-high requires --wayfinder')
if args.wayfinder_public and (not args.wayfinder or args.wayfinder_dh): parser.error('--wayfinder-public requires --wayfinder without --wayfinder-dh')
if args.search_fixture and not args.structure_search: parser.error('--search-fixture requires --structure-search')
if args.quests and args.minimal: parser.error('--quests requires the installed FTB runtime')
if args.policy_disabled and not args.access: parser.error('--policy-disabled requires --access')
if not 0 <= args.search_min_matches <= 65536 or args.search_min_matches and not args.structure_search:
    parser.error('--search-min-matches requires --structure-search and a count from 0 through 65536')
if args.search_planning and not args.structure_search:
    parser.error('--search-planning requires --structure-search')
if (args.settlement_review or args.sky_neighborhood) and not args.surface_structures:
    parser.error('--settlement-review/--sky-neighborhood require --surface-structures')
if args.settlement_review and args.sky_neighborhood:
    parser.error('Select one settlement diagnostic')
if args.ocean_fringes and not args.perimeter: parser.error('--ocean-fringes requires --perimeter')
if args.climate_only and (not args.hydrology or not args.water_witnesses or args.fluid_ticks != 0 or args.reopen):
    parser.error('--climate-only requires --hydrology --water-witnesses --fluid-ticks 0 without --reopen')
if args.natural_shore_fixture and not args.fixed_biome:
    parser.error('--natural-shore-fixture requires --fixed-biome')
if args.ecology and (args.minimal or not args.fixed_biome):
    parser.error('--ecology requires Geophilic and an explicit --fixed-biome; it cannot use --minimal')
if args.drainage_only and (not args.hydrology or args.fluid_ticks != 0 or args.reopen):
    parser.error('--drainage-only requires --hydrology --fluid-ticks 0 without --reopen')
if args.water_witnesses:
    witness = json.loads(args.water_witnesses.read_text())
    if not args.hydrology or args.drainage_only or args.fixed_biome or witness['seed'] != args.seed or not 1 <= len(witness['points']) <= 16:
        parser.error('--water-witnesses requires --hydrology, its exact seed, 1..16 points and the natural biome source')
if args.city_witness and (not args.lookup or args.seed != 42 or not args.shape_config):
    parser.error('--city-witness requires --lookup --seed 42 and its explicit frozen --shape-config')
if not 0 <= args.lookup_radius <= 100 or args.lookup_radius and not args.lookup:
    parser.error('--lookup-radius requires --lookup and at most 100 rings')
if not re.fullmatch(r'[a-z0-9_.-]+:[a-z0-9_./-]+', args.lookup_structure):
    parser.error('--lookup-structure requires a namespaced structure ID')
if args.lookup_structure != 'minecraft:ancient_city' and (not args.lookup or args.city_witness):
    parser.error('--lookup-structure requires --lookup without --city-witness')
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
if args.minimal and args.generation != 'adapted' and not any((args.wayfinder, args.access, args.perimeter, args.hydrology, args.variation, args.structure_search, args.locator, args.lookup, args.coverage, args.structures, args.surface_structures, args.overlap, args.interiors, args.veins, args.water, args.materials, args.oceans, args.biomes, args.surface_climate, args.terrain, args.benchmark)):
    parser.error('--minimal requires a terrain, coverage, structure, interior, vein, water or material check')
if args.reopen and not (args.quests or args.access or args.ocean_fringes or args.structure_search or args.hydrology or args.lookup or args.generation or args.natural or args.structures or args.surface_structures or args.water or args.oceans):
    parser.error('--reopen requires generation, natural villages, structures, water or ocean fringes')
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
if args.structure_id and not re.fullmatch(r'[a-z0-9_.-]+(?::[a-z0-9_./-]+)?', args.structure_id):
    parser.error('--structure-id requires a vanilla name or namespaced resource ID')
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
if args.village_pools: name = f'village-pools-{args.seed}'
if args.yung: name = f'yung-{args.yung}-{args.seed}'
if args.benchmark: name = f'benchmark-{args.seed}'
if args.lookup: name = f'lookup-{args.seed}'
if args.access: name = 'access-policy-' + ('disabled-' if args.policy_disabled else 'enabled-') + str(args.seed)
if args.wayfinder: name = f'wayfinder-{args.seed}'
if args.wayfinder_dh: name += '-dh'
if args.wayfinder_high: name += '-high'
if args.wayfinder_public: name += '-public'
if args.quests: name = f'quest-behavior-{args.seed}'
if args.structure_search: name = f'structure-search-{args.seed}-' + args.search_target.replace(':', '-').replace('/', '-').replace('=', '-')
if args.locator: name = f'locator-{args.seed}'
if args.perimeter: name = f'perimeter-{args.seed}'
if args.hydrology: name = f'hydrology-{args.seed}'
if args.climate_only: name += '-climate-only'
if args.drainage_only: name += '-drainage'
if args.water_witnesses: name += '-' + args.water_witnesses.stem
if args.variation: name = f'variation-{args.variation}-{args.seed}'
if args.overlap: name = 'overlap'
if args.interiors: name = f'interiors-{args.seed}'
if args.skylights_only:
    if not args.interiors: parser.error('--skylights-only requires --interiors')
    name += '-skylights'
if args.veins: name = f'veins-{args.seed}'
if args.water: name = f'water-{args.seed}-{args.fluid_ticks}'
if args.materials: name = f'materials-{args.seed}'
if args.ecology: name = f'ecology-{args.seed}'
if args.oceans: name = f'oceans-{args.seed}-{args.fluid_ticks}'
if args.surface_climate: name = f'surface-climate-{args.seed}'
if args.chunk_futures: name = f'chunk-futures-{args.seed}'
if args.dh_pregen: name = f'dh-pregen-{args.seed}'
if args.dh_stop_after: name += '-shutdown'
if args.dh_world_key: name += '-world-key'
if args.settlement_review: name += '-settlement-' + args.settlement_review
if args.sky_neighborhood: name += '-sky-neighborhood'
if args.ocean_fringes: name += '-ocean-fringes'
if args.disable_climate_adapter: name += '-native-climate'
if args.disable_carver_adapter:
    name += '-native-carvers'
if args.minimal:
    name += '-minimal'
if args.audio: name = f'audio-{args.audio}'
if args.structure_id: name += '-' + args.structure_id.replace(':', '-').replace('/', '-')
if args.layouts_only: name += '-layouts'
if args.disable_structure_policy: name += '-native-policy'
if args.reverse_structure_order: name += '-reverse'
if args.far_structure_fixture: name += '-far'
if args.empty_village_start: name += '-empty-village'
if args.structure_config: name += '-' + args.structure_config.stem
if args.fixed_biome: name += '-' + args.fixed_biome.replace(':', '-').replace('/', '-')
if args.natural_shore_fixture: name += '-shore-hosts'
for label, profile in [('geometry', args.generation_config), ('shapes', args.shape_config),
                       ('interiors', args.interior_config), ('rivers', args.river_config), ('oceans', args.ocean_config)]:
    if profile: name += '-' + label + '-' + profile.stem
if args.without_c2me: name += '-without-c2me'
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
        if args.without_c2me and 'c2me' in ids: continue
        retired = ids & {'create_sky_compat', 'sky_village_neighbors', 'veil_iris_fix'}
        if retired: raise SystemExit('Remove retired integration artifacts from fixture inputs: ' + ', '.join(sorted(retired)))
        if 'skyweave_compat' in ids:
            continue
        if args.minimal or (args.audio and args.audio != 'full'):
            selected = set() if args.wayfinder and args.minimal else {'sky_archipelago'}
            if args.audio in ('create', 'pair'): selected.add('create')
            if args.audio in ('analog', 'pair'): selected.add('analogaudio')
            if not ids & selected:
                continue
        (run / 'mods' / source.name).symlink_to(source)
    if args.minimal and not args.wayfinder and len(list((run / 'mods').glob('*.jar'))) != 1:
        raise SystemExit('Minimal run requires exactly one installed Sky Archipelago JAR')
    if args.audio and args.audio != 'full':
        expected = {'minimal': 1, 'create': 2, 'analog': 2, 'pair': 3}[args.audio]
        if len(list((run / 'mods').glob('*.jar'))) != expected:
            raise SystemExit('Audio fixture dependencies missing from installed server')
    if args.hydrology and not args.dynamic_waters:
        parser.error('--hydrology requires --dynamic-waters')
    if args.dynamic_waters:
        candidate = args.dynamic_waters.expanduser().resolve()
        expected = '8a1eed3810e1e7a375e6ed50e21e7950d08d0b115903877c44fc1645c8f237e0cd3cac0ae325aa4988677b161317ce5ce0e22e8f2695109a286fee374c956e51'
        if hashlib.sha512(candidate.read_bytes()).hexdigest() != expected:
            raise SystemExit('Dynamic Waters fixture requires the pinned 13.1.0 NeoForge artifact')
        shutil.copy2(candidate, run / 'mods' / candidate.name)
    shutil.copy2(mod, run / 'mods' / mod.name)
    shutil.copy2(fixture, run / 'mods' / fixture.name)
    for folder in ['config', 'defaultconfigs']:
        if not args.minimal and args.audio in (None, 'full') and (server / folder).exists():
            shutil.copytree(server / folder, run / folder)
    (run / 'config').mkdir(exist_ok=True)
    (run / 'config/skyweave').mkdir(exist_ok=True)
    if args.wayfinder_public:
        shutil.copy2(ROOT / 'src/probe/resources/wayfinder-public.json', run / 'config/skyweave/skyweave_compat-wayfinder.json')
    if args.search_fixture == 'cap':
        (run / 'config/skyweave/skyweave_compat-search.json').write_text(json.dumps({
            'candidateLimit': 16, 'timeLimitSeconds': 120, 'concurrentChunks': 2, 'tickBudgetMillis': 5}))
    if args.quests:
        book = run / 'config/ftbquests/quests'
        if book.exists(): shutil.rmtree(book)
        shutil.copytree(ROOT / 'src/probe/resources/quest-behavior', book)
        adq = run / 'config/aeronautics_delivery_quests.toml'
        if adq.exists():
            text, changes = re.subn(r'(?m)^(\s*maxActiveQuestsPerBoard\s*=\s*)\d+', r'\g<1>0', adq.read_text())
            if changes != 1: raise RuntimeError('Unrecognized ADQ fixture configuration')
            adq.write_text(text)
    if args.dynamic_waters:
        # VANILLA disables Dynamic Waters' continental network and ridge rewriting;
        # NONE also rewrites ridges, changing the climate underneath the island preset.
        (run / 'config/dynamicwaters-common.toml').write_text('[Generation]\nnormalRiverMode = "VANILLA"\n')
    feature_file = run / 'config/skyweave/skyweave_compat-features.properties'
    feature_settings = {}
    if feature_file.exists():
        for line in feature_file.read_text().splitlines():
            if line.strip() and not line.lstrip().startswith('#'):
                key, value = line.split('=', 1)
                feature_settings[key.strip()] = value.strip()
    if args.disable_climate_adapter:
        feature_settings['terrain.climate'] = 'false'
    if args.disable_carver_adapter:
        feature_settings['terrain.relative_carvers'] = 'false'
    if args.disable_structure_policy:
        feature_settings['terrain.structure_distribution'] = 'false'
    if args.dh_pregen:
        feature_settings['distant_horizons.world_cache'] = str(args.dh_world_key).lower()
    if args.policy_disabled:
        feature_settings.update({'minecraft.nether_access':'false', 'terrain.structure_command':'false', 'terrain.spawn_landing':'false'})
    if feature_settings:
        feature_file.write_text(''.join(f'{key}={value}\n' for key, value in sorted(feature_settings.items())))
    # Native probes do not serve maps or download/render map assets.
    # Only disposable configuration is edited; the integration still class-loads.
    bluemap = run / 'config/bluemap'
    if bluemap.is_dir():
        (bluemap / 'webserver.conf').write_text('enabled: false\n')
        (bluemap / 'core.conf').write_text('accept-download: false\nmetrics: false\n')

    (run / 'config/skyweave/skyweave_compat-common.toml').write_text(
        f'minimumGap = 64\nmaximumGap = {args.maximum_gap}\nminimumIslandRadius = 96\n')
    if args.generation_config:
        shutil.copy2(args.generation_config.expanduser().resolve(), run / 'config/skyweave/skyweave_compat-generation.json')
    if args.shape_config:
        shutil.copy2(args.shape_config.expanduser().resolve(), run / 'config/skyweave/skyweave_compat-shapes.json')
    if args.interior_config:
        shutil.copy2(args.interior_config.expanduser().resolve(), run / 'config/skyweave/skyweave_compat-interiors.json')
    if args.river_config:
        shutil.copy2(args.river_config.expanduser().resolve(), run / 'config/skyweave/skyweave_compat-rivers.json')
    if args.ocean_config:
        shutil.copy2(args.ocean_config.expanduser().resolve(), run / 'config/skyweave/skyweave_compat-oceans.json')
    if args.structure_config:
        shutil.copy2(args.structure_config.expanduser().resolve(), run / 'config/skyweave/skyweave_compat-structures.json')
    # Reuse the owner's existing EULA acceptance; never create acceptance implicitly.
    shutil.copy2(server / 'eula.txt', run / 'eula.txt')
    preset = {'current': 'sky_archipelago:sky_islands', 'adapted': 'skyweave_compat:islands_v1', 'vanilla': 'minecraft:normal'}.get(args.generation, ('skyweave_compat:islands_v1' if args.natural_profile == 'adapted' else 'sky_archipelago:sky_islands') if args.natural else 'minecraft:flat')
    if args.access or args.perimeter or args.hydrology or args.variation or args.structure_search or args.locator or args.lookup or args.structures or args.surface_structures or args.coverage or args.benchmark or args.biomes or args.surface_climate or args.mechanics or args.islands or args.chunk_futures or args.dh_pregen: preset = 'skyweave_compat:islands_v1'
    if args.interiors or args.veins or args.water or args.materials or args.ecology or args.oceans or args.village_pools or args.yung: preset = 'skyweave_compat:islands_v1'
    (run / 'server.properties').write_text('\n'.join([
        'server-ip=127.0.0.1', 'server-port=0', 'online-mode=false',
        'level-name=probe-world', f'level-type={preset}', f'level-seed={args.seed}',
        f'generate-structures={str(args.lookup or args.natural or args.structure_search or args.chunk_futures or args.structures or args.surface_structures or args.dh_pregen or args.benchmark).lower()}', 'view-distance=2',
        'max-tick-time=0', '',
    ]))
    if args.wayfinder_high:
        fixture_pack = run / 'probe-world/datapacks/wayfinder-heights'
        dimension = json.loads((ROOT / 'src/main/resources/packs/terrain/data/skyweave_compat/dimension_type/islands_v1.json').read_text())
        # Qualify actual build/LOD bounds without making the flat fixture an island preset.
        dimension['effects'] = 'minecraft:overworld'
        definitions = {'pack.mcmeta': {'pack': {'pack_format': 48, 'description': 'Disposable Wayfinder height contract'}},
                       'data/minecraft/dimension_type/overworld.json': dimension}
        for relative, value in definitions.items():
            destination = fixture_pack / relative; destination.parent.mkdir(parents=True, exist_ok=True)
            destination.write_text(json.dumps(value) + '\n')
    if args.search_fixture == 'mixed':
        locator_fixture = run / 'probe-world/datapacks/locator-contract'
        definitions = {
            'pack.mcmeta': {'pack': {'pack_format': 48, 'description': 'Disposable locator contracts'}},
            'data/skyweave_probe/worldgen/structure/air_marker.json': {'type': 'minecraft:jigsaw',
                'biomes': '#minecraft:is_overworld', 'step': 'surface_structures', 'spawn_overrides': {},
                'start_pool': 'skyweave_probe:air_marker', 'size': 0, 'start_height': {'absolute': 400},
                'max_distance_from_center': 80, 'use_expansion_hack': False},
            'data/skyweave_probe/worldgen/template_pool/air_marker.json': {'name': 'skyweave_probe:air_marker',
                'fallback': 'minecraft:empty', 'elements': [{'weight': 1, 'element': {
                    'element_type': 'minecraft:single_pool_element', 'location': 'skyweave_probe:air_marker',
                    'processors': 'minecraft:empty', 'projection': 'rigid'}}]},
            'data/skyweave_probe/worldgen/structure_set/air_marker.json': {'structures': [{'structure': 'skyweave_probe:air_marker', 'weight': 1}],
                'placement': {'type': 'minecraft:random_spread', 'spacing': 64, 'separation': 32, 'salt': 19233}},
            'data/skyweave_probe/tags/worldgen/structure/mixed.json': {'values': [
                '#minecraft:village' if args.minimal else '#skyweave_compat:managed/villages',
                'skyweave_probe:air_marker', 'minecraft:stronghold']},
        }
        for relative, value in definitions.items():
            target = locator_fixture / relative; target.parent.mkdir(parents=True, exist_ok=True)
            target.write_text(json.dumps(value))
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
        if args.natural_shore_fixture:
            data['dimensions']['minecraft:overworld']['generator']['sky_island_settings']['terrain']['natural_shore_biomes_enabled'] = True
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
               f'-Dneighbors.probe.wayfinder={str(args.wayfinder).lower()}',
               f'-Dneighbors.probe.wayfinderDh={str(args.wayfinder_dh).lower()}',
               f'-Dneighbors.probe.wayfinderHigh={str(args.wayfinder_high).lower()}',
               f'-Dneighbors.probe.wayfinderPublic={str(args.wayfinder_public).lower()}',
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
               f'-Dneighbors.probe.skyNeighborhood={str(args.sky_neighborhood).lower()}',
               f'-Dneighbors.probe.settlementReview={args.settlement_review or ""}',
               f'-Dneighbors.probe.structures={str(args.structures or args.surface_structures).lower()}',
               f'-Dneighbors.probe.surfaceStructures={str(args.surface_structures).lower()}',
               f'-Dneighbors.probe.coverage={str(args.coverage).lower()}',
               f'-Dneighbors.probe.villagePools={str(args.village_pools).lower()}',
               f'-Dneighbors.probe.yung={args.yung or ""}',
               f'-Dneighbors.probe.variation={str(bool(args.variation)).lower()}',
               f'-Dneighbors.probe.variationKind={(args.variation or 'fractured').upper()}',
               f'-Dneighbors.probe.access={str(args.access).lower()}',
               f'-Dneighbors.probe.quests={str(args.quests).lower()}',
               f'-Dneighbors.probe.policyDisabled={str(args.policy_disabled).lower()}',
               f'-Dneighbors.probe.structureSearch={str(args.structure_search).lower()}',
               f'-Dneighbors.probe.searchRadius={args.search_radius}',
               f'-Dneighbors.probe.searchMinMatches={args.search_min_matches}',
               f'-Dneighbors.probe.searchTarget={args.search_target}',
               f'-Dneighbors.probe.searchPlanning={str(args.search_planning).lower()}',
               f'-Dneighbors.probe.searchFixture={args.search_fixture or ""}',
               f'-Dneighbors.probe.locator={str(args.locator).lower()}',
               f'-Dneighbors.probe.oceanFringes={str(args.ocean_fringes).lower()}',
               f'-Dneighbors.probe.perimeter={str(args.perimeter).lower()}',
               f'-Dneighbors.probe.hydrology={str(args.hydrology).lower()}',
               f'-Dneighbors.probe.drainageOnly={str(args.drainage_only).lower()}',
               *([f'-Dneighbors.probe.waterWitnesses={args.water_witnesses.resolve()}'] if args.water_witnesses else []),
               f'-Dneighbors.probe.climateOnly={str(args.climate_only).lower()}',
               f'-Dneighbors.probe.cityWitness={str(args.city_witness).lower()}',
               f'-Dneighbors.probe.lookupRadius={args.lookup_radius}',
               f'-Dneighbors.probe.lookupStructure={args.lookup_structure}',
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
               f'-Dneighbors.probe.skylightsOnly={str(args.skylights_only).lower()}',
               f'-Dneighbors.probe.veins={str(args.veins).lower()}',
               f'-Dneighbors.probe.water={str(args.water or args.oceans).lower()}',
               f'-Dneighbors.probe.oceans={str(args.oceans).lower()}',
               f'-Dneighbors.probe.materials={str(args.materials).lower()}',
               f'-Dneighbors.probe.ecology={str(args.ecology).lower()}',
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
    if args.water_witnesses and report.exists():
        initial = json.loads(report.read_text())
        for site in initial.get('regressionSites', []):
            stages = site['generationStages']
            for status in ['minecraft:noise', 'minecraft:surface', 'minecraft:carvers', 'minecraft:features']:
                if status not in stages:
                    raise SystemExit(f'Missing actual water generation stage {status}: {site["x"]},{site["z"]}')
                sample = stages[status]
                if 'expectedWet' in site and (sample['wetAtWaterline'] != site['expectedWet'] or sample['lowSourceCells'] != 0):
                    raise SystemExit(f'Water regression at actual stage {status}: {site["x"]},{site["z"]}')
    if args.reopen and report.exists():
        first = json.loads(report.read_text())
        if first.get('complete') is False:
            raise SystemExit(f'Native check failed before reopening; diagnostics: {report}')
        if args.structures or args.surface_structures:
            (run / 'structure-witnesses.json').write_text(json.dumps(first))
        report.unlink()
        if args.structure_search: command.insert(1, '-Dneighbors.probe.savedStartsOnly=true')
        launch(output / (name + '-reopen.log'))
        if not report.exists():
            # Keep the first observations when the reopened runtime aborts before writing its report.
            first.update(complete=False, reopenFailure='Reopened runtime did not produce a report; inspect the reopen log')
            report.write_text(json.dumps(first, indent=2) + '\n')
        else:
            second = json.loads(report.read_text())
            if second.get('complete') is False:
                raise SystemExit(f'Native check failed after reopening; diagnostics: {report}')
            def observed(data):
                if args.quests:
                    return data['partyId']
                if args.access:
                    return data['spawn']
                if args.ocean_fringes:
                    return data['witnesses']
                if args.structure_search:
                    return data['warm']['outcome'], data['warm']['matches'], data['warm']['results']
                if args.hydrology:
                    return data['generated'], data['generatedLakes']
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
            match_key = ('reopenQuestClaimsMatch' if args.quests else 'reopenSpawnMatches' if args.access else 'reopenFringeMaterialsMatch' if args.ocean_fringes else 'reopenSavedStartsMatch' if args.structure_search else 'reopenHydrologyMatch' if args.hydrology else 'reopenLookupMatch' if args.lookup else
                         'reopenWaterOwnershipMatch' if args.water or args.oceans else
                         'reopenStructuresMatch' if args.structures or args.surface_structures else
                         'reopenVillagesMatch' if args.natural else 'reopenOreCountsMatch')
            if args.search_planning and 'planning' in first:
                second['initialPlanning'] = first['planning']
            if args.structure_search:
                second['initialSearch'] = {key: first[key] for key in ['cold', 'warm']}
            if observed(first) != observed(second):
                second[match_key] = False
                second['firstObservations'] = observed(first)
                report.write_text(json.dumps(second, indent=2) + '\n')
                raise SystemExit('Observations changed after reopening; inspect generation reports and logs')
            second[match_key] = True
            if args.water_witnesses:
                for initial, reopened in zip(first['regressionSites'], second['regressionSites'], strict=True):
                    if (initial['x'], initial['z']) != (reopened['x'], reopened['z']):
                        raise SystemExit('Water regression sites changed after reopening')
                    reopened['initialGenerationStages'] = initial['generationStages']
            report.write_text(json.dumps(second, indent=2) + '\n')
    if report.exists():
        data = json.loads(report.read_text())
        if args.dh_pregen and not args.dh_stop_after:
            database = run / 'probe-world/data/DistantHorizons.sqlite'
            with closing(sqlite3.connect(database.as_uri() + '?mode=ro', uri=True)) as connection:
                integrity = [row[0] for row in connection.execute('PRAGMA quick_check')]
                rows = connection.execute('SELECT count(*) FROM FullData').fetchone()[0]
            if integrity != ['ok'] or rows == 0:
                raise SystemExit('DH generation did not retain a healthy populated LOD database')
            data['lodStorage'] = {'integrity': integrity[0], 'fullDataRows': rows,
                                  'database': str(database.relative_to(run))}
        data['wallSecondsIncludingStartup'] = time.monotonic() - began
        data['maximumHeap'] = args.heap
        if args.water or args.oceans: data['waterSearchRadius'] = args.water_radius
        data['peakResidentBytes'] = resource.getrusage(resource.RUSAGE_CHILDREN).ru_maxrss * (1 if sys.platform == 'darwin' else 1024)
        data['modSelection'] = args.audio or ('minimal' if args.minimal else 'full')
        data['installedJars'] = sorted(path.name for path in (run / 'mods').glob('*.jar'))
        if args.dynamic_waters:
            data['dynamicWatersConfig'] = (run / 'config/dynamicwaters-common.toml').read_text()
        data['carverAdapterDisabled'] = args.disable_carver_adapter
        data['fixedBiomeFixture'] = args.fixed_biome
        data['naturalShoreFixture'] = args.natural_shore_fixture
        data['structureDebugFixture'] = args.structure_debug
        data['reverseStructureOrder'] = args.reverse_structure_order
        data['farStructureFixture'] = args.far_structure_fixture
        data['emptyVillageStartFixture'] = args.empty_village_start
        data['configuration'] = {path.name: json.loads(path.read_text()) for path in (run / 'config/skyweave').glob('skyweave_compat-*.json')
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
if args.wayfinder:
    print('Native Wayfinder:', report)
    raise SystemExit(0)
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
if args.village_pools:
    print('Native village NPC pools:', report)
    raise SystemExit(0)
if args.yung:
    print('Native YUNG contract:', report)
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
if args.quests:
    print('Native FTB party/tasks/rewards:', report, 'reloaded:', data.get('reloadedPartyClaims', False))
    raise SystemExit(0)
if args.access:
    print('Native access/spawn policy:', report, 'enabled:', data['policyEnabled'])
    raise SystemExit(0)
if args.structure_search:
    print('Native structure command:', report, 'seconds:', data['elapsedSeconds'])
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
    print('Native skylight controls:' if args.skylights_only else 'Native interior sections:', report, 'seconds:', data['elapsedSeconds'])
    raise SystemExit(0)
if args.veins:
    print('Native vein witnesses:', report, 'seconds:', data['elapsedSeconds'])
    raise SystemExit(0)
if args.water or args.oceans:
    print('Native water check:', report, 'seconds:', data['elapsedSeconds'])
    raise SystemExit(0)
if args.perimeter:
    print('Native granular perimeter check:', report)
    raise SystemExit(0)
if args.hydrology:
    print('Native climate witnesses:' if args.climate_only else 'Native watershed check:', report, 'seconds:', data['elapsedSeconds'])
    raise SystemExit(0)
if args.materials:
    print('Native material check:', report)
    raise SystemExit(0)
if args.ecology:
    print('Native ecology check:', report)
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
