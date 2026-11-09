#!/usr/bin/env python3
"""Check release metadata and ensure fixture/upstream classes are not bundled."""
import json
import sys
import tomllib
import zipfile
from pathlib import Path

root = Path(__file__).resolve().parents[1]
version = sys.argv[1]
with zipfile.ZipFile(root / f'build/libs/skyweave-compat-{version}-mc1.21.1-neoforge.jar') as jar:
    names = set(jar.namelist())
    metadata = tomllib.loads(jar.read('META-INF/neoforge.mods.toml').decode())
    assert metadata['mods'][0]['version'] == version
    deps = {entry['modId']: entry for entry in metadata['dependencies']['skyweave_compat']}
    assert deps['minecraft']['versionRange'] == '[1.21.1]'
    assert {name for name, d in deps.items() if d['type'] == 'required'} == {'minecraft', 'neoforge'}
    for name, dependency in deps.items():
        if name not in {'minecraft', 'neoforge'}:
            assert dependency['type'] == 'optional', name
            assert dependency['versionRange'] == '[0,)', name
    assert not any(name.startswith('data/') and not name.endswith('/') for name in names), 'Integration data must be gated'
    for group in ['terrain', 'ores', 'pressure', 'linear']:
        assert f'packs/{group}/pack.mcmeta' in names
    assert 'packs/terrain/data/skyweave_compat/worldgen/world_preset/islands_v1.json' in names
    assert 'packs/pressure/data/skyweave_compat/dimension_physics/islands_v1.json' in names
    assert 'seedrenderer' not in deps
    assert not any('/skyweavecompat/lod/' in n or '/mixin/lod/' in n or '/clientprobe/' in n for n in names)
    assert 'LICENSE' in names
    assert not any(n.startswith(('dev/ftb/', 'org/sathrek/', 'com/ishland/', 'com/aureljz/', 'de/bluecolored/', 'com/palm1/', 'com/b1n_ry/', 'me/shedaniel/', 'com/simibubi/', 'com/supermartijn642/', 'fr0nick/', 'dev/lambdaurora/', 'net/caffeinemc/', 'dev/djefrey/', 'dev/engine_room/', 'com/bearing/', 'com/mcwbridges/', 'dev/simulated_team/', 'dev/ryanhcode/', 'net/createmod/')) or '/probe/' in n or n.endswith('.jar') for n in names)
    mixin = json.loads(jar.read('skyweave_compat.mixins.json'))
    assert mixin['required'] and mixin['injectors']['defaultRequire'] == 1
    assert mixin['mixins'] == [] and mixin['client'] == [], 'Every target hook must be selected at startup'
    assert mixin['plugin'] == 'design.inherent.skyweavecompat.OptionalMixinPlugin'
    assert 'design/inherent/skyweavecompat/mixin/StructureSupportMixin.class' in names
print('Release metadata and package boundaries verified')
