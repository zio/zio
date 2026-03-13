# ZScheduler stress testing and layout verification

## JOL layout test

Verifies that hot fields on `ZScheduler.Worker` have at least the configured byte separation to reduce false sharing. Goal is 128 bytes; see the test class for the current requirement.

```bash
sbt "coreTestsJVM/testOnly zio.internal.ZSchedulerJOLPaddingSpec"
```

Run this before claiming cache-line safety for scheduler changes.

## jcstress (scheduler concurrency)

Scheduler jcstress tests live in `core-tests/jvm`: `ZSchedulerConcurrencyTests`. They stress yield and submit paths under multiple threads.

```bash
sbt "coreTestsJVM/jcstress:run -t <N>"
```

Use `-t <N>` with N = logical CPU count (e.g. cores+2) to stress atomics without oversaturating. Example for 8 threads:

```bash
sbt "coreTestsJVM/jcstress:run -t 8"
```

**Windows:** sbt-jcstress 0.2.0 can hit path/regex issues on Windows. Run jcstress on WSL or Linux (e.g. in CI) for full stress runs.

## JMH (low-yield vs high-yield)

`benchmarks/src/main/scala/zio/internal/ZSchedulerYieldBenchmark` compares throughput with low-yield (yield every 64 ops) vs high-yield (yield every op). Use it to check for regressions under heavy `submitAndYield` load.

```bash
sbt "benchmarks/jmh:run -i 3 -wi 3 -f 1 zio.internal.ZSchedulerYieldBenchmark"
```

## PR verification / demo

For PR review (screenshots or short video):

- **Run:** JOL then jcstress: `sbt "coreTestsJVM/testOnly zio.internal.ZSchedulerJOLPaddingSpec"` then `sbt "coreTestsJVM/jcstress:run -t 8"`. On Windows use WSL or rely on CI for jcstress.
- **Capture:** JOL test pass and layout output; jcstress run completing with no FORBIDDEN outcomes. Optionally JMH run for throughput.
