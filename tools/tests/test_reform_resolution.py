"""Resolver receipts must describe the sources actually checked, including local adoption work."""
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from reform_resolution_probe import snapshot_working_tree


class ResolutionSnapshotTest(unittest.TestCase):
    def test_snapshot_includes_dirty_and_new_sources_but_not_ignored_or_deleted_files(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            repository, output = root / 'source', root / 'snapshot'
            repository.mkdir()
            subprocess.run(['git', 'init', '-q', str(repository)], check=True)
            for name, value in {'build.gradle': 'old build', 'gradle.properties': 'old pin',
                                'deleted.java': 'old source', '.gitignore': 'ignored/\n'}.items():
                (repository / name).write_text(value)
            subprocess.run(['git', 'add', '.'], cwd=repository, check=True)
            (repository / 'build.gradle').write_text('selected build')
            (repository / 'gradle.properties').write_text('selected pin')
            (repository / 'deleted.java').unlink()
            (repository / 'new.java').write_text('selected new source')
            (repository / 'ignored').mkdir()
            (repository / 'ignored/private.txt').write_text('excluded fixture data')
            receipt = snapshot_working_tree(repository, output)
            self.assertEqual('selected build', (output / 'build.gradle').read_text())
            self.assertEqual('selected pin', (output / 'gradle.properties').read_text())
            self.assertTrue((output / 'new.java').is_file())
            self.assertFalse((output / 'ignored').exists())
            self.assertFalse((output / 'deleted.java').exists())
            self.assertFalse((output / '.git').exists())
            (repository / 'new.java').write_text('changed selection')
            changed = snapshot_working_tree(repository, root / 'second')
            self.assertNotEqual(receipt['sha256'], changed['sha256'])
            (repository / 'unsafe.java').symlink_to(root / 'outside')
            with self.assertRaisesRegex(ValueError, 'regular file'):
                snapshot_working_tree(repository, root / 'third')
