# Context

> `Context[+R]` is a type-indexed heterogeneous collection that stores values of different types, indexed by their types, with compile-time type safety for lookups. It provides an immutable, cache-aware dependency container where the phantom type `R` (using intersection types) tracks which types are present.

`Context[+R]` is a type-indexed heterogeneous collection that stores values of different types, indexed by their types, with compile-time type safety for lookups. It provides an immutable, cache-aware dependency container where the phantom type `R` (using intersection types) tracks which types are present.

The core type looks like this:

```scala
// Signature (showing public API structure, not actual implementation)
final class Context[+R] {
  def size: Int
  def isEmpty: Boolean
  def nonEmpty: Boolean
  def get[A >: R](implicit ev: IsNominalType[A]): A
  def getOption[A](implicit ev: IsNominalType[A]): Option[A]
  def add[A](a: A)(implicit ev: IsNominalType[A]): Context[R & A]
  def update[A >: R](f: A => A)(implicit ev: IsNominalType[A]): Context[R]
  def ++[R1](that: Context[R1]): Context[R & R1]
  def prune[A >: R](implicit ev: IsNominalIntersection[A]): Context[A]
  override def toString: String
}
```

Key properties: covariant (`+R`), immutable, cached for repeated lookups, supports only nominal types.

## Overview

Context serves as a type-safe registry for heterogeneous dependencies. Here's a quick example:

```scala

case class Config(debug: Boolean)
case class Logger(name: String)
case class Metrics(count: Int)
```

We can create and retrieve values by type:

```scala
val ctx: Context[Config & Logger & Metrics] = Context(
  Config(debug = true),
  Logger("app"),
  Metrics(count = 42)
)
// ctx: Context[Config & Logger & Metrics] = Context(repl.MdocSession.MdocApp.Metrics -> Metrics(42), repl.MdocSession.MdocApp.Logger -> Logger(app), repl.MdocSession.MdocApp.Config -> Config(true))

val config: Config = ctx.get[Config]
// config: Config = Config(true)
val logger: Logger = ctx.get[Logger]
// logger: Logger = Logger("app")
```

This ASCII diagram shows how Context maps types to values:

```
┌─────────────────────────────────┐
│         Context[R]              │
│  (Type-Indexed Store)           │
├─────────────────────────────────┤
│  Config         → Config(true)  │
│  Logger         → Logger("app") │
│  Metrics        → Metrics(42)   │
├─────────────────────────────────┤
│ ✓ Type-safe retrieval           │
│ ✓ No casting                    │
│ ✓ Cache-aware (O(1) repeats)    │
└─────────────────────────────────┘
```

## Motivation

When building modular applications, we often need to pass multiple dependencies around—a database connection, a config object, a logger, and so on. Existing approaches each have limitations:

**`Map[Class[_], Any]`** — no compile-time safety. You must cast the result and remember which keys you registered:

```scala
// Unsafe approach — easy to make mistakes
val deps = scala.collection.mutable.Map[Class[_], Any]()
deps(classOf[Config]) = Config(debug = true)
deps(classOf[Logger]) = Logger("app")

val config = deps(classOf[Config]).asInstanceOf[Config]  // Manual cast
val db = deps(classOf[Database]) // Runtime error if missing
```

**ZIO's `ZEnvironment`** — type-safe but requires the full ZIO effect system:

```scala
// Requires ZIO context

val makeEnv = for {
  config <- ZIO.service[Config]
  logger <- ZIO.service[Logger]
} yield (config, logger)
```

**Context** — combines compile-time type safety with synchronous, pure code:

```scala

case class Config(debug: Boolean)
case class Logger(name: String)

// Type-safe, no effects needed
val ctx = Context(Config(true), Logger("app"))
val config = ctx.get[Config]  // Compile-time proof it exists
```

## Installation

Add the ZIO Blocks Context module to your `build.sbt`:

```scala
libraryDependencies += "dev.zio" %% "zio-blocks-context" % "0.0.33"
```

## Construction

Context provides several ways to create instances. Choose the approach that best fits your use case: start empty and add values incrementally, or construct a fully-populated context directly with `apply`.

### Creating Empty Contexts

Use `Context.empty` to create an empty context with no entries:

```scala

val emptyCtx: Context[Any] = Context.empty
// emptyCtx: Context[Any] = Context()
val isEmpty = emptyCtx.isEmpty
// isEmpty: Boolean = true
```

An empty context has type `Context[Any]` and represents no stored dependencies. This is a useful starting point for incremental construction.

### Creating Multi-Value Contexts with `Context.apply`

`Context.apply` is overloaded to accept 1–10 values and returns a context with type `Context[A1 & A2 & ...]`, reflecting all stored types.

#### Single Value

Create a context with one value:

```scala
case class Config(debug: Boolean)
```

With `Config` defined, we can create a single-value context:

```scala
val single: Context[Config] = Context(Config(debug = true))
// single: Context[Config] = Context(repl.MdocSession.MdocApp1.Config -> Config(true))
```

#### Multiple Values

Create a context with multiple values—the type parameter automatically becomes an intersection of all stored types:

```scala
case class Logger(name: String)
```

With `Config` and `Logger` in scope, we can create a multi-value context:

```scala
val multi: Context[Config & Logger] = Context(
  Config(debug = true),
  Logger("myapp")
)
// multi: Context[Config & Logger] = Context(repl.MdocSession.MdocApp1.Logger -> Logger(myapp), repl.MdocSession.MdocApp1.Config -> Config(true))
```

### Building Incrementally with `Context#add`

For contexts that grow over time, use `Context#add` to build incrementally from an empty context. This is useful when dependencies become available at different points in your initialization:

```scala
val ctx = Context.empty
  .add(Config(debug = false))
  .add(Logger("init"))
// ctx: Context[Config & Logger] = Context(repl.MdocSession.MdocApp1.Logger -> Logger(init), repl.MdocSession.MdocApp1.Config -> Config(false))
```

The context accumulates all added entries:

```scala
val size1 = ctx.size
// size1: Int = 2
```

**When to use `Context#add` vs. `Context.apply`:**
- Use `Context.apply` when you know all dependencies upfront and can construct them together
- Use `Context#add` when dependencies are added incrementally or conditionally

## Core Operations

Context supports inspection, retrieval, and modification operations. All methods are type-safe and leverage the phantom type `R` to track what's stored.

### Inspection

The following methods let you check the contents of a context without retrieving specific values:

#### `Context#size`

Returns the number of entries in the context:

```scala
case class Config(debug: Boolean)
case class Logger(name: String)

val ctx = Context(Config(true), Logger("app"))
```

Get the number of entries in the context:

```scala
val sz = ctx.size
// sz: Int = 2
```

#### `Context#isEmpty`

Returns `true` if the context contains no entries:

```scala
case class Config(debug: Boolean)

val empty = Context.empty
val notEmpty = Context(Config(true))
```

Now check the `isEmpty` status of both contexts:

```scala
val e1 = empty.isEmpty
// e1: Boolean = true
val e2 = notEmpty.isEmpty
// e2: Boolean = false
```

#### `Context#nonEmpty`

Returns `true` if the context contains at least one entry (opposite of `isEmpty`):

```scala
case class Config(debug: Boolean)

val empty = Context.empty
val notEmpty = Context(Config(true))
```

Check the `nonEmpty` status of both contexts:

```scala
val e1 = empty.nonEmpty
// e1: Boolean = false
val e2 = notEmpty.nonEmpty
// e2: Boolean = true
```

### Retrieval

The following methods let you retrieve values from a context by type:

#### `Context#get`

Retrieves a value by type. The type bound `A >: R` ensures that a value of type `A` (or a subtype of `A`) is present at compile time:

```scala

case class Config(debug: Boolean)
case class Logger(name: String)
case class Metrics(count: Int)

val ctx = Context(Config(debug = true), Logger("app"), Metrics(100))

// Retrieve by exact type
val config = ctx.get[Config]
```

Or by supertype (subtype matching):

```scala

trait Animal { def sound: String }
case class Dog(name: String) extends Animal {
  def sound = "Woof"
}

val ctxDog = Context(Dog("Buddy"))
val animal = ctxDog.get[Animal]
```

If you attempt to retrieve a type that is not in the context, the code will not compile because the type bound `A >: R` requires it to be present:

```scala
// This is a compile-time error, not a runtime error:
// val metrics: String = ctx.get[String]  // Error: String is not in context type
```

#### `Context#getOption`

Retrieves a value if present, returning `Option[A]`. Unlike `get`, this method does not require the type to be in the context's type parameter. Use it for optional lookups:

```scala
case class Config(debug: Boolean)

val ctx = Context(Config(debug = true))
```

Try to retrieve both an existing type and a missing type:

```scala
val found: Option[Config] = ctx.getOption[Config]
// found: Option[Config] = Some(Config(true))
val missing: Option[String] = ctx.getOption[String]
// missing: Option[String] = None
```

### Modification

All modification methods return a new `Context`—the original remains immutable:

#### `Context#add`

Adds a value to the context, expanding the phantom type by `& A`:

```scala

case class Config(debug: Boolean)
case class Logger(name: String)

val ctx1 = Context(Config(true))
val ctx2 = ctx1.add(Logger("new"))
```

If a value of the same type already exists, it is replaced:

```scala

case class Config(debug: Boolean)
case class Logger(name: String)

val ctx1 = Context(Config(true))
val ctx2 = ctx1.add(Logger("new"))
val ctx3 = ctx2.add(Config(debug = false))
val replaced = ctx3.get[Config]
```

#### `Context#update`

Transforms an existing value if it is present. If the type is not found, the context is returned unchanged:

```scala

case class Metrics(count: Int)

val ctx = Context(Metrics(count = 10))
val updated = ctx.update[Metrics](m => m.copy(count = m.count + 5))
val newCount = updated.get[Metrics].count
```

#### `Context#++ (Union)`

Combines two contexts into a new context containing all entries. When both contexts contain the same type, the value from the right side (second argument) wins:

```scala

case class Config(debug: Boolean)
case class Logger(name: String)
case class Metrics(count: Int)

val left = Context(Config(debug = false), Logger("left"))
val right = Context(Config(debug = true), Metrics(99))
val merged = left ++ right
```

#### `Context#prune`

Narrows a context to contain only specified types. All other entries are discarded:

```scala

case class Config(debug: Boolean)
case class Logger(name: String)
case class Metrics(count: Int)

val full = Context(Config(true), Logger("app"), Metrics(100))
val justConfig = full.prune[Config]
val configSize = justConfig.size
```

#### `Context#toString`

Returns a human-readable representation of the context showing all type-value pairs:

```scala
case class Config(debug: Boolean)
case class Logger(name: String)

val ctx = Context(Config(debug = true), Logger("app"))
```

Convert the context to a human-readable string representation:

```scala
val str = ctx.toString
// str: String = "Context(repl.MdocSession.MdocApp1.Logger -> Logger(app), repl.MdocSession.MdocApp1.Config -> Config(true))"
```

## Covariance

`Context` is covariant in its type parameter, meaning `Context[Dog] <: Context[Animal]` when `Dog <: Animal`. This allows passing a more-specific context to code expecting a more-general one:

```scala

trait Animal { def sound: String }
case class Dog(name: String) extends Animal {
  def sound = "Woof"
}

def processAnimal(ctx: Context[Animal]): String = ctx.get[Animal].sound

val dogCtx = Context(Dog("Buddy"))
val sound = processAnimal(dogCtx)
```

Covariance also applies during retrieval—if you request a supertype, the stored subtype is returned.

## Type Safety: IsNominalType

Context only accepts **nominal types**—concrete classes, case classes, traits, and objects. The compiler automatically derives `IsNominalType[A]` for allowed types and rejects unsupported kinds:

**Supported:**
- Classes: `case class Config(...)`
- Traits: `trait Logger`
- Objects: `object Registry`
- Applied types: `List[Int]`, `Map[String, Int]`
- Enums (Scala 3): `enum Color { case Red, Green, Blue }`

**Not supported (compile error):**
- Intersection types: `A & B` (use the context type parameter instead)
- Union types: `A | B`
- Structural types: `{ def foo: Int }`

Attempting to store an unsupported type:

```scala
// This fails at compile time:
// Context.empty.add(null: (String & Int))  // Error: unsupported type
```

## Performance

**Caching**: When a value is retrieved via `get` or `getOption`, the result is cached. Repeated lookups for the same type return the cached value in O(1) time without traversing the entries again.

**Subtype matching**: Supertype lookups scan the entry list linearly to find a compatible subtype. After the first lookup, the result is cached, so subsequent requests for that supertype are O(1).

**Platform optimizations**: The JVM implementation uses `ConcurrentHashMap` for thread-safe caching. The JavaScript platform uses a simpler in-memory mutable hash map for efficient lookups.

## Comparing Approaches

Here is a comparison of Context with related alternatives:

| Feature             | `Map[Class[_], Any]` | `ZEnvironment`   | `Context` |
|---------------------|----------------------|------------------|-----------|
| Type-safe retrieval | ✗ (cast required)    | ✓                | ✓         |
| Compile-time proof  | ✗                    | ✓                | ✓         |
| Effect-free         | ✓                    | ✗ (requires ZIO) | ✓         |
| Immutable           | ✓                    | ✓                | ✓         |
| Cached lookups      | ✗                    | ✓                | ✓         |
| Supertype matching  | ✗                    | ✓                | ✓         |

## Integration with Wire and Scope

`Context` is the dependency carrier in ZIO Blocks' Wire-based dependency injection system. A `Wire[-In, +Out]` describes how to build an output given input dependencies, and contexts supply those dependencies. Wire and Scope work together to provide type-safe, compile-checked dependency injection:

```scala
// Pseudocode illustrating how Context integrates with Wire and Scope

case class Config(debug: Boolean)
case class Logger(name: String)
case class Service(config: Config, logger: Logger)

// Define a wire that requires Config and Logger to build Service
val buildService = Wire.make[Config & Logger, Service]

// Create a context with the required dependencies
val deps = Context(Config(debug = true), Logger("app"))

// Create a scope and instantiate the service
Scope.global.scoped { scope =>
  val service: Service = buildService.make(scope, deps)
}
```

## Running the Examples

All code from this guide is available as runnable examples in the `schema-examples` module.

**1. Clone the repository and navigate to the project:**

```bash
git clone https://github.com/zio/zio-blocks.git
cd zio-blocks
```

**2. Run individual examples with sbt:**

**Context construction: creating contexts with apply, empty.add, and inspecting size/isEmpty/nonEmpty**

```scala title="schema-examples/src/main/scala/context/ContextConstructionExample.scala" 
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

package context

// Context.empty creates an empty, type-safe dependency container.
// Use Context.apply(...) to construct contexts with 1–10 values.
// The phantom type parameter tracks which types are present.
object ContextConstructionExample extends App {

  case class Config(debug: Boolean)
  case class Logger(name: String)
  case class Metrics(count: Int)

  // Start with an empty context.
  show(Context.empty.size)
  show(Context.empty.isEmpty)

  // Create a context with one value.
  val ctx1 = Context(Config(debug = true))
  show(ctx1.size)
  show(ctx1.nonEmpty)

  // Create a context with multiple values (up to 10 supported).
  val ctx2 = Context(
    Config(debug = false),
    Logger("myapp")
  )
  show(ctx2.size)
  show(ctx2.isEmpty)

  // Create a larger context.
  val ctx3 = Context(
    Config(debug = true),
    Logger("prod"),
    Metrics(count = 100)
  )
  show(ctx3.size)

  // Build incrementally from empty using add.
  val ctxBuilt = Context.empty
    .add(Config(debug = false))
    .add(Logger("init"))
    .add(Metrics(count = 0))
  show(ctxBuilt.size)
  show(ctxBuilt.nonEmpty)

  // toString shows the context contents in a readable format.
  show(ctx3.toString)
}
```

([source](https://github.com/zio/zio-blocks/blob/main/schema-examples/src/main/scala/context/ContextConstructionExample.scala))

```bash
sbt "schema-examples/runMain context.ContextConstructionExample"
```

**Context retrieval: using get, supertype lookups, and getOption for safe access**

```scala title="schema-examples/src/main/scala/context/ContextRetrievalExample.scala" 
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

package context

// Context#get[A] retrieves a value by type with compile-time proof of existence.
// Context#getOption[A] retrieves a value if present, returning None if missing.
// Supertype matching allows retrieving a subtype using a more general supertype.
object ContextRetrievalExample extends App {

  case class Config(debug: Boolean)
  case class Logger(name: String)
  case class Metrics(count: Int)

  trait Animal { def sound: String }
  case class Dog(name: String) extends Animal {
    def sound = "Woof"
  }

  // Create a context with multiple values.
  val ctx = Context(
    Config(debug = true),
    Logger("app"),
    Metrics(count = 42)
  )

  // Retrieve by exact type.
  val config: Config = ctx.get[Config]
  show(config)

  val logger: Logger = ctx.get[Logger]
  show(logger)

  val metrics: Metrics = ctx.get[Metrics]
  show(metrics)

  // getOption returns Some if the type is present.
  val configOpt: Option[Config] = ctx.getOption[Config]
  show(configOpt)

  // getOption returns None if the type is missing (safe for optional lookups).
  val missingOpt: Option[String] = ctx.getOption[String]
  show(missingOpt)

  // Supertype matching: retrieve a subtype using a supertype.
  val dogCtx         = Context(Dog("Buddy"))
  val animal: Animal = dogCtx.get[Animal]
  show(animal.sound)

  // getOption also supports supertype matching.
  val animalOpt: Option[Animal] = dogCtx.getOption[Animal]
  show(animalOpt.map(_.sound))

  // Cache efficiency: repeated lookups return the cached instance.
  val first  = ctx.get[Logger]
  val second = ctx.get[Logger]
  show(first eq second)
}
```

([source](https://github.com/zio/zio-blocks/blob/main/schema-examples/src/main/scala/context/ContextRetrievalExample.scala))

```bash
sbt "schema-examples/runMain context.ContextRetrievalExample"
```

**Context modification: adding values, updating existing ones, merging contexts, and pruning types**

```scala title="schema-examples/src/main/scala/context/ContextModificationExample.scala" 
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

package context

// Context is immutable; modification methods return new contexts.
// Context#add expands the context with a new value (or replaces if type exists).
// Context#update transforms a value if present.
// Context#++ merges two contexts (right side wins on conflict).
// Context#prune narrows to specific types.
object ContextModificationExample extends App {

  case class Config(debug: Boolean)
  case class Logger(name: String)
  case class Metrics(count: Int)

  // Start with a simple context.
  val ctx1 = Context(Config(debug = false))
  show(ctx1.size)

  // add expands the context with a new value.
  val ctx2 = ctx1.add(Logger("app"))
  show(ctx2.size)
  show(ctx2.get[Config])
  show(ctx2.get[Logger])

  // add replaces if the type already exists.
  val ctx3 = ctx2.add(Config(debug = true))
  show(ctx3.size)
  show(ctx3.get[Config].debug)

  // update transforms a value if present.
  val ctx4    = ctx3.add(Metrics(count = 100))
  val updated = ctx4.update[Metrics](m => m.copy(count = m.count + 50))
  show(updated.get[Metrics].count)

  // update also works on existing types.
  val configUpdated = updated.update[Config](c => c.copy(debug = false))
  show(configUpdated.get[Config])

  // ++ merges two contexts; right side wins on conflict.
  val left   = Context(Config(debug = false), Logger("left"))
  val right  = Context(Config(debug = true), Metrics(count = 99))
  val merged = left ++ right
  show(merged.size)
  show(merged.get[Config].debug)
  show(merged.get[Logger].name)
  show(merged.get[Metrics].count)

  // prune narrows a context to specific types.
  val full = Context(Config(true), Logger("app"), Metrics(100))
  show(full.size)
  val justConfig = full.prune[Config]
  show(justConfig.size)
  show(justConfig.getOption[Logger])

  // Chaining modifications.
  val chain = Context.empty
    .add(Config(debug = true))
    .add(Logger("chain"))
    .add(Metrics(count = 0))
    .update[Metrics](m => m.copy(count = 42))
  show(chain.size)
  show(chain.get[Metrics].count)
}
```

([source](https://github.com/zio/zio-blocks/blob/main/schema-examples/src/main/scala/context/ContextModificationExample.scala))

```bash
sbt "schema-examples/runMain context.ContextModificationExample"
```
