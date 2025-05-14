#!/bin/bash

echo "=== SimplifiedOptimizedRace Benchmark Runner ==="
echo "This script will run the benchmark and save the results to a file."

# Create results directory if it doesn't exist
mkdir -p results

# Generate output filename with timestamp
OUTPUT_FILE="results/simplified_race_benchmark_results_$(date +%Y%m%d_%H%M%S).txt"

echo "Running benchmark..."

# Try different methods to run the benchmark
if command -v scala-cli &> /dev/null; then
  echo "Using scala-cli to run the benchmark..."
  scala-cli SimplifiedOptimizedRaceBenchmark.scala | tee "$OUTPUT_FILE"
elif command -v scala &> /dev/null; then
  echo "Using scala to run the benchmark..."
  scala SimplifiedOptimizedRaceBenchmark.scala | tee "$OUTPUT_FILE"
else
  echo "Error: Scala not found."
  echo "Please install Scala or Scala CLI to run the benchmark."
  exit 1
fi

# Make the file executable
chmod +x "$OUTPUT_FILE"

echo "Benchmark complete! Results saved to: $OUTPUT_FILE"