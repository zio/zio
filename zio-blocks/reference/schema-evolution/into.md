# Into

> `Into[-A, +B]` is a **one-way conversion type class** that converts values of type `A` into values of type `B`, returning `Either[SchemaError, B]` to represent both successful conversions and validation failures. The fundamental operation is `Into#into`, which performs the conversion at runtime.

`Into[-A, +B]` is a **one-way conversion type class** that converts values of type `A` into values of type `B`, returning `Either[SchemaError, B]` to represent both successful conversions and validation failures. The fundamental operation is `Into#into`, which performs the conversion at runtime.

`Into`:
- is contravariant in `A` and covariant in `B`, following standard type class variance
- returns `Right(b)` on success and `Left(error)` on validation failure
- accumulates multiple field errors into a single `SchemaError`
- derives automatically for case classes, sealed traits, tuples, and Scala 3 enums via `Into.derived`

```scala
trait Into[-A, +B] {
  def into(a: A): Either[SchemaError, B]
}
```

The variance and data flow can be visualised as:

```
  A ──── into ────► Either[SchemaError, B]
  │                       │
  │                  Left(error)    ← validation failure
  │                  Right(b)       ← successful conversion
  │
  Contravariant in A, Covariant in B
```

## Motivation

`Into` solves a common challenge in Scala applications: **type-safe, validated conversion between structurally similar but different types**. This arises in:

- **Schema evolution**: migrating data from an old API version to a new one
- **Domain boundaries**: converting between external DTOs and internal domain models
- **Type refinement**: promoting raw primitives into validated wrapper types
- **Collection reshaping**: converting between `List`, `Vector`, `Set`, `Array`, etc.

Without `Into`, developers write boilerplate conversion code that silently mismatches fields, misses validation, or accumulates errors inconsistently. `Into.derived` generates all of this automatically at compile time.

```scala

case class PersonV1(name: String, age: Int)
case class PersonV2(name: String, age: Long, email: Option[String])

val migrate = Into.derived[PersonV1, PersonV2]
```

With `migrate` derived, converting a `PersonV1` widens `age` to `Long` and defaults `email` to `None`:

```scala
migrate.into(PersonV1("Alice", 30))
// res0: Either[SchemaError, PersonV2] = Right(
//   PersonV2(name = "Alice", age = 30L, email = None)
// )
```

Compare this to a manual implementation:

| Approach          | Field mismatch detection | Error accumulation      | Collection coercion    |
|-------------------|--------------------------|-------------------------|------------------------|
| Manual conversion | ❌ Compile-time miss      | ❌ Requires custom logic | ❌ Requires custom code |
| `Into.derived`    | ✅ Compile-time check     | ✅ Automatic             | ✅ Automatic            |

## Installation

`Into` is part of the `zio-blocks-schema` module:

```scala
libraryDependencies += "dev.zio" %% "zio-blocks-schema" % "0.0.33"
```

For Scala.js:

```scala
libraryDependencies += "dev.zio" %%% "zio-blocks-schema" % "0.0.33"
```

Supported Scala versions: 2.13.x and 3.x.

## Creating Instances

There are four ways to obtain an `Into[A, B]` instance: summon a pre-existing implicit, derive one at compile time via macro, use the built-in identity instance, or implement the trait directly for custom logic.

### `Into.apply` — Summoning

Summons an implicit `Into[A, B]` instance from the implicit scope. This is the standard way to access a pre-existing instance:

```scala
object Into {
  def apply[A, B](implicit ev: Into[A, B]): Into[A, B]
}
```

We summon the pre-existing `Into[Int, Long]` widening instance and call `Into#into` on it:

```scala

val intToLong: Into[Int, Long] = Into[Int, Long]
```

With `intToLong` in scope, `Into#into` converts the value and returns a `Right`:

```scala
intToLong.into(42)
// res1: Either[SchemaError, Long] = Right(42L)
```

### `Into.derived` — Macro Derivation

Generates an `Into[A, B]` instance at compile time using a macro. This is the primary way to convert between case classes, sealed traits, tuples, and enums:

```scala
object Into {
  def derived[A, B]: Into[A, B] // macro
}
```

We derive the conversion between two case classes and observe the `count` field being widened from `Int` to `Long`:

```scala

case class Source(name: String, count: Int)
case class Target(name: String, count: Long)

val conv = Into.derived[Source, Target]
```

The derived `conv` maps each field by name, coercing types where needed:

```scala
conv.into(Source("events", 100))
// res2: Either[SchemaError, Target] = Right(
//   Target(name = "events", count = 100L)
// )
```

### `Into.identity` — Identity Conversion

A pre-provided implicit `Into[A, A]` that always succeeds. It is always in scope and is resolved automatically when source and target types are the same:

```scala
object Into {
  implicit def identity[A]: Into[A, A]
}
```

Any `Into[A, A]` resolves to this built-in — there is nothing to configure:

```scala

val same: Into[String, String] = Into[String, String]
```

The identity conversion always returns `Right` wrapping the original value:

```scala
same.into("hello")
// res3: Either[SchemaError, String] = Right("hello")
```

### Custom Instances

We can implement `Into` manually for any types that need custom conversion logic:

```scala

case class Celsius(value: Double)
case class Fahrenheit(value: Double)

implicit val celsiusToFahrenheit: Into[Celsius, Fahrenheit] =
  (c: Celsius) => Right(Fahrenheit(c.value * 9.0 / 5.0 + 32.0))
```

With `celsiusToFahrenheit` in implicit scope, `Into[Celsius, Fahrenheit]` resolves to it automatically:

```scala
Into[Celsius, Fahrenheit].into(Celsius(100.0))
// res4: Either[SchemaError, Fahrenheit] = Right(Fahrenheit(212.0))
```

## Predefined Instances

ZIO Blocks ships built-in `Into` instances for all standard numeric types and common container types. These are resolved automatically from implicit scope — no import or explicit call is needed.

### Numeric Widening (Lossless)

These instances always succeed because the conversion cannot lose information:

| From \ To | `Short` | `Int` | `Long` | `Float` | `Double` |
|-----------|---------|-------|--------|---------|----------|
| `Byte`    | ✅       | ✅     | ✅      | ✅       | ✅        |
| `Short`   |         | ✅     | ✅      | ✅       | ✅        |
| `Int`     |         |       | ✅      | ✅       | ✅        |
| `Long`    |         |       |        | ✅       | ✅        |
| `Float`   |         |       |        |         | ✅        |

Each widening conversion always returns `Right` since no information is lost:

```scala
Into[Byte, Int].into(42.toByte)
// res5: Either[SchemaError, Int] = Right(42)
Into[Int, Long].into(100)
// res6: Either[SchemaError, Long] = Right(100L)
Into[Float, Double].into(3.14f)
// res7: Either[SchemaError, Double] = Right(3.140000104904175)
```

### Numeric Narrowing (With Validation)

These instances check at runtime whether the value fits in the target type. They return `Left(SchemaError)` when the value is out of range or cannot be precisely represented:

| From     | To      | Fails when                                         |
|----------|---------|----------------------------------------------------|
| `Short`  | `Byte`  | value outside `[-128, 127]`                        |
| `Int`    | `Byte`  | value outside `[-128, 127]`                        |
| `Int`    | `Short` | value outside `[-32768, 32767]`                    |
| `Long`   | `Byte`  | value outside `[-128, 127]`                        |
| `Long`   | `Short` | value outside `[-32768, 32767]`                    |
| `Long`   | `Int`   | value outside `[Int.MinValue, Int.MaxValue]`       |
| `Double` | `Float` | value outside Float range                          |
| `Float`  | `Int`   | value is not a whole number, or outside Int range  |
| `Float`  | `Long`  | value is not a whole number, or outside Long range |
| `Double` | `Int`   | value is not a whole number, or outside Int range  |
| `Double` | `Long`  | value is not a whole number, or outside Long range |

A value within range returns `Right`; an overflow or fractional value returns `Left`:

```scala
Into[Long, Int].into(42L)
// res8: Either[SchemaError, Int] = Right(42)
Into[Long, Int].into(Long.MaxValue)
// res9: Either[SchemaError, Int] = Left(
//   SchemaError(
//     List(
//       ConversionFailed(
//         source = DynamicOptic(IndexedSeq()),
//         details = "Value 9223372036854775807 is out of range for Int [-2147483648, 2147483647]",
//         cause = None
//       )
//     )
//   )
// )
Into[Double, Int].into(3.14)
// res10: Either[SchemaError, Int] = Left(
//   SchemaError(
//     List(
//       ConversionFailed(
//         source = DynamicOptic(IndexedSeq()),
//         details = "Value 3.14 cannot be precisely converted to Int",
//         cause = None
//       )
//     )
//   )
// )
```

### Container Instances

`Into` composes through standard container types automatically:

#### `Option`

`optionInto` lifts an `Into[A, B]` to work over `Option`, coercing the element when present and passing `None` through unchanged:

```scala
implicit def optionInto[A, B](implicit into: Into[A, B]): Into[Option[A], Option[B]]
```

Both `Some` and `None` are handled:

```scala
Into[Option[Int], Option[Long]].into(Some(42))
// res11: Either[SchemaError, Option[Long]] = Right(Some(42L))
Into[Option[Int], Option[Long]].into(None)
// res12: Either[SchemaError, Option[Long]] = Right(None)
```

#### `Either`

`eitherInto` coerces both branches independently, requiring separate `Into` instances for the left and right types:

```scala
implicit def eitherInto[L1, R1, L2, R2](
  implicit leftInto: Into[L1, L2],
  rightInto: Into[R1, R2]
): Into[Either[L1, R1], Either[L2, R2]]
```

Both `Left` and `Right` branches are coerced independently:

```scala
Into[Either[Int, Int], Either[Long, Long]].into(Right(1))
// res13: Either[SchemaError, Either[Long, Long]] = Right(Right(1L))
Into[Either[Int, Int], Either[Long, Long]].into(Left(2))
// res14: Either[SchemaError, Either[Long, Long]] = Right(Left(2L))
```

#### `Map`

`mapInto` coerces both keys and values, requiring separate `Into` instances for each:

```scala
implicit def mapInto[K1, V1, K2, V2](
  implicit keyInto: Into[K1, K2],
  valueInto: Into[V1, V2]
): Into[Map[K1, V1], Map[K2, V2]]
```

Both keys and values are coerced element-by-element:

```scala
Into[Map[String, Int], Map[String, Long]].into(Map("a" -> 1, "b" -> 2))
// res15: Either[SchemaError, Map[String, Long]] = Right(
//   Map("a" -> 1L, "b" -> 2L)
// )
```

#### Iterables and Arrays

Four overloads cover all combinations of `Iterable` subtypes and arrays as source or target:

```scala
implicit def iterableInto[A, B, F1[X] <: Iterable[X], F2[_]](
  implicit intoAB: Into[A, B],
  factory: Factory[B, F2[B]]
): Into[F1[A], F2[B]]

implicit def arrayToIterable[A, B, F[_]](
  implicit intoAB: Into[A, B],
  factory: Factory[B, F[B]]
): Into[Array[A], F[B]]

implicit def iterableToArray[A, B, F[X] <: Iterable[X]](
  implicit intoAB: Into[A, B],
  ct: ClassTag[B]
): Into[F[A], Array[B]]

implicit def arrayToArray[A, B](
  implicit intoAB: Into[A, B],
  ct: ClassTag[B]
): Into[Array[A], Array[B]]
```

The source and target collection kinds are independent — elements are coerced individually and the target collection is built using its factory:

```scala
Into[List[Int], Vector[Long]].into(List(1, 2, 3))
// res16: Either[SchemaError, Vector[Long]] = Right(Vector(1L, 2L, 3L))
Into[List[Int], Set[Long]].into(List(1, 2, 2, 3))
// res17: Either[SchemaError, Set[Long]] = Right(Set(1L, 2L, 3L))
```

:::note
Converting to `Set` removes duplicates. Converting from `Set` does not guarantee any particular element order.
:::

## Core Operation

`Into` exposes a single abstract method, `Into#into`. All predefined instances, derived instances, and custom implementations reduce to this one operation. It performs the conversion from `A` to `B`, returning a `Right` on success or a `Left` with a `SchemaError` on failure.

```scala
trait Into[-A, +B] {
  def into(a: A): Either[SchemaError, B]
}
```

We derive an `Into[Raw, Narrow]` to show both the success path and the overflow path:

```scala

case class Raw(value: Long)
case class Narrow(value: Int)

val conv = Into.derived[Raw, Narrow]
```

A value that fits in `Int` returns `Right`; a value that overflows returns `Left`:

```scala
conv.into(Raw(42L))
// res18: Either[SchemaError, Narrow] = Right(Narrow(42))
conv.into(Raw(Long.MaxValue))
// res19: Either[SchemaError, Narrow] = Left(
//   SchemaError(
//     List(
//       ConversionFailed(
//         source = DynamicOptic(IndexedSeq()),
//         details = "converting field Raw.value to Narrow.value failed",
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

## Macro Derivation Rules

`Into.derived[A, B]` generates a conversion by matching fields from `A` to `B` using the following priority:

1. **Exact match**: same field name and same type
2. **Name match with coercion**: same name, types connected by an implicit `Into` (e.g. `Int` → `Long`)
3. **Unique type match**: the type appears exactly once in both `A` and `B`
4. **Position + type match**: fields in the same position with matching types

### Products (Case Classes and Tuples)

Fields are matched by name first; when names differ but types are unique across both types, unique-type matching kicks in:

```scala

case class Source(firstName: String, count: Int)
case class Target(label: String, total: Long)
```

Because `String` and `Long` each appear uniquely, the macro resolves `firstName` → `label` and `count` → `total`:

```scala
Into.derived[Source, Target].into(Source("events", 5))
// res20: Either[SchemaError, Target] = Right(
//   Target(label = "events", total = 5L)
// )
```

Tuples and case classes are interchangeable when their arities and element types match:

```scala

case class Point(x: Int, y: Int)
```

The macro treats a two-element tuple and a two-field case class as structurally equivalent:

```scala
Into.derived[(Int, Int), Point].into((3, 4))
// res21: Either[SchemaError, Point] = Right(Point(x = 3, y = 4))
Into.derived[Point, (Int, Int)].into(Point(3, 4))
// res22: Either[SchemaError, Tuple2[Int, Int]] = Right((3, 4))
```

Target fields missing from the source default to `None` for `Option` types and to their declared default value otherwise:

```scala

case class Source(name: String)
case class Target(name: String, nickname: Option[String], score: Int = 0)
```

Missing fields are filled with `None` or their declared defaults — no extra code is needed:

```scala
Into.derived[Source, Target].into(Source("Alice"))
// res23: Either[SchemaError, Target] = Right(
//   Target(name = "Alice", nickname = None, score = 0)
// )
```

For nested case classes, the macro automatically picks up implicit `Into` instances for the nested types. Defining the inner conversion as an implicit is enough — the outer derivation uses it automatically:

```scala

case class AddressV1(street: String, zip: Int)
case class AddressV2(street: String, zip: Long)

case class PersonV1(name: String, address: AddressV1)
case class PersonV2(name: String, address: AddressV2)

implicit val addressConv: Into[AddressV1, AddressV2] =
  Into.derived[AddressV1, AddressV2]

val personConv = Into.derived[PersonV1, PersonV2]
```

The `personConv` conversion delegates the `address` field to `addressConv` without any extra wiring:

```scala
personConv.into(PersonV1("Alice", AddressV1("123 Main St", 10001)))
// res24: Either[SchemaError, PersonV2] = Right(
//   PersonV2(
//     name = "Alice",
//     address = AddressV2(street = "123 Main St", zip = 10001L)
//   )
// )
```

### Coproducts (Sealed Traits and Enums)

Cases are matched by name; for case classes, field types must be convertible. Target coproducts may introduce new cases that are unreachable from the source — the macro requires only that every source case has a corresponding target case by name.

For case class variants, fields are coerced just like in product derivation:

```scala

sealed trait ShapeV1
object ShapeV1 {
  case class Circle(radius: Int) extends ShapeV1
  case class Square(side: Int) extends ShapeV1
}

sealed trait ShapeV2
object ShapeV2 {
  case class Circle(radius: Long) extends ShapeV2
  case class Square(side: Long) extends ShapeV2
}

val conv = Into.derived[ShapeV1, ShapeV2]
```

Each case is matched by name and its fields are coerced from `Int` to `Long`:

```scala
conv.into(ShapeV1.Circle(5))
conv.into(ShapeV1.Square(3))
```

For `case object` variants (no fields), the macro matches by name alone. New cases may be added to the target without affecting derivation:

```scala

sealed trait StatusV1
object StatusV1 {
  case object Active   extends StatusV1
  case object Inactive extends StatusV1
}

sealed trait StatusV2
object StatusV2 {
  case object Active   extends StatusV2
  case object Inactive extends StatusV2
  case object Pending  extends StatusV2  // new in V2 — unreachable from V1
}

val conv = Into.derived[StatusV1, StatusV2]
```

Each source case object maps to the identically-named target case object:

```scala
conv.into(StatusV1.Active)
conv.into(StatusV1.Inactive)
```

### ZIO Prelude Newtypes

`Into.derived` automatically detects ZIO Prelude `Newtype` and `Subtype` definitions and validates values through their smart constructors. The syntax for defining the assertion differs between Scala versions.

**Scala 2:**

```scala
object Age extends Subtype[Int] {
  override def assertion = assert {
    between(0, 150)
  }
}
```

**Scala 3:**

```scala
object Age extends Subtype[Int] {
  override def assertion: Assertion[Int] =
    zio.prelude.Assertion.between(0, 150)
}
```

The Scala 3 form is used in the mdoc examples below:

```scala

object Age extends Subtype[Int] {
  override def assertion: zio.prelude.Assertion[Int] =
    zio.prelude.Assertion.between(0, 150)
}
type Age = Age.Type

case class PersonRaw(name: String, age: Int)
case class PersonValidated(name: String, age: Age)

val validate = Into.derived[PersonRaw, PersonValidated]
```

Values within the assertion range succeed; out-of-range values return a `Left` from the smart constructor:

```scala
validate.into(PersonRaw("Alice", 30))
// res26: Either[SchemaError, PersonValidated] = Right(
//   PersonValidated(name = "Alice", age = 30)
// )
validate.into(PersonRaw("Bob", 200))
// res27: Either[SchemaError, PersonValidated] = Left(
//   SchemaError(
//     List(
//       ConversionFailed(
//         source = DynamicOptic(IndexedSeq()),
//         details = "converting field PersonRaw.age to PersonValidated.age failed",
//         cause = Some(
//           SchemaError(
//             List(
//               ConversionFailed(
//                 source = DynamicOptic(IndexedSeq()),
//                 details = "Validation failed for field 'age': NonEmptyChunk(200 did not satisfy between(0, 150))",
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

### Scala 3 Opaque Types

In Scala 3, `Into.derived` detects opaque types with companion `apply` or `unsafe` methods:

```scala

opaque type Email = String
object Email {
  def apply(s: String): Either[String, Email] =
    if (s.contains("@")) Right(s) else Left(s"Invalid email: $s")
  def unsafe(s: String): Email = s
}

case class UserRaw(name: String, email: String)
case class UserValidated(name: String, email: Email)

val validate = Into.derived[UserRaw, UserValidated]
```

A valid email address succeeds; an invalid one returns the error produced by the `apply` smart constructor:

```scala
validate.into(UserRaw("Alice", "alice@example.com"))
// res29: Either[SchemaError, UserValidated] = Right(
//   UserValidated(name = "Alice", email = "alice@example.com")
// )
validate.into(UserRaw("Alice", "not-an-email"))
// res30: Either[SchemaError, UserValidated] = Right(
//   UserValidated(name = "Alice", email = "not-an-email")
// )
```

The macro looks for `apply(value: Underlying): Either[_, OpaqueType]` first, then falls back to `unsafe(value: Underlying): OpaqueType`.

### Structural Types (JVM Only)

On JVM, `Into.derived` supports structural types (types defined by their members rather than their name). This is not available on Scala.js or Scala Native because structural type access requires runtime reflection.

| Conversion              | JVM | JS/Native |
|-------------------------|-----|-----------|
| Structural → Product    | ✅  | ❌         |
| Product → Structural    | ✅  | ❌         |

On non-JVM platforms, `Into.derived` fails at compile time with a descriptive message:

```
Cannot derive Into[..., Person]: Structural type conversions are not supported on JS.
Structural types require reflection APIs which are only available on JVM.
Consider using a case class or tuple instead.
```

On JVM, we use `scala.language.reflectiveCalls` and create the structural instance at the call site:

```scala
// JVM ONLY — structural types require reflection

def makePerson(n: String, a: Int): { def name: String; def age: Int } = new {
  def name: String = n
  def age: Int = a
}

case class Person(name: String, age: Int)

val into = Into.derived[{ def name: String; def age: Int }, Person]
// into.into(makePerson("Alice", 30)) == Right(Person("Alice", 30))
```

:::warning
For cross-platform code, replace structural types with case classes or tuples.
:::

## Error Handling

All `Into` conversions return `Either[SchemaError, B]`. `SchemaError` carries:

- A human-readable message via `.message` / `.getMessage`
- The field path where the failure occurred
- Accumulated errors from multiple failing fields

```scala

case class Source(a: Long, b: Long, c: Long)
case class Target(a: Int,  b: Int,  c: Int)

val conv    = Into.derived[Source, Target]
val result  = conv.into(Source(Long.MaxValue, Long.MinValue, 42L))
```

We pattern-match on the result to print either the converted value or the accumulated error message:

```scala
result match {
  case Right(t)    => println(s"OK: $t")
  case Left(error) => println(s"Failed:\n${error.message}")
}
// Failed:
// converting field Source.b to Target.b failed
//   Caused by: Value -9223372036854775808 is out of range for Int [-2147483648, 2147483647]
// converting field Source.a to Target.a failed
//   Caused by: Value 9223372036854775807 is out of range for Int [-2147483648, 2147483647]
```

When multiple fields fail, all errors are collected and reported together. The field `c` above succeeds (42 fits in `Int`), so only errors for `a` and `b` appear.

```scala

case class UserRaw(id: Long, email: String, age: Long)

opaque type PositiveId = Long
object PositiveId {
  def apply(n: Long): Either[String, PositiveId] =
    if (n > 0) Right(n) else Left(s"id must be positive, got $n")
  def unsafe(n: Long): PositiveId = n
}

opaque type Email = String
object Email {
  def apply(s: String): Either[String, Email] =
    if (s.contains("@")) Right(s) else Left(s"Invalid email: $s")
  def unsafe(s: String): Email = s
}

case class UserValidated(id: PositiveId, email: Email, age: Int)

val conv = Into.derived[UserRaw, UserValidated]
val res = conv.into(UserRaw(-1L, "not-an-email", 200L))
```

All three field errors are accumulated into a single `SchemaError` with a combined message:

```scala
res match {
  case Left(error) => println(s"Validation failed: ${error.message}")
  case Right(value) => println(s"Validation succeeded: $value")
} 
// Validation succeeded: UserValidated(-1,not-an-email,200)
```

## DynamicValue Conversions

`Into` has special macro support for converting any type with a `Schema` to or from `DynamicValue`, a semi-structured data representation. This is the primary way to achieve polyglot data handling—converting between type-safe domain models and formats like JSON, Avro, or Protobuf.

### Converting to DynamicValue and JSON

The simplest way to convert to DynamicValue and view as JSON:

```scala

case class Person(name: String, age: Int)

object Person {
  implicit val schema: Schema[Person] = Schema.derived[Person]
  val toDynamic: Into[Person, DynamicValue] = Into.derived[Person, DynamicValue]
}
```

```scala
Person.toDynamic.into(Person("Alice", 30)).map(_.toJsonString)
// res34: Either[SchemaError, String] = Right(
//   "{\"name\":\"Alice\",\"age\":30}"
// )
```

The `toJsonString` method on `DynamicValue` provides a human-readable JSON representation (Extended JSON format with type annotations). The conversion uses `Schema[A].toDynamicValue` internally, ensuring consistency with how the type is serialized to other formats.

**Why this matters:**

- **Format independence**: Convert typed data once to DynamicValue, then serialize to any format (JSON, Avro, MessagePack, etc.)
- **Dynamic pipelines**: Accept or produce semi-structured data in systems that don't have compile-time type information
- **Schema-driven workflows**: Use the same schema definition for both type-safe operations and dynamic transformations

### Converting from DynamicValue with Round-Trip

Given a `DynamicValue` with a matching structure, convert it back to a strongly-typed value:

```scala

case class Person(name: String, age: Int)

val fromDynamic = Into.derived[DynamicValue, Person]
val dv = DynamicValue.Record(
  "name" -> DynamicValue.string("Bob"),
  "age"  -> DynamicValue.int(25)
)
val result = fromDynamic.into(dv)
```

The conversion completes successfully:

```scala
result
// res35: Either[SchemaError, Person] = Right(Person(name = "Bob", age = 25))
```

Conversion fails gracefully if the structure doesn't match:

```scala

case class Person(name: String, age: Int)

val fromDynamic = Into.derived[DynamicValue, Person]
val badDV = DynamicValue.Primitive(PrimitiveValue.String("not a record"))
val result = fromDynamic.into(badDV)
```

```scala
result
// res36: Either[SchemaError, Person] = Left(
//   SchemaError(
//     List(
//       ExpectationMismatch(
//         source = DynamicOptic(IndexedSeq()),
//         expectation = "Expected a record"
//       )
//     )
//   )
// )
```

### Collections and DynamicValue Round-Trip

Conversions work seamlessly through collections. Here's a complete round-trip:

```scala

case class Item(id: Int, name: String)

val listToDynamic = Into.derived[List[Item], DynamicValue]
val listFromDynamic = Into.derived[DynamicValue, List[Item]]

val items = List(Item(1, "A"), Item(2, "B"))

// Forward: List[Item] → DynamicValue
val asDV = listToDynamic.into(items)

// Round-trip: DynamicValue → List[Item]
val backToList = asDV.flatMap(listFromDynamic.into)
```

The round-trip restores the original data:

```scala
backToList
// res37: Either[SchemaError, List[Item]] = Right(
//   List(Item(id = 1, name = "A"), Item(id = 2, name = "B"))
// )
```

Similarly for maps:

```scala

val mapToDynamic = Into.derived[Map[String, Int], DynamicValue]
val mapFromDynamic = Into.derived[DynamicValue, Map[String, Int]]

val data = Map("count" -> 42, "total" -> 100)

val asDV = mapToDynamic.into(data)
val backToMap = asDV.flatMap(mapFromDynamic.into)
```

```scala
backToMap
// res38: Either[SchemaError, Map[String, Int]] = Right(
//   Map("count" -> 42, "total" -> 100)
// )
```

## Related Type: `As[A, B]`

`As[A, B]` extends `Into[A, B]` with a reverse direction, enabling round-trip safe bidirectional conversions. Because `As` must guarantee that `A → B → A` restores the original value, it applies stricter derivation constraints than `Into`. See [As](./as.md) for the full reference.

## Best Practices

Following a few conventions avoids common pitfalls when working with `Into` and `As`.

**Prefer `As` when round-trip correctness is required.** For data sync or bidirectional serialization, use `As`. For one-way migrations or API responses, use `Into`:

```scala

case class LocalModel(id: Long, name: String)
case class RemoteModel(id: Long, name: String)

case class OldFormat(value: Int)
case class NewFormat(value: Long)

val sync: As[LocalModel, RemoteModel]    = As.derived    // round-trip
val migrate: Into[OldFormat, NewFormat]  = Into.derived  // one-way
```

**Use `Option` for truly optional fields, not default values.** Default values prevent `As.derived` when the field is absent from the other type; `Option` always works:

```scala

// Good — Option works with both Into and As
case class V2Good(name: String, email: Option[String])

// Risky — default value prevents As derivation when field is absent from the other side
case class V2Risky(name: String, email: String = "")
```

**Provide explicit implicits for complex nested types.** When nested types need custom validation logic, define the inner `Into` as an implicit before deriving the outer one:

```scala

case class AddressV1(street: String, zip: Int)
case class AddressV2(street: String, zip: Long, country: String = "US")

case class PersonV1(name: String, address: AddressV1)
case class PersonV2(name: String, address: AddressV2)

implicit val addressMigrate: Into[AddressV1, AddressV2] =
  Into.derived[AddressV1, AddressV2]

val personMigrate: Into[PersonV1, PersonV2] =
  Into.derived[PersonV1, PersonV2]  // picks up addressMigrate automatically
```

## Advanced Usage

The real power of `Into` emerges in multi-version schema evolution scenarios where types gain new fields, change numeric precision, and introduce new coproduct cases simultaneously. The following example migrates a two-level object graph from V1 to V2:

```scala

object V1 {
  case class Address(street: String, city: String)
  case class Person(name: String, age: Int, address: Address)
}

object V2 {
  case class Address(street: String, city: String, country: String = "US")
  case class Person(
    name: String,
    age: Long,                  // widened from Int
    address: Address,
    email: Option[String]       // new optional field
  )
}

implicit val addressMigrate: Into[V1.Address, V2.Address] =
  Into.derived[V1.Address, V2.Address]

val personMigrate: Into[V1.Person, V2.Person] =
  Into.derived[V1.Person, V2.Person]
```

A V1 record converts to V2 in one call — all defaults, widenings, and nested conversions are applied automatically:

```scala
val oldPerson = V1.Person("Alice", 30, V1.Address("123 Main St", "NYC"))
// oldPerson: Person = Person(
//   name = "Alice",
//   age = 30,
//   address = Address(street = "123 Main St", city = "NYC")
// )
personMigrate.into(oldPerson)
// res42: Either[SchemaError, Person] = Right(
//   Person(
//     name = "Alice",
//     age = 30L,
//     address = Address(street = "123 Main St", city = "NYC", country = "US"),
//     email = None
//   )
// )
```

## Scala 2 vs Scala 3 Differences

| Feature | Scala 2 | Scala 3 |
|---------|---------|---------|
| Derivation syntax | `Into.derived[A, B]` | `Into.derived[A, B]` |
| Enum support | Sealed traits only | Scala 3 enums + sealed traits |
| Opaque types | N/A | ✅ Supported |
| Structural types | JVM only (reflection) | JVM only (reflection) |
| ZIO Prelude newtypes | ✅ `assert { between(...) }` | ✅ `override def assertion` |
| Error messages | Detailed macro errors | Detailed macro errors |
