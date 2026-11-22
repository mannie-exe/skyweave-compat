import shutil
import json
import os
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch
import zipfile
from pathlib import Path

TOOLS = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(TOOLS))
from client_bootstrap import argument_file, connector_bootstrap, packaged_bootstrap, native_classpath, game_arguments, mod_ids


class ClientBootstrapTest(unittest.TestCase):
    def test_connector_input_requires_matching_provenance_and_identity_without_partial_arguments(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary); clean = root / 'rename_fixture_output.jar'; metadata = root / 'rename_fixture.txt'
            metadata.write_text(json.dumps({'type': 'rename', 'components': {'versionManifest': {'annotation': 'minecraft_1.21.1_version_manifest.json'}}}))
            with zipfile.ZipFile(clean, 'w') as archive:
                archive.writestr('net/minecraft/SharedConstants.class', b'fixture1.21.1')
            vm = []
            receipt = connector_bootstrap(vm, clean)
            self.assertEqual(['-Dconnector.clean.path=' + str(clean.resolve())], vm)
            self.assertEqual('1.21.1', receipt['mc'])
            with zipfile.ZipFile(clean, 'w') as archive:
                archive.writestr('net/minecraft/SharedConstants.class', b'other game version')
            vm = []
            with self.assertRaisesRegex(ValueError, 'Minecraft identity'): connector_bootstrap(vm, clean)
            self.assertEqual([], vm)
            metadata.write_text('{"type":"patched"}')
            with self.assertRaisesRegex(ValueError, 'provenance'): connector_bootstrap(vm, clean)
            self.assertEqual([], vm)
    def test_game_directory_is_explicit_and_cannot_retain_a_different_prepared_target(self):
        with tempfile.TemporaryDirectory(prefix='owned Ünicode fixture ') as temporary:
            root = Path(temporary)
            self.assertEqual(['--gameDir', str(root.resolve())], game_arguments(root, []))
            self.assertEqual(['--username', 'Name', '--gameDir', str(root.resolve())],
                             game_arguments(root, ['--username', 'Name', '--gameDir', '/unrelated/world']))
            for arguments in (['--gameDir'], ['--gameDir', '--username', 'Name'],
                              ['--gameDir=a'], ['--gameDir', 'a', '--gameDir', 'b']):
                with self.assertRaises(ValueError): game_arguments(root, arguments)

    def test_fabric_gameplay_metadata_is_not_a_bootstrap_library(self):
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / 'foreign.jar'
            with zipfile.ZipFile(path, 'w') as archive:
                archive.writestr('fabric.mod.json', json.dumps({'id': 'foreign'}))
            self.assertEqual({'foreign'}, mod_ids(path))
    def test_prepared_classpath_preserves_literal_paths_and_refuses_malformed_current_input(self):
        with tempfile.TemporaryDirectory(prefix='native classpath ') as temporary:
            root = Path(temporary)
            arguments = ['-cp', os.pathsep.join(['space and Ünicode', r'path\with\slashes'])]
            prepared = root / 'clientRunClasspath.json'
            prepared.write_text(json.dumps({'schemaVersion': 1, 'arguments': arguments}), encoding='utf-8')
            self.assertEqual(arguments, native_classpath(root))
            argument_file(root / 'clientRunClasspath.txt', ['-cp', 'stale'])
            for value in [[], {'schemaVersion': 2, 'arguments': arguments},
                          {'schemaVersion': 1, 'arguments': ['-other', 'value']},
                          {'schemaVersion': 1, 'arguments': ['-cp', None]},
                          {'schemaVersion': 1, 'arguments': ['-cp', '']}]:
                prepared.write_text(json.dumps(value), encoding='utf-8')
                with self.assertRaises(ValueError): native_classpath(root)

    def test_missing_launch_preparation_has_an_actionable_error(self):
        with tempfile.TemporaryDirectory() as temporary:
            with self.assertRaisesRegex(FileNotFoundError, 'preparePackagedClient'):
                native_classpath(Path(temporary))

    def test_packaged_bootstrap_excludes_gameplay_and_source_roots(self):
        with tempfile.TemporaryDirectory(prefix='packaged client ') as temporary:
            root=Path(temporary);repository=root/'repository';moddev=repository/'build/moddev';moddev.mkdir(parents=True)
            paths=[]
            for name,mod_id in [('library',None),('minecraft','minecraft'),('loader','neoforge'),('content','foreign')]:
                path=root/(name+'.jar')
                with zipfile.ZipFile(path,'w') as archive:
                    if mod_id:archive.writestr('META-INF/neoforge.mods.toml',f'[[mods]]\nmodId="{mod_id}"\n')
                paths.append(str(path))
            paths += [str(repository/'build/classes/java/main'),str(repository/'build/resources/main')]
            argument_file(moddev/'clientRunClasspath.txt',['-cp',os.pathsep.join(paths)])
            (moddev/'clientLegacyClasspath.txt').write_text('\n'.join(paths))
            argument_file(moddev/'clientRunVmArgs.txt',['-DlegacyClassPath.file=retired','-Dother=retained'])
            argument_file(moddev/'clientRunProgramArgs.txt',['--username','Literal User'])
            game=root/'game';game.mkdir()
            with patch('client_bootstrap.REPOSITORY',repository):vm,program=packaged_bootstrap(game)
            self.assertEqual(paths[:3],(game/'legacy-classpath.txt').read_text().splitlines())
            classpath=(game/'classpath.args').read_text()
            self.assertNotIn('content.jar',classpath);self.assertNotIn('build/classes',classpath);self.assertNotIn('build/resources',classpath)
            self.assertEqual(['-Dother=retained','-DlegacyClassPath.file='+str(game/'legacy-classpath.txt')],vm)
            self.assertEqual(['--username','Literal User','--gameDir',str(game.resolve())],program)

    def test_runner_help_is_available_without_a_fixture_or_launch(self):
        for name in ('rendering_probe.py', 'reform_probe.py', 'reform_workspace_probe.py', 'reform_resolution_probe.py',
                     'ipn_probe.py', 'settings_refinement_probe.py', 'world_scope_probe.py', 'world_scope_client.py'):
            with self.subTest(runner=name):
                result = subprocess.run([sys.executable, str(TOOLS / name), '--help'],
                                        text=True, capture_output=True, timeout=10)
                self.assertEqual(0, result.returncode, result.stderr)
                self.assertIn('usage:', result.stdout)

    @unittest.skipUnless(shutil.which('java'), 'Native Java is required for argument-file verification')
    def test_native_java_receives_literal_arguments_from_a_path_with_spaces(self):
        with tempfile.TemporaryDirectory(prefix='client argument check ') as temporary:
            root = Path(temporary)
            source = root / 'Arguments.java'
            source.write_text('class Arguments { public static void main(String[] args) { '
                              'for (String value : args) System.out.println(value); } }\n')
            values = ['white space', 'quoted "value"', r'path\with\slashes', '#literal', 'Ünicode', '']
            arguments = root / 'launch args.txt'
            argument_file(arguments, ['--source', '21', str(source), *values])
            result = subprocess.run([shutil.which('java'), '@' + str(arguments)],
                                    text=True, capture_output=True, timeout=30)
            self.assertEqual(0, result.returncode, result.stderr)
            self.assertEqual(values, result.stdout.splitlines())


if __name__ == '__main__':
    unittest.main()
