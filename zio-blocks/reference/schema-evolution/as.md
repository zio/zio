# As

> `As[A, B]` is a **bidirectional conversion type class** that extends `Into[A, B]` with a reverse direction. In addition to converting `A → B` via `As#into`, it also converts `B → A` via `As#from`, providing a round-trip guarantee.

`As[A, B]` is a **bidirectional conversion type class** that extends `Into[A, B]` with a reverse direction. In addition to converting `A → B` via `As#into`, it also converts `B → A` via `As#from`, providing a round-trip guarantee.

`As`:
- extends `Into[A, B]`, so every `As` can be used wherever an `Into` is expected
- returns `Right(b)` or `Right(a)` on success and `Left(error)` on validation failure in both directions
- derives automatically for case classes, sealed traits, tuples, and Scala 3 enums via `As.derived`
- enforces stricter derivation constraints than `Into` to guarantee that `A → B → A` always restores the original value

```scala
trait As[A, B] extends Into[A, B] {
  def from(input: B): Either[SchemaError, A]
  def reverse: As[B, A]
}
```

The bidirectional data flow looks like this:

```
  ┌──────────────────────────────────────────────────┐
  │               As[A, B]                           │
  │                                                  │
  │     into(a: A) ──────────────────────► B         │
  │                                                  │
  │     from(b: B) ◄────────────────────── B         │
  │                                                  │
  │     reverse: As[B, A]  (flips directions)        │
  └──────────────────────────────────────────────────┘
```

`As` is the right choice when the conversion must be safe to run in both directions — for example when synchronising data between a local model and a remote representation, or when migrating a database schema that must remain rollback-capable.

## Installation

`As` is part of `zio-blocks-schema`:

```scala
libraryDependencies += "dev.zio" %% "zio-blocks-schema" % "0.0.33"
```

For Scala.js and Scala Native, use `%%%`:

```scala
libraryDependencies += "dev.zio" %%% "zio-blocks-schema" % "0.0.33"
```

Supported Scala versions: 2.13.x and 3.x.

## Creating Instances

There are three ways to obtain an `As[A, B]`: construct it from a pair of `Into` instances, derive it automatically with the macro, or summon an implicit already in scope.

### `As.apply` — Manual Construction

`As.apply(intoAB, intoBA)` composes two `Into` instances into one `As`:

```scala
object As {
  def apply[A, B](intoAB: Into[A, B], intoBA: Into[B, A]): As[A, B]
}
```

We build an `As[Int, Long]` by supplying both directions explicitly:

```scala

val intoAB: Into[Int, Long] = a => Right(a.toLong)
val intoBA: Into[Long, Int] = b =>
  if (b >= Int.MinValue && b <= Int.MaxValue) Right(b.toInt)
  else Left(SchemaError.validationFailed("overflow"))

val manualAs: As[Int, Long] = As(intoAB, intoBA)
```

With `manualAs` in scope we can convert in both directions and verify overflow detection:

```scala
manualAs.into(42)
// res0: Either[SchemaError, Long] = Right(42L)
manualAs.from(100L)
// res1: Either[SchemaError, Int] = Right(100)
manualAs.from(Long.MaxValue)
// res2: Either[SchemaError, Int] = Left(
//   SchemaError(
//     List(
//       ConversionFailed(
//         source = DynamicOptic(IndexedSeq()),
//         details = "overflow",
//         cause = None
//       )
//     )
//   )
// )
```

### `As.derived` — Macro Derivation

`As.derived[A, B]` generates an `As[A, B]` at compile time by deriving both `Into[A, B]` and `Into[B, A]` and running bidirectional compatibility checks:

```scala
object As {
  def derived[A, B]: As[A, B]
}
```

The macro works with case classes, sealed traits, Scala 3 enums, tuples, ZIO Prelude newtypes, Scala 3 opaque types, and structural types (JVM only). We derive an `As` for two case classes with matching fields:

```scala

case class PersonA(name: String, age: Int)
case class PersonB(name: String, age: Long)

val personAs: As[PersonA, PersonB] = As.derived[PersonA, PersonB]
```

Both `As#into` and `As#from` are now available, and we can verify that `A → B → A` restores the original value:

```scala
personAs.into(PersonA("Alice", 30))
// res3: Either[SchemaError, PersonB] = Right(
//   PersonB(name = "Alice", age = 30L)
// )
personAs.from(PersonB("Bob", 25L))
// res4: Either[SchemaError, PersonA] = Right(PersonA(name = "Bob", age = 25))
personAs.into(PersonA("Alice", 30)).flatMap(personAs.from)
// res5: Either[SchemaError, PersonA] = Right(
//   PersonA(name = "Alice", age = 30)
// )
```

### `As.apply[A, B]` — Summoning

`As.apply[A, B]` (with no arguments) summons an implicit `As[A, B]` already in scope — the same pattern used by `Into.apply`:

```scala
object As {
  def apply[A, B](implicit ev: As[A, B]): As[A, B]
}
```

This is useful when you want to retrieve a type-class instance by type rather than by variable name:

```scala

case class Foo(x: Int)
case class Bar(x: Int)

implicit val fooBarAs: As[Foo, Bar] = As.derived[Foo, Bar]

// Summon the implicit instance
val summoned = As[Foo, Bar]
summoned.into(Foo(1))
summoned.from(Bar(2))
```

## Core Operations

`As` exposes three operations: `As#into`, `As#from`, and `As#reverse`.

### `As#into` — Forward Conversion

`As#into` is inherited from `Into[A, B]` and converts an `A` into `Either[SchemaError, B]`:

```scala
trait As[A, B] extends Into[A, B] {
  def into(a: A): Either[SchemaError, B]
}
```

### `As#from` — Reverse Conversion

`As#from` is the operation that distinguishes `As` from `Into`. It converts a `B` back to `Either[SchemaError, A]`:

```scala
trait As[A, B] {
  def from(b: B): Either[SchemaError, A]
}
```

We define two simple wrapper types and derive an `As` between them to show both directions:

```scala

case class IntBox(value: Int)
case class LongBox(value: Long)

val boxAs: As[IntBox, LongBox] = As.derived[IntBox, LongBox]
```

`As#into` widens the value while `As#from` narrows it, validating that the result fits in the target type:

```scala
boxAs.into(IntBox(42))
// res8: Either[SchemaError, LongBox] = Right(LongBox(42L))
boxAs.from(LongBox(99L))
// res9: Either[SchemaError, IntBox] = Right(IntBox(99))
boxAs.from(LongBox(Long.MaxValue))
// res10: Either[SchemaError, IntBox] = Left(
//   SchemaError(
//     List(
//       ConversionFailed(
//         source = DynamicOptic(IndexedSeq()),
//         details = "converting field LongBox.value to IntBox.value failed",
//         cause = Some(
//           SchemaError(
//             List(
//               ConversionFailed(
//                 source = DynamicOptic(IndexedSeq()),
//                 details = "Value 9223372036854775807 is out of range for Int [-2147483648, 2147483647]",
//                 cause = None
//               )
//             )
//           )
//         )
//       )
//     )
//   )
// )
```

### `As#reverse` — Flipping Directions

`As#reverse` returns an `As[B, A]` whose `As#into` and `As#from` are swapped:

```scala
trait As[A, B] {
  def reverse: As[B, A]
}
```

`As#reverse` creates a new `As` without touching the original:

```scala
val revAs: As[LongBox, IntBox] = boxAs.reverse
// revAs: As[LongBox, IntBox] = zio.blocks.schema.As$$anon$1@6145661b

revAs.into(LongBox(5L))
// res11: Either[SchemaError, IntBox] = Right(IntBox(5))
revAs.from(IntBox(10))
// res12: Either[SchemaError, LongBox] = Right(LongBox(10L))
```

## Using `As` as `Into`

Because `As[A, B]` extends `Into[A, B]`, any `As` instance can be passed wherever an `Into` is expected — with no casts or wrapping needed.

We write a generic migration helper that requires only an `Into`, then pass an `As` directly:

```scala

case class P2D(x: Int, y: Int)
case class Coord(x: Int, y: Int)

def migrate[A, B](data: A)(implicit into: Into[A, B]): Either[SchemaError, B] =
  into.into(data)

implicit val pointAs: As[P2D, Coord] = As.derived[P2D, Coord]
```

Passing `pointAs` where the function expects `Into[P2D, Coord]` works because `As` is a subtype of `Into`:

```scala
migrate(P2D(1, 2))
// res13: Either[SchemaError, Coord] = Right(Coord(x = 1, y = 2))
```

## `As.reverseInto` Implicit

`AsLowPriorityImplicits` provides `As.reverseInto`, an implicit that materialises an `Into[B, A]` from any `As[A, B]` in scope. This lets libraries that only require `Into` automatically benefit from `As` instances without any extra wiring:

```scala
trait AsLowPriorityImplicits {
  implicit def reverseInto[A, B](implicit as: As[A, B]): Into[B, A]
}
```

With an `As[String, Int]` in scope, `As.reverseInto` synthesises `Into[Int, String]` automatically:

```scala

implicit val stringIntAs: As[String, Int] = new As[String, Int] {
  def into(s: String): Either[SchemaError, Int] =
    try Right(s.toInt)
    catch { case _: NumberFormatException => Left(SchemaError.validationFailed("not an int")) }
  def from(n: Int): Either[SchemaError, String] = Right(n.toString)
}
```

We import `As.reverseInto` and use it to obtain the reverse `Into[Int, String]`:

```scala

val intToStr: Into[Int, String] = reverseInto[String, Int]
// intToStr: Into[Int, String] = zio.blocks.schema.AsLowPriorityImplicits$$Lambda$17549/0x00007faac29bf110@e1a6316
intToStr.into(42)
// res14: Either[SchemaError, String] = Right("42")
```

## Derivation Rules

`As.derived` applies the same rules as `Into.derived` in both directions and adds bidirectional compatibility checks on top. The derivation supports the same type categories as `Into`.

### Products (Case Classes and Tuples)

For two case classes `A` and `B`, the macro checks:
- fields with matching names must be convertible in **both** directions
- fields present in one type but absent from the other must be `Option` (defaults are not allowed — see [Restrictions](#restrictions))

We derive `As` for two structurally compatible case classes:

```scala

case class UserV1(name: String, age: Int)
case class UserV2(name: String, age: Long)

val userAs: As[UserV1, UserV2] = As.derived[UserV1, UserV2]
```

Tuples are matched positionally, so field name checks are skipped:

```scala

val tupleAs: As[(Int, String), (Long, String)] = As.derived[(Int, String), (Long, String)]
```

### Coproducts (Sealed Traits and Enums)

`As.derived` handles sealed traits and Scala 3 enums the same way `Into.derived` does — each subtype is matched by name and derived recursively:

```scala

sealed trait ShapeV1
object ShapeV1 {
  case class Circle(radius: Int)  extends ShapeV1
  case class Rect(w: Int, h: Int) extends ShapeV1
}

sealed trait ShapeV2
object ShapeV2 {
  case class Circle(radius: Long) extends ShapeV2
  case class Rect(w: Long, h: Long) extends ShapeV2
}

val shapeAs: As[ShapeV1, ShapeV2] = As.derived[ShapeV1, ShapeV2]
```

### Numeric Coercions

All numeric primitive types (`Byte`, `Short`, `Int`, `Long`, `Float`, `Double`) are bidirectionally coercible. Widening always succeeds; narrowing validates at runtime and returns a `Left` on overflow:

```scala

case class IntModel(value: Int)
case class LongModel(value: Long)

val numericAs: As[IntModel, LongModel] = As.derived[IntModel, LongModel]
```

A value within `Int` range round-trips without loss; one outside it fails on the way back:

```scala
numericAs.into(IntModel(1000)).flatMap(numericAs.from)
// res15: Either[SchemaError, IntModel] = Right(IntModel(1000))
numericAs.from(LongModel(Long.MaxValue))
// res16: Either[SchemaError, IntModel] = Left(
//   SchemaError(
//     List(
//       ConversionFailed(
//         source = DynamicOptic(IndexedSeq()),
//         details = "converting field LongModel.value to IntModel.value failed",
//         cause = Some(
//           SchemaError(
//             List(
//               ConversionFailed(
//                 source = DynamicOptic(IndexedSeq()),
//                 details = "Value 9223372036854775807 is out of range for Int [-2147483648, 2147483647]",
//                 cause = None
//               )
//             )
//           )
//         )
//       )
//     )
//   )
// )
```

## Restrictions

`As` enforces constraints that `Into` does not. Because `As.derived` must produce valid conversions in both directions, it rejects configurations that would silently lose data during a round-trip.

**Default values on asymmetric fields are rejected.** A field with a default that has no counterpart in the other type cannot be round-tripped: when converting back, the field is missing and there is no way to distinguish a real default from a missing value:

```scala

case class WithDefault(name: String, age: Int = 25)
case class NoDefault(name: String)

// Does NOT compile — age has a default but is absent from NoDefault:
// As.derived[WithDefault, NoDefault]
```

Default values are allowed when the field exists in **both** types, because the value is never discarded during the round-trip:

```scala

case class PersonA(name: String, age: Int = 25)
case class PersonB(name: String, age: Int)

As.derived[PersonA, PersonB]  // compiles — age is present in both types
```

**`Option` fields on one side are allowed.** An `Option` field absent from the other type round-trips cleanly: `Some(v)` becomes `None` after a round-trip, which is the only safe behaviour for a missing field:

```scala

case class TypeA(name: String, nickname: Option[String])
case class TypeB(name: String)

As.derived[TypeA, TypeB]  // compiles
```

**Numeric coercions must be invertible in both directions.** Widening `Int → Long` is automatically paired with narrowing `Long → Int`. The narrowing validates at runtime, so the round-trip is safe even though it can fail:

```scala

case class IntVersion(value: Int)
case class LongVersion(value: Long)

As.derived[IntVersion, LongVersion]  // compiles — widening + narrowing form a valid pair
```

**Fields present in one type but absent from the other must be `Option`.** A non-optional field that exists only on one side cannot be populated in the reverse direction:

```scala

case class Short_(name: String)
case class Long_(name: String, extra: String)

// Does NOT compile — extra is not Optional and does not exist in Short_:
// As.derived[Short_, Long_]

case class Long2_(name: String, extra: Option[String])

As.derived[Short_, Long2_]  // compiles — extra is Optional
```

## DynamicValue Conversions

Like `Into`, `As` supports bidirectional conversions with `DynamicValue`, allowing you to define a single schema and use it for both type-safe operations and polyglot data handling.

### Bidirectional DynamicValue Support with JSON Round-Trip

You can derive `As[A, DynamicValue]` for any type with a `Schema[A]` and achieve full polyglot round-trips:

```scala

case class Config(host: String, port: Int)

object Config {
  implicit val schema: Schema[Config] = Schema.derived[Config]
  val asDynamic: As[Config, DynamicValue] = As.derived[Config, DynamicValue]
}
```

```scala
// Forward: Config → DynamicValue → JSON
Config.asDynamic.into(Config("localhost", 8080)).map(_.toJsonString)
// res21: Either[SchemaError, String] = Right(
//   "{\"host\":\"localhost\",\"port\":8080}"
// )
```

Now in the reverse direction, deserialize JSON back to Config:

```scala

case class Config(host: String, port: Int)

object Config {
  implicit val schema: Schema[Config] = Schema.derived[Config]
  val asDynamic: As[Config, DynamicValue] = As.derived[Config, DynamicValue]

  // JSON string to parse
  val jsonString = """{"host":"example.com","port":9000}"""
}
```

```scala
// Reverse: JSON → DynamicValue → Config
for {
  dv <- Config.jsonString.fromJson[DynamicValue]
  config <- Config.asDynamic.from(dv)
} yield config
// res22: Either[SchemaError, Config] = Right(
//   Config(host = "example.com", port = 9000)
// )
```

The call to `jsonString.fromJson[DynamicValue]` parses the JSON string into a `DynamicValue`, and `asDynamic.from` converts it back to the strongly-typed `Config`. (Equivalently, you could use `Schema[DynamicValue].getInstance(JsonFormat).decode(jsonString)` for the same decoding step.) This demonstrates the full cycle: **JSON → DynamicValue → Type**, ensuring perfect round-trip fidelity.

### Use Cases

`As` is ideal when data must flow in both directions within the same system, with guarantees that neither direction silently loses or corrupts data.

#### Polyglot configuration systems

Configuration is often stored externally (Consul, etcd, a JSON file) and must be read, modified in-place, and written back. A naive approach requires two separate conversions — `Into[DynamicValue, DatabaseConfig]` to read and `Into[DatabaseConfig, DynamicValue]` to write — with no guarantee they align. `As` solves this by providing a single bidirectional instance that the macro verifies will round-trip faithfully.

Consider a service that:
1. Reads config from an external store (JSON → `DynamicValue` → typed `DatabaseConfig`)
2. Applies business logic to the typed config (validate, scale, migrate)
3. Writes the updated config back to the store (typed `DatabaseConfig` → `DynamicValue` → JSON)

Without `As`, step 3 might serialize data differently than step 1 read it, causing silent corruption or misalignment. With `As`, the macro guarantees that `config → DynamicValue → config'` preserves the structure.

```scala

case class DatabaseConfig(host: String, port: Int, timeout: Long)

object DatabaseConfig {
  implicit val schema: Schema[DatabaseConfig] = Schema.derived[DatabaseConfig]
  val asDynamic: As[DatabaseConfig, DynamicValue] = As.derived[DatabaseConfig, DynamicValue]
}
```

```scala
// Simulate JSON arriving from the config store (e.g. Consul, etcd, a JSON file)
val storedJson = """{"host":"db.prod.example.com","port":5432,"timeout":30000}"""
// storedJson: String = "{\"host\":\"db.prod.example.com\",\"port\":5432,\"timeout\":30000}"

val result = for {
  stored  <- storedJson.fromJson[DynamicValue]          // Step 1: Read from store
  config  <- DatabaseConfig.asDynamic.from(stored)      // Step 2a: Hydrate into typed config
  updated  = config.copy(timeout = 60000)               // Step 2b: Apply business logic
  written <- DatabaseConfig.asDynamic.into(updated)     // Step 3: Serialize back to store (guaranteed round-trip)
} yield written.toJsonString
// result: Either[SchemaError, String] = Right(
//   "{\"host\":\"db.prod.example.com\",\"port\":5432,\"timeout\":60000}"
// )

result
// res23: Either[SchemaError, String] = Right(
//   "{\"host\":\"db.prod.example.com\",\"port\":5432,\"timeout\":60000}"
// )
```

## Scala 2 vs Scala 3 Differences

| Feature | Scala 2 | Scala 3 |
|---------|---------|---------|
| Derivation syntax | `As.derived[A, B]` | `As.derived[A, B]` |
| Enum support | Sealed traits only | Scala 3 enums + sealed traits |
| Opaque types | N/A | ✅ Supported |
| Structural types | JVM only (reflection) | JVM only (reflection) |
| ZIO Prelude newtypes | ✅ `assert { between(...) }` | ✅ `override def assertion` |
| Error messages | Detailed macro errors | Detailed macro errors |
| DynamicValue ambiguity detection | ✅ Two-pass implicit resolution | ✅ Built-in ambiguity reporting |

## Integration

`As[A, B]` is defined in `zio.blocks.schema` alongside `Into[A, B]`. Because `As` is a subtype of `Into`, the two type classes compose naturally: you can derive an outer `As` from inner `As` instances, or mix `As` and custom `Into` instances when some fields need one-way or custom logic.

For a full reference on one-way conversions and the derivation rules that `As` builds on, see [Into](./into.md).
