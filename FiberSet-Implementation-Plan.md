# ZIO #8861 FiberSet Implementation Plan

## 📋 Task Overview
- **Issue**: https://github.com/zio/zio/issues/8861
- **Bounty**: $1,500
- **Timeline**: 2-3 days
- **Status**: ✅ CLAIMED (2026-04-22)

## 🎯 Requirements

### Core Features
1. **High-performance concurrent weak set** for Fiber references
2. **Lock-free add/remove/iterate** operations
3. **Optimized weak reference handling** (reduce WeakRef creation overhead)
4. **Comprehensive test suite** + benchmarks
5. **Integration** with Fiber children set and root fibers set

### Key Insights from Issue Description
- "Ensuring there are no duplicates is not really important" → Can skip deduplication for performance
- "Weak references are slow. It would be nice to avoid them entirely" → Use hybrid approach
- "Some fibers are suspended forever and do not shut down cleanly" → GC still necessary
- "Reduce the number of weak refs we create" → Reference queue optimization

## 🏗 Architecture Design

### Current State: `WeakConcurrentBag`
```
┌─────────────────────────────────────┐
│  Nursery (PartitionedRingBuffer)    │
│  - Stores initial refs strongly     │
│  - Zero allocation in happy path    │
│  - Capacity: 1000 per partition     │
└─────────────────────────────────────┘
              │
              ▼ (when full)
┌─────────────────────────────────────┐
│  Graduates (ConcurrentHashMap Set)  │
│  - WeakReference[A]                 │
│  - GC'd via removeIf(notAlive)      │
│  - Auto GC thread (5s interval)     │
└─────────────────────────────────────┘
```

### Proposed: `FiberSet` (Optimized)
```
┌──────────────────────────────────────────────────────┐
│  Tier 1: Hot Path (Lock-Free Ring Buffer)            │
│  - Direct Fiber references (no WeakRef overhead)     │
│  - CAS-based add/remove                              │
│  - Capacity: 256 (fits in L1 cache)                  │
│  - When full → spill to Tier 2                       │
└──────────────────────────────────────────────────────┘
                    │
                    ▼ (spill)
┌──────────────────────────────────────────────────────┐
│  Tier 2: Warm Storage (Reference Queue Optimized)    │
│  - WeakReference + ReferenceQueue polling            │
│  - Batch GC on queue poll (not full traversal)       │
│  - ConcurrentHashMap.KeySet for O(1) lookup          │
└──────────────────────────────────────────────────────┘
                    │
                    ▼ (on GC)
┌──────────────────────────────────────────────────────┐
│  Tier 3: Cold Storage (PhantomReference Queue)       │
│  - PhantomReference for precise GC notification      │
│  - No finalizer overhead                             │
│  - Direct removal from Tier 2                        │
└──────────────────────────────────────────────────────┘
```

## 📁 File Structure

```
core/shared/src/main/scala/zio/internal/
├── FiberSet.scala              # Main implementation
├── FiberSetBenchmark.scala     # JMH benchmarks
└── FiberSetSpec.scala          # Test suite

core/jvm/src/main/scala/zio/internal/
└── FiberSetPlatform.scala      # JVM-specific optimizations

core/js/src/main/scala/zio/internal/
└── FiberSetPlatform.scala      # JS stub implementation

core/shared/src/main/scala/zio/
└── Fiber.scala                 # Integration (replace WeakConcurrentBag)
```

## 🔧 Implementation Details

### 1. Core Data Structure

```scala
package zio.internal

import zio.{Chunk, Duration, Unsafe}
import java.lang.ref.{ReferenceQueue, WeakReference, PhantomReference}
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.{AtomicBoolean, AtomicInteger}
import scala.annotation.tailrec

/**
 * A high-performance concurrent set optimized for Fiber references.
 * 
 * Features:
 * - Lock-free add/remove for hot path (Tier 1 ring buffer)
 * - Reference queue-based GC (no full traversal)
 * - Weakly consistent iteration
 * - No duplicate enforcement (per issue requirements)
 * 
 * @param hotCapacity Size of tier-1 hot path (default: 256)
 * @param warmCapacity Initial capacity of tier-2 warm storage
 */
private[zio] final class FiberSet[A <: AnyRef] private (
  hotCapacity: Int,
  warmCapacity: Int,
  isAlive: FiberSet.IsAlive[A]
) { self =>
  
  // Tier 1: Hot path - lock-free ring buffer
  private[this] val hotBuffer = new FiberSet.HotRingBuffer[A](hotCapacity)
  
  // Tier 2: Warm storage - WeakReference + ReferenceQueue
  private[this] val warmStorage = ConcurrentHashMap.newKeySet[WeakReference[A]](warmCapacity)
  private[this] val refQueue = new ReferenceQueue[A]()
  
  // GC state
  private[this] val gcLock = new AtomicBoolean(false)
  private[this] val gcScheduled = new AtomicBoolean(false)
  
  /** Add a fiber to the set */
  def add(fiber: A): Unit = {
    // Try hot path first (lock-free)
    if (hotBuffer.offer(fiber)) {
      () // Success, no allocation
    } else {
      // Hot path full, spill to warm storage
      spillToWarm(fiber)
    }
  }
  
  /** Remove a fiber (best-effort, no guarantee) */
  def remove(fiber: A): Boolean = {
    // Try hot path
    if (hotBuffer.remove(fiber)) {
      true
    } else {
      // Search warm storage
      removeExpired() // Poll ref queue first
      warmStorage.removeIf(ref => ref.get() eq fiber)
    }
  }
  
  /** Weakly consistent iterator */
  def iterator: Iterator[A] = {
    removeExpired() // Clean up before iteration
    
    val hotIter = hotBuffer.iterator()
    val warmIter = warmStorage.iterator()
    
    new Iterator[A] {
      private var nextElem: A = null.asInstanceOf[A]
      private var hasNextElem: Boolean = true
      
      prefetch()
      
      @tailrec
      private def prefetch(): Unit = {
        if (hotIter.hasNext) {
          val fiber = hotIter.next()
          if (isAlive(fiber)) {
            nextElem = fiber
            return
          }
        }
        if (warmIter.hasNext) {
          val ref = warmIter.next()
          val fiber = ref.get()
          if ((fiber ne null) && isAlive(fiber)) {
            nextElem = fiber
            return
          } else {
            warmIter.remove() // Auto-remove dead refs
          }
        }
        hasNextElem = false
      }
      
      def hasNext: Boolean = hasNextElem
      
      def next(): A = {
        if (!hasNextElem) throw new NoSuchElementException("FiberSet iterator exhausted")
        val result = nextElem
        prefetch()
        result
      }
    }
  }
  
  /** Approximate size */
  def size: Int = hotBuffer.size() + warmStorage.size()
  
  /** Force garbage collection */
  def gc(): Unit = {
    removeExpired()
    warmStorage.removeIf(ref => (ref.get() eq null) || !isAlive(ref.get()))
  }
  
  /** Poll reference queue and remove expired entries */
  private def removeExpired(): Unit = {
    @tailrec
    def loop(): Unit = {
      val ref = refQueue.poll()
      if (ref ne null) {
        warmStorage.remove(ref)
        loop()
      }
    }
    loop()
  }
  
  /** Spill from hot path to warm storage */
  private def spillToWarm(fiber: A): Unit = {
    // Flush half of hot buffer to make room
    val flushed = hotBuffer.flushHalf()
    
    // Add flushed to warm storage
    val iter = flushed.iterator()
    while (iter.hasNext) {
      val f = iter.next()
      if (isAlive(f)) {
        warmStorage.add(new WeakReference[A](f, refQueue))
      }
    }
    
    // Add current fiber
    warmStorage.add(new WeakReference[A](fiber, refQueue))
  }
  
  /** Start auto-GC thread */
  def withAutoGc(every: Duration): FiberSet[A] = {
    if (gcScheduled.compareAndSet(false, true)) {
      FiberSetGcThread.start(this, every)
    }
    this
  }
}

private[zio] object FiberSet {
  
  def apply[A <: AnyRef](
    hotCapacity: Int = 256,
    warmCapacity: Int = 1024,
    isAlive: IsAlive[A] = IsAlive.always
  )(implicit unsafe: Unsafe): FiberSet[A] = 
    new FiberSet(hotCapacity, warmCapacity, isAlive)
  
  trait IsAlive[-A] {
    def apply(value: A): Boolean
  }
  
  object IsAlive {
    val always: IsAlive[Any] = _ => true
  }
  
  /** Lock-free ring buffer for hot path */
  private final class HotRingBuffer[A <: AnyRef](capacity: Int) {
    private[this] val buffer = new Array[AnyRef](capacity)
    private[this] val head = new AtomicInteger(0)
    private[this] val tail = new AtomicInteger(0)
    
    def offer(value: A): Boolean = {
      @tailrec
      def tryOffer(): Boolean = {
        val t = tail.get()
        val h = head.get()
        val size = t - h
        
        if (size >= capacity) {
          false // Full
        } else {
          val idx = t & (capacity - 1) // Power of 2 optimization
          if (buffer.compareAndSet(idx, null, value)) {
            tail.compareAndSet(t, t + 1)
          } else {
            tryOffer() // Retry
          }
        }
      }
      tryOffer()
    }
    
    def remove(value: A): Boolean = {
      var i = head.get()
      val end = tail.get()
      
      while (i < end) {
        val idx = i & (capacity - 1)
        val current = buffer(idx)
        
        if (current eq value) {
          if (buffer.compareAndSet(idx, value, null)) {
            return true
          }
        }
        i += 1
      }
      false
    }
    
    def flushHalf(): Chunk[A] = {
      val builder = Chunk.newBuilder[A]
      var i = head.get()
      val end = tail.get()
      val flushCount = (end - i) / 2
      
      var flushed = 0
      while (i < end && flushed < flushCount) {
        val idx = i & (capacity - 1)
        val current = buffer(idx)
        
        if (current ne null) {
          builder += current.asInstanceOf[A]
          buffer(idx) = null
          flushed += 1
        }
        i += 1
      }
      
      if (flushed > 0) {
        head.compareAndSet(head.get(), head.get() + flushed)
      }
      
      builder.result()
    }
    
    def size(): Int = math.max(0, tail.get() - head.get())
    
    def iterator(): Iterator[A] = {
      val currentHead = head.get()
      val currentTail = tail.get()
      
      new Iterator[A] {
        private var i = currentHead
        
        def hasNext: Boolean = i < currentTail
        
        def next(): A = {
          while (i < currentTail) {
            val idx = i & (capacity - 1)
            val value = buffer(idx)
            i += 1
            if (value ne null) {
              return value.asInstanceOf[A]
            }
          }
          throw new NoSuchElementException("Iterator exhausted")
        }
      }
    }
  }
}
```

### 2. Auto-GC Thread (JVM-specific)

```scala
// core/jvm/src/main/scala/zio/internal/FiberSetGcThread.scala
package zio.internal

import zio.Duration
import java.util.concurrent.atomic.AtomicInteger

private final class FiberSetGcThread[A <: AnyRef](
  set: FiberSet[A],
  interval: Duration
) extends Thread(s"zio.internal.FiberSet.GcThread-${FiberSetGcThread.idGen.incrementAndGet()}") {
  
  setDaemon(true)
  
  override def run(): Unit = {
    while (!isInterrupted) {
      Thread.sleep(interval.toMillis)
      set.gc()
    }
  }
}

private object FiberSetGcThread {
  val idGen = new AtomicInteger(0)
  
  def start[A <: AnyRef](set: FiberSet[A], every: Duration): Unit = {
    val thread = new FiberSetGcThread(set, every)
    thread.start()
  }
}
```

### 3. Integration with Fiber

```scala
// In Fiber.scala, replace:
// private[zio] val _roots: WeakConcurrentBag[Fiber.Runtime[_, _]] = ...

// With:
private[zio] val _roots: FiberSet[Fiber.Runtime[_, _]] = 
  FiberSet[Fiber.Runtime[_, _]](
    hotCapacity = 256,
    warmCapacity = 1024,
    isAlive = _.isAlive()
  )(Unsafe.unsafe).withAutoGc(5.seconds)
```

### 4. Children tracking in FiberRuntime

```scala
// In FiberRuntime.scala, add:
private[this] val children: FiberSet[FiberRuntime[_, _]] = 
  FiberSet(hotCapacity = 64, warmCapacity = 256)
```

## 📊 Benchmarks

### Benchmark Scenarios

1. **Add-only throughput** (16 threads, 1M additions)
2. **Add/remove mixed** (80% add, 20% remove)
3. **Iteration performance** (concurrent iteration during modification)
4. **GC overhead** (time spent in garbage collection)
5. **Memory footprint** (heap usage vs WeakConcurrentBag)

### Expected Improvements

| Metric | WeakConcurrentBag | FiberSet | Improvement |
|--------|-------------------|----------|-------------|
| Add latency (hot path) | ~50ns | ~15ns | 3.3x faster |
| Add latency (cold path) | ~200ns | ~100ns | 2x faster |
| GC pause time | ~5ms | ~0.5ms | 10x faster |
| Memory overhead | 32 bytes/ref | 24 bytes/ref | 25% reduction |

## ✅ Test Coverage

### Unit Tests
- [ ] Add/remove basic operations
- [ ] Concurrent add from multiple threads
- [ ] Iterator weak consistency
- [ ] GC removes dead fibers
- [ ] Reference queue polling
- [ ] Hot path overflow/spill
- [ ] Auto-GC thread lifecycle

### Stress Tests
- [ ] 100 threads adding simultaneously
- [ ] Rapid add/remove cycles
- [ ] Long-running fiber tracking
- [ ] Memory pressure scenarios

## 📅 Implementation Timeline

### Day 1: Core Implementation
- [ ] Create `FiberSet.scala` with basic structure
- [ ] Implement `HotRingBuffer`
- [ ] Implement warm storage with ReferenceQueue
- [ ] Basic iterator
- [ ] Unit tests for core functionality

### Day 2: Optimization + Integration
- [ ] Add auto-GC thread
- [ ] Platform-specific optimizations (JVM)
- [ ] Integrate with `Fiber._roots`
- [ ] Integrate with `FiberRuntime.children`
- [ ] Run existing ZIO test suite

### Day 3: Benchmarks + Polish
- [ ] Create JMH benchmarks
- [ ] Compare vs WeakConcurrentBag
- [ ] Documentation (Scaladoc)
- [ ] Final code review
- [ ] Submit PR

## 🎯 Success Criteria

1. ✅ All existing ZIO tests pass
2. ✅ Benchmarks show ≥2x improvement in hot path
3. ✅ No memory leaks in stress tests
4. ✅ GC pause times < 1ms
5. ✅ Code reviewed and approved by maintainer

## 📝 Notes

- **No duplicate enforcement** per issue description
- **Lock-free hot path** for maximum throughput
- **ReferenceQueue** eliminates need for full traversal GC
- **Tiered architecture** balances performance vs memory
