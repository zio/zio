#!/bin/bash

echo "=== SimplifiedOptimizedRace Test Runner ==="
echo "This script will run the tests to verify correctness of the implementation."

# Run the tests
if command -v scala-cli &> /dev/null; then
  echo "Using scala-cli to run the tests..."
  scala-cli test SimplifiedOptimizedRaceTest.scala
elif command -v scala &> /dev/null; then
  echo "Using scala to run the tests..."
  scala -cp "$(find . -name '*.jar' | tr '\n' ':')" org.scalatest.run SimplifiedOptimizedRaceTest
else
  echo "Error: Scala not found."
  echo "Please install Scala or Scala CLI to run the tests."
  exit 1
fi

if [ $? -eq 0 ]; then
  echo "\nTests passed! You can now run the benchmark with ./run_simplified_race_benchmark.sh"
else
  echo "\nTests failed. Please fix the implementation before running the benchmark."
  exit 1
fi