# Fix Empty or Unnamed Profiles

> This guide lists the usual reasons a profile has no useful content, and the fix for each.

## The profile has one line with no name

A line such as ` 123` means all samples landed in the root cost center. Nothing in the profiled code is tagged.

- Enable the [tagging compiler plugin](setup-tagging-plugin.md) in the module that contains the code, and run `sbt clean compile`.
- Or tag the effects yourself. See [Tag effects by hand](tag-effects-by-hand.md).

## The profile is missing a part of the program

The sampler counts only fibers that are running. Time spent waiting on an asynchronous result, for example `ZIO.sleep` or a queue `take`, is not counted. Only effects that run with the profiler attached are sampled: those inside `profile`, inside `.supervised(supervisor)`, or on the `supervisedRuntime`.

## The causal profiler dies with "Program completed before profiler could collect sufficient data"

The program ended before the profiler finished its experiments. Pass an effect that never ends, such as `iteration.forever`, or use `profileIterations`. If the iterations are slow, lower `iterations`, `warmUpPeriod` and `minExperimentDuration`.

## The causal profiler never finishes

The profiler experiments only with tagged cost centers that are running and not the root. If no part of the program is tagged, or `candidateSelector` rejects every name, no experiment ever starts. Tag the code, and check the selector. See [Find a bottleneck with the causal profiler](find-bottleneck-causal.md).

## The causal profile has no throughput lines

The program never reached a progress point during an experiment. Call `CausalProfiler.progressPoint` where one unit of work completes.

## JMH writes no `profile.folded`

The JMH profiler starts in the first warm-up iteration. Run with at least one warm-up iteration (`-wi 1` or more). See the [JMH profiler reference](../reference/jmh-profiler.md).

## Related

- [How ZIO Profiling works](../concepts/how-it-works.md) explains what each profiler counts.
