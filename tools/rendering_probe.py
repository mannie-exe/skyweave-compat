#!/usr/bin/env python3
"""Run rendering checks with packaged JARs in a disposable client."""
import argparse
import hashlib
import json
import os
import re
from pathlib import Path
import shutil
import subprocess
import zipfile

from client_bootstrap import REPOSITORY, argument_file, connector_bootstrap, mod_ids, packaged_bootstrap


def resource_format_range(path):
    """Read the declared legacy format range for the 1.21.1 fixture."""
    with zipfile.ZipFile(path) as archive:
        pack = json.loads(archive.read('pack.mcmeta'))['pack']
    declared = pack.get('supported_formats', pack['pack_format'])
    if isinstance(declared, dict):
        minimum, maximum = declared['min_inclusive'], declared['max_inclusive']
    elif isinstance(declared, list) and len(declared) == 2:
        minimum, maximum = declared
    else:
        minimum = maximum = declared
    if any(type(value) is not int for value in (minimum, maximum)) or minimum > maximum:
        raise ValueError('Candidate has no understood legacy integer pack range: ' + str(path))
    return minimum, maximum


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--pack', type=Path, required=True, help='Read-only materialized Create: Skyweave checkout')
    parser.add_argument('--root', type=Path, required=True, help='New disposable run directory; must not exist')
    parser.add_argument('--java', type=Path, required=True)
    parser.add_argument('--compat', type=Path, help='Explicit packaged Compat artifact for a matched before/after control')
    parser.add_argument('--source-prism', type=Path, help='Read-only owned stage made from a native Prism export and instance install')
    parser.add_argument('--scene', choices=('startup', 'poses', 'hand-effects', 'visual-options', 'resource-evolution', 'villages', 'foliage'), required=True)
    parser.add_argument('--foliage-control', action='store_true', help='Remove only the foliage shader repair from the owned Visual Patches copy')
    parser.add_argument('--village-data', type=Path, help='Generated, provenance-bound village fixture for the villages scene')
    parser.add_argument('--reform', type=Path, help='Explicit required re:form candidate when absent from the owned pack stage')
    parser.add_argument('--resource-control', action='store_true', help='Resource scene without the selected evolution packs or maintained Visual Patches')
    parser.add_argument('--resource-focus', choices=('all', 'rotations', 'music', 'equipment', 'formats', 'interactive', 'ores', 'mobs'), default='all', help='Held gallery, rotations, discs, equipped Artifacts, formats, scripted placement, ore/ingots, or native mob pose controls')
    parser.add_argument('--resource-pack', type=Path, action='append', default=[], help='Read-only candidate ZIP copied into this resource scene; at most four')
    parser.add_argument('--resource-allow-incompatible', action='store_true', help='Fixture-only native acknowledgment for explicit candidate packs; preserve metadata and report compatibility')
    parser.add_argument('--visual-menu-only', action='store_true', help='Inspect native pack option controls without opening a world')
    parser.add_argument('--visual-torch-ui', action='store_true', help='Change both torch choices through native UI and verify save/reload/restore')
    parser.add_argument('--visual-accessories', action='store_true', help='Qualify independent workstation accessories, native save/reload and coordinate seeds')
    parser.add_argument('--heap', choices=('4G', '6G', '8G'), default='4G')
    parser.add_argument('--shader', choices=('off', 'complementary', 'bsl'), default='off', help='Hand-effects fixture shader')
    parser.add_argument('--connector-clean', type=Path, help='Clean NeoForm 1.21.1 rename output for Connector development bootstrap')
    parser.add_argument('--timeout', type=int, default=600, choices=range(60, 901), metavar='60..900')
    args = parser.parse_args()
    if args.foliage_control and args.scene != 'foliage':
        parser.error('--foliage-control requires the foliage scene')
    if bool(args.village_data) != (args.scene == 'villages'):
        parser.error('--village-data is required only for the villages scene')
    if args.resource_control and args.scene != 'resource-evolution':
        parser.error('--resource-control requires the resource-evolution scene')
    if args.resource_focus != 'all' and args.scene != 'resource-evolution':
        parser.error('--resource-focus requires the resource-evolution scene')
    if args.resource_pack and (args.scene != 'resource-evolution' or len(args.resource_pack) > 4):
        parser.error('--resource-pack requires the resource-evolution scene and at most four archives')
    if args.resource_allow_incompatible and not args.resource_pack:
        parser.error('--resource-allow-incompatible requires explicit resource candidates')
    for path in args.resource_pack:
        if not path.is_file() or path.stat().st_size > 64 * 1024 * 1024 or not zipfile.is_zipfile(path):
            parser.error('Resource candidate must be a ZIP file no larger than 64 MiB')
    if args.visual_menu_only and args.scene != 'visual-options':
        parser.error('--visual-menu-only requires the visual-options scene')
    if args.visual_torch_ui and (args.scene != 'visual-options' or args.visual_menu_only):
        parser.error('--visual-torch-ui requires its own visual-options world scene')
    if args.visual_accessories and (args.scene != 'visual-options' or args.visual_menu_only or args.visual_torch_ui):
        parser.error('--visual-accessories requires its own visual-options world scene')
    source = args.source_prism.resolve() if args.source_prism else None
    stage_marker = source / 'owned-rendering-stage.json' if source else None
    if args.scene == 'startup':
        if source is not None:
            parser.error('The startup scene uses the minimum install without --source-prism')
    elif stage_marker is None or not stage_marker.is_file():
        parser.error('Rendering scenes require --source-prism with its owned-rendering-stage.json marker')
    pack, root = args.pack.resolve(), args.root.resolve()
    if root.exists():
        parser.error('Run directory already exists; preserve evidence and select a new path')
    root.mkdir(parents=True)
    (root / 'rendering-review-fixture.json').write_text(json.dumps({'owner': 'rendering_probe.py', 'schema': 1}) + '\n')
    (root / 'mods').mkdir()
    (root / 'resourcepacks').mkdir()
    if source is not None:
        for folder in ('mods', 'config', 'resourcepacks', 'shader-inputs', 'shaderpacks'):
            path = source / '.minecraft' / folder
            if path.is_dir():
                shutil.copytree(path, root / folder, dirs_exist_ok=True)
        shutil.copy2(source / '.minecraft/options.txt', root / 'options.txt')
        for path in (root / 'mods').glob('*.jar'):
            if 'skyweave_compat' in mod_ids(path): path.unlink()
        subtle = root / 'config/subtle_effects/general.toml'
        if subtle.is_file():
            text, count = re.subn(r'(?m)^(\s*enableEasterEggs\s*=\s*)(?:true|false)\s*$', r'\g<1>false', subtle.read_text())
            if count != 1: raise RuntimeError('Expected one native development easter-egg fixture preference')
            subtle.write_text(text)
    foliage_input = None
    if args.visual_accessories:
        visual = pack / 'overrides/client/resourcepacks/skyweave-visual-patches-0.1.0.zip'
        shutil.copy2(visual, root / 'resourcepacks' / visual.name)
    if args.scene == 'foliage':
        visual = pack / 'overrides/client/resourcepacks/skyweave-visual-patches-0.1.0.zip'
        destination = root / 'resourcepacks' / visual.name
        shader_path = 'assets/mc2_interactivefoliage/shaders/core/foliage_legacy.vsh'
        with zipfile.ZipFile(visual) as archive:
            if shader_path not in archive.namelist():
                parser.error('Prepare the foliage repair through the owning Visual Patches builder first')
            foliage_input = {'archiveSha256': hashlib.sha256(visual.read_bytes()).hexdigest(),
                             'shaderSha256': hashlib.sha256(archive.read(shader_path)).hexdigest(),
                             'control': args.foliage_control}
            if args.foliage_control:
                with zipfile.ZipFile(destination, 'w') as control:
                    for entry in archive.infolist():
                        if entry.filename != shader_path: control.writestr(entry, archive.read(entry.filename))
            else:
                shutil.copy2(visual, destination)
        foliage_input['installedArchiveSha256'] = hashlib.sha256(destination.read_bytes()).hexdigest()
        selected_packs = next((json.loads(line.split(':', 1)[1]) for line in
                               (root / 'options.txt').read_text().splitlines() if line.startswith('resourcePacks:')), [])
        if 'file/' + visual.name not in selected_packs:
            parser.error('Foliage scene requires the selected Visual Patches pack')
    version = next(line.split('=', 1)[1].strip() for line in (REPOSITORY / 'gradle.properties').read_text().splitlines() if line.startswith('mod_version='))
    product = args.compat or REPOSITORY / f'build/libs/skyweave-compat-{version}-mc1.21.1-neoforge.jar'
    for path in [product, REPOSITORY / 'build/probe/skyweave-client-probe.jar']:
        shutil.copy2(path, root / 'mods' / path.name)
    installed_libraries = [path for path in (root / 'mods').glob('*.jar') if 'reform' in mod_ids(path)]
    library = args.reform
    if library is None and not installed_libraries:
        choices = [path for path in (pack / 'pack/mods').glob('*.jar') if mod_ids(path) == {'reform'}]
        if len(choices) != 1:
            parser.error('Supply --reform or an owned pack stage with exactly one re:form artifact')
        library = choices[0]
    if library is not None:
        if mod_ids(library) != {'reform'}:
            parser.error('--reform must identify only re:form')
        for path in installed_libraries:
            path.unlink()
        shutil.copy2(library, root / 'mods' / library.name)
    elif len(installed_libraries) != 1:
        parser.error('Owned pack stage has multiple re:form artifacts')
    village_input = None
    if args.village_data:
        with zipfile.ZipFile(args.village_data) as archive:
            provenance = json.loads(archive.read('pack.mcmeta')).get('skyweave_village_fixture', {})
            resources = provenance.get('resources', {})
            if (provenance.get('schema') != 1 or not resources or len(resources) > 1024
                    or set(archive.namelist()) != set(resources) | {'pack.mcmeta'}):
                parser.error('--village-data is not a complete generated village fixture')
            providers = {'ctov': 'ct-overhaul-village', 'bettervillage': 'better-village', 't_and_t': 'towns-and-towers'}
            found_providers = set()
            for path in (root / 'mods').glob('*.jar'):
                for identity in mod_ids(path) & providers.keys():
                    dependency = providers[identity]
                    if hashlib.sha512(path.read_bytes()).hexdigest() != provenance.get('sources', {}).get(dependency):
                        parser.error('--village-data provider pins differ from the owned stage')
                    found_providers.add(identity)
            if found_providers != providers.keys():
                parser.error('Village scene is missing its exact provider stack')
            metadata = ('modLoader="lowcodefml"\nloaderVersion="[1,)"\nlicense="Apache-2.0"\n'
                        '[[mods]]\nmodId="skyweave_village_preview"\nversion="1.0.0"\n'
                        'displayName="Owned village preview data"\n'
                        '[[dependencies.skyweave_village_preview]]\nmodId="skyweave_resource_patches"\n'
                        'type="required"\nversionRange="[0,)"\nordering="AFTER"\nside="BOTH"\n')
            with zipfile.ZipFile(root / 'mods/owned-village-data.jar', 'w') as fixture:
                fixture.writestr('META-INF/neoforge.mods.toml', metadata)
                fixture.writestr('pack.mcmeta', archive.read('pack.mcmeta'))
                for name, digest in resources.items():
                    original = name.startswith('data/skyweave_compat/structure/villages/') and name.endswith('.nbt')
                    if (not name.startswith('data/') or '..' in Path(name).parts or not (name.endswith('.json') or original)
                            or hashlib.sha256(archive.read(name)).hexdigest() != digest):
                        parser.error('Invalid or changed village resource: ' + name)
                    fixture.writestr(name, archive.read(name))
            village_input = hashlib.sha256(args.village_data.read_bytes()).hexdigest()
    if args.resource_control:
        removed = {'file/' + name for name in ('Artifacts 3D.zip', 'FA+Quivers-v2.3.zip', 'FreshVines_v1.1.zip',
                   'Just 3D Potions 1.21 Backport.zip', '§9Bray\'s Zombie Overhaul FA v1.4.zip', 'skyweave-visual-patches-0.1.0.zip')}
        options = root / 'options.txt'
        lines = options.read_text().splitlines()
        for i, line in enumerate(lines):
            if line.startswith('resourcePacks:'):
                lines[i] = 'resourcePacks:' + json.dumps([key for key in json.loads(line.split(':', 1)[1]) if key not in removed])
        options.write_text('\n'.join(lines) + '\n')
    if args.scene == 'resource-evolution':
        expected_mods = json.loads(stage_marker.read_text()).get('expectedClientMods', [])
        if (not isinstance(expected_mods, list) or len(expected_mods) > 4
                or any(not isinstance(value, str) or not re.fullmatch(r'[a-z][a-z0-9_]{1,63}', value) for value in expected_mods)):
            parser.error('Owned stage has malformed expected client mod IDs')
        (root / 'resource-mod-candidates.json').write_text(json.dumps(expected_mods) + '\n')
        candidates = []
        for index, path in enumerate(args.resource_pack):
            name = f'resource-candidate-{index}-{path.name}'
            destination = root / 'resourcepacks' / name
            shutil.copy2(path, destination)
            minimum, maximum = resource_format_range(destination)
            candidates.append({'file': name, 'sha256': hashlib.sha256(destination.read_bytes()).hexdigest(),
                               'declaredFormats': [minimum, maximum],
                               'acknowledgedIncompatible': args.resource_allow_incompatible and not minimum <= 34 <= maximum})
        (root / 'resource-candidates.json').write_text(json.dumps(candidates, indent=2) + '\n')
        # Authored geometry controls, never foreign assets or a published pack.
        body = {'textures': {'all': 'minecraft:block/stone', 'particle': 'minecraft:block/stone'},
                'elements': [{'from': [3, 4, 5], 'to': [11, 12, 13], 'rotation': {'origin': [4, 8, 12], 'axis': 'x', 'angle': 22.5},
                              'faces': {face: {'texture': '#all', 'uv': [1, 2, 3, 4]} for face in ('north', 'south', 'east', 'west', 'up', 'down')}}]}
        modern = json.loads(json.dumps(body))
        modern['elements'][0]['rotation'] = {'origin': [4, 8, 12], 'x': 22.5, 'y': 0, 'z': 0}
        fixture = root / 'resourcepacks/resource-geometry-fixture.zip'
        with zipfile.ZipFile(fixture, 'w') as archive:
            archive.writestr('pack.mcmeta', json.dumps({'pack': {'pack_format': 34, 'description': 'Owned resource geometry controls'}}))
            archive.writestr('assets/minecraft/models/item/clay_ball.json', json.dumps(body))
            archive.writestr('assets/minecraft/models/item/slime_ball.json', json.dumps(modern))
        options = root / 'options.txt'; lines = options.read_text().splitlines()
        for i, line in enumerate(lines):
            if line.startswith('resourcePacks:'):
                lines[i] = 'resourcePacks:' + json.dumps(json.loads(line.split(':', 1)[1]) +
                    ['file/' + candidate['file'] for candidate in candidates] + ['file/' + fixture.name])
        if args.resource_allow_incompatible:
            prefix = 'incompatibleResourcePacks:'
            # Native Options also drops a compatible pack on the boot that clears a stale acknowledgment.
            acknowledgments = ['file/' + candidate['file'] for candidate in candidates if candidate['acknowledgedIncompatible']]
            for i, line in enumerate(lines):
                if line.startswith(prefix):
                    lines[i] = prefix + json.dumps(list(dict.fromkeys(json.loads(line[len(prefix):]) + acknowledgments)))
                    break
            else:
                lines.append(prefix + json.dumps(acknowledgments))
        options.write_text('\n'.join(lines) + '\n')
    if args.scene == 'startup':
        (root / 'options.txt').write_text(
            'resourcePacks:["vanilla","mod_resources"]\npauseOnLostFocus:false\nfullscreen:false\nmaxFps:60\n')
    elif args.visual_accessories:
        options = root / 'options.txt'
        text = options.read_text()
        text, count = re.subn(r'(?m)^pauseOnLostFocus:(?:true|false)$', 'pauseOnLostFocus:false', text)
        if count != 1: raise RuntimeError('Expected one disposable-client focus preference')
        options.write_text(text)

    # Keep the pinned ModDev loader/bootstrap, but remove content mods and project source roots.
    # The only gameplay/probe artifacts must be the packaged JARs above.
    vm, program = packaged_bootstrap(root)
    vm += ['-Xms512M', '-Xmx' + args.heap,
           '-Dmixin.debug.export=true', '-Dmixin.debug.export.filter=net.minecraft.client.Minecraft']
    try:
        connector_input = connector_bootstrap(vm, args.connector_clean)
    except (ValueError, OSError, KeyError) as failure:
        parser.error(str(failure))
    shader = 'off'
    if args.scene in {'resource-evolution', 'visual-options', 'hand-effects'} and args.shader != 'off':
        recipe_name = {'complementary': 'complementary-euphoria-skyweave', 'bsl': 'bsl-colorwheel'}[args.shader]
        recipe = root / 'config/skyweave/shaders/recipes' / (recipe_name + '.json')
        shader = json.loads(recipe.read_text())['output']
    if args.scene == 'startup':
        vm.append('-Dskyweave_compat.probe.renderStartup=true')
    elif args.scene == 'poses':
        vm.append('-Dskyweave_compat.probe.playerPoses=true')
    elif args.scene == 'foliage':
        vm += ['-Dskyweave_compat.probe.foliage=true',
               '-Dskyweave_compat.probe.foliage.repaired=' + str(not args.foliage_control).lower()]
    elif args.scene == 'resource-evolution':
        vm += ['-Dskyweave_compat.probe.resourceEvolution=true',
               '-Dskyweave_compat.probe.resourceEvolution.shader=' + shader,
               '-Dskyweave_compat.probe.resourceEvolution.focus=' + args.resource_focus,
               '-Dskyweave_compat.probe.resourceEvolution.control=' + str(args.resource_control).lower()]
    elif args.scene == 'villages':
        vm.append('-Dskyweave_compat.probe.villages=true')
    elif args.scene == 'visual-options':
        vm.append('-Dskyweave_compat.probe.visualOptions=true')
        vm.append('-Dskyweave_compat.probe.visualOptions.shader=' + shader)
        if args.visual_menu_only:
            vm.append('-Dskyweave_compat.probe.visualOptions.menuOnly=true')
        if args.visual_torch_ui:
            vm.append('-Dskyweave_compat.probe.visualOptions.torchUi=true')
        if args.visual_accessories:
            vm.append('-Dskyweave_compat.probe.visualOptions.accessories=true')
    else:
        vm += ['-Dskyweave_compat.probe.handEffects=true', '-Dskyweave_compat.probe.handEffects.shader=' + shader,
               '-Dskyweave_compat.probe.handEffects.trace=true']
    argument_file(root / 'vm.args', vm)
    argument_file(root / 'program.args', program)
    command = [str(args.java), '@' + str(root / 'classpath.args'), '@' + str(root / 'vm.args'), 'net.neoforged.devlaunch.Main', '@' + str(root / 'program.args')]
    artifacts = {path.name: hashlib.sha256(path.read_bytes()).hexdigest() for path in (root / 'mods').glob('*.jar')}
    configurations = {str(path.relative_to(root)): hashlib.sha256(path.read_bytes()).hexdigest() for path in (root / 'config').rglob('*') if path.is_file()}
    (root / 'launch.json').write_text(json.dumps({'command': command, 'artifacts': artifacts, 'connectorCleanArtifact': connector_input,
        'configurationsBeforeBoot': configurations,
        'villageDataSha256': village_input, 'foliageInput': foliage_input,
        'bootstrap': 'Pinned ModDev forgeclientdev; packaged gameplay/probe JARs; no project source roots'}, indent=2) + '\n')
    print(f'Launching owned {args.scene} client: {root}', flush=True)
    environment = {key: value for key, value in os.environ.items() if key not in ('MOD_CLASSES', 'FML_MOD_FOLDERS')}
    with (root / 'client.log').open('w') as log:
        with subprocess.Popen(command, cwd=root, env=environment, stdout=log, stderr=subprocess.STDOUT) as process:
            (root / 'client.pid').write_text(str(process.pid))
            try:
                result = process.wait(timeout=args.timeout)
            except subprocess.TimeoutExpired:
                process.terminate()
                try:
                    process.wait(timeout=20)
                except subprocess.TimeoutExpired:
                    process.kill()
                    process.wait()
                raise RuntimeError(f'Owned client timed out; inspect {root / "client.log"}')
    report = root / {
        'startup': 'rendering-startup-results.json',
        'foliage': 'foliage-veil-results.json',
        'villages': 'village-preview-results.json',
        'resource-evolution': 'resource-evolution-results.json',
        'poses': 'pose-results.json',
        'visual-options': 'visual-options-results.json',
        'hand-effects': 'hand-effects-results.json',
    }[args.scene]
    if result or not report.exists():
        raise RuntimeError(f'Client exited {result}; inspect {root / "client.log"}')
    data = json.loads(report.read_text())
    if data.get('failure'):
        raise RuntimeError(data['failure'])
    if data.get('complete') is not True:
        raise RuntimeError('Rendering fixture did not complete')
    if args.scene == 'startup':
        print(json.dumps({'root': str(root), 'startup': data}, indent=2))
        return
    if args.scene == 'villages':
        count = data.get('profileCount')
        if type(count) is not int or not 1 <= count <= 32:
            raise RuntimeError('Village preview has no bounded profile count')
        for profile in range(count):
            for view in range(3):
                capture = root / f'screenshots/village-{profile}-{view}.png'
                if not capture.is_file():
                    raise RuntimeError('Village capture was not saved: ' + str(capture))
                with capture.open('rb') as image:
                    if image.read(8) != b'\x89PNG\r\n\x1a\n':
                        raise RuntimeError('Invalid village capture: ' + str(capture))
    if args.resource_pack:
        requested = json.loads((root / 'resource-candidates.json').read_text())
        missing = ['file/' + entry['file'] for entry in requested if 'file/' + entry['file'] not in data.get('selectedPacks', [])]
        if missing:
            raise RuntimeError(f'Requested resource candidates were not selected: {missing}')
    count_key = 'passes' if args.scene == 'foliage' else 'samples'
    rows = data.get('profiles' if args.scene == 'visual-options' else count_key, [])
    print(json.dumps({'root': str(root), 'scene': args.scene, count_key: len(rows), 'complete': True}, indent=2))


if __name__ == '__main__':
    main()
