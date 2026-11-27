"""Static regression checks for critical workflow wiring."""
from pathlib import Path
import re
import unittest


ROOT = Path(__file__).resolve().parents[1]


class WorkflowTests(unittest.TestCase):
    def test_nightly_publisher_depends_on_the_verified_artifact_and_runs_on_main_only(self):
        workflow = (ROOT / ".github/workflows/create-release.yml").read_text(encoding="utf-8")
        verify, publish = workflow.split("\n  publish:", maxsplit=1)
        self.assertNotIn("contents: write", verify)
        self.assertIn("persist-credentials: false", verify)
        self.assertIn("contents: write", publish)
        self.assertIn("needs: verify", publish)
        self.assertIn("github.ref == 'refs/heads/main' && github.event_name != 'pull_request'", publish)
        self.assertIn("uses: actions/download-artifact@", publish)
        self.assertIn("bash scripts/publish_nightly.sh apk/app-release-unsigned.apk", publish)

    def test_nightly_publication_is_gated_on_the_publisher_regressions(self):
        workflow = (ROOT / ".github/workflows/create-release.yml").read_text(encoding="utf-8")
        verify, publish = workflow.split("\n  publish:", maxsplit=1)
        self.assertIn("python3 -m unittest discover -s tests -p 'test_publish_nightly.py' -v", verify)
        self.assertNotIn("continue-on-error: true", verify)
        self.assertIn("needs: verify", publish)

    def test_android_reruns_can_replace_artifacts_in_the_same_workflow_run(self):
        workflow = (ROOT / ".github/workflows/create-release.yml").read_text(encoding="utf-8")
        for artifact in ("app-release", "unit-test-reports"):
            self.assertIn(f"name: {artifact}\n          overwrite: true", workflow)

    def test_android_job_caches_gradle_downloads(self):
        workflow = (ROOT / ".github/workflows/create-release.yml").read_text(encoding="utf-8")

        self.assertIn("cache: gradle", workflow)

    def test_android_checks_include_lint_before_publication(self):
        workflow = (ROOT / ".github/workflows/create-release.yml").read_text(encoding="utf-8")
        verify = workflow.split("\n  publish:", maxsplit=1)[0]
        self.assertIn("./gradlew --no-daemon testDebugUnitTest lintDebug assembleRelease", verify)

    def test_font_workflows_use_pinned_dependencies_and_regenerate_cleanly(self):
        subset = (ROOT / ".github/workflows/font-subset.yml").read_text(encoding="utf-8")
        pages = (ROOT / ".github/workflows/static.yml").read_text(encoding="utf-8")

        self.assertIn('"scripts/requirements.txt"', subset)
        self.assertIn("pip install -r scripts/requirements.txt", subset)
        self.assertIn("pip install -r scripts/requirements.txt", pages)
        self.assertIn("cache: pip", subset)
        self.assertIn("cache: pip", pages)
        self.assertIn("cache-dependency-path: scripts/requirements.txt", subset)
        self.assertIn("cache-dependency-path: scripts/requirements.txt", pages)
        self.assertIn('"web/**"', pages)
        self.assertIn('".github/workflows/static.yml"', pages)
        self.assertIn("persist-credentials: false", pages)
        self.assertIn("GIT_USER_NAME: ${{ github.actor }}", subset)
        self.assertIn("GIT_USER_EMAIL: ${{ github.event.pusher.email || format(", subset)
        self.assertIn("for attempt in 1 2 3; do", subset)
        self.assertIn('if git push origin "HEAD:$GITHUB_REF_NAME"; then', subset)
        self.assertIn('echo "Push raced with a newer commit (attempt $attempt); retrying."', subset)
        self.assertNotIn("git pull --rebase", subset)
        self.assertNotIn('git config user.email "${{', subset)
        self.assertLess(
            subset.index('git checkout -B "$GITHUB_REF_NAME" "origin/$GITHUB_REF_NAME"'),
            subset.index("python scripts/subset_font.py"),
            "subset against the latest branch head to avoid @font-face rebase conflicts",
        )

    def test_pages_runs_its_script_regressions_before_generating_artifacts(self):
        pages = (ROOT / '.github/workflows/static.yml').read_text(encoding='utf-8')
        build = pages.split('\n  deploy:', maxsplit=1)[0]
        generate = build.index('run: python scripts/subset_font.py')
        for name in ('test_subset_font.py', 'test_pages_revision.py'):
            self.assertIn(f'"tests/{name}"', build)
            self.assertLess(build.index(f"python -m unittest discover -s tests -p '{name}'"), generate)
        self.assertNotIn('continue-on-error: true', build)

    def test_pages_build_and_deploy_are_restricted_to_main(self):
        pages = (ROOT / ".github/workflows/static.yml").read_text(encoding="utf-8")
        self.assertEqual(pages.count("if: github.ref == 'refs/heads/main'"), 2)

    def test_pages_trigger_paths_match_the_revision_guard(self):
        pages = (ROOT / ".github/workflows/static.yml").read_text(encoding="utf-8")
        guard = (ROOT / "scripts/check_pages_revision.sh").read_text(encoding="utf-8")
        triggers = re.findall(r'^      - "([^"]+)"$', pages, re.MULTILINE)
        watched = re.search(r"git diff --quiet \S+ \S+ -- ((?:.*\\\n)*.*); then", guard).group(1)
        self.assertTrue(triggers)
        # A directory glob in the trigger is a directory pathspec in the guard.
        self.assertEqual(sorted(path.replace("/**", "/") for path in triggers),
                         sorted(watched.replace("\\\n", " ").split()))

    def test_pages_checks_its_generated_html_before_upload(self):
        workflow = (ROOT / ".github/workflows/static.yml").read_text(encoding="utf-8")
        generate = workflow.index("python scripts/subset_font.py")
        test = workflow.index("node --test tests/web.test.cjs")
        upload = workflow.index("uses: actions/upload-pages-artifact@")
        self.assertLess(generate, test)
        self.assertLess(test, upload)

    def test_pages_reruns_deploy_the_artifact_named_by_the_build_job(self):
        workflow = (ROOT / ".github/workflows/static.yml").read_text(encoding="utf-8")
        build, deploy = workflow.split("\n  deploy:", maxsplit=1)
        self.assertIn('name=github-pages-${GITHUB_RUN_ATTEMPT}', build)
        self.assertIn('artifact_name: ${{ steps.artifact.outputs.name }}', build)
        self.assertIn('name: ${{ steps.artifact.outputs.name }}', build)
        self.assertIn('artifact_name: ${{ needs.build.outputs.artifact_name }}', deploy)
        self.assertNotIn('github.run_attempt', deploy)

    def test_publication_queues_never_replace_pending_runs(self):
        for filename, job in (("static.yml", "deploy"), ("create-release.yml", "publish")):
            workflow = (ROOT / ".github/workflows" / filename).read_text(encoding="utf-8")
            before, publisher = workflow.split(f"\n  {job}:", maxsplit=1)
            self.assertNotIn("concurrency:", before)
            self.assertIn("    concurrency:", publisher)
            self.assertIn("queue: max", publisher)
            self.assertNotIn("cancel-in-progress: true", publisher)

    def test_workflow_actions_are_pinned_to_full_commits(self):
        for path in (ROOT / ".github/workflows").glob("*.yml"):
            refs = re.findall(r"uses:\s*([^\s#]+)", path.read_text(encoding="utf-8"))
            self.assertTrue(refs, path.name)
            for action in refs:
                self.assertRegex(action, r"@[0-9a-f]{40}$", path.name)

    def test_workflow_changes_trigger_lightweight_regression_checks(self):
        checks = (ROOT / ".github/workflows/lightweight-checks.yml").read_text(encoding="utf-8")

        self.assertEqual(checks.count('".github/workflows/**"'), 2)
        self.assertIn("concurrency:", checks)


if __name__ == "__main__":
    unittest.main()
