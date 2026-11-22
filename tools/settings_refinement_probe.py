#!/usr/bin/env python3
"""Qualify current settings through the packaged Configure factory and its native controls."""
import argparse
import json
from pathlib import Path
import shutil
import signal
import subprocess
import sys
import tempfile
import time

from reform_probe import Client, digest, packaged_command, stop
from reform_workspace_probe import capture


def field(client, path):
    client.act('reform-select-section', field=path.split('.'))
    client.act('reform-field-proof', field=path.split('.'))
    return json.loads((client.root / 'reform-field-proof.json').read_text())


def edit(client, path, before, after):
    client.edit_field(path.split('.'), before, after)
    return client.ready_editor()


def route(client, suffix):
    if not client.state().get('screen', '').endswith('ClientEditingWorkflow$Overview'):
        client.act('key', key=256)
    catalog = client.proof('reform-catalog-proof', 'reform-catalog-proof.json')
    matches = [item for item in catalog['placementIds'] if item.endswith('/' + suffix)]
    if len(matches) != 1:
        raise AssertionError(('Expected one declared placement', suffix, matches))
    client.navigate(matches[0])
    first_field = {'shapes': 'strengthVariation', 'generation': 'spacing', 'climate': 'bottomDepth',
                   'wayfinder': 'cooldownTicks'}
    if suffix in first_field:
        client.act('reform-focus-field', document='skyweave_compat:' + suffix, field=first_field[suffix])
    return client.until(lambda: value if (value := client.ready_editor())['document'] == 'skyweave_compat:' + suffix else None,
                        'The selected document did not become the actual editor')


def verify(client, output):
    result = {}
    client.wait_active('Local files')
    client.click('Local files')
    initial = route(client, 'shapes')
    assert initial['draft']['strengthVariation'] == .15, initial
    result['shapeInput'] = edit(client, 'strengthVariation', '0.15', '.16')
    result['shapeField'] = field(client, 'strengthVariation')
    assert result['shapeInput']['draft']['strengthVariation'] == .16
    assert 'step=0.01' in result['shapeField']['shape'], result['shapeField']
    client.act('reform-focus-field', field=['strengthVariation'])
    state = client.act('snapshot')
    focused = [widget for widget in state['widgets'] if widget.get('focused') and 'value' in widget]
    assert len(focused) == 1, state
    increases = [index for index, widget in enumerate(widget for widget in state['widgets'] if widget['label'] == '+')
                 if widget['y'] == focused[0]['y']]
    assert len(increases) == 1, state
    client.act('click', label='+', index=increases[0])
    stepped = client.ready_editor()
    assert stepped['draft']['strengthVariation'] == .17, stepped
    result['nativeStep'] = stepped

    original = stepped['draft']['fringe']['minimumSmoothFraction']
    invalid = edit(client, 'fringe.minimumSmoothFraction', str(original), '.99')
    assert not invalid['valid'], invalid
    result['coupledFailure'] = field(client, 'fringe.minimumSmoothFraction')
    assert any(issue.get('related') for issue in result['coupledFailure']['issues']), result
    edit(client, 'fringe.minimumSmoothFraction', '.99', str(original))
    client.act('reform-focus-field', field=['fringe', 'enabled'])
    client.act('reform-field-click', field=['fringe', 'enabled'])
    result['inactiveFringe'] = field(client, 'fringe.density')
    assert not result['inactiveFringe']['editable'], result['inactiveFringe']
    assert result['inactiveFringe']['node'], result['inactiveFringe']
    client.act('text', value='9')
    result['inactiveTyping'] = field(client, 'fringe.density')
    assert result['inactiveTyping']['node'] == result['inactiveFringe']['node'], result
    result['capture'] = capture(client, output, 'reform-inactive-fringe.png')
    client.act('reform-focus-field', field=['fringe', 'enabled'])
    client.act('reform-field-click', field=['fringe', 'enabled'])
    result['restoredFringe'] = field(client, 'fringe.density')
    assert result['restoredFringe']['editable'], result
    assert result['restoredFringe']['node'] == result['inactiveFringe']['node'], result
    client.wait_active('Review Generation and shapes…')
    client.click('Review Generation and shapes…')
    client.wait_active('Save reviewed documents')
    client.click('Save reviewed documents')
    saved = client.root / 'config/skyweave/skyweave_compat-shapes.json'
    client.until(lambda: saved.is_file() and json.loads(saved.read_text())['strengthVariation'] == .17,
                 'The real paired owner did not save the refined value')
    result['savedShape'] = json.loads(saved.read_text())
    result['savedShapeSha256'] = digest(saved)
    return result


def verify_numeric(client, output):
    result = {}
    client.wait_active('Local files')
    client.click('Local files')
    for document, name in (('generation', 'activation'), ('climate', 'bottomDepth')):
        initial = route(client, document)
        before = field(client, name)['text']
        observations = {'initial': initial}
        for raw in ('0', '-0.0'):
            invalid = edit(client, name, before, raw)
            observations[raw] = field(client, name)
            assert not invalid['valid'], invalid
            assert observations[raw]['node']['raw'] in ('0', '-0.0'), observations
            assert observations[raw]['issues'], observations
            before = raw
        tiny = edit(client, name, before, '1e-300')
        observations['tiny'] = field(client, name)
        assert tiny['valid'], tiny
        assert observations['tiny']['node']['rawAfterThumbRead'] == '1e-300', observations
        assert observations['tiny']['text'] == '1e-300', observations
        # Return to the default through actual input; do not leave an extreme world policy behind.
        observations['restored'] = edit(client, name, '1e-300', str(initial['draft'][name]))
        result[document] = observations
    initial = route(client, 'wayfinder')
    proof = field(client, 'cooldownTicks')
    assert proof['node']['caption'] == 'Off' and proof['text'] == '0', proof
    result['zeroCaption'] = proof
    result['oneTick'] = edit(client, 'cooldownTicks', '0', '1')
    assert 'caption' not in field(client, 'cooldownTicks')['node']
    result['zeroRestored'] = edit(client, 'cooldownTicks', '1', '0')
    result['ticks'] = edit(client, 'cooldownTicks', '0', '20')
    def unit_next(label):
        client.act('reform-focus-field', field=['cooldownTicks'])
        state = client.act('snapshot')
        focused = [widget for widget in state['widgets'] if widget.get('focused') and 'value' in widget]
        assert len(focused) == 1, state
        matches = [widget for widget in state['widgets'] if widget['label'] == label]
        selected = [i for i, widget in enumerate(matches) if widget['y'] == focused[0]['y']]
        assert len(selected) == 1, state
        client.act('click', label=label, index=selected[0])
        return field(client, 'cooldownTicks')
    result['secondsView'] = unit_next('ticks')
    assert result['secondsView']['text'] == '1' and client.ready_editor()['draft']['cooldownTicks'] == 20, result
    result['secondsEdit'] = edit(client, 'cooldownTicks', '1', '1.5')
    assert result['secondsEdit']['draft']['cooldownTicks'] == 30, result
    result['millisecondsView'] = unit_next('seconds')
    assert result['millisecondsView']['text'] == '1500' and client.ready_editor()['draft']['cooldownTicks'] == 30, result
    result['ticksView'] = unit_next('milliseconds')
    assert result['ticksView']['text'] == '30', result
    saved = client.root / 'config/skyweave/skyweave_compat-wayfinder.json'
    client.wait_active('Save staged values')
    client.click('Save staged values')
    client.until(lambda: saved.is_file() and json.loads(saved.read_text())['cooldownTicks'] == 30, 'Actual staged owner did not save30ticks')
    def saved_receipt():
        value = client.ready_editor()
        return value if value.get('attemptTerminal') and value.get('persistence') == 'SAVED' and not value['busy'] and not value['dirty'] else None
    result['unitSaveReceipt'] = client.until(saved_receipt, 'Staged save did not complete')
    result['savedUnits'] = json.loads(saved.read_text())
    client.act('world', slot=1)
    client.until(lambda: client.state().get('playerReady') and client.state().get('integrated'), 'Saved-policy fixture did not join')
    def policy(ticks):
        request = client.act('server-proof')['sequence']
        def observed():
            value = client.state().get('serverProof', {})
            return value if value.get('sequence') == request and value.get('wayfinder', {}).get('cooldownTicks') == ticks else None
        return client.until(observed, 'Actual server policy did not report expected cooldown')
    result['firstServer'] = policy(30)
    client.act('open'); client.wait_active('Local files'); client.click('Local files'); route(client, 'wayfinder')
    before = field(client, 'cooldownTicks')['text']
    result['stagedZero'] = edit(client, 'cooldownTicks', before, '0')
    client.wait_active('Save staged values'); client.click('Save staged values')
    client.until(lambda: json.loads(saved.read_text())['cooldownTicks'] == 0, 'Actual staged owner did not save0ticks')
    result['zeroSaveReceipt'] = client.until(saved_receipt, 'Second staged save did not complete')
    result['runningPolicyUnchanged'] = policy(30)
    result['capture'] = capture(client, output, 'reform-zero-caption.png')
    result['widgets'] = [widget for widget in client.act('snapshot')['widgets'] if widget.get('reachable')]
    client.act('disconnect'); client.until(lambda: not client.state().get('integrated'), 'Saved-policy fixture did not disconnect')
    client.act('world', slot=2)
    client.until(lambda: client.state().get('playerReady') and client.state().get('integrated'), 'New server policy fixture did not join')
    result['secondServer'] = policy(0)
    assert result['secondServer']['previousStopped'] and result['secondServer']['oldEffectiveRevisionRefused'], result
    client.act('disconnect'); client.until(lambda: not client.state().get('integrated'), 'New policy fixture did not disconnect')
    return result


def verify_references(client, output):
    result = {}
    client.partial = result
    client.wait_active('Local files')
    client.click('Local files')

    def browser():
        route(client, 'wayfinder')
        client.act('reform-select-section', field=['blockedBlocks'])
        client.reveal('Choose values…')
        client.click('Choose values…')
        return client.until(lambda: value if 'page' in (value := client.proof(
            'reform-reference-proof', 'reform-reference-proof.json')) else None, 'Reference source did not settle')

    result['title'] = browser()
    assert result['title']['page']['availability'] == 'UNAVAILABLE_CONTEXT', result
    client.act('world', slot=1)
    client.until(lambda: client.state().get('playerReady') and client.state().get('integrated'), 'Disposable reference world did not join')
    pack = client.root / 'saves/config-review-world-1/datapacks/reference-review'
    (pack / 'data/skyweave_review/tags/block').mkdir(parents=True)
    (pack / 'pack.mcmeta').write_text(json.dumps({'pack': {'pack_format': 48, 'description': 'Disposable reference reload witness'}}))
    tag = pack / 'data/skyweave_review/tags/block/empty.json'
    tag.write_text('{"values": []}\n')
    client.act('reference-command', action='reload')
    def tag_ready(matches):
        value = client.proof('reference-tag-proof', 'reference-tag-proof.json')
        return value if value['exists'] and value['matches'] == matches else None
    result['emptyTagNative'] = client.until(lambda: tag_ready(0), 'Native reload did not publish the empty tag')
    client.act('open')
    client.wait_active('Local files')
    client.click('Local files')
    result['world'] = browser()
    assert result['world']['page']['availability'] == 'READY', result
    def inspect(index, expected):
        client.reveal('Inspect', index=index)
        client.act('click', label='Inspect', index=index)
        def settled():
            value = client.proof('reform-reference-proof', 'reform-reference-proof.json')
            return value if value.get('resolution', {}).get('status') == expected else None
        return client.until(settled, 'Native reference resolution did not settle: ' + expected)
    result['empty'] = inspect(0, 'RESOLVED')
    assert result['empty']['resolution']['value'] == [] and result['empty']['resolution']['preview']['matches'] == '0', result
    result['unknown'] = inspect(1, 'UNKNOWN_REFERENCE')
    tag.write_text('{"values": ["minecraft:stone"]}\n')
    client.act('reference-command', action='reload')
    result['reloadedTagNative'] = client.until(lambda: tag_ready(1), 'Native reload did not publish the changed tag')
    client.click('Refresh views')
    result['reloaded'] = inspect(0, 'RESOLVED')
    assert result['reloaded']['resolution']['value'] == ['minecraft:stone'], result
    assert result['empty']['page']['stamp'] != result['reloaded']['page']['stamp'], result
    client.field('', 'minecraft:stone', 'Search choices')
    client.reveal('Search choices', index=1)
    client.act('click', label='Search choices', index=1)
    def stone_ready():
        value = client.proof('reform-reference-proof', 'reform-reference-proof.json')
        return value if any(choice['key'] == 'minecraft:stone' for choice in value.get('page', {}).get('choices', [])) else None
    result['stone'] = client.until(stone_ready, 'Registry search did not include stone')
    result['capture'] = capture(client, output, 'reform-world-references.png')
    client.act('disconnect')
    client.until(lambda: not client.state().get('integrated') and not client.state().get('playerReady'), 'Reference world did not disconnect')
    client.act('open')
    client.wait_active('Local files')
    client.click('Local files')
    result['disconnected'] = browser()
    assert result['disconnected']['page']['availability'] == 'UNAVAILABLE_CONTEXT', result
    return result


def verify_modifiers(client, output, expected):
    client.act('world', slot=1)
    client.until(lambda: client.state().get('playerReady') and client.state().get('integrated'), 'Disposable modifier world did not join')
    client.act('focus-window')
    client.until(lambda: client.state().get('windowActive'), 'Modifier fixture did not receive native window focus')
    result = client.proof('modifier-proof', 'modifier-proof.json')
    assert result['plainOpenedCamera'] and result['plainPerspectiveChanged'] and result['plainPerspectiveObserved'], result
    assert result['wrongModifierOpenedCamera'] is (expected == 'baseline'), result
    assert not result['wrongModifierPerspectiveChanged'], result
    assert result['wrongModifierPerspectiveObserved'] is (expected == 'baseline'), result
    assert result['plainPerspectiveNotifications'] == 1, result
    assert result['wrongModifierPerspectiveNotifications'] == (1 if expected == 'baseline' else 0), result
    admission = result['nativeAdmission']
    for name, value in admission.items():
        if name.startswith(('SHIFT', 'ALT', 'CONTROL')):
            assert value, (name, result)
    assert admission['competingModifiedCameraOpened'] is (expected == 'baseline'), result
    if expected == 'protected':
        assert admission['competingModifiedClickRetained'], result
    assert admission['inactiveContextCameraOpened'] is (expected == 'baseline'), result
    assert admission['extraModifierPlainFallbackOpened'] and admission['chatRetained'] and admission['chatTextRetained'], result
    assert not admission['repeatOpenedCamera'], result
    camera = result['activeCamera']
    assert camera['nativeFreeCameraStarted'] and camera['wrongModifierKeptPerspective'], result
    assert camera['wrongModifierKeptFreeCamera'] is (expected == 'protected'), result
    assert camera['nativePerspectiveChanged'] and camera['nativePerspectiveStoppedFreeCamera'] and camera['publicObserverPreserved'], result
    assert camera['nativePerspectiveNotifications'] == 1, result
    emotes = result['emotes']
    assert emotes['wrongModifierTriggers'] == (1 if expected == 'baseline' else 0), result
    assert emotes['configuredTriggers'] == 1 and emotes['configuredMenuOpened'] and emotes['secondPressMenuClosed'], result
    assert emotes['wrongModifierMenuOpened'] is (expected == 'baseline'), result
    assert emotes['removedActiveCount'] == 0 and emotes['restoredModifiedBinding'], result
    assert emotes['removedModifiedBindingPreservedNativeFallback'] is (expected == 'protected'), result
    client.act('disconnect')
    client.until(lambda: not client.state().get('integrated') and not client.state().get('playerReady'), 'Modifier world did not disconnect')
    return result


def verify_effortless(client, output, expected):
    client.act('world', slot=1)
    client.until(lambda: client.state().get('playerReady') and client.state().get('integrated'), 'Disposable building world did not join')
    client.act('focus-window')
    client.until(lambda: client.state().get('windowActive'), 'Building fixture did not receive native focus')
    result = client.proof('modifier-effortless', 'modifier-effortless-proof.json')
    assert result['wrongModifierOpened'] is (expected == 'baseline'), result
    for key in ('plainOpened', 'plainRetainedAcrossScreenReset', 'plainClosedAfterRelease', 'configuredModifierOpened'):
        assert result[key], (key, result)
    assert result['modifierFirstReleaseClosed'] is (expected == 'protected'), result
    history = result['history']
    assert history['wrongModifierUndo'] == 0 and history['plainWithoutPhysicalControlUndo'] == 0, result
    assert history['plainPhysicalControlUndo'] == 1, result
    assert history['configuredShiftUndo'] == (1 if expected == 'protected' else 0), result
    assert history['configuredAltRedo'] == (1 if expected == 'protected' else 0), result
    if expected == 'protected':
        for key in ('mouseOpened', 'mouseRetained', 'mouseReleasedClosed', 'scancodeOpened', 'scancodeReleasedClosed', 'focusLossClosed', 'otherScreenRetained'):
            assert result[key], (key, result)
        assert not result['repeatReopenedAfterFocusLoss'] and not result['staleScreenHoldReopened'], result
    client.act('effortless-build', action='setup')
    def block_ready(block):
        path = client.root / 'effortless-build-proof.json'
        path.unlink(missing_ok=True)
        client.act('effortless-build', action='state')
        value = client.until(lambda: json.loads(path.read_text()) if path.is_file() else None, 'Native build state did not publish')
        return value if value['serverBlock'] == value['clientBlock'] == block and value['clientHeldDirt'] else None
    result['buildInitial'] = client.until(lambda: block_ready('minecraft:air'), 'Native build fixture did not synchronize')
    client.act('effortless-build', action='place')
    result['built'] = client.until(lambda: block_ready('minecraft:dirt'), 'Native placement packet did not place its block')
    for operation, block in (('undo', 'minecraft:air'), ('redo', 'minecraft:dirt')):
        client.act('effortless-build', action=operation, configured=(expected == 'protected'))
        result[operation] = client.until(lambda: block_ready(block), 'Native ' + operation + ' did not restore the block')
    assert all(result[key]['serverHeldCount'] == 64 and result[key]['serverCarriedEmpty'] for key in ('built', 'undo', 'redo')), result
    client.act('disconnect')
    client.until(lambda: not client.state().get('integrated'), 'Building world did not disconnect')
    return result


def verify_persistence(client, output):
    client.act('modifier-persistence', save=True)
    saved = json.loads((client.root / 'modifier-persistence-proof.json').read_text())
    assert saved['cameraEditor']['key'] == 'key.keyboard.f13' and saved['cameraEditor']['modifier'] == 'SHIFT', saved
    assert saved['cameraToggle']['key'] == 'key.keyboard.f14' and saved['cameraToggle']['modifier'] == 'ALT', saved
    assert saved['freeCamera']['key'] == 'key.keyboard.unknown', saved
    assert saved['wayfinder'] == {'key': 'key.keyboard.f8', 'defaultKey': 'key.keyboard.f12'}, saved
    assert any(line.endswith(':SHIFT') for line in saved['nativeOptions']), saved
    client.act('modifier-reload')
    client.act('modifier-persistence', save=False)
    reloaded = json.loads((client.root / 'modifier-persistence-proof.json').read_text())
    assert reloaded == saved, (saved, reloaded)
    return {'saved': saved, 'resourceReload': reloaded}


def verify_powergrid(client, output, expected):
    result = {}
    client.partial = result
    client.act('world', slot=1)
    client.until(lambda: client.state().get('playerReady') and client.state().get('integrated'), 'Disposable Power Grid world did not join')
    client.act('focus-window')
    client.until(lambda: client.state().get('windowActive'), 'Power Grid fixture did not receive native focus')
    client.act('powergrid-setup')
    def setup_ready():
        path = client.root / 'powergrid-setup-proof.json'
        return json.loads(path.read_text()) if path.is_file() else None
    result['setup'] = client.until(setup_ready, 'Native table/menu setup did not settle')
    assert 'error' not in result['setup'], result
    client.until(lambda: client.state().get('powergridTableReady'), 'Native table block entity did not synchronize')
    result['open'] = client.proof('powergrid-open', 'powergrid-open-proof.json')
    assert result['open']['serverMenu'].endswith('.CircuitDesignTableMenu'), result
    client.until(lambda: client.state().get('screen', '').endswith('.CircuitDesignTableScreen'), 'Native table menu did not synchronize')
    client.act('powergrid-editor')
    client.until(lambda: client.state().get('screen', '').endswith('.CircuitDesignTableEditScreen'), 'Native editor packet did not synchronize')
    result['input'] = client.proof('powergrid-input-proof', 'powergrid-input-proof.json')
    assert result['input']['wrongModifierFlipped'] is (expected == 'baseline'), result
    assert result['input']['configuredModifierFlipped'] and result['input']['plainFlipped'], result
    assert not result['input']['focusedTextFlipped'] and result['input']['typedTextRetained'] and result['input']['screenRetained'], result
    result['capture'] = capture(client, output, 'reform-powergrid-editor.png')
    client.act('powergrid-seed-wire')
    client.until(lambda: client.state().get('powergridWireReady') and client.state().get('screen') == 'none', 'Native wire/component did not synchronize')
    result['wire'] = client.proof('powergrid-wire-proof', 'powergrid-wire-proof.json')
    wire = result['wire']
    assert wire['configuredActive'] and wire['plainActive'] and wire['modifiedMouseActive'], result
    assert wire['wrongModifierActive'] is (expected == 'baseline') and wire['wrongMouseActive'] is (expected == 'baseline'), result
    assert wire['modifierReleaseActive'] is (expected == 'baseline') and wire['mouseModifierReleaseActive'] is (expected == 'baseline'), result
    assert not wire['plainReleaseActive'] and not wire['finalActive'] and not wire['repeatActive'], result
    if expected == 'protected':
        assert not wire['focusLossActive'] and not wire['screenActive'], result
    def server_state(active):
        path = client.root / 'powergrid-server-proof.json'
        path.unlink(missing_ok=True)
        client.act('powergrid-server-proof')
        value = client.until(lambda: json.loads(path.read_text()) if path.is_file() else None, 'Native server flag did not publish')
        assert 'error' not in value, value
        return value if value['clientActive'] is active and value['serverActive'] is active else None
    client.act('powergrid-held', down=True)
    result['serverPress'] = client.until(lambda: server_state(True), 'Native wire press did not reach the server')
    client.act('powergrid-held', down=False)
    result['serverRelease'] = client.until(lambda: server_state(False), 'Native wire release did not reach the server')
    client.act('disconnect')
    client.until(lambda: not client.state().get('integrated'), 'Power Grid world did not disconnect')
    return result


def run(args):
    if args.output.exists():
        raise ValueError('Choose a new evidence directory')
    args.output.mkdir(parents=True)
    artifacts = [path.resolve(strict=True) for path in args.mod]
    report = {'status': 'FAIL', 'suite': args.suite,
              'invocation': sys.argv, 'probeScriptSha256': digest(Path(__file__)),
              'disabledFeatures': args.feature_off,
              'artifacts': {path.name: digest(path) for path in artifacts},
              'scope': ('Native input consumers and saved bindings; synthetic delivery/polling as stated in each observation, no OS layout qualification'
                        if args.suite in ('modifiers', 'effortless', 'persistence', 'powergrid')
                        else 'Native Configure controls and owner bindings in disposable fixtures; no personal operations')}
    process = None
    with tempfile.TemporaryDirectory(prefix='skyweave-refinement-') as temporary:
        root = Path(temporary)
        (root / 'mods').mkdir()
        for artifact in artifacts:
            target = root / 'mods' / artifact.name
            if target.exists():
                raise ValueError('Duplicate staged artifact filename: ' + artifact.name)
            shutil.copy2(artifact, target)
            if digest(target) != report['artifacts'][artifact.name]:
                raise RuntimeError('Artifact changed while staging: ' + artifact.name)
        (root / 'config-review-fixture.json').write_text('{}\n')
        if args.suite in ('modifiers', 'effortless', 'persistence', 'powergrid'):
            (root / 'modifier-review-fixture.json').write_text('{}\n')
        if args.suite == 'references':
            (root / 'reference-review-fixture.json').write_text('{}\n')
        (root / 'options.txt').write_text('onboardAccessibility:false\nrenderDistance:2\nsimulationDistance:2\nguiScale:2\noverrideWidth:1100\noverrideHeight:760\nmaxFps:30\n')
        if args.suite == 'modifiers':
            pack = root / 'resourcepacks/input-fixture'
            files = {
                'pack.mcmeta': {'pack': {'pack_format': 34, 'description': 'Disposable input fixture'}},
                'assets/punchy_moves/animations/probe.animation.json': {'format_version': '1.8.0', 'animations': {
                    'skyweave_probe': {'loop': True, 'animation_length': 1, 'bones': {'head': {'rotation': [0, 0, 0]}}}}},
                'assets/punchy_moves/compat/probe.json': {'format_version': 2, 'emotes': {'menu_key': 'F14', 'entries': [
                    {'id': 'skyweave_probe', 'display_name': 'Fixture emote', 'animation': 'skyweave_probe', 'keybind': 'unknown'}]}}
            }
            for name, value in files.items():
                path = pack / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(json.dumps(value) + '\n')
            report['authoredFixtureResources'] = {name: digest(pack / name) for name in files}
            with (root / 'options.txt').open('a') as options:
                options.write('resourcePacks:["vanilla","mod_resources","file/input-fixture"]\n')
        config = root / 'config/skyweave'
        config.mkdir(parents=True)
        (config / 'skyweave_compat-features.properties').write_text('terrain.preset=false\n' + ''.join(key + '=false\n' for key in args.feature_off))
        if args.suite == 'references':
            (config / 'skyweave_compat-wayfinder.json').write_text(json.dumps({
                'operatorsOnly': True, 'cooldownTicks': 0, 'placementLookupHeight': 16,
                'stepsPerTick': 256, 'concurrentRequests': 4, 'requestTimeoutTicks': 200,
                'blockedBlocks': ['#skyweave_review:empty', 'skyweave_review:future_block', 'minecraft:stone']}))
        try:
            command = packaged_command(root, args.java, gameplay=False)
            if args.suite in ('modifiers', 'effortless', 'persistence', 'powergrid'):
                with (root / 'vm.args').open('a') as vm:
                    vm.write('\n-Dskyweave_compat.probe.modifiers=true\n')
            with (args.output / 'console.log').open('wb') as console:
                process = subprocess.Popen(command, cwd=root, stdout=console, stderr=subprocess.STDOUT)
                client = Client(root, process)
                client.deadline = time.monotonic() + 240
                client.until(lambda: client.state().get('screen', '').endswith('ClientSettingsMenus$ContextScreen'), 'Configure did not open')
                if args.suite in ('modifiers', 'effortless', 'powergrid'):
                    report['observations'] = {'modifiers': verify_modifiers, 'effortless': verify_effortless,
                                              'powergrid': verify_powergrid}[args.suite](client, args.output, args.expected)
                else:
                    report['observations'] = {'staged': verify, 'numeric': verify_numeric,
                                              'references': verify_references, 'persistence': verify_persistence}[args.suite](client, args.output)
                client.act('quit')
                report['exit'] = process.wait(timeout=20)
                assert report['exit'] == 0
                report['status'] = 'PASS'
            if args.suite == 'persistence':
                report['status'] = 'FAIL'
                shutil.copy2(root / 'options.txt', args.output / 'saved-options.txt')
                for name in ('config-review-control.json', 'config-review-state.json'):
                    (root / name).unlink(missing_ok=True)
                with (args.output / 'console-restart.log').open('wb') as console:
                    process = subprocess.Popen(command, cwd=root, stdout=console, stderr=subprocess.STDOUT)
                    client = Client(root, process)
                    client.deadline = time.monotonic() + 180
                    client.until(lambda: client.state().get('screen', '').endswith('ClientSettingsMenus$ContextScreen'), 'Restart Configure did not open')
                    client.act('modifier-persistence', save=False)
                    restarted = json.loads((root / 'modifier-persistence-proof.json').read_text())
                    report['restarted'] = restarted
                    expected = args.expected == 'protected'
                    assert (restarted['cameraEditor']['key'] == 'key.keyboard.f13') is expected, restarted
                    assert restarted['cameraEditor']['modifier'] == ('SHIFT' if expected else 'NONE'), restarted
                    assert (restarted['cameraToggle']['key'] == 'key.keyboard.f14') is expected, restarted
                    assert restarted['cameraToggle']['modifier'] == ('ALT' if expected else 'NONE'), restarted
                    assert restarted['freeCamera']['key'] == 'key.keyboard.unknown', restarted
                    assert restarted['wayfinder'] == {'key': 'key.keyboard.f8', 'defaultKey': 'key.keyboard.f12'}, restarted
                    client.act('quit')
                    report['restartExit'] = process.wait(timeout=20)
                    assert report['restartExit'] == 0
                    report['status'] = 'PASS'
        except BaseException as failure:
            report['error'] = str(failure)
            if process is not None:
                report['lastState'] = client.state()
                if hasattr(client, 'partial'):
                    report['partialObservations'] = client.partial
            raise
        finally:
            if process is not None and process.poll() is None:
                process.terminate()
                try:
                    process.wait(timeout=10)
                except subprocess.TimeoutExpired:
                    process.kill(); process.wait(timeout=10)
            for name in ('reform-editor-proof.json', 'reform-field-proof.json', 'reform-catalog-proof.json',
                         'reform-reference-proof.json', 'modifier-proof.json', 'modifier-effortless-proof.json',
                         'modifier-persistence-proof.json', 'powergrid-setup-proof.json', 'powergrid-open-proof.json', 'powergrid-input-proof.json', 'powergrid-wire-proof.json', 'powergrid-server-proof.json', 'effortless-build-proof.json'):
                if (root / name).is_file():
                    shutil.copy2(root / name, args.output / name)
            (args.output / 'report.json').write_text(json.dumps(report, indent=2) + '\n')
    return report


if __name__ == '__main__':
    signal.signal(signal.SIGTERM, stop)
    signal.signal(signal.SIGINT, stop)
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--java', type=Path, required=True)
    parser.add_argument('--mod', type=Path, action='append', required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--suite', choices=('staged', 'numeric', 'references', 'modifiers', 'effortless', 'persistence', 'powergrid'), default='staged')
    parser.add_argument('--feature-off', action='append', default=[], choices=(
        'punchy_moves.keybinding_modifiers', 'effortlessbuilding.keybinding_modifiers', 'powergrid.keybinding_modifiers'),
        help='Explicitly disable an input adapter in this disposable fixture')
    parser.add_argument('--expected', choices=('baseline', 'protected'), default='protected')
    args = parser.parse_args()
    report = run(args)
    print(json.dumps({'status': report['status'], 'suite': args.suite, 'report': str(args.output / 'report.json')}))
