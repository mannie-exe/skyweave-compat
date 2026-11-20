#!/usr/bin/env python3
"""Run the packaged Compat settings client against an isolated Hoofprint world."""
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


def verify(client, config, migrated):
    client.until(lambda: client.state('misc').get('loaded'), 'integrated fixture did not join')
    client.act('map', 'misc')
    before = client.map()
    assert before['preferences']['zoomMaximum'] == 32 and before['skyweave$scale'] > .75, before
    live_connection = before['connectionIdentity']
    initial = digest(config)
    client.act('open')
    client.field('', 'Hoofprint', 'Search settings…')
    client.wait_active('Hoofprint')
    client.click('Hoofprint')
    if migrated:
        client.click('Edit settings')
        client.until(lambda: any(w.get('value') == '32' for w in client.state().get('widgets', [])),
                     'the authored bare JSON did not load in the shared form')
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
    after = client.map()
    assert after['connectionIdentity'] == live_connection, after
    assert after['preferences'] == saved and saved['zoomMaximum'] == .75, after
    assert after['skyweave$scale'] == .75 and after['nativeScaleFactor'] == .75 / after['windowGuiScale'], after
    assert all(before[name] == after[name] for name in ('centreX', 'centreZ', 'dim')), after
    if migrated:
        assert after['screenIdentity'] == before['screenIdentity'], (before, after)
    return dict(before=before, after=after, saved=saved, beforeSha256=initial, savedSha256=digest(config),
                receipt=receipt, loadedMods=client.state()['loadedMods'])


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ('java', 'clean', 'output', 'compat', 'probe'):
        parser.add_argument('--' + name, required=True, type=Path)
    parser.add_argument('--reform', type=Path)
    parser.add_argument('--mod', action='append', required=True, type=Path)
    args = parser.parse_args()
    if args.output.exists():
        parser.error('Choose a new receipt path')
    artifacts = [args.compat, args.probe, *args.mod]
    if args.reform:
        artifacts.append(args.reform)
    evidence = {'artifacts': {path.name: digest(path) for path in artifacts},
                'connectorCleanSha256': digest(args.clean), 'scenario': 'migrated' if args.reform else 'baseline'}
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
        log = root / 'console.log'
        command = packaged_command(root, args.java, args.clean.resolve(strict=True))
        with log.open('wb') as console:
            process = subprocess.Popen(command, cwd=root, stdout=console, stderr=subprocess.STDOUT)
            client = Client(root, process)
            try:
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
