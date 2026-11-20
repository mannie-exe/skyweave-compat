#!/usr/bin/env python3
"""Run the packaged Compat settings client against isolated local settings fixtures."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shlex
import shutil
import signal
import subprocess
import tempfile
import time

from combat_probe import REPOSITORY, argument_file, mod_ids


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def packaged_command(root, java, clean):
    moddev = REPOSITORY / 'build/moddev'

    def runtime_library(path):
        return not (str(REPOSITORY / 'build/classes') in path or str(REPOSITORY / 'build/resources') in path
                    or mod_ids(Path(path)) - {'minecraft', 'neoforge'})

    classpath = shlex.split((moddev / 'clientRunClasspath.txt').read_text())
    for index, value in enumerate(classpath):
        if index and classpath[index - 1] in ('-cp', '-classpath', '--class-path'):
            classpath[index] = os.pathsep.join(path for path in value.split(os.pathsep) if runtime_library(path))
    argument_file(root / 'classpath.args', classpath)
    legacy = (moddev / 'clientLegacyClasspath.txt').read_text().splitlines()
    (root / 'legacy-classpath.txt').write_text('\n'.join(path for path in legacy if runtime_library(path)) + '\n')
    vm = shlex.split((moddev / 'clientRunVmArgs.txt').read_text())
    vm = [value for value in vm if not value.startswith('-DlegacyClassPath.file=')]
    vm.extend(['-DlegacyClassPath.file=' + str(root / 'legacy-classpath.txt'), '-Xms512M', '-Xmx2G',
               '-Dconnector.clean.path=' + str(clean), '-Dskyweave_compat.probe.configuration=true',
               '-Dskyweave_compat.probe.misc=true'])
    argument_file(root / 'vm.args', vm)
    program = shlex.split((moddev / 'clientRunProgramArgs.txt').read_text(), comments=True)
    argument_file(root / 'program.args', program)
    return [str(java), '@classpath.args', '@vm.args', 'net.neoforged.devlaunch.Main', '@program.args']


class Client:
    def __init__(self, root, process):
        self.root = root
        self.process = process
        self.deadline = time.monotonic() + 115
        self.sequence = 0

    def until(self, predicate, reason):
        limit = min(self.deadline, time.monotonic() + 45)
        while time.monotonic() < limit and self.process.poll() is None:
            result = predicate()
            if result:
                return result
            time.sleep(.12)
        raise RuntimeError(f'{reason} (client exit {self.process.poll()})')

    def state(self, probe='config'):
        try:
            return json.loads((self.root / f'{probe}-review-state.json').read_text())
        except (FileNotFoundError, json.JSONDecodeError):
            return {}

    def request(self, operation, probe='config', **arguments):
        self.sequence += 1
        control = self.root / f'{probe}-review-control.json'
        pending = control.with_suffix('.pending')
        pending.write_text(json.dumps(dict(sequence=self.sequence, operation=operation, **arguments)))
        pending.replace(control)

        def seen():
            state = self.state(probe)
            return state if state.get('sequence') == self.sequence else None

        return self.until(seen, f'control {operation!r} was not observed')

    def act(self, operation, probe='config', **arguments):
        state = self.request(operation, probe, **arguments)
        if state.get('error'):
            raise RuntimeError(f'{operation} {arguments}: {state["error"]}')
        return state

    def click(self, label):
        return self.act('click', label=label)

    def wait_active(self, label):
        return self.until(lambda: any(widget['label'] == label and widget['active']
                                      for widget in self.state().get('widgets', [])),
                          f'button {label!r} did not activate')

    def field(self, before, after, label=None):
        inputs = [w for w in self.state()['widgets'] if 'value' in w]
        matching = [i for i, widget in enumerate(inputs) if widget['value'] == before
                    and (label is None or widget['label'] == label)]
        if len(matching) != 1:
            raise RuntimeError(f'Expected one native input with {before!r}: {[w["value"] for w in inputs]}')
        return self.act('field', index=matching[0], value=after)

    def map(self):
        self.act('hoofprint-proof')
        return json.loads((self.root / 'hoofprint-observation.json').read_text())

    def open_form(self, query, context='Local files'):
        self.act('open')
        self.wait_active(context)
        self.click(context)
        self.act('reform-catalog-proof')
        self.field('', query, 'Search documents')
        self.wait_active('Open')
        self.click('Open')


def verify(client, config, migrated):
    client.until(lambda: client.state('misc').get('loaded'), 'integrated fixture did not join')
    client.act('map', 'misc')
    before = client.map()
    assert before['preferences']['zoomMaximum'] == 32 and before['skyweave$scale'] > .75, before
    live_connection = before['connectionIdentity']
    initial = digest(config)
    if migrated:
        client.open_form('skyweave_compat:files/maps/hoofprint/hoofprint_zoom')
        client.wait_active('Maximum zoom')
        client.click('Maximum zoom')
        client.until(lambda: any(w.get('value') == '32' for w in client.state().get('widgets', [])),
                     'the authored bare JSON did not load in the shared form')
    else:
        client.act('open')
        client.field('', 'Hoofprint', 'Search settings…')
        client.wait_active('Hoofprint')
        client.click('Hoofprint')
    save = 'Save and apply' if migrated else 'Save changes'
    client.field('32', '0.75')
    client.wait_active(save)
    assert digest(config) == initial, 'staging changed the source'
    client.click(save)
    saved = client.until(lambda: digest(config) != initial and json.loads(config.read_text()),
                         'the native save did not persist')
    receipt = None
    if migrated:
        client.act('reform-receipt')
        receipt = json.loads((client.root / 'reform-receipt.json').read_text())
        assert receipt['persistence'] == 'SAVED' and receipt['application'] == 'APPLIED', receipt
        assert Path(receipt['source']).resolve() == config.resolve(), receipt
        assert receipt['savedRevision'] == 'sha256:' + digest(config), receipt
        client.act('key', key=256)
        client.act('key', key=256)
    client.act('key', key=256)
    after = client.map()
    assert after['connectionIdentity'] == live_connection, after
    assert after['preferences'] == saved and saved['zoomMaximum'] == .75, after
    assert after['skyweave$scale'] == .75 and after['nativeScaleFactor'] == .75 / after['windowGuiScale'], after
    assert all(before[name] == after[name] for name in ('centreX', 'centreZ', 'dim')), after
    if migrated:
        assert after['screenIdentity'] == before['screenIdentity'], (before, after)
    return dict(before=before, after=after, saved=saved, beforeSha256=initial, savedSha256=digest(config),
                receipt=receipt, loadedMods=client.state()['loadedMods'])


def verify_terrain_pair(client, config):
    client.until(lambda: client.state('misc').get('loaded'), 'integrated fixture did not join')
    generation = config / 'skyweave_compat-generation.json'
    shapes = config / 'skyweave_compat-shapes.json'
    before = {path.name: digest(path) for path in (generation, shapes)}
    original = json.loads(generation.read_text())
    client.open_form('skyweave_compat:files/world/sky_archipelago/generation')
    client.wait_active('Cluster spacing')
    client.click('Cluster spacing')
    previous = str(original['spacing'])
    client.until(lambda: any(w.get('value') == previous for w in client.state().get('widgets', [])),
                 'the authored generation profile did not load in the shared form')
    client.field(previous, str(original['spacing'] + 100))
    review_action = 'Review Generation and shapes…'
    client.wait_active(review_action)
    assert digest(generation) == before[generation.name]
    client.click(review_action)
    submit = 'Save reviewed documents'
    client.wait_active(submit)
    client.act('reform-batch-proof')
    receipt = json.loads((client.root / 'reform-batch-proof.json').read_text())
    assert receipt == {'intents': {'skyweave_compat:generation': 'COMMIT', 'skyweave_compat:shapes': 'CHECK'},
                       'valid': True}, receipt
    client.click(submit)
    saved = client.until(lambda: digest(generation) != before[generation.name] and json.loads(generation.read_text()),
                         'the reviewed generation profile did not save')
    assert saved == {**original, 'spacing': original['spacing'] + 100}, saved
    assert digest(shapes) == before[shapes.name]
    return dict(before=before, after={path.name: digest(path) for path in (generation, shapes)},
                reviewed=receipt, spacingBefore=original['spacing'], spacingAfter=saved['spacing'])


def verify_integrated_policy(client, config):
    client.until(lambda: client.state('misc').get('loaded'), 'integrated fixture did not join')
    path = config / 'skyweave_compat-wayfinder.json'
    before = json.loads(path.read_text())
    original_hash = digest(path)

    def effective():
        response = client.act('server-proof')
        sequence = response['sequence']
        return client.until(lambda: (state if state.get('sequence') == sequence else None)
                            if (state := client.state().get('serverProof', {})) else None,
                            'actual integrated policy was not observed')

    original = effective()
    assert before == original['wayfinder'], original
    client.open_form('skyweave_compat:server/wayfinder', 'Running singleplayer server')
    client.wait_active('Query work per tick')
    client.click('Query work per tick')
    client.until(lambda: any(w.get('value') == str(before['stepsPerTick']) for w in client.state().get('widgets', [])),
                 'integrated re:form did not load the saved Wayfinder policy')
    client.field(str(before['stepsPerTick']), '128')
    client.wait_active('Save and apply')
    assert digest(path) == original_hash and effective()['wayfinder'] == before
    client.click('Save and apply')
    saved = client.until(lambda: digest(path) != original_hash and json.loads(path.read_text()),
                         'native integrated settings did not save')
    client.act('reform-receipt')
    receipt = json.loads((client.root / 'reform-receipt.json').read_text())
    assert receipt['persistence'] == 'SAVED' and receipt['application'] == 'APPLIED', receipt
    assert receipt['savedRevision'] == 'sha256:' + digest(path)
    after = effective()
    assert saved == {**before, 'stepsPerTick': 128} == after['wayfinder'], after
    assert original['serverIdentity'] == after['serverIdentity'] and original['revisionIdentity'] != after['revisionIdentity']
    return dict(before=original, after=after, saved=saved, receipt=receipt, originalSha256=original_hash)


def verify_startup_property(client, config):
    client.until(lambda: client.state('misc').get('loaded'), 'integrated fixture did not join')
    path = config / 'skyweave_compat-features.properties'
    before = path.read_bytes()
    client.open_form('skyweave_compat:features/navigation.wayfinder')
    client.wait_active('true')
    client.click('true')
    client.wait_active('Save for restart')
    assert path.read_bytes() == before
    client.click('Save for restart')
    after = client.until(lambda: path.read_bytes() if path.read_bytes() != before else None,
                         'native startup property did not save')
    client.act('reform-receipt')
    receipt = json.loads((client.root / 'reform-receipt.json').read_text())
    assert receipt['persistence'] == 'SAVED' and receipt['application'] == 'STAGED', receipt
    assert receipt['savedRevision'] == 'sha256:' + digest(path)
    assert after == b'# Retained startup comment\r\n  navigation.wayfinder=false\r\n', after
    assert 'navigation.wayfinder' in client.state()['active']
    return dict(before=before.decode(), after=after.decode(), receipt=receipt, remainsActive=True)


def verify_native_preferences(client, config):
    client.until(lambda: client.state('misc').get('loaded'), 'integrated fixture did not join')

    def native():
        client.act('native-preferences-proof')
        return json.loads((client.root / 'native-preferences-proof.json').read_text())

    before = native()
    client.open_form('skyweave_compat:files/machines/redstone_link_gui/link_gui_client')
    client.wait_active(before['clientClickMode'])
    client.click(before['clientClickMode'])
    client.wait_active('Save native settings')
    assert native() == before
    client.click('Save native settings')
    changed = client.until(lambda: state if (state := native()) != before else None,
                           'the real registered client reader did not observe its native save')
    client.act('reform-receipt')
    receipt = json.loads((client.root / 'reform-receipt.json').read_text())
    assert receipt['persistence'] == 'SAVED' and receipt['application'] == 'STAGED', receipt
    source = client.root / 'config/createredstonelinkgui-client.toml'
    assert receipt['savedRevision'] == 'sha256:' + digest(source)
    catalog = json.loads((client.root / 'reform-catalog-proof.json').read_text())
    assert {owner['route'] for owner in catalog['owners']} == set(catalog['expectedRoutes']), catalog
    assert catalog['documents'] == len(catalog['expectedRoutes']) and catalog['placements'] > 120, catalog
    assert all(not owner['opened'] for owner in catalog['owners']), catalog
    return dict(before=before, after=changed, receipt=receipt, reader='Native ClientConfig.CLICK_MODE.get', catalog=catalog)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ('java', 'clean', 'output', 'compat', 'probe'):
        parser.add_argument('--' + name, required=True, type=Path)
    parser.add_argument('--reform', type=Path)
    scenario = parser.add_mutually_exclusive_group()
    scenario.add_argument('--terrain-pair', action='store_true')
    scenario.add_argument('--integrated-policy', action='store_true')
    scenario.add_argument('--startup-property', action='store_true')
    scenario.add_argument('--native-preferences', action='store_true')
    parser.add_argument('--mod', action='append', required=True, type=Path)
    args = parser.parse_args()
    if args.output.exists():
        parser.error('Choose a new receipt path')
    if (args.terrain_pair or args.integrated_policy or args.startup_property or args.native_preferences) and not args.reform:
        parser.error('This settings scenario requires a re:form artifact')
    artifacts = [args.compat, args.probe, *args.mod]
    if args.reform:
        artifacts.append(args.reform)
    evidence = {'artifacts': {path.name: digest(path) for path in artifacts},
                'connectorCleanSha256': digest(args.clean),
                'scenario': ('native-preferences' if args.native_preferences else 'startup-property' if args.startup_property else 'terrain-pair' if args.terrain_pair
                             else 'integrated-policy' if args.integrated_policy
                             else 'migrated' if args.reform else 'baseline')}
    started = time.monotonic()
    with tempfile.TemporaryDirectory(prefix='skyweave-reform-client-') as directory:
        root = Path(directory)
        (root / 'mods').mkdir()
        for path in artifacts:
            shutil.copy2(path, root / 'mods' / path.name)
        for marker in ('config-review-fixture.json', 'misc-review-fixture.json'):
            (root / marker).write_text('{}\n')
        (root / 'options.txt').write_text('onboardAccessibility:false\nguiScale:2\nfullscreen:false\n'
                                         'overrideWidth:1024\noverrideHeight:768\n'
                                         'renderDistance:2\nsimulationDistance:2\nmaxFps:40\n')
        config = root / 'config/skyweave/skyweave_compat-hoofprint.json'
        config.parent.mkdir(parents=True)
        config.write_text('{"zoomMinimum":0.125,"zoomMaximum":32,"zoomInitial":0,"zoomStep":1.25}\n')
        if args.startup_property:
            (config.parent / 'skyweave_compat-features.properties').write_bytes(
                b'# Retained startup comment\r\n  navigation\\.wayfinder = tr\\\r\n  ue\r\n')
        log = root / 'console.log'
        command = packaged_command(root, args.java, args.clean.resolve(strict=True))
        with log.open('wb') as console:
            process = subprocess.Popen(command, cwd=root, stdout=console, stderr=subprocess.STDOUT)
            client = Client(root, process)
            try:
                if args.terrain_pair:
                    evidence['observations'] = verify_terrain_pair(client, config.parent)
                elif args.integrated_policy:
                    evidence['observations'] = verify_integrated_policy(client, config.parent)
                elif args.startup_property:
                    evidence['observations'] = verify_startup_property(client, config.parent)
                elif args.native_preferences:
                    evidence['observations'] = verify_native_preferences(client, config.parent)
                else:
                    evidence['observations'] = verify(client, config, bool(args.reform))
                client.act('quit')
                if process.wait(timeout=20):
                    raise RuntimeError('Native client exit failed')
                evidence['status'] = 'PASS'
            except Exception as failure:
                evidence.update(status='FAIL', error=str(failure), state=client.state(), misc=client.state('misc'))
                raise
            finally:
                if process.poll() is None:
                    process.terminate()
                    try:
                        process.wait(timeout=5)
                    except subprocess.TimeoutExpired:
                        process.kill()
                        process.wait()
                if evidence.get('status') != 'PASS':
                    with log.open('rb') as stream:
                        stream.seek(max(0, log.stat().st_size - 12500))
                        evidence['consoleTail'] = stream.read().decode('utf-8', errors='replace')
                evidence.update(exitCode=process.returncode, seconds=round(time.monotonic() - started, 3),
                                command=[Path(command[0]).name, *command[1:]])
                args.output.write_text(json.dumps(evidence, indent=2) + '\n')


def stop(_signum, _frame):
    raise InterruptedError('Fixture stopped on loss of desktop access')


if __name__ == '__main__':
    signal.signal(signal.SIGTERM, stop)
    signal.signal(signal.SIGINT, stop)
    main()
