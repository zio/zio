# Structural Types

> <!--
BLOCKING ISSUE: Scala 3.7.4 Compiler Crash
============================================

<!--
BLOCKING ISSUE: Scala 3.7.4 Compiler Crash
============================================

This file uses structural type syntax ({ def field: Type }) which triggers a compiler
crash in Scala 3.7.4 during the erasure phase. Therefore, code blocks here are marked
as plain `scala` instead of `mdoc:compile-only` to allow the documentation build to pass.

WORKAROUND: Code examples are NOT compiled/type-checked until Scala 3.8.x is adopted.

TODO (When Scala 3.8.x is adopted):
1. Change all `\`\`\`scala` blocks back to `\`\`\`scala mdoc:compile-only`
2. Run `sbt docs/mdoc` to verify compilation succeeds
3. Delete this comment

Reference:
- Scala 3.7.4 compiler issue: https://github.com/scala/scala3/issues/24598 (or similar)
- Scala 3.8.x migration: PR #1169 "Preparing to migration on Scala 3.8.x"
- This PR: Addresses structural-types.md CI failures
-->

Structural types enable **duck typing** with ZIO Blocks schemas. Instead of requiring a nominal type name (like `class Person`), a structural schema validates based on the **shape** of an object — the fields it provides, regardless of how it was defined.

## Motivation

Consider a common integration scenario:

```scala
// Your system
case class Person(name: String, age: Int)

// External system (same data, different class)
case class User(name: String, age: Int)
```

Without structural types, converting between `Person` and `User` requires manual translation. With structural types, they both have the same structural schema:

```scala

case class Person(name: String, age: Int)
case class User(name: String, age: Int)

val personSchema = Schema.derived[Person]
val personStructural = personSchema.structural
// Schema[{ def name: String; def age: Int }]

val userSchema = Schema.derived[User]
val userStructural = userSchema.structural
// Schema[{ def name: String; def age: Int }]

// Both schemas accept the same data shape
```

## Construction: `Schema#structural`

Use the `Schema#structural` method on any schema to get the corresponding structural schema.

**Scala 3:** Using transparent inline — the return type is inferred to the full refinement type:

```scala

case class Person(name: String, age: Int)
object Person {
  implicit val schema: Schema[Person] = Schema.derived[Person]
}

val personSchema: Schema[Person] = Schema.derived[Person]
val structuralSchema: Schema[{ def name: String; def age: Int }] = personSchema.structural
```

**Scala 2:** Implicit derivation — returns `Schema[ts.StructuralType]` (path-dependent type):

```scala

case class Person(name: String, age: Int)
object Person {
  implicit val schema: Schema[Person] = Schema.derived[Person]
}

val personSchema: Schema[Person] = Schema.derived[Person]
val structuralSchema = personSchema.structural
// Type: Schema[ts.StructuralType] (structural type inferred from macro)
```

## Supported Conversions

The following type categories can be converted to structural schemas:

### Product Types (Case Classes)

Both Scala 2 and 3 support structural conversion of case classes:

```scala

case class Address(street: String, city: String, zipCode: Int)
object Address {
  implicit val schema: Schema[Address] = Schema.derived[Address]
}

val schema = Schema.derived[Address]
val structural = schema.structural
// Schema[{ def street: String; def city: String; def zipCode: Int }]
```

### Tuples

Tuples convert to structural records with field names derived from positions:

```scala

type StringIntBool = (String, Int, Boolean)
implicit val schema: Schema[StringIntBool] = Schema.derived[StringIntBool]

val tupleSchema = Schema.derived[(String, Int, Boolean)]
val structuralSchema = tupleSchema.structural
// Schema[{ def _1: String; def _2: Int; def _3: Boolean }]
```

### Nested Products

Nested product fields keep their nominal types; only the outer product is structuralized:

```scala

case class Address(street: String, city: String)
object Address {
  implicit val schema: Schema[Address] = Schema.derived[Address]
}

case class Person(name: String, age: Int, address: Address)
object Person {
  implicit val schema: Schema[Person] = Schema.derived[Person]
}

val personSchema = Schema.derived[Person]
val structuralSchema = personSchema.structural
// Schema[{
//   def name: String
//   def age: Int
//   def address: Address
// }]
```

### Opaque Types (Scala 3)

Opaque type aliases are unwrapped to their underlying type:

```scala

opaque type UserId = String

case class User(id: UserId, name: String)
object User {
  implicit val schema: Schema[User] = Schema.derived[User]
}

val schema = Schema.derived[User]
val structural = schema.structural
// Schema[{ def id: String; def name: String }]
// (UserId unwrapped to String)
```

### Sum Types / Sealed Traits (Scala 3)

Sealed traits and enums convert to union types with nested method syntax:

```scala

sealed trait Shape
object Shape {
  case class Circle(radius: Double) extends Shape
  case class Rectangle(width: Double, height: Double) extends Shape
  implicit val schema: Schema[Shape] = Schema.derived[Shape]
}

val schema = Schema.derived[Shape]
val structural = schema.structural
// Schema[
//   { def Circle: { def radius: Double } } |
//   { def Rectangle: { def height: Double; def width: Double } }
// ]
// (cases sorted alphabetically)
```

**Enum syntax** (Scala 3):

```scala

enum Color {
  case Red, Green, Blue
}
object Color {
  implicit val schema: Schema[Color] = Schema.derived[Color]
}

val schema = Schema.derived[Color]
val structural = schema.structural
// Schema[
//   { def Blue: {} } |
//   { def Green: {} } |
//   { def Red: {} }
// ]
```

Cases appear in **alphabetical order** in the union type. This alphabetical ordering (applied to fields in products and case names in unions) ensures **deterministic, normalized type identity**: two structural types with the same fields but different declaration order produce the same structural type and normalized name. This is essential for predictable schema evolution and cross-system interop.

## Direct Structural Derivation (Scala 3)

Create a schema directly for a structural type without a nominal base:

```scala

// No case class needed — define the schema for the shape directly
val personStructural = Schema.derived[{ def name: String; def age: Int }]

// The schema is ready to use with values matching that structural shape
```

This is only supported in **Scala 3** with the right macro machinery.

## Round-tripping Through DynamicValue

Structural schemas enable **cross-type conversion through `DynamicValue`** — encode a value of one nominal type and decode it as a *different* nominal type with the same structural shape. This is the core benefit of structural types for system integration.

### Motivation

In real integrations, you often receive data from an external system shaped like one type, but you need to work with it as a different type in your system. Without structural types, field-by-field translation is required. With structural types, if both types have identical shape, `DynamicValue` acts as the seamless bridge.

Common scenarios:
- **API gateways** — receive a `PersonDTO` from an external API, decode as your internal `Person` type
- **Message brokers** — consume an event shaped like `UserEvent`, convert to your domain `Account` type
- **Data pipelines** — records with identical fields but different class names from different services

### Cross-type conversion in action

Set up two types with identical structural shape:

```scala

case class Person(name: String, age: Int)
object Person {
  implicit val schema: Schema[Person] = Schema.derived[Person]
}

case class Employee(name: String, age: Int)
object Employee {
  implicit val schema: Schema[Employee] = Schema.derived[Employee]
}

val personSchema = Schema.derived[Person]
val employeeSchema = Schema.derived[Employee]
```

Now encode a `Person` to `DynamicValue` and decode it as an `Employee`:

```scala
val person = Person("Alice", 30)
// person: Person = Person(name = "Alice", age = 30)
val dynamic = personSchema.toDynamicValue(person)
// dynamic: DynamicValue = Record(
//   IndexedSeq(("name", Primitive(String("Alice"))), ("age", Primitive(Int(30))))
// )

val employee: Either[SchemaError, Employee] =
  employeeSchema.fromDynamicValue(dynamic)
// employee: Either[SchemaError, Employee] = Right(
//   Employee(name = "Alice", age = 30)
// )
```

The structural shape guarantee ensures type-safe conversion: at compile time, you know both schemas accept the same fields, so round-tripping through `DynamicValue` is safe and zero-cost.

## Integration

Structural types integrate seamlessly with ZIO Blocks' broader ecosystem:

### With Schema Evolution Macros

Structural schemas work with [Schema Evolution](./schema-evolution/into.md) macros for cross-type conversion. When two types share the same structural shape, the conversion machinery can work across type boundaries:

```scala

case class Person(name: String, age: Int)
object Person {
  implicit val schema: Schema[Person] = Schema.derived[Person]
}

case class PersonDTO(name: String, age: Int)
object PersonDTO {
  implicit val schema: Schema[PersonDTO] = Schema.derived[PersonDTO]
}

// Both types have identical structural schemas
val personSchema = Schema.derived[Person]
val dtoSchema = Schema.derived[PersonDTO]

// They share the same structural shape:
// Schema[{ def name: String; def age: Int }]
```

### With Binding.of (Serialization)

Structural types are also supported by the `Binding.of` macro for high-performance serialization via register-based encoding:

```scala

// Direct structural type serialization (JVM only)
val binding = Binding.of[{ def name: String; def age: Int }]

// Works with nested structural types
val nestedBinding = Binding.of[{
  def name: String
  def address: { def street: String; def city: String }
}]

// Works with containers
val containerBinding = Binding.of[{
  def name: String
  def emails: List[String]
}]
```

This enables anonymous structural types to benefit from ZIO Blocks' high-performance serialization without requiring nominal case class definitions. Like `Schema#structural`, this is **JVM-only**.

See [Binding](./binding.md) for detailed serialization documentation.

## Running the Examples

Example applications demonstrating structural types are available in `schema-examples`:

```sh
# Simple product type
sbt "schema-examples/runMain structural.StructuralSimpleProductExample"

# Nested products
sbt "schema-examples/runMain structural.StructuralNestedProductExample"

# Sealed trait (Scala 3)
sbt "schema-examples/runMain structural.StructuralSealedTraitExample"

# Enum (Scala 3)
sbt "schema-examples/runMain structural.StructuralEnumExample"

# Tuples
sbt "schema-examples/runMain structural.StructuralTupleExample"

# Integration with Into macro
sbt "schema-examples/runMain structural.StructuralIntoExample"
```
