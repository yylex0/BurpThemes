#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"
SRC_DIR="$ROOT_DIR/src"
BUILD_DIR="$ROOT_DIR/build"
CLASSES_DIR="$BUILD_DIR/classes"
RESOURCES_DIR="$BUILD_DIR/resources"
MANIFEST_FILE="$BUILD_DIR/manifest.mf"
DIST_DIR="$ROOT_DIR/dist"

BURP_JAR="${BURP_JAR:-/usr/share/burpsuite/burpsuite.jar}"
JAVAC_BIN="${JAVAC_BIN:-$(command -v javac || true)}"
JAVAP_BIN="${JAVAP_BIN:-$(command -v javap || true)}"
PYTHON_BIN="${PYTHON_BIN:-$(command -v python3 || true)}"
JAR_BIN="${JAR_BIN:-$(command -v jar || true)}"
UNZIP_BIN="${UNZIP_BIN:-$(command -v unzip || true)}"
STRINGS_BIN="${STRINGS_BIN:-$(command -v strings || true)}"
GREP_BIN="${GREP_BIN:-$(command -v grep || true)}"
DIFF_BIN="${DIFF_BIN:-$(command -v diff || true)}"
FIND_BIN="${FIND_BIN:-$(command -v find || true)}"
SORT_BIN="${SORT_BIN:-$(command -v sort || true)}"
JAVAC_RELEASE="${JAVAC_RELEASE:-17}"
OUTPUT_JAR="$DIST_DIR/arcade-burp-community.jar"
THEME_VALIDATOR="$ROOT_DIR/tools/validate_themes.py"
UI_COVERAGE_VERIFIER="$ROOT_DIR/tools/verify_ui_coverage.py"
VISUAL_POLISH_VERIFIER="$ROOT_DIR/tools/verify_visual_polish.py"
WINDOW_POLISH_VERIFIER="$ROOT_DIR/tools/verify_window_polish.py"
SMOKE_TESTER="$ROOT_DIR/tools/smoke_test_extension.py"

require_executable()
{
  local label="$1"
  local path="$2"
  local variable="$3"
  if [[ -z "$path" || ! -x "$path" ]]; then
    echo "$label not found: ${path:-<empty>}" >&2
    echo "Set $variable=/absolute/path and rerun." >&2
    exit 1
  fi
}

if [[ ! -f "$BURP_JAR" ]]; then
  echo "Burp JAR not found: $BURP_JAR" >&2
  echo "Set BURP_JAR=/absolute/path/to/burpsuite.jar and rerun." >&2
  exit 1
fi

require_executable "Java compiler" "$JAVAC_BIN" "JAVAC_BIN"
require_executable "javap" "$JAVAP_BIN" "JAVAP_BIN"
require_executable "jar" "$JAR_BIN" "JAR_BIN"
require_executable "unzip" "$UNZIP_BIN" "UNZIP_BIN"
require_executable "strings" "$STRINGS_BIN" "STRINGS_BIN"
require_executable "grep" "$GREP_BIN" "GREP_BIN"
require_executable "diff" "$DIFF_BIN" "DIFF_BIN"
require_executable "find" "$FIND_BIN" "FIND_BIN"
require_executable "sort" "$SORT_BIN" "SORT_BIN"
require_executable "Python 3" "$PYTHON_BIN" "PYTHON_BIN"

"$PYTHON_BIN" - <<'PY'
import sys
if sys.version_info < (3, 9):
    raise SystemExit("Python 3.9+ is required for theme validation.")
PY

"$PYTHON_BIN" "$THEME_VALIDATOR"
"$PYTHON_BIN" "$UI_COVERAGE_VERIFIER"
BURP_JAR="$BURP_JAR" "$PYTHON_BIN" "$VISUAL_POLISH_VERIFIER"
if [[ -n "${DISPLAY:-}" || -n "$(command -v Xvfb || true)" ]]; then
  BURP_JAR="$BURP_JAR" "$PYTHON_BIN" "$WINDOW_POLISH_VERIFIER"
else
  echo "Skipping window polish verifier because DISPLAY and Xvfb are unavailable." >&2
fi

mapfile -t EXPECTED_ASSETS < <("$PYTHON_BIN" "$THEME_VALIDATOR" --list-assets)

if [[ "${#EXPECTED_ASSETS[@]}" -eq 0 ]]; then
  echo "No theme assets found in BurpTheme.java" >&2
  exit 1
fi

rm -rf "$BUILD_DIR"
mkdir -p "$CLASSES_DIR" "$RESOURCES_DIR/assets" "$DIST_DIR"

mapfile -t JAVA_SOURCES < <("$FIND_BIN" "$SRC_DIR" -name '*.java' | "$SORT_BIN")

"$JAVAC_BIN" \
  --release "$JAVAC_RELEASE" \
  -classpath "$BURP_JAR" \
  -sourcepath "$SRC_DIR" \
  -implicit:none \
  -d "$CLASSES_DIR" \
  "${JAVA_SOURCES[@]}"

cat > "$MANIFEST_FILE" <<EOF
Manifest-Version: 1.0
Burp-Extension-Class: burp.arcade.BurpThemeExtension
Implementation-Title: BurpTheme
Implementation-Version: 1.0.0
EOF

for asset in "${EXPECTED_ASSETS[@]}"
do
  if [[ ! -f "$ROOT_DIR/$asset" ]]; then
    echo "Missing asset: $asset" >&2
    exit 1
  fi
  cp "$ROOT_DIR/$asset" "$RESOURCES_DIR/$asset"
done

"$JAR_BIN" cfm "$OUTPUT_JAR" "$MANIFEST_FILE" -C "$CLASSES_DIR" . -C "$RESOURCES_DIR" assets

mapfile -t JAR_CONTENTS < <("$JAR_BIN" tf "$OUTPUT_JAR")
for asset in "${EXPECTED_ASSETS[@]}"
do
  printf '%s\n' "${JAR_CONTENTS[@]}" | "$GREP_BIN" -Fxq "$asset"
done
mapfile -t JAR_ASSETS < <(printf '%s\n' "${JAR_CONTENTS[@]}" | "$GREP_BIN" '^assets/' | "$GREP_BIN" -v '/$' | "$SORT_BIN")
mapfile -t EXPECTED_SORTED < <(printf '%s\n' "${EXPECTED_ASSETS[@]}" | "$SORT_BIN")
if ! "$DIFF_BIN" -u <(printf '%s\n' "${EXPECTED_SORTED[@]}") <(printf '%s\n' "${JAR_ASSETS[@]}"); then
  echo "Jar asset set does not match the active theme registry." >&2
  exit 1
fi
"$JAVAP_BIN" -classpath "$OUTPUT_JAR" -private burp.arcade.BurpTheme | "$GREP_BIN" -q "GREAT_PLATEAU"
"$JAVAP_BIN" -classpath "$OUTPUT_JAR" -private burp.arcade.BurpTheme | "$GREP_BIN" -q "GLOOM_DEPTHS"
if "$JAVAP_BIN" -classpath "$OUTPUT_JAR" -private burp.arcade.BurpTheme | "$GREP_BIN" -q "TWILIGHT_REALM"; then
  echo "Stale Twilight Realm enum constant found in jar." >&2
  exit 1
fi
"$JAVAP_BIN" -classpath "$OUTPUT_JAR" -private burp.arcade.BurpTheme | "$GREP_BIN" -q "avatarResource"
"$JAVAP_BIN" -classpath "$OUTPUT_JAR" -private burp.arcade.BurpTheme | "$GREP_BIN" -q "previewResource"
"$JAVAP_BIN" -classpath "$OUTPUT_JAR" -private burp.arcade.BurpTheme | "$GREP_BIN" -q "detailResource"
"$JAVAP_BIN" -classpath "$OUTPUT_JAR" -private burp.arcade.BurpTheme | "$GREP_BIN" -q "motionResource"
if "$JAR_BIN" tf "$OUTPUT_JAR" | "$GREP_BIN" -q 'ThemeQuickSwitchPanel'; then
  echo "Stale quick-switch widget class found in jar." >&2
  exit 1
fi
"$JAR_BIN" tf "$OUTPUT_JAR" | "$GREP_BIN" -q 'BurpThemeEngine\$ProxyPillButtonUI.class'
if "$JAVAP_BIN" -classpath "$OUTPUT_JAR" -private burp.arcade.BurpThemeExtension | "$GREP_BIN" -q "registerThemeMenu"; then
  echo "Theme-switching menu registration should not be present in jar." >&2
  exit 1
fi
"$JAVAP_BIN" -classpath "$OUTPUT_JAR" -private burp.arcade.BurpThemeExtension | "$GREP_BIN" -q "deregisterRegistrations"
if "$JAVAP_BIN" -classpath "$OUTPUT_JAR" -private burp.arcade.BurpThemeExtension | "$GREP_BIN" -q "deregisterTabs"; then
  echo "Stale deregisterTabs method found in jar." >&2
  exit 1
fi
mapfile -t EXTENSION_STRINGS < <("$UNZIP_BIN" -p "$OUTPUT_JAR" burp/arcade/BurpThemeExtension.class | "$STRINGS_BIN")
printf '%s\n' "${EXTENSION_STRINGS[@]}" | "$GREP_BIN" -Fxq "selectedThemeId"
printf '%s\n' "${EXTENSION_STRINGS[@]}" | "$GREP_BIN" -Fxq "selectedTheme"
printf '%s\n' "${EXTENSION_STRINGS[@]}" | "$GREP_BIN" -Fxq "suiteWideThemeEnabledV2"
if printf '%s\n' "${EXTENSION_STRINGS[@]}" | "$GREP_BIN" -Fxq "suiteWideTheme"; then
  echo "Stale suite-wide preference key found in jar." >&2
  exit 1
fi

PYTHONDONTWRITEBYTECODE=1 "$PYTHON_BIN" "$SMOKE_TESTER" --jar "$OUTPUT_JAR" --burp-jar "$BURP_JAR" --release "$JAVAC_RELEASE" --skip-compile

echo "Built $OUTPUT_JAR"
