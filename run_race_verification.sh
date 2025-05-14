#!/bin/bash

echo "=== ZIO Race Optimization Verification ==="
echo "This script will verify if the optimized race implementation solves the performance issue."
echo ""

# Compile the benchmark
echo "Compiling benchmark..."
scalac -classpath "$(find . -name '*.jar' | tr '\n' ':')" RaceOptimizationVerificationBenchmark.scala

# Run the benchmark
echo "\nRunning benchmark..."
scala RaceOptimizationVerificationApp

echo "\nVerification complete!"