"""Shared packaged-client inputs; importing this module never prepares or launches a client."""
from pathlib import Path
import os
import shlex
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


def packaged_bootstrap(root):
    """Retain pinned ModDev bootstrap libraries; gameplay comes only from staged JARs."""
    moddev = REPOSITORY / 'build/moddev'
    def library(path):
        if any(str(REPOSITORY / name) in path for name in ('build/classes', 'build/resources')):
            return False
        return not (mod_ids(Path(path)) - {'minecraft', 'neoforge'})
    classpath = shlex.split((moddev / 'clientRunClasspath.txt').read_text())
    for i, value in enumerate(classpath):
        if i and classpath[i - 1] in ('-cp', '-classpath', '--class-path'):
            classpath[i] = os.pathsep.join(path for path in value.split(os.pathsep) if library(path))
    argument_file(root / 'classpath.args', classpath)
    legacy = (moddev / 'clientLegacyClasspath.txt').read_text().splitlines()
    (root / 'legacy-classpath.txt').write_text('\n'.join(path for path in legacy if library(path)) + '\n')
    vm = [value for value in shlex.split((moddev / 'clientRunVmArgs.txt').read_text())
          if not value.startswith('-DlegacyClassPath.file=')]
    vm.append('-DlegacyClassPath.file=' + str(root / 'legacy-classpath.txt'))
    program = shlex.split((moddev / 'clientRunProgramArgs.txt').read_text(), comments=True)
    return vm, program
