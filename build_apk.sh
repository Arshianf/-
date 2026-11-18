#!/usr/bin/env bash
set -euo pipefail

if ! command -v gradle >/dev/null 2>&1; then
  echo "Gradle پیدا نشد. پروژه را در Android Studio باز کنید تا Gradle Sync انجام شود." >&2
  exit 1
fi

gradle --no-daemon clean :app:assembleDebug
APK="app/build/outputs/apk/debug/app-debug.apk"
if [[ ! -f "$APK" ]]; then
  echo "خروجی APK ساخته نشد." >&2
  exit 1
fi
cp "$APK" "imen_saz.apk"
BYTES=$(wc -c < "imen_saz.apk")
echo "APK ساخته شد: imen_saz.apk"
echo "حجم دقیق: ${BYTES} bytes"
echo "حجم تقریبی: $(awk -v n="$BYTES" 'BEGIN {printf "%.2f MB (decimal), %.2f MiB", n/1000000, n/1048576}')"
