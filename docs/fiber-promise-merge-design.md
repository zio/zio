# Design Document: Can Fiber.Runtime and Promise Be Merged?

**Issue**: [zio/zio#9877](https://github.com/zio/zio/issues/9877)
**Date**: 2026-04-04
**Status**: Analysis Complete

## Executive Summary

**Recommendation: Do NOT merge Fiber.Runtime and Promise.**

While both types share superficial similarities (single completion, observer pattern, async wait), their internal mechanisms, semantics, and use cases are fundamentally different. A merge would increase complexity without meaningful benefit and would break backward compatibility.

---

## 1. Current Structure Analysis

### 1.1 Promise Internal Structure

**File**: `core/shared/src/main/scala/zio/Promise.scala` (347 lines)

```scala
final class Promise[E, A] private (blockingOn: FiberId) extends Serializable {
  private[zio] val unsafe: UnsafeAPI = new AtomicReference[State[E, A]](State.empty[E, A])
}
```

**State Machine**:
```
Pending[E, A]  ──completeWith──>  Done[E, A]
     │
     ├── Empty (no waiters)
     └── Link[E, A] (waiter list: linked list of callbacks)
```

**Key Fields** (lines 40-243):
| Field | Type | Purpose |
|-------|------|---------|
| `blockingOn` | `FiberId` | Identifies which fiber this promise blocks on (for deadlock detection) |
| `unsafe` | `AtomicReference[State[E, A]]` | Lock-free state machine (Pending or Done) |

**State Hierarchy** (lines 246-329):
- `State[E, A]` - sealed abstract class
  - `Done[E, A](value: IO[E, A])` - completed state
  - `Pending[E, A]` - sealed abstract, with `add`, `remove`, `complete`, `size` methods
    - `Empty` - singleton, no waiters
    - `Link[E, A](waiter, ws)` - linked list node holding callbacks

**Key Operations**:
- `await` - suspends caller until complete (lines 47-72)
- `completeWith(io)` - CAS-based completion, notifies all waiters (lines 109-110, 195-209)
- `poll` - non-blocking check for completion (lines 151-152, 228-232)

### 1.2 Fiber.Runtime Internal Structure

**File**: `core/shared/src/main/scala/zio/internal/FiberRuntime.scala` (1754 lines)

```scala
final class FiberRuntime[E, A](fiberId: FiberId.Runtime, fiberRefs0: FiberRefs, runtimeFlags0: RuntimeFlags)
    extends Fiber.Runtime.Internal[E, A] with FiberRunnable
```

**State Machine**:
```
Running ──evaluateEffect──> Done
    │
    ├── Suspended (async wait)
    │       └── Resume/Interrupt
    └── Interrupted
```

**Key Fields** (lines 30-68):
| Field | Type | Purpose |
|-------|------|---------|
| `fiberId` | `FiberId.Runtime` | Unique identity with location trace |
| `_fiberRefs` | `FiberRefs` | Thread-local state (FiberRef values) |
| `_runtimeFlags` | `RuntimeFlags` | Interruption, tracing, coop-yielding flags |
| `_blockingOn` | `() => FiberId` | Dynamic blocking-on for async ops |
| `_asyncContWith` | `AsyncContWith` | Async callback + optional interrupt handler |
| `running` | `AtomicBoolean` | Runloop active flag |
| `inbox` | `ConcurrentLinkedQueue[FiberMessage]` | Message queue for stateful ops |
| `_children` | `JavaSet[Fiber.Runtime[_, _]]` | Weak set of child fibers |
| `observers` | `List[Exit[E, A] => Unit]` | Completion observers |
| `_stack` | `Array[Continuation]` | Reified continuation stack |
| `_stackSize` | `Int` | Current stack depth |
| `_exitValue` | `Exit[E, A]` | Final result (null until complete) |

**Key Operations**:
- `await` - register observer callback via inbox messaging (lines 69-85)
- `start(effect)` - synchronous execution entry (lines 1469-1499)
- `startConcurrently(effect)` - async execution via inbox (lines 1506-1507)
- `tell(message)` - thread-safe message passing (lines 1521-1526)
- `runLoop(effect, ...)` - main interpreter (lines 1085-1358)

---

## 2. Overlap Analysis

### 2.1 Shared Concepts

| Concept | Promise | Fiber.Runtime |
|---------|---------|---------------|
| Single completion | ✅ `Done` state | ✅ `_exitValue` field |
| Observer pattern | ✅ `Link` waiter list | ✅ `observers` list |
| Async await | ✅ `ZIO.asyncInterrupt` | ✅ `ZIO.asyncInterrupt` in `awaitUnsafe` |
| Non-blocking poll | ✅ `poll` method | ✅ `poll` method |
| Interruptible wait | ✅ Returns `Left(canceler)` | ✅ Returns `Left(canceler)` |
| `blockingOn` tracking | ✅ Constructor param | ✅ `_blockingOn` field |

### 2.2 Fundamental Differences

| Aspect | Promise | Fiber.Runtime |
|--------|---------|---------------|
| **Purpose** | Write-once async cell | Effect executor with runloop |
| **Result type** | `IO[E, A]` (memoized effect) | `Exit[E, A]` (evaluated result) |
| **Creation** | External, independent | Created by forking an effect |
| **Completion trigger** | External (`succeed`, `fail`, etc.) | Internal (runloop finishes) |
| **Thread affinity** | None | Executes on Executor threads |
| **Stack** | None | Reified continuation stack |
| **Children** | None | Weak set of child fibers |
| **FiberRefs** | None | Full FiberRefs support |
| **Runtime flags** | None | Interruption, tracing, coop-yield |
| **Interrupt handling** | Simple broadcast to waiters | Complex: children, async callbacks, onInterrupt handlers |
| **Inheritance** | None | `inheritAll` propagates FiberRefs |
| **Code size** | ~347 lines | ~1754 lines |
| **Message queue** | None | `ConcurrentLinkedQueue[FiberMessage]` |

### 2.3 Key Architectural Distinction

**Promise** is a *data structure*:
- Pure state machine (Pending → Done)
- No execution context
- Completable from any thread
- Waiters are callbacks invoked once

**Fiber.Runtime** is an *actor*:
- Has inbox-based message passing
- Owns execution thread (via Executor)
- Completes itself via runloop
- Observers are notified via inbox messages

---

## 3. Proposed Merged Design (Hypothetical)

If we were to merge them, here's what it might look like:

```scala
sealed trait Completable[+E, +A] {
  def await(implicit trace: Trace): UIO[Exit[E, A]]
  def poll(implicit trace: Trace): UIO[Option[Exit[E, A]]]
  def isDone(implicit trace: Trace): UIO[Boolean]
}

trait Promise[E, A] extends Completable[E, A] {
  def succeed(a: A)(implicit trace: Trace): UIO[Boolean]
  def fail(e: E)(implicit trace: Trace): UIO[Boolean]
  def complete(io: IO[E, A])(implicit trace: Trace): UIO[Boolean]
  // ... other completion methods
}

trait Fiber.Runtime[+E, +A] extends Completable[E, A] {
  def id: FiberId.Runtime
  def children(implicit trace: Trace): UIO[Chunk[Fiber.Runtime[_, _]]]
  def inheritAll(implicit trace: Trace): UIO[Unit]
  def status(implicit trace: Trace): UIO[Fiber.Status]
  // ... internal runloop methods (private[zio])
}
```

### 3.1 Why This Doesn't Work

1. **Completion semantics differ**: Promise completes with `IO[E, A]` (effect), Fiber completes with `Exit[E, A]` (evaluated result). Promise can be completed externally; Fiber completes itself.

2. **No shared implementation**: The 5x code size difference comes from Fiber's runloop, stack management, children tracking, and inbox messaging. None of this is useful for Promise.

3. **Backward compatibility**: Both APIs are public and widely used. Merging would require deprecation cycles and break binary compatibility.

4. **Performance regression**: Promise's lock-free `AtomicReference` CAS is faster than Fiber's inbox-based observer registration. Promise uses `ZIO.asyncInterrupt` directly; Fiber goes through `tell(FiberMessage.Stateful(...))`.

5. **Semantic confusion**: Promise is "a cell that can be filled once". Fiber.Runtime is "a lightweight thread of execution". Users would be confused by a merged type.

---

## 4. Migration Path and Backward Compatibility

### 4.1 If We Were to Merge

**Phase 1 (ZIO 2.x)**:
- Introduce `Completable[E, A]` trait with `await`, `poll`, `isDone`
- Both `Promise` and `Fiber.Runtime` extend `Completable`
- Deprecate duplicate methods

**Phase 2 (ZIO 3.x)**:
- Merge implementations behind `Completable` interface
- Remove deprecated methods

### 4.2 Why Not To Merge

- **Breaking changes**: Both types are in hot paths. Any change risks subtle bugs.
- **Migration cost**: Users would need to update code for no functional benefit.
- **Maintenance burden**: Merged code would be more complex than separate implementations.

---

## 5. Risks and Tradeoffs

### 5.1 Risks of Merging

| Risk | Severity | Description |
|------|----------|-------------|
| Performance regression | HIGH | Promise's fast path would be slowed by Fiber's inbox machinery |
| API confusion | HIGH | Users would not understand when to use which methods |
| Binary compatibility | HIGH | Would break existing compiled code |
| Bug surface | MEDIUM | 5x more code in Fiber.Runtime means more places for bugs to hide |
| Maintenance | MEDIUM | Coupling unrelated concepts makes future changes harder |

### 5.2 Benefits of Merging

| Benefit | Value | Description |
|---------|-------|-------------|
| Code reuse | LOW | Only ~50 lines overlap (observer pattern) |
| Conceptual simplicity | LOW | Both represent "async values" but with very different semantics |
| Consistent API | LOW | Already consistent: both have `await`, `poll`, `id` |

### 5.3 Tradeoff Summary

| Factor | Merge | Don't Merge |
|--------|-------|-------------|
| Performance | ❌ Regression | ✅ Preserved |
| API clarity | ❌ Confusion | ✅ Clear separation |
| Code size | ❌ +1000+ lines | ✅ ~400 lines total |
| Maintenance | ❌ Coupled | ✅ Decoupled |
| Migration effort | ❌ High | ✅ None |

---

## 6. Conclusion

**Do NOT merge Fiber.Runtime and Promise.**

The overlap is superficial (observer pattern, single completion, async wait). The fundamental difference—Promise is a data structure, Fiber.Runtime is an actor—means any merged implementation would be more complex than the sum of its parts.

### Alternative Improvements

If code reuse is desired, consider:

1. **Extract common trait** (non-breaking):
   ```scala
   private[zio] trait Awaitable[+E, +A] {
     def awaitUnsafe(implicit trace: Trace): UIO[Exit[E, A]]
     def pollUnsafe(implicit unsafe: Unsafe): Option[Exit[E, A]]
   }
   ```

2. **Share observer list implementation**:
   - Promise uses `Link` linked list for waiters
   - Fiber.Runtime uses `List[Exit[E, A] => Unit]`
   - Could extract a `WaiterList[A]` utility class

3. **Documentation**: Add cross-references in ScalaDoc explaining the relationship between Promise and Fiber.Runtime.

---

## Appendix A: File References

| Type | File Path | Lines |
|------|-----------|-------|
| Promise | `core/shared/src/main/scala/zio/Promise.scala` | 347 |
| Fiber.Runtime impl | `core/shared/src/main/scala/zio/internal/FiberRuntime.scala` | 1754 |
| Fiber trait | `core/shared/src/main/scala/zio/Fiber.scala` | 1077 |

## Appendix B: Key Code Sections

### Promise.await (lines 47-72)
```scala
def await(implicit trace: Trace): IO[E, A] =
  ZIO.suspendSucceed {
    state.get match {
      case Done(value) => value
      case pending =>
        ZIO.asyncInterrupt[Any, E, A](
          k => {
            @annotation.tailrec
            def loop(current: State[E, A]): Unit =
              current match {
                case pending: Pending[?, ?] =>
                  if (state.compareAndSet(pending, pending.add(k))) ()
                  else loop(state.get)
                case Done(value) => k(value)
              }
            loop(pending)
            Left(ZIO.succeed(state.updateAndGet {
              case pending: Pending[?, ?] => pending.remove(k)
              case completed              => completed
            }))
          },
          blockingOn
        )
    }
  }
```

### FiberRuntime.await (lines 69-85)
```scala
def await(implicit trace: Trace): UIO[Exit[E, A]] =
  ZIO.suspendSucceed(awaitUnsafe)

@inline
private[this] def awaitUnsafe(implicit trace: Trace): UIO[Exit[E, A]] = {
  val exitValue = self._exitValue
  if (exitValue ne null) Exit.succeed(exitValue)
  else
    ZIO.asyncInterrupt[Any, Nothing, Exit[E, A]](
      { k =>
        val cb = (exit: Exit[_, _]) => k(Exit.Success(exit.asInstanceOf[Exit[E, A]]))
        unsafe.addObserver(cb)(Unsafe)
        Left(ZIO.succeed(unsafe.removeObserver(cb)(Unsafe)))
      },
      id
    )
}
```

### Promise.completeWith (lines 195-209)
```scala
def completeWith(io: IO[E, A])(implicit unsafe: Unsafe): Boolean = {
  @annotation.tailrec
  def loop(): Boolean =
    state.get match {
      case pending: Pending[?, ?] =>
        if (state.compareAndSet(pending, Done(io))) {
          pending.complete(io)
          true
        } else {
          loop()
        }
      case _ => false
    }
  loop()
}
```

### FiberRuntime.setExitValue (lines 1390-1448)
```scala
private def setExitValue(e: Exit[E, A]): Unit = {
  _exitValue = e
  // ... metrics, logging ...
  val obs = observers
  if (obs ne Nil) {
    val it = obs.reverseIterator
    while (it.hasNext) {
      it.next().apply(e)
    }
    observers = Nil
  }
}
```
