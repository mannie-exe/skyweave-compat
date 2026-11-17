#!/usr/bin/env python3
"""Qualify optional encounters in owned native servers; installed inputs stay read-only."""
import argparse
import hashlib
import json
import re
import shutil
import subprocess
import tempfile
import time
import tomllib
import zipfile
from pathlib import Path
from fixture_files import copy_owned, stage_configs

ROOT = Path(__file__).resolve().parents[1]
REPAIRS = [
    'data/minecraft/worldgen/template_pool/trial_chambers/spawner/slow_ranged/murk.json',
    'data/minecraft/worldgen/template_pool/trial_chambers/spawner/slow_ranged/verdant.json',
    'data/create_skyweave/worldgen/processor_list/variants_slow_ranged.json',
]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--server', type=Path, required=True, help='Read-only installed game libraries')
    parser.add_argument('--pack', type=Path, required=True, help='Read-only authored pack/mod artifacts')
    parser.add_argument('--java', required=True)
    parser.add_argument('--profile', choices=['baseline', 'adapted', 'disabled', 'foreign'], required=True)
    parser.add_argument('--reopen', action='store_true')
    parser.add_argument('--with-dynamic-waters', action='store_true', help='Include exact native DW13.1.0 constructor observation')
    parser.add_argument('--server-export', type=Path, help='Exercise the exact built server recipe and authored config in an owned6G fixture')
    parser.add_argument('--trial-bridge-off', action='store_true', help='Full-pack negative control for V&V/Lithostitched trial selection')
    parser.add_argument('--mode-conflict', action='store_true', help='Expect refusal of stacked native/per-emission modes')
    args = parser.parse_args()
    if args.server_export and (args.profile != 'adapted' or args.mode_conflict or args.with_dynamic_waters):
        parser.error('--server-export requires adapted without other stack controls')
    if args.trial_bridge_off and not args.server_export:
        parser.error('--trial-bridge-off requires --server-export')
    server, pack = args.server.resolve(), args.pack.resolve()
    launch = server / 'libraries/net/neoforged/neoforge/21.1.265/unix_args.txt'
    if not launch.is_file():
        parser.error('Expected pinned NeoForge21.1.265 installed libraries')
    mod_version = re.search(r'^mod_version=(.+)$', (ROOT / 'gradle.properties').read_text(), re.M)[1]
    product = ROOT / f'build/libs/skyweave-compat-{mod_version}-mc1.21.1-neoforge.jar'
    probe = ROOT / 'build/probe/skyweave-compat-probe.jar'
    output = ROOT / 'build/verification/encounters'
    output.mkdir(parents=True, exist_ok=True)
    report = {'profile': args.profile, 'productSha256': digest(product), 'probeSha256': digest(probe),
              'maximumHeap': '6G' if args.server_export else '4G', 'boots': [], 'modeConflict': args.mode_conflict}
    wanted = {'sky_archipelago', 'variantsandventures', 'resourcefullib'}
    if args.with_dynamic_waters:
        wanted.add('dynamicwaters')
    suffix = '-full' if args.server_export else '-dw' if args.with_dynamic_waters else ''
    if args.trial_bridge_off:
        suffix += '-trial-off'
    selected = {}
    for source in sorted((pack / 'pack/mods').glob('*.jar')):
        with zipfile.ZipFile(source) as jar:
            if 'META-INF/neoforge.mods.toml' not in jar.namelist():
                continue
            meta = tomllib.loads(jar.read('META-INF/neoforge.mods.toml').decode())
        ids = {entry['modId'] for entry in meta.get('mods', [])}
        if ids & wanted:
            selected[source] = ids & wanted
    if set().union(*selected.values()) != wanted:
        parser.error('Exact Sky Archipelago, V&V and Resourceful Lib inputs required')
    with tempfile.TemporaryDirectory(prefix='skyweave-encounters-') as temp:
        run = Path(temp)
        (run / 'libraries').symlink_to(server / 'libraries', target_is_directory=True)
        (run / 'mods').mkdir()
        if args.server_export:
            export = args.server_export.resolve()
            report['serverExportSha256'] = digest(export)
            report['dependencies'] = {}
            with zipfile.ZipFile(export) as archive:
                recipe = json.loads(archive.read('.empack-consumer/release.json'))
                for entry in recipe['files']:
                    if entry['server']['kind'] == 'unsupported' or not entry['destination'].startswith('mods/'):
                        continue
                    relative = Path(entry['destination'])
                    if relative.parent != Path('mods') or relative.suffix != '.jar':
                        raise ValueError('Unexpected exported mod path')
                    content = (archive.read('.empack-consumer/'+entry['source']['path']) if entry['source']['kind'] == 'asset'
                               else (pack / 'pack' / relative).read_bytes())
                    actual = hashlib.sha256(content).hexdigest()
                    if actual != entry['sha256']:
                        raise ValueError('Exported mod differs from native input: '+str(relative))
                    (run / relative).write_bytes(content)
                    report['dependencies'][relative.name] = actual
            if report['dependencies'].get('skyweave-compat.jar') != digest(product):
                raise ValueError('Server recipe does not carry this product')
            copy_owned(probe, run / 'mods' / probe.name, run)
            report['authoredConfiguration'] = stage_configs(pack, run)
            dh = run / 'config/DistantHorizons.toml'
            if dh.is_file():
                text, count = re.subn(r'(?m)^(\s*generatorPlan\s*=).*$', r'\1 "DISABLED"', dh.read_text())
                if count != 1:
                    raise ValueError('Ambiguous fixture DH generation plan')
                dh.write_text(text)
                report['authoredConfiguration']['config/DistantHorizons.toml']['fixtureOverride'] = 'Disable background DH generation; no pregen/LOD claim'
        else:
            for source in [product, probe, *selected]:
                copy_owned(source, run / 'mods' / source.name, run)
            report['dependencies'] = {p.name: digest(p) for p in selected}
        copy_owned(server / 'eula.txt', run / 'eula.txt', run)
        owned = run / 'config/skyweave'
        owned.mkdir(parents=True, exist_ok=True)
        keys = re.findall(r'\("([a-z_]+\.[a-z_]+)"', (ROOT / 'src/main/java/design/inherent/skyweavecompat/Feature.java').read_text())
        enabled = set()
        if args.profile in {'adapted', 'disabled', 'foreign'}:
            enabled.add('terrain.preset')
        if args.profile in {'adapted', 'foreign'}:
            enabled.update({'minecraft.patrol_probability', 'variantsandventures.spawner_emissions', 'variantsandventures.local_water'})
        if not args.server_export:
            (owned / 'skyweave_compat-features.properties').write_text(''.join(f'{key}={str(key in enabled).lower()}\n' for key in keys))
        elif args.trial_bridge_off:
            file = owned / 'skyweave_compat-features.properties'
            text, count = re.subn(r'(?m)^variantsandventures\.lithostitched_trials=true$', 'variantsandventures.lithostitched_trials=false', file.read_text())
            if count != 1:
                raise ValueError('Expected one enabled authored trial bridge')
            file.write_text(text)
        foreign_config = json.loads((pack / 'overrides/common/config/variantsandventures.json').read_text())
        for key in foreign_config:
            if key.endswith('SpawnerChance'):
                foreign_config[key] = 35 if args.mode_conflict else 0
        (run / 'config/variantsandventures.json').write_text(json.dumps(foreign_config, indent=2)+'\n')
        if args.with_dynamic_waters:
            source = pack / 'overrides/common/config/dynamicwaters-common.toml'
            copy_owned(source, run / 'config/dynamicwaters-common.toml', run)
        if args.mode_conflict:
            # This exact authored profile is complete, typed and server-owned.
            value = json.loads((pack / 'overrides/common/config/skyweave/skyweave_compat-encounters.json').read_text())
            value['spawners']['mode'] = 'PER_EMISSION'
            (owned / 'skyweave_compat-encounters.json').write_text(json.dumps(value)+'\n')
        is_islands = args.profile in {'adapted', 'disabled'}
        preset = 'skyweave_compat:islands_v1' if is_islands else 'minecraft:flat'
        (run / 'server.properties').write_text('\n'.join([
            'server-ip=127.0.0.1', 'server-port=0', 'online-mode=false', 'level-name=probe-world',
            f'level-type={preset}', 'level-seed=42', 'generate-structures=false', 'view-distance=2',
            'simulation-distance=2', 'max-tick-time=0', 'spawn-protection=0', '',
            '' if is_islands else 'generator-settings='+json.dumps({'biome': 'minecraft:plains', 'layers': [{'height': 1, 'block': 'minecraft:bedrock'}], 'features': False, 'lakes': False, 'structure_overrides': []}),
        ]))
        data = run / 'probe-world/datapacks/encounter-contract'
        data.mkdir(parents=True)
        (data / 'pack.mcmeta').write_text(json.dumps({'pack': {'pack_format': 48, 'description': 'Disposable encounter qualification'}}))
        if not is_islands:
            dimension = json.loads((ROOT / 'src/main/resources/packs/terrain/data/skyweave_compat/dimension_type/islands_v1.json').read_text())
            dimension['effects'] = 'minecraft:overworld'
            p = data / 'data/minecraft/dimension_type/overworld.json'
            p.parent.mkdir(parents=True)
            p.write_text(json.dumps(dimension))
        repaired = args.profile != 'baseline'
        if repaired and not args.server_export:
            # Copy exact built product bytes, rather than a parallel test-only repair.
            resource = pack / 'overrides/common/mods/skyweave-resource-patches-0.1.0.jar'
            with zipfile.ZipFile(resource) as jar:
                report['resourcePatchesSha256'] = digest(resource)
                report['repairEntries'] = {}
                for name in REPAIRS:
                    content = jar.read(name)
                    if content != (pack / 'resource-patches' / name).read_bytes():
                        raise ValueError('Resource Patches needs regeneration: '+name)
                    p = data / name
                    p.parent.mkdir(parents=True, exist_ok=True)
                    p.write_bytes(content)
                    report['repairEntries'][name] = hashlib.sha256(content).hexdigest()
        for boot in range(2 if args.reopen else 1):
            receipt = output / f'{args.profile}{suffix}{"-mode-conflict" if args.mode_conflict else ""}-{boot+1}.json'
            log = receipt.with_suffix('.log')
            receipt.unlink(missing_ok=True)
            command = [args.java, '-Xms256M', '-Xmx'+report['maximumHeap'], '-Djava.awt.headless=true',
                       '-Dneighbors.probe.encounters=true', f'-Dneighbors.probe.encounterRepair={str(repaired).lower()}',
                       f'-Dneighbors.probe.output={receipt}', '-Dmixin.debug.export=true', '@'+str(launch), 'nogui']
            started = time.monotonic()
            config_inputs = {str(p.relative_to(run)): digest(p) for p in (run / 'config/variantsandventures.json', owned / 'skyweave_compat-encounters.json') if p.exists()}
            with log.open('w') as stream:
                process = subprocess.Popen(command, cwd=run, stdout=stream, stderr=subprocess.STDOUT)
                try:
                    code = process.wait(timeout=180)
                except subprocess.TimeoutExpired:
                    process.terminate()
                    try:
                        process.wait(timeout=15)
                    except subprocess.TimeoutExpired:
                        process.kill()
                        process.wait()
                    raise SystemExit('Encounter fixture timed out; inspect '+str(log))
            text = log.read_text(errors='replace')
            if args.mode_conflict:
                item = {'complete': 'Done (' not in text and 'PER_EMISSION requires all six V&V' in text,
                        'usefulRefusal': 'PER_EMISSION requires all six V&V' in text}
            else:
                item = json.loads(receipt.read_text()) if receipt.exists() else {'complete': False, 'reportMissing': True}
                boundaries = {
                    'net/minecraft/world/level/levelgen/PatrolSpawner': 'minecraft.patrol_probability',
                    'net/minecraft/world/level/BaseSpawner': 'variantsandventures.spawner_emissions',
                    'com/faboslav/variantsandventures/common/entity/mob/MurkEntity': 'variantsandventures.local_water',
                    'com/faboslav/variantsandventures/common/entity/ai/goal/TargetAboveWaterGoal': 'variantsandventures.local_water',
                    'dev/worldgen/lithostitched/impl/LithostitchedInternalHooks': 'variantsandventures.lithostitched_trials',
                }
                item['nativeMixinBoundaries'] = {}
                for target, feature in boundaries.items():
                    path = run / '.mixin.out/class' / (target+'.class')
                    present = path.is_file() and b'design/inherent/skyweavecompat/encounters/' in path.read_bytes()
                    expected = (args.server_export is not None or feature in enabled)
                    if feature == 'variantsandventures.lithostitched_trials':
                        expected = bool(args.server_export) and not args.trial_bridge_off
                    item['nativeMixinBoundaries'][target] = {'mainEncounterReferences': present, 'expected': expected}
                    if present != expected:
                        item['complete'] = False
                item['nativeConfigWriters'] = {name: {'before': value, 'after': digest(run / name)} for name, value in config_inputs.items()}
                if any(row['before'] != row['after'] for row in item['nativeConfigWriters'].values()):
                    item['complete'] = False
                side_report = Path(str(receipt)+'.dist.json')
                if side_report.exists():
                    item['sideLoading'] = json.loads(side_report.read_text())
                    if item['sideLoading'].get('matchedEvents', 0):
                        item['complete'] = False
            item.update(boot=boot+1, nativeExit=code, wallSeconds=round(time.monotonic()-started, 3))
            if not args.mode_conflict and code:
                item['complete'] = False
            receipt.write_text(json.dumps(item, indent=2)+'\n')
            report['boots'].append(item)
            if not item['complete']:
                print(json.dumps(item, indent=2))
                raise SystemExit('Encounter fixture failed; inspect '+str(log))
        report['complete'] = all(boot['complete'] for boot in report['boots'])
        destination = output / f'{args.profile}{suffix}{"-mode-conflict" if args.mode_conflict else ""}.json'
        destination.write_text(json.dumps(report, indent=2)+'\n')
        print(json.dumps({'profile': args.profile, 'boots': len(report['boots']), 'complete': report['complete'], 'report': str(destination)}, indent=2))


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


if __name__ == '__main__':
    main()
