# How ZIO Profiling Works

> A thread profiler sees only the ZIO runtime's evaluation loop. ZIO Profiling instead asks each fiber where in your program it is, and reports per named location. This page explains the pieces and their limits.

## Cost centers

A [`CostCenter`](../reference/cost-center.md) is a path of names, such as `handle;parse`. The path is stored in a `FiberRef`. Running an effect with `CostCenter.withChildCostCenter("parse")` extends the path for the duration of that effect. A fiber that is forked inherits the path of its parent. When it is joined, the parent keeps its own path.

Names come from two places: the [compiler plugin](../reference/tagging-plugin.md), which wraps every `def` and `val` of type `ZIO` or `ZStream`, and the calls you write yourself ([Tag effects by hand](../how-to/tag-effects-by-hand.md)). Code outside any tag runs in the root cost center.

## The sampling profiler

The sampling profiler is a ZIO `Supervisor`. It keeps the set of fibers that are currently running: a fiber joins the set when it starts or resumes and leaves it when it suspends or ends. A background fiber wakes up every `samplingPeriod` and adds one to the count of each running fiber's cost center.

Two consequences follow:

- Time spent waiting on an asynchronous result, such as a queue `take` or a sleep with `ZIO.sleep`, is not counted, because the fiber is suspended. Blocking code, such as `Thread.sleep` inside `ZIO.succeed`, is counted.
- Parallel fibers are all counted on each tick, so the total can exceed the wall-clock time.

## The causal profiler

The causal profiler runs a series of experiments. In each one it picks a cost center and a speedup between 0 and `maxConsideredSpeedUp` percent. It cannot make code faster, so it does the opposite: on every tick, for each fiber running inside the selected cost center, the profiler adds a delay that all other fibers owe. Each of them pays it by sleeping at its next synchronous step. Slowing everything else by 25% has the same effect on the ratio as speeding up the selected part by 25%.

Each experiment counts how often your [progress points](../reference/causal-profiler.md#progress-and-latency-points) were reached. Comparing the counts of experiments with different speedups for the same cost center shows whether a faster cost center would raise throughput. A rise means the part limits the program. A flat line means it does not.

A fiber pays its delay in two places: just before it runs a synchronous step, and when it ends without being interrupted. A fiber that blocks for a long time inside one step is therefore delayed only at its next synchronous step or when it ends. A fiber that was suspended on an asynchronous result is credited for the delay that built up meanwhile when it resumes, and is not delayed for it again.

## Limits

- ZIO Profiling reports CPU use of ZIO programs on the JVM. For heap profiling, use another tool such as async-profiler or VisualVM.
- Only tagged code gets its own name. Untagged code is attributed to the nearest enclosing tag, or to the root.
- The causal profiler considers only tagged cost centers as candidates.
- The profilers add overhead that grows as `samplingPeriod` shrinks.

## Related

- [Which profiler do I need?](which-profiler.md)
- [Sampling profiler reference](../reference/sampling-profiler.md) and [causal profiler reference](../reference/causal-profiler.md)
