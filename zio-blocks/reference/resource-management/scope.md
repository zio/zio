# Scope

> `Scope` is a **compile-time safe resource lifecycle manager** that tags allocated values with a scope-specific type, preventing use-after-close at compile time. Each scope instance has a distinct `$[A]` type that is unique to that scope, making values from different scopes structurally incompatible. The `$` operator macro and `Unscoped` typeclass create multiple layers of compile-time protection, eliminating an entire class of lifetime bugs without runtime overhead.

`Scope` is a **compile-time safe resource lifecycle manager** that tags allocated values with a scope-specific type, preventing use-after-close at compile time. Each scope instance has a distinct `$[A]` type that is unique to that scope, making values from different scopes structurally incompatible. The `$` operator macro and `Unscoped` typeclass create multiple layers of compile-time protection, eliminating an entire class of lifetime bugs without runtime overhead.

`Scope`:
- Prevents resource leaks and use-after-close via compile-time type checking
- Allocates resources eagerly and runs finalizers deterministically in LIFO order
- Is purely synchronous with zero runtime overhead (scoped values erase to underlying types)

Here's the interface definition:

```scala
trait Scope {
  type $[+A]

  def scoped[A](f: Scope => A): A
  def allocate[A](resource: Resource[A]): $[A]
  def allocate(value: => AutoCloseable): $[AutoCloseable]
  def open(): $[OpenScope]
  def defer(f: => Unit): DeferHandle
  def lower[A](value: parent.$[A]): $[A]
  def isClosed: Boolean
  def isOwner: Boolean
}
```

## Motivation

Most resource bugs in Scala are "escape" bugs—scenarios where a resource is used outside of its intended lifetime, leading to undefined behavior, crashes, or data corruption:

- **Storing in fields:** You open a database connection and store it in a field, intending to close it in a finalizer. But if the finalizer runs before you're truly done with the connection, or if you forget to close it, the connection is silently used after closure.
- **Capturing in closures:** You create a file handle and pass it to an async framework via a callback. The callback might be invoked long after your scope has closed and the file has been released, causing the program to crash or silently read/write corrupted data.
- **Passing to untrusted code:** You pass a resource to a library function that might store a reference and use it later, outside your scope. You have no way to know when it's safe to close.
- **Mixing lifetimes:** In large codebases, it becomes unclear which scope owns which resource. A developer might use a resource in the wrong scope, or two scopes might try to close the same resource.

Scope addresses these with a *tight* design. Each design choice solves a specific problem and works together with the others:

1. **Compile-time leak prevention via type tagging** — Every scope has its own `$[A]` type, combined with the `$` macro that restricts how you can use values and the `Unscoped` typeclass that marks safe return types. Together, these prevent returning resources from their scope at compile time. No runtime wrapper objects needed.

2. **Zero runtime overhead** — Scoped values erase to the underlying type `A` at runtime (via casts). There's no boxing, no extra objects, no GC pressure. The compile-time safety is "free."

3. **Eager allocation** — Resources are acquired immediately when you call `allocate`, not deferred to some later point. This makes lifetimes predictable and your code matches your mental model.

4. **Deterministic, LIFO finalization** — Finalizers are guaranteed to run in reverse order of allocation when a scope closes. If acquisition order implies dependencies (common in resource hierarchies), cleanup order is automatically correct. Exceptions in finalizers are collected rather than stopping cleanup.

5. **Structured scopes with parent-child relationships** — Scopes form a hierarchy; children always close before parents. The `lower` operator lets you safely use parent-scoped values in children, since parent will outlive child.

If you've used `try/finally`, `Using`, or ZIO's `Scope`, this is the same problem space—but optimized for **synchronous code** with **compile-time boundaries**.

## Installation

Add the following dependency to your `build.sbt`:

```scala
libraryDependencies += "dev.zio" %% "zio-blocks-scope" % "0.0.33"
```

Supported Scala versions: **2.13.x** and **3.x**.

## Quickstart

Here's a minimal example showing resource allocation, usage, and cleanup. This example introduces a canonical `Database` stub that we'll reuse throughout this guide:

```scala

final class Database extends AutoCloseable {
  def query(sql: String): String = s"result: $sql"
  def close(): Unit = println("db closed")
}

val out: String =
  Scope.global.scoped { scope =>

    val db: $[Database] =
      Resource.fromAutoCloseable(new Database).allocate

    // Safe access: the lambda parameter can only be used as a receiver
    $(db)(_.query("SELECT 1"))
  }

println(out)
```

What's happening in this code:

**Allocating resources in a scope.** When you call `Resource.fromAutoCloseable(new Database).allocate`, you're acquiring a database connection. The `allocate` method returns a **scoped value** of type `scope.$[Database]`—notice the `$` wrapper. This type is unique to the `scope` instance. You can import the scope to use the short form `$[Database]`.

**The `$` operator restricts access.** You cannot call `db.query(...)` directly on `$[Database]` because the methods are hidden at the type level. Instead, you use the `$` access operator: `$(db)(f)`, which takes a lambda. The lambda's parameter must be used only as a receiver (for method/field access), preventing accidental capture or escape.

**Safe return from scoped.** The `scoped` block returns a plain `String` (the result of `_.query("SELECT 1")`). This is safe because `String` is marked as `Unscoped`—a typeclass that says "this type is pure data, safe to leave a scope." If you tried to return `db` instead, the compiler would error.

**LIFO cleanup.** When the `scoped` block exits (normally or via exception), all finalizers run in reverse order. The database's `close()` method is registered automatically because `Database` extends `AutoCloseable`. So cleanup happens at the right time, in the right order, even if an exception occurred.

## Safety Model

Scope's compile-time safety comes from *three reinforcing layers* that work together to prevent resource leaks.

1. **Type identity per scope.** Every scope has a distinct `$[A]` type. This makes values from different scopes **structurally incompatible** at compile time, so you cannot accidentally use a resource in the wrong scope without an explicit conversion (`lower` for parent → child). For example, `scope1.$[Database]` and `scope2.$[Database]` are different types—the compiler refuses to mix them:

```scala
// does not compile:
Scope.global.scoped { scope1 =>

  val db1 = allocate(new Database)

  scope1.scoped { scope2 =>

    val x: scope2.$[Database] = db1  // Error: type mismatch
    // scope2.$[Database] is not compatible with scope1.$[Database]
  }
}
```

To safely use a parent scope's resource in a child scope, use `lower`:

```scala
Scope.global.scoped { outer =>

  val db = allocate(new Database)

  outer.scoped { inner =>

    val dbInChild = inner.lower(db)  // ✓ Correct: retags for child scope
    $(dbInChild)(_.query("SELECT 1"))
  }
  // db is still alive here after child closes
}
```

2. **Controlled access via the `$` macro.** The `$` operator only allows using an unwrapped value as a **method/field receiver**. This prevents returning the resource, storing it in a local val/var, passing it as an argument to a function, or capturing it in a closure. The `$` macro also requires a **lambda literal** (not a method reference or variable):

```scala
// does not compile:
val f: Database => String = _.query("x")
(scope $ db)(f) // Error: "$ requires a lambda literal ..."
```

A lambda literal is an anonymous function written directly in code (e.g., `_.query("x")` or `x => x + 1`). The macro inspects the actual code you pass, so you must pass the lambda directly: `$(db)(_.query("x"))` compiles, but storing it in a variable first defeats this check. Without this restriction, you could smuggle the resource out indirectly via a stored function:

```scala
// hypothetical: if the macro didn't require a lambda literal
var leaked: Database = null

val f: Database => String = { db =>
  leaked = db  // Store the database somewhere the macro can't see
  db.query("x")
}

$(db)(f)  // Macro sees the call but can't detect the smuggling above

// After the scope closes, the resource is still accessible:
leaked.query("SELECT *")  // Use-after-close bug!
```

By requiring a lambda literal, the macro can analyze the actual code syntax. It rejects any attempt to store or capture the parameter, making smuggling impossible.

3. **Scope boundary enforcement via `Unscoped`.** A `scoped { ... }` block can only return values with an `Unscoped` instance (pure data). Resources and closures cannot escape the scope boundary at compile time. For example, trying to return a resource directly fails:

```scala
// does not compile:
Scope.global.scoped { scope =>

  val db = allocate(new Database)
  db  // Error: No given instance of Unscoped[$[Database]]
}
```

Closures over resources are also rejected:

```scala
// does not compile:
Scope.global.scoped { scope =>

  val db = allocate(new Database)
  () => db.query("SELECT 1")  // Error: No given instance of Unscoped[() => String]
  // (the closure captures db)
}
```

Only types with an `Unscoped` instance can cross the scope boundary—typically pure data:

```scala
Scope.global.scoped { scope =>

  val db = allocate(new Database)
  $(db)(_.query("SELECT 1"))  // ✓ Correct: returns String, which is Unscoped
}
```

## Construction

### `Scope.global` — The Root Scope

`Scope.global` is the predefined root scope instance. It exists for the lifetime of your application and is the entry point for all scope-based resource management.

In `Scope.global`, the `$[A]` type is an identity type (i.e., `$[A] = A`). Finalizers registered in the global scope run on JVM shutdown via a shutdown hook. On Scala.js, global finalizers are not automatically invoked.

Use `Scope.global` to access the root scope:

```scala

val result: String = Scope.global.scoped { scope =>

  "no resources allocated"
}
```

### `Scope#scoped` — Create and Enter a Child Scope

`scoped` creates a new child scope with lexical lifetime. All resources allocated within the lambda are automatically cleaned up (LIFO) when the lambda exits, whether normally or via exception:

The lambda receives the child scope as a parameter. You can import its members to use the short form `$[A]` instead of `scope.$[A]`:

```scala

final class Database extends AutoCloseable {
  def query(sql: String): String = s"result: $sql"
  def close(): Unit = println("database closed")
}

Scope.global.scoped { scope =>

  val db: $[Database] =
    Resource.fromAutoCloseable(new Database).allocate

  // Use the database within the scope
  val result = $(db)(_.query("SELECT 1"))
  result
  // db is automatically closed here (scope exits)
}
```

### `Scope#open` — Create an Unowned Child Scope

`open()` creates a child scope you explicitly close, returning an `OpenScope` handle. Unlike `scoped { }`, this allows non-lexical lifetime management:

The child scope is **unowned** (usable from any thread) but remains **linked to the parent** (if the parent closes, the child's finalizers also run). You must call `close()` to detach and finalize immediately.

This is useful for resource pools, lazy initialization, or service factories where you need to decouple resource acquisition from cleanup. Unlike `scoped { }`, which ties lifetime to a lexical block, `open()` lets you keep resources alive across function boundaries and explicit time boundaries.

Here's a practical application initialization pattern:

```scala

final class Database extends AutoCloseable {
  def query(sql: String): String = s"result: $sql"
  def close(): Unit = println("db closed")
}

// Application initialization: open resources early, return handle for later cleanup
val appResources = Scope.global.open()
val db = appResources.scope.allocate(Resource.fromAutoCloseable(new Database))

try {
  // Use database from anywhere in the application
  val result = appResources.scope.scoped { scope =>

    // Can create child scopes and use parent resources with lower()
    val dbInChild = scope.lower(db)
    $(dbInChild)(_.query("SELECT 1"))
  }

  println(s"Query result: $result")

  // ... rest of application code ...

} finally {
  // Application shutdown: explicit cleanup (decoupled from creation)
  appResources.close().orThrow()
}
```

## Core Operations

### `Scope#allocate` — Acquire a Resource

Allocates a `Resource[A]` in this scope, acquiring the underlying value immediately and registering its finalizer:

```scala
trait Scope {
  def allocate[A](resource: Resource[A]): $[A]
  def allocate[A <: AutoCloseable](value: => A): $[A]
}
```

The first overload accepts any `Resource`. The second is a convenience for `AutoCloseable` values—their `close()` method is automatically registered as a finalizer.

If the scope is already closed, `allocate` throws `IllegalStateException`. Otherwise, the resource is acquired eagerly and its finalizer is registered to run LIFO when the scope closes:

```scala

final class Database extends AutoCloseable {
  def query(sql: String): String = s"result: $sql"
  def close(): Unit = println("db closed")
}

Scope.global.scoped { scope =>

  // Using Resource factory
  val db1: $[Database] =
    Resource.fromAutoCloseable(new Database).allocate

  // Using AutoCloseable overload (convenience)
  val db2: $[Database] = allocate(new Database)

  // Both are equivalent; use whichever is more readable
  ()
}
```

### `$` — Access a Scoped Value

The `$` operator safely accesses a scoped value by enforcing it is only used as a method/field receiver, preventing accidental capture or escape:

**Single value:** Use infix or unqualified syntax:

```scala

final class Database extends AutoCloseable {
  def query(sql: String): String = s"result: $sql"
  def close(): Unit = println("db closed")
}

Scope.global.scoped { scope =>

  val db: $[Database] = allocate(new Database)

  // Infix syntax
  val result1 = (scope $ db)(_.query("SELECT 1"))

  // Unqualified after `import scope._`
  val result2 = $(db)(_.query("SELECT 2"))

  result1 + result2
}
```

**Multiple values:** Use unqualified syntax only:

```scala

final class Database extends AutoCloseable {
  def query(sql: String): String = s"result: $sql"
  def close(): Unit = println("db closed")
}

final class Cache extends AutoCloseable {
  def key(): String = "cache_key"
  def close(): Unit = ()
}

Scope.global.scoped { scope =>

  val db: $[Database] = allocate(new Database)
  val cache: $[Cache] = allocate(new Cache)

  // Multiple values: each parameter may only be a receiver
  val result = $(db, cache)((d, c) => d.query(c.key()))
  result
}
```

The `$` macro enforces receiver-only rules at compile time:
- ✓ Allowed: `d.method()`, `d.method(c.key())` (method calls, field access)
- ✗ Rejected: `store(d)`, `() => d.method()`, `d` (returned), `{ val x = d; 1 }` (binding)

If a result type is `Unscoped[B]` (pure data), `$` auto-unwraps it to `B`. Otherwise, it returns `scope.$[B]`.

### `Scope#lower` — Use a Parent Value in a Child Scope

`lower` retagges a parent-scoped value into a child scope. This is safe because a parent scope always outlives its children:

This is useful when a child scope needs access to resources allocated in its parent:

```scala

final class Database extends AutoCloseable {
  def query(sql: String): String = s"result: $sql"
  def close(): Unit = println("db closed")
}

Scope.global.scoped { outer =>

  val db: $[Database] = allocate(new Database)

  // Create an inner scope that needs the database
  outer.scoped { inner =>

    // Retag the parent's database into the child
    val dbInChild: inner.$[Database] = inner.lower(db)

    // Now use it in the child
    $(dbInChild)(_.query("child query"))
  }
  // When inner exits, its finalizers run
  // When outer exits, db's finalizers run (still alive for the outer scope)
}
```

### `Finalizer#defer` — Register a Manual Finalizer

`defer` registers a cleanup action to run when the scope closes. It returns a `DeferHandle` that can cancel the registration:

```scala
trait Finalizer {
  def defer(f: => Unit): DeferHandle
}
```

`defer` is useful for resources that are not wrapped in `Resource`, or when you need explicit control over finalization. Here's a practical example—managing a temporary file and a logger that don't implement `AutoCloseable`:

```scala

// A logger that needs manual cleanup but doesn't implement AutoCloseable
class Logger {
  def log(msg: String): Unit = println(s"[LOG] $msg")
  def close(): Unit = println("Logger closed")
}

Scope.global.scoped { scope =>

  // Create a temporary file (not AutoCloseable from standard library)
  val tempFile = Files.createTempFile("app", ".tmp")
  defer {
    Files.deleteIfExists(tempFile)
    println(s"Temp file deleted: $tempFile")
  }

  // Create a logger (not AutoCloseable)
  val logger = new Logger
  val loggerHandle = defer(logger.close())

  // Use both resources
  logger.log("Processing file: " + tempFile)
  Files.write(tempFile, "data".getBytes())

  // If needed, cancel the finalizer and clean up manually
  val data = Files.readAllBytes(tempFile)
  logger.log(s"Read ${data.length} bytes")

  // loggerHandle.cancel()  // Would prevent auto-cleanup
}
// When the scope exits: logger closes, then temp file is deleted (LIFO order)
```

If the scope is already closed, `defer` is silently ignored (no-op). The finalizer is guaranteed to run in LIFO order with other finalizers when the scope closes.

### `Scope#isClosed` — Check If Closed

Returns whether this scope's finalizers have already run:

```scala
trait Scope {
  def isClosed: Boolean
}
```

Once `isClosed` returns `true`, subsequent calls to `allocate`, `open`, or `$` throw `IllegalStateException`. This is checked to prevent use-after-close bugs.

Here's a practical example—a resource manager that guards against using a closed scope:

```scala

final class Database extends AutoCloseable {
  def query(sql: String): String = s"result: $sql"
  def close(): Unit = println("db closed")
}

// A service that holds and manages a scope
class DatabaseService {
  private val serviceScope = Scope.global.open()

  // Initialize database once at startup
  private val db = {
    try {
      serviceScope.scope.allocate(Resource.fromAutoCloseable(new Database))
    } catch {
      case e: IllegalStateException =>
        serviceScope.close().orThrow()
        throw e
    }
  }

  def isAvailable: Boolean = !serviceScope.scope.isClosed

  def execute(query: String): Either[String, String] = {
    if (serviceScope.scope.isClosed) {
      Left("Database service has been shut down")
    } else {
      try {
        Right(serviceScope.scope.scoped { scope =>

          val dbInChild = scope.lower(db)
          $(dbInChild)(_.query(query))
        })
      } catch {
        case e: IllegalStateException => Left(s"Service error: ${e.getMessage}")
      }
    }
  }

  def shutdown(): Unit = {
    if (!serviceScope.scope.isClosed) {
      serviceScope.close().orThrow()
      println("Service shutdown complete")
    }
  }
}

// Usage
val service = new DatabaseService
println(s"Service available: ${service.isAvailable}")

val result1 = service.execute("SELECT 1")
println(s"Query result: $result1")

service.shutdown()

// Attempting to use after shutdown is now safe
val result2 = service.execute("SELECT 2")
println(s"Query after shutdown: $result2")
```

`Scope.global` returns `false` until JVM shutdown. Child scopes created with `scoped { }` are closed when the block exits, while those created with `open()` remain open until you call `close()`.

### `Scope#isOwner` — Check Thread Ownership

Returns whether the calling thread is the owner of this scope:

```scala
trait Scope {
  def isOwner: Boolean
}
```

Ownership is used to detect cross-thread scope misuse. Thread ownership rules:
- `Scope.global`: always returns `true` (any thread may use it)
- Child scopes created via `scoped { }`: returns `true` only on the thread that entered the block
- Child scopes created via `open()`: always returns `true` (unowned, usable cross-thread)

Calling `scoped { }` on a scope you don't own throws `IllegalStateException` at runtime:

```scala

Scope.global.scoped { scope =>
  // On the thread that entered scoped, isOwner is true
  assert(scope.isOwner)

  // On a different thread, isOwner returns false
  val thread = new Thread {
    override def run(): Unit = {
      assert(!scope.isOwner)
    }
  }
  thread.start()
  thread.join()
}
```

**Example 1: Thread-owned scope (scoped) — fails on worker thread**

Thread-owned scopes cannot be used to create child scopes from a different thread:

```scala

final class Database extends AutoCloseable {
  def query(sql: String): String = s"result: $sql"
  def close(): Unit = println("db closed")
}

val executor = Executors.newFixedThreadPool(1)

try {
  Scope.global.scoped { scope =>

    val db = allocate(new Database)

    // Try to create a child scope from a different thread
    val future = executor.submit { () =>
      try {
        scope.scoped { childScope =>

          val dbInChild = childScope.lower(db)
          $(dbInChild)(_.query("SELECT 1"))
        }
      } catch {
        case e: IllegalStateException => s"Error: ${e.getMessage}"
      }
    }
    println(future.get())
  }
} finally {
  executor.shutdown()
}

// Example Output:
// Error: Cannot create child scope: current thread 'pool-1-thread-1' does not own this scope (owner: 'main')
// db closed
```

**Example 2: Unowned scope (open) — works across threads**

Open scopes are unowned and usable from any thread:

```scala

final class Database extends AutoCloseable {
  def query(sql: String): String = s"result: $sql"
  def close(): Unit = println("db closed")
}

val executor = Executors.newFixedThreadPool(1)

try {
  val poolScope = Scope.global.open()
  val db = poolScope.scope.allocate(Resource.fromAutoCloseable(new Database))

  // Use the resource from a worker thread
  val future = executor.submit { () =>
    poolScope.scope.scoped { scope =>

      val dbInChild = scope.lower(db)
      $(dbInChild)(_.query("SELECT 1"))
    }
  }
  println(future.get())

  poolScope.close().orThrow()
} finally {
  executor.shutdown()
}

// Output:
// result: SELECT 1
// db closed
```

The key difference: `scoped { }` creates **owned** scopes (tied to the entering thread), while `open()` creates **unowned** scopes (usable from any thread). Choose based on whether your resources need to cross thread boundaries.

## Returning Unscoped Data from a Scope

A `scoped { }` block can only return values that have an `Unscoped` instance—that is, pure data types with no embedded resources or cleanup logic. This restriction prevents resource leaks: you cannot accidentally return a resource that would be cleaned up before you could use it.

For built-in types like `String`, `Int`, or `List[String]`, `Unscoped` instances exist automatically. However, when your custom type contains a field whose type has no predefined `Unscoped` instance (such as `java.util.Date`, a legacy Java type that is pure data but not automatically recognized), automatic derivation won't work. In such cases, you must provide an `Unscoped` instance explicitly, asserting that your type holds only pure data:

```scala

// java.util.Date has no predefined Unscoped instance, so Unscoped.derived
// won't work here — we must provide the instance explicitly
case class QueryResult(rows: List[String], count: Int, executedAt: Date)

object QueryResult {
  implicit val unscoped: Unscoped[QueryResult] = new Unscoped[QueryResult] {}
}

Scope.global.scoped { scope =>

  // ... acquire database ...
  QueryResult(List("a", "b"), 2, new Date())  // Returns safely
}
```

**Only add `Unscoped` for pure data types.** Never add it for types that hold resources (connections, streams, file handles). If you encounter the compile error [`No given instance of Unscoped[MyType]`](#no-given-instance-of-unscopedmytype--escaping-a-scope), see the compile errors section for how to fix it. For the complete API and examples, see the [Unscoped reference](./unscoped.md).

## Lexical vs Explicit Scopes

The Scope API provides two primary patterns for managing resource lifetimes. Choose `Scope#scoped` if you can write both the code that acquires and the code that releases the resource in the same expression; choose `Scope#open()` if the resource lifetime must outlive the function that creates it. Most user code should prefer `scoped` for automatic cleanup and thread safety—use `open()` only when you need manual lifetime control, such as in connection pools or DI containers.

### Lexical Scopes with `Scope#scoped`

Use `Scope#scoped` when the resource lifetime is lexically bounded. Lexical scopes are thread-owned by default, preventing accidental cross-thread access and providing automatic cleanup even on exception. This makes them safe and composable: you can nest `scoped` blocks to express hierarchical resource dependencies, and the code structure naturally matches the resource lifetime.

Here's a basic pattern showing how to acquire and use a resource within a single scope:

```scala

final class Database extends AutoCloseable {
  def query(sql: String): String = s"result: $sql"
  def close(): Unit = println("db closed")
}

Scope.global.scoped { scope =>

  val db = allocate(new Database)
  $(db)(_.query("SELECT * FROM users"))
  // db closes when scope exits
}
```

The only trade-off is that you must know the scope's lifetime upfront and cannot easily extend resource lifetime across function boundaries without returning resources themselves. For details on different allocation approaches (with `Resource.fromAutoCloseable()` or directly with `AutoCloseable`), see [Core Operations — allocate](#scopeallocate--acquire-a-resource).

#### Nesting for hierarchical resources

When resources depend on each other, nest `scoped` blocks to express the hierarchy. Parent scopes always outlive their children, so you can safely use parent resources in child scopes:

```scala

final class Database extends AutoCloseable {
  def query(sql: String): String = s"result: $sql"
  def close(): Unit = println("db closed")
}

final class Connection extends AutoCloseable {
  def close(): Unit = println("connection closed")
}

Scope.global.scoped { outerScope =>

  val db = allocate(new Database)

  // Child scope for connection
  outerScope.scoped { innerScope =>

    val conn = allocate(new Connection)
    // Use conn and db here
    // conn closes first (LIFO)
  }

  // Can still use db here
  // db closes when outerScope exits
}
```

### Explicit Scopes with `Scope#open`

Use `Scope#open()` when the resource lifetime is not lexically bounded. Open scopes are unowned (usable from any thread), which makes them suitable for patterns like connection pools, resource caches, and DI containers where resources must outlive the function that creates them. This pattern gives you full control over resource acquisition and release timing.

The trade-off is that you accept full responsibility for cleanup: forgetting to call `close()` leaves resources open, and any exception during cleanup must be explicitly handled. Here's the key pattern—returning an `OpenScope` handle from a function:

```scala

final class Database extends AutoCloseable {
  def query(sql: String): String = s"result: $sql"
  def close(): Unit = println("db closed")
}

def acquireDatabase(): Scope.OpenScope = {
  val os = Scope.global.open()
  val _ = os.scope.allocate(Resource.fromAutoCloseable(new Database))
  os
}

val handle = acquireDatabase()
try {
  // Use handle.scope as needed
  ()
} finally {
  handle.close().orThrow()
}
```

## Dependency Injection

`Scope` integrates seamlessly with `Wire` and `Resource.from` for automatic dependency injection. `Wire` describes a recipe for constructing a service and its dependencies, while `Scope` manages the resource lifetime. Together they eliminate manual dependency passing and ensure proper cleanup in LIFO order.

Here's an example using `Wire` and `Resource.from` within a scope:

```scala

final class Config(val dbUrl: String)

final class Database(config: Config) extends AutoCloseable {
  def query(sql: String): String = s"result: $sql"
  def close(): Unit = println(s"db closed (${config.dbUrl})")
}

final class UserService(db: Database) {
  def getUser(id: Int): String = s"user $id from ${db.query("SELECT * FROM users")}"
}

final class App(service: UserService) {
  def run(): Unit = println(service.getUser(1))
}

// Wire describes the dependency graph: App -> UserService -> Database -> Config
// Resource.from uses the Wire to automatically construct the entire graph
Scope.global.scoped { scope =>

  val config = Config("jdbc:postgres://localhost/db")
  val app = allocate(Resource.from[App](
    Wire(config)
  ))
  $(app)(_.run())
  // All resources (Database, App) clean up automatically in reverse order
}
```

For more details on `Wire` sharing strategies, resource composition, and advanced DI patterns, see the [Wire reference](./wire.md) and [Resource reference](./resource.md).

## Best Practices

### Entry point pattern — use `Scope.global.scoped` at the top level

Wrap your entire application's resource acquisition in a single lexical scope:

```scala

object MyApp {
  def main(args: Array[String]): Unit = {
    Scope.global.scoped { scope =>

      // All resources acquired here
      // Automatic cleanup when main exits
    }
  }
}
```

This is your "outer boundary" for resource safety. Everything inside is protected.

### Composition — use `Resource` builders before allocation

Build resource acquisition/release logic outside the scope, then `Scope#allocate` once inside. This separates *construction* (how) from *allocation* (when), making code testable and reusable.

**Key combinators:**

- **`.map(f)`** — Transform a resource's value
- **`.flatMap(f)`** — Chain resources where the second depends on the first
- **`.zip(other)`** — Combine two independent resources

**Example: Using `.zip()` to combine independent resources:**

```scala

final class Database extends AutoCloseable {
  def query(sql: String): String = s"result: $sql"
  def close(): Unit = println("db closed")
}

final class Cache extends AutoCloseable {
  def get(key: String): Option[String] = None
  def close(): Unit = println("cache closed")
}

// Compose outside scope — reusable across multiple applications
val dbResource = Resource.fromAutoCloseable(new Database)
val cacheResource = Resource.fromAutoCloseable(new Cache)
val appResources = dbResource.zip(cacheResource)

// Allocate once inside scope
Scope.global.scoped { scope =>

  val (db, cache) = allocate(appResources)
  // Use both — cleanup happens in LIFO order (cache first, then db)
}
```

**Example: Using `.flatMap()` for dependent resources:**

```scala

final class Config(val host: String, val port: Int)

final class Database(val config: Config) extends AutoCloseable {
  def query(sql: String): String = s"result: $sql"
  def close(): Unit = println("db closed")
}

// Config resource must be acquired first, then database
val configResource = Resource(new Config("localhost", 5432))
val dbResource = configResource.flatMap { cfg =>
  Resource.fromAutoCloseable(new Database(cfg))
}

// Allocate the dependent chain
Scope.global.scoped { scope =>

  val db = allocate(dbResource)
  // db was initialized with config; cleanup happens in reverse order
}
```

To learn more about building and composing resources, see the [Resource reference](./resource.md).

## Runtime Errors

Runtime errors occur when you violate scope rules at runtime—typically by accessing resources after the scope has already cleaned them up, or by mixing scopes across threads.

### `IllegalStateException` — accessing a closed scope

This error occurs when you attempt to acquire resources (via `allocate`, `open`, or the `$` operator) on a scope that has already closed. Every `scoped { }` block cleans up its resources as soon as the block exits, so any attempt to use the scope after that point fails.

The error message is typically:
```
Cannot acquire resource: scope has already been closed. ...
```

This usually happens when you:
- Store a scope in a field or closure and try to use it later after the enclosing `scoped` block has exited
- Accidentally pass a scope to an async operation that runs after cleanup

To avoid this, keep the scope's lifetime clear: allocate resources, use them, then let them clean up when the scope exits. If you need resources to survive longer, use `Scope.global.open()` to get a handle you can manage manually. You can also check `scope.isClosed` before attempting operations as a defensive check.

### `IllegalStateException` — cross-thread scope usage

Scopes are thread-owned by default. When you create a child scope using `scoped { }`, it's owned by the thread that created it. If you try to access that scope (allocate resources, create child scopes) from a different thread, the scope will reject it.

The error message is typically:
```
Cannot create child scope: current thread '...' does not own this scope (owner: '...')
```

This happens when you try to:
- Pass a scope to another thread and use it there
- Share a scope across multiple threads that call `scoped { }` on it

To fix this, use thread-unowned scopes when you need to share across threads. Instead of `scope.scoped { }` (which creates a thread-owned child), use `Scope.global.open()` or the scope's `open()` method directly to get an `OpenScope` handle. These unowned scopes can be safely passed and used from any thread, though you're responsible for manual cleanup via the returned handle.

## Compile Errors

The following compile errors occur when `Scope` type rules are violated. All examples below use this scoping pattern (see [Quickstart](#quickstart) for full context):

```scala
Scope.global.scoped { scope =>

  val db: $[Database] = allocate(new Database)
  // ... usage or error ...
}
```

### `No given instance of Unscoped[MyType]` — escaping a scope

This error occurs when the type you return from a `scoped { }` block has no `Unscoped` instance. See [Scope boundary enforcement via `Unscoped`](#safety-model) for the full explanation of why this restriction exists.

If you write:
```scala
db  // ERROR: No given instance of Unscoped[$[Database]]
```

The compiler rejects this because `db` is a scoped resource (type `$[Database]`), not safe data. Even though you're inside the `scoped` block, the type system prevents you from returning it because it would be useless outside the scope (the resource would already be cleaned up).

To fix this, you have two options:

**Option 1: Extract data from the resource before returning**

Call a method on the resource to get pure data (strings, numbers, etc.) that are naturally `Unscoped`:

```scala
$(db)(_.query("data"))  // ✓ Correct: Returns String, which is Unscoped
```

The `String` returned by `query()` is pure data with no cleanup logic, so it can safely escape the scope.

**Option 2: Implement `Unscoped` for your custom types**

If you create custom types that hold only pure data, add an `Unscoped` instance so they can escape scopes. See the [Unscoped reference](./unscoped.md) for details and examples.

### `Scoped values may only be used as a method receiver` — macro violation

**What this means:** A scoped value (the parameter inside a `$(value)` lambda) can only be used as the receiver of a method call—the object you call `.method()` on. It cannot be passed to other functions, stored in variables, or captured in nested lambdas. This restriction prevents the resource from leaking out of its scope and being used after cleanup.

**When you hit this error:**

The macro detects several violations:

- **Passing as an argument:**
  ```scala
  $(db)(d => store(d))    // ERROR: cannot pass scoped value to a function
  $(db)(d => println(d))  // ERROR: cannot pass to println
  ```

- **Storing in a variable:**
  ```scala
  $(db)(d => {
    val conn = d           // ERROR: cannot bind to val/var
    conn.query()
  })
  ```

- **Returning the value itself:**
  ```scala
  $(db)(d => d)           // ERROR: must call a method, not return bare reference
  ```

- **Capturing in a nested lambda or closure:**
  ```scala
  $(db)(d =>
    () => d.query()       // ERROR: cannot capture in nested lambda
  )
  ```

**What works — calling methods on the parameter:**

```scala
$(db)(d => d.query("SELECT * FROM users"))      // ✓ Method call on receiver
$(db)(_.query("data"))                           // ✓ Using underscore shorthand
$(db)(d => d.execute(statement).rows)           // ✓ Chain method calls
```

If you need to transform or extract data from a resource before using it elsewhere, call a method to extract what you need:

```scala
$(db)(d => d.query("SELECT COUNT(*)"))  // ✓ Returns String (pure data)
// The returned String can now be passed to other functions
```

**Why this restriction exists:** Scoped values are bound to a specific cleanup phase. Allowing them to escape (via arguments or closures) would let them be used after cleanup, causing crashes or data corruption. By restricting usage to method calls only, the macro ensures the resource never leaves its scope.

## Integration

Scope integrates seamlessly with ZIO Blocks' other data types for building complex resource management systems.

### Resource

`Scope` manages the lifecycle of `Resource[A]` values through the `allocate` method. A `Resource` describes how to acquire and clean up a value; `Scope` executes that plan and tracks finalizers. For comprehensive information on constructing, composing, and sharing resources, see the [Resource reference](./resource.md).

Key integration points:
- Use `Resource[A].allocate` to acquire within a scope
- Compose resources with `flatMap`, `andThen`, and other combinators before allocating
- Use `Resource.shared` for multiple-use resources within a scope

### Finalizer

`Scope` extends `Finalizer`, the interface for registering cleanup actions. The `defer` method registers a finalizer that runs when the scope closes. For information on the `DeferHandle` and cancellation, see the [Finalizer reference](./finalizer.md).

Key integration points:
- `scope.defer(f)` registers a cleanup action
- `DeferHandle.cancel()` prevents a finalizer from running
- Finalizers run in LIFO order regardless of whether registered via `allocate` or `defer`

### Wire + Resource.from

For dependency injection patterns, Scope works naturally with [`Wire`](./wire.md) and [`Resource.from`](./resource.md) to build layered service architectures. Allocate resources in a parent scope, then use `lower` to pass them to child scopes as needed.

## Running the Examples

All code from this guide is available as runnable examples in the `scope-examples` module.

**1. Clone the repository and navigate to the project:**

```bash
git clone https://github.com/zio/zio-blocks.git
cd zio-blocks
```

**2. Run individual examples with sbt:**

### Basic Database Connection Lifecycle Management

This example demonstrates how to allocate a database connection within a scope, ensure proper cleanup, and handle the connection's lifecycle safely.

```scala title="scope-examples/src/main/scala/scope/examples/DatabaseConnectionExample.scala" 
/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package scope.examples

/**
 * Configuration for database connection.
 *
 * @param host
 *   the database server hostname
 * @param port
 *   the database server port
 * @param database
 *   the database name to connect to
 */
final case class DbConfig(host: String, port: Int, database: String) {
  def connectionUrl: String = s"jdbc:postgresql://$host:$port/$database"
}

/**
 * Represents the result of a database query.
 *
 * @param rows
 *   the result set as a list of row maps
 */
final case class QueryResult(rows: List[Map[String, String]]) {
  def isEmpty: Boolean = rows.isEmpty
  def size: Int        = rows.size
}

/**
 * Simulates a database connection with lifecycle management.
 *
 * This class demonstrates how AutoCloseable resources integrate with ZIO Blocks
 * Scope. When allocated via `allocate(Resource(...))`, the `close()` method is
 * automatically registered as a finalizer.
 *
 * @param config
 *   the database configuration
 */
final class Database(config: DbConfig) extends AutoCloseable {
  private var connected = false

  def connect(): Unit = {
    println(s"[Database] Connecting to ${config.connectionUrl}...")
    connected = true
    println(s"[Database] Connected successfully")
  }

  def query(sql: String): QueryResult = {
    require(connected, "Database not connected")
    println(s"[Database] Executing: $sql")
    sql match {
      case s if s.contains("users") =>
        QueryResult(
          List(
            Map("id" -> "1", "name" -> "Alice"),
            Map("id" -> "2", "name" -> "Bob")
          )
        )
      case s if s.contains("orders") =>
        QueryResult(
          List(
            Map("order_id" -> "101", "user_id" -> "1", "total" -> "99.99"),
            Map("order_id" -> "102", "user_id" -> "2", "total" -> "149.50")
          )
        )
      case _ =>
        QueryResult(List(Map("result" -> "OK")))
    }
  }

  override def close(): Unit = {
    println(s"[Database] Closing connection to ${config.connectionUrl}")
    connected = false
  }
}

/**
 * Demonstrates basic resource lifecycle management with ZIO Blocks Scope.
 *
 * This example shows:
 *   - Allocating an AutoCloseable resource with automatic cleanup
 *   - Using `$(value)(f)` to access scoped values and execute queries
 *   - LIFO finalizer ordering (last allocated = first closed)
 *
 * When the scope exits, all registered finalizers run in reverse order,
 * ensuring proper cleanup even if exceptions occur.
 */
@main def runDatabaseExample(): Unit = {
  println("=== Database Connection Example ===\n")

  val config = DbConfig("localhost", 5432, "myapp")

  Scope.global.scoped { scope =>

    println("[Scope] Entering scoped region\n")

    // Allocate the database resource. Because Database extends AutoCloseable,
    // its close() method is automatically registered as a finalizer.
    val db: $[Database] = allocate(Resource {
      val database = new Database(config)
      database.connect()
      database
    })

    // Use $(value)(f) to access the scoped value and execute queries.
    $(db) { database =>
      val users = database.query("SELECT * FROM users")
      println(s"[Result] Found ${users.size} users: ${users.rows.map(_("name")).mkString(", ")}\n")

      val orders = database.query("SELECT * FROM orders WHERE status = 'pending'")
      println(s"[Result] Found ${orders.size} orders\n")

      val health = database.query("SELECT 1 AS health_check")
      println(s"[Result] Health check: ${health.rows.head("result")}\n")
    }

    println("[Scope] Exiting scoped region - finalizers will run in LIFO order")
  }

  println("\n=== Example Complete ===")
}
```

([source](https://github.com/zio/zio-blocks/blob/main/scope-examples/src/main/scala/scope/examples/DatabaseConnectionExample.scala))

```bash
sbt "scope-examples/runMain runDatabaseExample"
```

### Managing a Connection Pool with Multiple Allocations

This example demonstrates allocating multiple connections from a pool within the same scope and ensuring all are cleaned up correctly.

```scala title="scope-examples/src/main/scala/scope/examples/ConnectionPoolExample.scala" 
/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package scope.examples

/**
 * Demonstrates `Resource.Shared` with reference counting and nested resource
 * acquisition.
 *
 * This example shows a realistic connection pool pattern where:
 *   - The pool itself is a shared resource (created once, ref-counted)
 *   - Individual connections are resources that must be allocated in a scope
 *   - `pool.acquire` returns `Resource[PooledConnection]`, forcing proper
 *     scoping
 *
 * This pattern is common for database pools, HTTP client pools, and thread
 * pools.
 */

/** Configuration for the connection pool. */
final case class PoolConfig(maxConnections: Int, timeout: Long)

/**
 * A connection retrieved from the pool.
 *
 * Connections are resources - they must be released back to the pool when done.
 * This is enforced by making `acquire` return a `Resource[PooledConnection]`.
 */
final class PooledConnection(val id: Int, pool: ConnectionPool) extends AutoCloseable {
  println(s"    [Conn#$id] Acquired from pool")

  def execute(sql: String): String = {
    println(s"    [Conn#$id] Executing: $sql")
    s"Result from connection $id"
  }

  override def close(): Unit =
    pool.release(this)
}

/**
 * A connection pool that manages pooled connections.
 *
 * Key design: `acquire` returns `Resource[PooledConnection]`, not a raw
 * connection. This forces callers to allocate the connection in a scope,
 * ensuring proper release even if exceptions occur.
 */
final class ConnectionPool(config: PoolConfig) extends AutoCloseable {
  private val nextId  = new AtomicInteger(0)
  private val active  = new AtomicInteger(0)
  private val _closed = new AtomicInteger(0)

  println(s"  [Pool] Created with max ${config.maxConnections} connections")

  /**
   * Acquires a connection from the pool.
   *
   * Returns a `Resource[PooledConnection]` that must be allocated in a scope.
   * The connection is automatically released when the scope exits.
   */
  def acquire: Resource[PooledConnection] = Resource.acquireRelease {
    if (_closed.get() > 0) throw new IllegalStateException("Pool is closed")
    if (active.get() >= config.maxConnections)
      throw new IllegalStateException(s"Pool exhausted (max: ${config.maxConnections})")

    val id   = nextId.incrementAndGet()
    val conn = new PooledConnection(id, this)
    active.incrementAndGet()
    println(s"  [Pool] Active connections: ${active.get()}/${config.maxConnections}")
    conn
  } { conn =>
    conn.close()
  }

  private[examples] def release(conn: PooledConnection): Unit = {
    val count = active.decrementAndGet()
    println(s"    [Conn#${conn.id}] Released back to pool (active: $count)")
  }

  def activeConnections: Int = active.get()

  override def close(): Unit =
    if (_closed.compareAndSet(0, 1)) {
      println(s"  [Pool] *** POOL CLOSED *** (served ${nextId.get()} total connections)")
    }
}

@main def connectionPoolExample(): Unit = {
  println("=== Connection Pool with Resource-based Acquire ===\n")

  val poolConfig = PoolConfig(maxConnections = 3, timeout = 5000L)

  val poolResource: Resource[ConnectionPool] =
    Resource.fromAutoCloseable(new ConnectionPool(poolConfig))

  Scope.global.scoped { appScope =>

    println("[App] Allocating pool\n")
    val pool: $[ConnectionPool] = poolResource.allocate

    println("--- ServiceA doing work (connection scoped to this block) ---")
    appScope.scoped { workScope =>

      val p: $[ConnectionPool]   = lower(pool)
      val c: $[PooledConnection] = $(p)(_.acquire).allocate
      val result                 = $(c)(_.execute("SELECT * FROM service_a_table"))
      println(s"  [ServiceA] Got: $result")
    }
    println()

    println("--- ServiceB doing work ---")
    appScope.scoped { workScope =>

      val p: $[ConnectionPool]   = lower(pool)
      val c: $[PooledConnection] = $(p)(_.acquire).allocate
      val result                 = $(c)(_.execute("SELECT * FROM service_b_table"))
      println(s"  [ServiceB] Got: $result")
    }
    println()

    println("--- Multiple connections in same scope ---")
    appScope.scoped { workScope =>

      val p: $[ConnectionPool]   = lower(pool)
      val a: $[PooledConnection] = $(p)(_.acquire).allocate
      val b: $[PooledConnection] = $(p)(_.acquire).allocate
      val aId                    = $(a)(_.id)
      val bId                    = $(b)(_.id)
      println(s"  [Parallel] Using connections $aId and $bId")
      $(a)(_.execute("UPDATE table_a SET x = 1"))
      $(b)(_.execute("UPDATE table_b SET y = 2"))
      ()
    }
    println()

    println("[App] All work complete, exiting app scope...")
  }

  println("\n=== Example Complete ===")
  println("\nKey insight: pool.acquire returns Resource[PooledConnection],")
  println("forcing proper scoped allocation and automatic release.")
}
```

([source](https://github.com/zio/zio-blocks/blob/main/scope-examples/src/main/scala/scope/examples/ConnectionPoolExample.scala))

```bash
sbt "scope-examples/runMain scope.examples.connectionPoolExample"
```

### Handling Temporary File Resources with Automatic Cleanup

This example shows how to allocate temporary file resources and ensure they are automatically cleaned up when the scope closes, even if errors occur.

```scala title="scope-examples/src/main/scala/scope/examples/TempFileHandlingExample.scala" 
/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package scope.examples

/**
 * Demonstrates `scope.defer(...)` for registering manual cleanup actions.
 *
 * This example shows how to create temporary files during processing and ensure
 * they are deleted when the scope exits—even if processing fails. Deferred
 * cleanup actions run in LIFO (last-in-first-out) order.
 */

/** Represents a temporary file with basic read/write operations. */
case class TempFile(path: String) {
  private var content: String = ""

  def write(data: String): Unit = content = data
  def read(): String            = content
  def delete(): Boolean         = { println(s"  Deleting: $path"); true }
}

/** Result of processing temporary files. */
case class ProcessingResult(processedCount: Int, totalBytes: Long, errors: List[String])

/** Processes a list of temporary files and aggregates results. */
object FileProcessor {
  def process(files: List[TempFile]): ProcessingResult = {
    val totalBytes = files.map(_.read().length.toLong).sum
    ProcessingResult(processedCount = files.size, totalBytes = totalBytes, errors = Nil)
  }
}

@main def tempFileHandlingExample(): Unit = {
  println("=== Temp File Handling Example ===\n")
  println("Demonstrating scope.defer() for manual cleanup registration.\n")

  val result = Scope.global.scoped { scope =>
    // Create temp files and register cleanup via defer.
    // Cleanup runs in LIFO order: file3, file2, file1.

    val file1 = createTempFile(scope, "/tmp/data-001.tmp", "First file content")
    val file2 = createTempFile(scope, "/tmp/data-002.tmp", "Second file - more data here")
    val file3 = createTempFile(scope, "/tmp/data-003.tmp", "Third file with the most content of all")

    println("\nProcessing files...")
    val processingResult = FileProcessor.process(List(file1, file2, file3))
    println(s"Processed ${processingResult.processedCount} files, ${processingResult.totalBytes} bytes\n")

    println("Exiting scope - cleanup runs in LIFO order:")
    processingResult
  }

  println(s"\nFinal result: $result")
}

/**
 * Creates a temporary file and registers its cleanup with the scope.
 *
 * The cleanup action is registered via `defer(...)`, ensuring the file is
 * deleted when the scope closes—regardless of whether processing succeeds.
 *
 * @param s
 *   the scope to register cleanup with
 * @param path
 *   the file path
 * @param content
 *   initial content to write
 * @return
 *   the created TempFile
 */
private def createTempFile(s: Scope, path: String, content: String): TempFile = {
  val file = TempFile(path)
  file.write(content)
  println(s"Created: $path (${content.length} bytes)")

  // Register cleanup - will run when scope exits, in LIFO order
  s.defer {
    file.delete()
  }

  file
}
```

([source](https://github.com/zio/zio-blocks/blob/main/scope-examples/src/main/scala/scope/examples/TempFileHandlingExample.scala))

```bash
sbt "scope-examples/runMain scope.examples.tempFileHandlingExample"
```

### Managing Database Transactions with Commit/Rollback Semantics

This example demonstrates managing database transactions within a scope, showing how to handle commit and rollback operations correctly.

```scala title="scope-examples/src/main/scala/scope/examples/TransactionBoundaryExample.scala" 
/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package scope.examples

/**
 * Transaction Boundary Example
 *
 * Demonstrates nested scopes and resource-returning methods for database
 * transaction management.
 *
 * Key patterns shown:
 *   - '''Resource-returning methods''': `beginTransaction` returns
 *     `Resource[DbTransaction]`
 *   - '''Nested scopes''': Transactions live in child scopes of the connection
 *   - '''Automatic cleanup''': Uncommitted transactions auto-rollback on scope
 *     exit
 *   - '''LIFO ordering''': Transaction closes before connection
 */
object TransactionBoundaryExample {

  /** Simulates a database connection that can create transactions. */
  class DbConnection(val id: String) extends AutoCloseable {
    println(s"  [DbConnection $id] Opened")

    /**
     * Begins a new transaction.
     *
     * Returns a `Resource[DbTransaction]` that must be allocated in a scope.
     * This ensures the transaction is always properly closed (with rollback if
     * not committed) when the scope exits.
     */
    def beginTransaction(txId: String): Resource[DbTransaction] =
      Resource.acquireRelease {
        new DbTransaction(this, txId)
      } { tx =>
        tx.close()
      }

    def close(): Unit =
      println(s"  [DbConnection $id] Closed")
  }

  /** Simulates an active database transaction. */
  class DbTransaction(val conn: DbConnection, val id: String) extends AutoCloseable {
    private var committed  = false
    private var rolledBack = false
    println(s"    [Tx $id] Started on connection ${conn.id}")

    def execute(sql: String): Int = {
      require(!committed && !rolledBack, s"Transaction $id already completed")
      println(s"    [Tx $id] Execute: $sql")
      sql.hashCode.abs % 100 + 1
    }

    def commit(): Unit = {
      require(!committed && !rolledBack, s"Transaction $id already completed")
      committed = true
      println(s"    [Tx $id] Committed")
    }

    def rollback(): Unit =
      if (!committed && !rolledBack) {
        rolledBack = true
        println(s"    [Tx $id] Rolled back")
      }

    def close(): Unit = {
      if (!committed && !rolledBack) {
        println(s"    [Tx $id] Auto-rollback (not committed)")
        rollback()
      }
      println(s"    [Tx $id] Closed")
    }
  }

  /** Result of transaction operations. */
  case class TxResult(success: Boolean, affectedRows: Int) derives Unscoped

  @main def runTransactionBoundaryExample(): Unit = {
    println("=== Transaction Boundary Example ===\n")
    println("Demonstrating Resource-returning beginTransaction method\n")

    Scope.global.scoped { connScope =>

      // Allocate the connection in the outer scope
      val conn: $[DbConnection] = Resource.fromAutoCloseable(new DbConnection("db-001")).allocate
      println()

      // Transaction 1: Successful insert
      println("--- Transaction 1: Insert user ---")
      val result1: TxResult =
        connScope.scoped { txScope =>

          val c: $[DbConnection]   = lower(conn)
          val tx: $[DbTransaction] = $(c)(_.beginTransaction("tx-001")).allocate
          val rows                 = $(tx)(_.execute("INSERT INTO users VALUES (1, 'Alice')"))
          $(tx)(_.commit())
          TxResult(success = true, affectedRows = rows)
        }
      println(s"  Result: $result1\n")

      // Transaction 2: Transfer funds (multiple operations)
      println("--- Transaction 2: Transfer funds ---")
      val result2: TxResult =
        connScope.scoped { txScope =>

          val c: $[DbConnection]   = lower(conn)
          val tx: $[DbTransaction] = $(c)(_.beginTransaction("tx-002")).allocate
          val rows1                = $(tx)(_.execute("UPDATE accounts SET balance = balance - 100 WHERE id = 1"))
          val rows2                = $(tx)(_.execute("UPDATE accounts SET balance = balance + 100 WHERE id = 2"))
          $(tx)(_.commit())
          TxResult(success = true, affectedRows = rows1 + rows2)
        }
      println(s"  Result: $result2\n")

      // Transaction 3: Demonstrates auto-rollback on scope exit without commit
      println("--- Transaction 3: Auto-rollback (no explicit commit) ---")
      val result3: TxResult =
        connScope.scoped { txScope =>

          val c: $[DbConnection]   = lower(conn)
          val tx: $[DbTransaction] = $(c)(_.beginTransaction("tx-003")).allocate
          $(tx)(_.execute("DELETE FROM audit_log"))
          println("    [App] Not committing - scope exit will trigger auto-rollback...")
          TxResult(success = false, affectedRows = 0)
        }
      println(s"  Result: $result3\n")

      println("--- All transactions complete, connection still open ---")
      println("--- Exiting connection scope ---")
    }

    println("\n=== Example complete ===")
    println("\nKey insight: beginTransaction() returns Resource[DbTransaction],")
    println("forcing proper scoped allocation and automatic cleanup.")
  }
}
```

([source](https://github.com/zio/zio-blocks/blob/main/scope-examples/src/main/scala/scope/examples/TransactionBoundaryExample.scala))

```bash
sbt "scope-examples/runMain scope.examples.runTransactionBoundaryExample"
```

### Implementing an HTTP Client Pipeline with Request/Response Interceptors

This example shows how to build an HTTP client pipeline with interceptors for logging, authentication, and error handling, all managed within a scope.

```scala title="scope-examples/src/main/scala/scope/examples/HttpClientPipelineExample.scala" 
/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package scope.examples

/**
 * HTTP Client Pipeline Example
 *
 * Demonstrates using scoped values with the `$` operator for safe resource
 * access. Operations are eager with the new opaque type API.
 */

/** API configuration containing base URL and authentication credentials. */
final case class ApiConfig(baseUrl: String, apiKey: String)

/** Parsed JSON data as a simple key-value store. */
final case class ParsedData(values: Map[String, String]) derives Unscoped

/** HTTP response containing status, body, and headers. */
final case class HttpResponse(statusCode: Int, body: String, headers: Map[String, String])

/** Stateless JSON parser that converts raw JSON strings to structured data. */
object JsonParser {
  def parse(json: String): ParsedData = {
    println(s"  [JsonParser] Parsing ${json.take(50)}...")
    val entries = json.stripPrefix("{").stripSuffix("}").split(",").map(_.trim).filter(_.nonEmpty)
    val values  = entries.flatMap { entry =>
      entry.split(":").map(_.trim.stripPrefix("\"").stripSuffix("\"")) match {
        case Array(k, v) => Some(k -> v)
        case _           => None
      }
    }.toMap
    ParsedData(values)
  }
}

/**
 * HTTP client that manages a connection to an API server.
 *
 * Implements `AutoCloseable` so the scope automatically registers cleanup.
 */
final class HttpClient(config: ApiConfig) extends AutoCloseable {
  println(s"  [HttpClient] Opening connection to ${config.baseUrl}")

  def get(path: String): HttpResponse = {
    println(s"  [HttpClient] GET $path")
    HttpResponse(200, s"""{"path":"$path","data":"sample"}""", Map("X-Api-Key" -> config.apiKey))
  }

  def post(path: String, body: String): HttpResponse = {
    println(s"  [HttpClient] POST $path with body: $body")
    HttpResponse(201, s"""{"created":true,"echo":"$body"}""", Map("Content-Type" -> "application/json"))
  }

  override def close(): Unit =
    println(s"  [HttpClient] Closing connection to ${config.baseUrl}")
}

/**
 * Demonstrates using scoped values with the `$` operator.
 *
 * Key concepts:
 *   - `allocate` returns `$[A]` (scoped value)
 *   - `$(scopedValue)(f)` applies a function to the underlying value
 *   - `$` auto-unwraps to pure data when the return type is `Unscoped`
 *   - Operations are eager (zero-cost wrapper)
 */
@main def httpClientPipelineExample(): Unit = {
  println("=== HTTP Client Pipeline Example ===\n")
  val config = ApiConfig("https://api.example.com", "secret-key-123")

  Scope.global.scoped { scope =>

    // Step 1: Allocate the HTTP client (automatically cleaned up when scope closes)
    val client: $[HttpClient] = allocate(Resource[HttpClient](new HttpClient(config)))

    // Step 2: Use the client to fetch and parse data
    println("Executing requests...\n")

    // Fetch and parse users
    println("--- Fetching: users ---")
    val users: ParsedData = $(client) { c =>
      val response = c.get("/users")
      JsonParser.parse(response.body)
    }

    // Fetch and parse orders
    println("\n--- Fetching: orders ---")
    val orders: ParsedData = $(client) { c =>
      val response = c.get("/orders")
      JsonParser.parse(response.body)
    }

    // Post analytics event
    println("\n--- Posting: analytics ---")
    val analytics: ParsedData = $(client) { c =>
      val response = c.post("/analytics", """{"event":"fetch_complete"}""")
      JsonParser.parse(response.body)
    }

    // Step 3: Access all results
    println(s"\n=== Users Result ===")
    println(s"Users data: ${users.values}")
    println(s"\n=== Orders Result ===")
    println(s"Orders data: ${orders.values}")
    println(s"\n=== Analytics Result ===")
    println(s"Analytics: ${analytics.values}")
  }

  println("\n[Scope closed - HttpClient was automatically cleaned up]")
}
```

([source](https://github.com/zio/zio-blocks/blob/main/scope-examples/src/main/scala/scope/examples/HttpClientPipelineExample.scala))

```bash
sbt "scope-examples/runMain scope.examples.httpClientPipelineExample"
```

### Managing a Shared, Cached Logger Across Multiple Services

This example demonstrates allocating a logger once at the top level and sharing it across multiple services, ensuring it is properly closed when the application shuts down.

```scala title="scope-examples/src/main/scala/scope/examples/CachingSharedLoggerExample.scala" 
/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package scope.examples

/**
 * Demonstrates `Wire.shared` vs `Wire.unique` and diamond dependency patterns.
 *
 * Two services (ProductService, OrderService) share one Logger instance
 * (diamond pattern), but each gets its own unique Cache instance. This shows
 * how shared wires provide singleton behavior while unique wires create fresh
 * instances per injection site.
 *
 * Key concepts:
 *   - `Wire.shared[T]`: Single instance shared across all dependents (memoized)
 *   - `Wire.unique[T]`: Fresh instance created for each dependent
 *   - Diamond dependency: Multiple services depend on the same shared resource
 *   - Reference counting: Shared resources track usage and clean up when last
 *     user closes
 */
object CachingSharedLoggerExample {

  /** Tracks instantiation counts for demonstration purposes. */
  val loggerInstances = new AtomicInteger(0)
  val cacheInstances  = new AtomicInteger(0)

  /**
   * A shared logger that tracks instantiations and provides logging methods.
   * Implements AutoCloseable for proper resource cleanup.
   */
  class Logger extends AutoCloseable {
    val instanceId: Int = loggerInstances.incrementAndGet()
    println(s"  [Logger#$instanceId] Created")

    def info(msg: String): Unit  = println(s"  [Logger#$instanceId] INFO: $msg")
    def debug(msg: String): Unit = println(s"  [Logger#$instanceId] DEBUG: $msg")
    def close(): Unit            = println(s"  [Logger#$instanceId] Closed")
  }

  /**
   * A unique cache per service. Each service gets its own isolated cache
   * instance. Implements AutoCloseable for proper resource cleanup. Note: No
   * constructor params so it can be auto-wired with Wire.unique.
   */
  class Cache extends AutoCloseable {
    val instanceId: Int                    = cacheInstances.incrementAndGet()
    private var store: Map[String, String] = Map.empty
    println(s"  [Cache#$instanceId] Created")

    def get(key: String): Option[String]      = store.get(key)
    def put(key: String, value: String): Unit = store = store.updated(key, value)
    def close(): Unit                         = println(s"  [Cache#$instanceId] Closed")
  }

  /** Product service with its own cache but sharing the logger. */
  class ProductService(val logger: Logger, val cache: Cache) {
    println(s"  [ProductService] Created with Logger#${logger.instanceId} and Cache#${cache.instanceId}")

    def findProduct(id: String): String =
      cache.get(id) match {
        case Some(product) =>
          logger.debug(s"Cache hit for product $id")
          product
        case None =>
          logger.info(s"Loading product $id from database")
          val product = s"Product-$id"
          cache.put(id, product)
          product
      }
  }

  /**
   * Order service with its own cache but sharing the same logger as
   * ProductService.
   */
  class OrderService(val logger: Logger, val cache: Cache) {
    println(s"  [OrderService] Created with Logger#${logger.instanceId} and Cache#${cache.instanceId}")

    def createOrder(productId: String): String = {
      val orderId = s"ORD-${System.currentTimeMillis() % 10000}"
      cache.put(orderId, productId)
      logger.info(s"Created order $orderId for product $productId")
      orderId
    }
  }

  /** Top-level application combining both services. */
  class CachingApp(val productService: ProductService, val orderService: OrderService) extends AutoCloseable {
    def run(): Unit = {
      productService.logger.info("=== Application Started ===")
      val product = productService.findProduct("P001")
      orderService.createOrder(product)
      productService.findProduct("P001") // cache hit
    }
    def close(): Unit = println("  [CachingApp] Closed")
  }

  @main def runCachingExample(): Unit = {
    println("\n╔════════════════════════════════════════════════════════════════╗")
    println("║  Wire.shared vs Wire.unique - Diamond Dependency Example       ║")
    println("╚════════════════════════════════════════════════════════════════╝\n")

    println("Creating wires...")
    println("  - Logger: Wire.shared (singleton across all services)")
    println("  - Cache:  Wire.unique (fresh instance per service)\n")

    println("─── Resource Acquisition ───")
    Scope.global.scoped { scope =>

      val app: $[CachingApp] = allocate(
        Resource.from[CachingApp](
          Wire.shared[Logger],
          Wire.unique[Cache]
        )
      )

      println("\n─── Verification ───")
      println(s"  Logger instances created: ${loggerInstances.get()} (expected: 1)")
      println(s"  Cache instances created:  ${cacheInstances.get()} (expected: 2)")
      $(app) { a =>
        println(s"  ProductService.logger eq OrderService.logger: ${a.productService.logger eq a.orderService.logger}")
        println(s"  ProductService.cache  eq OrderService.cache:  ${a.productService.cache eq a.orderService.cache}")

        println("\n─── Running Application ───")
        a.run()
      }

      println("\n─── Scope Closing (LIFO cleanup) ───")
    }

    println("\n─── Summary ───")
    println(s"  Final Logger count: ${loggerInstances.get()} (shared = 1 instance)")
    println(s"  Final Cache count:  ${cacheInstances.get()} (unique = 2 instances)")
    println("\nDiamond pattern verified: both services received the same Logger instance.")
  }
}
```

([source](https://github.com/zio/zio-blocks/blob/main/scope-examples/src/main/scala/scope/examples/CachingSharedLoggerExample.scala))

```bash
sbt "scope-examples/runMain scope.examples.runCachingExample"
```

### Building a Layered Web Service with Dependency Injection

This example shows how to build a multi-layered web service using Scope for dependency injection, allocating services at different layers and passing them down through child scopes.

```scala title="scope-examples/src/main/scala/scope/examples/LayeredWebServiceExample.scala" 
/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package scope.examples

/**
 * Demonstrates auto-wiring a layered web service using
 * `Resource.from[T](wires*)`.
 *
 * The macro automatically derives wires for concrete classes (Database,
 * UserRepository, UserController) while requiring only the leaf config value to
 * be provided explicitly. Resources are cleaned up in LIFO order when the scope
 * closes.
 *
 * Layer hierarchy:
 * {{{
 *   AppConfig (leaf value via Wire)
 *       ↓
 *   Database (auto-wired, AutoCloseable)
 *       ↓
 *   UserRepository (auto-wired)
 *       ↓
 *   UserController (auto-wired, AutoCloseable)
 * }}}
 */

/** Application configuration - the leaf dependency provided via Wire(value). */
case class WebAppConfig(dbUrl: String, serverPort: Int)

/** Domain model for users. */
case class User(id: Long, name: String, email: String)

/** Database layer - acquires a connection and releases it on close. */
class WebDatabase(config: WebAppConfig) extends AutoCloseable {
  println(s"  [WebDatabase] Connecting to ${config.dbUrl}")

  def execute(sql: String): Int = {
    println(s"  [WebDatabase] Executing: $sql")
    1
  }

  def close(): Unit = println("  [WebDatabase] Connection closed")
}

/** Repository layer - provides data access using the database. */
class UserRepository(db: WebDatabase) {
  println("  [UserRepository] Initialized")

  private var nextId = 1L

  def findById(id: Long): Option[User] = {
    db.execute(s"SELECT * FROM users WHERE id = $id")
    if (id > 0) Some(User(id, "Alice", "alice@example.com")) else None
  }

  def save(user: User): Long = {
    db.execute(s"INSERT INTO users VALUES (${user.id}, '${user.name}', '${user.email}')")
    val id = nextId
    nextId += 1
    id
  }
}

/** Controller layer - handles HTTP requests using the repository. */
class UserController(repo: UserRepository) extends AutoCloseable {
  println("  [UserController] Ready to serve requests")

  def getUser(id: Long): String =
    repo.findById(id).map(u => s"User(${u.id}, ${u.name})").getOrElse("Not found")

  def createUser(name: String, email: String): String = {
    val id = repo.save(User(0, name, email))
    s"Created user with id=$id"
  }

  def close(): Unit = println("  [UserController] Shutting down")
}

/**
 * Entry point demonstrating the auto-wiring feature.
 *
 * Only `Wire(config)` is provided; the macro derives wires for Database,
 * UserRepository, and UserController from their constructors.
 */
@main def layeredWebServiceExample(): Unit = {
  val config = WebAppConfig(dbUrl = "jdbc:postgresql://localhost:5432/mydb", serverPort = 8080)

  println("=== Constructing layers (order: config → database → repository → controller) ===")

  // Resource.from auto-wires the entire dependency graph
  val controllerResource: Resource[UserController] = Resource.from[UserController](
    Wire(config)
  )

  // Allocate within a scoped block; cleanup runs on scope exit
  Scope.global.scoped { scope =>

    val controller: $[UserController] = allocate(controllerResource)

    println("\n=== Handling requests ===")
    println(s"  GET /users/1  → ${$(controller)(_.getUser(1))}")
    println(s"  POST /users   → ${$(controller)(_.createUser("Bob", "bob@example.com"))}")

    println("\n=== Scope closing (LIFO cleanup: controller → database) ===")
  }

  println("=== Done ===")
}
```

([source](https://github.com/zio/zio-blocks/blob/main/scope-examples/src/main/scala/scope/examples/LayeredWebServiceExample.scala))

```bash
sbt "scope-examples/runMain scope.examples.layeredWebServiceExample"
```

### Reading Configuration from a File with Scope Management

This example demonstrates loading configuration from a file within a scope, ensuring the file handle is properly closed when no longer needed.

```scala title="scope-examples/src/main/scala/scope/examples/ConfigReaderExample.scala" 
/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package scope.examples

/**
 * Demonstrates the `Unscoped` marker trait behavior.
 *
 * ==Key Concepts==
 *
 *   - '''`Unscoped`''' marks pure data types that can safely escape a scope
 *   - Pure data escapes freely; resources remain scope-bound
 *
 * ==Example Scenario==
 *
 * A configuration reader produces `ConfigData` (pure, escapable data), while a
 * secret store holds resources that must remain scoped to prevent leakage.
 */

// ---------------------------------------------------------------------------
// Domain Types
// ---------------------------------------------------------------------------

/**
 * Pure configuration data that can safely escape any scope.
 *
 * By deriving `Unscoped`, we declare this type contains no resources. When
 * returned from `scoped { ... }`, the raw `ConfigData` is returned.
 */
case class ConfigData(
  appName: String,
  version: String,
  settings: Map[String, String]
) derives Unscoped

/**
 * Reads configuration files from disk.
 *
 * This is a resource (holds file handles, caches) and must be closed.
 */
class ConfigReader extends AutoCloseable {
  private var closed = false

  def readConfig(@annotation.unused path: String): ConfigData = {
    require(!closed, "ConfigReader is closed")
    ConfigData(
      appName = "MyApplication",
      version = "1.0.0",
      settings = Map(
        "database.host" -> "localhost",
        "database.port" -> "5432",
        "log.level"     -> "INFO"
      )
    )
  }

  override def close(): Unit = {
    closed = true
    println("  [ConfigReader] Closed.")
  }
}

/**
 * Manages access to application secrets.
 *
 * This resource maintains connections and caches; it should NOT have an
 * `Unscoped` instance. It cannot escape the scope.
 */
class SecretStore extends AutoCloseable {
  private var closed = false

  def getSecret(key: String): String = {
    require(!closed, "SecretStore is closed")
    s"secret-value-for-$key"
  }

  override def close(): Unit = {
    closed = true
    println("  [SecretStore] Closed.")
  }
}

// ---------------------------------------------------------------------------
// Main Example
// ---------------------------------------------------------------------------

@main def runConfigReaderExample(): Unit = {
  println("=== Unscoped Example ===\n")

  // ConfigData is Unscoped, so it escapes the scope as raw ConfigData
  val escapedConfig: ConfigData = Scope.global.scoped { scope =>

    val reader: $[ConfigReader] = allocate(Resource(new ConfigReader))

    // $(reader)(f) auto-unwraps to ConfigData (Unscoped)
    $(reader)(_.readConfig("/etc/app/config.json"))
  }

  println("Escaped config (used outside scope):")
  println(s"  App: ${escapedConfig.appName} v${escapedConfig.version}")
  escapedConfig.settings.foreach { case (k, v) => println(s"    $k = $v") }
  println()

  println("SecretStore stays scoped:")
  Scope.global.scoped { scope =>

    val secrets: $[SecretStore] = allocate(Resource(new SecretStore))

    $(secrets) { s =>
      val dbPassword = s.getSecret("database.password")
      println(s"  Retrieved secret: $dbPassword")
    }
    ()
  }
  println("\n=== Example Complete ===")
}
```

([source](https://github.com/zio/zio-blocks/blob/main/scope-examples/src/main/scala/scope/examples/ConfigReaderExample.scala))

```bash
sbt "scope-examples/runMain scope.examples.runConfigReaderExample"
```

### Implementing a Plugin Architecture with Automatic Resource Discovery

This example shows how to build a plugin system that discovers and loads plugins dynamically, managing their lifecycle with scopes.

```scala title="scope-examples/src/main/scala/scope/examples/PluginArchitectureExample.scala" 
/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package scope.examples

/**
 * Plugin Architecture Example — Trait Injection via Subtype Wires
 *
 * Demonstrates how abstract trait dependencies are resolved via concrete
 * implementation wires using subtype resolution. This pattern enables:
 *   - Clean interface/implementation separation
 *   - Easy swapping of implementations (e.g., Stripe vs PayPal)
 *   - Compile-time verified dependency graphs
 */

/** Configuration for payment gateway connections. */
final case class GatewayConfig(apiKey: String, merchantId: String)

/** Result of a payment operation. */
final case class PaymentResult(transactionId: String, success: Boolean, message: String)

/**
 * Abstract payment gateway interface.
 *
 * Services depend on this trait, not concrete implementations.
 */
trait PaymentGateway {
  def charge(amount: BigDecimal, currency: String): PaymentResult
  def refund(transactionId: String): PaymentResult
}

/**
 * Stripe implementation of [[PaymentGateway]].
 *
 * When wired via `Wire.shared[StripeGateway]`, this satisfies any
 * `PaymentGateway` dependency through subtype resolution.
 */
final class StripeGateway(config: GatewayConfig) extends PaymentGateway with AutoCloseable {
  println(s"[Stripe] Connected with merchant ${config.merchantId}")

  def charge(amount: BigDecimal, currency: String): PaymentResult = {
    val txId = s"stripe_${System.nanoTime()}"
    PaymentResult(txId, success = true, s"Charged $currency $amount via Stripe")
  }

  def refund(transactionId: String): PaymentResult =
    PaymentResult(transactionId, success = true, s"Refunded $transactionId via Stripe")

  def close(): Unit = println("[Stripe] Connection closed")
}

/** PayPal implementation — demonstrates swappability. */
final class PayPalGateway(config: GatewayConfig) extends PaymentGateway with AutoCloseable {
  println(s"[PayPal] Connected with merchant ${config.merchantId}")

  def charge(amount: BigDecimal, currency: String): PaymentResult = {
    val txId = s"paypal_${System.nanoTime()}"
    PaymentResult(txId, success = true, s"Charged $currency $amount via PayPal")
  }

  def refund(transactionId: String): PaymentResult =
    PaymentResult(transactionId, success = true, s"Refunded $transactionId via PayPal")

  def close(): Unit = println("[PayPal] Connection closed")
}

/**
 * Checkout service that depends on the abstract [[PaymentGateway]] trait.
 *
 * This service is unaware of which gateway implementation is injected.
 */
final class CheckoutService(gateway: PaymentGateway) extends AutoCloseable {
  def processOrder(orderId: String, amount: BigDecimal): PaymentResult = {
    println(s"[Checkout] Processing order $orderId")
    gateway.charge(amount, "USD")
  }

  def close(): Unit = println("[Checkout] Service shutdown")
}

@main def pluginArchitectureExample(): Unit = {
  val config = GatewayConfig(apiKey = "sk_test_xxx", merchantId = "acme_corp")

  println("=== Using Stripe Gateway ===")
  val stripeResource: Resource[CheckoutService] = Resource.from[CheckoutService](
    Wire(config),
    Wire.shared[StripeGateway] // Satisfies PaymentGateway via subtyping
  )

  Scope.global.scoped { scope =>

    val checkout: $[CheckoutService] = allocate(stripeResource)
    $(checkout) { c =>
      val result = c.processOrder("ORD-001", BigDecimal("99.99"))
      println(s"Result: ${result.message}")
    }
    ()
  }

  println("\n=== Using PayPal Gateway ===")
  val paypalResource: Resource[CheckoutService] = Resource.from[CheckoutService](
    Wire(config),
    Wire.shared[PayPalGateway] // Swap to PayPal — no other changes needed
  )

  Scope.global.scoped { scope =>

    val checkout: $[CheckoutService] = allocate(paypalResource)
    $(checkout) { c =>
      val result = c.processOrder("ORD-002", BigDecimal("149.99"))
      println(s"Result: ${result.message}")
    }
    ()
  }
}
```

([source](https://github.com/zio/zio-blocks/blob/main/scope-examples/src/main/scala/scope/examples/PluginArchitectureExample.scala))

```bash
sbt "scope-examples/runMain scope.examples.pluginArchitectureExample"
```

### Demonstrating Thread Ownership Enforcement in Scope Hierarchies

This example demonstrates how Scope enforces thread ownership, preventing cross-thread scope misuse and illustrating the difference between owned and unowned scopes.

```scala title="scope-examples/src/main/scala/scope/examples/ThreadOwnershipExample.scala" 
/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package scope.examples

/**
 * Simulates a stateful resource that tracks which thread owns it.
 *
 * @param name
 *   the resource name
 */
final class ThreadAwareResource(val name: String) extends AutoCloseable {
  private val createdThread = Thread.currentThread()

  def getInfo: String = {
    val currentThread = Thread.currentThread()
    val owner         = createdThread.getName
    val current       = currentThread.getName
    if (createdThread eq currentThread) {
      s"[$name] Safe: owned by '$owner', accessed by '$current' (same thread)"
    } else {
      s"[$name] WARNING: owned by '$owner', accessed by '$current' (different thread!)"
    }
  }

  override def close(): Unit =
    println(s"[$name] Closing resource (was created by ${createdThread.getName})")
}

/**
 * Demonstrates thread ownership enforcement in ZIO Blocks Scope.
 *
 * This example shows:
 *   - Scope.global: `isOwner` always true; any thread can create children from
 *     it
 *   - Scope.Child: captures the creating thread; `isOwner` checks
 *     `Thread.currentThread() eq owner`
 *   - Scope.open(): creates an unowned child scope; `isOwner` always true from
 *     any thread
 *   - Calling `scoped` on a Scope.Child from a different thread throws
 *     IllegalStateException
 *
 * Thread ownership prevents accidentally passing a scope to another thread and
 * using it there, which would violate structured concurrency guarantees.
 */
@main def runThreadOwnershipExample(): Unit = {
  println("=== Thread Ownership Example ===\n")

  // === Part 1: Single-thread usage (CORRECT) ===
  println("--- Part 1: Single-thread usage (correct) ---\n")

  Scope.global.scoped { scope =>
    val currentThread = Thread.currentThread().getName
    println(s"[Main] Entered scope on thread: $currentThread\n")

    // Scope.Child is owned by the current thread (main)
    scope.scoped { child =>

      println(s"[Main] Created child scope on thread: $currentThread")
      println(s"[Main] Child scope isOwner: ${child.isOwner} (true only for the creating thread)")

      val res: $[ThreadAwareResource] =
        allocate(Resource(new ThreadAwareResource("SingleThreadResource")))

      $(res) { r =>
        println(s"[Main] ${r.getInfo}\n")
      }
    }

    println(s"[Main] Child scope closed, finalizers ran\n")
  }

  // === Part 2: Demonstrating Scope.open() for cross-thread usage ===
  println("--- Part 2: Unowned scope via open() (for cross-thread) ---\n")

  Scope.global.scoped { scope =>

    val mainThread = Thread.currentThread().getName
    println(s"[Main] On thread: $mainThread\n")

    // open() creates an unowned scope that any thread can use
    $(open()) { handle =>
      val childScope = handle.scope
      println(s"[Main] Created unowned scope via open()")
      println(s"[Main] Unowned scope isOwner: ${childScope.isOwner} (true from any thread)\n")

      // Now we can use this scope from a different thread
      val executor = Executors.newSingleThreadExecutor { r =>
        val t = new Thread(r)
        t.setName("worker-thread")
        t
      }

      try {
        val latch = new CountDownLatch(1)

        executor.execute { () =>
          try {
            val workerThread = Thread.currentThread().getName
            println(s"[Worker] On thread: $workerThread\n")

            // Using the unowned scope from a different thread - this works!
            childScope.scoped { workerChild =>

              println(s"[Worker] Created child of unowned scope")

              val res: $[ThreadAwareResource] =
                allocate(Resource(new ThreadAwareResource("CrossThreadResource")))

              $(res) { r =>
                println(s"[Worker] ${r.getInfo}\n")
              }

              println("[Worker] Worker scope closed")
            }
          } finally {
            latch.countDown()
          }
        }

        // Wait for worker thread to finish
        latch.await()
        println()
      } finally {
        executor.shutdown()
        // Clean up the open scope and propagate any finalizer failures
        handle.close().orThrow()
        println("[Main] Unowned scope closed\n")
      }
    }
  }

  // === Part 3: Explanation of ownership violation (what would fail) ===
  println("--- Part 3: Thread ownership violation (explanation) ---\n")
  println("""
If you tried to pass a Scope.Child to another thread and call scoped on it,
you would get an IllegalStateException:

  Scope.global.scoped { scope =>

    // This scope is owned by the main thread
    val executor = Executors.newSingleThreadExecutor()

    executor.execute { () =>
      // This would throw: Cannot create child scope: current thread does not own this scope.
      scope.scoped { child => ... }  // WRONG: scope is owned by main thread
    }
  }

Solution: Use scope.open() instead, which creates an unowned scope that
any thread can use. See Part 2 above for the correct pattern.
""")

  println("=== Example Complete ===")
}
```

([source](https://github.com/zio/zio-blocks/blob/main/scope-examples/src/main/scala/scope/examples/ThreadOwnershipExample.scala))

```bash
sbt "scope-examples/runMain runThreadOwnershipExample"
```

### Detecting and Demonstrating Circular Dependency Scenarios

This example shows how to detect and handle circular dependencies in resource management, illustrating how scopes help prevent subtle bugs in complex dependency graphs.

```scala title="scope-examples/src/main/scala/scope/examples/CircularDependencyDemoExample.scala" 
/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package scope.examples

/**
 * Demonstrates compile-time cycle detection in ZIO Blocks Scope.
 *
 * The `Resource.from[T]` macro analyzes the dependency graph at compile time
 * and rejects circular dependencies with a descriptive error message showing
 * the exact cycle path.
 *
 * ==The Problem==
 * Circular dependencies (A → B → A) cannot be resolved by constructor injection
 * because neither service can be instantiated without the other already
 * existing.
 *
 * ==The Solution==
 * Break the cycle by introducing an interface (trait) that one service depends
 * on, allowing the implementation to be provided separately. This is a standard
 * Dependency Inversion Principle pattern.
 */

// ─────────────────────────────────────────────────────────────────────────────
// PROBLEMATIC: Circular Dependency (would not compile)
// ─────────────────────────────────────────────────────────────────────────────

// These classes form a cycle: ServiceA → ServiceB → ServiceA
// Uncommenting the Resource.from call below would produce a compile error.

// class ServiceA(b: ServiceB) {
//   def greet(): String = s"A says hello, B says: ${b.respond()}"
// }
//
// class ServiceB(a: ServiceA) {
//   def respond(): String = s"B responds, A type: ${a.getClass.getSimpleName}"
// }
//
// Attempting to wire this would fail at compile time:
// val circularResource = Resource.from[ServiceA]()
//
// Expected compile error:
// ┌────────────────────────────┐
// │                            ▼
//     ServiceA ──► ServiceB
// ▲                            │
// └────────────────────────────┘
//
// Break the cycle by:
//   • Introducing an interface/trait
//   • Using lazy initialization
//   • Restructuring dependencies

// ─────────────────────────────────────────────────────────────────────────────
// SOLUTION: Break the cycle with an interface
// ─────────────────────────────────────────────────────────────────────────────

/** Interface that ServiceA depends on, breaking the compile-time cycle. */
trait ServiceBApi {
  def respond(): String
}

/** Concrete implementation of ServiceA that depends only on the interface. */
class ServiceAImpl(b: ServiceBApi) {
  def greet(): String = s"A says hello, B says: ${b.respond()}"
}

/** Concrete implementation of ServiceB without any dependency on A. */
class ServiceBImpl extends ServiceBApi {
  override def respond(): String = "B responds successfully"
}

/** Application that composes both services. */
class Application(a: ServiceAImpl, @annotation.unused b: ServiceBApi) {
  def run(): String = a.greet()
}

/**
 * Demonstrates the working (non-circular) pattern.
 *
 * The dependency graph is now: Application → ServiceAImpl → ServiceBApi ↘
 * ServiceBApi
 *
 * ServiceBImpl provides ServiceBApi, and there is no cycle.
 */
@main def circularDependencyDemoExample(): Unit = {
  println("=== Circular Dependency Demo ===\n")
  println("Demonstrating compile-time cycle detection and how to break cycles.\n")

  Scope.global.scoped { scope =>

    println("Creating application with proper dependency structure...")

    // Wire.shared[ServiceBImpl] provides both ServiceBImpl and ServiceBApi (via subtyping)
    val app: $[Application] = allocate(
      Resource.from[Application](
        Wire.shared[ServiceBImpl]
      )
    )

    println(s"Result: ${$(app)(_.run())}")
    println("\nThe dependency graph was validated at compile time.")
    println("No cycles detected - application wired successfully.")
  }

  println("\n─── Key Takeaways ───")
  println("• Resource.from[T] detects cycles at compile time")
  println("• Cycles produce clear ASCII diagrams showing the path")
  println("• Break cycles by introducing interfaces/traits")
  println("• The Dependency Inversion Principle resolves most cycles")
}
```

([source](https://github.com/zio/zio-blocks/blob/main/scope-examples/src/main/scala/scope/examples/CircularDependencyDemoExample.scala))

```bash
sbt "scope-examples/runMain scope.examples.circularDependencyDemoExample"
```

### Using Scope with Legacy Libraries that Don't Support Managed Resources

This example demonstrates how to integrate Scope with legacy libraries that don't natively support resource management, using wrapper resources and the `leak` escape hatch when necessary.

```scala title="scope-examples/src/main/scala/scope/examples/LegacyLibraryInteropExample.scala" 
/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package scope.examples

/**
 * Demonstrates `leak(scopedValue)` for third-party library interop.
 *
 * Sometimes you must pass scoped resources to legacy or third-party libraries
 * that require raw types. The `leak` escape hatch extracts the underlying
 * value, bypassing compile-time safety. Use sparingly—you assume responsibility
 * for ensuring the resource outlives its usage.
 */

// ---------------------------------------------------------------------------
// Fake classes simulating a legacy networking library
// ---------------------------------------------------------------------------

/** Configuration for establishing a socket connection. */
case class SocketConfig(host: String, port: Int)

/**
 * A managed socket that implements AutoCloseable.
 *
 * In a real scenario, this would wrap an actual network socket.
 */
class ManagedSocket(val config: SocketConfig) extends AutoCloseable {
  private var closed = false

  def send(data: Array[Byte]): Unit =
    if (closed) throw new IllegalStateException("Socket closed")
    else println(s"  [Socket] Sent ${data.length} bytes to ${config.host}:${config.port}")

  def receive(): Array[Byte] =
    if (closed) throw new IllegalStateException("Socket closed")
    else {
      println(s"  [Socket] Received response from ${config.host}:${config.port}")
      "ACK".getBytes
    }

  override def close(): Unit = {
    closed = true
    println(s"  [Socket] Connection to ${config.host}:${config.port} closed")
  }
}

/**
 * Simulates a third-party protocol handler that cannot be modified.
 *
 * This legacy library requires a raw `ManagedSocket` and does not understand
 * scoped types. This is the typical scenario where `leak` becomes necessary.
 */
object LegacyProtocolHandler {

  /**
   * Handles a connection using a proprietary protocol.
   *
   * @param socket
   *   the raw socket—must remain open for the duration of this call
   */
  def handleConnection(socket: ManagedSocket): Unit = {
    println("  [Legacy] Starting proprietary protocol handshake...")
    socket.send("HELLO".getBytes)
    val response = socket.receive()
    println(s"  [Legacy] Handshake complete: ${new String(response)}")
  }
}

// ---------------------------------------------------------------------------
// Example entry point
// ---------------------------------------------------------------------------

@main def legacyLibraryInteropExample(): Unit = {
  println("=== Legacy Library Interop Example ===\n")
  println("Demonstrating leak() for passing scoped resources to third-party code.\n")

  Scope.global.scoped { scope =>

    // Allocate the socket as a scoped resource.
    // The socket is tagged with the scope's type, preventing accidental escape.
    val scopedSocket: $[ManagedSocket] = allocate(
      Resource.fromAutoCloseable(new ManagedSocket(SocketConfig("api.example.com", 443)))
    )
    println("Allocated scoped socket.\n")

    // -------------------------------------------------------------------------
    // WARNING: leak() bypasses compile-time safety guarantees!
    //
    // After calling leak(), the compiler cannot prevent you from:
    //   - Storing the socket in a field that outlives the scope
    //   - Passing it to code that might cache or close it unexpectedly
    //   - Using it after the scope has closed
    //
    // Only use leak() when:
    //   1. The third-party API genuinely cannot accept scoped types
    //   2. You can guarantee the scope outlives all usage of the leaked value
    //   3. The third-party code won't cache or transfer ownership
    // -------------------------------------------------------------------------

    // WARNING: leak() bypasses compile-time safety — use only for third-party interop.
    // This intentionally escapes the scoped type and will emit a compiler warning.
    @nowarn("msg=.*leaked.*|.*leak.*")
    val rawSocket: ManagedSocket = leak(scopedSocket)

    println("Passing raw socket to legacy protocol handler:")
    LegacyProtocolHandler.handleConnection(rawSocket)

    println("\nScope exiting - socket will be closed automatically:")
  }

  println("\nExample complete. The socket was safely closed when the scope exited.")
}
```

([source](https://github.com/zio/zio-blocks/blob/main/scope-examples/src/main/scala/scope/examples/LegacyLibraryInteropExample.scala))

```bash
sbt "scope-examples/runMain scope.examples.legacyLibraryInteropExample"
```

### Integration Testing with Automatic Setup and Teardown

This example shows how to use Scope to manage test fixtures and resources in integration tests, ensuring automatic cleanup between test runs and proper resource finalization.

```scala title="scope-examples/src/main/scala/scope/examples/IntegrationTestHarnessExample.scala" 
/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package scope.examples

/**
 * Demonstrates combining DI-wired services with manually-managed test fixtures.
 *
 * This example shows a realistic integration test setup where:
 *   - Application services are wired via [[Resource.from]] (DI approach)
 *   - Test fixtures are managed with [[Resource.acquireRelease]] (manual
 *     approach)
 *   - Both resource types are properly cleaned up in LIFO order
 */
object IntegrationTestHarnessExample {

  // --- Test Infrastructure ---

  /** Configuration for the test environment. */
  case class TestConfig(dbUrl: String, serverPort: Int)

  /** Test database with lifecycle hooks for setup/teardown and data seeding. */
  class TestDatabase(val config: TestConfig) extends AutoCloseable {
    private var data = Map.empty[String, Any]

    def setup(): Unit                         = println(s"  [DB] Initialized at ${config.dbUrl}")
    def teardown(): Unit                      = { data = Map.empty; println("  [DB] Data cleared") }
    def seed(newData: Map[String, Any]): Unit = {
      data = newData; println(s"  [DB] Seeded with ${newData.size} entries")
    }
    def query(key: String): Option[Any] = data.get(key)
    def close(): Unit                   = println("  [DB] Connection closed")
  }

  /** Test HTTP server that can be started and stopped. */
  class TestServer(val config: TestConfig) extends AutoCloseable {
    val baseUrl: String = s"http://localhost:${config.serverPort}"

    def start(): Unit = println(s"  [Server] Started at $baseUrl")
    def stop(): Unit  = println("  [Server] Stopped")
    def close(): Unit = println("  [Server] Resources released")
  }

  /** Aggregates test fixtures for convenient access during tests. */
  case class TestFixture(db: TestDatabase, server: TestServer)

  // --- Application Under Test ---

  /** The application being tested; requires a database connection. */
  class AppUnderTest(val db: TestDatabase) extends AutoCloseable {
    def handleRequest(req: String): String = db.query(req).map(_.toString).getOrElse("Not found")
    def close(): Unit                      = println("  [App] Shutdown complete")
  }

  // --- Resource Definitions ---

  /**
   * Creates a manually-managed test fixture using [[Resource.acquireRelease]].
   *
   * This approach gives explicit control over setup and teardown phases, which
   * is typical for test fixtures that need initialization beyond construction.
   */
  def testFixtureResource(config: TestConfig): Resource[TestFixture] =
    Resource.acquireRelease {
      println("  [Fixture] Acquiring test fixture...")
      val db     = new TestDatabase(config)
      val server = new TestServer(config)
      db.setup()
      server.start()
      TestFixture(db, server)
    } { fixture =>
      println("  [Fixture] Releasing test fixture...")
      fixture.server.stop()
      fixture.db.teardown()
      fixture.db.close()
      fixture.server.close()
    }

  /**
   * Runs the integration test harness example.
   *
   * Demonstrates:
   *   1. Manual fixture via [[Resource.acquireRelease]] for test infrastructure
   *   2. DI-wired application via [[Resource.from]] consuming the fixture
   *   3. Proper cleanup ordering: app closes before fixtures
   */
  def run(): Unit = {
    println("=== Integration Test Harness Example ===\n")

    val config = TestConfig("jdbc:h2:mem:test", 8080)

    // Combine manual fixtures with DI-wired application
    val testHarnessResource: Resource[(TestFixture, AppUnderTest)] =
      testFixtureResource(config).flatMap { fixture =>
        // Seed test data
        fixture.db.seed(Map("user:1" -> "Alice", "user:2" -> "Bob"))

        // Wire the app using DI, injecting the fixture's database
        val appWire     = Wire.shared[AppUnderTest]
        val dbWire      = Wire(fixture.db)
        val appResource = Resource.from[AppUnderTest](appWire, dbWire)

        appResource.map(app => (fixture, app))
      }

    // Run in a scoped block - all resources cleaned up on exit
    Scope.global.scoped { scope =>

      println("Allocating resources...")
      val harness: $[(TestFixture, AppUnderTest)] = allocate(testHarnessResource)
      println()

      // Run test scenarios - access the tuple via $
      println("Running test scenarios:")
      $(harness) { h =>
        println(s"  GET user:1 -> ${h._2.handleRequest("user:1")}")
        println(s"  GET user:2 -> ${h._2.handleRequest("user:2")}")
        println(s"  GET user:3 -> ${h._2.handleRequest("user:3")}")
        println(s"  Server URL: ${h._1.server.baseUrl}")
      }
      println()

      println("Scope closing, releasing resources in LIFO order...")
    }

    println("\n=== Example Complete ===")
  }
}

@main def runIntegrationTestHarness(): Unit = IntegrationTestHarnessExample.run()
```

([source](https://github.com/zio/zio-blocks/blob/main/scope-examples/src/main/scala/scope/examples/IntegrationTestHarnessExample.scala))

```bash
sbt "scope-examples/runMain scope.examples.IntegrationTestHarnessExample"
```
