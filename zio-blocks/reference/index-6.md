# RingBuffer

> Ring buffers are **fixed-size, lock-free queues** for efficiently exchanging elements between producer and consumer threads with minimal contention and cache-line effects. They use a circular array to recycle memory, eliminating garbage collection pressure from transient allocations. The `zio.blocks.ringbuffer` module provides four specialized implementations tuned for different producer/consumer thread patterns:

Ring buffers are **fixed-size, lock-free queues** for efficiently exchanging elements between producer and consumer threads with minimal contention and cache-line effects. They use a circular array to recycle memory, eliminating garbage collection pressure from transient allocations. The `zio.blocks.ringbuffer` module provides four specialized implementations tuned for different producer/consumer thread patterns:

```scala
final class SpscRingBuffer[A <: AnyRef](val capacity: Int) {
  def offer(a: A): Boolean
  def take(): A
  def size: Int
  def isEmpty: Boolean
  def isFull: Boolean
  def drain(consumer: A => Unit, limit: Int): Int
  def fill(supplier: () => A, limit: Int): Int
}

final class SpmcRingBuffer[A <: AnyRef](val capacity: Int) {
  def offer(a: A): Boolean
  def take(): A
  def size: Int
  def isEmpty: Boolean
  def isFull: Boolean
}

final class MpscRingBuffer[A <: AnyRef](val capacity: Int) {
  def offer(a: A): Boolean
  def take(): A
  def size: Int
  def isEmpty: Boolean
  def isFull: Boolean
  def drain(consumer: A => Unit, limit: Int): Int
}

final class MpmcRingBuffer[A <: AnyRef](val capacity: Int) {
  def offer(a: A): Boolean
  def take(): A
  def size: Int
  def isEmpty: Boolean
  def isFull: Boolean
}
```

## Motivation

Building low-latency systems — trading platforms, game engines, real-time event processors — requires careful control over memory allocation and CPU cache behavior. Standard JVM collections like `LinkedList` or `ArrayDeque` are convenient but have a cost: every enqueue/dequeue pair allocates a node, triggering garbage collection pauses that can destroy millisecond-scale latencies.

A naive approach is to pre-allocate a large `Array[A]` and manually manage head/tail indices. This code shows a simplified single-threaded version:

```scala mdoc:compile-only
class SimpleRingBuffer {
  // WARNING: Mutable state (vars) demonstrated here to show the problem;
  // this is NOT how ring buffers actually solve concurrency.
  private var head = 0
  private var tail = 0
  private val array = new Array[String](1024)

  def offer(x: String): Boolean = {
    if (tail - head >= array.length) false  // full
    else {
      array(tail % array.length) = x
      tail += 1
      true
    }
  }

  def take(): String = {
    if (head == tail) null  // empty
    else {
      val x = array(head % array.length)
      head += 1
      x
    }
  }
}
```

This works for single-threaded code, but introduces a critical problem under concurrency: both threads read and write `head` and `tail` without synchronization, leading to lost updates, stale reads, and silent data corruption. Adding `synchronized` blocks solves the data race but reintroduces contention and latency pauses.

Ring buffers solve both problems with **lock-free algorithms** and **cache-line padding**. A ring buffer provides:
- **No garbage collection** — reuses the same array forever
- **Lock-free access** — uses atomic compare-and-swap (CAS) for coordination, avoiding mutex contention
- **Predictable latency** — no surprise GC pauses or lock waits
- **Thread-specialized variants** — choose SPSC, MPSC, SPMC, or MPMC based on your thread pattern

This module provides four implementations tuned for maximum throughput and minimal latency across all producer/consumer combinations.

## Overview

Ring buffers are high-performance data structures for:

- **Event queues** in IO, networking, and game engines where throughput and latency matter
- **Thread-safe work queues** with bounded capacity to backpressure senders
- **Inter-thread communication** between producers and consumers with predictable latency
- **Concurrent batch processing** where producers fill elements and consumers drain them

## Why Ring Buffers

Ring buffers excel when you need:

- **Ultra-low latency** — lock-free algorithms and cache-line padding eliminate pauses from contention
- **Predictable throughput** — no GC overhead since the same memory array is reused forever
- **Bounded resources** — fixed capacity prevents runaway memory growth and enables backpressure
- **High concurrency** — multiple implementations optimized for different thread patterns (SPSC, MPMC, etc.)

How do ring buffers compare to other queue and collection types?

### Comparison with Java and Scala Alternatives

| Property                | RingBuffer (ZIO Blocks)                | `java.util.Queue` (ConcurrentLinkedQueue) | `scala.collection.concurrent.TrieMap` | Array + manual index     | Disruptor                |
|-------------------------|----------------------------------------|-------------------------------------------|---------------------------------------|--------------------------|--------------------------|
| **Allocation**          | Single upfront                         | Per-element nodes                         | Per-node allocations                  | Single upfront           | Single upfront           |
| **Lock-free**           | Yes (CAS-based)                        | Yes                                       | Yes (CASes on trie nodes)             | Yes (if single-threaded) | Yes (CAS)                |
| **GC pressure**         | Minimal (reuses slots)                 | High (node garbage)                       | High (trie node garbage)              | Minimal                  | Minimal                  |
| **Bounded**             | Fixed capacity                         | Unbounded                                 | Unbounded                             | Fixed                    | Fixed (bounded capacity) |
| **Thread patterns**     | Four variants (SPSC, MPSC, SPMC, MPMC) | Multi-producer/multi-consumer             | Multi-reader/multi-writer             | Limited (see impl)       | Multi-producer/consumer  |
| **Predictable latency** | High (no GC)                           | Medium (GC pauses)                        | Medium (GC pauses)                    | High (if no contention)  | High                     |

RingBuffer is ideal when you control both producer and consumer thread counts and want maximum performance. `java.util.Queue` is better if you need unbounded capacity; Disruptor is a comparable JVM alternative with similar guarantees.

## Installation

Add the ZIO Blocks Ring Buffer module to your `build.sbt`:

```scala
libraryDependencies += "dev.zio" %% "zio-blocks-ringbuffer" % "@VERSION@"
```

For Scala.js cross-platform support:

```scala
libraryDependencies += "dev.zio" %%% "zio-blocks-ringbuffer" % "@VERSION@"
```

## Implementations

ZIO Blocks provides four optimized ring buffer implementations. Choose the one matching your producer/consumer thread counts:

- **SPSC** — Single Producer, Single Consumer (⚡⚡⚡ **Fastest**)
- **SPMC** — Single Producer, Multiple Consumers
- **MPSC** — Multiple Producers, Single Consumer
- **MPMC** — Multiple Producers, Multiple Consumers

Use the sidebar for the per-implementation guides and the advanced topics page covering thread safety, cache-line padding, design patterns, and performance characteristics.
