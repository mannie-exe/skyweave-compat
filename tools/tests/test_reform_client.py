"""A final native acknowledgement must survive process exit without accepting a missing reply."""
import json
from pathlib import Path
import sys
import tempfile
from types import SimpleNamespace
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from reform_probe import Client


class ClientCompletionTest(unittest.TestCase):
    def test_final_acknowledgement_is_observed_after_the_native_process_exits(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / 'config-review-state.json').write_text(json.dumps({'sequence': 1}))
            client = Client(root, SimpleNamespace(poll=lambda: 0))
            self.assertEqual(1, client.act('quit')['sequence'])

    def test_clean_exit_without_the_requested_acknowledgement_is_not_success(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / 'config-review-state.json').write_text(json.dumps({'sequence': 0}))
            with self.assertRaisesRegex(RuntimeError, 'was not observed'):
                Client(root, SimpleNamespace(poll=lambda: 0)).act('quit')

    def test_a_final_error_acknowledgement_is_still_a_failure(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / 'config-review-state.json').write_text(json.dumps({'sequence': 1, 'error': 'controlled failure'}))
            with self.assertRaisesRegex(RuntimeError, 'controlled failure'):
                Client(root, SimpleNamespace(poll=lambda: 0)).act('quit')
