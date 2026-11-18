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

    def test_android_job_caches_gradle_downloads(self):
        workflow = (ROOT / ".github/workflows/create-release.yml").read_text(encoding="utf-8")

        self.assertIn("cache: gradle", workflow)

    def test_android_checks_include_lint_before_publication(self):
        workflow = (ROOT / ".github/workflows/create-release.yml").read_text(encoding="utf-8")
        verify = workflow.split("\n  publish:", maxsplit=1)[0]
        self.assertIn("./gradlew --no-daemon testDebugUnitTest lintDebug assembleRelease", verify)

    def test_pages_generates_the_subset_without_repository_write_credentials(self):
        workflows = ROOT / ".github/workflows"
        pages = (workflows / "static.yml").read_text(encoding="utf-8")
        self.assertFalse((workflows / "font-subset.yml").exists())
        build, deploy = pages.split("\n  deploy:", maxsplit=1)
        self.assertIn("python scripts/subset_font.py", build)
        self.assertIn("pip install -r scripts/requirements.txt", build)
        self.assertIn("cache: pip", build)
        self.assertIn("cache-dependency-path: scripts/requirements.txt", build)
        self.assertIn('"web/**"', build)
        self.assertIn('".github/workflows/static.yml"', build)
        self.assertIn("persist-credentials: false", build)
        self.assertNotIn("contents: write", pages)
        self.assertNotIn("id-token: write", build)
        self.assertNotIn("pages: write", build)
        self.assertIn("needs: build", deploy)
        self.assertIn("id-token: write", deploy)
        self.assertIn("pages: write", deploy)

    def test_pages_build_and_deploy_are_restricted_to_main(self):
        pages = (ROOT / ".github/workflows/static.yml").read_text(encoding="utf-8")
        self.assertEqual(pages.count("if: github.ref == 'refs/heads/main'"), 2)

    def test_pages_checks_its_generated_html_before_upload(self):
        workflow = (ROOT / ".github/workflows/static.yml").read_text(encoding="utf-8")
        generate = workflow.index("python scripts/subset_font.py")
        test = workflow.index("node --test tests/web.test.cjs")
        upload = workflow.index("uses: actions/upload-pages-artifact@")
        self.assertLess(generate, test)
        self.assertLess(test, upload)

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
