#!/bin/bash

echo "=== ZIO Optimized Race Test Runner ==="
echo "This script will run the tests to verify the correctness of the optimized race implementation."
echo

# Run the tests
if command -v scala-cli &> /dev/null; then
    echo "Using scala-cli to run the tests..."
    scala-cli test OptimizedRaceTest.scala
elif command -v scala &> /dev/null; then
    echo "Using scala to run the tests..."
    scala OptimizedRaceTest.scala
else
    echo "Error: Neither scala-cli nor scala found in PATH."
    echo "Please install Scala or Scala CLI to run the tests."
    exit 1
fi

echo
echo "Tests complete!"
echo "If all tests passed, you can now run the performance benchmark using:"
echo "./run_optimized_race_verification.sh"