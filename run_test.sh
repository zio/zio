#!/bin/bash

echo "=== ZIO Race Optimization Verification ===" 
echo "Running test to verify if the optimized race implementation solves the performance issue..."
echo ""

# Run the test using sbt
sbt "testOnly RaceOptimizationVerificationTest"

echo ""
echo "Test complete!"