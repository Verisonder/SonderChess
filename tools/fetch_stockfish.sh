#!/usr/bin/env bash
# Downloads the official Stockfish build for 64-bit ARM phones and puts it where Android
# installs native libraries, so it lands on disk as an executable file.
set -euo pipefail
VERSION="${STOCKFISH_VERSION:-sf_17.1}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/app/src/main/jniLibs/arm64-v8a"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
URL="https://github.com/official-stockfish/Stockfish/releases/download/$VERSION/stockfish-android-armv8.tar"
echo "Downloading $URL"
curl -fsSL --retry 3 -o "$TMP/sf.tar" "$URL"
tar -xf "$TMP/sf.tar" -C "$TMP"
mkdir -p "$OUT"
cp "$TMP/stockfish/stockfish-android-armv8" "$OUT/libstockfish.so"
chmod +x "$OUT/libstockfish.so"
ls -la "$OUT"
