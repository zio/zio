# SPMC RingBuffer

> `SpmcRingBuffer[A]` allows a single producer thread to efficiently feed multiple consumer threads. It uses an **index-based algorithm** where slot validity is determined by comparing producer and consumer indices, allowing multiple consumers to coordinate safely via CAS operations on a shared consumer index.

## Algorithm

`SpmcRingBuffer` allows a single producer to feed multiple consumers. The algorithm is **index-based**: slot validity is determined by comparing `producerIndex` and `consumerIndex`, not by null-checking slots.

**How it works:**

- The **producer** is single-threaded and never uses CAS. It maintains a `producerLimit` derived from reading the volatile `consumerIndex`. On the fast path, it checks its local limit; when exhausted, it refreshes by reading `consumerIndex`. This design avoids the producer ever reading consumer state on the fast path.
- The **consumers** (any number) coordinate via a CAS loop on `consumerIndex`. Each consumer reads the element at its claimed index, then attempts to CAS `consumerIndex` forward. If CAS fails, it retries with a refreshed index. This ensures each element is claimed by exactly one consumer.
- **No slot clearing by consumers:** Consumers do not write `null` after reading. Instead, the producer safely overwrites slots once its `producerLimit` check (based on `consumerIndex`) confirms all consumers have advanced past them. This eliminates the race that would occur if a consumer claimed a slot but hadn't yet cleared it.

**Trade-off:** Consumer CAS introduces overhead under contention, but the producer remains extremely fast (no synchronization).

### Diagram

To see the single-producer multi-consumer algorithm in action, use this interactive stepper. Type any label, click **Offer** to enqueue (single producer) or **Take** to dequeue (any consumer), and watch the trace panel show every intermediate variable — `pIdx`, `cIdx`, `size`, `slot`, `element` — and the exact decision each side makes.

Here is a complete walkthrough of every variable in the trace, in the order the algorithm computes them.

### `pIdx` — the monotonic producer counter

The producer has its own index that starts at zero and only ever increases. Because only one thread produces, `pIdx` is read and written with plain loads and stores — no CAS is needed. The producer's job is simple: check capacity (`pIdx - cIdx < capacity`), write to the slot, then advance `pIdx`.

### `cIdx` — the monotonic consumer counter (multi-threaded)

All consumers share a single `consumerIndex` counter. Because multiple consumers may be operating concurrently, each consumer reads `cIdx` with volatile semantics to see the most recent updates, then attempts to atomically advance it via CAS. If the CAS fails, another consumer claimed the slot first and the reader discards their read and retries.

### `size = pIdx − cIdx` — the occupancy check

Both sides use the same occupancy formula: `pIdx − cIdx` is the number of elements currently in the buffer. The producer checks if `size == capacity` to detect a full buffer. The consumer checks if `size == 0` to detect an empty buffer.

### `slot = pIdx & mask` or `slot = cIdx & mask` — the circular array index

Identical to all other ring buffer variants: because capacity is a power of two, the bitwise AND `& mask` replaces modulo division, giving a slot number in `[0, capacity)`. So `pIdx = 7` maps to slot `7 & 3 = 3`, and `pIdx = 8` wraps back to slot `0`.

### `element = buf[slot]` — read-before-CAS

This is the critical SPMC distinction. The consumer reads the element *before* attempting the CAS on `cIdx`. This ordering is essential: once the CAS succeeds and `cIdx` advances, the producer is permitted to overwrite that slot on its next lap. By reading the element first, the consumer guarantees it captures the value while the slot is still logically owned.

In the trace, when a consumer takes an element, you will see the `element` row highlighted — this captures the read that must happen before the CAS.

### Why SPMC doesn't clear slots

Unlike MPSC (which uses FastFlow's null-check pattern), SPMC determines slot validity purely by index comparison: if `cIdx ≤ slot < pIdx`, the slot is live. After a consumer advances `cIdx`, the slot becomes "stale" — no longer live, but still holding the old value until the producer wraps around and overwrites it. This eliminates the race that would occur if a consumer had to null out the slot while holding the CAS; instead, the producer simply overwrites when it knows the consumer has advanced.

The diagram marks stale slots with a dashed border and a "stale" label in grey.

## Creating Instances

`SpmcRingBuffer[A]` allows a single producer thread to offer elements while multiple consumer threads concurrently take elements via compare-and-swap on the consumer index. Use the companion object to instantiate a buffer:

```scala
object SpmcRingBuffer {
  def apply[A <: AnyRef](capacity: Int): SpmcRingBuffer[A]
}
```

The capacity must be a positive power of two. To create an SPMC buffer:

```scala mdoc:compile-only
import zio.blocks.ringbuffer.SpmcRingBuffer

val rb = SpmcRingBuffer[java.lang.Integer](64)
```

## Operations

`SpmcRingBuffer` provides three core operations for sending and receiving elements:

### Inserting Elements — `SpmcRingBuffer#offer`

Use `SpmcRingBuffer#offer` to insert an element with this signature:

```scala
final class SpmcRingBuffer[A <: AnyRef](val capacity: Int) {
  def offer(a: A): Boolean
}
```

Inserts the element without blocking. Returns `true` on successful insertion, `false` when the buffer is full. Raises `NullPointerException` if the element is `null`. Call only from the single producer thread; concurrent calls from multiple threads cause undefined behavior.

### Removing Elements — `SpmcRingBuffer#take`

Use `SpmcRingBuffer#take` to remove an element with this signature:

```scala
final class SpmcRingBuffer[A <: AnyRef](val capacity: Int) {
  def take(): A
}
```

Retrieves and removes an element from the front of the buffer. Returns immediately without blocking. Returns the element, or `null` if the buffer is empty. Thread-safe; multiple consumer threads may call this concurrently.

### Checking State — `size`, `isEmpty`, `isFull`

Ring buffers provide three query methods to check their state. Note that under concurrent access, these results are **approximate** — by the time the method returns, other threads may already modify the buffer.

All four implementations provide the same method signatures: `size`, `isEmpty`, and `isFull`. See the [`SpscRingBuffer` Operations](./spsc.mdx#operations) section for detailed descriptions and examples of these methods.
