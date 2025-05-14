# SimplifiedOptimizedRace Implementation and Verification

This project implements and verifies a simplified optimized version of ZIO's race operation that aims to achieve a 5x performance improvement over cats-effect's race implementation.

## Overview

The `SimplifiedOptimizedRace` implementation optimizes the standard ZIO race operation by reusing the calling fiber for one side of the race, creating only one new fiber instead of two. This reduces overhead and improves performance, especially in scenarios where one side of the race completes immediately while the other never completes.

## Implementation

The implementation is in `SimplifiedOptimizedRace.scala`. The key optimization is:

1. Reusing the calling fiber for the left side of the race
2. Creating only one new fiber for the right side
3. Using an atomic boolean to ensure only one side wins the race
4. Properly handling interruption of the losing side

## Verification

The verification process consists of two parts:

1. **Correctness Tests**: Ensure the optimized implementation maintains the same behavior as the standard ZIO race operation.
2. **Performance Benchmarks**: Measure the performance improvement compared to both standard ZIO race and cats-effect race.

### Running Tests

To verify the correctness of the implementation:

```bash
./run_simplified_race_test.sh
```

The tests check that the optimized implementation:
- Completes with the first effect to succeed
- Properly propagates errors from either side
- Interrupts the loser when one side completes
- Behaves the same as the standard ZIO race operation

### Running Benchmarks

To measure the performance improvement:

```bash
./run_simplified_race_benchmark.sh
```

The benchmark focuses on the scenario where one side completes immediately while the other never completes, which is the critical case for race performance. It compares:

1. Cats-effect race implementation
2. Standard ZIO race implementation
3. SimplifiedOptimizedRace implementation

## Performance Goal

The target is to achieve a 5x performance improvement over cats-effect's race implementation. The benchmark will explicitly indicate whether this goal has been achieved.

## Files

- `SimplifiedOptimizedRace.scala`: The optimized race implementation
- `SimplifiedOptimizedRaceTest.scala`: Tests to verify correctness
- `SimplifiedOptimizedRaceBenchmark.scala`: Benchmark to measure performance
- `run_simplified_race_test.sh`: Script to run the tests
- `run_simplified_race_benchmark.sh`: Script to run the benchmark

## Results

After running the benchmark, you'll see performance metrics including:

- Raw execution times
- Operations per second
- Performance ratios between implementations
- Whether the 5x performance goal was achieved

The benchmark results will be saved to a timestamped file in the `results` directory for future reference.