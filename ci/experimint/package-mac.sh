#!/bin/bash
# Patches, ad-hoc-signs and zips the built Mac app.
# Usage: package-mac.sh <omegat-checkout-dir> <YYYYMMDD> <app display name>
set -euo pipefail
SRC="$1"; STAMP="$2"; APP_NAME="$3"

APP=$(ls -d "$SRC"/build/install/OmegaT-*-Mac_arm/OmegaT.app 2>/dev/null | head -1)
[ -n "$APP" ] || { echo "no OmegaT.app under build/install"; exit 1; }

MODULES=$(ls "$APP/Contents/Java/modules" | wc -l | tr -d ' ')
[ "$MODULES" -ge 40 ] || { echo "only $MODULES modules — incomplete dist?"; exit 1; }

# Nothing licensed may ride along: a plain source build must not carry plugins.
if ls "$APP/Contents/Java/plugins"/*.jar >/dev/null 2>&1; then
  echo "unexpected plugin jars in dist:"; ls "$APP/Contents/Java/plugins"; exit 1
fi

STAGE="omegaT-experimental-integration-$STAMP"
rm -rf "$STAGE" dist
mkdir -p "$STAGE" dist
ditto "$APP" "$STAGE/$APP_NAME.app"
APP="$STAGE/$APP_NAME.app"

python3 - "$APP/Contents/Info.plist" "$APP_NAME" "$STAMP" <<'PYEOF'
import plistlib, sys
path, name, stamp = sys.argv[1:4]
with open(path, 'rb') as f:
    d = plistlib.load(f)
d['CFBundleName'] = name
d['CFBundleDisplayName'] = name
d['CFBundleIdentifier'] = d.get('CFBundleIdentifier', 'org.omegat.OmegaT') + '.experimint'
d['CFBundleVersion'] = stamp
opts = d.get('JVMOptions', [])
for i, o in enumerate(opts):
    if o.startswith('-Xdock:name='):
        opts[i] = '-Xdock:name=' + name
    elif o.startswith('-Dapple.awt.application.name='):
        opts[i] = '-Dapple.awt.application.name=' + name
with open(path, 'wb') as f:
    plistlib.dump(d, f)
PYEOF

# The plist patch invalidates the linker signature; on arm64 unsigned code
# does not launch at all, so re-sign ad hoc (no identity, not notarized).
codesign --force --deep --sign - "$APP"

cp "$(cd "$(dirname "$0")" && pwd)/DISCLAIMER.txt" "$STAGE/DISCLAIMER.txt"
ditto -c -k --keepParent "$STAGE" "dist/${STAGE}-mac-arm.zip"
echo "packaged: dist/${STAGE}-mac-arm.zip"
