"""Copy authored inputs into disposable native fixtures without following output links."""
import hashlib
import json
import re
import shutil
from pathlib import Path


def copy_owned(source, destination, root):
    source, destination, root = Path(source), Path(destination), Path(root).resolve()
    if source.is_symlink() or not source.is_file():
        raise ValueError(f'Expected a regular fixture input: {source}')
    if destination.name in {'.', '..', ''} or not destination.parent.resolve().is_relative_to(root):
        raise ValueError('Fixture output parent escapes the owned root')
    if destination.exists() and not destination.is_symlink() and not destination.is_file():
        raise ValueError('Expected an exact fixture output file, not a directory')
    destination.parent.mkdir(parents=True, exist_ok=True)
    if destination.is_symlink():
        destination.unlink()
    shutil.copy2(source, destination)


def stage_configs(pack, root):
    pack, root = Path(pack).resolve(), Path(root).resolve()
    if not (pack / 'empack.yml').is_file():
        raise ValueError('Expected an actual authored pack root')
    inputs = {}
    for side in ['common', 'server']:
        directory = pack / 'overrides' / side
        for source in sorted((directory / 'config').rglob('*')):
            if not source.is_file() or source.name == '.DS_Store':
                continue
            relative = source.relative_to(directory)
            copy_owned(source, root / relative, root)
            inputs[relative.as_posix()] = {'layer': side, 'authoredSha256': hashlib.sha256(source.read_bytes()).hexdigest()}
    # Host web listeners/downloads are not part of terrain/configuration qualification.
    for relative, text in {'config/bluemap/webserver.conf': 'enabled: false\n',
                           'config/bluemap/core.conf': 'accept-download: false\nmetrics: false\n'}.items():
        if relative in inputs:
            (root / relative).write_text(text)
            inputs[relative]['fixtureOverride'] = 'Disable fixture web listener/downloads; not pack-rule qualification'
    for relative, row in inputs.items():
        row['beforeLaunchSha256'] = hashlib.sha256((root / relative).read_bytes()).hexdigest()
    return inputs


def stage_replay(regions, database, root, seed):
    """Replay marked region copies and a cache, never source save identity or player state."""
    root = Path(root).resolve()
    inputs = {}
    if regions:
        regions = Path(regions).resolve()
        manifest = json.loads((regions / 'replay-fixture.json').read_text())
        if int(manifest['seed']) != seed or len(manifest['regions']) > 16:
            raise ValueError('Replay seed or bounded region list does not match')
        for entry in manifest['regions']:
            name = entry['file']
            if not re.fullmatch(r'r\.-?\d+\.-?\d+\.mca', name):
                raise ValueError('Invalid replay region name')
            source = regions / 'region' / name
            if hashlib.sha256(source.read_bytes()).hexdigest() != entry['sha256']:
                raise ValueError('Replay region digest changed')
            destination = root / 'probe-world/region' / name
            if destination.exists() or destination.is_symlink():
                raise ValueError('Replay cannot replace an existing fixture region')
            copy_owned(source, destination, root)
            inputs[name] = entry['sha256']
    if database:
        database = Path(database)
        copy_owned(database, root / 'probe-world/data/DistantHorizons.sqlite', root)
        inputs['DistantHorizons.sqlite'] = hashlib.sha256(database.read_bytes()).hexdigest()
    return inputs
