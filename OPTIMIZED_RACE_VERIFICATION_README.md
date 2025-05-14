# ZIO Optimized Race Verification

This project verifies if the optimized race implementation in ZIO achieves the 5x performance goal compared to cats-effect. The optimization focuses on reusing the calling fiber for one side of the race, reducing overhead by creating only one new fiber instead of two.

## Files Overview

- `OptimizedRace.scala`: The optimized race implementation that reuses the calling fiber for one side of the race.
- `OptimizedRaceTest.scala`: Tests to verify the correctness of the optimized race implementation.
- `VerifyOptimizedRacePerformance.scala`: Benchmark to compare the performance of cats-effect race, original ZIO race, and optimized ZIO race.
- `run_optimized_race_test.sh`: Script to run the correctness tests.
- `run_optimized_race_verification.sh`: Script to run the performance benchmark.

## Verification Process

The verification process consists of two steps:

1. **Correctness Testing**: Verify that the optimized race implementation behaves correctly and produces the same results as the original implementation.
2. **Performance Benchmarking**: Measure the performance of the optimized race implementation compared to the original ZIO race and cats-effect race.

## Running the Tests

To verify the correctness of the optimized race implementation, run:

```bash
./run_optimized_race_test.sh
```

This will run a series of tests to ensure that the optimized race implementation behaves correctly in various scenarios, including:
- Completing with the right side when the left never completes
- Completing with the left side when the right never completes
- Handling errors correctly
- Interrupting the loser of the race

## Running the Benchmark

To measure the performance of the optimized race implementation, run:

```bash
./run_optimized_race_verification.sh
```

This will run a benchmark that compares:
1. Cats-effect race implementation
2. Original ZIO race implementation
3. Optimized ZIO race implementation

The benchmark focuses on the scenario where one side completes immediately while the other never completes, which is the critical case for race performance.

## Benchmark Methodology

The benchmark:
- Performs warmup runs to stabilize JVM performance
- Measures the time taken to execute a large number of race operations
- Calculates operations per second for each implementation
- Computes performance ratios between implementations
- Verifies if the 5x performance goal is achieved

## Expected Results

If the optimization is successful, you should see:

1. The optimized ZIO race implementation is significantly faster than the original implementation
2. The performance gap between ZIO and cats-effect is substantially reduced or eliminated

The benchmark will explicitly indicate whether the 5x performance goal has been achieved.

## Results Analysis

The benchmark outputs several performance metrics:

- **Execution time**: Lower is better
- **Operations per second**: Higher is better
- **Performance ratios**: 
  - Time ratios: Lower is better for the optimized implementation
  - Ops/sec ratios: Higher is better for the optimized implementation

The key metric to look for is the "Optimized ZIO / Original ZIO" ops/sec ratio, which should be at least 5.0 to meet the performance goal.

## Conclusion

This verification process provides concrete evidence of whether the optimized race implementation successfully addresses the performance issue mentioned in the bounty. The combination of correctness tests and performance benchmarks ensures that the optimization not only improves performance but also maintains the correct behavior.