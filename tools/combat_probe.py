#!/usr/bin/env python3
"""Run opt-in combat and rendering witnesses with packaged JARs in a disposable client."""
import argparse
import hashlib
import json
import os
import re
from pathlib import Path
import shlex
import shutil
import subprocess
import tomllib
import zipfile

REPOSITORY = Path(__file__).resolve().parents[1]


def resource_format_range(path):
    """Read legacy pack ranges for this pinned1.21.1 fixture; native admission remains authoritative."""
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


def mod_ids(path):
    if not path.is_file() or path.suffix != '.jar':
        return set()
    with zipfile.ZipFile(path) as archive:
        for name in ('META-INF/neoforge.mods.toml', 'META-INF/mods.toml'):
            if name in archive.namelist():
                return {mod['modId'] for mod in tomllib.loads(archive.read(name).decode()).get('mods', [])}
    return set()


def argument_file(path, arguments):
    path.write_text('\n'.join('"' + value.replace('\\', '\\\\').replace('"', '\\"') + '"' for value in arguments) + '\n')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--pack', type=Path, required=True, help='Read-only materialized Create: Skyweave checkout')
    parser.add_argument('--root', type=Path, required=True, help='New disposable run directory; must not exist')
    parser.add_argument('--java', type=Path, required=True)
    parser.add_argument('--compat', type=Path, help='Explicit packaged Compat artifact for a matched before/after control')
    parser.add_argument('--bridges', choices=('all', 'off', 'punchy', 'moves'), default='all')
    parser.add_argument('--view', choices=('first', 'third'), default='first')
    parser.add_argument('--pacing', choices=('on', 'off'), default='off')
    parser.add_argument('--capture-frames', action='store_true')
    parser.add_argument('--extended', action='store_true', help='Run native attribute, coalescing and lifecycle controls after the basic scene')
    parser.add_argument('--settings-only', action='store_true', help='Inspect the native pacing controls from the title screen without a world')
    parser.add_argument('--startup-targets', choices=('none', 'punchy', 'moves', 'future'), help='Title-only optional dependency boundary control')
    parser.add_argument('--network-role', choices=('actor', 'observer'), help='Real loopback client role instead of the integrated scene')
    parser.add_argument('--address', help='Explicit 127.0.0.1:port for the network scene')
    parser.add_argument('--source-prism', type=Path, help='Read-only owned stage made from a native Prism export and instance install')
    parser.add_argument('--scene', choices=('combat', 'poses', 'hand-effects', 'visual-options', 'resource-evolution', 'foliage'), default='combat')
    parser.add_argument('--foliage-control', action='store_true', help='Remove only the foliage shader repair from the owned Visual Patches copy')
    parser.add_argument('--resource-control', action='store_true', help='Resource scene without the selected evolution packs or maintained Visual Patches')
    parser.add_argument('--resource-focus', choices=('all', 'rotations', 'music', 'equipment', 'formats', 'interactive', 'ores', 'mobs'), default='all', help='Held gallery, rotations, discs, equipped Artifacts, formats, scripted placement, ore/ingots, or native mob pose controls')
    parser.add_argument('--resource-pack', type=Path, action='append', default=[], help='Read-only candidate ZIP copied into this resource scene; at most four')
    parser.add_argument('--resource-allow-incompatible', action='store_true', help='Fixture-only native acknowledgment for explicit candidate packs; preserve metadata and report compatibility')
    parser.add_argument('--visual-menu-only', action='store_true', help='Inspect native pack option controls without opening a world')
    parser.add_argument('--visual-torch-ui', action='store_true', help='Change both torch choices through native UI and verify save/reload/restore')
    parser.add_argument('--visual-accessories', action='store_true', help='Qualify independent workstation accessories, native save/reload and coordinate seeds')
    parser.add_argument('--heap', choices=('4G', '6G', '8G'), default='4G')
    parser.add_argument('--shader', choices=('off', 'complementary', 'bsl'), default='off', help='Hand-effects fixture shader')
    parser.add_argument('--connector-clean', type=Path, help='NeoForm clean renamed1.21.1 JAR for Connector development bootstrap')
    parser.add_argument('--admission', type=float, choices=(0, .5, 1), default=1)
    parser.add_argument('--sprint-crits', choices=('on', 'off'), default='on')
    parser.add_argument('--timeout', type=int, default=600, choices=range(60, 901), metavar='60..900')
    args = parser.parse_args()
    if args.foliage_control and args.scene != 'foliage':
        parser.error('--foliage-control requires the foliage scene')
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
    if args.settings_only and (args.extended or args.startup_targets or args.network_role or args.scene != 'combat'):
        parser.error('Settings-only is a separate title-screen scene')
    if args.network_role and (args.extended or args.startup_targets or not args.address or not re.fullmatch(r'127\.0\.0\.1:[0-9]{1,5}', args.address)):
        parser.error('Network scene requires a loopback address and the complete client selection')
    pack, root = args.pack.resolve(), args.root.resolve()
    if root.exists():
        parser.error('Run directory already exists; preserve evidence and select a new path')
    root.mkdir(parents=True)
    (root / 'owned-cn-fixture.json').write_text(json.dumps({'owner': 'combat_probe.py', 'schema': 1}))
    (root / 'mods').mkdir()
    (root / 'resourcepacks').mkdir()
    if args.source_prism:
        source = args.source_prism.resolve()
        if not (source / 'owned-combat-stage.json').is_file():
            parser.error('Full-pack input needs its owned stage marker; personal instances are not fixture sources')
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
    if args.scene != 'combat':
        if not args.source_prism or args.network_role or args.startup_targets or args.extended:
            parser.error('Rendering scenes require their owned full-pack source without another scene selector')
        (root / 'rendering-review-fixture.json').write_text('{"owner":"combat_probe.py"}\n')
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
    # The pinned Moves JAR supplies punchy_expressions through its declared Jar-in-Jar metadata.
    selected = {'combatnouveau', 'puzzleslib', 'punchy', 'punchy_moves'}
    if args.startup_targets:
        if args.extended:
            parser.error('--extended requires the complete native combat scene')
        selected = ({'combatnouveau', 'puzzleslib', args.startup_targets if args.startup_targets == 'punchy' else 'punchy_moves'}
                    if args.startup_targets in ('punchy', 'moves') else set())
    found = set()
    for path in (pack / 'pack/mods').glob('*.jar'):
        identities = mod_ids(path)
        if identities & selected:
            shutil.copy2(path, root / 'mods' / path.name)
            found |= identities
    if selected - found:
        raise RuntimeError(f'Missing exact selected dependencies: {sorted(selected - found)}')
    if args.startup_targets == 'future':
        metadata = 'modLoader="lowcodefml"\nloaderVersion="[1,)"\nlicense="MIT"\n'
        for target in ('combatnouveau', 'puzzleslib', 'punchy', 'punchy_moves'):
            metadata += f'[[mods]]\nmodId="{target}"\nversion="999.0.0"\ndisplayName="Fixture {target}"\n'
        with zipfile.ZipFile(root / 'mods/future-combat-targets.jar', 'w') as fixture:
            fixture.writestr('META-INF/neoforge.mods.toml', metadata)
    version = next(line.split('=', 1)[1].strip() for line in (REPOSITORY / 'gradle.properties').read_text().splitlines() if line.startswith('mod_version='))
    product = args.compat or REPOSITORY / f'build/libs/skyweave-compat-{version}-mc1.21.1-neoforge.jar'
    for path in [product, REPOSITORY / 'build/probe/skyweave-client-probe.jar']:
        shutil.copy2(path, root / 'mods' / path.name)
    for side in ('common', 'client'):
        for path in (pack / f'overrides/{side}/config').glob('combatnouveau-*.toml'):
            destination = root / 'config' / path.name
            destination.parent.mkdir(exist_ok=True)
            shutil.copy2(path, destination)
    server_config = root / 'config/combatnouveau-server.toml'
    text = server_config.read_text()
    for key, value in [('min_attack_strength', str(args.admission)), ('critical_hits_while_sprinting', str(args.sprint_crits == 'on').lower())]:
        text, count = re.subn(r'(?m)^(\s*' + key + r'\s*=\s*)[^\n]+', lambda match: match[1] + value, text)
        if count != 1:
            raise RuntimeError(f'Expected one authored native config field: {key}')
    server_config.write_text(text)
    owned = root / 'config/skyweave'; owned.mkdir(exist_ok=True)
    feature_file = owned / 'skyweave_compat-features.properties'
    previous_features = feature_file.read_text().splitlines() if feature_file.exists() else []
    previous_features = [line for line in previous_features if not line.startswith(('combat_nouveau.punchy_attacks=', 'combat_nouveau.moves_attacks='))]
    feature_file.write_text('\n'.join(previous_features) + '\n' +
        f'combat_nouveau.punchy_attacks={str(args.bridges in ("all", "punchy")).lower()}\n'
        f'combat_nouveau.moves_attacks={str(args.bridges in ("all", "moves")).lower()}\n')
    (owned / 'skyweave_compat-combat-client.toml').write_text(
        f'punchyPacing={str(args.pacing == "on").lower()}\nmovesPacing={str(args.pacing == "on").lower()}\n')
    resource = pack / 'pack/resourcepacks/Fluid & Foolish 1.0.1.zip'
    shutil.copy2(resource, root / 'resourcepacks' / resource.name)
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
        expected_mods = json.loads((args.source_prism / 'owned-combat-stage.json').read_text()).get('expectedClientMods', []) if args.source_prism else []
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
    if not args.source_prism:
        (root / 'options.txt').write_text('resourcePacks:' + json.dumps(['vanilla', 'mod_resources', 'file/' + resource.name]) + '\npauseOnLostFocus:false\nfullscreen:false\nrenderDistance:3\nsimulationDistance:3\nmaxFps:60\n')
    elif args.visual_accessories:
        options = root / 'options.txt'
        text = options.read_text()
        text, count = re.subn(r'(?m)^pauseOnLostFocus:(?:true|false)$', 'pauseOnLostFocus:false', text)
        if count != 1: raise RuntimeError('Expected one disposable-client focus preference')
        options.write_text(text)

    # Keep the pinned ModDev loader/bootstrap, but remove content mods and project source roots.
    # The only gameplay/probe artifacts must be the packaged JARs above.
    moddev = REPOSITORY / 'build/moddev'
    def library(path):
        candidate = Path(path)
        if str(REPOSITORY / 'build/classes') in path or str(REPOSITORY / 'build/resources') in path:
            return False
        return not (mod_ids(candidate) - {'minecraft', 'neoforge'})
    classpath = shlex.split((moddev / 'clientRunClasspath.txt').read_text())
    for i, value in enumerate(classpath):
        if i and classpath[i - 1] in ('-cp', '-classpath', '--class-path'):
            classpath[i] = os.pathsep.join(path for path in value.split(os.pathsep) if library(path))
    argument_file(root / 'classpath.args', classpath)
    legacy_paths = (moddev / 'clientLegacyClasspath.txt').read_text().splitlines()
    (root / 'legacy-classpath.txt').write_text('\n'.join(path for path in legacy_paths if library(path)) + '\n')
    vm = shlex.split((moddev / 'clientRunVmArgs.txt').read_text())
    vm = [value for value in vm if not value.startswith('-DlegacyClassPath.file=')]
    vm += ['-DlegacyClassPath.file=' + str(root / 'legacy-classpath.txt'), '-Xms512M', '-Xmx' + args.heap,
           '-Dmixin.debug.export=true', '-Dmixin.debug.export.filter=net.minecraft.client.Minecraft']
    connector_input = None
    if args.connector_clean:
        clean = args.connector_clean.resolve()
        metadata = clean.with_name(clean.name.removesuffix('_output.jar') + '.txt')
        description = json.loads(metadata.read_text())
        if description.get('type') != 'rename' or not description.get('components', {}).get('versionManifest', {}).get('annotation', '').endswith('minecraft_1.21.1_version_manifest.json'):
            parser.error('Connector needs the exact clean1.21.1 NeoForm rename output and its provenance')
        with zipfile.ZipFile(clean) as archive:
            if b'1.21.1' not in archive.read('net/minecraft/SharedConstants.class'):
                parser.error('Connector clean artifact has an unexpected Minecraft identity')
        vm.append('-Dconnector.clean.path=' + str(clean))
        connector_input = {'sha256': hashlib.sha256(clean.read_bytes()).hexdigest(), 'neoformMetadataSha256': hashlib.sha256(metadata.read_bytes()).hexdigest(), 'mc':'1.21.1'}
    shader = 'off'
    if args.scene in {'resource-evolution', 'visual-options', 'hand-effects'} and args.shader != 'off':
        recipe_name = {'complementary': 'complementary-euphoria-skyweave', 'bsl': 'bsl-colorwheel'}[args.shader]
        recipe = root / 'config/skyweave/shaders/recipes' / (recipe_name + '.json')
        shader = json.loads(recipe.read_text())['output']
    if args.startup_targets:
        (root / 'rendering-review-fixture.json').write_text('{"owner":"combat_probe.py"}\n')
        vm += ['-Dskyweave_compat.probe.renderStartup=true', '-Dskyweave_compat.probe.combatStartup=true']
        for key, active in [('CN_PUNCHY', args.startup_targets == 'punchy' and args.bridges in ('all', 'punchy')),
                            ('CN_MOVES', args.startup_targets == 'moves' and args.bridges in ('all', 'moves'))]:
            vm.append('-Dskyweave_compat.probe.expected.' + key + '=' + str(active).lower())
    elif args.scene == 'combat':
        vm.append('-Dskyweave_compat.probe.combat=true')
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
    if args.network_role:
        vm += ['-Dskyweave_compat.probe.combat.role=' + args.network_role,
               '-Dskyweave_compat.probe.combat.server=' + args.address]
    vm += ['-Dskyweave_compat.probe.combat.view=' + args.view]
    if args.capture_frames:
        vm.append('-Dskyweave_compat.probe.combat.capture=true')
    if args.extended:
        vm.append('-Dskyweave_compat.probe.combat.extended=true')
    if args.settings_only:
        vm.append('-Dskyweave_compat.probe.combat.settingsOnly=true')
    argument_file(root / 'vm.args', vm)
    program = shlex.split((moddev / 'clientRunProgramArgs.txt').read_text(), comments=True)
    if args.network_role:
        username = 'CombatActor' if args.network_role == 'actor' else 'CombatObserver'
        if '--username' in program:
            program[program.index('--username') + 1] = username
        else:
            program += ['--username', username]
    argument_file(root / 'program.args', program)
    command = [str(args.java), '@' + str(root / 'classpath.args'), '@' + str(root / 'vm.args'), 'net.neoforged.devlaunch.Main', '@' + str(root / 'program.args')]
    artifacts = {path.name: hashlib.sha256(path.read_bytes()).hexdigest() for path in (root / 'mods').glob('*.jar')}
    configurations = {str(path.relative_to(root)): hashlib.sha256(path.read_bytes()).hexdigest() for path in (root / 'config').rglob('*') if path.is_file()}
    (root / 'launch.json').write_text(json.dumps({'command': command, 'artifacts': artifacts, 'connectorCleanArtifact': connector_input,
        'configurationsBeforeBoot': configurations, 'selectedResourceSha256': hashlib.sha256(resource.read_bytes()).hexdigest(),
        'foliageInput': foliage_input,
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
    report = root / ('rendering-startup-results.json' if args.startup_targets else 'combat-network-client.json' if args.network_role
                     else 'foliage-veil-results.json' if args.scene == 'foliage'
                     else 'resource-evolution-results.json' if args.scene == 'resource-evolution' else 'pose-results.json' if args.scene == 'poses' else 'visual-options-results.json' if args.scene == 'visual-options' else 'hand-effects-results.json' if args.scene == 'hand-effects' else 'combat-report.json')
    if result or not report.exists():
        raise RuntimeError(f'Client exited {result}; inspect {root / "client.log"}')
    data = json.loads(report.read_text())
    if data.get('failure'):
        raise RuntimeError(data['failure'])
    if args.scene != 'combat':
        if data.get('complete') is not True: raise RuntimeError('Rendering fixture did not complete')
        if args.resource_pack:
            requested = json.loads((root / 'resource-candidates.json').read_text())
            missing = ['file/' + entry['file'] for entry in requested if 'file/' + entry['file'] not in data.get('selectedPacks', [])]
            if missing: raise RuntimeError(f'Requested resource candidates were not selected: {missing}')
        count_key = 'passes' if args.scene == 'foliage' else 'samples'
        rows = data.get('profiles' if args.scene == 'visual-options' else count_key, [])
        print(json.dumps({'root': str(root), 'scene': args.scene, count_key: len(rows), 'complete': True}, indent=2))
        return
    if args.network_role:
        print(json.dumps({'root': str(root), 'network': data}, indent=2))
        return
    if args.startup_targets:
        print(json.dumps({'root': str(root), 'startup': data}, indent=2))
        return
    if args.settings_only:
        if data.get('complete') is not True: raise RuntimeError('Settings fixture did not complete')
        print(json.dumps({'root': str(root), 'settings': data['controls']}, indent=2))
        return
    log = (root / 'client.log').read_text()
    if data.get('complete') is not True:
        raise RuntimeError('Combat fixture did not complete')
    if 'Removed resource pack file/Fluid & Foolish' in log or not any(
            int(count) > 0 for count in re.findall(r'Loaded (\d+) animation clips', log)):
        raise RuntimeError('F&F did not load; this run does not qualify third-person animation')
    print(json.dumps({'root': str(root), 'cases': len(data['cases']), 'frames': len(data['frames']),
                      'bodySamples': len(data['bodies']), 'resourceLoaded': True}, indent=2))


if __name__ == '__main__':
    main()
