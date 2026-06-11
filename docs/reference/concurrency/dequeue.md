---
id: dequeue
title: "Dequeue"
---

`Dequeue[+A]` is a **read-only consumer interface for asynchronous producer-consumer queues**. It provides safe, fiber-friendly access to dequeue items while managing backpressure and suspension semantics. Unlike traditional thread-blocking queues, `Dequeue` uses ZIO's fiber suspension mechanism to enable thousands of concurrent fibers to wait for items without blocking OS threads.

`Dequeue`:
- **Covariant type parameter** — enables safe substitution with supertypes
- **Sealed trait** — only internal implementations available, ensuring consistency  
- **Non-blocking fibers** — operations suspend fibers without blocking threads
- **Minimal API surface** — focused on extraction and lifecycle management
- **Infallible operations** — no error types, only interruption via shutdown
- **Batch operations** — flexible methods for single items or multiple items

```scala
sealed trait Dequeue[+A] extends Serializable {
  // Element extraction
  def take(implicit trace: Trace): UIO[A]
  def takeAll(implicit trace: Trace): UIO[Chunk[A]]
  def takeUpTo(max: Int)(implicit trace: Trace): UIO[Chunk[A]]
  def takeBetween(min: Int, max: Int)(implicit trace: Trace): UIO[Chunk[A]]
  def takeN(n: Int)(implicit trace: Trace): UIO[Chunk[A]]
  def poll(implicit trace: Trace): UIO[Option[A]]
  
  // State inspection
  def size(implicit trace: Trace): UIO[Int]
  def isEmpty(implicit trace: Trace): UIO[Boolean]
  def isFull(implicit trace: Trace): UIO[Boolean]
  def capacity: Int
  
  // Lifecycle
  def shutdown(implicit trace: Trace): UIO[Unit]
  def isShutdown(implicit trace: Trace): UIO[Boolean]
  def awaitShutdown(implicit trace: Trace): UIO[Unit]
}
```

## Quick Showcase

Here's a typical use of `Dequeue` for coordinating work between fibers:

```scala mdoc:silent
import zio._

val example = for {
  queue <- Queue.bounded[Int](5)
  
  // Offer some items
  _ <- queue.offer(1)
  _ <- queue.offer(2)
  _ <- queue.offer(3)
  
  // Consumer: take items
  item1 <- queue.take
  item2 <- queue.take
  item3 <- queue.take
  
  _ <- ZIO.debug(s"Consumed: $item1, $item2, $item3")
  _ <- queue.shutdown
} yield ()
```

## Motivation

Coordination between concurrent fibers often requires producer-consumer patterns: one fiber produces items while another consumes them. A queue abstracts this pattern with:

- **Back-pressure**: consumers suspend (fiber-suspend) when the queue is empty, waiting for items
- **Fair ordering**: items are consumed in FIFO order, with suspended consumers waking fairly
- **Non-blocking**: uses ZIO fiber suspension, not OS thread blocking
- **Type safety**: `Dequeue` enforces read-only access, preventing accidental writes

The `Dequeue` trait provides the consumer-only view, enabling safe sharing of queues where certain fibers should only read.

## Construction

`Dequeue` instances are created through `Queue` factory methods or by subscribing to a `Hub`. The `Dequeue` trait itself has no public constructors; all instances come from backing implementations.

### Via Queue Factories

The primary way to create a `Dequeue` is through `Queue` companion object methods. While `Queue[A]` extends both `Dequeue[A]` and `Enqueue[A]`, you can treat it as a `Dequeue` for read-only operations:

```scala mdoc:reset
import zio._

val bounded: UIO[Queue[String]] = 
  Queue.bounded[String](10)

val unbounded: UIO[Queue[String]] = 
  Queue.unbounded[String]

val dropping: UIO[Queue[String]] = 
  Queue.dropping[String](10)

val sliding: UIO[Queue[String]] = 
  Queue.sliding[String](10)
```

Each factory returns a `Queue[A]` which is a subtype of `Dequeue[A]`:
- **`bounded(capacity)`** — suspends producers when full; must be closed
- **`unbounded`** — never full; grows unbounded; must be closed
- **`dropping(capacity)`** — silently drops new items when full
- **`sliding(capacity)`** — removes oldest item to make room for new ones

### Via Hub Subscription

Hubs broadcast items to multiple subscribers. Each subscriber receives a `Dequeue`:

```scala mdoc:reset
import zio._

val hubExample = ZIO.scoped {
  for {
    hub <- Hub.bounded[Int](100)
    
    // Publish items
    _ <- hub.publish(42)
    
    // Subscribe creates a Dequeue for this consumer
    dequeue <- hub.subscribe
    
    // Use the Dequeue to consume items
    item <- dequeue.take
    _ <- ZIO.debug(s"Got: $item")
  } yield ()
}


```

### Via Type Widening

A `Queue[A]` can be used anywhere a `Dequeue[A]` is expected:

```scala mdoc:reset
import zio._

val queueAsDequeue: UIO[Dequeue[Int]] = 
  Queue.bounded[Int](5)
```

## Core Operations

### Single Item Extraction

#### `take` — Extract Next Item (Blocking)

Removes and returns the oldest item in the queue. If the queue is empty, the fiber suspends until an item is available or the queue is shut down.

```scala
def take(implicit trace: Trace): UIO[A]
```

When `take` is called on an empty queue, the calling fiber suspends without blocking any OS threads. Internally, ZIO creates a `Promise` for the waiting fiber, adds it to a queue of suspended consumers, and frees the thread to run other work. When an item arrives, the first waiting `Promise` is completed with that item, resuming the fiber.

```scala mdoc:reset
import zio._

val takeExample = for {
  queue <- Queue.bounded[String](3)
  _ <- queue.offer("hello")
  item <- queue.take
  _ <- ZIO.debug(s"Took: $item")
} yield ()


```

**Performance:** O(1) per item extracted.

#### `poll` — Try to Extract Without Suspending (Non-Blocking)

Attempts to get the next item without suspending the fiber. Returns `Option[A]`: `Some(item)` if available, `None` if empty.

```scala
def poll(implicit trace: Trace): UIO[Option[A]]
```

`poll` is useful for opportunistic consumption: take what's available now without waiting.

```scala mdoc:reset
import zio._

val pollExample = for {
  queue <- Queue.bounded[String](3)
  _ <- queue.offer("hello")
  _ <- queue.offer("world")
  
  first <- queue.poll
  _ <- ZIO.debug(s"First: $first")  // Some(hello)
  
  second <- queue.poll
  _ <- ZIO.debug(s"Second: $second") // Some(world)
  
  third <- queue.poll
  _ <- ZIO.debug(s"Third: $third")   // None
} yield ()


```

**Performance:** O(1) per item.

### Batch Extraction — Non-blocking Methods

These methods never suspend; they return immediately with 0 to N items.

#### `takeUpTo` — Extract Up to N Items (Non-Blocking)

Removes up to `max` items without suspending. Returns immediately with available items (possibly fewer than `max`).

```scala
def takeUpTo(max: Int)(implicit trace: Trace): UIO[Chunk[A]]
```

This is useful for batching: get what's available now and process all at once.

```scala mdoc:reset
import zio._

val takeUpToExample = for {
  queue <- Queue.bounded[Int](10)
  _ <- ZIO.foreach(1 to 5)(queue.offer(_))
  
  items <- queue.takeUpTo(3)
  _ <- ZIO.debug(s"Took: $items")  // Chunk(1, 2, 3)
} yield ()


```

**Performance:** O(min(n, queue_size)) where n is the requested max.

#### `takeAll` — Extract All Available Items (Non-Blocking)

Removes all currently available items without suspending. Equivalent to `takeUpTo(Int.MaxValue)`.

```scala
def takeAll(implicit trace: Trace): UIO[Chunk[A]]
```

Drains the queue completely in a single operation, useful for cleanup or batch processing.

```scala mdoc:reset
import zio._

val takeAllExample = for {
  queue <- Queue.bounded[Int](10)
  _ <- ZIO.foreach(1 to 5)(queue.offer(_))
  
  all <- queue.takeAll
  _ <- ZIO.debug(s"All: $all")  // Chunk(1, 2, 3, 4, 5)
} yield ()


```

**Performance:** O(queue_size) — copies all available items.

### Batch Extraction — Flexible Methods

#### `takeBetween` — Extract Between Min and Max Items (Conditional Blocking)

Waits until at least `min` items are available, then returns up to `max` items. A powerful method that combines suspending (until `min` is ready) with opportunistic collection (up to `max`).

```scala
final def takeBetween(min: Int, max: Int)(implicit trace: Trace): UIO[Chunk[A]]
```

When `min > max`, returns empty immediately. Otherwise, suspends until `min` items available, then grabs up to `max`. This is used internally by `ZStream.fromQueue` to balance throughput with batching.

```scala mdoc:reset
import zio._

val takeBetweenExample = for {
  queue <- Queue.bounded[Int](10)
  
  // Add items
  _ <- ZIO.foreach(1 to 5)(queue.offer(_))
  
  // Wait for at least 2, collect up to 5
  items <- queue.takeBetween(2, 5)
  _ <- ZIO.debug(s"Got: $items")  // At least Chunk(1, 2), up to 5 items
} yield ()


```

**Performance:** O(n) where n is items extracted.

#### `takeN` — Extract Exactly N Items (Blocking)

Waits until exactly `n` items are available, then returns them as a `Chunk`. Equivalent to `takeBetween(n, n)`.

```scala
final def takeN(n: Int)(implicit trace: Trace): UIO[Chunk[A]]
```

Useful when you need a fixed number of items to proceed.

```scala mdoc:reset
import zio._

val takeNExample = for {
  queue <- Queue.bounded[Int](10)
  
  // Add items
  _ <- ZIO.foreach(1 to 5)(queue.offer(_))
  
  // Wait for exactly 3 items
  items <- queue.takeN(3)
  _ <- ZIO.debug(s"Got: $items")  // Chunk(1, 2, 3)
} yield ()


```

**Performance:** O(n) where n is the number of items extracted.

### State Inspection

#### `size` — Current Queue Depth

Returns the current number of items minus suspended consumers. Can be negative if consumers outnumber items.

```scala
def size(implicit trace: Trace): UIO[Int]
```

The `size` semantic is: `items_in_queue - suspended_consumers + suspended_producers`. This formula explains all three state categories: when consumers call `take` on an empty queue, they suspend (reducing size below zero); when producers call `offer` on a full queue, they suspend (increasing size above capacity). This single metric reflects the queue's health:

- **size > capacity** — producers waiting (back-pressure, oversaturated)
- **size = 0** — balanced state (items equal suspended consumers)
- **size < 0** — consumers waiting (starved, |size| consumers suspended)

```scala mdoc:reset
import zio._

val sizeExample = for {
  queue <- Queue.bounded[String](5)
  
  size1 <- queue.size
  _ <- ZIO.debug(s"Empty queue size: $size1")  // 0
  
  _ <- queue.offer("a")
  _ <- queue.offer("b")
  size2 <- queue.size
  _ <- ZIO.debug(s"After two offers: $size2")  // 2
  
  _ <- queue.take
  size3 <- queue.size
  _ <- ZIO.debug(s"After one take: $size3")  // 1
} yield ()


```

**Performance:** O(1) — atomic read.

#### Negative Size Semantics

Negative size indicates consumer starvation and is a powerful tool for detecting back-pressure. Here's a detailed example:

```scala mdoc:reset
import zio._

val negativeSizeExample = for {
  queue <- Queue.bounded[Int](5)
  
  // Add items and then consume them to reach empty state
  _ <- queue.offer(1)
  _ <- queue.offer(2)
  size1 <- queue.size
  _ <- ZIO.debug(s"After 2 offers: size = $size1")  // 2
  
  // Consume items to reach empty state
  _ <- queue.take
  _ <- queue.take
  size1b <- queue.size
  _ <- ZIO.debug(s"After consuming both: size = $size1b")  // 0
  
  // Now start a fiber that will call take on an empty queue
  takerFiber <- queue.take.fork
  
  // Give the fiber time to suspend
  _ <- ZIO.sleep(100.millis)
  
  // Now size should reflect the waiting taker (negative value)
  size2 <- queue.size
  _ <- ZIO.debug(s"With 1 suspended taker: size = $size2")  // -1
  
  // Cancel the taker (it was just to demonstrate)
  _ <- takerFiber.interrupt
} yield ()


```

The negative size metric enables dynamic monitoring: if `size < 0`, your consumers are waiting, indicating producers aren't keeping up.

#### `isEmpty` and `isFull` — Key Semantic Differences

⚠️ **Important:** The `isEmpty` and `isFull` semantics differ between `Dequeue` and `Queue`:

| Type | isEmpty | isFull |
|------|---------|--------|
| **Dequeue** (trait) | `size == 0` | `size == capacity` |
| **Queue** (implementation) | `size <= 0` | `size >= capacity` |

The Queue versions use broader definitions to detect suspended fibers: `isEmpty` returns true when size ≤ 0 (detecting suspended **consumers**), and `isFull` returns true when size ≥ capacity (detecting suspended **producers**). Since most examples use `Queue`, this distinction is critical to understand.

#### `isEmpty` — Check if Queue is Empty

Returns `true` based on the queue's size. For `Dequeue` (trait), checks if `size == 0` (balanced state). For `Queue` (implementation), checks if `size <= 0` (also true when consumers are suspended).

```scala
def isEmpty(implicit trace: Trace): UIO[Boolean]
```

```scala mdoc:reset
import zio._

val isEmptyExample = for {
  queue <- Queue.bounded[Int](5)
  empty1 <- queue.isEmpty
  _ <- ZIO.debug(s"New queue empty? $empty1")  // true (size == 0)
  
  _ <- queue.offer(42)
  empty2 <- queue.isEmpty
  _ <- ZIO.debug(s"After offer? $empty2")  // false (size > 0)
} yield ()


```

**Performance:** O(1).

#### `isFull` — Check if Queue is at Capacity

Returns `true` based on the queue's size. For `Dequeue` (trait), checks if `size == capacity`. For `Queue` (implementation), checks if `size >= capacity` (also true when producers are suspended and over-full).

```scala
def isFull(implicit trace: Trace): UIO[Boolean]
```

Useful with bounded queues to detect back-pressure and suspended producers.

```scala mdoc:reset
import zio._

val isFullExample = for {
  queue <- Queue.bounded[String](2)
  full1 <- queue.isFull
  _ <- ZIO.debug(s"New queue full? $full1")  // false
  
  _ <- queue.offer("first")
  _ <- queue.offer("second")
  full2 <- queue.isFull
  _ <- ZIO.debug(s"After 2 offers to capacity 2? $full2")  // true
} yield ()


```

**Performance:** O(1).

#### `capacity` — Get Fixed Capacity

Returns the maximum number of items the queue can hold. For unbounded queues, returns `Int.MaxValue`. This is the only non-IO property.

```scala
def capacity: Int
```
```scala mdoc:reset
import zio._

val capacityExample = for {
  bounded <- Queue.bounded[Int](10)
  _ <- ZIO.debug(s"Bounded queue capacity: ${bounded.capacity}")
  
  unbounded <- Queue.unbounded[Int]
  _ <- ZIO.debug(s"Unbounded queue capacity: ${unbounded.capacity}")
} yield ()


```

**Performance:** O(1) — not an effect, direct property access.

### Lifecycle Management

#### `shutdown` — Close the Queue

Signals the queue to shut down. All items remaining in the queue are discarded. Any fibers suspended on `take`, `takeN`, or `takeBetween` are interrupted.

```scala
def shutdown(implicit trace: Trace): UIO[Unit]
```

Shutdown is idempotent: calling it multiple times is safe.

```scala mdoc:reset
import zio._

val shutdownExample = for {
  queue <- Queue.bounded[Int](5)
  _ <- queue.offer(1)
  _ <- queue.offer(2)
  
  _ <- queue.shutdown
  _ <- ZIO.debug("Queue was shut down")
} yield ()


```

**Performance:** O(1) — marks queue closed and wakes suspended consumers.

#### `isShutdown` — Check Shutdown Status

Returns `true` if the queue has been shut down.

```scala
def isShutdown(implicit trace: Trace): UIO[Boolean]
```
```scala mdoc:reset
import zio._

val isShutdownExample = for {
  queue <- Queue.bounded[Int](5)
  
  open <- queue.isShutdown
  _ <- ZIO.debug(s"Queue open? ${!open}")  // true
  
  _ <- queue.shutdown
  closed <- queue.isShutdown
  _ <- ZIO.debug(s"Queue closed? $closed")  // true
} yield ()


```

**Performance:** O(1) — atomic read.

#### `awaitShutdown` — Wait for Shutdown Signal

Suspends the fiber until the queue is shut down by another fiber (or if already shutdown, returns immediately).

```scala
def awaitShutdown(implicit trace: Trace): UIO[Unit]
```

Useful for coordinating cleanup: one fiber waits for shutdown signal from elsewhere.

```scala mdoc:reset
import zio._

val awaitShutdownExample = for {
  queue <- Queue.bounded[Int](5)
  _ <- ZIO.debug("Queue created")
  
  // Trigger shutdown
  _ <- queue.shutdown
  
  // Now awaitShutdown returns immediately since shutdown was already called
  _ <- queue.awaitShutdown
  _ <- ZIO.debug("Queue shut down!")
} yield ()


```

**Performance:** O(1) — fiber suspends; resumes when shutdown or if already shutdown.

## Fiber Suspension Architecture

`Dequeue` uses a sophisticated fiber coordination mechanism that enables thousands of concurrent consumers without blocking OS threads. Here's how it works:

### When Empty Queue Meets Waiting Consumer

When a fiber calls `take` on an empty queue:

1. **Create Promise**: ZIO creates a `Promise[Nothing, A]` representing this fiber's wait
2. **Enqueue Waiter**: The Promise is added to the queue's internal `takers` deque
3. **Suspend Fiber**: The fiber suspends (pauses) without blocking the OS thread
4. **Thread Freed**: The OS thread can immediately run other work

### When Item Arrives

When another fiber calls `offer(item)`:

1. **Check Waiters**: If there are suspended consumers, grab the first Promise
2. **Complete Promise**: Fulfill that Promise with the item value
3. **Resume Fiber**: The waiting fiber automatically resumes with the item

### FIFO Fairness

Suspended fibers wake in FIFO order: the fiber that called `take` first gets the item first. This prevents starvation in high-contention scenarios.

This architecture is fundamentally different from thread-blocking queues (like `java.util.concurrent.LinkedBlockingQueue`), which would block the OS thread and be unable to use that thread for other work.

## Subtypes and Variants

### Queue[A] — Read-Write Interface

`Queue[A]` extends both `Dequeue[A]` and `Enqueue[A]`, providing full read-write access. Use `Queue` when a single fiber manages both production and consumption, or when you don't need role-based restrictions.

```scala mdoc:reset
import zio._

val queueExample = for {
  // Queue supports both take and offer
  queue <- Queue.bounded[String](5)
  
  _ <- queue.offer("hello")
  item <- queue.take
  _ <- ZIO.debug(s"From queue: $item")
} yield ()


```

### Enqueue[-A] — Write-Only Interface

`Enqueue[-A]` (contravariant) provides only write operations. Use it when you want to share write-only access with other producers or external code that should only enqueue items.

```scala mdoc:reset
import zio._

val enqueueExample = for {
  queue <- Queue.bounded[Int](5)
  
  // Treat as write-only
  enqueue: Enqueue[Int] = queue
  _ <- enqueue.offer(42)
} yield ()


```

### Hub[A] — Broadcast Publisher

A `Hub` is a broadcast queue: multiple `Dequeue` subscribers each receive copies of published items. Calling `hub.subscribe` returns a `Dequeue`.

```scala mdoc:reset
import zio._

val hubBroadcastExample = ZIO.scoped {
  for {
    hub <- Hub.bounded[String](10)
    
    // Publish items
    _ <- ZIO.foreach(List("a", "b", "c"))(hub.publish(_))
    
    // Two independent consumers
    sub1 <- hub.subscribe
    sub2 <- hub.subscribe
    
    a1 <- sub1.take
    a2 <- sub2.take
    
    _ <- ZIO.debug(s"Sub1: $a1, Sub2: $a2")
  } yield ()
}


```

### TDequeue[A] — Transactional Variant

`TDequeue` provides STM (Software Transactional Memory) operations. All methods return `ZSTM` effects that compose transactionally with other STM operations, enabling atomic multi-step operations on queues.

```scala mdoc:reset
import zio._

val tdequeueExample = for {
  // TDequeue is the STM variant of Dequeue
  // Created via TQueue.bounded[A] instead of Queue.bounded[A]
  // All operations return ZSTM effects instead of UIO effects
  
  // Example transactional operation pattern:
  // val result <- ZIO.atomically {
  //   for {
  //     _ <- tqueue.offer(42)      // Returns ZSTM[Any, Nothing, Boolean]
  //     value <- tqueue.take        // Returns ZSTM[Any, Nothing, A]
  //   } yield value
  // }
  
  _ <- ZIO.debug("TDequeue enables atomic multi-step queue operations via STM.atomically")
} yield ()

```

For comprehensive transactional queue documentation, see the [TQueue](../stm/tqueue.md) reference in the STM section for more examples and advanced patterns.

## Advanced Usage

### Concurrent Consumers with Fiber Suspension

Multiple fibers can call `take` on the same queue. They suspend fairly in FIFO order:

```scala mdoc:reset
import zio._

val multiConsumerExample = for {
  queue <- Queue.bounded[Int](100)
  
  // Fill queue
  _ <- ZIO.foreach(1 to 3)(queue.offer(_))
  
  // Multiple consumers
  item1 <- queue.take
  item2 <- queue.take
  item3 <- queue.take
  
  _ <- ZIO.debug(s"Got items: $item1, $item2, $item3")
} yield ()


```

When multiple fibers call `take` on an empty queue, they all suspend. As items arrive via `offer`, they wake in order.

### Batch Processing with Fair Balancing

Combine batch extraction with fair consumption using `takeBetween`:

```scala mdoc:reset
import zio._

val batchProcessingExample = for {
  queue <- Queue.bounded[Int](100)
  
  // Add items
  _ <- ZIO.foreach(1 to 10) { i =>
    queue.offer(i)
  }
  
  // Consumer takes a batch at once (wait for at least 3, up to 5)
  batch <- queue.takeBetween(3, 5)
  _ <- ZIO.debug(s"Processing batch of ${batch.length}: $batch")
} yield ()


```

### Graceful Shutdown Coordination

Coordinate shutdown across multiple producers and consumers:

```scala mdoc:reset
import zio._

val gracefulShutdownExample = for {
  queue <- Queue.bounded[String](5)
  
  // Produce and consume items
  _ <- ZIO.foreach(1 to 5) { i =>
    queue.offer(s"item-$i")
  }
  
  // Take a few items
  item1 <- queue.take
  item2 <- queue.take
  _ <- ZIO.debug(s"Consumed: $item1, $item2")
  
  // Signal shutdown
  _ <- queue.shutdown
} yield ()


```

### Back-Pressure Response

Use `size` to detect back-pressure and respond appropriately:

```scala mdoc:reset
import zio._

val backPressureExample = for {
  queue <- Queue.bounded[Int](10)
  
  // Produce items
  _ <- ZIO.foreach(1 to 10)(queue.offer(_))
  size <- queue.size
  
  // If queue is full, take immediate action
  _ <- if (size >= queue.capacity) {
    ZIO.debug("Back-pressure: queue is full, throttling producer")
  } else {
    ZIO.debug(s"Queue has room: size = $size / capacity = ${queue.capacity}")
  }
} yield ()


```

## Integration with ZIO Ecosystem

### With ZStream

Convert a `Dequeue` to a `ZStream` for streaming operations:

```scala mdoc:reset
import zio._
import zio.stream._

val streamExample = for {
  queue <- Queue.bounded[Int](5)
  
  _ <- ZIO.foreach(1 to 5)(queue.offer(_))
  
  result <- ZStream.fromQueue(queue)
    .take(3)
    .runCollect
    
  _ <- ZIO.debug(s"Stream result: $result")
} yield ()


```

### With Fiber Coordination

Queues are fundamental for fiber-to-fiber communication:

```scala mdoc:reset
import zio._

val fiberCoordinationExample = for {
  queue <- Queue.bounded[String](10)
  
  // Send requests
  _ <- queue.offer("request-1")
  _ <- queue.offer("request-2")
  _ <- queue.offer("request-3")
  
  // Process requests
  req1 <- queue.take
  req2 <- queue.take
  req3 <- queue.take
  
  _ <- ZIO.debug(s"Processed: $req1, $req2, $req3")
  _ <- queue.shutdown
} yield ()


```

## Complete Method Reference

| Method | Type | Blocking? | Purpose |
|--------|------|-----------|---------|
| `take` | `UIO[A]` | Yes | Extract one item, suspend if empty |
| `takeN(n)` | `UIO[Chunk[A]]` | Yes | Extract exactly n items |
| `takeUpTo(max)` | `UIO[Chunk[A]]` | No | Extract up to max items |
| `takeBetween(min, max)` | `UIO[Chunk[A]]` | Yes* | Extract min–max items |
| `takeAll` | `UIO[Chunk[A]]` | No | Drain all available items |
| `poll` | `UIO[Option[A]]` | No | Try one item, no suspend |
| `size` | `UIO[Int]` | — | Current queue depth (can be negative) |
| `isEmpty` | `UIO[Boolean]` | — | Check if empty |
| `isFull` | `UIO[Boolean]` | — | Check if at capacity |
| `capacity` | `Int` | — | Get fixed capacity (property) |
| `shutdown` | `UIO[Unit]` | — | Close queue |
| `isShutdown` | `UIO[Boolean]` | — | Check if closed |
| `awaitShutdown` | `UIO[Unit]` | Yes | Wait for shutdown signal |

*`takeBetween` suspends until `min` items available; always returns immediately if `min > max`.

## Design Notes

### Covariance for Type Safety

`Dequeue[+A]` uses covariance to enable safe sharing with supertypes:

```scala mdoc:reset
import zio._

class Animal
class Dog extends Animal

def consumeAnimals(q: Dequeue[Animal]): UIO[Animal] = 
  q.take

val dogQueueExample = for {
  dogQueue <- Queue.bounded[Dog](5)
  // Type-safe: Queue[Dog] is Dequeue[Dog] <: Dequeue[Animal]
  _ <- consumeAnimals(dogQueue)
} yield ()


```

The `Enqueue[-A]` counterpart is contravariant for the same reason: you can assign `Enqueue[Dog]` to a variable of type `Enqueue[Animal]` and then safely offer `Animal` to it.

### Why No Error Types?

All `Dequeue` operations return `UIO` (no errors). Shutdown doesn't return an error; it interrupts suspended fibers, which manifests as `Fiber.Interrupted` in error channels of composed effects. This keeps the `Dequeue` API simple and composable.

### Why Fiber Suspension Over Thread Blocking?

When a fiber calls `take` on an empty queue, it **suspends**—the fiber pauses but the underlying OS thread is freed to run other fibers. This is fundamentally different from thread blocking and allows thousands of concurrent fibers on a single thread pool. With traditional thread-blocking queues, you'd quickly exhaust your thread pool and hit catastrophic performance degradation.

### Size Semantics Recap

The `size` method returns `items - suspended_consumers`. This is why `size` can be negative: if 5 fibers are suspended on `take` and the queue is empty, `size = 0 - 5 = -5`. This semantic ensures that a balanced queue has `size ≈ 0`:

- **size > 0** — items waiting, producers ahead
- **size = 0** — balanced
- **size < 0** — consumers starved, |size| fibers waiting

This single metric reflects queue equilibrium without needing separate state tracking.

## See Also

- [Queue](./queue.md) — The read-write queue interface that extends both Dequeue and Enqueue
- [Hub](./hub.md) — For broadcast pub-sub messaging where subscribers receive Dequeue instances
- [Promise](./promise.md) — For single-value synchronization between fibers
- [Fiber](../fiber/index.md) — For creating and managing concurrent work
