#!/bin/bash
# Writes the release facts for the publisher (experimint/publisher branch,
# scripts/compose-body.sh): base.txt = merge-base SHA with upstream,
# ingredients.txt = one "- `branch`" line per first-parent merge. Plain data
# only: the publisher validates every line and writes all prose itself.
# Usage: release-body.sh <checkout-dir> <base-ref> <out-dir>
set -euo pipefail
cd "$1"; BASE_REF="$2"; OUT="$3"
mkdir -p "$OUT"

BASE=$(git merge-base HEAD "$BASE_REF")
echo "$BASE" > "$OUT/base.txt"
git log --merges --first-parent --reverse --format='%s' "$BASE"..HEAD \
  | sed -E "s/^Merge remote-tracking branch '([^']+)'.*/\1/; s/^Merge branch '([^']+)'.*/\1/" \
  | sed -E 's|^origin-zs/||' \
  | sed -e 's/^/- `/' -e 's/$/`/' > "$OUT/ingredients.txt"
