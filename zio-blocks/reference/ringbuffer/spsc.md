# SPSC RingBuffer

> `SpscRingBuffer[A]` is optimized for the simplest and fastest case: exactly one producer thread and one consumer thread. It uses the **FastFlow** algorithm to eliminate all cross-core cache traffic, achieving nanosecond-scale latencies with no volatile reads on the fast path.

## Why FastFlow?

Understanding the FastFlow algorithm helps explain why `SpscRingBuffer` achieves such high performance.

Imagine two threads sharing data with a traditional lock-based queue like `ArrayBlockingQueue`. They use a mutex (lock) to coordinate:

1. Producer thread: acquires lock, adds element, releases lock
2. Consumer thread: acquires lock, removes element, releases lock

This works, but locks have a cost: when threads contend for the same lock, one thread **blocks** (goes to sleep) while waiting. Waking a thread is expensive (thousands of CPU cycles). Even lock-free queues using `synchronized` or `volatile` reads can cause **cache-coherency traffic**: when one CPU core writes to a variable another core reads, the entire cache line must be invalidated and transferred — a costly operation that slows down both cores.

The fundamental issue: **if the producer has to read what the consumer wrote (or vice versa), their CPU caches constantly fight**. This is called *false sharing* and can reduce throughput by 10x or more on heavily loaded systems.

The problem is that *any* read by the producer of the consumer's state (or vice versa) causes cache line bouncing. So FastFlow's radical idea: **neither side should ever read the other's state**.

Instead, the producer simply **writes data into slots** and marks them non-null. The consumer independently **reads slots** and takes any non-null values. The array slot's null/non-null status itself is the only coordination needed — a *happens-before* relationship written once by the producer, read once by the consumer. No locks, no atomic operations on the fast path, no reading the other side's counters.

The **note-passing analogy**:
- You (producer) have a row of empty desks between you and your friend (consumer)
- Empty desk = `null` (available)
- Note on desk = non-null value (message ready)
- **You never look at your friend's side** — you just place notes on empty desks
- **Your friend never looks at your side** — they just pick up notes they see
- No shouting "are you ready?", no waiting, no lock contention

**This is how FastFlow solves the cache-coherency problem**: since producer and consumer never read each other's counters, there's no cache line bouncing between CPU cores. All coordination happens through the slots themselves, which are written once and read once.

The **look-ahead cache** is a further optimization: the producer maintains a local cached limit (`producerLimit`) so they don't have to check every slot individually. It's like glancing ahead at the next N desks to see if they're empty, without actually bending over to look. This keeps the fast path extremely fast — just a local counter check and an array write.

**Why FastFlow is fast**:
- **No locks** — no thread ever blocks another
- **Minimal cache coordination** — producer and consumer touch separate memory locations; the array slot is written once, read once
- **Write-once, read-once semantics** — the slot's null/non-null status is the handshake
- **Cache-line padding** — producer and consumer indices padded to separate cache lines, eliminating false sharing

The result: **lock-free** communication that scales linearly with CPU count and achieves nanosecond-scale latencies. This is why FastFlow is the algorithm of choice for SPSC ring buffers in high-performance systems like trading platforms, game engines, and network stacks. (The slow path's retry on full buffers means the algorithm is not wait-free, though contention is rare.)

## Algorithm

`SpscRingBuffer` uses the **FastFlow** pattern, originally developed for the C++ FastFlow framework and popularized in Java by JCTools and the LMAX Disruptor. The core insight: the array element's `null`/non-`null` state **is** the synchronization signal. The producer never reads `consumerIndex`; the consumer never reads `producerIndex`. This eliminates all cross-core cache traffic between the two sides.

**How it works:**

- The producer walks forward through the array, placing elements into slots that are currently `null`. It marks a slot non-`null` after writing. The producer never reads what the consumer has consumed — it only checks its own cached `producerLimit` to know how many slots are available.
- The consumer walks forward through the array, reading any slot that contains a non-`null` value. After reading, it writes `null` to clear the slot. The consumer never reads the producer's index.
- The slot's nullness itself is the coordination: written once by the producer, read once by the consumer. No locks, no atomic operations on the fast path, no reading the other side's counters.

**Look-ahead cache:** The producer maintains a local `producerLimit` (derived from `consumerIndex` during slow path). This allows the fast path to check a simple local counter instead of reading the volatile `consumerIndex` on every `offer`. The look-ahead step is `max(1, min(capacity/4, 4096))`, balancing between reducing consumer reads and memory usage.

### Diagram

To see the single-producer single-consumer FastFlow algorithm in action, use this interactive stepper. Type any label, click **Offer** to enqueue or **Take** to dequeue, and watch how the producer and consumer coordinate using only release/acquire semantics — no CAS on either side.

Here is a complete walkthrough of every variable in the trace, in the order the algorithm computes them.

### `pIdx` — the monotonic producer counter (plain load/store)

The producer reads `pIdx` with a plain load — no atomic operations at all. Since only one thread produces, no synchronization is needed. The producer maintains its own count, never reads `consumerIndex`, and advances `pIdx` after writing with a release store.

### `cIdx` — the monotonic consumer counter

SPSC avoids reading `consumerIndex` entirely. Instead of checking the occupancy formula (`pIdx - cIdx`), the producer reads the array slot at `producerIndex + lookAheadStep` to determine if a slot is free (on the slow path when the look-ahead cache is exhausted). This slot-based approach eliminates all counter reads between producer and consumer.

### `size = pIdx − cIdx` — the occupancy check

Both sides use the same occupancy formula, but only during slow paths (rare). On the fast path, neither side reads the other's counter; they rely on slot nullness (FastFlow) for synchronization.

### `slot = pIdx & mask` or `slot = cIdx & mask` — the circular array index

Identical bitmask arithmetic: `& mask` replaces modulo, giving a slot in `[0, capacity)`.

### `element = buf[slot]` — no CAS, plain slot reads

Unlike SPMC and MPMC, the consumer reads the slot directly with an acquire load — because there's only one consumer, no read-before-CAS is needed. The consumer is the sole owner of the take path. After reading, the consumer nulls the slot with a release store, which serves two purposes: (1) signals to the producer that the slot is free, and (2) releases any bookkeeping updates to other cores via the release semantics.

### Why SPSC doesn't read producer state

The consumer never reads `pIdx`. Instead, it reads the slot directly: if the slot is non-null, data is ready. If null, the buffer is empty. The producer wrote a non-null value via a release store, and the consumer reads via an acquire load, forming a happens-before pair. This is the core of FastFlow: **eliminate cross-core reads entirely**.

The diagram marks slots as "live" (still owned by the producer-consumer pair) or "stale" (consumed but not yet overwritten). Unlike SPMC where consumers CAS and have ordering concerns, SPSC clears immediately because only one consumer exists — there's no race.

## Creating Instances

Ring buffers are instantiated via the companion object's `apply` method. `SpscRingBuffer[A]` uses the FastFlow pattern with a look-ahead cache. On the fast path, the producer checks a cached `producerLimit` to avoid reading `consumerIndex`. When the cached limit is exhausted, the slow path reads the array slot at `producerIndex + lookAheadStep` — never `consumerIndex` directly. This keeps the producer and consumer cache lines fully independent. The consumer uses null/non-null slot reads (FastFlow semantics). Together, these avoid repeated volatile reads and minimize cross-core cache traffic.

```scala
object SpscRingBuffer {
  def apply[A <: AnyRef](capacity: Int): SpscRingBuffer[A]
}
```

Creates a new SPSC ring buffer with the given capacity. The capacity must be a positive power of two.

We create an SPSC buffer as follows:

```scala mdoc:compile-only
import zio.blocks.ringbuffer.SpscRingBuffer

val rb = SpscRingBuffer[String](16)  // capacity must be power of 2
```

## Operations

`SpscRingBuffer` provides operations for sending and receiving elements, as well as querying buffer state:

### Inserting Elements — `SpscRingBuffer#offer`

Use `SpscRingBuffer#offer` to insert an element with this signature:

```scala
final class SpscRingBuffer[A <: AnyRef](val capacity: Int) {
  def offer(a: A): Boolean
}
```

Inserts the element without blocking. Returns `true` on successful insertion, `false` when the buffer is full. Raises `NullPointerException` if the element is `null`. Call only from the producer thread.

To handle backpressure by checking if insertion succeeds:

```scala mdoc:silent:reset
import zio.blocks.ringbuffer.SpscRingBuffer

val rb = SpscRingBuffer[String](4)
```

When the buffer becomes full, `offer` returns `false`:

```scala mdoc
val result1 = rb.offer("a")
val result2 = rb.offer("b")
val result3 = rb.offer("c")
val result4 = rb.offer("d")
val result5 = rb.offer("e")
```

### Removing Elements — `SpscRingBuffer#take`

Use `SpscRingBuffer#take` to remove an element with this signature:

```scala
final class SpscRingBuffer[A <: AnyRef](val capacity: Int) {
  def take(): A
}
```

Retrieves and removes an element from the front of the buffer. Returns immediately without blocking, providing the element or `null` if the buffer is empty. Call only from the consumer thread.

To retrieve elements in FIFO order:

```scala mdoc
rb.take()
rb.take()
rb.take()
rb.take()
rb.take()
```

### Checking State — `size`, `isEmpty`, `isFull`

Ring buffers provide three query methods to check their state. Note that under concurrent access, these results are **approximate** — by the time the method returns, other threads may modify the buffer.

Use `SpscRingBuffer#size` to get the approximate element count with this signature:

```scala
final class SpscRingBuffer[A <: AnyRef](val capacity: Int) {
  def size: Int
}
```

Returns the approximate number of elements currently in the buffer. Under concurrent access, the result may be stale. O(1).

Use `SpscRingBuffer#isEmpty` to check if the buffer is empty with this signature:

```scala
final class SpscRingBuffer[A <: AnyRef](val capacity: Int) {
  def isEmpty: Boolean
}
```

Returns `true` if the buffer contains no elements (approximate). O(1).

Use `SpscRingBuffer#isFull` to check if the buffer is at capacity with this signature:

```scala
final class SpscRingBuffer[A <: AnyRef](val capacity: Int) {
  def isFull: Boolean
}
```

Returns `true` if the buffer is at capacity (approximate). O(1).

All four implementations provide the same three methods. To check state after operations:

```scala mdoc:silent:reset
import zio.blocks.ringbuffer.SpscRingBuffer

val rb2 = SpscRingBuffer[String](8)
```

State queries are cheap but approximate under concurrency:

```scala mdoc
rb2.offer("x")
rb2.offer("y")

rb2.size
rb2.isEmpty
rb2.isFull
```

### Batch Operations — `SpscRingBuffer#drain` and `SpscRingBuffer#fill`

Use `SpscRingBuffer#drain` to consume up to N elements with this signature:

```scala
final class SpscRingBuffer[A <: AnyRef](val capacity: Int) {
  def drain(consumer: A => Unit, limit: Int): Int
}
```

Removes up to `limit` elements from the buffer, passing each to the `consumer` callback. Returns the number of elements actually drained. Raises `IllegalArgumentException` if `limit` is negative. Call only from the consumer thread. O(n) where n is the number of elements drained. If the callback raises an exception, all elements passed to it up to that point remain consumed and the buffer stays in a consistent state.

Use `SpscRingBuffer#fill` to produce up to N elements with this signature:

```scala
final class SpscRingBuffer[A <: AnyRef](val capacity: Int) {
  def fill(supplier: () => A, limit: Int): Int
}
```

Inserts up to `limit` elements by calling the `supplier` for each new element. Returns the number of elements actually inserted. Raises `IllegalArgumentException` if `limit` is negative. Raises `NullPointerException` if the supplier returns `null`. Call only from the producer thread. O(n) where n is the number of elements inserted.

Batch operations amortize synchronization costs. To drain multiple elements at once:

```scala mdoc:silent:reset
import zio.blocks.ringbuffer.SpscRingBuffer

val rb3 = SpscRingBuffer[java.lang.Integer](16)
(1 to 5).foreach(i => rb3.offer(Integer.valueOf(i)))

val collected = scala.collection.mutable.Buffer[java.lang.Integer]()
val drained = rb3.drain(collected += _, 10)  // drained = 5

println(s"Drained items: ${collected.mkString(", ")}")
```

To avoid repeated `offer` calls when producing elements, use fill:

```scala mdoc:silent:reset
import zio.blocks.ringbuffer.SpscRingBuffer
import java.util.concurrent.atomic.AtomicInteger

val rb4 = SpscRingBuffer[String](8)
val counter = new AtomicInteger(0)
val filled = rb4.fill(() => { counter.incrementAndGet(); s"item-${counter.get()}" }, 5)

println(s"Filled $filled items")
```

## Primitive variants

The generic `SpscRingBuffer[A]` stores `AnyRef` slots, which boxes Java primitives on the producer side. For SPSC workloads handing off `Int`, `Long`, `Float`, or `Double` values between two threads, four zero-boxing companions are provided:

| Type | Class | Slot encoding |
|---|---|---|
| `Int`    | `IntSpscRingBuffer`    | one `Long` per slot: high 32 bits hold an occupancy tag (`0` = empty, `1` = data, `2` = DONE sentinel), low 32 bits hold the `Int` payload. The whole 64-bit word is `0L` when empty, so any tagged value is non-zero regardless of the payload bits. |
| `Long`   | `LongSpscRingBuffer`   | one `Long` per slot. `Long.MinValue` is reserved as the empty marker and `Long.MinValue + 1L` as the in-band DONE sentinel; offering either value throws `IllegalArgumentException`. All other `Long` values pass through unchanged. |
| `Float`  | `FloatSpscRingBuffer`  | one `Long` per slot: high 32 bits tag (`0` = empty, `1` = data, `2` = DONE), low 32 bits hold `floatToRawIntBits(value)`. The whole word is `0L` when empty. Every `Float` (including `0.0f`, `NaN`, `±Inf`) is representable. |
| `Double` | `DoubleSpscRingBuffer` | one `Long` per slot. `doubleToRawLongBits(value)` is stored directly; two reserved non-canonical `NaN` bit patterns mark empty (`0xFFF8_0000_0000_0001L`) and DONE (`0xFFF8_0000_0000_0002L`). Any `NaN` payload is canonicalized to `Double.NaN` on offer so it can never collide with the reserved markers. |

All four use the same FastFlow algorithm as the generic version — single backing `Array[Long]`, padded indices, look-ahead cache — and expose the same core operations (`offer`, `take`, `peek`, `isEmpty`, `isFull`, `size`, `capacity`). The only API difference is that `take` returns a primitive instead of `AnyRef`, so callers should check `peek()` first to know whether the next read will be a real value or just the empty marker. (The generic `drain`/`fill` batch helpers are not provided on the primitive variants; loop `peek`/`take` directly.)

In addition, each primitive variant exposes two methods for **in-band end-of-stream signalling**, used by the concurrent stream operators to propagate "no more data" through a primitive queue without needing a separate side channel:

- `offerDone(): Boolean` — write the DONE sentinel into the next free slot, returning `true` on success or `false` if the queue is full. Once `offerDone()` succeeds the producer should not offer any further values.
- `pollPacked(): Long` — atomically poll the next slot and return its packed encoding: `EMPTY_PACKED`/`EMPTY` / `EMPTY_BITS` when empty (consumer index NOT advanced), `DONE_PACKED`/`DONE` / `DONE_BITS` for the DONE sentinel (index advanced), or a non-sentinel encoded value otherwise (index advanced). The exact constant names live on each companion object (`IntSpscRingBuffer.DONE_PACKED`, `LongSpscRingBuffer.DONE`, `FloatSpscRingBuffer.DONE_PACKED`, `DoubleSpscRingBuffer.DONE_BITS`).

`pollPacked` is intended for advanced consumers that want a single atomic operation distinguishing empty / data / DONE. Most application code that simply needs primitive throughput is fine using the higher-level concurrent stream operators below.

```scala mdoc:silent:reset
import zio.blocks.ringbuffer.{IntSpscRingBuffer, LongSpscRingBuffer, DoubleSpscRingBuffer}

val ints = new IntSpscRingBuffer(16)
ints.offer(42)
if (ints.peek()) {
  val v: Int = ints.take()     // 42
}

val longs = new LongSpscRingBuffer(16)
longs.offer(Long.MaxValue)
if (longs.peek()) {
  val w: Long = longs.take()   // Long.MaxValue
}

val doubles = new DoubleSpscRingBuffer(16)
doubles.offer(Double.NaN)      // canonicalized to Double.NaN's bit pattern on offer
```

These variants underpin the primitive-specialized concurrent stream operators (`mapPar`, `mergeAll`, `flatMapPar`) so primitive streams do not box during cross-thread handoff.

## Examples

### SPSC: Producer-Consumer Ping-Pong

In a single-producer, single-consumer setup, use `SpscRingBuffer` for maximum throughput. This example demonstrates how two threads communicate efficiently using FastFlow signaling.

```scala mdoc:passthrough
import docs.SourceFile

SourceFile.print("zio-blocks-examples/src/main/scala/ringbuffer/SpscExample.scala")
```

([source](https://github.com/zio/zio-blocks/blob/main/zio-blocks-examples/src/main/scala/ringbuffer/SpscExample.scala))

```bash
sbt "zio-blocks-examples/runMain ringbuffer.SpscExample"
```

### Batch Fill and Drain (SPSC)

Use `fill` and `drain` for efficient batch operations. This example shows how to amortize synchronization costs by processing multiple elements at once.

```scala mdoc:passthrough
import docs.SourceFile

SourceFile.print("zio-blocks-examples/src/main/scala/ringbuffer/BatchExample.scala")
```

([source](https://github.com/zio/zio-blocks/blob/main/zio-blocks-examples/src/main/scala/ringbuffer/BatchExample.scala))

```bash
sbt "zio-blocks-examples/runMain ringbuffer.BatchExample"
```
