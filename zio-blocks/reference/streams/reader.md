# Reader

> `Reader[+Elem]` is the **pull-based source that powers ZIO Blocks streams**. When you call a terminal operation like `stream.run(sink)`, the stream compiles into a `Reader`, which yields values one at a time on demand until closed.

`Reader[+Elem]` is the **pull-based source that powers ZIO Blocks streams**. When you call a terminal operation like `stream.run(sink)`, the stream compiles into a `Reader`, which yields values one at a time on demand until closed. 

The fundamental operations are `read(sentinel)` — returns the next element or a sentinel when exhausted — and `close()` — signals stream end and releases resources. Most users never interact with `Reader` directly, but understanding it clarifies how streams work internally.

The compilation and execution flow:

```
Stream[E, A] ──(compile)──> Reader[A]
                              │
                              └─(drain via Sink)──> Either[E, Z]
```

`Reader`:
- Is lazy and pull-based — `Stream` transformations don't run until `read()` is called, running in constant space one element at a time
- Is not thread-safe — designed for single-threaded consumption
- Uses a sentinel protocol where callers specify the end-of-stream value; for primitives, specialized methods like `Reader#readInt(sentinel)` avoid boxing entirely
- Dispatches on `Reader#jvmType` to use specialized, unboxed reads for primitive types
- Is the compilation target of `Stream` — when a stream runs, it becomes a `Reader`
- Guarantees resource safety by tracking and closing files, database connections, and buffers via `finally` blocks, even if consumption stops early or fails
- Supports composition by chaining readers through transformations without materializing intermediate data

Here is the core `Reader` interface with the most essential methods:

```scala
abstract class Reader[+Elem] {
  def read[A >: Elem](sentinel: A): A
  def close(): Unit
  def isClosed: Boolean
  def readable(): Boolean
}
```

## Quick Showcase

Here's how to create and drain a `Reader`:

```scala
import zio.blocks.streams.io.Reader
import zio.blocks.chunk.Chunk
import scala.collection.mutable.Buffer

val r = Reader.fromChunk(Chunk(1, 2, 3, 4, 5))
// r: Reader[Int] = zio.blocks.streams.io.Reader$FromChunkInt@2305ca8e
val collected = Buffer[Int]()
// collected: Buffer[Int] = ArrayBuffer(1, 2, 3, 4, 5)

// Pull elements until sentinel
def drainAll(): Unit = {
  val elem = r.read(-1)
  if (elem != -1) {
    collected += elem
    drainAll()
  }
}
drainAll()

println(s"Collected: $collected")
// Collected: ArrayBuffer(1, 2, 3, 4, 5)
```

## Motivation

Imagine you're processing a massive CSV file—millions of rows of customer data. Your first instinct is to load it all into memory as a `List[Row]`, transform it, filter it, and then write the results. This works fine for small files, but one day someone feeds you a 50GB dataset and your application crashes with `OutOfMemoryError`. You've hit the fundamental problem of eager evaluation: **you must load everything before you can do anything**, and if the data is bigger than available memory, you're stuck.

Even if you manage to fit the data in memory, you've paid the startup cost upfront. If your pipeline only needs the first 100 rows to produce a result, you've wasted time and energy materializing the other millions. And if something fails partway through—a database connection drops, a file is corrupted—you've already consumed resources and may have inconsistencies to clean up.

The streaming intuition is different: instead of pulling all data at once, what if the consumer asked the producer "give me the next element?" one at a time? This way, you never hold more than one element in memory, you only do work on elements you actually use, and you can stop immediately when you have enough.

`Reader` embodies this pull-based philosophy. Rather than materializing a `List`, a `Stream` compiles down to a `Reader`—a stateful object that produces one element each time you call `read()`. The consumer (a `Sink`) drives the pace: it calls `read()` when ready, and the `Reader` computes and returns the next value. When the stream is exhausted, `Reader` returns a sentinel—a special value you provide—signaling "no more data." No exceptions, no null, no boxing overhead.

`Reader` shines when you're processing large, unbounded, or expensive-to-produce data sources: database result sets, network streams, log files, sensor data, or any pipeline where memory or time efficiency matters. Instead of hoping your data fits in memory, you pay a constant, predictable cost per element.

## Construction

Several ways to create a `Reader`, from predefined singletons to collections and I/O sources:

### Creating Predefined Readers

`Reader.closed` — An already-closed reader that emits no elements. Useful as a base case or for empty streams:

```scala
object Reader {
  def closed: Reader[Nothing]
}
```

Here's how to create and use a closed reader:

```scala
import zio.blocks.streams.io.Reader

val r = Reader.closed
// r: Reader[Nothing] = zio.blocks.streams.io.Reader$ClosedReader$@55f2a900
println(r.isClosed)        // true
// true
println(r.read(-1))        // -1 (the sentinel)
// -1
```

### From Collections

`Reader.fromChunk` — Creates a reader backed by a `Chunk`. Dispatches on the element type to use specialized, unboxed reads for primitives:

```scala
object Reader {
  def fromChunk[A](chunk: Chunk[A])(implicit jt: JvmType.Infer[A]): Reader[A]
}
```

Create a reader from a chunk and drain its elements:

```scala
import zio.blocks.streams.io.Reader
import zio.blocks.chunk.Chunk

val chunk = Chunk(10, 20, 30)
// chunk: Chunk[Int] = IndexedSeq(10, 20, 30)
val r = Reader.fromChunk(chunk)
// r: Reader[Int] = zio.blocks.streams.io.Reader$FromChunkInt@8388986

def drain(): Unit = {
  val v = r.read(-1)
  if (v != -1) {
    println(v)
    drain()
  }
}
drain()
// 10
// 20
// 30
// Output: 10, 20, 30
```

`Reader.fromIterable` — Creates a reader from any `Iterable`. Works with lists, sets, vectors, and other collections:

```scala
object Reader {
  def fromIterable[A](it: Iterable[A]): Reader[A]
}
```

Create a reader from a list and consume its elements:

```scala
import zio.blocks.streams.io.Reader

val list = List("a", "b", "c")
// list: List[String] = List("a", "b", "c")
val r = Reader.fromIterable(list)
// r: Reader[String] = zio.blocks.streams.io.Reader$FromIterable@76a099be

def drain(): Unit = {
  val v = r.read(null)
  if (v != null) {
    println(v)
    drain()
  }
}
drain()
// a
// b
// c
// Output: a, b, c
```

`Reader.fromRange` — Creates a reader from a Scala `Range`. Optimized for integer ranges without allocation:

```scala
object Reader {
  def fromRange(range: Range): Reader[Int]
}
```

Create a reader from a range and drain the integers:

```scala
import zio.blocks.streams.io.Reader

val r = Reader.fromRange(1 to 5)
// r: Reader[Int] = zio.blocks.streams.io.Reader$FromRange@5a4e4971

def drain(): Unit = {
  val v = r.read(-1)
  if (v != -1) {
    println(v)
    drain()
  }
}
drain()
// 1
// 2
// 3
// 4
// 5
// Output: 1, 2, 3, 4, 5
```

### From I/O

`Reader.fromInputStream` — Wraps a `java.io.InputStream` as a `Reader[Int]`, where each byte is widened to `Int` (0–255). This avoids boxing on `.map`/`.filter` since `Function1` is specialized for `Int`:

```scala
object Reader {
  def fromInputStream(is: InputStream): Reader[Int]
}
```

`Reader.fromReader` — Wraps a `java.io.Reader` as a `Reader[Char]` for character-based I/O:

```scala
object Reader {
  def fromReader(r: java.io.Reader): Reader[Char]
}
```

### Single Element

`Reader.single` — Creates a reader that emits exactly one element, then closes. Primitive types use specialized variants for zero-boxing:

```scala
object Reader {
  def single[A](value: A)(implicit jt: JvmType.Infer[A]): Reader[A]
  def singleInt(value: Int): Reader[Int]
  def singleLong(value: Long): Reader[Long]
  def singleFloat(value: Float): Reader[Float]
  def singleDouble(value: Double): Reader[Double]
  def singleChar(value: Char): Reader[Char]
  def singleShort(value: Short): Reader[Short]
  def singleByte(value: Byte): Reader[Int]
  def singleBoolean(value: Boolean): Reader[Boolean]
}
```

When you use `Reader.single`, behavior differs between reference types and primitives. The `JvmType.Infer[A]` implicit parameter enables compile-time type detection, automatically selecting the appropriate implementation (specialized primitive or reference-type generic).

For reference types like String, `Reader.single("hello")` stores the element directly and uses an internal sentinel object (`EndOfStream`) to signal end-of-stream. You read via the generic `Reader#read[A](sentinel)` method, passing your own sentinel value. On the first call, you get your string; on subsequent calls, you receive the sentinel you provided, allowing you to detect stream closure.

For primitive types, `Reader.single(42)` could naively box the integer, but the library avoids this penalty entirely via `SingletonPrim`—a zero-boxing specialization that stores the primitive unboxed in memory. The `JvmType.Infer` implicit detects this at compile time and routes you through specialized factory methods (`Reader.singleInt`, `Reader.singleLong`, etc.) and specialized read methods (`Reader#readInt`, `Reader#readLong`, etc.). Both storage and retrieval stay unboxed, maintaining zero-copy efficiency.

Note: `Reader.singleByte` returns `Reader[Int]` (not `Reader[Byte]`) because Java's primitive byte type is typically widened to int in arrays and I/O contexts; this aligns with JVM conventions for byte-level operations. When reading, use `Reader#readInt(sentinel: Long): Long`, which returns a long to maintain the sentinel protocol—extract the int via casting if needed.

Create and read from a single-element reference-type reader with a custom sentinel:

```scala
import zio.blocks.streams.io.Reader

val r = Reader.single("hello")
// r: Reader[String] = zio.blocks.streams.io.Reader$SingletonGeneric@4ccaf3f7
val sentinel = "END"
// sentinel: String = "END"
println(r.read(sentinel))    // hello
// hello
println(r.read(sentinel))    // END (sentinel, reader is closed)
// END
```

For primitive types, use the specialized factory and read methods. The `Reader#readInt` method takes a `Long` sentinel (to avoid confusion with sentinel values that fit in int range) and returns `Long` so you can distinguish the actual int value from the sentinel:

```scala
import zio.blocks.streams.io.Reader

val r = Reader.singleInt(100)
// r: Reader[Int] = zio.blocks.streams.io.Reader$SingletonPrim@56f86b9b
val sentinel = Long.MinValue
// sentinel: Long = -9223372036854775808L
val v1 = r.readInt(sentinel)
// v1: Long = 100L
println(v1)    // 100
// 100
val v2 = r.readInt(sentinel)
// v2: Long = -9223372036854775808L
println(v2)    // -9223372036854775808 (sentinel, reader is closed)
// -9223372036854775808
```

### Infinite & Repeating

`Reader.repeat` — Creates an infinite reader that always emits the same value:

```scala
object Reader {
  def repeat[A](a: A)(implicit jt: JvmType.Infer[A]): Reader[A]
}
```

Create an infinite reader that repeatedly emits the same value:

```scala
import zio.blocks.streams.io.Reader

val r = Reader.repeat(1)
// r: Reader[Int] = zio.blocks.streams.io.Reader$SingletonPrim@7c91cdb2

def drainN(n: Int): Unit = {
  if (n > 0) {
    val v = r.read(-1)
    println(v)
    drainN(n - 1)
  }
}
drainN(3)
// 1
// 1
// 1
// Output: 1, 1, 1
```

`Reader.repeated` — Restarts an inner reader each time it closes cleanly. Used by `Stream.repeated` to create indefinitely repeating streams:

```scala
object Reader {
  def repeated[A](inner: Reader[A]): Reader[A]
}
```

### Unfold (State Machine)

`Reader.unfold` — Creates a reader by unfolding state with a function. Returns `None` to signal completion, or `Some((elem, nextState))` to emit an element and advance state:

```scala
object Reader {
  def unfold[S, A](s: S)(f: S => Option[(A, S)]): Reader[A]
}
```

Create a reader that unfolds state incrementally until completion:

```scala
import zio.blocks.streams.io.Reader

val r = Reader.unfold(1) { s =>
  if (s > 3) None else Some((s, s + 1))
}
// r: Reader[Int] = zio.blocks.streams.io.Reader$Unfold@65f92a5f

def drain(): Unit = {
  val v = r.read(-1)
  if (v != -1) {
    println(v)
    drain()
  }
}
drain()
// 1
// 2
// 3
// Output: 1, 2, 3
```

## Core Operations

These methods form the primary interface for consuming elements and querying reader state:

### Pulling Elements

`Reader#read` — Pulls the next element, or returns `sentinel` if the reader is closed and empty. This is the fundamental operation: call it repeatedly to consume all elements until it returns your sentinel value:

```scala
abstract class Reader[+Elem] {
  def read[A >: Elem](sentinel: A): A
}
```

The sentinel value is caller-chosen and should never appear as a real element. For reference types, `null` is convenient. For primitives, use a value outside the domain (e.g., `-1` for unsigned bytes, `Long.MinValue` for `Int`):

```scala
import zio.blocks.streams.io.Reader
import zio.blocks.chunk.Chunk

val r = Reader.fromChunk(Chunk(10, 20))
// r: Reader[Int] = zio.blocks.streams.io.Reader$FromChunkInt@677263ef
val v1 = r.read(-1)        // 10
// v1: Int = 10
val v2 = r.read(-1)        // 20
// v2: Int = 20
val v3 = r.read(-1)        // -1 (sentinel, reader is closed)
// v3: Int = -1
```

### Primitive Specialization

For primitive types, specialized methods avoid boxing by widening the return type.

`Reader#readInt` — Sentinel-return `Int` pull. Returns the element widened to `Long`, or `sentinel` when closed. The sentinel must lie outside `[Int.MinValue, Int.MaxValue]` (typically `Long.MinValue`):

```scala
abstract class Reader[+Elem] {
  def readInt(sentinel: Long)(using Elem <:< Int): Long
}
```

Why widen to `Long`? If `Reader#readInt` returned `Int`, you couldn't distinguish a real element from the sentinel—both would fit in the int range. By widening to `Long`, the sentinel (e.g., `Long.MinValue`) lies outside the possible int domain, allowing reliable end-of-stream detection. Cast the result back to `Int` if needed: `r.readInt(Long.MinValue).toInt`.

`Reader#readLong` — Sentinel-return `Long` pull. Returns the element, or `sentinel` when closed. The sentinel must be a value that never appears in the stream (typically `Long.MaxValue`):

```scala
abstract class Reader[+Elem] {
  def readLong(sentinel: Long)(using Elem <:< Long): Long
}
```

:::note[Sentinel Collisions Are Disambiguated]
Unlike `Reader#readInt` which widens to `Long`, `Reader#readLong` has no wider type to safely house the sentinel — a real `Long.MaxValue` element and end-of-stream both come back as the sentinel value. To disambiguate, every read records an out-of-band flag, exposed as `Reader#lastReadWasEOF`: after a read that returned the sentinel, `lastReadWasEOF` is `true` only for genuine end-of-stream. The library's own drain loops test `v == sentinel && reader.lastReadWasEOF`, so streams containing the sentinel value are processed losslessly; manual pull loops should do the same.

**Performance Tradeoff:** `Reader#readLong` avoids boxing on every read—the long stays unboxed in memory, and retrieval is a simple memory fetch. In contrast, `Reader#read[Long](sentinel)` boxes each long into a generic `Any` reference, forcing allocation and garbage collection pressure in hot loops. For latency-sensitive or high-throughput workloads (millions of elements per second), this difference is measurable. The `lastReadWasEOF` check costs nothing on the hot path — it only needs consulting on the rare value/sentinel collision.
:::

`Reader#readFloat` — Sentinel-return `Float` pull. Returns the element widened to `Double`, or `sentinel` when closed:

```scala
abstract class Reader[+Elem] {
  def readFloat(sentinel: Double)(using Elem <:< Float): Double
}
```

Like `Reader#readInt`, widening to `Double` allows the sentinel to lie safely outside the float domain. A float value will always fit in the lower precision bits of the double result, and the sentinel (typically `Double.MaxValue`) occupies the upper range. This ensures you can reliably distinguish real float elements from end-of-stream. Cast back to `Float` if needed: `r.readFloat(Double.MaxValue).toFloat`.

`Reader#readDouble` — Sentinel-return `Double` pull. Returns the element, or `sentinel` when closed. The sentinel must be a value outside the domain (typically `Double.MaxValue`):

```scala
abstract class Reader[+Elem] {
  def readDouble(sentinel: Double)(using Elem <:< Double): Double
}
```

:::danger[Sentinel Collision Risk for Doubles]
Like `Reader#readLong`, `Reader#readDouble` has no wider type to safely contain the sentinel. If your actual data stream contains `Double.MaxValue` or the sentinel you chose, you will incorrectly detect end-of-stream mid-stream. Always verify that your data domain excludes the chosen sentinel value. Alternatively, use `Reader#read[Double](sentinel)` (the generic method) if you need the flexibility to choose any sentinel regardless of your data—this trades performance (boxing on every read) for safety.
:::

These specialized methods are the hot path for primitive streams — they avoid allocation and boxing entirely:

```scala
import zio.blocks.streams.io.Reader
import zio.blocks.chunk.Chunk

val r = Reader.fromChunk(Chunk(10, 20, 30))
// r: Reader[Int] = zio.blocks.streams.io.Reader$FromChunkInt@f89374e
val sentinel = Long.MinValue
// sentinel: Long = -9223372036854775808L

val v = r.readInt(sentinel)
// v: Long = 10L
```

### Byte-Level Reading

`Reader#readByte` — Reads a single byte (0–255), widened to `Int`. Returns `-1` when the reader is closed. Dispatches on `Reader#jvmType` for zero-boxing when the reader is specialized:

```scala
abstract class Reader[+Elem] {
  def readByte(): Int
}
```

Read bytes one at a time from a reader until end-of-stream:

```scala
import zio.blocks.streams.io.Reader
import java.io.ByteArrayInputStream

val bytes = Array[Byte](72, 101, 108, 108, 111)  // Hello in ASCII bytes
// bytes: Array[Byte] = Array(72, 101, 108, 108, 111)
val is = new ByteArrayInputStream(bytes)
// is: ByteArrayInputStream = java.io.ByteArrayInputStream@78144ba7
val r = Reader.fromInputStream(is)
// r: Reader[Byte] = zio.blocks.streams.io.Reader$InputStreamReader@aafbf86

def drainBytes(): Unit = {
  val b = r.readByte()
  if (b != -1) {
    println(s"Byte: $b (${b.toChar})")
    drainBytes()
  }
}
drainBytes()
// Byte: 72 (H)
// Byte: 101 (e)
// Byte: 108 (l)
// Byte: 108 (l)
// Byte: 111 (o)
// Output:
// Byte: 72 (H)
// Byte: 101 (e)
// Byte: 108 (l)
// Byte: 108 (l)
// Byte: 111 (o)
```

`Reader#readBytes` — Bulk byte read into a caller-supplied buffer, mirroring `java.io.InputStream#read(byte[], int, int)`. The behavior is:

- Blocks until at least 1 byte is available.
- Returns the number of bytes read (`1 <= r <= len`).
- Returns `-1` when closed and empty.
- Returns `0` immediately when `len == 0`.

The method signature is:

```scala
abstract class Reader[+Elem] {
  def readBytes(buf: Array[Byte], offset: Int, len: Int): Int
}
```

Read multiple bytes into a buffer in bulk with a loop pattern:

```scala
import zio.blocks.streams.io.Reader
import java.io.ByteArrayInputStream

val bytes = Array[Byte](72, 101, 108, 108, 111)  // The word Hello
// bytes: Array[Byte] = Array(72, 101, 108, 108, 111)
val is = new ByteArrayInputStream(bytes)
// is: ByteArrayInputStream = java.io.ByteArrayInputStream@1ba77670
val r = Reader.fromInputStream(is)
// r: Reader[Byte] = zio.blocks.streams.io.Reader$InputStreamReader@144c778

val buffer = new Array[Byte](3)
// buffer: Array[Byte] = Array(108, 111, 108)

def drainBulk(): Unit = {
  val bytesRead = r.readBytes(buffer, 0, 3)
  if (bytesRead > 0) {
    val chunk = buffer.take(bytesRead).map(_.toChar).mkString
    println(s"Read $bytesRead bytes: $chunk")
    drainBulk()
  }
}
drainBulk()
// Read 3 bytes: Hel
// Read 2 bytes: lo
// Output:
// Read 3 bytes: Hel
// Read 2 bytes: lo
```

### Character and Numeric Specialization

`Reader#readChar` — Sentinel-return `Char` pull. Returns the element widened to `Int`, or `sentinel` when closed. Requires evidence that `Elem <:< Char`:

```scala
abstract class Reader[+Elem] {
  def readChar(sentinel: Int)(using Elem <:< Char): Int
}
```

`Reader#readShort` — Sentinel-return `Short` pull. Returns the element widened to `Int`, or `sentinel` when closed:

```scala
abstract class Reader[+Elem] {
  def readShort(sentinel: Int)(using Elem <:< Short): Int
}
```

`Reader#readBoolean` — Sentinel-return `Boolean` pull. Returns `1` for `true`, `0` for `false`, or `sentinel` when closed. The sentinel must lie outside `[0, 1]` (typically `-1`):

```scala
abstract class Reader[+Elem] {
  def readBoolean(sentinel: Int)(using Elem <:< Boolean): Int
}
```

### Bulk Operations

`Reader#readAll` — Drains the entire reader into a `Chunk`. Dispatches on `Reader#jvmType` for zero-boxing on primitive readers:

```scala
abstract class Reader[+Elem] {
  def readAll[A >: Elem](): Chunk[A]
}
```

The result is a new chunk containing all remaining elements:

```scala
import zio.blocks.streams.io.Reader
import zio.blocks.chunk.Chunk

val r = Reader.fromChunk(Chunk(10, 20, 30))
// r: Reader[Int] = zio.blocks.streams.io.Reader$FromChunkInt@22ee08e9
val all = r.readAll()
// all: Chunk[Int] = IndexedSeq(10, 20, 30)
println(all)  // Chunk(10, 20, 30)
// Chunk(10,20,30)
```

`Reader#skip` — Eagerly discards the first `n` elements. Dispatches on `Reader#jvmType` for zero-boxing when possible:

```scala
abstract class Reader[+Elem] {
  def skip(n: Long): Unit
}
```

### State Queries

`Reader#isClosed` — Returns `true` if the reader is closed. Monotone: once `true`, never returns `false`:

```scala
abstract class Reader[+Elem] {
  def isClosed: Boolean
}
```

`Reader#readable` — Returns `true` if the next `read()` would return a value (not the sentinel). Default implementation returns `!isClosed`. Buffered readers can override `readable()` for accuracy to peek ahead without consuming:

```scala
abstract class Reader[+Elem] {
  def readable(): Boolean
}
```

Use `readable()` to check if elements are available before calling `read()`:

```scala
import zio.blocks.streams.io.Reader
import zio.blocks.chunk.Chunk

val r = Reader.fromChunk(Chunk(1, 2))
// r: Reader[Int] = zio.blocks.streams.io.Reader$FromChunkInt@65ea641
println(r.readable())      // true
// true
r.read(-1)
// res32: Int = 1
println(r.readable())      // true
// true
r.read(-1)
// res34: Int = 2
println(r.readable())      // false
// false
```

## Composition

Combine multiple readers to build more complex sources:

### Concatenation

`Reader#concat` — Concatenates this reader with `next`. When this reader is exhausted, it is closed and elements are pulled from `next` (evaluated lazily). Optimized for left-associative chains:

```scala
abstract class Reader[+Elem] {
  def concat[Elem2 >: Elem](next: () => Reader[Elem2]): Reader[Elem2]
}
```

`Reader#++` — Alias for `Reader#concat`. Syntactic sugar for composing readers:

```scala
abstract class Reader[+Elem] {
  def ++[Elem2 >: Elem](next: => Reader[Elem2]): Reader[Elem2]
}
```

Here is how concatenation chains multiple readers together:

```scala
import zio.blocks.streams.io.Reader
import zio.blocks.chunk.Chunk

val r1 = Reader.fromChunk(Chunk(1, 2))
// r1: Reader[Int] = zio.blocks.streams.io.Reader$FromChunkInt@2f0f9a51
val r2 = Reader.fromChunk(Chunk(3, 4))
// r2: Reader[Int] = zio.blocks.streams.io.Reader$FromChunkInt@4a811b20
val combined = r1 ++ r2
// combined: Reader[Int] = zio.blocks.streams.io.Reader$ConcatReader@1eb0ee36

def drain(): Unit = {
  val v = combined.read(-1)
  if (v != -1) {
    println(v)
    drain()
  }
}
drain()
// 1
// 2
// 3
// 4
// Output: 1, 2, 3, 4
```

**Optimization**: If this reader is already a `ConcatReader`, the thunk is appended to its internal array and `this` is returned (mutable append, O(1) amortized). Otherwise a new `ConcatReader` is created. This ensures that left-associative chains like `a ++ b ++ c ++ d` compile into a single flat `ConcatReader` with O(1) per-element read, rather than O(n) nested wrappers.

## Resource Management

Close readers and attach cleanup callbacks:

### Closing

`Reader#close` — Signals end-of-stream from the consumer side and releases any held resources. Implementations set internal closed state and wake any blocked readers. This is always called in a `finally` block by sinks to guarantee resource cleanup:

```scala
abstract class Reader[+Elem] {
  def close(): Unit
}
```

`Reader#withRelease` — Wraps this reader so that `release` runs after `Reader#close()`. Useful for attaching cleanup logic:

```scala
abstract class Reader[+Elem] {
  def withRelease(release: () => Unit): Reader[Elem]
}
```

Here is how cleanup logic is attached to a reader:

```scala
import zio.blocks.streams.io.Reader
import zio.blocks.chunk.Chunk
import scala.sys.Prop

val cleanupRef = scala.collection.mutable.ListBuffer[String]()
// cleanupRef: ListBuffer[String] = ListBuffer("cleaned")
val r = Reader.fromChunk(Chunk(1, 2)).withRelease { () =>
  cleanupRef += "cleaned"
  println("Cleaned up")
}
// r: Reader[Int] = zio.blocks.streams.io.Reader$$anon$1@697b73b8

r.close()
// Cleaned up
println(cleanupRef.nonEmpty)  // true
// true
```

## Pushdown Operations

Readers can sometimes handle skip, limit, and repeat operations natively (O(1), zero per-element cost). These methods attempt that; if the reader cannot handle it natively, they return `false` and the caller must wrap the reader.

`Reader#setSkip` — Attempts to set a skip (drop) on this reader. Returns `true` if handled natively, `false` if the caller must wrap. When `true`, the next n elements are discarded before producing. After `Reader#reset()`, the skip is re-applied:

```scala
abstract class Reader[+Elem] {
  def setSkip(n: Long): Boolean
}
```

Set a skip to discard the first two elements:

```scala
import zio.blocks.streams.io.Reader
import zio.blocks.chunk.Chunk

val r = Reader.fromChunk(Chunk(1, 2, 3, 4, 5))
// r: Reader[Int] = zio.blocks.streams.io.Reader$FromChunkInt@377387c0
val handled = r.setSkip(2)
// handled: Boolean = true
println(s"Skip handled natively: $handled")
// Skip handled natively: true

def drain(): Unit = {
  val v = r.read(-1)
  if (v != -1) {
    println(v)
    drain()
  }
}
drain()
// 3
// 4
// 5
// Output:
// Skip handled natively: true
// 3
// 4
// 5
```

`Reader#setLimit` — Attempts to set a limit on this reader so it produces at most `n` elements. Returns `true` if handled natively, `false` if the caller must wrap. After `reset()`, the limit is re-applied from the new start position:

```scala
abstract class Reader[+Elem] {
  def setLimit(n: Long): Boolean
}
```

Set a limit to produce only three elements:

```scala
import zio.blocks.streams.io.Reader
import zio.blocks.chunk.Chunk

val r = Reader.fromChunk(Chunk(1, 2, 3, 4, 5))
// r: Reader[Int] = zio.blocks.streams.io.Reader$FromChunkInt@2e287808
val handled = r.setLimit(3)
// handled: Boolean = true
println(s"Limit handled natively: $handled")
// Limit handled natively: true

def drain(): Unit = {
  val v = r.read(-1)
  if (v != -1) {
    println(v)
    drain()
  }
}
drain()
// 1
// 2
// 3
// Output:
// Limit handled natively: true
// 1
// 2
// 3
```

`Reader#setRepeat` — Attempts to set this reader into repeat-forever mode, so it restarts from the beginning whenever it would otherwise close. Returns `true` if handled natively, `false` if the caller must wrap:

```scala
abstract class Reader[+Elem] {
  def setRepeat(): Boolean
}
```

Set repeat mode to emit elements multiple times:

```scala
import zio.blocks.streams.io.Reader
import zio.blocks.chunk.Chunk

val r = Reader.fromChunk(Chunk(1, 2))
// r: Reader[Int] = zio.blocks.streams.io.Reader$FromChunkInt@742ce9f
val handled = r.setRepeat()
// handled: Boolean = false
println(s"Repeat handled natively: $handled")
// Repeat handled natively: false

def drain(count: Int): Unit = {
  if (count < 6) {
    val v = r.read(-1)
    println(v)
    drain(count + 1)
  }
}
drain(0)
// 1
// 2
// -1
// -1
// -1
// -1
// Output:
// Repeat handled natively: true
// 1
// 2
// 1
// 2
// 1
// 2
```

`Reader#reset` — Rewinds this reader to its initial state, as if freshly constructed. After `Reader#reset()`, all elements are available again from the beginning. Not all readers support this; readers backed by one-shot resources (InputStreams, `java.io.Reader`s) throw `UnsupportedOperationException`:

```scala
abstract class Reader[+Elem] {
  def reset(): Unit
}
```

After rewinding, the reader starts from the beginning:

```scala
import zio.blocks.streams.io.Reader
import zio.blocks.chunk.Chunk

val r = Reader.fromChunk(Chunk(1, 2, 3))
// r: Reader[Int] = zio.blocks.streams.io.Reader$FromChunkInt@37838f3b
println(r.read(-1))  // 1
// 1
r.reset()
println(r.read(-1))  // 1 (back to the beginning)
// 1
```

## Integration with Stream

`Reader` is the compilation target of `Stream`. When you call a terminal operation, the stream compiles to a `Reader`, which is then consumed.

You can also open a stream for manual element-by-element pulling using `Stream#start`:

```scala
import zio.blocks.streams.*
import zio.blocks.streams.io.Reader
import zio.blocks.scope.*

Scope.global.scoped { scope =>
  import scope.*

  val reader: $[Reader[Int]] = Stream.range(1, 6).start(using scope)

  $(reader) { r =>
    def drain(): Unit = {
      val v = r.read(-1)
      if (v != -1) {
        println(v)   // prints 1, 2, 3, 4, 5
        drain()
      }
    }
    drain()
  }
  // reader is closed automatically when scope exits
}
```

:::caution
Avoid holding references to a `Reader` obtained via `Stream#start` outside its `Scope`. The scope guarantees cleanup; escaping the reader defeats that guarantee.
:::

## Integration with Sink

`Reader` and `Sink` are dual: `Reader` is the source, `Sink` is the consumer. When you call `stream.run(sink)`, the stream compiles to a `Reader`, and the sink drains it:

```scala
abstract class Sink[+E, -A, +Z] {
  def drain[A2 <: A](reader: Reader[A2]): Either[E, Z]
}
```

The sink calls `read()` repeatedly until the reader is closed, transforming the sequence of elements into a result of type `Z`.

For example, `Sink.collectAll` drains all elements and returns them as a `Chunk`:

```scala
import zio.blocks.streams._

val result = Stream.range(1, 10)
  .run(Sink.collectAll[Int])
// result: Either[Nothing, Chunk[Int]] = Right(
//   IndexedSeq(1, 2, 3, 4, 5, 6, 7, 8, 9)
// )
```

## Implementation Notes

Understand the design choices and mechanisms that power `Reader`:

### Sentinel Protocol

The `read(sentinel)` method uses a caller-chosen sentinel value to signal end-of-stream. This avoids the allocation and boxing of wrapping results in `Option` or `Either`. The sentinel must be a value that never appears as a real element.

For reference types, `null` is the natural sentinel. For primitives, specialized methods widen the return type and use fixed sentinels:

| Type   | Sentinel     | Method            | Return Type |
|--------|--------------|-------------------|-------------|
| `Int`  | `Long.MinValue` | `readInt(sentinel: Long)` | `Long`      |
| `Long` | `Long.MaxValue` | `readLong(sentinel: Long)` | `Long`      |
| `Float` | `Double.MaxValue` | `readFloat(sentinel: Double)` | `Double`   |
| `Double` | `Double.MaxValue` | `readDouble(sentinel: Double)` | `Double`   |

:::note
The `Long.MaxValue` and `Double.MaxValue` sentinels coincide with valid data values. To keep specialized paths lossless, every read additionally records an out-of-band `Reader#lastReadWasEOF` flag: a sentinel-valued result means end-of-stream only when the flag is set. Streams containing exactly those values are therefore processed without truncation, at zero cost on the hot path.
:::

### JVM Type Dispatch

`Reader` dispatches on `jvmType` to choose between unboxed and boxed pull paths. Subclasses with primitive specialization override `jvmType`:

```scala
abstract class Reader[+Elem] {
  def jvmType: JvmType = JvmType.AnyRef
}
```

For example, a `Reader[Int]` backed by a `Chunk[Int]` overrides `jvmType` to return `JvmType.Int`. Then, methods like `Reader#readAll` check `Reader#jvmType` and dispatch to the unboxed `Reader#readInt(sentinel: Long)` path instead of boxing.

### Thread Safety

`Reader` is **not thread-safe**. It is designed for single-threaded, pull-based consumption. Do not share a `Reader` across threads without external synchronization. If you need concurrent consumption, wrap the reader in a thread-safe queue or use a concurrent streaming library.

## Running the Examples

All code from this guide is available as runnable examples in the `streams-examples` module. Follow these steps to run them:

**Step 1** — Clone the repository and navigate to the project:

```bash
git clone https://github.com/zio/zio-blocks.git
cd zio-blocks
```

**Step 2** — Run individual examples with sbt:

### Basic Reader Construction

This example demonstrates the most common reader factories: `Reader.fromChunk`, `Reader.fromIterable`, `Reader.fromRange`, and `Reader.single`. Embed the source:

```scala title="streams-examples/src/main/scala/reader/ReaderBasicConstructionExample.scala" 
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

package reader

import zio.blocks.chunk.Chunk
import zio.blocks.streams.io.Reader

/**
 * Demonstrates the most common Reader factories: fromChunk, fromIterable,
 * fromRange, single, and unfold. Each reader is drained manually with read() to
 * show how to consume elements.
 */
object ReaderBasicConstructionExample extends App {

  println("=== Reader.fromChunk ===")
  val chunkReader = Reader.fromChunk(Chunk(10, 20, 30))
  var v           = chunkReader.read(-1)
  while (v != -1) {
    println(s"Read: $v")
    v = chunkReader.read(-1)
  }

  println("\n=== Reader.fromRange ===")
  val rangeReader = Reader.fromRange(1 to 3)
  v = rangeReader.read(-1)
  while (v != -1) {
    println(s"Read: $v")
    v = rangeReader.read(-1)
  }

  println("\n=== Reader.fromIterable ===")
  val listReader = Reader.fromIterable(List("a", "b", "c"))
  var sv         = listReader.read(null: String)
  while (sv != null) {
    println(s"Read: $sv")
    sv = listReader.read(null: String)
  }

  println("\n=== Reader.single ===")
  val singleReader = Reader.single(42)
  println(s"Read: ${singleReader.read(-1)}")
  println(s"Read again (closed): ${singleReader.read(-1)}")

  println("\n=== Reader.unfold ===")
  val unfoldReader = Reader.unfold(1) { s =>
    if (s > 3) None else Some((s * 10, s + 1))
  }
  v = unfoldReader.read(-1)
  while (v != -1) {
    println(s"Read: $v")
    v = unfoldReader.read(-1)
  }

  println("\n=== Reader state ===")
  val stateReader = Reader.fromChunk(Chunk(5, 6))
  println(s"readable before: ${stateReader.readable()}")
  stateReader.read(-1)
  println(s"readable after one read: ${stateReader.readable()}")
  stateReader.read(-1)
  println(s"readable after exhaustion: ${stateReader.readable()}")
  println(s"isClosed: ${stateReader.isClosed}")
}
```

Run it with:

```bash
sbt "streams-examples/runMain reader.ReaderBasicConstructionExample"
```

### Primitive Specialization and Bulk Operations

This example shows how primitive readers avoid boxing through `Reader#jvmType` dispatch, and demonstrates `Reader#readAll` and `Reader#skip` for bulk operations. Embed the source:

```scala title="streams-examples/src/main/scala/reader/ReaderPrimitiveSpecializationExample.scala" 
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

package reader

import zio.blocks.chunk.Chunk
import zio.blocks.streams.io.Reader

/**
 * Demonstrates primitive specialization in readers. When a Reader is backed by
 * primitive types (Int, Long, Float, Double), specialized factory methods like
 * singleInt, singleLong, etc. avoid boxing entirely. This example also shows
 * readAll for bulk consumption and skip for advancing the reader.
 */
object ReaderPrimitiveSpecializationExample extends App {

  println("=== singleInt (zero-boxed) ===")
  val intReader = Reader.singleInt(42)
  println(s"Read: ${intReader.read(-1)}")

  println("\n=== singleLong (zero-boxed) ===")
  val longReader = Reader.singleLong(9999999999L)
  println(s"Read: ${longReader.read(Long.MaxValue)}")

  println("\n=== singleFloat (zero-boxed) ===")
  val floatReader = Reader.singleFloat(3.14f)
  println(s"Read: ${floatReader.readFloat(Float.MaxValue)}")

  println("\n=== singleDouble (zero-boxed) ===")
  val doubleReader = Reader.singleDouble(2.718)
  println(s"Read: ${doubleReader.read(Double.MaxValue)}")

  println("\n=== readAll: bulk drain to Chunk ===")
  val bulkReader  = Reader.fromChunk(Chunk(1, 2, 3, 4, 5))
  val allElements = bulkReader.readAll()
  println(s"All elements: $allElements")

  println("\n=== skip: discard n elements ===")
  val skipReader = Reader.fromRange(10 to 15)
  skipReader.skip(2)
  // Should now read from 12 onward
  var v         = skipReader.read(-1)
  val remaining = scala.collection.mutable.ArrayBuffer[Int]()
  while (v != -1) {
    remaining += v
    v = skipReader.read(-1)
  }
  println(s"After skipping 2: ${remaining.toList}")

  println("\n=== reset: rewind to beginning ===")
  val resetReader = Reader.fromChunk(Chunk("x", "y", "z"))
  var elem        = resetReader.read(null: String)
  println(s"First read: $elem")
  resetReader.reset()
  elem = resetReader.read(null: String)
  println(s"After reset: $elem")

  println("\n=== readable: check if elements remain ===")
  val checkReader = Reader.fromChunk(Chunk(100, 200))
  println(s"readable before read: ${checkReader.readable()}")
  checkReader.read(-1)
  println(s"readable after one read: ${checkReader.readable()}")
  checkReader.read(-1)
  println(s"readable after exhaustion: ${checkReader.readable()}")
}
```

Run it with:

```bash
sbt "streams-examples/runMain reader.ReaderPrimitiveSpecializationExample"
```

### Composition and Resource Management

This example demonstrates reader composition with `Reader#++`, resource cleanup with `Reader#withRelease`, and integration with `Stream.start` for manual pulling. Embed the source:

```scala title="streams-examples/src/main/scala/reader/ReaderCompositionExample.scala" 
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

package reader

import zio.blocks.chunk.Chunk
import zio.blocks.streams.io.Reader
import zio.blocks.streams.Stream
import zio.blocks.scope.Scope

/**
 * Demonstrates reader composition with ++ (concat), resource cleanup with
 * withRelease, and integration with Stream.start for manual element-by-element
 * pulling within a Scope.
 */
object ReaderCompositionExample extends App {

  println("=== concat: ++ operator ===")
  val r1       = Reader.fromChunk(Chunk(1, 2, 3))
  val r2       = Reader.fromChunk(Chunk(4, 5, 6))
  val combined = r1 ++ r2

  var v           = combined.read(-1)
  val allCombined = scala.collection.mutable.ArrayBuffer[Int]()
  while (v != -1) {
    allCombined += v
    v = combined.read(-1)
  }
  println(s"Combined result: ${allCombined.toList}")

  println("\n=== Multiple concat: a ++ b ++ c ===")
  val ra    = Reader.fromChunk(Chunk("a"))
  val rb    = Reader.fromChunk(Chunk("b"))
  val rc    = Reader.fromChunk(Chunk("c"))
  val multi = ra ++ rb ++ rc

  var sv     = multi.read(null: String)
  val result = scala.collection.mutable.ArrayBuffer[String]()
  while (sv != null) {
    result += sv
    sv = multi.read(null: String)
  }
  println(s"Multiple concat: ${result.toList}")

  println("\n=== withRelease: cleanup on close ===")
  var cleanupCalled  = false
  val resourceReader = Reader.fromChunk(Chunk(10, 20)).withRelease { () =>
    cleanupCalled = true
    println("  Cleanup executed!")
  }
  var res = resourceReader.read(-1)
  while (res != -1) {
    res = resourceReader.read(-1)
  }
  resourceReader.close()
  println(s"Cleanup was called: $cleanupCalled")

  println("\n=== Stream.start: manual pull with Scope ===")
  Scope.global.scoped { scope =>
    import scope.*

    // Create a stream and open it for manual pulling
    val reader: scope.$[Reader[Int]] = Stream.range(1, 6).start(using scope)

    $(reader) { r =>
      var streamV      = r.read(-1)
      val manualResult = scala.collection.mutable.ArrayBuffer[Int]()
      while (streamV != -1) {
        manualResult += streamV
        streamV = r.read(-1)
      }
      println(s"Manual stream pull: ${manualResult.toList}")
    }
    // reader is automatically closed when scope exits
  }

  println("\n=== repeat: infinite reader ===")
  val infiniteReader = Reader.repeat(99)
  infiniteReader.setRepeat()

  var repeatCount = 0
  var repV        = infiniteReader.read(-1)
  while (repeatCount < 3) {
    println(s"Infinite read $repeatCount: $repV")
    repV = infiniteReader.read(-1)
    repeatCount += 1
  }
  infiniteReader.close()
}
```

Run it with:

```bash
sbt "streams-examples/runMain reader.ReaderCompositionExample"
```
