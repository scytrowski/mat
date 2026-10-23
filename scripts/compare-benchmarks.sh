#!/usr/bin/env bash

set -euo pipefail

current_file=$1
baseline_file=$2
tolerance=${3:-0.25}
minimum_delta_ms=${4:-25}

benchmark_protocol() {
  sed -n 's/^# benchmark-protocol=//p' "$1" | head -n 1
}

current_protocol=$(benchmark_protocol "$current_file")
baseline_protocol=$(benchmark_protocol "$baseline_file")
if [[ -z "$current_protocol" ]]; then
  echo "Current benchmark file is missing its measurement protocol marker." >&2
  exit 1
fi
if [[ "$current_protocol" != "$baseline_protocol" ]]; then
  echo "Skipping benchmark comparison: measurement protocols differ (current=${current_protocol:-legacy}, baseline=${baseline_protocol:-legacy})."
  exit 0
fi

awk -F '\t' \
  -v tolerance="$tolerance" \
  -v minimum_delta_ms="$minimum_delta_ms" '
  NR == FNR {
    if (FNR > 1 && $1 !~ /^#/) baseline[$1 FS $2] = $3
    next
  }

  FNR == 1 || $1 ~ /^#/ { next }

  {
    key = $1 FS $2
    if (!(key in baseline)) {
      printf "No baseline for %s/%s; skipping.\n", $1, $2
      next
    }

    previous = baseline[key]
    current = $3
    delta = current - previous
    limit = previous * (1 + tolerance)

    if (delta > minimum_delta_ms && current > limit) {
      printf "REGRESSION %s/%s: %d ms -> %d ms (+%.1f%%).\n",
        $1, $2, previous, current, (delta / previous) * 100
      failures++
    } else {
      printf "OK %s/%s: %d ms -> %d ms (%+.1f%%).\n",
        $1, $2, previous, current, (delta / previous) * 100
    }
  }

  END {
    if (failures > 0) {
      printf "Benchmark regression threshold exceeded for %d case(s).\n", failures
      exit 1
    }
  }
' "$baseline_file" "$current_file"
