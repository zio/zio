# Low-Level Primitives

> Low-Level Primitives index: Reader and Writer, the live cursors a Stream and Sink compile into at execution time.

`Reader` and `Writer` are the live, stateful objects a pipeline compiles into and runs on. Where the [Core Types](../core/index.md) are inert descriptions, these hold real resources — an open file, a socket, a position in a buffer — until closed.

A terminal such as `stream.runAsync(sink)` compiles to and drains a `Reader` for you. Work with these types directly only when writing a custom source or sink, wrapping a native I/O API, or driving a compiled stream by hand.

## Reader

[`Reader[+Elem]`](./reader.md) is the pull side: the cursor a `Stream` compiles into. It has two kinds — `Reader.SyncReader[Elem]`, whose pulls return directly, and `Reader.AsyncReader[Elem]`, whose pulls return `Async`. An asynchronous reader is single-consumer, with exactly one owner responsible for awaiting its `close()`.

## Writer

[`Writer[-Elem]`](./writer.md) is the push side: a destination you feed elements into one at a time until it closes or fills. Reach for it when adapting an `OutputStream`-shaped API, or implementing a sink that a producer outside the pipeline writes into.

## See Also

- [Core Types](../core/index.md) — the descriptions that compile into these primitives
- [Streams Reference](../index.md) — module overview
- [Asynchronous Stream Execution](../execution-and-compatibility/async-execution.md) — how a graph decides between a `SyncReader` and an `AsyncReader`
