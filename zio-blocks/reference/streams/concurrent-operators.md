# Concurrent Operators

> ZIO Blocks Streams ships **three** concurrent operators that fan work across virtual threads while preserving the typed-error, synchronous, pull-based programming model. The calling thread still receives `Either[E, Z]` — no effect system is required.

ZIO Blocks Streams ships **three** concurrent operators that fan work across virtual threads while preserving the typed-error, synchronous, pull-based programming model. The calling thread still receives `Either[E, Z]` — no effect system is required.

| Operator | Purpose |
|---|---|
| `Stream#mapPar(n)(f)` | Apply `f` to each element on up to `n` worker threads. Output is **unordered** (arrival order, not input order). |
| `Stream.mergeAll(n)(streams)` | Drain up to `n` inner streams concurrently; interleave their elements as they arrive. |
| `Stream#flatMapPar(n)(f)` | Per element, produce a sub-stream via `f`; drain up to `n` sub-streams concurrently. |

All three operators are **JVM-only**. On Scala.js they degrade to sequential equivalents (`map`, `flatten`, `flatMap`).

## Semantics

**Output order.** Concurrent output is **unordered** with respect to input position. Elements arrive as workers complete, not in input order. If you need input order, use sequential `map` / `flatMap`.

**Error propagation.** The first typed error from any worker or inner stream terminates all concurrent work and surfaces as `Left(e)` from the terminal operation. Defects (unexpected exceptions) propagate as thrown exceptions, same as sequential operators.

**Resource safety.** All worker threads and ring-buffer queues are cleaned up deterministically when the consumer closes the reader, the stream errors, or the scope finalizes.

**Primitive specialization.** Readers produced by concurrent operators preserve primitive specialization — `Int`, `Long`, `Float`, and `Double` streams use specialized lock-free queues internally, avoiding boxing in the concurrent handoff between threads.

## Buffer sizing

Concurrent operators use internal ring-buffer queues (default size **64**). Override with `Stream.bufferSize(n) { ... }` where `n` is a positive power of two:

```
Stream.bufferSize(256) {
  Stream.range(0, 1_000_000).mapPar(8)(heavyComputation)
}.runCollect
```

Larger buffers help when producers are bursty; smaller buffers reduce memory when many concurrent streams are active. The default is fine for most workloads.

`Pipeline.buffer(n)` inserts a buffer of `n` elements between upstream and downstream (async handoff on JVM, sync on JS).

## Examples

### `mapPar`

```
// Apply an expensive function using 8 virtual threads.
// Output order varies between runs.
val result = Stream.range(0, 1000)
  .mapPar(8)(n => { Thread.sleep(1); n * 2 })
  .runCollect
// result: Right(Chunk(...)) -- all 1000 elements, but not in 0,2,4,... order
```

### `mergeAll`

```
// Drain 10 streams concurrently, up to 4 at a time.
val streams = Stream.fromIterable(
  (0 until 10).map(i => Stream.range(i * 100, (i + 1) * 100))
)
val merged = Stream.mergeAll(4)(streams).runFold(0L)(_ + _)
// merged: Right(499500) -- all elements consumed, order interleaved
```

### `flatMapPar`

```
// Each element spawns a sub-stream; up to 8 drained concurrently.
val flat = Stream.range(0, 50)
  .flatMapPar(8)(i => Stream.range(i * 20, (i + 1) * 20))
  .runFold(0L)(_ + _)
// flat: Right(499500)
```

### Error behaviour

```
// Typed error in a worker terminates all workers
val err1 = Stream.range(0, 1000)
  .flatMap(n => if (n == 500) Stream.fail("bad element") else Stream.succeed(n))
  .mapPar(4)(identity)
  .runCollect
// err1: Left("bad element")

// Error in one inner stream terminates mergeAll
val err2 = Stream.mergeAll(4)(Stream.fromIterable(
  List(Stream.range(0, 100), Stream.fail("inner error"), Stream.range(200, 300))
)).runCollect
// err2: Left("inner error")
```

## Guidelines

- **Use `mapPar(n)(f)` for expensive per-element work** — network calls, CPU-bound computation, blocking I/O. Do not use it for trivially cheap functions (e.g. `_ + 1`); the thread-handoff overhead exceeds the parallelism benefit.
- **Use `mergeAll(n)(streams)` for concurrent fan-in** — draining multiple independent sources (files, connections, partitions) simultaneously. Use `flatMapPar(n)(f)` when each input element produces a sub-stream to drain concurrently.
- **Concurrent output is unordered.** If you need sorted results, apply `.runCollect.map(_.sorted)` or accumulate into a structure that handles ordering. If you need input-order preservation, use sequential `map` / `flatMap`.
- **`mapPar`, `mergeAll`, and `flatMapPar` are JVM-only.** On JS they degrade to sequential equivalents.

## Comparison with other libraries

| Feature | ZB Streams | fs2 | Kyo | Ox | Pekko |
|---|---|---|---|---|---|
| Concurrent operators | `mapPar`, `mergeAll`, `flatMapPar` | `parEvalMap` | `mapParUnordered`* | `mapPar` | `mapAsync`, `flatMapMerge` |
| Effect system required | No | Yes (cats-effect) | Yes (Kyo) | No (virtual threads) | Yes (Akka) |
| Typed errors | `Either[E, Z]` | ApplicativeError | Kyo effects | Exceptions | No |

\* Kyo's `mapParUnordered` forks a fiber per element (very slow for large streams). Kyo's `collectAll` merges streams but does not parallelize pure computation within them.
