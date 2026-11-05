#!/usr/bin/env bash
# Fork (Thomas): run the Gradle command given, and when it failed only because a repository refused a download
# (Maven Central answering "429 Too Many Requests" to GitHub's shared runners, a 5xx, a timeout), run it again
# after a pause, up to 3 times. A failing test, lint or compile error stops at once.
set -uo pipefail
log=${RUNNER_TEMP:-/tmp}/gradle-retry.log
status=1
for attempt in 1 2 3; do
  "$@" 2>&1 | tee "$log"
  status=${PIPESTATUS[0]}
  [ "$status" -eq 0 ] && exit 0
  grep -qE 'Received status code (429|5[0-9][0-9])|Read timed out|Connect timed out|Connection reset' "$log" || exit "$status"
  if [ "$attempt" -lt 3 ]; then
    echo "::warning::a repository refused a download (attempt $attempt of 3); trying again in $((attempt * 120)) s"
    sleep $((attempt * 120))
  fi
done
exit "$status"
