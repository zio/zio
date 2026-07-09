# ThreadLocalBridge

> `ThreadLocalBridge` is a **service for synchronizing ZIO fiber-local state with Java `ThreadLocal` storage**. It enables seamless interoperability with legacy Java code and external libraries that rely on thread-local variables, while maintaining full compatibility with ZIO's fiber-based concurrency model.

`ThreadLocalBridge` is a **service for synchronizing ZIO fiber-local state with Java `ThreadLocal` storage**. It enables seamless interoperability with legacy Java code and external libraries that rely on thread-local variables, while maintaining full compatibility with ZIO's fiber-based concurrency model.

`ThreadLocalBridge`:
- Automatically synchronizes a `FiberRef` value with a `ThreadLocal` whenever the fiber-local state changes or when fibers suspend/resume on different threads
- Ensures cleanup by resetting the `ThreadLocal` to its initial value when the resource scope exits
- Provides transparent value propagation to non-ZIO code that expects thread-local state
- Integrates seamlessly with ZIO's supervisor-based fiber lifecycle hooks

The following shows the `ThreadLocalBridge` API:

```scala

sealed trait ThreadLocalBridge {
  def makeFiberRef[A](initialValue: A)(link: A => Unit): ZIO[Scope, Nothing, FiberRef[A]]
}

object ThreadLocalBridge {
  def makeFiberRef[A](initialValue: A)(link: A => Unit): ZIO[Scope with ThreadLocalBridge, Nothing, FiberRef[A]] = ???
  val live: ZLayer[Any, Nothing, ThreadLocalBridge] = ???
}
```

## Motivation

Java code and many third-party libraries use `ThreadLocal` variables to store context that needs to be accessible from any point in a call stack without explicit parameter passing. Examples include:

- **Logging frameworks** (SLF4J MDC, log4j context) — store request IDs, user identifiers, or correlation tokens
- **Tracing systems** (OpenTelemetry, Jaeger) — propagate span and trace context
- **Security frameworks** (Spring Security) — store authentication and authorization information
- **Legacy code** — code you cannot refactor but must integrate with ZIO

The challenge arises from the fundamental difference between ZIO's fiber model and Java's thread model. When running ZIO effects on the JVM, fibers are multiplexed across a thread pool and may suspend and resume on different threads. This breaks the assumption that a single `ThreadLocal` value is associated with a single logical task.

`ThreadLocalBridge` solves this by:

1. Using ZIO's `FiberRef` to maintain fiber-local state (automatically isolated per fiber)
2. Automatically synchronizing that state to a `ThreadLocal` whenever the fiber-local value changes
3. Ensuring the `ThreadLocal` reflects the correct value even when the fiber suspends and resumes on a different thread
4. Cleaning up the `ThreadLocal` when the scope exits to prevent leaks

## Quick Showcase

In this example, we create a fiber-local request ID and automatically synchronize it to a `ThreadLocal` so that a logging function can access it:

```scala

// Simulating a logging framework that reads from ThreadLocal
val threadLocal = new ThreadLocal[Option[String]] {
  override def initialValue() = None
}
// threadLocal: ThreadLocal[Option[String]]{def initialValue(): None.type} = repl.MdocSession$MdocApp1$$anon$1@40012307

def logMessage(message: String): ZIO[Any, Nothing, Unit] =
  ZIO.succeed {
    val requestId = threadLocal.get().getOrElse("unknown")
    println(s"[$requestId] $message")
  }

// Create a fiber-local request ID synchronized with ThreadLocal
val example = ZIO.scoped {
  ThreadLocalBridge.makeFiberRef[String]("default-id")(requestId =>
    threadLocal.set(Some(requestId))
  ).flatMap { requestIdRef =>
    for {
      _ <- logMessage("Request started")
      _ <- requestIdRef.set("request-123")
      _ <- logMessage("After setting request ID")
      _ <- requestIdRef.locally("request-456") {
        logMessage("Inside locally scoped ID")
      }
      _ <- logMessage("After local scope")
    } yield ()
  }
}.provide(ThreadLocalBridge.live)
// example: ZIO[Any, Nothing, Unit] = FlatMap(
//   trace = "repl.MdocSession.MdocApp1.example(threadlocal-bridge.md:65)",
//   first = Sync(
//     trace = "repl.MdocSession.MdocApp1.example(threadlocal-bridge.md:65)",
//     eval = zio.Scope$$$Lambda$19576/0x00007effea484ca8@b7eaa86
//   ),
//   successK = zio.ZIO$$$Lambda$19577/0x00007effea484f60@697ec37b
// )
```

The output shows how the `ThreadLocal` reflects the current fiber-local value at each step, even though the underlying thread may change between effects.

## Construction

This section covers the main ways to create and configure synchronized `FiberRef` instances using `ThreadLocalBridge`. The `ThreadLocalBridge#makeFiberRef` method enables automatic synchronization with Java's `ThreadLocal` storage, and the `ThreadLocalBridge#live` layer provides the runtime infrastructure needed to manage the synchronization.

### Creating a Synchronized FiberRef

The primary way to create a synchronized `FiberRef` is using `ThreadLocalBridge#makeFiberRef` with a `ThreadLocal` and a link function. This method must be called within a `ZIO.scoped` context since it requires resource cleanup.

`ThreadLocalBridge#makeFiberRef` creates a new `FiberRef` that synchronizes its value with a `ThreadLocal` via a user-supplied callback function. Its signature is:

```scala

trait ThreadLocalBridge {
  def makeFiberRef[A](initialValue: A)(link: A => Unit): ZIO[Scope, Nothing, FiberRef[A]]
}
```

The method takes two parameter lists:

1. **Initial value** — the starting value for the `FiberRef`
2. **Link function** — a callback that is invoked whenever the `FiberRef` value changes

The method returns a `ZIO` that requires a `Scope` (for resource cleanup) and the `ThreadLocalBridge` service. It cannot fail (error type is `Nothing`).

These situations trigger the link function:

- **On creation** — `ThreadLocalBridge#makeFiberRef` immediately invokes the link function with the initial value
- **On value modification** — when you invoke `FiberRef#set` or `FiberRef#modify`, the link function receives the new state value (note: `FiberRef#set` delegates to `FiberRef#modify` internally, as explained in the Implementation Note below)
- **On locally scope entry** — when entering a `FiberRef#locally(newValue) { ... }` block, the link function is invoked with the scoped value
- **On locally scope exit** — when exiting the scoped block, the link function is invoked again with the restored value
- **On fiber suspend** — when the fiber suspends and yields control to the scheduler, the supervisor invokes the link function with the **initial value** to reset the `ThreadLocal`, preventing state pollution on the thread being vacated
- **On fiber resume** — when the fiber resumes (possibly on a different thread), the supervisor re-invokes the link function with the **current fiber-local value** to synchronize the `ThreadLocal` to the correct value for this fiber

Below is a complete example showing how to wrap a Java `ThreadLocal` and use it with a logging framework:

```scala

// Step 1: Create a Java ThreadLocal to hold the request ID
val requestIdThreadLocal = new ThreadLocal[Option[String]] {
  override def initialValue() = None
}
// requestIdThreadLocal: ThreadLocal[Option[String]]{def initialValue(): None.type} = repl.MdocSession$MdocApp3$$anon$2@6d002d94

// Step 2: Define a logging function that reads from the ThreadLocal
def logWithRequestId(message: String): ZIO[Any, Nothing, Unit] =
  ZIO.succeed {
    val requestId = requestIdThreadLocal.get().getOrElse("[no request]")
    println(s"$requestId: $message")
  }

// Step 3: Set up the bridge in a scoped context
val program = ZIO.scoped {
  // Create a FiberRef that syncs with the ThreadLocal
  ThreadLocalBridge.makeFiberRef[String]("system") { newId =>
    requestIdThreadLocal.set(Some(newId))
  }.flatMap { requestIdRef =>
    // Now use the requestIdRef normally
    for {
      _ <- logWithRequestId("Starting request processing")
      _ <- requestIdRef.set("user-42")
      _ <- logWithRequestId("User identified")
      _ <- logWithRequestId("Querying database")
      _ <- requestIdRef.set("user-99")
      _ <- logWithRequestId("Switched to different user")
    } yield ()
  }
}.provide(ThreadLocalBridge.live)
// program: ZIO[Any, Nothing, Unit] = FlatMap(
//   trace = "repl.MdocSession.MdocApp3.program(threadlocal-bridge.md:121)",
//   first = Sync(
//     trace = "repl.MdocSession.MdocApp3.program(threadlocal-bridge.md:121)",
//     eval = zio.Scope$$$Lambda$19576/0x00007effea484ca8@b7eaa86
//   ),
//   successK = zio.ZIO$$$Lambda$19577/0x00007effea484f60@3be69f0d
// )
```

### The ThreadLocalBridge.live Layer

`ThreadLocalBridge.live` provides the default implementation of the `ThreadLocalBridge` service. Its signature is:

```scala

object ThreadLocalBridge {
  val live: ZLayer[Any, Nothing, ThreadLocalBridge] = ???
}
```

The `ThreadLocalBridge#live` layer:

- Creates and registers an internal `FiberRefTrackingSupervisor` that supervises all `FiberRef` objects from `ThreadLocalBridge#makeFiberRef`
- Hooks into the fiber suspension and resumption lifecycle to re-invoke the link function when needed
- Ensures that even if a fiber suspends and resumes on a different thread, the `ThreadLocal` value is restored
- Cannot fail and requires no environment dependencies

To use `ThreadLocalBridge` in your application, include `ThreadLocalBridge.live` in your layer composition:

```scala

val requestIdThreadLocal = new ThreadLocal[Option[String]] {
  override def initialValue() = None
}
// requestIdThreadLocal: ThreadLocal[Option[String]]{def initialValue(): None.type} = repl.MdocSession$MdocApp5$$anon$3@530c8560

def example: ZIO[Scope with ThreadLocalBridge, Nothing, Unit] =
  ThreadLocalBridge.makeFiberRef[String]("initial")(id =>
    requestIdThreadLocal.set(Some(id))
  ).flatMap { ref =>
    ZIO.succeed(println(s"FiberRef created: ${requestIdThreadLocal.get()}"))
  }

// Provide the ThreadLocalBridge.live layer
val withBridge = ZIO.scoped(example).provide(ThreadLocalBridge.live)
// withBridge: ZIO[Any, Nothing, Unit] = FlatMap(
//   trace = "repl.MdocSession.MdocApp5.withBridge(threadlocal-bridge.md:163)",
//   first = Sync(
//     trace = "repl.MdocSession.MdocApp5.withBridge(threadlocal-bridge.md:163)",
//     eval = zio.Scope$$$Lambda$19576/0x00007effea484ca8@b7eaa86
//   ),
//   successK = zio.ZIO$$$Lambda$19577/0x00007effea484f60@3bb0ec68
// )
```

## Core Operations

Once you have a `FiberRef[A]` from `ThreadLocalBridge#makeFiberRef`, you can use all standard `FiberRef` operations. The key difference is that mutations are automatically synchronized to the underlying `ThreadLocal` via the link function.

**Important:** The link function executes **synchronously** whenever the fiber-local value changes (via `FiberRef#set`, `FiberRef#modify`, `FiberRef#locally`) or during fiber lifecycle hooks (suspend/resume). This means:
- Exceptions in the link function propagate as defects
- The link function blocks the current effect until it completes
- Keep link functions fast and avoid I/O operations or blocking calls

This synchronous behavior ensures the `ThreadLocal` is always in sync with the fiber-local state, but you should optimize link functions for minimal latency.

### FiberRef#set

`FiberRef#set` atomically replaces the fiber-local value and synchronizes the new value to the `ThreadLocal`. Its signature is:

```scala

trait FiberRef[A] {
  def set(value: A): UIO[Unit]
}
```

The method:

- Atomically replaces the fiber-local value
- Calls the link function with the new value for immediate synchronization
- Returns immediately (non-blocking)
- Does not fail (returns `UIO`)

**Implementation Note:** `FiberRef#set` works correctly with ThreadLocalBridge because it internally delegates to the `FiberRef#modify` method, which is overridden in `TrackingFiberRef` to invoke the link function. This delegation ensures that the `ThreadLocal` is automatically synchronized whenever the fiber-local value changes via `FiberRef#set`.

Setting a value automatically triggers the link function, so the `ThreadLocal` is updated synchronously:

```scala

val threadLocal = new ThreadLocal[Option[String]] {
  override def initialValue() = None
}
// threadLocal: ThreadLocal[Option[String]]{def initialValue(): None.type} = repl.MdocSession$MdocApp7$$anon$4@24bf77c5

val example = ZIO.scoped {
  ThreadLocalBridge.makeFiberRef[String]("initial")(value =>
    threadLocal.set(Some(value))
  ).flatMap { ref =>
    for {
      _ <- ZIO.succeed(println(s"Initial: ${threadLocal.get()}"))
      _ <- ref.set("updated")
      _ <- ZIO.succeed(println(s"After set: ${threadLocal.get()}"))
    } yield ()
  }
}.provide(ThreadLocalBridge.live)
// example: ZIO[Any, Nothing, Unit] = FlatMap(
//   trace = "repl.MdocSession.MdocApp7.example(threadlocal-bridge.md:207)",
//   first = Sync(
//     trace = "repl.MdocSession.MdocApp7.example(threadlocal-bridge.md:207)",
//     eval = zio.Scope$$$Lambda$19576/0x00007effea484ca8@b7eaa86
//   ),
//   successK = zio.ZIO$$$Lambda$19577/0x00007effea484f60@473c2316
// )
```

### FiberRef#modify

`FiberRef#modify` applies a function to the current value and synchronizes the result to the `ThreadLocal`. Its signature is:

```scala

trait FiberRef[A] {
  def modify[B](f: A => (B, A)): UIO[B]
}
```

The method:

- Atomically applies a function `f` to the current value
- The function returns both a result `B` and the new state `A`
- Calls the link function with the new state value
- Returns the result `B`
- Does not fail (returns `UIO`)

This is useful for extracting information while updating the state in one operation:

```scala

val counterThreadLocal = new ThreadLocal[Option[Int]] {
  override def initialValue() = None
}
// counterThreadLocal: ThreadLocal[Option[Int]]{def initialValue(): None.type} = repl.MdocSession$MdocApp9$$anon$5@1d6d81f3

val example = ZIO.scoped {
  ThreadLocalBridge.makeFiberRef[Int](0)(count =>
    counterThreadLocal.set(Some(count))
  ).flatMap { ref =>
    for {
      oldValue <- ref.modify(n => (n, n + 1))
      _ <- ZIO.succeed(println(s"Incremented from $oldValue to ${oldValue + 1}"))
      _ <- ZIO.succeed(println(s"ThreadLocal: ${counterThreadLocal.get()}"))
    } yield ()
  }
}.provide(ThreadLocalBridge.live)
// example: ZIO[Any, Nothing, Unit] = FlatMap(
//   trace = "repl.MdocSession.MdocApp9.example(threadlocal-bridge.md:251)",
//   first = Sync(
//     trace = "repl.MdocSession.MdocApp9.example(threadlocal-bridge.md:251)",
//     eval = zio.Scope$$$Lambda$19576/0x00007effea484ca8@b7eaa86
//   ),
//   successK = zio.ZIO$$$Lambda$19577/0x00007effea484f60@43e2ea3e
// )
```

### FiberRef#locally

`FiberRef#locally` creates a scoped region where the fiber-local value is temporarily replaced. Its signature is:

```scala

trait FiberRef[A] {
  def locally[R, E, B](newValue: A)(body: ZIO[R, E, B]): ZIO[R, E, B]
}
```

The method:

- Temporarily replaces the fiber-local value for the duration of `body`
- Calls the link function with the new value on entry
- Automatically restores the previous value on exit (even if `body` fails or is interrupted)
- Calls the link function with the restored value on exit
- Supports arbitrary nesting

This is useful for scoped context changes, such as temporarily changing the request ID or user context:

```scala

val userIdThreadLocal = new ThreadLocal[Option[String]] {
  override def initialValue() = None
}
// userIdThreadLocal: ThreadLocal[Option[String]]{def initialValue(): None.type} = repl.MdocSession$MdocApp11$$anon$6@2eb6e1e8

def logWithUserId(message: String): ZIO[Any, Nothing, Unit] =
  ZIO.succeed {
    val userId = userIdThreadLocal.get().getOrElse("anonymous")
    println(s"[$userId] $message")
  }

val example = ZIO.scoped {
  ThreadLocalBridge.makeFiberRef[String]("guest")(userId =>
    userIdThreadLocal.set(Some(userId))
  ).flatMap { ref =>
    for {
      _ <- logWithUserId("Starting as guest")
      _ <- ref.locally("alice") {
        for {
          _ <- logWithUserId("Switched to alice")
          _ <- ref.locally("bob") {
            logWithUserId("Nested: switched to bob")
          }
          _ <- logWithUserId("Back to alice")
        } yield ()
      }
      _ <- logWithUserId("Back to guest")
    } yield ()
  }
}.provide(ThreadLocalBridge.live)
// example: ZIO[Any, Nothing, Unit] = FlatMap(
//   trace = "repl.MdocSession.MdocApp11.example(threadlocal-bridge.md:310)",
//   first = Sync(
//     trace = "repl.MdocSession.MdocApp11.example(threadlocal-bridge.md:310)",
//     eval = zio.Scope$$$Lambda$19576/0x00007effea484ca8@b7eaa86
//   ),
//   successK = zio.ZIO$$$Lambda$19577/0x00007effea484f60@255ef058
// )
```

The output demonstrates how `FiberRef#locally` isolates value changes to a specific scope and restores the previous value automatically.

### FiberRef#get

`FiberRef#get` retrieves the current fiber-local value without modifying it. Its signature is:

```scala

trait FiberRef[A] {
  def get: UIO[A]
}
```

The method:

- Returns the current fiber-local value
- Does not invoke the link function
- Returns immediately (non-blocking)
- Does not fail (returns `UIO`)

Reading the current value does not trigger synchronization since the `ThreadLocal` is already at the correct value:

```scala

val threadLocal = new ThreadLocal[Option[String]] {
  override def initialValue() = None
}
// threadLocal: ThreadLocal[Option[String]]{def initialValue(): None.type} = repl.MdocSession$MdocApp13$$anon$7@1180133a

val example = ZIO.scoped {
  ThreadLocalBridge.makeFiberRef[String]("initial")(value =>
    threadLocal.set(Some(value))
  ).flatMap { ref =>
    for {
      current <- ref.get
      _ <- ZIO.succeed(println(s"Current value: $current"))
    } yield ()
  }
}.provide(ThreadLocalBridge.live)
// example: ZIO[Any, Nothing, Unit] = FlatMap(
//   trace = "repl.MdocSession.MdocApp13.example(threadlocal-bridge.md:353)",
//   first = Sync(
//     trace = "repl.MdocSession.MdocApp13.example(threadlocal-bridge.md:353)",
//     eval = zio.Scope$$$Lambda$19576/0x00007effea484ca8@b7eaa86
//   ),
//   successK = zio.ZIO$$$Lambda$19577/0x00007effea484f60@7e62d6e3
// )
```

## Advanced Topics

This section explores advanced features of `ThreadLocalBridge`, including how it handles fiber lifecycle events, forking semantics, parallel execution, error handling, and resource cleanup.

### Fiber Suspension and Resumption

The primary power of `ThreadLocalBridge` is its automatic synchronization when fibers suspend and resume on different threads. This is handled by an internal supervisor that supervises all `FiberRef` objects from `ThreadLocalBridge#makeFiberRef`.

When a fiber suspends (yields control back to the scheduler), the supervisor's `onSuspend` hook is triggered. It invokes the link function with the **initial value** of the `FiberRef`. This resets the `ThreadLocal` to its initial state on the thread being vacated, preventing state pollution—ensuring other fibers that run on the same thread won't see leftover values from the suspended fiber.

When the fiber resumes (typically on a different thread), the supervisor's `onResume` hook is triggered, which re-invokes the link function with the **current fiber-local value**. This synchronizes the `ThreadLocal` to the correct value for this fiber on the new thread.

This dual-phase mechanism ensures that:

- When a fiber suspends, the `ThreadLocal` is cleaned up on the old thread to prevent pollution
- When the fiber resumes on a new thread, the `ThreadLocal` is updated to the fiber's current value
- If a fiber resumes on a different thread where the `ThreadLocal` had a different value, it is correctly updated to the fiber's current value
- Legacy code that relies on `ThreadLocal` always sees the correct value for the current fiber

When you modify the `FiberRef` value, the link function synchronizes the change to the `ThreadLocal` immediately:

```scala

val threadLocal = new ThreadLocal[Option[String]] {
  override def initialValue() = None
}
// threadLocal: ThreadLocal[Option[String]]{def initialValue(): None.type} = repl.MdocSession$MdocApp14$$anon$8@250fad2c

// Helper to read current ThreadLocal value
def checkThreadLocal(message: String): ZIO[Any, Nothing, Unit] = {
  ZIO.succeed {
    val value = threadLocal.get().getOrElse("none")
    println(s"$message: ThreadLocal = $value")
  }
}

val example = ZIO.scoped {
  ThreadLocalBridge.makeFiberRef[String]("initial")(v =>
    threadLocal.set(Some(v))
  ).flatMap { ref =>
    for {
      _ <- checkThreadLocal("After setup")
      _ <- ref.set("value-1")
      _ <- checkThreadLocal("After set to value-1")
      _ <- ref.set("value-2")
      _ <- checkThreadLocal("After set to value-2")
    } yield ()
  }
}.provide(ThreadLocalBridge.live)
// example: ZIO[Any, Nothing, Unit] = FlatMap(
//   trace = "repl.MdocSession.MdocApp14.example(threadlocal-bridge.md:390)",
//   first = Sync(
//     trace = "repl.MdocSession.MdocApp14.example(threadlocal-bridge.md:390)",
//     eval = zio.Scope$$$Lambda$19576/0x00007effea484ca8@b7eaa86
//   ),
//   successK = zio.ZIO$$$Lambda$19577/0x00007effea484f60@7570cabe
// )
```

:::info
The supervisor's `onSuspend` and `onResume` hooks are triggered automatically when fibers suspend and resume on different threads. This synchronization is transparent to your code—the `ThreadLocal` is always kept in sync with the current fiber-local value without explicit action on your part. The exact timing depends on the ZIO runtime scheduler and thread pool configuration.
:::

### Forking and Fiber Inheritance

`FiberRef` uses **copy-on-fork** semantics: when you fork a fiber using `ZIO.fork` or other fiber spawning combinators, the child fiber starts with a **copy of the parent's current value**, not the initial value. ThreadLocalBridge preserves this behavior, ensuring the child's `ThreadLocal` is properly synchronized.

When a child fiber forks from the parent, it inherits the parent's current value, and the `ThreadLocal` reflects this inheritance:

```scala

val threadLocal = new ThreadLocal[Option[String]] {
  override def initialValue() = None
}
// threadLocal: ThreadLocal[Option[String]]{def initialValue(): None.type} = repl.MdocSession$MdocApp15$$anon$9@48969feb

val example = ZIO.scoped {
  ThreadLocalBridge.makeFiberRef[String]("initial")(value =>
    threadLocal.set(Some(value))
  ).flatMap { ref =>
    for {
      _ <- ref.set("parent-value")
      _ <- ZIO.succeed(println(s"Parent: ${threadLocal.get()}"))
      // Fork a child fiber - it inherits parent's current value "parent-value"
      child <- ref.get.flatMap { inherited =>
        ZIO.succeed(println(s"Child inherited: $inherited"))
      }.fork
      _ <- child.join
      // Parent's value is unchanged after child fork
      _ <- ZIO.succeed(println(s"Parent after fork: ${threadLocal.get()}"))
    } yield ()
  }
}.provide(ThreadLocalBridge.live)
// example: ZIO[Any, Nothing, Unit] = FlatMap(
//   trace = "repl.MdocSession.MdocApp15.example(threadlocal-bridge.md:423)",
//   first = Sync(
//     trace = "repl.MdocSession.MdocApp15.example(threadlocal-bridge.md:423)",
//     eval = zio.Scope$$$Lambda$19576/0x00007effea484ca8@b7eaa86
//   ),
//   successK = zio.ZIO$$$Lambda$19577/0x00007effea484f60@1f1ea043
// )
```

The child fiber inherits the parent's current value (`"parent-value"`), not the initial value. Each fiber maintains its own independent copy of the fiber-local state, and the `ThreadLocal` is automatically synchronized via the supervisor's hooks when the child fiber runs on different threads.

### Parallel Execution

When using combinators like `ZIO.collectAllPar` or other parallel composition operators, each fiber maintains its own fiber-local value, and the `ThreadLocal` is properly synchronized as fibers suspend and resume across the thread pool:

```scala

val threadLocal = new ThreadLocal[Option[String]] {
  override def initialValue() = None
}
// threadLocal: ThreadLocal[Option[String]]{def initialValue(): None.type} = repl.MdocSession$MdocApp16$$anon$10@43cfcf25

val example = ZIO.scoped {
  ThreadLocalBridge.makeFiberRef[String]("task")(taskId =>
    threadLocal.set(Some(taskId))
  ).flatMap { ref =>
    ZIO.collectAllPar(
      List(
        ref.locally("task-1") {
          ZIO.succeed(println(s"Task 1: ${threadLocal.get()}"))
        },
        ref.locally("task-2") {
          ZIO.succeed(println(s"Task 2: ${threadLocal.get()}"))
        }
      )
    )
  }
}.provide(ThreadLocalBridge.live)
// example: ZIO[Any, Nothing, List[Unit]] = FlatMap(
//   trace = "repl.MdocSession.MdocApp16.example(threadlocal-bridge.md:455)",
//   first = Sync(
//     trace = "repl.MdocSession.MdocApp16.example(threadlocal-bridge.md:455)",
//     eval = zio.Scope$$$Lambda$19576/0x00007effea484ca8@b7eaa86
//   ),
//   successK = zio.ZIO$$$Lambda$19577/0x00007effea484f60@31a6fd3f
// )
```

Each task sees its own locally-scoped value, even when running in parallel on different threads.

### Error Handling in Link Functions

If the link function throws an exception, it will propagate as a defect (unhandled exception). Use defensive programming to prevent exceptions:

```scala

val threadLocal = new ThreadLocal[Option[String]] {
  override def initialValue() = None
}
// threadLocal: ThreadLocal[Option[String]]{def initialValue(): None.type} = repl.MdocSession$MdocApp17$$anon$11@57542b68

val example = ZIO.scoped {
  // Defensive approach: validate input and avoid potential failures
  ThreadLocalBridge.makeFiberRef[String]("safe")(value => {
    // This implementation is safe because it only performs simple operations
    // without I/O or blocking calls
    if (value != null) {
      threadLocal.set(Some(value))
    }
    // If validation fails, silently skip the update rather than throwing
  }).flatMap { ref =>
    ref.set("updated") *>
    ZIO.succeed(println(s"ThreadLocal: ${threadLocal.get()}"))
  }
}.provide(ThreadLocalBridge.live)
// example: ZIO[Any, Nothing, Unit] = FlatMap(
//   trace = "repl.MdocSession.MdocApp17.example(threadlocal-bridge.md:485)",
//   first = Sync(
//     trace = "repl.MdocSession.MdocApp17.example(threadlocal-bridge.md:485)",
//     eval = zio.Scope$$$Lambda$19576/0x00007effea484ca8@b7eaa86
//   ),
//   successK = zio.ZIO$$$Lambda$19577/0x00007effea484f60@f97454
// )
```

:::warning[Link Function Failures]
Keep link functions **simple and side-effect free**. Do not perform I/O, blocking calls, or side effects. Use defensive programming (validation, null-checks) to prevent exceptions rather than catching them with try-catch. If the link function must handle potentially failing operations, consider moving the logic to a wrapper function outside the link callback.
:::

### Scope Cleanup and Finalizers

When the scope exits (whether due to success, failure, or interruption), the finalizer automatically resets the `ThreadLocal` to its initial value. This prevents `ThreadLocal` leaks and ensures clean state for subsequent code:

```scala

val threadLocal = new ThreadLocal[Option[String]] {
  override def initialValue() = Some("initial")
}
// threadLocal: ThreadLocal[Option[String]]{def initialValue(): Some[String]} = repl.MdocSession$MdocApp18$$anon$12@4337c6dc

val example = {
  val scoped = ZIO.scoped {
    ThreadLocalBridge.makeFiberRef[String]("initial")(value =>
      threadLocal.set(Some(value))
    ).flatMap { ref =>
      ref.set("modified") *> ZIO.succeed(println(s"Inside scope: ${threadLocal.get()}"))
    }
  }

  for {
    _ <- scoped.provide(ThreadLocalBridge.live)
    _ <- ZIO.succeed(println(s"After scope: ${threadLocal.get()}"))
  } yield ()
}
// example: ZIO[Any, Nothing, Unit] = FlatMap(
//   trace = "repl.MdocSession.MdocApp18.example(threadlocal-bridge.md:512)",
//   first = FlatMap(
//     trace = "repl.MdocSession.MdocApp18.example(threadlocal-bridge.md:512)",
//     first = Sync(
//       trace = "repl.MdocSession.MdocApp18.example(threadlocal-bridge.md:512)",
//       eval = zio.Scope$$$Lambda$19576/0x00007effea484ca8@b7eaa86
//     ),
//     successK = zio.ZIO$$$Lambda$19577/0x00007effea484f60@1ce64d03
//   ),
//   successK = <function1>
// )
```

After the scope exits, the `ThreadLocal` is reset to its initial value, preventing accumulated state from leaking into subsequent effects.

:::note
The finalizer runs even if an exception is thrown within the scope or if the effect is interrupted. This guarantees resource cleanup.
:::

## Performance Considerations

- **Supervisor overhead:** The supervisor is only invoked when a fiber suspends or resumes, not on every operation. For most workloads, this overhead is negligible.
- **Link function cost:** The link function executes synchronously on every fiber-local value change. Keep it as fast as possible to avoid performance degradation.
- **Memory overhead:** Each tracked `FiberRef` requires a small amount of memory for tracking metadata (typically a few bytes per `FiberRef`).
- **ThreadLocal access:** Direct `ThreadLocal.set()` and `ThreadLocal.get()` are O(1) operations with minimal overhead.

For performance-critical code, avoid frequent value changes or complex link functions. If you need to batch updates, consider using a mutable container (like a `Map` or `Queue`) as the `FiberRef` value type and updating the `ThreadLocal` less frequently.

## See Also

- **[Getting Started with ThreadLocalBridge](../../guides/tutorials/getting-started-threadlocal-bridge.md)** — A tutorial that teaches how to use `ThreadLocalBridge` for Java interoperability, starting from ThreadLocal basics and building up to real-world library integration with SLF4J MDC.
- **[FiberRef](./fiberref.md)** — The fiber-local reference type that `ThreadLocalBridge` synchronizes with ThreadLocal.
- **[Java Interoperability](../../guides/interop/with-java.md)** — General patterns for interoperating with Java code in ZIO applications.
