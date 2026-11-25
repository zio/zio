# Profile a Running Application

> `SamplingProfiler().profile` profiles one effect and stops when that effect finishes. Sometimes you want a profiler that keeps collecting samples for as long as the application runs, for example in a long-running service or in a benchmark harness. This guide shows how to build a runtime that is profiled from the start, read its samples at any time, and shut it down.

## How it works

`SamplingProfiler().supervisedRuntime` creates a ZIO `Runtime` with the sampling profiler attached as a supervisor. Every effect you run with that runtime is observed. A background fiber takes a sample every `samplingPeriod`. The samples are kept in a `SamplingProfilerSupervisor`, which you get from the runtime's environment.

Creating the runtime needs an implicit `Unsafe` value, which is why it is wrapped in `Unsafe.unsafe`. Running effects through the runtime also uses the unsafe API.

## Step 1: Create the runtime

Create the runtime once, at startup. Creating it starts the sampling fiber.

## Step 2: Run your effects through the runtime

Use the runtime's unsafe API to run effects. Only effects run through this runtime are profiled.

## Step 3: Read the samples

Get the supervisor from the runtime's environment and ask it for the samples collected so far. `unsafeValue()` returns a `ProfilingResult` without waiting for anything, so you can call it while the application keeps running. `stackCollapseToFile` returns a `Task[Unit]`, so run it as an effect in your application.

## Step 4: Reset and shut down

To start a new measurement window, clear the samples with `reset()`. To stop profiling, shut the runtime down, which also stops the sampling fiber.

:::warning[Shut down the runtime]
A profiled runtime keeps a sampling fiber running until it is shut down. Shut it down when you are done, or the background sampling continues for the life of the process.
:::

## The complete sequence

```scala
import zio._
import zio.profiling.sampling._

// Step 1: create the runtime
val profiledRuntime: Runtime.Scoped[SamplingProfilerSupervisor] =
  Unsafe.unsafe { implicit unsafe =>
    SamplingProfiler().supervisedRuntime
  }

// Step 2: run effects through the runtime
val work: UIO[Unit] = ZIO.succeed(Thread.sleep(500))

Unsafe.unsafe { implicit unsafe =>
  profiledRuntime.unsafe.run(work)
}

// Step 3: read the samples
val supervisor = profiledRuntime.environment.get[SamplingProfilerSupervisor]

val snapshot: ProfilingResult = supervisor.unsafeValue()

snapshot.stackCollapseToFile("profile.folded")

// Step 4: reset and shut down
supervisor.reset()

Unsafe.unsafe { implicit unsafe =>
  profiledRuntime.unsafe.shutdown()
}
```

## Choose a sampling period

The default sampling period is 10 ms. A shorter period gives more samples and more overhead. A longer period gives fewer samples and less overhead. Pass the period to the constructor:

```scala
import zio._
import zio.profiling.sampling._

val lowOverhead = SamplingProfiler(samplingPeriod = 50.millis)
```

## Related

- [Sampling profiler reference](../reference/sampling-profiler.md) describes every method used here.
- [Profile JMH benchmarks](profile-jmh-benchmarks.md) uses this runtime for benchmarks.
