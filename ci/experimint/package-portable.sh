#!/bin/bash
# Packs a built Linux or Windows tree as a portable archive. No installer:
# the upstream installers (deb/rpm, Inno Setup AppId org.omegat) would
# replace an official OmegaT installation.
# Usage: package-portable.sh <dist-dir> <YYYYMMDD> <platform suffix> <zip|tar.bz2>
set -euo pipefail
DIST="$1"; STAMP="$2"; SUFFIX="$3"; FORMAT="$4"

[ -f "$DIST/OmegaT.jar" ] || { echo "no OmegaT.jar under $DIST"; exit 1; }
[ -d "$DIST/jre" ] || { echo "no bundled jre under $DIST"; exit 1; }

MODULES=$(ls "$DIST/modules" | wc -l | tr -d ' ')
[ "$MODULES" -ge 40 ] || { echo "only $MODULES modules — incomplete dist?"; exit 1; }

# Nothing licensed may ride along: a plain source build must not carry plugins.
if ls "$DIST/plugins"/*.jar >/dev/null 2>&1; then
  echo "unexpected plugin jars in dist:"; ls "$DIST/plugins"; exit 1
fi

STAGE="omegaT-experimental-integration-$STAMP"
rm -rf "$STAGE"
mkdir -p dist
cp -a "$DIST" "$STAGE"
# Installer leftovers that would touch an official installation.
rm -f "$STAGE/linux-install.sh" "$STAGE/OmegaT.iss"
cp "$(cd "$(dirname "$0")" && pwd)/DISCLAIMER.txt" "$STAGE/DISCLAIMER.txt"

OUT="dist/${STAGE}-${SUFFIX}.${FORMAT}"
case "$FORMAT" in
  zip)     zip -qr "$OUT" "$STAGE" ;;
  tar.bz2) tar -cjf "$OUT" "$STAGE" ;;
  *)       echo "unknown format $FORMAT"; exit 1 ;;
esac
echo "packaged: $OUT"
