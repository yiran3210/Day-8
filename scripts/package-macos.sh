#!/usr/bin/env bash
# Day 8 macOS app-image build. Run on macOS with JDK 25, Maven and Python 3.
set -euo pipefail

if [[ "$(uname -s)" != "Darwin" ]]; then
  echo 'Error: jpackage macOS bundles must be built on macOS.' >&2
  exit 1
fi

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

: "${JAVA_HOME:?Set JAVA_HOME to a JDK 25 installation}"
JPACKAGE="$JAVA_HOME/bin/jpackage"
JAVAC="$JAVA_HOME/bin/javac"
[[ -x "$JPACKAGE" && -x "$JAVAC" ]] || {
  echo "Error: JDK 25 jpackage/javac missing from JAVA_HOME=$JAVA_HOME" >&2
  exit 1
}
[[ "$("$JAVAC" -version 2>&1)" =~ ^javac\ 25([.\ ]|$) ]] || {
  echo 'Error: Java 25 is required.' >&2
  exit 1
}

case "$(uname -m)" in
  arm64) ARCH='arm64'; NATIVE='mac-aarch64' ;;
  x86_64) ARCH='x64'; NATIVE='mac' ;;
  *) echo "Unsupported Mac architecture: $(uname -m)" >&2; exit 1 ;;
esac

# Keep application version aligned with the Maven artifact.
VERSION="$(python3 - "$ROOT/pom.xml" <<'PY'
import sys
import xml.etree.ElementTree as ET
root = ET.parse(sys.argv[1]).getroot()
ns = {'m': 'http://maven.apache.org/POM/4.0.0'}
print(root.findtext('m:version', namespaces=ns) or '')
PY
)"
if [[ ! "$VERSION" =~ ^[0-9]+([.][0-9]+){1,3}$ ]]; then
  echo "Error: Maven project version must be numeric for jpackage; got '$VERSION'." >&2
  exit 1
fi

ICON="$ROOT/src/main/resources/icons/agenda-icon.icns"
[[ -s "$ICON" ]] || { echo "Missing macOS icon: $ICON" >&2; exit 1; }

INPUT="$ROOT/target/release-input-macos"
DIST="$ROOT/dist"
APP="$DIST/Day 8.app"
ZIP="$DIST/Day8-v${VERSION}-macos-${ARCH}.zip"
JAR="$ROOT/target/personal-agenda-${VERSION}.jar"

printf '\n[1/4] Build Day 8 %s for macOS %s\n' "$VERSION" "$ARCH"
mvn -B -ntp clean verify

printf '\n[2/4] Collect matching platform dependencies\n'
mkdir -p "$INPUT" "$DIST"
# clean verify removes target/: create the release directory AFTER it succeeds.
mkdir -p "$INPUT"
mvn -B -ntp dependency:copy-dependencies \
  -DincludeScope=runtime "-DoutputDirectory=$INPUT"
[[ -f "$JAR" ]] || { echo "Missing application JAR: $JAR" >&2; exit 1; }
cp "$JAR" "$INPUT/"

# A mismatched JavaFX graphics native library can compile but will fail at launch.
shopt -s nullglob
graphics=( "$INPUT"/javafx-graphics-*"-$NATIVE.jar" )
sqlite=( "$INPUT"/sqlite-jdbc-*.jar )
if (( ${#graphics[@]} == 0 || ${#sqlite[@]} == 0 )); then
  echo "Missing macOS JavaFX native ($NATIVE) or SQLite JDBC runtime dependency." >&2
  printf 'Collected JARs:\n' >&2
  ls -1 "$INPUT"/*.jar >&2 || true
  exit 1
fi

printf '\n[3/4] Build self-contained macOS .app\n'
rm -rf "$APP"
"$JPACKAGE" \
  --type app-image \
  --name 'Day 8' \
  --app-version "1.2.1" \
  --description 'Day 8 (星期八) personal planning and productivity' \
  --input "$INPUT" \
  --main-jar "$(basename "$JAR")" \
  --main-class dev.personalagenda.Launcher \
  --icon "$ICON" \
  --mac-package-identifier dev.personalagenda.day8 \
  --mac-package-name 'Day 8' \
  --mac-app-category public.app-category.productivity \
  --java-options '--enable-native-access=ALL-UNNAMED' \
  --dest "$DIST"

[[ -x "$APP/Contents/MacOS/Day 8" ]] || {
  echo "jpackage did not produce the expected app bundle: $APP" >&2
  exit 1
}

printf '\n[4/4] Create portable ZIP (preserve .app symlinks)\n'
rm -f "$ZIP"
ditto -c -k --keepParent "$APP" "$ZIP"
[[ -s "$ZIP" ]] || { echo 'ZIP creation failed.' >&2; exit 1; }

printf '\nReady:\n  App: %s\n  ZIP: %s\n' "$APP" "$ZIP"
shasum -a 256 "$ZIP"
printf '\nNote: The app is not Developer ID signed or Apple notarized.\n'
