#!/usr/bin/env bash

set -euo pipefail

output_file="${1:-benchmark-results.tsv}"
repetitions="${BENCHMARK_REPETITIONS:-5}"
benchmark_scala_version="3.8.4"

if ! [[ "$repetitions" =~ ^[1-9][0-9]*$ ]]; then
  echo "BENCHMARK_REPETITIONS must be a positive integer" >&2
  exit 1
fi

sbt --server --batch \
  ";++$benchmark_scala_version;benchmarkMatrix \"$output_file\" \"$repetitions\""
