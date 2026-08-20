#!/usr/bin/env bash
set -euo pipefail

# Workaround untuk bug upstream app_links 6.4.1 (Flutter 3.24.x):
# android/build.gradle line 28 memakai `flutter.compileSdkVersion` yang
# tidak ter-resolve di AGP 8.x, menyebabkan:
#   "Could not get unknown property 'flutter' for extension 'android'"
# Patch: ganti dengan compileSdk literal. Dipanggil oleh CI sebelum build APK.

BASE="${PUB_CACHE:-$HOME/.pub-cache}/hosted/pub.dev"
GRADLE="$BASE/app_links-6.4.1/android/build.gradle"

if [[ ! -f "$GRADLE" ]]; then
  echo "app_links build.gradle tidak ditemukan di $GRADLE"
  exit 1
fi

if grep -q "flutter.compileSdkVersion" "$GRADLE"; then
  sed -i 's/compileSdk = flutter.compileSdkVersion/compileSdk = 34/' "$GRADLE"
  echo "Patched $GRADLE -> compileSdk = 34"
else
  echo "app_links build.gradle sudah ter-patch, skip"
fi