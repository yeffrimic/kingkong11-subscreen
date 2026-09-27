#!/usr/bin/env bash
# Compila, empaqueta, firma e instala Subscreen Poke sin Gradle ni Android Studio.
#
# Configura las rutas con variables de entorno. Ejemplo:
#   export TOOLS=/c/Android            # contiene platform-tools/ y build-tools/
#   export JDK=/c/jdk-21               # cualquier JDK 17+
#   export ANDROID_JAR=/c/android.jar  # android.jar de API 34
#   ./build.sh
set -euo pipefail
export MSYS_NO_PATHCONV=1

ROOT="${ROOT:-$(cd "$(dirname "$0")" && pwd)}"
TOOLS="${TOOLS:?define TOOLS: carpeta con platform-tools/ y build-tools/}"
BT="${BT:-$TOOLS/build-tools/android-14}"
JDK="${JDK:?define JDK: ruta a un JDK 17+}"
ANDROID_JAR="${ANDROID_JAR:-$TOOLS/android.jar}"
ADB="${ADB:-$TOOLS/platform-tools/adb.exe}"

export JAVA_HOME="$JDK"
JAVAC="$JDK/bin/javac"
KEYTOOL="$JDK/bin/keytool"
JAVA="$JDK/bin/java"
AAPT2="$BT/aapt2.exe"
ZIPALIGN="$BT/zipalign.exe"

BUILD="$ROOT/build"
rm -rf "$BUILD"
mkdir -p "$BUILD/classes" "$BUILD/dex"

echo "== 1. javac =="
find "$ROOT/src" -name '*.java' > "$BUILD/sources.txt"
"$JAVAC" -source 8 -target 8 -bootclasspath "$ANDROID_JAR" \
    -d "$BUILD/classes" @"$BUILD/sources.txt" 2>&1 | grep -v 'system modules path' || true

echo "== 2. d8 (class -> dex) =="
( cd "$BUILD/classes" && "$JDK/bin/jar" -cf "$BUILD/app-classes.jar" . )
"$JAVA" -cp "$BT/lib/d8.jar" com.android.tools.r8.D8 \
    --min-api 29 --lib "$ANDROID_JAR" \
    --output "$BUILD/dex" "$BUILD/app-classes.jar"

echo "== 3. aapt2 link (manifest -> apk base) =="
"$AAPT2" link -o "$BUILD/base.apk" -I "$ANDROID_JAR" \
    --manifest "$ROOT/AndroidManifest.xml" \
    --min-sdk-version 29 --target-sdk-version 34

echo "== 4. meter classes.dex en el apk =="
cp "$BUILD/dex/classes.dex" "$BUILD/classes.dex"
( cd "$BUILD" && "$JAVA" -cp "$BT/lib/d8.jar" com.android.tools.r8.D8 --version >/dev/null 2>&1 || true )
# usar el jar del JDK para agregar la entrada (evita dependencias externas)
"$JDK/bin/jar" -uf "$BUILD/base.apk" -C "$BUILD" classes.dex

echo "== 5. zipalign =="
"$ZIPALIGN" -f -p 4 "$BUILD/base.apk" "$BUILD/aligned.apk"

echo "== 6. keystore de debug (si no existe) =="
KS="$ROOT/debug.keystore"
if [ ! -f "$KS" ]; then
    "$KEYTOOL" -genkeypair -v -keystore "$KS" -storepass android -keypass android \
        -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 \
        -dname "CN=Android Debug,O=Android,C=US"
fi

echo "== 7. firmar =="
"$BT/apksigner.bat" sign --ks "$KS" --ks-pass pass:android --key-pass pass:android \
    --out "$BUILD/subscreen-poke.apk" "$BUILD/aligned.apk"
"$BT/apksigner.bat" verify --print-certs "$BUILD/subscreen-poke.apk" | head -3

echo "== 8. instalar =="
"$ADB" install -r "$BUILD/subscreen-poke.apk"

echo "OK -> $BUILD/subscreen-poke.apk"
