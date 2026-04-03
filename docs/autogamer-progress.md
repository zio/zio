# ZIO NioScheduler Implementation Progress

## Overview

Implementation of the NIO-based Scheduler for ZIO as described in [Issue #9356](https://github.com/zio/zio/issues/9356).

## What Was Implemented

### NioScheduler

A new `NioScheduler` executor that uses a **Least-Loaded scheduling algorithm** instead of work-stealing.

**Location:** `core/jvm-native/src/main/scala/zio/internal/NioScheduler.scala`

#### Key Features

1. **Least-Loaded Scheduling**: New tasks are assigned to the worker with the smallest queue size, providing natural load balancing without the complexity of work-stealing.

2. **Work Stealing Fallback**: When a worker is idle and searching for work, it can steal tasks from other workers' queues as a fallback mechanism.

3. **Global + Local Queues**: 
   - Global queue for external submissions
   - Local queues (RingBufferPow2) for each worker

4. **Blocking Detection**: Workers can be marked as blocking, triggering:
   - Migration of pending tasks to global queue
   - Spawning of a replacement worker

5. **Proper Shutdown**: Clean shutdown with worker interruption

### DefaultExecutors Integration

Added factory methods to create NioScheduler instances:

```scala
def makeNio(): zio.Executor
def makeNio(autoBlocking: Boolean): zio.Executor
```

**Location:** `core/jvm-native/src/main/scala/zio/internal/DefaultExecutors.scala`

## Implementation Details

### Algorithm

1. **Task Submission**:
   - If called from a worker thread: submit to local queue
   - If called externally: find the worker with the least load and submit there
   - Fall back to global queue if all workers are busy

2. **Task Execution**:
   - Workers first check their local queue
   - Then check the global queue
   - If idle, enter "searching" mode and try to steal from other workers

3. **Load Balancing**:
   - The `submitToLeastLoaded` method finds the worker with the smallest queue
   - Early exit optimization when an empty worker is found

### Design Decisions

1. **autoBlocking Parameter**: Currently accepted but not fully implemented. Future work could add a supervisor thread similar to ZScheduler.

2. **Queue Size**: Local queues are 256 elements (RingBufferPow2), matching ZScheduler.

3. **Thread Count**: Uses `Runtime.getRuntime.availableProcessors` workers.

## Files Modified/Created

| File | Action | Description |
|------|--------|-------------|
| `core/jvm-native/src/main/scala/zio/internal/NioScheduler.scala` | Created | Main scheduler implementation |
| `core/jvm-native/src/main/scala/zio/internal/DefaultExecutors.scala` | Modified | Added `makeNio()` factory methods |

## Compilation Status

✅ Successfully compiles with `coreJVM/compile`

## Related Resources

- [Issue #9356](https://github.com/zio/zio/issues/9356)
- [Nio Rust Runtime Announcement](https://nurmohammed840.github.io/posts/announcing-nio/)
- [Making the Tokio Scheduler 10X Faster](https://tokio.rs/blog/2019-10-scheduler)

## Future Work

1. **Auto-blocking Supervisor**: Implement automatic detection of blocking operations
2. **Benchmarks**: Add performance benchmarks comparing with ZScheduler
3. **Configuration**: Allow customization of pool size, queue sizes, etc.
4. **Metrics**: Add more detailed metrics for monitoring
