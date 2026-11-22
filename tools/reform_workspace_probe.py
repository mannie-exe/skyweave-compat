#!/usr/bin/env python3
"""Exercise actual Compat menus twice without an optional mod, world or personal instance."""
import argparse
import json
from pathlib import Path
import shutil
import subprocess
import tempfile
import zipfile

from reform_probe import Client, digest, packaged_command

LONG_LABEL = 'Wayfinder for distant landscape targeting with a deliberately long translated setting name'


def observed(client, operation, filename, ready):
    response = client.request(operation)
    if response.get('error'):
        return None
    source = client.root / filename
    value = json.loads(source.read_text()) if source.is_file() else {}
    return value if ready(value) else None


def editor(client):
    return observed(client, 'reform-editor-proof', 'reform-editor-proof.json',
                    lambda value: value.get('availability') == 'READY')


def information(client):
    return observed(client, 'reform-information-proof', 'reform-information-proof.json',
                    lambda value: value.get('available'))


def open_information(client, field='navigation.wayfinder', expected_label=None):
    client.act('reform-focus-field', field=field)
    client.act('snapshot')
    rows = client.state()['widgets']
    focused = [widget for widget in rows if widget.get('focused')]
    if len(focused) != 1:
        raise AssertionError('The requested native field did not receive focus')
    matches = [index for index, widget in enumerate(widget for widget in rows if widget['label'] == 'i')
               if widget['y'] == focused[0]['y']]
    if len(matches) != 1:
        raise AssertionError('The native information action is not on the requested field row')
    assert [widget for widget in rows if widget['label'] == 'i'][matches[0]]['bold']
    narrated = None
    if expected_label is not None:
        client.act('key', key=258)
        focused_info = [widget for widget in client.state()['widgets'] if widget.get('focused')]
        assert len(focused_info) == 1 and focused_info[0]['label'] == 'i'
        client.act('narration')
        narrated = json.loads((client.root / 'configuration-narration.json').read_text())
        assert any('About ' + expected_label in row['text'] for row in narrated)
        assert client.act('key', key=257)['keyAccepted']
    else:
        client.act('click', label='i', index=matches[0])
    result = client.until(lambda: information(client), 'Actual information did not become available')
    if narrated is not None:
        result['nativeButtonNarration'] = narrated
        result['nativeKeyboardActivation'] = True
    return result


def capture(client, output, name):
    client.act('snapshot')
    client.act('reform-capture', name=name)
    source = client.root / 'screenshots' / name
    client.until(lambda: source.is_file() and source.stat().st_size > 100, 'Native frame is missing')
    shutil.copy2(source, output / name)
    return {'file': name, 'sha256': digest(source)}


def retained_workspace(client, expected):
    """Use native history/filter/search controls while retaining the same authored draft."""
    client.navigate('skyweave_compat:features/interface.main_menu_identity')
    alias = client.ready_editor()
    assert alias['context'] == expected['context'] and alias['draft'] == expected['draft']
    client.wait_active('Back')
    client.click('Back')
    client.wait_active('Forward')
    client.click('Forward')
    client.navigate('skyweave_compat:features/navigation.wayfinder')
    for current, following in [('All', 'Modified'), ('Modified', 'Invalid'),
                               ('Invalid', 'Restart'), ('Restart', 'All')]:
        client.click('Show: ' + current)
        client.wait_active('Show: ' + following)
    previous = ''
    samples = []
    for query in ['navigation.wayfinder', 'Wayfinder', 'maps', 'navigation.wayfinder', '']:
        changed = client.field(previous, query, 'Search settings')
        samples.append({'query': query, 'synchronousInputNanos': changed['fieldEditNanos']})
        previous = query
    client.navigate('skyweave_compat:features/navigation.wayfinder')
    restored = client.ready_editor()
    assert restored['context'] == expected['context'] and restored['draft'] == expected['draft']
    return {'sameOwnerDraft': True, 'nativeHistory': True, 'filters': ['All', 'Modified', 'Invalid', 'Restart'],
            'searchSamples': samples,
            'costScope': 'Synchronous native EditBox responder only; excludes later work and is not frame-time or full-pack performance evidence'}


def narrow_information(client, output, name):
    before = client.state()
    client.act('resize', width=427, height=320)
    state = client.until(lambda: state if (state := client.act('snapshot'))['width'] <= 427 else None,
                         'The actual native window did not become narrow')
    details = information(client)
    assert details and details['key'] == 'navigation.wayfinder'
    result = {'width': state['width'], 'height': state['height'], 'capture': capture(client, output, name)}
    client.act('resize', width=854, height=480)
    client.until(lambda: client.act('snapshot')['width'] == before['width'], 'The native window did not restore')
    client.act('scale', value=4)
    scaled = client.until(lambda: value if (value := client.act('snapshot'))['width'] < before['width'] else None,
                          'The native high-GUI layout did not reflow')
    assert information(client)['key'] == 'navigation.wayfinder'
    result['highGui'] = {'width': scaled['width'], 'height': scaled['height'], 'scale': scaled['scale'],
                         'capture': capture(client, output, 'reform-compat-high-gui-information.png')}
    client.act('scale', value=2)
    client.until(lambda: client.act('snapshot')['width'] == before['width'], 'The native GUI scale did not restore')
    return result


def loaded_choices(client, features, loaded, output, name):
    previous = features.read_bytes()
    client.click('More…')
    client.reveal('Restore loaded choices')
    frame = capture(client, output, name)
    client.click('Restore loaded choices')
    restored = client.until(lambda: value if (value := editor(client)) and
                            not value['busy'] and value['valid'] and
                            value['draft'].get('navigation.wayfinder', True) is loaded else None,
                            'The actual bulk action did not restore the loaded startup choice')
    assert features.read_bytes() == previous
    client.click('Revert')
    client.until(lambda: value if (value := editor(client)) and not value['dirty'] else None,
                 'Revert did not restore the captured saved baseline')
    assert features.read_bytes() == previous
    return {'loadedChoice': loaded, 'sourceUnchanged': True, 'restoredDraft': restored, 'capture': frame}


def run(java, artifacts, output, long_labels=False):
    if output.exists():
        raise ValueError('Choose a new receipt directory')
    output.mkdir(parents=True)
    report = {'status': 'FAIL', 'scope': 'Actual minimum Compat Configure factory, native controls and saved JVM reopen; no world or personal operations',
              'artifacts': {path.name: digest(path) for path in artifacts}, 'boots': [], 'longLabels': long_labels,
              'tools': {path.name: digest(path) for path in
                        (Path(__file__), Path(__file__).with_name('reform_probe.py'),
                         Path(__file__).with_name('client_bootstrap.py'))}}
    process = None
    with tempfile.TemporaryDirectory(prefix='skyweave-settings-workspace-') as temporary:
        game = Path(temporary)
        (game / 'mods').mkdir()
        for artifact in artifacts:
            shutil.copy2(artifact, game / 'mods' / artifact.name)
        (game / 'config-review-fixture.json').write_text('{}\n')
        resource_options = ''
        if long_labels:
            packs = game / 'resourcepacks'
            packs.mkdir()
            resource_pack = packs / 'reform-label-review.zip'
            with zipfile.ZipFile(resource_pack, 'w') as archive:
                archive.writestr('pack.mcmeta', json.dumps({'pack': {'pack_format': 34, 'description': 'Owned settings label review fixture'}}))
                archive.writestr('assets/skyweave_compat/lang/en_us.json', json.dumps({
                    'skyweave_compat.configuration.feature.navigation.wayfinder': LONG_LABEL,
                    'skyweave_compat.settings.mod.skyweave_compat': 'Skyweave Compatibility Patches with a long localized navigation name'}))
            report['labelFixtureSha256'] = digest(resource_pack)
            resource_options = 'resourcePacks:' + json.dumps(['vanilla', 'mod_resources', 'file/reform-label-review.zip']) + '\n'
        (game / 'options.txt').write_text('onboardAccessibility:false\nguiScale:2\nfullscreen:false\n'
                                        'overrideWidth:1100\noverrideHeight:760\n'
                                        'renderDistance:2\nsimulationDistance:2\nmaxFps:40\n' + resource_options)
        features = game / 'config/skyweave/skyweave_compat-features.properties'
        features.parent.mkdir(parents=True)
        features.write_bytes(b'# Retained native workspace fixture\r\nnavigation\\.wayfinder = tr\\\r\n ue\r\n')
        try:
            for reopen in (False, True):
                for name in ('config-review-control.json', 'config-review-state.json', 'reform-catalog-proof.json',
                             'reform-editor-proof.json', 'reform-information-proof.json', 'reform-receipt.json',
                             'reform-lifetime-proof.json', 'reform-operation-proof.json'):
                    (game / name).unlink(missing_ok=True)
                boot = {'reopen': reopen, 'status': 'FAIL'}
                report['boots'].append(boot)
                command = packaged_command(game, java, gameplay=False)
                with (output / ('reopen.log' if reopen else 'first.log')).open('wb') as console:
                    process = subprocess.Popen(command, cwd=game, stdout=console, stderr=subprocess.STDOUT)
                    client = Client(game, process)
                    client.until(lambda: client.state().get('pid') == process.pid and
                                 client.state().get('screen', '').endswith('ClientSettingsMenus$ContextScreen'),
                                 'The actual Configure entry did not arrive')
                    boot['loadedMods'] = client.state()['loadedMods']
                    client.wait_active('Local files')
                    client.click('Local files')
                    catalog = client.proof('reform-catalog-proof', 'reform-catalog-proof.json')
                    boot['catalog'] = catalog
                    assert catalog['documents'] == len(catalog['expectedRoutes'])
                    client.navigate('skyweave_compat:features/navigation.wayfinder')
                    boot['initialEditor'] = client.until(lambda: editor(client), 'Startup owner is unavailable')
                    first = open_information(client, expected_label=LONG_LABEL if long_labels else 'Wayfinder')
                    boot['initialInformation'] = first
                    facts = {fact['label']: fact['value'] for fact in first['facts']}
                    assert first['key'] == 'navigation.wayfinder' and first['purpose'] and first['related'] >= 2
                    assert {'Loaded startup choice', 'Draft choice', 'Last observed next-launch choice', 'Physical scope'} <= facts.keys()
                    assert not any(widget['label'] == 'Copy key' for widget in client.state()['widgets'])
                    client.reveal('Show technical details')
                    client.click('Show technical details')
                    assert any(widget['label'] == 'Copy key' for widget in client.state()['widgets'])
                    client.reveal('Hide technical details')
                    client.click('Hide technical details')
                    boot['captures'] = [capture(client, output, 'reform-compat-' + ('reopen' if reopen else 'first') + '-information.png')]
                    client.reveal('Close information')
                    client.click('Close information')
                    if not reopen:
                        previous = features.read_bytes()
                        client.act('reform-focus-field', field='navigation.wayfinder')
                        client.act('reform-field-click', field='navigation.wayfinder')
                        draft = client.until(lambda: value if (value := editor(client)) and value['dirty'] else None,
                                             'The actual startup draft did not change')
                        assert draft['draft']['navigation.wayfinder'] is False and features.read_bytes() == previous
                        boot['retainedWorkspace'] = retained_workspace(client, draft)
                        assert features.read_bytes() == previous
                        saved_actions = [widget['label'] for widget in client.state()['widgets']
                                         if widget['active'] and widget['label'].startswith('Save')]
                        assert len(saved_actions) == 1
                        client.click(saved_actions[0])
                        client.until(lambda: features.read_bytes() != previous, 'Native Save did not persist')
                        receipt = client.proof('reform-receipt', 'reform-receipt.json')
                        assert receipt['persistence'] == 'SAVED' and receipt['application'] == 'STAGED'
                        boot['receipt'] = receipt
                        client.until(lambda: value if (value := editor(client)) and not value['dirty'] else None,
                                     'The actual source acknowledgement did not settle')
                        saved = open_information(client)
                        boot['savedInformation'] = saved
                        values = {fact['label']: fact['value'] for fact in saved['facts']}
                        assert values['Loaded startup choice'].upper().startswith('ON')
                        assert values['Last observed next-launch choice'].upper().startswith('OFF')
                        boot['narrowInformation'] = narrow_information(client, output, 'reform-compat-narrow-information.png')
                        client.reveal('Close information')
                        client.click('Close information')
                        boot['loadedChoices'] = loaded_choices(client, features, True, output, 'reform-compat-loaded-choices.png')
                    else:
                        assert facts['Loaded startup choice'].upper().startswith('OFF')
                        assert facts['Running adaptation'] == 'Disabled'
                        boot['loadedChoices'] = loaded_choices(client, features, False, output, 'reform-compat-reopen-loaded-choices.png')

                    client.navigate('skyweave_compat:features/navigation.wayfinder_dh')
                    client.until(lambda: editor(client), 'Optional startup field is unavailable')
                    missing = open_information(client, 'navigation.wayfinder_dh')
                    boot['missingDependency'] = missing
                    missing_facts = {fact['label']: fact['value'] for fact in missing['facts']}
                    assert missing_facts['Running adaptation'] != 'Active'
                    assert any(fact['label'].startswith('Requires ') for fact in missing['facts'])
                    client.reveal('Close information')
                    client.click('Close information')

                    missing_file = game / 'config/skyweave/skyweave_compat-hoofprint.json'
                    assert not missing_file.exists()
                    client.navigate('skyweave_compat:settings/files/file/skyweave_compat-hoofprint.json', ('Start…',))
                    refusal = client.until(lambda: value if (value := client.proof('reform-operation-proof', 'reform-operation-proof.json'))['terminal'] else None,
                                           'Missing-file inspection did not settle')
                    assert refusal['state'] == 'REFUSED' and not refusal['hasResult'] and not missing_file.exists()
                    boot['missingFileRefusal'] = refusal

                    client.navigate('skyweave_compat:settings/files/status', ('Start…',))
                    status = client.until(lambda: value if (value := client.proof('reform-operation-proof', 'reform-operation-proof.json'))['terminal'] else None,
                                          'Explicit local file status did not settle')
                    assert status['state'] == 'COMPLETED' and status['hasResult']
                    assert len(status['result']['details']) == len(catalog['expectedRoutes']) - 1
                    boot['fileStatus'] = status
                    client.act('key', key=256)
                    client.click('Done')
                    closed = client.proof('reform-lifetime-proof', 'reform-lifetime-proof.json')
                    assert closed['open'] is False
                    boot['closed'] = closed
                    boot['sourceSha256'] = digest(features)
                    client.act('quit')
                    assert process.wait(timeout=20) == 0
                    boot['status'] = 'PASS'
                    process = None
            report['status'] = 'PASS'
        except BaseException as failure:
            report['error'] = str(failure)
            if process is not None:
                report['lastState'] = client.state()
            raise
        finally:
            if process is not None and process.poll() is None:
                process.terminate()
                try:
                    process.wait(timeout=8)
                except subprocess.TimeoutExpired:
                    process.kill()
                    process.wait()
            report['ownedProcessTerminal'] = process is None or process.poll() is not None
            (output / 'result.json').write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps({'status': report['status'], 'report': str(output / 'result.json')}))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ('java', 'compat', 'probe', 'reform', 'output'):
        parser.add_argument('--' + name, type=Path, required=True)
    parser.add_argument('--long-labels', action='store_true', help='Use an owned temporary language pack for long-label layout and native narration checks')
    args = parser.parse_args()
    run(args.java, [args.compat, args.probe, args.reform], args.output, args.long_labels)
