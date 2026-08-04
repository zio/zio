# ZIO Blocks

> **Modular, zero-dependency building blocks for modern Scala applications.**

**Modular, zero-dependency building blocks for modern Scala applications.**

[![Development](https://img.shields.io/badge/Project%20Stage-Development-green.svg)](https://github.com/zio/zio/wiki/Project-Stages) ![CI Badge](https://github.com/zio/zio-blocks/workflows/CI/badge.svg) [![ZIO Blocks](https://img.shields.io/github/stars/zio/zio-blocks?style=social)](https://github.com/zio/zio-blocks)

## What Is ZIO Blocks?

ZIO Blocks is a **family of type-safe, modular building blocks** for Scala applications. Each block is a standalone library with zero or minimal dependencies, designed to work with *any* Scala stack—ZIO, Cats Effect, Kyo, Ox, Akka, or plain Scala.

The philosophy is simple: **use what you need, nothing more**. Each block is independently useful, cross-platform (JVM, JS), and designed to compose with other blocks or your existing code.

## The Blocks

| Block | Description | Status |
|-------|-------------|--------|
| **Schema** | Type-safe schemas with automatic codec derivation | ✅ Available |
| **Chunk** | High-performance immutable indexed sequences | ✅ Available |
| **Scope** | Compile-time safe resource management and DI | ✅ Available |
| **Docs** | GitHub Flavored Markdown parsing and rendering | ✅ Available |
| **Codegen** | Generic Scala code generation IR and emitter | ✅ Available |
| **TypeId** | Compile-time type identity with rich metadata | ✅ Available |
| **Context** | Type-indexed heterogeneous collections | ✅ Available |
| **MediaType** | Type-safe IANA media types with 2,600+ predefined types | ✅ Available |
| **OpenAPI** | Type-safe OpenAPI 3.1 specification generation | ✅ Available |
| **Ring Buffer** | High-performance bounded ring buffers (SPSC, MPSC, SPMC, MPMC) | ✅ Available |
| **Streams** | Pull-based streaming primitives | ✅ Available |
| **SQL** | Type-safe JDBC wrapper with schema-derived codecs and CRUD repository | ✅ Available |
| **Async** | Zero-allocation asynchronous effect type with direct-style `await` | ✅ Available |

## Config

Type-safe configuration loading, feature flags, rollout logic, and source adapters for YAML, JSON, and HOCON.

See the [Config reference](reference/config.md) for the full API surface, supported rollout syntax, and format-adapter entry points.

### Key Features

- **Static flags**: Resolve once at class load with `StaticFlag[A]`
- **Typed config loading**: Decode case classes with `Config.load[A]`
- **Flag sources**: Register custom flag sources in `FlagSource.Registry`
- **Source composition**: Combine sources with `orElse` and keep provenance
- **Rollout DSL**: Select values with path and percentage rules
- **File adapters**: Parse YAML, JSON, and HOCON into `ConfigSource`

### Installation

```scala
libraryDependencies += "dev.zio" %% "zio-blocks-config" % "0.0.51"
libraryDependencies += "dev.zio" %% "zio-blocks-config-yaml" % "0.0.51"
libraryDependencies += "dev.zio" %% "zio-blocks-config-json" % "0.0.51"
libraryDependencies += "dev.zio" %% "zio-blocks-config-hocon" % "0.0.51"
```

### Quick Start: StaticFlag

```scala
import zio.blocks.config._

object poolSize extends StaticFlag[Int](10)

val size: Int = poolSize()
```

### Quick Start: Config.load[A]

The snippet below uses Scala 3 syntax.

```scala
import zio.blocks.config._
import zio.blocks.scope.Unscoped

final case class AppConfig(host: String, port: Int) derives Schema, Unscoped

val cfg = Config.load[AppConfig](ConfigSource.fromMap(Map("host" -> "localhost", "port" -> "8080")))
```

### Example: FlagSource Plugin

```scala
package myapp

import zio.blocks.config._

object poolSize extends StaticFlag[Int](10)

FlagSource.Registry.register(
  FlagSource.fromMap(Map("myapp.poolSize" -> "20"), "demo")
)

val size = poolSize()
```

:::note
Register a `FlagSource` before the first reference to a `StaticFlag` object. `StaticFlag` resolves during object initialization, so a source registered later will not change a flag that has already been loaded. The lookup key is the flag object's fully qualified name (`myapp.poolSize` in this example).
:::

### Example: ConfigSource Composition with Provenance

The snippet below uses Scala 3 syntax.

```scala
import zio.blocks.config._
import zio.blocks.scope.Unscoped

val defaults = ConfigSource.fromMap(Map("app.host" -> "localhost"), "defaults")
val env      = ConfigSource.fromMap(Map("app.port" -> "8080"), "env")
val source   = env.orElse(defaults).prefix("app")

final case class AppConfig(host: String, port: Int) derives Schema, Unscoped

val loaded   = Config.loadWithProvenance[AppConfig](source)
val hostProv = loaded.map(_.provenanceOf("host"))
```

### Example: Rollout DSL

```scala
import zio.blocks.config._

val bucket = Rollout.bucketFor("user-123")
val choice = Rollout.select("true@prod/50%;false", "prod", bucket)
```

`prod/50%` applies the choice to the `prod` path and enables it for roughly half of the `prod` buckets. The trailing `false` entry is the catch-all fallback for every non-matching case.

### File Format Adapters

- **YAML**: `ConfigSource.fromYaml(...)` (requires `config-yaml` dependency and `import zio.blocks.config.yaml._`)
- **JSON**: `ConfigSource.fromJson(...)` (requires `config-json` dependency and `import zio.blocks.config.json._`)
- **HOCON**: `ConfigSource.fromHocon(...)` (requires `config-hocon` dependency and `import zio.blocks.config.hocon._`)

## Core Principles

- **Zero Lock-In**: No dependencies on ZIO, Cats Effect, or any effect system. Use with whatever stack you prefer.
- **Modular**: Each block is a separate artifact. Import only what you need.
- **Cross-Platform**: Full support for JVM and Scala.js.
- **Cross-Version**: Full support for Scala 2.13 and Scala 3.x with source compatibility—adopt Scala 3 on your timeline, not ours.
- **High Performance**: Optimized implementations that avoid boxing, minimize allocations, and leverage platform-specific features.
- **Type Safety**: Leverage Scala's type system for correctness without runtime overhead.

---

## Schema

The Schema block brings dynamic-language productivity to statically-typed Scala. Define your data types once, and derive codecs, validators, optics, and more automatically.

### The Problem

In statically-typed languages, you often maintain separate codec implementations for each data format (JSON, Avro, Protobuf, etc.). Meanwhile, dynamic languages handle data effortlessly:

```javascript
// JavaScript: one line and done
const data = await res.json();
```

In Scala, you'd typically need separate codecs for each format—a significant productivity gap.

### The Solution

ZIO Blocks Schema derives everything from a single schema definition:

```scala
case class Person(name: String, age: Int)

object Person {
  implicit val schema: Schema[Person] = Schema.derived
}

// Derive codecs for any format:
val jsonCodec    = Schema[Person].derive(JsonFormat)        // JSON
val avroCodec    = Schema[Person].derive(AvroFormat)        // Avro
val toonCodec    = Schema[Person].derive(ToonFormat)        // TOON (LLM-optimized)
val msgpackCodec = Schema[Person].derive(MessagePackFormat) // MessagePack
val thriftCodec  = Schema[Person].derive(ThriftFormat)      // Thrift
```

### Key Features

- **Universal Data Formats**: JSON, Avro, TOON (compact LLM-optimized format), MessagePack, Thrift, and BSON, with Protobuf planned.
- **High Performance**: Register-based design stores primitives directly in byte arrays, enabling zero-allocation serialization.
- **Reflective Optics**: Type-safe lenses, prisms, and traversals with embedded structural metadata.
- **Automatic Derivation**: Derive type class instances for any type with a schema.

### Installation

```scala
libraryDependencies += "dev.zio" %% "zio-blocks-schema" % "0.0.51"

// Optional format modules:
libraryDependencies += "dev.zio" %% "zio-blocks-schema-avro"        % "0.0.51"
libraryDependencies += "dev.zio" %% "zio-blocks-schema-toon"        % "0.0.51"
libraryDependencies += "dev.zio" %% "zio-blocks-schema-messagepack" % "0.0.51"
libraryDependencies += "dev.zio" %% "zio-blocks-schema-thrift"      % "0.0.51"
libraryDependencies += "dev.zio" %% "zio-blocks-schema-bson"        % "0.0.51"
```

### Example: Optics

```scala
import zio.blocks.schema._

case class Address(street: String, city: String)
case class Person(name: String, age: Int, address: Address)

object Person extends CompanionOptics[Person] {
  implicit val schema: Schema[Person] = Schema.derived

  val name: Lens[Person, String] = $(_.name)
  val age: Lens[Person, Int] = $(_.age)
  val streetName: Lens[Person, String] = $(_.address.street)
}

val person = Person("Alice", 30, Address("123 Main St", "Springfield"))
val updated = Person.age.replace(person, 31)
```

---

## Chunk

A high-performance, immutable indexed sequence optimized for the patterns common in streaming, parsing, and data processing. Think of it as `Vector` but faster for the operations that matter most.

### Why Chunk?

Standard library collections make trade-offs that aren't ideal for streaming and binary data processing:

- `Vector` is general-purpose but not optimized for concatenation patterns
- `Array` is mutable and boxes primitives when used generically
- `List` has O(n) random access

Chunk is designed for:

- **Fast concatenation** via balanced trees (Conc-Trees)
- **Zero-boxing** for primitive types with specialized builders
- **Efficient slicing** without copying
- **Seamless interop** with `ByteBuffer`, `Array`, and standard collections

### Key Features

- **Specialized Builders**: Dedicated builders for `Byte`, `Int`, `Long`, `Double`, etc. avoid boxing overhead.
- **Balanced Concatenation**: Based on Conc-Trees for O(log n) concatenation while maintaining O(1) indexed access.
- **Bit Operations**: First-class support for bit-level operations, bit chunks backed by `Byte`, `Int`, or `Long` arrays.
- **NonEmptyChunk**: A statically-guaranteed non-empty variant for APIs that require at least one element.
- **Full Scala Collection Integration**: Implements `IndexedSeq` for seamless interop.

### Installation

```scala
libraryDependencies += "dev.zio" %% "zio-blocks-chunk" % "0.0.51"
```

### Example

```scala
import zio.blocks.chunk._

// Create chunks
val bytes = Chunk[Byte](1, 2, 3, 4, 5)
val moreBytes = Chunk.fromArray(Array[Byte](6, 7, 8))

// Efficient concatenation (O(log n))
val combined = bytes ++ moreBytes

// Zero-copy slicing
val slice = combined.slice(2, 6)

// Bit operations
val bits = bytes.asBitsByte
val masked = bits & Chunk.fill(bits.length)(true)

// NonEmptyChunk for type-safe non-emptiness
val nonEmpty = NonEmptyChunk(1, 2, 3)
val head: Int = nonEmpty.head  // Always safe, no Option needed
```

---

## Scope

Compile-time verified resource safety for synchronous Scala code. Scope prevents resource leaks at compile time by tagging values with an unnameable type-level identity—values allocated in a scope can only be used within that scope. Child scope values cannot escape to parent scopes, enforced by both the abstract scope-tagged type and the `Unscoped` constraint on `scoped`.

### The Problem

Resource management in Scala is error-prone:

```scala
// Classic try/finally - verbose and easy to get wrong
val db = openDatabase()
try {
  val tx = db.beginTransaction()
  try {
    doWork(tx)
    tx.commit()
  } finally tx.close()  // What if commit() throws?
} finally db.close()

// Using - better, but doesn't prevent returning resources
Using(openDatabase()) { db =>
  db  // Oops! Returned the resource - use after close!
}
```

### The Solution

Scope makes resource leaks a **compile error**, not a runtime bug:

```scala
import zio.blocks.scope.*

Scope.global.scoped { scope =>
  import scope.*

  val db: $[Database] = allocate(Resource(openDatabase()))

  // Methods are hidden - can't call db.query() directly
  // Must use $ to access:
  val result: String = $(db)(_.query("SELECT 1"))

  // Trying to return `db` would be a compile error!
  result  // Only pure data (String) escapes
}
// db.close() called automatically
```

### Key Features

- **Compile-Time Leak Prevention**: Values of type `scope.$[A]` are opaque and unique to each scope instance. Returning a scoped value from its scope is a type error.
- **Zero Runtime Overhead**: `$[A]` erases to `A` at runtime—zero allocation overhead.
- **Structured Scopes**: Child scopes nest within parents; resources clean up LIFO when scopes exit.
- **Built-in Dependency Injection**: Wire up your application with `Resource.from[T](wires*)` for automatic constructor-based DI.
- **AutoCloseable Integration**: Resources implementing `AutoCloseable` have `close()` registered automatically.
- **Unscoped Constraint**: The `scoped` method requires `Unscoped[A]` evidence on the return type, ensuring only pure data (not resources or closures) can escape.
- **Actionable Runtime Errors**: If a scope reference escapes and is used after closing, `allocate`, `open()`, and `$` throw `IllegalStateException` with a detailed message explaining what went wrong, the common causes, and how to fix it—no silent null returns.

### Installation

```scala
libraryDependencies += "dev.zio" %% "zio-blocks-scope" % "0.0.51"
```

### Example: Basic Resource Management

```scala
import zio.blocks.scope.*

final class Database extends AutoCloseable:
  def query(sql: String): String = s"Result: $sql"
  def close(): Unit = println("Database closed")

Scope.global.scoped { scope =>
  import scope.*

  // Allocate returns $[Database] (scoped value)
  val db: $[Database] = allocate(Resource(new Database))

  // Access via $ - result (String) escapes, db does not
  val result: String = $(db)(_.query("SELECT * FROM users"))

  println(result)
}
// Output: Result: SELECT * FROM users
//         Database closed
```

### Example: Dependency Injection

```scala
import zio.blocks.scope.*

case class Config(dbUrl: String)
class Database(config: Config) extends AutoCloseable { ... }
class UserRepo(db: Database) { ... }
class UserService(repo: UserRepo) extends AutoCloseable { ... }

// Resource.from auto-wires the dependency graph
// Only provide leaf values - concrete classes are auto-wired
val serviceResource: Resource[UserService] = Resource.from[UserService](
  Wire(Config("jdbc:postgresql://localhost/mydb"))
)

serviceResource.use(_.createUser("Alice"))
// Cleanup runs LIFO: UserService → Database (UserRepo has no cleanup)
```

### Example: Nested Scopes with Transactions

```scala
Scope.global.scoped { connScope =>
  import connScope.*

  val conn = allocate(Resource.fromAutoCloseable(new Connection))

  // Transaction lives in child scope - cleaned up before connection
  val result: String = scoped { txScope =>
    import txScope.*
    val c  = lower(conn)
    val tx = $(c)(_.beginTransaction()).allocate
    $(tx)(_.execute("INSERT INTO users VALUES (1, 'Alice')"))
    $(tx)(_.commit())
    "success"
  }
  // Transaction closed here, connection still open

  println(result)
}
// Connection closed here
```

### Getting Started

New to Scope? Check out the [Scope Tutorial](./guides/compile-time-resource-safety-with-scope.md) for a comprehensive step-by-step guide that walks you through the concepts, patterns, and real-world examples. The tutorial is designed for newcomers and covers everything from basic resource management to advanced dependency injection.

For detailed API documentation, see the [Scope Reference](./reference/resource-management/scope.md).

---

## Docs

A zero-dependency GitHub Flavored Markdown library for parsing, rendering, and programmatic construction of Markdown documents.

### Why Docs?

Generating documentation, README files, or any Markdown content programmatically is common but error-prone with string concatenation. Docs provides:

- **Type-safe AST**: Build Markdown documents with compile-time guarantees
- **Compile-time validation**: The `md"..."` interpolator validates syntax at compile time
- **Multiple renderers**: Output to Markdown, HTML, or ANSI terminal
- **Round-trip parsing**: Parse Markdown to AST and render back to Markdown

### Key Features

- **GFM Compliant**: Tables, strikethrough, autolinks, task lists, fenced code blocks
- **Zero Dependencies**: Only depends on zio-blocks-chunk
- **Cross-Platform**: Full support for JVM and Scala.js
- **Type-Safe Interpolator**: `md"# Hello $name"` with compile-time validation
- **Multiple Renderers**: Markdown, HTML (full document or fragment), ANSI terminal

### Installation

```scala
libraryDependencies += "dev.zio" %% "zio-blocks-docs" % "0.0.51"
```

### Example

```scala
import zio.blocks.docs._

// Parse Markdown
val doc = Parser.parse("# Hello\n\nThis is **bold** text.")
// Right(Doc(Chunk(Heading(H1, "Hello"), Paragraph(...))))

// Render to HTML
val html = doc.map(_.toHtml)
// Full HTML5 document with <html>, <head>, <body>

// Render to HTML fragment (just the content)
val fragment = doc.map(_.toHtmlFragment)
// "<h1>Hello</h1><p>This is <strong>bold</strong> text.</p>"

// Render to terminal with ANSI colors
val terminal = doc.map(_.toTerminal)

// Use the type-safe interpolator
val name = "World"
val greeting = md"# Hello $name"
// Doc containing: Heading(H1, Chunk(Text("Hello World")))

// Build documents programmatically
import zio.blocks.chunk.Chunk

val manual = Doc(Chunk(
  Block.Heading(HeadingLevel.H1, Chunk(Inline.Text("API Reference"))),
  Block.Paragraph(Chunk(
    Inline.Text("See "),
    Inline.Link(Chunk(Inline.Text("docs")), "/docs", None),
    Inline.Text(" for details.")
  ))
))

// Render back to Markdown
val markdown = Renderer.render(manual)
```

### Supported GFM Features

| Feature | Supported |
|---------|-----------|
| Headings (ATX) | ✅ |
| Paragraphs | ✅ |
| Emphasis/Strong | ✅ |
| Code (inline & fenced) | ✅ |
| Links & Images | ✅ |
| Lists (bullet, ordered, task) | ✅ |
| Blockquotes | ✅ |
| Tables | ✅ |
| Strikethrough | ✅ |
| Autolinks | ✅ |
| Hard/Soft breaks | ✅ |
| HTML (passthrough) | ✅ |

### Limitations

- **No frontmatter**: YAML/TOML headers are not parsed
- **No HTML entity decoding**: `&amp;` stays as-is
- **No footnotes**: GFM footnote extension not supported
- **No emoji shortcodes**: `:smile:` not converted to emoji

---

## TypeId

Compile-time type identity with rich metadata. TypeId captures comprehensive information about Scala types including name, owner, type parameters, variance, parent types, and annotations.

### Key Features

- **Rich Metadata**: Captures type name, owner, kind (class/trait/object/enum), parent types, and annotations
- **Higher-Kinded Support**: Works with proper types and type constructors via `AnyKind`
- **Subtype Checking**: Runtime subtype/supertype relationship checks using compile-time extracted information
- **Cross-Platform**: Works identically on JVM and Scala.js

### Installation

```scala
libraryDependencies += "dev.zio" %% "zio-blocks-typeid" % "0.0.51"
```

### Example

```scala
import zio.blocks.typeid._

// Get TypeId for any type
val listId = TypeId.of[List[Int]]
println(listId.name)       // "List"
println(listId.fullName)   // "scala.collection.immutable.List"
println(listId.arity)      // 1 (type constructor)

// Check type relationships
trait Animal
case class Dog(name: String) extends Animal

val dogId = TypeId.of[Dog]
val animalId = TypeId.of[Animal]
dogId.isSubtypeOf(animalId)  // true

// Access structural information
dogId.isCaseClass  // true
dogId.isSealed     // false
```

---

## Context

A type-indexed heterogeneous collection that stores values by their types with compile-time type safety.

### Key Features

- **Type-Safe Lookup**: Retrieve values by type with compile-time guarantees
- **Covariant**: `Context[Specific]` is a subtype of `Context[General]`
- **Subtype Matching**: Lookup by supertype finds matching subtypes
- **Cached Access**: O(1) subsequent lookups after first retrieval

### Installation

```scala
libraryDependencies += "dev.zio" %% "zio-blocks-context" % "0.0.51"
```

### Example

```scala
import zio.blocks.context._

case class Config(debug: Boolean)
case class Metrics(count: Int)

// Create a context with multiple values
val ctx: Context[Config & Metrics] = Context(
  Config(debug = true),
  Metrics(count = 42)
)

// Retrieve values by type
val config: Config = ctx.get[Config]
val metrics: Metrics = ctx.get[Metrics]

// Add or update values
val updated = ctx.update[Metrics](m => m.copy(count = m.count + 1))

// Combine contexts
val ctx1 = Context(Config(false))
val ctx2 = Context(Metrics(0))
val merged: Context[Config & Metrics] = ctx1 ++ ctx2
```

---

## Ring Buffer

High-performance, bounded ring buffers for inter-thread communication. Four lock-free variants cover every producer/consumer pattern (SPSC, MPSC, SPMC, MPMC).

### Why Ring Buffer?

Standard `java.util.concurrent` queues use node allocation (`ConcurrentLinkedQueue`) or coarse locking (`ArrayBlockingQueue`). Ring buffers avoid both:

- **Zero allocation** on the hot path—pre-allocated circular array
- **Lock-free** on the fast path—CAS or release/acquire semantics only
- **Cache-friendly**—sequential memory access with 128-byte padding between producer/consumer fields

### Key Features

- **Four concurrency patterns**: SPSC, SPMC, MPSC, MPMC—pick the most constrained variant for your use case
- **Cross-platform**: Same API on JVM and Scala.js (JS uses sequential implementations)

### Installation

```scala
libraryDependencies += "dev.zio" %% "zio-blocks-ringbuffer" % "0.0.51"
```

### Example

```scala
import zio.blocks.ringbuffer._

// SPSC: fastest, for dedicated producer-consumer pairs
val spsc = SpscRingBuffer[String](1024)
spsc.offer("hello") // true
spsc.take()          // "hello"

// MPMC: general-purpose, any number of threads
val mpmc = MpmcRingBuffer[String](1024)
mpmc.offer("hello") // false if full
mpmc.take()          // null if empty
```

---

## SQL

A thin, type-safe JDBC wrapper that maps Scala case classes to database tables using the same `Schema` you use for JSON and Avro codecs. No ORM runtime, no code generation — just composable SQL fragments, a derived repository abstraction, and a direct ZIO integration.

### The Problem

JDBC is powerful but tedious: manual `ResultSet` traversal, index-based parameter binding, and repetitive CRUD boilerplate make even simple database access error-prone. ORMs solve the boilerplate but add heavy runtimes, hidden queries, and opaque magic.

### The Solution

ZIO Blocks SQL derives everything from a single `Schema[A]`:

```scala
case class User(id: Long, name: String, email: String)
object User:
  given Schema[User] = Schema.derived

// Derive the table, codec, and repository in one line
val repo = Repo.derived[User, Long]

// Use the sql"..." interpolator for custom queries
val frag = sql"SELECT * FROM user WHERE email = ${"alice@example.com"}"
```

### Key Features

- **Schema-derived codecs**: `DbCodec[A]` is auto-derived from `Schema[A]` — column names, types, and nullability come for free.
- **Composable fragments**: The `sql"..."` interpolator creates `Frag` values that compose safely with `++`. SQL injection is structurally impossible.
- **CRUD repository**: `Repo[E, ID]` provides `all`, `find`, `findAll`, `insert`, `insertAll`, `update`, `delete`, `deleteAll`, and `clear` out of the box.
- **DDL generation**: `Table.createTable(dialect)` generates type-accurate `CREATE TABLE IF NOT EXISTS` SQL from the schema.
- **ZIO integration**: `TransactorZIO` lifts blocking JDBC calls into `Task` (or `ZIO`) with proper bracketing and rollback.
- **Effect-system agnostic core**: The `zio-blocks-sql` module has no ZIO dependency — use it with any effect system or plain Scala.

### Installation

```scala
// Core module (Scala 3, JVM + Scala.js)
libraryDependencies += "dev.zio" %% "zio-blocks-sql" % "0.0.51"

// ZIO integration (Scala 3, JVM only)
libraryDependencies += "dev.zio" %% "zio-blocks-sql-zio" % "0.0.51"
```

### Example

```scala
import zio.blocks.schema._
import zio.blocks.sql._
import zio.blocks.sql.zio._

case class Product(id: Long, name: String, price: Double)
object Product:
  given Schema[Product] = Schema.derived
  given DbCodec[Product] = summon[Schema[Product]].deriving(DbCodecDeriver).derive

val repo        = Repo.derived[Product, Long]
val transactor  = TransactorZIO.fromUrl("jdbc:postgresql://localhost/shop", SqlDialect.PostgreSQL)

// Batch insert, then query with a custom filter
val program = transactor.transact:
  repo.insertAll(List(
    Product(1L, "Widget", 9.99),
    Product(2L, "Gadget", 29.99)
  ))
  sql"SELECT * FROM product WHERE price < ${15.0}".query[Product]
```

---

## Streams (In Development)

A pull-based streaming library for composable, backpressure-aware data processing.

```scala
import zio.blocks.streams._

// Coming soon: efficient pull-based streams
// that compose with any effect system
```

---

## Async

A lightweight, zero-dependency asynchronous effect type. A ready `Async[A]` *is*
an `A`, so synchronous code composed with `map` / `flatMap` allocates nothing on
the happy path while still suspending on genuinely asynchronous work.

```scala
import zio.blocks.async._

// Constructors collapse to bare values; transformers inline with no allocation
val computed: Int =
  Async.succeed(20).map(_ + 1).flatMap(n => Async.succeed(n * 2)).block
// computed: Int = 42
```

Write straight-line asynchronous code with `Async.async` and `.await`, rewritten
at compile time into a non-blocking `flatMap` chain:

```scala
import zio.blocks.async._

def fetch(id: Int): Async[String] = Async.succeed(s"item-$id")

val program: Async[Int] =
  Async.async {
    val a = fetch(1).await
    val b = fetch(2).await
    (a + b).length
  }
```

See the [Async reference](./reference/async.md) for the full API, including
`zip`, `catchAll`, `collectAll`, the `Async.promise` callback bridge, and
`Future` / `CompletionStage` interop.

**Runnable tour:** the [`async-examples`](https://github.com/zio/zio-blocks/blob/main/async-examples/src/main/scala/async/AsyncShowcaseExample.scala)
module is a single-file order-fulfillment demo (`sbt "++3.8.3; async-examples/run"`).

---

## Compatibility

ZIO Blocks works with any Scala stack:

| Stack | Compatible |
|-------|------------|
| ZIO 2.x | ✅ |
| Cats Effect 3.x | ✅ |
| Kyo | ✅ |
| Ox | ✅ |
| Akka | ✅ |
| Plain Scala | ✅ |

Each block has zero dependencies on effect systems. Use the blocks directly, or integrate them with your effect system of choice.

## Scala & Platform Support

ZIO Blocks supports **Scala 2.13** and **Scala 3.x** with full source compatibility. Write your code once and compile it against either version—migrate to Scala 3 when your team is ready, not when your dependencies force you.

| Platform | Schema | Chunk | Scope | Docs | TypeId | Context | Ring Buffer | Streams | SQL | Async |
|----------|--------|-------|-------|------|--------|---------|-------------|---------|-----|-------|
| JVM | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | 🚧 | ✅ | ✅ |
| Scala.js | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | 🚧 | ✅ | ✅ |

## Documentation

### Core Schema Concepts

- [Schema](./reference/schema/schema.md) - Core schema definitions and derivation
- [Allows](./reference/schema/allows.md) - Compile-time structural grammar constraints
- [Reflect](./reference/schema/reflect.md) - Structural reflection API
- [Binding](./reference/schema/binding.md) - Runtime constructors and deconstructors
- [BindingResolver](reference/schema/binding-resolver.md) - Binding lookup and schema rebinding
- [Registers](./reference/schema/registers.md) - Register-based primitive storage

### Optics & Navigation

- [Optics](./reference/schema/optics.md) - Lenses, prisms, and traversals
- [SchemaExpr](./reference/schema/schema-expr.md) - Schema-aware expressions for queries and validation
- [Path Interpolator](./reference/schema/path-interpolator.md) - Type-safe path construction
- [DynamicValue](./reference/schema/dynamic-value.md) - Schema-less dynamic values
- [DynamicSchema](./reference/schema/dynamic-schema.md) - Type-erased schemas for validation and cross-process transport

### Serialization

- [Codec & Format](./reference/schema/codec.md) - Codec, Format, BinaryCodec & TextCodec
- [JSON](./reference/schema/built-in-codecs/json/index.md) - JSON codec and parsing
- [JsonPatch](./reference/schema/built-in-codecs/json/json-patch.md) - Diff and patch JSON values
- [JsonDiffer](./reference/schema/built-in-codecs/json/json-differ.md) - Compute minimal diffs between JSON values
- [JSON Schema](./reference/schema/built-in-codecs/json/json-schema.md) - JSON Schema generation and validation
- [XML Codec](./reference/schema/built-in-codecs/xml.md) - Zero-dependency XML serialization with fluent navigation and patching
- [CSV Codec](./reference/schema/built-in-codecs/csv.md) - RFC 4180-compliant CSV serialization with schema-driven derivation
- [BSON Codec](./reference/schema/built-in-codecs/bson.md) - MongoDB-compatible BSON serialization with native type support
- [Avro Codec](./reference/schema/built-in-codecs/avro.md) - Apache Avro binary serialization with automatic schema generation
- [MessagePack Codec](./reference/schema/built-in-codecs/messagepack.md) - Compact binary serialization with optimized streaming
- [Thrift Codec](./reference/schema/built-in-codecs/thrift.md) - Apache Thrift binary serialization with TBinaryProtocol
- [YAML Codec](./reference/schema/built-in-codecs/yaml.md) - Human-readable YAML serialization with JSON interop
- [TOON Codec](./reference/schema/built-in-codecs/toon.md) - Compact token-oriented notation 30-60% smaller than JSON, optimized for LLM prompts
- [Built-in Codecs](./reference/schema/built-in-codecs/index.md) - Overview of all supported serialization formats
- [Extension Syntax](./reference/schema/syntax.md) - `.toJson`, `.fromJson`, and more

### Data Operations

- [Patching](./reference/schema/patch.md) - Serializable data transformations
- [SchemaError](./reference/schema/schema-error.md) - Structured error type for schema operations
- [Validation](./reference/schema/validation.md) - Data validation and error handling
- [Schema Evolution](reference/schema/schema-evolution/index.md) - One-way and bidirectional type-safe conversions
  - [Into](reference/schema/schema-evolution/into.md) - One-way conversion with validation
  - [As](reference/schema/schema-evolution/as.md) - Bidirectional round-trip conversion

### Other Blocks

- [Chunk](./reference/chunk.md) - High-performance immutable sequences
- [Maybe](./reference/maybe.md) - Low-allocation optional values using null
- [Mux](./reference/mux.mdx) - Thread-safe multiplexer for ID-multiplexed protocols (HTTP/2, QUIC, WebSockets) with lock-free per-stream queues
- [Scope](./reference/resource-management/scope.md) - Compile-time safe resource management and DI
- [Wire](./reference/resource-management/wire.md) - Recipes for constructing services and dependencies
- [TypeId](./reference/typeid.md) - Type identity and metadata
- [Context](./reference/context.md) - Type-indexed heterogeneous collections
- [Combinators](./reference/combinators.md) - Compile-time composition and decomposition of values (Tuples, Eithers, Unions)
- [Docs (Markdown)](./reference/docs.md) - Markdown parsing and rendering
- [HTML](./reference/html.md) - Type-safe HTML templating with XSS protection
- [HTMX](./reference/htmx/index.md) - Typed HTMX DSL for safe, compile-time HTMX attribute declarations
- [HTTP Model](./reference/http-model/index.md) - Pure HTTP data model with URL parsing, headers, cookies, and forms
- [Endpoint](./reference/endpoint/index.md) - Pure, type-safe HTTP endpoint descriptors with composable codecs and typed auth
- [MediaType](./reference/media-type.md) - Type-safe IANA media types
- [Smithy](./reference/smithy.md) - Smithy IDL parser and AST library for API modeling
- [OpenAPI](./reference/openapi.md) - Type-safe OpenAPI 3.1 specification generation and rendering
- [Ring Buffer](./reference/ringbuffer/index.mdx) - High-performance bounded ring buffers
- [Stream](./reference/streams/stream.md) - Lazy, pull-based, type-safe streaming with resource safety
- [Pipeline](./reference/streams/pipeline.md) - Reusable, composable stream transformations
- [Sink](./reference/streams/sink.md) - Stream consumers that produce typed results
- [Reader](./reference/streams/reader.md) - Low-level pull-based sources for streaming
- [Writer](./reference/streams/writer.md) - Low-level push-based sinks for streaming
- [SQL](./reference/sql/index.md) - Type-safe JDBC wrapper with schema-derived codecs and repository
  - [DbCodec](./reference/sql/db-codec.md) - Bidirectional codec between Scala values and database columns
  - [Frag](./reference/sql/frag.md) - Immutable SQL fragment with safe parameterization via `sql"..."` interpolator
  - [Table](./reference/sql/table.md) - Schema-derived table metadata binding Scala types to database tables
  - [Repo](./reference/sql/repo.md) - Type-safe CRUD repository with pre-built SQL operations
  - [Transactor](./reference/sql/transactor.md) - Connection lifecycle and transaction management
  - [DbCon](./reference/sql/db-con.md) - Implicit context carrying connection, dialect, and logger
  - [DbTx](./reference/sql/db-tx.md) - Transactional scope marker extending `DbCon`
  - [SqlDialect](./reference/sql/sql-dialect.md) - Database-specific SQL rendering (PostgreSQL, SQLite)
  - [TransactorZIO](./reference/sql/transactor-zio.md) - ZIO integration with `ZIO.attemptBlocking` and `ZLayer`
- [Async](./reference/async.md) - Zero-allocation asynchronous effect type with direct-style `await`

### Guides

- [Getting Started with Mux](./guides/getting-started-with-mux.md) - Learn how to manage multiplexed bidirectional message streams with capacity limits
- [Migrating from ZIO Schema](./guides/zio-schema-migration.md) - Step-by-step guide to migrating from ZIO Schema 1.x to ZIO Blocks Schema
- [Query DSL Part 1: Expressions](./guides/query-dsl-reified-optics.md) - Build type-safe, composable query expressions
- [Query DSL Part 2: SQL Generation](./guides/query-dsl-sql.md) - Translate query expressions into SQL
- [Query DSL Part 3: Extending the Expression Language](./guides/query-dsl-extending.md) - Add custom operators beyond SchemaExpr
- [Query DSL Part 4: A Fluent SQL Builder](./guides/query-dsl-fluent-builder.md) - Build type-safe SELECT, UPDATE, INSERT, DELETE statements
