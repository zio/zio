# Schema

> `Schema[A]` is the primary data type in ZIO Blocks (ZIO Schema 2) that contains reified information about the structure of a Scala data type `A`, together with the ability to tear down and build up values of that type.

`Schema[A]` is the primary data type in ZIO Blocks (ZIO Schema 2) that contains reified information about the structure of a Scala data type `A`, together with the ability to tear down and build up values of that type.

```scala
final case class Schema[A](reflect: Reflect.Bound[A])
```

```
┌────────────────────────────────────────────────────────────────┐
│                        Schema[A]                               │
├────────────────────────────────────────────────────────────────┤
│                Reflect.Bound[A] (Reflect[Binding, A])          │
│    ┌─────────────────────────────────────────────────────┐     │
│    │  Structure       │  TypeName[A], DynamicValue       │     │
│    │  (ADT nodes)     │  Doc, Examples, Default Value    │     │
│    │                  │  Modifiers, Metadata             │     │
│    └─────────────────────────────────────────────────────┘     │
│    ┌─────────────────────────────────────────────────────┐     │
│    │  Binding[T, A]                                      │     │
│    │  - Constructor[A] (build values)                    │     │
│    │  - Deconstructor[A] (tear down values)              │     │
│    └─────────────────────────────────────────────────────┘     │
└────────────────────────────────────────────────────────────────┘
```

## Reflect: Structure vs Binding

The [`Reflect`](./reflect.md) data type represents the structure of Scala types. It is parameterized by `F[_, _]` which allows plugging in different "binding" strategies:

```
┌────────────────────────────────────────────────────────────────┐
│                    Reflect[F[_, _], A]                         │
├────────────────────────────────────────────────────────────────┤
│  F = Binding      → Reflect.Bound[A]    (with construction/    │
│                                          deconstruction)       │
│  F = NoBinding    → Reflect.Unbound[A]  (pure data,            │
│                                          serializable)         │
└────────────────────────────────────────────────────────────────┘
```

- **Bound Schema**: Contains functions for constructing/deconstructing values (not serializable)
- **Unbound Schema**: Pure data representation (can be serialized across the wire)

Schema is a bound Reflect, meaning that other than structural information, it also contains construction and deconstruction capabilities via the `Binding` type:

```scala
// Schema wraps a bound Reflect
final case class Schema[A](reflect: Reflect.Bound[A])

// Reflect.Bound is a type alias
type Bound[A] = Reflect[Binding, A]
```

## Schema Derivation

ZIO Blocks provides both automatic schema derivation and also ways to manually create schemas. As an end user, you will mostly use automatic derivation. So we will focus on automatic derivation here. If you need to go deeper and create schemas manually, check out the [Reflect](./reflect.md) and [Binding](./binding.md) documentation pages.

To leverage auto-derivation, simply define an implicit `Schema` for your type using `Schema.derived`:

```scala

case class Person(name: String, age: Int)

object Person {
  implicit val schema: Schema[Person] = Schema.derived
}
```

It will automatically derive the schema for `Person` based on its structure. After that, you can summon the schema using `Schema[Person]` anywhere in your code.

For sealed traits (ADTs), it will derive the schema for all subtypes as well:

```scala

sealed trait Shape
object Shape {
  case class Circle(radius: Double) extends Shape
  case class Rectangle(width: Double, height: Double) extends Shape
  
  implicit val schema: Schema[Shape] = Schema.derived
}
```

Under the hood, the derivation process includes deriving schemas for cases of enum (i.e. `Circle` and `Rectangle`) and finally creating a schema for the `Shape` trait itself.

## Built-in Schemas

ZIO Blocks ships with a comprehensive set of pre-defined schemas for standard Scala and Java types, eliminating boilerplate and ensuring consistent behavior across your application.

### Primitive Types

The foundational building blocks for all data types. These schemas leverage the [register-based architecture](registers.md) for zero-allocation performance:

```scala

Schema[Unit]      // The unit type (singleton value)
Schema[Boolean]   // Boolean values (true/false)
Schema[Byte]      // 8-bit signed integer (-128 to 127)
Schema[Short]     // 16-bit signed integer
Schema[Int]       // 32-bit signed integer
Schema[Long]      // 64-bit signed integer
Schema[Float]     // 32-bit IEEE 754 floating point
Schema[Double]    // 64-bit IEEE 754 floating point
Schema[Char]      // 16-bit Unicode character
Schema[String]    // Immutable character sequence
```

### Arbitrary Precision Numbers

For financial calculations and scenarios requiring exact decimal representation:

```scala

Schema[BigInt]      // Arbitrary precision integer
Schema[BigDecimal]  // Arbitrary precision decimal
```

### Temporal Types (java.time)

Complete coverage of the modern Java Time API for robust date/time handling:

```scala

// Date components
Schema[java.time.LocalDate]      // Date without time (2024-01-15)
Schema[java.time.LocalTime]      // Time without date (14:30:00)
Schema[java.time.LocalDateTime]  // Date and time without timezone

// Timezone-aware types  
Schema[java.time.ZonedDateTime]   // Full date-time with timezone
Schema[java.time.OffsetDateTime]  // Date-time with UTC offset
Schema[java.time.OffsetTime]      // Time with UTC offset
Schema[java.time.ZoneId]          // Timezone identifier (e.g., "America/New_York")
Schema[java.time.ZoneOffset]      // Fixed UTC offset (e.g., +05:30)

// Duration and period
Schema[java.time.Duration]  // Time-based duration (hours, minutes, seconds)
Schema[java.time.Period]    // Date-based period (years, months, days)
Schema[java.time.Instant]   // Point on the timeline (Unix timestamp)

// Calendar components
Schema[java.time.Year]       // Year value (2024)
Schema[java.time.Month]      // Month of year (JANUARY..DECEMBER)
Schema[java.time.DayOfWeek]  // Day of week (MONDAY..SUNDAY)
Schema[java.time.YearMonth]  // Year and month combination
Schema[java.time.MonthDay]   // Month and day combination
```

### Common Java Utility Types

There are also schemas for frequently used Java utility types, UUID and Currency:

```scala

Schema[java.util.UUID]      // 128-bit universally unique identifier
Schema[java.util.Currency]  // ISO 4217 currency code
```

### Optional Values

ZIO Blocks provides specialized `Option` schemas optimized for primitive types. These avoid boxing overhead by storing primitive values directly in registers:

```scala

// Specialized primitive options (no boxing overhead)
Schema[Option[Boolean]]  // Also: Byte, Short, Int, Long, Float, Double, Char, Unit
```

Other than primitive types, ZIO Blocks uses a generic representation for `Option[A]` which works for all reference types:

```scala

// Reference type options (requires A <: AnyRef)
Schema[Option[A]]  // Generic option for reference types
```

### Collection Types

ZIO Blocks also provides polymorphic schemas for standard Scala collections. You can summon schemas for collections of any element type `A` (and key/value types `K`/`V` for maps):

```scala

// Built-in Scala collections
Schema[List[A]]        // Immutable singly-linked list
Schema[Vector[A]]      // Immutable indexed sequence (efficient random access)
Schema[Set[A]]         // Immutable set (unique elements)
Schema[Seq[A]]         // General immutable sequence
Schema[IndexedSeq[A]]  // Indexed sequence
Schema[Map[K, V]]      // Immutable key-value mapping

// ZIO Blocks collections (the chunk module)
Schema[zio.blocks.chunk.Chunk[A]]       // Chunk sequence
Schema[zio.blocks.chunk.ChunkMap[K, V]] // ChunkMap key-value mapping
```

To learn how to create custom collection schemas, check out the [`Sequence`](./reflect.md#3-sequence) and [`Map`](./reflect.md#4-map) nodes on the documentation of [`Reflect`](./reflect.md) data type.

### DynamicValue

ZIO Blocks includes a built-in schema for `DynamicValue`, a semi-structured data representation that serves as a superset of JSON:

```scala

val schema = Schema[DynamicValue]  // Semi-structured data (superset of JSON)
```

Having the schema for `DynamicValue` allows seamless encoding/decoding between `DynamicValue` and other formats, such as JSON, Avro, Protobuf, etc. It enables us to convert our type-safe data into a semi-structured representation and serialize it into any desired format.

### DynamicValue toString (EJSON Format)

`DynamicValue` has a custom `toString` that produces EJSON (Extended JSON) format - a superset of JSON that handles non-string keys, tagged variants, and typed primitives:

```scala

// Records have unquoted keys
val record = DynamicValue.Record(Vector(
  "name" -> DynamicValue.Primitive(PrimitiveValue.String("Alice")),
  "age" -> DynamicValue.Primitive(PrimitiveValue.Int(30))
))
println(record)
// {
//   name: "Alice",
//   age: 30
// }

// Maps have quoted string keys
val map = DynamicValue.Map(Vector(
  DynamicValue.Primitive(PrimitiveValue.String("key")) ->
    DynamicValue.Primitive(PrimitiveValue.String("value"))
))
println(map)
// {
//   "key": "value"
// }

// Variants use @ metadata
val variant = DynamicValue.Variant("Some", DynamicValue.Record(Vector(
  "value" -> DynamicValue.Primitive(PrimitiveValue.Int(42))
)))
println(variant)
// {
//   value: 42
// } @ {tag: "Some"}

// Typed primitives use @ metadata
val instant = DynamicValue.Primitive(PrimitiveValue.Instant(java.time.Instant.now))
println(instant)
// 1705312800000 @ {type: "instant"}
```

**Key EJSON properties:**
- **Records**: unquoted keys (`{ name: "John" }`)
- **Maps**: quoted string keys (`{ "name": "John" }`) or unquoted non-string keys (`{ 42: "value" }`)
- **Variants**: postfix `@ {tag: "CaseName"}`
- **Typed primitives**: postfix `@ {type: "instant"}` for types that would lose precision as JSON

## Debug-Friendly toString

`Schema` has a custom `toString` that wraps the underlying `Reflect` output in a `Schema { ... }` block, providing a complete structural view of your data types:

```scala

case class Person(name: String, age: Int)
object Person {
  implicit val schema: Schema[Person] = Schema.derived
}

println(Schema[Person])
// Output:
// Schema {
//   record Person {
//     name: String
//     age: Int
//   }
// }
```

For primitive schemas:

```scala
println(Schema[Int])
// Output:
// Schema {
//   Int
// }
```

This format makes it easy to inspect complex nested schemas during debugging. See the [Reflect documentation](./reflect.md#debug-friendly-tostring) for details on the SDL format used for the inner structure.

## Encoding and Decoding

The `Schema[A]` provides methods to encode and decode values of type `A` to/from various formats using the `Format` abstraction:

```scala
case class Schema[A](reflect: Reflect.Bound[A]) {
  def encode[F <: codec.Format](format: F)(output: format.EncodeOutput)(value: A): Unit = ???
  def decode[F <: codec.Format](format: F)(decodeInput: format.DecodeInput): Either[SchemaError, A] = ???
}
```

The `Format` is the base abstraction for serialization formats in ZIO Blocks, such as Avro, JSON, Protobuf, etc. Each format associates with a specific codec type class which defines the interface for encoding and decoding values and a deriver for deriving codecs for specific types.

ZIO Blocks has built-in support for several popular formats, currently `AvroFormat` and `JsonFormat`. You can also implement your own custom formats by extending the `Format` trait.

The following example demonstrates encoding and decoding a `Person` case class to/from JSON using the built-in `JsonFormat`:

```scala

case class Person(name: String, age: Int)

object Person {
  implicit val schema: Schema[Person] = Schema.derived
}

object EncodeDecodeExample extends App {
  val person = Person("John", 42)

  // Encode to JSON
  val encodedBuffer = {
    val buffer = ByteBuffer.allocate(1024)
    Schema[Person].encode(JsonFormat)(buffer)(person)
    buffer.flip()
    buffer
  }

  // Extract JSON string
  val jsonString = new String(encodedBuffer.duplicate().array())
  println(s"Encoded JSON: $jsonString")

  // Decode back to Person
  val decodedPerson = Schema[Person].decode(JsonFormat)(encodedBuffer.duplicate())
  println(s"Decoded Person: $decodedPerson")
}
```

Please note that both `Schema#encode` and `Schema#decode` cache instances, so using encode and decode in multiple places only performs the derivation process once.

## Type Class Derivation

To derive type class instances for a type `A` based on its schema, you can use the `derive` method on `Schema[A]`. This method takes a `Deriver` for the desired type class and produces an instance of that type class for `A`.

In the following example, we derive a JSON codec for the `Person` case class using the `JsonFormat` deriver:

```scala

case class Person(name: String, age: Int)

object Person {
  implicit val schema: Schema[Person] = Schema.derived
  val codec: JsonCodec[Person] = schema.derive(JsonFormat)
}

val person = Person("John", 42)

// Encode to JSON
val json = Person.codec.encode(person)

// Extract JSON string
val jsonString = new String(json)
println(s"Encoded JSON: $jsonString")

// Decode back to Person
val decodedPerson = Person.codec.decode(json)
println(s"Decoded Person: $decodedPerson")
```

## Metadata Operations

ZIO Blocks allows attaching metadata to schemas and their fields, such as documentation, example values, and default values. This metadata can be useful for generating API documentation, client code, or providing hints to serialization formats.

Here is an example of how to set and retrieve documentation values:

```scala

case class Person(name: String, age: Int)

object Person extends CompanionOptics[Person]{
  implicit val schema: Schema[Person] = Schema.derived

  val name: Lens[Person, String] = optic(_.name)
  val age: Lens[Person, Int]     = optic(_.age)
}

// Add documentation to the schema
val documented: Schema[Person] = Schema[Person].doc("A person entity")

// Get documentation
val doc: Doc = Schema[Person].doc

// Add documentation to a field using optics
val fieldDoc: Schema[Person] = Schema[Person].doc(Person.name, "The person's name")
```

The important thing to note here is that we can use optics to target specific fields within the schema when adding or retrieving metadata. In the last example, we added documentation specifically to the `name` field of the `Person` schema.

Similarly, you can set and get example values:

```scala

// Add example values
val withExamples: Schema[Person] = 
  Schema[Person].examples(Person("Alice", 30), Person("Bob", 25))

// Get examples
val examples: Seq[Person] = Schema[Person].examples

// Add examples to a specific field
val fieldExamples: Schema[Person] =
  Schema[Person].examples(Person.name, "Alice", "Bob", "Charlie")
```

There are also methods for setting and getting default values:

```scala
// Add default value to schema
val withDefault: Schema[Person] = Schema[Person]
  .defaultValue(Person("Unknown", 0))

// Get default value
val default: Option[Person] = Schema[Person].getDefaultValue

// Add default to a specific field
val fieldDefault: Schema[Person] = Schema[Person]
  .defaultValue(Person.age, 18)

// Get default for a field
val ageDefault: Option[Int] = 
  Schema[Person].getDefaultValue(Person.age)
```

## Accessing and Updating Partial Schemas

To access a specific part of a schema, we can use the `Schema#get` method, which takes an optic and returns the reflection of the targeted field. 

```scala

case class Person(name: String, address: Address)

object Person extends CompanionOptics[Person]{
  implicit val schema: Schema[Person] = Schema.derived

  val name: Lens[Person, String]      = optic(_.name)
  val address: Lens[Person, Address]  = optic(_.address)
}

case class Address(street: String, city: String)

object Address extends CompanionOptics[Address]{
  implicit val schema: Schema[Address] = Schema.derived

  val street: Lens[Address, String] = optic(_.street)
  val city  : Lens[Address, String] = optic(_.city)
}

// Get schema for a nested field
val addressSchema: Option[Reflect.Bound[Address]] =
  Schema[Person].get(Person.address)
```

This method enables us to retrieve the schema for any nested field using optics. For example, to get the schema for the `street` field inside the `address` of a `Person`, we can do as follows:

```scala
val streetSchema: Option[Reflect.Bound[String]] =
  Schema[Person].get(Person.address(Address.street))
```

### Updating Nested Schemas

To update a specific part of a schema, we can use the `Schema#updated` method, which takes an optic and a function to transform the targeted schema:

```scala
case class Schema[A](reflect: Reflect.Bound[A]) {
  def updated[B](optic: Optic[A, B])(
    f: Reflect.Bound[B] => Reflect.Bound[B]
  ): Option[Schema[A]]
}
```

Here is an example of updating the documentation of a nested field:

```scala
// Update schema at a specific path
val updated: Option[Schema[Person]] = Schema[Person]
  .updated(Person.address)(_.doc("Mailing address"))
```

## Schema Aspects

Schema aspects are a powerful mechanism in ZIO Blocks for transforming schemas. You can think of the schema aspect as a function that takes a reflect and produces a new reflect:

```scala
trait SchemaAspect[-Upper, +Lower, F[_, _]] {
  def apply[A >: Lower <: Upper](reflect: Reflect[F, A]): Reflect[F, A]
  def recursive(implicit ev1: Any <:< Upper, ev2: Lower <:< Nothing): SchemaAspect[Upper, Lower, F]
}
```

The `Schema` data type has a `@@` method used for applying schema aspects:

```scala
case class Schema[A](reflect: Reflect.Bound[A]) {
  def @@[Min >: A, Max <: A](aspect: SchemaAspect[Min, Max, Binding]): Schema[A] = ???
  def @@[B](part: Optic[A, B], aspect: SchemaAspect[B, B, Binding]) = ???
}
```

These methods enable us to use `@@` syntax for applying aspects to either the entire schema or a specific path within the schema using optics:

```scala

// Apply aspect to entire schema
val documented: Schema[Person] = Schema[Person] @@ SchemaAspect.doc("A person")

// Apply aspect to specific path
val fieldDoc: Schema[Person] = Schema[Person] @@ (
  Person.name, 
  SchemaAspect.examples("Alice", "Bob")
)
```

Currently, ZIO Blocks provides the following built-in schema aspects:

- `SchemaAspect.identity`: No-op transformation
- `SchemaAspect.doc`: Attach documentation to schema or field
- `SchemaAspect.examples`: Attach example values to schema or field

## Modifiers

Modifiers in ZIO Blocks provide a mechanism to attach metadata and configuration to schema elements without polluting the domain types themselves. They serve as the successor to ZIO Schema 1's annotation system, with the critical advantage of being **pure data** so, unlike Scala annotations, modifiers are runtime values that can be serialized.

The `Schema.modifier` and `Schema.modifiers` methods allow adding one or more modifiers to a schema:

```scala
final case class Schema[A](reflect: Reflect.Bound[A]) {
  def modifier(modifier: Modifier.Reflect)            : Schema[A] = ???
  def modifiers(modifiers: Iterable[Modifier.Reflect]): Schema[A] = ???
}
```

Here is an example of adding modifiers to a schema:

```scala
// Add modifier to schema
  val modified: Schema[Person] = Schema[Person]
    .modifier(Modifier.config("db.table-name", "person_table"))
    .modifier(Modifier.config("schema.version", "v2"))

// Add multiple modifiers
  val multiModified: Schema[Person] = Schema[Person]
    .modifiers(
      Seq(
        Modifier.config("db.table-name", "person_table"),
        Modifier.config("schema.version", "v2")
      )
    )
```

## Wrapper Types

ZIO Blocks provides the `transform` method for creating schemas for wrapper types, such as newtypes, opaque types and value classes:

```scala
final case class Schema[A](reflect: Reflect.Bound[A]) {
  def transform[B](to: A => B, from: B => A)(implicit typeId: TypeId[B]): Schema[B] = ???
}
```

The `transform` method allows you to define transformations that can fail by throwing `SchemaError` exceptions. Use it for both simple wrapper types and types with validation requirements. 

Here are examples of both:

```scala

// For types with validation (may fail)
case class Email(value: String)

object Email {
  private val EmailRegex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$".r
  
  implicit val schema: Schema[Email] = Schema[String]
    .transform(
      {
        case x @ EmailRegex(_*) => Email(x)
        case _                  => throw SchemaError.validationFailed("Invalid email format")
      },
      _.value
    )
}

// For total transformations (never fail)
case class UserId(value: Long)

object UserId {
  implicit val schema: Schema[UserId] = Schema[Long]
    .transform(UserId(_), _.value)
}
```

## Compile-Time Shape Constraints (`Allows`)

ZIO Blocks provides `Allows[A, S]` — a phantom-typed capability token that proves, at compile time, that type `A` satisfies the structural grammar `S`. This lets library authors express and enforce structural preconditions on their generic APIs without writing macros themselves.

```scala

// Require a flat record of scalars (e.g. for CSV or RDBMS)
def writeCsv[A: Schema](rows: Seq[A])(using
  Allows[A, Record[Primitive | Optional[Primitive]]]
): Unit = ???

// Sealed traits auto-unwrap: each case must satisfy Record[...] — no Variant node needed
def publish[A: Schema](event: A)(using
  Allows[A, Record[Primitive | Sequence[Primitive]]]
): Unit = ???

// Recursive grammar (e.g. for a JSON document store)
def toJson[A: Schema](doc: A)(using
  Allows[A, Record[Primitive | Self | Optional[Primitive | Self] | Sequence[Primitive | Self]]]
): String = ???
```

When a type does not satisfy the grammar, the user gets a precise compile-time error naming the violating field and suggesting a fix. No runtime surprises.

See the [`Allows` reference](./allows.md) for the full grammar node table, union syntax, `Self` for recursive types, newtypes, and error message examples.
