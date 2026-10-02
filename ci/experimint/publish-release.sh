#!/bin/bash
# Creates the pre-release on the fork (idempotent for same-day reruns) and
# prunes releases beyond the retention count.
# Usage: publish-release.sh <repo> <tag> <title> <body-file> <asset> <target-sha> <keep> <tag-prefix>
set -euo pipefail
REPO="$1"; TAG="$2"; TITLE="$3"; BODY_FILE="$4"; ASSET="$5"; TARGET_SHA="$6"; KEEP="$7"; PREFIX="$8"
: "${FORK_TOKEN:?FORK_TOKEN missing}"

api() {
  curl -fsS -H "Authorization: Bearer $FORK_TOKEN" \
       -H "Accept: application/vnd.github+json" "$@"
}

# Same-day rerun: drop the existing release + tag, then recreate.
EXISTING=$( (api "https://api.github.com/repos/$REPO/releases/tags/$TAG" 2>/dev/null || true) \
  | jq -r '.id // empty')
if [ -n "$EXISTING" ]; then
  echo "replacing existing release $TAG (id $EXISTING)"
  api -X DELETE "https://api.github.com/repos/$REPO/releases/$EXISTING"
  api -X DELETE "https://api.github.com/repos/$REPO/git/refs/tags/$TAG" || true
fi

BODY=$(jq -Rs . < "$BODY_FILE")
REL=$(api -X POST "https://api.github.com/repos/$REPO/releases" -d @- <<JSON
{"tag_name": "$TAG", "target_commitish": "$TARGET_SHA", "name": "$TITLE",
 "body": $BODY, "prerelease": true, "draft": false}
JSON
)
REL_ID=$(echo "$REL" | jq -r .id)
[ -n "$REL_ID" ] && [ "$REL_ID" != "null" ] || { echo "release creation failed: $REL"; exit 1; }

NAME=$(basename "$ASSET")
curl -fsS -H "Authorization: Bearer $FORK_TOKEN" \
     -H "Content-Type: application/zip" \
     --data-binary @"$ASSET" \
     "https://uploads.github.com/repos/$REPO/releases/$REL_ID/assets?name=$NAME" > /dev/null
echo "release created: https://github.com/$REPO/releases/tag/$TAG"

# Retention: keep the newest $KEEP matching releases, delete the rest + tags.
api "https://api.github.com/repos/$REPO/releases?per_page=100" \
  | jq -r --arg p "$PREFIX" --argjson k "$KEEP" \
      '[.[] | select(.tag_name | startswith($p))] | sort_by(.created_at) | reverse | .[$k:] | .[] | "\(.id) \(.tag_name)"' \
  | while read -r ID T; do
      [ -n "$ID" ] || continue
      echo "pruning old release $T"
      api -X DELETE "https://api.github.com/repos/$REPO/releases/$ID"
      api -X DELETE "https://api.github.com/repos/$REPO/git/refs/tags/$T" || true
    done
