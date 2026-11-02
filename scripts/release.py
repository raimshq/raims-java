"""Verify approved Maven artifacts and publish their exact bytes to Maven Central."""

import argparse
import base64
import hashlib
import io
import json
import os
from pathlib import Path
import re
import shutil
import stat
import subprocess
import sys
import tempfile
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
import xml.etree.ElementTree as ET
import zipfile

sys.dont_write_bytecode = True
REPOSITORY = 'raimshq/raims-java'
WORKFLOW = '.github/workflows/validate.yml'
JOBS = {'validateJava': ('java-sdk-check', 'Run the canonical Java SDK checks'),
        'securityLinux': ('java-sdk-security', 'Run the canonical SDK security checks')}
JAVA_VERSIONS = {'11': '11.0.32.1', '21': '21.0.13', '25': '25.0.5'}
SHA = re.compile(r'[a-f0-9]{40}')
VERSION = re.compile(r'(?:0|[1-9]\d*)\.(?:0|[1-9]\d*)\.(?:0|[1-9]\d*)')
MAX_DOWNLOAD = 64 * 1024 * 1024
MAX_METADATA = 2 * 1024 * 1024
CENTRAL = 'https://central.sonatype.com/api/v1/publisher/'
REGISTRY = 'https://repo.maven.apache.org/maven2/'
FILES = {'raims-java.jar', 'raims-java.pom', 'raims-java-sources.jar', 'raims-java-javadoc.jar'}


def require(condition, message):
    if not condition:
        raise RuntimeError(message)


def digest(data):
    return hashlib.sha256(data).hexdigest()


def git(source, *args):
    result = subprocess.run(['git', '-C', str(source), *args], check=False, capture_output=True, text=True, timeout=60)
    require(result.returncode == 0, 'Release source identity could not be verified.')
    return result.stdout.strip()


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, request, file, code, message, headers, new_url):
        return None


def read_response(request, maximum=MAX_METADATA):
    with urllib.request.build_opener(NoRedirect).open(request, timeout=60) as response:
        raw = response.read(maximum + 1)
    require(len(raw) <= maximum, 'The remote response exceeds the allowed size.')
    return raw


def public_download(url):
    parsed = urllib.parse.urlsplit(url)
    require(parsed.scheme == 'https' and parsed.hostname in {'github.com', 'repo.maven.apache.org'}
            and parsed.username is None and parsed.password is None, 'Unexpected public artifact origin.')
    request = urllib.request.Request(url, headers={'User-Agent': 'Raims-Java-Release'})
    try:
        return read_response(request, MAX_DOWNLOAD)
    except urllib.error.HTTPError as error:
        require(parsed.hostname == 'github.com' and error.code in {301, 302, 303, 307, 308},
                'A public artifact could not be read.')
        destination = error.headers.get('Location', '')
        redirect = urllib.parse.urlsplit(destination)
        require(redirect.scheme == 'https' and redirect.hostname == 'release-assets.githubusercontent.com'
                and redirect.username is None and redirect.password is None, 'Unexpected release download origin.')
        return read_response(urllib.request.Request(destination, headers={'User-Agent': 'Raims-Java-Release'}), MAX_DOWNLOAD)


def github_url(endpoint):
    # The repository endpoint is /repos/owner/name; adding a trailing slash returns 404.
    return ('https://api.github.com/repos/' + REPOSITORY + '/' + endpoint).rstrip('/')


def github(endpoint, binary=False):
    token = os.environ.get('GITHUB_TOKEN')
    require(token, 'A GitHub token is required for release verification.')
    headers = {'Authorization': 'Bearer ' + token, 'Accept': 'application/vnd.github+json',
               'User-Agent': 'Raims-Java-Release', 'X-GitHub-Api-Version': '2022-11-28'}
    try:
        raw = read_response(urllib.request.Request(github_url(endpoint), headers=headers), MAX_DOWNLOAD if binary else MAX_METADATA)
    except urllib.error.HTTPError as error:
        require(binary and error.code in {301, 302, 303, 307, 308}, 'GitHub release metadata could not be read.')
        destination = error.headers.get('Location', '')
        url = urllib.parse.urlsplit(destination)
        require(url.scheme == 'https' and url.hostname and url.username is None and url.password is None and
                (url.hostname.endswith('.blob.core.windows.net') or url.hostname.endswith('.githubusercontent.com')
                 or url.hostname.endswith('.actions.githubusercontent.com')), 'Unexpected artifact download origin.')
        # Signed download URLs never receive the repository token.
        raw = read_response(urllib.request.Request(destination, headers={'User-Agent': 'Raims-Java-Release'}), MAX_DOWNLOAD)
    return raw if binary else json.loads(raw)


def pom_version(raw):
    require(len(raw) <= MAX_METADATA, 'The Maven POM exceeds the allowed size.')
    text = raw.decode('utf-8')
    require('\x00' not in text and '<!DOCTYPE' not in text.upper() and '<!ENTITY' not in text.upper(),
            'The Maven POM contains unsupported declarations.')
    root = ET.fromstring(text)
    ns = {'m': 'http://maven.apache.org/POM/4.0.0'}
    require(root.tag == '{http://maven.apache.org/POM/4.0.0}project'
            and root.findtext('m:groupId', namespaces=ns) == 'com.raims'
            and root.findtext('m:artifactId', namespaces=ns) == 'raims-java', 'Maven coordinates differ.')
    version = root.findtext('m:version', namespaces=ns)
    require(isinstance(version, str) and VERSION.fullmatch(version), 'A stable Maven version is required.')
    require(root.findtext('m:scm/m:tag', namespaces=ns) == 'v' + version, 'The Maven SCM tag and version differ.')
    return version


def context(source):
    require(os.environ.get('GITHUB_REPOSITORY') == REPOSITORY and os.environ.get('GITHUB_EVENT_NAME') == 'release',
            'This publication accepts only the official repository release event.')
    event = json.loads(Path(os.environ['GITHUB_EVENT_PATH']).read_text(encoding='utf-8'))
    release = event.get('release', {})
    require(event.get('action') == 'published' and release.get('draft') is False and release.get('prerelease') is False,
            'Only a published stable release can be uploaded.')
    pom = (source / 'pom.xml').read_bytes()
    version = pom_version(pom)
    client = (source / 'src/main/java/com/raims/RaimsClient.java').read_text(encoding='utf-8')
    require(re.findall(r'public static final String VERSION = "([^"]+)";', client) == [version],
            'The SDK client version and Maven version differ.')
    tag = 'v' + version
    require(release.get('tag_name') == tag and os.environ.get('GITHUB_REF') == 'refs/tags/' + tag,
            'The release tag and SDK version differ.')
    head = git(source, 'rev-parse', 'HEAD')
    require(SHA.fullmatch(head) and head == os.environ.get('GITHUB_SHA')
            and git(source, 'rev-parse', 'refs/tags/' + tag + '^{commit}') == head,
            'The checked-out release commit differs.')
    require(not git(source, 'status', '--porcelain', '--untracked-files=all'), 'Release sources must be clean.')
    repo = github('')
    require(repo.get('full_name') == REPOSITORY and repo.get('private') is False and repo.get('default_branch') == 'main',
            'The repository visibility or default branch differs.')
    require(github('commits/' + tag).get('sha') == head, 'The public tag changed.')
    git(source, 'merge-base', '--is-ancestor', head, 'origin/main')
    identifier = release.get('id')
    require(type(identifier) is int and identifier > 0, 'The release identity is invalid.')
    current = github('releases/' + str(identifier))
    require(current.get('id') == identifier and current.get('tag_name') == tag
            and current.get('draft') is False and current.get('prerelease') is False,
            'The public release changed.')
    return {'sourceRevision': head, 'version': version, 'tag': tag, 'releaseId': identifier, 'pomSha256': digest(pom)}


def validation(head, wait_seconds=600):
    deadline = time.monotonic() + wait_seconds
    while True:
        value = github('actions/workflows/validate.yml/runs?head_sha=' + head + '&per_page=100&page=1')
        rows = value.get('workflow_runs', [])
        require(isinstance(rows, list) and type(value.get('total_count')) is int, 'Validation history is incomplete.')
        if rows and rows[0].get('status') == 'completed':
            run = rows[0]
            break
        require(time.monotonic() < deadline, 'Validation of this exact commit did not finish within the allowed wait.')
        print('Waiting for validation of the release commit.', flush=True)
        time.sleep(min(15, max(0, deadline - time.monotonic())))
    require(run.get('head_sha') == head and run.get('path') == WORKFLOW and run.get('name') == 'Validate Java SDK'
            and run.get('status') == 'completed' and run.get('conclusion') == 'success'
            and run.get('event') in {'push', 'workflow_dispatch'}
            and run.get('repository', {}).get('full_name') == REPOSITORY
            and run.get('head_repository', {}).get('full_name') == REPOSITORY,
            'Successful validation of this exact commit is required.')
    identifier, attempt = run.get('id'), run.get('run_attempt')
    require(type(identifier) is int and identifier > 0 and type(attempt) is int and attempt > 0,
            'Validation attempt identity is incomplete.')
    result = github(f'actions/runs/{identifier}/attempts/{attempt}/jobs?per_page=100&page=1')
    jobs = result.get('jobs', [])
    require(result.get('total_count') == len(jobs) == len(JOBS) and {job.get('name') for job in jobs} == set(JOBS),
            'The complete Java validation job inventory is required.')
    for job in jobs:
        suffix, step_name = JOBS[job['name']]
        require(job.get('status') == 'completed' and job.get('conclusion') == 'success'
                and job.get('labels') and set(job['labels']) == {f'codebuild-raims-github-actions-{identifier}-{attempt}', suffix}
                and len(job['labels']) == 2 and isinstance(job.get('runner_name'), str)
                and re.fullmatch(r'[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}', job['runner_name'])
                and job.get('runner_group_name'), 'A required AWS validation job did not pass.')
        canonical = [step for step in job.get('steps', []) if step.get('name') == step_name]
        require(len(canonical) == 1 and canonical[0].get('status') == 'completed'
                and canonical[0].get('conclusion') == 'success', 'The canonical validation step did not pass.')
    return {'validationRunId': identifier, 'validationRunAttempt': attempt}


def artifact_files(value, proven):
    run, attempt = proven['validationRunId'], proven['validationRunAttempt']
    response = github(f'actions/runs/{run}/artifacts?per_page=100&page=1')
    require(type(response.get('total_count')) is int and response['total_count'] <= 100
            and isinstance(response.get('artifacts'), list) and len(response['artifacts']) == response['total_count'],
            'Validation artifact inventory is incomplete.')
    found = [artifact for artifact in response['artifacts'] if artifact.get('name') == f'raims-java-validation-{run}-{attempt}']
    require(len(found) == 1 and found[0].get('expired') is False
            and found[0].get('workflow_run', {}).get('id') == run
            and found[0].get('workflow_run', {}).get('head_sha') == value['sourceRevision'],
            'The approved Maven artifact is missing.')
    artifact = found[0]
    require(type(artifact.get('id')) is int and artifact['id'] > 0, 'The artifact identity is invalid.')
    raw = github('actions/artifacts/' + str(artifact['id']) + '/zip', binary=True)
    require(artifact.get('digest') == 'sha256:' + digest(raw), 'The GitHub Actions artifact digest differs.')
    with zipfile.ZipFile(io.BytesIO(raw)) as archive:
        expected = {'release-manifest.json'} | {'dist/' + name for name in FILES}
        require(set(archive.namelist()) == expected and len(archive.infolist()) == len(expected)
                and sum(item.file_size for item in archive.infolist()) <= MAX_DOWNLOAD
                and all(not item.is_dir() and not stat.S_ISLNK(item.external_attr >> 16) for item in archive.infolist()),
                'The canonical artifact contains unexpected entries.')
        files = {name: archive.read('dist/' + name) for name in FILES}
        manifest_raw = archive.read('release-manifest.json')
    manifest = json.loads(manifest_raw)
    require(set(manifest) == {'package', 'sourceRevision', 'toolkitRevision', 'javaVersions', 'files'}
            and manifest.get('package') == {'group': 'com.raims', 'artifact': 'raims-java', 'version': value['version']}
            and manifest.get('sourceRevision') == value['sourceRevision']
            and manifest.get('toolkitRevision') == os.environ.get('RAIMS_JAVA_TOOLKIT_REF')
            and SHA.fullmatch(str(manifest.get('toolkitRevision', '')))
            and manifest.get('javaVersions') == JAVA_VERSIONS
            and manifest.get('files') == {name: digest(raw) for name, raw in files.items()},
            'The canonical report and Maven bytes differ.')
    require(pom_version(files['raims-java.pom']) == value['version']
            and digest(files['raims-java.pom']) == value['pomSha256'], 'The approved POM and release source differ.')
    files['release-manifest.json'] = manifest_raw
    return files, artifact['id']


def upload_release_asset(identifier, name, raw):
    token = os.environ.get('GITHUB_TOKEN')
    require(token and type(identifier) is int and identifier > 0 and Path(name).name == name,
            'A release asset upload could not be authorized.')
    endpoint = 'https://uploads.github.com/repos/' + REPOSITORY + '/releases/' + str(identifier) + '/assets?name=' + urllib.parse.quote(name, safe='')
    request = urllib.request.Request(endpoint, data=raw, method='POST', headers={
        'Authorization': 'Bearer ' + token, 'Content-Type': 'application/octet-stream',
        'User-Agent': 'Raims-Java-Release', 'Accept': 'application/vnd.github+json', 'X-GitHub-Api-Version': '2022-11-28'})
    try:
        read_response(request)
    except (OSError, RuntimeError):
        # An uncertain write is read back before another upload can be attempted.
        current = github('releases/' + str(identifier))
        existing = [asset for asset in current.get('assets', []) if asset.get('name') == name]
        require(len(existing) == 1 and public_download(existing[0]['browser_download_url']) == raw,
                'Release asset creation was not confirmed; inspect the release before retrying.')


def release_files(value, files, create_missing=False):
    release = github('releases/' + str(value['releaseId']))
    rows = release.get('assets', [])
    assets = {asset['name']: asset for asset in rows}
    expected = set(files) | {'SHA256SUMS'}
    require(len(assets) == len(rows) and set(assets).issubset(expected),
            'The GitHub release contains unexpected or duplicate assets.')
    complete = dict(files, SHA256SUMS=''.join(digest(files[name]) + '  ' + name + '\n' for name in sorted(files)).encode('utf-8'))
    for name, raw in complete.items():
        if name in assets:
            require(public_download(assets[name]['browser_download_url']) == raw,
                    'An existing release asset differs from the approved CI bytes.')
    missing = set(complete) - set(assets)
    require(not missing or create_missing, 'The approved Maven assets are missing from the GitHub release.')
    for name in sorted(missing):
        upload_release_asset(value['releaseId'], name, complete[name])
    if missing:
        rows = github('releases/' + str(value['releaseId'])).get('assets', [])
        assets = {asset['name']: asset for asset in rows}
    require(len(assets) == len(rows) and set(assets) == expected, 'The release asset inventory differs.')
    for name, raw in complete.items():
        require(public_download(assets[name]['browser_download_url']) == raw, 'A public release asset differs.')


def central_names(version):
    prefix = 'raims-java-' + version
    return {'raims-java.jar': prefix + '.jar', 'raims-java.pom': prefix + '.pom',
            'raims-java-sources.jar': prefix + '-sources.jar', 'raims-java-javadoc.jar': prefix + '-javadoc.jar'}


def indexed_files(version, files):
    found = set()
    for name, central_name in central_names(version).items():
        url = REGISTRY + 'com/raims/raims-java/' + version + '/' + central_name
        try:
            raw = read_response(urllib.request.Request(url, headers={'User-Agent': 'Raims-Java-Release'}), MAX_DOWNLOAD)
        except urllib.error.HTTPError as error:
            if error.code == 404:
                continue
            raise
        require(raw == files[name], 'An immutable Maven Central artifact differs from the approved bytes.')
        found.add(name)
    return found


def validate_local(artifact, files):
    require(artifact.is_dir() and not artifact.is_symlink(), 'The approved upload directory is missing.')
    paths = list(artifact.rglob('*'))
    expected = {'release-manifest.json', 'dist'} | {'dist/' + name for name in FILES}
    require({path.relative_to(artifact).as_posix() for path in paths} == expected
            and all(not path.is_symlink() for path in paths), 'The upload directory contains unexpected entries.')
    for name, raw in files.items():
        local = artifact / name if name == 'release-manifest.json' else artifact / 'dist' / name
        require(local.read_bytes() == raw, 'The local Maven upload differs from the approved CI bytes.')


def child_environment():
    return {key: value for key, value in os.environ.items()
            if key.upper() in {'PATH', 'HOME', 'USERPROFILE', 'SYSTEMROOT', 'WINDIR', 'TEMP', 'TMP', 'TMPDIR', 'LD_LIBRARY_PATH'}}


def sign_bundle(artifact, version, files):
    armored = os.environ.get('MAVEN_GPG_PRIVATE_KEY', '')
    passphrase = os.environ.get('MAVEN_GPG_PASSPHRASE')
    fingerprint = os.environ.get('MAVEN_GPG_FINGERPRINT', '')
    require(armored.startswith('-----BEGIN PGP PRIVATE KEY BLOCK-----') and len(armored) < 65536
            and isinstance(passphrase, str) and '\n' not in passphrase and '\r' not in passphrase
            and re.fullmatch(r'[A-F0-9]{40}', fingerprint), 'The configured signing identity is incomplete.')
    gpg, gpgconf = shutil.which('gpg'), shutil.which('gpgconf')
    require(gpg and gpgconf, 'GnuPG and its agent control utility are required for Maven Central signatures.')
    base = artifact.parent
    environment = child_environment()
    with tempfile.TemporaryDirectory(prefix='central-sign-', dir=base) as temporary:
        home = Path(temporary) / 'gnupg'
        home.mkdir(mode=0o700)
        environment['GNUPGHOME'] = str(home)
        command = [gpg, '--homedir', str(home), '--batch', '--no-tty']

        def run(arguments, data=None):
            result = subprocess.run(command + arguments, input=data, capture_output=True, check=False,
                                    env=environment, timeout=120)
            require(result.returncode == 0, 'The isolated GnuPG signing operation failed.')
            return result.stdout

        try:
            run(['--import'], armored.encode('utf-8'))
            listing = run(['--with-colons', '--list-secret-keys']).decode('utf-8')
            primary = []
            pending = False
            for line in listing.splitlines():
                fields = line.split(':')
                if fields[0] == 'sec':
                    pending = True
                elif fields[0] == 'fpr' and pending:
                    primary.append(fields[9])
                    pending = False
            require(primary == [fingerprint], 'The private signing key fingerprint differs.')
            entries = {}
            for name, central_name in sorted(central_names(version).items()):
                raw = files[name]
                path = Path(temporary) / central_name
                path.write_bytes(raw)
                signature = path.with_name(path.name + '.asc')
                run(['--pinentry-mode', 'loopback', '--passphrase-fd', '0', '--local-user', fingerprint, '--digest-algo', 'SHA512',
                     '--armor', '--detach-sign', '--output', str(signature), str(path)], (passphrase + '\n').encode('utf-8'))
                verified = run(['--status-fd', '1', '--verify', str(signature), str(path)]).decode('utf-8')
                valid = [line.split() for line in verified.splitlines() if line.startswith('[GNUPG:] VALIDSIG ')]
                require(len(valid) == 1 and fingerprint in {valid[0][2], valid[0][-1]}, 'The artifact signature identity differs.')
                entries[central_name] = raw
                entries[central_name + '.asc'] = signature.read_bytes()
                for algorithm in ('md5', 'sha1', 'sha256', 'sha512'):
                    entries[central_name + '.' + algorithm] = (hashlib.new(algorithm, raw).hexdigest() + '\n').encode('ascii')
        finally:
            # Stop only the agent started with this isolated keyring before deleting it.
            cleanup = subprocess.run([gpgconf, '--homedir', str(home), '--kill', 'gpg-agent'], capture_output=True,
                                     env=environment, check=False, timeout=30)
            require(cleanup.returncode == 0, 'The isolated GnuPG agent could not be stopped.')
        buffer = io.BytesIO()
        with zipfile.ZipFile(buffer, 'w', compression=zipfile.ZIP_STORED) as archive:
            for name, raw in sorted(entries.items()):
                info = zipfile.ZipInfo('com/raims/raims-java/' + version + '/' + name, (2026, 11, 1, 0, 0, 0))
                info.external_attr = 0o100644 << 16
                archive.writestr(info, raw)
        return buffer.getvalue()


def central_request(endpoint, data=b'', content_type='application/json'):
    username = os.environ.get('MAVEN_CENTRAL_USERNAME', '')
    password = os.environ.get('MAVEN_CENTRAL_PASSWORD', '')
    require(username and password and ':' not in username and not any(c in username + password for c in '\r\n'),
            'A Central Portal user token is required.')
    token = base64.b64encode((username + ':' + password).encode('utf-8')).decode('ascii')
    request = urllib.request.Request(CENTRAL + endpoint, data=data, method='POST', headers={
        'Authorization': 'Bearer ' + token, 'Content-Type': content_type, 'User-Agent': 'Raims-Java-Release'})
    try:
        return read_response(request)
    except urllib.error.HTTPError as error:
        # Never log response bodies or authenticated redirect locations.
        raise RuntimeError('The Central Portal request failed (HTTP ' + str(error.code) + ').') from None


def upload_bundle(bundle, version):
    boundary = 'raims-central-' + uuid.uuid4().hex
    body = ('--' + boundary + '\r\nContent-Disposition: form-data; name="bundle"; filename="raims-java-' + version
            + '.zip"\r\nContent-Type: application/octet-stream\r\n\r\n').encode('ascii') + bundle + ('\r\n--' + boundary + '--\r\n').encode('ascii')
    response = central_request('upload?' + urllib.parse.urlencode({'publishingType': 'AUTOMATIC', 'name': 'raims-java-' + version}),
                               body, 'multipart/form-data; boundary=' + boundary)
    identifier = response.decode('ascii').strip()
    require(re.fullmatch(r'[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}', identifier), 'Central returned an invalid deployment identity.')
    return identifier


def await_deployment(identifier, wait_seconds=1200):
    deadline = time.monotonic() + wait_seconds
    previous = None
    while True:
        value = json.loads(central_request('status?id=' + urllib.parse.quote(identifier, safe='')))
        state = value.get('deploymentState')
        require(value.get('deploymentId') == identifier and state in {'PENDING', 'VALIDATING', 'VALIDATED', 'PUBLISHING', 'PUBLISHED', 'FAILED'},
                'The Central deployment response is invalid.')
        if state != previous:
            print('Maven Central deployment: ' + state, flush=True)
            previous = state
        require(state != 'FAILED', 'Maven Central rejected the deployment; inspect its validation results in the Portal.')
        if state == 'PUBLISHED':
            return
        require(time.monotonic() < deadline, 'Maven Central has not confirmed publication; inspect the deployment before retrying.')
        time.sleep(min(15, max(0, deadline - time.monotonic())))


def confirm(version, files, wait_seconds=300):
    deadline = time.monotonic() + wait_seconds
    while True:
        if indexed_files(version, files) == FILES:
            print('Maven Central contains the exact approved Java artifacts.', flush=True)
            return
        require(time.monotonic() < deadline, 'Maven Central publication succeeded but public artifact indexing is not yet confirmed.')
        time.sleep(min(15, max(0, deadline - time.monotonic())))


def prior_deployment(value, files):
    response = github('actions/workflows/central-release.yml/runs?head_sha=' + value['sourceRevision'] + '&per_page=100&page=1')
    rows = response.get('workflow_runs')
    require(isinstance(rows, list) and type(response.get('total_count')) is int and response['total_count'] <= 100
            and len(rows) == response['total_count'], 'Prior publication history is incomplete.')
    current_run, current_attempt = int(os.environ['GITHUB_RUN_ID']), int(os.environ['GITHUB_RUN_ATTEMPT'])
    for run in rows:
        require(run.get('head_sha') == value['sourceRevision'] and run.get('path') == '.github/workflows/central-release.yml'
                and run.get('name') == 'Publish Java SDK' and run.get('event') == 'release'
                and run.get('repository', {}).get('full_name') == REPOSITORY
                and run.get('head_repository', {}).get('full_name') == REPOSITORY,
                'The prior publication identity differs.')
        identifier, attempt = run.get('id'), run.get('run_attempt')
        require(type(identifier) is int and identifier > 0 and type(attempt) is int and attempt > 0,
                'The prior publication attempt identity is invalid.')
        artifacts = github(f'actions/runs/{identifier}/artifacts?per_page=100&page=1')
        require(type(artifacts.get('total_count')) is int and artifacts['total_count'] <= 100
                and isinstance(artifacts.get('artifacts'), list) and len(artifacts['artifacts']) == artifacts['total_count'],
                'Prior publication receipt inventory is incomplete.')
        for previous_attempt in range(attempt, 0, -1):
            if identifier == current_run and previous_attempt >= current_attempt:
                continue
            name = f'raims-java-central-deployment-{identifier}-{previous_attempt}'
            found = [item for item in artifacts['artifacts'] if item.get('name') == name]
            require(len(found) <= 1, 'Duplicate publication receipts were found.')
            if found:
                receipt_artifact = found[0]
                require(receipt_artifact.get('expired') is False and type(receipt_artifact.get('id')) is int
                        and receipt_artifact.get('workflow_run', {}).get('id') == identifier
                        and receipt_artifact.get('workflow_run', {}).get('head_sha') == value['sourceRevision'],
                        'A prior deployment receipt is unavailable or has the wrong source.')
                raw = github('actions/artifacts/' + str(receipt_artifact['id']) + '/zip', binary=True)
                require(receipt_artifact.get('digest') == 'sha256:' + digest(raw), 'The deployment receipt digest differs.')
                with zipfile.ZipFile(io.BytesIO(raw)) as archive:
                    require(archive.namelist() == ['deployment.json'] and archive.infolist()[0].file_size <= MAX_METADATA,
                            'The publication receipt archive is invalid.')
                    receipt = json.loads(archive.read('deployment.json'))
                require(set(receipt) == {'sourceRevision', 'version', 'files', 'deploymentId'}
                        and receipt.get('sourceRevision') == value['sourceRevision'] and receipt.get('version') == value['version']
                        and receipt.get('files') == {name: digest(files[name]) for name in sorted(FILES)}
                        and re.fullmatch(r'[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}', receipt.get('deploymentId', '')),
                        'A prior deployment receipt differs from the approved artifacts.')
                return receipt['deploymentId']
            jobs = github(f'actions/runs/{identifier}/attempts/{previous_attempt}/jobs?per_page=100&page=1')
            require(isinstance(jobs.get('jobs'), list) and jobs.get('total_count') == len(jobs['jobs']) <= 2,
                    'Prior publication job history is incomplete.')
            attempted = [step for job in jobs['jobs'] for step in job.get('steps', [])
                         if step.get('name') == 'Sign and publish the exact approved artifacts to Maven Central'
                         and step.get('conclusion') != 'skipped']
            require(not attempted, 'A prior upload has no receipt. Inspect Central Portal deployments before retrying.')
    return None


def publish(artifact, value, files):
    published = indexed_files(value['version'], files)
    if published == FILES:
        print('The exact approved Java artifacts are already published.', flush=True)
        return
    require(not published, 'Maven Central has a partial immutable version; wait for indexing before retrying.')
    identifier = prior_deployment(value, files)
    if identifier is None:
        # Upload has no automatic retry: a lost response can represent a successful upload.
        bundle = sign_bundle(artifact, value['version'], files)
        try:
            identifier = upload_bundle(bundle, value['version'])
        except (OSError, RuntimeError):
            raise RuntimeError('The Central upload was not confirmed. Inspect Portal deployments before another upload.') from None
    receipt = {'sourceRevision': value['sourceRevision'], 'version': value['version'],
               'files': {name: digest(files[name]) for name in sorted(FILES)}, 'deploymentId': identifier}
    (artifact.parent / 'deployment.json').write_text(json.dumps(receipt, indent=2) + '\n', encoding='utf-8')
    print('Maven Central deployment ID: ' + identifier, flush=True)
    await_deployment(identifier)
    confirm(value['version'], files)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('command', choices=('prepare', 'verify', 'publish', 'confirm'))
    parser.add_argument('--source', type=Path, required=True)
    parser.add_argument('--artifact', type=Path, required=True)
    args = parser.parse_args()
    source, artifact = args.source.resolve(), args.artifact.resolve()
    workspace = Path(os.environ.get('GITHUB_WORKSPACE', '.')).resolve()
    require(workspace / '.tmp' in artifact.parents, 'Release artifacts must stay in workspace .tmp.')
    value = context(source)
    proven = validation(value['sourceRevision'])
    files, _artifact_id = artifact_files(value, proven)
    release_files(value, files, create_missing=args.command == 'prepare')
    if args.command == 'prepare':
        require(not artifact.exists(), 'Use a new upload directory.')
        (artifact / 'dist').mkdir(parents=True)
        for name, raw in files.items():
            (artifact / name if name == 'release-manifest.json' else artifact / 'dist' / name).write_bytes(raw)
    else:
        validate_local(artifact, files)
    if args.command == 'verify':
        published = indexed_files(value['version'], files)
        require(not published or published == FILES, 'Maven Central contains a partial immutable version.')
        if os.environ.get('GITHUB_OUTPUT'):
            with Path(os.environ['GITHUB_OUTPUT']).open('a', encoding='utf-8') as output:
                output.write('publishRequired=' + ('false' if published == FILES else 'true') + '\n')
    elif args.command == 'publish':
        publish(artifact, value, files)
    elif args.command == 'confirm':
        confirm(value['version'], files)
    return 0


if __name__ == '__main__':
    try:
        sys.exit(main())
    except (OSError, ValueError, RuntimeError, subprocess.SubprocessError, zipfile.BadZipFile, ET.ParseError) as error:
        print('Java release verification failed: ' + str(error), file=sys.stderr)
        sys.exit(1)
