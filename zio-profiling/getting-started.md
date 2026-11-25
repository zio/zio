# Getting Started with ZIO Profiling

> This page takes you from an empty build to your first profile in a few minutes. For a slower walk-through that also draws a flamegraph, see the [tutorial](tutorials/first-flamegraph.md).

## Add the dependencies

Add the library and the tagging compiler plugin to the module you want to profile. The artifacts, platforms and supported Scala versions are listed in [Installation](index.md#installation) and [Modules](index.md#modules).

The plugin is what gives the profile useful names. Without it, every sample lands in one unnamed entry.

## Profile your first program

The program below runs two effects at the same time. `SamplingProfiler().profile` runs it and returns a `ProfilingResult`: the number of samples taken in each cost center. A cost center is a name for a part of your program, such as `fast` or `slow` below.

```scala
import zio._
import zio.profiling.CostCenter
import zio.profiling.sampling._

val fast: UIO[Unit] = CostCenter.withChildCostCenter("fast")(ZIO.succeed(Thread.sleep(200)))

val slow: UIO[Unit] = CostCenter.withChildCostCenter("slow")(ZIO.succeed(Thread.sleep(600)))

val profile: Task[Unit] =
  SamplingProfiler()
    .profile(fast <&> slow)
    .flatMap(result => ZIO.foreach(result.stackCollapse)(Console.printLine(_)).unit)
```

Here `CostCenter.withChildCostCenter` names the two effects by hand. With the compiler plugin enabled, every `def` and `val` of type `ZIO` or `ZStream` gets such a name automatically, so you can leave the wrapping out. Running `profile` prints one line per cost center, such as `fast 20` and `slow 60`: the path of names, a space, and the sample count. The `slow` line has about three times as many samples as `fast`, because it runs three times as long.

## Next steps

- Draw a flamegraph from the output: [Profile a program and read the flamegraph](tutorials/first-flamegraph.md).
- Decide which profiler fits your question: [Which profiler do I need?](concepts/which-profiler.md).
- Find the part of a program that limits throughput: [Find a bottleneck with the causal profiler](how-to/find-bottleneck-causal.md).
