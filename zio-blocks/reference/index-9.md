# Streams

> `zio.blocks.streams` is a **synchronous, pull-based** streaming library for **Scala 3** (and Scala 2.13) with typed errors, resource safety, and primitive specialization. Streams are lazy descriptions -- nothing executes until a terminal operation is called. All results are returned as `Either[E, Z]`, keeping error handling explicit and typed. The library has zero runtime dependencies beyond `zio.blocks.chunk` and `zio.blocks.scope`, and achieves zero-boxing on primitive element types (`Int`, `Long`, `Float`, `Double`) through JVM-type-specialized internal readers.

ZIO Blocks Streams is built on three composable primitives:

| Type                                   | Description                                                          | Key operation         |
|----------------------------------------|----------------------------------------------------------------------|-----------------------|
| [`Stream[+E, +A]`](./stream.md)        | A lazy, pull-based sequence of elements that may fail with error `E` | `stream.via(pipe)`    |
| [`Pipeline[-In, +Out]`](./pipeline.md) | A reusable, composable stream-to-stream transformation               | `pipe.andThen(other)` |
| [`Sink[+E, -A, +Z]`](./sink.md)        | A stream consumer that produces a typed result `Z`                   | `stream.run(sink)`    |

## Overview

ZIO Blocks Streams is designed around three core principles:

**Synchronous execution.** All terminal operations (`run`, `runCollect`, `head`, etc.) return `Either[E, Z]` directly — no async effects, no ZIO runtime required. This makes streams easy to embed in any Scala or Java code.

**Pull-based evaluation.** Execution is driven from the consumer (Sink) backward through the pipeline to the source (Stream). This enables natural short-circuiting: if a sink only needs the first three elements, the stream stops producing after three elements — no work is wasted.

**Resource safety via RAII.** Resources acquired during stream construction (file handles, database connections, etc.) are always released in `finally` blocks, whether the stream succeeds, fails, or is short-circuited.

## Quick Start

Here's a minimal example. Streams are lazy descriptions — nothing executes until you call a terminal operation like `runCollect`. The result is always `Either[E, Z]`, keeping errors explicit and typed.

```scala
import zio.blocks.streams.*
import zio.blocks.chunk.Chunk

// Build a lazy stream description
val stream = Stream.range(1, 100)
  .filter(_ % 2 == 0)
  .map(_ * 3)

// Run it — nothing executes until here
val result = stream.take(5).runCollect
// Right(Chunk(6, 12, 18, 24, 30))
```

## Installation

Add the Streams module to your SBT build:

```scala
libraryDependencies += "dev.zio" %% "zio-blocks-streams" % "0.0.51"
```

For Scala.js (JavaScript/Node.js):

```scala
libraryDependencies += "dev.zio" %%% "zio-blocks-streams" % "0.0.51"
```

Supported Scala versions: 2.13.x and 3.x.

## Why Streams?

Streaming libraries in the Scala ecosystem typically require an effect system. fs2 needs `cats.effect.IO`, Kyo Streams needs the Kyo runtime, and Pekko (formerly Akka) Streams needs the actor runtime. When your code is synchronous and you want streaming without pulling in an effect monad, the options narrow considerably.

`zio.blocks.streams` fills that gap:

| Feature                   | ZB Streams              | fs2                  | Kyo           | Ox                      | Pekko           |
|---------------------------|-------------------------|----------------------|---------------|-------------------------|-----------------|
| Effect system required    | No                      | Yes (cats-effect)    | Yes (Kyo)     | No (virtual threads)    | Yes (Akka)      |
| Execution model           | Synchronous, pull-based | Async, pull-based    | Async, chunk  | Synchronous, pull-based | Async, push     |
| Typed errors              | `Either[E, Z]`          | ApplicativeError     | Kyo effects   | Exceptions              | No              |
| Primitive specialization  | Yes (zero boxing)       | No                   | No            | No                      | No              |
| Stack-safe deep pipelines | Yes (trampolined)       | Yes (Pull)           | Yes           | No (SO on deep flatMap) | N/A             |
| Resource safety           | Scope integration       | Resource/bracket     | Kyo resources | try/finally             | Graph lifecycle |
| Dependencies              | chunk + scope           | cats-effect + scodec | Kyo core      | Ox core                 | Akka actor      |

## Benchmarks

All benchmarks use 10,000 elements, measured in operations per second (higher is better). Run on Apple M-series, JDK 25, Scala 3.7.4.

If you are evaluating Scala 2 compatibility work, read the [Scala 2 compatibility design note](./scala-2-compatibility.md) before moving any `Stream` or `Sink` hot-path combinators behind version-specific seams.

| Benchmark            | ZB Streams | Ox     | Kyo    | fs2    | Pekko |
|----------------------|------------|--------|--------|--------|-------|
| drain                | 179,872    | 54,512 | 31,777 | 20,795 | 4,381 |
| map                  | 161,920    | 42,007 | 12,012 | 13,295 | 2,259 |
| filter               | 168,541    | 47,933 | 19,962 | 14,977 | 2,901 |
| flatMap              | 49,165     | 30,506 | 28,303 | 748    | 742   |
| take/drop            | 322,470    | 28,708 | 64,640 | 28,836 | 2,379 |
| map+filter+flatMap   | 980        | 508    | 602    | 19     | 16    |
| mixed depth 1        | 47,459     | 19,449 | 13,427 | 257    | 639   |
| mixed depth 2        | 33,859     | 15,336 | 7,328  | 208    | 459   |
| mixed depth 3        | 23,610     | 11,878 | 3,174  | 139    | 256   |
| nested flatMap (10K) | 8,161      | --     | --     | 937    | --    |
| nested concat (10K)  | 6,140      | --     | 3      | 1,065  | 1     |

"--" indicates the benchmark was not run or the library crashed.

ZB Streams leads in every single-operator benchmark and maintains its advantage as pipeline depth increases. The "mixed depth" rows show cascading `map`/`filter`/`flatMap` stages — ZB Streams degrades gracefully thanks to its trampolined execution model, while libraries without stack-safety (Ox) or with high per-element overhead (fs2, Pekko) fall off sharply.

## Core mental model

To understand ZIO Blocks Streams fully, it's helpful to see how the three primitives fit together and how data flows through a pipeline from source to sink. This section walks through the architecture and explains each component in depth.

### Execution Flow

Operations on streams transform the pipeline and ultimately run it against a sink:

```
┌──────────────────────────────────┐
│ Stream[E, A]                     │
│ (lazy description)               │
└──────────────────┬───────────────┘
                   │
      .flatMap, .map, .filter, etc.
                   │
┌──────────────────▼───────────────┐
│ Pipeline[-In, +Out]              │
│ (stream → stream transformation) │
└──────────────────┬───────────────┘
                   │
        .via(pipe)
                   │
┌──────────────────▼───────────────┐
│ Sink[E, A, Z]                    │
│ (stream consumer → result Z)     │
└──────────────────┬───────────────┘
                   │
         .run(sink)
                   │
┌──────────────────▼───────────────┐
│ Either[E, Z]                     │
│ (synchronous result)             │
└──────────────────────────────────┘
```

### 1) `Stream[E, A]` -- a lazy sequence

A `Stream[+E, +A]` is a **description** of a potentially infinite sequence of elements of type `A` that may fail with an error of type `E`. It is covariant in both type parameters.

Nothing happens when you construct a stream or chain transformations. Execution only begins when you call a terminal operation (`run`, `runCollect`, `runDrain`, `head`, `count`, etc.). Terminal operations return `Either[E, Z]`:

- `Left(e)` -- a typed stream error
- `Right(z)` -- the successful result

Untyped defects (unexpected exceptions) propagate as thrown exceptions, not as `Left` values.

```scala
import zio.blocks.streams.*

// This does nothing -- it's just a description
val description: Stream[Nothing, Int] =
  Stream.range(0, 1_000_000)
    .filter(_ % 7 == 0)
    .map(_ * 2)
    .take(100)

// Only this line executes the pipeline
// val result = description.runCollect
```

Streams render their pipeline structure as a human-readable string:

```scala
val s = Stream.range(0, 100).map(_ + 1).filter(_ > 50).take(10)
println(s)  // Stream.range(0, 100).map(...).filter(...).take(10)
```

This makes debugging and logging straightforward -- you can see exactly what transformations a stream applies without running it.

---

### 2) `Sink[E, A, Z]` -- a consumer

A `Sink[+E, -A, +Z]` consumes elements of type `A` from a stream and produces a final result of type `Z`. Sinks are passed to `Stream.run`:

```scala
import zio.blocks.streams.*

val streamSinks = Stream.range(1, 101)

// Built-in sinks
val total = streamSinks.run(Sink.count)
val items = streamSinks.run(Sink.collectAll)
val sum   = streamSinks.run(Sink.sumInt)
val first = streamSinks.run(Sink.head)
```

Most sinks also have convenience methods directly on `Stream`:

```scala
stream.count       // Either[Nothing, Long]
stream.runCollect  // Either[Nothing, Chunk[Int]]
stream.head        // Either[Nothing, Option[Int]]
stream.last        // Either[Nothing, Option[Int]]
```

Sinks compose with `contramap` (pre-process input) and `map` (post-process result):

```scala
val lengthSink: Sink[Nothing, String, Long] =
  Sink.sumInt.contramap[String](_.length)

val doubled: Sink[Nothing, Int, Long] =
  Sink.sumInt.map(_ * 2)
```

---

### 3) `Pipeline[In, Out]` -- reusable transformation

A `Pipeline[-In, +Out]` is a reusable stream transformation. It decouples the transformation logic from any specific stream, so you can define it once and apply it many times.

```scala
// Define a reusable pipeline
val normalize: Pipeline[Int, Double] =
  Pipeline.filter[Int](_ > 0)
    .andThen(Pipeline.map[Int, Double](_.toDouble / 100.0))

// Apply to different streams
val result1 = Stream.range(-10, 10).via(normalize).runCollect
val result2 = Stream.fromIterable(List(42, -5, 100, 0)).via(normalize).runCollect
```

Pipelines compose with `andThen`:

```scala
val step1: Pipeline[String, Int] =
  Pipeline.map[String, Int](_.length)

val step2: Pipeline[Int, Int] =
  Pipeline.filter[Int](_ > 3)

val combined: Pipeline[String, Int] =
  step1.andThen(step2)
```

You can also apply a pipeline to a sink with `andThenSink` / `applyToSink`, which pre-processes the sink's input:

```scala
val countLong: Sink[Nothing, String, Long] =
  Pipeline.map[String, Int](_.length)
    .andThenSink(Sink.sumInt)
```

## Error Handling

Streams distinguish between two kinds of failures:

- **Typed errors** (`E`) — domain errors you expect and handle, returned as `Left` in the result. Use `catchAll`, `orElse`, or `mapError` to recover.
- **Defects** (`Throwable`) — unexpected exceptions from bugs or system failures. Use `catchDefect` to recover, or they propagate as thrown exceptions.

```scala
val failing: Stream[String, Int] =
  Stream.fromIterable(List(1, 2, 3)) ++ Stream.fail("oops") ++ Stream.fromIterable(List(4, 5))

val recovered = failing.catchAll(_ => Stream.fromIterable(List(99)))
recovered.runCollect  // Right(Chunk(1, 2, 3, 99))
```

## Resource Management

Streams integrate with `zio.blocks.scope.Scope` for deterministic resource cleanup. The `fromAcquireRelease` constructor guarantees that a resource is acquired lazily (when the stream runs), used to produce elements, and then released — even if the stream is short-circuited early via `take()`, fails with an error, or succeeds normally. The release function is wired into a finally block, ensuring cleanup always happens.

```scala
import zio.blocks.streams.*

val managed = Stream.fromAcquireRelease(
  acquire = scala.io.Source.fromFile("data.txt"),
  release = _.close()
)(source => Stream.fromIterator(source.getLines()))

managed.take(10).runCollect
// File is closed in finally block regardless of outcome
```

This eliminates the need for manual try/finally when working with resources — the stream handles it for you.

## Primitive Specialization

ZB Streams eliminates boxing for `Int`, `Long`, `Float`, and `Double` elements throughout the entire pipeline. Every intermediate step uses specialized `readInt`/`readLong`/`readFloat`/`readDouble` methods, so no `java.lang.Integer` wrappers are allocated.

```scala
import zio.blocks.streams.*

// This entire pipeline runs with ZERO boxing of the Int elements.
// Every step uses specialized readInt/writeInt internally.
val sum: Either[Nothing, Long] =
  Stream.range(0, 1_000_000)   // Int-specialized source
    .filter(_ % 2 == 0)        // Int-specialized filter
    .map(_ * 3)                 // Int->Int specialized map
    .runFold(0L)(_ + _)         // Long-specialized accumulator
```

This matters most for numeric workloads — data processing, statistics, encoding/decoding — where millions of elements flow through multi-stage pipelines.

## Practical Guidance

- **Start with `Stream` constructors and terminal operations.** You can get very far with `Stream.range`, `Stream.fromIterable`, `.map`, `.filter`, and `.runCollect`.
- **Use `Either` pattern matching** to handle the result: `Right(value)` for success, `Left(error)` for typed failures.
- **Prefer `Stream.fromAcquireRelease`** when wrapping resources (files, connections, etc.) over manual try/finally. It guarantees cleanup even on early termination via `take`, `head`, or error.
- **Use the auto-closing I/O constructors** (`fromInputStream`, `fromJavaReader`, `NioStreams.fromChannel`) by default. Only use the `Unmanaged` variants when you need to borrow a resource whose lifetime is managed elsewhere.
- **Use `Pipeline`** when you have a transformation you want to reuse across multiple streams or apply to sinks.
- **Use `&&` for zipping** instead of manual zip calls. Tuples flatten automatically: `a && b && c` produces `(A, B, C)` not `((A, B), C)`.
- **Leverage primitive specialization** for numeric workloads. Streams of `Int`, `Long`, `Float`, and `Double` avoid boxing automatically; use `Sink.sumInt`, `runFold(0)(_ + _)`, etc. for zero-allocation folds.
- **Use `scan` for running accumulators**, `grouped` for batching, and `sliding` for windowed computations.
- **Use `render`/`toString`** to inspect pipeline structure during debugging — it shows each transformation stage without executing the stream.
- **Use `Sink.create`** as an escape hatch when none of the built-in sinks fit.
- **`suspend`** is your friend for recursive or self-referential stream definitions, preventing stack overflow during construction.
- **Typed errors vs. defects**: use `Stream.fail` for expected domain errors and `Stream.die` for programmer errors. Use `catchAll` for the former, `catchDefect` for the latter.

## Usage examples

This section shows practical examples of using streams in real-world scenarios. Each subsection demonstrates a different aspect of the API with runnable code examples.

### Creating streams

Here are the most common ways to construct a stream. Choose the constructor that best fits your data source:

```scala
import zio.blocks.streams.*
import zio.blocks.chunk.Chunk

// From explicit elements
Stream.fromIterable(List(1, 2, 3))           // Stream[Nothing, Int]
Stream.fromIterable(List("a", "b", "c"))     // Stream[Nothing, String]

// From collections
Stream.fromChunk(Chunk(1, 2, 3))             // Stream[Nothing, Int]
Stream.fromIterable(List("x", "y", "z"))     // Stream[Nothing, String]
Stream.fromIterator(Iterator.from(1))        // Stream[Nothing, Int] (lazy)

// Ranges
Stream.range(0, 100)                         // 0 to 99
Stream.fromRange(1 to 50)                    // 1 to 50

// Single values (primitive-specialized)
Stream.succeed(42)                           // Stream[Nothing, Int]
Stream.succeed(3.14)                         // Stream[Nothing, Double]
Stream.succeed("hello")                      // Stream[Nothing, String]

// Special streams
Stream.empty                                 // Stream[Nothing, Nothing]
Stream.fail("error")                         // Stream[String, Nothing]
// Stream.die(new Exception("defect"))       // throws on evaluation

// Generators
Stream.repeat(1)                             // infinite stream of 1s
// Stream.iterate(1)(_ * 2)                   // 1, 2, 4, 8, 16, ...
// Stream.repeatThunk(scala.util.Random.nextInt(100))  // infinite random ints
Stream.unfold(0)(n =>                        // 0, 1, 2, ..., 9
  if n < 10 then Some((n, n + 1)) else None
)

// Side-effects
Stream.eval(println("hello"))                   // prints, emits nothing
Stream.attempt(someFallibleCall())              // captures exceptions as typed errors
Stream.attemptEval(riskyEffect())               // same, for Unit-returning effects

// Deferred construction (useful for recursion)
Stream.suspend(expensiveStreamBuilder())

// I/O sources (auto-closing) - JVM only
Stream.fromInputStream(inputStream)             // Stream[IOException, Int] (bytes as 0-255, auto-closes)
Stream.fromJavaReader(javaReader)               // Stream[IOException, Char] (auto-closes)

// I/O sources (borrowing -- caller manages lifetime) - JVM only
Stream.fromInputStreamUnmanaged(inputStream)    // Stream[IOException, Int] (does NOT close)
Stream.fromJavaReaderUnmanaged(javaReader)      // Stream[IOException, Char] (does NOT close)
```

---

### Transforming streams

Streams support many transformation operations. Use `map` for element-wise changes, `filter` for selection, and `flatMap` for expanding elements into sub-streams. See the [Stream reference](./stream.md) page for comprehensive examples of all transformation methods including `map`, `filter`, `flatMap`, `collect`, `scan`, `mapAccum`, `distinct`, `intersperse`, and more.

---

### Zipping streams with `&&`

The `&&` operator zips two streams element-by-element into tuples. The resulting stream ends when either input is exhausted.

```scala
import zio.blocks.streams.*

val names: Stream[Nothing, String] = Stream.fromIterable(List("Alice", "Bob", "Charlie"))
val ages:  Stream[Nothing, Int]    = Stream.fromIterable(List(30, 25, 35))
val ids:   Stream[Nothing, Long]   = Stream.fromIterable(List(1L, 2L, 3L))

// Two-way zip
val pairs = names && ages
pairs.runCollect // Right(Chunk(("Alice", 30), ("Bob", 25), ("Charlie", 35)))

// Three-way zip -- tuples flatten automatically
val triples = names && ages && ids
triples.runCollect // Right(Chunk(("Alice", 30, 1L), ("Bob", 25, 2L), ("Charlie", 35, 3L)))
```

When the error types differ, they widen via union:

```scala
import zio.blocks.streams.*

sealed trait MyError
val s1: Stream[MyError, Int]  = Stream.fromIterable(List(1, 2, 3))
sealed trait OtherError
val s2: Stream[OtherError, Int] = Stream.fromIterable(List(4, 5, 6))
// val zipped = s1 && s2
```

---

### Primitive specialization

ZB Streams eliminates boxing for `Int`, `Long`, `Float`, and `Double` elements throughout the entire pipeline. Every intermediate step uses specialized `readInt`/`writeInt` (or the corresponding type) methods, so no `java.lang.Integer` wrappers are allocated.

```scala
// This entire pipeline runs with ZERO boxing of the Int elements.
// Every step uses specialized readInt/writeInt internally.
val sum: Either[Nothing, Long] =
  Stream.range(0, 1_000_000)   // Int-specialized source
    .filter(_ % 2 == 0)        // Int-specialized filter
    .map(_ * 3)                 // Int->Int specialized map
    .runFold(0L)(_ + _)         // Long-specialized accumulator
```

This matters most for numeric workloads -- data processing, statistics, encoding/decoding -- where millions of elements flow through multi-stage pipelines. The benchmark results above reflect this advantage directly.

---

### Consuming streams

Terminal operations run the stream and produce a final result. Use `runCollect` to gather all elements, `runDrain` to discard them, or specialized operations like `head`, `count`, and `foldLeft`:

```scala
val s = Stream.range(1, 11) // 1 to 10

// Collect all elements
s.runCollect              // Right(Chunk(1, 2, 3, ..., 10))

// Discard all elements (run for side-effects only)
s.tapEach(println).runDrain

// Fold
s.runFold(0)(_ + _)       // Right(55)              (Int accumulator)
s.runFold(0L)(_ + _)      // Right(55L)             (Long accumulator)
s.runFold(0.0)(_ + _)     // Right(55.0)            (Double accumulator)

// Foreach
s.runForeach(n => println(n))
s.foreach(n => println(n))  // alias

// Aggregates
s.count                   // Right(10L)
s.head                    // Right(Some(1))
s.last                    // Right(Some(10))
s.exists(_ > 5)           // Right(true)
s.forall(_ > 0)           // Right(true)
s.find(_ > 7)             // Right(Some(8))

// Run with an explicit Sink
s.run(Sink.sumInt)        // Right(55L)
s.run(Sink.take(3))       // Right(Chunk(1, 2, 3))
```

---

### Error handling patterns

Streams support two types of failures: typed errors that you can handle explicitly, and defects (exceptions) that propagate. Here are common patterns for dealing with both:

```scala
// Typed error: appears in Either
val result = Stream.fail("not found").runCollect
// result: Left("not found")

// Recover and continue
val safe =
  Stream.fromIterable(List(1, 2)) ++ Stream.fail("oops") ++ Stream.fromIterable(List(3))
val recovered = safe.catchAll(_ => Stream.fromIterable(List(99))).runCollect
// Right(Chunk(1, 2, 99))

// Transform error type by catching and converting
val inputError: Stream[String, Int] = Stream.fail("bad input")
val transformed = inputError.catchAll(msg => Stream.fail(new IllegalArgumentException(msg)))

// Fallback stream
val primary: Stream[String, Int] = Stream.fail("down")
val backup:  Stream[String, Int] = Stream.fromIterable(List(1, 2, 3))
val result2 = (primary || backup).runCollect
// Right(Chunk(1, 2, 3))

// Catch defects (unexpected exceptions)
val risky: Stream[Nothing, Int] =
  Stream.fromIterable(List(1, 2, 3)).map { n =>
    if n == 2 then throw new ArithmeticException("boom")
    else n
  }

val handled = risky.catchDefect {
  case _: ArithmeticException => Stream.fromIterable(List(0))
}.runCollect
// Right(Chunk(1, 0))
```

---

### Resource safety patterns

When working with files, network connections, or other resources, use the resource-safe constructors to guarantee cleanup. Here are the most common patterns:

```scala
import zio.blocks.streams.*
import zio.blocks.scope.*

// Bracket pattern: acquire/use/release
def fileLines(path: String): Stream[Nothing, String] =
  Stream.fromAcquireRelease(
    acquire = scala.io.Source.fromFile(path),
    release = _.close()
  ) { source =>
    Stream.fromIterable(source.getLines().toList)
  }

// Compose resource-safe streams -- both resources are released
val merged =
  fileLines("input1.txt") ++ fileLines("input2.txt")

// Only reads 10 lines; both files are still closed properly
merged.take(10).runCollect

// ensuring: attach a finalizer
var cleaned = false
Stream.range(1, 6)
  .ensuring { cleaned = true }
  .take(2)
  .runDrain
// cleaned == true, even though only 2 of 5 elements were consumed

// defer: register cleanup that runs on stream close
val withDefer =
  Stream.defer(println("releasing lock")) ++
  Stream.range(1, 100)
```

---

### NIO integration (JVM only)

On the JVM, `NioStreams` and `NioSinks` provide zero-copy integration with `java.nio` buffers and channels.

#### `NioStreams` -- creating streams from NIO sources

```scala
import zio.blocks.streams.*
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.{Paths, StandardOpenOption}

// From a ByteBuffer
val buf = ByteBuffer.wrap(Array[Byte](1, 2, 3, 4, 5))
NioStreams.fromByteBuffer(buf).runCollect
// Right(Chunk(1, 2, 3, 4, 5))

// Typed buffer views (zero-boxing)
val intBuf = ByteBuffer.allocate(16).putInt(1).putInt(2).putInt(3).putInt(4).flip()
NioStreams.fromByteBufferInt(intBuf).runCollect
// Right(Chunk(1, 2, 3, 4))

// Similarly: fromByteBufferLong, fromByteBufferFloat, fromByteBufferDouble

// From a ReadableByteChannel (auto-closing)
val ch = FileChannel.open(Paths.get("data.bin"), StandardOpenOption.READ)
val bytes = NioStreams.fromChannel(ch, bufSize = 4096).runCollect
// ch is closed automatically when the stream completes

// From a ReadableByteChannel (borrowing -- caller manages lifetime)
val ch2 = FileChannel.open(Paths.get("data.bin"), StandardOpenOption.READ)
val bytes2 = NioStreams.fromChannelUnmanaged(ch2, bufSize = 4096).runCollect
ch2.close() // caller is responsible for closing
```

#### `NioSinks` -- writing to NIO targets

```scala
import zio.blocks.streams.*
import zio.blocks.chunk.Chunk
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.{Files, StandardOpenOption}

// Write to a ByteBuffer using a typed sink (Int values, zero-boxing)
val outBuf = ByteBuffer.allocate(1024)
Stream.range(1, 5).run(NioSinks.fromByteBufferInt(outBuf))

// Write to a WritableByteChannel using a stream of bytes
val tempPath = Files.createTempFile("zio-blocks-streams-", ".bin")
val outCh = FileChannel.open(
  tempPath,
  StandardOpenOption.WRITE,
  StandardOpenOption.TRUNCATE_EXISTING
)
val bytes = Chunk.fromIterable(List[Byte](1, 2, 3, 4, 5))
try Stream.fromChunk(bytes).run(NioSinks.fromChannel(outCh))
finally {
  outCh.close()
  Files.deleteIfExists(tempPath)
}
```

---

### Pipeline composition

Pipelines are composable transformations that can be reused across different streams. Build complex transformations by chaining pipelines together with `andThen`:

```scala
import zio.blocks.streams.*

// Build reusable transformation steps
val parseInts: Pipeline[String, Int] =
  Pipeline.collect[String, Int] {
    case s if s.matches("-?\\d+") => s.toInt
  }

val positiveOnly: Pipeline[Int, Int] =
  Pipeline.filter[Int](_ > 0)

val doubled: Pipeline[Int, Int] =
  Pipeline.map[Int, Int](_ * 2)

// Compose into a single pipeline
val fullPipeline: Pipeline[String, Int] =
  parseInts
    .andThen(positiveOnly)
    .andThen(doubled)

// Apply to any stream of strings
Stream.fromIterable(List("10", "abc", "-3", "7", "0", "25"))
  .via(fullPipeline)
  .runCollect
// Right(Chunk(20, 14, 50))

// Apply to a sink (pre-process the sink's input)
val sumPositiveDoubled: Sink[Nothing, String, Long] =
  fullPipeline.andThenSink(Sink.sumInt)

Stream.fromIterable(List("10", "abc", "-3", "7", "0", "25"))
  .run(sumPositiveDoubled)
// Right(84L)
```
