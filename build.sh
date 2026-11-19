#!/usr/bin/env bash
set -euo pipefail

WS="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
DL="$WS/tools/dl"
LINUX_TOOLS="$WS/tools/linux"
JDK_ARCHIVE="$DL/OpenJDK17U-jdk_x64_linux_hotspot_17.0.21_9.tar.gz"
BT_ARCHIVE="$DL/build-tools_r34-linux.zip"
JDK="$LINUX_TOOLS/jdk-17.0.21+9"
BT="$LINUX_TOOLS/build-tools-34.0.0/android-14"
PLATFORM="$WS/tools/sdk/platforms/android-34/android.jar"
XPOSE="$WS/tools/libxposed-ext/classes.jar"
SRC="$WS/module/src/main"
OUT="${OUT_DIR:-$WS/build}"
KEYSTORE="${KEYSTORE_PATH:-$WS/tools/keystore.jks}"

for tool in tar unzip; do
  command -v "$tool" >/dev/null || { echo "[ERROR] Required tool not found: $tool" >&2; exit 1; }
done

if [[ ! -x "$JDK/bin/javac" ]]; then
  [[ -f "$JDK_ARCHIVE" ]] || { echo "[ERROR] JDK archive not found: $JDK_ARCHIVE" >&2; exit 1; }
  mkdir -p "$JDK"
  tar -xzf "$JDK_ARCHIVE" --strip-components=1 -C "$JDK"
fi

if [[ ! -x "$BT/aapt2" ]]; then
  [[ -f "$BT_ARCHIVE" ]] || { echo "[ERROR] Build-tools archive not found: $BT_ARCHIVE" >&2; exit 1; }
  mkdir -p "$LINUX_TOOLS/build-tools-34.0.0"
  unzip -oq "$BT_ARCHIVE" -d "$LINUX_TOOLS/build-tools-34.0.0"
  chmod +x "$BT/aapt2" "$BT/d8" "$BT/zipalign" "$BT/apksigner"
fi

for path in "$PLATFORM" "$XPOSE" "$KEYSTORE"; do
  [[ -f "$path" ]] || { echo "[ERROR] Required file not found: $path" >&2; exit 1; }
done

JAVAC="$JDK/bin/javac"
JAR="$JDK/bin/jar"
export JAVA_HOME="$JDK"
export PATH="$JDK/bin:$PATH"

rm -rf "$OUT"
mkdir -p "$OUT/classes" "$OUT/dex" "$OUT/gen"

echo "[1/6] aapt2 compile"
"$BT/aapt2" compile --dir "$SRC/res" -o "$OUT/res.zip"

echo "[2/6] aapt2 link"
"$BT/aapt2" link -o "$OUT/base.apk" \
  -I "$PLATFORM" \
  --manifest "$WS/module/AndroidManifest.xml" \
  --java "$OUT/gen" \
  --min-sdk-version 29 --target-sdk-version 34 \
  "$OUT/res.zip"

echo "[3/6] javac"
mapfile -d '' GENERATED < <(find "$OUT/gen" -name '*.java' -print0)
mapfile -d '' SOURCES < <(find "$SRC/java" -name '*.java' -print0)
"$JAVAC" -source 17 -target 17 -encoding UTF-8 -nowarn -Xlint:-options \
  -classpath "$PLATFORM:$XPOSE" \
  -d "$OUT/classes" "${GENERATED[@]}" "${SOURCES[@]}"

echo "[4/6] d8"
mapfile -d '' CLASSES < <(find "$OUT/classes" -name '*.class' -print0)
"$BT/d8" --min-api 29 --lib "$PLATFORM" --classpath "$XPOSE" \
  --output "$OUT/dex" "${CLASSES[@]}"

echo "[5/6] package and align"
cp "$OUT/base.apk" "$OUT/AODCoverWallpaper-unsigned.apk"
(cd "$OUT/dex" && "$JAR" uf "$OUT/AODCoverWallpaper-unsigned.apk" classes.dex)
(cd "$SRC/assets" && "$JAR" uf "$OUT/AODCoverWallpaper-unsigned.apk" META-INF/xposed)
"$BT/zipalign" -f 4 "$OUT/AODCoverWallpaper-unsigned.apk" \
  "$OUT/AODCoverWallpaper-aligned.apk"

echo "[6/6] sign and verify"
"$BT/apksigner" sign \
  --ks "$KEYSTORE" \
  --ks-pass "pass:${KEYSTORE_PASSWORD:-android}" \
  --key-pass "pass:${KEY_PASSWORD:-android}" \
  --ks-key-alias "${KEY_ALIAS:-aodcoverwallpaper}" \
  --out "$OUT/AODCoverWallpaper.apk" "$OUT/AODCoverWallpaper-aligned.apk"
"$BT/apksigner" verify "$OUT/AODCoverWallpaper.apk"

echo "[OK] $OUT/AODCoverWallpaper.apk"
