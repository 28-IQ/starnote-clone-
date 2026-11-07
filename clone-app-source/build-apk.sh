#!/usr/bin/env bash
# Builds a debug APK on your own computer. Needs: JDK 17, the Android SDK (ANDROID_HOME set), and Gradle 8.11 or newer.
# Easiest: install Android Studio, open this folder once so it downloads the SDK, then run this script from its terminal.
set -euo pipefail

command -v gradle >/dev/null || { echo "Gradle is not installed. Install Gradle 8.11+ (https://gradle.org/install/) or build from Android Studio."; exit 1; }
[ -n "${ANDROID_HOME:-}" ] || { echo "ANDROID_HOME is not set. In Android Studio: Settings > Languages & Frameworks > Android SDK shows the path."; exit 1; }

gradle :app:assembleDebug --stacktrace 2>&1 | tee build-output.txt
echo
echo "APK: $(pwd)/app/build/outputs/apk/debug/app-debug.apk"
