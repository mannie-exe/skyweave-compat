#!/usr/bin/env python3
"""Observe real IPN callbacks through its native dispatcher in a disposable client."""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import signal
import subprocess
import sys
import tempfile

from client_bootstrap import argument_file, connector_bootstrap, packaged_bootstrap
from reform_probe import stop


def run(args):
    if args.output.exists():
        raise ValueError('Choose a new evidence directory')
    args.output.mkdir(parents=True)
    artifacts = [path.resolve(strict=True) for path in args.mod]
    report = {'status': 'FAIL', 'expected': args.expected,
              'invocation': sys.argv, 'probeScriptSha256': hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
              'artifacts': {path.name: hashlib.sha256(path.read_bytes()).hexdigest() for path in artifacts}}
    with tempfile.TemporaryDirectory(prefix='skyweave-ipn-review-') as temporary:
        root = Path(temporary)
        (root / 'mods').mkdir()
        for artifact in artifacts:
            target = root / 'mods' / artifact.name
            if target.exists():
                raise ValueError('Duplicate staged artifact filename: ' + artifact.name)
            shutil.copy2(artifact, target)
            if hashlib.sha256(target.read_bytes()).hexdigest() != report['artifacts'][artifact.name]:
                raise RuntimeError('Artifact changed while staging: ' + artifact.name)
        (root / 'ipn-review-fixture.json').write_text('{}\n')
        if args.features:
            source = args.features.resolve(strict=True)
            target = root / 'config/skyweave/skyweave_compat-features.properties'
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(source, target)
            report['featureSelectionSha256'] = hashlib.sha256(target.read_bytes()).hexdigest()
        for source, relative in ((args.ipn_settings, 'config/inventoryprofilesnext/inventoryprofiles.json'),
                                 (args.mouse_tweaks, 'config/MouseTweaks.cfg')):
            if source is None:
                continue
            target = root / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(source.resolve(strict=True), target)
            report.setdefault('settingsInputs', {})[relative] = hashlib.sha256(target.read_bytes()).hexdigest()
        (root / 'options.txt').write_text('renderDistance:2\nsimulationDistance:2\nguiScale:2\nmaxFps:30\npauseOnLostFocus:false\n')
        vm, program = packaged_bootstrap(root)
        report['connectorInput'] = connector_bootstrap(vm, args.connector_clean)
        vm.extend(['-Xms512M', '-Xmx' + args.heap, '-Dskyweave_compat.probe.ipn=true', '-Dskyweave_compat.probe.modifiers=true'])
        if args.extended:
            vm.append('-Dskyweave_compat.probe.ipn.extended=true')
        argument_file(root / 'vm.args', vm)
        argument_file(root / 'program.args', program)
        command = [str(args.java.resolve(strict=True)), '@classpath.args', '@vm.args',
                   'net.neoforged.devlaunch.Main', '@program.args']
        report['command'] = command
        def boot(log_name):
            with (args.output / log_name).open('wb') as console:
                process = subprocess.Popen(command, cwd=root, stdout=console, stderr=subprocess.STDOUT)
                try:
                    code = process.wait(timeout=150)
                finally:
                    if process.poll() is None:
                        process.terminate()
                        try:
                            process.wait(timeout=15)
                        except subprocess.TimeoutExpired:
                            process.kill()
                            process.wait(timeout=15)
                    report.setdefault('bootExits', {})[log_name] = process.returncode
            result = root / 'ipn-review-result.json'
            if not result.is_file():
                raise RuntimeError(f'Client exited {code} before writing its native report; inspect {log_name}')
            return code, json.loads(result.read_text())
        try:
            report['exit'], result = boot('console.log')
            report['native'] = result
            assert report['exit'] == 0 and result['status'] == 'OBSERVED', result
            assert result['outsideTextReloads'] == 1, result
            assert result['chatText'] == 'ry' and result['chatScreenRetained'] and result['pressedKeysEmpty'], result
            assert result['chatReloads'] == (1 if args.expected == 'baseline' else 0), result
            if args.extended:
                checks = result['extended']
                denied = 0 if args.expected == 'protected' else 1
                for key in ('nestedFocusedReloads', 'directFocusedReloads', 'typedPrefixThenCloseReloads',
                            'prefixReleasedFirstReloads', 'primaryReleasedFirstReloads'):
                    assert checks[key] == denied, (key, checks)
                for key in ('nestedUnfocusedReloads', 'freshAfterCloseReloads', 'freshReleaseBindingReloads'):
                    assert checks[key] == 1, (key, checks)
                assert checks['unrelatedLibraryBindingActivated'] and checks['configurationListenerOutsideToggled'], checks
                assert checks['configurationListenerTextToggled'] is (args.expected == 'baseline'), checks
                assert checks['nativeCapturedKeys'] == [296, 297] and checks['nativeCaptureFinished'], checks
                assert set(checks['nativeCapturedModifierMouse']) == {340, -98} and checks['nativeModifierMouseCaptureFinished'], checks
                assert checks['nativeEscapeUnbound'] and checks['pressedKeysEmpty'], checks
                assert checks['focusLossReleaseReloads'] == denied, checks
                editors = checks['editors']
                for key in ('anvilFocusedReloads',):
                    assert editors[key] == denied, (key, editors)
                assert editors['ipnConfigSearchReloads'] == 0, editors
                if not editors['recipeBookReplaced']:
                    assert editors['recipeFocusedReloads'] == denied and editors['staleSlotTextMouseReloads'] == denied, editors
                    assert editors['recipeUnfocusedReloads'] == 1, editors
                    assert editors['slotRendered'] and editors['slotMouseReloads'] == 1, editors
                    assert editors['nativeMiddleSortsWithRecipeFocus'] == (1 if args.expected == 'protected' else 0), editors
                else:
                    assert editors.get('emiSearchFocused'), editors
                assert editors['anvilFocusedNativeEditBox'], editors
                assert editors['reformSearchReloads'] == denied and editors['reformScreenRetained'], editors
                if args.expected == 'protected':
                    assert editors['reformSearchText'] == 'ry', editors
                if 'emiSearchFocused' in editors:
                    assert editors['emiSearchFocused'] and editors['emiFocusedReloads'] == denied, editors
                reconnect = result['reconnect']
                assert reconnect['freshReloads'] == 1 and reconnect['pressedKeysEmpty'], reconnect
                if args.expected == 'protected':
                    assert reconnect['carriedReloads'] == 0, reconnect
                assert result['persistedRebinding'], result
                inventory = result['inventory']
                assert inventory['before'] == inventory['after'] == inventory['serverAfter'] == {'minecraft:dirt': 13, 'minecraft:cobblestone': 11}, inventory
                assert inventory['slotRendered'] and inventory['searchFocusRetained'] and inventory['beforeDirtStacks'] == 2, inventory
                assert inventory['nativeSortCallbacks'] == (1 if args.expected == 'protected' else 0), inventory
                assert inventory['afterDirtStacks'] == (1 if args.expected == 'protected' else 2), inventory
                assert inventory['serverDirtStacks'] == inventory['afterDirtStacks'] and inventory['serverCarriedEmpty'], inventory
                assert inventory['textLetterMouseSorts'] == 0 and inventory['modifierMouseSorts'] == (1 if args.expected == 'protected' else 0), inventory
                assert inventory['beforeShiftHotbarItems'] == 0 and inventory['nativeShiftClickHotbarItems'] > 0, inventory
                assert inventory['afterShiftClick'] == inventory['serverAfter'], inventory
                if inventory['toolbarSortAvailable']:
                    assert inventory['nativeToolbarSorts'] == 1, inventory
                if 'mouseTweaks' in inventory:
                    gestures = inventory['mouseTweaks']
                    assert gestures['nativeWheelCalls'] >= 2 and gestures['nativeRmbDragClicks'] >= 2, gestures
                    assert gestures['restored']['player'] == inventory['serverAfter'] and not gestures['restored']['chest'] and not gestures['restored']['carried'], gestures
                vm.append('-Dskyweave_compat.probe.ipn.reopen=true')
                argument_file(root / 'vm.args', vm)
                (root / 'ipn-review-result.json').unlink()
                report['reopenExit'], reopened = boot('reopen-console.log')
                report['reopen'] = reopened
                assert report['reopenExit'] == 0 and reopened['status'] == 'OBSERVED', reopened
                assert reopened['reloadBinding'] == [296, 297], reopened
                assert reopened['outsideTextReloads'] == 1 and reopened['chatReloads'] == denied, reopened
                assert reopened['pressedKeysEmpty'], reopened
            report['status'] = 'PASS'
        except BaseException as failure:
            report['error'] = str(failure)
            raise
        finally:
            (args.output / 'report.json').write_text(json.dumps(report, indent=2) + '\n')
            log = root / 'logs/latest.log'
            if log.is_file():
                shutil.copy2(log, args.output / 'latest.log')
            for crash in (root / 'crash-reports').glob('*.txt'):
                target = args.output / 'crash-reports'
                target.mkdir(exist_ok=True)
                shutil.copy2(crash, target / crash.name)
    return report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--java', type=Path, required=True)
    parser.add_argument('--mod', type=Path, action='append', required=True,
                        help='Exact product, re:form, probe, IPN, libIPN, and Kotlin dependency JARs')
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--expected', choices=('baseline', 'protected'), required=True)
    parser.add_argument('--heap', choices=('2G', '4G', '6G'), default='2G', help='Disposable client heap; use a measured larger fixture only for the full mod set')
    parser.add_argument('--connector-clean', type=Path, help='Clean Minecraft 1.21.1 NeoForm rename output for Connector development bootstrap')
    parser.add_argument('--features', type=Path, help='Read-only authored startup selections to copy into the disposable fixture')
    parser.add_argument('--ipn-settings', type=Path, help='Read-only authored IPN settings for this disposable fixture')
    parser.add_argument('--mouse-tweaks', type=Path, help='Read-only authored Mouse Tweaks settings for this disposable fixture')
    parser.add_argument('--extended', action='store_true', help='Also observe nested focus, configuration listener, release, capture, and window transitions')
    args = parser.parse_args()
    result = run(args)
    print(json.dumps({'status': result['status'], 'report': str(args.output / 'report.json')}))


if __name__ == '__main__':
    signal.signal(signal.SIGTERM, stop)
    signal.signal(signal.SIGINT, stop)
    main()
