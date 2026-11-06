#!/usr/bin/env bash
set -euo pipefail
mkdir -p evidence
adb install -r "$(find artifacts -name app-debug.apk | head -1)"
adb install -r "$(find artifacts -name '*androidTest.apk' | head -1)"
python3 ci/fixture.py > evidence/fixture.log 2>&1 &
FIXTURE_PID=$!
trap 'kill "$FIXTURE_PID" 2>/dev/null || true' EXIT
adb reverse tcp:8765 tcp:8765
adb logcat -c
adb logcat > evidence/logcat-live.txt 2>&1 &
LOG_PID=$!
trap 'kill "$FIXTURE_PID" "$LOG_PID" 2>/dev/null || true' EXIT
status=0
for language in en de; do
  adb wait-for-device
  adb shell cmd locale set-app-locales app.mannybrowser --locales "$language"
  adb shell am force-stop app.mannybrowser
  adb shell am instrument -w app.mannybrowser.test/androidx.test.runner.AndroidJUnitRunner | tee "evidence/instrumentation-$language.txt"
  adb pull /sdcard/Android/data/app.mannybrowser/files/manny-evidence/. evidence/ || true
  if ! grep -q 'OK (' "evidence/instrumentation-$language.txt" || grep -q 'FAILURES\|INSTRUMENTATION_FAILED\|Process crashed' "evidence/instrumentation-$language.txt"; then status=1; fi
done
adb logcat -d > evidence/logcat.txt

exit "$status"
