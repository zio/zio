# Advanced Topics

> Ring buffers are **lock-free** but must be used correctly:

## Thread Safety and Correctness

Ring buffers are **lock-free** but must be used correctly:

- **Wrong thread access causes undefined behavior**: Using `SpscRingBuffer` from multiple producer threads produces data races, silent data loss, or crashes. Always use the implementation matching your thread pattern.
- **`SpscRingBuffer#offer` and `SpscRingBuffer#take` thread contract**: The producer thread must be the sole caller of `SpscRingBuffer#offer`; the consumer thread must be the sole caller of `SpscRingBuffer#take`. They may be the same physical thread (as in single-threaded environments like Scala.js or unit tests) or different threads.
- **State queries are approximate**: Under concurrency, `SpscRingBuffer#size`, `SpscRingBuffer#isEmpty`, and `SpscRingBuffer#isFull` may stay stale by the time they return. Do not rely on them for exact synchronization — use `SpscRingBuffer#offer`'s return value for backpressure instead.
- **Null elements are forbidden**: All implementations reject `null` with `NullPointerException`. If you need to store nullable values, wrap them in `Option` or another container.

:::warning
**Critical:** Ring buffers do not enforce thread-safety at runtime. Using the wrong implementation for your thread pattern or calling methods from the wrong thread **does not throw an exception** — it silently corrupts data. Test thoroughly and document your threading contract.
:::

## Advanced Usage: Cache-Line Padding

Ring buffers use **cache-line padding** to prevent false sharing between producer and consumer indices on modern CPUs. The padding is transparent to users but enables dramatically lower latency on multi-core systems.

Each implementation pads its internal index fields (producer index, consumer index) to occupy a full cache line (128 bytes on Apple Silicon, 64 bytes on most other architectures). This ensures that when one thread reads its index, it does not invalidate the cache line holding the other thread's index, eliminating costly cache-coherency traffic.

This optimization is automatic and requires no configuration. Ring buffers are inherently more efficient than comparable Scala and Java implementations because of this padding.

## Designing With Ring Buffers

Common patterns for using ring buffers include:

### Pattern: Producer-Consumer Pipeline

Ring buffers form the backbone of producer-consumer pipelines where one or more producers generate work and one or more consumers process it:

```
┌──────────┐       ┌──────────────┐       ┌──────────┐
│Producer 1├──────>│ RingBuffer   │<──────┤Consumer 1│
│Producer 2├──────>│ (MPMC, cap=N)│<──────┤Consumer 2│
└──────────┘       └──────────────┘       └──────────┘
```

In this pattern:
- Producers call `MpmcRingBuffer#offer` and handle backpressure if `false` is returned (e.g., retry, queue internally, apply rate limiting).
- Consumers call `MpmcRingBuffer#take` in a tight loop, checking for `null` to detect empty buffers.
- Ring buffer capacity bounds memory and provides natural backpressure.

### Pattern: Batch Processing

For workloads where producers batch elements together, use `SpscRingBuffer#fill` (SPSC) or call `SpscRingBuffer#offer` in a loop:

```
Producer fills batch of N items
         ↓
   Ring Buffer (growing)
         ↓
Consumer drain()s batch of M items
         ↓
     Process batch
```

Batching reduces per-element synchronization costs.

### Pattern: Work Stealing with Multiple Consumers (MPMC)

When multiple workers consume from the same queue, use `MpmcRingBuffer`. Each worker calls `MpmcRingBuffer#take` to grab the next item atomically:

```scala mdoc:compile-only
import zio.blocks.ringbuffer.MpmcRingBuffer

case class Task(id: Int, work: String)

val queue = MpmcRingBuffer[Task](256)

def worker(): Unit = {
  while (true) {
    val task = queue.take()
    if (task ne null) {
      println(s"Processing: ${task.work}")
    }
  }
}
```

The CAS loop in `MpmcRingBuffer#take` ensures no two workers grab the same task.

## Performance Characteristics

All ring buffer implementations provide O(1) time complexity for `offer`, `take`, `size`, `isEmpty`, and `isFull` operations.

- **SPSC** (FastFlow) — Fastest: avoids volatile reads on the fast path, minimal cache traffic
- **SPMC** — Fast: producer uses index-based checking; consumers CAS on a shared index
- **MPSC** — Fast: producers CAS on a shared index with a cached limit; consumer uses FastFlow relaxed-poll
- **MPMC** — Slightly slower: uses sequence buffer stamps for coordination; all indices use CAS

Actual performance depends on:
- **CPU cache architecture** — 64-byte vs 128-byte cache lines affect padding efficiency
- **Contention level** — high contention increases CAS failure rates and retries
- **Element size** — larger elements may affect cache locality
- **Platform** — JVM JIT warmup, Scala.js compiled code, GraalVM-generated native image

Micro-benchmark your specific workload if latency is critical.

## Integration with Other ZIO Blocks Types

Ring buffers are standalone data structures and do not depend on other ZIO Blocks types. However, they integrate well with:

- **Threading models**: Ring buffers work on raw JVM threads, virtual threads (Loom), or platform-specific threads. Pair with `ZIO.fork` or `Thread` as needed.
- **Reactive streams**: Ring buffers can back reactive sources, where producers feed a `Source` and consumers pull from it. The ring buffer provides natural backpressure via `offer`'s return value.
- **Event loops**: In game engines or event-driven systems, ring buffers connect event producers (input, network) to event dispatchers (main loop) with predictable latency.
