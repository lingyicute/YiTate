"""Exercise the Pages guard against real local Git refs."""
import os
from pathlib import Path
import subprocess
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[1]


class PagesRevisionTests(unittest.TestCase):
    def setUp(self):
        tmp = tempfile.TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        path = Path(tmp.name)
        self.repo = path / 'working'
        self.repo.mkdir()
        self.output = path / 'output'
        self.output.touch()
        self.git('init', '--quiet', '--bare', str(path / 'remote'))
        self.git('init', '--quiet', '-b', 'main')
        self.git('remote', 'add', 'origin', str(path / 'remote'))
        self.git('config', 'user.name', 'Test Author')
        self.git('config', 'user.email', 'test@example.com')
        self.built_sha = self.push('web/index.html', 'built page')

    def git(self, *args):
        return subprocess.check_output(['git', *args], cwd=self.repo, text=True).strip()

    def push(self, filename, content):
        target = self.repo / filename
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(content, encoding='utf-8')
        self.git('add', filename)
        self.git('commit', '--quiet', '-m', filename)
        self.git('push', '--quiet', 'origin', 'main')
        return self.git('rev-parse', 'HEAD')

    def check(self, **overrides):
        env = {**os.environ, 'GITHUB_REF': 'refs/heads/main', 'GITHUB_SHA': self.built_sha,
               'GITHUB_OUTPUT': str(self.output), **overrides}
        return subprocess.run(['bash', str(ROOT / 'scripts/check_pages_revision.sh')],
                              cwd=self.repo, env=env, text=True, capture_output=True)

    def test_unrelated_main_commit_does_not_block_a_valid_build(self):
        self.push('app/build.gradle', 'new Android version')
        result = self.check()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(self.output.read_text(), 'deploy=true\n')

    def test_changed_site_skips_the_old_build(self):
        self.push('web/index.html', 'new page')
        result = self.check()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn('Skipping stale Pages deployment', result.stdout)
        self.assertEqual(self.output.read_text(), '')

    def test_main_branch_check_is_case_sensitive(self):
        result = self.check(GITHUB_REF='refs/heads/Main')
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(self.output.read_text(), '')

    def test_git_failure_is_not_treated_as_a_successful_skip(self):
        result = self.check(GITHUB_SHA='0' * 40)
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(self.output.read_text(), '')
