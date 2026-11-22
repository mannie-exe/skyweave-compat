#!/usr/bin/env python3
"""Qualify generator ownership in disposable native worlds without touching an installation."""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import tempfile
import time
import zipfile

from fixture_files import copy_owned

ROOT = Path(__file__).resolve().parents[1]


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--server', type=Path, required=True, help='Read-only installed game directory supplying libraries and accepted EULA')
    parser.add_argument('--java', required=True)
    parser.add_argument('--pack', type=Path, required=True, help='Actual authored pack tree, read only')
    parser.add_argument('--native-village-data', action='store_true', help='Standalone Compat control using the pinned upstream Sky Villages definition')
    parser.add_argument('--village-definition', type=Path, help='Explicit before-control resource definition; never edits source packs')
    parser.add_argument('--disable-village-placement', action='store_true', help='Native explicit-off control')
    parser.add_argument('--identity-control', type=Path, help='Actual prior owned marker for identity-mismatch refusal')
    parser.add_argument('--compat', type=Path, help='Explicit product, including a retained before-control')
    parser.add_argument('--output', type=Path, required=True, help='New owned evidence directory')
    parser.add_argument('--phase', choices=['baseline', 'fixed'], required=True)
    parser.add_argument('--baseline', type=Path, help='Earlier evidence for owned assembly/RNG comparison')
    parser.add_argument('--boots', type=int, choices=[1,2], default=2, help='Boot again in the same disposable save to verify retained identities')
    parser.add_argument('--cases', nargs='+', choices=['foreign', 'owned', 'mixed', 'tall', 'ordinary', 'foreign-type', 'owned-type', 'mixed-type', 'bounds', 'recorded-foreign', 'unmarked-owned', 'identity-mismatch'],
                        default=['foreign', 'owned', 'mixed', 'tall', 'foreign-type', 'owned-type'])
    args = parser.parse_args()
    output, pack, server = args.output.resolve(), args.pack.resolve(), args.server.resolve()
    if output.exists(): parser.error('Evidence directory must be new')
    output.mkdir(parents=True)
    properties = dict(line.split('=', 1) for line in (ROOT / 'gradle.properties').read_text().splitlines() if '=' in line)
    compat = (args.compat or ROOT / f"build/libs/skyweave-compat-{properties['mod_version']}-mc1.21.1-neoforge.jar").resolve(strict=True)
    probe = (ROOT / 'build/probe/skyweave-compat-probe.jar').resolve(strict=True)
    cache = Path.home() / '.gradle/caches/modules-2/files-2.1'
    coordinates = [('maven.modrinth', 'sky-archipelago', 'XPc17YJD'),
                   ('maven.modrinth', 'sky-villages', '3InSHTOp'),
                   ('dev.architectury', 'architectury-neoforge', '13.0.12'),
                   ('maven.modrinth', 'dynamic-waters-realistic-flowing-rivers', 'At4uOo4s'),
                   ('maven.modrinth', 'bluemap', '8iJcPOHJ'),
                   ('design.inherent', 'reform', properties['reform_version'])]
    mods = [compat, probe]
    for group, name, version in coordinates:
        found = [p for p in (cache / group / name / version).glob('*/*.jar') if not p.name.endswith(('-sources.jar', '-javadoc.jar'))]
        if len(found) != 1: parser.error(f'Expected one exact cached artifact: {group}:{name}:{version}')
        mods.append(found[0])
    skyvillages = next(p for p in mods if p.name.startswith('sky-villages-'))
    with zipfile.ZipFile(skyvillages) as archive:
        native_village = archive.read('data/skyvillages/worldgen/structure/skyvillage.json')
    terrain = ROOT / 'src/main/resources/packs/terrain/data/skyweave_compat'
    owned_type = json.loads((terrain / 'dimension_type/islands_v1.json').read_text())
    owned_preset = json.loads((terrain / 'worldgen/world_preset/islands_v1.json').read_text())
    owned_generator = owned_preset['dimensions']['minecraft:overworld']['generator']
    flat = dict(type='minecraft:flat', settings=dict(biome='minecraft:plains', lakes=False, features=False, structure_overrides=[],
                layers=[dict(height=1, block='minecraft:bedrock'), dict(height=2, block='minecraft:dirt'), dict(height=1, block='minecraft:grass_block')]))
    summary = dict(phase=args.phase, productSha256=digest(compat), probeSha256=digest(probe), cases=[], complete=False)
    for case in args.cases:
        with tempfile.TemporaryDirectory(prefix='skyweave-world-scope-') as temp:
            run = Path(temp)
            (run / 'libraries').symlink_to(server / 'libraries', target_is_directory=True)
            for source in mods: copy_owned(source, run / 'mods' / source.name, run)
            copy_owned(server / 'eula.txt', run / 'eula.txt', run)
            copy_owned(pack / 'overrides/common/config/dynamicwaters-common.toml', run / 'config/dynamicwaters-common.toml', run)
            if args.disable_village_placement:
                feature=run/'config/skyweave/skyweave_compat-features.properties';feature.parent.mkdir(parents=True,exist_ok=True)
                feature.write_text('villages.placement=false\n')
            for name, text in {'core.conf': 'accept-download: false\nmetrics: false\n', 'webserver.conf': 'enabled: false\n',
                               'plugin.conf': 'render-thread-count: 1\nscan-for-mod-resources: false\n'}.items():
                p = run / 'config/bluemap' / name
                p.parent.mkdir(parents=True, exist_ok=True)
                p.write_text(text)
            copy_owned(pack / 'overrides/server/config/bluemap/maps/world.conf', run / 'scope-map.conf', run)
            (run / 'scope-native-skyvillage.json').write_bytes(native_village)
            (run / 'scope-owned-generator.json').write_text(json.dumps(owned_generator))
            data = run / 'world/datapacks/scope-data'
            live = run / 'world/datapacks/scope-live'
            for folder in [data, live]:
                folder.mkdir(parents=True)
                (folder / 'pack.mcmeta').write_text(json.dumps(dict(pack=dict(pack_format=48, description='Disposable world ownership fixture'))))
            destination = data / 'data/skyvillages/worldgen/structure/skyvillage.json'
            if args.village_definition:
                copy_owned(args.village_definition,destination,run)
            elif args.native_village_data:
                destination.parent.mkdir(parents=True,exist_ok=True);destination.write_bytes(native_village)
            else:copy_owned(pack / 'resource-patches/data/skyvillages/worldgen/structure/skyvillage.json', destination, run)
            module = 'scope_resources' if args.native_village_data else 'skyweave_resource_patches'
            declaration = '' if args.native_village_data else '"skyweave:owned-village-band"=1\n'
            metadata = ('modLoader="lowcodefml"\nloaderVersion="[1,)"\nlicense="Apache-2.0"\n'
                        f'[[mods]]\nmodId="{module}"\nversion="1.0.0"\n{declaration}'
                        f'[[dependencies.{module}]]\nmodId="skyvillages"\ntype="required"\nversionRange="[1.0.6]"\nordering="AFTER"\nside="BOTH"\n')
            with zipfile.ZipFile(run / 'mods/scope-resource-fixture.jar', 'w') as archive:
                archive.writestr('META-INF/neoforge.mods.toml',metadata)
                archive.writestr('data/skyvillages/worldgen/structure/skyvillage.json',destination.read_bytes())
            dimensions = json.loads(json.dumps(owned_preset['dimensions']))
            dimensions['minecraft:overworld'] = dict(type='minecraft:overworld', generator=flat)
            if case in {'owned', 'owned-type'}:
                dimensions['minecraft:overworld'] = dict(type='skyweave_compat:islands_v1' if case == 'owned' else 'scope_probe:lookalike', generator=owned_generator)
            if case == 'ordinary': dimensions['minecraft:overworld'] = dict(type='minecraft:overworld', generator=dict(owned_generator, settings='minecraft:overworld'))
            if case in {'bounds','unmarked-owned','identity-mismatch'}: dimensions['minecraft:overworld'] = dict(type='skyweave_compat:islands_v1', generator=owned_generator)
            if case == 'mixed-type': dimensions['scope_probe:invalid'] = dict(type='skyweave_compat:islands_v1', generator=flat)
            if case == 'foreign-type': dimensions['minecraft:overworld']['type'] = 'skyweave_compat:islands_v1'
            if case == 'tall': dimensions['minecraft:overworld']['type'] = 'scope_probe:tall'
            if case == 'mixed': dimensions['scope_probe:islands'] = dict(type='skyweave_compat:islands_v1', generator=owned_generator)
            p = data / 'data/scope_probe/worldgen/world_preset/test.json'
            p.parent.mkdir(parents=True)
            p.write_text(json.dumps(dict(dimensions=dimensions)))
            p = data / 'data/scope_probe/dimension_type/lookalike.json'
            p.parent.mkdir(parents=True)
            p.write_text(json.dumps(owned_type))
            if case == 'bounds':
                p=data / 'data/skyweave_compat/dimension_type/islands_v1.json';p.parent.mkdir(parents=True,exist_ok=True)
                p.write_text(json.dumps(dict(owned_type,min_y=-112,height=880,logical_height=880)))
            if case == 'identity-mismatch':
                if args.identity_control is None:parser.error('identity-mismatch requires --identity-control')
                copy_owned(args.identity_control,run/'world/skyweave-generation.json',run)
            if case == 'recorded-foreign': (run/'world/skyweave-generation.json').write_text('recorded-foreign-control')
            if case == 'unmarked-owned':
                p=run/'world/region/r.0.0.mca';p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(b'preserve-unmarked-control')
            tall = dict(owned_type, min_y=-512, height=2048, logical_height=2048, effects='minecraft:overworld')
            (p.parent / 'tall.json').write_text(json.dumps(tall))
            contract = dict(schema=1, roles=dict(fixture=dict(minimum=1, alternatives=[dict(template='minecraft:village/plains/houses/plains_small_house_1',
                           type='minecraft:single_pool_element', processors='minecraft:empty', projection='rigid')])))
            p = live / 'data/skyvillages/village_composition/skyvillage.json'
            p.parent.mkdir(parents=True)
            p.write_text(json.dumps(contract))
            (run / 'server.properties').write_text('server-ip=127.0.0.1\nserver-port=0\nonline-mode=false\nlevel-name=world\n'
                 'level-type=scope_probe:test\nlevel-seed=42\ngenerate-structures=false\nview-distance=2\nsimulation-distance=2\n'
                 'spawn-protection=0\nmax-tick-time=60000\ninitial-enabled-packs=vanilla,file/scope-data,file/scope-live\n')
            receipt = run / 'scope-result.json'
            launch = server / f"libraries/net/neoforged/neoforge/{properties['neo_version']}/unix_args.txt"
            command = [args.java, '-Xmx2G', '-XX:ActiveProcessorCount=4', f'-Dneighbors.probe.worldScope={case}',
                       f'-Dneighbors.probe.output={receipt}', '@' + str(launch), 'nogui']
            started = time.monotonic()
            with (output / f'{case}.log').open('w') as log:
                process = subprocess.Popen(command, cwd=run, stdin=subprocess.PIPE, stdout=log, stderr=subprocess.STDOUT, text=True)
                try: process.communicate(timeout=180)
                except subprocess.TimeoutExpired:
                    process.kill(); process.communicate()
                    raise TimeoutError(f'Owned {case} fixture exceeded 180 seconds; log retained')
            row = dict(case=case, command=command, seconds=round(time.monotonic()-started, 2), exit=process.returncode)
            if receipt.exists(): row['native'] = json.loads(receipt.read_text())
            row['regionFiles'] = len(list((run / 'world').rglob('*.mca')))
            row['identities'] = sorted(str(p.relative_to(run)) for p in (run / 'world').rglob('skyweave-generation.json'))
            for marker in (run/'world').rglob('skyweave-generation.json'):
                retained=output/'identities'/(digest(marker)+'.json');retained.parent.mkdir(exist_ok=True);retained.write_bytes(marker.read_bytes())
            row['identityDigests'] = {str(p.relative_to(run)): digest(p) for p in (run / 'world').rglob('skyweave-generation.json')}
            row['mods'] = {p.name: digest(p) for p in mods}
            text = (output / f'{case}.log').read_text()
            wrong_pair = case in {'foreign-type', 'owned-type', 'mixed-type', 'bounds', 'recorded-foreign', 'unmarked-owned', 'identity-mismatch'}
            if args.phase == 'fixed' and wrong_pair:
                expected = {'bounds':'dimension and noise settings', 'recorded-foreign':'refusing an incompatible replacement',
                            'unmarked-owned':'Existing terrain has no Skyweave generation identity',
                            'identity-mismatch':"Skyweave generation settings differ from this world's recorded identity"}.get(case,'Unsupported generator/dimension type pairing')
                row['passed'] = (not receipt.exists() and expected in text
                    and row['regionFiles'] == int(case=='unmarked-owned')
                    and len(row['identities']) == int(case in {'recorded-foreign','identity-mismatch'}))
                if case=='recorded-foreign': row['passed'] &= (run/'world/skyweave-generation.json').read_text()=='recorded-foreign-control'
                if case=='identity-mismatch':row['passed'] &= (run/'world/skyweave-generation.json').read_bytes()==args.identity_control.read_bytes()
                if case=='unmarked-owned': row['passed'] &= (run/'world/region/r.0.0.mca').read_bytes()==b'preserve-unmarked-control'
            else:
                n = row.get('native', {})
                row['passed'] = n.get('complete', False) and process.returncode == 0
                if args.phase == 'fixed' and row['passed']:
                    row['passed'] = (n['S1']['foreignDifferences'] == 0 and n['W2']['scalarIsolated'] and n['W2']['envelopesIsolated']
                                     and len(n['storagePaths']) == 4 and all(n['storagePaths'].values())
                                     and n['W2']['foreignWindowWrongCells'] == 0 and n['M1']['coversDeepAndTall'] and n['M1']['coversWorld']
                                     and n['M1']['operatorCropPreserved']
                                     and all(v for v in n['W2']['cacheControls'].values())
                                     and n['L1']['changedAccepted'] == (case in {'foreign','tall','ordinary'}) and n['L1']['unchangedAccepted']
                                     and n['L1']['malformedRefused'] and n['L1']['malformedRetainedInputs'] and n['L1']['restoredAccepted'])
                    own = [p for p in n['pairings'] if p['owned']]
                    row['dimensionIdentitiesVerified'] = (len(own) == len(row['identities'])
                        and all(p.get('verifiedIdentity') and p.get('generationIdentity') in row['identityDigests'].values() for p in own))
                    row['passed'] = row['passed'] and row['dimensionIdentitiesVerified']
                    if args.baseline and (args.baseline / f'{case}.json').exists():
                        old = json.loads((args.baseline / f'{case}.json').read_text())
                        before = old['native']['S1']['owned']
                        row['ownedAssemblyPreserved'] = before == n['S1']['owned']
                        row['existingIdentityBytesPreserved'] = all(row['identityDigests'].get(k) == v for k,v in old['identityDigests'].items())
                        row['passed'] = row['passed'] and row['existingIdentityBytesPreserved']
                        row['passed'] = row['passed'] and row['ownedAssemblyPreserved']
            if args.boots == 2 and not wrong_pair and row['passed']:
                receipt.unlink()
                started_reopen = time.monotonic()
                with (output / f'{case}-reopen.log').open('w') as log:
                    second = subprocess.Popen(command, cwd=run, stdin=subprocess.PIPE, stdout=log, stderr=subprocess.STDOUT, text=True)
                    try: second.communicate(timeout=180)
                    except subprocess.TimeoutExpired:
                        second.kill(); second.communicate()
                        raise TimeoutError(f'Owned {case} reopen exceeded 180 seconds')
                observed = json.loads(receipt.read_text()) if receipt.exists() else {}
                row['reopen'] = dict(seconds=round(time.monotonic()-started_reopen,2), exit=second.returncode, native=observed,
                    identityDigests={str(p.relative_to(run)): digest(p) for p in (run / 'world').rglob('skyweave-generation.json')})
                row['reopen']['passed'] = (second.returncode == 0 and observed.get('complete')
                    and row['identityDigests'] == row['reopen']['identityDigests']
                    and observed['S1'] == row['native']['S1'] and observed['L1'] == row['native']['L1']
                    and observed['pairings'] == row['native']['pairings']
                    and all(v for v in observed['W2'].get('cacheControls',{}).values()))
                row['passed'] = row['passed'] and row['reopen']['passed']
            (output / f'{case}.json').write_text(json.dumps(row, indent=2)+'\n')
            summary['cases'].append({k: row[k] for k in ['case', 'seconds', 'passed']})
            (output / 'summary.json').write_text(json.dumps(summary, indent=2)+'\n')
            print(json.dumps(summary['cases'][-1]), flush=True)
            if not row['passed']: raise SystemExit(f'{case} fixture failed; inspect retained native report/log')
    summary['complete'] = True
    (output / 'summary.json').write_text(json.dumps(summary, indent=2)+'\n')


if __name__ == '__main__':
    main()
