#!/usr/bin/env python3
"""Run the opt-in combat witness with packaged JARs in a new disposable client."""
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
    parser.add_argument('--scene', choices=('combat', 'poses', 'hand-effects'), default='combat')
    parser.add_argument('--heap', choices=('4G', '6G', '8G'), default='4G')
    parser.add_argument('--shader', choices=('off', 'complementary', 'bsl'), default='off', help='Hand-effects fixture shader')
    parser.add_argument('--connector-clean', type=Path, help='NeoForm clean renamed1.21.1 JAR for Connector development bootstrap')
    parser.add_argument('--admission', type=float, choices=(0, .5, 1), default=1)
    parser.add_argument('--sprint-crits', choices=('on', 'off'), default='on')
    parser.add_argument('--timeout', type=int, default=600, choices=range(60, 901), metavar='60..900')
    args = parser.parse_args()
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
            text, count = re.subn(r'(?m)^(\s*enableEasterEggs\s*=\s*)true\s*$', r'\g<1>false', subtle.read_text())
            if count != 1: raise RuntimeError('Expected one native development easter-egg fixture preference')
            subtle.write_text(text)
    if args.scene != 'combat':
        if not args.source_prism or args.network_role or args.startup_targets or args.extended:
            parser.error('Rendering scenes require their owned full-pack source without another scene selector')
        (root / 'rendering-review-fixture.json').write_text('{"owner":"combat_probe.py"}\n')
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
    if not args.source_prism:
        (root / 'options.txt').write_text('resourcePacks:' + json.dumps(['vanilla', 'mod_resources', 'file/' + resource.name]) + '\npauseOnLostFocus:false\nfullscreen:false\nrenderDistance:3\nsimulationDistance:3\nmaxFps:60\n')

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
    else:
        shader = {'off':'off','complementary':'Complementary Unbound r5.9.3 + Euphoria 1.10.5 + Skyweave 0.3.0.zip',
                  'bsl':'BSL_v10.1.8 + Clrwl_1.0.5 + Skyweave_0.3.0.zip'}[args.shader]
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
        'bootstrap': 'Pinned ModDev forgeclientdev; packaged gameplay/probe JARs; no project source roots'}, indent=2) + '\n')
    print(f'Launching owned combat client: {root}', flush=True)
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
                     else 'pose-results.json' if args.scene == 'poses' else 'hand-effects-results.json' if args.scene == 'hand-effects' else 'combat-report.json')
    if result or not report.exists():
        raise RuntimeError(f'Client exited {result}; inspect {root / "client.log"}')
    data = json.loads(report.read_text())
    if data.get('failure'):
        raise RuntimeError(data['failure'])
    if args.scene != 'combat':
        if data.get('complete') is not True: raise RuntimeError('Rendering fixture did not complete')
        print(json.dumps({'root': str(root), 'scene': args.scene, 'samples': len(data.get('samples', [])), 'complete': True}, indent=2))
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
