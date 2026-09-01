# MPMC RingBuffer

> `MpmcRingBuffer[A]` is the fully general-purpose implementation for systems with multiple producer and consumer threads. It uses the **Vyukov/Dmitry sequence-buffer algorithm**, a sophisticated lock-free design that coordinates all access through monotonically increasing indices and per-slot sequence stamps—making every slot's state self-describing at any moment.

## Algorithm

`MpmcRingBuffer` handles the hardest case: **many producers and many consumers** all accessing the same buffer at once. It uses the **Vyukov/Dmitry algorithm**, which is a lock-free MPMC queue design.

The challenge: when multiple producers might try to write to the same slot, and multiple consumers might try to read the same slot, we need a fair way to say "this slot is mine" without using locks.

**The trick: Give each slot a "ticket number" that changes in a predictable cycle.**

The algorithm runs two parallel arrays of the same length:

- `buffer[i]` — holds the actual element at position `i`
- `seqBuf[i]` — holds a *sequence stamp* at position `i`

The sequence stamps are the heart of the algorithm. Each stamp encodes the *ownership state* of its slot: who is allowed to act on it and what they are allowed to do.

On initialization, `seqBuf[i]` is set to `i`. The producer index `pIdx` and consumer index `cIdx` both start at zero. From here, every operation follows the same pattern: read the stamp, compute a single difference (`diff`), and branch on whether `diff` is zero, negative, or positive.

At any moment, slot `i` (where `i = index & mask`) is in exactly one of three states:

| stamp value                 | meaning                                          |
|-----------------------------|--------------------------------------------------|
| if `seq == pIdx`            | Slot is free — the producer at `pIdx` may write  |
| if `seq == cIdx + 1`        | Data written — the consumer at `cIdx` may read   |
| if `seq == cIdx + capacity` | Slot consumed — free for the producer's next lap |

The producer looks for `diff = seq - pIdx == 0`. The consumer looks for `diff = seq - (cIdx + 1) == 0`. In both cases, a negative diff means the other side has fallen behind (buffer full or empty), and a positive diff means another thread already claimed this slot and you should retry.

### Diagram

To see the sequence buffer in action, use this interactive stepper. The component below implements the algorithm faithfully in React. Type any label, click **Offer** to enqueue or **Take** to dequeue, and watch the trace panel show every intermediate variable — `pIdx`, `slot`, `seq`, `diff` — and the exact decision the algorithm makes from them.

Click "Step Producer" and "Step Consumer" to see how the algorithm coordinates access handoff without locks.

Here is a complete walkthrough of every variable in the trace, in the order the algorithm computes them.

### `pIdx` / `cIdx` — the monotonic counters

These two numbers are the heartbeat of the entire algorithm. `pIdx` is the producer's counter and `cIdx` is the consumer's counter. They start at zero and *only ever increase* — they never wrap, never reset, never go backwards. After a million operations `pIdx` might be 1,000,000 and `cIdx` might be 999,996. The raw slot position is derived from them rather than stored directly, which is what makes the algorithm safe for multiple concurrent threads.

### `slot = idx & mask` — the circular array index

Because the buffer has a power-of-two capacity (4 in the demo), `mask = capacity - 1 = 3`, which in binary is `0011`. The bitwise AND strips everything above the lowest two bits, giving a number in the range `[0, 3]`. This is mathematically identical to `idx % capacity` but costs a single CPU instruction instead of a division. So `pIdx = 7` maps to slot `7 & 3 = 3`, and `pIdx = 8` maps back to slot `8 & 3 = 0` — that is the wrap-around.

### `seq = seqBuf[slot]` — the sequence stamp

Every slot carries its own stamp, completely independent of the other slots. The stamp is not a lock and not a boolean "occupied/free" flag — it is a number that encodes the *exact generation* of the slot. On construction `seqBuf[i] = i`, so slot 0 starts at 0, slot 1 at 1, and so on. After each write the stamp advances by 1. After each consume it advances by `capacity`. Because of this, slot 0's stamp trail across three laps looks like `0 → 1 → 4 → 5 → 8 → 9 → 12 → 13 …` — it grows forever and never repeats, which is what prevents the ABA problem entirely.

### `expected` (take only) `= cIdx + 1`

The producer, after winning its CAS and writing data, stamps the slot with `pIdx + 1`. So if a producer claimed slot 0 when `pIdx` was 4, it leaves `seqBuf[0] = 5`. The consumer that arrives with `cIdx = 4` therefore looks for `seqBuf[0] == cIdx + 1 == 5`. The `+ 1` is the handshake signal: *"a producer has finished writing here, and you are the right consumer to read it."* The offer trace does not need an `expected` row because the producer compares `seq` directly against `pIdx` (not `pIdx + 1`) — the slot is free when the stamp equals the producer index exactly.

### `diff` — the three-way decision

This is the key insight of the Vyukov algorithm. A single subtraction replaces what would otherwise be a tangle of conditional checks.

For **offer**: `diff = seq − pIdx`
- `diff == 0` — the stamp exactly matches the producer index, meaning no one has touched this slot since it was last released. The slot is yours. The thread does a CAS on `pIdx`, writes the element, then stamps `seqBuf[slot] = pIdx + 1`.
- `diff < 0` — the stamp is *behind* the producer index. This means the slot is still occupied by data from the current lap that has not been consumed yet. The buffer is full. Return `false`.
- `diff > 0` — the stamp is *ahead* of the producer index. Another producer already claimed this slot and advanced past it. Retry the loop with a fresh read of `pIdx`.

For **take**: `diff = seq − expected` where `expected = cIdx + 1`
- `diff == 0` — the stamp matches exactly what the producer left. Data is ready. CAS on `cIdx`, read the element, null out the slot for GC, stamp `seqBuf[slot] = cIdx + capacity` to release the slot for a future producer on the next lap.
- `diff < 0` — the stamp is behind what the consumer expects, meaning the producer has not finished writing yet (or has not written at all). The buffer appears empty from this consumer's perspective. Return `null`.
- `diff > 0` — another consumer already read this slot and advanced past it. Retry.

### Why all three decisions are safe without any lock

The diff check and the subsequent CAS form an atomic claim. Two producers might both read the same `pIdx` and both see `diff == 0`, but only one will win the CAS that advances `pIdx`. The loser sees the CAS fail, loops back, reads the new `pIdx`, and naturally ends up looking at the next slot. No explicit coordination between threads is ever needed — the sequence stamps and the monotonically increasing indices together make every slot's state self-describing at any point in time.

## Creating Instances

`MpmcRingBuffer[A]` is the fully general-purpose implementation supporting any number of producers and consumers. It uses the Vyukov/Dmitry algorithm with a parallel sequence buffer to coordinate access safely.

```scala
object MpmcRingBuffer {
  def apply[A <: AnyRef](capacity: Int): MpmcRingBuffer[A]
}
```

Creates a new MPMC ring buffer with the given capacity. The capacity must be a power of two >= 2 (the sequence buffer algorithm requires at least 2 slots). A capacity of 1 is valid for `SpscRingBuffer`, `SpmcRingBuffer`, and `MpscRingBuffer`, but `MpmcRingBuffer` needs at least 2 because its sequence stamp mechanism requires a second slot to distinguish the written-but-not-consumed state from the empty state.

To create an MPMC buffer:

```scala mdoc:compile-only
import zio.blocks.ringbuffer.MpmcRingBuffer

val rb = MpmcRingBuffer[String](128)
```

## Operations

`MpmcRingBuffer` provides operations for multiple producers and consumers to exchange elements:

### Inserting Elements — `MpmcRingBuffer#offer`

Use `MpmcRingBuffer#offer` to insert an element with this signature:

```scala
final class MpmcRingBuffer[A <: AnyRef](val capacity: Int) {
  def offer(a: A): Boolean
}
```

Inserts the element without blocking. Returns `true` on successful insertion, `false` when the buffer is full. Raises `NullPointerException` if the element is `null`. Thread-safe; multiple producer threads may call this concurrently.

### Removing Elements — `MpmcRingBuffer#take`

Use `MpmcRingBuffer#take` to remove an element with this signature:

```scala
final class MpmcRingBuffer[A <: AnyRef](val capacity: Int) {
  def take(): A
}
```

Retrieves and removes an element from the front of the buffer. Returns immediately without blocking, providing the element or `null` if the buffer is empty. Thread-safe; multiple consumer threads may call this concurrently.

### Checking State — `size`, `isEmpty`, `isFull`

Ring buffers provide three query methods to check their state. Note that under concurrent access, these results are **approximate** — by the time the method returns, other threads may modify the buffer.

All four implementations provide the same method signatures: `size`, `isEmpty`, and `isFull`. See the [`SpscRingBuffer` Operations](./spsc.mdx#operations) section for detailed descriptions and examples of these methods.

## Examples

### MPMC: General-Purpose Queue

For workloads with multiple producers and consumers, use `MpmcRingBuffer`. This example demonstrates how multiple workers coordinate to process tasks from a shared queue.

```scala mdoc:passthrough
import docs.SourceFile

SourceFile.print("zio-blocks-examples/src/main/scala/ringbuffer/MpmcExample.scala")
```

([source](https://github.com/zio/zio-blocks/blob/main/zio-blocks-examples/src/main/scala/ringbuffer/MpmcExample.scala))

```bash
sbt "zio-blocks-examples/runMain ringbuffer.MpmcExample"
```
