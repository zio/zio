# Core Types

> Core Types index: Stream, Pipeline, and Sink, the declarative descriptions you compose into a stream processing pipeline.

`Stream`, `Pipeline`, and `Sink` are the three types you compose to build a pipeline. Each is a lazy, immutable description rather than a running process: you assemble one with ordinary combinators, and nothing executes until a terminal operation such as `stream.runAsync(sink)` — or `stream.run(sink)`, which is JVM-only — compiles and drives it.

Compiling is what materializes a description into a [`Reader`](../primitives/reader.md), the stateful cursor a sink pulls from. Those live primitives are documented under [Low-Level Primitives](../primitives/index.md); this section covers the descriptions you write.

## Stream

[`Stream[+E, +A]`](./stream.md) describes a source of elements of type `A` that may fail with `E`. Values are produced lazily, so a stream can outlive what fits in memory and can hold an external resource open only while it is being drained.

## Pipeline

[`Pipeline[-In, +Out]`](./pipeline.md) describes a reusable transformation from `In` to `Out`. Reach for it when the same steps apply to more than one stream. Pipelines compose with `andThen`, apply to a stream with `stream.via(pipe)`, and to a sink with `pipe.andThenSink(sink)`.

## Sink

[`Sink[+E, -A, +Z]`](./sink.md) describes how to consume elements of type `A` into a result `Z`, possibly failing with `E` — the endpoint of a pipeline, built once and reusable across streams.

## See Also

- [Streams Reference](../index.md) — module overview
- [Low-Level Primitives](../primitives/index.md) — the `Reader` and `Writer` cursors these compile into
