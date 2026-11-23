#!/usr/bin/env python3
"""Check release metadata and ensure fixture/upstream classes are not bundled."""
import json
import sys
import tomllib
import zipfile
from pathlib import Path

root = Path(__file__).resolve().parents[1]
version = sys.argv[1]
reform_version = sys.argv[2]
reform_minimum = sys.argv[3]
reform_sha256 = sys.argv[4]
with zipfile.ZipFile(root / f'build/libs/skyweave-compat-{version}-mc1.21.1-neoforge.jar') as jar:
    names = set(jar.namelist())
    metadata = tomllib.loads(jar.read('META-INF/neoforge.mods.toml').decode())
    assert metadata['license'] == 'Apache-2.0 AND LGPL-3.0-only AND MIT'
    assert metadata['mods'][0]['authors'] == 'inherent.design contributors'
    assert '#credits' in metadata['mods'][0]['credits']
    assert metadata['mods'][0]['version'] == version
    deps = {entry['modId']: entry for entry in metadata['dependencies']['skyweave_compat']}
    assert deps['minecraft']['versionRange'] == '[1.21.1]'
    required = {'minecraft', 'neoforge', 'reform'}
    assert {name for name, d in deps.items() if d['type'] == 'required'} == required
    assert deps['reform']['versionRange'] == f'[{reform_minimum},0.4)'
    assert deps['reform']['side'] == 'BOTH'
    development = dict(line.split('=', 1) for line in jar.read('META-INF/skyweave-compat-development.properties').decode().splitlines() if line)
    assert development['reform'] == reform_version
    assert development['reform.minimum'] == reform_minimum
    assert development['reform.sha256'] == reform_sha256
    assert development['minecraft'] == '1.21.1'
    assert metadata['loaderVersion'] == '[4,5)'
    assert deps['neoforge']['versionRange'] == f"[{development['neoforge']},21.2)"
    assert not any(name.startswith('design/inherent/reform/') for name in names), 're:form is a separate base library'
    retired = ['client/ConfigurationOverview', 'client/SettingsPanel', 'client/SettingsControls',
               'client/ServerSettingsView', 'configuration/JsonEdits', 'configuration/RegisteredEdits',
               'configuration/StartupEdits', 'configuration/SettingsCommit', 'configuration/EditSession',
               'configuration/ServerSettingsContext']
    assert not any(any(name.startswith('design/inherent/skyweavecompat/' + entry + '.') or
                       name.startswith('design/inherent/skyweavecompat/' + entry + '$')
                       for entry in retired) for name in names), 'Retired settings belong only to baseline fixtures'
    for name, dependency in deps.items():
        if name not in required:
            assert dependency['type'] == 'optional', name
            assert dependency['versionRange'] == '[0,)', name
    assert not any(name.startswith('data/') and not name.endswith('/') for name in names), 'Integration data must be gated'
    for group in ['terrain', 'ores', 'pressure', 'linear']:
        assert f'packs/{group}/pack.mcmeta' in names
    assert 'packs/terrain/data/skyweave_compat/worldgen/world_preset/islands_v1.json' in names
    assert 'packs/pressure/data/skyweave_compat/dimension_physics/islands_v1.json' in names
    assert 'seedrenderer' not in deps
    assert not any('/skyweavecompat/lod/' in n or '/mixin/lod/' in n or '/clientprobe/' in n for n in names)
    with zipfile.ZipFile(root / f'build/libs/skyweave-compat-{version}-sources.jar') as sources:
        for notice in ['LICENSE', 'NOTICE', 'licenses/LICENSE.txt', 'licenses/LICENSE.LESSER.txt', 'licenses/BlueMap-MIT.txt']:
            expected = (root / notice).read_bytes()
            assert jar.read(notice) == expected and sources.read(notice) == expected, notice
    assert not any(n.startswith(('dev/ftb/', 'org/sathrek/', 'com/ishland/', 'com/aureljz/', 'de/bluecolored/', 'com/palm1/', 'com/b1n_ry/', 'me/shedaniel/', 'com/simibubi/', 'com/copycatsplus/', 'com/supermartijn642/', 'fr0nick/', 'dev/lambdaurora/', 'net/caffeinemc/', 'dev/djefrey/', 'dev/engine_room/', 'com/bearing/', 'com/mcwbridges/', 'dev/simulated_team/', 'dev/ryanhcode/', 'net/createmod/', 'net/sprocketgames/')) or '/probe/' in n or n.endswith('.jar') for n in names)
    mixin = json.loads(jar.read('skyweave_compat.mixins.json'))
    assert mixin['required'] and mixin['injectors']['defaultRequire'] == 1
    assert mixin['mixins'] == [] and mixin['client'] == [], 'Every target hook must be selected at startup'
    assert mixin['plugin'] == 'design.inherent.skyweavecompat.OptionalMixinPlugin'
    assert 'design/inherent/skyweavecompat/mixin/StructureSupportMixin.class' in names
print('Release metadata and package boundaries verified')
