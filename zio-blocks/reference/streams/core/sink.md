# Sink

> The stream consumer: its dual synchronous and asynchronous drains, the three construction factories, and the async aggregation and transformation combinators.

`Sink[+E, -A, +Z]` is a **stream consumer** that reads elements of type `A` and produces a result of type `Z`, potentially failing with an error of type `E`. Every sink has synchronous and asynchronous drain paths: use cross-platform `Stream.runAsync`, or the JVM-only blocking `Stream.run`.

`Sink`:
- Is covariant in `E` (error) and `Z` (result) — these are outputs
- Is contravariant in `A` (input) — a `Sink[_, Any, _]` accepts any element type
- Dispatches on the reader's physical primitive lane, so element pulls avoid boxing
- Provides `Sink#contramap`, `Sink#map`, and `Sink#mapError` for composable transformations

Built-in sinks implement both drain paths. Asynchronous constructors include `existsAsync`, `findAsync`, `forallAsync`, `foreachAsync`, and `foldLeftAsync`; callbacks are sequential and back-pressured. The result/error combinators `contramapAsync`, `mapAsync`, and `mapErrorAsync` likewise select the native async drain when used by `runAsync`. On the JVM, driving such a sink through a plain blocking terminal blocks while awaiting its async drain; Scala.js has no blocking terminal.

Here is the structural shape of the `Sink` type:

```scala
abstract class Sink[+E, -A, +Z] {
  def contramap[A0 <: A, A2](g: A2 => A0)(implicit jtA0: JvmType.Infer[A0]): Sink[E, A2, Z]
  def contramapAsync[A0 <: A, A2](g: A2 => Async[A0])(implicit jtA0: JvmType.Infer[A0]): Sink[E, A2, Z]
  def map[Z2](f: Z => Z2): Sink[E, A, Z2]
  def mapAsync[Z2](f: Z => Async[Z2]): Sink[E, A, Z2]
  def mapError[E2](f: E => E2)(implicit isNothing: Sink.IsNothing[E]): Sink[E2, A, Z]
  def mapErrorAsync[E2](f: E => Async[E2])(implicit isNothing: Sink.IsNothing[E]): Sink[E2, A, Z]
}
```

## Overview

Sink is the terminal piece in the streaming architecture. A [Stream](./stream.md) describes *what* to produce, a [Pipeline](./pipeline.md) describes *how* to transform, and a Sink describes *how to consume*:

```
┌──────────────┐     ┌──────────────────┐     ┌──────────────┐
│ Stream[E, A] │ ──→ │ Pipeline[A, B]   │ ──→ │ Sink[E, B, Z]│
└──────────────┘     └──────────────────┘     └──────────────┘
                                                      │
                                              ┌───────▼──────┐
                                              │ Either[E, Z] │
                                              └──────────────┘
```

When you call `stream.run(sink)`:
1. The stream compiles into a `Reader` (a low-level pull-based source)
2. The sink's internal `Sink#drain` method pulls elements in a tight loop until end-of-stream
3. On success, the result wraps in `Right(z)`
4. Typed errors (`E`) surface as `Left(e)`, while untyped defects propagate as exceptions
5. The reader's `close()` runs after the drain whether it succeeded or threw, and a failure from `close()` is attached to the drain's own error rather than replacing it, ensuring resource safety

### Physical Input Lanes and Ownership

A sink consumes either kind of reader. `Reader` is not one type with a mode flag: it splits into `Reader.SyncReader[A]`, whose pulls return values, and `Reader.AsyncReader[A]`, whose pulls return [`Async`](../../async.md) values. [The reader union](../primitives/reader.md#the-reader-union) describes the split from the reader's side. From the sink's side, the consequence is that every sink is drained through one of two entry points, and the terminal decides which — the next section covers that choice.

Whichever kind arrives, a sink discovers its input representation from the materialized reader's `jvmType`, not from the sink's contravariant static input type. Generic sinks dispatch once per drain and then pull `Boolean`, `Byte`, `Char`, `Short`, `Int`, `Long`, `Float`, and `Double` through library-internal physical pulls. The synchronous drain uses `readBooleanPhysical`, `readBytePhysical`, `readCharPhysical`, `readShortPhysical`, `readIntPhysical`, `readLongsPhysical`, `readFloatPhysical`, and `readDoublesPhysical`, respectively; the asynchronous drain differs on two lanes, pulling `Byte` through `readBytesPhysical` and `Float` through `readFloatsPhysical`. These are the internal counterparts of the public `readBoolean`, `readByte`, `readChar`, `readShort`, `readInt`, `readLongs`, `readFloat`, and `readDoubles`, which a generic sink cannot call because all but `readByte` demand `Elem <:< X` evidence it has no way to supply. Reference inputs use generic `read`. In particular, the four small primitive lanes do not share the `Int` pull.

`Long` and `Double` use one-element primitive arrays with `readLongsPhysical(scratch, 0, 1)` and `readDoublesPhysical(scratch, 0, 1)`, and on the asynchronous drain `Byte` and `Float` use one-element arrays in the same way. The returned count carries end-of-stream status out of band, so every `Long` value and every raw `Double` bit pattern—including NaN payloads—remains valid data. The scratch storage is allocated once per drain, not once per element.

The reader owns this physical representation. Consequently, widening a specialized stream (for example, from `Stream[Nothing, Int]` to `Stream[Nothing, AnyVal]`) does not erase its `Int` lane before it reaches a sink. A sink adapter preserves the wrapped reader's lane and forwards every exact pull method. It also preserves ownership: the run terminal owns and closes the materialized reader; a sink or sink adapter does not independently close it. External destinations supplied to I/O sinks remain caller-owned unless a constructor explicitly says otherwise.

## Consuming Asynchronous Readers

Every sink carries two drains, one per reader kind:

```scala
abstract class Sink[+E, -A, +Z] {
  private[streams] def drain(reader: Reader.SyncReader[_]): Z
  private[streams] def drain(reader: Reader.AsyncReader[_]): Async[Z]
}
```

They are an overload on the argument type, not a runtime branch inside one method, so the selection is a static one made where the reader is known: at the terminal. Both are `private[streams]`, which means you never call either yourself. The terminal you already chose called one of them for you:

```
┌────────────────────────────────────────────────────────────────────┐
│      Stream#run  (JVM only)         Stream#runAsync  (JVM and JS)  │
│                 │ materializes                    │ materializes   │
│                 ▼                                 ▼                │
│       Reader.SyncReader[A]              Reader.AsyncReader[A]      │
│                 │ is handed to                    │ is handed to   │
│                 ▼                                 ▼                │
│       drain(SyncReader): Z          drain(AsyncReader): Async[Z]   │
│                 │ returns                         │ returns        │
│                 ▼                                 ▼                │
│           Either[E, Z]                   Async[Either[E, Z]]       │
└────────────────────────────────────────────────────────────────────┘
```

The asynchronous drain is a native one, not an adapter: `Stream#runAsync` hands the sink a `Reader.AsyncReader` and never converts it to a blocking reader first, so no thread is parked waiting for a pull. [Async Terminals](../execution-and-compatibility/async-execution.md#async-terminals) covers the terminal family that takes this path, and [The `Async[Either[E, Z]]` convention](../execution-and-compatibility/async-execution.md#the-asynceithere-z-convention) explains why the typed error stays inside the `Either`.

Every built-in sink implements both drains, so nothing on this page is available on only one path. A third member, `drainAsync(reader: Reader.SyncReader[_]): Async[Z]`, is likewise `private[streams]`; it presents a synchronous reader through the asynchronous contract by calling `reader.toAsync`, and exists for internal stages that must expose an asynchronous face over synchronous input.

### Choosing a Factory

Because a `Sink` has two abstract drains and both are `private[streams]`, code outside `zio.blocks.streams` cannot implement them — subclassing `Sink` directly is not a supported route, and the compiler will not let you complete the class. Build custom sinks from one of the three factories instead:

```scala
object Sink {
  // JVM and Scala.js
  def createAsync[E, A, Z](f: Reader.AsyncReader[A] => Async[Z]): Sink[E, A, Z]
  def createBoth[E, A, Z](
    sync: Reader.SyncReader[A] => Z,
    async: Reader.AsyncReader[A] => Async[Z]
  ): Sink[E, A, Z]

  // JVM only — absent from the Scala.js companion entirely
  def create[E, A, Z](f: Reader.SyncReader[A] => Z): Sink[E, A, Z]
}
```

Each factory fills in the drain you did not write:

| Factory            | Callbacks you write               | Platforms        | Reach for it when                                                    |
|--------------------|-----------------------------------|------------------|----------------------------------------------------------------------|
| `Sink.createAsync` | one asynchronous                  | JVM and Scala.js | one implementation is enough, and awaiting per element is acceptable |
| `Sink.createBoth`  | one synchronous, one asynchronous | JVM and Scala.js | the synchronous path can be cheaper than the asynchronous one        |
| `Sink.create`      | one synchronous                   | JVM only         | the sink is JVM-only anyway and its callback wants a blocking reader |

`Sink.createAsync` writes the synchronous drain for you by awaiting the asynchronous one at a JVM blocking terminal. `Sink.create` writes the asynchronous drain for you by deferring the blocking callback into a cancellable effect, where cancellation closes the reader — the callback still runs inline on whichever thread drives that effect, so an asynchronous terminal driving a `Sink.create` sink blocks that thread for the duration of the callback. `Sink.createBoth` writes neither: it takes two independent callbacks and the terminal runs exactly one, so the two must agree on how much input they consume and what they produce — nothing compares their results.

:::warning[`Sink.create` does not exist on Scala.js]
It is declared in the JVM copy of `SinkCompanionPlatformSpecific` and simply absent from the Scala.js copy, so cross-built code that calls it fails to compile for the JS target rather than failing at runtime. [`Sink.create` and Custom Sinks](../execution-and-compatibility/platform-differences.md#sinkcreate-and-custom-sinks) states the platform rule.
:::

[Custom sink factories](#custom-sink-factories) shows a worked callback against the reader protocol.

## Predefined Sinks

These are value sinks (no factory arguments). They work on any element type.

### `Sink.drain` — Discard All Elements

Consumes every element and discards them. Returns `Unit`:

```scala
object Sink {
  val drain: Sink[Nothing, Any, Unit]
}
```

Use `Sink.drain` when you only care about side effects (e.g., via `Stream#tapEach`) and not the elements themselves:

```scala
import zio.blocks.streams._
import scala.collection.mutable.Buffer

val log = Buffer[String]()
// log: Buffer[String] = ArrayBuffer(
//   "Processing: 1",
//   "Processing: 2",
//   "Processing: 3"
// )
val result = Stream(1, 2, 3)
  .tapEach(x => log += s"Processing: $x")
  .run(Sink.drain)
// result: Either[Nothing, Unit] = Right(())
// result is Right(())
// log contains: ["Processing: 1", "Processing: 2", "Processing: 3"]
```

### `Sink.count` — Count Elements

Counts the total number of elements consumed. Returns `Long`:

```scala
object Sink {
  val count: Sink[Nothing, Any, Long]
}
```

Count all elements in a stream:

```scala
import zio.blocks.streams._

val result = Stream(1, 2, 3, 4, 5).run(Sink.count)
// result: Either[Nothing, Long] = Right(5L)
```

### `Sink.sumInt` / `Sink.sumLong` / `Sink.sumFloat` / `Sink.sumDouble` — Typed Numeric Sums

Returns the sum of all elements as a numeric type. Each sink accepts the corresponding primitive type:

```scala
object Sink {
  val sumInt:    Sink[Nothing, Int, Long]
  val sumLong:   Sink[Nothing, Long, Long]
  val sumFloat:  Sink[Nothing, Float, Double]
  val sumDouble: Sink[Nothing, Double, Double]
}
```

Note that `Sink.sumInt` returns `Long` (to avoid overflow) and `Sink.sumFloat` returns `Double` (to reduce rounding loss):

```scala
import zio.blocks.streams._

val intSum = Stream(1, 2, 3, 4, 5).run(Sink.sumInt)
// intSum: Either[Nothing, Long] = Right(15L)

val doubleSum = Stream(1.5, 2.5, 3.0).run(Sink.sumDouble)
// doubleSum: Either[Nothing, Double] = Right(7.0)
```

## Construction

Sinks are created using factory methods on the companion object. These methods fall into several categories based on what they do:

### Collecting

Gather elements into collections:

#### `Sink.collectAll[A]` — Collect Into a Chunk

Collects all elements into a [`Chunk[A]`](../../chunk.md):

```scala
object Sink {
  def collectAll[A]: Sink[Nothing, A, Chunk[A]]
}
```

This is the sink behind `Stream.runCollect`:

```scala
import zio.blocks.streams._

val result = Stream(1, 2, 3).run(Sink.collectAll[Int])
// result: Either[Nothing, Chunk[Int]] = Right(IndexedSeq(1, 2, 3))
```

#### `Sink.take[A]` — Collect First N Elements

Collects at most `n` elements into a `Chunk[A]`, then stops (short-circuiting the upstream):

```scala
object Sink {
  def take[A](n: Int): Sink[Nothing, A, Chunk[A]]
}
```

Collect only the first three elements from a large stream:

```scala
import zio.blocks.streams._

val result = Stream.range(0, 1000).run(Sink.take(3))
// result: Either[Nothing, Chunk[Int]] = Right(IndexedSeq(0, 1, 2))
```

### Aggregation and Search

These sinks combine elements into a single result or search for specific elements within a stream:

#### `Sink.foldLeft[A, Z]` — General Left Fold

Folds all elements using an accumulator function, starting from initial value `z`:

```scala
object Sink {
  def foldLeft[A, Z](z: Z)(f: (Z, A) => Z)(implicit jtZ: JvmType.Infer[Z]): Sink[Nothing, A, Z]
}
```

This is the most general aggregation sink:

```scala
import zio.blocks.streams._

val sum = Stream(1, 2, 3, 4).run(Sink.foldLeft(0)(_ + _))
// sum: Either[Nothing, Int] = Right(10)

val concat = Stream("a", "b", "c").run(Sink.foldLeft("")(_ + _))
// concat: Either[Nothing, String] = Right("abc")
```

#### `Sink.head[A]` — First Element

Returns the first element wrapped in `Some`, or `None` for an empty stream:

```scala
object Sink {
  def head[A]: Sink[Nothing, A, Option[A]]
}
```

Get the first element from a stream, or None if empty:

```scala
import zio.blocks.streams._

val first = Stream(10, 20, 30).run(Sink.head[Int])
// first: Either[Nothing, Option[Int]] = Right(Some(10))

val empty = Stream.empty.run(Sink.head[Int])
// empty: Either[Nothing, Option[Int]] = Right(None)
```

#### `Sink.last[A]` — Last Element

Returns the last element wrapped in `Some`, or `None` for an empty stream. Must consume all elements:

```scala
object Sink {
  def last[A]: Sink[Nothing, A, Option[A]]
}
```

Get the last element from a stream:

```scala
import zio.blocks.streams._

val result = Stream(10, 20, 30).run(Sink.last[Int])
// result: Either[Nothing, Option[Int]] = Right(Some(30))
```

#### `Sink.find[A]` — First Matching Element

Returns the first element satisfying `pred`, or `None`. Short-circuits on first match:

```scala
object Sink {
  def find[A](pred: A => Boolean): Sink[Nothing, A, Option[A]]
}
```

Find the first even number in the stream:

```scala
import zio.blocks.streams._

val found = Stream(1, 3, 4, 6).run(Sink.find[Int](_ % 2 == 0))
// found: Either[Nothing, Option[Int]] = Right(Some(4))
```

#### `Sink.exists[A]` — Any Element Matches

Returns `true` if any element satisfies `pred`. Short-circuits on first match:

```scala
object Sink {
  def exists[A](pred: A => Boolean): Sink[Nothing, A, Boolean]
}
```

Check if any element matches a condition:

```scala
import zio.blocks.streams._

val hasNegative = Stream(1, -2, 3).run(Sink.exists[Int](_ < 0))
// hasNegative: Either[Nothing, Boolean] = Right(true)
```

#### `Sink.forall[A]` — All Elements Match

Returns `true` if all elements satisfy `pred`. Short-circuits to `false` on first failure:

```scala
object Sink {
  def forall[A](pred: A => Boolean): Sink[Nothing, A, Boolean]
}
```

Test whether all elements satisfy a condition:

```scala
import zio.blocks.streams._

val allPositive = Stream(1, 2, 3).run(Sink.forall[Int](_ > 0))
// allPositive: Either[Nothing, Boolean] = Right(true)

val notAll = Stream(1, -2, 3).run(Sink.forall[Int](_ > 0))
// notAll: Either[Nothing, Boolean] = Right(false)
```

### Effectful

These sinks perform side effects during stream consumption:

#### `Sink.foreach[A]` — Apply Side Effect to Each Element

Applies `f` to every element for side effects. Returns `Unit`:

```scala
object Sink {
  def foreach[A](f: A => Unit): Sink[Nothing, A, Unit]
}
```

Print each element as it is processed:

```scala
import zio.blocks.streams._

val result = Stream(1, 2, 3).run(Sink.foreach[Int](x => println(s"Got: $x")))
// Got: 1
// Got: 2
// Got: 3
// result: Either[Nothing, Unit] = Right(())
```

### Async Aggregation and Search

Five of the aggregation, search, and effectful sinks have an `*Async` twin whose callback returns `Async` instead of a value. The twin consumes the same input and returns the same result type; only the callback's shape differs:

```scala
object Sink {
  def existsAsync[A](pred: A => Async[Boolean]): Sink[Nothing, A, Boolean]
  def findAsync[A](pred: A => Async[Boolean]): Sink[Nothing, A, Option[A]]
  def foldLeftAsync[A, Z](z: Z)(f: (Z, A) => Async[Z])(implicit jtZ: JvmType.Infer[Z]): Sink[Nothing, A, Z]
  def forallAsync[A](pred: A => Async[Boolean]): Sink[Nothing, A, Boolean]
  def foreachAsync[A](f: A => Async[Unit]): Sink[Nothing, A, Unit]
}
```

Each stops where its synchronous twin stops:

| Async sink           | Sync twin       | Result      | Stops pulling at                                             |
|----------------------|-----------------|-------------|--------------------------------------------------------------|
| `Sink.existsAsync`   | `Sink.exists`   | `Boolean`   | the first `true`; otherwise end-of-stream, returning `false` |
| `Sink.findAsync`     | `Sink.find`     | `Option[A]` | the first match; otherwise end-of-stream, returning `None`   |
| `Sink.foldLeftAsync` | `Sink.foldLeft` | `Z`         | end-of-stream                                                |
| `Sink.forallAsync`   | `Sink.forall`   | `Boolean`   | the first `false`; otherwise end-of-stream, returning `true` |
| `Sink.foreachAsync`  | `Sink.foreach`  | `Unit`      | end-of-stream                                                |

Three properties hold across all five. At most one callback effect runs at a time, in encounter order, so these are sequential and back-pressured rather than concurrent — for concurrency, put it in the stream with [`Stream#mapParAsync`](./stream.md#streammapparasync) before the sink. No effect is started for input the sink never pulls, so short-circuiting still costs nothing after the decisive element. And a callback that fails or is cancelled fails or cancels the run as a *defect*, not as a value in the sink's typed error channel `E` — all five have `E = Nothing`.

`Sink.foldLeftAsync` carries the same `JvmType.Infer[Z]` evidence as `Sink.foldLeft`. It keeps the asynchronous fold aligned with the specialized synchronous one; the semantics are identical.

```scala
import zio.blocks.async._
import zio.blocks.streams._

val readings = Stream(12, 7, 30, 21, 5)

// Sequential: one predicate effect at a time, stopping at 30.
val tooHot: Async[Either[Nothing, Boolean]] =
  readings.runAsync(Sink.existsAsync[Int](c => Async.succeed(c > 25)))

// The fold visits every element; the accumulator is threaded through Async.
val total: Async[Either[Nothing, Long]] =
  readings.runAsync(Sink.foldLeftAsync(0L)((sum: Long, c: Int) => Async.succeed(sum + c)))
```

### Failing

These sinks can be used to produce typed errors or fail under specific conditions:

#### `Sink.fail[E]` — Immediately Fail

Creates a sink that fails immediately with a typed error, without consuming any elements:

```scala
object Sink {
  def fail[E](e: E): Sink[E, Any, Nothing]
}
```

Use this in conditional sink construction:

```scala
import zio.blocks.streams._

val sink: Sink[String, Int, Long] =
  if (false) Sink.count
  else Sink.fail("not ready")
// sink: Sink[String, Int, Long] = zio.blocks.streams.Sink$$anon$7@43056e8d

val result = Stream(1, 2, 3).run(sink)
// result: Either[String, Long] = Left("not ready")
```

### I/O

Write elements to Java I/O destinations:

#### `Sink.fromOutputStream` — Write Bytes

Writes every `Byte` element to a `java.io.OutputStream`:

```scala
object Sink {
  def fromOutputStream(os: java.io.OutputStream): Sink[Nothing, Byte, Unit]
}
```
The sink does **not** close the stream when done. This is intentional: you own the stream's lifecycle, not the sink. You're responsible for closing it yourself (typically via try-with-resources or explicit `close()` calls) to flush buffers and release system resources. This design gives you flexibility to reuse the stream after the sink finishes, or to coordinate closing with other stream operations:

```scala
import zio.blocks.streams._
import java.io.ByteArrayOutputStream

val bos = new ByteArrayOutputStream()
// bos: ByteArrayOutputStream = Hi!

// Write first batch of bytes
Stream.fromChunk(zio.blocks.chunk.Chunk[Byte](72, 105)).run(Sink.fromOutputStream(bos))
// res15: Either[Nothing, Unit] = Right(())

// Write second batch to the same stream (reuse it)
Stream.fromChunk(zio.blocks.chunk.Chunk[Byte](33)).run(Sink.fromOutputStream(bos))
// res16: Either[Nothing, Unit] = Right(())

// When done writing all batches, YOU close the stream
bos.close()

// ByteArrayOutputStream ignores close(), so you can still call toByteArray()
val allBytes = bos.toByteArray()
// allBytes: Array[Byte] = Array(72, 105, 33)
// This works because ByteArrayOutputStream doesn't maintain any closeable resources
```

#### `Sink.fromJavaWriter` — Write Characters

Writes every `Char` element to a `java.io.Writer`. Does not close the writer when done — you own its lifecycle:

```scala
object Sink {
  def fromJavaWriter(w: java.io.Writer): Sink[Nothing, Char, Unit]
}
```

Write a stream of characters to a StringWriter and access the accumulated text:

```scala
import zio.blocks.streams._
import java.io.StringWriter

val writer = new StringWriter()
// writer: StringWriter = Hello World

// Write a stream of individual characters
Stream('H', 'e', 'l', 'l', 'o', ' ', 'W', 'o', 'r', 'l', 'd')
  .run(Sink.fromJavaWriter(writer))
// res19: Either[Nothing, Unit] = Right(())

// Get the final string
val result = writer.toString()
// result: String = "Hello World"
```

Like `Sink.fromOutputStream`, this sink intentionally does not close the writer. This gives you control over when to flush or close, allowing you to write multiple streams to the same writer or coordinate lifecycle with other operations.

### Custom

Advanced low-level use cases with direct reader protocol access:

#### Custom Sink Factories

`createAsync` is the cross-platform escape hatch for a native asynchronous drain. `createBoth` supplies independent native synchronous and asynchronous drains. The plain `create` factory is JVM-only because its callback consumes a blocking `SyncReader`:

```scala
object Sink {
  def createAsync[E, A, Z](f: Reader.AsyncReader[A] => Async[Z]): Sink[E, A, Z]
  def createBoth[E, A, Z](sync: Reader.SyncReader[A] => Z,
                           async: Reader.AsyncReader[A] => Async[Z]): Sink[E, A, Z]
  // JVM only
  def create[E, A, Z](f: Reader.SyncReader[A] => Z): Sink[E, A, Z]
}
```

:::note
These factories give you direct access to the reader protocol. Await asynchronous pulls sequentially and prefer built-in sinks when possible.
:::

Here's a custom sink that computes the average of all integers in a stream:

```scala
import zio.blocks.streams._
import zio.blocks.streams.io.Reader

// A custom sink that computes the average of Ints
val average = Sink.create[Nothing, Int, Double] { reader =>
  def loop(sum: Long, count: Long): (Long, Long) = {
    val v = reader.readInt(Long.MinValue)
    if (v == Long.MinValue) (sum, count)
    else {
      val newSum = sum + v
      loop(newSum, count + 1)
    }
  }
  val (sum, count) = loop(0L, 0L)
  if (count == 0) 0.0 else sum.toDouble / count
}
```

This example shows how `Sink.create` works. `readInt` widens an `Int` to `Long`, leaving `Long.MinValue` available as an out-of-domain end marker. For full-domain `Long` and `Double` inputs, do not choose a data sentinel: allocate a reusable one-element primitive array and use `readLongs` or `readDoubles`, whose returned count reports data or end-of-stream without collisions. You'd use `Sink.create` when no built-in sink provides the exact aggregation or transformation logic you need — it is powerful but requires understanding the low-level [Reader protocol](../primitives/reader.md).

## Transforming Sinks

Every sink can be transformed using these instance methods:

### `Sink#contramap[A2]` — Pre-Process Input

Transforms the input elements before they reach the sink. The sink's result and error types are unchanged:

```scala
abstract class Sink[+E, -A, +Z] {
  def contramap[A0 <: A, A2](g: A2 => A0)(implicit jtA0: JvmType.Infer[A0]): Sink[E, A2, Z]
}
```

The evidence describes the callback's **result** type `A0`, not the new sink input `A2`. The bound `A0 <: A` is variance-sound for contravariant `A`, while invariant `JvmType.Infer[A0]` lets the mapped reader advertise the callback's exact physical result lane. Primitive results therefore retain their exact lane; the `AnyRef` fallback deliberately uses the boxed reference lane. `contramapAsync` has the same evidence and representation rules for `A2 => Async[A0]`. Both adapters transform only elements requested by the wrapped sink, preserve short-circuiting, and leave reader closure to the run terminal.

`Sink#contramap` is the dual of `Sink#map`: it transforms what goes *in*, not what comes *out*:

```scala
import zio.blocks.streams._

// A sink that counts the length of strings
val totalLength: Sink[Nothing, String, Long] =
  Sink.sumInt.contramap[Int, String](_.length)
// totalLength: Sink[Nothing, String, Long] = zio.blocks.streams.Sink$Contramapped@73659487

val result = Stream("hello", "world").run(totalLength)
// result: Either[Nothing, Long] = Right(10L)
```

### `Sink#map[Z2]` — Transform Result

Transforms the result after the sink finishes draining:

```scala
abstract class Sink[+E, -A, +Z] {
  def map[Z2](f: Z => Z2): Sink[E, A, Z2]
}
```

Transform the result after draining:

```scala
import zio.blocks.streams._

val countAsString: Sink[Nothing, Any, String] =
  Sink.count.map(n => s"Total: $n elements")
// countAsString: Sink[Nothing, Any, String] = zio.blocks.streams.Sink$Mapped@3164630

val result = Stream(1, 2, 3).run(countAsString)
// result: Either[Nothing, String] = Right("Total: 3 elements")
```

### `Sink#mapError[E2]` — Transform Error

Transforms the error channel of a sink:

```scala
abstract class Sink[+E, -A, +Z] {
  def mapError[E2](f: E => E2)(implicit isNothing: Sink.IsNothing[E]): Sink[E2, A, Z]
}
```

The `IsNothing` evidence records whether `E` is `Nothing`. For an infallible sink the method returns the same sink without allocating a wrapper; otherwise it maps typed errors:

```scala
import zio.blocks.streams._

// No-op: drain never fails, so mapError is free
val mapped = Sink.drain.mapError[String](_.toString)
// At compile time: this is just a cast, no wrapper allocated

// Real mapping: fail can produce errors
val failing = Sink.fail("oops").mapError[RuntimeException](new RuntimeException(_))
```

### `Sink#contramapAsync[A2]` — Pre-Process Input Asynchronously

Applies an asynchronous function to each element before it reaches the sink:

```scala
abstract class Sink[+E, -A, +Z] {
  def contramapAsync[A0 <: A, A2](g: A2 => Async[A0])(implicit jtA0: JvmType.Infer[A0]): Sink[E, A2, Z]
}
```

The evidence is the same as `Sink#contramap`'s and targets the callback's **result** type `A0`, not the new sink input `A2`. `g` runs sequentially, one effect at a time, as the wrapped sink requests elements, so short-circuiting is unchanged and `g` is never started for input the sink does not pull. A failed or cancelled `g` fails or cancels the run as a defect and stops pulling input; it does not appear in the sink's typed error channel.

```scala
import zio.blocks.async._
import zio.blocks.streams._

// Resolve each identifier to a length before counting it
val totalLength: Sink[Nothing, String, Long] =
  Sink.sumInt.contramapAsync[Int, String](id => Async.succeed(id.length))

val result: Async[Either[Nothing, Long]] =
  Stream("hello", "world").runAsync(totalLength)
```

### `Sink#mapAsync[Z2]` — Transform Result Asynchronously

Transforms the result asynchronously after the sink finishes draining:

```scala
abstract class Sink[+E, -A, +Z] {
  def mapAsync[Z2](f: Z => Async[Z2]): Sink[E, A, Z2]
}
```

`f` runs once, after the drain completes, so it does not change input consumption. When the sink fails, `f` is never started. Failure or cancellation of `f` fails or cancels the run as a defect.

```scala
import zio.blocks.async._
import zio.blocks.streams._

val persistedCount: Sink[Nothing, Any, String] =
  Sink.count.mapAsync(n => Async.succeed(s"Total: $n elements"))

val result: Async[Either[Nothing, String]] =
  Stream(1, 2, 3).runAsync(persistedCount)
```

### `Sink#mapErrorAsync[E2]` — Transform Error Asynchronously

Transforms the typed error channel asynchronously:

```scala
abstract class Sink[+E, -A, +Z] {
  def mapErrorAsync[E2](f: E => Async[E2])(implicit isNothing: Sink.IsNothing[E]): Sink[E2, A, Z]
}
```

It carries the same `IsNothing` evidence as `Sink#mapError`: for an infallible sink (`E = Nothing`) the method returns the same sink without allocating a wrapper, and `f` can never be evaluated. Successful results, upstream stream errors, and defects pass through untransformed; only the sink's own typed error reaches `f`. Failure or cancellation of `f` is a defect.

```scala
import zio.blocks.async._
import zio.blocks.streams._

// Infallible: no wrapper is allocated and `f` can never run
val mapped = Sink.drain.mapErrorAsync[String](e => Async.succeed(e.toString))

// Fallible: the typed error is transformed before it reaches the `Left`
val failing = Sink.fail("oops").mapErrorAsync[RuntimeException](e => Async.succeed(new RuntimeException(e)))
```

## Integration with Stream

`Stream.run(sink)` is the primary entry point. ZIO Blocks also provides convenience methods on `Stream` that delegate to built-in sinks:

| Stream method          | Equivalent Sink                   |
|------------------------|-----------------------------------|
| `stream.runCollect`    | `stream.run(Sink.collectAll)`     |
| `stream.runDrain`      | `stream.run(Sink.drain)`          |
| `stream.runForeach(f)` | `stream.run(Sink.foreach(f))`     |
| `stream.runFold(z)(f)` | `stream.run(Sink.foldLeft(z)(f))` |
| `stream.count`         | `stream.run(Sink.count)`          |
| `stream.head`          | `stream.run(Sink.head)`           |
| `stream.last`          | `stream.run(Sink.last)`           |
| `stream.find(pred)`    | `stream.run(Sink.find(pred))`     |
| `stream.exists(pred)`  | `stream.run(Sink.exists(pred))`   |
| `stream.forall(pred)`  | `stream.run(Sink.forall(pred))`   |

The `runFold` method with primitive accumulator types (`Int`, `Long`, `Double`) uses specialized internal sink classes that keep the accumulator unboxed.

See [Stream — Running Streams](./stream.md#running-streams) for more details on terminal operations.

## Integration with Pipeline

A [Pipeline](./pipeline.md) can be applied to a Sink using `Pipeline#andThenSink`, producing a new Sink that pre-processes input elements through the pipeline:

```scala
import zio.blocks.streams._
import zio.blocks.chunk.Chunk

val cleanAndCollect: Sink[Nothing, String, Chunk[String]] =
  Pipeline.map[String, String](_.trim.toLowerCase)
    .andThenSink(Sink.collectAll[String])
// cleanAndCollect: Sink[Nothing, String, Chunk[String]] = zio.blocks.streams.Sink$Contramapped@6cb23b20

val result = Stream("  Hello ", " WORLD  ").run(cleanAndCollect)
// result: Either[Nothing, Chunk[String]] = Right(IndexedSeq("hello", "world"))
```

The equivalence law holds: `stream.via(pipe).run(sink) == stream.run(pipe.andThenSink(sink))`.

See [Pipeline — Applying to a Sink](./pipeline.md#applying-to-a-sink) for more details.

## JVM NIO Sinks

The `NioSinks` object (JVM-only) provides sinks for Java NIO (`java.nio`) buffers and channels. NIO offers efficient buffers and both blocking and selector-based channel APIs, but these sinks use blocking channel writes; they do not expose selector-based non-blocking output. When you're writing to network sockets, files, or other NIO-based resources, these sinks give you a convenient way to drain streams directly into NIO data structures.

Traditional Java I/O (`OutputStream`, `Writer`) blocks threads and requires manual buffering for efficiency. `NioSinks.fromChannel` also blocks, but handles buffer allocation, position management, and flushing automatically (default 8KB), while typed variants like `NioSinks.fromByteBufferInt` and `NioSinks.fromByteBufferLong` eliminate boxing overhead by writing primitives directly to buffers you provide.

Choose `NioSinks.fromChannel` when blocking channel output is acceptable and you want automatic buffering for network sockets or files. Choose typed variants when you control buffer allocation and are streaming millions of primitives where boxing would degrade performance.

Each of these sinks implements both drains, so NIO output works under the blocking `Stream#run` and under `Stream#runAsync` alike. What the asynchronous drain removes is the thread parked waiting for stream *input*; the destination writes are the same blocking `java.nio` calls on either path. `NioSinks.fromChannel` does budget its asynchronous flush loop: when a channel write makes no progress it yields instead of spinning, so a stalled channel does not monopolise the caller. [Reader](../primitives/reader.md#from-native-asynchronous-sources) covers the NIO adapters on the source side in full, and this page does not duplicate them.

Here are the available NIO sinks:

```scala
object NioSinks {
  def fromByteBuffer      (buf: ByteBuffer): Sink[Nothing, Byte,   Unit]
  def fromByteBufferInt   (buf: ByteBuffer): Sink[Nothing, Int,    Unit]
  def fromByteBufferLong  (buf: ByteBuffer): Sink[Nothing, Long,   Unit]
  def fromByteBufferFloat (buf: ByteBuffer): Sink[Nothing, Float,  Unit]
  def fromByteBufferDouble(buf: ByteBuffer): Sink[Nothing, Double, Unit]
  def fromChannel(ch: WritableByteChannel, bufSize: Int = 8192): Sink[IOException, Byte, Unit]
}
```

### From ByteBuffer Sinks

**`NioSinks.fromByteBuffer` and typed variants** — Write primitive streams directly into a pre-allocated NIO ByteBuffer:
- `NioSinks.fromByteBuffer` — writes individual `Byte` elements.
- `NioSinks.fromByteBufferInt`, `NioSinks.fromByteBufferLong`, `NioSinks.fromByteBufferFloat`, `NioSinks.fromByteBufferDouble` — write primitives directly using the buffer's native methods (`putInt`, `putLong`, etc.), one buffer write per element rather than one per byte.

Here's an example using ByteBuffer with typed primitive writes:

```scala
import zio.blocks.streams._
import zio.blocks.streams.NioSinks
import java.nio.ByteBuffer
import java.nio.ByteOrder

val buffer = ByteBuffer.allocate(32).order(ByteOrder.BIG_ENDIAN)
// buffer: ByteBuffer = java.nio.HeapByteBuffer[pos=32 lim=32 cap=32]

// Write a stream of Longs to the buffer
Stream(1L, 2L, 3L, 4L).run(NioSinks.fromByteBufferLong(buffer))
// res29: Either[Nothing, Unit] = Right(())

// After writing, rewind to read
buffer.rewind()
// res30: ByteBuffer = java.nio.HeapByteBuffer[pos=32 lim=32 cap=32]

val readBack = List(
  buffer.getLong(),
  buffer.getLong(),
  buffer.getLong(),
  buffer.getLong()
)
// readBack: List[Long] = List(1L, 2L, 3L, 4L)
```

This example allocates a 32-byte buffer (4 Longs × 8 bytes each), writes four `Long` values using `NioSinks.fromByteBufferLong` (which calls `putLong` on each element), then rewinds and reads them back to verify. The typed variant writes one 8-byte buffer operation per element, where the byte variant would require the stream to carry each byte separately.

The following example shows streaming voltage sensor readings through a calibration curve and buffering them for downstream computation. When processing sensor arrays or scientific measurements, pre-allocated buffers with typed sinks let each element be written through its exact primitive lane without boxing.

Here is the complete example:

```scala title="streams-examples/src/main/scala/sink/SinkScientificComputingExample.scala" 
package sink

import zio.blocks.streams.*
import zio.blocks.streams.NioSinks
import java.nio.ByteBuffer
import scala.math.pow

object SinkScientificComputingExample extends App {
  println("=== Batching Doubles for Scientific Computing ===\n")

  // Simulate raw sensor measurements that need calibration
  println("Scenario: Streaming voltage sensor measurements with calibration curve\n")

  val measurementCount = 10
  val bufferCapacity   = measurementCount * 8 // 8 bytes per Double

  println(s"Processing $measurementCount measurements...\n")

  // Generate raw voltage readings (0.0 to 0.09)
  val rawVoltages = (0 until measurementCount).map(i => (i * 0.01).toDouble).toList

  println("Raw measurements (voltage):")
  rawVoltages.zipWithIndex.foreach { case (v, i) =>
    println(f"  [$i] $v%.4f V")
  }
  println()

  // Process and calibrate measurements
  val buffer = processAndBufferMeasurements(measurementCount, bufferCapacity)

  println("After calibration (applied quadratic correction: V' = V × (1 + 0.05×V²)):\n")

  // Read back and display calibrated values
  buffer.rewind()
  var index = 0
  while (buffer.hasRemaining) {
    val calibrated = buffer.getDouble()
    println(f"  [$index] $calibrated%.6f V")
    index += 1
  }

  println("\n=== Pattern Use Cases ===")
  println("This pattern is used in:")
  println("  • Scientific instrumentation (analog-to-digital conversion)")
  println("  • Machine learning pipelines (sensor data → training batches)")
  println("  • Signal processing (raw signals → preprocessed data → computation)")
  println("\nKey benefits:")
  println("  • Zero-copy batch processing with DirectByteBuffer")
  println("  • Efficient numerical stream transformation")
  println("  • Memory-friendly for large datasets")

  // Process and buffer measurements using NioSinks
  def processAndBufferMeasurements(
    measurementCount: Int,
    bufferCapacity: Int
  ): ByteBuffer = {
    val buffer = ByteBuffer.allocateDirect(bufferCapacity)

    // Stream of raw voltage measurements (need calibration)
    val voltages = Stream.range(0, measurementCount).map(i => (i * 0.01).toDouble)

    // Apply calibration curve: quadratic correction
    // This simulates real sensor calibration with nonlinear response
    val calibrated = voltages.map { raw =>
      val calibrationFactor = 1.0 + (0.05 * pow(raw, 2))
      raw * calibrationFactor
    }

    // Write calibrated values directly to ByteBuffer using typed sink
    // This is much faster than element-by-element byte writing
    calibrated.run(NioSinks.fromByteBufferDouble(buffer))
    buffer.flip()
    buffer
  }
}
```

Run this example with:

```bash
sbt "streams-examples/runMain sink.SinkScientificComputingExample"
```

This use case is typical in scientific instrumentation, machine learning data preprocessing, and signal processing pipelines where you need to efficiently batch-process numerical streams into memory-efficient structures for downstream computation.

The typed sinks dispatch through their exact primitive lanes. `Long` and `Double` use collision-free bulk-count status, so `Long.MinValue`, `Long.MaxValue`, every finite or infinite `Double`, signed zero, and every raw NaN payload are written as ordinary data. There is no sentinel-value truncation restriction. The supplied buffer remains caller-owned and is not flipped, rewound, or closed by the sink. [Zero-Boxing Streams](../execution-and-compatibility/zero-boxing.md) carries the per-lane end-of-stream table this rests on.

### From Channel Sink

The **`NioSinks.fromChannel`** constructor performs buffered writes to a `WritableByteChannel` (e.g., a network socket or file channel). This is the general-purpose NIO sink: it accumulates bytes in an internal buffer of size `bufSize` (default 8192), flushes when the buffer is full, and flushes again at end-of-stream. It does not close the caller-owned channel.

It handles `IOException` as a typed error, so failures surface as `Left(IOException)` from `Stream.run`. Use this for network I/O or when you can't pre-allocate a buffer. The channel I/O is blocking—NIO's non-blocking advantage comes when using selectors across many channels, which this sink does not expose.

Suppose you're collecting metrics from thousands of sensors (temperature, pressure, timestamps) and need to write them to a file efficiently. Using `NioSinks.fromChannel` with a file's `WritableByteChannel` gives you automatic buffering and eliminates manual position management.

Here is the complete example:

```scala title="streams-examples/src/main/scala/sink/SinkTelemetryExample.scala" 
package sink

import zio.blocks.streams.*
import zio.blocks.streams.NioSinks
import java.io.RandomAccessFile
import java.nio.file.Files
import scala.util.Using

object SinkTelemetryExample extends App {
  println("=== Streaming Telemetry to File Channel ===\n")

  // Simulated sensor readings (timestamp, temperature)
  case class SensorReading(timestamp: Long, temperature: Double) {
    override def toString: String = f"[$timestamp] $temperature%.2f°C"
  }

  // Generate mock sensor data
  val sensorReadings = List(
    SensorReading(1000L, 22.5),
    SensorReading(1100L, 23.1),
    SensorReading(1200L, 22.8),
    SensorReading(1300L, 23.4),
    SensorReading(1400L, 24.0)
  )

  println("Sensor readings to write:")
  sensorReadings.foreach(r => println(s"  $r"))
  println()

  // Write telemetry to file using NioSinks.fromChannel
  val tempFile = Files.createTempFile("telemetry", ".bin")
  val filePath = tempFile.toString

  println(s"Writing to $filePath...")
  Using(new RandomAccessFile(filePath, "rw")) { file =>
    val channel = file.getChannel
    channel.truncate(0) // Clear file

    // Serialize readings into a single buffer: 8 bytes timestamp + 8 bytes temperature per reading
    val buffer = java.nio.ByteBuffer.allocate(sensorReadings.length * 16)
    sensorReadings.foreach { reading =>
      buffer.putLong(reading.timestamp)
      buffer.putDouble(reading.temperature)
    }
    buffer.flip()

    // Write all bytes to file with internal buffering (8KB chunks)
    val bytes      = buffer.array()
    val byteStream = Stream.fromChunk(zio.blocks.chunk.Chunk.fromArray(bytes))
    byteStream.run(NioSinks.fromChannel(channel, bufSize = 8192))

    println(s"✓ Wrote ${file.length()} bytes to disk")
  }.get

  // Read back and verify
  println("\nVerifying written data:")
  Using(new RandomAccessFile(filePath, "r")) { file =>
    val buf = java.nio.ByteBuffer.allocate((8 + 8) * sensorReadings.length)
    file.getChannel.read(buf)
    buf.rewind()

    var count = 0
    while (buf.remaining() >= 16) {
      val timestamp   = buf.getLong()
      val temperature = buf.getDouble()
      println(f"  [$timestamp] $temperature%.2f°C")
      count += 1
    }
    println(s"✓ Read back $count sensor readings")
  }.get

  println("\n=== Pattern Use Cases ===")
  println("This pattern is used in:")
  println("  • IoT telemetry platforms (time-series databases)")
  println("  • High-throughput logging systems")
  println("  • Sensor data aggregation pipelines")
  println("\nKey benefits:")
  println("  • Automatic buffering eliminates manual position management")
  println("  • Integrated with Stream composition (no boilerplate)")
  println("  • Type-safe error handling (IOException as Sink error type)")

  Files.delete(tempFile)
}
```

Run it with:

```bash
sbt "streams-examples/runMain sink.SinkTelemetryExample"
```

This pattern is common in high-throughput logging systems, time-series databases, and IoT platforms where you need to write streams of telemetry data to persistent storage without blocking or allocating excessively.

## Running the Examples

All code from this guide is available as runnable examples in the `streams-examples` module.

Start by cloning the repository and navigating to the project:

```bash
git clone https://github.com/zio/zio-blocks.git
cd zio-blocks
```

Run individual examples with sbt:

### Basic Usage

This example demonstrates the most commonly used built-in sinks: `Sink.drain`, `Sink.count`, `Sink.collectAll`, `Sink.head`, `Sink.last`, and `Sink.take`:

```scala title="streams-examples/src/main/scala/sink/SinkBasicUsageExample.scala" 
package sink

import zio.blocks.streams.*
import zio.sbt.ExprEval.show

object SinkBasicUsageExample extends App {
  println("=== Sink Basic Usage ===\n")

  val data = Stream(1, 2, 3, 4, 5)

  // 1. Sink.drain — discard all elements
  println("1. Sink.drain — discard all elements:")
  show(data.run(Sink.drain))

  // 2. Sink.count — count elements
  println("\n2. Sink.count — count elements:")
  show(data.run(Sink.count))

  // 3. Sink.collectAll — collect into Chunk
  println("\n3. Sink.collectAll — collect into Chunk:")
  show(data.run(Sink.collectAll[Int]))

  // 4. Sink.head — first element
  println("\n4. Sink.head — first element:")
  show(data.run(Sink.head[Int]))

  println("\n   Sink.head on empty stream:")
  show(Stream.empty.run(Sink.head[Int]))

  // 5. Sink.last — last element
  println("\n5. Sink.last — last element:")
  show(data.run(Sink.last[Int]))

  // 6. Sink.take — first n elements
  println("\n6. Sink.take — first n elements:")
  show(data.run(Sink.take(3)))

  // 7. take on a large stream (short-circuits)
  println("\n7. Sink.take short-circuits (only reads 3 of 1000):")
  show(Stream.range(0, 1000).run(Sink.take(3)))

  // 8. Combining stream operations with sinks
  println("\n8. Combining stream operations with explicit sinks:")
  val result = Stream(1, 2, 3, 4, 5)
    .filter(_ % 2 == 0)
    .run(Sink.collectAll[Int])
  show(result)

  // 9. Equivalence with convenience methods
  println("\n9. Stream convenience methods delegate to sinks:")
  show(data.runCollect == data.run(Sink.collectAll[Int]))
  show(data.count == data.run(Sink.count))
}
```

Run this example with:

```bash
sbt "streams-examples/runMain sink.SinkBasicUsageExample"
```

### Aggregation and Search

This example shows aggregation sinks (`Sink.foldLeft`, `Sink.sumInt`, `Sink.sumDouble`) and search sinks (`Sink.exists`, `Sink.forall`, `Sink.find`, `Sink.foreach`):

```scala title="streams-examples/src/main/scala/sink/SinkAggregationExample.scala" 
package sink

import zio.blocks.streams.*
import zio.sbt.ExprEval.show

object SinkAggregationExample extends App {
  println("=== Sink Aggregation and Search ===\n")

  // 1. foldLeft — general accumulation
  println("1. Sink.foldLeft — general accumulation:")
  val sum = Stream(1, 2, 3, 4, 5).run(Sink.foldLeft(0)(_ + _))
  show(sum)

  println("\n   foldLeft with string concatenation:")
  val concat = Stream("a", "b", "c").run(Sink.foldLeft("")(_ + _))
  show(concat)
  // 2. sumInt — typed numeric sum
  println("\n2. Sink.sumInt — returns Long to avoid overflow:")
  val intSum = Stream(1, 2, 3, 4, 5).run(Sink.sumInt)
  show(intSum)

  // 3. sumDouble — typed floating point sum
  println("\n3. Sink.sumDouble:")
  val doubleSum = Stream(1.5, 2.5, 3.0).run(Sink.sumDouble)
  show(doubleSum)

  // 4. exists — short-circuits on first match
  println("\n4. Sink.exists — short-circuits on first match:")
  val hasNegative = Stream(1, 2, -3, 4).run(Sink.exists[Int](_ < 0))
  show(hasNegative)

  val noNegative = Stream(1, 2, 3, 4).run(Sink.exists[Int](_ < 0))
  show(noNegative)

  // 5. forall — all elements must match
  println("\n5. Sink.forall — all elements must match:")
  val allPositive = Stream(1, 2, 3).run(Sink.forall[Int](_ > 0))
  show(allPositive)

  val notAllPositive = Stream(1, -2, 3).run(Sink.forall[Int](_ > 0))
  show(notAllPositive)

  // 6. find — first element matching predicate
  println("\n6. Sink.find — first matching element:")
  val firstEven = Stream(1, 3, 4, 6, 8).run(Sink.find[Int](_ % 2 == 0))
  show(firstEven)

  val noMatch = Stream(1, 3, 5, 7).run(Sink.find[Int](_ % 2 == 0))
  show(noMatch)

  // 7. foreach — side effects
  println("\n7. Sink.foreach — apply side effects:")
  val items  = scala.collection.mutable.Buffer[String]()
  val result = Stream("x", "y", "z").run(Sink.foreach[String](s => items += s))
  show(result)
  show(items.toList)

  // 8. Complex aggregation: combine foldLeft with map
  println("\n8. Complex aggregation — average via foldLeft + map:")
  val average = Sink
    .foldLeft[Int, (Int, Int)]((0, 0)) { case ((sum, count), x) =>
      (sum + x, count + 1)
    }
    .map { case (sum, count) =>
      if (count == 0) 0.0 else sum.toDouble / count
    }

  val avg = Stream(10, 20, 30, 40).run(average)
  show(avg)
}
```

Run this example with:

```bash
sbt "streams-examples/runMain sink.SinkAggregationExample"
```

### Transformations and Composition

This example demonstrates `Sink#contramap`, `Sink#map`, `Sink#mapError`, `Sink.fail`, `Sink.create`, and `Pipeline#andThenSink`:

```scala title="streams-examples/src/main/scala/sink/SinkTransformationExample.scala" 
package sink

import zio.blocks.streams.*
import zio.sbt.ExprEval.show

object SinkTransformationExample extends App {
  println("=== Sink Transformations and Composition ===\n")

  // 1. contramap — pre-process input
  println("1. Sink.contramap — pre-process input elements:")
  val stringLengthSum: Sink[Nothing, String, Long] =
    Sink.sumInt.contramap[Int, String](_.length)

  show(Stream("hello", "world").run(stringLengthSum))

  // 2. contramap — change element type
  println("\n2. contramap to convert types:")
  val parseInts: Sink[Nothing, String, Long] =
    Sink.sumInt.contramap[Int, String](_.toInt)

  show(Stream("10", "20", "30").run(parseInts))

  // 3. map — transform result
  println("\n3. Sink.map — transform the result:")
  val countFormatted: Sink[Nothing, Any, String] =
    Sink.count.map(n => s"Processed $n elements")

  show(Stream(1, 2, 3).run(countFormatted))

  // 4. Chaining contramap + map
  println("\n4. Chaining contramap + map:")
  val pipeline = Sink.sumInt
    .contramap[Int, String](_.length)
    .map(total => s"Total chars: $total")

  show(Stream("hi", "hello").run(pipeline))

  // 5. mapError — transform error channel
  println("\n5. Sink.mapError — transform errors:")

  sealed trait AppError
  case class ParseError(msg: String) extends AppError

  val failingSink = Sink.fail("raw error").mapError[AppError](msg => ParseError(msg))
  show(Stream(1).run(failingSink))

  // 6. fail — immediately fail
  println("\n6. Sink.fail — immediate failure:")
  show(Stream(1, 2, 3).run(Sink.fail("error")))

  // 7. Pipeline.andThenSink integration
  println("\n7. Pipeline.andThenSink — pipeline pre-processes before sink:")
  val cleanAndCollect =
    Pipeline
      .map[String, String](_.trim.toLowerCase)
      .andThenSink(Sink.collectAll[String])

  show(Stream("  Hello ", " WORLD  ").run(cleanAndCollect))

  // 8. Equivalence: via + run == andThenSink + run
  println("\n8. Equivalence law: via + run == andThenSink + run:")
  val pipe   = Pipeline.filter[Int](_ > 2).andThen(Pipeline.map[Int, Int](_ * 10))
  val source = Stream(1, 2, 3, 4, 5)

  val viaResult  = source.via(pipe).run(Sink.collectAll[Int])
  val sinkResult = source.run(pipe.andThenSink(Sink.collectAll[Int]))
  show(viaResult)
  show(sinkResult)

  // 9. Composing multiple transformations into a reusable sink
  println("\n9. Reusable composed sink:")
  case class Metric(name: String, value: Double)

  val metricSumSink: Sink[Nothing, Metric, Double] =
    Sink.foldLeft(0.0)((acc, m: Metric) => acc + m.value)

  val metrics = Stream(
    Metric("cpu", 45.0),
    Metric("cpu", 67.0),
    Metric("cpu", 23.0)
  )
  show(metrics.run(metricSumSink))

  val metricAvgSink: Sink[Nothing, Metric, Double] =
    Sink
      .foldLeft[Metric, (Double, Int)]((0.0, 0)) { case ((sum, count), m) =>
        (sum + m.value, count + 1)
      }
      .map { case (sum, count) => if (count == 0) 0.0 else sum / count }

  show(metrics.run(metricAvgSink))
}
```

Run this example with:

```bash
sbt "streams-examples/runMain sink.SinkTransformationExample"
```

### Async Sinks End to End

This example builds a custom mean sink with `Sink.createBoth` — a `while` loop for the synchronous drain, an `Async#flatMap` recursion for the asynchronous one — then drives it and `Sink.existsAsync` through `Stream#runAsync`, unwrapping each `Either` at the end. It is JVM-only, because it finishes with `.block` to turn the `Async` into a value for `main`:

```scala title="streams-examples/src/main/scala/sink/SinkAsyncExample.scala" 
package sink

import zio.blocks.async._
import zio.blocks.streams._
import zio.blocks.streams.io.Reader

/**
 * A custom sink built with `Sink.createBoth`, plus one of the `*Async`
 * combinators, both driven from the cross-platform asynchronous terminal
 * `Stream#runAsync`.
 *
 * `createBoth` supplies two independent drains. The terminal picks exactly one:
 * `runAsync` takes the asynchronous callback, and the JVM-only blocking `run`
 * takes the synchronous one. Both must agree on what they consume and what they
 * return.
 *
 * JVM only, because it ends in `.block` to turn the `Async` into a value for
 * `main`.
 */
object SinkAsyncExample {

  /**
   * Mean of an `Int` lane. The synchronous callback is a plain `while` loop
   * over `readInt`, which allocates no `Async` per element; the asynchronous
   * callback threads the same accumulator through `flatMap`.
   */
  val meanCelsius: Sink[Nothing, Int, Double] =
    Sink.createBoth[Nothing, Int, Double](
      sync = { (reader: Reader.SyncReader[Int]) =>
        var sum   = 0L
        var count = 0L
        var value = reader.readInt(Long.MinValue)
        while (value != Long.MinValue) {
          sum += value
          count += 1L
          value = reader.readInt(Long.MinValue)
        }
        mean(sum, count)
      },
      async = { (reader: Reader.AsyncReader[Int]) =>
        def loop(sum: Long, count: Long): Async[Double] =
          reader.readInt(Long.MinValue).flatMap { value =>
            if (value == Long.MinValue) Async.succeed(mean(sum, count))
            else loop(sum + value, count + 1L)
          }

        loop(0L, 0L)
      }
    )

  private def mean(sum: Long, count: Long): Double =
    if (count == 0L) 0.0 else sum.toDouble / count.toDouble

  def main(args: Array[String]): Unit = {
    val celsius: Stream[Nothing, Int] = Stream(12, 7, 30, 21, 5)

    // Nothing has run yet: both terminals are descriptions.
    val average: Async[Either[Nothing, Double]] = celsius.runAsync(meanCelsius)

    // `existsAsync` runs at most one predicate effect at a time and stops at
    // the first `true`; 30 matches, so 21 and 5 are never pulled.
    val tooHot: Async[Either[Nothing, Boolean]] =
      celsius.runAsync(Sink.existsAsync[Int](c => Async.succeed(c > 25)))

    // `.block` parks the calling thread until the value arrives. It belongs
    // here, at the edge of a JVM `main`, and nowhere else.
    report("createBoth mean", average.block)
    report("existsAsync (> 25)", tooHot.block)
  }

  private def report[E, Z](label: String, result: Either[E, Z]): Unit =
    result match {
      case Right(value) => println(s"$label -> $value")
      case Left(error)  => println(s"$label -> typed error: $error")
    }
}
```

Run this example with:

```bash
sbt "streams-examples/runMain sink.SinkAsyncExample"
```

## See Also

- [Asynchronous Stream Execution](../execution-and-compatibility/async-execution.md#async-terminals) — the terminals that select a sink's asynchronous drain, and the `Async[Either[E, Z]]` convention
- [Platform Differences](../execution-and-compatibility/platform-differences.md#sinkcreate-and-custom-sinks) — why `Sink.create` is JVM-only and what replaces it in shared source
- [Reader](../primitives/reader.md#the-reader-union) — the two reader kinds a sink drains, from the reader's side
- [Zero-Boxing Streams](../execution-and-compatibility/zero-boxing.md) — how a primitive lane is chosen, and the per-lane end-of-stream table
- [Stream](./stream.md) — the producer a sink consumes
- [Pipeline](./pipeline.md) — transformations that can be attached to a sink's input
