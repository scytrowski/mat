#!/usr/bin/env bash

set -euo pipefail

output_file="${1:-benchmark-results.tsv}"
repetitions="${BENCHMARK_REPETITIONS:-3}"
benchmark_scala_version="3.8.4"

if ! [[ "$repetitions" =~ ^[1-9][0-9]*$ ]]; then
  echo "BENCHMARK_REPETITIONS must be a positive integer" >&2
  exit 1
fi

printf 'benchmark\tsize\telapsed_ms\n' > "$output_file"

for benchmark_kind in tuple product union; do
  for benchmark_size in 16 24 32; do
    echo "Running $benchmark_kind benchmark with $benchmark_size elements ($repetitions repetitions)"

    sbt_commands=";++$benchmark_scala_version"
    for ((run = 1; run <= repetitions; run++)); do
      sbt_commands+=";Benchmark / clean;Benchmark / compile"
    done

    log_file=$(mktemp)
    if ! sbt --batch --timings \
      -Dmat.benchmark="$benchmark_kind" \
      -Dmat.size="$benchmark_size" \
      "$sbt_commands" >"$log_file" 2>&1; then
      cat "$log_file"
      rm -f "$log_file"
      exit 1
    fi
    timings=()
    while IFS= read -r timing; do
      timings+=( "$timing" )
    done < <(
      awk '
        /Benchmark \/ compileIncremental/ {
          timing = $0
          sub(/^.*: */, "", timing)
          sub(/ ms.*$/, "", timing)
          if (timing ~ /^[0-9]+$/) print timing
        }
      ' "$log_file" | head -n "$repetitions"
    )
    printf 'Compile timings: %s\n' "${timings[*]}"
    rm -f "$log_file"

    if (( ${#timings[@]} != repetitions )); then
      echo "Expected $repetitions benchmark timings, found ${#timings[@]}" >&2
      exit 1
    fi

    median_ms=$(printf '%s\n' "${timings[@]}" | sort -n | awk '{ values[NR] = $1 } END { print values[int((NR + 1) / 2)] }')
    printf '%s\t%s\t%s\n' "$benchmark_kind" "$benchmark_size" "$median_ms" \
      >> "$output_file"
    echo "Completed in ${median_ms} ms (median compile time of ${repetitions} runs)"
  done
done

echo "Benchmark results written to $output_file"
