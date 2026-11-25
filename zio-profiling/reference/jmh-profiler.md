# JMH Profiler Reference

> The `zio-profiling-jmh` module attaches the [sampling profiler](sampling-profiler.md) to a JMH run. It lives in package `zio.profiling.jmh`. For the steps to run a benchmark with it, see [Profile JMH benchmarks](../how-to/profile-jmh-benchmarks.md).

## Artifact

```scala
libraryDependencies ++= Seq(
  "dev.zio" %% "zio-profiling-jmh" % "0.3.5"
)
```

## JmhZioProfiler

`JmhZioProfiler` is a JMH `InternalProfiler`. Pass its fully qualified name to JMH with `-prof`:

```text
-prof zio.profiling.jmh.JmhZioProfiler
```

It has a no-argument constructor and a constructor that takes the profiler option string, which it ignores, so it accepts no options.

| Phase | What the profiler does |
|-------|------------------------|
| First warm-up iteration | Creates a runtime with a [`SamplingProfiler`](sampling-profiler.md) at the default sampling period. |
| First measurement iteration | Resets the collected samples, so warm-up is not included. |
| After the last measurement iteration | Writes `profile.folded` and shuts the runtime down. |

The profiler starts only in a warm-up iteration. Run with at least one warm-up iteration (`-wi 1` or more), or no profile is written.

JMH prints `zio-profiler results:` followed by the path of the file. The file is `profile.folded` in a directory named after the benchmark id, inside the working directory of the JVM, in the folded-stack format described in the [sampling profiler reference](sampling-profiler.md#profilingresult).

## BenchmarkUtils

`BenchmarkUtils` gives benchmark code access to the profiled runtime. Without the profiler attached, it uses `Runtime.default`.

| Method | Type | Description |
|--------|------|-------------|
| `unsafeRun(zio)` | `[E, A](zio: ZIO[Any, E, A]): A` | Runs `zio` on the profiled runtime and returns its value. If `zio` fails, throws a `FiberFailure` that wraps the failure. |
| `getRuntime()` | `Runtime[Any]` | The profiled runtime while a profile is running, otherwise `Runtime.default`. |
| `getSupervisor()` | `Supervisor[Any]` | The sampling supervisor while a profile is running, otherwise `Supervisor.none`. |

Only effects that run on the runtime from `getRuntime()` are profiled.

## Related

- [Profile a running application](../how-to/profile-running-application.md) shows the runtime that the profiler creates.
