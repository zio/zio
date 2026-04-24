# Unscoped

> `Unscoped[A]` is a marker typeclass for types that can safely escape a scope without tracking. Types with an `Unscoped` instance are considered "safe data"—they don't hold resources and can be freely extracted from a scope. Here's the definition:

`Unscoped[A]` is a marker typeclass for types that can safely escape a scope without tracking. Types with an `Unscoped` instance are considered "safe data"—they don't hold resources and can be freely extracted from a scope. Here's the definition:

```scala
trait Unscoped[A]
```

The `Unscoped` typeclass distinguishes between two categories of types:

1. **Unscoped types** (have an instance): Primitives, strings, collections, value types, and pure data. These can leave a scope without risk.
2. **Scoped types** (no instance): Resources like streams, connections, handles. These must remain tracked within a scope.

When the `$` operator is used to access a scoped value, if the result type has an `Unscoped` instance, it returns the value directly (unwrapped). Otherwise, it returns the value still wrapped in `$`.

## Motivation / Use Case

The exact problem: A `scoped` block automatically closes all resources when it exits. If you accidentally returned a resource (like a database connection or file handle) from the block, it would be closed—but you might try to use it later, causing a **use-after-close** crash. Here's an example (this would fail without `Unscoped`):

```scala

final class Database {
  def query(sql: String) = s"result: $sql"
}

// Without Unscoped constraint, this compiles (BAD):
// val db: Database = Scope.global.scoped { scope =>
//   val db = allocate(Resource(new Database()))
//   db  // BUG: returns the resource itself, not data extracted from it
// }
// db.query("SELECT 1")  // CRASH: use-after-close (scope already closed it)
```

The solution: `Unscoped` makes this a **compile error** instead of a runtime bug. When a `scoped` block returns a value, that value's type must have an `Unscoped` instance—meaning the type checker verifies you're only extracting *computed results* (like `Int`, `String`, or aggregate data), not resources themselves.

You can still extract computed results by using the `$` operator to unwrap scoped values *within* the scope:

```scala

Scope.global.scoped { scope =>

  val intValue = allocate(Resource(42))
  // Extract the Int value (not the Resource), computed inside the scope
  val n: Int = $(intValue)(x => x + 1)

  val text = allocate(Resource("hello"))
  // Extract the String value (not the Resource), computed inside the scope
  val s: String = $(text)(x => x.toUpperCase)

  (n, s)  // Tuple of pure data: safe to return
}
```

## Returning Unscoped Data from Scopes

Extract computed results that don't hold resources:

```scala

case class ProcessingResult(count: Int, elapsed: FiniteDuration)

object ProcessingResult {
  implicit val unscoped: Unscoped[ProcessingResult] = new Unscoped[ProcessingResult] {}
}

def processData(): ProcessingResult = Scope.global.scoped { scope =>

  val startTime = java.time.Instant.now()
  val input = allocate(Resource(Seq(1, 2, 3, 4, 5)))
  val count = $(input)(_.length)

  val endTime = java.time.Instant.now()
  val elapsed = java.time.Duration.between(startTime, endTime).toNanos

  ProcessingResult(count, FiniteDuration(elapsed, java.util.concurrent.TimeUnit.NANOSECONDS))
}

val result = processData()
println(result)
```

Only create instances for **pure data types** that don't hold resources. Never create instances for types that contain connections, streams, handles, or any resource-like fields.

## Predefined Instances

All built-in instances follow a simple principle: **if a type cannot hold resources, it gets an `Unscoped` instance**. Collections inherit this property from their elements — `List[Int]` is unscoped because `Int` is unscoped.

**Primitive and atomic values** (cannot hold resources by nature):
- `Int`, `Long`, `Short`, `Byte`, `Char`, `Boolean`, `Float`, `Double`, `Unit`
- `String`, `BigInt`, `BigDecimal`
- `java.util.UUID`

**Collections with conditional instances** (safe when elements/entries are unscoped):
- Sequences: `Array[A]`, `List[A]`, `Vector[A]`, `Seq[A]`, `IndexedSeq[A]`, `Iterable[A]`
- Sets: `Set[A]`
- Maps: `Map[K, V]` (when both `K` and `V` are unscoped)
- Wrappers: `Option[A]`, `Either[A, B]`, `Tuple2[A, B]` through `Tuple4[A, B, C, D]`
- ZIO types: `zio.blocks.chunk.Chunk[A]`

**Standard library time types** (immutable, cannot hold resources):
- `java.time.Instant`, `LocalDate`, `LocalTime`, `LocalDateTime`, `ZonedDateTime`, `OffsetDateTime`
- `java.time.Duration`, `Period`, `ZoneId`, `ZoneOffset`
- `scala.concurrent.duration.Duration`, `FiniteDuration`

All other types (resources, handles, connections) must be manually defined if needed.

## Thread Safety

`Unscoped` instances themselves are immutable and thread-safe. However, the types they mark must be truly immutable for safe concurrent use. For example, `Array[Int]` is mutable—if shared across threads without synchronization, it could cause data races.

## Integration

- [`Scope.$`](./scope.md) — the operator that uses `Unscoped`
- [`Resource`](./resource.md) — types that provide `Unscoped` may be wrapped in resources
- [`Scope`](./scope.md) — manages the lifecycle of resources
