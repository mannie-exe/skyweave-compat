#!/usr/bin/env python3
"""Run bounded machinery and recovery checks in a disposable, loopback-only server."""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parents[1]
LINEAR_HASH = '29563cdb52a82ac7d8b3aca823cd47a15c443e06314df06ca1a635ae9e91a253'
FIX_HASH = '04afaaaf62951fc4cccfbb4468572fea2a876a6b5e872a6c60545d171fb21976'
SCENES = {'lantern': 'macaw/LanternProbe', 'redstone': 'redstone/RedstoneProbe', 'recovery': 'recovery/RecoveryProbe', 'bootstrap': 'linear/BootstrapProbe', 'carriage': 'linear/CarriageProbe',
          'parent': 'linear/ParentProbe', 'reopen': 'linear/ParentProbe',
          'disassembly': 'linear/DisassemblyProbe', 'transmission': 'linear/TransmissionProbe',
          'linkage': 'linkage/LinkageProbe',
          'linkage-physics': 'linkage/LinkagePhysicsProbe',
          'linkage-lifecycle': 'linkage/LinkageLifecycleProbe'}


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--server', type=Path, required=True, help='Read-only installed game directory')
    parser.add_argument('--pack', type=Path, required=True, help='Read-only authored Skyweave directory')
    parser.add_argument('--java', type=Path, required=True, help='Java 21 executable; javac must be alongside it')
    parser.add_argument('--linear', type=Path, help='Unmodified Linear Bearing 1.3.5 JAR')
    parser.add_argument('--redstone-gui', type=Path)
    parser.add_argument('--redstone-additions', type=Path)
    parser.add_argument('--redstone-target', choices=['both', 'gui', 'additions'], default='both')
    parser.add_argument('--redstone-baseline', action='store_true')
    parser.add_argument('--yigd', type=Path, help='Exact YiGD recovery fixture dependency')
    parser.add_argument('--linkage', type=Path, help='Unmodified Transmission & Linkage 0.2.9 JAR')
    parser.add_argument('--linkage-baseline', action='store_true', help='Disable the Linkage adapters for a native failure control')
    parser.add_argument('--fix', type=Path, help='Optional unmodified Linear Bearings Fix 1.0.3 JAR')
    parser.add_argument('--mod', type=Path)
    parser.add_argument('--minimal', action='store_true', help='Only the selected scene’s required target dependencies')
    parser.add_argument('--scenario', choices=SCENES, required=True)
    parser.add_argument('--orientation', choices=['floor', 'ceiling', 'wall-front', 'wall-back', 'height', 'quarter'], default='floor')
    args = parser.parse_args()
    if args.scenario == 'recovery' and not args.yigd:
        parser.error('--scenario recovery requires --yigd')
    if args.scenario not in {'recovery', 'redstone', 'lantern'} and not args.linear:
        parser.error('Machinery scenes require --linear')
    allowed = {'carriage': {'floor', 'ceiling', 'wall-front', 'wall-back'},
               'disassembly': {'floor', 'height', 'quarter'}}.get(args.scenario, {'floor'})
    if args.orientation not in allowed:
        parser.error('This scene does not implement that orientation')
    args.server = args.server.expanduser().resolve()
    args.pack = args.pack.expanduser().resolve()
    args.java = args.java.expanduser().resolve()
    if (args.linear and digest(args.linear) != LINEAR_HASH) or (args.fix and digest(args.fix) != FIX_HASH):
        parser.error('Unexpected Linear Bearing or standalone-fix bytes')
    if args.yigd and digest(args.yigd) != 'dd2142a3c6a9d5b990ab36220be482f7aa9f528755f93b8fef8996f509ddcda2':
        parser.error('Unexpected YiGD 2.0.13 bytes')
    version = next(line.split('=', 1)[1] for line in (ROOT / 'gradle.properties').read_text().splitlines()
                   if line.startswith('mod_version='))
    mod = args.mod or ROOT / f'build/libs/skyweave-compat-{version}-mc1.21.1-neoforge.jar'
    runtime = json.loads(subprocess.check_output(['node', str(args.pack / 'web/scripts/runtime-inputs.mjs'), str(args.pack)], text=True))
    excluded = {'skyweave-compat', 'linear-bearing', 'linear-bearings-fix'}
    if args.yigd:
        excluded.add('yigd')
    if args.linkage:
        excluded.add('create-aeronautics-transmission-linkage')
    minimal_keys = {'sable', 'cloth-config'} if args.scenario == 'recovery' else {'create', 'ponder', 'sable', 'create-aeronautics'}
    if args.scenario == 'lantern':
        minimal_keys = {'sable', 'cloth-config', 'macaws-bridges'}
    if args.scenario == 'redstone':
        minimal_keys = {'create', 'ponder'}
    files = [Path(row['path']) for row in runtime['mods'] if row['key'] not in excluded
             and (not args.minimal or row['key'] in minimal_keys)]
    if not args.minimal:
        files.extend((args.pack / 'overrides/common/mods').glob('*.jar'))
    files.append(mod)
    for target, expected in [(args.redstone_gui, '34cb29227e7aa2a497956aa6f5bb74f07f40f134346ca847c0d4350b87345c97'), (args.redstone_additions, 'e030a25518b757c037d80e9ba25f9631074d3936d157f4d6fd129337b434e706')]:
        if target:
            if digest(target) != expected:
                parser.error('Unexpected redstone target bytes')
            if args.redstone_target == "both" or (target == args.redstone_gui and args.redstone_target == "gui") or (target == args.redstone_additions and args.redstone_target == "additions"):
                files.append(target.resolve())
    if args.linear:
        files.append(args.linear)
    if args.yigd:
        files.append(args.yigd)
    if args.fix:
        files.append(args.fix)
    if args.scenario.startswith('linkage'):
        if not args.linkage or digest(args.linkage) != '2b1015c61e66e6e9df0e74e819e2797882b9ca246afc431984858fce95752c41':
            parser.error('Linkage scene requires the reviewed 0.2.9 JAR')
        files.append(args.linkage)
    if len({path.name for path in files}) != len(files):
        parser.error('Duplicate selected JAR basename')
    output = ROOT / 'build/verification/linear' / f'{args.scenario}-{args.orientation}-{"coexist" if args.fix else "absent"}'
    if args.minimal:
        output = output.with_name(output.name + '-minimal')
    if args.scenario == 'redstone':
        output = output.with_name(output.name + '-' + args.redstone_target)
    if args.redstone_baseline:
        output = output.with_name(output.name + '-baseline')
    if args.linkage_baseline:
        output = output.with_name(output.name + '-baseline')
    output.mkdir(parents=True, exist_ok=True)
    manifest = {'scenario': args.scenario, 'orientation': args.orientation,
                'artifact': str(mod), 'sha256': digest(mod),
                'files': {path.name: digest(path) for path in files}}
    # Freeze selected bytes for both phases, including when another author edits the pack.
    with tempfile.TemporaryDirectory(prefix='linear-review-') as directory:
        run = Path(directory)
        (run / 'mods').mkdir()
        for path in files:
            shutil.copy2(path, run / 'mods' / path.name)
        libraries = args.server / 'libraries'
        (run / 'libraries').symlink_to(libraries, target_is_directory=True)
        game = libraries / 'net/neoforged/neoforge/21.1.265/neoforge-21.1.265-server.jar'
        dependencies = list((ROOT / 'build/dependencies').glob('*.jar'))
        classpath = ':'.join(map(str, [game, *sorted(libraries.rglob('*.jar')), *sorted((run / 'mods').glob('*.jar')), *dependencies, *[p.resolve() for p in [args.redstone_gui, args.redstone_additions] if p]]))
        classes = run / 'classes'
        classes.mkdir()
        source = ROOT / 'tools/fixtures' / f'{SCENES[args.scenario]}.java'
        compile_command = [str(args.java.with_name('javac')), '-proc:none', '-cp', classpath, '-d', str(classes), str(source)]
        if args.scenario == 'recovery' and not args.minimal:
            compile_command.append(str(ROOT / 'tools/fixtures/recovery/BackpackRecovery.java'))
        if args.scenario == 'redstone':
            compile_command.append(str(ROOT / 'tools/fixtures/redstone/RedstoneGuiProbe.java'))
        if args.scenario == 'redstone' and not args.minimal:
            compile_command.append(str(ROOT / 'tools/fixtures/redstone/RedstoneShipProbe.java'))
        result = subprocess.run(compile_command, capture_output=True, text=True)
        (output / 'compile.log').write_text(result.stdout + result.stderr)
        if result.returncode:
            raise SystemExit(f'Fixture compilation failed: {output / "compile.log"}')
        with zipfile.ZipFile(run / 'mods/linear-review-probe.jar', 'w') as jar:
            for path in classes.rglob('*.class'):
                jar.write(path, path.relative_to(classes))
            jar.writestr('META-INF/neoforge.mods.toml', 'modLoader="javafml"\nloaderVersion="[4,)"\nlicense="MIT"\n[[mods]]\nmodId="linear_review_probe"\nversion="1.0.0"\ndisplayName="Isolated compatibility checks"\n')
        shutil.copytree(args.pack / 'overrides/common/config', run / 'config')
        if args.linkage_baseline:
            settings = run / 'config/skyweave/skyweave_compat-features.properties'
            import re
            text = settings.read_text() if settings.exists() else ''
            text = re.sub(r'(?m)^transmission_linkage\.[^\n]*\n?', '', text)
            settings.write_text(text + '\ntransmission_linkage.interactions=false\ntransmission_linkage.jade=false\n')
        if args.redstone_baseline:
            settings = run / 'config/skyweave/skyweave_compat-features.properties'
            import re
            text = re.sub(r'(?m)^(redstone_link_gui|redstone_additions)\.[^\n]*\n?', '', settings.read_text() if settings.exists() else '')
            keys = ['redstone_link_gui.interactions', 'redstone_link_gui.relocation', 'redstone_link_gui.ship_access', 'redstone_additions.logic', 'redstone_additions.scraping', 'redstone_additions.wrench', 'redstone_additions.transparency']
            settings.write_text(text + '\n' + ''.join(key + '=false\n' for key in keys))
        blue = run / 'config/bluemap'
        blue.mkdir(exist_ok=True)
        (blue / 'webserver.conf').write_text('enabled: false\n')
        (blue / 'core.conf').write_text('accept-download: false\nmetrics: false\n')
        shutil.copy2(args.server / 'eula.txt', run / 'eula.txt')
        (run / 'server.properties').write_text('server-ip=127.0.0.1\nserver-port=0\nonline-mode=false\nlevel-name=fixture\nlevel-type=minecraft:flat\ngenerate-structures=false\nview-distance=2\nsimulation-distance=2\nmax-tick-time=0\n')
        manifest['probeSha256'] = digest(run / 'mods/linear-review-probe.jar')
        manifest['sources'] = {str(source.relative_to(ROOT)): digest(source)}
        if args.scenario == 'redstone':
            for helper in ['RedstoneGuiProbe.java', *(['RedstoneShipProbe.java'] if not args.minimal else [])]:
                path = ROOT / 'tools/fixtures/redstone' / helper
                manifest['sources'][str(path.relative_to(ROOT))] = digest(path)
        if args.scenario == 'recovery' and not args.minimal:
            helper = ROOT / 'tools/fixtures/recovery/BackpackRecovery.java'
            manifest['sources'][str(helper.relative_to(ROOT))] = digest(helper)
        manifest['config'] = {str(path.relative_to(run)): digest(path) for path in sorted((run / 'config').rglob('*')) if path.is_file()}
        (output / 'manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')
        phases = ['save', 'restore'] if args.scenario in {'reopen', 'transmission', 'linkage-lifecycle', 'recovery'} else ['run']
        for phase in phases:
            report = output / f'{phase}.json'
            report.unlink(missing_ok=True)
            Path(str(report) + '.failure').unlink(missing_ok=True)
            command = [str(args.java), '-Xmx4G', f'-Dlinear.review.case={args.orientation}',
                       f'-Dlinear.review.output={report}', f'-Dlinear.review.previous={output / "save.json"}',
                       f'-Dlinear.review.restore={str(phase == "restore").lower()}',
                       f'-Dredstone.review.baseline={str(args.redstone_baseline).lower()}',
                       f'-Dlinkage.review.baseline={str(args.linkage_baseline).lower()}',
                       f'-Dlinear.review.reopen={str(args.scenario == "reopen").lower()}',
                       '@' + str(libraries / 'net/neoforged/neoforge/21.1.265/unix_args.txt'), 'nogui']
            with (output / f'{phase}.log').open('w') as log:
                result = subprocess.run(command, cwd=run, stdout=log, stderr=subprocess.STDOUT, timeout=180)
            if args.scenario == 'recovery' and (run / 'config/yigd.json').is_file():
                shutil.copy2(run / 'config/yigd.json', output / 'generated-yigd.json')
            if result.returncode or not report.exists() or Path(str(report) + '.failure').exists():
                raise SystemExit(f'Native {args.scenario}/{phase} failed: {output}')
            print(phase, json.loads(report.read_text()), flush=True)


if __name__ == '__main__':
    main()
