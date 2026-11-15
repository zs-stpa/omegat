#!/bin/bash
# Writes the release text. All prose comes from this branch; from the build
# run only base.txt (one SHA) and ingredients.txt (one "- `branch`" line per
# merge) are taken, each line checked against a strict pattern.
# Usage: compose-body.sh <dist-dir> <repo> <tag> <head-sha> > body.md
set -euo pipefail
DIST="$1"; REPO="$2"; TAG="$3"; HEAD_SHA="$4"
HERE=$(cd "$(dirname "$0")" && pwd)

fail() { echo "::error::$*" >&2; exit 1; }

BASE=$(cat "$DIST/base.txt")
[[ "$BASE" =~ ^[0-9a-f]{40}$ ]] || fail "base.txt is not a SHA"
[[ "$HEAD_SHA" =~ ^[0-9a-f]{40}$ ]] || fail "head SHA invalid"
COUNT=0; LIST=""
# "|| [ -n ]" also reads a last line without newline; only checked lines
# are ever written out.
while IFS= read -r line || [ -n "$line" ]; do
  [[ "$line" =~ ^-\ \`[A-Za-z0-9/._-]{1,120}\`$ ]] || fail "unexpected ingredient line: ${line:0:80}"
  LIST="$LIST$line"$'\n'
  COUNT=$((COUNT + 1))
done < "$DIST/ingredients.txt"
[ "$COUNT" -ge 1 ] && [ "$COUNT" -le 300 ] || fail "ingredient count out of range: $COUNT"

cat "$HERE/../text/DISCLAIMER-release.md"
echo
echo "**Base:** OmegaT master (merge-base \`${BASE:0:9}\`)"
echo
echo "**Integrated branches ($COUNT, merge order):**"
printf '%s' "$LIST"
echo
echo "_Source: tag [\`$TAG\`](https://github.com/$REPO/tree/$TAG) at \`${HEAD_SHA:0:9}\`._"
