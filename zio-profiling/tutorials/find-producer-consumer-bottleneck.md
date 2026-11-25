# Profile a Producer-Consumer Program with the Causal Profiler

> In this tutorial you will run the causal profiler on a program with producers and consumers that share a bounded queue, and read what it reports. A causal profile tells you how much faster the whole program would become if one part were faster. By the end you will know how to run an experiment, and how to read its lines.

You need an sbt project with ZIO 2 and the `zio-profiling` dependency from [Getting Started](../getting-started.md). A similar program is in the repository as `CausalProfilerProducerConsumerExample` in the `examples` module. It relies on the compiler plugin for names and uses the default warm-up.

## Step 1: Write the program

The program moves 10,000 items through a queue of size 10. Four producers offer items and twenty consumers take them. Each unit of work is the moment all producers and consumers have finished, and we mark it with a progress point.

```scala
import zio._
import zio.profiling.CostCenter
import zio.profiling.causal._

val items         = 10000
val queueSize     = 10
val producerCount = 4
val consumerCount = 20

val program: Task[Unit] =
  Queue.bounded[Unit](queueSize).flatMap { queue =>
    val producer: UIO[Unit] =
      CostCenter.withChildCostCenter("produce")(queue.offer(()).repeatN(items / producerCount - 1).unit)

    val consumer: UIO[Unit] =
      CostCenter.withChildCostCenter("consume")(queue.take.repeatN(items / consumerCount - 1).unit)

    for {
      producers <- ZIO.forkAll(List.fill(producerCount)(producer))
      consumers <- ZIO.forkAll(List.fill(consumerCount)(consumer))
      _         <- producers.join *> consumers.join
      _         <- CausalProfiler.progressPoint("iteration")
    } yield ()
  }
```

`CostCenter.withChildCostCenter` gives `produce` and `consume` their names by hand. With the [compiler plugin](../how-to/setup-tagging-plugin.md) you would not need it, but the names would then be source locations. The profiler only experiments with named parts.

## Step 2: Run the profiler

`profile` needs a program that never ends, so we add `forever`. The defaults wait 30 seconds before the first experiment, so we shorten them for this run.

```scala
val run: Task[Unit] =
  CausalProfiler(iterations = 30, experimentTargetSamples = 5, warmUpPeriod = 5.seconds, coolOffPeriod = 0.seconds)
    .profile(program.forever)
    .flatMap(_.renderToFile("profile.coz"))
```

The profiler logs a line when each experiment starts. When 30 experiments are done, it interrupts the program and writes `profile.coz`.

## Step 3: Read the output

Each experiment is a block of tab-separated lines. This is an invented example of the shape:

```scala
import zio.profiling.causal.{ExperimentResult, LatencyData, ThroughputData}

val result = ProfilingResult(
  List(
    ExperimentResult(
      selected = CostCenter.Root / "consume",
      speedup = 0.25f,
      duration = 1000000000L,
      effectiveDuration = 900000000L,
      selectedSamples = 100L,
      throughputData = List(ThroughputData("iteration", 12)),
      latencyData = List(LatencyData("request", 20, 18))
    )
  )
)
```

```scala
result.render
// res1: String = """experiment	selected=consume	speedup=0.25	duration=900000000	selected-samples=100
// throughput-point	name=iteration	delta=12
// latency-point	name=request	arrivals=20	departures=18	difference=2"""
```

The first line says: with `consume` made 25% faster, the experiment effectively lasted 900,000,000 ns. The `throughput-point` line says `iteration` was reached 12 times in it. To judge a cost center, compare experiments with different speedups for it. If the count rises as the speedup rises, speeding that part up speeds up the program.

## Step 4: Change the program and profile again

Raise `queueSize` to 1000, run the profiler again, and compare the two files. A larger queue means producers wait less. If the counts for `produce` and `consume` move differently, you have found out which side limits this program.

## What you learned

- The causal profiler needs named parts, a progress point and a program that runs forever.
- Each experiment reports the throughput of the progress points for one selected part at one speedup.
- You compare experiments to find the part that limits the program.

## Next steps

- [Find a bottleneck with the causal profiler](../how-to/find-bottleneck-causal.md) shows the same workflow for a loop.
- [Causal profiler reference](../reference/causal-profiler.md) lists every option and the output format.
