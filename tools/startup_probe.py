#!/usr/bin/env python3
"""Check optional integration startup in disposable dedicated servers."""
import argparse
import hashlib
import json
import re
import select
import shutil
import subprocess
import sys
import tempfile
import time
import tomllib
import zipfile
from pathlib import Path
from fixture_files import copy_owned, stage_configs, stage_replay

ROOT = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--server', type=Path, required=True, help='Read-only installed game directory')
    parser.add_argument('--java', required=True)
    parser.add_argument('--heap', choices=['4G','6G','8G'], default='4G', help='Owned fixture maximum heap; full replay scenes may require6G')
    parser.add_argument('--scenario', choices=['standalone', 'all-disabled', 'terrain-disabled', 'terrain', 'full',
                                             'unsupported-disabled', 'unsupported-enabled', 'questlog', 'questlog-disabled', 'jade', 'macaw', 'macaw-disabled'], required=True)
    parser.add_argument('--mod', type=Path)
    parser.add_argument('--configuration-pack', type=Path, help='Authored common/server settings for the owned full served fixture')
    parser.add_argument('--serve', action='store_true', help='Keep an owned loopback server running; forward console input until stop')
    parser.add_argument('--without-compat', action='store_true', help='Serve a standalone fixture without the optional server capability')
    parser.add_argument('--islands', action='store_true', help='With terrain/full --serve, create an owned Skyweave world for remote-client checks')
    parser.add_argument('--seed', type=int, default=42, help='Seed for the owned served island fixture')
    parser.add_argument('--replay-regions', type=Path, help='Marked bounded region snapshot; no world identity or players')
    parser.add_argument('--replay-dh', type=Path, help='Existing cache copy for natural server-to-client LOD transfer')
    parser.add_argument('--enable', action='append', default=[], help='Explicit startup feature key to enable in this disposable fixture')
    args = parser.parse_args()
    if args.configuration_pack and (not args.serve or args.scenario!='full'):parser.error('--configuration-pack requires full --serve')
    if args.without_compat and (not args.serve or args.scenario != 'standalone'):
        parser.error('--without-compat requires --serve --scenario standalone')
    if args.islands and (not args.serve or args.scenario not in {'terrain','full'}):
        parser.error('--islands requires --serve with terrain or full')
    if not -(2**63)<=args.seed<2**63:parser.error('--seed must fit a signed Java long')
    if (args.replay_regions or args.replay_dh) and not(args.serve and args.islands):
        parser.error('Replay inputs require an owned served island fixture')
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
            for target in ['bettervillage', 'libraryferret', 'sky_archipelago', 'skyvillages', 'architectury', 'sable', 'aeronautics_delivery_quests',
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
            keys = re.findall(r'\("([a-z_]+\.[a-z_]+)"', (ROOT / 'src/main/java/design/inherent/skyweavecompat/Feature.java').read_text())
            (run / 'config/skyweave/skyweave_compat-features.properties').write_text(''.join(f'{key}=false\n' for key in keys))
        if args.enable:
            keys = set(re.findall(r'\("([a-z_]+\.[a-z_]+)"', (ROOT / 'src/main/java/design/inherent/skyweavecompat/Feature.java').read_text()))
            if not set(args.enable) <= keys:
                parser.error('Unknown fixture feature key')
            feature_file = run / 'config/skyweave/skyweave_compat-features.properties'
            lines = feature_file.read_text().splitlines() if feature_file.exists() else []
            lines = [line for line in lines if line.partition('=')[0] not in args.enable]
            feature_file.write_text('\n'.join(lines + [key + '=true' for key in args.enable]) + '\n')
        if args.configuration_pack:
            report['authoredConfiguration']=stage_configs(args.configuration_pack,run)
            adq_config=run / 'config/aeronautics_delivery_quests.toml'
            if adq_config.is_file():
                text,count=re.subn(r'(?m)^(\s*questCreationInterval\s*=).*$',r'\1 1440',adq_config.read_text())
                if count!=1:raise ValueError('Ambiguous native ADQ quest interval')
                adq_config.write_text(text)
                report['authoredConfiguration']['config/aeronautics_delivery_quests.toml']['fixtureOverride']='Defer unrelated periodic quests during navigation checks; not ADQ qualification'
                report['authoredConfiguration']['config/aeronautics_delivery_quests.toml']['beforeLaunchSha256']=hashlib.sha256(adq_config.read_bytes()).hexdigest()
            dh_config=run / 'config/DistantHorizons.toml'
            if dh_config.is_file():
                # Ordinary transfer uses DH's request machinery. A disabled plan also
                # disables retrieval; cache replay may read existing terrain but never generate it.
                text=dh_config.read_text()
                plan='CHUNKS_ONLY' if args.replay_dh else 'DISABLED'
                if 'generatorPlan' in tomllib.loads(text).get('common',{}).get('worldGenerator',{}):
                    text,count=re.subn(r'(?m)^(\s*generatorPlan\s*=).*$',r'\1 "'+plan+'"',text)
                    if count!=1:raise ValueError('Ambiguous native DH generator plan')
                else:text+='\n[common.worldGenerator]\ngeneratorPlan = "'+plan+'"\nchunkGeneratorMode = "PRE_EXISTING_ONLY"\n'
                if args.replay_dh:
                    text,count=re.subn(r'(?m)^(\s*chunkGeneratorMode\s*=).*$',r'\1 "PRE_EXISTING_ONLY"',text)
                    if count!=1:raise ValueError('Ambiguous native DH chunk mode')
                    text,count=re.subn(r'(?m)^(\s*maxGenerationRequestDistance\s*=).*$',r'\1 64',text)
                    if count!=1:raise ValueError('Ambiguous native DH request distance')
                dh_config.write_text(text)
                report['authoredConfiguration']['config/DistantHorizons.toml']['fixtureOverride']=(
                        'Native cache transfer, PRE_EXISTING_ONLY, bounded64chunks; no new terrain or pregen'
                        if args.replay_dh else 'Disable background DH generation; existing cached data only')
                report['authoredConfiguration']['config/DistantHorizons.toml']['beforeLaunchSha256']=hashlib.sha256(dh_config.read_bytes()).hexdigest()
            resources=args.configuration_pack / 'overrides/common/mods/skyweave-resource-patches-0.1.0.jar'
            if resources.is_file():
                copy_owned(resources,run / 'mods' / resources.name,run)
                selected[resources.name]=hashlib.sha256(resources.read_bytes()).hexdigest()
        # Startup boundaries stay flat; the explicit served fixture qualifies tracked island metadata.
        preset='skyweave_compat:islands_v1' if args.islands else 'minecraft:flat'
        # The generator must establish its own fresh identity. Distant region replay
        # follows startup and refuses any region the new world already created.
        report['replayInputs']=stage_replay(None,args.replay_dh,run,args.seed)
        report['worldPreset']=preset
        (run / 'server.properties').write_text(
            'server-ip=127.0.0.1\nserver-port=0\nonline-mode=false\nlevel-name=probe-world\n'
            f'level-type={preset}\ngenerate-structures=false\nview-distance=2\nsimulation-distance=2\nmax-tick-time=0\n'
            + (f'level-seed={args.seed}\n' if args.islands else '')
            + ('difficulty=peaceful\ngamemode=creative\n' if args.serve else ''))
        command = [args.java, '-Xmx'+args.heap, '-Dmixin.debug.export=true', '-Dmixin.debug.verbose=true',
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
                            report['replayInputs'].update(stage_replay(args.replay_regions,None,run,args.seed))
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
