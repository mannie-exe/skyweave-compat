"""A final native acknowledgement must survive process exit without accepting a missing reply."""
import json
import os
from pathlib import Path
import signal
import subprocess
import sys
import tempfile
from types import SimpleNamespace
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from reform_probe import Client


class ClientCompletionTest(unittest.TestCase):
    @unittest.skipUnless(os.name == 'posix', 'Requires POSIX signal delivery')
    def test_stop_interrupts_a_child_wait_and_runs_owned_cleanup(self):
        script = '''
import json, pathlib, signal, subprocess, sys
sys.path.insert(0, sys.argv[1])
from reform_probe import stop
signal.signal(signal.SIGTERM, stop)
child = subprocess.Popen([sys.executable, '-c', 'import time; time.sleep(30)'])
try:
    print(child.pid, flush=True)
    child.wait(timeout=30)
finally:
    child.terminate()
    child.wait(timeout=3)
    pathlib.Path(sys.argv[2]).write_text(json.dumps({'terminal': child.poll() is not None}))
'''
        with tempfile.TemporaryDirectory() as directory:
            marker = Path(directory) / 'cleanup.json'
            process = subprocess.Popen([sys.executable, '-c', script, str(Path(__file__).resolve().parents[1]), str(marker)],
                                       stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, start_new_session=True)
            try:
                self.assertTrue(process.stdout.readline().strip().isdigit())
                process.send_signal(signal.SIGTERM)
                self.assertEqual(143, process.wait(timeout=2))
                self.assertTrue(json.loads(marker.read_text())['terminal'])
            finally:
                try:
                    os.killpg(process.pid, signal.SIGTERM)
                except ProcessLookupError:
                    pass
                try:
                    process.communicate(timeout=4)
                except subprocess.TimeoutExpired:
                    os.killpg(process.pid, signal.SIGKILL)
                    process.communicate(timeout=2)

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
