"""Shared packaged-client inputs; importing this module never prepares or launches a client."""
from pathlib import Path
import hashlib
import json
import os
import shlex
import tomllib
import zipfile

REPOSITORY = Path(__file__).resolve().parents[1]


def connector_bootstrap(vm, clean):
    """Validate Connector's existing development input before adding a launch property."""
    if clean is None:
        return None
    clean = clean.resolve(strict=True)
    metadata = clean.with_name(clean.name.removesuffix('_output.jar') + '.txt')
    description = json.loads(metadata.read_text())
    if (description.get('type') != 'rename' or not description.get('components', {}).get('versionManifest', {})
            .get('annotation', '').endswith('minecraft_1.21.1_version_manifest.json')):
        raise ValueError('Connector needs the clean Minecraft 1.21.1 NeoForm rename output and its provenance')
    with zipfile.ZipFile(clean) as archive:
        if b'1.21.1' not in archive.read('net/minecraft/SharedConstants.class'):
            raise ValueError('Connector clean artifact has an unexpected Minecraft identity')
    receipt = {'sha256': hashlib.sha256(clean.read_bytes()).hexdigest(),
               'neoformMetadataSha256': hashlib.sha256(metadata.read_bytes()).hexdigest(), 'mc': '1.21.1'}
    vm.append('-Dconnector.clean.path=' + str(clean))
    return receipt


def mod_ids(path):
    if not path.is_file() or path.suffix != '.jar':
        return set()
    with zipfile.ZipFile(path) as archive:
        ids = set()
        for name in ('META-INF/neoforge.mods.toml', 'META-INF/mods.toml'):
            if name in archive.namelist():
                ids.update(mod['modId'] for mod in tomllib.loads(archive.read(name).decode()).get('mods', []))
        if 'fabric.mod.json' in archive.namelist():
            ids.add(json.loads(archive.read('fabric.mod.json'))['id'])
        return ids


def argument_file(path, arguments):
    path.write_text('\n'.join('"' + value.replace('\\', '\\\\').replace('"', '\\"') + '"' for value in arguments) + '\n')


def game_arguments(root, arguments):
    """Bind the generated launch to this caller-owned disposable game directory."""
    arguments = list(arguments)
    matches = [i for i, value in enumerate(arguments) if value == '--gameDir']
    if len(matches) > 1 or any(value.startswith('--gameDir=') for value in arguments):
        raise ValueError('Ambiguous prepared native game directory')
    if matches:
        index = matches[0] + 1
        if index == len(arguments) or arguments[index].startswith('--'):
            raise ValueError('Missing prepared native game directory')
        arguments[index] = str(root.resolve(strict=True))
    else:
        arguments.extend(['--gameDir', str(root.resolve(strict=True))])
    return arguments


def native_classpath(moddev):
    prepared = moddev / 'clientRunClasspath.json'
    if prepared.is_file():
        value = json.loads(prepared.read_text(encoding='utf-8'))
        arguments = value.get('arguments') if isinstance(value, dict) else None
        if (not isinstance(value, dict) or value.get('schemaVersion') != 1 or not isinstance(arguments, list) or len(arguments) != 2
                or arguments[0] != '-cp' or not isinstance(arguments[1], str) or not arguments[1]):
            raise ValueError('Invalid prepared native client classpath; run ./gradlew preparePackagedClient')
        return arguments
    historical = moddev / 'clientRunClasspath.txt'
    if not historical.is_file():
        raise FileNotFoundError('Prepare native client inputs with ./gradlew preparePackagedClient')
    return shlex.split(historical.read_text(encoding='utf-8'))


def packaged_bootstrap(root):
    """Retain pinned ModDev bootstrap libraries; gameplay comes only from staged JARs."""
    moddev = REPOSITORY / 'build/moddev'
    def library(path):
        if any(str(REPOSITORY / name) in path for name in ('build/classes', 'build/resources')):
            return False
        return not (mod_ids(Path(path)) - {'minecraft', 'neoforge'})
    classpath = native_classpath(moddev)
    for i, value in enumerate(classpath):
        if i and classpath[i - 1] in ('-cp', '-classpath', '--class-path'):
            classpath[i] = os.pathsep.join(path for path in value.split(os.pathsep) if library(path))
    argument_file(root / 'classpath.args', classpath)
    legacy = (moddev / 'clientLegacyClasspath.txt').read_text().splitlines()
    (root / 'legacy-classpath.txt').write_text('\n'.join(path for path in legacy if library(path)) + '\n')
    vm = [value for value in shlex.split((moddev / 'clientRunVmArgs.txt').read_text())
          if not value.startswith('-DlegacyClassPath.file=')]
    vm.append('-DlegacyClassPath.file=' + str(root / 'legacy-classpath.txt'))
    program = game_arguments(root, shlex.split((moddev / 'clientRunProgramArgs.txt').read_text(), comments=True))
    return vm, program
