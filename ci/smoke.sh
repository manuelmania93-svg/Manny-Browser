#!/usr/bin/env bash
set -euo pipefail
mkdir -p evidence
adb install -r "$(find artifacts -name app-debug.apk | head -1)"
adb install -r "$(find artifacts -name '*androidTest.apk' | head -1)"
adb logcat -c
set +e
adb shell am instrument -w app.mannybrowser.test/androidx.test.runner.AndroidJUnitRunner | tee evidence/instrumentation.txt
adb pull /sdcard/Android/data/app.mannybrowser/files/manny-evidence/. evidence/ || true
adb logcat -d > evidence/logcat.txt
set -e
grep -q 'OK (' evidence/instrumentation.txt
! grep -q 'FAILURES\|INSTRUMENTATION_FAILED\|Process crashed' evidence/instrumentation.txt
