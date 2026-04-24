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
| **TypeId** | Compile-time type identity with rich metadata | ✅ Available |
| **Context** | Type-indexed heterogeneous collections | ✅ Available |
| **MediaType** | Type-safe IANA media types with 2,600+ predefined types | ✅ Available |
| **Ring Buffer** | High-performance bounded ring buffers (SPSC, MPSC, SPMC, MPMC) | ✅ Available |
| **Streams** | Pull-based streaming primitives | 🚧 In Development |

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
libraryDependencies += "dev.zio" %% "zio-blocks-schema" % "0.0.33"

// Optional format modules:
libraryDependencies += "dev.zio" %% "zio-blocks-schema-avro" % "0.0.33"
libraryDependencies += "dev.zio" %% "zio-blocks-schema-toon" % "0.0.33"
libraryDependencies += "dev.zio" %% "zio-blocks-schema-messagepack" % "0.0.33"
libraryDependencies += "dev.zio" %% "zio-blocks-schema-thrift" % "0.0.33"
libraryDependencies += "dev.zio" %% "zio-blocks-schema-bson" % "0.0.33"
```

### Example: Optics

```scala

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
libraryDependencies += "dev.zio" %% "zio-blocks-chunk" % "0.0.33"
```

### Example

```scala

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

Scope.global.scoped { scope =>

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
libraryDependencies += "dev.zio" %% "zio-blocks-scope" % "0.0.33"
```

### Example: Basic Resource Management

```scala

final class Database extends AutoCloseable:
  def query(sql: String): String = s"Result: $sql"
  def close(): Unit = println("Database closed")

Scope.global.scoped { scope =>

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

case class Config(dbUrl: String)
class Database(config: Config) extends AutoCloseable { ... }
class UserRepo(db: Database) { ... }
class UserService(repo: UserRepo) extends AutoCloseable { ... }

// Resource.from auto-wires the dependency graph
// Only provide leaf values - concrete classes are auto-wired
val serviceResource: Resource[UserService] = Resource.from[UserService](
  Wire(Config("jdbc:postgresql://localhost/mydb"))
)

Scope.global.scoped { scope =>

  val service = allocate(serviceResource)

  $(service)(_.createUser("Alice"))
}
// Cleanup runs LIFO: UserService → Database (UserRepo has no cleanup)
```

### Example: Nested Scopes with Transactions

```scala
Scope.global.scoped { connScope =>

  val conn = allocate(Resource.fromAutoCloseable(new Connection))

  // Transaction lives in child scope - cleaned up before connection
  val result: String = scoped { txScope =>

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
libraryDependencies += "dev.zio" %% "zio-blocks-docs" % "0.0.33"
```

### Example

```scala

// Parse Markdown
val doc = Parser.parse("# Hello\n\nThis is **bold** text.")
// Right(Doc(Chunk(Heading(H1, "Hello"), Paragraph(...))))

// Render to HTML
val html = doc.map(_.toHtml)
// Full HTML5 document with <html>, <head>, <body>

// Render to HTML fragment (just the content)
val fragment = doc.map(_.toHtmlFragment)
// "HelloThis is bold text."

// Render to terminal with ANSI colors
val terminal = doc.map(_.toTerminal)

// Use the type-safe interpolator
val name = "World"
val greeting = md"# Hello $name"
// Doc containing: Heading(H1, Chunk(Text("Hello World")))

// Build documents programmatically

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
libraryDependencies += "dev.zio" %% "zio-blocks-typeid" % "0.0.33"
```

### Example

```scala

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
libraryDependencies += "dev.zio" %% "zio-blocks-context" % "0.0.33"
```

### Example

```scala

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
libraryDependencies += "dev.zio" %% "zio-blocks-ringbuffer" % "0.0.33"
```

### Example

```scala

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

## Streams (In Development)

A pull-based streaming library for composable, backpressure-aware data processing.

```scala

// Coming soon: efficient pull-based streams
// that compose with any effect system
```

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

| Platform | Schema | Chunk | Scope | Docs | TypeId | Context | Ring Buffer | Streams |
|----------|--------|-------|-------|------|--------|---------|-------------|---------|
| JVM | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Scala.js | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |

## Documentation

### Core Schema Concepts

- [Schema](./reference/schema.md) - Core schema definitions and derivation
- [Allows](./reference/allows.md) - Compile-time structural grammar constraints
- [Reflect](./reference/reflect.md) - Structural reflection API
- [Binding](./reference/binding.md) - Runtime constructors and deconstructors
- [BindingResolver](./reference/binding-resolver.md) - Binding lookup and schema rebinding
- [Registers](./reference/registers.md) - Register-based primitive storage

### Optics & Navigation

- [Optics](./reference/optics.md) - Lenses, prisms, and traversals
- [SchemaExpr](./reference/schema-expr.md) - Schema-aware expressions for queries and validation
- [Path Interpolator](./path-interpolator.md) - Type-safe path construction
- [DynamicValue](./reference/dynamic-value.md) - Schema-less dynamic values
- [DynamicSchema](./reference/dynamic-schema.md) - Type-erased schemas for validation and cross-process transport

### Serialization

- [Codec & Format](./reference/codec.md) - Codec, Format, BinaryCodec & TextCodec
- [JSON](./reference/json.md) - JSON codec and parsing
- [JsonPatch](./reference/json-patch.md) - Diff and patch JSON values
- [JsonDiffer](./reference/json-differ.md) - Compute minimal diffs between JSON values
- [JSON Schema](./reference/json-schema.md) - JSON Schema generation and validation
- [Formats](./reference/formats.md) - Avro, TOON, MessagePack, BSON, Thrift
- [Extension Syntax](./reference/syntax.md) - `.toJson`, `.fromJson`, and more

### Data Operations

- [Patching](./reference/patch.md) - Serializable data transformations
- [SchemaError](./reference/schema-error.md) - Structured error type for schema operations
- [Validation](./reference/validation.md) - Data validation and error handling
- [Schema Evolution](./reference/schema-evolution/index.md) - One-way and bidirectional type-safe conversions
  - [Into](./reference/schema-evolution/into.md) - One-way conversion with validation
  - [As](./reference/schema-evolution/as.md) - Bidirectional round-trip conversion

### Other Blocks

- [Chunk](./reference/chunk.md) - High-performance immutable sequences
- [Scope](./reference/resource-management/scope.md) - Compile-time safe resource management and DI
- [Wire](./reference/resource-management/wire.md) - Recipes for constructing services and dependencies
- [TypeId](./reference/typeid.md) - Type identity and metadata
- [Context](./reference/context.md) - Type-indexed heterogeneous collections
- [Docs (Markdown)](./reference/docs.md) - Markdown parsing and rendering
- [MediaType](./reference/media-type.md) - Type-safe IANA media types
- [HTTP Model](./reference/http-model.md) - Pure HTTP data model with URL parsing, headers, cookies, and forms
- [Ring Buffer](./ringbuffer.md) - High-performance bounded ring buffers

### Guides

- [Migrating from ZIO Schema](./guides/zio-schema-migration.md) - Step-by-step guide to migrating from ZIO Schema 1.x to ZIO Blocks Schema
- [Query DSL Part 1: Expressions](./guides/query-dsl-reified-optics.md) - Build type-safe, composable query expressions
- [Query DSL Part 2: SQL Generation](./guides/query-dsl-sql.md) - Translate query expressions into SQL
- [Query DSL Part 3: Extending the Expression Language](./guides/query-dsl-extending.md) - Add custom operators beyond SchemaExpr
- [Query DSL Part 4: A Fluent SQL Builder](./guides/query-dsl-fluent-builder.md) - Build type-safe SELECT, UPDATE, INSERT, DELETE statements
