#!/usr/bin/env bash
set -euo pipefail

apk=${1:?usage: publish_nightly.sh PATH_TO_UNSIGNED_APK}
: "${GITHUB_SHA:?}" "${GITHUB_RUN_ID:?}" "${GITHUB_RUN_ATTEMPT:?}"
: "${GITHUB_SERVER_URL:?}" "${GITHUB_REPOSITORY:?}"
if [[ ! -s "$apk" || ! $GITHUB_SHA =~ ^[0-9a-f]{40}$ ||
      ! $GITHUB_RUN_ID =~ ^[0-9]+$ || ! $GITHUB_RUN_ATTEMPT =~ ^[0-9]+$ ]]; then
  echo 'Invalid nightly artifact or build identity' >&2
  exit 1
fi

ensure_current_main() {
  local ref
  ref=$(git ls-remote --exit-code origin refs/heads/main)
  if [[ ${ref%%$'\t'*} != "$GITHUB_SHA" ]]; then
    echo "Skipping stale nightly for $GITHUB_SHA: main has moved."
    exit 0
  fi
}

ensure_current_main
previous_tag=$(git ls-remote origin refs/tags/nightly)
previous_tag=${previous_tag%%$'\t'*}

# Use a new filename instead of deleting the live asset before an upload.
staging=$(mktemp -d)
staged=false
asset="app-release-unsigned-${GITHUB_SHA:0:12}-${GITHUB_RUN_ID}-${GITHUB_RUN_ATTEMPT}.apk"
cleanup_staging() {
  if [[ $staged == true ]]; then
    gh release delete-asset nightly "$asset" --yes ||
      echo "::warning::Could not remove unpublished nightly asset $asset" >&2
  fi
  rm -rf -- "$staging"
}
trap cleanup_staging EXIT
cp -- "$apk" "$staging/$asset"
url="$GITHUB_SERVER_URL/$GITHUB_REPOSITORY/releases/download/nightly/$asset"
printf -v notes 'Automated build of `%s` from `main`. The APK is unsigned.\n\n[Download the unsigned APK](%s)' \
  "$GITHUB_SHA" "$url"

# Listing releases covers drafts as well and fails on API errors, rather than
# confusing an unavailable API with a missing release.
release=$(gh api --paginate 'repos/{owner}/{repo}/releases?per_page=100' \
  --jq '.[] | select(.tag_name == "nightly") | .id')
if [[ -n "$release" ]]; then
  gh release upload nightly "$staging/$asset"
else
  gh release create nightly "$staging/$asset" --draft --prerelease --latest=false \
    --title 'Nightly Build' --target "$GITHUB_SHA"
fi
staged=true

ensure_current_main
# Only move the tag once the new asset exists. Do not overwrite a tag changed
# by someone else during the upload.
git push "--force-with-lease=refs/tags/nightly:$previous_tag" origin "$GITHUB_SHA:refs/tags/nightly"
# Once the edit is attempted, a transport failure may still mean the new
# asset is linked. Never delete it on that path.
staged=false
gh release edit nightly --draft=false --prerelease --latest=false --notes "$notes"

# Keep unrelated files, and keep the previous APK until the new notes point to
# the new asset. If metadata updates fail, both APKs remain available for retry.
assets=$(gh release view nightly --json assets --jq '.assets[].name')
while IFS= read -r old; do
  if [[ $old != "$asset" && ( $old == app-release-unsigned.apk ||
        $old =~ ^app-release-unsigned-[0-9a-f]{12}-[0-9]+-[0-9]+\.apk$ ) ]]; then
    gh release delete-asset nightly "$old" --yes
  fi
done <<< "$assets"
