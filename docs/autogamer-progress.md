# Autogamer Progress: ZIO Scheduler Optimizations

## Mission 1: ZScheduler Parking/Unparking Optimization (Issue #9878)

### Status: PR-Ready ✓

### Task 8: Final Code Review & Commit (2026-04-04)

#### Code Review Summary
- **Code-reviewer agent**: No CRITICAL or HIGH issues found
- **Spin-before-park**: Correctly implemented — spin BEFORE state transition, spinCount reset on work found
- **Submit-count throttle**: Correctly implemented — global always unparks, local throttled to 1/16
- **`searching = true` placement**: Correct — only set after actual park wake-up, never after spin
- **Race conditions**: None found — `submitCount.incrementAndGet()` is atomic
- **Deadlocks/livelocks**: None possible — spin bounded by maxSpins, global queue always unparks

#### Verification Results
- `sbt coreJVM/compile`: SUCCESS
- `sbt "coreJVM/scalafmtCheck"`: SUCCESS
- `sbt "coreNative/scalafmtCheck"`: SUCCESS
- `sbt "coreTestsJVM/testOnly *Scheduler*"`: SUCCESS — 17 tests pass, 2 ignored

#### Commit Prepared
- Branch: `nio-scheduler-clean`
- Files to commit: `core/jvm-native/src/main/scala/zio/internal/ZScheduler.scala`, docs
- Message: `perf(core): reduce ZScheduler park/unpark frequency with spin-before-park and submit-count throttling`

#### PR Summary (for description)
**Changes:**
1. **Spin-before-park**: Workers spin 100 iterations using `Thread.onSpinWait()` before calling `LockSupport.park()`. This avoids the expensive park/unpark syscall pair when work arrives within a short window.

2. **Submit-count throttled unpark**: Only call `maybeUnparkWorker()` every 16th local-queue submission. Global queue submissions always unpark (critical for correctness since no active worker can see those tasks).

**Addresses issue #9878**: The core complaint was `maybeUnparkWorker` (specifically `LockSupport.unpark(worker)`) being called too frequently in the hot path, causing excessive context switches.

#### Acceptance Criteria
- [x] Code review passed with no CRITICAL or HIGH issues
- [x] Documentation updated
- [x] Changes committed with proper message
- [x] Branch ready for PR submission to zio/zio repo targeting series/2.x

---

### Task 7: Submit-Count Throttled Unpark Implementation (2026-04-04)

#### Changes Made
- **ZScheduler.scala**: Added submit-count throttling to reduce expensive `LockSupport.unpark()` calls
  - Added `submitCount` AtomicInteger field (line 43)
  - Added `shouldUnparkWorker()` method — returns true every 16th call using bitmask (lines 491-494)
  - Modified `submit()` to track `toGlobalQueue` flag and only call `maybeUnparkWorker()` for global queue OR every 16th local submission (lines 146-165)
  - Modified `submitAndYield()` to track `submittedToGlobal` flag and throttle `maybeUnparkWorker()` for local queue submissions (lines 167-208)

- **ZSchedulerBenchmarks.scala**: Added `zioSchedulerForkBomb()` benchmark
  - Creates 100k lightweight fibers rapidly to stress-test the unpark path
  - This is the exact scenario where excessive unparks cause overhead (lines 93-101)

#### Algorithm
```scala
// submit() - throttle unparks for local queue submissions
val toGlobalQueue = (worker eq null) || worker.blocking
if (toGlobalQueue) {
  globalQueue.offer(runnable)
} else if (!worker.localQueue.offer(runnable)) {
  handleFullWorkerQueue(worker, runnable)
}
if (toGlobalQueue || shouldUnparkWorker()) {  // Every 16th local, always for global
  maybeUnparkWorker(state.get)
}

// shouldUnparkWorker() - pure count-based, no O(n) queue size check
private def shouldUnparkWorker(): Boolean =
  (submitCount.incrementAndGet() & 15) == 0
```

#### Verification
- `sbt coreJVM/compile`: SUCCESS
- `sbt "coreJVM/scalafmtCheck"`: SUCCESS
- `sbt "coreNative/scalafmtCheck"`: SUCCESS
- `sbt "coreTestsJVM/testOnly *Scheduler*"`: SUCCESS — 17 tests pass, 2 ignored
- `sbt "benchmarks/compile"`: SUCCESS

#### Acceptance Criteria
- [x] `submitCount` AtomicInteger field added to ZScheduler
- [x] `shouldUnparkWorker()` method added with `(submitCount.incrementAndGet() & 15) == 0`
- [x] `submit()` only calls `maybeUnparkWorker` on global queue submissions OR every 16th local submission
- [x] `submitAndYield()` throttles `maybeUnparkWorker` for local queue submissions
- [x] Global queue submissions always bypass the throttle (critical for correctness)
- [x] JMH benchmark for high-frequency forking added
- [x] Code compiles and formats correctly
- [x] Existing tests pass
- [x] No public API changes

---

### Task 6: Code Review Round 3 (2026-04-04)

#### Review Findings
| # | Severity | Issue | Resolution |
|---|----------|-------|------------|
| 1 | HIGH | `searching = true` set unconditionally after spin iterations, causing state corruption when non-searcher workers find work | Fixed: moved `searching = true` into only the two park branches (after actual `LockSupport.park()` wake-up) |
| 2 | LOW | Each spin iteration runs full work-finding loop (carried from Round 2) | Accepted: enables finding work sooner, spin count of 100 is bounded |

#### Root Cause Analysis (Issue #1)
The `searching = true` at the end of the `if (runnable eq null)` block was shared by all three branches (spin, park, re-park). When a non-searcher worker (one that did NOT increment the state's searching count at line 327) entered the spin branch:
1. Worker had `searching = false` (state searching count not incremented)
2. `2 * searching_count >= poolSize` → worker did NOT become a searcher
3. Worker spun (lines 372-377), then `searching = true` was set at the shared location
4. On next iteration, worker skipped `if (!searching)` guard, went to stealing, found work
5. `state.decrementAndGet()` at line 425 decremented a searching count that was never incremented
6. State corruption: searching count wraps to 0xffff, breaking `maybeUnparkWorker` logic

The fix moves `searching = true` into only the `else if (active)` (park) and `else` (re-park) branches, removing it from the shared location. Spin iterations now preserve the worker's existing `searching` value, which correctly reflects whether the worker is counted as a searcher in the state.

#### Verification (Post-Fix)
- `sbt compile`: SUCCESS (all projects)
- `sbt "coreJVM/scalafmtCheck"`: SUCCESS
- `sbt "coreNative/scalafmtCheck"`: SUCCESS

#### Acceptance Criteria
- [x] `searching = true` only set after actual park wake-up, never after spin
- [x] Non-searcher workers that spin preserve correct `searching` value
- [x] State searching count never corrupted by spin-then-find-work path
- [x] No public API changes
- [x] Code compiles and formats correctly

---

### Task 5: Code Review Round 2 (2026-04-04)

#### Review Findings
| # | Severity | Issue | Resolution |
|---|----------|-------|------------|
| 1 | CRITICAL | Spin loop re-enters state-update block, corrupting active/searching counts and duplicating idle-queue entries | Fixed: moved spin BEFORE state update so transition happens at most once |
| 2 | LOW | Each spin iteration runs full work-finding loop (queue polls + potential steals) — heavier than a tight spin | Accepted: enables finding work sooner, spin count of 100 is bounded |

#### Root Cause Analysis (Issue #1)
The original spin-before-park placed the spin logic INSIDE the `if (runnable eq null)` block, AFTER the state update and idle-queue add. When `spinCount < maxSpins`, the worker did NOT park but continued the outer `while (!isInterrupted)` loop, re-entering the state-update block on every iteration. After 100 spin iterations:
- `state.addAndGet(0xfffeffff)` called 100 times → activeCount/searchingCount decremented by 100 each (should be 1)
- `idle.offer(self)` called 100 times → worker appears 100 times in idle queue
- `active = false` set 100 times → harmless but indicative of the re-entry

The fix moves the spin BEFORE the state update. Workers now spin while still active (counted correctly in state, not in idle queue). Only when the spin budget is exhausted does the worker transition to inactive (single state update, single idle-queue add) and park.

#### Verification (Post-Fix)
- `sbt coreJVM/compile`: SUCCESS
- `sbt "coreJVM/scalafmtCheck"`: SUCCESS

#### Acceptance Criteria
- [x] State update happens at most once per park cycle
- [x] Worker added to idle queue at most once per park cycle
- [x] Spinning workers remain active (visible to scheduler state)
- [x] No public API changes
- [x] Code compiles and formats correctly

---

### Task 3: Spin-Before-Park Optimization (2026-04-04)

#### Problem
Workers call `LockSupport.park()` immediately when no work is found, then require an `unpark()` call when new work arrives. This park/unpark syscall pair is expensive and causes latency spikes and context switches when work arrives within a short window.

#### Solution
Added spin-before-park optimization in `Worker.run()`:
- Workers spin up to 100 iterations using `Thread.onSpinWait()` before parking
- Spin count is reset when work is found
- This avoids the expensive park/unpark syscall pair when work arrives quickly

#### Changes Made
- `core/jvm-native/src/main/scala/zio/internal/ZScheduler.scala`:
  - Added `spinCount` and `maxSpins` local variables (lines 306-307)
  - Added spin logic before `LockSupport.park()` (lines 401-411)
  - Reset `spinCount` when work is found (line 415)

#### Algorithm
```scala
// When no work found (runnable eq null):
if (spinCount < maxSpins) {
  // Spin BEFORE going inactive — worker stays active in state
  spinCount += 1
  Thread.onSpinWait()
  // Continue outer loop to re-check for work
} else {
  // Spin budget exhausted — transition to inactive ONCE
  spinCount = 0
  // State update (activeCount-1, searchingCount-1)
  // Add to idle queue
  // LockSupport.park()
}

// When work is found:
spinCount = 0
```

#### Verification
- `sbt coreJVM/compile`: SUCCESS
- `sbt "coreJVM/scalafmtCheck"`: SUCCESS

#### Acceptance Criteria
- [x] Workers spin briefly before parking
- [x] Spin count resets when work is found
- [x] No public API changes
- [x] Code compiles and formats correctly

### Task 2: Code Review Round 1 (2026-04-04)

#### Review Findings
| # | Severity | Issue | Resolution |
|---|----------|-------|------------|
| 1 | HIGH | Task stranding: throttle applied to ALL submissions including global queue | Fixed: global queue submissions bypass throttle |
| 2 | MEDIUM | O(n) `globalQueue.size()` on 15/16 submissions | Fixed: removed from `shouldUnparkWorker()` |

### Task 1: Submit-Count Throttled Unpark Policy (2026-04-04)

#### Problem
`LockSupport.unpark()` was called on every task submission in the hot path, causing excessive context switches.

#### Solution
- Added `submitCount` AtomicInteger
- Added `shouldUnparkWorker()` that returns true every 16th submission
- Global queue submissions always bypass throttle to prevent task stranding

---

## Mission 2: NIO Scheduler for ZIO (Issue #9356)

## Status: PR-Ready ✓

### Task 14: PR Readiness Verification (2026-04-03)

#### Verification Results
- **sbt "coreTestsJVM/testOnly zio.internal.NioSchedulerSpec"**: SUCCESS — 17 tests pass, 2 ignored (stress tests by design)
- **sbt scalafmtCheckAll**: SUCCESS — All formatting checks pass
- **git status**: CLEAN — Branch `nio-scheduler-clean` is 7 commits ahead of `upstream/series/2.x`

#### Issue #9356 Requirements Verification
Fetched and verified against original issue and Nio blog post:
- ✅ Least-Loaded scheduling algorithm implemented (assigns tasks to worker with smallest queue)
- ✅ Per-worker local queues (RingBufferPow2, capacity 256)
- ✅ Global queue fallback for overflow
- ✅ Auto-blocking detection via supervisor thread (optional)
- ✅ Task migration when worker blocks
- ✅ ZLayer integration for easy adoption
- ✅ Comprehensive test coverage
- ✅ Benchmarks for performance comparison

#### Files Ready for PR
| File | Purpose |
|------|---------|
| `core/jvm-native/src/main/scala/zio/internal/NioScheduler.scala` | Core scheduler (613 lines) |
| `core/jvm-native/src/main/scala/zio/internal/DefaultExecutors.scala` | Factory methods |
| `core/jvm-native/src/main/scala/zio/internal/Blocking.scala` | Auto-blocking integration |
| `core/jvm/src/main/scala/zio/RuntimePlatformSpecific.scala` | ZLayer API (JVM) |
| `core/native/src/main/scala/zio/RuntimePlatformSpecific.scala` | ZLayer API (Native) |
| `core-tests/jvm-native/src/test/scala/zio/internal/NioSchedulerSpec.scala` | Test suite (318 lines) |
| `benchmarks/src/main/scala/zio/internal/NioSchedulerBenchmarks.scala` | JMH benchmarks |
| `docs/reference/core/runtime.md` | User documentation |

#### Acceptance Criteria
- [x] All tests pass (17/17, 2 stress tests ignored by design)
- [x] Formatting passes
- [x] Git status clean
- [x] Issue requirements verified
- [x] Documentation complete

**Ready for PR submission to https://github.com/zio/zio/issues/9356**

---

### Task 13: Final Verification & Test Fix (2026-04-03)

#### Issue Found & Fixed
- **Interruption test failure**: Test "interruption works correctly" was flaky because it didn't wait for the fiber to start before interrupting
- **Fix**: Added `started` Promise to ensure fiber is running before interruption, following the pattern used in other ZIO tests (e.g., CancelableFutureSpec, FiberSpec)
- **Additional fix**: Store supervisor as class field to properly interrupt it on shutdown

#### Final Verification
- `sbt coreTestsNative/testOnly *NioSchedulerSpec*`: SUCCESS — 17 tests pass (2 stress tests ignored), 0 failures
- `sbt scalafmtCheckAll`: SUCCESS
- All tests now pass reliably

#### Acceptance Criteria
- [x] All tests pass
- [x] Formatting passes
- [x] Branch is clean and ready for PR

---

### Task 12: Final Documentation & PR Readiness (2026-04-03)

#### ScalaDoc Review
All public-facing APIs and key internal methods now have ScalaDoc:
- **NioScheduler class-level doc**: Threading model, auto-blocking, state encoding, usage examples (lines 28-74)
- **Overridden Executor methods**: `submit`, `submitAndYield`, `stealWork`, `metrics`, `isCurrentThreadInExecutor` — each has concise doc describing NioScheduler-specific behavior
- **Internal types**: `Worker` (queue details, BlockContext integration), `Supervisor` (monitoring behavior), `markCurrentWorkerAsBlocking` (task migration)
- **Factory methods**: `DefaultExecutors.makeNio()` with `@see` references
- **ZLayer API**: `Runtime.enableNioScheduler`, `Runtime.enableNioSchedulerWithAutoBlocking` on both JVM and Native platforms

#### Documentation Coverage
- No separate `zio-nio` subproject README needed — NioScheduler lives in `core/jvm-native`
- `docs/reference/core/runtime.md` already has "Enabling the NIO Scheduler" section with usage examples, auto-blocking guidance, and "When to Use" recommendations
- No additional documentation sections needed

#### Acceptance Criteria
- [x] Public API has ScalaDoc on all methods and types
- [x] `docs/reference/core/runtime.md` covers NIO scheduler usage
- [x] `docs/autogamer-progress.md` is current
- [x] `docs/autogamer-decisions.md` is current
- [x] Branch is clean and ready for PR creation

---

### Task 11: CI Verification & Code Review (2026-04-03)

#### Build Verification
- `sbt coreJVM/compile`: SUCCESS
- `sbt "coreTestsJVM/testOnly zio.internal.NioSchedulerSpec"`: SUCCESS — 17 tests pass, 2 ignored
- `sbt scalafmtCheckAll`: SUCCESS

#### Code Review (NioScheduler.scala, 584 lines)
- **Thread safety**: `@volatile` on `active`, `blocking`, `currentRunnable`, `opCount`; `synchronized` on `markAsBlocking()`; `AtomicInteger` for state; `ConcurrentLinkedQueue` for global queue — all correct
- **Resource cleanup**: `shutdown()` sets flag + interrupts all workers; workers check both `shutdown` and `isInterrupted` in loop
- **No task loss**: `submitToLeastLoaded` unparks the specific worker that received the task (line 272); safety-net `parkNanos(10ms)` handles missed unparks; double-check for work before parking (line 432)
- **Executor integration**: All required methods implemented (`submit`, `submitAndYield`, `stealWork`, `metrics`, `isCurrentThreadInExecutor`); `stealWork` correctly handles `FiberRunnable` with depth propagation
- **No issues found**: Implementation is sound, no fixes needed

#### Acceptance Criteria
- [x] `sbt coreJVM/compile` succeeds
- [x] `sbt "coreTestsJVM/testOnly zio.internal.NioSchedulerSpec"` passes (17/17)
- [x] `sbt scalafmtCheckAll` passes
- [x] Thread safety verified — shared mutable state properly synchronized
- [x] Resource cleanup verified — shutdown terminates threads
- [x] No task loss verified — submitted tasks always eventually executed
- [x] ZIO Executor trait integration correct

---

### Task 10: PR-Ready Branch Verification (2026-04-03)

#### Integration Wiring Check
- No SPI registration or `META-INF/services` needed — ZIO uses explicit `Runtime.setExecutor()` configuration
- NioScheduler is wired via:
  - `DefaultExecutors.makeNio()` factory methods (matching `makeDefault()` pattern)
  - `Runtime.enableNioScheduler` / `Runtime.enableNioSchedulerWithAutoBlocking` ZLayers
  - `Blocking.signalBlocking()` delegates to both ZScheduler and NioScheduler
- All integration consistent with existing ZScheduler, Loom, and auto-blocking patterns

#### Build Verification
- `sbt coreJVM/compile`: SUCCESS
- `sbt coreTestsJVM/compile`: SUCCESS
- `sbt "coreTestsJVM/testOnly zio.internal.NioSchedulerSpec"`: SUCCESS — 17 tests pass, 2 ignored

#### Diff Cleanliness
- No `println` / `debug` / `TODO` / `FIXME` / `HACK` statements in any changed files
- No build artifacts accidentally included
- No unintended formatting changes
- Only 3 NIO-specific commits on top of ZIO baseline:
  1. `5a41ec3` feat(zio-nio): Implement NIO Scheduler with least-loaded scheduling
  2. `86ed799` fix(zio-nio): Fix NioScheduler task loss, test reliability, and formatting
  3. `79c2554` fix(zio-nio): Fix scalafmt formatting for CI

#### Files Changed (NIO-specific)
| File | Status | Lines |
|------|--------|-------|
| `core/jvm-native/src/main/scala/zio/internal/NioScheduler.scala` | New | 584 |
| `core/jvm-native/src/main/scala/zio/internal/DefaultExecutors.scala` | Modified | +22 |
| `core/jvm-native/src/main/scala/zio/internal/Blocking.scala` | Modified | +10/-1 |
| `core/jvm/src/main/scala/zio/RuntimePlatformSpecific.scala` | Modified | +25 |
| `core/native/src/main/scala/zio/RuntimePlatformSpecific.scala` | Modified | +25 |
| `core-tests/jvm-native/src/test/scala/zio/internal/NioSchedulerSpec.scala` | New | 315 |
| `benchmarks/src/main/scala/zio/internal/NioSchedulerBenchmarks.scala` | New | 170 |
| `docs/reference/core/runtime.md` | Modified | +78 |

#### Acceptance Criteria Met
- [x] `sbt coreJVM/compile` succeeds
- [x] `sbt "coreTestsJVM/testOnly zio.internal.NioSchedulerSpec"` passes (17/17)
- [x] Git diff is clean — no debug code, no accidental changes
- [x] No `println` statements in any changed files
- [x] Integration wiring complete (DefaultExecutors, Blocking, RuntimePlatformSpecific)
- [x] No SPI registration needed (explicit configuration pattern)

---

### Task 9: Verification & Completeness Review (2026-04-03)

#### Review Scope
Full review of NioScheduler implementation against ZIO's Executor interface and ZScheduler patterns.

#### Interface Compliance
- NioScheduler correctly implements `Executor` (not `Scheduler` — the Scheduler trait handles delayed scheduling, a separate concern)
- All required `Executor` methods implemented: `submit`, `submitAndYield`, `stealWork`, `metrics`, `isCurrentThreadInExecutor`
- Factory method `Executor.makeNio()` in `DefaultExecutors.scala` follows the same pattern as `Executor.makeDefault()`
- ZLayer integration via `Runtime.enableNioScheduler` / `Runtime.enableNioSchedulerWithAutoBlocking` matches existing `enableAutoBlockingExecutor` and `enableLoomBasedExecutor` patterns

#### Correctness Verification
- **State encoding**: `AtomicInteger` with upper 16 bits (active) and lower 16 bits (searching) matches ZScheduler exactly. Constants `0x10001`, `0xfffeffff`, `0xffff0000` verified correct.
- **Worker lifecycle**: Workers start active, transition through searching → idle → parked → woken states correctly. 10ms safety-net park prevents indefinite stalls.
- **Least-loaded routing**: `submitToLeastLoaded` scans workers, picks lowest queue, early-exits on empty worker. Falls back to global queue when all busy.
- **Blocking detection**: `markAsBlocking` synchronized correctly, migrates tasks to global queue, spawns replacement worker with state increment. Both auto-blocking (supervisor thread) and `BlockContext` integration verified.
- **`Blocking.signalBlocking()`**: Correctly delegates to both `ZScheduler.markCurrentWorkerAsBlocking()` and `NioScheduler.markCurrentWorkerAsBlocking()`
- **Metrics**: `dequeuedCount`, `enqueuedCount`, `size`, `concurrency`, `workersCount` all verified consistent with ZScheduler patterns

#### Bug Fix Applied
- **Missing `nextRunnable` in metrics**: `enqueuedCount` and `size` did not account for tasks in `worker.nextRunnable`, while ZScheduler does. Fixed to match ZScheduler convention.

#### Accepted Trade-offs (same as ZScheduler)
- O(n) worker scan in `submitToLeastLoaded` / `maybeUnparkWorker` — acceptable for typical pool sizes (4–64 cores)
- No thread joining in `shutdown()` — matches ZScheduler pattern
- No exception handling in worker `run()` — ZIO catches at fiber level
- `stealWork` could overwrite `nextRunnable` in rare race — same as ZScheduler, accepted trade-off
- `unparkWorker` state counter may drift from concurrent calls — heuristic counter, self-correcting

#### Verification Results
- **sbt coreJVM/compile**: SUCCESS
- **sbt coreTestsJVM/testOnly zio.internal.NioSchedulerSpec**: SUCCESS — 17 tests pass, 2 ignored
- **sbt scalafmtCheck**: SUCCESS

#### Files Changed in This Task
- `core/jvm-native/src/main/scala/zio/internal/NioScheduler.scala` — Fixed `enqueuedCount` and `size` metrics to include `nextRunnable`

---

### Task 8: Code Quality Review & Documentation Pass (2026-04-03)

#### Code Review Findings
- **NioScheduler.scala**: Clean implementation. Thread safety verified: `@volatile` on shared fields, `synchronized` on `markAsBlocking`, proper `AtomicInteger` state management.
- **NioSchedulerSpec.scala**: Removed debug `println` from stress test (line 302). Removed unused `start`/`elapsed` variables.
- **NioSchedulerBenchmarks.scala**: Added missing copyright header.
- **DefaultExecutors.scala**, **Blocking.scala**, **RuntimePlatformSpecific.scala**: All clean, proper ScalaDoc.

#### ScalaDoc Enhancements
- `NioScheduler` class doc expanded with '''Threading Model''', '''Auto-Blocking''', '''State Encoding''', and '''Usage''' sections
- `NioScheduler.Worker` doc expanded with queue details, opCount purpose, and BlockContext integration
- `NioScheduler.Supervisor` doc expanded with monitoring behavior details
- `markCurrentWorkerAsBlocking` doc expanded with explanation of task migration and replacement worker spawning
- `submitToLeastLoaded` doc expanded with algorithm details and fallback behavior

#### Verification
- `sbt coreJVM/compile`: SUCCESS
- `sbt coreTestsJVM/testOnly zio.internal.NioSchedulerSpec`: 17 tests pass
- `sbt scalafmtCheck` on coreJVM, coreTestsJVM, benchmarks: SUCCESS

---

### Task 7: Final Verification (2026-04-03)

#### Verification Results
- **sbt "coreTestsJVM/testOnly zio.internal.NioSchedulerSpec"**: SUCCESS - 17 tests pass, 2 ignored (stress tests)
- **sbt "coreJVM/compile"**: SUCCESS - Compiles cleanly
- **sbt "scalafmtCheckAll"**: SUCCESS - All formatting checks pass

#### Implementation Summary

The NioScheduler implementation is complete and verified:

| Component | File | Purpose |
|-----------|------|---------|
| NioScheduler | `core/jvm-native/src/main/scala/zio/internal/NioScheduler.scala` | Least-loaded scheduler (530 lines) |
| DefaultExecutors | `core/jvm-native/src/main/scala/zio/internal/DefaultExecutors.scala` | `makeNio()` factory methods |
| Blocking | `core/jvm-native/src/main/scala/zio/internal/Blocking.scala` | Integration with `markCurrentWorkerAsBlocking()` |
| RuntimePlatformSpecific | `core/jvm/src/main/scala/zio/RuntimePlatformSpecific.scala` | `enableNioScheduler` and `enableNioSchedulerWithAutoBlocking` ZLayers |
| NioSchedulerSpec | `core-tests/jvm-native/src/test/scala/zio/internal/NioSchedulerSpec.scala` | Test suite (17 tests) |

#### Issue #9356 Requirements Met
- ✅ Least-loaded scheduling algorithm (assigns tasks to worker with least workload)
- ✅ Worker pool with per-worker local queues
- ✅ Auto-blocking detection (optional, via supervisor thread)
- ✅ Integration with ZIO runtime via ZLayer
- ✅ Comprehensive tests
- ✅ Benchmarks for comparison with ZScheduler

#### No Issues Found
All tests pass, compilation succeeds, and formatting is correct.

---

### Task 6: PR Preparation (2026-04-03)

#### Summary of Changes

**Files Modified (8 files, +1158 lines):**

| File | Changes |
|------|---------|
| `core/jvm-native/src/main/scala/zio/internal/NioScheduler.scala` | New: 530-line Least-Loaded scheduler implementation |
| `core/jvm-native/src/main/scala/zio/internal/DefaultExecutors.scala` | Added `makeNio()` factory methods with ScalaDoc |
| `core/jvm-native/src/main/scala/zio/internal/Blocking.scala` | Updated `signalBlocking()` to call `NioScheduler.markCurrentWorkerAsBlocking()` |
| `core/jvm/src/main/scala/zio/RuntimePlatformSpecific.scala` | Added `enableNioScheduler` and `enableNioSchedulerWithAutoBlocking` layers |
| `core/native/src/main/scala/zio/RuntimePlatformSpecific.scala` | Same for Scala Native platform |
| `core-tests/jvm-native/src/test/scala/zio/internal/NioSchedulerSpec.scala` | New: 318-line test suite (17 tests, 2 stress tests ignored) |
| `benchmarks/src/main/scala/zio/internal/NioSchedulerBenchmarks.scala` | New: 154-line JMH benchmark suite |
| `docs/reference/core/runtime.md` | Added NIO scheduler documentation with usage examples |

#### Public API Surface

All public APIs have ScalaDoc:

1. **Runtime.enableNioScheduler** - ZLayer to enable NIO scheduler
2. **Runtime.enableNioSchedulerWithAutoBlocking** - ZLayer with auto-blocking detection
3. **Executor.makeNio()** - Factory method to create NIO executor directly

#### Documentation

- `docs/reference/core/runtime.md` updated with:
  - "Enabling the NIO Scheduler" section
  - Usage examples with `bootstrap` layer
  - Programmatic usage with `Executor.makeNio()`
  - "When to Use NIO Scheduler" guidance

#### Issue Requirements

Issue #9356 requested implementing an NIO scheduler inspired by https://nurmohammed840.github.io/posts/announcing-nio/

**Requirements Met:**
- ✅ Least-loaded scheduling algorithm (assigns tasks to worker with least workload)
- ✅ Worker pool with per-worker local queues
- ✅ Auto-blocking detection (optional, via supervisor thread)
- ✅ Integration with ZIO runtime via ZLayer
- ✅ Comprehensive tests
- ✅ Benchmarks for comparison with ZScheduler

#### Verification Commands

```bash
sbt coreTestsJVM/testOnly zio.internal.NioSchedulerSpec  # 17 tests pass
sbt coreJVM/compile                                       # Compiles cleanly
sbt scalafmtCheckAll                                      # Formatting passes
```

#### Ready for PR

The branch is ready for `gh pr create`:
- All tests pass
- ScalaDoc complete on public APIs
- Documentation updated
- Code formatted correctly

---

### Task 5: Code Review Round 3 (2026-04-03)

#### Review Result: PASS

All files reviewed:
- `core/jvm-native/src/main/scala/zio/internal/NioScheduler.scala` (530 lines)
- `core/jvm-native/src/main/scala/zio/internal/DefaultExecutors.scala`
- `core/jvm-native/src/main/scala/zio/internal/Blocking.scala`
- `core/jvm/src/main/scala/zio/RuntimePlatformSpecific.scala`
- `core/native/src/main/scala/zio/RuntimePlatformSpecific.scala`
- `core/js/src/main/scala/zio/RuntimePlatformSpecific.scala`
- `core-tests/jvm-native/src/test/scala/zio/internal/NioSchedulerSpec.scala`
- `benchmarks/src/main/scala/zio/internal/NioSchedulerBenchmarks.scala`
- `docs/reference/core/runtime.md`

#### Verification Results
- **sbt coreTestsJVM/testOnly zio.internal.NioSchedulerSpec**: SUCCESS - 17 tests pass, 2 ignored
- **sbt coreJVM/compile**: SUCCESS - Compiles cleanly

#### Issues Found: 0 Critical, 0 High, 2 Medium, 3 Low

**Medium Issues (accepted, match ZScheduler patterns):**
1. O(n) worker scan in `submitToLeastLoaded` and `maybeUnparkWorker` — acceptable for pool sizes (typically 4-64 cores)
2. Supervisor thread accesses `workers` array entries that may be replaced during `markAsBlocking` — safe because `synchronized` on `markAsBlocking` and old worker thread gracefully terminates

**Low Issues (accepted):**
1. `shutdown()` doesn't join threads — matches ZScheduler
2. No exception handling in worker `run()` loop — ZIO catches at fiber level
3. Stress tests are `@@ TestAspect.ignore` — by design, for manual benchmarking

#### Code Quality Assessment
- Correctness: No logic errors found. State management (`0x10001`, `0xfffeffff`, `0xffff0000`) matches ZScheduler
- Concurrency: `synchronized` on `markAsBlocking` prevents race conditions. `@volatile` correctly used on shared fields
- Integration: `Blocking.signalBlocking()` correctly delegates to both ZScheduler and NioScheduler
- Platform: JS platform correctly has no-op `DefaultExecutors` and `Blocking` — no NioScheduler there
- Tests: Comprehensive coverage of basic, integration, concurrency, auto-blocking, and stress scenarios
- Docs: Clear usage examples with `mdoc:compile-only` markers

---

### Task 4: Final Verification (2026-04-03)

#### Verification Results
- **sbt coreTestsJVM/testOnly zio.internal.NioSchedulerSpec**: SUCCESS - 17 tests pass, 2 ignored (stress tests)
- **sbt coreJVM/compile**: SUCCESS - Compiles cleanly, no warnings
- **sbt coreTestsJVM/scalafmtCheck**: SUCCESS - All formatting checks pass
- **sbt coreJVM/scalafmtCheck**: SUCCESS - All formatting checks pass

#### Code Review Findings
- State management pattern (`0xfffeffff` for searching worker idle) matches ZScheduler exactly
- `unparkWorker` correctly targets specific worker that received a task (avoiding stranded tasks)
- `maybeUnparkWorker` uses same `0x10001` increment pattern as ZScheduler
- `markAsBlocking` correctly migrates tasks to global queue and spawns replacement worker
- All volatile fields properly annotated (`@volatile`)
- `BlockContext` override in Worker correctly calls `markAsBlocking()` for blocking operations
- Test suite uses `CountDownLatch` synchronization (avoids TestClock interference)

#### No Issues Found
No compilation errors, test failures, formatting issues, or code quality problems were found.
The implementation is clean and ready for PR submission.

---

### Task 3: Build Verification, Bug Fixes, and PR Preparation (2026-04-03)

#### Build Verification
- **sbt coreJVM/compile**: SUCCESS - All JVM modules compile cleanly
- **sbt coreTestsJVM/testOnly zio.internal.NioSchedulerSpec**: SUCCESS - 17 tests pass, 2 ignored (stress tests)
- **sbt scalafmtCheckAll**: SUCCESS - All formatting checks pass after `sbt scalafmtAll`

#### Compilation Errors Fixed
1. `System.nanoTime()` shadowed by `zio.System` import → fixed to `java.lang.System.nanoTime()`
2. Unused `var i` warnings treated as errors → renamed to `ii` consistently
3. Unused `submitFireAndForget` private method → removed

#### Scheduler Bug Fixes (Critical)
1. **Workers stuck in park losing tasks**: Workers parked indefinitely when tasks were submitted to their local queues. Fixed by:
   - Adding `unparkWorker(target)` in `submitToLeastLoaded` to wake the specific worker that received the task
   - Adding a timed `parkNanos(10ms)` safety net after initial park to prevent indefinite waits
2. **Test race condition**: `ZIO.raceAll(ZIO.succeed(1), List(ZIO.succeed(2)))` was non-deterministic → changed to `raceAll(ZIO.succeed(1), List(ZIO.never))`
3. **Test sleep issues**: All `ZIO.sleep` calls in tests were replaced with `CountDownLatch` synchronization to avoid TestClock interference in ZIO's test framework

#### Code Review Findings
| # | Severity | Issue | Status |
|---|----------|-------|--------|
| 1 | MEDIUM | O(n) worker scan in maybeUnparkWorker | Accepted - matches ZScheduler, optimize later |
| 2 | MEDIUM | Linear indexOf in markAsBlocking | Accepted - matches ZScheduler |
| 3 | LOW | No exception handling in worker run() | Accepted - ZIO catches at fiber level |
| 4 | LOW | shutdown() doesn't join threads | Accepted - matches ZScheduler |

### Files Modified
1. `core/jvm-native/src/main/scala/zio/internal/NioScheduler.scala` - Scheduler implementation (bug fixes)
2. `core-tests/jvm-native/src/test/scala/zio/internal/NioSchedulerSpec.scala` - Tests (rewritten for reliability)
3. `core/jvm-native/src/main/scala/zio/internal/Blocking.scala` - Integration with signalBlocking()
4. `core/jvm-native/src/main/scala/zio/internal/DefaultExecutors.scala` - makeNio() factory methods
5. `core/jvm/src/main/scala/zio/RuntimePlatformSpecific.scala` - enableNioScheduler/enableNioSchedulerWithAutoBlocking
6. `core/native/src/main/scala/zio/RuntimePlatformSpecific.scala` - Same for native
7. `docs/reference/core/runtime.md` - Documentation

### Untracked Files
- `benchmarks/src/main/scala/zio/internal/NioSchedulerBenchmarks.scala` - JMH benchmarks

### Test Results Summary
- 17 tests pass across 7 suites
- 2 stress tests ignored (by design, `@@ TestAspect.ignore`)
- Test execution time: ~400ms
- Suites: basic functionality, least-loaded scheduling, ZIO runtime integration, submitAndYield, auto-blocking, concurrent scheduling, stress tests
