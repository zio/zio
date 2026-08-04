# MPSC RingBuffer

> `MpscRingBuffer[A]` handles the inverse case: multiple producer threads safely offering elements to a single consumer thread. It uses a **hybrid design** combining producer-side CAS coordination with a FastFlow-style relaxed-poll consumer, balancing producer contention while keeping the consumer blazingly fast.

`MpscRingBuffer[A]` handles the inverse case: multiple producer threads safely offering elements to a single consumer thread. It uses a **hybrid design** combining producer-side CAS coordination with a FastFlow-style relaxed-poll consumer, balancing producer contention while keeping the consumer blazingly fast.

## Algorithm

`MpscRingBuffer` handles multiple producers with a single consumer, based on the **JCTools `MpscArrayQueue`** design. It's a hybrid: producers use CAS among themselves, while the consumer remains FastFlow-style.

**How it works:**

- The **producers** (multiple) coordinate via CAS on the shared `producerIndex`. Each producer claims a slot by atomically incrementing `producerIndex`, then writes its element with release semantics. A cached `producerLimit` (initialized to capacity) reduces volatile reads of `consumerIndex`.
- The **consumer** (single) uses **FastFlow relaxed-poll semantics**: it reads array slots directly. A `null` slot indicates either the buffer is empty or a producer has claimed the slot via CAS but has not yet written the element (mid-write). In both cases, `take` returns `null` rather than spinning. The consumer clears the slot after reading.
- The `producerLimit` is updated opportunistically (racy updates are benign) to reflect approximate available capacity.

**Why hybrid?** Multiple producers need CAS to coordinate their offers, but the single consumer can achieve maximum speed using pure FastFlow (no CAS, no reading producer state).

### Diagram

To see the hybrid algorithm in action, use this interactive stepper. Type any label, click **Offer** to enqueue or **Take** to dequeue, and watch the trace panel show every intermediate variable — `pIdx`, `cIdx`, `size`, `slot`, `value` — and the exact decision the algorithm makes.

Here is a complete walkthrough of every variable in the trace, in the order the algorithm computes them.

### `pIdx` / `cIdx` — the monotonic counters

Like the MPMC algorithm, both counters start at zero and only ever increase — they never wrap, never reset, never go backwards. `pIdx` is shared by all producer threads: each producer atomically claims the next slot by performing a compare-and-swap (CAS) from `pIdx` to `pIdx + 1`. Whoever wins the CAS owns that slot. `cIdx` is read with a plain load because only one thread ever consumes, so no CAS is needed.

### `size = pIdx − cIdx` — the occupancy check

Before claiming a slot, the producer checks whether the buffer is full: if `pIdx − cIdx == capacity`, every slot is occupied and `offer` returns `false` immediately. The difference `pIdx − cIdx` is the number of elements currently in the buffer. Under real concurrent access, the JVM implementation maintains a cached `producerLimit` to avoid reading the volatile `consumerIndex` on every `offer` call — but the fundamental check is the same occupancy test shown here.

### `slot = pIdx & mask` — the circular array index

Because capacity is a power of two, `mask = capacity - 1`, and the bitwise AND strips high bits to give a slot number in `[0, capacity)`. This is mathematically equivalent to `pIdx % capacity` but costs a single CPU instruction. So `pIdx = 7` maps to slot `7 & 3 = 3`, and `pIdx = 8` wraps back to slot `0`.

### `value = buf[slot]` — the FastFlow slot check

The consumer uses the **FastFlow relaxed-poll pattern**: read the array slot directly with acquire semantics. If the slot is `null`, either the buffer is empty, or a producer has won the CAS but has not yet finished writing the element (mid-write). In both cases, `take` returns `null` rather than blocking or spinning — this is the *relaxed poll* semantic. This is why the consumer never reads `pIdx`; it derives all its information from the slot value itself.

Once the producer wins its CAS, it writes the element with release semantics. The acquire/release pair guarantees the consumer will see the fully written element as soon as the slot is non-null.

## Creating Instances

`MpscRingBuffer[A]` allows multiple producer threads to offer elements concurrently via compare-and-swap on the producer index, while a single consumer thread takes elements efficiently.

```scala
object MpscRingBuffer {
  def apply[A <: AnyRef](capacity: Int): MpscRingBuffer[A]
}
```

Creates a new MPSC ring buffer with the given capacity. The capacity must be a positive power of two.

To create an MPSC buffer:

```scala mdoc:compile-only
import zio.blocks.ringbuffer.MpscRingBuffer

val rb = MpscRingBuffer[java.lang.Long](32)
```

## Operations

`MpscRingBuffer` supports inserting elements from multiple producers, removing from a single consumer, and batch operations:

### Inserting Elements — `MpscRingBuffer#offer`

Use `MpscRingBuffer#offer` to insert an element with this signature:

```scala
final class MpscRingBuffer[A <: AnyRef](val capacity: Int) {
  def offer(a: A): Boolean
}
```

Inserts the element without blocking. Returns `true` on successful insertion, `false` when the buffer is full. Raises `NullPointerException` if the element is `null`. Thread-safe; multiple producer threads may call this concurrently.

### Removing Elements — `MpscRingBuffer#take`

Use `MpscRingBuffer#take` to remove an element with this signature:

```scala
final class MpscRingBuffer[A <: AnyRef](val capacity: Int) {
  def take(): A
}
```

Retrieves and removes an element from the front of the buffer. Returns immediately without blocking, providing the element or `null` if the buffer is empty. Call only from the single consumer thread.

### Checking State — `size`, `isEmpty`, `isFull`

Ring buffers provide three query methods to check their state. Note that under concurrent access, these results are **approximate** — by the time the method returns, other threads may modify the buffer.

All four implementations provide the same method signatures: `size`, `isEmpty`, and `isFull`. See the [`SpscRingBuffer` Operations](./spsc.mdx#operations) section for detailed descriptions and examples of these methods.

### Batch Operations — `MpscRingBuffer#drain`

Use `MpscRingBuffer#drain` to consume up to N elements with this signature:

```scala
final class MpscRingBuffer[A <: AnyRef](val capacity: Int) {
  def drain(consumer: A => Unit, limit: Int): Int
}
```

Removes up to `limit` elements from the buffer, passing each to the `consumer` callback. Returns the number of elements actually drained. Raises `IllegalArgumentException` if `limit` is negative. Call only from the consumer thread. O(n) where n is the number of elements drained.

**Note**: Uses relaxed poll semantics and stops at the first `null` slot, which may indicate either an empty buffer or a producer that has claimed a slot but has not yet written its element (mid-write). In the mid-write case, fewer than `limit` elements are returned even though more elements will become available shortly. If the callback throws, all elements passed to it up to that point remain consumed and the buffer is in a consistent state.

## Examples

### MPSC: Multiple Producers, Single Aggregator

When multiple threads produce work for a single processor, use `MpscRingBuffer`. This example shows how three producer threads safely offer items to a single consumer.

```scala mdoc:passthrough
import docs.SourceFile

SourceFile.print("zio-blocks-examples/src/main/scala/ringbuffer/MpscExample.scala")
```

([source](https://github.com/zio/zio-blocks/blob/main/zio-blocks-examples/src/main/scala/ringbuffer/MpscExample.scala))

```bash
sbt "zio-blocks-examples/runMain ringbuffer.MpscExample"
```
