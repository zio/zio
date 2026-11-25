# Profile a Program and Read the Flamegraph

> In this tutorial you will profile a small ZIO program with the sampling profiler, write the result to a file, and turn that file into a flamegraph. A flamegraph shows which parts of your program used the most time. By the end you will know how to read one.

You need:

- an sbt project that uses Scala 2.12, Scala 2.13 or Scala 3 and ZIO 2
- the [FlameGraph](https://github.com/brendangregg/FlameGraph) scripts, in particular `flamegraph.pl`, on your `PATH`

## Step 1: Add ZIO Profiling to your build

Add the library and the compiler plugin to your `build.sbt`:

```sbt
libraryDependencies += "dev.zio" %% "zio-profiling" % "0.3.5"
libraryDependencies += compilerPlugin("dev.zio" %% "zio-profiling-tagging-plugin" % "0.3.5")
```

The compiler plugin is not optional for this tutorial. It tags each effect with the place in your source code where it was defined. Without it, every sample lands in one unnamed entry and the flamegraph tells you nothing. You will see this in Step 5.

## Step 2: Write a program with two parts

Our program has two parts that run at the same time. `fast` takes 400 ms, and `slow` takes 600 ms. Both are built with `ZIO.succeed`, which runs the `Thread.sleep` call when the effect executes. `<&>` runs the two effects in parallel, so the program takes about 600 ms.

```scala
import zio._
import zio.profiling.sampling._

val fast: UIO[Unit] = ZIO.succeed(Thread.sleep(400))

val slow: UIO[Unit] = ZIO.succeed(Thread.sleep(600))

val program: UIO[Unit] = fast <&> slow
```

## Step 3: Profile the program

`SamplingProfiler()` creates a profiler with the default sampling period of 10 ms. Call `profile` with the effect you want to measure. Once the effect finishes, `profile` gives you a `ProfilingResult`. `stackCollapseToFile` writes that result in the folded-stack format that `flamegraph.pl` reads.

```scala
SamplingProfiler()
  .profile(program)
  .flatMap(_.stackCollapseToFile("profile.folded"))
```

The profiler takes samples only while the program runs. It does not change how the program runs, so the profiled effect keeps its normal result.

## Step 4: Understand the folded format

Each line of `profile.folded` is one cost center followed by a space and a sample count. A cost center is a path of names, joined with `;`, that describes where the time was spent. Here is a hand-written `ProfilingResult` so you can see the format without running a profile:

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
example.stackCollapse.foreach(println)
// outer;fast 40
// outer;slow 60
```

The first line says that 40 samples were taken while the `fast` effect ran inside `outer`. The second line says that 60 samples were taken in `slow`. The sample counts are the numbers the flamegraph uses for the width of each bar.

## Step 5: Render the flamegraph

Turn the folded file into an SVG:

```bash
flamegraph.pl profile.folded > profile.svg
```

Open `profile.svg` in a browser. Each box is a cost center. Its width is proportional to its share of the samples, and a box sits on top of the cost center that contains it. In our program, `slow` should be wider than `fast`, because it runs for 600 ms and `fast` runs for 400 ms.

If you skipped the compiler plugin, the file contains one line with an empty name, for example ` 123`. All the time is in the root cost center, so there is nothing to compare. Add the plugin from Step 1 and profile again.

:::tip[Names come from your source]
With the plugin, each name looks like `fullName(File.scala:line)`, for example `zio.profiling.ProfilerExamples.slow(ProfilerExamples.scala:9)`. The exact text depends on your package, object and file names. The line number points to the definition that the plugin tagged.
:::

## What you learned

- The sampling profiler counts, every few milliseconds, which cost center each running fiber is in.
- `stackCollapseToFile` writes those counts as folded stacks, and `flamegraph.pl` draws them.
- The compiler plugin is what makes cost centers meaningful. Without it there is nothing to see.

## Next steps

- To tell which part of a program is worth optimizing, not just where the time goes, use the [causal profiler](../how-to/find-bottleneck-causal.md).
- To learn what each option of the sampling profiler does, read the [sampling profiler reference](../reference/sampling-profiler.md).
