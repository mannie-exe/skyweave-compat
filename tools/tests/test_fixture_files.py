from pathlib import Path
import sys
import tempfile
import unittest
import hashlib
import json

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from fixture_files import copy_owned, stage_replay


class FixtureFilesTest(unittest.TestCase):
    def test_replay_binds_seed_digest_and_only_declared_inputs(self):
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory);source=root/'snapshot';(source/'region').mkdir(parents=True)
            data=b'owned saved region';name='r.-12.58.mca'
            (source/'region'/name).write_bytes(data);(source/'level.dat').write_bytes(b'never copy identity')
            manifest={'seed':'42','regions':[{'file':name,'sha256':hashlib.sha256(data).hexdigest()}]}
            (source/'replay-fixture.json').write_text(json.dumps(manifest))
            destination=root/'run';destination.mkdir()
            stage_replay(source,None,destination,42)
            self.assertEqual((destination/'probe-world/region'/name).read_bytes(),data)
            self.assertFalse((destination/'probe-world/level.dat').exists())
            with self.assertRaises(ValueError):stage_replay(source,None,destination,42)
            with self.assertRaises(ValueError):stage_replay(source,None,destination,43)
            (source/'region'/name).write_bytes(b'changed')
            with self.assertRaises(ValueError):stage_replay(source,None,destination,42)

    def test_replacing_fixture_link_never_writes_to_readonly_input(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            original = root / 'input.jar'; original.write_bytes(b'original installed input')
            candidate = root / 'candidate.jar'; candidate.write_bytes(b'candidate')
            fixture = root / 'fixture'; fixture.mkdir()
            output = fixture / 'mod.jar'; output.symlink_to(original)
            # Explicitly selecting an already-linked installed binary must copy it locally,
            # rather than following the link into a SameFileError or input replacement.
            copy_owned(original, output, fixture)
            self.assertFalse(output.is_symlink())
            self.assertEqual(original.read_bytes(), b'original installed input')
            output.unlink(); output.symlink_to(original)
            copy_owned(candidate, output, fixture)
            self.assertFalse(output.is_symlink())
            self.assertEqual(original.read_bytes(), b'original installed input')
            self.assertEqual(output.read_bytes(), b'candidate')

    def test_parent_links_and_input_links_are_refused_before_mutation(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            original = root / 'original'; original.write_bytes(b'keep')
            fixture = root / 'fixture'; fixture.mkdir()
            (fixture / 'outside').symlink_to(root, target_is_directory=True)
            with self.assertRaises(ValueError):
                copy_owned(original, fixture / 'outside' / 'original', fixture)
            input_link = root / 'linked'; input_link.symlink_to(original)
            with self.assertRaises(ValueError):
                copy_owned(input_link, fixture / 'copy', fixture)
            with self.assertRaises(ValueError):
                copy_owned(original, fixture / '..', fixture)
            directory = fixture / 'directory'; directory.mkdir()
            (directory / original.name).symlink_to(original)
            with self.assertRaises(ValueError):
                copy_owned(original, directory, fixture)
            self.assertEqual(original.read_bytes(), b'keep')
