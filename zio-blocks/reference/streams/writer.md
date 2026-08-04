# Writer

> `Writer[-Elem]` is a **push-based sink for elements** that accepts values one at a time until closed or filled. It is the push-based counterpart to `Reader[+Elem]` (which pulls). Elements are written on demand by the producer, making it ideal for streaming, buffering, and integration with I/O subsystems. The fundamental operations are `write(elem): Boolean` — pushes an element and returns success or closure — and `close()` — signals the end of writing and releases resources.

`Writer[-Elem]` is a **push-based sink for elements** that accepts values one at a time until closed or filled. It is the push-based counterpart to `Reader[+Elem]` (which pulls). Elements are written on demand by the producer, making it ideal for streaming, buffering, and integration with I/O subsystems. The fundamental operations are `write(elem): Boolean` — pushes an element and returns success or closure — and `close()` — signals the end of writing and releases resources.

`Writer[-Elem]` has these key properties:

- **Lazy and Push-Based** — nothing happens until the producer calls `write()`
- **Non-Thread-Safe** — designed for single-threaded production; concurrent access requires external synchronization
- **Explicit Closure Signal** — returns `false` when closed (clean closure) or throws when error-closed

Here is the structural shape of the `Writer` type:

```scala
abstract class Writer[-Elem] {
  def write(a: Elem): Boolean
  def close(): Unit
  def isClosed: Boolean
  
  // concrete defaults for fail() and writeable()
  def fail(error: Throwable): Unit = close()
  def writeable(): Boolean = !isClosed
}
```

## Motivation

Imagine you're building a data pipeline where a producer feeds items to a bounded sink. The producer doesn't control the sink's internal state—how much capacity remains, whether it's busy, or if it's permanently closed. You need to know before each write: Is the sink ready? Did the write succeed? Is the sink closed?

With Java's `OutputStream`, you call `write()` and either it succeeds (void return) or throws an exception. This leaves ambiguity: Was the exception transient (try again later) or permanent (the stream is done)? If the buffer fills, the thread blocks—but you don't know how long, or even that it will block beforehand. There's no way to check capacity upfront, so you're forced to either over-allocate buffers (wasting memory) or catch exceptions and guess the right strategy.

`Writer` makes the state explicit and non-throwing. You check readiness with `writeable()`, then push with `write()`, which returns a `Boolean` indicating success or closure. The protocol is clear and exception-free: when `write()` returns `false`, the sink is permanently closed and you should stop.

## Quick Showcase

Here's how to create and push elements to a `Writer`:

```scala
import zio.blocks.streams.io.Writer
import scala.collection.mutable.Buffer

val collected = Buffer[Int]()
// collected: Buffer[Int] = ArrayBuffer(10, 20, 30, 40, 50)
val w = new Writer[Int] {
  private var closed = false
  
  def isClosed = closed
  def write(a: Int) = {
    if (!closed) { collected += a; true }
    else false
  }
  def close() = { closed = true }
  override def fail(error: Throwable) = close()
  override def writeable() = !isClosed
}
// w: Writer[Int] = repl.MdocSession$MdocApp0$$anon$2@350ce732

// Push elements, checking writeable() before each write
def pushAll(elements: List[Int]): Unit = {
  elements match {
    case Nil => ()
    case head :: tail =>
      if (w.writeable() && w.write(head)) pushAll(tail)
  }
}

pushAll(List(10, 20, 30, 40, 50))
w.close()

println(s"Collected: $collected")
// Collected: ArrayBuffer(10, 20, 30, 40, 50)
println(s"Writable after close: ${w.writeable()}")
// Writable after close: false
```

## Writing and Closure

The fundamental protocol is: call `write(element)` to push an element. It returns `true` on success, `false` only when the writer is **closed** (not when the buffer is full). Once `write()` returns `false`, the writer is permanently closed—all further writes return `false`. There is no recovery.

```scala
import zio.blocks.streams.io.Writer

val w = Writer.single[Int]
// w: Writer[Int] = zio.blocks.streams.io.Writer$SingleWriter@78b107f
println(s"First write: ${w.write(42)}")      // true (accepted)
// First write: true
println(s"Second write: ${w.write(99)}")     // false (writer auto-closed after one element)
// Second write: false
println(s"Third write: ${w.write(77)}")      // false (still closed)
// Third write: false
```

## Capacity and Buffering

The default `writeable()` method returns `!isClosed`—it only tells you if the writer is closed, not whether the buffer has space. Bounded implementations can override `writeable()` to reflect remaining capacity, but this is not guaranteed by the interface. The important distinction:

- **`writeable()` returns `false`**: the writer is closed (permanent state)
- **`writeable()` returns `true` but `write()` would block**: the buffer is full but not closed; bounded implementations block the calling thread until space becomes available (they don't return `false`)

Implementations like `ByteBufferWriter` auto-close when the buffer fills, turning the full state into closure. Others may block indefinitely waiting for space.

## Error Handling

When the writer encounters an error, signal it with `fail(error)`. By default, `fail()` closes the writer; all subsequent `write()` calls return `false`.

If you override `fail()` to store the error internally, `write()` will throw it on the next call:

```scala
import zio.blocks.streams.io.Writer

class ErrorStoringWriter extends Writer[Int] {
  private var closed = false
  private var storedError: Option[Throwable] = None
  
  def isClosed = closed
  def write(a: Int): Boolean = {
    if (storedError.isDefined) throw storedError.get
    if (closed) false else true
  }
  def close() = { closed = true }
  override def fail(error: Throwable) = {
    storedError = Some(error)
    closed = true
  }
}

val w = new ErrorStoringWriter()
// w: ErrorStoringWriter = repl.MdocSession$MdocApp9$ErrorStoringWriter@35e00b45
w.fail(new Exception("Stream error"))
try {
  w.write(42)  // throws the stored error
} catch {
  case e: Exception => println(s"Caught: ${e.getMessage}")
}
// Caught: Stream error
```

This gives you optional error propagation: use the default `fail()` for silent closure, or override it to propagate errors as exceptions.

## Construction

Writers are created using factory methods on the companion object, from adapters wrapping Java I/O, or by direct subclassing for custom implementations:

### Creating Predefined Writers

`Writer.closed` — A pre-closed writer that rejects all writes. Useful as a base case for empty streams:

```scala
object Writer {
  def closed: Writer[Any]
}
```

Create a pre-closed writer that rejects all writes:

```scala
import zio.blocks.streams.io.Writer

val w = Writer.closed
// w: Writer[Any] = zio.blocks.streams.io.Writer$$anon$1@603b3a16
println(w.write(42))        // false (closed)
// false
println(w.isClosed)         // true
// true
```

### Single Element

`Writer.single` — Creates a writer that accepts exactly one element, then auto-closes. The dual of `Reader.single`:

```scala
object Writer {
  def single[Elem]: Writer[Elem]
}
```

Create a writer that accepts exactly one element, then auto-closes:

```scala
import zio.blocks.streams.io.Writer

val w = Writer.single[Int]
// w: Writer[Int] = zio.blocks.streams.io.Writer$SingleWriter@20f8ee95
println(w.write(42))        // true
// true
println(w.write(99))        // false (already accepted one element)
// false
println(w.isClosed)         // true
// true
```

### Limited Capacity

`Writer.limited` — Creates a writer that accepts at most `n` elements from `inner`, then becomes closed. The dual of `Stream.take`. If `inner` closes before `n` elements are accepted, the limited writer also closes immediately without consuming the remaining capacity. 

:::note
The inner writer is not automatically closed—only the limited wrapper's `isClosed` returns `true` when the limit is reached. The inner writer stays open until someone explicitly calls `close()`.
:::

```scala
object Writer {
  def limited[Elem](inner: Writer[Elem], n: Long): Writer[Elem]
}
```

Limit a writer to accept at most n elements:

```scala
import zio.blocks.streams.io.Writer
import scala.collection.mutable.Buffer

val collected = Buffer[Int]()
// collected: Buffer[Int] = ArrayBuffer(1, 2)
val inner = new Writer[Int] {
  def isClosed = false
  def write(a: Int) = { collected += a; true }
  def close() = ()
}
// inner: Writer[Int] = repl.MdocSession$MdocApp19$$anon$23@73ed153a

val limited = Writer.limited(inner, 2)
// limited: Writer[Int] = zio.blocks.streams.io.Writer$LimitedWriter@6e804688
println(limited.write(1))    // true
// true
println(limited.write(2))    // true (space available)
// true
println(limited.write(3))    // false (limit of 2 reached)
// false
println(s"Collected: $collected")  // Collected: Buffer(1, 2)
// Collected: ArrayBuffer(1, 2)
```

### I/O Adapters

`Writer.fromOutputStream` — Wraps a `java.io.OutputStream` as a `Writer[Byte]`. Calling `close()` flushes and closes the underlying stream:

```scala
object Writer {
  def fromOutputStream(os: OutputStream): Writer[Byte]
}
```

`Writer.fromWriter` — Wraps a `java.io.Writer` as a `Writer[Char]`. Calling `close()` flushes and closes the underlying writer:

```scala
object Writer {
  def fromWriter(w: java.io.Writer): Writer[Char]
}
```

## Core Operations

The fundamental operations on `Writer` cover pushing elements one at a time, bulk operations, specialized writes for primitives, and state checks:

### Writing Elements

`Writer#write` — Pushes one element to the writer. Returns `true` on success, `false` if the writer is closed and cannot accept more elements. Throws if the writer was closed with an error via `Writer#fail`:

```scala
abstract class Writer[-Elem] {
  def write(a: Elem): Boolean
}
```

Write elements and observe the return value indicating success or closure:

```scala
import zio.blocks.streams.io.Writer

val w = Writer.single[Int]
// w: Writer[Int] = zio.blocks.streams.io.Writer$SingleWriter@5855f9a7
val result1 = w.write(42)
// result1: Boolean = true
val result2 = w.write(99)  // false, already closed
// result2: Boolean = false
println(s"First: $result1, Second: $result2")
// First: true, Second: false
```

### Bulk Writing

`Writer#writeAll` — Writes every element in a chunk. Returns the suffix not delivered. If the writer is already closed, returns the entire chunk. Exceptions from individual writes propagate to the caller:

```scala
abstract class Writer[-Elem] {
  def writeAll[Elem1 <: Elem](chunk: Chunk[Elem1]): Chunk[Elem1]
}
```

Write a chunk and observe how many elements were delivered:

```scala
import zio.blocks.streams.io.Writer
import zio.blocks.chunk.Chunk

val w = Writer.single[Int]
// w: Writer[Int] = zio.blocks.streams.io.Writer$SingleWriter@4130dfc
val chunk = Chunk(1, 2, 3)
// chunk: Chunk[Int] = IndexedSeq(1, 2, 3)
val remaining = w.writeAll(chunk)
// remaining: Chunk[Int] = IndexedSeq(2, 3)
println(s"Remaining: $remaining")  // Chunk(2, 3)
// Remaining: Chunk(2,3)
```

### Specialized Writes

For primitive types, specialized write methods avoid boxing by using subtype witnesses.

`writeInt` — Specialized `Int` write. Requires implicit evidence that `Int` is a subtype of `Elem`:

```scala
abstract class Writer[-Elem] {
  def writeInt(value: Int)(using Int <:< Elem): Boolean
}
```

`writeLong` — Specialized `Long` write:

```scala
abstract class Writer[-Elem] {
  def writeLong(value: Long)(using Long <:< Elem): Boolean
}
```

`writeFloat` — Specialized `Float` write:

```scala
abstract class Writer[-Elem] {
  def writeFloat(value: Float)(using Float <:< Elem): Boolean
}
```

`writeDouble` — Specialized `Double` write:

```scala
abstract class Writer[-Elem] {
  def writeDouble(value: Double)(using Double <:< Elem): Boolean
}
```

### Byte and Character Writes

`writeByte` — Specialized byte write. Avoids boxing when `Elem = Byte`. Requires evidence that `Byte` is a subtype of `Elem`:

```scala
abstract class Writer[-Elem] {
  def writeByte(b: Byte)(using Byte <:< Elem): Boolean
}
```

`writeBytes` — Blocking bulk byte write. Calls `writeByte` for each byte in `buf[offset, offset+len)`, stopping early if the channel closes. Returns the number of bytes successfully written:

```scala
abstract class Writer[-Elem] {
  def writeBytes(buf: Array[Byte], offset: Int, len: Int)(using Byte <:< Elem): Int
}
```

`writeChar` — Specialized `Char` write. Requires evidence that `Char` is a subtype of `Elem`:

```scala
abstract class Writer[-Elem] {
  def writeChar(value: Char)(using Char <:< Elem): Boolean
}
```

`writeShort` — Specialized `Short` write. Requires evidence that `Short` is a subtype of `Elem`:

```scala
abstract class Writer[-Elem] {
  def writeShort(value: Short)(using Short <:< Elem): Boolean
}
```

`writeBoolean` — Specialized `Boolean` write. Requires evidence that `Boolean` is a subtype of `Elem`:

```scala
abstract class Writer[-Elem] {
  def writeBoolean(value: Boolean)(using Boolean <:< Elem): Boolean
}
```

### State Checks

`Writer#isClosed` — Returns `true` if the writer is closed. Monotone: once `true`, never returns `false`:

```scala
abstract class Writer[-Elem] {
  def isClosed: Boolean
}
```

`writeable` — Returns `true` if the next `write()` would accept a value without blocking (space is available and the writer is not closed). Default returns `!isClosed`. Buffered writers override for accuracy. Note: the analogous method on `Reader` is named `readable()`, not `writable()`.

```scala
abstract class Writer[-Elem] {
  def writeable(): Boolean
}
```

Check writer capacity before writing:

```scala
import zio.blocks.streams.io.Writer

val w = Writer.single[Int]
// w: Writer[Int] = zio.blocks.streams.io.Writer$SingleWriter@35d64778
println(w.writeable())      // true
// true
w.write(42)
// res30: Boolean = true
println(w.writeable())      // false (closed after accepting one)
// false
```

## Composition

Writers can be concatenated to chain multiple sinks together, or transformed to adapt their input types:

### Concatenation

`Writer#concat` — Returns a `Writer` that writes to `this` until it closes, then transparently switches to `next`. If `this` closes with an error, the error is propagated immediately without consulting `next`. The dual of `Reader#concat`:

```scala
abstract class Writer[-Elem] {
  def concat[Elem1 <: Elem](next: => Writer[Elem1]): Writer[Elem1]
}
```

`Writer#++` — Alias for `Writer#concat`. Syntactic sugar for composing writers:

```scala
abstract class Writer[-Elem] {
  def ++[Elem1 <: Elem](next: => Writer[Elem1]): Writer[Elem1]
}
```

Here is how concatenation switches to the next writer when the first closes:

```scala
import zio.blocks.streams.io.Writer
import scala.collection.mutable

val collected = mutable.ArrayBuffer[Int]()
// collected: ArrayBuffer[Int] = ArrayBuffer(5, 200)
val w1 = new Writer[Int] {
  def isClosed = false
  def write(a: Int) = {
    if (a < 10) { collected += a; true; }
    else false
  }
  def close() = ()
}
// w1: Writer[Int] = repl.MdocSession$MdocApp32$$anon$43@3bcbf5bd

val w2 = new Writer[Int] {
  def isClosed = false
  def write(a: Int) = { collected += a * 10; true }
  def close() = ()
}
// w2: Writer[Int] = repl.MdocSession$MdocApp32$$anon$45@3ed3f62

val combined = w1 ++ w2
// combined: Writer[Int] = zio.blocks.streams.io.Writer$ConcatWith@6f75d7cc
combined.write(5)
// res33: Boolean = true
combined.write(20)  // first writer rejects, switches to second
// res34: Boolean = true
println(collected.toList)  // List(5, 200)
// List(5, 200)
```

### Transformation

`Writer#contramap` — Returns a `Writer` that transforms incoming elements with `g` before passing them to this writer. All other operations (`Writer#isClosed`, `Writer#close`, `Writer#fail`) delegate unchanged:

```scala
abstract class Writer[-Elem] {
  def contramap[Elem2](g: Elem2 => Elem): Writer[Elem2]
}
```

Transform the input type before writing:

```scala
import zio.blocks.streams.io.Writer

val stringWriter = new Writer[String] {
  def isClosed = false
  def write(a: String) = { println(s"Writing: $a"); true }
  def close() = ()
}
// stringWriter: Writer[String] = repl.MdocSession$MdocApp36$$anon$51@23d463e2

val intWriter = stringWriter.contramap[Int](_.toString)
// intWriter: Writer[Int] = zio.blocks.streams.io.Writer$Contramapped@254fb1b1
intWriter.write(42)   // Prints: Writing: 42
// Writing: 42
// res37: Boolean = true
```

## Closure and Error Handling

Writers support both clean closure and error closure, allowing you to signal end-of-stream gracefully or with an error condition:

### Clean Closure

`Writer#close` — Closes the writer cleanly. After this call, `write()` returns `false` and `Writer#isClosed` returns `true`. Idempotent:

```scala
abstract class Writer[-Elem] {
  def close(): Unit
}
```

### Error Closure

`Writer#fail` — Closes the writer with an error. After this call, `Writer#isClosed` returns `true`. Subclasses that override this method may cause `write()` to throw `error` on subsequent calls; the default simply delegates to `Writer#close`. Both `Writer#close` and `Writer#fail` are idempotent; only the first call wins:

```scala
abstract class Writer[-Elem] {
  def fail(error: Throwable): Unit
}
```

Close a writer with an error:

```scala
import zio.blocks.streams.io.Writer

val w = Writer.single[Int]
// w: Writer[Int] = zio.blocks.streams.io.Writer$SingleWriter@7e886598
w.write(42)
// res39: Boolean = true
w.fail(new RuntimeException("Error"))
println(w.isClosed)     // true
// true
```

## Contravariance

`Writer` is **contravariant** in `Elem`, meaning `Writer[-Elem]` can accept narrower types. If you have a `Writer[Number]`, you can use it as a `Writer[Int]` because every `Int` is a `Number`:

```scala
import zio.blocks.streams.io.Writer

trait Number
case class IntNum(value: Int) extends Number

val numberWriter = new Writer[Number] {
  def isClosed = false
  def write(a: Number) = { println(s"Number: $a"); true }
  def close() = ()
}
// numberWriter: Writer[Number] = repl.MdocSession$MdocApp42$$anon$59@290eac28

// numberWriter is also a Writer[IntNum] due to contravariance
val intNumWriter: Writer[IntNum] = numberWriter
// intNumWriter: Writer[IntNum] = repl.MdocSession$MdocApp42$$anon$59@290eac28
intNumWriter.write(IntNum(42))
// Number: IntNum(42)
// res43: Boolean = true
```

This is the dual of Reader's covariance: Reader is covariant (`+Elem`) because narrower elements flow out; Writer is contravariant (`-Elem`) because broader element types flow in.

## Integration with Readers and Channels

While Reader is typically used with pull-based stream operations, Writer is used internally by channel-based implementations and as an I/O adapter. The pairing is natural: a Reader pulls from a source, while a Writer pushes to a sink.

For typical stream usage, you'll see Writer indirectly when writing to files, network sockets, or other I/O resources. The `Writer.fromOutputStream` and `Writer.fromWriter` factories adapt standard Java I/O to the Writer interface.

## Implementation Notes

Understanding `Writer`'s design decisions helps you use it correctly and avoid common pitfalls:

### Push vs Pull

`Writer` is push-based (producer-driven), contrasting with `Reader` which is pull-based (consumer-driven):

| Aspect         | Reader                     | Writer                  |
|----------------|----------------------------|-------------------------|
| **Direction**  | Source → Consumer (pull)   | Producer → Sink (push)  |
| **Variance**   | Covariant (`+Elem`)        | Contravariant (`−Elem`) |
| **Blocking**   | `read()` may block         | `write()` may block     |
| **Signal end** | Returns sentinel or `null` | `close()` or `fail()`   |
| **Dual**       | Sink drains Reader         | Producer feeds Writer   |

### Thread Safety

`Writer` is **not thread-safe** by default. It is designed for single-threaded, push-based production. Do not share a `Writer` across threads without external synchronization. If you need concurrent production, wrap the writer in a thread-safe queue or use a concurrent streaming library.

### Idempotency

Both `close()` and `fail()` are idempotent: only the first call wins. Subsequent calls have no effect. This simplifies error handling in try-finally blocks.

## Running the Examples

All code from this guide is available as runnable examples in the `streams-examples` module.

**1. Clone the repository and navigate to the project:**

Run these commands to set up the examples:

```bash
git clone https://github.com/zio/zio-blocks.git
cd zio-blocks
```

**2. Run individual examples with sbt:**

### Basic Writer Construction

This example demonstrates the most common writer factories: `Writer.single`, `Writer.limited`, `Writer.closed`, and custom writers via subclassing:

```scala title="streams-examples/src/main/scala/writer/WriterBasicConstructionExample.scala" 
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

package writer

import zio.blocks.streams.io.Writer
import zio.blocks.chunk.Chunk
import scala.collection.mutable

/**
 * Demonstrates the most common Writer factories: single, limited, closed, and
 * custom writers via subclassing. Each writer is fed manually with write() to
 * show how to produce elements.
 */
object WriterBasicConstructionExample extends App {

  println("=== Writer.single ===")
  val singleWriter = Writer.single[Int]
  println(s"Write 42: ${singleWriter.write(42)}")
  println(s"Write 99 (closed): ${singleWriter.write(99)}")
  println(s"isClosed: ${singleWriter.isClosed}")

  println("\n=== Writer.closed ===")
  val closedWriter = Writer.closed
  println(s"Write to closed: ${closedWriter.write(1)}")
  println(s"isClosed: ${closedWriter.isClosed}")

  println("\n=== Writer.limited ===")
  val limitedWriter = Writer.limited(Writer.single[String], 2)
  val a             = "a"
  val b             = "b"
  val c             = "c"
  println(s"Write 'a': ${limitedWriter.write(a)}")
  println(s"Write 'b': ${limitedWriter.write(b)}")
  println(s"Write 'c': ${limitedWriter.write(c)}")
  println(s"isClosed: ${limitedWriter.isClosed}")

  println("\n=== writeAll: bulk write ===")
  val collected     = mutable.ArrayBuffer[Int]()
  val collectWriter = new Writer[Int] {
    def isClosed      = false
    def write(a: Int) = { collected += a; true }
    def close(): Unit = ()
  }

  val chunk     = Chunk(10, 20, 30)
  val remaining = collectWriter.writeAll(chunk)
  println(s"Collected: ${collected.toList}")
  println(s"Remaining: $remaining")

  println("\n=== writeable: check capacity ===")
  val capWriter = Writer.single[Int]
  println(s"writeable before: ${capWriter.writeable()}")
  capWriter.write(42)
  println(s"writeable after: ${capWriter.writeable()}")

  println("\n=== Custom Writer ===")
  val upperWriter = new Writer[String] {
    private val buffer   = mutable.ArrayBuffer[String]()
    def isClosed         = false
    def write(a: String) = {
      buffer += a.toUpperCase()
      true
    }
    def close(): Unit = println(s"Final buffer: $buffer")
  }

  upperWriter.write("hello")
  upperWriter.write("world")
  upperWriter.close()
}
```

Run this example with:

```bash
sbt "streams-examples/runMain writer.WriterBasicConstructionExample"
```

### Composition and Transformation

This example shows writer composition with `Writer#++` (concat), transformation with `Writer#contramap`, and bulk writes with `Writer#writeAll`:

```scala title="streams-examples/src/main/scala/writer/WriterCompositionExample.scala" 
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

package writer

import zio.blocks.streams.io.Writer
import zio.blocks.chunk.Chunk
import scala.collection.mutable

/**
 * Demonstrates writer composition with ++ (concat), transformation with
 * contramap, and error handling via fail(). Shows how multiple writers can be
 * chained and how transformations are applied before writing.
 */
object WriterCompositionExample extends App {

  println("=== concat: ++ operator ===")
  val results = mutable.ArrayBuffer[Int]()

  val w1 = new Writer[Int] {
    def isClosed      = false
    def write(a: Int) = {
      results += a * 10
      a < 50 // reject values >= 50
    }
    def close(): Unit = ()
  }

  val w2 = new Writer[Int] {
    def isClosed      = false
    def write(a: Int) = {
      results += a * 100
      true
    }
    def close(): Unit = ()
  }

  val combined = w1 ++ w2

  combined.write(10) // accepted by w1 (10 < 50)
  combined.write(60) // rejected by w1, switches to w2
  println(s"Results: ${results.toList}")

  println("\n=== contramap: transform elements ===")
  val stringResults = mutable.ArrayBuffer[String]()
  val stringWriter  = new Writer[String] {
    def isClosed         = false
    def write(a: String) = {
      stringResults += a
      true
    }
    def close(): Unit = ()
  }

  val intWriter = stringWriter.contramap[Int](_.toString)
  intWriter.write(42)
  intWriter.write(99)
  println(s"String results: ${stringResults.toList}")

  println("\n=== Multiple contramap: chained transformations ===")
  val doubleResults      = mutable.ArrayBuffer[String]()
  val doubleStringWriter = new Writer[String] {
    def isClosed         = false
    def write(a: String) = {
      doubleResults += a
      true
    }
    def close(): Unit = ()
  }

  val intDoubleWriter = doubleStringWriter
    .contramap[Double](d => s"${d * 2}")
    .contramap[Int](i => i.toDouble)

  intDoubleWriter.write(5)  // 5 -> 5.0 -> "10.0"
  intDoubleWriter.write(10) // 10 -> 10.0 -> "20.0"
  println(s"Double results: ${doubleResults.toList}")

  println("\n=== fail: error closure ===")
  val failWriter = new Writer[Int] {
    private var closed = false
    def isClosed       = closed
    def write(a: Int)  =
      if (closed) false else { println(s"Write: $a"); true }
    def close(): Unit                         = closed = true
    override def fail(error: Throwable): Unit = {
      closed = true
      println(s"Failed with: ${error.getMessage}")
    }
  }

  failWriter.write(1)
  failWriter.fail(new RuntimeException("Oops"))
  println(s"isClosed after fail: ${failWriter.isClosed}")

  println("\n=== writeAll: bulk operations ===")
  val bulkResults = mutable.ArrayBuffer[Int]()
  val bulkWriter  = Writer.limited(
    new Writer[Int] {
      def isClosed      = false
      def write(a: Int) = { bulkResults += a; true }
      def close(): Unit = ()
    },
    2
  )

  val chunk     = Chunk(1, 2, 3, 4)
  val unwritten = bulkWriter.writeAll(chunk)
  println(s"Bulk results: ${bulkResults.toList}")
  println(s"Unwritten: $unwritten")
}
```

Run this example with:

```bash
sbt "streams-examples/runMain writer.WriterCompositionExample"
```

### I/O Adapters

This example demonstrates I/O integration with `Writer.fromOutputStream` and `Writer.fromWriter` for streaming to files or character streams:

```scala title="streams-examples/src/main/scala/writer/WriterIOAdapterExample.scala" 
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

package writer

import zio.blocks.streams.io.Writer
import java.io.{ByteArrayOutputStream, StringWriter}

/**
 * Demonstrates I/O integration with Writer via fromOutputStream and fromWriter.
 * Shows how to write bytes to streams and characters to writers using the
 * Writer interface.
 */
object WriterIOAdapterExample extends App {

  println("=== Writer.fromOutputStream ===")
  val byteStream = new ByteArrayOutputStream()
  val byteWriter = Writer.fromOutputStream(byteStream)

  byteWriter.write(72.toByte)  // 'H'
  byteWriter.write(105.toByte) // 'i'
  byteWriter.write(33.toByte)  // '!'
  byteWriter.close()

  println(s"Output: ${byteStream.toString("UTF-8")}")

  println("\n=== writeBytes: bulk byte write ===")
  val byteStream2 = new ByteArrayOutputStream()
  val byteWriter2 = Writer.fromOutputStream(byteStream2)

  val message      = "Hello".getBytes("UTF-8")
  val bytesWritten = byteWriter2.writeBytes(message, 0, message.length)
  byteWriter2.close()

  println(s"Bytes written: $bytesWritten")
  println(s"Output: ${byteStream2.toString("UTF-8")}")

  println("\n=== Writer.fromWriter ===")
  val charStream = new StringWriter()
  val charWriter = Writer.fromWriter(charStream)

  charWriter.write('H')
  charWriter.write('e')
  charWriter.write('l')
  charWriter.write('l')
  charWriter.write('o')
  charWriter.close()

  println(s"Output: ${charStream.toString}")

  println("\n=== writeChar: individual character writes ===")
  val charStream2 = new StringWriter()
  val charWriter2 = Writer.fromWriter(charStream2)

  val greeting = "Hi!"
  for (c <- greeting) {
    val result = charWriter2.writeChar(c)
    println(s"Write '$c': $result")
  }
  charWriter2.close()

  println(s"Output: ${charStream2.toString}")

  println("\n=== Specialized numeric writes ===")
  val charStream3 = new StringWriter()
  val charWriter3 = Writer.fromWriter(charStream3)

  // Note: These specialized methods require the writer to be typed to accept them
  // For a demo, we'll just show the interface exists
  println("Specialized write methods available:")
  println("  - writeInt(value: Int)")
  println("  - writeLong(value: Long)")
  println("  - writeFloat(value: Float)")
  println("  - writeDouble(value: Double)")
  println("  - writeBoolean(value: Boolean)")
  println("  - writeShort(value: Short)")

  charWriter3.close()

  println("\n=== Error handling in I/O ===")
  val closedStream = new ByteArrayOutputStream()
  closedStream.close()
  val failingWriter = Writer.fromOutputStream(closedStream)

  val writeResult = failingWriter.write(65.toByte) // 'A'
  println(s"Write to closed stream: $writeResult")
  println(s"Writer is closed: ${failingWriter.isClosed}")
}
```

Run this example with:

```bash
sbt "streams-examples/runMain writer.WriterIOAdapterExample"
```

### Bounded Implementation

This example shows how to implement a bounded Writer that wraps a fixed-capacity container and auto-closes when full. It demonstrates the protocol: `write()` returns `false` only on closure (not buffer fullness), and `writeable()` reflects closure state:

```scala title="streams-examples/src/main/scala/writer/WriterBoundedImplementationExample.scala" 
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

package writer

import zio.blocks.streams.io.Writer
import scala.collection.mutable

/**
 * Demonstrates implementing a bounded Writer that auto-closes when capacity is
 * reached. Shows how write() returns false only on closure, not on buffer
 * fullness, and how writeable() reflects the closure state.
 */
object WriterBoundedImplementationExample extends App {

  class BoundedWriter[A](maxCapacity: Int) extends Writer[A] {
    private val buffer = mutable.Buffer[A]()
    private var closed = false

    def isClosed: Boolean = closed

    def write(a: A): Boolean =
      if (closed) false
      else if (buffer.size < maxCapacity) {
        buffer += a
        true
      } else {
        // Buffer full: auto-close and reject
        closed = true
        false
      }

    def close(): Unit = closed = true

    override def fail(error: Throwable): Unit = close()

    def contents: mutable.Buffer[A] = buffer
  }

  println("=== Bounded Writer with auto-close ===")
  val bounded = new BoundedWriter[Int](3)

  println(s"Write 10: ${bounded.write(10)}")
  println(s"Write 20: ${bounded.write(20)}")
  println(s"Write 30: ${bounded.write(30)}")
  println(s"Write 40 (buffer full, auto-closes): ${bounded.write(40)}")
  println(s"Write 50 (closed): ${bounded.write(50)}")

  println(s"\nBuffer contents: ${bounded.contents}")
  println(s"Writer closed: ${bounded.isClosed}")
  println(s"Writeable: ${bounded.writeable()}")

  println("\n=== Behavior summary ===")
  println("• write() returns true while space exists")
  println("• When buffer fills, write() auto-closes and returns false")
  println("• All subsequent write() calls return false (closure is permanent)")
  println("• writeable() reflects closure state, not buffer capacity")
}
```

Run this example with:

```bash
sbt "streams-examples/runMain writer.WriterBoundedImplementationExample"
```
