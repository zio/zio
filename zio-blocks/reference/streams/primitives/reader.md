# Reader

> The pull-based source behind ZIO Blocks streams: the SyncReader and AsyncReader kinds, the pull protocol, and the custom reader contracts.

`Reader[+Elem]` is the **pull-based source that powers ZIO Blocks streams**. When you call a terminal operation like `stream.run(sink)`, the stream compiles into a `Reader`, which yields values one at a time on demand until closed.

`Reader` has two library-provided kinds, `Reader.SyncReader[Elem]` and `Reader.AsyncReader[Elem]`. A synchronous reader's `read` and `close` return directly; an asynchronous reader's pull and lifecycle methods return `Async`. Most users never interact with either kind directly, but understanding them clarifies how streams work internally.

The compilation and execution flow:

```
Stream[E, A] ──(compile)──> Reader[A]
                              │
                              └─(drain via Sink)──> Either[E, Z]
```

`Reader`:
- Is lazy and pull-based — `Stream` transformations don't run until `read()` is called, running in constant space one element at a time
- Is a single-consumer cursor — do not share a `SyncReader` between threads or overlap operations on an `AsyncReader`
- Uses a sentinel protocol where callers specify the end-of-stream value; all eight JVM primitives have exact physical pull methods: `readBoolean`, `readByte`, `readChar`, `readShort`, `readInt`, `readLong`, `readFloat`, and `readDouble`. The `Long` and `Double` lanes are the exception: no sentinel is safe there, so they detect end of stream by the count returned from `readLongs` / `readDoubles`.
- Dispatches on `Reader#jvmType`, which describes the reader's physical representation and therefore the exact pull method it supports, not merely the static or logical element type
- Is the compilation target of `Stream` — when a stream runs, it becomes a `Reader`
- Transfers lifecycle responsibility explicitly: terminals and bracketed APIs close their owned reader, while callers of `startAsync` own the returned reader and must await `close()`
- Supports composition by chaining readers through transformations without materializing intermediate data

Here is the core `Reader` interface with the most essential methods:

```scala
abstract class Reader[+Elem]

abstract class Reader.SyncReader[+Elem] extends Reader[Elem] {
  def read[A >: Elem](sentinel: A): A
  def readAll[A >: Elem](): Chunk[A]
  def readN[A >: Elem](n: Int): Chunk[A]
  def readUpToN[A >: Elem](n: Int): Chunk[A]
  def isClosed: Boolean
  def readable(): Boolean
  def close(): Unit
  def toAsync: Reader.AsyncReader[Elem]
}

abstract class Reader.AsyncReader[+Elem] extends Reader[Elem] {
  def read[A >: Elem](sentinel: A): Async[A]
  def readAll[A >: Elem](): Async[Chunk[A]]
  def readN[A >: Elem](n: Int): Async[Chunk[A]]
  def readUpToN[A >: Elem](n: Int): Async[Chunk[A]]
  def isClosed: Async[Boolean]
  def readable(): Async[Boolean]
  def close(): Async[Unit]
  // JVM only: def toSync: Reader.SyncReader[Elem]
}
```

The root contains only kind-independent composition and metadata (`++`, `concat`, `concatAsync`, `withReleaseAsync`, and `jvmType`); it cannot be pulled, queried, or closed directly. Those operations belong to one of the two reader kinds, described in [The reader union](#the-reader-union). Every primitive, bulk, lifecycle, and pushdown method on `AsyncReader` has the same parameters as its `SyncReader` counterpart but returns its result in `Async`; [Asynchronous reading](#asynchronous-reading) is the full member list. The eight physical primitive methods are `readBoolean`, `readByte`, `readChar`, `readShort`, `readInt`, `readLong`, `readFloat`, and `readDouble`; a primitive `jvmType` is a contract that the corresponding method works, even when covariance has widened the reader's static element type.

An `AsyncReader` permits one active operation at a time, and closing it is the owner's responsibility. `SyncReader#toAsync` and the JVM-only `AsyncReader#toSync` move a reader between the two kinds without copying it.

## Quick Showcase

Here's how to create and drain a `Reader`:

```scala
import zio.blocks.streams.io.Reader
import zio.blocks.chunk.Chunk
import scala.collection.mutable.Buffer

val r = Reader.fromChunk(Chunk(1, 2, 3, 4, 5))
// r: SyncReader[Int] = zio.blocks.streams.io.Reader$FromChunkInt@72d71007
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

## The Reader Union

`Reader[+Elem]` is the root of two kinds. It declares composition and one piece of metadata, and nothing that pulls, queries, or closes:

```scala
abstract class Reader[+Elem] {
  def ++[Elem2 >: Elem](next: => Reader[Elem2]): Reader[Elem2]
  def concat[Elem2 >: Elem](next: () => Reader[Elem2]): Reader[Elem2]
  def concatAsync[Elem2 >: Elem](next: () => Async[Reader[Elem2]]): Reader.AsyncReader[Elem2]
  def withReleaseAsync(release: () => Async[Unit]): Reader.AsyncReader[Elem]
  def jvmType: JvmType = JvmType.AnyRef
}
```

A value typed `Reader[A]` can be concatenated and can report its physical lane, and that is all. To read from it you must know its kind:

- `Reader.SyncReader[Elem]` holds the direct pull API — `read`, `readAll`, `readN`, `readUpToN`, the eight primitive pulls, the array transfers, `skip`, the pushdown operations, `isClosed`, `readable()`, and `close()`, each returning its result immediately.
- `Reader.AsyncReader[Elem]` mirrors that surface method for method, with every result wrapped in `Async`.

The two kinds line up one to one, so a signature written against one translates mechanically to the other:

| Member                                     | `SyncReader` result | `AsyncReader` result |
|--------------------------------------------|---------------------|----------------------|
| `read(sentinel)`                           | `A`                 | `Async[A]`           |
| `readAll()`                                | `Chunk[A]`          | `Async[Chunk[A]]`    |
| `readN(n)`, `readUpToN(n)`                 | `Chunk[A]`          | `Async[Chunk[A]]`    |
| `readInt(_sentinel)`                       | `Long`              | `Async[Long]`        |
| `readBytes(dest, offset, length)`          | `Int`               | `Async[Int]`         |
| `isClosed`                                 | `Boolean`           | `Async[Boolean]`     |
| `readable()`                               | `Boolean`           | `Async[Boolean]`     |
| `close()`                                  | `Unit`              | `Async[Unit]`        |
| `skip(n)`                                  | `Unit`              | `Async[Unit]`        |
| `reset()`                                  | `Unit`              | `Async[Unit]`        |
| `setLimit(n)`, `setRepeat()`, `setSkip(n)` | `Boolean`           | `Async[Boolean]`     |

Which kind a stream materializes as is decided once, when the graph compiles: a fully synchronous graph produces a `SyncReader`, and a graph with any asynchronous node produces an `AsyncReader`. See [Asynchronous Stream Execution](../execution-and-compatibility/async-execution.md#one-stream-type-two-execution-modes) for that decision.

One caveat about the root: `Reader` is declared `abstract class Reader[+Elem]`, not `sealed`. Every reader the library hands you is a `SyncReader` or an `AsyncReader`, and code may rely on that in practice — what it cannot rely on is the compiler proving a `match` over the two kinds exhaustive, so write such a match with a fallback case.

### Mixed-kind Composition

Concatenation keeps both kinds usable through the same `++` and `concat` names. `SyncReader` adds a pair of overloads that are narrowed to synchronous arguments and disambiguated from the inherited ones by a `DummyImplicit` parameter:

```scala
abstract class Reader.SyncReader[+Elem] extends Reader[Elem] {
  final def ++[Elem2 >: Elem](next: => SyncReader[Elem2])(implicit dummy: DummyImplicit): SyncReader[Elem2]
  final def concat[Elem2 >: Elem](next: () => SyncReader[Elem2])(implicit dummy: DummyImplicit): SyncReader[Elem2]

  final override def ++[Elem2 >: Elem](next: => Reader[Elem2]): AsyncReader[Elem2]
  final override def concat[Elem2 >: Elem](next: () => Reader[Elem2]): AsyncReader[Elem2]
}
```

The rule that falls out is simple: **synchronous plus synchronous stays synchronous; every other combination widens to `AsyncReader`**. When the widening overload is chosen, the synchronous side is adapted with `toAsync` and the pair is concatenated on the asynchronous path.

```scala
import zio.blocks.streams.io.Reader
import zio.blocks.chunk.Chunk

val sync: Reader.SyncReader[Int]   = Reader.fromChunk(Chunk(1, 2))
val async: Reader.AsyncReader[Int] = Reader.fromChunk(Chunk(3, 4)).toAsync

val syncSync: Reader.SyncReader[Int]    = sync ++ Reader.fromChunk(Chunk(5, 6))
val syncAsync: Reader.AsyncReader[Int]  = sync ++ async
val asyncSync: Reader.AsyncReader[Int]  = async ++ Reader.fromChunk(Chunk(7, 8))
val asyncAsync: Reader.AsyncReader[Int] = async ++ async
```

Because the overloads are selected on the *static* type of the argument, a value already widened to `Reader[Int]` picks the widening overload even when it happens to hold a `SyncReader` at runtime. Keep the narrow type if you want to stay on the synchronous path.

`concatAsync` and `withReleaseAsync` are declared on the root and always produce an `AsyncReader`, whichever kind they are called on — the first because the next reader arrives inside an `Async`, the second because the release action does:

```scala
abstract class Reader[+Elem] {
  def concatAsync[Elem2 >: Elem](next: () => Async[Reader[Elem2]]): Reader.AsyncReader[Elem2]
  def withReleaseAsync(release: () => Async[Unit]): Reader.AsyncReader[Elem]
}
```

### Converting Between Kinds

Two adapters move a reader across the split:

```scala
abstract class Reader.SyncReader[+Elem] extends Reader[Elem] {
  final def toAsync: Reader.AsyncReader[Elem]
}

abstract class Reader.AsyncReader[+Elem] extends Reader[Elem] {
  final def toSync: Reader.SyncReader[Elem]   // JVM only
}
```

`toAsync` is available on every platform. `toSync` is supplied by a JVM-only platform trait; on Scala.js that trait is empty, so the method does not exist and shared code cannot call it. [Platform Differences](../execution-and-compatibility/platform-differences.md#availability-matrix) has the full capability split.

Both adapters are lifecycle-preserving views rather than copies. The adapter wraps the original reader, so the two sides share one position and one lifecycle: closing either one closes the underlying source, and consuming through both interleaves pulls on the same cursor. Pick one view and drive the reader through it.

Both also unwrap a round trip instead of stacking. Calling `toAsync` on a reader that is itself the synchronous view of an `AsyncReader` returns that original asynchronous reader, and `toSync` on a synchronous reader's asynchronous view returns the original synchronous one. Converting back and forth therefore costs nothing and never builds a tower of adapters.

`toAsync` is the adapter to reach for when a helper is written against `Reader.AsyncReader` — the kind that compiles on both platforms — and the reader at the call site happens to be synchronous:

```scala
import zio.blocks.async._
import zio.blocks.streams.io.Reader

def firstByte(reader: Reader.AsyncReader[Byte]): Async[Int] = reader.readByte()

def firstByteOfSync(reader: Reader.SyncReader[Byte]): Async[Int] = firstByte(reader.toAsync)
```

What it does not do is make blocking work non-blocking. Driving the view still runs the synchronous reader's pulls on the driving thread.

`toSync` runs the other way. Use it at a JVM edge — an `InputStream`-shaped API, a legacy protocol loop — and not in code that cross-builds:

```scala
import zio.blocks.streams.io.Reader

def firstByteBlocking(reader: Reader.AsyncReader[Byte]): Int = {
  val sync = reader.toSync
  try sync.readByte()
  finally sync.close()
}
```

Unlike `toAsync`, it has hazards that belong at the call site:

:::warning[`toSync` blocks, serializes, and interrupts]
Every pull blocks the calling thread until the asynchronous work settles. Only one pull runs at a time: a second thread entering the view waits until the first pull completes, so the view is a serialization point, not a way to share a reader. Calling `close()` from another thread interrupts the thread parked in a pull — that pull then returns its closed value (`-1`, the sentinel, or an empty chunk) instead of the value it was waiting for. Closing the view also closes the underlying asynchronous reader. After a close, the control operations — `reset()`, `setLimit`, `setRepeat`, `setSkip`, and `skip` — throw `IOException("Reader is closed")`.
:::

The rule to carry away is that `toSync` belongs at JVM edges, not in shared code.

## Construction

Several ways to create a `Reader`, from predefined singletons to collections and I/O sources:

Every companion constructor states its kind in its return type, so you never have to guess which engine a hand-built reader will drive. Factories such as `closed`, `fromChunk`, `fromIterable`, `fromRange`, `single`, `repeat`, and `unfold` are declared to return `SyncReader` — `def fromRange(range: Range): SyncReader[Int]`, and so on. `unfoldAsync` is the one native asynchronous constructor and returns an `AsyncReader`. `repeated` is overloaded three ways and preserves whether its input is synchronous or asynchronous. Composition also preserves asynchronous work: `concatAsync` lazily acquires the next reader and `withReleaseAsync` awaits asynchronous cleanup. Asynchronous children are supported throughout the reader graph.

### Creating Predefined Readers

`Reader.closed` — An already-closed reader that emits no elements. Useful as a base case or for empty streams:

```scala
object Reader {
  def closed: Reader.SyncReader[Nothing]
}
```

Here's how to create and use a closed reader:

```scala
import zio.blocks.streams.io.Reader

val r = Reader.closed
// r: SyncReader[Nothing] = zio.blocks.streams.io.Reader$ClosedReader$@37ab4fcf
println(r.isClosed)        // true
// true
println(r.read(-1))        // -1 (the sentinel)
// -1
```

### From Collections

`Reader.fromChunk` — Creates a reader backed by a [`Chunk`](../../chunk.md). Dispatches on the element type to use specialized, unboxed reads for primitives:

```scala
object Reader {
  def fromChunk[A](chunk: Chunk[A])(implicit jt: JvmType.Infer[A]): Reader.SyncReader[A]
}
```

Create a reader from a chunk and drain its elements:

```scala
import zio.blocks.streams.io.Reader
import zio.blocks.chunk.Chunk

val chunk = Chunk(10, 20, 30)
// chunk: Chunk[Int] = IndexedSeq(10, 20, 30)
val r = Reader.fromChunk(chunk)
// r: SyncReader[Int] = zio.blocks.streams.io.Reader$FromChunkInt@61eb43ca

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
  def fromIterable[A](it: Iterable[A])(implicit jt: JvmType.Infer[A]): Reader.SyncReader[A]
}
```

Create a reader from a list and consume its elements:

```scala
import zio.blocks.streams.io.Reader

val list = List("a", "b", "c")
// list: List[String] = List("a", "b", "c")
val r = Reader.fromIterable(list)
// r: SyncReader[String] = zio.blocks.streams.io.Reader$FromIterable@58ab88aa

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
  def fromRange(range: Range): Reader.SyncReader[Int]
}
```

Create a reader from a range and drain the integers:

```scala
import zio.blocks.streams.io.Reader

val r = Reader.fromRange(1 to 5)
// r: SyncReader[Int] = zio.blocks.streams.io.Reader$FromRange@28012030

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

`Reader.fromInputStream` — Wraps a `java.io.InputStream` as a `SyncReader[Byte]`. `readByte()` exposes the unsigned `0`–`255` view and reserves `-1` exclusively for EOF; ordinary element pulls retain `Byte` values:

```scala
object Reader {
  def fromInputStream(is: InputStream): Reader.SyncReader[Byte]
}
```

`Reader.fromReader` — Wraps a `java.io.Reader` as a `SyncReader[Char]` for character-based I/O:

```scala
object Reader {
  def fromReader(r: java.io.Reader): Reader.SyncReader[Char]
}
```

`NioReaders` is the `java.nio` counterpart, and it is synchronous throughout. It has no asynchronous twins, and the reason is in the type it wraps: `ReadableByteChannel#read` blocks the calling thread. No wrapper can make it non-blocking, so presenting its result as an `AsyncReader` would have promised something the channel cannot deliver. The two objects split by capability — `AsyncNioReaders` for channels that implement the JDK's asynchronous read protocol, `NioReaders` for the blocking ones and for `ByteBuffer`s.

Every `NioReaders` factory states its kind in its return type:

```scala
object NioReaders {
  def fromByteBuffer(buf: ByteBuffer): Reader.SyncReader[Byte]
  def fromByteBufferDouble(buf: ByteBuffer): Reader.SyncReader[Double]
  def fromByteBufferFloat(buf: ByteBuffer): Reader.SyncReader[Float]
  def fromByteBufferInt(buf: ByteBuffer): Reader.SyncReader[Int]
  def fromByteBufferLong(buf: ByteBuffer): Reader.SyncReader[Long]
  def fromChannel(ch: ReadableByteChannel, bufSize: Int = 8192): Reader.SyncReader[Byte]
}
```

These factories return `Reader.SyncReader[Byte]` rather than the root `Reader[Byte]`, which is what makes pulling and closing available on the result: those members belong to the reader kinds, not to the root type. Two details of this object are worth noting at a call site: its channel factory names the buffer parameter `bufSize`, where the asynchronous one names it `bufferSize`, and there is no public unmanaged channel variant here — `NioReaders.fromChannel` always owns the channel it wraps.

File bytes are reachable only through this synchronous side: a `java.nio.channels.FileChannel` is a `ReadableByteChannel`, so `NioReaders.fromChannel` accepts it. That reader blocks, and `SyncReader#toAsync` does not change it — the resulting asynchronous reader still blocks the thread that drives it.

### From Native Asynchronous Sources

Each platform ships a small set of factories that turn a native asynchronous byte source into a `Reader.AsyncReader[Byte]`. On the JVM that source is a `java.nio.channels.AsynchronousByteChannel`; on Scala.js it is a Web Streams API `ReadableStream`. Once wrapped, the result is an ordinary asynchronous reader: pull from it by hand, or hand it to `Stream.fromReader` and run the pipeline with a `*Async` terminal.

Six factories exist across the two platforms, all of them returning `Reader.AsyncReader[Byte]` on the `JvmType.Byte` lane. They differ in what they wrap and in who owns the native source once the reader closes:

| Factory                                             | Platform | Native source               | On reader `close()`                        |
|-----------------------------------------------------|----------|-----------------------------|--------------------------------------------|
| `AsyncNioReaders.fromChannel`                       | JVM      | `AsynchronousByteChannel`   | Closes the channel                         |
| `AsyncNioReaders.fromChannelUnmanaged`              | JVM      | `AsynchronousByteChannel`   | Leaves the channel open                    |
| `AsyncNioReaders.fromSocket`                        | JVM      | `AsynchronousSocketChannel` | Closes the socket                          |
| `AsyncNioReaders.fromSocketUnmanaged`               | JVM      | `AsynchronousSocketChannel` | Leaves the socket open                     |
| `ReadableStreamReaders.fromReadableStream`          | Scala.js | `ReadableStream`            | Cancels the stream, then releases the lock |
| `ReadableStreamReaders.fromReadableStreamUnmanaged` | Scala.js | `ReadableStream`            | Releases the lock, never cancels           |

The two adapters follow the same rules, so a cross-platform consumer sees the same behaviour from either one:

| Situation                   | JVM `AsyncNioReaders`                          | Scala.js `ReadableStreamReaders`           |
|-----------------------------|------------------------------------------------|--------------------------------------------|
| A delivery carries no bytes | The buffer is cleared and the read resubmitted | The chunk is skipped and `read()` reissued |
| End of stream               | A negative completion count                    | `done = true` on the read result           |
| A source failure            | `IOException`, trusted                         | A rejected promise, trusted                |
| Read buffer size            | `bufferSize`, default 8192                     | Not configurable                           |

Neither module is a general native-I/O layer. `AsyncNioReaders` reads from channels that implement the JDK's asynchronous read protocol, and `ReadableStreamReaders` reads from a browser or Node byte stream. Everything else — files on the JVM, non-byte sources on Scala.js — is outside what these factories accept.

#### JVM: `AsyncNioReaders`

`AsyncNioReaders` is the JVM factory object for genuinely non-blocking reads. Its four factories divide along two axes: the type of the native source, and whether the reader owns it.

Both channel factories wrap an `AsynchronousByteChannel` and take the read buffer size as a defaulted second parameter:

```scala
object AsyncNioReaders {
  def fromChannel(channel: AsynchronousByteChannel, bufferSize: Int = 8192): Reader.AsyncReader[Byte]
  def fromChannelUnmanaged(channel: AsynchronousByteChannel, bufferSize: Int = 8192): Reader.AsyncReader[Byte]
}
```

`bufferSize` is the capacity of the single `ByteBuffer` the reader refills from the channel, and it is validated eagerly: a value of zero or less throws `IllegalArgumentException` with the message `requirement failed: bufferSize must be positive` from the factory call itself, not from the first pull.

To lift a channel into a stream and run it with a cross-platform terminal:

```scala
import zio.blocks.async._
import zio.blocks.chunk.Chunk
import zio.blocks.streams._

import java.nio.channels.AsynchronousByteChannel

def collect(channel: AsynchronousByteChannel): Async[Either[Nothing, Chunk[Byte]]] =
  Stream.fromReader[Nothing, Byte](AsyncNioReaders.fromChannel(channel, bufferSize = 4096)).runCollectAsync
```

Driving the reader by hand works the same way, and is what you want when the protocol is framed rather than streamed:

```scala
import zio.blocks.async._
import zio.blocks.chunk.Chunk
import zio.blocks.streams.AsyncNioReaders
import zio.blocks.streams.io.Reader

import java.nio.channels.AsynchronousByteChannel

def header(channel: AsynchronousByteChannel): Async[Chunk[Byte]] = {
  val reader: Reader.AsyncReader[Byte] = AsyncNioReaders.fromChannelUnmanaged(channel, bufferSize = 512)
  reader.readN[Byte](16).flatMap(bytes => reader.close().map(_ => bytes))
}
```

The socket pair narrows the parameter type to `AsynchronousSocketChannel`, which is the `AsynchronousByteChannel` most callers actually hold:

```scala
object AsyncNioReaders {
  def fromSocket(socket: AsynchronousSocketChannel, bufferSize: Int = 8192): Reader.AsyncReader[Byte]
  def fromSocketUnmanaged(socket: AsynchronousSocketChannel, bufferSize: Int = 8192): Reader.AsyncReader[Byte]
}
```

There is no behavioural difference to learn: `AsyncNioReaders.fromSocket` delegates to `AsyncNioReaders.fromChannel` and `AsyncNioReaders.fromSocketUnmanaged` to `AsyncNioReaders.fromChannelUnmanaged`, with the same buffer and the same ownership rule. They exist so a socket-shaped call site reads as one.

#### Managed Versus Unmanaged Ownership

Ownership is the whole of the difference between the two variants, and it is decided when you pick the factory, not later.

A managed reader — `AsyncNioReaders.fromChannel` or `AsyncNioReaders.fromSocket` — closes the underlying channel when the reader closes, and only if the channel is still open. If that channel close fails, the failure surfaces from the reader's own `close()` rather than being swallowed. An unmanaged reader releases the reader and nothing else: the channel stays open for whoever owns it, and a reader close is invisible to the rest of the program apart from the read it cancels.

Closing either kind cancels a read that is still in flight. The reader marks itself closed, cancels the underlying channel operation, and settles the pending pull with its end-of-stream answer — `readByte()` returns `-1`, `read(sentinel)` returns the sentinel — so a consumer parked on a pull is released rather than left waiting for a channel that will never answer.

`close()` is idempotent. The first caller performs the work; every later caller awaits the same memoized outcome, and the channel is closed at most once.

All three facts are observable rather than asserted. The example below is a runnable file in the JVM-only `streams-examples` module of the [zio-blocks repository](https://github.com/zio/zio-blocks). It drives a scripted `AsynchronousByteChannel` — one that counts its own `close()` calls and parks a read it cannot serve — through both ownership modes, so the managed close, the untouched unmanaged channel, the memoized second close, and the cancelled pending read are all printed:

```scala title="streams-examples/src/main/scala/nio/AsyncChannelReaderExample.scala" 
package nio

import zio.blocks.async._
import zio.blocks.chunk.Chunk
import zio.blocks.streams.AsyncNioReaders

import java.nio.ByteBuffer
import java.nio.channels.{AsynchronousByteChannel, AsynchronousCloseException, CompletionHandler}
import java.nio.charset.StandardCharsets.UTF_8
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.{CompletableFuture, CountDownLatch, Future}

/**
 * Managed and unmanaged readers over an `AsynchronousByteChannel` (JVM only).
 *
 * `AsyncNioReaders.fromChannel` takes ownership of the channel and closes it
 * when the reader closes. `AsyncNioReaders.fromChannelUnmanaged` releases the
 * reader and leaves the channel alive for its owner. Both memoize `close()`,
 * and both settle a read that is still in flight when the reader closes.
 *
 * The channel below is scripted rather than networked: it counts its own
 * `close()` calls and parks a read that has nothing left to deliver, so
 * ownership, idempotent close, and the cancelled pending read are all
 * observable without a socket.
 */
object AsyncChannelReaderExample {
  def main(args: Array[String]): Unit = {
    managedClosesTheChannel()
    unmanagedLeavesTheChannelOpen()
    closeCancelsAPendingRead()
  }

  /** Managed ownership: the reader closes the channel, and only once. */
  private def managedClosesTheChannel(): Unit = {
    val channel = new ScriptedChannel("async".getBytes(UTF_8))
    val reader  = AsyncNioReaders.fromChannel(channel, bufferSize = 16)

    val bytes: Chunk[Byte] = reader.readN[Byte](5).block
    reader.close().block
    reader.close().block // memoized: the channel is not closed a second time

    println(
      s"managed   -> read=${text(bytes)}, channelOpen=${channel.isOpen}, closes=${channel.closeCount.get()}"
    )
  }

  /** Unmanaged ownership: the channel outlives the reader untouched. */
  private def unmanagedLeavesTheChannelOpen(): Unit = {
    val channel = new ScriptedChannel("async".getBytes(UTF_8))
    val reader  = AsyncNioReaders.fromChannelUnmanaged(channel, bufferSize = 16)

    val bytes: Chunk[Byte] = reader.readN[Byte](5).block
    reader.close().block
    reader.close().block

    println(
      s"unmanaged -> read=${text(bytes)}, channelOpen=${channel.isOpen}, closes=${channel.closeCount.get()}"
    )
  }

  /**
   * Closing an unmanaged reader cancels the read the channel is still holding
   * and hands the consumer the end-of-stream answer instead.
   */
  private def closeCancelsAPendingRead(): Unit = {
    val channel = new ScriptedChannel("hi".getBytes(UTF_8))
    val reader  = AsyncNioReaders.fromChannelUnmanaged(channel, bufferSize = 16)

    val delivered: Chunk[Byte] = reader.readN[Byte](2).block
    val pending                = reader.readByte().start

    // Wait until the channel is genuinely holding a read, so the close races an
    // in-flight operation rather than an unstarted one.
    channel.readParked.await()
    reader.close().block

    println(
      s"pending   -> read=${text(delivered)}, cancelledRead=${pending.block}, " +
        s"channelOpen=${channel.isOpen}, closes=${channel.closeCount.get()}"
    )
  }

  private def text(bytes: Chunk[Byte]): String = new String(bytes.toArray, UTF_8)

  /**
   * A channel that delivers `payload` once and then parks every further read
   * until it is closed, the way a live socket waits between packets.
   */
  private final class ScriptedChannel(payload: Array[Byte]) extends AsynchronousByteChannel {
    val closeCount: AtomicInteger  = new AtomicInteger
    val readParked: CountDownLatch = new CountDownLatch(1)

    private val lock                      = new AnyRef
    private var position                  = 0
    private var open                      = true
    private var parked: Throwable => Unit = null

    def read[A](dst: ByteBuffer, attachment: A, handler: CompletionHandler[Integer, ? >: A]): Unit = {
      val served = lock.synchronized {
        val outcome = serve(dst)
        if (outcome.isEmpty) parked = cause => handler.failed(cause, attachment)
        outcome
      }
      served match {
        case Some(count) => handler.completed(count, attachment)
        case None        => readParked.countDown()
      }
    }

    def read(dst: ByteBuffer): Future[Integer] = {
      val future = new CompletableFuture[Integer]
      val served = lock.synchronized {
        val outcome = serve(dst)
        if (outcome.isEmpty) parked = cause => { future.completeExceptionally(cause); () }
        outcome
      }
      served match {
        case Some(count) => future.complete(count)
        case None        => readParked.countDown()
      }
      future
    }

    def write[A](src: ByteBuffer, attachment: A, handler: CompletionHandler[Integer, ? >: A]): Unit =
      handler.failed(new UnsupportedOperationException("scripted channel is read-only"), attachment)

    def write(src: ByteBuffer): Future[Integer] = {
      val future = new CompletableFuture[Integer]
      future.completeExceptionally(new UnsupportedOperationException("scripted channel is read-only"))
      future
    }

    def isOpen: Boolean = lock.synchronized(open)

    def close(): Unit = {
      val release = lock.synchronized {
        closeCount.incrementAndGet()
        open = false
        val current = parked
        parked = null
        current
      }
      if (release ne null) release(new AsynchronousCloseException)
    }

    private def serve(dst: ByteBuffer): Option[Int] = {
      val remaining = payload.length - position
      if (remaining <= 0) None
      else {
        val count = math.min(remaining, dst.remaining)
        dst.put(payload, position, count)
        position += count
        Some(count)
      }
    }
  }
}
```

([source](https://github.com/zio/zio-blocks/blob/main/streams-examples/src/main/scala/nio/AsyncChannelReaderExample.scala))

Run it with:

```bash
sbt "streams-examples/runMain nio.AsyncChannelReaderExample"
```

It prints:

```
managed   -> read=async, channelOpen=false, closes=1
unmanaged -> read=async, channelOpen=true, closes=0
pending   -> read=hi, cancelledRead=-1, channelOpen=true, closes=0
```

#### JVM Invariants

Four properties hold for every reader the `AsyncNioReaders` factories produce, and each one is a rule a hand-written channel wrapper commonly gets wrong.

1. **A zero-byte completion is not end of stream.** When the channel completes a read having transferred nothing, the adapter clears its buffer and resubmits the read; only a negative completion count ends the stream. A channel that yields `0` under backpressure therefore stalls the pull, it does not truncate the stream.
2. **`IOException`s are trusted source failures.** A failure reported by the channel is wrapped as a source failure and surfaces in the typed error channel of the stream built from the reader, not as a defect, and every later pull replays it rather than pretending the source recovered.
3. **Pulls are inert until driven.** Every read method returns a deferred `Async`; building `reader.readByte()` submits nothing to the channel, and the read is issued when the effect is driven. `readable()` follows the same rule — it reports whether bytes are already buffered and never initiates I/O to find out.
4. **One operation may be in flight at a time.** A second pull started while another is active fails with `IllegalStateException`; [One Active Operation at a Time](#one-active-operation-at-a-time) covers the rule and the way to sequence pulls instead.

#### JVM Limitations

The asynchronous NIO surface is exactly the four factories above, and file input is not among them.

:::warning[`AsynchronousFileChannel` is not supported]
No factory accepts an `AsynchronousFileChannel`, and it is not an `AsynchronousByteChannel`, so it cannot be passed to `AsyncNioReaders.fromChannel` either. There is no asynchronous file reader in this module.
:::

File bytes go through the synchronous `NioReaders.fromChannel` instead, as [From I/O](#from-io) describes.

#### Scala.js: `ReadableStreamReaders`

`ReadableStreamReaders` is the Scala.js counterpart, wrapping the byte-reading side of the Web Streams API. The module declares minimal `@js.native` facades for `ReadableStream`, its reader, and a read result, so using it does not pull a DOM library into your build.

Two factories mirror the managed and unmanaged pair on the JVM:

```scala
object ReadableStreamReaders {
  def fromReadableStream(stream: ReadableStream): Reader.AsyncReader[Byte]
  def fromReadableStreamUnmanaged(stream: ReadableStream): Reader.AsyncReader[Byte]
}
```

Both take the *stream*, not a reader. Each factory calls `stream.getReader()` itself and keeps the acquired reader for its own lifetime, which is what makes the lock release on close well defined. Acquiring a reader yourself and passing it in is not part of the API.

The managed factory owns the acquired reader: closing it cancels the JavaScript stream, awaits the read that was in flight, and then releases the lock. The unmanaged factory releases the lock and never cancels, so the underlying stream remains usable by the code that created it. As on the JVM, either kind settles a pending pull with end of stream instead of leaving it outstanding, and drops whatever it had buffered.

#### Scala.js Invariants

The JVM adapter's rules about deferral and exclusivity hold here too — pulls are inert until driven, and one operation may be in flight at a time. The four properties below restate the adapter's end-of-stream, buffering and failure behaviour in the terms of the Web Streams `read()` promise and its `done`/`value` result, which is where this adapter is easiest to get wrong.

1. **An empty chunk is skipped, never treated as end of stream.** A result with `done = false` and a zero-length value causes the adapter to reissue `read()`; only `done = true` ends the stream.
2. **Buffered bytes are preserved across pulls.** A chunk delivered by the stream is consumed byte by byte from the adapter's own index, so a pull that the buffer can satisfy issues no `read()` at all, and a partially consumed chunk survives until it is drained.
3. **End of stream is observed without prefetch.** The adapter never calls `read()` merely to discover whether the stream has finished; it learns that from the read that a pull actually needed.
4. **A rejected promise is a trusted source failure.** The rejection is wrapped as a source failure, surfaces in the typed error channel, and is replayed by every later pull.

#### Scala.js Limitations

Two of the JVM adapter's affordances have no Scala.js equivalent, and one of them cannot be worked around from user code.

:::warning[Byte-only, no BYOB, no buffer size]
Both factories produce `Reader.AsyncReader[Byte]` on the `JvmType.Byte` lane and read `Uint8Array` chunks; there is no factory for another element type. Neither factory offers BYOB support — `getReader()` is called with no arguments, so the adapter never acquires a bring-your-own-buffer reader and cannot read into a caller-supplied `ArrayBuffer`. Neither takes a buffer-size parameter: chunk sizes are whatever the underlying stream produces.
:::

### Single Element

`Reader.single` — Creates a reader that emits exactly one element, then closes. Primitive types use specialized variants for zero-boxing:

```scala
object Reader {
  def single[A](value: A)(implicit jt: JvmType.Infer[A]): Reader.SyncReader[A]
  def singleInt(value: Int): Reader.SyncReader[Int]
  def singleLong(value: Long): Reader.SyncReader[Long]
  def singleFloat(value: Float): Reader.SyncReader[Float]
  def singleDouble(value: Double): Reader.SyncReader[Double]
  def singleChar(value: Char): Reader.SyncReader[Char]
  def singleShort(value: Short): Reader.SyncReader[Short]
  def singleByte(value: Byte): Reader.SyncReader[Byte]
  def singleBoolean(value: Boolean): Reader.SyncReader[Boolean]
}
```

When you use `Reader.single`, behavior differs between reference types and primitives. The `JvmType.Infer[A]` implicit parameter enables compile-time type detection, automatically selecting the appropriate implementation (specialized primitive or reference-type generic).

For reference types like String, `Reader.single("hello")` stores the element directly and tracks a taken/not-taken flag internally. You read via the generic `SyncReader#read[A](sentinel)` method, passing your own sentinel value. On the first call, you get your string; on subsequent calls, you receive the sentinel you provided, allowing you to detect stream closure.

For primitive types, `Reader.single(42)` could naively box the integer, but the library avoids this penalty entirely via `SingletonPrim`—a zero-boxing specialization that stores the primitive unboxed in memory. The `JvmType.Infer` implicit detects this at compile time and routes you through specialized factory methods (`Reader.singleInt`, `Reader.singleLong`, etc.) and specialized read methods (`Reader#readInt`, `Reader#readLong`, etc.). Both storage and retrieval stay unboxed, maintaining zero-copy efficiency.

`Reader.singleByte` returns `SyncReader[Byte]` and reports `JvmType.Byte`. Its physical scalar pull is `readByte(): Int`, which returns the unsigned byte value `0`–`255` or `-1` at EOF.

Create and read from a single-element reference-type reader with a custom sentinel:

```scala
import zio.blocks.streams.io.Reader

val r = Reader.single("hello")
// r: SyncReader[String] = zio.blocks.streams.io.Reader$SingletonGeneric@65f1d92c
val sentinel = "END"
// sentinel: String = "END"
println(r.read(sentinel))    // hello
// hello
println(r.read(sentinel))    // END (sentinel, reader is closed)
// END
```

For primitive types, use the specialized factory and read methods. `SyncReader#readInt` takes a `Long` sentinel and returns `Long`; `AsyncReader#readInt` takes the same sentinel and returns `Async[Long]`:

```scala
import zio.blocks.streams.io.Reader

val r = Reader.singleInt(100)
// r: SyncReader[Int] = zio.blocks.streams.io.Reader$SingletonPrim@63751c80
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
  def repeat[A](a: A)(implicit jt: JvmType.Infer[A]): Reader.SyncReader[A]
}
```

Create an infinite reader that repeatedly emits the same value:

```scala
import zio.blocks.streams.io.Reader

val r = Reader.repeat(1)
// r: SyncReader[Int] = zio.blocks.streams.io.Reader$SingletonPrim@5003d04d

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
  def repeated[A](inner: SyncReader[A]): SyncReader[A]
  def repeated[A](inner: AsyncReader[A]): AsyncReader[A]
  def repeated[A](inner: Reader[A]): Reader[A]
}
```

### Unfold (State Machine)

`Reader.unfold` — Creates a reader by unfolding state with a function. Returns `None` to signal completion, or `Some((elem, nextState))` to emit an element and advance state:

```scala
object Reader {
  def unfold[S, A](s: S)(f: S => Option[(A, S)])(implicit jt: JvmType.Infer[A]): SyncReader[A]
}
```

Create a reader that unfolds state incrementally until completion:

```scala
import zio.blocks.streams.io.Reader

val r = Reader.unfold(1) { s =>
  if (s > 3) None else Some((s, s + 1))
}
// r: SyncReader[Int] = zio.blocks.streams.io.Reader$Unfold@28e9841a

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

### `Reader.unfoldAsync`

`Reader.unfoldAsync` is the only native asynchronous constructor in the companion. It has the same shape as `unfold`, with the step function returning its `Option` inside an `Async`, and it produces an `AsyncReader`:

```scala
object Reader {
  def unfoldAsync[S, A](s: S)(f: S => Async[Option[(A, S)]])(implicit jt: JvmType.Infer[A]): AsyncReader[A]
}
```

Use it when producing the next element is itself asynchronous — a network round trip, a callback-based API, a timer. Everything downstream of it compiles on the asynchronous path.

```scala
import zio.blocks.streams.io.Reader
import zio.blocks.chunk.Chunk
import zio.blocks.async._

val ticks: Reader.AsyncReader[Int] =
  Reader.unfoldAsync(1) { s =>
    Async.succeed(if (s > 3) None else Some((s, s + 1)))
  }

val drained: Async[Chunk[Int]] = ticks.readAll()
val closed: Async[Unit]        = ticks.close()
```

The state callback is lazy and generation-aware: exactly one callback may be in flight, and the next state is committed only when that callback succeeds while its reader generation is still current. A callback that completes after a `reset` or a `close` therefore cannot advance state that no longer exists.

## Core Operations

These methods form the primary interface for consuming elements and querying reader state:

### Pulling Elements

`read` pulls the next element, or produces `sentinel` if the reader is closed and empty. This is the fundamental operation. The synchronous and asynchronous signatures are distinct:

```scala
abstract class Reader.SyncReader[+Elem] {
  def read[A >: Elem](sentinel: A): A
}

abstract class Reader.AsyncReader[+Elem] {
  def read[A >: Elem](sentinel: A): Async[A]
}
```

The sentinel value is caller-chosen and should never appear as a real element. For reference types, `null` is convenient. For primitives, use a value outside the domain (e.g., `-1` for unsigned bytes, `Long.MinValue` for `Int`):

```scala
import zio.blocks.streams.io.Reader
import zio.blocks.chunk.Chunk

val r = Reader.fromChunk(Chunk(10, 20))
// r: SyncReader[Int] = zio.blocks.streams.io.Reader$FromChunkInt@2cb36210
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
abstract class Reader.SyncReader[+Elem] {
  def readInt(_sentinel: Long)(implicit _ev: Elem <:< Int): Long
}

abstract class Reader.AsyncReader[+Elem] {
  def readInt(_sentinel: Long)(implicit _ev: Elem <:< Int): Async[Long]
}
```

Why widen to `Long`? If `Reader#readInt` returned `Int`, you couldn't distinguish a real element from the sentinel—both would fit in the int range. By widening to `Long`, the sentinel (e.g., `Long.MinValue`) lies outside the possible int domain, allowing reliable end-of-stream detection. Cast the result back to `Int` if needed: `r.readInt(Long.MinValue).toInt`.

`Reader#readLong` — Sentinel-return `Long` pull. This low-level scalar method cannot distinguish EOF from a real element equal to the caller's sentinel:

```scala
abstract class Reader.SyncReader[+Elem] {
  def readLong(_sentinel: Long)(implicit _ev: Elem <:< Long): Long
}

abstract class Reader.AsyncReader[+Elem] {
  def readLong(_sentinel: Long)(implicit _ev: Elem <:< Long): Async[Long]
}
```

The scalar API necessarily permits a collision with the caller's sentinel. Collision-free internal pulls preserve the complete `Long` domain by calling `readLongs` with a length-one array and using its returned count (`-1` for EOF, `1` for data) as status. Custom full-domain loops should use the same pattern.

`Reader#readFloat` — Sentinel-return `Float` pull. Returns the element widened to `Double`, or `sentinel` when closed:

```scala
abstract class Reader.SyncReader[+Elem] {
  def readFloat(_sentinel: Double)(implicit _ev: Elem <:< Float): Double
}

abstract class Reader.AsyncReader[+Elem] {
  def readFloat(_sentinel: Double)(implicit _ev: Elem <:< Float): Async[Double]
}
```

Like `Reader#readInt`, widening to `Double` allows the sentinel to lie safely outside the float domain. A float value will always fit in the lower precision bits of the double result, and the sentinel (typically `Double.MaxValue`) occupies the upper range. This ensures you can reliably distinguish real float elements from end-of-stream. Cast back to `Float` if needed: `r.readFloat(Double.MaxValue).toFloat`.

`Reader#readDouble` — Sentinel-return `Double` pull. Returns the element, or `sentinel` when closed. No `Double` bit pattern can be reserved as a collision-free sentinel, so the parameter is kept for symmetry with the other sentinel-taking pulls and for callers who can prove that a particular value lies outside their own data domain:

```scala
abstract class Reader.SyncReader[+Elem] {
  def readDouble(_sentinel: Double)(implicit _ev: Elem <:< Double): Double
}

abstract class Reader.AsyncReader[+Elem] {
  def readDouble(_sentinel: Double)(implicit _ev: Elem <:< Double): Async[Double]
}
```

Like scalar `readLong`, scalar `readDouble` cannot reserve a collision-free value (and NaN comparisons add another trap). Collision-free internal pulls call `readDoubles` with a length-one array and use its returned count as EOF/data status, preserving infinities, every NaN payload, and either zero. Custom full-domain loops should use the same pattern.

These specialized methods are the hot path for primitive streams — they avoid allocation and boxing entirely:

```scala
import zio.blocks.streams.io.Reader
import zio.blocks.chunk.Chunk

val r = Reader.fromChunk(Chunk(10, 20, 30))
// r: SyncReader[Int] = zio.blocks.streams.io.Reader$FromChunkInt@412d60e3
val sentinel = Long.MinValue
// sentinel: Long = -9223372036854775808L

val v = r.readInt(sentinel)
// v: Long = 10L
```

### Byte-Level Reading

`Reader#readByte` — Reads a single byte (0–255), widened to `Int`. Returns `-1` when the reader is closed. Dispatches on `Reader#jvmType` for zero-boxing when the reader is specialized:

```scala
abstract class Reader.SyncReader[+Elem] {
  def readByte(): Int
}

abstract class Reader.AsyncReader[+Elem] {
  def readByte(): Async[Int]
}
```

Read bytes one at a time from a reader until end-of-stream:

```scala
import zio.blocks.streams.io.Reader
import java.io.ByteArrayInputStream

val bytes = Array[Byte](72, 101, 108, 108, 111)  // Hello in ASCII bytes
// bytes: Array[Byte] = Array(72, 101, 108, 108, 111)
val is = new ByteArrayInputStream(bytes)
// is: ByteArrayInputStream = java.io.ByteArrayInputStream@5196ddfa
val r = Reader.fromInputStream(is)
// r: SyncReader[Byte] = zio.blocks.streams.io.Reader$InputStreamReader@795c948d

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

- A `SyncReader` blocks until at least 1 byte is available; an `AsyncReader` represents that wait in `Async`.
- Returns the number of bytes read (`1 <= r <= len`).
- Returns `-1` when closed and empty.
- Returns `0` immediately when `len == 0`.

The method signature is:

```scala
abstract class Reader.SyncReader[+Elem] {
  def readBytes(buf: Array[Byte], offset: Int, len: Int)(implicit ev: Elem <:< Byte): Int
}

abstract class Reader.AsyncReader[+Elem] {
  def readBytes(dest: Array[Byte], offset: Int, length: Int)(implicit ev: Elem <:< Byte): Async[Int]
}
```

Read multiple bytes into a buffer in bulk with a loop pattern:

```scala
import zio.blocks.streams.io.Reader
import java.io.ByteArrayInputStream

val bytes = Array[Byte](72, 101, 108, 108, 111)  // The word Hello
// bytes: Array[Byte] = Array(72, 101, 108, 108, 111)
val is = new ByteArrayInputStream(bytes)
// is: ByteArrayInputStream = java.io.ByteArrayInputStream@267e1357
val r = Reader.fromInputStream(is)
// r: SyncReader[Byte] = zio.blocks.streams.io.Reader$InputStreamReader@13a2b7a2

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
abstract class Reader.SyncReader[+Elem] {
  def readChar(_sentinel: Int)(implicit _ev: Elem <:< Char): Int
}

abstract class Reader.AsyncReader[+Elem] {
  def readChar(_sentinel: Int)(implicit _ev: Elem <:< Char): Async[Int]
}
```

`Reader#readShort` — Sentinel-return `Short` pull. Returns the element widened to `Int`, or `sentinel` when closed:

```scala
abstract class Reader.SyncReader[+Elem] {
  def readShort(_sentinel: Int)(implicit _ev: Elem <:< Short): Int
}

abstract class Reader.AsyncReader[+Elem] {
  def readShort(_sentinel: Int)(implicit _ev: Elem <:< Short): Async[Int]
}
```

`Reader#readBoolean` — Sentinel-return `Boolean` pull. Returns `1` for `true`, `0` for `false`, or `sentinel` when closed. The sentinel must lie outside `[0, 1]` (typically `-1`):

```scala
abstract class Reader.SyncReader[+Elem] {
  def readBoolean(_sentinel: Int)(implicit _ev: Elem <:< Boolean): Int
}

abstract class Reader.AsyncReader[+Elem] {
  def readBoolean(_sentinel: Int)(implicit _ev: Elem <:< Boolean): Async[Int]
}
```

### Bulk Operations

`Reader#readAll` — Drains the entire reader into a `Chunk`. Dispatches on `Reader#jvmType` for zero-boxing on primitive readers:

```scala
abstract class Reader.SyncReader[+Elem] {
  def readAll[A >: Elem](): Chunk[A]
}

abstract class Reader.AsyncReader[+Elem] {
  def readAll[A >: Elem](): Async[Chunk[A]]
}
```

The result is a new chunk containing all remaining elements:

```scala
import zio.blocks.streams.io.Reader
import zio.blocks.chunk.Chunk

val r = Reader.fromChunk(Chunk(10, 20, 30))
// r: SyncReader[Int] = zio.blocks.streams.io.Reader$FromChunkInt@71f34e37
val all = r.readAll()
// all: Chunk[Int] = IndexedSeq(10, 20, 30)
println(all)  // Chunk(10,20,30)
// Chunk(10,20,30)
```

`Reader#readN` and `Reader#readUpToN` — Bounded drains. `readN` gathers up to `n` elements, returning early only when the reader is exhausted; `readUpToN` gathers at most `n` elements and stops as soon as the next element is not already available, so it never waits for a slow producer to fill the request. Both produce an empty chunk when `n <= 0` or the reader is at end-of-stream:

```scala
abstract class Reader.SyncReader[+Elem] {
  def readN[A >: Elem](n: Int): Chunk[A]
  def readUpToN[A >: Elem](n: Int): Chunk[A]
}

abstract class Reader.AsyncReader[+Elem] {
  def readN[A >: Elem](n: Int): Async[Chunk[A]]
  def readUpToN[A >: Elem](n: Int): Async[Chunk[A]]
}
```

:::caution[Bound `n` yourself]
`n` is a request, and some readers size a buffer from it before knowing how much data will arrive. The JVM channel-backed byte reader allocates `new Array[Byte](n)` up front in `readUpToN`, and the only guards on that allocation are `n <= 0` and an already-closed reader — there is no upper bound. Passing `Int.MaxValue` therefore asks for a 2 GB array rather than "whatever is ready". Choose a bound that reflects how much you are prepared to hold in memory, such as a page or buffer size.
:::

`Reader#skip` — Eagerly discards the first `n` elements. Dispatches on `Reader#jvmType` for zero-boxing when possible:

```scala
abstract class Reader.SyncReader[+Elem] {
  def skip(n: Long): Unit
}

abstract class Reader.AsyncReader[+Elem] {
  def skip(n: Long): Async[Unit]
}
```

### State Queries

`isClosed` reports whether the reader is closed. Its result is monotone: once `true`, it never becomes `false`:

```scala
abstract class Reader.SyncReader[+Elem] {
  def isClosed: Boolean
}

abstract class Reader.AsyncReader[+Elem] {
  def isClosed: Async[Boolean]
}
```

`readable` reports whether the next `read()` would produce a value (not the sentinel). On `AsyncReader` the answer itself is asynchronous. Buffered readers can override it for an accurate, non-consuming probe:

```scala
abstract class Reader.SyncReader[+Elem] {
  def readable(): Boolean
}

abstract class Reader.AsyncReader[+Elem] {
  def readable(): Async[Boolean]
}
```

Use `readable()` to check if elements are available before calling `read()`:

```scala
import zio.blocks.streams.io.Reader
import zio.blocks.chunk.Chunk

val r = Reader.fromChunk(Chunk(1, 2))
// r: SyncReader[Int] = zio.blocks.streams.io.Reader$FromChunkInt@289d0f41
println(r.readable())      // true
// true
r.read(-1)
// res39: Int = 1
println(r.readable())      // true
// true
r.read(-1)
// res41: Int = 2
println(r.readable())      // false
// false
```

## Asynchronous Reading

`Reader.AsyncReader[Elem]` is the kind a stream materializes as whenever its graph contains an asynchronous node. It is not a second API: it is the surface described above with every result moved inside `Async`. What follows is that member list, and the handful of behaviours that are specific to the asynchronous kind.

A custom asynchronous reader supplies four members. Everything else on the class has a working default built on top of them:

```scala
abstract class Reader.AsyncReader[+Elem] extends Reader[Elem] {
  def read[A >: Elem](sentinel: A): Async[A]
  def readable(): Async[Boolean]
  def isClosed: Async[Boolean]
  def close(): Async[Unit]
}
```

That is enough to build a reader the whole stream machinery can drive:

```scala
import zio.blocks.streams.io.Reader
import zio.blocks.async._

final class OneShot(value: Int) extends Reader.AsyncReader[Int] {
  private var delivered = false
  private var closed    = false

  def read[A >: Int](sentinel: A): Async[A] =
    if (closed || delivered) Async.succeed(sentinel)
    else { delivered = true; Async.succeed(value) }

  def readable(): Async[Boolean] = Async.succeed(!closed && !delivered)
  def isClosed: Async[Boolean]   = Async.succeed(closed)
  def close(): Async[Unit]       = Async.succeed { closed = true }
}
```

The three bulk reads are derived from `read` and the reader's lane, so an implementation gets them for free and overrides them only to exploit a cheaper native path:

```scala
abstract class Reader.AsyncReader[+Elem] extends Reader[Elem] {
  def readAll[A >: Elem](): Async[Chunk[A]]
  def readN[A >: Elem](n: Int): Async[Chunk[A]]
  def readUpToN[A >: Elem](n: Int): Async[Chunk[A]]
}
```

`readAll()` is `readN(Int.MaxValue)`, `readN` gathers until it has `n` elements or hits end-of-stream, and `readUpToN` additionally stops as soon as the next element is not already available. All three yield to the scheduler after a fixed budget of consecutive pulls, so a fast in-memory reader cannot monopolize the calling thread.

The eight primitive pulls mirror the synchronous lane exactly, including the widened carriers — a `Char`, `Short`, `Boolean`, or `Byte` lane returns its value in an `Int`, an `Int` lane in a `Long`, and a `Float` lane in a `Double` — with the result inside `Async`:

```scala
abstract class Reader.AsyncReader[+Elem] extends Reader[Elem] {
  def readBoolean(_sentinel: Int)(implicit _ev: Elem <:< Boolean): Async[Int]
  def readByte(): Async[Int]
  def readChar(_sentinel: Int)(implicit _ev: Elem <:< Char): Async[Int]
  def readShort(_sentinel: Int)(implicit _ev: Elem <:< Short): Async[Int]
  def readInt(_sentinel: Long)(implicit _ev: Elem <:< Int): Async[Long]
  def readLong(_sentinel: Long)(implicit _ev: Elem <:< Long): Async[Long]
  def readFloat(_sentinel: Double)(implicit _ev: Elem <:< Float): Async[Double]
  def readDouble(_sentinel: Double)(implicit _ev: Elem <:< Double): Async[Double]
}
```

Calling one of these on a reader whose `jvmType` is a different primitive lane does not throw at the call site: it returns a failed `Async` carrying an `UnsupportedOperationException` that names both lanes. A reader on the `AnyRef` lane, by contrast, satisfies every one of them by pulling boxed and converting.

Five bulk array transfers fill a caller-supplied array and report how many elements were written, or `-1` when the reader was already at end-of-stream:

```scala
abstract class Reader.AsyncReader[+Elem] extends Reader[Elem] {
  def readBytes(dest: Array[Byte], offset: Int, length: Int)(implicit ev: Elem <:< Byte): Async[Int]
  def readInts(dest: Array[Int], offset: Int, length: Int)(implicit ev: Elem <:< Int): Async[Int]
  def readLongs(dest: Array[Long], offset: Int, length: Int)(implicit ev: Elem <:< Long): Async[Int]
  def readFloats(dest: Array[Float], offset: Int, length: Int)(implicit ev: Elem <:< Float): Async[Int]
  def readDoubles(dest: Array[Double], offset: Int, length: Int)(implicit ev: Elem <:< Double): Async[Int]
}
```

A transfer also stops short of `length` when the next element is not already available, so a partial count is a normal result rather than a sign of end-of-stream. An out-of-range `offset` or `length` surfaces as a failed `Async`, not a thrown exception.

The five control operations complete the mirror:

```scala
abstract class Reader.AsyncReader[+Elem] extends Reader[Elem] {
  def skip(n: Long): Async[Unit]
  def reset(): Async[Unit]
  def setLimit(n: Long): Async[Boolean]
  def setRepeat(): Async[Boolean]
  def setSkip(n: Long): Async[Boolean]
}
```

`skip` has a real default that discards elements through the reader's own lane. The pushdown operations do not: on the base class `reset()` fails with an `UnsupportedOperationException`, and `setLimit`, `setRepeat`, and `setSkip` each succeed with `false`. Those defaults are the honest answer for a reader that cannot rewind or bound itself natively, and callers already handle them — a `false` simply means the interpreter wraps the reader instead of pushing the operation down. Override them only when your reader can genuinely do the work in O(1).

### One Active Operation at a Time

An `AsyncReader` is a single-consumer cursor with one position and one lifecycle. **At most one operation may be in flight at a time.** Await the `Async` returned by a pull, a transfer, a control operation, or `close()` before beginning the next one.

For an implementor this is a contract you may rely on and must not weaken: your `read` will not be re-entered while a previous `read` is still pending, so internal position and buffer state need no defence against overlap. It is also a contract you inherit — a reader you wrap gets the same guarantee only if you preserve it, so never fan a single downstream pull out into concurrent pulls on your source.

Readers are not thread-safe either. Driving one reader from two threads without external synchronization is outside the contract, and the result is not specified. [Asynchronous Stream Execution](../execution-and-compatibility/async-execution.md#one-active-operation-per-reader) states the same rule from the consumer's side.

### Close Ownership

Every asynchronous reader has exactly one owner, and the owner is responsible for awaiting `close()`. Which side holds it is never ambiguous, because the entry point that handed you the reader decides: terminals own and close the reader they compile, `Stream#startAsync` transfers ownership to you, and `Stream#useReaderAsync` retains it. [Manual Pull and Ownership](../execution-and-compatibility/async-execution.md#manual-pull-and-ownership) gives each case in full from the consumer's side, including what a forgotten `close()` costs. The rest of this section is what ownership means for the reader itself.

`close()` is itself an asynchronous operation: it participates in the one-active-operation rule, it cancels or joins work already in flight, and its result must be awaited rather than discarded. Library readers tolerate a repeated close, but the owner should still close exactly once.

For an implementor, `close()` is where release actions and underlying resources are surfaced. A failure during cleanup is reported through the returned `Async` rather than swallowed, so do not let a failing release leave the reader believing it is still open.

```scala
import zio.blocks.streams._
import zio.blocks.streams.io.Reader
import zio.blocks.chunk.Chunk
import zio.blocks.async._

// Ownership retained by the library: the reader is closed on every outcome.
val firstFive: Async[Chunk[Int]] =
  Stream.range(1, 100).useReaderAsync { (r: Reader.AsyncReader[Int]) =>
    r.readN(5)
  }

// Ownership transferred to the caller: closing is now your job.
val owned: Async[Chunk[Int]] =
  Stream.range(1, 100).startAsync.flatMap { r =>
    r.readN(5).flatMap(chunk => r.close().map(_ => chunk))
  }
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
// r1: SyncReader[Int] = zio.blocks.streams.io.Reader$FromChunkInt@21bb3acc
val r2 = Reader.fromChunk(Chunk(3, 4))
// r2: SyncReader[Int] = zio.blocks.streams.io.Reader$FromChunkInt@883375
val combined = r1 ++ r2
// combined: SyncReader[Int] = zio.blocks.streams.io.Reader$ConcatReader@10f054e8

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

These are the root's declarations, which answer with a `Reader[Elem2]`. `SyncReader` narrows them with a second pair of overloads so that concatenating two synchronous readers gives back a `SyncReader`; see [Mixed-kind composition](#mixed-kind-composition) for which combination produces which kind.

**Optimization**: If this reader is already a `ConcatReader`, the thunk is appended to its internal array and `this` is returned (mutable append, O(1) amortized). Otherwise a new `ConcatReader` is created. This ensures that left-associative chains like `a ++ b ++ c ++ d` compile into a single flat `ConcatReader` with O(1) per-element read, rather than O(n) nested wrappers.

## Resource Management

Close readers and attach cleanup callbacks:

### Closing

`close` signals end-of-stream from the consumer side and releases any held resources. Implementations set internal closed state and wake or cancel any pending work. A synchronous owner calls it directly; an asynchronous owner must run and await the returned `Async`:

```scala
abstract class Reader.SyncReader[+Elem] {
  def close(): Unit
}

abstract class Reader.AsyncReader[+Elem] {
  def close(): Async[Unit]
}
```

`SyncReader#withRelease` wraps a synchronous reader so that `release` runs when it closes. `withReleaseAsync`, available on the root and therefore on both kinds, returns an `AsyncReader` and awaits asynchronous cleanup:

```scala
abstract class Reader.SyncReader[+Elem] {
  def withRelease(release: () => Unit): Reader.SyncReader[Elem]
}

abstract class Reader[+Elem] {
  def withReleaseAsync(release: () => Async[Unit]): Reader.AsyncReader[Elem]
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
// r: SyncReader[Int] = zio.blocks.streams.io.Reader$SyncReader$$anon$3@a88df6f

r.close()
// Cleaned up
println(cleanupRef.nonEmpty)  // true
// true
```

## Pushdown Operations

Readers can sometimes handle skip, limit, and repeat operations natively (O(1), zero per-element cost). These methods attempt that; if the reader cannot handle it natively, they return `false` and the caller must wrap the reader.

`Reader#setSkip` — Attempts to set a skip (drop) on this reader. Returns `true` if handled natively, `false` if the caller must wrap. When `true`, the next n elements are discarded before producing. After `Reader#reset()`, the skip is re-applied:

```scala
abstract class Reader.SyncReader[+Elem] {
  def setSkip(n: Long): Boolean
}

abstract class Reader.AsyncReader[+Elem] {
  def setSkip(n: Long): Async[Boolean]
}
```

Set a skip to discard the first two elements:

```scala
import zio.blocks.streams.io.Reader
import zio.blocks.chunk.Chunk

val r = Reader.fromChunk(Chunk(1, 2, 3, 4, 5))
// r: SyncReader[Int] = zio.blocks.streams.io.Reader$FromChunkInt@1cff5dd5
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
abstract class Reader.SyncReader[+Elem] {
  def setLimit(n: Long): Boolean
}

abstract class Reader.AsyncReader[+Elem] {
  def setLimit(n: Long): Async[Boolean]
}
```

Set a limit to produce only three elements:

```scala
import zio.blocks.streams.io.Reader
import zio.blocks.chunk.Chunk

val r = Reader.fromChunk(Chunk(1, 2, 3, 4, 5))
// r: SyncReader[Int] = zio.blocks.streams.io.Reader$FromChunkInt@305462a2
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
abstract class Reader.SyncReader[+Elem] {
  def setRepeat(): Boolean
}

abstract class Reader.AsyncReader[+Elem] {
  def setRepeat(): Async[Boolean]
}
```

Set repeat mode so a reader restarts instead of closing. Not every reader can do this natively — the chunk-, iterable- and range-backed readers cannot, and return `false`; the single-element readers can:

```scala
import zio.blocks.streams.io.Reader

val r = Reader.single(1)
// r: SyncReader[Int] = zio.blocks.streams.io.Reader$SingletonPrim@6f7050db
val handled = r.setRepeat()
// handled: Boolean = true
println(s"Repeat handled natively: $handled")
// Repeat handled natively: true

def drain(count: Int): Unit = {
  if (count < 4) {
    val v = r.read(-1)
    println(v)
    drain(count + 1)
  }
}
drain(0)
// 1
// 1
// 1
// 1
// Output:
// Repeat handled natively: true
// 1
// 1
// 1
// 1
```

`Reader.fromChunk(Chunk(1, 2)).setRepeat()` returns `false` instead: chunk-backed readers do not implement repeat, so the caller wraps the reader rather than pushing the operation down.

`Reader#reset` — Rewinds this reader to its initial state, as if freshly constructed. After `Reader#reset()`, all elements are available again from the beginning. Not all readers support this; readers backed by one-shot resources (InputStreams, `java.io.Reader`s) throw `UnsupportedOperationException`:

```scala
abstract class Reader.SyncReader[+Elem] {
  def reset(): Unit
}

abstract class Reader.AsyncReader[+Elem] {
  def reset(): Async[Unit]
}
```

After rewinding, the reader starts from the beginning:

```scala
import zio.blocks.streams.io.Reader
import zio.blocks.chunk.Chunk

val r = Reader.fromChunk(Chunk(1, 2, 3))
// r: SyncReader[Int] = zio.blocks.streams.io.Reader$FromChunkInt@43a3be3c
println(r.read(-1))  // 1
// 1
r.reset()
println(r.read(-1))  // 1 (back to the beginning)
// 1
```

## Integration with Stream

`Reader` is the compilation target of `Stream`. When you call a terminal operation, the stream compiles to a `Reader`, which is then consumed.

For cross-platform manual pulling, use caller-owned `Stream#startAsync` or bracketed `Stream#useReaderAsync`. `startAsync` transfers ownership to you, so you must await `close()` on every exit path; `useReaderAsync` retains ownership and closes automatically on success, failure, or cancellation. The JVM-only `Stream#start` returns a scoped blocking reader owned by its scope:

```scala
import zio.blocks.streams.*
import zio.blocks.streams.io.Reader
import zio.blocks.scope.*

Scope.global.scoped { scope =>
  import scope.*

  val reader: $[Reader.SyncReader[Int]] = Stream.range(1, 6).start(using scope)

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
Avoid holding references to a `SyncReader` obtained via `Stream#start` outside its [`Scope`](../../resource-management/scope.md). The scope guarantees cleanup; escaping the reader defeats that guarantee.
:::

## Integration with Sink

`Reader` and `Sink` are dual: `Reader` is the source, and `Sink` is the consumer. A terminal compiles the stream to the reader kind required by the graph, hands that reader to the sink's drain, and retains ownership of it. On the JVM, plain terminals such as `run` use the blocking `SyncReader` path when the graph is synchronous and bridge genuine asynchronous boundaries at the final edge. Cross-platform `runAsync` drains an `AsyncReader` without blocking. Both terminal families close the owned reader on success, typed failure, defect, or cancellation.

The sink repeatedly pulls from its reader until end-of-stream, transforming the sequence of elements into a result of type `Z`. This kind-selected drain is an implementation detail; callers choose it through `run` or `runAsync` rather than invoking a sink drain method directly.

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

The contract has three parts, and it is the same on both reader kinds:

1. **The caller owns the sentinel.** The reader never invents one. Pick a value that cannot occur in your data — `null` is the usual choice for reference elements.
2. **The sentinel travels in the widened carrier.** A primitive pull returns the lane's widened type, not the element type, precisely so a value outside the element's domain is available to spend as the sentinel. `readInt` takes and returns `Long`; `readChar`, `readShort`, and `readBoolean` take and return `Int`; `readFloat` takes and returns `Double`. `readByte()` is the exception that proves the rule: it takes no sentinel parameter because it yields unsigned bytes in `0..255` and can reserve `-1` permanently.
3. **Getting the sentinel back means exhausted, and nothing else.** It is not an error signal. Failures arrive as thrown exceptions on a `SyncReader` and as failed `Async` values on an `AsyncReader`.

Two lanes sit outside that arrangement. There is no `Long` value and no `Double` bit pattern left over to reserve — the carrier is the element type itself, so every candidate sentinel is also legitimate data. **The `Long` and `Double` lanes therefore use no sentinel.** `readLong` and `readDouble` still take a sentinel parameter, for symmetry with the five other sentinel-taking pulls, but nothing can safely fill it; the library never relies on it. Instead those lanes detect end-of-stream by count: a length-one `readLongs` or `readDoubles` whose returned count is negative. That is what makes those two lanes fully lossless — every `Long` value and every `Double` bit pattern stays readable as data.

For the per-lane end-of-stream detail, including which carrier each lane widens to, see [Zero-Boxing Streams](../execution-and-compatibility/zero-boxing.md), which owns that table.

### JVM Type Dispatch

`Reader` dispatches on `jvmType` to choose between unboxed and boxed pull paths. This is a physical contract: `JvmType.Byte`, for example, means `readByte` is supported and yields this reader's elements, even if covariance has widened its static type to `Reader[AnyVal]`. Type-preserving wrappers and widening operations preserve a known lane; only an actually unknown or mixed representation falls back to `AnyRef`. Subclasses with primitive specialization override `jvmType`:

```scala
abstract class Reader[+Elem] {
  def jvmType: JvmType = JvmType.AnyRef
}
```

`JvmType` has nine lanes: the eight JVM primitives — `Boolean`, `Byte`, `Char`, `Short`, `Int`, `Long`, `Float`, `Double` — and `AnyRef` for everything else. The eight primitive tags map exactly to `readBoolean`, `readByte`, `readChar`, `readShort`, `readInt`, `readLong`, `readFloat`, and `readDouble`; `AnyRef` is the ninth, and it is the only lane on which all eight of those methods work, because it satisfies them by pulling boxed and converting. For example, a `SyncReader[Int]` backed by a `Chunk[Int]` reports `JvmType.Int`, so consumers may use `readInt`; a pull belonging to another lane throws `UnsupportedOperationException` unless that particular reader happens to implement it too.

The lane is a property of the reader, not of the kind. A `SyncReader` and the `AsyncReader` it becomes under `toAsync` report the same `jvmType`, and asynchronous readers expose the corresponding values through `Async`.

### Thread Safety

Readers are single-consumer cursors, not concurrent work queues. In particular, do not overlap pulls on an `AsyncReader`; await one operation before beginning another.

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
    val reader: scope.$[Reader.SyncReader[Int]] = Stream.range(1, 6).start(using scope)

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

## See Also

- [Asynchronous Stream Execution](../execution-and-compatibility/async-execution.md) — how a graph picks its engine, the `*Async` surface, and close ownership from the stream's side
- [Platform Differences](../execution-and-compatibility/platform-differences.md#availability-matrix) — which reader operations exist on the JVM, on Scala.js, and on both
- [Zero-Boxing Streams](../execution-and-compatibility/zero-boxing.md) — how a primitive lane is chosen, and the per-lane end-of-stream table
- [Stream](../core/stream.md) — the operator and terminal reference for the type that compiles to a `Reader`
- [Sink](../core/sink.md) — the consumer that drains a `Reader`
- [Async](../../async.md#the-pollable-protocol) — `Async[A]`, `Pollable`, and what awaiting an asynchronous result means
