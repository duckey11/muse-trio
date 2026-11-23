#!/bin/bash
# Build manual Muse Trio tanpa Gradle daemon.
# Dipakai kalau Gradle daemon tidak bisa jalan (mis. sandbox tanpa IPC daemon).
# Hasilnya sama dengan ./gradlew assembleDebug: APK debug-signed.
#
# Kebutuhan: JDK 17, Android SDK (platform 34, build-tools 34.0.0),
# Kotlin compiler 1.9.22. Semua dibaca dari $JAVA_HOME / $ANDROID_HOME /
# $KOTLINC_HOME (default di bawah $HOME).
#
# Keystore debug disimpan di ~/.muse-trio/debug.keystore (di luar repo,
# supaya update berikutnya pakai signature yang sama). Bukan secret
# produksi, ini key debug standar (storepass "android").

set -e
ROOT="$(cd "$(dirname "$0")" && pwd)"
JAVA_HOME="${JAVA_HOME:-$HOME/jdk17}"
ANDROID_HOME="${ANDROID_HOME:-$HOME/.android-sdk}"
KOTLINC_HOME="${KOTLINC_HOME:-$HOME/kotlinc}"
export PATH="$JAVA_HOME/bin:$PATH"

BT="$ANDROID_HOME/build-tools/34.0.0"
ANDROID_JAR="$ANDROID_HOME/platforms/android-34/android.jar"
OUT="$ROOT/manual-build"
APK_NAME="muse-trio-1.0.0-debug.apk"

for tool in "$BT/aapt2" "$BT/d8" "$BT/zipalign" "$BT/apksigner" "$KOTLINC_HOME/bin/kotlinc" "$ANDROID_JAR"; do
    if [ ! -e "$tool" ]; then
        echo "Tidak ditemukan: $tool" >&2
        exit 1
    fi
done

rm -rf "$OUT"
mkdir -p "$OUT/r-gen" "$OUT/classes" "$OUT/dex"

echo "== 1. Compile resources (aapt2 compile) =="
"$BT/aapt2" compile --dir "$ROOT/app/src/main/res" -o "$OUT/compiled-res.zip"

echo "== 2. Link resources + manifest (aapt2 link) =="
# aapt2 butuh atribut package di manifest; Gradle mengisinya dari namespace,
# jadi manifest sementara dibuat dengan package disuntikkan.
sed 's|<manifest xmlns:android="http://schemas.android.com/apk/res/android">|<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.duckey11.musetrio">|' \
    "$ROOT/app/src/main/AndroidManifest.xml" > "$OUT/AndroidManifest.xml"
"$BT/aapt2" link -o "$OUT/base.apk" \
    --manifest "$OUT/AndroidManifest.xml" \
    -I "$ANDROID_JAR" \
    --java "$OUT/r-gen" \
    --auto-add-overlay \
    --min-sdk-version 28 \
    --target-sdk-version 34 \
    --version-code 1 \
    --version-name 1.0.0 \
    --debug-mode \
    "$OUT/compiled-res.zip"

echo "== 3. Compile Kotlin + R.java (kotlinc) =="
"$KOTLINC_HOME/bin/kotlinc" \
    -cp "$ANDROID_JAR" \
    -d "$OUT/classes" \
    "$OUT/r-gen/com/duckey11/musetrio/R.java" \
    "$ROOT/app/src/main/java/com/duckey11/musetrio/"*.kt

echo "== 4. Dex (d8) =="
# d8 tidak menerima file .kotlin_module di dalam folder classes,
# jadi hanya file .class yang diumpankan.
"$BT/d8" --output "$OUT/dex" \
    --lib "$ANDROID_JAR" \
    --min-api 28 \
    $(find "$OUT/classes" -name '*.class') \
    "$KOTLINC_HOME/lib/kotlin-stdlib.jar"

echo "== 5. Tambah classes.dex ke APK =="
python3 - "$OUT/base.apk" "$OUT/dex/classes.dex" << 'EOF'
import sys
import zipfile
apk, dex = sys.argv[1], sys.argv[2]
with zipfile.ZipFile(apk, 'a', zipfile.ZIP_DEFLATED) as z:
    z.write(dex, 'classes.dex')
print('classes.dex ditambahkan')
EOF

echo "== 6. Align + sign =="
KS_DIR="$HOME/.muse-trio"
KS="$KS_DIR/debug.keystore"
if [ ! -f "$KS" ]; then
    mkdir -p "$KS_DIR"
    keytool -genkeypair -keystore "$KS" -storepass android -keypass android \
        -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 \
        -dname "CN=Muse Trio Debug, OU=Muse Trio, O=duckey11, C=ID" -noprompt
    echo "Keystore debug baru dibuat di $KS"
fi
"$BT/zipalign" -f 4 "$OUT/base.apk" "$OUT/base.aligned.apk"
"$BT/apksigner" sign --ks "$KS" --ks-pass pass:android --key-pass pass:android \
    --ks-key-alias androiddebugkey \
    --out "$OUT/$APK_NAME" "$OUT/base.aligned.apk"

echo "== 7. Verifikasi =="
"$BT/apksigner" verify --verbose "$OUT/$APK_NAME"
"$BT/aapt" dump badging "$OUT/$APK_NAME" | head -5
ls -la "$OUT/$APK_NAME"
echo "SELESAI: $OUT/$APK_NAME"
