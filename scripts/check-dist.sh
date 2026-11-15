#!/bin/bash
# Validates the artifacts handed over by the build run: exactly three
# packages with one common date stamp plus the release facts, nothing else,
# regular files only, sane sizes. The build ran code from the integration
# branch, so nothing is trusted beyond these checks. Prints the stamp.
# Usage: check-dist.sh <dist-dir>
set -euo pipefail
shopt -s dotglob nullglob
DIST="$1"
NAME_RE='^omegaT-experimental-integration-([0-9]{8})-(mac-arm\.zip|linux-x64\.tar\.bz2|windows-x64\.zip)$'
MIN_ASSET=$((10 * 1024 * 1024))
MAX_ASSET=$((1900 * 1024 * 1024))   # GitHub limit per asset is 2 GiB
MAX_FACTS=$((64 * 1024))

fail() { echo "::error::$*" >&2; exit 1; }

STAMP=""; PACKAGES=0; SUFFIXES=""
for f in "$DIST"/*; do
  NAME=$(basename "$f")
  [ -f "$f" ] && [ ! -L "$f" ] || fail "not a regular file: $NAME"
  SIZE=$(wc -c < "$f" | tr -d ' ')
  case "$NAME" in
    base.txt|ingredients.txt)
      [ "$SIZE" -le "$MAX_FACTS" ] || fail "too large: $NAME"
      continue ;;
  esac
  [[ "$NAME" =~ $NAME_RE ]] || fail "unexpected file: $NAME"
  [ -z "$STAMP" ] || [ "$STAMP" = "${BASH_REMATCH[1]}" ] || fail "mixed date stamps: $STAMP vs ${BASH_REMATCH[1]}"
  STAMP=${BASH_REMATCH[1]}
  case " $SUFFIXES " in *" ${BASH_REMATCH[2]} "*) fail "duplicate package: $NAME" ;; esac
  SUFFIXES="$SUFFIXES ${BASH_REMATCH[2]}"
  [ "$SIZE" -ge "$MIN_ASSET" ] || fail "suspiciously small: $NAME ($SIZE bytes)"
  [ "$SIZE" -le "$MAX_ASSET" ] || fail "too large: $NAME ($SIZE bytes)"
  PACKAGES=$((PACKAGES + 1))
done
[ "$PACKAGES" -eq 3 ] || fail "expected 3 packages, found $PACKAGES"
[ -f "$DIST/base.txt" ] && [ -f "$DIST/ingredients.txt" ] || fail "release facts missing"
echo "$STAMP"
