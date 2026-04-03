# ZIO: Implement NIO scheduler for ZIO

Bounty: https://github.com/zio/zio/issues/9356
Reward: $2500

## Implementation Status: COMPLETE

### What was implemented

1. **NioScheduler** (`core/jvm-native/src/main/scala/zio/internal/NioScheduler.scala`)
   - Least-Loaded scheduling algorithm that assigns tasks to the worker with the least workload
   - Worker threads with local task queues (RingBufferPow2)
   - Global queue fallback for overflow and external submissions
   - Support for auto-blocking detection
   - Proper shutdown handling
   - Metrics reporting (concurrency, size, dequeued/enqueued counts)

2. **Blocking Integration** (`core/jvm-native/src/main/scala/zio/internal/Blocking.scala`)
   - Updated `signalBlocking()` to support both ZScheduler and NioScheduler workers

3. **DefaultExecutors** (`core/jvm-native/src/main/scala/zio/internal/DefaultExecutors.scala`)
   - Added `makeNio()` and `makeNio(autoBlocking: Boolean)` factory methods

4. **Runtime Configuration** (`core/jvm/src/main/scala/zio/RuntimePlatformSpecific.scala`)
   - Added `enableNioScheduler` layer for enabling the NIO scheduler
   - Added `enableNioSchedulerWithAutoBlocking` layer with auto-blocking support

5. **Tests** (`core-tests/jvm-native/src/test/scala/zio/internal/NioSchedulerSpec.scala`)
   - Basic functionality tests
   - Least-loaded scheduling tests
   - Integration tests with ZIO runtime
   - submitAndYield tests
   - Auto-blocking tests

### Usage

```scala
// Enable the NIO scheduler for your ZIO application
ZIO.succeed(42).provide(Runtime.enableNioScheduler(Trace.empty))

// Or with auto-blocking detection
ZIO.succeed(42).provide(Runtime.enableNioSchedulerWithAutoBlocking(Trace.empty))

// Or create the executor directly
val executor = Executor.makeNio()
val executorWithAutoBlocking = Executor.makeNio(autoBlocking = true)
```

### Algorithm

The Least-Loaded scheduler assigns new tasks to the worker with the smallest queue size.
This approach:
- Eliminates the complexity of work-stealing
- Reduces contention on shared queues
- Provides natural load balancing
- Is simpler to implement and maintain

Inspired by the Nio async runtime for Rust:
https://nurmohammed840.github.io/posts/announcing-nio/

### Files Changed

- `core/jvm-native/src/main/scala/zio/internal/NioScheduler.scala` (new)
- `core/jvm-native/src/main/scala/zio/internal/Blocking.scala` (modified)
- `core/jvm-native/src/main/scala/zio/internal/DefaultExecutors.scala` (modified)
- `core/jvm/src/main/scala/zio/RuntimePlatformSpecific.scala` (modified)
- `core-tests/jvm-native/src/test/scala/zio/internal/NioSchedulerSpec.scala` (new)
