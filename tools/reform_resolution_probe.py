#!/usr/bin/env python3
"""Check the default re:form release resolver in a disposable clean source checkout."""
import argparse
from contextlib import ExitStack
import hashlib
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
import io
import json
import os
from pathlib import Path
import shutil
import subprocess
import tarfile
import tempfile
import threading

REPOSITORY = Path(__file__).resolve().parents[1]
RELEASE_URL = 'https://github.com/mannie-exe/reform/releases/download'
INIT = '''
gradle.projectsEvaluated {
    def p = gradle.rootProject
    assert !p.hasProperty('reform_repository'): 'A development repository override was supplied'
    def repo = p.repositories.find { it.name == 'reformRelease' }
    assert repo instanceof org.gradle.api.artifacts.repositories.IvyArtifactRepository
    assert repo.url.toString() == 'https://github.com/mannie-exe/reform/releases/download'
    def mirror = System.getenv('REFORM_MIRROR_URL')
    def publicOnly = System.getenv('REFORM_PUBLIC_ONLY') == 'true'
    assert !(publicOnly && mirror): 'Public verification cannot use a mirror'
    if (mirror) {
        repo.url = p.uri(mirror)
        repo.allowInsecureProtocol = true
    }
    p.tasks.register('verifyCandidateDependency') {
        doLast {
            def version = p.providers.gradleProperty('reform_version').get()
            def artifact = p.configurations.compileClasspath.resolvedConfiguration.resolvedArtifacts.find {
                it.moduleVersion.id.group == 'design.inherent' && it.name == 'reform'
            }
            assert artifact != null && artifact.moduleVersion.id.version == version
            def digest = java.security.MessageDigest.getInstance('SHA-256').digest(artifact.file.bytes).encodeHex().toString()
            assert digest == System.getenv('REFORM_CANDIDATE_SHA256')
            def result = [coordinate: "design.inherent:reform:${version}", file: artifact.file.name, sha256: digest,
                          repository: repo.url.toString(), publicOnly: publicOnly]
            p.layout.buildDirectory.file('reform-resolution.json').get().asFile.with {
                parentFile.mkdirs(); text = groovy.json.JsonOutput.toJson(result)
            }
        }
    }
}
'''


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--library', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--public', action='store_true', help='Use the declared public release repository without a local mirror')
    args = parser.parse_args()
    if args.output.exists():
        parser.error('Choose a new receipt path')
    source = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=REPOSITORY, text=True).strip()
    archive = subprocess.check_output(['git', 'archive', '--format=tar', source], cwd=REPOSITORY)
    jar = args.library.resolve(strict=True)
    digest = hashlib.sha256(jar.read_bytes()).hexdigest()
    requests = []
    evidence = dict(source=source, librarySha256=digest, releaseUrl=RELEASE_URL,
                    qualification=('Public release resolution with no development override or loopback repository.' if args.public else
                                   'Local mirror proves default resolver mechanics and exact candidate bytes; public release availability is not exercised.'))
    with tempfile.TemporaryDirectory(prefix='skyweave-reform-resolution-') as temporary:
        root = Path(temporary)
        checkout = root / 'checkout'
        checkout.mkdir()
        with tarfile.open(fileobj=io.BytesIO(archive)) as bundle:
            bundle.extractall(checkout, filter='data')
        properties = dict(line.split('=', 1) for line in (checkout / 'gradle.properties').read_text().splitlines()
                          if '=' in line and not line.startswith('#'))
        version = properties['reform_version']
        asset = f'reform-{version}-mc1.21.1-neoforge.jar'
        mirror = root / 'mirror'
        target = mirror / f'releases/download/v{version}/{asset}'
        if not args.public:
            target.parent.mkdir(parents=True)
            shutil.copy2(jar, target)

        class Handler(SimpleHTTPRequestHandler):
            def __init__(self, *values, **keywords):
                super().__init__(*values, directory=mirror, **keywords)

            def log_request(self, code='-', size='-'):
                requests.append(dict(method=self.command, path=self.path, status=code))

            def log_message(self, *_values):
                pass

        with ExitStack() as resources:
            server = None
            env = dict(os.environ)
            env.pop('REFORM_MIRROR_URL', None)
            env.update(REFORM_CANDIDATE_SHA256=digest, REFORM_PUBLIC_ONLY=str(args.public).lower())
            if not args.public:
                server = resources.enter_context(ThreadingHTTPServer(('127.0.0.1', 0), Handler))
                worker = threading.Thread(target=server.serve_forever, daemon=True)
                worker.start()
                env['REFORM_MIRROR_URL'] = f'http://127.0.0.1:{server.server_port}/releases/download'
            init = root / 'resolver.gradle'
            init.write_text(INIT)
            command = ['./gradlew', '--no-daemon', '--init-script', str(init), 'verifyCandidateDependency']
            try:
                result = subprocess.run(command, cwd=checkout, env=env, text=True,
                                        stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=180)
                evidence.update(exitCode=result.returncode, requests=requests,
                                command=['./gradlew', '--no-daemon', '--init-script', '<public-verification>' if args.public else '<local-mirror-only>', 'verifyCandidateDependency'])
                result.check_returncode()
                resolved = json.loads((checkout / 'build/reform-resolution.json').read_text())
                assert resolved['coordinate'] == f'design.inherent:reform:{version}' and resolved['sha256'] == digest, resolved
                if args.public:
                    assert resolved['repository'] == RELEASE_URL and resolved['publicOnly'], resolved
                    assert not requests
                else:
                    assert any(request == dict(method='GET', path=f'/releases/download/v{version}/{asset}', status=200)
                               for request in requests), requests
                evidence.update(status='PASS', releaseAsset=asset, resolved=resolved)
            except Exception as failure:
                evidence.update(status='FAIL', error=str(failure))
                if 'result' in locals():
                    evidence['consoleTail'] = result.stdout[-6000:]
                raise
            finally:
                if server is not None:
                    server.shutdown()
                    worker.join()
                args.output.parent.mkdir(parents=True, exist_ok=True)
                args.output.write_text(json.dumps(evidence, indent=2) + '\n')
    print('Default release resolution:', args.output)


if __name__ == '__main__':
    main()
