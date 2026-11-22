import shutil
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
from client_bootstrap import argument_file, packaged_bootstrap


class ClientBootstrapTest(unittest.TestCase):
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
            self.assertEqual(['--username','Literal User'],program)

    def test_runner_help_is_available_without_a_fixture_or_launch(self):
        for name in ('combat_probe.py', 'reform_probe.py', 'reform_resolution_probe.py', 'world_scope_probe.py', 'world_scope_client.py'):
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
