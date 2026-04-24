# DeferHandle

> `DeferHandle` is a handle returned by `Scope.defer` that allows cancelling a registered finalizer before the scope closes:

`DeferHandle` is a handle returned by `Scope.defer` that allows cancelling a registered finalizer before the scope closes:

```scala
abstract class DeferHandle {
  def cancel(): Unit
}
```

When `Scope#defer(cleanup)` is called, the cleanup action is registered and a `DeferHandle` is returned. This handle can be used to remove that finalizer early, preventing it from running when the scope closes. This is useful when a resource is explicitly released before the scope ends, and running the finalizer again would be unnecessary or harmful.

## Construction

`DeferHandle` is not instantiated directly. Instead, it is created by calling `Scope#defer` with a cleanup action:

```scala

trait Scope {
  def defer(cleanup: => Unit): DeferHandle
}
```

The following example demonstrates creating a `DeferHandle`:

```scala

Scope.global.scoped { scope =>

  val handle = defer {
    println("This cleanup will run when scope closes, unless cancelled")
  }

  // The handle can now be used to cancel the cleanup
  handle.cancel()
}
```

## Core Operations

The `DeferHandle#cancel` method removes the registered finalizer so it will not run when the scope closes:

```scala
trait DeferHandle {
  def cancel(): Unit
}
```

This method is:

- **Thread-safe**: Can be called from any thread without synchronization
- **Idempotent**: Calling it multiple times has the same effect as calling once

If the scope has already closed (and the finalizer has already run or been discarded), calling `DeferHandle#cancel` is a no-op. In the following example, we register a cleanup action, then cancel it before the scope closes:

```scala

Scope.global.scoped { scope =>

  val buffer = allocate(ByteArrayOutputStream())
  val closeHandle = defer {
    println("Auto-closing buffer")
    $(buffer)(_.close())
  }

  // Manually close the buffer
  $(buffer)(_.close())

  // Cancel the automatic finalizer since we already closed it
  closeHandle.cancel()
}
```

## Use Cases

`DeferHandle` is useful in several common scenarios:

### Preventing Duplicate Cleanup

When a resource is explicitly released before the scope ends, cancel the automatic finalizer to avoid duplicate cleanup:

```scala

val result = Scope.global.scoped { scope =>

  val buffer = allocate(ByteArrayOutputStream())

  val finalizeHandle = defer {
    println(s"Finalizer running, buffer closing")
    $(buffer)(_.close())
  }

  // Explicit cleanup
  $(buffer) { buf =>
    buf.write("data".getBytes)
    println(s"Manual use: buffer has ${buf.size()} bytes")
    buf.close()
  }

  // Cancel the automatic finalizer
  finalizeHandle.cancel()
  
  "done"
}
```

### Conditional Cleanup

Cancel finalizers based on runtime conditions:

```scala

def acquireResource(shouldCleanup: Boolean) = Scope.global.scoped { scope =>

  val resource = "important resource"
  val handle = scope.defer {
    println("Cleaning up resource")
  }

  if (!shouldCleanup) {
    handle.cancel()
    println("Cleanup disabled")
  }

  resource
}

acquireResource(shouldCleanup = false)
acquireResource(shouldCleanup = true)
```

### Transferring Ownership

When transferring a resource to external management, cancel its finalizer so the external system can control cleanup:

```scala

val result = Scope.global.scoped { scope =>

  val stream = allocate(ByteArrayInputStream("data".getBytes))
  val handle = defer {
    println("Scope finalizer would close stream")
    $(stream)(_.close())
  }

  // Transfer ownership to external manager
  // (In real code, this might pass to a thread pool or async framework)
  handle.cancel()  // Let the manager handle cleanup
  
  "transferred"
}
```

## Noop Handle

When `defer()` is called on an already-closed scope, a no-op handle is returned:

```scala

Scope.global.scoped { scope =>

  val handle = scope.defer {
    println("This will run when scope closes")
  }

  // Subsequent calls to cancel() remove the finalizer
  handle.cancel()

  println("Finalizer has been cancelled")
}
```

## Thread Safety

`DeferHandle` is thread-safe. Multiple threads can call `cancel()` on the same handle without external synchronization:

```scala

val result = Scope.global.scoped { scope =>

  val handle = defer {
    println("Finalizer")
  }

  // Use a thread pool to simulate concurrent access
  val executor = Executors.newFixedThreadPool(5)
  val latch = new CountDownLatch(5)

  (1 to 5).foreach { i =>
    executor.submit(new Runnable {
      def run(): Unit = {
        handle.cancel()
        println(s"Thread $i cancelled")
        latch.countDown()
      }
    })
  }

  // Wait for all threads to finish
  latch.await()
  executor.shutdown()
  
  "completed"
}
```

## See Also

- [`Scope#defer`](./scope.md#registering-finalizers) — the method that returns a `DeferHandle`
- [`Finalizer`](./finalizer.md) — the trait defining `Finalizer#defer`
- [`Finalization`](./finalization.md) — the result of running all finalizers

## Integration

`DeferHandle` is part of ZIO Blocks' resource management system. It works directly with:

- **[`Scope`](./scope.md)** — The primary way to create a `DeferHandle` is via `Scope#defer`. A scope manages multiple finalizers and runs them all when the scope closes. `DeferHandle` allows selective cancellation of individual finalizers before that happens.

- **[`Finalizer`](./finalizer.md)** — `Finalizer` defines the `Finalizer#defer` operation that returns a `DeferHandle`. It abstracts the concept of registering cleanup actions.

- **[`Finalization`](./finalization.md)** — When a scope closes, it runs all registered finalizers. A cancelled `DeferHandle` removes its associated finalizer from this process.

Together, these types form the foundation of compile-time resource safety in ZIO Blocks, allowing you to manage resource lifecycles with certainty that cleanup will occur exactly when needed.
