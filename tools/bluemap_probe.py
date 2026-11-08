#!/usr/bin/env python3
"""Verify optional BlueMap selection/reload/shutdown in disposable dedicated worlds."""
import argparse
import hashlib
import json
import shutil
import subprocess
import tempfile
import time
import tomllib
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def wait_for(process, predicate, seconds=150):
    deadline = time.monotonic() + seconds
    while time.monotonic() < deadline:
        if process.poll() is not None:
            raise RuntimeError(f'Test server exited: {process.returncode}')
        try:
            result = predicate()
            if result:
                return result
        except (FileNotFoundError, json.JSONDecodeError):
            pass
        time.sleep(.2)
    raise TimeoutError('Disposable BlueMap server did not reach the expected state')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--server', type=Path, required=True, help='Read-only installed game directory')
    parser.add_argument('--java', required=True)
    args = parser.parse_args()
    server = args.server.resolve()
    version = next(line.split('=', 1)[1] for line in (ROOT / 'gradle.properties').read_text().splitlines() if line.startswith('mod_version='))
    artifact = ROOT / f'build/libs/skyweave-compat-{version}-mc1.21.1-neoforge.jar'
    selected = {}
    for jar in (server / 'mods').glob('*.jar'):
        with zipfile.ZipFile(jar) as archive:
            if 'META-INF/neoforge.mods.toml' not in archive.namelist():
                continue
            metadata = tomllib.loads(archive.read('META-INF/neoforge.mods.toml').decode())
            for mod in metadata.get('mods', []):
                if mod['modId'] in {'bluemap', 'sky_archipelago'}:
                    selected[mod['modId']] = jar
    if set(selected) != {'bluemap', 'sky_archipelago'}:
        raise ValueError('Expected installed BlueMap and Sky Archipelago')
    output = ROOT / 'build/verification'
    output.mkdir(parents=True, exist_ok=True)
    report = {'artifactSha256': hashlib.sha256(artifact.read_bytes()).hexdigest(), 'cases': [], 'complete': False}
    cases = [('adapted', 'skyweave_compat:islands_v1', [-128, 768]),
             ('ordinary', 'sky_archipelago:sky_islands', [-64, 320]),
             ('foreign', 'minecraft:flat', None)]
    for name, preset, expected in cases:
        with tempfile.TemporaryDirectory(prefix='skyweave-bluemap-') as temp:
            run = Path(temp)
            (run / 'libraries').symlink_to(server / 'libraries', target_is_directory=True)
            (run / 'mods').mkdir()
            for jar in selected.values():
                (run / 'mods' / jar.name).symlink_to(jar)
            shutil.copy2(artifact, run / 'mods' / artifact.name)
            shutil.copy2(server / 'eula.txt', run / 'eula.txt')
            (run / 'server.properties').write_text(f'server-ip=127.0.0.1\nserver-port=0\nonline-mode=false\nlevel-name=world\nlevel-type={preset}\nlevel-seed=42\ngenerate-structures=false\nview-distance=2\nmax-tick-time=0\n')
            config = run / 'config/bluemap'
            shutil.copytree(server / 'config/bluemap', config)
            (config / 'webserver.conf').write_text('enabled: false\n')
            (config / 'plugin.conf').write_text('render-thread-count: 1\nscan-for-mod-resources: true\n')
            shutil.rmtree(config / 'maps')
            (config / 'maps').mkdir()
            (config / 'maps/world.conf').write_text('world: "world"\ndimension: "minecraft:overworld"\n')
            (run / 'bluemap').mkdir()
            shutil.copy2(server / 'bluemap/minecraft-client-1.21.1.jar', run / 'bluemap/minecraft-client-1.21.1.jar')
            # Reuse the source server's already accepted Minecraft resources. The
            # general full-pack stage disables downloads for unrelated probes;
            # that policy is not a valid BlueMap resource-loading fixture.
            core = config / 'core.conf'
            import re
            core.write_text(re.sub(r'accept-download:\s*(?:true|false)', 'accept-download: true', core.read_text()))
            policy = run / 'config/skyweave/skyweave_compat-bluemap.json'
            policy.parent.mkdir(parents=True, exist_ok=True)
            settings = dict(maps=['world'], detailDistance=4000, tileRadius=31, concurrentRequests=4, geometryMiB=256)
            policy.write_text(json.dumps(settings))
            manifest = run / 'bluemap/web/skyweave-compat/settings.json'
            launch = next((server / 'libraries/net/neoforged/neoforge').glob('*/unix_args.txt'))
            log_path = output / f'bluemap-{name}.log'
            with log_path.open('w') as log:
                process = subprocess.Popen([args.java, '-Xmx4G', '@' + str(launch), 'nogui'], cwd=run,
                                           stdin=subprocess.PIPE, stdout=log, stderr=subprocess.STDOUT, text=True)
                try:
                    wait_for(process, lambda: 'For help, type "help"' in log_path.read_text(errors='replace'))
                    def selected_manifest():
                        value = json.loads(manifest.read_text())
                        return value if value.get('schema') == 2 and value.get('maps') == (['world'] if expected else []) else None
                    value = wait_for(process, selected_manifest)
                    def reload_plugin():
                        offset = len(log_path.read_text(errors='replace'))
                        process.stdin.write('bluemap reload\n'); process.stdin.flush()
                        def completed():
                            text = log_path.read_text(errors='replace')[offset:]
                            if 'Could not load BlueMap!' in text or 'There was an error reloading BlueMap!' in text:
                                raise RuntimeError('BlueMap reload failed; inspect ' + str(log_path))
                            return 'BlueMap reloaded!' in text
                        # The adapter clears its manifest at disable, before the
                        # native reload finishes. Never overlap two reloads.
                        wait_for(process, completed)
                    if expected:
                        assert value['bounds']['world'] == dict(minY=expected[0], maxY=expected[1]), value
                        settings['maps'] = []
                        policy.write_text(json.dumps(settings))
                        reload_plugin()
                        wait_for(process, lambda: json.loads(manifest.read_text()).get('maps') == [])
                        settings['maps'] = ['world']
                        policy.write_text(json.dumps(settings))
                        reload_plugin()
                        wait_for(process, selected_manifest)
                    process.stdin.write('stop\n'); process.stdin.flush()
                    assert process.wait(timeout=90) == 0
                    assert json.loads(manifest.read_text())['maps'] == []
                    errors = ['Encountered an unexpected exception', 'Exception stopping the server', 'Could not clear Skyweave BlueMap', 'Skyweave BlueMap adapter disabled']
                    assert not any(error in log_path.read_text(errors='replace') for error in errors)
                    report['cases'].append(dict(preset=preset, bounds=expected, selected=bool(expected), reload=bool(expected), shutdownCleared=True))
                    print(f'{name}: selection, bounds and lifecycle passed', flush=True)
                finally:
                    if process.poll() is None:
                        process.stdin.write('stop\n'); process.stdin.flush()
                        try:
                            process.wait(timeout=60)
                        except subprocess.TimeoutExpired:
                            process.kill(); process.wait()
    report['complete'] = True
    (output / 'bluemap-native.json').write_text(json.dumps(report, indent=2) + '\n')


if __name__ == '__main__':
    main()
