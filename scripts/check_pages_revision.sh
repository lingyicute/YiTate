#!/usr/bin/env bash
set -euo pipefail

: "${GITHUB_REF:?}" "${GITHUB_SHA:?}" "${GITHUB_OUTPUT:?}"
if [[ $GITHUB_REF != refs/heads/main ]]; then
  echo '::notice::Skipping Pages deployment outside main.'
  exit 0
fi

# An Android-only push neither changes these inputs nor triggers a new Pages build.
git fetch --quiet --no-tags --depth=1 origin refs/heads/main
if git diff --quiet "$GITHUB_SHA" FETCH_HEAD -- web/ scripts/subset_font.py \
    scripts/requirements.txt scripts/check_pages_revision.sh tests/web.test.cjs \
    tests/test_subset_font.py tests/test_pages_revision.py .github/workflows/static.yml; then
  echo 'deploy=true' >> "$GITHUB_OUTPUT"
else
  status=$?
  if (( status != 1 )); then exit "$status"; fi
  echo '::notice::Skipping stale Pages deployment: the site inputs on main have changed.'
fi
