#!/bin/bash
# Creates the pre-release as a draft, uploads all assets and only then makes
# it public, so a failed or interrupted run never leaves a half release.
# The tag names the commit (date stamp + sha7): if it is already published,
# a rerun keeps it and stops. Leftover drafts under the prefix are removed;
# published releases beyond the retention count are pruned (release first,
# then its tag, as immutable releases require).
# Usage: publish-release.sh <repo> <tag> <title> <body-file> <target-sha> <keep> <tag-prefix> <asset>...
set -euo pipefail
shopt -s inherit_errexit 2>/dev/null || true  # bash >= 4.4 (runner); explicit checks below
REPO="$1"; TAG="$2"; TITLE="$3"; BODY_FILE="$4"; TARGET_SHA="$5"; KEEP="$6"; PREFIX="$7"
shift 7
[ $# -gt 0 ] || { echo "no assets given"; exit 1; }
for ASSET in "$@"; do [ -f "$ASSET" ] || { echo "asset missing: $ASSET"; exit 1; }; done
: "${FORK_TOKEN:?FORK_TOKEN missing}"

API=https://api.github.com/repos/$REPO
AUTH=(-H "Authorization: Bearer $FORK_TOKEN" -H "Accept: application/vnd.github+json")
# Reads are safe to retry; writes are not (a lost response would duplicate).
get() { curl -fsS --retry 3 "${AUTH[@]}" "$@"; }
post() { curl -fsS "${AUTH[@]}" "$@"; }
# DELETE: 404 (release) or 422 (tag ref) means already gone.
del() {
  local code
  code=$(curl -sS -o /dev/null -w '%{http_code}' -X DELETE "${AUTH[@]}" "$1")
  case "$code" in 204|404|422) ;; *) echo "DELETE $1 -> $code"; return 1 ;; esac
}
# All releases, every page (drafts included for an authorized token), one
# JSON object per line. Any failed or malformed page aborts the run: a
# truncated list must never look like "tag not published yet".
releases() {
  local page=1 chunk n
  while :; do
    chunk=$(get "$API/releases?per_page=100&page=$page") || return 1
    n=$(echo "$chunk" | jq -e 'if type == "array" then length else error("not an array") end') || return 1
    [ "$n" -gt 0 ] || break
    echo "$chunk" | jq -c '.[]'
    page=$((page + 1))
  done
}
count_tag() {  # <all> <tag> <jq-condition on the release>
  echo "$1" | jq -s --arg t "$2" "[.[] | select(.tag_name == \$t) | select($3)] | length"
}

ALL=$(releases)
if [ "$(count_tag "$ALL" "$TAG" '.draft | not')" -gt 0 ]; then
  echo "already published: $TAG — nothing to do"
  exit 0
fi
echo "$ALL" | jq -r --arg p "$PREFIX" 'select(.draft and (.tag_name | startswith($p))) | "\(.id) \(.tag_name)"' \
  | while read -r ID T; do
      [ -n "$ID" ] || continue
      echo "removing leftover draft $T (id $ID)"
      del "$API/releases/$ID"
    done
# Not published (checked on the complete list above), so a tag ref of this
# name is a leftover and would pin the new release to an old commit.
del "$API/git/refs/tags/$TAG"

REL=$(jq -n --arg tag "$TAG" --arg sha "$TARGET_SHA" --arg name "$TITLE" \
           --rawfile body "$BODY_FILE" \
  '{tag_name: $tag, target_commitish: $sha, name: $name, body: $body,
    prerelease: true, draft: true}' \
  | post -X POST "$API/releases" -d @-)
REL_ID=$(echo "$REL" | jq -r .id)
[ -n "$REL_ID" ] && [ "$REL_ID" != "null" ] || { echo "release creation failed: $REL"; exit 1; }

for ASSET in "$@"; do
  NAME=$(basename "$ASSET")
  echo "uploading $NAME"
  post -X POST -H "Content-Type: application/octet-stream" -T "$ASSET" \
       "https://uploads.github.com/repos/$REPO/releases/$REL_ID/assets?name=$NAME" > /dev/null
done

post -X PATCH "$API/releases/$REL_ID" -d '{"draft": false}' > /dev/null
echo "release published: https://github.com/$REPO/releases/tag/$TAG"

# Retention: keep the newest $KEEP published matching releases.
releases \
  | jq -s -r --arg p "$PREFIX" --argjson k "$KEEP" \
      '[.[] | select(.draft | not) | select(.tag_name | startswith($p))]
       | sort_by(.created_at) | reverse | .[$k:] | .[] | "\(.id) \(.tag_name)"' \
  | while read -r ID T; do
      [ -n "$ID" ] || continue
      echo "pruning old release $T"
      del "$API/releases/$ID"
      del "$API/git/refs/tags/$T"
    done
