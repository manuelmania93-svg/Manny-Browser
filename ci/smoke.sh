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
run_instrumentation() { # run_instrumentation <language> <out-file> [class-filter]
  local language="$1" out="$2" filter="${3:-}"
  adb wait-for-device
  adb shell cmd locale set-app-locales app.mannybrowser --locales "$language"
  # Fresh app state per invocation: leftover bookmarks/tabs from a previous
  # locale or a failed full run make retried methods fail deterministically.
  adb shell pm clear app.mannybrowser
  adb shell am force-stop app.mannybrowser
  if [ -n "$filter" ]; then
    adb shell am instrument -w -e class "$filter" app.mannybrowser.test/androidx.test.runner.AndroidJUnitRunner | tee "$out"
  else
    adb shell am instrument -w app.mannybrowser.test/androidx.test.runner.AndroidJUnitRunner | tee "$out"
  fi
  adb pull /sdcard/Android/data/app.mannybrowser/files/manny-evidence/. evidence/ || true
}

result_ok() { grep -q 'OK (' "$1" && ! grep -q 'FAILURES\|INSTRUMENTATION_FAILED\|Process crashed' "$1"; }

status=0
for language in en de; do
  run_instrumentation "$language" "evidence/instrumentation-$language.txt"
  if ! result_ok "evidence/instrumentation-$language.txt"; then
    # Emulator input races flake individual methods at DIFFERENT steps run to
    # run (TabGroupsTest failed at a different assertion on the retry than on
    # the first pass). Re-run only the failed methods, in a fresh process with
    # cleared app data, up to two attempts; only a method failing every
    # attempt fails the job.
    previous="evidence/instrumentation-$language.txt"
    for attempt in 1 2; do
      retry=$(sed -nE 's/^[0-9]+\) ([A-Za-z0-9_]+)\(([A-Za-z0-9_.]+)\)$/\2#\1/p' "$previous" | sort -u | paste -sd, -)
      echo "retry attempt $attempt for failed methods: $retry" | tee -a "evidence/instrumentation-$language.txt"
      if [ -z "$retry" ]; then status=1; break; fi
      previous="evidence/instrumentation-$language-retry$attempt.txt"
      run_instrumentation "$language" "$previous" "$retry"
      if result_ok "$previous"; then break; fi
      if [ "$attempt" = 2 ]; then status=1; fi
    done
  fi
done
adb logcat -d > evidence/logcat.txt

exit "$status"
