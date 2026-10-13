# Execution and Compatibility

> Execution and Compatibility index: async execution, primitive lanes, platform availability, and Scala 2 support for streams.

[Core Types](../core/index.md) covers the descriptions you write and [Low-Level Primitives](../primitives/index.md) the machinery they compile into. This section covers neither: four concerns that cut across every `Stream`, `Pipeline`, and `Sink` once one starts running. None adds a type to compose with.

## Asynchronous Stream Execution

[Asynchronous Stream Execution](./async-execution.md) — how one `Stream[E, A]` describes both synchronous and asynchronous pipelines, with no second type or mode parameter to track. Covers the async constructors, operators, and `*Async` terminals, plus cancellation and resource ownership.

## Zero-Boxing Optimization

[Zero-Boxing Optimization](./zero-boxing.md) — how the library dispatches on a primitive element type to select a physical lane, and how each lane signals end of stream. A structural account of the dispatch mechanism, not a benchmark.

## Platform Differences: JVM and Scala.js

[Platform Differences](./platform-differences.md) — which members are JVM-only, which are cross-platform, and what to write instead when a blocking terminal is unavailable on Scala.js. Read it before cross-building.

## Scala 2 Compatibility Design Note

[Scala 2 Compatibility Design Note](./scala-2-compatibility.md) — why streams supports Scala 2.13, the hot-path constraint that shaped the approach, and why the two versions share sources rather than splitting them.

## See Also

- [Streams Reference](../index.md) — module overview
- [Core Types](../core/index.md) — `Stream`, `Pipeline`, and `Sink`
- [Low-Level Primitives](../primitives/index.md) — `Reader` and `Writer`
