"""Exercise the publisher with real temporary Git refs and a fake GitHub CLI."""
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[1]
FAKE_GH = r'''
import os
from pathlib import Path
import subprocess
import sys

args = sys.argv[1:]
store = Path(os.environ['FAKE_GH_STORE'])
mode = os.environ.get('FAKE_GH_MODE')
assets = store / 'assets'
notes = store / 'notes'
draft = store / 'draft'
with (store / 'calls').open('a') as log:
    log.write(' '.join(args[:2]) + '\n')

if args[0] == 'api':
    if mode == 'lookup-failed':
        sys.exit(1)
    if notes.exists() or draft.exists():
        print(42)
elif args[:2] in (['release', 'create'], ['release', 'upload']):
    assert '--clobber' not in args
    if mode == 'upload-failed':
        sys.exit(1)
    path = Path(args[3])
    assert not (assets / path.name).exists()
    (assets / path.name).write_bytes(path.read_bytes())
    if args[1] == 'create':
        assert '--draft' in args and not notes.exists()
        draft.touch()
    if mode == 'main-moved':
        subprocess.run(['git', 'commit', '--quiet', '--allow-empty', '-m', 'concurrent main'], check=True)
        subprocess.run(['git', 'push', '--quiet', 'origin', 'main'], check=True)
    if mode == 'tag-moved':
        subprocess.run(['git', 'tag', '-a', '-m', 'external update', 'external-nightly', 'HEAD^'], check=True)
        subprocess.run(['git', 'push', '--quiet', '--force', 'origin',
                        'refs/tags/external-nightly:refs/tags/nightly'], check=True)
elif args[:2] == ['release', 'edit']:
    tag = subprocess.check_output(['git', 'ls-remote', 'origin', 'refs/tags/nightly'], text=True)
    assert tag.split()[0] == os.environ['GITHUB_SHA']
    if mode == 'metadata-failed':
        sys.exit(1)
    body = args[args.index('--notes') + 1]
    asset_name = body.split('/')[-1].rstrip(')')
    assert (assets / asset_name).read_bytes() == b'new apk'
    assert '--draft=false' in args and '--latest=false' in args
    notes.write_text(body)
    draft.unlink(missing_ok=True)
elif args[:2] == ['release', 'view']:
    print('\n'.join(p.name for p in assets.iterdir()))
elif args[:2] == ['release', 'delete-asset']:
    assert not notes.exists() or args[3] not in notes.read_text(), 'the linked asset must not be deleted'
    (assets / args[3]).unlink()
elif args[:2] == ['release', 'delete']:
    assert draft.exists() and not notes.exists(), 'only an unpublished draft release may be deleted'
    for p in assets.iterdir():
        p.unlink()
    draft.unlink()
else:
    raise AssertionError(args)
'''


class PublishNightlyTests(unittest.TestCase):
    def setUp(self):
        temp = tempfile.TemporaryDirectory()
        self.addCleanup(temp.cleanup)
        root = Path(temp.name)
        self.repo = root / 'working'
        self.repo.mkdir()
        self.git('init', '--bare', '--quiet', str(root / 'remote'))
        self.git('init', '--quiet', '-b', 'main')
        self.git('remote', 'add', 'origin', str(root / 'remote'))
        self.git('config', 'user.name', 'Test Author')
        self.git('config', 'user.email', 'test@example.com')
        self.git('commit', '--quiet', '--allow-empty', '-m', 'previous')
        self.old_sha = self.git('rev-parse', 'HEAD')
        self.git('commit', '--quiet', '--allow-empty', '-m', 'current')
        self.sha = self.git('rev-parse', 'HEAD')
        self.git('push', '--quiet', 'origin', 'main')
        self.apk = root / 'app-release-unsigned.apk'
        self.apk.write_bytes(b'new apk')
        self.store = root / 'gh-state'
        self.assets = self.store / 'assets'
        self.assets.mkdir(parents=True)
        gh = root / 'gh'
        gh.write_text(f'#!{sys.executable}\n{FAKE_GH}')
        gh.chmod(0o755)
        self.env = dict(os.environ, PATH=f'{root}{os.pathsep}{os.environ["PATH"]}',
                        FAKE_GH_STORE=str(self.store), GITHUB_SHA=self.sha,
                        GITHUB_SERVER_URL='https://github.com', GITHUB_REPOSITORY='owner/project',
                        GITHUB_RUN_ID='123', GITHUB_RUN_ATTEMPT='1')
        self.asset = f'app-release-unsigned-{self.sha[:12]}-123-1.apk'

    def git(self, *args):
        return subprocess.check_output(['git', *args], cwd=self.repo, text=True).strip()

    def previous_release(self):
        self.git('push', '--quiet', 'origin', f'{self.old_sha}:refs/tags/nightly')
        (self.store / 'notes').write_text('previous notes')
        (self.assets / 'app-release-unsigned.apk').write_bytes(b'previous apk')
        (self.assets / 'unrelated.txt').write_text('keep')

    def publish(self, **env):
        return subprocess.run(['bash', str(ROOT / 'scripts/publish_nightly.sh'), str(self.apk)],
                              cwd=self.repo, env={**self.env, **env}, text=True, capture_output=True)

    def tag(self):
        return self.git('ls-remote', 'origin', 'refs/tags/nightly').partition('\t')[0]

    def test_existing_release_replaces_only_managed_assets_after_publish(self):
        self.previous_release()
        result = self.publish()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(self.tag(), self.sha)
        self.assertEqual({p.name for p in self.assets.iterdir()}, {self.asset, 'unrelated.txt'})
        notes = (self.store / 'notes').read_text()
        self.assertIn(self.asset, notes)
        self.assertIn(f'[{self.sha}](https://github.com/owner/project/tree/{self.sha})', notes)

    def test_first_release_is_staged_as_a_draft_then_published(self):
        result = self.publish()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(self.tag(), self.sha)
        self.assertEqual((self.assets / self.asset).read_bytes(), b'new apk')
        self.assertFalse((self.store / 'draft').exists())

    def test_aborted_first_release_deletes_the_unpublished_draft(self):
        result = self.publish(FAKE_GH_MODE='main-moved')
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(self.tag(), '')
        self.assertFalse((self.assets / self.asset).exists())
        self.assertFalse((self.store / 'draft').exists())

    def test_failed_upload_does_not_change_the_existing_release(self):
        self.previous_release()
        self.assertNotEqual(self.publish(FAKE_GH_MODE='upload-failed').returncode, 0)
        self.assertEqual(self.tag(), self.old_sha)
        self.assertEqual((self.store / 'notes').read_text(), 'previous notes')
        self.assertEqual((self.assets / 'app-release-unsigned.apk').read_bytes(), b'previous apk')

    def test_failed_metadata_update_retains_the_previous_usable_asset(self):
        self.previous_release()
        self.assertNotEqual(self.publish(FAKE_GH_MODE='metadata-failed').returncode, 0)
        self.assertTrue((self.assets / self.asset).exists())
        self.assertTrue((self.assets / 'app-release-unsigned.apk').exists())
        self.assertEqual((self.store / 'notes').read_text(), 'previous notes')

    def test_stale_rerun_cannot_rewind_nightly(self):
        self.previous_release()
        self.git('push', '--quiet', '--force', 'origin', f'{self.sha}:refs/tags/nightly')
        result = self.publish(GITHUB_SHA=self.old_sha)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(self.tag(), self.sha)
        self.assertFalse((self.store / 'calls').exists())

    def test_main_moving_during_staging_withdraws_the_unpublished_asset(self):
        self.previous_release()
        result = self.publish(FAKE_GH_MODE='main-moved')
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn('Skipping stale nightly', result.stdout)
        self.assertEqual(self.tag(), self.old_sha)
        self.assertFalse((self.assets / self.asset).exists())
        self.assertEqual((self.store / 'notes').read_text(), 'previous notes')

    def test_lost_tag_lease_withdraws_only_this_attempts_asset(self):
        self.previous_release()
        result = self.publish(FAKE_GH_MODE='tag-moved')
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(self.tag(), self.git('rev-parse', 'refs/tags/external-nightly'))
        self.assertFalse((self.assets / self.asset).exists())
        self.assertTrue((self.assets / 'app-release-unsigned.apk').exists())
        self.assertEqual((self.store / 'notes').read_text(), 'previous notes')

    def test_api_failure_is_not_treated_as_a_missing_release(self):
        self.previous_release()
        self.assertNotEqual(self.publish(FAKE_GH_MODE='lookup-failed').returncode, 0)
        self.assertEqual(self.tag(), self.old_sha)
        self.assertNotIn('release create', (self.store / 'calls').read_text())
