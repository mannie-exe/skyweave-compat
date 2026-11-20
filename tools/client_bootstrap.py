"""Shared packaged-client inputs; importing this module never prepares or launches a client."""
from pathlib import Path
import tomllib
import zipfile

REPOSITORY = Path(__file__).resolve().parents[1]


def mod_ids(path):
    if not path.is_file() or path.suffix != '.jar':
        return set()
    with zipfile.ZipFile(path) as archive:
        for name in ('META-INF/neoforge.mods.toml', 'META-INF/mods.toml'):
            if name in archive.namelist():
                return {mod['modId'] for mod in tomllib.loads(archive.read(name).decode()).get('mods', [])}
    return set()


def argument_file(path, arguments):
    path.write_text('\n'.join('"' + value.replace('\\', '\\\\').replace('"', '\\"') + '"' for value in arguments) + '\n')
