# ZIO Race Optimization Verification

This project contains benchmarks and analysis tools to verify if the optimized race implementation in ZIO achieves the 5x performance improvement goal compared to cats-effect.

## Overview

The original ZIO race implementation had a performance issue where it was significantly slower than cats-effect's race implementation. The optimization in `OptimizedRace` aims to improve performance by reusing the calling fiber for one side of the race operation instead of creating two new fibers.

## Files in this Project

- `RaceOptimizationBenchmark.scala`: The main benchmark script that compares the performance of the original ZIO race implementation, the optimized version, and cats-effect.
- `VerifyOptimizedRaceImplementation.scala`: A simple test script to verify that the OptimizedRace implementation works correctly before running the full benchmark.
- `run_race_benchmark.sh`: A shell script to run the benchmark and capture the results in a file for analysis.
- `RACE_OPTIMIZATION_ANALYSIS.md`: Analysis of the optimized race implementation and its expected performance improvements.
- `RACE_OPTIMIZATION_BENCHMARK_RESULTS.md`: Template for documenting the benchmark results.
- `RACE_OPTIMIZATION_ANALYSIS_GUIDE.md`: Guide for interpreting the benchmark results.

## How to Run the Benchmark

### Prerequisites

- Scala or Scala CLI installed on your system
- ZIO 2.0.15 or later
- Cats-effect 3.5.1 or later

### Running the Verification Test

Before running the full benchmark, you can verify that the OptimizedRace implementation works correctly by running:

```bash
chmod +x run_race_benchmark.sh  # Make the script executable
./run_race_benchmark.sh
```

Or directly with Scala:

```bash
scala-cli VerifyOptimizedRaceImplementation.scala
```

### Running the Benchmark

To run the benchmark and capture the results:

```bash
chmod +x run_race_benchmark.sh  # Make the script executable
./run_race_benchmark.sh
```

This will run the benchmark and save the results to a file in the `results` directory.

## Analyzing the Results

After running the benchmark, you can analyze the results to determine if the optimized race implementation achieves the 5x performance improvement goal compared to cats-effect.

The key metrics to look for are:

1. **Execution Time**: The average time taken to complete the benchmark for each implementation. Lower is better.
2. **Operations Per Second**: The throughput of each implementation. Higher is better.
3. **Performance Ratios**: The relative performance of each implementation compared to the others.

The most important ratio is **Cats-Effect / Optimized ZIO**, which should be at least 5.0 to meet the performance goal.

For a detailed guide on interpreting the results, refer to `RACE_OPTIMIZATION_ANALYSIS_GUIDE.md`.

## Expected Results

Based on the optimizations implemented, we expect to see:

1. The optimized ZIO race implementation should be significantly faster than the original implementation.
2. The optimized ZIO race implementation should be at least 5x faster than cats-effect's race implementation.
3. The throughput (operations per second) of the optimized implementation should be significantly higher than both the original implementation and cats-effect.

## Conclusion

The benchmark results will determine whether the optimized race implementation successfully addresses the performance issue. If the "Cats-Effect / Optimized ZIO" ratio is 5.0 or higher, we can conclude that the optimization has successfully met its goal.

If the ratio is less than 5.0, further optimizations may be needed to fully address the performance gap between ZIO and cats-effect.