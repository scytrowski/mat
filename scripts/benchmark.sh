#!/usr/bin/env bash

set -euo pipefail

output_file="${1:-benchmark-results.tsv}"
repetitions="${BENCHMARK_REPETITIONS:-3}"

if ! [[ "$repetitions" =~ ^[1-9][0-9]*$ ]]; then
  echo "BENCHMARK_REPETITIONS must be a positive integer" >&2
  exit 1
fi

printf 'benchmark\tsize\telapsed_ms\n' > "$output_file"

for benchmark_kind in tuple product union; do
  for benchmark_size in 16 24 32; do
    timings=()
    for ((run = 1; run <= repetitions; run++)); do
      echo "Running $benchmark_kind benchmark with $benchmark_size elements (run $run/$repetitions)"
      start_us=${EPOCHREALTIME/./}

      sbt --batch \
        -Dmat.benchmark="$benchmark_kind" \
        -Dmat.size="$benchmark_size" \
        "Benchmark / clean" \
        "Benchmark / compile"

      end_us=${EPOCHREALTIME/./}
      timings+=( "$(( (end_us - start_us) / 1000 ))" )
    done

    median_ms=$(printf '%s\n' "${timings[@]}" | sort -n | awk '{ values[NR] = $1 } END { print values[int((NR + 1) / 2)] }')
    printf '%s\t%s\t%s\n' "$benchmark_kind" "$benchmark_size" "$median_ms" \
      >> "$output_file"
    echo "Completed in ${median_ms} ms (median of ${repetitions} runs)"
  done
done

echo "Benchmark results written to $output_file"
