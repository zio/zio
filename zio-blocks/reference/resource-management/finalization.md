# Finalization

> `Finalization` is the result of running all finalizers in a scope, collecting any errors that occurred during cleanup:

`Finalization` is the result of running all finalizers in a scope, collecting any errors that occurred during cleanup:

```scala

abstract class Finalization(val errors: Chunk[Throwable]) {
  def isEmpty: Boolean
  def nonEmpty: Boolean
  def orThrow(): Unit
  def suppress(initial: Throwable): Throwable
}
```

When a scope closes, each registered finalizer runs in LIFO order. If any finalizer throws an exception, that error is caught and collected into a `Finalization`. This type ensures that all finalizers run even if some fail, and allows the caller to decide how to handle accumulated errors.

`Finalization` collects errors from finalizers in a `Chunk[Throwable]`. The first error in the chunk corresponds to the head of the chunk (the first finalizer that failed in LIFO execution order).

## Core Methods

The following four methods allow you to inspect and handle errors from finalization:

### `Finalization#isEmpty`

Returns `true` if no finalizer errors were collected:

```scala

abstract class Finalization(val errors: Chunk[Throwable]) {
  def isEmpty: Boolean
}
```

Here's an example of checking if finalization succeeded:

```scala

Scope.global.scoped { scope =>

  $(open()) { openScope =>

    defer {
      println("Cleanup")
    }
    val fin = openScope.close()
    if (fin.isEmpty) println("No errors") else println("Errors occurred")
  }
}
```

### `Finalization#nonEmpty`

Returns `true` if at least one finalizer error was collected:

```scala

abstract class Finalization(val errors: Chunk[Throwable]) {
  def nonEmpty: Boolean
}
```

Here's an example of checking for errors:

```scala

Scope.global.scoped { scope =>

  $(open()) { openScope =>

    defer {
      throw new Exception("Cleanup failed")
    }
    val fin = openScope.close()
    if (fin.nonEmpty) {
      println(s"Errors occurred: ${fin.errors.length}")
    }
  }
}
```

### `Finalization#orThrow()`

Throws the first collected error with all remaining errors added as suppressed exceptions. Does nothing if there are no errors:

```scala

abstract class Finalization(val errors: Chunk[Throwable]) {
  def orThrow(): Unit
}
```

The first error corresponds to the head of the chunk (the first finalizer that failed in LIFO execution order). Remaining errors are attached as suppressed exceptions using `addSuppressed()`. Here's an example:

```scala

Scope.global.scoped { scope =>

  $(open()) { openScope =>
    defer { throw Exception("Error 2") }
    defer { throw Exception("Error 1") }
    val fin = openScope.close()
    
    try {
      fin.orThrow()
    } catch {
      case e: Exception =>
        println(s"Primary: ${e.getMessage}")
        e.getSuppressed.foreach(s => println(s"Suppressed: ${s.getMessage}"))
    }
  }
}
```

### `Finalization#suppress(initial)`

Adds all collected finalizer errors as suppressed exceptions to `initial` and returns it. If there are no errors, `initial` is returned unchanged:

```scala

abstract class Finalization(val errors: Chunk[Throwable]) {
  def suppress(initial: Throwable): Throwable
}
```

This is useful when you want to preserve the original error context while attaching cleanup errors. Here's an example:

```scala

Scope.global.scoped { scope =>

  val initialError = Exception("Original error")

  $(open()) { openScope =>
    defer { throw Exception("Cleanup error") }
    val fin = openScope.close()
    
    val combined = fin.suppress(initialError)
    println(s"Primary: ${combined.getMessage}")
    combined.getSuppressed.foreach(s => println(s"Suppressed: ${s.getMessage}"))
  }
}
```

## Error Ordering

Errors in the finalization are ordered by when finalizers ran (in LIFO sequence):

```scala

Scope.global.scoped { scope =>

  $(open()) { openScope =>
    // Registered first, runs last (LIFO)
    defer { throw Exception("Error 1") }

    // Registered second, runs first
    defer { throw Exception("Error 2") }

    // Registered third, runs first
    defer { throw Exception("Error 3") }

    val fin = openScope.close()
    
    // Errors list order: [Error 3, Error 2, Error 1]
    println(s"First error: ${fin.errors.head.getMessage}")
    println(s"Total errors: ${fin.errors.length}")
  }
}
```

## Use Cases

Here are common scenarios where finalization handling is useful:

### Conditional Error Handling

Check if errors occurred and handle them appropriately:

```scala

Scope.global.scoped { scope =>

  $(open()) { openScope =>
    defer {
      println("Cleanup complete")
    }
    val fin = openScope.close()
    
    if (fin.nonEmpty) {
      fin.orThrow()
    } else {
      println("No errors during finalization")
    }
  }
}
```

### Combining Multiple Error Sources

Attach cleanup errors to an existing error:

```scala

def doWork(): Unit = {
  Scope.global.scoped { scope =>

    try {
      throw Exception("Work failed")
    } catch {
      case workError: Exception =>
        $(open()) { openScope =>
          defer { throw Exception("Cleanup failed") }
          val fin = openScope.close()
          val combined = fin.suppress(workError)
          throw combined
        }
    }
    ()  // Return unit on normal path
  }
}

try {
  doWork()
} catch {
  case e: Exception =>
    println(s"Work: ${e.getMessage}")
    e.getSuppressed.foreach(s => println(s"During cleanup: ${s.getMessage}"))
}
```

### Logging All Cleanup Errors

Inspect and log all errors without stopping execution:

```scala

Scope.global.scoped { scope =>

  $(open()) { openScope =>
    defer { throw Exception("Error 1") }
    defer { throw Exception("Error 2") }
    val fin = openScope.close()
    
    if (fin.nonEmpty) {
      println(s"Finalization collected ${fin.errors.length} errors:")
      fin.errors.foreach(e => println(s"  - ${e.getMessage}"))
    }
  }
}
```

## Relationship to Scope

`Finalization` is returned by:

- `Scope.open().close()` — when explicitly closing a scope
- `Scope.global.runFinalizers()` — when running global finalizers on shutdown

## See Also

- [`Scope#defer`](./scope.md) — registers finalizers that produce errors
- [`DeferHandle`](./defer-handle.md) — handle for cancelling finalizers
- [`Finalizer`](./finalizer.md) — the trait for registering cleanup actions
