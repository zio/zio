# Resource

> `Resource[A]` is a **lazy recipe for managing resource lifecycles**, encapsulating both acquisition and finalization tied to a `Scope`. Resources describe *what* to do, not *when* — creation only happens when a resource is passed to `scope.allocate()`. They compose naturally with `map`, `flatMap`, and `zip` to build complex dependency graphs with automatic cleanup in LIFO order.

`Resource[A]` is a **lazy recipe for managing resource lifecycles**, encapsulating both acquisition and finalization tied to a `Scope`. Resources describe *what* to do, not *when* — creation only happens when a resource is passed to `scope.allocate()`. They compose naturally with `map`, `flatMap`, and `zip` to build complex dependency graphs with automatic cleanup in LIFO order.

```scala
sealed trait Resource[+A] {
  def map[B](f: A => B): Resource[B]
  def flatMap[B](f: A => Resource[B]): Resource[B]
  def zip[B](that: Resource[B]): Resource[(A, B)]
}
```

Key properties:
- **Lazy**: Resources don't acquire anything until allocated via `scope.allocate()`
- **Covariant**: `Resource[Dog]` is a subtype of `Resource[Animal]` when `Dog <: Animal`
- **Composable**: `map`, `flatMap`, and `zip` combine resources into larger structures
- **Two strategies**: Shared (memoized with reference counting) and Unique (fresh per allocation)
- **Auto-cleanup**: Finalizers run automatically in LIFO order when scopes close

## Motivation

Without Resources, managing complex initialization and cleanup is tedious and error-prone. Resources eliminate manual bookkeeping by tying value lifecycles to Scopes and registering finalizers automatically.

**Benefits:**
- Automatic cleanup even on exceptions
- LIFO finalization order (inner resources close before outer ones)
- Compositional: build complex dependency graphs declaratively
- Type-safe: compiler ensures you have dependencies available
- Works seamlessly with `Wire` for constructor-based dependency injection

## Installation

Add the ZIO Blocks Scope module to your `build.sbt`:

```scala
libraryDependencies += "dev.zio" %% "zio-blocks-scope" % "0.0.33"
```

For cross-platform (Scala.js):

```scala
libraryDependencies += "dev.zio" %%% "zio-blocks-scope" % "0.0.33"
```

Supported Scala versions: 2.13.x and 3.x.

## Shared and Unique Resources

Resource offers two distinct types for managing instance lifecycles: **Shared** for singleton-like resources that are allocated once and reused across multiple scopes, and **Unique** for fresh resources created on each allocation. Understanding when to use each type is critical for building efficient and correct resource-management architectures.

### Comparison

| Aspect             | Shared                                             | Unique                                    |
|--------------------|----------------------------------------------------|-------------------------------------------|
| **Creation**       | `Resource.shared(f)`                               | `Resource.unique(f)` or `Resource(value)` |
| **Memoization**    | Yes, with reference counting                       | No, fresh per allocation                  |
| **When to use**    | Expensive resources (DB connections, thread pools) | Per-request state, isolated handlers      |
| **Instance reuse** | Same instance across all allocations               | New instance per allocation               |
| **Finalization**   | Runs when last reference released                  | Runs immediately when scope closes        |
| **Thread safety**  | Lock-free atomic reference counting                | Per-scope, no global coordination needed  |

### Shared Resources

**Shared resources are memoized**: the first allocation initializes the instance; subsequent allocations return the same reference with automatic reference counting. Finalizers run only when the last reference is released. Use shared resources for **expensive, globally singular components** — database connection pools, thread pools, logging systems, caches, and other heavyweight infrastructure that should exist exactly once for the lifetime of the application.

The canonical example is a **database connection pool**. Building a fresh pool for each service layer is wasteful and defeats pooling's purpose. Instead, wrap the pool in a shared resource: both the user service and order service allocate the same pool instance, with the system automatically tracking references and closing the pool only when the last service releases it.

Here's what shared acquisition looks like:

```scala
object Resource {
  def shared[A](f: Scope => A): Resource[A]
}
```

When you allocate a shared resource, reference counting ensures cleanup happens exactly once:

```scala

class DatabasePool extends AutoCloseable {
  def close(): Unit = println("Pool closed (after all services released)")
}

val poolResource = Resource.shared { scope =>
  println("Creating database pool (first allocation only)")
  new DatabasePool
}

// Allocate in ServiceA
Scope.global.scoped { scopeA =>

  val poolA: $[DatabasePool] = poolResource.allocate
  println("ServiceA allocated pool")

  // Allocate in ServiceB within a nested scope
  scopeA.scoped { scopeB =>

    val poolB: $[DatabasePool] = poolResource.allocate
    println("ServiceB allocated same pool instance (reference count incremented)")

    // Both services share the same underlying pool instance
    println("Both ServiceA and ServiceB are using the same pool instance")
  }
  println("ServiceB released, but pool stays open for ServiceA (ref count -= 1)")
}
println("All services released, pool closed (ref count == 0)")
```

### Unique Resources

**Unique resources create fresh instances each time**: every allocation produces a new value. Finalizers run when their owning scope closes, independent of other allocations. Use unique resources for **per-request state, isolated services, and stateful handlers** — anything that should never be shared because it encapsulates request-specific or scope-specific data.

A typical scenario is **per-request caches**: each API request gets its own cache instance to avoid one request polluting another's cached data. Similarly, stateful handlers (parser state machines, transaction contexts, event buffers) need isolation to prevent cross-contamination.

Here's what unique acquisition looks like:

```scala
object Resource {
  def unique[A](f: Scope => A): Resource[A]
}
```

When you allocate a unique resource, each allocation is independent:

```scala

class RequestCache extends AutoCloseable {
  def close(): Unit = println("Request cache closed")
}

val cacheResource = Resource.unique { scope =>
  println("Creating new request cache")
  new RequestCache
}

// Two allocations in the same scope produce different instances
Scope.global.scoped { scope =>

  println("Creating first cache...")
  val cache1: $[RequestCache] = cacheResource.allocate

  println("Creating second cache...")
  val cache2: $[RequestCache] = cacheResource.allocate

  println("Both caches are independent instances")
}
```

### Diamond Dependency Pattern

A classic architecture uses shared resources to solve **diamond dependencies**, where multiple services depend on the same expensive component. Consider an e-commerce application where both `ProductService` and `OrderService` depend on `Logger`:

```
       CachingApp
        /      \
       /        \
  ProductService  OrderService
       \        /
        \      /
         Logger
```

Without shared resources, each service would receive a different `Logger` instance. With `Resource.shared`, both services automatically receive the same singleton instance:

```scala

class Logger extends AutoCloseable {
  def log(msg: String): Unit = println(s"LOG: $msg")
  def close(): Unit = println("Logger closed")
}

class ProductService(val logger: Logger) {
  def findProduct(id: String): String = {
    logger.log(s"Finding product $id")
    s"Product-$id"
  }
}

class OrderService(val logger: Logger) {
  def createOrder(productId: String): String = {
    logger.log(s"Creating order for $productId")
    s"ORD-123"
  }
}

class CachingApp(val productService: ProductService, val orderService: OrderService) extends AutoCloseable {
  def close(): Unit = println("App closed")
}

Scope.global.scoped { scope =>

  // Use Wire.shared[Logger] to ensure only one instance is created
  val app: $[CachingApp] = allocate(
    Resource.from[CachingApp](
      Wire.shared[Logger]  // Both services get the same Logger instance
    )
  )

  $(app) { a =>
    println(s"ProductService and OrderService share Logger? ${a.productService.logger eq a.orderService.logger}")
    a.productService.findProduct("P001")
    a.orderService.createOrder("P001")
  }
}
```

In this pattern, `Resource.shared` (via `Wire.shared`) guarantees a single `Logger` instance across all services, eliminating duplication while maintaining proper cleanup semantics.

### Multiple Shared Dependencies

Most applications need more than one shared resource. Here's a realistic example where `CachingApp` depends on both a shared `Logger` and a shared `MetricsCollector`. Both resources are singletons that all services share:

```
          CachingApp
           /      \
          /        \
         /          \
    ProductService  OrderService
     /  \            /  \
    /    \          /    \
   Logger Metrics  Logger Metrics
    \    /          \    /
     \  /            \  /
      \/              \/
   (shared singleton instances)
```

Here's the implementation:

```scala

class Logger extends AutoCloseable {
  def log(msg: String): Unit = println(s"[LOG] $msg")
  def close(): Unit = println("[Logger] Closed")
}

class MetricsCollector extends AutoCloseable {
  private var eventCount = 0
  def recordEvent(name: String): Unit = {
    eventCount += 1
    println(s"[METRICS] Event: $name (total: $eventCount)")
  }
  def close(): Unit = println(s"[MetricsCollector] Closed after $eventCount events")
}

class ProductService(val logger: Logger, val metrics: MetricsCollector) {
  def findProduct(id: String): String = {
    logger.log(s"Finding product $id")
    metrics.recordEvent("product.find")
    s"Product-$id"
  }
}

class OrderService(val logger: Logger, val metrics: MetricsCollector) {
  def createOrder(productId: String): String = {
    logger.log(s"Creating order for $productId")
    metrics.recordEvent("order.create")
    s"ORD-123"
  }
}

class CachingApp(
  val productService: ProductService,
  val orderService: OrderService
) extends AutoCloseable {
  def close(): Unit = println("[CachingApp] Closed")
}

Scope.global.scoped { scope =>

  // Both Logger and MetricsCollector are shared (singleton instances)
  val app: $[CachingApp] = allocate(
    Resource.from[CachingApp](
      Wire.shared[Logger],              // One Logger for all services
      Wire.shared[MetricsCollector]     // One MetricsCollector for all services
    )
  )

  $(app) { a =>
    println(s"ProductService and OrderService share Logger? ${a.productService.logger eq a.orderService.logger}")
    println(s"ProductService and OrderService share Metrics? ${a.productService.metrics eq a.orderService.metrics}")

    a.productService.findProduct("P001")
    a.orderService.createOrder("P001")
  }
}
```

When this scope closes, both the `Logger` and `MetricsCollector` are finalized exactly once, regardless of how many services reference them. The macro automatically builds a dependency graph, verifies all wires, and ensures LIFO cleanup order.

## Construction

Resources can be created in several ways: from values, from explicit acquire/release pairs, from `AutoCloseable` types, from custom functions, or derived automatically from a type's constructor using the `Resource.from` macros.

### `Resource.apply` — Wrap a Value

Wraps a by-name value as a resource. If the value implements `AutoCloseable`, its `close()` method is automatically registered as a finalizer:

```scala
object Resource {
  def apply[A](value: => A): Resource[A]
}
```

Here's how to use it:

```scala

case class Config(debug: Boolean)

class Database(val name: String) extends AutoCloseable {
  def close(): Unit = println(s"Closing database $name")
}

val configResource = Resource(Config(debug = true))
val dbResource = Resource(new Database("mydb"))
```

### `Resource.acquireRelease` — Explicit Lifecycle

Creates a resource with separate acquire and release functions. The acquire thunk runs during allocation; the release function is registered as a finalizer:

```scala
object Resource {
  def acquireRelease[A](acquire: => A)(release: A => Unit): Resource[A]
}
```

Here's an example with file handling:

```scala

val fileResource = Resource.acquireRelease {
  new FileInputStream("data.txt")
} { stream =>
  stream.close()
}
```

### `Resource.fromAutoCloseable` — Type-Safe Wrapping

Creates a resource specifically for `AutoCloseable` subtypes. This is a compile-time verified alternative to `Resource(value)` when you know the value is closeable:

```scala
object Resource {
  def fromAutoCloseable[A <: AutoCloseable](value: => A): Resource[A]
}
```

Here's an example with a stream:

```scala

val streamResource = Resource.fromAutoCloseable {
  new BufferedInputStream(new FileInputStream("data.bin"))
}
```

### `Resource.shared` — Memoized with Reference Counting

Creates a shared resource that memoizes its value across multiple allocations using reference counting. The first allocation initializes the value using an `OpenScope` parented to `Scope.global`; subsequent allocations increment the reference count and return the same instance. Each scope that receives the shared value registers a finalizer that decrements the count. When the count reaches zero, the shared scope closes automatically. This mechanism is **thread-safe and lock-free**, implemented using `AtomicReference` with atomic compare-and-swap operations to avoid contention.

Under the hood, a shared resource progresses through four states: (1) **Uninitialized** — the resource has never been allocated and the first call triggers initialization; (2) **Pending** — initialization is in progress and other threads wait via spin-yield for the value to become available; (3) **Created** — the value is fully initialized and ready, each allocation increments the reference count, and each scope registers a finalizer to decrement it; (4) **Destroyed** — the reference count reached zero, the resource was cleaned up, and further allocations will fail. This state machine ensures that no matter how many threads try to allocate simultaneously, the underlying value initializes exactly once, and cleanup happens exactly once when the last reference is released.

Use shared resources for **expensive, singleton-like components** (database connection pools, thread pools, logging systems, caches) that should exist exactly once for the lifetime of the application, even when multiple services depend on them.

Here's the signature:

```scala
object Resource {
  def shared[A](f: Scope => A): Resource[A]
}
```

Here's a realistic example showing reference counting and automatic cleanup:

```scala

class ExpensiveComponent extends AutoCloseable {
  println("ExpensiveComponent initialized (expensive operation)")
  def close(): Unit = println("ExpensiveComponent cleaned up (last reference released)")
}

val sharedResource = Resource.shared { scope =>
  new ExpensiveComponent()
}

// Multiple services allocating the same shared resource
Scope.global.scoped { scope =>

  // First allocation initializes the component
  println("ServiceA allocating...")
  val componentA: $[ExpensiveComponent] = sharedResource.allocate

  // ServiceB in a nested scope receives the same instance
  scope.scoped { innerScope =>

    println("ServiceB allocating (same instance, ref count += 1)...")
    val componentB: $[ExpensiveComponent] = sharedResource.allocate
    println("Both services have the same instance")
  }

  println("ServiceB released, but component stays alive (ref count -= 1)")
}

println("ServiceA released, component cleaned up (ref count == 0)")
```

### `Resource.unique` — Fresh Instances

Creates a unique resource that produces a fresh instance each time it's allocated. Use for per-request state or resources that should never be shared:

```scala
object Resource {
  def unique[A](f: Scope => A): Resource[A]
}
```

Here's an example showing fresh instances:

```scala

var counter = 0

val uniqueResource = Resource.unique[Int] { _ =>
  counter += 1
  counter
}
```

### `Resource.from[T]` — Derive from Constructor

Derives a `Resource[T]` from `T`'s primary constructor, requiring no external dependencies.
If `T` extends `AutoCloseable`, `Resource.from` automatically registers its `close()` method
as a finalizer:

```scala
object Resource {
  def from[T]: Resource[T]
}
```

To derive a resource for a type that has no constructor dependencies:

```scala

class MetricsCollector extends AutoCloseable {
  def record(event: String): Unit = println(s"Recording: $event")
  def close(): Unit               = println("MetricsCollector closed")
}

val metricsResource = Resource.from[MetricsCollector]
```

Internally, the macro inspects `T`'s constructor at compile time, verifies that no external
dependencies are needed, and synthesizes a `Resource.shared` that instantiates `T` and —
when `T` extends `AutoCloseable` — registers `close()` as a scope finalizer. Any attempt
to use `Resource.from[T]` on a type with unsatisfied constructor parameters results in a
compile-time error.

### `Resource.from[T](wires: Wire[?, ?]*)` — Derive with Dependency Overrides

Derives a `Resource[T]` from `T`'s constructor, with `Wire` values provided as dependency
overrides. Any dependency not covered by an explicit wire is auto-derived if the macro can
construct it; otherwise a compile-time error is produced:

```scala
object Resource {
  def from[T](wires: Wire[?, ?]*): Resource[T]
}
```

To derive a resource for a type whose dependencies are provided via wires:

```scala

case class Config(host: String, port: Int)

class Logger(config: Config) extends AutoCloseable {
  def log(msg: String): Unit = println(s"[$msg] ${config.host}:${config.port}")
  def close(): Unit          = log("Logger shutting down")
}

class Service(logger: Logger) extends AutoCloseable {
  def run(): Unit  = logger.log("Service running")
  def close(): Unit = logger.log("Service shutting down")
}

val serviceResource = Resource.from[Service](
  Wire(Config("localhost", 8080))
)
```

Internally, the macro builds a complete dependency graph at compile time: it inspects each
provided wire's input and output types, identifies all constructor parameters of `T`, and
auto-derives wires for any remaining dependencies. It then topologically sorts all wires to
determine acquisition order and composes them into a single `Resource` whose finalizers run
in LIFO order — inner dependencies close before outer ones.

## Core Operations

Resources support transformation and composition through `map`, `flatMap`, and `zip`.

### `Resource#map` — Transform the Value

Transforms the value produced by a resource without affecting finalization. The transformation function is applied after the resource is acquired:

```scala
trait Resource[+A] {
  def map[B](f: A => B): Resource[B]
}
```

Here's a usage example:

```scala

val portResource = Resource(8080)
val urlResource = portResource.map(port => s"http://localhost:$port")
```

### `Resource#flatMap` — Sequence Resources

Sequences two resources, using the result of the first to create the second. Both sets of finalizers are registered and run in LIFO order (inner before outer):

```scala
trait Resource[+A] {
  def flatMap[B](f: A => Resource[B]): Resource[B]
}
```

Here's an example with dependent resources:

```scala

case class DbConfig(url: String)

class Database(config: DbConfig) extends AutoCloseable {
  def query(sql: String): String = s"Result from ${config.url}: $sql"
  def close(): Unit = println("Database closed")
}

val configResource = Resource(DbConfig("jdbc:postgres://localhost"))
val dbResource = configResource.flatMap { config =>
  Resource.fromAutoCloseable(new Database(config))
}
```

### `Resource#zip` — Combine Resources

Combines two resources into a single resource that produces a tuple of both values. Both resources are acquired and both sets of finalizers are registered:

```scala
trait Resource[+A] {
  def zip[B](that: Resource[B]): Resource[(A, B)]
}
```

Here's an example combining multiple resources:

```scala

case class DbConfig(url: String)

class Database(config: DbConfig) extends AutoCloseable {
  def close(): Unit = println("Database closed")
}

class Cache extends AutoCloseable {
  def close(): Unit = println("Cache closed")
}

val dbResource = Resource.fromAutoCloseable(new Database(DbConfig("jdbc:postgres://localhost")))
val cacheResource = Resource.fromAutoCloseable(new Cache())
val combined = dbResource.zip(cacheResource)
```

### `Resource#allocate` — Acquire Within a Scope

Allocates a `Resource[A]` within the current `Scope`, returning a scoped `$[A]`. This is syntax sugar for `scope.allocate(resource)`, available via `import scope._` inside a `scoped` block:

```scala
implicit class ResourceOps[A](private val r: Resource[A]) {
  def allocate: $[A]
}
```

To allocate a resource and use its value inside a scope:

```scala

class Database extends AutoCloseable {
  def query(sql: String): String = s"Result: $sql"
  def close(): Unit              = println("Database closed")
}

Scope.global.scoped { implicit scope =>

  val db: $[Database] = Resource.fromAutoCloseable(new Database).allocate
  $(db)(_.query("SELECT 1"))
}
```

### `$[Resource[A]]#allocate` — Allocate a Scoped Resource

Allocates a `$[Resource[A]]` — a Resource that is itself a scoped value — returning `$[A]`. Use this when a method on a scoped object returns a Resource and you need to immediately acquire it while keeping the result scoped.

**What is a scoped resource?** A scoped value `$[A]` represents an `A` that is valid only while a scope is alive. A **scoped resource** `$[Resource[A]]` is a Resource object that exists *inside* that scope. When you call a method like `$(pool)(_.lease())` that returns a Resource, the result is typed as `$[Resource[Connection]]` — the Resource itself is scoped. The `.allocate` extension method unwraps this scoped resource and acquires it, returning the acquired value as a new scoped value `$[A]`.

**Motivation:** This pattern appears frequently in resource factories. A scoped object (like a database pool) has methods that produce new Resources. Without this extension, you'd need to unwrap the `$[Resource[A]]` from the `$` context, allocate it separately, and re-wrap the result. The `.allocate` method chains naturally, letting you write `$(pool)(_.lease()).allocate` instead of dealing with intermediate unwrapping.

The implicit class:

```scala
implicit class ScopedResourceOps[A](private val sr: $[Resource[A]]) {
  def allocate: $[A]
}
```

Here's a realistic example where a database pool (a scoped object) has a method that returns a Resource for individual connections:

```scala

class Connection extends AutoCloseable {
  def query(sql: String): String = s"Result: $sql"
  def close(): Unit              = println("Connection closed")
}

class Pool extends AutoCloseable {
  def lease(): Resource[Connection] = Resource.fromAutoCloseable(new Connection)
  def close(): Unit                 = println("Pool closed")
}

Scope.global.scoped { implicit scope =>

  val pool: $[Pool]           = Resource.fromAutoCloseable(new Pool).allocate
  val conn: $[Connection]     = $(pool)(_.lease()).allocate
  $(conn)(_.query("SELECT 1"))
}
```

## Integration

Resource is a core abstraction in ZIO Blocks' resource management ecosystem:

- **[`Scope`](./scope.md)** — Resources require a `Scope` for allocation. The `Scope` manages the lifetime of acquired resources and automatically runs finalizers when the scope closes.
- **[`Wire`](./wire.md)** — The `Resource.from[T](wires)` macro builds dependency graphs using `Wire` values, enabling constructor-based dependency injection with automatic resource management.
- **[`Finalizer`](./finalizer.md)** — Resources register their cleanup logic with `Finalizer` objects, which execute in LIFO order when scopes close.

For example, `Resource.from[T]` uses `Wire` to construct instances with their dependencies, automatically registering any `AutoCloseable` cleanup:

```scala
val serviceResource = Resource.from[Service](
  Wire(Config("localhost", 8080))
)
```

This builds a complete dependency graph: `Config` → `Logger` → `Service`, with all finalizers managed by the containing `Scope`.

## Running the Examples

All code from this guide is available as runnable examples in the `scope-examples` module.

**1. Clone the repository and navigate to the project:**

```bash
git clone https://github.com/zio/zio-blocks.git
cd zio-blocks
```

**2. Run individual examples with sbt:**

### Basic Lifecycle Management with Temporary Files

This example demonstrates creating and automatically cleaning up temporary files using Resource's lifecycle management. It shows how Resource ensures files are closed even if exceptions occur:

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

Run this example with:

```bash
sbt "scope-examples/runMain scope.examples.tempFileHandlingExample"
```

### Acquiring and Releasing Database Connections

This example demonstrates the acquire-release pattern using Resource to manage database connections. It shows proper connection initialization and guaranteed cleanup:

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

Run this example with:

```bash
sbt "scope-examples/runMain scope.examples.runDatabaseExample"
```

### Shared Resources with Memoization and Reference Counting

This example demonstrates Resource.shared to create a singleton logger instance that is automatically cleaned up only when the last service releases it. Shows reference counting in action:

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

Run this example with:

```bash
sbt "scope-examples/runMain scope.examples.runCachingExample"
```

### Managing Shared Expensive Resources

This example demonstrates using Resource.shared for a database connection pool—an expensive resource that should exist exactly once. Shows how multiple services safely share the same pool instance with automatic cleanup:

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

Run this example with:

```bash
sbt "scope-examples/runMain scope.examples.connectionPoolExample"
```

### Transactional Resource Management

This example demonstrates combining Resource with transaction boundaries. Shows how to manage resources (connections, transactions) that must be coordinated across scopes with proper rollback on failure:

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

Run this example with:

```bash
sbt "scope-examples/runMain scope.examples.runTransactionBoundaryExample"
```

### Multi-Layer Service Construction

This example demonstrates Resource.from macro to automatically build a complex dependency graph with multiple services. Shows automatic wiring of constructor dependencies and cleanup in correct LIFO order:

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
