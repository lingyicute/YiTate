"""Static regression checks for critical workflow wiring."""
from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]


class WorkflowTests(unittest.TestCase):
    def test_nightly_publish_moves_the_existing_tag_to_the_built_commit(self):
        workflow = (ROOT / ".github/workflows/create-release.yml").read_text(encoding="utf-8")

        self.assertIn("contents: write", workflow)
        self.assertIn("uses: actions/checkout@v4", workflow)
        self.assertNotIn("fetch-depth: 0", workflow)
        self.assertIn('git tag --force nightly "$GITHUB_SHA"', workflow)
        self.assertIn("git push --force origin refs/tags/nightly", workflow)
        self.assertIn('gh release edit nightly --notes "$notes" --target "$GITHUB_SHA"', workflow)
        self.assertLess(
            workflow.index("gh release upload nightly apk/app-release-unsigned.apk --clobber"),
            workflow.index("git push --force origin refs/tags/nightly"),
            "upload the new APK before moving the nightly tag",
        )
        self.assertLess(
            workflow.index("git push --force origin refs/tags/nightly"),
            workflow.index("gh release edit nightly"),
            "move the Git tag before updating the release metadata",
        )

    def test_android_job_caches_gradle_downloads(self):
        workflow = (ROOT / ".github/workflows/create-release.yml").read_text(encoding="utf-8")

        self.assertIn("cache: gradle", workflow)

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

    def test_workflow_changes_trigger_lightweight_regression_checks(self):
        checks = (ROOT / ".github/workflows/lightweight-checks.yml").read_text(encoding="utf-8")

        self.assertEqual(checks.count('".github/workflows/**"'), 2)
        self.assertIn("concurrency:", checks)


if __name__ == "__main__":
    unittest.main()
