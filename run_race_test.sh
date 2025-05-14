#!/bin/bash

echo "=== ZIO Race Optimization Verification ==="
echo "Attempting to run the verification test..."

# Try to run with scala command if available
if command -v scala &> /dev/null; then
    echo "Running with scala command..."
    scala VerifyRaceOptimization.scala
    exit $?
fi

# Try to run with scala-cli if available
if command -v scala-cli &> /dev/null; then
    echo "Running with scala-cli command..."
    scala-cli VerifyRaceOptimization.scala
    exit $?
fi

# Try to run with java directly
echo "Attempting to run directly with java..."
echo "This requires the ZIO and Cats Effect libraries to be in the classpath"

# Print the verification code to show what would be tested
echo "\nVerification code that would be executed:\n"
head -n 20 VerifyRaceOptimization.scala
echo "..."

echo "\nUnable to run the verification test automatically."
echo "Please manually run the verification test using one of these methods:"
echo "1. Install Scala and run: scala VerifyRaceOptimization.scala"
echo "2. Install Scala CLI and run: scala-cli VerifyRaceOptimization.scala"
echo "3. Use SBT to run the test in the ZIO project"

echo "\nBased on the code examination, here's what we know about the optimization:"
echo "- The OptimizedRace implementation reuses the calling fiber for the left side of the race"
echo "- It creates only one new fiber for the right side instead of two fibers"
echo "- This reduces allocations and improves interrupt handling"
echo "- The implementation should provide significant performance improvements"
echo "  over the original ZIO race implementation"

echo "\nTo verify the 5x performance goal, we need to run the benchmark"
echo "that compares the three implementations:"
echo "1. Original ZIO race implementation"
echo "2. Optimized ZIO race implementation"
echo "3. Cats-effect race implementation"