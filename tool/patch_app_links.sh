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

# Workaround Start.io SDK 0.1.6: missing buildFeatures.buildConfig & namespace (AGP 8)
STARTAPP_GRADLE="$BASE/startapp_sdk-0.1.6/android/build.gradle"
if [[ -f "$STARTAPP_GRADLE" ]]; then
  if ! grep -q "buildFeatures" "$STARTAPP_GRADLE"; then
    sed -i '/^android {/a \    buildFeatures {\n        buildConfig true\n    }' "$STARTAPP_GRADLE"
    echo "Patched $STARTAPP_GRADLE -> buildFeatures.buildConfig true"
  else
    echo "startapp_sdk build.gradle buildFeatures sudah ter-patch, skip"
  fi
  if ! grep -q "namespace" "$STARTAPP_GRADLE"; then
    sed -i '/^android {/a \    namespace '\''com.startapp.flutter.sdk'\''' "$STARTAPP_GRADLE"
    echo "Patched $STARTAPP_GRADLE -> namespace com.startapp.flutter.sdk"
  else
    echo "startapp_sdk namespace sudah ada, skip"
  fi
  if grep -q "compileSdkVersion 31" "$STARTAPP_GRADLE"; then
    sed -i 's/compileSdkVersion 31/compileSdkVersion 34/' "$STARTAPP_GRADLE"
    echo "Patched $STARTAPP_GRADLE -> compileSdkVersion 34"
  fi
else
  echo "startapp_sdk build.gradle tidak ditemukan di $STARTAPP_GRADLE (skip)"
fi