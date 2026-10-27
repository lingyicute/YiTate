"""Static regression checks for critical workflow wiring."""
from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]


class WorkflowTests(unittest.TestCase):
    def test_nightly_publish_moves_the_existing_tag_to_the_built_commit(self):
        workflow = (ROOT / ".github/workflows/create-release.yml").read_text(encoding="utf-8")

        self.assertIn("contents: write", workflow)
        self.assertIn("uses: actions/checkout@v4", workflow)
        self.assertIn('git tag --force nightly "$GITHUB_SHA"', workflow)
        self.assertIn("git push --force origin refs/tags/nightly", workflow)
        self.assertIn('gh release edit nightly --notes "$notes" --target "$GITHUB_SHA"', workflow)
        self.assertLess(
            workflow.index("git push --force origin refs/tags/nightly"),
            workflow.index("gh release edit nightly"),
            "move the Git tag before updating the release metadata",
        )

    def test_font_dependency_changes_trigger_the_subset_workflow(self):
        workflow = (ROOT / ".github/workflows/font-subset.yml").read_text(encoding="utf-8")

        self.assertIn('"scripts/requirements.txt"', workflow)
        self.assertIn("pip install -r scripts/requirements.txt", workflow)


if __name__ == "__main__":
    unittest.main()
