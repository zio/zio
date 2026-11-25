# Profile Part of a Program with a Supervisor

> `SamplingProfiler().profile` and `CausalProfiler.profile` wrap the whole effect you pass in. When you want to profile only one part, for example one request handler in a larger application, attach the profiler as a supervisor to just that part. This guide shows how, for both profilers.

## Profile part of a program with the sampling profiler

`makeSupervisor` starts the sampler in the current scope and returns the supervisor that stores the samples. The sampler only sees effects that run with `.supervised(supervisor)`:

```scala
import zio._
import zio.profiling.sampling._

val handleRequest: UIO[Unit] = ZIO.succeed(Thread.sleep(100))

val profiled: Task[ProfilingResult] =
  ZIO.scoped {
    SamplingProfiler().makeSupervisor.flatMap { supervisor =>
      handleRequest.supervised(supervisor) *> supervisor.value
    }
  }
```

Sampling stops when the scope closes. Read the samples before that, with `supervisor.value`.

## Profile part of a program with the causal profiler

`CausalProfiler.supervisor` creates the profiler as a supervisor. Reading its `value` waits until the profile is complete, so run the program on a separate fiber. The profiled effect needs OpSupervision enabled and must run forever:

```scala
import zio._
import zio.profiling.causal._

val iteration: UIO[Unit] = ZIO.succeed(Thread.sleep(20)) *> CausalProfiler.progressPoint("iteration")

val profile: Task[ProfilingResult] =
  ZIO.scoped {
    CausalProfiler(iterations = 10, warmUpPeriod = 0.seconds, coolOffPeriod = 0.seconds).supervisor.flatMap {
      supervisor =>
        iteration.forever
          .withRuntimeFlags(RuntimeFlags.enable(RuntimeFlag.OpSupervision))
          .supervised(supervisor)
          .fork
          .flatMap(fiber => supervisor.value <* fiber.interrupt)
    }
  }
```

`CausalProfiler.profile` does the same steps for you, and also fails with `Program completed before profiler could collect sufficient data` when the program ends early.

## Related

- [Profile a running application](profile-running-application.md) attaches the sampler to a whole runtime.
- [Sampling profiler reference](../reference/sampling-profiler.md) and [causal profiler reference](../reference/causal-profiler.md) describe the methods used here.
