# Wire

> `Wire[-In, +Out]` is a **compile-time safe recipe for constructing a service and its dependencies**. Wires describe how to construct an `Out` value given access to its dependencies via a `Context[In]` and a `Scope` for finalization:

`Wire[-In, +Out]` is a **compile-time safe recipe for constructing a service and its dependencies**. Wires describe how to construct an `Out` value given access to its dependencies via a `Context[In]` and a `Scope` for finalization:

```scala
sealed trait Wire[-In, +Out] {
  def isShared: Boolean
  def isUnique: Boolean = !isShared

  def shared: Wire.Shared[In, Out]
  def unique: Wire.Unique[In, Out]

  def toResource(deps: Context[In]): Resource[Out]
}
```

The type parameters are:
- **`In` (contravariant)**: the dependencies required to construct the service
- **`Out` (covariant)**: the service produced

Wires are the building blocks of dependency injection in Scope. They form the foundation for constructor-based dependency injection via `Resource.from[T](wires*)`.

## Overview

A `Wire` is a **lazy recipe**, not an execution. It holds a construction function `(Scope, Context[In]) => Out` and a **sharing strategy** — either `Shared` (reference-counted via `Resource.shared`) or `Unique` (fresh instance per allocation). The Wire itself does nothing until you convert it to a `Resource` and allocate it within a scope.

Here's the typical flow:

```
Wire (recipe)
  ↓
Resource (lazy, composable)
  ↓
scope.allocate(...)
  ↓
$[T] (scoped value in scope)
```

## Motivation

Without Wires, building a multi-layer application requires manual dependency passing:

```scala
final case class Config(dbUrl: String)

final class Database(config: Config) extends AutoCloseable {
  def close(): Unit = println(s"closing connection to ${config.dbUrl}")
}

final class UserService(db: Database) {
  def getUser(id: Int): String = s"user $id"
}

final class App(service: UserService) {
  def run(): Unit = println(service.getUser(1))
}

// Manual wiring:
Scope.global.scoped { scope =>

  val config = Config("jdbc:postgres://localhost/db")
  val db = Resource.fromAutoCloseable(new Database(config)).allocate
  val service = new UserService($(db)(identity))
  val app = new App(service)
  app.run()
}
```

With `Wire` + `Resource.from`, the macro handles the dependency graph:

```scala
Scope.global.scoped { scope =>

  val app = Resource.from[App](
    Wire(Config("jdbc:postgres://localhost/db"))
  ).allocate
  $(app)(_.run())
}
```

**Benefits:**
- **Compile-time graph validation** — cycle detection, duplicate providers, missing dependencies
- **Automatic finalization** — `AutoCloseable` resources are finalized in LIFO order
- **Sharing control** — choose which services are singletons (shared) vs fresh per allocation (unique)
- **Type-safe construction** — no stringly-typed dependency resolution

## Installation

Add the following dependency to your `build.sbt`:

```scala
libraryDependencies += "dev.zio" %% "zio-blocks-scope" % "0.0.33"
```

For cross-platform (Scala.js):

```scala
libraryDependencies += "dev.zio" %%% "zio-blocks-scope" % "0.0.33"
```

Supported Scala versions: 2.13.x and 3.x.

## Construction

`Wire` provides multiple ways to create instances, ranging from automatic macro-based derivation to manual construction for custom logic. Here are the main construction methods:

### Wire.shared[T] — derive a shared wire

The `Wire.shared[T]` macro inspects `T`'s primary constructor and generates a shared wire that reuses the same instance across dependents:

```scala

final case class Config(debug: Boolean)

final class Database(config: Config) extends AutoCloseable {
  def query(sql: String): String = s"[db] $sql"
  def close(): Unit = println("database closed")
}

val wire: Wire.Shared[Config, Database] = Wire.shared[Database]

Scope.global.scoped { scope =>

  val config = Config(debug = true)
  val deps = Context[Config](config)
  val db = allocate(wire.toResource(deps))
  $(db)(_.query("SELECT 1"))
}
```

### Wire.unique[T] — derive a unique wire

Like `Wire.shared[T]`, but creates a fresh instance each time the wire is used. Use for request-scoped or per-call services:

```scala

final class RequestHandler {
  val id = scala.util.Random.nextInt()
}

val wire: Wire.Unique[Any, RequestHandler] = Wire.unique[RequestHandler]

Scope.global.scoped { scope =>

  val deps = Context.empty[Any]
  val resource = wire.toResource(deps)

  val h1 = allocate(resource)
  val h2 = allocate(resource)

  val ids: (Int, Int) = (
    $(h1)(_.id),
    $(h2)(_.id)
  )
  // ids._1 != ids._2 (different instances)
}
```

### Wire.apply[T] — lift a pre-existing value

Creates a shared wire that injects a value you already have. If the value is `AutoCloseable`, its `close()` method is automatically registered as a finalizer:

```scala

final case class Config(dbUrl: String)

val config = Config("jdbc:postgres://localhost/db")
val wire: Wire.Shared[Any, Config] = Wire(config)

Scope.global.scoped { scope =>

  val cfg = allocate(wire.toResource(Context.empty[Any]))
  $(cfg)(_.dbUrl)
}
```

### Wire.Shared.fromFunction — manual shared wire construction

Use this for custom construction logic when macro derivation doesn't fit:

```scala

final case class Config(timeout: Int)

final class Client(config: Config) {
  def call(): String = s"calling with timeout=${config.timeout}"
}

val wire: Wire.Shared[Config, Client] =
  Wire.Shared.fromFunction { (scope, ctx) =>
    val config = ctx.get[Config]
    new Client(config)
  }

Scope.global.scoped { scope =>

  val config = Config(30)
  val deps = Context[Config](config)
  val client = allocate(wire.toResource(deps))
  $(client)(_.call())
}
```

### Wire.Unique.fromFunction — manual unique wire construction

Like `Wire.Shared.fromFunction`, but for unique wires:

```scala

final class RequestContext {
  val id = scala.util.Random.nextInt()
}

val wire: Wire.Unique[Any, RequestContext] =
  Wire.Unique.fromFunction { (_, _) =>
    new RequestContext
  }

Scope.global.scoped { scope =>

  val deps = Context.empty[Any]
  val resource = wire.toResource(deps)

  val r1 = allocate(resource)
  val r2 = allocate(resource)

  val different: Boolean = $(r1)(_.id) != $(r2)(_.id)
  // different == true
}
```

## Shared vs Unique

The fundamental difference is **reuse semantics**:

| Aspect | Shared | Unique |
|--------|--------|--------|
| **Construction** | `Wire.shared[T]` macro or `Wire.Shared.fromFunction` | `Wire.unique[T]` macro or `Wire.Unique.fromFunction` |
| **Resource type** | `Resource.shared[T]` (reference-counted) | `Resource.unique[T]` (fresh per call) |
| **When to use** | Singletons, expensive resources (connections, thread pools) | Request scoped, stateful per-call (request handlers) |
| **Instance reuse** | Same instance across entire dependency graph | New instance per allocation |
| **Finalization** | Runs when last referencing scope closes | Runs when each scope closes |

In the diamond pattern (where `App` depends on both `UserService` and `OrderService`, both of which depend on `Database`), a shared wire ensures `Database` is constructed once and both services receive the same instance:

```scala

final class Database {
  val id = scala.util.Random.nextInt()
}

final class UserService(db: Database) {
  def getDbId(): Int = db.id
}

final class OrderService(db: Database) {
  def getDbId(): Int = db.id
}

final class App(userService: UserService, orderService: OrderService) {
  def check(): Boolean = {
    // With shared Database, these should be equal
    userService.getDbId() == orderService.getDbId()
  }
}

// Using shared wires for all dependencies
val resource = Resource.from[App](
  Wire.shared[Database],
  Wire.shared[UserService],
  Wire.shared[OrderService]
)

Scope.global.scoped { scope =>

  val app = resource.allocate
  $(app)(_.check())  // true: Database is shared
}
```

## Core Operations

The `Wire` interface provides a set of operations for inspecting and transforming wires:

### `Wire#isShared` and `Wire#isUnique`

Check the sharing strategy of a wire:

```scala
trait Wire[-In, +Out] {
  def isShared: Boolean
  def isUnique: Boolean = !isShared
}
```

Here's how to use these methods:

```scala

val sharedWire = Wire.shared[String]
val uniqueWire = Wire.unique[String]

println(s"sharedWire.isShared: ${sharedWire.isShared}")      // true
println(s"sharedWire.isUnique: ${sharedWire.isUnique}")      // false
println(s"uniqueWire.isShared: ${uniqueWire.isShared}")      // false
println(s"uniqueWire.isUnique: ${uniqueWire.isUnique}")      // true
```

### `Wire#shared` and `Wire#unique` — convert between strategies

Convert a wire to the opposite strategy:

```scala
trait Wire[-In, +Out] {
  def shared: Wire.Shared[In, Out]
  def unique: Wire.Unique[In, Out]
}
```

Here's how to convert between sharing strategies:

```scala

val original = Wire.shared[String]

val converted: Wire.Unique[Any, String] = original.unique
println(s"original.isShared: ${original.isShared}")          // true
println(s"converted.isUnique: ${converted.isUnique}")        // true

// Converting back returns a new shared wire
val backToShared: Wire.Shared[Any, String] = converted.shared
println(s"backToShared.isShared: ${backToShared.isShared}")  // true
```

Calling `shared` on an already-shared wire returns `this` (identity); likewise `unique` on a unique wire returns `this`.

### `Wire#toResource` — convert a wire to a Resource

Converts the wire to a lazy `Resource` by providing the dependency context:

```scala
trait Wire[-In, +Out] {
  def toResource(deps: Context[In]): Resource[Out]
}
```

Here's how to use this method:

```scala

final case class Config(value: String)

val wire = Wire.shared[Config]
val deps = Context[Config](Config("hello"))

val resource: Resource[Config] = wire.toResource(deps)

Scope.global.scoped { scope =>

  val cfg = allocate(resource)
  $(cfg)(_.value)
}
```

### `Wire#make` — construct directly from a wire

Directly construct a value without going through `Resource.toResource`. This is a low-level operation; prefer `allocate(wire.toResource(...))` for safety:

```scala
trait Wire.Shared[-In, +Out] {
  def make(scope: Scope, context: Context[In]): Out
}
```

Here's how to use this method:

```scala

final class Service {
  def getName: String = "service"
}

val wire = Wire.shared[Service]

Scope.global.scoped { scope =>

  val service = wire.asInstanceOf[Wire.Shared[Any, Service]].make(scope, Context.empty[Any])
  println(service.getName)
}
```

## Macro Derivation

When you call `Wire.shared[T]` or `Wire.unique[T]`, the macro performs these checks:

1. **Is `T` a class?** — traits and abstract classes are rejected; only concrete classes can be auto-wired.
2. **Does `T` have a primary constructor?** — the macro inspects constructor parameters to determine dependencies.
3. **Is each parameter either a dependency type or a special injected type?** — parameters of type `Scope` or `Finalizer` are recognized and injected automatically; others are looked up in the context.
4. **Does `T` extend `AutoCloseable`?** — if yes, `close()` is automatically registered as a finalizer in the scope.

Example with all three features:

```scala

final case class Config(dbUrl: String)

final class Logger(implicit finalizer: Finalizer) {
  def log(msg: String): Unit = println(msg)
}

final class Database(config: Config)(implicit scope: Scope) extends AutoCloseable {
  def connect(): Unit = {
    scope.defer(println("database connection closed"))
    println(s"connecting to ${config.dbUrl}")
  }

  def query(sql: String): String = s"result: $sql"

  def close(): Unit = println("database closed")
}

final class Service(db: Database, logger: Logger) {
  def run(): Unit = {
    logger.log(db.query("SELECT 1"))
  }
}

// Macro handles Finalizer injection, Scope injection, and AutoCloseable registration
val wire = Wire.shared[Service]
```

### What happens with subtype conflicts

If a constructor has dependencies of related types (e.g., both `FileInputStream` and `InputStream`), the macro rejects the wire because `Context` is type-indexed and cannot reliably disambiguate.

```scala
// This will NOT compile
final class App(input: InputStream, fileInput: FileInputStream)
val wire = Wire.shared[App]  // error: subtype conflict

// Fix: wrap one type to make it distinct
final case class FileInputWrapper(value: FileInputStream)
final class App(input: InputStream, fileInput: FileInputWrapper)
val wire = Wire.shared[App]  // ok
```

## Integration with Resource.from

`Wire` is designed for use with `Resource.from[T](wires*)`, which performs whole-graph dependency injection:

```scala

final case class AppConfig(dbUrl: String)

final class Database(config: AppConfig) extends AutoCloseable {
  def query(sql: String): String = s"result: $sql"
  def close(): Unit = ()
}

final class Repository(db: Database) {
  def query(): String = db.query("SELECT *")
}

final class Service(repo: Repository) extends AutoCloseable {
  def run(): String = repo.query()
  def close(): Unit = ()
}

final class App(service: Service) {
  def run(): String = service.run()
}

// Provide only the leaf dependency; Resource.from derives the rest
val appResource: Resource[App] = Resource.from[App](
  Wire(AppConfig("jdbc:postgres://localhost/db"))
)

Scope.global.scoped { scope =>

  val app = allocate(appResource)
  $(app)(_.run())
}
```

When `Resource.from` composes wires, it respects the sharing strategy:
- **Shared wires** → reference-counted (single instance in the graph)
- **Unique wires** → fresh per allocation

The macro detects cycles, duplicate providers, and missing dependencies at compile time.

## Comparison with Alternatives

| Feature | Wire | Manual Passing | Service Locator |
|---------|------|----------------|-----------------|
| **Type safety** | ✓ (compile-time validation) | ✓ (implicit) | ✗ (string keys) |
| **Cycle detection** | ✓ (compile time) | ✗ | ✗ (runtime) |
| **Sharing semantics** | ✓ (configurable) | Manual | ✓ (singleton pattern) |
| **Finalization** | ✓ (LIFO, automatic) | Manual | Manual |
| **Performance** | ~0 overhead (macro-generated) | ~0 overhead | ~1 allocation overhead |

## Running the Examples

All code from this guide is available as runnable examples in the `scope-examples` module.

**1. Clone the repository and navigate to the project:**

```bash
git clone https://github.com/zio/zio-blocks.git
cd zio-blocks
```

**2. Run individual examples with sbt:**

Basic wire construction demonstrates how to create and use `Wire` for dependency injection. View the example source code:

```scala title="scope-examples/src/main/scala/wire/WireBasicExample.scala" 
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

package wire

/**
 * Demonstrates basic Wire construction patterns:
 *   - Wire.shared[T] macro derivation
 *   - Wire.apply(value) to lift a value
 *   - Converting wires to Resources
 *   - Using wires in a dependency graph
 */

final case class DbConfig(host: String, port: Int) {
  def url: String = s"jdbc:postgresql://$host:$port/db"
}

final class Database(config: DbConfig) extends AutoCloseable {
  println(s"[Database] Connecting to ${config.url}")

  def query(sql: String): String = {
    println(s"[Database] Executing: $sql")
    "result"
  }

  def close(): Unit =
    println("[Database] Connection closed")
}

final class UserService(db: Database) {
  println("[UserService] Initialized")

  def getUser(id: Int): String = {
    db.query(s"SELECT * FROM users WHERE id = $id")
    s"User(id=$id, name=Alice)"
  }
}

final class BasicApp(service: UserService) {
  def run(): Unit = {
    val user = service.getUser(1)
    println(s"[App] Got: $user")
  }
}

@main def wireBasicExample(): Unit = {
  println("=== Wire Basic Construction Example ===\n")

  // Create the dependency leaf (config) using Wire.apply
  val configWire: Wire.Shared[Any, DbConfig] =
    Wire(DbConfig("localhost", 5432))

  // Derive wires for Database and UserService using the macro
  val dbWire: Wire.Shared[DbConfig, Database] =
    Wire.shared[Database]

  val serviceWire: Wire.Shared[Database, UserService] =
    Wire.shared[UserService]

  val appWire: Wire.Shared[UserService, BasicApp] =
    Wire.shared[BasicApp]

  println("[Setup] Created all wires\n")

  // Use Resource.from to automatically compose the dependency graph
  val appResource: Resource[BasicApp] = Resource.from[BasicApp](
    configWire,
    dbWire,
    serviceWire,
    appWire
  )

  println("[Setup] Composed resource graph\n")

  // Allocate within a scope
  Scope.global.scoped { scope =>

    println("[Scope] Entering scoped region\n")

    val app: $[BasicApp] = allocate(appResource)

    println("\n[App] Running application")
    $(app)(_.run())

    println("\n[Scope] Exiting scoped region - finalizers will run")
  }

  println("\n=== Example Complete ===")
}
```

([source](https://github.com/zio/zio-blocks/blob/main/scope-examples/src/main/scala/wire/WireBasicExample.scala))

Run this example with:

```bash
sbt "scope-examples/runMain wire.wireBasicExample"
```

Comparing shared vs unique semantics shows how shared wires reuse the same instance across dependents, while unique wires create fresh instances. View the example:

```scala title="scope-examples/src/main/scala/wire/WireSharedUniqueExample.scala" 
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

package wire

/**
 * Demonstrates the semantic difference between shared and unique wires:
 *   - Shared wires: same instance across dependents (reference-counted)
 *   - Unique wires: fresh instance per allocation
 *
 * Uses a counter to track how many times each service is instantiated.
 */

final class Counter {
  private val count = new AtomicInteger(0)

  def next(): Int = count.incrementAndGet()

  def value: Int = count.get()
}

final class ServiceA(counter: Counter) {
  val id = counter.next()
  println(s"[ServiceA] Initialized with counter id=$id")
}

final class ServiceB(counter: Counter) {
  val id = counter.next()
  println(s"[ServiceB] Initialized with counter id=$id")
}

final class SharedDependencyApp(a: ServiceA, b: ServiceB) {
  def checkSharing(): Boolean = {
    // If Counter is shared, both services got the same instance
    val aCountId = a.id
    val bCountId = b.id
    println(s"[SharedDependencyApp] ServiceA counter id=$aCountId, ServiceB counter id=$bCountId")
    aCountId != bCountId && a.id < b.id // Sequential IDs from same Counter
  }
}

final class UniqueDependencyApp(a: ServiceA, b: ServiceB) {
  def checkUniqueness(): Boolean = {
    // If Counter is unique, services got different instances (different starting IDs)
    val aCountId = a.id
    val bCountId = b.id
    println(s"[UniqueDependencyApp] ServiceA counter id=$aCountId, ServiceB counter id=$bCountId")
    aCountId != bCountId // Different Counter instances
  }
}

@main def wireSharedUniqueExample(): Unit = {
  println("=== Wire Shared vs Unique Example ===\n")

  println("--- Test 1: Shared Counter (diamond pattern) ---\n")

  // Using a SHARED Counter: both ServiceA and ServiceB share the same Counter instance
  val sharedResource: Resource[SharedDependencyApp] = Resource.from[SharedDependencyApp](
    Wire.shared[Counter] // Shared: one instance across the graph
  )

  Scope.global.scoped { scope =>

    println("[Scope] Entering scoped region\n")

    val app      = allocate(sharedResource)
    val isShared = $(app)(_.checkSharing())

    println(s"\n[Result] Counter was shared: $isShared\n")
  }

  println("\n--- Test 2: Unique Counter ---\n")

  // Using a UNIQUE Counter: each dependency gets a fresh Counter instance
  val uniqueResource: Resource[UniqueDependencyApp] = Resource.from[UniqueDependencyApp](
    Wire.unique[Counter] // Unique: fresh instance per dependency
  )

  Scope.global.scoped { scope =>

    println("[Scope] Entering scoped region\n")

    val app      = allocate(uniqueResource)
    val isUnique = $(app)(_.checkUniqueness())

    println(s"\n[Result] Counters were unique: $isUnique\n")
  }

  println("\n=== Example Complete ===")
}
```

([source](https://github.com/zio/zio-blocks/blob/main/scope-examples/src/main/scala/wire/WireSharedUniqueExample.scala))

Run this example with:

```bash
sbt "scope-examples/runMain wire.wireSharedUniqueExample"
```

Manual wire construction demonstrates how to use `fromFunction` for custom construction logic. View the example:

```scala title="scope-examples/src/main/scala/wire/WireFromFunctionExample.scala" 
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

package wire

/**
 * Demonstrates manual wire construction using fromFunction:
 *   - Wire.Shared.fromFunction for custom shared wire logic
 *   - Wire.Unique.fromFunction for custom unique wire logic
 *   - Using manual wires when macro derivation doesn't fit
 *
 * This is useful for complex initialization, conditional logic, or when you
 * need control over which dependencies to use.
 */

final case class ApiKey(value: String)

final case class HttpConfig(baseUrl: String, timeout: Int)

/**
 * A custom HTTP client that uses an API key and timeout from config.
 * Demonstrates a scenario where we want custom construction logic rather than
 * simple parameter passing.
 */
final class HttpClient(config: HttpConfig, apiKey: ApiKey) extends AutoCloseable {
  println(
    s"[HttpClient] Created with baseUrl=${config.baseUrl}, timeout=${config.timeout}ms, apiKey=${apiKey.value}"
  )

  def get(path: String): String = {
    println(s"[HttpClient] GET $path with api key")
    "response"
  }

  def close(): Unit =
    println("[HttpClient] Connection pool closed")
}

/**
 * A custom authenticator that needs the API key. Demonstrates context
 * extraction in a manual wire.
 */
final class Authenticator(apiKey: ApiKey) {
  println(s"[Authenticator] Using API key: ${apiKey.value}")

  def authenticate(): Boolean = {
    println("[Authenticator] Validating API key...")
    true
  }
}

final class ManualWireApp(client: HttpClient, auth: Authenticator) {
  def run(): Unit =
    if (auth.authenticate()) {
      val response = client.get("/api/users")
      println(s"[App] Got response: $response")
    }
}

@main def wireFromFunctionExample(): Unit = {
  println("=== Wire fromFunction (Manual Construction) Example ===\n")

  // Manually create wires using fromFunction for custom logic
  // This gives full control when macro derivation isn't suitable

  val httpClientWire: Wire.Shared[HttpConfig & ApiKey, HttpClient] =
    Wire.Shared.fromFunction { (scope, ctx) =>
      // Extract both dependencies from context
      val config = ctx.get[HttpConfig]
      val apiKey = ctx.get[ApiKey]

      // Custom initialization logic
      println("[Manual] Custom HttpClient construction")
      val client = new HttpClient(config, apiKey)

      // Register custom cleanup if needed (in addition to AutoCloseable)
      scope.defer(println("[Manual] HttpClient cleanup deferred"))

      client
    }

  val authenticatorWire: Wire.Shared[ApiKey, Authenticator] =
    Wire.Shared.fromFunction { (scope, ctx) =>
      val apiKey = ctx.get[ApiKey]

      // Custom initialization logic
      println("[Manual] Custom Authenticator construction")
      val auth = new Authenticator(apiKey)

      scope.defer(println("[Manual] Authenticator cleanup deferred"))

      auth
    }

  // Provide leaf dependencies
  val configWire = Wire(HttpConfig("https://api.example.com", 30000))
  val apiKeyWire = Wire(ApiKey("secret-key-12345"))

  // Compose into the app resource
  val appResource: Resource[ManualWireApp] = Resource.from[ManualWireApp](
    configWire,
    apiKeyWire,
    httpClientWire,
    authenticatorWire
  )

  println("[Setup] Created manual wires\n")

  // Allocate and run
  Scope.global.scoped { scope =>

    println("[Scope] Entering scoped region\n")

    val app = allocate(appResource)

    println("\n[App] Running application")
    $(app)(_.run())

    println("\n[Scope] Exiting scoped region - finalizers will run")
  }

  println("\n=== Example Complete ===")
}
```

([source](https://github.com/zio/zio-blocks/blob/main/scope-examples/src/main/scala/wire/WireFromFunctionExample.scala))

Run this example with:

```bash
sbt "scope-examples/runMain wire.wireFromFunctionExample"
```
