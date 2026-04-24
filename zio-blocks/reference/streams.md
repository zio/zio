# Streams

> `zio.blocks.streams` is a **synchronous, pull-based** streaming library for **Scala 3** (and Scala 2.13) with typed errors, resource safety, and primitive specialization. Streams are lazy descriptions -- nothing executes until a terminal operation is called. All results are returned as `Either[E, Z]`, keeping error handling explicit and typed. The library has zero runtime dependencies beyond `zio.blocks.chunk` and `zio.blocks.scope`, and achieves zero-boxing on primitive element types (`Int`, `Long`, `Float`, `Double`) through JVM-type-specialized internal readers.

`zio.blocks.streams` is a **synchronous, pull-based** streaming library for **Scala 3** (and Scala 2.13) with typed errors, resource safety, and primitive specialization. Streams are lazy descriptions -- nothing executes until a terminal operation is called. All results are returned as `Either[E, Z]`, keeping error handling explicit and typed. The library has zero runtime dependencies beyond `zio.blocks.chunk` and `zio.blocks.scope`, and achieves zero-boxing on primitive element types (`Int`, `Long`, `Float`, `Double`) through JVM-type-specialized internal readers.

## Why Streams?

Streaming libraries in the Scala ecosystem typically require an effect system. fs2 needs `cats.effect.IO`, Kyo Streams needs the Kyo runtime, and Pekko (formerly Akka) Streams needs the actor runtime. When your code is synchronous and you want streaming without pulling in an effect monad, the options narrow considerably.

`zio.blocks.streams` fills that gap:

| Feature | ZB Streams | fs2 | Kyo | Ox | Pekko |
|---|---|---|---|---|---|
| Effect system required | No | Yes (cats-effect) | Yes (Kyo) | No (virtual threads) | Yes (Akka) |
| Execution model | Synchronous, pull-based | Async, pull-based | Async, chunk-based | Synchronous, pull-based | Async, push-based |
| Typed errors | `Either[E, Z]` | ApplicativeError | Kyo effects | Exceptions | No |
| Primitive specialization | Yes (zero boxing) | No | No | No | No |
| Internal chunking | No (element-at-a-time) | Yes (Chunk) | Yes (Chunk) | No | No |
| Stack-safe deep pipelines | Yes (trampolined) | Yes (Pull) | Yes | No (SO on deep flatMap) | N/A |
| Resource safety | Scope integration | Resource/bracket | Kyo resources | try/finally | Graph lifecycle |
| Dependencies | chunk + scope | cats-effect + scodec | Kyo core | Ox core | Akka actor |

Key properties:

- **No effect system** -- streams run on the calling thread; `run` returns `Either[E, Z]` directly
- **Pull-based** -- the consumer drives evaluation; elements are produced on demand. Contrast with push-based systems (like Pekko) where the producer drives and the consumer must keep up.
- **Primitive specialization** -- `Int`, `Long`, `Float`, and `Double` streams avoid boxing through specialized `readInt`, `readLong`, `readFloat`, `readDouble` methods on `Reader`
- **Resource safe** -- integrates with `zio.blocks.scope.Scope` for deterministic finalization; also provides `fromAcquireRelease`, `ensuring`, and `defer` for standalone resource management
- **Lazy** -- a `Stream[E, A]` is a description; construction is free and nothing executes until a terminal operation (`run`, `runCollect`, `head`, etc.)
- **Debuggable** -- streams render their pipeline structure via `toString`/`render`, so you can inspect what a stream does without running it

---

## Quick start

```scala

// Create a stream, transform it, consume it
val result: Either[Nothing, Chunk[Int]] =
  Stream.range(1, 11)        // 1 to 10
    .filter(_ % 2 == 0)      // keep evens
    .map(_ * 10)              // multiply by 10
    .runCollect               // collect into a Chunk

// result: Right(Chunk(20, 40, 60, 80, 100))
```

Key points:

- `Stream.range(1, 11)` creates a lazy stream of integers 1 through 10 (exclusive upper bound)
- `.filter` and `.map` add transformations without executing anything
- `.runCollect` is the terminal operation -- it drives evaluation and returns `Either[E, Chunk[A]]`
- Since `Stream.range` cannot fail, the error type is `Nothing` and the result is always `Right`

A stream that can fail:

```scala
val fallible: Either[String, Chunk[Int]] =
  Stream.range(1, 6)
    .flatMap { n =>
      if n == 3 then Stream.fail("boom at 3")
      else Stream.succeed(n)
    }
    .runCollect

// fallible: Left("boom at 3")
```

---

## Benchmarks

All benchmarks use 10,000 elements, measured in operations per second (higher is better). Run on Apple M-series, JDK 25, Scala 3.7.4.

| Benchmark | ZB Streams | Ox | Kyo | fs2 | Pekko |
|---|---|---|---|---|---|
| drain | 179,872 | 54,512 | 31,777 | 20,795 | 4,381 |
| map | 161,920 | 42,007 | 12,012 | 13,295 | 2,259 |
| filter | 168,541 | 47,933 | 19,962 | 14,977 | 2,901 |
| flatMap | 49,165 | 30,506 | 28,303 | 748 | 742 |
| take/drop | 322,470 | 28,708 | 64,640 | 28,836 | 2,379 |
| map+filter+flatMap | 980 | 508 | 602 | 19 | 16 |
| mixed depth 1 | 47,459 | 19,449 | 13,427 | 257 | 639 |
| mixed depth 2 | 33,859 | 15,336 | 7,328 | 208 | 459 |
| mixed depth 3 | 23,610 | 11,878 | 3,174 | 139 | 256 |
| nested flatMap (10K) | 8,161 | -- | -- | 937 | -- |
| nested concat (10K) | 6,140 | -- | 3 | 1,065 | 1 |

"--" indicates the benchmark was not run or the library crashed.

ZB Streams leads in every single-operator benchmark and maintains its advantage as pipeline depth increases. The "mixed depth" rows show cascading `map`/`filter`/`flatMap` stages -- ZB Streams degrades gracefully thanks to its trampolined execution model, while libraries without stack-safety (Ox) or with high per-element overhead (fs2, Pekko) fall off sharply.

---

## Core mental model

### 1) `Stream[E, A]` -- a lazy sequence

A `Stream[+E, +A]` is a **description** of a potentially infinite sequence of elements of type `A` that may fail with an error of type `E`. It is covariant in both type parameters.

Nothing happens when you construct a stream or chain transformations. Execution only begins when you call a terminal operation (`run`, `runCollect`, `runDrain`, `head`, `count`, etc.). Terminal operations return `Either[E, Z]`:

- `Left(e)` -- a typed stream error
- `Right(z)` -- the successful result

Untyped defects (unexpected exceptions) propagate as thrown exceptions, not as `Left` values.

```scala
// This does nothing -- it's just a description
val description: Stream[Nothing, Int] =
  Stream.range(0, 1_000_000)
    .filter(_ % 7 == 0)
    .map(_ * 2)
    .take(100)

// Only this line executes the pipeline
val result: Either[Nothing, Chunk[Int]] = description.runCollect
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
val stream = Stream.range(1, 101)

// Built-in sinks
val total: Either[Nothing, Long]        = stream.run(Sink.count)
val items: Either[Nothing, Chunk[Int]]  = stream.run(Sink.collectAll)
val sum:   Either[Nothing, Long]        = stream.run(Sink.sumInt)
val first: Either[Nothing, Option[Int]] = stream.run(Sink.head)
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

Built-in sinks:

| Sink | Result type | Description |
|---|---|---|
| `Sink.collectAll` | `Chunk[A]` | Collects all elements |
| `Sink.drain` | `Unit` | Consumes and discards all elements |
| `Sink.count` | `Long` | Counts elements |
| `Sink.foldLeft(z)(f)` | `Z` | Left fold with initial value |
| `Sink.foreach(f)` | `Unit` | Side-effect per element |
| `Sink.head` | `Option[A]` | First element |
| `Sink.last` | `Option[A]` | Last element |
| `Sink.take(n)` | `Chunk[A]` | First n elements |
| `Sink.exists(p)` | `Boolean` | Short-circuiting existential |
| `Sink.forall(p)` | `Boolean` | Short-circuiting universal |
| `Sink.find(p)` | `Option[A]` | First matching element |
| `Sink.sumInt` | `Long` | Sum of Ints (zero-boxing) |
| `Sink.sumLong` | `Long` | Sum of Longs (zero-boxing) |
| `Sink.sumFloat` | `Double` | Sum of Floats (zero-boxing) |
| `Sink.sumDouble` | `Double` | Sum of Doubles (zero-boxing) |
| `Sink.fromOutputStream(os)` | `Unit` | Writes bytes to an `OutputStream` |
| `Sink.fromJavaWriter(w)` | `Unit` | Writes chars to a `java.io.Writer` |
| `Sink.fail(e)` | `Nothing` | Fails immediately |
| `Sink.create(f)` | `Z` | Custom sink from `Reader[A] => Z` |

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
val result2 = Stream(42, -5, 100, 0).via(normalize).runCollect
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

Built-in pipeline factories:

| Factory | Description |
|---|---|
| `Pipeline.map(f)` | Transform each element |
| `Pipeline.filter(p)` | Keep elements matching predicate |
| `Pipeline.collect(pf)` | Partial function -- filter + map |
| `Pipeline.take(n)` | Keep first n elements |
| `Pipeline.drop(n)` | Skip first n elements |
| `Pipeline.identity` | Pass-through (useful as a base for composition) |

---

### 4) `Reader[A]` -- low-level pull source

`Reader[+Elem]` is the low-level, pull-based source that backs every stream. Most users will never interact with `Reader` directly; it is the compilation target when a stream runs.

The protocol is simple:

- `read(sentinel)` -- returns the next element, or `sentinel` when exhausted
- `close()` -- signal the consumer is done
- `isClosed` -- check whether the reader has been closed

For primitive types, specialized methods avoid boxing:

- `readInt(sentinel: Long): Long`
- `readLong(sentinel: Long): Long`
- `readFloat(sentinel: Double): Double`
- `readDouble(sentinel: Double): Double`

You interact with `Reader` in two situations:

1. **Custom sources** -- create a stream from a `Reader` via `Stream.fromReader`
2. **Manual pull** -- open a stream for element-by-element control via `stream.start`

```scala

Scope.global.scoped { scope =>

  // Open a stream for manual pulling
  val reader: $[Reader[Int]] = Stream.range(1, 6).start(using scope)

  $(reader) { r =>
    var v = r.read(-1)
    while v != -1 do
      println(v)   // prints 1, 2, 3, 4, 5
      v = r.read(-1)
  }
  // reader is closed automatically when scope exits
}
```

---

### 5) Error handling

Streams distinguish between two kinds of failures:

- **Typed errors** (`E`) -- domain errors you expect and handle. These appear as `Left` in the `Either` result.
- **Defects** (`Throwable`) -- unexpected exceptions. These propagate as thrown exceptions, bypassing the `Either` channel.

```scala
// Create a failing stream
val failing: Stream[String, Int] =
  Stream(1, 2, 3) ++ Stream.fail("oops") ++ Stream(4, 5)

// catchAll: recover from typed errors
val recovered: Stream[Nothing, Int] =
  failing.catchAll(_ => Stream(99))

recovered.runCollect  // Right(Chunk(1, 2, 3, 99))
```

Error handling operators:

| Operator | Description |
|---|---|
| `catchAll(f: E => Stream[E2, A])` | Recover from all typed errors |
| `catchDefect(pf: PartialFunction[Throwable, Stream[E1, A]])` | Recover from matching defects |
| `mapError(f: E => E2)` | Transform the error type |
| `orElse(that)` / `\|\|(that)` | Fall back to another stream on error |

---

### 6) Resource safety

Streams integrate with `zio.blocks.scope.Scope` for deterministic finalization. Several constructors guarantee that acquired resources are released when the stream closes, whether it completes normally, short-circuits, or fails.

#### `fromAcquireRelease`

The primary resource-safe constructor. Acquires a resource, uses it to produce a stream, and guarantees the release function runs on close:

```scala

val lines: Stream[Nothing, String] =
  Stream.fromAcquireRelease(
    acquire = new BufferedReader(new FileReader("data.txt")),
    release = _.close()
  ) { reader =>
    Stream.unfold(()) { _ =>
      Option(reader.readLine()).map(line => (line, ()))
    }
  }

// The BufferedReader is closed when the stream finishes,
// even if the consumer takes only a few lines
lines.take(5).runCollect
```

If the resource is `AutoCloseable`, the release function defaults to calling `close()`:

```scala
val lines: Stream[Nothing, String] =
  Stream.fromAcquireRelease(
    acquire = new BufferedReader(new FileReader("data.txt"))
  ) { reader =>
    Stream.unfold(()) { _ =>
      Option(reader.readLine()).map(line => (line, ()))
    }
  }
```

#### `fromResource`

Integrates with `zio.blocks.scope.Resource` directly:

```scala

val resource: Resource[BufferedReader] =
  Resource.fromAutoCloseable(new BufferedReader(new FileReader("data.txt")))

val lines: Stream[Nothing, String] =
  Stream.fromResource(resource) { reader =>
    Stream.unfold(()) { _ =>
      Option(reader.readLine()).map(line => (line, ()))
    }
  }
```

#### `ensuring` and `defer`

For attaching finalizers to existing streams:

```scala
// ensuring: run a finalizer when the stream closes
val withCleanup: Stream[Nothing, Int] =
  Stream.range(1, 11).ensuring(println("stream closed"))

// defer: register a release action (runs on close, not on construction)
val withDefer: Stream[Nothing, Int] =
  Stream.defer(println("cleanup")) ++ Stream.range(1, 6)
```

#### `start` with `Scope`

For manual pull-based consumption with scope-managed lifetime:

```scala

Scope.global.scoped { scope =>

  val reader = Stream.range(1, 100).start(using scope)
  // reader is automatically closed when scope exits
}
```

---

## Usage examples

### Creating streams

```scala

// From explicit elements
Stream(1, 2, 3)                              // Stream[Nothing, Int]
Stream("a", "b", "c")                        // Stream[Nothing, String]

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
Stream.die(new Exception("defect"))          // throws on evaluation

// Generators
Stream.repeat(1)                             // infinite stream of 1s
Stream.iterate(1)(_ * 2)                     // 1, 2, 4, 8, 16, ...
Stream.repeatThunk(scala.util.Random.nextInt(100))  // infinite random ints
Stream.unfold(0)(n =>                        // 0, 1, 2, ..., 9
  if n < 10 then Some((n, n + 1)) else None
)

// Side-effects
Stream.eval(println("hello"))               // prints, emits nothing
Stream.attempt(someFallibleCall())           // captures exceptions as typed errors
Stream.attemptEval(riskyEffect())            // same, for Unit-returning effects

// Deferred construction (useful for recursion)
Stream.suspend(expensiveStreamBuilder())

// I/O sources (auto-closing)
Stream.fromInputStream(inputStream)          // Stream[IOException, Int] (bytes as 0-255, auto-closes)
Stream.fromJavaReader(javaReader)            // Stream[IOException, Char] (auto-closes)

// I/O sources (borrowing -- caller manages lifetime)
Stream.fromInputStreamUnmanaged(inputStream) // Stream[IOException, Int] (does NOT close)
Stream.fromJavaReaderUnmanaged(javaReader)   // Stream[IOException, Char] (does NOT close)
```

---

### Transforming streams

```scala
val s = Stream.range(1, 21) // 1 to 20

// map: transform each element
s.map(_ * 2)                                 // 2, 4, 6, ..., 40

// filter: keep matching elements
s.filter(_ % 3 == 0)                         // 3, 6, 9, 12, 15, 18

// flatMap: expand each element into a sub-stream
s.flatMap(n => Stream(n, n * 10))            // 1, 10, 2, 20, 3, 30, ...

// collect: partial function (filter + map)
s.collect { case n if n % 2 == 0 => n / 2 } // 1, 2, 3, ..., 10

// take / drop / takeWhile
s.take(5)                                    // 1, 2, 3, 4, 5
s.drop(15)                                   // 16, 17, 18, 19, 20
s.takeWhile(_ < 8)                           // 1, 2, 3, 4, 5, 6, 7

// scan: running accumulator (emits initial value + one value per element)
Stream(1, 2, 3, 4).scan(0)(_ + _)           // 0, 1, 3, 6, 10

// mapAccum: stateful transformation
s.mapAccum(0) { (acc, n) =>
  val newAcc = acc + n
  (newAcc, newAcc)
}
// running sum: 1, 3, 6, 10, 15, ...

// grouped: collect into fixed-size chunks
Stream.range(1, 11).grouped(3)
// Chunk(1,2,3), Chunk(4,5,6), Chunk(7,8,9), Chunk(10)

// sliding: overlapping windows
Stream.range(1, 7).sliding(3, 1)
// Chunk(1,2,3), Chunk(2,3,4), Chunk(3,4,5), Chunk(4,5,6)

// intersperse: insert separator between elements
Stream("a", "b", "c").intersperse(",")       // "a", ",", "b", ",", "c"

// distinct / distinctBy: deduplication
Stream(1, 2, 2, 3, 1, 3).distinct            // 1, 2, 3
Stream("ab", "cd", "ae").distinctBy(_.head)   // "ab", "cd"

// zipWithIndex: pair elements with their 0-based index
Stream("a", "b", "c").zipWithIndex
// ("a", 0L), ("b", 1L), ("c", 2L)

// tapEach: side-effect without changing elements
s.tapEach(n => println(s"processing $n"))

// concat: sequence two streams
Stream(1, 2) ++ Stream(3, 4)                // 1, 2, 3, 4

// repeated: restart on completion
Stream(1, 2, 3).repeated.take(8)             // 1, 2, 3, 1, 2, 3, 1, 2

// via: apply a Pipeline
s.via(Pipeline.filter[Int](_ > 10))          // 11, 12, ..., 20
```

---

### Zipping streams with `&&`

The `&&` operator zips two streams element-by-element into tuples. The resulting stream ends when either input is exhausted.

```scala
val names: Stream[Nothing, String] = Stream("Alice", "Bob", "Charlie")
val ages:  Stream[Nothing, Int]    = Stream(30, 25, 35)
val ids:   Stream[Nothing, Long]   = Stream(1L, 2L, 3L)

// Two-way zip
val pairs: Stream[Nothing, (String, Int)] = names && ages
pairs.runCollect // Right(Chunk(("Alice", 30), ("Bob", 25), ("Charlie", 35)))

// Three-way zip -- tuples flatten automatically
val triples: Stream[Nothing, (String, Int, Long)] = names && ages && ids
triples.runCollect // Right(Chunk(("Alice", 30, 1L), ("Bob", 25, 2L), ("Charlie", 35, 3L)))
```

When the error types differ, they widen via union:

```scala
val s1: Stream[String, Int]  = Stream(1, 2, 3)
val s2: Stream[IOException, Int] = Stream(4, 5, 6)
val zipped: Stream[String | IOException, (Int, Int)] = s1 && s2
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

// Compare: in fs2 or ZIO Streams, every Int would be boxed to java.lang.Integer
// at each pipeline stage boundary.
```

This matters most for numeric workloads -- data processing, statistics, encoding/decoding -- where millions of elements flow through multi-stage pipelines. The benchmark results above reflect this advantage directly.

---

### Consuming streams

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

```scala
// Typed error: appears in Either
val result = Stream.fail("not found").runCollect
// result: Left("not found")

// Recover and continue
val safe =
  Stream(1, 2) ++ Stream.fail("oops") ++ Stream(3)
val recovered = safe.catchAll(_ => Stream(99)).runCollect
// Right(Chunk(1, 2, 99))

// Transform error type
val mapped =
  Stream.fail("bad input")
    .mapError(msg => new IllegalArgumentException(msg))
// Stream[IllegalArgumentException, Nothing]

// Fallback stream
val primary: Stream[String, Int] = Stream.fail("down")
val backup:  Stream[String, Int] = Stream(1, 2, 3)
val result2 = (primary || backup).runCollect
// Right(Chunk(1, 2, 3))

// Catch defects (unexpected exceptions)
val risky: Stream[Nothing, Int] =
  Stream(1, 2, 3).map { n =>
    if n == 2 then throw new ArithmeticException("boom")
    else n
  }

val handled = risky.catchDefect {
  case _: ArithmeticException => Stream(0)
}.runCollect
// Right(Chunk(1, 0))
```

---

### Resource safety patterns

```scala

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

// Write to a ByteBuffer
val outBuf = ByteBuffer.allocate(1024)
Stream.fromInputStream(inputStream).run(NioSinks.fromByteBuffer(outBuf))

// Typed buffer sinks (zero-boxing)
Stream.range(1, 5).run(NioSinks.fromByteBufferInt(outBuf))
// Also: fromByteBufferLong, fromByteBufferFloat, fromByteBufferDouble

// Write to a WritableByteChannel (buffered)
val outCh = FileChannel.open(
  Paths.get("output.bin"),
  StandardOpenOption.WRITE, StandardOpenOption.CREATE
)
Stream.fromInputStream(inputStream).run(NioSinks.fromChannel(outCh))
outCh.close()
```

---

### Pipeline composition

```scala

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
Stream("10", "abc", "-3", "7", "0", "25")
  .via(fullPipeline)
  .runCollect
// Right(Chunk(20, 14, 50))

// Apply to a sink (pre-process the sink's input)
val sumPositiveDoubled: Sink[Nothing, String, Long] =
  fullPipeline.andThenSink(Sink.sumInt)

Stream("10", "abc", "-3", "7", "0", "25")
  .run(sumPositiveDoubled)
// Right(84L)
```

---

## API reference

### `Stream[+E, +A]`

#### Constructors

```scala
object Stream:
  def apply[A](as: A*): Stream[Nothing, A]
  val empty: Stream[Nothing, Nothing]
  def succeed[A](a: A): Stream[Nothing, A]              // also specialized for primitives
  def fail[E](error: E): Stream[E, Nothing]
  def die(t: Throwable): Stream[Nothing, Nothing]

  def fromChunk[A](chunk: Chunk[A]): Stream[Nothing, A]
  def fromIterable[A](it: Iterable[A]): Stream[Nothing, A]
  def fromIterator[A](it: => Iterator[A]): Stream[Nothing, A]
  def range(from: Int, until: Int): Stream[Nothing, Int]
  def fromRange(range: Range): Stream[Nothing, Int]

  def repeat[A](a: A): Stream[Nothing, A]               // infinite
  def iterate[A](init: A)(f: A => A): Stream[Nothing, A] // infinite: init, f(init), f(f(init)), ...
  def repeatThunk[A](thunk: => A): Stream[Nothing, A]    // infinite: thunk() per element
  def unfold[S, A](s: S)(f: S => Option[(A, S)]): Stream[Nothing, A]

  def eval(f: => Any): Stream[Nothing, Nothing]          // side-effect, no output
  def attempt[A](f: => A): Stream[Throwable, A]          // captures exceptions
  def attemptEval(f: => Any): Stream[Throwable, Nothing]
  def suspend[E, A](stream: => Stream[E, A]): Stream[E, A]
  def defer(f: => Unit): Stream[Nothing, Nothing]        // register finalizer

  def fromInputStream(is: => InputStream): Stream[IOException, Int]           // auto-closes
  def fromInputStreamUnmanaged(is: InputStream): Stream[IOException, Int]     // borrowing
  def fromJavaReader(r: => java.io.Reader): Stream[IOException, Char]         // auto-closes
  def fromJavaReaderUnmanaged(r: java.io.Reader): Stream[IOException, Char]   // borrowing
  def fromReader[E, A](mkReader: => Reader[A]): Stream[E, A]

  def fromAcquireRelease[R, E, A](acquire: => R, release: R => Unit)(use: R => Stream[E, A]): Stream[E, A]
  def fromResource[R, E, A](resource: Resource[R])(use: R => Stream[E, A]): Stream[E, A]
  def flattenAll[E, A](streams: Stream[E, Stream[E, A]]): Stream[E, A]
```

#### Transformations (return `Stream`)

```scala
abstract class Stream[+E, +A]:
  def map[B](f: A => B): Stream[E, B]
  def flatMap[E2, B](f: A => Stream[E2, B]): Stream[E | E2, B]
  def filter(pred: A => Boolean): Stream[E, A]
  def collect[B](pf: PartialFunction[A, B]): Stream[E, B]
  def scan[S](init: S)(f: (S, A) => S): Stream[E, S]
  def mapAccum[S, B](init: S)(f: (S, A) => (S, B)): Stream[E, B]
  def tapEach(f: A => Unit): Stream[E, A]

  def take(n: Long): Stream[E, A]
  def drop(n: Long): Stream[E, A]
  def takeWhile(pred: A => Boolean): Stream[E, A]

  def grouped(n: Int): Stream[E, Chunk[A]]
  def sliding(size: Int, step: Int): Stream[E, Chunk[A]]
  def intersperse[A1 >: A](sep: A1): Stream[E, A1]
  def distinct: Stream[E, A]
  def distinctBy[B](f: A => B): Stream[E, A]
  def zipWithIndex: Stream[E, (A, Long)]

  def concat[E2, A2](that: Stream[E2, A2]): Stream[E | E2, A | A2]  // alias: ++
  def &&[E2, B](that: Stream[E2, B]): Stream[E | E2, (A, B)]        // zip with tuple flattening
  def repeated: Stream[E, A]

  def catchAll[E2, A1](f: E => Stream[E2, A1]): Stream[E2, A | A1]
  def catchDefect[E1, A1](f: PartialFunction[Throwable, Stream[E1, A1]]): Stream[E | E1, A | A1]
  def mapError[E2](f: E => E2): Stream[E2, A]
  def orElse[E2, A1](that: => Stream[E2, A1]): Stream[E2, A | A1]   // alias: ||

  def ensuring(finalizer: => Unit): Stream[E, A]
  def via[B](pipe: Pipeline[A, B]): Stream[E, B]

  def render: String                                      // human-readable pipeline description
  override def toString: String                           // alias for render
```

#### Terminal operations (return `Either[E, Z]`)

```scala
abstract class Stream[+E, +A]:
  def run[E2 >: E, Z](sink: Sink[E2, A, Z]): Either[E2, Z]
  def runCollect: Either[E, Chunk[A]]
  def runDrain: Either[E, Unit]
  def runFold[Z](z: Z)(f: (Z, A) => Z): Either[E, Z]    // also specialized for Int, Long, Double
  def runForeach(f: A => Unit): Either[E, Unit]
  def foreach(f: A => Unit): Either[E, Unit]              // alias

  def head: Either[E, Option[A]]
  def last: Either[E, Option[A]]
  def count: Either[E, Long]
  def exists(pred: A => Boolean): Either[E, Boolean]
  def forall(pred: A => Boolean): Either[E, Boolean]
  def find(pred: A => Boolean): Either[E, Option[A]]

  def start(using scope: Scope): scope.$[Reader[A]]      // manual pull
```

---

### `Sink[+E, -A, +Z]`

```scala
abstract class Sink[+E, -A, +Z]:
  def contramap[A2](g: A2 => A): Sink[E, A2, Z]
  def map[Z2](f: Z => Z2): Sink[E, A, Z2]
  def mapError[E2](f: E => E2): Sink[E2, A, Z]

object Sink:
  def collectAll[A]: Sink[Nothing, A, Chunk[A]]
  val drain: Sink[Nothing, Any, Unit]
  val count: Sink[Nothing, Any, Long]
  def foldLeft[A, Z](z: Z)(f: (Z, A) => Z): Sink[Nothing, A, Z]
  def foreach[A](f: A => Unit): Sink[Nothing, A, Unit]
  def head[A]: Sink[Nothing, A, Option[A]]
  def last[A]: Sink[Nothing, A, Option[A]]
  def take[A](n: Int): Sink[Nothing, A, Chunk[A]]
  def exists[A](pred: A => Boolean): Sink[Nothing, A, Boolean]
  def forall[A](pred: A => Boolean): Sink[Nothing, A, Boolean]
  def find[A](pred: A => Boolean): Sink[Nothing, A, Option[A]]
  val sumInt: Sink[Nothing, Int, Long]
  val sumLong: Sink[Nothing, Long, Long]
  val sumFloat: Sink[Nothing, Float, Double]
  val sumDouble: Sink[Nothing, Double, Double]
  def fromOutputStream(os: OutputStream): Sink[Nothing, Byte, Unit]
  def fromJavaWriter(w: java.io.Writer): Sink[Nothing, Char, Unit]
  def fail[E](e: E): Sink[E, Any, Nothing]
  def create[E, A, Z](f: Reader[A] => Z): Sink[E, A, Z]
```

---

### `Pipeline[-In, +Out]`

```scala
abstract class Pipeline[-In, +Out]:
  def andThen[C](that: Pipeline[Out, C]): Pipeline[In, C]
  def andThenSink[E, Z](sink: Sink[E, Out, Z]): Sink[E, In, Z]
  def applyToStream[E](stream: Stream[E, In]): Stream[E, Out]
  def applyToSink[E, Z](sink: Sink[E, Out, Z]): Sink[E, In, Z]

object Pipeline:
  def map[A, B](f: A => B): Pipeline[A, B]
  def filter[A](pred: A => Boolean): Pipeline[A, A]
  def collect[A, B](pf: PartialFunction[A, B]): Pipeline[A, B]
  def take[A](n: Long): Pipeline[A, A]
  def drop[A](n: Long): Pipeline[A, A]
  def identity[A]: Pipeline[A, A]
```

---

### `NioStreams` (JVM only)

```scala
object NioStreams:
  def fromByteBuffer(buf: ByteBuffer): Stream[Nothing, Byte]
  def fromByteBufferInt(buf: ByteBuffer): Stream[Nothing, Int]
  def fromByteBufferLong(buf: ByteBuffer): Stream[Nothing, Long]
  def fromByteBufferFloat(buf: ByteBuffer): Stream[Nothing, Float]
  def fromByteBufferDouble(buf: ByteBuffer): Stream[Nothing, Double]
  def fromChannel(ch: => ReadableByteChannel, bufSize: Int = 8192): Stream[IOException, Byte]          // auto-closes
  def fromChannelUnmanaged(ch: ReadableByteChannel, bufSize: Int = 8192): Stream[IOException, Byte]    // borrowing
```

### `NioSinks` (JVM only)

```scala
object NioSinks:
  def fromByteBuffer(buf: ByteBuffer): Sink[Nothing, Byte, Unit]
  def fromByteBufferInt(buf: ByteBuffer): Sink[Nothing, Int, Unit]
  def fromByteBufferLong(buf: ByteBuffer): Sink[Nothing, Long, Unit]
  def fromByteBufferFloat(buf: ByteBuffer): Sink[Nothing, Float, Unit]
  def fromByteBufferDouble(buf: ByteBuffer): Sink[Nothing, Double, Unit]
  def fromChannel(ch: WritableByteChannel, bufSize: Int = 8192): Sink[Nothing, Byte, Unit]
```

---

## Practical guidance

- **Start with `Stream` constructors and terminal operations.** You can get very far with `Stream.range`, `Stream.fromIterable`, `.map`, `.filter`, and `.runCollect`.
- **Use `Either` pattern matching** to handle the result: `Right(value)` for success, `Left(error)` for typed failures.
- **Prefer `Stream.fromAcquireRelease`** when wrapping resources (files, connections, etc.) over manual try/finally. It guarantees cleanup even on early termination via `take`, `head`, or error.
- **Use the auto-closing I/O constructors** (`fromInputStream`, `fromJavaReader`, `NioStreams.fromChannel`) by default. Only use the `Unmanaged` variants when you need to borrow a resource whose lifetime is managed elsewhere.
- **Use `Pipeline`** when you have a transformation you want to reuse across multiple streams or apply to sinks. Pipelines have two type parameters: `Pipeline[In, Out]`.
- **Use `&&` for zipping** instead of manual `zip` calls. Tuples flatten automatically for three or more streams: `a && b && c` produces `(A, B, C)` not `((A, B), C)`.
- **Leverage primitive specialization** for numeric workloads. Streams of `Int`, `Long`, `Float`, and `Double` avoid boxing automatically; use `Sink.sumInt`, `Sink.sumLong`, `runFold(0)(_ + _)`, etc. for zero-allocation folds.
- **Use `scan` for running accumulators**, `grouped` for batching, and `sliding` for windowed computations.
- **Use `render`/`toString`** to inspect pipeline structure during debugging -- it shows each transformation stage without executing the stream.
- **Use `Sink.create`** as an escape hatch when none of the built-in sinks fit. It gives you direct access to the `Reader` for custom consumption logic.
- **Use `NioStreams` / `NioSinks`** on the JVM for efficient NIO buffer and channel integration.
- **Avoid holding references** to a `Reader` obtained via `start` outside its `Scope`. The scope guarantees cleanup; escaping the reader defeats that guarantee.
- **`suspend`** is your friend for recursive or self-referential stream definitions, preventing stack overflow during construction.
- **Typed errors vs. defects**: use `Stream.fail` for expected domain errors and `Stream.die` for programmer errors. Use `catchAll` for the former, `catchDefect` for the latter.
