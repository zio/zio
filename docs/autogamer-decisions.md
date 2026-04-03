# Autogamer Decisions: NIO Scheduler for ZIO

## Decision 1: Supervisor Thread for Auto-Blocking
- **Date**: 2026-04-03
- **Decision**: Add a `Supervisor` thread when `autoBlocking = true`, matching ZScheduler pattern
- **Rationale**: The original implementation accepted `autoBlocking` but never used it. The ZScheduler has a supervisor that monitors worker opCounts and marks stuck workers as blocking. NioScheduler now has the same capability.

## Decision 2: State Increment for Replacement Workers
- **Date**: 2026-04-03
- **Decision**: Added `state.getAndAdd(0x10001)` in `markAsBlocking()` before spawning replacement worker
- **Rationale**: The state AtomicInteger tracks active workers (upper 16 bits) and searching workers (lower 16 bits). When a worker is marked blocking and a replacement is spawned, the replacement needs to be counted. Without this fix, the state counter would drift downward over time, eventually preventing worker wake-ups.

## Decision 3: Clean Loop Break Pattern
- **Date**: 2026-04-03
- **Decision**: Replaced `i = poolSize` break with `found` boolean flag
- **Rationale**: The previous pattern set `i = poolSize` then `i += 1` made it `poolSize + 1`. While the loop condition still worked, it was confusing and error-prone.

## Decision 4: Targeted Worker Unpark on Submit
- **Date**: 2026-04-03
- **Decision**: When submitting to a specific worker via least-loaded algorithm, wake that specific worker (not just any idle worker)
- **Rationale**: The original code only called `maybeUnparkWorker()` which wakes any idle worker. If tasks were directed to worker[i]'s local queue but a different worker[j] was woken, worker[i]'s tasks could be stranded. The `unparkWorker()` method now targets the specific worker that received the task.

## Decision 5: Timed Park Safety Net
- **Date**: 2026-04-03
- **Decision**: Workers park with `parkNanos(10ms)` after the initial `park()` as a safety net
- **Rationale**: In rare race conditions (e.g., task added between the work-check and the park call), a worker could miss an unpark signal. The 10ms timed park ensures workers periodically re-check for work even without an explicit unpark.

## Decision 6: CountDownLatch-Based Tests
- **Date**: 2026-04-03
- **Decision**: All tests use CountDownLatch instead of ZIO.sleep for synchronization
- **Rationale**: ZIO's test framework uses TestClock which intercepts ZIO.sleep calls. Since the test clock is never advanced, ZIO.sleep-based tests would hang forever. CountDownLatch provides direct synchronization without TestClock interference.

## Decision 7: Accepted Trade-offs
- **Date**: 2026-04-03
- **Decision**: Accept O(n) worker scanning, no shutdown join, no worker exception handling
- **Rationale**: These match the ZScheduler patterns and are acceptable for a first implementation:
  - O(n) worker scan in maybeUnparkWorker: ZScheduler uses an idle queue for O(1), but this optimization can be added later
  - No shutdown() thread join: ZScheduler has the same pattern
  - No exception handling in worker run(): ZIO catches at fiber level

## Decision 8: Worker Stealing as Secondary Strategy
- **Date**: 2026-04-03
- **Decision**: Workers in "searching" mode steal half of another worker's tasks (not just rely on least-loaded submission)
- **Rationale**: Pure least-loaded submission handles the common case well, but when bursts arrive faster than workers can dequeue, or when tasks have variable durations, some workers may accumulate work while others go idle. The stealing mechanism handles this edge case without adding complexity to the submission path.

## Decision 9: Dual-Park Strategy
- **Date**: 2026-04-03
- **Decision**: Workers first do an indefinite `LockSupport.park()`, then fall back to `parkNanos(10ms)` if woken without work
- **Rationale**: The initial park avoids unnecessary CPU usage when the scheduler is idle. The timed park ensures that even with a missed unpark (race condition), the worker will re-check for work within 10ms. This trades a small amount of latency (up to 10ms) for correctness guarantees.

## Decision 10: Include nextRunnable in Metrics
- **Date**: 2026-04-03
- **Decision**: Added `if (worker.nextRunnable ne null) enqueued += 1` and `size += 1` to match ZScheduler
- **Rationale**: ZScheduler counts tasks in `nextRunnable` as part of `enqueuedCount` and `size`. NioScheduler was missing this contribution, causing metrics to undercount pending tasks. While `nextRunnable` is typically null or set briefly, consistency with ZScheduler ensures tools relying on metrics work correctly across scheduler implementations.
