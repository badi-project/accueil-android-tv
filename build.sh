#!/usr/bin/env bash
# Compile l'écran d'accueil sans Gradle : aapt + javac + dx + zipalign + apksigner.
set -euo pipefail
cd "$(dirname "$0")"
SDK_JAR=/usr/lib/android-sdk/platforms/android-23/android.jar
OUT=build
rm -rf "$OUT" && mkdir -p "$OUT"/{gen,obj}

aapt package -f -m -J "$OUT/gen" -M AndroidManifest.xml -S res -I "$SDK_JAR"

javac -Xlint:-options -source 8 -target 8 -encoding UTF-8 \
  -bootclasspath "$SDK_JAR" -d "$OUT/obj" \
  $(find src "$OUT/gen" -name '*.java')

dalvik-exchange --dex --output="$OUT/classes.dex" "$OUT/obj"

aapt package -f -M AndroidManifest.xml -S res -I "$SDK_JAR" -F "$OUT/unsigned.apk"
(cd "$OUT" && aapt add -f unsigned.apk classes.dex >/dev/null)
zipalign -f 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"

KS=keystore.jks
if [ ! -f "$KS" ]; then
  keytool -genkeypair -keystore "$KS" -storepass accueil -keypass accueil -alias accueil \
    -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Accueil TV" >/dev/null 2>&1
fi
apksigner sign --ks "$KS" --ks-pass pass:accueil --key-pass pass:accueil \
  --out "$OUT/AccueilTV.apk" "$OUT/aligned.apk"
apksigner verify "$OUT/AccueilTV.apk" && ls -la "$OUT/AccueilTV.apk"
