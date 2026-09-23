#!/bin/bash
# Emits the ingredient section of the release body from first-parent merges.
# Usage: release-body.sh <checkout-dir> <base-ref> <source-branch>
set -euo pipefail
cd "$1"; BASE_REF="$2"; SOURCE_BRANCH="$3"

BASE=$(git merge-base HEAD "$BASE_REF")
COUNT=$(git rev-list --merges --first-parent --count "$BASE"..HEAD)

echo "**Base:** OmegaT master (merge-base \`$(git rev-parse --short=9 "$BASE")\`)"
echo
echo "**Integrated branches ($COUNT, merge order):**"
git log --merges --first-parent --reverse --format='%s' "$BASE"..HEAD \
  | sed -E "s/^Merge remote-tracking branch '([^']+)'.*/\1/; s/^Merge branch '([^']+)'.*/\1/" \
  | sed -E 's|^origin-zs/||' \
  | sed -e 's/^/- `/' -e 's/$/`/'
echo
echo "_Source: branch [\`$SOURCE_BRANCH\`](https://github.com/zs-stpa/omegat/tree/$SOURCE_BRANCH) at \`$(git rev-parse --short=9 HEAD)\` — this branch is rebuilt from scratch and force-pushed for every snapshot._"
