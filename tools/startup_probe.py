#!/usr/bin/env python3
"""Check optional integration startup in disposable dedicated servers."""
import argparse
import hashlib
import json
import select
import shutil
import subprocess
import sys
import tempfile
import time
import tomllib
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--server', type=Path, required=True, help='Read-only installed game directory')
    parser.add_argument('--java', required=True)
    parser.add_argument('--scenario', choices=['standalone', 'all-disabled', 'terrain-disabled', 'terrain', 'full',
                                             'unsupported-disabled', 'unsupported-enabled', 'questlog', 'questlog-disabled', 'jade', 'macaw', 'macaw-disabled'], required=True)
    parser.add_argument('--mod', type=Path)
    parser.add_argument('--serve', action='store_true', help='Keep an owned loopback server running; forward console input until stop')
    parser.add_argument('--without-compat', action='store_true', help='Serve a standalone fixture without the optional server capability')
    parser.add_argument('--islands', action='store_true', help='With terrain --serve, create an owned Skyweave world for remote-client metadata checks')
    parser.add_argument('--enable', action='append', default=[], help='Explicit startup feature key to enable in this disposable fixture')
    args = parser.parse_args()
    if args.without_compat and (not args.serve or args.scenario != 'standalone'):
        parser.error('--without-compat requires --serve --scenario standalone')
    if args.islands and (not args.serve or args.scenario != 'terrain'):
        parser.error('--islands requires --serve --scenario terrain')
    server = args.server.expanduser().resolve()
    version = next(line.split('=', 1)[1] for line in (ROOT / 'gradle.properties').read_text().splitlines()
                   if line.startswith('mod_version='))
    mod = args.mod or ROOT / f'build/libs/skyweave-compat-{version}-mc1.21.1-neoforge.jar'
    launch = list((server / 'libraries/net/neoforged/neoforge').glob('21.1.265/unix_args.txt'))
    if len(launch) != 1:
        parser.error('Expected the supported NeoForge 21.1.265 launch configuration')
    output = ROOT / 'build/verification'
    output.mkdir(parents=True, exist_ok=True)
    report = {'scenario': args.scenario, 'artifact': str(mod), 'sha256': hashlib.sha256(mod.read_bytes()).hexdigest()}
    selected = {}
    with tempfile.TemporaryDirectory(prefix='skyweave-startup-') as directory:
        run = Path(directory)
        (run / 'libraries').symlink_to(server / 'libraries', target_is_directory=True)
        (run / 'mods').mkdir()
        (run / 'config').mkdir()
        (run / 'config/skyweave').mkdir()
        # Full-pack startup includes its established external prerequisite: Zfastnoise's
        # biome redirect conflicts with Sky Archipelago even when Compat is absent.
        # Keep Compat's own switches controlled below rather than importing them here.
        if args.scenario in {'full', 'all-disabled'}:
            prerequisite = server / 'config/zfastnoise.mixin.properties'
            if prerequisite.is_file():
                shutil.copy2(prerequisite, run / 'config' / prerequisite.name)
        shutil.copy2(server / 'eula.txt', run / 'eula.txt')
        if not args.without_compat:
            shutil.copy2(mod, run / 'mods' / mod.name)
        if args.scenario.startswith('unsupported-'):
            # Our own metadata-only fixture advertises future targets without providing their classes.
            # Disabled adapters must neither reject their versions nor resolve their APIs.
            metadata = 'modLoader="lowcodefml"\nloaderVersion="[1,)"\nlicense="MIT"\n'
            for target in ['sky_archipelago', 'skyvillages', 'architectury', 'sable', 'aeronautics_delivery_quests',
                           'distanthorizons', 'bluemap', 'surveyor', 'lithostitched', 'yungsapi', 'betterdeserttemples', 'betterjungletemples', 'bettermineshafts', 'betterwitchhuts', 'betteroceanmonuments', 'analogaudio', 'analoglib', 'yigd', 'create', 'veil', 'iris', 'sodium', 'aeronautics', 'questlog', 'sabledynlights', 'lambdynlights', 'colorwheel', 'flywheel', 'linearbearing', 'linearbearingsfix', 'simulated', 'aeronautics_utility_objects', 'fusion', 'rechiseled', 'mcwbridges', 'ssrd']:
                metadata += f'[[mods]]\nmodId="{target}"\nversion="999.0.0"\ndisplayName="Fixture {target}"\n'
            with zipfile.ZipFile(run / 'mods/future-targets-fixture.jar', 'w') as fixture:
                fixture.writestr('META-INF/neoforge.mods.toml', metadata)
        elif args.scenario != 'standalone':
            for source in sorted((server / 'mods').glob('*.jar')):
                with zipfile.ZipFile(source) as jar:
                    meta = (tomllib.loads(jar.read('META-INF/neoforge.mods.toml').decode())
                            if 'META-INF/neoforge.mods.toml' in jar.namelist() else {})
                ids = {entry['modId'] for entry in meta.get('mods', [])}
                retired = ids & {'create_sky_compat', 'sky_village_neighbors', 'veil_iris_fix'}
                if retired: raise SystemExit('Remove retired integration artifacts from fixture inputs: ' + ', '.join(sorted(retired)))
                if 'skyweave_compat' in ids:
                    continue
                if args.scenario in {'terrain', 'terrain-disabled'} and 'sky_archipelago' not in ids:
                    continue
                if args.scenario in {'questlog', 'questlog-disabled'} and 'questlog' not in ids:
                    continue
                if args.scenario in {'macaw', 'macaw-disabled'} and 'mcwbridges' not in ids:
                    continue
                if args.scenario == 'jade' and 'jade' not in ids:
                    continue
                shutil.copy2(source, run / 'mods' / source.name)
                selected[source.name] = hashlib.sha256(source.read_bytes()).hexdigest()
        if args.scenario in {'all-disabled', 'terrain-disabled', 'unsupported-disabled', 'questlog-disabled', 'macaw-disabled'}:
            import re
            keys = re.findall(r'\("([a-z_]+\.[a-z_]+)"', (ROOT / 'src/main/java/design/inherent/skyweavecompat/Feature.java').read_text())
            (run / 'config/skyweave/skyweave_compat-features.properties').write_text(''.join(f'{key}=false\n' for key in keys))
        if args.enable:
            import re
            keys = set(re.findall(r'\("([a-z_]+\.[a-z_]+)"', (ROOT / 'src/main/java/design/inherent/skyweavecompat/Feature.java').read_text()))
            if not set(args.enable) <= keys:
                parser.error('Unknown fixture feature key')
            feature_file = run / 'config/skyweave/skyweave_compat-features.properties'
            lines = feature_file.read_text().splitlines() if feature_file.exists() else []
            lines = [line for line in lines if line.partition('=')[0] not in args.enable]
            feature_file.write_text('\n'.join(lines + [key + '=true' for key in args.enable]) + '\n')
        # Startup boundaries stay flat; the explicit served fixture qualifies tracked island metadata.
        preset='skyweave_compat:islands_v1' if args.islands else 'minecraft:flat'
        report['worldPreset']=preset
        (run / 'server.properties').write_text(
            'server-ip=127.0.0.1\nserver-port=0\nonline-mode=false\nlevel-name=probe-world\n'
            f'level-type={preset}\ngenerate-structures=false\nview-distance=2\nsimulation-distance=2\nmax-tick-time=0\n'
            + ('level-seed=42\n' if args.islands else ''))
        command = [args.java, '-Xmx4G', '-Dmixin.debug.export=true', '-Dmixin.debug.verbose=true',
                   '@' + str(launch[0]), 'nogui']
        report['command'] = command
        log_path = output / f'startup-{args.scenario}.log'
        started = time.monotonic()
        done = False
        with log_path.open('w') as log:
            process = subprocess.Popen(command, cwd=run, stdin=subprocess.PIPE, stdout=log, stderr=subprocess.STDOUT, text=True)
            try:
                deadline = time.monotonic() + 240
                while process.poll() is None and time.monotonic() < deadline:
                    if 'Done (' in log_path.read_text(errors='replace'):
                        done = True
                        if args.serve:
                            print(json.dumps({'ready': True, 'pid': process.pid, 'run': str(run), 'log': str(log_path)}), flush=True)
                            while process.poll() is None:
                                if select.select([sys.stdin], [], [], 0.5)[0]:
                                    line = sys.stdin.readline()
                                    if not line:
                                        process.communicate('stop\n', timeout=90)
                                        break
                                    process.stdin.write(line)
                                    process.stdin.flush()
                        else:
                            process.communicate('stop\n', timeout=90)
                        break
                    time.sleep(0.5)
            finally:
                if process.poll() is None:
                    process.terminate()
                    try:
                        process.wait(timeout=15)
                    except subprocess.TimeoutExpired:
                        process.kill()
                        process.wait()
        text = log_path.read_text(errors='replace')
        # Exported transformed targets must not reference Compat when no adapter was selected.
        references = []
        for target in (run / '.mixin.out').rglob('*.class'):
            if b'design/inherent/skyweavecompat/' in target.read_bytes():
                references.append(str(target.relative_to(run / '.mixin.out')))
        report.update(dependencies=selected, startup=done, exit_code=process.returncode,
                      seconds=round(time.monotonic() - started, 2), transformed_targets=references,
                      active_lines=[line for line in text.splitlines() if 'Skyweave: Compatibility Patches active adaptations:' in line],
                      generated_config=sorted(p.name for p in (run / 'config').iterdir()))
        inactive = args.scenario in {'all-disabled', 'terrain-disabled', 'unsupported-disabled', 'questlog-disabled', 'macaw-disabled'}
        report['passed'] = (done and process.returncode == 0 and bool(report['active_lines'])
                            and (not inactive or (not references and any('adaptations: []' in line for line in report['active_lines']))))
        if args.scenario == 'unsupported-enabled':
            report['passed'] = not done and "Review Wayfinder's coordinate-frame guard for Sable 999.0.0; supported optional version is 2.0.6" in text
        if args.scenario == 'standalone':
            expected = ['class/design/inherent/skyweavecompat/mixin/StructureSearchCacheAccess.class',
                        'class/net/minecraft/server/level/ServerChunkCache.class']
            report['passed'] = (done and process.returncode == 0 and
                (not references if args.without_compat else sorted(references) == expected
                 and any('adaptations: [WAYFINDER]' in line for line in report['active_lines'])))
        (output / f'startup-{args.scenario}.json').write_text(json.dumps(report, indent=2) + '\n')
        print(json.dumps({key: report[key] for key in ['scenario', 'sha256', 'startup', 'exit_code', 'seconds', 'active_lines', 'passed']}, indent=2), flush=True)
        if not report['passed']:
            raise SystemExit(f'Startup check failed; inspect {log_path}')


if __name__ == '__main__':
    main()
