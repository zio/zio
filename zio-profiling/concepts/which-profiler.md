# Which Profiler Do I Need?

> ZIO Profiling ships two profilers in the `zio-profiling` module, plus a JMH integration. They answer different questions.

## Where does my program spend its time?

Use the [sampling profiler](../reference/sampling-profiler.md). It records, at a fixed interval, which cost center each running fiber is in, and the result renders as a flamegraph. It is cheap, it stops when your program finishes, and it works on any program. Start with the [tutorial](../tutorials/first-flamegraph.md).

## Which part should I speed up?

Use the [causal profiler](../reference/causal-profiler.md). Time spent is not the same as time that matters: a part of a program that runs in parallel with a slower part can be made faster without changing the result at all. The causal profiler estimates how much the whole program would speed up if one part became faster. It needs a program that runs in a loop, and a progress point that marks one unit of work. Start with [Find a bottleneck with the causal profiler](../how-to/find-bottleneck-causal.md).

## How fast is my benchmark, and where does it spend its time?

Use the [JMH profiler](../reference/jmh-profiler.md), which runs the sampling profiler inside a JMH run. See [Profile JMH benchmarks](../how-to/profile-jmh-benchmarks.md).

## Related

- [How ZIO Profiling works](how-it-works.md) explains cost centers and what each profiler measures.
