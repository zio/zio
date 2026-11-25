# Profile JMH Benchmarks

> The [Java Microbenchmark Harness (JMH)](https://github.com/openjdk/jmh) measures how fast a piece of code runs. ZIO Profiling can attach a sampling profiler to a JMH run, so each benchmark also produces a profile of the ZIO effects it runs. This guide shows how to set up a benchmark, run it with the profiler, and find the profile file.

## Step 1: Tag the code you benchmark

Put the effects you want to profile in code that the compiler plugin tags, so that they have names in the profile. See [Set up the tagging compiler plugin](setup-tagging-plugin.md). The benchmark module needs the plugin too, because the effects you measure are usually defined there.

## Step 2: Add the JMH integration

Add the `zio-profiling-jmh` module to the project that holds your benchmarks:

```sbt
libraryDependencies += "dev.zio" %% "zio-profiling-jmh" % "0.3.5"
```

The module provides `zio.profiling.jmh.BenchmarkUtils`, which runs ZIO effects on the runtime that the profiler uses. Run your effects through `BenchmarkUtils.unsafeRun` rather than through `Unsafe` yourself, so that they are profiled.

## Step 3: Write a benchmark that runs ZIO effects

The benchmark below sums the doubled values of a list with ZIO. The effect is defined in a `def`, so the plugin tags it. It is a self-contained file: it needs only the `zio-profiling-jmh` dependency and the JMH annotations. The import `zio.{Scope => _, _}` hides ZIO's `Scope`, which would otherwise clash with JMH's `@State(Scope.Thread)`.

```scala
package bench

import org.openjdk.jmh.annotations._
import zio.{Scope => _, _}
import zio.profiling.jmh.BenchmarkUtils

import java.util.concurrent.TimeUnit

@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@Measurement(iterations = 5)
@Warmup(iterations = 3)
@Fork(1)
class SumBenchmarks {

  private val numbers: List[Int] = (1 to 1000).toList

  private def doubledSum: UIO[Int] =
    ZIO.foreach(numbers)(n => ZIO.succeed(n * 2)).map(_.sum)

  @Benchmark
  def sumWithZio(): Int = BenchmarkUtils.unsafeRun(doubledSum)
}
```

Keep each benchmark method to one call to `BenchmarkUtils.unsafeRun`. Put the effect itself in a `def` or `val` outside the method, so the compiler plugin can name it.

## Step 4: Run the benchmark with the profiler

With sbt-jmh, pass the profiler class to the `-prof` option. The argument is the fully qualified name of the benchmark method:

```sbt
Jmh/run -i 3 -wi 3 -f1 -t1 -prof zio.profiling.jmh.JmhZioProfiler bench.SumBenchmarks.sumWithZio
```

The options `-i`, `-wi`, `-f` and `-t` set the number of measurement iterations, warm-up iterations, forks and threads. Use the same values you use for the benchmark otherwise.

## Step 5: Find the profile

The profiler starts sampling during the first warm-up iteration and resets its counts when the measurement iterations start. Only the samples from the measurement iterations are written. After the last measurement iteration, it writes a `profile.folded` file into a directory named after the benchmark and its mode, inside the directory the JVM was started from.

For the benchmark above, which runs in throughput mode, the file is `<project directory>/bench.SumBenchmarks.sumWithZio-Throughput/profile.folded`. Turn it into a flamegraph as described in the [tutorial](../tutorials/first-flamegraph.md):

```bash
flamegraph.pl "bench.SumBenchmarks.sumWithZio-Throughput/profile.folded" > sum.svg
```

:::note[What the profile covers]
The profile records only the ZIO effects that run during the measurement iterations. Time spent in JMH itself, or in code that is not run through `BenchmarkUtils`, is not included. If the benchmark code is not tagged by the compiler plugin, the file has a single line with no name, as in the [tutorial](../tutorials/first-flamegraph.md).
:::

## Related

- [Profile a running application](profile-running-application.md) explains the runtime that the JMH profiler creates internally.
