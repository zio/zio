---
id: getting-started-threadlocal-bridge
title: "Getting Started with ThreadLocalBridge"
description: "Learn to synchronize ZIO fiber-local state with Java ThreadLocal variables for seamless Java library interoperability."
keywords:
  - ThreadLocalBridge
  - ThreadLocal
  - "Java interoperability"
  - FiberRef
  - "Fiber context"
  - "Context synchronization"
---

Welcome to this tutorial on **ThreadLocalBridge**, a powerful tool for integrating ZIO with Java code that relies on `ThreadLocal` variables. This tutorial is for ZIO developers who need to interoperate with legacy Java libraries or frameworks that use `ThreadLocal` for storing context — like SLF4J, OpenTelemetry, or Spring Security.

You don't need any prior experience with `ThreadLocal` or `ThreadLocalBridge` to follow along — we'll cover everything step by step.

## Learning Objectives

By the end of this tutorial, you will understand:

- Why Java's `ThreadLocal` breaks under ZIO's fiber-based concurrency model
- What `ThreadLocalBridge` is and why you need it for Java interop
- How to synchronize a `FiberRef` with a `ThreadLocal` using `ThreadLocalBridge#makeFiberRef`
- The two critical requirements for using `ThreadLocalBridge` correctly (`Scope` and the service layer)
- Common mistakes that beginners make and how to avoid them
- How to integrate `ThreadLocalBridge` with real-world libraries like SLF4J

We'll learn these concepts through hands-on examples, starting with a simple request ID tracker and building up to real-world library integration.

Read this tutorial from top to bottom — each section builds on the previous one.

## Background: The Big Picture

Before we dive into `ThreadLocalBridge`, let's understand the problem it solves.

Java's `ThreadLocal` is a classic pattern for storing per-thread context — values that belong to a single thread and are accessible from anywhere in that thread's call stack without explicit parameter passing. Libraries like SLF4J use it to store request IDs, user information, or trace context:

```
Thread 1  → ThreadLocal contains request-id-123
Thread 2  → ThreadLocal contains request-id-456
Thread 3  → ThreadLocal contains request-id-789
```

This works perfectly when your application runs on traditional threads — one thread per logical task.

ZIO's fiber model is different. ZIO applications run **fibers** — lightweight logical tasks — on a **shared thread pool**. A single fiber may suspend on one thread and resume on a different thread minutes later. This breaks the `ThreadLocal` assumption:

```
Fiber 1: starts on Thread A (ThreadLocal = request-id-123)
         ↓ suspends
         later resumes on Thread C
         ↓ ThreadLocal now contains request-id-789 (wrong!)
```

When Fiber 1 resumes on a different thread, the `ThreadLocal` it sees doesn't belong to Fiber 1 anymore — it belongs to whatever task was running on that thread. Legacy Java libraries that read from `ThreadLocal` will see the wrong context.

`ThreadLocalBridge` solves this by automatically **synchronizing** your fiber-local state with the `ThreadLocal` whenever your fiber moves between threads or when your state changes. The bridge ensures that no matter which thread your fiber is running on, the `ThreadLocal` always contains your fiber's correct value.

## 1. Understanding ThreadLocal in Java

To use `ThreadLocalBridge` effectively, you need to understand what `ThreadLocal` is and how it works.

A `ThreadLocal` in Java stores a separate value for each thread. When you call `threadLocal.get()`, you receive the value that was stored for the **current** thread. If you call `threadLocal.set(value)`, you're storing that value only for the current thread — other threads don't see it.

Here's how `ThreadLocal` isolates values per thread — when you set a value in one thread, other threads still see their own independent values:

```scala mdoc
import scala.io.StdIn

object ThreadLocalDemo {
  // Simulate a simple ThreadLocal string storage
  val appContext = new ThreadLocal[String] {
    override def initialValue() = "default-context"
  }

  // In the main thread
  def main(): Unit = {
    appContext.set("main-thread-context")
    println(s"Main thread sees: ${appContext.get()}")

    // In a new thread, the ThreadLocal is isolated
    val worker = new Thread(() => {
      println(s"Worker thread sees: ${appContext.get()}")
      appContext.set("worker-context")
      println(s"Worker thread after set: ${appContext.get()}")
    })

    worker.start()
    worker.join()

    // Back in main thread, the ThreadLocal still has our original value
    println(s"Main thread still sees: ${appContext.get()}")
  }
}

ThreadLocalDemo.main()
```

The output shows that:
- When the worker thread reads the `ThreadLocal`, it gets the initial value (since no one set it for that thread)
- Each thread has its own isolated storage in the `ThreadLocal`
- Changes in one thread don't affect other threads

This isolation is exactly what makes `ThreadLocal` useful for context in traditional multi-threaded programs — your context follows your thread.

## 2. How ZIO Fibers Work Differently

In traditional Java applications, one thread ≈ one task. So thread-local context works well. ZIO is different.

A ZIO fiber is a **logical task**, but it doesn't run on a fixed thread. Instead, fibers are scheduled by ZIO's runtime and multiplexed across a **thread pool**. This is what makes ZIO lightweight and efficient — you can have thousands of concurrent fibers on just a handful of threads.

Here's a realistic sequence of what can happen to a single fiber:

```
Time 1: Fiber A starts executing on Thread #1
        ThreadLocal.set("fiber-a-context")

Time 2: Fiber A performs an I/O operation (network call)
        → The fiber suspends (yields the thread)
        → Thread #1 is now free to run other work

Time 3: Another fiber (Fiber B) starts on Thread #1
        → It sets ThreadLocal to "fiber-b-context"

Time 4: The I/O completes, Fiber A resumes
        → ZIO scheduler assigns it to Thread #3 (different from #1!)
        → Fiber A reads ThreadLocal
        → It sees "fiber-b-context" (WRONG!)
```

Here's how the problem manifests when a fiber is rescheduled on a different thread and loses its intended context:

```scala mdoc:silent
import zio._

object ProblemDemo {
  // Simulate a Java library that reads from ThreadLocal
  val loggingContext = new ThreadLocal[String] {
    override def initialValue() = "unknown"
  }

  def logWithContext(message: String): ZIO[Any, Nothing, String] =
    ZIO.succeed(s"[${loggingContext.get()}] $message")

  def demonstrateProblem(): ZIO[Any, Nothing, Unit] =
    for {
      _ <- ZIO.succeed(loggingContext.set("request-123"))
      result1 <- logWithContext("Step 1")
      _ <- ZIO.succeed(println(result1))
      
      // After this, we INTEND to keep request-123
      // But if the fiber is scheduled on a different thread,
      // the ThreadLocal might have a different value!
      _ <- ZIO.succeed(loggingContext.set("request-123"))  // Reset to be sure
      result2 <- logWithContext("Step 2")
      _ <- ZIO.succeed(println(result2))
    } yield ()
}
```

The issue is: we have to manually manage the `ThreadLocal` to keep it in sync with our fiber's intended context. This is tedious, error-prone, and doesn't scale. What if your fiber spawns child fibers? What if you have concurrent operations?

This is the problem `ThreadLocalBridge` solves.

## 3. Introducing ThreadLocalBridge

`ThreadLocalBridge` is a **service** in ZIO that automatically keeps your `ThreadLocal` in sync with your fiber-local state. Instead of manually managing `ThreadLocal`, you create a `FiberRef` (which is automatically per-fiber) and tell `ThreadLocalBridge` to synchronize it to a `ThreadLocal`.

The pattern is simple:

1. Create a `ThreadLocal` (the storage that Java libraries expect)
2. Create a `FiberRef` using `ThreadLocalBridge.makeFiberRef` (your ZIO-local state)
3. Provide a **link function** — a callback that syncs the `FiberRef` to the `ThreadLocal`
4. Provide the `ThreadLocalBridge.live` service layer

The link function is invoked automatically by `ThreadLocalBridge` whenever:
- The `FiberRef` value changes (you call `FiberRef#set` or `FiberRef#modify`)
- Your fiber enters a `locally()` scope (updating the `ThreadLocal` with the scoped value)
- Your fiber exits a `locally()` scope (reverting the `ThreadLocal` to the previous value)
- Your fiber suspends (resetting the `ThreadLocal` to its initial value to prevent stale values from persisting on the old thread)
- Your fiber resumes on a new thread (restoring the `ThreadLocal` to your fiber's current value)
- The resource scope exits (cleanup)

Here's the essential pattern for synchronizing a `FiberRef` with a `ThreadLocal`:

```scala mdoc:compile-only
import zio._

object MinimalExample {
  // 1. Create a ThreadLocal
  val threadLocal = new ThreadLocal[String] {
    override def initialValue() = "default"
  }

  // 2. Create and use a fiber-local reference that syncs to ThreadLocal
  val example: ZIO[ThreadLocalBridge, Nothing, Unit] = ZIO.scoped {
    ThreadLocalBridge.makeFiberRef[String]("initial-value") { value =>
      // This function is called automatically whenever the value changes
      // or the fiber moves between threads
      threadLocal.set(value)
    }.flatMap { fiberRef =>
      for {
        _ <- fiberRef.set("new-value")
        // At this point, threadLocal.get() == "new-value" automatically!
      } yield ()
    }
  }

  // 3. Provide the ThreadLocalBridge service
  val result = example.provide(ThreadLocalBridge.live)
}
```

Key observations:
- **`ZIO.scoped`** is required because `ThreadLocalBridge#makeFiberRef` needs to manage cleanup when the scope exits
- **`ThreadLocalBridge.live`** must be in the environment (via `ZIO#provide`)
- The link function receives the current value and can do anything with it

This pattern ensures that no matter when or where your fiber runs, the `ThreadLocal` always contains the correct value.

### How Supervisor Synchronization Works

Under the hood, `ThreadLocalBridge` uses ZIO's supervisor system to detect when fibers suspend and resume. Here's how the synchronization works:

#### When Fiber Suspends

When your fiber suspends (e.g., during an I/O operation):
- The supervisor triggers the `onSuspend` hook
- `ThreadLocalBridge` calls your link function with the **initial value** of the `FiberRef`
- This resets the `ThreadLocal` to its initial value
- **Why?** This prevents stale values from persisting on the old thread when another fiber runs on that thread. If we left the current value in the `ThreadLocal`, the next fiber on that thread would see your fiber's context (incorrect!)

#### When Fiber Resumes

When your fiber resumes (after the I/O completes):
- The supervisor triggers the `onResume` hook
- `ThreadLocalBridge` calls your link function with your fiber's **current value**
- This restores the `ThreadLocal` to the correct value for your fiber on the new thread
- **Why?** This ensures your fiber sees the value it expects, even though it may be running on a different thread than before

Here's a timeline showing how `ThreadLocalBridge` synchronizes the link function across suspend and resume boundaries:
```
Time 1: Fiber A on Thread #1, value = "request-123"
        link("request-123")        // ThreadLocal = "request-123"
        
Time 2: Fiber A suspends for I/O
        link("initial-value")      // ThreadLocal reset to initial
        
Time 3: Fiber B runs on Thread #1
        link("request-456")        // ThreadLocal = "request-456" (Fiber B's value)
        
Time 4: Fiber A resumes on Thread #3
        link("request-123")        // ThreadLocal = "request-123" (Fiber A's current value)
```

This asymmetry—resetting to initial on suspend but restoring the current value on resume—is the key mechanism that makes `ThreadLocalBridge` work correctly with fiber scheduling.

## 4. Your First ThreadLocalBridge Example

Now let's build a complete working example from scratch. We'll create a simple request ID tracker that uses `ThreadLocalBridge` to synchronize a `FiberRef` with a `ThreadLocal`.

This is a complete, self-contained example:

```scala mdoc:silent:reset
import zio._

object SimpleRequestTracker {
  // Step 1: Create a ThreadLocal to store the request ID
  val requestIdStorage = new ThreadLocal[Option[String]] {
    override def initialValue() = None
  }
  
  // Step 2: A helper function that reads from the ThreadLocal
  // (simulating a logging library that does the same)
  def getRequestId(): String = 
    requestIdStorage.get().getOrElse("no-request")
  
  // Step 3: The main example
  def trackRequest(): ZIO[ThreadLocalBridge, Nothing, Unit] = 
    ZIO.scoped {
      // Create a FiberRef that syncs to the ThreadLocal
      ThreadLocalBridge.makeFiberRef[String]("request-default") { requestId =>
        requestIdStorage.set(Some(requestId))
      }.flatMap { requestIdRef =>
        for {
          // Initially, the ThreadLocal has the initial value
          id1 <- ZIO.succeed(getRequestId())
          _ <- ZIO.succeed(println(s"Initial request ID: $id1"))
          
          // Change the FiberRef value
          _ <- requestIdRef.set("request-001")
          
          // The ThreadLocal is automatically updated!
          id2 <- ZIO.succeed(getRequestId())
          _ <- ZIO.succeed(println(s"After set to request-001: $id2"))
          
          // Even nested scopes work correctly
          _ <- requestIdRef.locally("request-002") {
            val id3 = getRequestId()
            ZIO.succeed(println(s"Inside locally scoped request-002: $id3"))
          }
          
          // Back to the original value after locally() exits
          id4 <- ZIO.succeed(getRequestId())
          _ <- ZIO.succeed(println(s"After locally() exits: $id4"))
        } yield ()
      }
    }
}
```

Now let's run it and see the output:

```scala mdoc
import zio._

SimpleRequestTracker.trackRequest().provide(ThreadLocalBridge.live)
```

Line-by-line explanation:
- `ThreadLocalBridge.makeFiberRef[String]("request-default")` creates a fiber-local string reference with initial value `"request-default"`
- `{ requestId => requestIdStorage.set(Some(requestId)) }` is the link function — whenever the fiber-ref changes, we update the ThreadLocal
- `.flatMap { requestIdRef => ... }` receives the synchronized fiber-ref
- `requestIdRef.set("request-001")` changes the value — this automatically invokes the link function, updating the ThreadLocal
- `requestIdRef.locally("request-002") { ... }` creates a scoped context where the value is temporarily changed to `"request-002"` — when the scope exits, it automatically reverts
- `getRequestId()` always reads the correct value from the ThreadLocal

**Implementation Note:** Under the hood, `FiberRef#set` delegates to `FiberRef#modify`, which invokes the link function whenever you call `FiberRef#set`. This is how `ThreadLocalBridge` achieves automatic synchronization — the `TrackingFiberRef` wrapper overrides `TrackingFiberRef#modify` to call your link function whenever the value changes, and since `FiberRef#set` uses `FiberRef#modify` internally, the link function calls automatically for both operations.

This demonstrates the core feature: **automatic synchronization**. You change the fiber-ref, and the `ThreadLocal` updates without you explicitly calling `set()` on it.

## Exceptions in the Link Function

If your link function throws an exception, the exception propagates and cancels the entire effect. This is usually not what you want. Always handle exceptions gracefully:

```scala mdoc:compile-only
import zio._

object ExceptionHandlingExample {
  val threadLocal = new ThreadLocal[String] {
    override def initialValue() = "default"
  }

  // ❌ Unsafe link function that might throw
  val unsafe: ZIO[ThreadLocalBridge, Nothing, Unit] = 
    ZIO.scoped {
      ThreadLocalBridge.makeFiberRef[String]("initial") { value =>
        if (value.length > 100) {
          throw new IllegalArgumentException("Value too long!")
        }
        threadLocal.set(value)
      }.flatMap { fiberRef =>
        fiberRef.set("a very long string that might exceed 100 characters...")
      }
    }

  // ✅ Safe link function that handles errors gracefully
  val safe: ZIO[ThreadLocalBridge, Nothing, Unit] = 
    ZIO.scoped {
      ThreadLocalBridge.makeFiberRef[String]("initial") { value =>
        try {
          if (value.length > 100) {
            // Log the error but don't crash
            println(s"Warning: value too long, skipping ThreadLocal update")
          } else {
            threadLocal.set(value)
          }
        } catch {
          case e: Exception =>
            println(s"Error in link function: ${e.getMessage}")
            // Continue gracefully
        }
      }.flatMap { fiberRef =>
        fiberRef.set("a very long string...")
      }
    }
}
```

The lesson: treat the link function as a critical piece of your application. If syncing to the `ThreadLocal` fails, you probably want to log it and continue, not crash the entire effect.

## Putting It Together: Real-World SLF4J Integration

Now let's see how to integrate `ThreadLocalBridge` with a real library: SLF4J, a popular logging framework.

SLF4J provides the **MDC (Mapped Diagnostic Context)** — a map stored in `ThreadLocal` where you can put key-value pairs (like request IDs, user names, etc.) that are automatically included in every log message.

Here's how to integrate `ThreadLocalBridge` with SLF4J's MDC (Mapped Diagnostic Context) to automatically synchronize request IDs across concurrent fibers:

```scala mdoc:silent:reset
import zio._
import org.slf4j.MDC

object SLF4JExample {
  
  // Helper function to log a message with the current request ID from MDC
  def logMessage(message: String): ZIO[Any, Nothing, Unit] = 
    ZIO.succeed {
      val requestId = Option(MDC.get("requestId")).getOrElse("unknown")
      println(s"[requestId=$requestId] $message")
    }
  
  // Example workflow - process a request with a specific request ID
  def processRequest(requestId: String): ZIO[ThreadLocalBridge, Nothing, Unit] =
    ZIO.scoped {
      ThreadLocalBridge.makeFiberRef[String](requestId) { id =>
        try {
          MDC.put("requestId", id)
        } catch {
          case e: Exception =>
            println(s"Warning: Could not update MDC: ${e.getMessage}")
        }
      }.flatMap { _ =>
        for {
          _ <- logMessage("Request received")
          _ <- logMessage("Processing step 1")
          _ <- logMessage("Processing step 2")
          _ <- logMessage("Request completed")
        } yield ()
      }
    }
  
  // Simulate handling two concurrent requests
  def handleConcurrentRequests(): ZIO[ThreadLocalBridge, Nothing, Unit] =
    for {
      fiber1 <- processRequest("request-001").fork
      fiber2 <- processRequest("request-002").fork
      _ <- fiber1.join
      _ <- fiber2.join
    } yield ()
}
```

Here's how to use the SLF4J example in your application:

```scala mdoc:compile-only
import zio._

SLF4JExample.handleConcurrentRequests().provide(ThreadLocalBridge.live)
```

This example shows:
- `processRequest` is a helper function that processes a request with synchronized request ID tracking
- Each request gets its own fiber-local request ID
- Even though both requests run concurrently, each one has its own request ID in MDC
- SLF4J logging automatically includes the correct request ID for each request
- When either fiber suspends/resumes on different threads, the MDC is automatically kept in sync

This is the power of `ThreadLocalBridge`: seamless integration with legacy Java libraries without manual thread-local management.

## What You've Learned

Congratulations! You've completed the tutorial on ThreadLocalBridge. Here's what you now understand:

- **The core problem**: ZIO fibers are scheduled across multiple threads, so plain `ThreadLocal` doesn't work for fiber-local context — the value you set on one thread might be read by a different thread when your fiber resumes.
- **Why ThreadLocalBridge exists**: It automatically synchronizes your fiber-local state (stored in a `FiberRef`) with a `ThreadLocal` that Java libraries expect, ensuring correct values no matter which thread your fiber is running on.
- **The pattern**: Create a `ThreadLocal`, wrap it with `ThreadLocalBridge.makeFiberRef`, provide a link function to sync the values, and call `ZIO#provide(ThreadLocalBridge.live)` to run the effect.
- **Two critical requirements**: You must use `ZIO.scoped` for proper cleanup, and you must provide the `ThreadLocalBridge.live` service.
- **Real-world integration**: Libraries like SLF4J MDC, OpenTelemetry, and Spring Security all use `ThreadLocal` — `ThreadLocalBridge` lets you use them seamlessly in ZIO applications.

You now have a solid foundation in `ThreadLocalBridge` and can integrate it into your ZIO applications to work with Java libraries that rely on thread-local context.

## Where to Go Next

Now that you understand the basics of `ThreadLocalBridge`, here's what you can explore next:

- **Ready to dive deeper into the API?** Read the comprehensive [ThreadLocalBridge reference documentation](../../reference/state-management/threadlocal-bridge.md) for complete method signatures, advanced patterns, and in-depth explanations of supervisor integration.
- **Want to understand fiber-local state better?** Check out the [FiberRef reference documentation](../../reference/state-management/fiberref.md) to learn more about fiber-local storage — `ThreadLocalBridge` builds on these concepts.
- **Interested in resource management?** Explore the [Scope documentation](../../reference/resource/scope.md) to understand how scopes work and why they're essential for cleanup.
- **Need to integrate with other Java libraries?** Look at the [Java Interoperability guide](../interop/with-java.md) for more patterns on working with Java code in ZIO applications.

Good luck integrating ZIO with your Java dependencies! 🚀

## Running the Examples

All examples in this tutorial have corresponding runnable Scala files in the `zio-examples` module. Run them in order to progressively build your understanding of `ThreadLocalBridge` in practice.

### Concept1Example — Understanding ThreadLocal Limitations with ZIO Fibers

A forked fiber runs on a different thread and sees the `ThreadLocal`'s initial value instead of what the main fiber set — this is the problem `ThreadLocalBridge` solves.

<details>
  <summary>zio-examples/threadlocal-bridge/src/main/scala/threadlocalbridge/Concept1Example.scala</summary>

```scala title="zio-examples/threadlocal-bridge/src/main/scala/threadlocalbridge/Concept1Example.scala" showLineNumbers
@THREADLOCAL_CONCEPT1_SOURCE@
```

</details>

Observe the broken `ThreadLocal` propagation firsthand:

```bash
sbt "threadlocal-bridge/runMain threadlocalbridge.Concept1Example"
```

### Concept2Example — Introducing ThreadLocalBridge

`ThreadLocalBridge.makeFiberRef` wraps a `ThreadLocal` in a `FiberRef` that stays in sync via a link function. `FiberRef.locally` scopes a value to a block and restores it afterwards.

<details>
  <summary>zio-examples/threadlocal-bridge/src/main/scala/threadlocalbridge/Concept2Example.scala</summary>

```scala title="zio-examples/threadlocal-bridge/src/main/scala/threadlocalbridge/Concept2Example.scala" showLineNumbers
@THREADLOCAL_CONCEPT2_SOURCE@
```

</details>

See correct context propagation with `ThreadLocalBridge` in action:

```bash
sbt "threadlocal-bridge/runMain threadlocalbridge.Concept2Example"
```

### Concept3Example — Fiber Isolation with ThreadLocalBridge

Covers two scenarios: a nested fiber chain where each level scopes its own value, and parallel fibers running simultaneously with isolated contexts — no cross-fiber bleed.

<details>
  <summary>zio-examples/threadlocal-bridge/src/main/scala/threadlocalbridge/Concept3Example.scala</summary>

```scala title="zio-examples/threadlocal-bridge/src/main/scala/threadlocalbridge/Concept3Example.scala" showLineNumbers
@THREADLOCAL_CONCEPT3_SOURCE@
```

</details>

Verify fiber isolation across nested and parallel hierarchies:

```bash
sbt "threadlocal-bridge/runMain threadlocalbridge.Concept3Example"
```

### CompleteExample — Request Context Propagation with ThreadLocalBridge

Three concurrent requests each carrying a `RequestContext` (request ID, user, correlation ID) processed in parallel across database queries, API calls, and logging — all context-isolated with no manual threading.

<details>
  <summary>zio-examples/threadlocal-bridge/src/main/scala/threadlocalbridge/CompleteExample.scala</summary>

```scala title="zio-examples/threadlocal-bridge/src/main/scala/threadlocalbridge/CompleteExample.scala" showLineNumbers
@THREADLOCAL_COMPLETE_SOURCE@
```

</details>

Run the complete end-to-end example:

```bash
sbt "threadlocal-bridge/runMain threadlocalbridge.CompleteExample"
```
