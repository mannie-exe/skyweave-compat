#!/usr/bin/env python3
"""Run the packaged Compat settings client against isolated local settings fixtures."""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import signal
import subprocess
import tempfile
import time
import tomllib

from client_bootstrap import REPOSITORY, argument_file, connector_bootstrap, packaged_bootstrap


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def packaged_command(root, java, clean=None, gameplay=True):
    vm, program = packaged_bootstrap(root)
    vm.extend(['-Xms512M', '-Xmx2G', '-Dskyweave_compat.probe.configuration=true'])
    connector_bootstrap(vm, clean)
    if gameplay:
        vm.append('-Dskyweave_compat.probe.misc=true')
    argument_file(root / 'vm.args', vm)
    argument_file(root / 'program.args', program)
    return [str(java), '@classpath.args', '@vm.args', 'net.neoforged.devlaunch.Main', '@program.args']


class Client:
    def __init__(self, root, process):
        self.root = root
        self.process = process
        self.deadline = time.monotonic() + 115
        self.sequence = 0
        self.context_title = 'Skyweave: Compatibility Patches'

    def until(self, predicate, reason):
        limit = min(self.deadline, time.monotonic() + 45)
        while time.monotonic() < limit:
            result = predicate()
            if result:
                return result
            if self.process.poll() is not None:
                break
            time.sleep(.12)
        raise RuntimeError(f'{reason} (client exit {self.process.poll()})')

    def state(self, probe='config'):
        try:
            return json.loads((self.root / f'{probe}-review-state.json').read_text())
        except (FileNotFoundError, json.JSONDecodeError):
            return {}

    def ready_context(self):
        return self.until(lambda: state if (state := self.state()).get('screen') ==
                          'design.inherent.reform.minecraft.client.ClientChoiceScreen'
                          and state.get('screenTitle') == self.context_title else None,
                          'The actual Compat configuration context chooser did not arrive')

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

    def reveal(self, label, index=None):
        """Scroll the native viewport; never bypass its clipping or click checks."""
        for _ in range(12):
            state = self.act('snapshot')
            matches = [widget for widget in state['widgets'] if widget['label'] == label]
            if (index is None and len(matches) != 1) or (index is not None and not 0 <= index < len(matches)):
                raise RuntimeError(f'Expected one native control {label!r}, found {len(matches)}')
            widget = matches[0 if index is None else index]
            if widget['reachable']:
                return
            self.act('scroll', x=widget['x'] + widget['width'] / 2,
                     y=state['height'] / 2, amount=-4 if widget['y'] >= state['height'] / 2 else 4)
        raise RuntimeError(f'Native scrolling did not reveal {label!r}')

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

    def edit_field(self, path, before, after):
        self.act('reform-focus-field', field=path)
        inputs = [widget for widget in self.state()['widgets'] if 'value' in widget]
        matches = [index for index, widget in enumerate(inputs)
                   if widget.get('focused') and widget['value'] == before]
        if len(matches) != 1:
            raise RuntimeError(f'The selected native field {path!r} does not contain {before!r}')
        return self.act('field', index=matches[0], value=after)

    def map(self):
        self.act('hoofprint-proof')
        return json.loads((self.root / 'hoofprint-observation.json').read_text())

    def open_form(self, query, context='Local files'):
        self.act('open')
        self.wait_active(context)
        self.click(context)
        self.act('reform-catalog-proof')
        self.act('reform-show-placement', id=query)
        self.ready_editor()

    def ready_editor(self):
        def ready():
            response = self.request('reform-editor-proof')
            if response.get('error'):
                return None
            value = json.loads((self.root / 'reform-editor-proof.json').read_text())
            return value if value['availability'] == 'READY' else None
        return self.until(ready, 'the actual selected owner did not become ready')

    def document_actions(self):
        self.click('Document actions…')
        def opened():
            value = self.state()
            name = value.get('screen', '')
            if name.endswith('DocumentActionsScreen'):
                return value
            if name.endswith('ClientEditingWorkflow$Overview') and any(
                    widget['label'] == 'Back to settings' for widget in value.get('widgets', [])):
                return value
            return None
        return self.until(opened, 'Document actions did not open its native body')

    def navigate(self, query, actions=('Open', 'Resume')):
        if not self.state().get('screen', '').endswith('ClientEditingWorkflow$Overview'):
            self.act('key', key=256)
        self.until(lambda: self.state().get('screen', '').endswith('ClientEditingWorkflow$Overview'),
                   'the retained editor did not return to its workflow')
        catalog = self.proof('reform-catalog-proof', 'reform-catalog-proof.json')
        if query in catalog['placementIds']:
            self.act('reform-show-placement', id=query)
            self.ready_editor()
        elif query in {action['id'] for action in catalog['actions']}:
            self.act('reform-reveal-operation', id=query, again='Run again…' in actions)
            focused = [widget for widget in self.state()['widgets'] if widget.get('focused')]
            declared_title = next(action['title'] for action in catalog['actions'] if action['id'] == query)
            if len(focused) != 1 or focused[0]['label'] not in (declared_title, 'Start…', 'View operation', 'Run again…'):
                raise RuntimeError('The contributed operation did not focus its native action')
            label = focused[0]['label']
            matching = [widget for widget in self.state()['widgets'] if widget['label'] == label]
            self.act('click', label=label, index=next(i for i, widget in enumerate(matching) if widget.get('focused')))
        else:
            raise ValueError('Select a declared placement or contributed operation')

    def proof(self, operation, filename):
        self.act(operation)
        return json.loads((self.root / filename).read_text())


def verify_owner_surfaces(client, config):
    client.until(lambda: client.state('misc').get('loaded'), 'integrated fixture did not join')
    native = lambda: client.proof('native-owner-readers', 'native-owner-readers.json')
    editor = lambda: client.proof('reform-editor-proof', 'reform-editor-proof.json')
    before = native()
    water_id = 'skyweave_compat:dynamic_waters'
    render_id = 'skyweave_compat:rendering_client'
    journal_id = 'skyweave_compat:journal'
    village_id = 'skyweave_compat:sky_villages'
    assert len(before[water_id]) == 46 and len(before[journal_id]) == 3, before
    observations = dict(initial=before)
    client.partial = observations

    client.open_form('skyweave_compat:files/world/dynamic_waters/watersheds')
    client.edit_field(['River Flow Speeds', 'itemSpeed'], '0.2', '0.35')
    assert native()[water_id] == before[water_id]
    client.wait_active('Save native settings')
    client.click('Save native settings')
    water = client.until(lambda: value if (value := native())[water_id]['River Flow Speeds.itemSpeed'] == .35 else None,
                         'the native water field did not observe its saved value')
    expected_water = dict(before[water_id], **{'River Flow Speeds.itemSpeed': .35})
    assert water[water_id] == expected_water, water[water_id]
    observations['waterReceipt'] = client.proof('reform-receipt', 'reform-receipt.json')
    observations['waterFields'] = water[water_id]

    def add_biome():
        # Both lists render inline. Select the registered field, then use its
        # focused real button rather than inventing a navigation row.
        client.act('reform-focus-field', field=['Generation', 'biomeBlacklist'])
        additions = [widget for widget in client.state()['widgets'] if widget['label'] == 'Add entry']
        selected = [index for index, widget in enumerate(additions) if widget.get('focused')]
        assert len(selected) == 1 and additions[selected[0]]['reachable'], additions
        client.act('click', label='Add entry', index=selected[0])
        client.until(lambda: any(widget['label'] == 'Entry 1' and widget.get('value') == ''
                                 for widget in client.state()['widgets']),
                     'The actual native list row did not mount its text control')
        client.field('', 'minecraft:desert', 'Entry 1')

    def remove_biome():
        # Declaration order places the unchanged customEntitySpeeds row first.
        client.act('reform-focus-field', field=['Generation', 'biomeBlacklist'])
        removals = [widget for widget in client.act('snapshot')['widgets'] if widget['label'] == 'Remove']
        assert len(removals) == 2, removals
        client.reveal('Remove', index=1)
        client.act('click', label='Remove', index=1)

    def save_biomes(expected, stage):
        baseline = native()[water_id]
        source = config.parent / 'dynamicwaters-common.toml'
        source_before = source.read_bytes()
        stage()
        for retry in range(2):
            assert native()[water_id] == baseline
            client.wait_active('Save native settings')
            client.click('Save native settings')
            attempt = client.until(lambda: value if (value := editor()).get('attemptTerminal')
                                   and not value['busy'] else None, 'Native list save did not settle')
            if not attempt.get('attemptRefused'):
                assert attempt['persistence'] == 'SAVED', attempt
                return client.until(lambda: value if (value := native())[water_id]['Generation.biomeBlacklist'] == expected else None,
                                    'The real native list reader did not observe its saved value')
            # A native file-watch reload retires the captured native identity.
            # Exercise explicit reload/review, never bypass or silently retry it.
            assert retry == 0 and attempt['persistence'] == 'REFUSED', attempt
            assert 'code=native.context_changed' in attempt['persistenceReport'], attempt
            assert 'Native source was reloaded; reload and review' in attempt['persistenceReport'], attempt
            assert native()[water_id] == baseline and source.read_bytes() == source_before
            observations.setdefault('nativeReloadRefusals', []).append(attempt)
            client.document_actions()
            client.reveal('Reload saved')
            client.click('Reload saved')
            client.wait_active('Review conflict…')
            client.click('Review conflict…')
            client.until(lambda: client.proof('reform-task-proof', 'reform-task-proof.json')['conflict'],
                         'The explicit native conflict review did not open')
            client.until(lambda: any(widget['active'] and widget['label'] in ('Choose a value', 'Resolve draft')
                                     for widget in client.act('snapshot')['widgets']),
                         'The actual source comparison did not become ready')
            selected = 0
            for _ in range(64):
                if not any(widget['label'] == 'Choose a value' for widget in client.act('snapshot')['widgets']):
                    break
                for label in ('Choose a value', 'Earlier saved value', 'Observed saved value'):
                    client.reveal(label, index=0)
                    client.act('click', label=label, index=0)
                selected += 1
            assert not any(widget['label'] == 'Choose a value' for widget in client.act('snapshot')['widgets'])
            client.wait_active('Resolve draft')
            client.click('Resolve draft')
            client.until(lambda: client.act('snapshot')['screen'].endswith('ClientEditingWorkflow$Overview'),
                         'The resolved native comparison did not return to its retained editor')
            reviewed = editor()
            assert reviewed['valid'] and reviewed['dirty']
            assert reviewed['draft'] == attempt['draft']
            assert native()[water_id] == baseline and source.read_bytes() == source_before
            observations.setdefault('nativeReloadResolutions', []).append(dict(
                selectedDraftChoices=selected, preservedDraft=True, sourceUnchanged=True))

    listed = save_biomes(['minecraft:desert'], add_biome)
    assert listed[water_id] == {**water[water_id], 'Generation.biomeBlacklist': ['minecraft:desert']}
    restored = save_biomes([], remove_biome)
    assert restored[water_id] == water[water_id], restored
    observations['waterCollection'] = dict(saved=listed[water_id]['Generation.biomeBlacklist'], restored=[],
                                           receipt=client.proof('reform-receipt', 'reform-receipt.json'))

    client.navigate('skyweave_compat:files/rendering/sky_archipelago/rendering_client/clouds.offset')
    client.until(lambda: any(w.get('value') == '0.0' for w in client.state().get('widgets', [])), 'cloud offset did not load')
    client.edit_field(['clouds', 'offset'], '0.0', '12.5')
    first = editor()
    assert first['dirty'] and first['draft']['clouds']['offset'] == 12.5, first
    assert native()[render_id]['clouds.offset'] == 0
    client.navigate('skyweave_compat:files/rendering/iris/rendering_client/worldtext.unlittextdisplays')
    second = editor()
    assert second['context'] == first['context'] and second['draft'] == first['draft'], (first, second)
    client.wait_active('Save native settings')
    client.click('Save native settings')
    rendering = client.until(lambda: values if (values := native())[render_id]['clouds.offset'] == 12.5 else None,
                             'the shared rendering save did not update its real native reader')
    observations['sharedRendering'] = dict(first=first, second=second, native=rendering[render_id])

    client.navigate('skyweave_compat:files/world/sky_villages/villages')
    source = config / 'skyweave_compat-common.toml'
    source_before = digest(source)
    client.edit_field('minimumGap', '64', '129')
    invalid = client.until(lambda: value if not (value := editor())['valid'] else None,
                           'the complete village min/max rule did not reject the draft')
    assert digest(source) == source_before and native()[village_id] == before[village_id]
    client.edit_field('minimumGap', '129', '65')
    client.wait_active('Save native settings')
    client.click('Save native settings')
    client.until(lambda: tomllib.loads(source.read_text())['minimumGap'] == 65, 'village native save did not complete')
    # Native worldRestart fields retain their existing runtime cache while this world is running.
    assert native()[village_id] == before[village_id]
    observations['village'] = dict(invalid=invalid, saved=tomllib.loads(source.read_text()),
                                   native=native()[village_id], receipt=client.proof('reform-receipt', 'reform-receipt.json'))

    client.navigate('skyweave_compat:journal/images')
    client.act('reform-focus-field', field='itemImages')
    client.act('reform-field-click', field='itemImages')
    staged = editor()
    assert staged['draft']['itemImages'] is False and staged['dirty'], staged
    assert native()[journal_id]['itemImages'] is True
    client.act('journal-shortcut', action='symbols')
    client.until(lambda: (client.root / 'journal-shortcut.json').exists(), 'journal shortcut did not settle')
    shortcut = json.loads((client.root / 'journal-shortcut.json').read_text())
    assert 'error' not in shortcut, shortcut
    current = native()[journal_id]
    assert current['itemImages'] is True and current['conditionSymbols'] is False, current
    retained = editor()
    assert retained['context'] == staged['context'] and retained['draft']['itemImages'] is False, retained
    journal_source = config / 'skyweave_compat-journal-client.toml'
    journal_bytes = journal_source.read_bytes()
    client.wait_active('Save native settings')
    client.click('Save native settings')
    refused = client.until(lambda: value if (value := editor()).get('attemptTerminal') else None,
                           'the retained stale journal save did not settle')
    assert refused['dirty'] and refused.get('attemptRefused'), refused
    assert journal_source.read_bytes() == journal_bytes and native()[journal_id] == current
    observations['journal'] = dict(staged=staged, shortcut=shortcut, native=current, retained=retained, refusal=refused)
    client.navigate('skyweave_compat:files/maps/skyweave_compat/wayfinder_client')
    client.act('reform-focus-field', field='debugMessages')
    client.act('reform-field-click', field='debugMessages')
    debug_id = 'skyweave_compat:wayfinder_client'
    assert native()[debug_id]['debugMessages'] is False
    client.wait_active('Save native settings')
    client.click('Save native settings')
    debug = client.until(lambda: value if (value := native())[debug_id]['debugMessages'] is True else None,
                         'The actual client debug preference reader did not change')
    observations['wayfinderDebug'] = dict(native=debug[debug_id], receipt=client.proof('reform-receipt', 'reform-receipt.json'),
                                          scope='Native CLIENT preference reader; operator-message transport is unchanged and not exercised here')
    return observations


def verify_action_surfaces(client, config):
    client.until(lambda: client.state('misc').get('loaded'), 'integrated fixture did not join')
    options = client.root / 'options.txt'
    before = options.read_bytes()
    client.open_form('skyweave_compat:controls/keys')
    proof = client.proof('reform-editor-proof', 'reform-editor-proof.json')
    readers = client.proof('native-owner-readers', 'native-owner-readers.json')
    assert proof['document'] == 'skyweave_compat:controls' and proof['valid'] and not proof['dirty'], proof
    assert readers['skyweave_compat:controls'] and all(key.startswith('key.skyweave_compat.')
                                                    for key in readers['skyweave_compat:controls'])
    assert options.read_bytes() == before, 'opening native key settings rewrote Options'
    results = dict(controls=proof, nativeKeys=readers['skyweave_compat:controls'])
    client.partial = results

    client.navigate('skyweave_compat:shaders/rebuild', ('Start…',))
    operation = lambda: client.proof('reform-operation-proof', 'reform-operation-proof.json')
    initial = client.until(lambda: value if (value := operation())['terminal'] else None, 'native shader operation did not settle')
    assert initial['operation'] == 'skyweave_compat:shaders/rebuild' and initial['state'] == 'COMPLETED', initial
    results['initialShaderReload'] = initial
    broken = config / 'shaders/recipes/broken.json'
    broken.parent.mkdir(parents=True, exist_ok=True)
    broken.write_text('{malformed fixture\n')
    client.navigate('skyweave_compat:shaders/rebuild', ('Run again…',))
    failed = client.until(lambda: value if (value := operation())['terminal'] else None, 'invalid recipe operation did not settle')
    assert failed['state'] == 'FAILED' and not failed['hasResult'], failed
    results['malformedRecipeFailure'] = failed
    client.navigate('skyweave_compat:shaders/status', ('Start…',))
    status = client.until(lambda: value if (value := operation())['terminal'] else None, 'cached shader status did not settle')
    assert status['state'] == 'COMPLETED' and any(row['state'] == 'failed' and row['detail'] for row in status['result']), status
    assert broken.is_file(), 'read-only status altered the malformed recipe'
    results['readOnlyFailureStatus'] = status
    broken.unlink()
    client.navigate('skyweave_compat:shaders/rebuild', ('Run again…',))
    retry = client.until(lambda: value if (value := operation())['terminal'] else None, 'explicit native shader retry did not settle')
    assert retry['state'] == 'COMPLETED' and retry['hasResult'], retry
    results['explicitRetry'] = retry
    return results


def verify(client, config, migrated):
    client.until(lambda: client.state('misc').get('loaded'), 'integrated fixture did not join')
    client.act('map', 'misc')
    before = client.map()
    assert before['preferences']['zoomMaximum'] == 32 and before['skyweave$scale'] > .75, before
    live_connection = before['connectionIdentity']
    initial = digest(config)
    if migrated:
        client.open_form('skyweave_compat:files/maps/hoofprint/hoofprint_zoom')
        client.until(lambda: any(w.get('value') == '32' for w in client.state().get('widgets', [])),
                     'the authored bare JSON did not load in the shared form')
    else:
        client.act('open')
        client.field('', 'Hoofprint', 'Search settings…')
        client.wait_active('Hoofprint')
        client.click('Hoofprint')
    save = 'Save and apply' if migrated else 'Save changes'
    if migrated:
        client.edit_field('zoomMaximum', '32', '0.75')
    else:
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
        client.click('Done')
        client.ready_context()
        client.click('Done')
    else:
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
    previous = str(original['spacing'])
    client.until(lambda: any(w.get('value') == previous for w in client.state().get('widgets', [])),
                 'the authored generation profile did not load in the shared form')
    client.edit_field('spacing', previous, str(original['spacing'] + 100))
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
    client.until(lambda: any(w.get('value') == str(before['stepsPerTick']) for w in client.state().get('widgets', [])),
                 'integrated re:form did not load the saved Wayfinder policy')
    client.edit_field('stepsPerTick', str(before['stepsPerTick']), '128')
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


def verify_integrated_permission(client, config):
    observations = verify_integrated_policy(client, config)
    path = config / 'skyweave_compat-wayfinder.json'
    saved_hash = digest(path)
    client.edit_field('stepsPerTick', '128', '129')
    client.wait_active('Save and apply')

    def permission(action, allowed):
        response = client.act('reform-permission', action=action)
        sequence = response['sequence']
        proof = client.until(lambda: value if (value := client.state().get('permissionProof', {}))
                             .get('sequence') == sequence else None, 'Native permission change did not settle')
        assert 'error' not in proof, proof
        assert proof['sourceCanAdmin'] is allowed, proof
        client.until(lambda: client.state().get('clientCanAdmin') is allowed,
                     'The native permission packet did not update the client')
        return proof

    denied = permission('revoke', False)
    assert denied['nativeLevel'] == 0 and denied['nativeReadRefused'] and denied['nativeApplyRefused'], denied
    client.until(lambda: not any(widget['label'] == 'Save and apply' and widget['active']
                                 for widget in client.act('snapshot')['widgets']),
                 'The retained UI still admitted submission without native permission')
    assert digest(path) == saved_hash
    observations['permissionLoss'] = denied
    observations['deniedUi'] = client.state()
    restored = permission('restore', True)
    assert restored['server'] == denied['server'] and restored['actor'] == denied['actor']
    client.open_form('skyweave_compat:server/wayfinder', 'Running singleplayer server')
    client.until(lambda: any(widget.get('value') == '128' for widget in client.state()['widgets']),
                 'Explicit reopening did not retain the saved policy after permission restoration')
    assert digest(path) == saved_hash
    observations['permissionRestore'] = restored
    observations['refusedDraftDidNotPersist'] = json.loads(path.read_text())['stepsPerTick'] == 128
    return observations


def verify_startup_property(client, config):
    client.until(lambda: client.state('misc').get('loaded'), 'integrated fixture did not join')
    path = config / 'skyweave_compat-features.properties'
    before = path.read_bytes()
    client.open_form('skyweave_compat:features/navigation.wayfinder')
    client.act('reform-focus-field', field='navigation.wayfinder')
    client.act('reform-field-click', field='navigation.wayfinder')
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
    # The retained workspace initially displays startup switches. Other owners
    # remain unopened until the explicit native-document selection.
    assert {owner['route'] for owner in catalog['owners'] if owner['opened']} == {'skyweave_compat:features'}, catalog
    client.navigate('skyweave_compat:files/machines/redstone_link_gui/link_gui_common')
    client.until(lambda: any(w.get('value') == '24' for w in client.state().get('widgets', [])), 'native COMMON input did not load')
    client.edit_field('moveRange', '24', '25')
    assert native() == changed
    client.wait_active('Save native settings')
    client.click('Save native settings')
    common = client.until(lambda: value if (value := native())['commonMoveRange'] == 25 else None,
                          'native COMMON reader did not observe the saved value')
    common_receipt = client.proof('reform-receipt', 'reform-receipt.json')
    assert common['clientClickMode'] == changed['clientClickMode']
    assert common_receipt['savedRevision'] == 'sha256:' + digest(client.root / 'config/createredstonelinkgui-common.toml')
    return dict(before=before, after=changed, receipt=receipt, reader='Native ClientConfig.CLICK_MODE.get',
                common=common, commonReceipt=common_receipt, catalog=catalog)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ('java', 'clean', 'output', 'compat', 'probe'):
        parser.add_argument('--' + name, required=True, type=Path)
    parser.add_argument('--reform', type=Path)
    scenario = parser.add_mutually_exclusive_group()
    scenario.add_argument('--terrain-pair', action='store_true')
    scenario.add_argument('--integrated-policy', action='store_true')
    scenario.add_argument('--integrated-permission', action='store_true')
    scenario.add_argument('--startup-property', action='store_true')
    scenario.add_argument('--native-preferences', action='store_true')
    scenario.add_argument('--owner-surfaces', action='store_true')
    scenario.add_argument('--action-surfaces', action='store_true')
    parser.add_argument('--mod', action='append', required=True, type=Path)
    args = parser.parse_args()
    if args.output.exists():
        parser.error('Choose a new receipt path')
    if any((args.terrain_pair, args.integrated_policy, args.integrated_permission, args.startup_property, args.native_preferences,
            args.owner_surfaces, args.action_surfaces)) and not args.reform:
        parser.error('This settings scenario requires a re:form artifact')
    artifacts = [args.compat, args.probe, *args.mod]
    if args.reform:
        artifacts.append(args.reform)
    evidence = {'artifacts': {path.name: digest(path) for path in artifacts},
                'connectorCleanSha256': digest(args.clean),
                'scenario': ('action-surfaces' if args.action_surfaces else 'owner-surfaces' if args.owner_surfaces else 'native-preferences' if args.native_preferences else 'startup-property' if args.startup_property else 'terrain-pair' if args.terrain_pair
                             else 'integrated-permission' if args.integrated_permission else 'integrated-policy' if args.integrated_policy
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
                                         'renderDistance:2\nsimulationDistance:5\nmaxFps:40\n')
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
                if args.action_surfaces:
                    evidence['observations'] = verify_action_surfaces(client, config.parent)
                elif args.owner_surfaces:
                    evidence['observations'] = verify_owner_surfaces(client, config.parent)
                elif args.terrain_pair:
                    evidence['observations'] = verify_terrain_pair(client, config.parent)
                elif args.integrated_permission:
                    evidence['observations'] = verify_integrated_permission(client, config.parent)
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
                evidence.update(status='FAIL', error=str(failure), state=client.state(), misc=client.state('misc'),
                                partial=getattr(client, 'partial', None))
                if args.owner_surfaces and process.poll() is None:
                    try:
                        evidence['nativeFailure'] = client.proof('native-owner-readers', 'native-owner-readers.json')
                        evidence['editorFailure'] = client.proof('reform-editor-proof', 'reform-editor-proof.json')
                        evidence['savedVillageFailure'] = (config.parent / 'skyweave_compat-common.toml').read_text()
                    except Exception as diagnostic:
                        evidence['diagnosticFailure'] = str(diagnostic)
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
                        prefix = stream.read(131072).decode('utf-8', errors='replace').splitlines()
                        failures = [index for index, line in enumerate(prefix)
                                    if any(marker in line for marker in ('[ERROR]', '/ERROR]', 'Caused by:', 'ShaderOperation'))]
                        evidence['earlyDiagnostics'] = '\n'.join('\n'.join(prefix[max(0, index - 1):index + 12])
                                                                 for index in failures[:18])[:32768]
                        stream.seek(max(0, log.stat().st_size - 12500))
                        evidence['consoleTail'] = stream.read().decode('utf-8', errors='replace')
                evidence.update(exitCode=process.returncode, seconds=round(time.monotonic() - started, 3),
                                command=[Path(command[0]).name, *command[1:]])
                args.output.write_text(json.dumps(evidence, indent=2) + '\n')


def stop(_signum, _frame):
    # subprocess.wait retries InterruptedError; SystemExit reaches the caller's finally cleanup.
    raise SystemExit(128 + _signum)


if __name__ == '__main__':
    signal.signal(signal.SIGTERM, stop)
    signal.signal(signal.SIGINT, stop)
    main()
