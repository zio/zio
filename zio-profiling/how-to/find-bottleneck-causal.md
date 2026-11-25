# Find a Bottleneck with the Causal Profiler

> Use the causal profiler when you want to know which part of your program is worth optimizing. A sampling profile shows where time is spent. A causal profile estimates how much the whole program would speed up if one part became faster. A part that takes a lot of time is not always the part that limits throughput.

This guide shows how to:

- mark the progress of your program with a progress point
- run the causal profiler on a program that runs in a loop
- write the result so you can open it in the Coz visualizer

It assumes that the effects you want to rank are tagged by the [compiler plugin](setup-tagging-plugin.md) (or by hand, see [Tag effects by hand](tag-effects-by-hand.md)). The profiler only ranks cost centers, so without tags there is nothing to rank.

## Step 1: Mark progress with a progress point

The profiler measures speed as the number of times a progress point is reached per unit of time. Put `CausalProfiler.progressPoint` where one unit of your work is done. For a request handler, that is the end of each request. For a loop, it is the end of each iteration.

```scala
import zio._
import zio.profiling.causal._

val fast: UIO[Unit] = ZIO.succeed(Thread.sleep(40))

val slow1: UIO[Unit] = ZIO.succeed(Thread.sleep(20))

val slow2: UIO[Unit] = ZIO.succeed(Thread.sleep(60))

val iteration: UIO[Unit] =
  (fast <&> (slow1 <&> slow2)) *> CausalProfiler.progressPoint("iteration done")
```

## Step 2: Run the profiler on an endless program

The profiler runs experiments until it has finished `iterations` of them, then interrupts your program itself. So the program you pass in must run forever. Use `forever`, which gives an effect of type `ZIO[R, E, Nothing]`, and `CausalProfiler.profile` only accepts that type.

The defaults are chosen for real programs, and they make a run slow. The profiler waits 30 seconds before its first experiment, runs each experiment for at least 1 second, and waits 2 seconds between experiments. For a quick first run, shorten those values:

```scala
val profiler = CausalProfiler(
  iterations = 10,
  warmUpPeriod = 0.seconds,
  coolOffPeriod = 0.seconds
)

profiler
  .profile(iteration.forever)
  .flatMap(_.renderToFile("profile.coz"))
```

Once you know the program works, raise `iterations` and keep the default timings. Each experiment picks a part of the program and slows down the other code that runs at the same time. Slowing everything else by the same amount is the same as speeding up the selected part. A larger number of iterations gives a more reliable ranking.

## Step 3: Read the result

`renderToFile` writes one block of text per experiment. Upload the file to the [Coz visualizer](https://plasma-umass.org/coz/) to see the result as a chart. Each experiment gives the cost center it selected and the speedup it applied. The chart shows how the program's throughput changed for each selected part.

Read the chart like this:

- A part where throughput rises with the speedup is a good place to optimize. Making it faster makes the whole program faster.
- A flat line means the part does not limit the program. Making it faster changes nothing.

For the program in Step 1, you should expect `slow2` to be the limiting part. Each iteration takes as long as its slowest parallel branch, which is `slow2` at 60 ms. A speedup of `slow1` or `fast` gives no gain, because `slow2` still takes the same time.

:::warning[Only cost centers are ranked]
The profiler considers only effects that run inside a cost center. Untagged code is ignored. If the chart lists no parts you expected, check that the module is compiled with the compiler plugin.
:::

## Step 4: Narrow the candidates (optional)

If you know which parts to test, pass `candidateSelector`. It receives the name of the last location of a cost center and returns `true` for the ones to consider:

```scala
CausalProfiler(
  iterations = 10,
  warmUpPeriod = 0.seconds,
  coolOffPeriod = 0.seconds,
  candidateSelector = _.contains("slow")
)
```

The selector only restricts which parts the profiler experiments with. It does not change how the program runs.

## Related

- [Causal profiler reference](../reference/causal-profiler.md) lists every option and the output format.
- [Set up the tagging compiler plugin](setup-tagging-plugin.md) makes the parts visible to the profiler.
