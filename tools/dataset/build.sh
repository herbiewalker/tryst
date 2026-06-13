#!/usr/bin/env bash
# Reproducibly (re)build samples/tryst-test-dataset.tryst.
#
#   1. generate_dataset.py  -> data.json + media/<id> PNGs (stdlib only)
#   2. Pack.java (real Tink) -> the encrypted .tryst container, byte-compatible with the app
#
# Requires: python3, a JDK (17+). Tink + deps are fetched from Maven Central on first run.
set -euo pipefail
cd "$(dirname "$0")"

PASSWORD="${1:-Tryst-Test-2026}"
OUT="${2:-../../samples/tryst-test-dataset.tryst}"
BUILD="$(mktemp -d)"
LIB=".tinklib"

TINK_VER="1.15.0"
declare -A JARS=(
  ["tink-android-$TINK_VER.jar"]="com/google/crypto/tink/tink-android/$TINK_VER/tink-android-$TINK_VER.jar"
  ["protobuf-java-3.25.1.jar"]="com/google/protobuf/protobuf-java/3.25.1/protobuf-java-3.25.1.jar"
  ["gson-2.10.1.jar"]="com/google/code/gson/gson/2.10.1/gson-2.10.1.jar"
  ["error_prone_annotations-2.23.0.jar"]="com/google/errorprone/error_prone_annotations/2.23.0/error_prone_annotations-2.23.0.jar"
)
mkdir -p "$LIB"
for name in "${!JARS[@]}"; do
  [ -f "$LIB/$name" ] || curl -sSL -o "$LIB/$name" "https://repo1.maven.org/maven2/${JARS[$name]}"
done
CP="$(printf '%s:' "$LIB"/*.jar)"

echo ">> generating dataset"
python3 generate_dataset.py "$BUILD"

echo ">> packing encrypted backup"
javac -d "$BUILD" -cp "$CP" Pack.java Verify.java
java -cp "$CP$BUILD" Pack "$BUILD" "$OUT" "$PASSWORD"
java -cp "$CP$BUILD" Verify "$OUT" "$PASSWORD"

rm -rf "$BUILD"
echo ">> done: $OUT  (import password: $PASSWORD)"
