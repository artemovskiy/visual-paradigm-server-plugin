#!/bin/bash
# Build the VP plugin and install it into the user's VP plugin folder.
set -euo pipefail
cd "$(dirname "$0")"
API="/Applications/Visual Paradigm CE.app/Contents/Resources/app/lib/openapi.jar"
rm -rf build && mkdir -p build/classes build/plugin/ru.urfu.lab2/lib
javac --release 8 -encoding UTF-8 -cp "$API" -d build/classes $(find src -name '*.java')
jar cf build/plugin/ru.urfu.lab2/lib/lab2.jar -C build/classes .
cp plugin.xml build/plugin/ru.urfu.lab2/
(cd build/plugin && zip -qr ../ru.urfu.lab2.zip ru.urfu.lab2)
DEST="$HOME/Library/Application Support/VisualParadigm/plugins"
mkdir -p "$DEST"
rm -rf "$DEST/ru.urfu.lab2"
cp -R build/plugin/ru.urfu.lab2 "$DEST/"
echo "Installed to $DEST/ru.urfu.lab2"
