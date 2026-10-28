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

    def test_font_workflows_use_pinned_dependencies_and_regenerate_cleanly(self):
        subset = (ROOT / ".github/workflows/font-subset.yml").read_text(encoding="utf-8")
        pages = (ROOT / ".github/workflows/static.yml").read_text(encoding="utf-8")

        self.assertIn('"scripts/requirements.txt"', subset)
        self.assertIn("pip install -r scripts/requirements.txt", subset)
        self.assertIn("pip install -r scripts/requirements.txt", pages)
        self.assertNotIn("git pull --rebase", subset)
        self.assertNotIn('git config user.email "${{', subset)
        self.assertLess(
            subset.index('git checkout -B "$GITHUB_REF_NAME" "origin/$GITHUB_REF_NAME"'),
            subset.index("python scripts/subset_font.py"),
            "subset against the latest branch head to avoid @font-face rebase conflicts",
        )

    def test_workflow_changes_trigger_lightweight_regression_checks(self):
        checks = (ROOT / ".github/workflows/lightweight-checks.yml").read_text(encoding="utf-8")

        self.assertEqual(checks.count('".github/workflows/**"'), 2)


if __name__ == "__main__":
    unittest.main()
