#!/bin/bash

echo "=== ZIO Optimized Race Verification Runner ==="
echo "This script will run the benchmark to verify if the optimized race implementation achieves the 5x performance goal."
echo

# Create results directory if it doesn't exist
mkdir -p results

# Set the output file
OUTPUT_FILE="results/optimized_race_verification_$(date +%Y%m%d_%H%M%S).txt"

echo "Running benchmark..."
echo "Results will be saved to: $OUTPUT_FILE"
echo

# Run the benchmark and capture the output
if command -v scala-cli &> /dev/null; then
    echo "Using scala-cli to run the benchmark..."
    scala-cli VerifyOptimizedRacePerformance.scala | tee "$OUTPUT_FILE"
elif command -v scala &> /dev/null; then
    echo "Using scala to run the benchmark..."
    scala VerifyOptimizedRacePerformance.scala | tee "$OUTPUT_FILE"
else
    echo "Error: Neither scala-cli nor scala found in PATH."
    echo "Please install Scala or Scala CLI to run the benchmark."
    exit 1
fi

echo
echo "Benchmark complete! Results saved to: $OUTPUT_FILE"
echo "You can analyze the results to determine if the optimized race implementation"
echo "achieves the 5x performance improvement goal compared to cats-effect."