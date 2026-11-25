# Causal Profiler Reference

> The causal profiler lives in package `zio.profiling.causal`. It is experimental. It is inspired by [Coz](https://github.com/plasma-umass/coz): it selects a cost center, makes the rest of the program run relatively slower for a while, and counts how many progress points are reached. From that it estimates how much the program would speed up if the selected part were faster. For a how-to guide, see [Find a bottleneck with the causal profiler](../how-to/find-bottleneck-causal.md).

```scala
import zio._
import zio.profiling.causal._
```

## CausalProfiler

```text
final case class CausalProfiler(
  iterations: Int = 100,
  candidateSelector: String => Boolean = _ => true,
  samplingPeriod: Duration = 20.millis,
  minExperimentDuration: Duration = 1.second,
  experimentTargetSamples: Int = 30,
  warmUpPeriod: Duration = 30.seconds,
  coolOffPeriod: Duration = 2.seconds,
  zeroSpeedupWeight: Int = 10,
  maxConsideredSpeedUp: Int = 100,
  sleepPrecision: Duration = 10.millis
)
```

| Parameter | Default | Meaning |
|-----------|---------|---------|
| `iterations` | `100` | Number of experiments to run before the profile is complete. |
| `candidateSelector` | `_ => true` | Called with the last location of a candidate cost center. Only cost centers for which it returns `true` are selected for experiments. Cost centers that are the root are never selected. |
| `samplingPeriod` | `20.millis` | How often the profiler looks at where fibers are. Lower values increase overhead. |
| `minExperimentDuration` | `1.second` | Shortest length of an experiment. |
| `experimentTargetSamples` | `30` | Number of times each progress point should be reached during an experiment. The profiler lengthens or shortens the next experiment to get close to this number. |
| `warmUpPeriod` | `30.seconds` | Time to wait after the profile starts before the first experiment. |
| `coolOffPeriod` | `2.seconds` | Time to wait between two experiments. |
| `zeroSpeedupWeight` | `10` | On average, one experiment in every `zeroSpeedupWeight` applies no speedup at all, which gives a baseline to compare against. |
| `maxConsideredSpeedUp` | `100` | Largest speedup, as a percentage, that an experiment can apply. Values above 100 have no practical use. |
| `sleepPrecision` | `10.millis` | Estimated accuracy of `Thread.sleep`. Within this distance of the target time, the profiler busy-waits for accuracy. |

With the defaults, a profile takes at least `warmUpPeriod + iterations * minExperimentDuration + (iterations - 1) * coolOffPeriod`, which is a little over 5 minutes (328 seconds). There is no cool-off after the last experiment. For tests and first runs, lower `warmUpPeriod`, `coolOffPeriod` and `iterations`.

```scala
CausalProfiler(iterations = 10, warmUpPeriod = 0.seconds).iterations
// res0: Int = 10
```

## Methods

| Method | Type | Description |
|--------|------|-------------|
| `profile` | `[R, E](zio: ZIO[R, E, Nothing]): ZIO[R, E, ProfilingResult]` | Runs `zio` until the profile is complete, then interrupts it and returns the result. `zio` must never end on its own: use `forever`. If `zio` finishes before enough data is collected, the profile dies with the message `Program completed before profiler could collect sufficient data`. |
| `profileIterations` | `[R, E](zio: ZIO[R, E, Any]): ZIO[R, E, ProfilingResult]` | Runs `zio` in a loop and adds a progress point named `iteration done` after each run. Use it when one run of `zio` is one unit of work. |
| `supervisor` | `ZIO[Scope, Nothing, Supervisor[ProfilingResult]]` | Creates the profiler as a supervisor, for use with `zio.supervised`. Reading its `value` waits until the profile is complete. The profiled effects must have OpSupervision enabled. |

## Progress and latency points

The companion object `CausalProfiler` has the functions that mark progress in your program. They do nothing when no profile is running.

| Function | Type | Description |
|----------|------|-------------|
| `progressPoint(name)` | `UIO[Unit]` | Counts one unit of progress under `name`. The profiler measures throughput as the rate of these counts. |
| `enterLatencyPoint(name)` | `UIO[Unit]` | Marks the start of a unit of work whose latency you want to track. |
| `exitLatencyPoint(name)` | `UIO[Unit]` | Marks the end of a unit of work started with `enterLatencyPoint`. |
| `latencyPointScoped(name)` | `URIO[Scope, Unit]` | Calls `enterLatencyPoint` now and `exitLatencyPoint` when the scope closes. |

```scala
val request: UIO[Unit] =
  ZIO.scoped(CausalProfiler.latencyPointScoped("request") *> ZIO.succeed(Thread.sleep(10)))
```

Latency points count arrivals and departures during each experiment. `difference` in the output is arrivals minus departures: the number of units that entered during the experiment and had not left by its end.

## ProfilingResult

```text
final case class ProfilingResult(experiments: List[ExperimentResult])
```

| Member | Type | Description |
|--------|------|-------------|
| `experiments` | `List[ExperimentResult]` | One entry per completed experiment. |
| `render` | `String` | The experiments in the text format read by the Coz visualizer. |
| `renderToFile(path)` | `Task[Unit]` | Writes `render` to `path`, creating parent directories if needed. |

### ExperimentResult

```text
final case class ExperimentResult(
  selected: CostCenter,
  speedup: Float,
  duration: Long,
  effectiveDuration: Long,
  selectedSamples: Long,
  throughputData: List[ThroughputData],
  latencyData: List[LatencyData]
)
```

| Field | Meaning |
|-------|---------|
| `selected` | The cost center that was sped up in this experiment. |
| `speedup` | Fraction of speedup applied, from `0` to `1`. `0.25` means the selected part was made 25% faster, by slowing everything else. |
| `duration` | Planned length of the experiment, in nanoseconds. |
| `effectiveDuration` | `duration` minus the total delay that was added to the other code during the experiment, in nanoseconds. This is the time the selected code effectively had to itself. |
| `selectedSamples` | Number of delay steps counted during the experiment: one for each running fiber in the selected cost center, or in a cost center nested under it, at each sampling tick. Despite its name, this counts delay steps, not sampled time. |
| `throughputData` | How often each progress point was reached during the experiment. |
| `latencyData` | Arrivals and departures for each latency point. |

`render` writes one block per experiment. The first line starts with `experiment` and has the form:

```text
experiment	selected=<cost center>	speedup=<speedup>	duration=<effective duration>	selected-samples=<samples>
```

Each following line is a `throughput-point` line (`name`, `delta`) or a `latency-point` line (`name`, `arrivals`, `departures`, `difference`), one for each progress point and latency point. The fields are separated by tabs.

### ThroughputData and LatencyData

```text
final case class ThroughputData(name: String, delta: Int)
final case class LatencyData(name: String, arrivals: Int, departures: Int)
```

| Field | Meaning |
|-------|---------|
| `ThroughputData.name` | The name passed to `progressPoint`. |
| `ThroughputData.delta` | How many times that progress point was reached during the experiment. |
| `LatencyData.name` | The name passed to `enterLatencyPoint`, `exitLatencyPoint` or `latencyPointScoped`. |
| `LatencyData.arrivals` | How many times `enterLatencyPoint` ran during the experiment. |
| `LatencyData.departures` | How many times `exitLatencyPoint` ran during the experiment. |

Each has a `render` method that produces its line in the output below.

## Logging

The profiler logs at the `Info` level when it starts warming up, when each experiment starts and when profiling is done. An experiment line contains the experiment number, the selected cost center, the speedup and the planned duration in nanoseconds. Use these lines to follow the progress of a long run.

## Output

Load the file written by `renderToFile` into the [Coz visualizer](https://plasma-umass.org/coz/) to see the experiments as a chart. Each selected cost center is shown with the throughput change it caused at each speedup.

## Related

- [Find a bottleneck with the causal profiler](../how-to/find-bottleneck-causal.md) walks through a run.
- [CostCenter reference](cost-center.md) explains the names that experiments select.
