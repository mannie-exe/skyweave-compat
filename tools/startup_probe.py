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


def adq_ready(run):
    """An intermediate request trace is not the terminal native observation."""
    proof = run / 'adq-search-proof.json'
    if not proof.is_file():
        return False
    try:
        return json.loads(proof.read_text()).get('terminal', False)
    except json.JSONDecodeError:
        return False


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--server', type=Path, required=True, help='Read-only installed game directory')
    parser.add_argument('--java', required=True)
    parser.add_argument('--heap', choices=['4G','6G','8G'], default='4G', help='Owned fixture maximum heap; full replay scenes may require6G')
    parser.add_argument('--scenario', choices=['standalone', 'library-missing', 'library-unsupported', 'all-disabled', 'terrain-disabled', 'terrain', 'full',
                                             'unsupported-disabled', 'unsupported-enabled', 'questlog', 'questlog-disabled', 'jade', 'macaw', 'macaw-disabled'], required=True)
    parser.add_argument('--mod', type=Path)
    parser.add_argument('--extra-mod', type=Path, action='append', default=[], help='Additional explicit read-only artifacts for a bounded startup boundary')
    parser.add_argument('--reform-metadata-version', choices=['0.2.0', '0.3.0', '0.3.99', '0.4.0-rc.1'],
                        help='Standalone fixture only: alter the copied library metadata, preserving its bytecode; this tests admission, not a future binary')
    parser.add_argument('--output', type=Path, help='New owned evidence directory; preserve other probe reports')
    parser.add_argument('--fixture-config', type=Path, help='Explicit read-only config tree for this disposable fixture')
    parser.add_argument('--capture-config', action='store_true', help='Retain bounded generated fixture configs with the evidence')
    parser.add_argument('--resource-patches', type=Path, help='Read-only candidate resource JAR for full/all-disabled fixtures')
    parser.add_argument('--configuration-pack', type=Path, help='Authored common/server settings for the owned full served fixture')
    parser.add_argument('--serve', action='store_true', help='Keep an owned loopback server running; forward console input until stop')
    parser.add_argument('--without-compat', action='store_true', help='Serve a standalone fixture without the optional server capability')
    parser.add_argument('--islands', action='store_true', help='With terrain/full --serve, create an owned Skyweave world for remote-client checks')
    parser.add_argument('--seed', type=int, default=42, help='Seed for the owned served island fixture')
    parser.add_argument('--replay-regions', type=Path, help='Marked bounded region snapshot; no world identity or players')
    parser.add_argument('--replay-dh', type=Path, help='Existing cache copy for natural server-to-client LOD transfer')
    parser.add_argument('--enable', action='append', default=[], help='Explicit startup feature key to enable in this disposable fixture')
    parser.add_argument('--cookery', choices=['original', 'repaired'], help='Exercise the actual optional millstone capability in the owned server; requires explicit Cookery and probe extra JARs')
    parser.add_argument('--connector', choices=['original', 'repaired'], help='Observe actual connector COMMON loading and native range readers; requires explicit connector, Aeronautics, Create, Ponder and probe JARs')
    parser.add_argument('--connector-ceiling', type=int, choices=[40, 255], default=255, help='Expected actual Simulated ceiling in the explicit connector fixture config')
    parser.add_argument('--curio-break', choices=['original','repaired'], help='Observe native equipment break custody and notification ownership')
    parser.add_argument('--curio-break-slot', choices=['back','glider'], default='back', help='Native equipment slot; glider requires the authored slot subset')
    parser.add_argument('--adq-search', choices=['first-read','route'], help='Observe actual ADQ locate futures in a disposable island server; first-read aborts after one native chunk read')
    parser.add_argument('--adq-fault', choices=['search-ticket','endpoint-ticket'], help='Inject one after-mutation native ticket failure; separate explicit negative control')
    parser.add_argument('--adq-world', type=Path, help='Read-only marked disposable ADQ world snapshot to reopen')
    parser.add_argument('--adq-capture-world', action='store_true', help='Retain this disposable ADQ world after normal shutdown for native reopen checks')
    parser.add_argument('--adq-lifetime', action='store_true', help='Hold one fixture-only future proxy, cancel the actual owned job, verify settling admission and native successor')
    parser.add_argument('--adq-rng', type=int, help='Explicit fixture-only native RNG seed for matched comparisons; preserves native algorithm/draws')
    parser.add_argument('--adq-max-ticks', type=int, choices=[3600,7200,12000], default=3600, help='Explicit disposable observation window; product deadlines remain unchanged')
    args = parser.parse_args()
    if args.adq_lifetime and (args.adq_search!='route' or args.adq_fault or 'delivery.structure_search' not in args.enable):
        parser.error('Lifecycle control requires a separate enabled ADQ route fixture')
    if (args.adq_world or args.adq_capture_world) and (args.adq_search!='route' or args.adq_fault):
        parser.error('ADQ world capture/reopen requires a non-fault route fixture')
    if args.adq_world and not (args.adq_world/'skyweave-adq-fixture.json').is_file():
        parser.error('ADQ reopen requires the explicit disposable fixture marker')
    if args.adq_fault and (args.adq_search!='route' or 'delivery.structure_search' not in args.enable):
        parser.error('--adq-fault requires an explicitly enabled ADQ route fixture')
    if args.adq_search and (args.scenario not in {'terrain','full'} or args.serve or args.cookery or args.connector or args.curio_break):
        parser.error('--adq-search requires a separate terrain/full island fixture')
    if args.curio_break and (args.scenario != 'standalone' or args.serve or args.cookery or args.connector):
        parser.error('--curio-break requires a separate non-served standalone fixture')
    if args.cookery and args.scenario != 'standalone':
        parser.error('--cookery requires standalone')
    if args.connector and (args.scenario != 'standalone' or args.cookery or args.serve):
        parser.error('--connector requires a non-served standalone fixture without --cookery')
    if args.reform_metadata_version and args.scenario != 'standalone':
        parser.error('--reform-metadata-version requires standalone')
    if args.scenario == 'unsupported-enabled' and len(args.enable) > 1:
        parser.error('An unsupported-enabled fixture must select one refusal owner')
    if args.resource_patches:
        if args.scenario not in {'full','all-disabled'} or args.configuration_pack:
            parser.error('--resource-patches requires full/all-disabled without --configuration-pack')
        with zipfile.ZipFile(args.resource_patches) as archive:
            identity=tomllib.loads(archive.read('META-INF/neoforge.mods.toml').decode())
        if [entry['modId'] for entry in identity.get('mods',[])]!=['skyweave_resource_patches']:
            parser.error('--resource-patches must contain only the Resource Patches mod')
    if args.configuration_pack and (not args.serve or args.scenario!='full'):parser.error('--configuration-pack requires full --serve')
    if args.without_compat and (not args.serve or args.scenario != 'standalone'):
        parser.error('--without-compat requires --serve --scenario standalone')
    if args.islands and ((not args.serve and not args.adq_search) or args.scenario not in {'terrain','full'}):
        parser.error('--islands requires --serve with terrain or full')
    if not -(2**63)<=args.seed<2**63:parser.error('--seed must fit a signed Java long')
    if (args.replay_regions or args.replay_dh) and not(args.serve and args.islands):
        parser.error('Replay inputs require an owned served island fixture')
    server = args.server.expanduser().resolve()
    properties = dict(line.split('=', 1) for line in (ROOT / 'gradle.properties').read_text().splitlines() if '=' in line)
    version = properties['mod_version']
    mod = args.mod or ROOT / f'build/libs/skyweave-compat-{version}-mc1.21.1-neoforge.jar'
    launch = list((server / 'libraries/net/neoforged/neoforge').glob(f"{properties['neo_version']}/unix_args.txt"))
    if len(launch) != 1:
        parser.error(f"Expected the developed-against NeoForge {properties['neo_version']} launch configuration")
    output = args.output.expanduser().resolve() if args.output else ROOT / 'build/verification'
    if args.output and output.exists():
        parser.error('Explicit evidence directory must not already exist')
    output.mkdir(parents=True, exist_ok=True)
    report = {'scenario': args.scenario, 'artifact': str(mod), 'sha256': hashlib.sha256(mod.read_bytes()).hexdigest()}
    selected = {}
    with tempfile.TemporaryDirectory(prefix='skyweave-startup-') as directory:
        run = Path(directory)
        (run / 'libraries').symlink_to(server / 'libraries', target_is_directory=True)
        (run / 'mods').mkdir()
        (run / 'config').mkdir()
        (run / 'config/skyweave').mkdir()
        if args.adq_world:
            shutil.copytree(args.adq_world,run/'probe-world')
            report['adqWorldInput']={'path':str(args.adq_world),'questSha256':hashlib.sha256((args.adq_world/'adq_quests.json').read_bytes()).hexdigest()}
        if args.fixture_config:
            source_root = args.fixture_config.expanduser().resolve()
            if not source_root.is_dir():
                parser.error('Fixture config input must be an existing directory')
            report['fixtureConfig'] = {}
            for source in sorted(source_root.rglob('*')):
                if source.is_file():
                    relative = source.relative_to(source_root)
                    copy_owned(source, run / 'config' / relative, run)
                    report['fixtureConfig'][relative.as_posix()] = hashlib.sha256(source.read_bytes()).hexdigest()
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
            metadata = 'modLoader="lowcodefml"\nloaderVersion="[1,)"\nlicense="Apache-2.0"\n'
            for target in ['bettervillage', 'libraryferret', 'sky_archipelago', 'skyvillages', 'architectury', 'sable', 'aeronautics_delivery_quests',
                           'distanthorizons', 'bluemap', 'surveyor', 'lithostitched', 'yungsapi', 'betterdeserttemples', 'betterjungletemples', 'bettermineshafts', 'betterwitchhuts', 'betteroceanmonuments', 'analogaudio', 'analoglib', 'yigd', 'create', 'veil', 'iris', 'sodium', 'aeronautics', 'questlog', 'sabledynlights', 'lambdynlights', 'colorwheel', 'flywheel', 'linearbearing', 'linearbearingsfix', 'simulated', 'aeronautics_utility_objects', 'fusion', 'rechiseled', 'mcwbridges', 'ssrd', 'variantsandventures', 'kaleidoscope_cookery', 'create_aeronautics_throwable_rope_connector']:
                metadata += f'[[mods]]\nmodId="{target}"\nversion="999.0.0"\ndisplayName="Fixture {target}"\n'
            with zipfile.ZipFile(run / 'mods/future-targets-fixture.jar', 'w') as fixture:
                fixture.writestr('META-INF/neoforge.mods.toml', metadata)
        elif args.scenario not in {'standalone', 'library-missing', 'library-unsupported'}:
            for source in sorted((server / 'mods').glob('*.jar')):
                with zipfile.ZipFile(source) as jar:
                    meta = (tomllib.loads(jar.read('META-INF/neoforge.mods.toml').decode())
                            if 'META-INF/neoforge.mods.toml' in jar.namelist() else {})
                ids = {entry['modId'] for entry in meta.get('mods', [])}
                retired = ids & {'create_sky_compat', 'sky_village_neighbors', 'veil_iris_fix'}
                if retired: raise SystemExit('Remove retired integration artifacts from fixture inputs: ' + ', '.join(sorted(retired)))
                if 'skyweave_compat' in ids:
                    continue
                if args.resource_patches and 'skyweave_resource_patches' in ids:
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
        if args.scenario == 'library-unsupported':
            fixture = run / 'mods/unsupported-reform-fixture.jar'
            with zipfile.ZipFile(fixture, 'w') as archive:
                archive.writestr('META-INF/neoforge.mods.toml',
                                 'modLoader="lowcodefml"\nloaderVersion="[1,)"\nlicense="Apache-2.0"\n'
                                 '[[mods]]\nmodId="reform"\nversion="0.0.0"\ndisplayName="Unsupported re:form fixture"\n')
            selected[fixture.name] = hashlib.sha256(fixture.read_bytes()).hexdigest()
        if args.resource_patches:
            source=args.resource_patches.resolve()
            copy_owned(source,run / 'mods' / source.name,run)
            selected[source.name]=hashlib.sha256(source.read_bytes()).hexdigest()
        for source in args.extra_mod:
            source = source.expanduser().resolve()
            destination = run / 'mods' / source.name
            if not source.is_file() or source.suffix != '.jar' or destination.exists():
                parser.error('Extra startup artifacts must be existing JARs with unique filenames')
            copy_owned(source, destination, run)
            selected[source.name] = hashlib.sha256(source.read_bytes()).hexdigest()
        if args.reform_metadata_version:
            libraries = []
            for path in (run / 'mods').glob('*.jar'):
                with zipfile.ZipFile(path) as archive:
                    if 'META-INF/neoforge.mods.toml' not in archive.namelist(): continue
                    metadata = tomllib.loads(archive.read('META-INF/neoforge.mods.toml').decode())
                    if any(mod['modId'] == 'reform' for mod in metadata.get('mods', [])): libraries.append(path)
            if len(libraries) != 1: parser.error('The metadata fixture requires exactly one copied re:form JAR')
            library = libraries[0]
            with zipfile.ZipFile(library) as archive:
                entries = [(entry, archive.read(entry.filename)) for entry in archive.infolist()]
            rewritten = library.with_suffix('.fixture')
            with zipfile.ZipFile(rewritten, 'w') as archive:
                for entry, data in entries:
                    if entry.filename == 'META-INF/neoforge.mods.toml':
                        data, count = re.subn(r'(?m)^version\s*=\s*"[^"\n]+"',
                                             f'version="{args.reform_metadata_version}"', data.decode())
                        if count != 1: parser.error('Ambiguous re:form fixture version')
                        data = data.encode()
                    archive.writestr(entry, data)
            rewritten.replace(library)
            report['reformMetadataFixture'] = {'advertised': args.reform_metadata_version,
                                              'sourceSha256': selected[library.name],
                                              'scope': 'metadata only; original library code and other resources unchanged'}
            selected[library.name] = hashlib.sha256(library.read_bytes()).hexdigest()
        if args.scenario in {'all-disabled', 'terrain-disabled', 'unsupported-disabled', 'questlog-disabled', 'macaw-disabled'}:
            keys = re.findall(r'\("([a-z0-9_]+(?:\.[a-z0-9_]+)+)"', (ROOT / 'src/main/java/design/inherent/skyweavecompat/Feature.java').read_text())
            (run / 'config/skyweave/skyweave_compat-features.properties').write_text(''.join(f'{key}=false\n' for key in keys))
        if args.scenario == 'unsupported-enabled':
            # One known target owns this refusal; unrelated newer adapters stay disabled.
            keys = re.findall(r'\("([a-z0-9_]+(?:\.[a-z0-9_]+)+)"', (ROOT / 'src/main/java/design/inherent/skyweavecompat/Feature.java').read_text())
            enabled = set(args.enable or ['navigation.wayfinder'])
            (run / 'config/skyweave/skyweave_compat-features.properties').write_text(
                ''.join(f'{key}={str(key in enabled).lower()}\n' for key in keys))
        if args.enable:
            keys = set(re.findall(r'\("([a-z0-9_]+(?:\.[a-z0-9_]+)+)"', (ROOT / 'src/main/java/design/inherent/skyweavecompat/Feature.java').read_text()))
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
        port = 0
        (run / 'server.properties').write_text(
            f'server-ip=127.0.0.1\nserver-port={port}\nonline-mode=false\nlevel-name=probe-world\n'
            f'level-type={preset}\ngenerate-structures={str(bool(args.adq_search)).lower()}\nview-distance=2\nsimulation-distance=2\nmax-tick-time=0\n'
            + (f'level-seed={args.seed}\n' if args.islands else '')
            + ('difficulty=peaceful\ngamemode=creative\n' if args.serve else ''))
        command = [args.java, '-Xmx'+args.heap, '-XX:+ExitOnOutOfMemoryError', '-Dmixin.debug.export=true', '-Dmixin.debug.verbose=true',
                   '@' + str(launch[0]), 'nogui']
        if args.curio_break:
            command[1:1] = ['-Dneighbors.probe.curioBreak=' + args.curio_break, '-Dneighbors.probe.curioBreakSlot=' + args.curio_break_slot]
        if args.adq_search:
            command[1:1] = ['-Dneighbors.probe.adqSearch=true', '-Dneighbors.probe.output=' + str(run / 'adq-search-proof.json')]
            command.insert(1,'-Dneighbors.probe.adqMaxTicks='+str(args.adq_max_ticks))
            if args.adq_rng is not None:command.insert(1,'-Dneighbors.probe.adqRng='+str(args.adq_rng))
            if args.adq_lifetime:command.insert(1,'-Dneighbors.probe.adqLifetime=true')
            if 'delivery.structure_search' in args.enable:
                command.insert(1, '-Dneighbors.probe.adqBridge=true')
            if args.adq_search == 'first-read':
                command.insert(1, '-Dneighbors.probe.adqFirstRead=true')
            else:
                command.insert(1, '-Dneighbors.probe.adqRoute=true')
            if args.adq_fault:
                command.insert(1, '-Dneighbors.probe.adqFault='+args.adq_fault)
        if args.cookery:
            command[1:1] = ['-Dneighbors.probe.cookery=' + args.cookery]
            (run / 'cookery-fixture.json').write_text('{}\n')
        if args.connector:
            command[1:1] = ['-Dneighbors.probe.connector=' + args.connector]
            (run / 'connector-fixture.json').write_text(json.dumps({'ceiling': args.connector_ceiling}) + '\n')
        report['command'] = command
        log_path = output / f'startup-{args.scenario}.log'
        started = time.monotonic()
        done = False
        with log_path.open('w') as log:
            process = subprocess.Popen(command, cwd=run, stdin=subprocess.PIPE, stdout=log, stderr=subprocess.STDOUT, text=True)
            if args.adq_search:
                (output / 'native-process.json').write_text(json.dumps({'pid':process.pid,'runtime':str(run),
                    'scope':'Owned disposable ADQ native fixture; wrapper exit alone does not prove JVM termination'},indent=2)+'\n')
            try:
                deadline = time.monotonic() + (max(240,args.adq_max_ticks/20+120) if args.adq_search else 240)
                while process.poll() is None and time.monotonic() < deadline:
                    if ('Done (' in log_path.read_text(errors='replace')
                            and (not args.cookery or (run / 'cookery-proof.json').is_file())
                            and (not args.connector or (run / 'connector-proof.json').is_file())
                            and (not args.curio_break or (run / 'curio-break-proof.json').is_file())
                            and (not args.adq_search or adq_ready(run))):
                        done = True
                        if args.serve:
                            report['replayInputs'].update(stage_replay(args.replay_regions,None,run,args.seed))
                            print(json.dumps({'ready': True, 'pid': process.pid, 'run': str(run), 'log': str(log_path), 'port': port}), flush=True)
                            while process.poll() is None:
                                if select.select([sys.stdin], [], [], 0.5)[0]:
                                    line = sys.stdin.readline()
                                    if not line:
                                        process.communicate('stop\n', timeout=90)
                                        break
                                    process.stdin.write(line)
                                    process.stdin.flush()
                        elif process.poll() is None:
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
        if args.adq_search:
            (output/'process-retirement.json').write_text(json.dumps({'pid':process.pid,'exitCode':process.returncode,'pollTerminal':process.poll() is not None},indent=2))
            quest=run/'probe-world/adq_quests.json'
            if quest.is_file():shutil.copy2(quest,output/'adq_quests.json')
            if args.adq_capture_world and process.returncode==0 and adq_ready(run):
                world=output/'adq-world';shutil.copytree(run/'probe-world',world)
                (world/'skyweave-adq-fixture.json').write_text(json.dumps({'scope':'Disposable ADQ native qualification world; no personal source','seed':args.seed,'artifactSha256':report['sha256']}))
        if not args.serve and process.returncode == 0 and 'Done (' in text:
            done = done or ((not args.curio_break or (run / 'curio-break-proof.json').is_file())
                            and (not args.cookery or (run / 'cookery-proof.json').is_file())
                            and (not args.connector or (run / 'connector-proof.json').is_file())
                            and (not args.adq_search or adq_ready(run)))

        # Exported transformed targets must not reference Compat when no adapter was selected.
        references = []
        for target in (run / '.mixin.out').rglob('*.class'):
            if b'design/inherent/skyweavecompat/' in target.read_bytes():
                references.append(str(target.relative_to(run / '.mixin.out')))
        report.update(dependencies=selected, startup=done, exit_code=process.returncode,
                      seconds=round(time.monotonic() - started, 2), transformed_targets=references,
                      active_lines=[line for line in text.splitlines() if 'Skyweave: Compatibility Patches active adaptations:' in line],
                      generated_config=sorted(p.name for p in (run / 'config').iterdir()))
        if args.curio_break:
            proof = run / 'curio-break-proof.json'
            report['curioBreak'] = json.loads(proof.read_text()) if proof.is_file() else {'passed':False,'error':'Missing native proof'}
        if args.adq_search:
            proof = run / 'adq-search-proof.json'
            report['adqSearch'] = json.loads(proof.read_text()) if proof.is_file() else {'observed':False,'error':'Missing actual ADQ observation'}
            if proof.is_file():
                shutil.copy2(proof, output / 'adq-search-proof.json')
            fault=run/'adq-search-proof.json.fault.json'
            if fault.is_file():
                shutil.copy2(fault,output/'adq-ticket-fault.json')
            shutdown=run/'adq-search-proof.json.shutdown.json'
            if shutdown.is_file():shutil.copy2(shutdown,output/'adq-shutdown.json')
        if args.cookery:
            proof = run / 'cookery-proof.json'
            report['cookery'] = json.loads(proof.read_text()) if proof.is_file() else {'passed': False, 'error': 'Native fixture produced no report'}
        if args.connector:
            proof = run / 'connector-proof.json'
            report['connector'] = json.loads(proof.read_text()) if proof.is_file() else {'passed': False, 'error': 'Native fixture produced no report'}
        report['generated_config_files'] = sorted(str(path.relative_to(run / 'config'))
                                                for path in (run / 'config').rglob('*') if path.is_file())
        inactive = args.scenario in {'all-disabled', 'terrain-disabled', 'unsupported-disabled', 'questlog-disabled', 'macaw-disabled'}
        report['passed'] = (done and process.returncode == 0 and bool(report['active_lines'])
                            and (not inactive or (not references and any('adaptations: []' in line for line in report['active_lines']))))
        if args.scenario == 'unsupported-enabled':
            refusal = 'cannot enable ' + (args.enable[0] if args.enable else 'navigation.wayfinder') + '.'
            report['passed'] = (not done and refusal in text and 'installed 999.0.0' in text
                                and 'developed against ' in text and not report['active_lines'])
        if args.scenario in {'library-missing', 'library-unsupported'}:
            report['libraryDiagnostics'] = [line for line in text.splitlines() if 'reform' in line.lower() or 'mandatory dependencies' in line.lower()][:24]
            expected = '[MISSING]' if args.scenario == 'library-missing' else '0.0.0'
            report['passed'] = (not done and process.returncode in (0, 1) and not report['active_lines']
                                and 'Missing or unsupported mandatory dependencies' in text
                                and "Mod ID: 'reform', Requested by: 'skyweave_compat'" in text
                                and 'Error during pre-loading phase: Mod skyweave_compat requires reform ' in text
                                and expected in '\n'.join(report['libraryDiagnostics']))
        if args.scenario == 'standalone':
            expected = ['class/design/inherent/skyweavecompat/mixin/StructureSearchCacheAccess.class',
                        'class/net/minecraft/server/level/ServerChunkCache.class',
                        'class/net/minecraft/world/level/levelgen/PatrolSpawner.class']
            if args.curio_break or args.cookery or args.connector:
                # The explicit probe JAR instruments these native classes; ordinary standalone
                # startup retains its stricter three-target boundary.
                expected += ['class/' + name + '.class' for name in [
                    'net/minecraft/server/commands/LocateCommand', 'net/minecraft/world/level/chunk/ChunkGenerator',
                    'net/minecraft/world/level/chunk/LevelChunk', 'net/minecraft/world/level/chunk/ProtoChunk',
                    'net/minecraft/world/level/levelgen/feature/OreFeature',
                    'net/minecraft/world/level/levelgen/structure/PoolElementStructurePiece',
                    'net/minecraft/world/level/chunk/status/ChunkStatusTasks']]
                if args.curio_break:
                    expected += ['class/'+name+'.class' for name in ['io/wispforest/accessories/networking/AccessoriesNetworking','top/theillusivec4/curios/mixin/CuriosImplMixinHooks','io/wispforest/accessories/data/EntitySlotLoader','top/theillusivec4/curios/common/data/CuriosEntityManager']]
                if args.cookery == 'repaired':
                    expected += ['class/com/github/ysbbbbbb/kaleidoscopecookery/inventory/itemhandler/MillstoneInputHandler.class']
                if args.connector == 'repaired':
                    expected += ['class/net/sprocketgames/create_aeronautics_throwable_rope_connector/config/ModCommonConfig.class']
            report['passed'] = (done and process.returncode == 0 and
                (not references if args.without_compat else sorted(references) == sorted(expected)
                 and any('PATROL_CHANCE' in line and 'WAYFINDER' in line for line in report['active_lines'])))
            if args.reform_metadata_version == '0.2.0':
                report['passed'] = (not done and not report['active_lines']
                                    and "Mod ID: 'reform', Requested by: 'skyweave_compat'" in text
                                    and 'Missing or unsupported mandatory dependencies' in text)
            if args.reform_metadata_version == '0.4.0-rc.1':
                report['passed'] = (not done and not report['active_lines']
                                    and 'reform: developed against ' in text
                                    and 'installed 0.4.0-rc.1' in text)
        if args.capture_config:
            configs = sorted(path for path in (run / 'config').rglob('*') if path.is_file())
            if len(configs) > 64 or sum(path.stat().st_size for path in configs) > 4 * 1024 * 1024:
                raise ValueError('Fixture config capture exceeds 64 files or 4 MiB')
            report['capturedConfigs'] = {}
            for source in configs:
                relative = source.relative_to(run / 'config')
                copy_owned(source, output / 'config' / relative, output)
                report['capturedConfigs'][relative.as_posix()] = hashlib.sha256(source.read_bytes()).hexdigest()
        if args.cookery:
            report['passed'] = report['passed'] and report['cookery'].get('passed', False)
        if args.connector:
            report['passed'] = report['passed'] and report['connector'].get('passed', False)
        if args.curio_break:
            report['passed'] = report['passed'] and report['curioBreak'].get('passed',False)
        if args.adq_search:
            report['passed'] = report['passed'] and report['adqSearch'].get('observed',False) and report['adqSearch'].get('nativeInvocationReturned',False)
            if args.adq_search == 'route':
                report['passed'] = report['passed'] and (report['adqSearch'].get('faultControl',{}).get('passed',False) if args.adq_fault else report['adqSearch'].get('routeRegistered',False))
                if not args.adq_fault:
                    proof=report['adqSearch'];stored=proof.get('finalPersistence',{})
                    report['passed']=report['passed'] and stored.get('storedQuestCount',-1)==proof.get('nativeQuestCount',-2)
                    if args.adq_world:
                        report['passed']=report['passed'] and proof.get('initialPersistence',{}).get('sha256')==report['adqWorldInput']['questSha256'] and proof.get('initialQuestCount',0)>0
                    if args.adq_lifetime:report['passed']=report['passed'] and proof.get('lifecycleControl',{}).get('passed',False)
        (output / f'startup-{args.scenario}.json').write_text(json.dumps(report, indent=2) + '\n')
        print(json.dumps({key: report[key] for key in ['scenario', 'sha256', 'startup', 'exit_code', 'seconds', 'active_lines', 'passed']}, indent=2), flush=True)
        if not report['passed']:
            raise SystemExit(f'Startup check failed; inspect {log_path}')


if __name__ == '__main__':
    main()
