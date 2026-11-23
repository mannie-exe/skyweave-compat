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
LONG_CONTEXT = 'Skyweave: Compatibility Patches — local preferences, installation defaults, and running singleplayer server policies'
TRANSLATED_HELP = 'Translated distant targeting note.'


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
    state = client.act('snapshot')
    measured = client.proof('reform-layout-proof', 'reform-layout-proof.json')
    geometry_file = output / (Path(name).stem + '-layout.json')
    geometry_file.write_text(json.dumps(measured, indent=2) + '\n')
    client.act('reform-capture', name=name)
    source = client.root / 'screenshots' / name
    client.until(lambda: source.is_file() and source.stat().st_size > 100, 'Native frame is missing')
    shutil.copy2(source, output / name)
    # Retain the actual native widget geometry beside the frame. Reachable means the
    # existing native hit/focus-tree observer admitted the widget's center, not that
    # every pixel of a disabled or clipped widget is visible.
    snapshot = output / (Path(name).stem + '-widgets.json')
    snapshot.write_text(json.dumps(state, indent=2) + '\n')
    assert measured['panels'], 'The actual screen exposed no measured settings panel'
    assert all(not panel['intersections'] for panel in measured['panels']), measured
    assert all(not panel['splitStepPairs'] for panel in measured['panels']), measured
    assert all(not panel['mixedRelatedRows'] for panel in measured['panels']), measured
    assert not measured['screenControlIntersections'] and not measured['screenControlsOutsideBounds'], measured
    assert state['screenFocusAttached'], state['focusOwner']
    reachable = [widget for widget in state['widgets'] if widget.get('reachable')]
    return {'file': name, 'sha256': digest(source),
            'measuredLayout': {'file': geometry_file.name, 'sha256': digest(geometry_file)},
            'widgets': {'file': snapshot.name, 'sha256': digest(snapshot)},
            'geometry': {'width': state['width'], 'height': state['height'], 'scale': state['scale'],
                         'reachableControls': len(reachable),
                         'reachableEditBoxes': sum('value' in widget for widget in reachable),
                         'reachableControlRows': len({widget['y'] for widget in reachable})}}


def context_scenes(client, output):
    """Inspect the real shared chooser without entering either owner."""
    original = client.act('snapshot')
    result = []
    for size, width, height, scale in (('wide', 1100, 760, 2), ('narrow', 427, 320, 2),
                                       ('high-gui', 1100, 760, 4)):
        client.act('resize', width=width, height=height)
        client.act('scale', value=scale)
        client.ready_context()
        state = client.act('snapshot')
        choices = {widget['label']: widget['active'] for widget in state['widgets']
                   if widget['label'] in ('Local files', 'Running singleplayer server')}
        assert choices == {'Local files': True, 'Running singleplayer server': False}, choices
        result.append(capture(client, output, 'reform-context-' + size + '.png'))
        proof = client.proof('reform-layout-proof', 'reform-layout-proof.json')
        rows = [row for panel in proof['panels'] for row in panel['contentRows']]
        reasons = [row['help'] for row in rows if row['key'] == 'choice:running_server']
        assert len(reasons) == 1 and 'Open a singleplayer world' in reasons[0], reasons
    client.act('resize', width=original['windowWidth'], height=original['windowHeight'])
    client.act('scale', value=original['scale'])
    client.ready_context()
    return result


def layout_scenes(client, output, catalog, result):
    """Capture matched real documents without changing their values or saving."""
    for suffix in ('generation', 'interiors', 'shapes'):
        matches = [value for value in catalog['placementIds'] if value.endswith('/' + suffix)]
        if len(matches) != 1:
            raise AssertionError(('Expected one authored document placement', suffix, matches))
        client.navigate(matches[0])
        initial = client.until(lambda: value if (value := editor(client)) and
                               value['document'] == 'skyweave_compat:' + suffix else None,
                               'The selected layout document did not become the actual native editor: ' + suffix)
        row = {'document': initial['document'], 'initialEditor': initial, 'captures': []}
        result.append(row)
        state = client.act('snapshot')
        focused = [widget for widget in state['widgets'] if widget.get('focused')]
        assert len(focused) == 1, focused
        if 'value' in focused[0]:
            assert client.act('key', key=263)['keyAccepted'], 'The native input did not accept Left'
        focused = [widget for widget in client.act('snapshot')['widgets'] if widget.get('focused')]
        caret = {key: focused[0][key] for key in ('value', 'cursor', 'selection') if key in focused[0]}
        proof = client.proof('reform-layout-proof', 'reform-layout-proof.json')
        focus_keys = [key for panel in proof['panels'] for key in panel['focusedFieldKeys']]
        assert len(focus_keys) == 1, focus_keys
        row['focus'] = {'fieldKeys': focus_keys, 'input': caret}
        source = client.root / 'config/skyweave' / ('skyweave_compat-' + suffix + '.json')
        source_before = digest(source) if source.is_file() else None
        row['sourceBefore'] = source_before
        for size, width, height, scale in (('wide', 1100, 760, 2), ('narrow', 427, 320, 2),
                                            ('high-gui', 1100, 760, 4)):
            client.act('resize', width=width, height=height)
            client.act('scale', value=scale)
            client.ready_editor()
            row['captures'].append(capture(client, output, 'reform-layout-' + suffix + '-' + size + '.png'))
            focus_state = client.act('snapshot')
            current = [widget for widget in focus_state['widgets'] if widget.get('focused')]
            assert len(current) == 1, current
            assert {key: current[0][key] for key in ('value', 'cursor', 'selection') if key in current[0]} == caret, current
            proof = client.proof('reform-layout-proof', 'reform-layout-proof.json')
            assert [key for panel in proof['panels'] for key in panel['focusedFieldKeys']] == focus_keys, proof
            first_field = {'generation': 'spacing', 'interiors': 'noiseChance', 'shapes': 'enabled'}[suffix]
            purposes = [field['help'] for panel in proof['panels'] for field in panel['rows']
                        if field['key'] == 'skyweave_compat:' + suffix + ':root/f'
                        + str(len(first_field)) + ':' + first_field + '/']
            assert len(purposes) == 1 and purposes[0] and not purposes[0].startswith('skyweave_compat.'), purposes
            retained = client.ready_editor()
            assert (retained['context'] == initial['context'] and retained['draft'] == initial['draft']
                    and retained['dirty'] == initial['dirty']), retained
        row['sourceAfter'] = digest(source) if source.is_file() else None
        assert row['sourceAfter'] == source_before, ('Layout inspection changed the source', row)
    client.act('resize', width=1100, height=760)
    client.act('scale', value=2)
    client.navigate('skyweave_compat:features/navigation.wayfinder')
    client.ready_editor()
    return result


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
    client.act('resize', width=before['windowWidth'], height=before['windowHeight'])
    client.until(lambda: client.act('snapshot')['width'] == before['width'], 'The native window did not restore')
    client.act('scale', value=4)
    scaled = client.until(lambda: value if (value := client.act('snapshot'))['width'] < before['width'] else None,
                          'The native high-GUI layout did not reflow')
    assert information(client)['key'] == 'navigation.wayfinder'
    result['highGui'] = {'width': scaled['width'], 'height': scaled['height'], 'scale': scaled['scale'],
                         'capture': capture(client, output, 'reform-compat-high-gui-information.png')}
    client.act('scale', value=before['scale'])
    client.until(lambda: client.act('snapshot')['width'] == before['width'], 'The native GUI scale did not restore')
    return result


def loaded_choices(client, features, loaded, output, name):
    previous = features.read_bytes()
    client.document_actions()
    client.reveal('Restore loaded choices')
    frame = capture(client, output, name)
    client.click('Restore loaded choices')
    client.until(lambda: client.state().get('screen', '').endswith('ClientEditingWorkflow$Overview'),
                 'The document action did not return to its retained owner')
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


def run(java, artifacts, output, long_labels=False, layouts=False):
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
                    'skyweave_compat.configuration.title': LONG_CONTEXT,
                    'skyweave_compat.configuration.feature.navigation.wayfinder': LONG_LABEL,
                    'skyweave_compat.configuration.feature.navigation.wayfinder_dh.help': TRANSLATED_HELP,
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
                    if long_labels:
                        client.context_title = LONG_CONTEXT
                    # The nine-scene gallery is additional work before the unchanged
                    # full workspace/reopen assertions. Its first measured run used
                    # all115seconds before quit could be polled; retain a bounded
                    # larger first-boot allowance rather than skipping those checks.
                    if layouts and not reopen:
                        client.deadline += 65
                    boot['timeoutSeconds'] = 180 if layouts and not reopen else 115
                    context = client.ready_context()
                    assert context['pid'] == process.pid, context
                    boot['loadedMods'] = client.state()['loadedMods']
                    if layouts and not reopen:
                        boot['contextScenes'] = context_scenes(client, output)
                    client.wait_active('Local files')
                    client.click('Local files')
                    catalog = client.proof('reform-catalog-proof', 'reform-catalog-proof.json')
                    boot['catalog'] = catalog
                    assert catalog['documents'] == len(catalog['expectedRoutes'])
                    assert len(catalog['composition']) == catalog['placements']
                    assert all(row.get('section') and row.get('title') and row.get('eyebrow')
                               for row in catalog['composition']), catalog['composition']
                    generation_help = [row['placementHelp'] for row in catalog['composition']
                                       if row['placement'].endswith('/generation')]
                    assert len(generation_help) == 1 and 'Restart after saving.' in generation_help[0]
                    assert 'inactive in this session' in generation_help[0]
                    if layouts and not reopen:
                        boot['layoutScenes'] = []
                        layout_scenes(client, output, catalog, boot['layoutScenes'])
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
                    if long_labels:
                        layout = client.proof('reform-layout-proof', 'reform-layout-proof.json')
                        descriptions = [row['help'] for panel in layout['panels'] for row in panel['rows']
                                        if 'navigation.wayfinder_dh/' in row['key']]
                        assert len(descriptions) == 1 and TRANSLATED_HELP in descriptions[0], descriptions
                        expected_reason = 'requires active navigation.wayfinder' if reopen else 'missing distanthorizons'
                        assert 'inactive in this session: ' + expected_reason in descriptions[0].lower(), descriptions
                        boot['translatedPurposeWithOwnerFacts'] = descriptions[0]
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
                proof = game / 'reform-editor-proof.json'
                if proof.is_file():
                    report['lastEditorProof'] = json.loads(proof.read_text())
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
    parser.add_argument('--layout-scenes', action='store_true', help='Capture the actual context chooser and generation, interior and shape forms at matched wide/narrow/high-GUI sizes without editing')
    args = parser.parse_args()
    run(args.java, [args.compat, args.probe, args.reform], args.output, args.long_labels, args.layout_scenes)
