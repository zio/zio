# ZIO Blocks

> **Modular building blocks for modern Scala applications—no effect system required.**

[![Development](https://img.shields.io/badge/Project%20Stage-Development-green.svg)](https://github.com/zio/zio/wiki/Project-Stages) ![CI Badge](https://github.com/zio/zio-blocks/workflows/CI/badge.svg) [![Maven Central](https://img.shields.io/maven-metadata/v?metadataUrl=https%3A%2F%2Frepo1.maven.org%2Fmaven2%2Fdev%2Fzio%2Fzio-blocks-config_3%2Fmaven-metadata.xml&label=Maven%20Central)](https://central.sonatype.com/artifact/dev.zio/zio-blocks-config_3) [![Sonatype Snapshot](https://img.shields.io/maven-metadata/v?metadataUrl=https%3A%2F%2Fcentral.sonatype.com%2Frepository%2Fmaven-snapshots%2Fdev%2Fzio%2Fzio-blocks-config_3%2Fmaven-metadata.xml&label=Sonatype%20Snapshot)](https://central.sonatype.com/repository/maven-snapshots/dev/zio/zio-blocks-config_3/) [![ZIO Blocks](https://img.shields.io/github/stars/zio/zio-blocks?style=social)](https://github.com/zio/zio-blocks)

## What Is ZIO Blocks?

ZIO Blocks is a **family of type-safe, modular building blocks** for Scala applications. Each block is a standalone library with zero or minimal dependencies, designed to work with *any* Scala stack—ZIO, Cats Effect, Kyo, Ox, Akka, or plain Scala.

The philosophy is simple: **use what you need, nothing more**. Each block is independently useful and designed to compose with other blocks or your existing code.

## Core Principles

- **Zero Lock-In**: No dependency on ZIO, Cats Effect, or any other effect system. Use a block with whatever stack you already have.
- **Modular**: Each block is a separate artifact. Depend on exactly what you need.
- **Cross-Platform**: Most blocks cross-build for JVM and Scala.js, and for Scala 2.13 and 3.x with source compatibility—adopt Scala 3 on your timeline, not ours. The catalog below records the exceptions per block.
- **High Performance**: Implementations that avoid boxing, minimize allocations, and use platform-specific features where they pay off.
- **Type Safety**: Scala's type system carries the correctness guarantees, without runtime overhead.

## Getting Started

Add a block and use it. Nothing else to wire up—no runtime to install, no effect
type to adopt:

```scala
libraryDependencies += "dev.zio" %% "zio-blocks-schema" % "0.0.56"
```

```scala
import zio.blocks.schema._

case class Person(name: String, age: Int)

object Person {
  implicit val schema: Schema[Person] = Schema.derived
}

val alice = Person("Alice", 30)

// One schema, every format
val jsonStr = alice.toJsonString                      // {"name":"Alice","age":30}
val parsed  = """{"name":"Bob","age":25}""".fromJson[Person]
```

The four blocks below get a full walkthrough because they have no close
equivalent elsewhere in Scala. Every other block is one row away in the catalog,
and each row links to its own reference page.

## All Blocks

Every block is published under the `dev.zio` organization. Most cross-build for
**Scala 2.13 and 3.x** on both **JVM and Scala.js** with full source compatibility —
adopt Scala 3 on your timeline, not ours. The handful of modules that are narrower
say so in their own row.

### Meta Programming

JSON support is built into `zio-blocks-schema`; the modules below add further formats.

| Block | Artifact | Platform | Scala | Description |
|-------|----------|----------|-------|-------------|
| [Schema](./reference/schema/index.md) | `zio-blocks-schema` | JVM · JS | 2.13 · 3.x | Type-safe schemas with automatic codec, optic, and validator derivation |
| [Avro Codec](./reference/schema/built-in-codecs/avro.md) | `zio-blocks-schema-avro` | JVM | 2.13 · 3.x | Apache Avro binary serialization with automatic schema generation |
| [BSON Codec](./reference/schema/built-in-codecs/bson.md) | `zio-blocks-schema-bson` | JVM | 2.13 · 3.x | MongoDB-compatible BSON serialization with native type support |
| [CSV Codec](./reference/schema/built-in-codecs/csv.md) | `zio-blocks-schema-csv` | JVM · JS | 2.13 · 3.x | RFC 4180-compliant CSV serialization |
| [MessagePack Codec](./reference/schema/built-in-codecs/messagepack.md) | `zio-blocks-schema-messagepack` | JVM · JS | 2.13 · 3.x | Compact binary serialization with optimized streaming |
| [Thrift Codec](./reference/schema/built-in-codecs/thrift.md) | `zio-blocks-schema-thrift` | JVM | 2.13 · 3.x | Apache Thrift binary serialization with TBinaryProtocol |
| [TOON Codec](./reference/schema/built-in-codecs/toon.md) | `zio-blocks-schema-toon` | JVM · JS | 2.13 · 3.x | Token-oriented notation 30–60% smaller than JSON, tuned for LLM prompts |
| [XML Codec](./reference/schema/built-in-codecs/xml.md) | `zio-blocks-schema-xml` | JVM · JS | 2.13 · 3.x | Zero-dependency XML serialization with fluent navigation and patching |
| [YAML Codec](./reference/schema/built-in-codecs/yaml.md) | `zio-blocks-schema-yaml` | JVM · JS | 2.13 · 3.x | Human-readable YAML serialization with JSON interop |
| [TypeId](./reference/typeid.md) | `zio-blocks-typeid` | JVM · JS | 2.13 · 3.x | Compile-time type identity with rich metadata |

### Resource Management

All of these ship in `zio-blocks-scope`.

| Block | Artifact | Platform | Scala | Description |
|-------|----------|----------|-------|-------------|
| [Scope](./reference/resource-management/index.md) | `zio-blocks-scope` | JVM · JS | 2.13 · 3.x | Compile-time safe resource boundaries that keep values from escaping their lifetime |
| [Resource](./reference/resource-management/resource.md) | `zio-blocks-scope` | JVM · JS | 2.13 · 3.x | Lazy recipes that pair acquisition with finalization, composable with `map`, `flatMap`, and `zip` |
| [Unscoped](./reference/resource-management/unscoped.md) | `zio-blocks-scope` | JVM · JS | 2.13 · 3.x | Marker typeclass for plain data that can safely leave a scope |
| [DeferHandle](./reference/resource-management/defer-handle.md) | `zio-blocks-scope` | JVM · JS | 2.13 · 3.x | Handle returned by `Scope.defer` for cancelling a registered finalizer |
| [Finalizer](./reference/resource-management/finalizer.md) | `zio-blocks-scope` | JVM · JS | 2.13 · 3.x | Minimal capability interface for registering cleanup actions |
| [Finalization](./reference/resource-management/finalization.md) | `zio-blocks-scope` | JVM · JS | 2.13 · 3.x | Result of running a scope's finalizers, including any cleanup errors |

### Dependency Injection

| Block | Artifact | Platform | Scala | Description |
|-------|----------|----------|-------|-------------|
| [Wire](./reference/resource-management/wire.md) | `zio-blocks-scope` | JVM · JS | 2.13 · 3.x | Compile-time safe recipes for constructing a service and its dependencies |
| [Context](./reference/context.md) | `zio-blocks-context` | JVM · JS | 2.13 · 3.x | Type-indexed heterogeneous collections |

### Configuration & Feature Flags

| Block | Artifact | Platform | Scala | Description |
|-------|----------|----------|-------|-------------|
| [Configuration](./reference/config/index.md) | `zio-blocks-config` | JVM · JS | 2.13 · 3.x | Typed configuration loading, feature flags, and rollout rules |
| [Config YAML](./reference/config/formats.md) | `zio-blocks-config-yaml` | JVM · JS | 2.13 · 3.x | YAML source adapter for `ConfigSource` |
| [Config JSON](./reference/config/formats.md) | `zio-blocks-config-json` | JVM · JS | 2.13 · 3.x | JSON source adapter for `ConfigSource` |
| [Config HOCON](./reference/config/formats.md) | `zio-blocks-config-hocon` | JVM · JS | 2.13 · 3.x | HOCON source adapter for `ConfigSource` |

### Web & HTTP

| Block | Artifact | Platform | Scala | Description |
|-------|----------|----------|-------|-------------|
| [MediaType](./reference/media-type.md) | `zio-blocks-mediatype` | JVM · JS | 2.13 · 3.x | Type-safe IANA media types with 2,600+ predefined types |
| [HTTP Model](./reference/http-model/index.md) | `zio-blocks-http-model` | JVM · JS | 2.13 · 3.x | Pure HTTP data model with URL parsing, headers, cookies, and forms |
| [HTTP Model Schema](./reference/http-model/schema.md) | `zio-blocks-http-model-schema` | JVM · JS | 2.13 · 3.x | Schema-based typed access to the HTTP model |
| [Endpoint](./reference/endpoint/index.md) | `zio-blocks-endpoint` | JVM · JS | 2.13 · 3.x | Type-safe HTTP endpoint descriptors with composable codecs and typed auth |
| [OpenAPI](./reference/openapi.md) | `zio-blocks-openapi` | JVM · JS | 2.13 · 3.x | Type-safe OpenAPI 3.1 specification generation and rendering |
| [JWT](./reference/jwt.md) | `zio-blocks-jwt` | JVM · JS | 2.13 · 3.x | Zero-dependency JWT signing and verification with HMAC, RSA, ECDSA and EdDSA support |
| [HTML](./reference/html.md) | `zio-blocks-html` | JVM · JS | 2.13 · 3.x | Type-safe HTML templating with XSS protection |
| [Datastar](./reference/datastar/index.md) | `zio-blocks-datastar` | JVM · JS | 3.x | Typed Datastar attribute and signal DSL, plus the SSE events that patch a live page |
| [HTMX](./reference/htmx/index.md) | `zio-blocks-http-htmx` | JVM · JS | 3.x | Typed HTMX DSL for compile-time-checked HTMX attributes |

### Data Types

| Block | Artifact | Platform | Scala | Description |
|-------|----------|----------|-------|-------------|
| [Chunk](./reference/chunk.md) | `zio-blocks-chunk` | JVM · JS | 2.13 · 3.x | High-performance immutable indexed sequences with zero-boxing builders |
| [Maybe](./reference/maybe.md) | `zio-blocks-maybe` | JVM · JS | 2.13 · 3.x | Low-allocation optional values backed by `null` |
| [Combinators](./reference/combinators.md) | `zio-blocks-combinators` | JVM · JS | 2.13 · 3.x | Compile-time composition and decomposition of tuples, eithers, and unions |

### Concurrency

| Block | Artifact | Platform | Scala | Description |
|-------|----------|----------|-------|-------------|
| [Async](./reference/async.md) | `zio-blocks-async` | JVM · JS | 2.13 · 3.x | Zero-allocation asynchronous effect type with direct-style `await` |
| [Mux](./reference/mux.mdx) | `zio-blocks-mux` | JVM · JS | 2.13 · 3.x | Thread-safe multiplexer for HTTP/2, QUIC, and WebSocket-style protocols |
| [RingBuffer](./reference/ringbuffer/index.mdx) | `zio-blocks-ringbuffer` | JVM · JS | 2.13 · 3.x | Lock-free bounded ring buffers (SPSC, SPMC, MPSC, MPMC) |

### Streams

| Block | Artifact | Platform | Scala | Description |
|-------|----------|----------|-------|-------------|
| [Streams](./reference/streams/index.md) | `zio-blocks-streams` | JVM · JS | 2.13 · 3.x | Pull-based streaming with typed errors, zero boxing, and synchronous or asynchronous execution |

### Telemetry

| Block | Artifact | Platform | Scala | Description |
|-------|----------|----------|-------|-------------|
| [Telemetry](./reference/telemetry/index.md) | `zio-blocks-telemetry` | JVM · JS | 2.13 · 3.x | Zero-dependency OpenTelemetry-aligned tracing, logging, and metrics |
| [OTLP Export](./reference/telemetry/otel/index.md) | `zio-blocks-telemetry-otel` | JVM | 2.13 · 3.x | OTLP exporters bridging telemetry signals to an OpenTelemetry collector |

### Persistence

| Block | Artifact | Platform | Scala | Description |
|-------|----------|----------|-------|-------------|
| [SQL Module](./reference/sql/index.md) | `zio-blocks-sql` | JVM · JS | 3.x | Type-safe JDBC wrapper with schema-derived codecs and a CRUD repository |
| [ZIO Integration](./reference/sql-zio.md) | `zio-blocks-sql-zio` | JVM | 3.x | ZIO integration with `ZIO.attemptBlocking` and `ZLayer` |
| [Data Migration](./reference/data-migration.md) | `zio-blocks-data-migration` | JVM · JS | 3.x | Typed, online database schema migrations in three execution models, with no hand-written SQL |
| [Projection](./reference/projection.md) | `zio-blocks-projection` | JVM | 3.x | Event-sourced projections with per-entity SQLite storage |

### Tooling & Codegen

| Block | Artifact | Platform | Scala | Description |
|-------|----------|----------|-------|-------------|
| [Code Generation](./reference/codegen/index.md) | `zio-blocks-codegen` | JVM | 2.13 · 3.x | Generic Scala code generation IR and emitter |
| [Docs](./reference/docs.md) | `zio-blocks-markdown` | JVM · JS | 2.13 · 3.x | GitHub Flavored Markdown parsing, rendering, and programmatic construction |
| [Smithy](./reference/smithy.md) | `zio-blocks-smithy` | JVM | 2.13 · 3.x | Smithy IDL parser and AST library for API modeling |

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

- **Universal Data Formats**: JSON built in, plus Avro, BSON, CSV, MessagePack, Thrift, TOON, XML, and YAML as separate modules, with Protobuf planned.
- **High Performance**: Register-based design stores primitives directly in byte arrays, enabling zero-allocation serialization.
- **Reflective Optics**: Type-safe lenses, prisms, and traversals with embedded structural metadata.
- **Automatic Derivation**: Derive type class instances for any type with a schema.

### Installation

```scala
libraryDependencies += "dev.zio" %% "zio-blocks-schema" % "0.0.56"
```

See the [Meta Programming](#meta-programming) rows above for the optional format modules.

### Example

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

### Learn More

- [Schema reference](./reference/schema/index.md) — the full API surface, from `Reflect` and `Binding` through optics, validation, and schema evolution
- [Migrating from ZIO Schema](./guides/zio-schema-migration.md) — a step-by-step port from ZIO Schema 1.x

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
libraryDependencies += "dev.zio" %% "zio-blocks-scope" % "0.0.56"
```

### Example

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

### Learn More

- [Compile-Time Resource Safety with Scope](./guides/compile-time-resource-safety-with-scope.md) — the step-by-step tutorial, from basic resource management through dependency injection
- [Resource Management reference](./reference/resource-management/index.md) — `Scope`, `Resource`, `Wire`, `Unscoped`, and finalization order

---

## Async

A lightweight, zero-dependency asynchronous effect type. A ready `Async[A]` *is*
an `A`, so synchronous code composed with `map` / `flatMap` allocates nothing on
the happy path while still suspending on genuinely asynchronous work.

### The Problem

Asynchronous Scala forces a choice between two costs. `Future` allocates for
every combinator and needs an `ExecutionContext` threaded everywhere, even when
the value is already available. Full effect systems avoid that but ask you to
adopt a runtime, a set of type classes, and a programming model across your
whole codebase—a heavy price for a library that only occasionally suspends.

### The Solution

`Async[A]` is a value, not a wrapper. When the result is already known, the
representation *is* the result, so composing ready values costs nothing:

```scala
import zio.blocks.async._

// Constructors collapse to bare values; transformers inline with no allocation
val computed: Int =
  Async.succeed(20).map(_ + 1).flatMap(n => Async.succeed(n * 2)).block
// computed: Int = 42
```

### Key Features

- **Zero Allocation on the Happy Path**: A completed `Async[A]` is represented as the `A` itself; `map` and `flatMap` over ready values allocate nothing.
- **Direct-Style `await`**: `Async.async { ... }` rewrites `.await` calls at compile time into a non-blocking `flatMap` chain—straight-line code, asynchronous execution.
- **No Runtime to Adopt**: No `ExecutionContext` to thread, no type class hierarchy, no effect system dependency.
- **Interop Built In**: Bridges to `Future` and `CompletionStage`, plus `Async.promise` for callback-based APIs.

### Installation

```scala
libraryDependencies += "dev.zio" %% "zio-blocks-async" % "0.0.56"
```

### Example

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

### Learn More

- [Getting Started with Async](./guides/async-getting-started.md) — create, compose, and run async effects
- [Async reference](./reference/async.md) — the full API, including `zip`, `catchAll`, `collectAll`, the `Async.promise` callback bridge, and `Future` / `CompletionStage` interop
- [`async-examples`](https://github.com/zio/zio-blocks/blob/main/async-examples/src/main/scala/async/AsyncShowcaseExample.scala) — a single-file order-fulfillment demo (`sbt "++3.9.0; async-examples/run"`)

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
libraryDependencies += "dev.zio" %% "zio-blocks-sql" % "0.0.56"

// Optional ZIO integration
libraryDependencies += "dev.zio" %% "zio-blocks-sql-zio" % "0.0.56"
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

### Learn More

- [SQL reference](./reference/sql/index.md) — `DbCodec`, `Frag`, `Table`, `Repo`, `Transactor`, dialects, and DDL generation
- [Query DSL guide](./guides/query-dsl-reified-optics.md) — a four-part series building a type-safe query language on reified optics

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

## Guides

- [Getting Started with Async](./guides/async-getting-started.md) - Create, compose, and run zero-allocation async effects with the `Async[A]` type
- [Compile-Time Resource Safety with Scope](./guides/compile-time-resource-safety-with-scope.md) - Resource management and dependency injection, from first principles
- [Getting Started with Mux](./guides/getting-started-with-mux.md) - Manage multiplexed bidirectional message streams with capacity limits
- [Telemetry: Architecture, Patterns, and Real-World Usage](./guides/telemetry-guide.md) - Wire tracing, logging, and metrics into a running application
- [Migrating from ZIO Schema](./guides/zio-schema-migration.md) - Step-by-step migration from ZIO Schema 1.x to ZIO Blocks Schema
- [Query DSL Part 1: Expressions](./guides/query-dsl-reified-optics.md) - Build type-safe, composable query expressions
- [Query DSL Part 2: SQL Generation](./guides/query-dsl-sql.md) - Translate query expressions into SQL
- [Query DSL Part 3: Extending the Expression Language](./guides/query-dsl-extending.md) - Add custom operators beyond `SchemaExpr`
- [Query DSL Part 4: A Fluent SQL Builder](./guides/query-dsl-fluent-builder.md) - Build type-safe SELECT, UPDATE, INSERT, and DELETE statements

## Full API Reference

Every block in the catalog above links to its own reference page. The blocks
large enough to have several pages start from an overview:

- [Schema](./reference/schema/index.md) - core type system, dynamic values, optics, validation, and schema evolution
  - [Built-in Codecs](./reference/schema/built-in-codecs/index.md) - JSON, Avro, BSON, CSV, MessagePack, Thrift, TOON, XML, and YAML
  - [Schema Evolution](./reference/schema/schema-evolution/index.md) - one-way and bidirectional type-safe conversions
- [Telemetry](./reference/telemetry/index.md) - tracing, logging, metrics, and OTLP export
- [SQL](./reference/sql/index.md) - codecs, fragments, tables, repositories, transactors, and dialects
- [Resource Management](./reference/resource-management/index.md) - `Scope`, `Resource`, `Wire`, `Unscoped`, and finalization
- [Streams](./reference/streams/index.md) - `Stream`, `Pipeline`, `Sink`, and the low-level readers and writers
- [Endpoint](./reference/endpoint/index.md) - endpoint descriptors, HTTP codecs, route patterns, and typed auth
- [HTTP Model](./reference/http-model/index.md) - the pure HTTP data model and its schema-based typed access
- [HTMX](./reference/htmx/index.md) - the typed HTMX attribute DSL
- [Ring Buffer](./reference/ringbuffer/index.mdx) - the SPSC, SPMC, MPSC, and MPMC variants
- [Code Generation](./reference/codegen/index.md) - the Scala code generation IR and emitter
