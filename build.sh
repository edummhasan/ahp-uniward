#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
BUILD="$ROOT/build"
CLASS_BUILD="$BUILD/classes"
NATIVE_BUILD="$BUILD/native"
mkdir -p "$CLASS_BUILD" "$NATIVE_BUILD"
JAVA_HOME_REAL="${JAVA_HOME:-$(dirname "$(dirname "$(readlink -f "$(which javac)")")") }"
JAVA_HOME_REAL="$(echo "$JAVA_HOME_REAL" | xargs)"
JNI_INC="$JAVA_HOME_REAL/include"
JNI_LINUX="$JAVA_HOME_REAL/include/linux"

echo "[1/4] Compiling Java..."
find "$ROOT/src/main/java" -name '*.java' -print0 | xargs -0 javac -d "$CLASS_BUILD"

echo "[2/4] Building JPEG JNI library..."
g++ -std=c++14 -O2 -fPIC -shared \
  -I"$JNI_INC" -I"$JNI_LINUX" \
  "$ROOT/src/main/c/jpegcore.cpp" \
  -ljpeg \
  -o "$NATIVE_BUILD/libjpegcore.so"

echo "[3/4] Building Original STC JNI library..."
g++ -std=c++14 -O3 -msse2 -fPIC -shared \
  -I"$JNI_INC" -I"$JNI_LINUX" \
  -I"$ROOT/src/main/native" \
  -I"$ROOT/src/main/native/original_stc" \
  "$ROOT/src/main/native/stc_jni_bridge.cpp" \
  "$ROOT/src/main/native/original_stc_adapter.cpp" \
  "$ROOT/src/main/native/original_stc/common.cpp" \
  "$ROOT/src/main/native/original_stc/stc_embed_c.cpp" \
  "$ROOT/src/main/native/original_stc/stc_extract_c.cpp" \
  -o "$NATIVE_BUILD/liboriginalstcjni.so"

echo "[4/4] Creating JAR..."
jar --create --file "$BUILD/ahp-100-jni-system.jar" --main-class thesis.ahp.app.Ahp100Cli -C "$CLASS_BUILD" .

echo "Built:"
echo "  $BUILD/ahp-100-jni-system.jar"
echo "  $NATIVE_BUILD/libjpegcore.so"
echo "  $NATIVE_BUILD/liboriginalstcjni.so"
