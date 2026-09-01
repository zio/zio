# Sink

> `Sink[+E, -A, +Z]` is a **stream consumer** that reads elements of type `A` and produces a result of type `Z`, potentially failing with an error of type `E`. You pass a sink to [Stream.run](./stream.md) to execute the stream synchronously and get `Either[E, Z]`.

`Sink`:
- Is covariant in `E` (error) and `Z` (result) — these are outputs
- Is contravariant in `A` (input) — a `Sink[_, Any, _]` accepts any element type
- Participates in JVM primitive specialization for zero-boxing overhead
- Provides `Sink#contramap`, `Sink#map`, and `Sink#mapError` for composable transformations

Here is the structural shape of the `Sink` type:

```scala
abstract class Sink[+E, -A, +Z] {
  def contramap[A2](g: A2 => A): Sink[E, A2, Z]
  def map[Z2](f: Z => Z2): Sink[E, A, Z2]
  def mapError[E2](f: E => E2): Sink[E2, A, Z]
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
5. The reader's `close()` runs in a `finally` block, ensuring resource safety

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

#### `Sink.collectAll[A]` — Collect into a Chunk

Collects all elements into a `Chunk[A]`:

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
  def foldLeft[A, Z](z: Z)(f: (Z, A) => Z): Sink[Nothing, A, Z]
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
// sink: Sink[String, Int, Long] = zio.blocks.streams.Sink$$anon$8@a09b6b

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
// res14: Either[Nothing, Unit] = Right(())

// Write second batch to the same stream (reuse it)
Stream.fromChunk(zio.blocks.chunk.Chunk[Byte](33)).run(Sink.fromOutputStream(bos))
// res15: Either[Nothing, Unit] = Right(())

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
// res18: Either[Nothing, Unit] = Right(())

// Get the final string
val result = writer.toString()
// result: String = "Hello World"
```

Like `Sink.fromOutputStream`, this sink intentionally does not close the writer. This gives you control over when to flush or close, allowing you to write multiple streams to the same writer or coordinate lifecycle with other operations.

### Custom

Advanced low-level use cases with direct reader protocol access:

#### `Sink.create[E, A, Z]` — Escape Hatch

Creates a sink from a raw function that takes a `Reader[A]` and returns `Z`. This is the low-level escape hatch for writing sinks that cannot be expressed using the built-in factories:

```scala
object Sink {
  def create[E, A, Z](f: Reader[A] => Z): Sink[E, A, Z]
}
```

:::note
`Sink.create` gives you direct access to the `Reader`, so you are responsible for using the correct read protocol (`Reader#read(sentinel)` for AnyRef, `Reader#readInt(sentinel)` for Int, etc.). Prefer the built-in sinks when possible.
:::

Here's a custom sink that computes the average of all integers in a stream:

```scala
import zio.blocks.streams._
import zio.blocks.streams.io.Reader

// A custom sink that computes the average of Ints
val average = Sink.create[Nothing, Int, Double] { reader =>
  def loop(sum: Long, count: Long): (Long, Long) = {
    val v = reader.readInt(Long.MinValue)
    if (v.asInstanceOf[Long] == Long.MinValue) (sum, count)
    else {
      val newSum = sum + v
      loop(newSum, count + 1)
    }
  }
  val (sum, count) = loop(0L, 0L)
  if (count == 0) 0.0 else sum.toDouble / count
}
```

This example shows how `Sink.create` works. The reader reads elements using `Reader#read[Any](null)` — the sentinel protocol — where `null` signals "read the next element" and the function returns `null` when the stream ends. We accumulate the sum and count via recursion, then return the average. You'd use `Sink.create` when no built-in sink provides the exact aggregation or transformation logic you need — it's powerful but requires understanding the low-level [Reader protocol](./reader.md).

## Transforming Sinks

Every sink can be transformed using these instance methods:

### `Sink#contramap[A2]` — Pre-Process Input

Transforms the input elements before they reach the sink. The sink's result and error types are unchanged:

```scala
trait Sink[+E, -A, +Z] {
  def contramap[A2](g: A2 => A): Sink[E, A2, Z]
}
```

`Sink#contramap` is the dual of `Sink#map`: it transforms what goes *in*, not what comes *out*:

```scala
import zio.blocks.streams._

// A sink that counts the length of strings
val totalLength: Sink[Nothing, String, Long] =
  Sink.sumInt.contramap[String](_.length)
// totalLength: Sink[Nothing, String, Long] = zio.blocks.streams.Sink$Contramapped@5360fe09

val result = Stream("hello", "world").run(totalLength)
// result: Either[Nothing, Long] = Right(10L)
```

### `Sink#map[Z2]` — Transform Result

Transforms the result after the sink finishes draining:

```scala
trait Sink[+E, -A, +Z] {
  def map[Z2](f: Z => Z2): Sink[E, A, Z2]
}
```

Transform the result after draining:

```scala
import zio.blocks.streams._

val countAsString: Sink[Nothing, Any, String] =
  Sink.count.map(n => s"Total: $n elements")
// countAsString: Sink[Nothing, Any, String] = zio.blocks.streams.Sink$Mapped@96f085b

val result = Stream(1, 2, 3).run(countAsString)
// result: Either[Nothing, String] = Right("Total: 3 elements")
```

### `Sink#mapError[E2]` — Transform Error

Transforms the error channel of a sink:

```scala
trait Sink[+E, -A, +Z] {
  inline def mapError[E2](f: E => E2): Sink[E2, A, Z]
}
```

This method uses Scala 3's `inline` + `summonFrom` to perform a compile-time check: if `E` is `Nothing` (the sink never fails), the compiler elides the wrapper entirely and returns `this` cast to the new type with zero allocation:

```scala
import zio.blocks.streams._

// No-op: drain never fails, so mapError is free
val mapped = Sink.drain.mapError[String](_.toString)
// At compile time: this is just a cast, no wrapper allocated

// Real mapping: fail can produce errors
val failing = Sink.fail("oops").mapError[RuntimeException](new RuntimeException(_))
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
// cleanAndCollect: Sink[Nothing, String, Chunk[String]] = zio.blocks.streams.Sink$Contramapped@2d273af9

val result = Stream("  Hello ", " WORLD  ").run(cleanAndCollect)
// result: Either[Nothing, Chunk[String]] = Right(IndexedSeq("hello", "world"))
```

The equivalence law holds: `stream.via(pipe).run(sink) == stream.run(pipe.andThenSink(sink))`.

See [Pipeline — Applying to a Sink](./pipeline.md#applying-to-a-sink) for more details.

## JVM NIO Sinks

The `NioSinks` object (JVM-only) provides sinks for Java NIO (`java.nio`) buffers and channels. These exist because NIO is the standard high-performance I/O mechanism on the JVM: non-blocking, memory-efficient, and capable of handling thousands of concurrent connections. When you're writing to network sockets, memory-mapped files, or other NIO-based resources, these sinks give you a convenient way to drain streams directly into NIO data structures without intermediate allocation or copying.

Traditional Java I/O (`OutputStream`, `Writer`) blocks threads and requires manual buffering for efficiency. NIO provides non-blocking channels, but using them directly requires buffer allocation, position management, and explicit flushing. `NioSinks` bridges this gap: `NioSinks.fromChannel` handles buffering automatically (default 8KB), while typed variants like `NioSinks.fromByteBufferInt` and `NioSinks.fromByteBufferLong` eliminate boxing overhead by writing primitives directly to buffers you provide.

Choose `NioSinks.fromChannel` when you need to write to network sockets or files and cannot afford to block threads. Choose typed variants when you control buffer allocation and are streaming millions of primitives where boxing would degrade performance. **Important:** Read the Sentinel Value Limitation section below—it describes a hard constraint that affects your choice depending on whether your data can contain specific values.

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
- `NioSinks.fromByteBuffer` — writes individual `Byte` elements using a read sentinel of `-1`. Use only for unstructured byte data.
- `NioSinks.fromByteBufferInt`, `NioSinks.fromByteBufferLong`, `NioSinks.fromByteBufferFloat`, `NioSinks.fromByteBufferDouble` — write primitives directly using the buffer's native methods (`putInt`, `putLong`, etc.). These avoid boxing and are faster than the byte variant.

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
// res25: Either[Nothing, Unit] = Right(())

// After writing, rewind to read
buffer.rewind()
// res26: ByteBuffer = java.nio.HeapByteBuffer[pos=32 lim=32 cap=32]

val readBack = List(
  buffer.getLong(),
  buffer.getLong(),
  buffer.getLong(),
  buffer.getLong()
)
// readBack: List[Long] = List(1L, 2L, 3L, 4L)
```

This example allocates a 32-byte buffer (4 Longs × 8 bytes each), writes four `Long` values using `NioSinks.fromByteBufferLong` (which efficiently calls `putLong` on each element), then rewinds and reads them back to verify. The typed variant is significantly faster than `NioSinks.fromByteBuffer` because it operates at the primitive level — no boxing, no element-by-element byte writing.

The following example shows streaming voltage sensor readings through a calibration curve and buffering them for downstream computation. When processing sensor arrays or scientific measurements, pre-allocated buffers with typed sinks enable zero-copy batch processing.

Here is the complete example:

```scala title="streams-examples/src/main/scala/sink/SinkScientificComputingExample.scala" 
/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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

:::warning[Sentinel Collisions Throw — Never Silently Truncate]

These typed sinks achieve **zero-boxing performance** by using a special "sentinel" value to signal end-of-stream, rather than allocating wrapper objects or checking for `null`. This design eliminates allocations entirely, keeping the read loop a **single primitive comparison per element**. This loop shape is a deliberate, protected performance choice (see the repository's `AGENTS.md`, "Sentinel performance policy"): no per-element flag checks, rawbits conversions, boxing, or extra branches are permitted in it.

A natural question: what happens if the stream *contains* the sentinel value (e.g. a `Long.MaxValue` element streamed into `NioSinks.fromByteBufferLong`)? The sink **throws `IllegalArgumentException`** — your data is never silently dropped. Detection costs nothing on the hot path: every read records an out-of-band `lastReadWasEOF` flag on the reader, and the sink consults it **once, after the drain loop exits**, to distinguish genuine end-of-stream from a real sentinel-valued element:

```scala
// fromByteBufferLong - tight loop with primitives only
val s = Long.MaxValue
var v = reader.readLong(s)(using unsafeEvidence)
while (v != s) { // single primitive comparison per element
  buf.putLong(v)
  v = reader.readLong(s)(using unsafeEvidence)
}
if (!reader.lastReadWasEOF) // consulted once, post-loop: zero hot-path cost
  throw new IllegalArgumentException("stream contains Long.MaxValue ...")
```

**Sentinels per typed sink:**
| Method | Input Type | Sentinel Value | Collision behavior |
|--------|-----------|---|---|
| `NioSinks.fromByteBuffer` | `Byte` | `-1` (as `Int`) | No collision possible — bytes are widened to [0, 255] |
| `NioSinks.fromByteBufferInt` | `Int` | `Long.MinValue` | No collision possible — outside Int range |
| `NioSinks.fromByteBufferLong` | `Long` | `Long.MaxValue` | Throws `IllegalArgumentException` |
| `NioSinks.fromByteBufferFloat` | `Float` | `Double.MaxValue` | No collision possible — outside Float range |
| `NioSinks.fromByteBufferDouble` | `Double` | `Double.MaxValue` | Throws `IllegalArgumentException` |

**If your data may contain the sentinel value**, use a generic sink instead — these use an out-of-band object sentinel and handle every value:
- `Sink.collectAll[A]` — collects into a Chunk
- `Sink.foreach[A](f: A => Unit)` — processes each element individually
- `Sink.foldLeft[A, Z](z: Z)(f: (Z, A) => Z)` — accumulates
- `Sink.create[E, A, Z](f: Reader[A] => Z)` — manual control

For a runnable demonstration of the guard, see the example below:

```scala title="streams-examples/src/main/scala/sink/SinkSentinelGuardExample.scala" 
/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package sink

import zio.blocks.streams.*
import zio.blocks.streams.NioSinks
import java.nio.ByteBuffer

object SinkSentinelGuardExample extends App {
  println("=== Sentinel Collisions Are Rejected Loudly (Never Silently) ===\n")

  println("Context: the typed NIO sinks use a primitive sentinel (e.g. Long.MaxValue for")
  println("fromByteBufferLong) to detect end-of-stream, keeping the drain loop a single")
  println("primitive comparison per element — zero boxing, zero allocation. This is a")
  println("deliberate performance choice (see AGENTS.md, Sentinel performance policy).")
  println("If your stream contains the sentinel value itself, the sink does NOT silently")
  println("truncate: it detects the collision at zero hot-path cost (one out-of-band EOF")
  println("flag check after the loop exits) and throws IllegalArgumentException.\n")

  // Example 1: normal data drains at full speed
  println("Test 1: Stream without sentinel values drains completely")
  println("-" * 60)

  val safeData = List(100L, 200L, 300L, 400L, 500L)
  val buffer1  = ByteBuffer.allocate(safeData.length * 8)
  Stream.fromIterable(safeData).run(NioSinks.fromByteBufferLong(buffer1))
  buffer1.flip()

  var count1 = 0
  while (buffer1.hasRemaining) {
    println(f"  [$count1] ${buffer1.getLong()}")
    count1 += 1
  }
  println(f"\n✓ All ${count1} values written\n")

  // Example 2: a sentinel-valued element is rejected with a clear error
  println("Test 2: Stream containing Long.MaxValue is rejected, not truncated")
  println("-" * 60)

  val riskyData = List(100L, 200L, Long.MaxValue, 300L, 400L)
  println(
    f"Stream data: ${riskyData.map(v => if (v == Long.MaxValue) "Long.MaxValue" else v.toString).mkString(", ")}\n"
  )

  val buffer2 = ByteBuffer.allocate(riskyData.length * 8)
  try {
    Stream.fromIterable(riskyData).run(NioSinks.fromByteBufferLong(buffer2))
    println("✗ UNEXPECTED: drain completed without error")
  } catch {
    case e: IllegalArgumentException =>
      println(s"✓ Rejected loudly: ${e.getMessage}")
  }

  // Recommendations
  println("\n=== Recommendations ===")
  println("1. If your data might contain the sentinel value (Long.MaxValue for the Long")
  println("   sink, Double.MaxValue for the Double sink):")
  println("   → Use a generic sink (Sink.collectAll, Sink.foreach, Sink.foldLeft) — these")
  println("     use an out-of-band object sentinel and handle every value")
  println("2. Otherwise the typed sinks are maximally fast: a single primitive comparison")
  println("   per element, zero boxing, zero allocation")
  println("3. Either way, data is never silently dropped — a collision throws")
  println()
  println("Sentinels per typed sink:")
  println("   → fromByteBufferInt: sentinel = Long.MinValue (outside Int range — no collision possible)")
  println("   → fromByteBufferLong: sentinel = Long.MaxValue (collision throws)")
  println("   → fromByteBufferFloat: sentinel = Double.MaxValue (outside Float range — no collision possible)")
  println("   → fromByteBufferDouble: sentinel = Double.MaxValue (collision throws)")
}
```

Run it with:

```bash
sbt "streams-examples/runMain sink.SinkSentinelGuardExample"
```
:::

You might ask: **Why not use a sentinel object like generic sinks do, instead of primitive values?** The answer reveals a fundamental performance trade-off.

Generic sinks use object sentinels to signal end-of-stream:

```scala
// Sink.collectAll - uses object reference for end-of-stream
def loop(v: Any): Unit =
  if (v.asInstanceOf[AnyRef] ne EndOfStream) {
    b += v.asInstanceOf[A]
    loop(reader.read(EndOfStream))
  }
val firstValue = reader.read(EndOfStream)  // EndOfStream is an object
loop(firstValue)
```

**Performance Impact:**
- **Typed sinks:** Direct primitive comparison, zero allocations, tight loop optimizable by JVM
- **Generic sinks:** Object casting, reference equality check, one `EndOfStream` object per stream

For a stream processing **millions of elements**, the typed sink approach has measurably better performance because:
1. No casting overhead per iteration
2. Primitive values are faster than object references
3. JIT compiler can better optimize tight primitive loops
4. Zero per-element allocation pressure

Neither approach silently drops data: the generic sinks use a reference-unique object that no stream element can equal, and the typed sinks detect a value/sentinel collision via the out-of-band EOF flag (consulted once, post-loop) and throw rather than truncate.

### From Channel Sink

The **`Sink.fromChannel`**  constructor performs buffered writes to a `WritableByteChannel` (e.g., a network socket or file channel). This is the general-purpose NIO sink: it accumulates bytes in an internal buffer of size `bufSize` (default 8192), flushes when the buffer is full, and flushes again at end-of-stream.

It handles `IOException` as a typed error, so failures surface as `Left(IOException)` from `Stream.run`. Use this for network I/O or when you can't pre-allocate a buffer. The channel I/O is blocking—NIO's non-blocking advantage comes when using selectors across many channels, which this sink does not expose.

Suppose you're collecting metrics from thousands of sensors (temperature, pressure, timestamps) and need to write them to a file efficiently. Using `NioSinks.fromChannel` with a file's `WritableByteChannel` gives you automatic buffering and eliminates manual position management.

Here is the complete example:

```scala title="streams-examples/src/main/scala/sink/SinkTelemetryExample.scala" 
/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package sink

import zio.blocks.streams.*
import zio.sbt.ExprEval.show

object SinkTransformationExample extends App {
  println("=== Sink Transformations and Composition ===\n")

  // 1. contramap — pre-process input
  println("1. Sink.contramap — pre-process input elements:")
  val stringLengthSum: Sink[Nothing, String, Long] =
    Sink.sumInt.contramap[String](_.length)

  show(Stream("hello", "world").run(stringLengthSum))

  // 2. contramap — change element type
  println("\n2. contramap to convert types:")
  val parseInts: Sink[Nothing, String, Long] =
    Sink.sumInt.contramap[String](_.toInt)

  show(Stream("10", "20", "30").run(parseInts))

  // 3. map — transform result
  println("\n3. Sink.map — transform the result:")
  val countFormatted: Sink[Nothing, Any, String] =
    Sink.count.map(n => s"Processed $n elements")

  show(Stream(1, 2, 3).run(countFormatted))

  // 4. Chaining contramap + map
  println("\n4. Chaining contramap + map:")
  val pipeline = Sink.sumInt
    .contramap[String](_.length)
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

### Sentinel Guard (NIO Typed Sinks)

This example demonstrates that the typed NIO sinks (`NioSinks.fromByteBufferLong`, `NioSinks.fromByteBufferDouble`) reject streams containing their sentinel value (e.g., `Long.MaxValue` for `NioSinks.fromByteBufferLong`) with an `IllegalArgumentException` instead of silently truncating — detected at zero hot-path cost via the reader's out-of-band EOF flag, consulted once after the drain loop exits:

```scala title="streams-examples/src/main/scala/sink/SinkSentinelGuardExample.scala" 
/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package sink

import zio.blocks.streams.*
import zio.blocks.streams.NioSinks
import java.nio.ByteBuffer

object SinkSentinelGuardExample extends App {
  println("=== Sentinel Collisions Are Rejected Loudly (Never Silently) ===\n")

  println("Context: the typed NIO sinks use a primitive sentinel (e.g. Long.MaxValue for")
  println("fromByteBufferLong) to detect end-of-stream, keeping the drain loop a single")
  println("primitive comparison per element — zero boxing, zero allocation. This is a")
  println("deliberate performance choice (see AGENTS.md, Sentinel performance policy).")
  println("If your stream contains the sentinel value itself, the sink does NOT silently")
  println("truncate: it detects the collision at zero hot-path cost (one out-of-band EOF")
  println("flag check after the loop exits) and throws IllegalArgumentException.\n")

  // Example 1: normal data drains at full speed
  println("Test 1: Stream without sentinel values drains completely")
  println("-" * 60)

  val safeData = List(100L, 200L, 300L, 400L, 500L)
  val buffer1  = ByteBuffer.allocate(safeData.length * 8)
  Stream.fromIterable(safeData).run(NioSinks.fromByteBufferLong(buffer1))
  buffer1.flip()

  var count1 = 0
  while (buffer1.hasRemaining) {
    println(f"  [$count1] ${buffer1.getLong()}")
    count1 += 1
  }
  println(f"\n✓ All ${count1} values written\n")

  // Example 2: a sentinel-valued element is rejected with a clear error
  println("Test 2: Stream containing Long.MaxValue is rejected, not truncated")
  println("-" * 60)

  val riskyData = List(100L, 200L, Long.MaxValue, 300L, 400L)
  println(
    f"Stream data: ${riskyData.map(v => if (v == Long.MaxValue) "Long.MaxValue" else v.toString).mkString(", ")}\n"
  )

  val buffer2 = ByteBuffer.allocate(riskyData.length * 8)
  try {
    Stream.fromIterable(riskyData).run(NioSinks.fromByteBufferLong(buffer2))
    println("✗ UNEXPECTED: drain completed without error")
  } catch {
    case e: IllegalArgumentException =>
      println(s"✓ Rejected loudly: ${e.getMessage}")
  }

  // Recommendations
  println("\n=== Recommendations ===")
  println("1. If your data might contain the sentinel value (Long.MaxValue for the Long")
  println("   sink, Double.MaxValue for the Double sink):")
  println("   → Use a generic sink (Sink.collectAll, Sink.foreach, Sink.foldLeft) — these")
  println("     use an out-of-band object sentinel and handle every value")
  println("2. Otherwise the typed sinks are maximally fast: a single primitive comparison")
  println("   per element, zero boxing, zero allocation")
  println("3. Either way, data is never silently dropped — a collision throws")
  println()
  println("Sentinels per typed sink:")
  println("   → fromByteBufferInt: sentinel = Long.MinValue (outside Int range — no collision possible)")
  println("   → fromByteBufferLong: sentinel = Long.MaxValue (collision throws)")
  println("   → fromByteBufferFloat: sentinel = Double.MaxValue (outside Float range — no collision possible)")
  println("   → fromByteBufferDouble: sentinel = Double.MaxValue (collision throws)")
}
```

Run it with this command:

```bash
sbt "streams-examples/runMain sink.SinkSentinelGuardExample"
```
