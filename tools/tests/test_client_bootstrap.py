import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

TOOLS = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(TOOLS))
from client_bootstrap import argument_file


class ClientBootstrapTest(unittest.TestCase):
    def test_runner_help_is_available_without_a_fixture_or_launch(self):
        for name in ('combat_probe.py', 'reform_probe.py', 'reform_resolution_probe.py'):
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
