# Ring Buffer

> `zio.blocks.ringbuffer` is a family of **high-performance, bounded ring buffers** for the JVM (and Scala.js). Each variant is optimized for a specific producer/consumer threading pattern—pick the one that matches your use case and get the fastest possible inter-thread communication with zero dependencies.

# ZIO Blocks — Ring Buffer (`zio.blocks.ringbuffer`)

`zio.blocks.ringbuffer` is a family of **high-performance, bounded ring buffers** for the JVM (and Scala.js). Each variant is optimized for a specific producer/consumer threading pattern—pick the one that matches your use case and get the fastest possible inter-thread communication with zero dependencies.

All variants expose `offer` (returns `false` if full) and `take` (returns `null` if empty)—both non-blocking. Capacity must be a **power of two** (enables bitwise masking instead of modulo). Elements must be **non-null reference types** (`A <: AnyRef`).

## Ring buffer variants

| Type | Producers | Consumers | Algorithm |
|------|-----------|-----------|-----------|
| `SpscRingBuffer` | 1 | 1 | FastFlow (null/non-null signaling) |
| `SpmcRingBuffer` | 1 | Many | Index-based capacity + CAS consumers |
| `MpscRingBuffer` | Many | 1 | CAS producers + relaxed poll |
| `MpmcRingBuffer` | Many | Many | Vyukov/Dmitry sequence buffer |

**Naming convention:** `S` = single, `M` = multi, `p` = producer, `c` = consumer.

---

## Installation

```scala
libraryDependencies += "dev.zio" %% "zio-blocks-ringbuffer" % "0.0.33"
```

---

## Quick start

```scala

val buf = SpscRingBuffer[String](1024) // capacity must be a power of 2

// Producer thread
buf.offer("hello") // true if inserted, false if full

// Consumer thread
val msg: String = buf.take() // element or null if empty
```

---

## API

Every ring buffer provides:

```scala
def offer(a: A): Boolean   // insert; returns false if full
def take(): A               // remove; returns null if empty
def size: Int               // approximate element count
def isEmpty: Boolean        // approximate emptiness check
def isFull: Boolean         // approximate fullness check
```

`SpscRingBuffer` additionally provides batch operations:

```scala
def drain(consumer: A => Unit, limit: Int): Int  // drain up to limit elements
def fill(supplier: () => A, limit: Int): Int      // fill up to limit slots
```

All `size`/`isEmpty`/`isFull` values are **approximate** under concurrency—they are snapshots that may be stale by the time the caller acts on them.

---

## Choosing a variant

Use the most constrained variant that fits your threading model:

| Scenario | Recommended |
|----------|-------------|
| Dedicated pipeline: one writer thread, one reader thread | `SpscRingBuffer` |
| Fan-in: many writers, one reader (e.g., logging, event aggregation) | `MpscRingBuffer` |
| Fan-out: one writer, many readers (e.g., work distribution) | `SpmcRingBuffer` |
| General purpose: any number of writers and readers | `MpmcRingBuffer` |

---

## Usage examples

### SPSC with drain/fill

```scala

val buf = SpscRingBuffer[java.lang.Integer](64)

// Producer: batch-fill from a data source
var seq = 0
val filled = buf.fill(() => { seq += 1; Integer.valueOf(seq) }, 32)
println(s"Filled $filled elements")

// Consumer: batch-drain into a processor
val drained = buf.drain(e => println(s"Processing $e"), 32)
println(s"Drained $drained elements")
```

### MPSC fan-in (multiple producers, single consumer)

```scala

val buf = MpscRingBuffer[String](256)

// Multiple producer threads
for (i <- 0 until 4) {
  new Thread(() => {
    for (j <- 0 until 100)
      buf.offer(s"producer-$i: message-$j")
  }).start()
}

// Single consumer thread
new Thread(() => {
  var msg = buf.take()
  while (msg != null) {
    println(msg)
    msg = buf.take()
  }
}).start()
```

### SPMC fan-out (single producer, multiple consumers)

```scala

val buf = SpmcRingBuffer[String](256)

// Single producer thread
new Thread(() => {
  for (i <- 0 until 400)
    while (!buf.offer(s"task-$i")) {} // retry if full
}).start()

// Multiple consumer (worker) threads
for (w <- 0 until 4) {
  new Thread(() => {
    var msg = buf.take()
    while (msg != null) {
      println(s"worker-$w: $msg")
      msg = buf.take()
    }
  }).start()
}
```

### Non-blocking try-once with fallback

```scala

val buf = MpmcRingBuffer[String](64)

// Try to offer without blocking; handle backpressure yourself
if (!buf.offer("data")) {
  // buffer is full — drop, log, or retry later
  println("Buffer full, applying backpressure")
}

// Try to take without blocking
val result = buf.take()
if (result != null) {
  println(s"Got: $result")
} else {
  // buffer is empty — do other work
}
```

---

## Design notes

### FastFlow pattern (SPSC)

`SpscRingBuffer` uses the FastFlow algorithm: the **null/non-null state of an array slot** is the synchronization signal. The producer never reads `consumerIndex`; the consumer never reads `producerIndex`. This minimizes cross-core cache traffic to a single cache line per operation.

A **look-ahead step** (`capacity/4`, capped at 4096) lets the producer batch-check multiple future slots at once, further reducing the frequency of slow-path reads.

### Vyukov/Dmitry sequence buffer (MPMC)

`MpmcRingBuffer` uses a parallel `Long` sequence buffer alongside the data array. Each slot carries a stamp indicating whether it is available for writing or reading. Both `producerIndex` and `consumerIndex` are advanced via CAS, allowing any number of threads on both sides. The minimum capacity is 2 (the algorithm requires at least 2 slots to distinguish written from consumed).

### CAS-based producers (MPSC)

`MpscRingBuffer` follows the JCTools `MpscArrayQueue` design: producers claim a slot via CAS on `producerIndex`, then write the element with release semantics. A cached `producerLimit` avoids reading `consumerIndex` on every offer, reducing cross-core traffic. The consumer side uses relaxed poll semantics—a `null` slot means either empty or a producer mid-write.

### Index-based SPMC

`SpmcRingBuffer` uses index-based capacity checking on the producer side (no CAS needed for a single producer). Consumers use a CAS loop on `consumerIndex`. Consumers read the element *before* the CAS to avoid a race with the producer overwriting the slot. Consumers do not null array slots after reading—the producer overwrites them on the next lap.

### Cache-line padding

All ring buffers use **128-byte padding regions** (16 `Long` fields) between producer and consumer fields. This prevents false sharing on all architectures, including Apple Silicon which uses 128-byte cache lines (most x86 CPUs use 64-byte lines).

The padding is implemented via a class hierarchy:

```
Pad0 → ProducerFields → Pad1 → ConsumerFields → Pad2
```

### VarHandle for memory ordering

All JVM implementations use `java.lang.invoke.VarHandle` (Java 9+) for acquire/release semantics instead of `sun.misc.Unsafe`. This is the recommended modern approach for lock-free data structures on the JVM.

### Power-of-two masking

Capacity must be a power of two. This allows `index & (capacity - 1)` instead of `index % capacity`, which is significantly faster because bitwise AND compiles to a single CPU instruction.

---

## Thread-safety contract

Violating the threading contract (e.g., calling `take` from multiple threads on an `SpscRingBuffer`) results in **undefined behavior**. No runtime check is performed—this is enforced by contract for maximum performance.

| Type | `offer` | `take` |
|------|---------|--------|
| `SpscRingBuffer` | Single producer thread only | Single consumer thread only |
| `SpmcRingBuffer` | Single producer thread only | Any number of consumer threads |
| `MpscRingBuffer` | Any number of producer threads | Single consumer thread only |
| `MpmcRingBuffer` | Any number of producer threads | Any number of consumer threads |

---

## Performance characteristics

| Operation | Complexity |
|-----------|-----------|
| `offer` | Lock-free (SPSC/SPMC: wait-free) |
| `take` | Lock-free (SPSC/MPSC: wait-free) |

**SPSC** is the fastest: no CAS, no locks, minimal cache-line traffic. Use it whenever your threading model allows a dedicated producer-consumer pair.

---

## Cross-platform support

On **Scala.js**, all ring buffer types compile and provide the same API surface. Since Scala.js is single-threaded, the JS implementations use plain reads and writes with no memory ordering primitives.

| Platform | Support |
|----------|---------|
| JVM | Full concurrency support |
| Scala.js | Sequential (same API) |
