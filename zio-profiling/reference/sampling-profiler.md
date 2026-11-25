# Sampling Profiler Reference

> The sampling profiler lives in package `zio.profiling.sampling`. It records, at a fixed interval, which cost center each running fiber is in. The result shows how much time each cost center accounts for. For a step-by-step walk-through, see the [tutorial](../tutorials/first-flamegraph.md).

```scala
import zio.profiling.sampling._
```

## SamplingProfiler

```text
final case class SamplingProfiler(samplingPeriod: Duration = 10.millis)
```

| Parameter        | Default   | Meaning                                                                                  |
|------------------|-----------|------------------------------------------------------------------------------------------|
| `samplingPeriod` | `10.millis` | Time between two samples. A shorter period gives more samples and more overhead.         |

`SamplingProfiler` is a case class with no companion `profile` method. Create an instance first: `SamplingProfiler().profile(...)`.

```scala
SamplingProfiler()
// res0: SamplingProfiler = SamplingProfiler(samplingPeriod = PT0.01S)
```

### Methods

| Method | Type | Description |
|--------|------|-------------|
| `profile` | `[R, E](zio: ZIO[R, E, Any]): ZIO[R, E, ProfilingResult]` | Runs `zio` with a sampler attached, then returns the samples. The sampler stops when `zio` finishes. The value of `zio` is discarded; the result is the profile. If `zio` fails, so does the profile. |
| `makeSupervisor` | `URIO[Scope, SamplingProfilerSupervisor]` | Starts a sampler in the current scope and returns the supervisor that holds its samples. Sampling continues until the scope closes. Only effects run with `.supervised(supervisor)` are sampled. |
| `supervisedRuntime` | `(implicit unsafe: Unsafe): Runtime.Scoped[SamplingProfilerSupervisor]` | Creates a runtime with a sampler attached, so every effect run with it is sampled. Get the supervisor with `runtime.environment.get[SamplingProfilerSupervisor]`. Shut the runtime down when you are done. |

The how-to guide [Profile a running application](../how-to/profile-running-application.md) shows `supervisedRuntime` in use.

## What a sample counts

On every tick, the sampler looks at each fiber that is currently running and adds one to the count of that fiber's cost center. A fiber counts as running from when it starts or resumes until it suspends or ends. Work that waits for an asynchronous result is therefore not counted, while `ZIO.succeed(Thread.sleep(...))` is counted, because the fiber is running while it sleeps.

Because every running fiber is counted on every tick, the total number of samples can be larger than the wall-clock time when fibers run in parallel. Compare cost centers with each other, not with the run time.

## ProfilingResult

```text
final case class ProfilingResult(entries: List[ProfilingResult.Entry])

object ProfilingResult {
  final case class Entry(costCenter: CostCenter, samples: Long)
}
```

| Member | Type | Description |
|--------|------|-------------|
| `entries` | `List[Entry]` | One entry per cost center that was sampled, in no particular order. |
| `stackCollapse` | `List[String]` | One line per entry, in folded-stack format: the cost center path joined with `;`, a space, and the sample count. |
| `stackCollapseToFile(path)` | `Task[Unit]` | Writes `stackCollapse` to `path`, one line per entry, creating parent directories if needed. |

A cost center with no name (the root) renders as an empty path. Lines for it start with a space.

The output of `stackCollapse` for a hand-written result:

```scala
import zio.profiling.CostCenter

val example = ProfilingResult(
  List(
    ProfilingResult.Entry(CostCenter.Root / "outer" / "fast", 40),
    ProfilingResult.Entry(CostCenter.Root / "outer" / "slow", 60)
  )
)
```

```scala
example.stackCollapse
// res1: List[String] = List("outer;fast 40", "outer;slow 60")
```

The file format is the input of [FlameGraph](https://github.com/brendangregg/FlameGraph): `flamegraph.pl profile.folded > profile.svg`.

## SamplingProfilerSupervisor

`SamplingProfilerSupervisor` is the supervisor that collects samples. You get one from `makeSupervisor` or from the environment of a `supervisedRuntime`.

| Method | Type | Description |
|--------|------|-------------|
| `value` | `UIO[ProfilingResult]` | The samples collected so far, as an effect. |
| `unsafeValue()` | `ProfilingResult` | The samples collected so far, read immediately. |
| `sample()` | `Unit` | Takes one sample by hand. The sampler calls this on every tick, so you rarely need it. |
| `reset()` | `Unit` | Clears all samples. The next profile starts from zero. |

## Related

- [CostCenter reference](cost-center.md) describes the names in each entry.
- [Causal profiler reference](causal-profiler.md) ranks parts of a program by their effect on throughput.
