# Migrating from ZIO Schema to ZIO Blocks Schema

> This guide helps you migrate an application that uses [ZIO Schema](https://github.com/zio/zio-schema) (version 1.x) to [ZIO Blocks Schema](https://github.com/zio/zio-blocks) (the schema module of ZIO Blocks). It covers the conceptual differences between the two libraries, provides a systematic mapping of data types, and shows how to rewrite the most common patterns in the idiomatic ZIO Blocks style.

This guide helps you migrate an application that uses [ZIO Schema](https://github.com/zio/zio-schema) (version 1.x) to [ZIO Blocks Schema](https://github.com/zio/zio-blocks) (the schema module of ZIO Blocks). It covers the conceptual differences between the two libraries, provides a systematic mapping of data types, and shows how to rewrite the most common patterns in the idiomatic ZIO Blocks style.

**What we will cover:**

- Prerequisites and dependency changes
- The core architectural shift from `Schema[A]` as a sealed trait to `Schema[A]` as a thin wrapper over `Reflect[F, A]`
- Migrating schema definitions for primitives, records, enums, collections, optional values, and newtypes
- Replacing the annotation/modifier system
- Adapting codec derivation to the unified `Format + Deriver` model
- Replacing `DynamicValue` usage
- Migrating optics and accessor patterns
- Migrating diff, patch, and schema evolution patterns
- Handling types and features that no longer have a direct analogue

---

## Prerequisites

### Dependency Changes

Replace the ZIO Schema dependency group with ZIO Blocks Schema:

**Before (ZIO Schema 1.x):**

```scala
libraryDependencies += "dev.zio" %% "zio-schema"            % "1.x.x"
libraryDependencies += "dev.zio" %% "zio-schema-derivation" % "1.x.x"
libraryDependencies += "dev.zio" %% "zio-schema-json"       % "1.x.x"
libraryDependencies += "dev.zio" %% "zio-schema-protobuf"   % "1.x.x"
libraryDependencies += "dev.zio" %% "zio-schema-avro"       % "1.x.x"
```

**After (ZIO Blocks Schema):**

```scala
libraryDependencies += "dev.zio" %% "zio-blocks-schema"            % "0.0.33"
// Optional codec modules:
libraryDependencies += "dev.zio" %% "zio-blocks-schema-avro"       % "0.0.33"
libraryDependencies += "dev.zio" %% "zio-blocks-schema-thrift"     % "0.0.33"
libraryDependencies += "dev.zio" %% "zio-blocks-schema-bson"       % "0.0.33"
libraryDependencies += "dev.zio" %% "zio-blocks-schema-messagepack" % "0.0.33"
libraryDependencies += "dev.zio" %% "zio-blocks-schema-toon"       % "0.0.33"
```

Key points:
- JSON codec support is now built into `zio-blocks-schema` — no separate JSON module.
- There is no separate `zio-blocks-schema-derivation` dependency; derivation is built in.
- The `scala-reflect` provided dependency (required in ZIO Schema for Scala 2) is still needed for Scala 2 macro derivation — add it the same way as before.
- ZIO Blocks Schema has **zero runtime dependency on ZIO itself**. You do not need `zio` on your classpath for schema operations.

### Package Rename

All imports change from `zio.schema` to `zio.blocks.schema`:

```scala
// Before

// After

```

---

## The Core Architecture Shift

The most important thing to understand when migrating is that `Schema[A]` is no longer a sealed trait hierarchy — it is a thin case class:

```scala
// ZIO Schema 1.x: Schema is a sealed trait with ~20 concrete cases
sealed trait Schema[A] {
  def annotations: Chunk[Any]
  def defaultValue: Either[String, A]
  // ...
}

// ZIO Blocks Schema: Schema is a case class wrapping Reflect
final case class Schema[A](reflect: Reflect.Bound[A])
```

The structural description lives in `Reflect[F[_, _], A]`, a sealed trait with eight node types. The `F` type parameter distinguishes a *bound* reflect (with runtime constructors and deconstructors) from an *unbound* one (structural information only):

```
Reflect[F, A]
├── Reflect.Record[F, A]         — case classes and other product types
├── Reflect.Variant[F, A]        — sealed traits, enums, Option, Either
├── Reflect.Sequence[F, A, C[_]] — List, Vector, Set, Chunk, etc.
├── Reflect.Map[F, K, V, M[_,_]] — Map[K, V]
├── Reflect.Primitive[F, A]      — Int, String, UUID, java.time.*, etc.
├── Reflect.Wrapper[F, A, B]     — opaque types and validated newtypes
├── Reflect.Dynamic[F]           — escape hatch for schema-agnostic data
└── Reflect.Deferred[F, A]       — recursive (self-referential) types
```

As a result, you will rarely pattern match on `Schema[A]` directly — instead, you work through `schema.reflect` when you need to inspect structure.

---

## Migrating Schema Definitions

### Schema Derivation

Automatic derivation syntax is essentially unchanged:

```scala
// ZIO Schema 1.x — Scala 2: type inferred from ascription; Scala 3: type param required

final case class Person(name: String, age: Int)
object Person {
  implicit val schema: Schema[Person] = DeriveSchema.gen[Person]
}

// ZIO Blocks Schema — identical call in Scala 2 and Scala 3

final case class Person(name: String, age: Int)
object Person {
  implicit val schema: Schema[Person] = Schema.derived[Person]
}
```

`Schema.derived[A]` works identically in both Scala 2 and Scala 3 in ZIO Blocks Schema. There is no separate `DeriveSchema` import and no arity limit.

### Primitives

All 30 primitive types from ZIO Schema are present in ZIO Blocks Schema with the same coverage: `Unit`, `Boolean`, `Byte`, `Short`, `Int`, `Long`, `Float`, `Double`, `Char`, `String`, `BigInt`, `BigDecimal`, all `java.time.*` types, `Currency`, and `UUID`.

Implicit schemas are available in the same way:

```scala
// ZIO Schema 1.x
val s: Schema[Int]              = Schema[Int]
val s: Schema[java.time.Instant] = Schema[java.time.Instant]

// ZIO Blocks Schema — identical call sites
val s: Schema[Int]               = Schema[Int]
val s: Schema[java.time.Instant]  = Schema[java.time.Instant]
```

The underlying representation changes: ZIO Schema uses `Schema.Primitive[A](standardType: StandardType[A])`, while ZIO Blocks Schema uses `Reflect.Primitive[F, A](primitiveType: PrimitiveType[A], ...)`. Both carry default values and ordering, but in ZIO Blocks the primitive type also carries an embedded `Validation[A]` constraint (see the [Migrating Validation](#migrating-validation) section below).

### Records (Case Classes)

**Before (ZIO Schema 1.x):**

```scala

final case class Address(street: String, city: String, postCode: String)
final case class Person(name: String, age: Int, address: Address)

object Person {
  implicit val schema: Schema[Person] = DeriveSchema.gen
}
```

**After (ZIO Blocks Schema):**

```scala

final case class Address(street: String, city: String, postCode: String)
final case class Person(name: String, age: Int, address: Address)

object Person {
  implicit val schema: Schema[Person] = Schema.derived[Person]
}
```

The derivation call is identical. Internally, ZIO Blocks generates a `Reflect.Record` node (a single generic type, not the arity-specialised `CaseClass1`..`CaseClass22` of ZIO Schema), so there is no 22-field arity limit.

If you were writing schemas manually using `Schema.CaseClass2[...]` or similar, you will need to rewrite those. The equivalent in ZIO Blocks is to write the `Reflect.Record` directly or, preferably, just use `Schema.derived[A]`:

```scala
// ZIO Schema 1.x — manual construction for a 2-field record
val personSchema: Schema[Person] =
  Schema.CaseClass2[String, Int, Person](
    id0 = TypeId.fromTypeName("Person"),
    field01 = Schema.Field("name", Schema[String], get0 = _.name, set0 = (p, v) => p.copy(name = v)),
    field02 = Schema.Field("age",  Schema[Int],    get0 = _.age,  set0 = (p, v) => p.copy(age = v)),
    construct0 = Person(_, _)
  )

// ZIO Blocks Schema — prefer derivation; no manual CaseClass* required
val personSchema: Schema[Person] = Schema.derived[Person]
```

Manual construction is still possible in ZIO Blocks Schema (by assembling a `Reflect.Record` directly), but it is substantially more involved because you must supply a `Binding.Record` with explicit `Constructor[A]` and `Deconstructor[A]` implementations that use the unboxed register system. Automatic derivation is strongly preferred.

### Sealed Traits / Enums (Sum Types)

**Before (ZIO Schema 1.x):**

```scala

sealed trait Shape
case class Circle(radius: Double)                   extends Shape
case class Rectangle(width: Double, height: Double) extends Shape

object Shape {
  implicit val schema: Schema[Shape] = DeriveSchema.gen
}
```

**After (ZIO Blocks Schema):**

```scala

sealed trait Shape
case class Circle(radius: Double)                   extends Shape
case class Rectangle(width: Double, height: Double) extends Shape

object Shape {
  implicit val schema: Schema[Shape] = Schema.derived[Shape]
}
```

Again, the call is identical. Internally, ZIO Blocks generates a `Reflect.Variant` node. There is no 22-case arity limit.

### Optional Values

ZIO Schema has a first-class `Schema.Optional[A]` node. In ZIO Blocks, `Option[A]` is modeled as a `Reflect.Variant` with two cases (`None` and `Some`). From a user perspective this is transparent — implicit schemas for `Option[A]` exist in the same form:

```scala
// ZIO Schema 1.x
val optSchema: Schema[Option[String]] = Schema[Option[String]]

// ZIO Blocks Schema — identical
val optSchema: Schema[Option[String]] = Schema[Option[String]]
```

For value-type `Option` variants (e.g., `Option[Int]`), ZIO Blocks provides specialised implicit instances (`Schema.optionInt`, `Schema.optionLong`, etc.) that avoid boxing. These are resolved automatically by the compiler — no code change required.

:::tip
The internal modeling difference (variant vs. dedicated node) is only relevant if you are pattern-matching on the raw `Schema` or `Reflect` structure. In that case, replace any match on `Schema.Optional(inner, _)` with a check on `reflect.isOption` and use `reflect.optionInnerType` to retrieve the inner reflect:

```scala
// ZIO Schema 1.x — pattern matching on Optional
schema match {
  case Schema.Optional(inner, _) => // use inner
  case _                         => // ...
}

// ZIO Blocks Schema — use the isOption predicate
// optionInnerType returns Option[Reflect[F, ?]] where F matches the enclosing Reflect's binding
val r = schema.reflect
if (r.isOption) {
  val inner: Option[Reflect[binding.Binding, ?]] = r.optionInnerType
  // use inner
}
```
:::

### Either

In ZIO Schema, `Either[A, B]` is a first-class `Schema.Either[A, B]` node. In ZIO Blocks, it is modeled as a two-case `Reflect.Variant`. The implicit schema is provided automatically:

```scala
// ZIO Schema 1.x
val eitherSchema: Schema[Either[String, Int]] = Schema.either[String, Int]

// ZIO Blocks Schema
val eitherSchema: Schema[Either[String, Int]] = Schema[Either[String, Int]]
```

:::warning
ZIO Blocks Schema does not have a `Fallback[A, B]` type. If you were using `Schema.Fallback` for partial decoding, you will need to model that with a custom `Reflect.Variant` or handle it in your codec logic directly.
:::

### Collections

All standard collection types are supported with the same implicit schema pattern:

```scala
// ZIO Schema 1.x
Schema[List[String]]
Schema[Vector[Int]]
Schema[Chunk[Double]]
Schema[Set[String]]
Schema[Map[String, Int]]

// ZIO Blocks Schema — identical call sites
Schema[List[String]]
Schema[Vector[Int]]
Schema[Chunk[Double]]   // uses zio.blocks.chunk.Chunk
Schema[Set[String]]
Schema[Map[String, Int]]
```

Note that `Chunk` is now `zio.blocks.chunk.Chunk` (not `zio.Chunk`). This is a zero-dependency replacement with the same API surface for typical usage.

ZIO Schema's `NonEmptyChunk` and `NonEmptyMap` schemas do not have direct equivalents in ZIO Blocks Schema. The recommended approach is to model them as wrapper types:

```scala
// ZIO Schema 1.x — NonEmptyChunk implicit schema
val schema: Schema[NonEmptyChunk[String]] = Schema[NonEmptyChunk[String]]

// ZIO Blocks Schema — model as a validated wrapper

final case class NonEmptyList[A] private (values: List[A])
object NonEmptyList {
  def apply[A](head: A, tail: A*): NonEmptyList[A] = new NonEmptyList(head :: tail.toList)

  implicit def schema[A](implicit element: Schema[A]): Schema[NonEmptyList[A]] =
    Schema[List[A]].transform(
      to   = list =>
        if (list.nonEmpty) new NonEmptyList(list)
        else throw SchemaError.validationFailed("List must not be empty"),
      from = _.values
    )
}
```

### Newtypes and Opaque Types

ZIO Schema uses `Schema.transform` (which wraps a `Transform` node) for both validated newtypes and lossless wrappers:

```scala
// ZIO Schema 1.x
implicit val bigDecimalSchema: Schema[BigDecimal] =
  Schema.primitive[java.math.BigDecimal].transform(BigDecimal(_), _.bigDecimal)
```

ZIO Blocks Schema has `Schema[A].transform(to: A => B, from: B => A)` which produces a `Reflect.Wrapper` node. The `to` and `from` functions are total but can throw to indicate failure. The method also requires an implicit `TypeId[B]`, which is derived automatically by the macro system for any concrete named type — you will not need to supply it manually for ordinary case classes:

```scala
// ZIO Blocks Schema
case class Email(value: String)
object Email {
  // TypeId[Email] is resolved implicitly from the macro-derived instance
  implicit val schema: Schema[Email] =
    Schema[String].transform(
      to   = str =>
        if (str.contains('@')) Email(str)
        else throw SchemaError.validationFailed("Not a valid email address"),
      from = _.value
    )
}
```

For simple lossless wrappers where no validation is needed, the pattern is the same but without the error throw:

```scala
// ZIO Blocks Schema — simple newtype wrapper
case class UserId(value: Long)
object UserId {
  implicit val schema: Schema[UserId] =
    Schema[Long].transform(UserId(_), _.value)
}
```

:::warning
In ZIO Schema, `transformOrFail` accepted `A => Either[String, B]` return types. In ZIO Blocks, `transform` uses total functions that throw on failure — use `throw SchemaError.validationFailed(message)` in the `to` function to signal failure. There is no `transformOrFail` method.

If you encounter a "could not find implicit value for parameter typeId: TypeId[B]" error, ensure the target type `B` is a concrete, named class or object (not an anonymous structural type or a type alias to a primitive). For primitive-backed aliases such as `type Meters = Double`, wrap in a `case class` instead.
:::

### Lazy / Recursive Schemas

**Before (ZIO Schema 1.x):**

```scala

case class Tree(value: Int, children: List[Tree])
object Tree {
  implicit lazy val schema: Schema[Tree] = DeriveSchema.gen
  // Or manually with Schema.defer:
  // implicit lazy val schema: Schema[Tree] = Schema.CaseClass2(
  //   ..., field02 = Schema.Field("children", Schema.defer(Schema.list(schema)), ...)
  // )
}
```

**After (ZIO Blocks Schema):**

```scala

case class Tree(value: Int, children: List[Tree])
object Tree {
  implicit val schema: Schema[Tree] = Schema.derived[Tree]
}
```

Recursive types are handled automatically by the macro. Internally, ZIO Blocks generates a `Reflect.Deferred` node that uses thread-local cycle detection — you do not need to use `Schema.defer` manually. The `implicit val` (not `lazy val`) is sufficient.

If you were wrapping a recursive reference manually with `Schema.defer(...)`, simply remove that wrapper — recursive references inside `Schema.derived` are handled for you.

---

## Migrating Annotations and Modifiers

ZIO Schema uses an open `Chunk[Any]` annotation system. ZIO Blocks Schema replaces this with a strongly-typed, sealed `Modifier` hierarchy.

### Transient Fields

```scala
// ZIO Schema 1.x

final case class User(name: String, @transientField password: String)
object User {
  implicit val schema: Schema[User] = DeriveSchema.gen
}

// ZIO Blocks Schema

// Transient fields must have a default value in ZIO Blocks Schema.
// Because transient fields are excluded from serialization, the decoder
// needs a default to reconstruct the object without that field in the input.
final case class User(name: String, @Modifier.transient() password: String = "")
object User {
  implicit val schema: Schema[User] = Schema.derived[User]
}
```

### Field Renaming

```scala
// ZIO Schema 1.x — @fieldName annotation

final case class Product(@fieldName("product_name") name: String, price: Double)

// ZIO Blocks Schema — @Modifier.rename annotation

final case class Product(@Modifier.rename("product_name") name: String, price: Double)
```

### Field Aliases (for Decoding)

```scala
// ZIO Schema 1.x — @fieldNameAliases annotation

final case class Config(@fieldNameAliases("max-size", "max_size") maxSize: Int)

// ZIO Blocks Schema — @Modifier.alias annotation (one alias per annotation)

final case class Config(
  @Modifier.alias("max-size")
  @Modifier.alias("max_size")
  maxSize: Int
)
```

### Codec-Specific Configuration

```scala
// ZIO Schema 1.x — no standard mechanism; each codec module defines its own
// e.g., @fieldDefaultValue, @optionalField, or codec-specific annotations

// ZIO Blocks Schema — use @Modifier.config with convention "format.property"

final case class Message(
  @Modifier.config("protobuf.field-id", "1") id: Long,
  @Modifier.config("protobuf.field-id", "2") content: String
)
```

### Discriminator and Case Name Annotations

```scala
// ZIO Schema 1.x

@discriminatorName("type")
sealed trait Event
@caseName("user_created")
case class UserCreated(userId: String) extends Event

// ZIO Blocks Schema — use Modifier.rename on the case, Modifier.config for discriminator

sealed trait Event
@Modifier.rename("user_created")
case class UserCreated(userId: String) extends Event
```

For discriminator key configuration on the enclosing sealed trait, use `Modifier.config` on the reflect node after derivation:

```scala
implicit val schema: Schema[Event] =
  Schema.derived[Event].modifier(Modifier.config("json.discriminator", "type"))
```

### Programmatic Annotation

ZIO Schema allows adding annotations at any time via `schema.annotate(annotation)`. In ZIO Blocks, you add modifiers:

```scala
// ZIO Schema 1.x
val schema2 = schema.annotate(someAnnotation)

// ZIO Blocks Schema
val schema2 = schema.modifier(Modifier.config("key", "value"))
```

---

## Migrating Codec Derivation

### The Unified Format Model

ZIO Schema has separate codec APIs in each codec sub-module (e.g., `JsonCodec.jsonCodec`, `ProtobufCodec.protobufCodec`). ZIO Blocks Schema introduces a unified `codec.Format` interface that all codec modules implement. Codecs are derived via a consistent call:

```scala
// ZIO Schema 1.x — each codec module has its own factory
// JsonCodec.jsonCodec returns a zio.json.JsonCodec (a text codec from the zio-json library)

val jsonCodec = JsonCodec.jsonCodec(Person.schema)

// ProtobufCodec.protobufCodec returns a BinaryCodec[A] (Chunk[Byte] in / out)

val protoCodec: BinaryCodec[Person] = ProtobufCodec.protobufCodec(Person.schema)

// ZIO Blocks Schema — all codecs via schema.derive(Format); return type inferred

val jsonCodec = Person.schema.derive(JsonFormat)   // inferred: JsonCodec[Person]

val avroCodec = Person.schema.derive(AvroFormat)
```

Derived codecs are cached per `(Schema, Format)` pair — subsequent calls to `schema.derive(JsonFormat)` return the same instance.

### Encoding and Decoding

The codec interface differs significantly between the two libraries:

```scala
// ZIO Schema 1.x — Protobuf (true BinaryCodec: Chunk[Byte] in/out)

val codec: BinaryCodec[Person] = ProtobufCodec.protobufCodec(Person.schema)
val encoded: Chunk[Byte]              = codec.encode(Person("Alice", 30))
val decoded: Either[DecodeError, Person] = codec.decode(encoded)

// ZIO Schema 1.x — JSON (zio-json JsonCodec: String in/out)

val jsonCodec = JsonCodec.jsonCodec(Person.schema)
val json: String                       = jsonCodec.encodeJson(Person("Alice", 30), None).toString
val fromJson: Either[String, Person]   = jsonCodec.decodeJson(json)
```

```scala
// ZIO Blocks Schema — all formats use ByteBuffer (binary) or CharBuffer (text)

val person = Person("Alice", 30)

// Encode
val buffer = ByteBuffer.allocate(1024)
Person.schema.encode(JsonFormat)(buffer)(person)

// Decode
buffer.flip()
val result: Either[SchemaError, Person] = Person.schema.decode(JsonFormat)(buffer)
```

ZIO Blocks codecs use `java.nio.ByteBuffer` for binary formats and `java.nio.CharBuffer` for text formats, and do not depend on `zio-json` or any other external codec library.

### JSON Codec

JSON support is built into the core `zio-blocks-schema` module — no separate dependency is needed:

```scala
// ZIO Schema 1.x — requires a separate zio-schema-json module
libraryDependencies += "dev.zio" %% "zio-schema-json" % "1.x.x"

val codec = JsonCodec.jsonCodec(Person.schema)  // returns zio.json.JsonCodec

// ZIO Blocks Schema — built into zio-blocks-schema; no extra dependency

val codec = Person.schema.derive(JsonFormat)  // returns JsonCodec[Person]
```

### Streaming Codecs

ZIO Schema's streaming codec methods (`streamEncoder`, `streamDecoder`) integrated with `ZStream`. ZIO Blocks Schema codecs are format-level `encode`/`decode` operations over `ByteBuffer` or `CharBuffer` — they do not depend on ZIO's streaming primitives. If you need streaming, wrap the codec in your effect system's streaming abstraction.

---

## Migrating DynamicValue

### Structure Changes

The `DynamicValue` ADT is significantly simplified in ZIO Blocks — from 15 cases down to 6. The key differences:

- ZIO Schema's `DynamicValue.Primitive[A](value: A, standardType: StandardType[A])` stores the raw value and its `StandardType` inline. ZIO Blocks wraps the scalar in a `PrimitiveValue` case class instead.
- `DynamicValue.Record` drops the `TypeId` parameter and uses `Chunk[(String, DynamicValue)]` instead of `ListMap[String, DynamicValue]`.
- `Option`, `Either`, and `Tuple` are no longer dedicated ADT cases — they are represented structurally using `Variant` and `Record`.

| ZIO Schema | ZIO Blocks Schema |
|---|---|
| `Primitive[A](value: A, standardType: StandardType[A])` | `Primitive(value: PrimitiveValue)` |
| `Record(id: TypeId, values: ListMap[String, DynamicValue])` | `Record(fields: Chunk[(String, DynamicValue)])` — no TypeId |
| `Enumeration(id: TypeId, value: (String, DynamicValue))` | `Variant(caseName: String, value: DynamicValue)` |
| `Sequence(values: Chunk[DynamicValue])` | `Sequence(elements: Chunk[DynamicValue])` |
| `Dictionary(entries: Chunk[(DynamicValue, DynamicValue)])` | `Map(entries: Chunk[(DynamicValue, DynamicValue)])` |
| `SomeValue(value: DynamicValue)` | `Variant("Some", Record(Chunk("value" -> ...)))` |
| `NoneValue` | `Variant("None", Null)` |
| `LeftValue(value: DynamicValue)` | `Variant("Left", Record(Chunk("value" -> ...)))` |
| `RightValue(value: DynamicValue)` | `Variant("Right", Record(Chunk("value" -> ...)))` |
| `Tuple(left, right)` | `Record(Chunk("_1" -> left, "_2" -> right))` |
| `SetValue(values: Set[DynamicValue])` | `Sequence(elements: Chunk[DynamicValue])` |
| `BothValue(left, right)` | No direct equivalent (used by `Fallback`, which is removed) |
| `DynamicAst(ast: MetaSchema)` | No direct equivalent |
| `Singleton[A](instance: A)` | No direct equivalent |
| `Error(message: String)` | No direct equivalent — use `SchemaError` |

### Primitive Values

In ZIO Schema, primitive values are stored inline in `DynamicValue.Primitive[A](value: A, standardType: StandardType[A])`. There is no separate `PrimitiveValue` type. In ZIO Blocks, a sealed `PrimitiveValue` ADT wraps each primitive:

```scala
// ZIO Schema 1.x — value and StandardType are separate constructor arguments

val pv: DynamicValue = DynamicValue.Primitive(42, StandardType[Int])
val ps: DynamicValue = DynamicValue.Primitive("hello", StandardType[String])

// ZIO Blocks Schema — value is wrapped in a PrimitiveValue case class

val pv: DynamicValue = DynamicValue.Primitive(PrimitiveValue.Int(42))
val ps: DynamicValue = DynamicValue.Primitive(PrimitiveValue.String("hello"))
```

The `PrimitiveValue` case names (`Int`, `Long`, `String`, `Boolean`, `Double`, etc.) match the Scala primitive names.

### Converting Between Typed Values and DynamicValue

```scala
// ZIO Schema 1.x
// toDynamic is a method on Schema[A], not on the value itself
val dv: DynamicValue         = Person.schema.toDynamic(person)
// toTypedValue requires an implicit Schema[Person] in scope
val back: Either[String, Person] = dv.toTypedValue[Person]

// ZIO Blocks Schema
val dv: DynamicValue                  = Person.schema.toDynamicValue(person)
val back: Either[SchemaError, Person] = Person.schema.fromDynamicValue(dv)
```

Two things change: `toDynamic` is renamed `toDynamicValue` (still on `Schema[A]`), and `toTypedValue` is replaced by `schema.fromDynamicValue`. The error type changes from `String` to `SchemaError`.

### DynamicValue Operations

ZIO Blocks `DynamicValue` has a rich operation API that was absent in ZIO Schema. Where ZIO Schema required you to convert back to a typed value to manipulate data, you can now operate directly on `DynamicValue`:

```scala

val record = DynamicValue.Record(
  Chunk(
    "name" -> DynamicValue.Primitive(PrimitiveValue.String("Alice")),
    "age"  -> DynamicValue.Primitive(PrimitiveValue.Int(30))
  )
)

// Navigate — get(fieldName) returns DynamicValueSelection (supports chaining)
// Call .one to extract a single value as Either[SchemaError, DynamicValue]
val name: Either[SchemaError, DynamicValue] = record.get("name").one

// Modify — set returns DynamicValue directly (silent no-op if path not found)
// Use setOrFail to get an Either on missing paths
val updated: DynamicValue = record.set(
  DynamicOptic.root.field("name"),
  DynamicValue.Primitive(PrimitiveValue.String("Bob"))
)

// Diff
val other = DynamicValue.Record(Chunk(
  "name" -> DynamicValue.Primitive(PrimitiveValue.String("Bob")),
  "age"  -> DynamicValue.Primitive(PrimitiveValue.Int(31))
))
val patch = record.diff(other)
```

---

## Migrating Schema Introspection

### MetaSchema → DynamicSchema

ZIO Schema has `MetaSchema` (a type-erased structural description of a schema) and `schema.ast` to convert to it. ZIO Blocks uses `DynamicSchema` for the same purpose:

```scala
// ZIO Schema 1.x
val meta: MetaSchema = schema.ast
val back: Schema[_]  = meta.toSchema

// ZIO Blocks Schema
val dynamic: DynamicSchema = schema.toDynamicSchema
```

`DynamicSchema` wraps a `Reflect[NoBinding, _]` — the full structural description without runtime constructors or deconstructors. Use it for:

- Runtime structural validation of `DynamicValue` instances
- Dynamic schema loading from configuration or network
- Schema inspection without compile-time type information

```scala
// ZIO Blocks Schema — validate a DynamicValue against a schema
val personSchema: Schema[Person]   = Schema.derived[Person]
val dynSchema: DynamicSchema       = personSchema.toDynamicSchema

val value = DynamicValue.Record(Chunk(
  "name" -> DynamicValue.Primitive(PrimitiveValue.String("Alice")),
  "age"  -> DynamicValue.Primitive(PrimitiveValue.Int(30))
))

dynSchema.conforms(value)         // true
dynSchema.check(value)            // None (no error)
```

:::warning
ZIO Schema's `Migration` system for schema-to-schema migration (i.e., automatically migrating values from one version of a type to another) is **not yet available** in ZIO Blocks Schema. The `schema.migrate[B](newSchema)` and `schema.coerce[B](newSchema)` methods do not exist. If your application relies on schema migration, you have two options:

1. Implement migration logic manually using `DynamicValue` transformations and `DynamicSchema` for validation.
2. Wait for schema migration support to be added to ZIO Blocks Schema (it is on the roadmap).
:::

### Schema Serialization

ZIO Schema supports serializing a schema itself (via `schema.serializable`). ZIO Blocks Schema does not have a direct equivalent at this time. All schema metadata types (`DynamicOptic`, `DynamicPatch`, `Modifier`, `Validation`, etc.) have `Schema` instances and are individually serializable, but there is no single `Schema[Schema[A]]` that round-trips the full structural description.

---

## Migrating Optics

ZIO Schema uses an `AccessorBuilder` pattern that delegates optic creation to an external `zio-schema-optics` module. ZIO Blocks Schema includes a complete, first-class optics system in the core module.

### Generating Optics

**Before (ZIO Schema 1.x with `zio-schema-optics`):**

```scala

case class Person(name: String, age: Int)
object Person {
  implicit val schema: Schema[Person] = DeriveSchema.gen
  val (name, age) = schema.makeAccessors(ZioOpticsBuilder)
}
```

**After (ZIO Blocks Schema):**

Optics are generated by the macro derivation and placed directly in the companion object as `Lens` instances via a `CompanionOptics` mechanism. In Scala 3, they are generated automatically. In Scala 2, use `Schema.derived[Person]` and access fields by calling `schema.reflect.asRecord.get.lensByName[String]("name")`, or use the macro-derived companion optics pattern:

```scala
// Scala 3 — optics generated in companion via macro

case class Person(name: String, age: Int)
object Person extends CompanionOptics[Person] {
  implicit val schema: Schema[Person] = Schema.derived[Person]
  // Scala 3 macro generates: val name: Lens[Person, String] = ...
  //                          val age:  Lens[Person, Int]    = ...
}

// Usage
val lens: Lens[Person, String] = Person.name
val person = Person("Alice", 30)
lens.modify(person, _.toUpperCase)  // Person("ALICE", 30)
```

```scala
// Scala 2 — obtain lenses from the schema

case class Person(name: String, age: Int)
object Person {
  implicit val schema: Schema[Person] = Schema.derived[Person]

  val name: Lens[Person, String] =
    schema.reflect.asRecord.get.lensByName[String]("name").get
  val age: Lens[Person, Int] =
    schema.reflect.asRecord.get.lensByName[Int]("age").get
}
```

### Using Optics

The four optic types in ZIO Blocks are `Lens`, `Prism`, `Optional`, and `Traversal`. Their usage API is similar to standard optics libraries:

```scala

case class Person(name: String, age: Int)
object Person {
  implicit val schema: Schema[Person] = Schema.derived[Person]
}

// Obtain lens (Scala 2 example)
val nameLens: Lens[Person, String] =
  Person.schema.reflect.asRecord.get.lensByName[String]("name").get

val person = Person("Alice", 30)

// Get
val name: String = nameLens.get(person)             // "Alice"

// Modify
val upper: Person = nameLens.modify(person, _.toUpperCase)   // Person("ALICE", 30)

// Replace — note: ZIO Blocks uses replace, not set (unlike Monocle and many other optics libraries)
val renamed: Person = nameLens.replace(person, "Bob")        // Person("Bob", 30)
```

For sealed traits, use `Prism`:

```scala

sealed trait Shape
case class Circle(radius: Double)    extends Shape
case class Rectangle(w: Double, h: Double) extends Shape

object Shape {
  implicit val schema: Schema[Shape] = Schema.derived[Shape]

  val circlePrism: Prism[Shape, Circle] =
    schema.reflect.asVariant.get.prismByName[Circle]("Circle").get
}

val shape: Shape = Circle(5.0)
Shape.circlePrism.getOption(shape)          // Some(Circle(5.0))
Shape.circlePrism.reverseGet(Circle(3.0))   // Circle(3.0): Shape
```

### Schema Expressions (New in ZIO Blocks)

ZIO Blocks introduces `SchemaExpr[S, A]`, a typed expression language built on top of optics. There is no equivalent in ZIO Schema. These allow you to build inspectable, composable predicates and computations:

```scala

case class Product(name: String, price: Double, inStock: Boolean)
object Product {
  implicit val schema: Schema[Product] = Schema.derived[Product]
  val priceLens: Lens[Product, Double] =
    schema.reflect.asRecord.get.lensByName[Double]("price").get
  val inStockLens: Lens[Product, Boolean] =
    schema.reflect.asRecord.get.lensByName[Boolean]("inStock").get
}

// Build a typed predicate expression
val cheapAndInStock: SchemaExpr[Product, Boolean] =
  (Product.priceLens < 100.0) && (Product.inStockLens === true)

// Evaluate against data — eval returns Either[OpticCheck, Seq[A]]
// Right(Seq(true))  on success
// Left(OpticCheck)  if a prism in the path did not match
val p = Product("Widget", 49.99, inStock = true)
cheapAndInStock.eval(p)  // Right(Seq(true))
```

---

## Migrating Diff and Patch

### Diff

ZIO Schema uses `Differ.fromSchema(schema).diff(a, b)` or the convenience method `schema.diff(a, b)`. ZIO Blocks Schema uses the same convenience method:

```scala
// ZIO Schema 1.x
val patch: Patch[Person] = Person.schema.diff(person1, person2)

// ZIO Blocks Schema
val patch: Patch[Person] = Person.schema.diff(person1, person2)
```

The call site is identical, but the underlying `Patch` types are different.

### Patch Application

```scala
// ZIO Schema 1.x
val result: Either[String, Person] = Person.schema.patch(person, patch)

// ZIO Blocks Schema
val result: Either[SchemaError, Person] = Person.schema.patch(person, patch)
// or equivalently:
val result: Either[SchemaError, Person] = patch.apply(person, PatchMode.Strict)
```

The error type changes from `String` to `SchemaError`.

### Creating Patches Programmatically

ZIO Schema has no structured API for creating patches programmatically. ZIO Blocks Schema provides one through `Patch` smart constructors:

```scala

case class Person(name: String, age: Int)
object Person {
  implicit val schema: Schema[Person] = Schema.derived[Person]
  val nameLens: Lens[Person, String] =
    schema.reflect.asRecord.get.lensByName[String]("name").get
  val ageLens: Lens[Person, Int] =
    schema.reflect.asRecord.get.lensByName[Int]("age").get
}

// Set a field
val renamePatch: Patch[Person] = Patch.set(Person.nameLens, "Bob")

// Compose patches
val combined: Patch[Person] = renamePatch ++ Patch.set(Person.ageLens, 31)

// Apply
val updated: Either[SchemaError, Person] = combined(Person("Alice", 30), PatchMode.Strict)
```

---

## Migrating Type Class Derivation

### Before (ZIO Schema 1.x)

ZIO Schema does not have a general `Deriver[TC]` interface. Each codec module implements its own derivation logic independently. There is no way to derive an arbitrary user-defined type class from a `Schema[A]`.

### After (ZIO Blocks Schema)

ZIO Blocks Schema introduces `Deriver[TC]`, a unified interface for deriving any type class `TC[_]` from a schema. This replaces ad-hoc codec-specific derivation:

```scala

// Define a type class
trait Show[A] {
  def show(a: A): String
}

// Implement Deriver[Show]
object DeriveShow extends Deriver[Show] {

  def derivePrimitive[A](
    primitiveType: PrimitiveType[A],
    typeId: TypeId[A],
    binding: Binding[BindingType.Primitive, A],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[A],
    examples: Seq[A]
  ): Lazy[Show[A]] = Lazy {
    new Show[A] {
      def show(a: A): String = a.toString
    }
  }

  def deriveRecord[F[_, _], A](
    fields: IndexedSeq[Term[F, A, _]],
    typeId: TypeId[A],
    binding: Binding[BindingType.Record, A],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[A],
    examples: Seq[A]
  )(implicit F: HasBinding[F], D: DeriveShow.HasInstance[F]): Lazy[Show[A]] = {
    val recordBinding = binding.asInstanceOf[Binding.Record[A]]
    val recordFields  = fields.asInstanceOf[IndexedSeq[Term[Binding, A, _]]]
    val recordReflect = new Reflect.Record[Binding, A](recordFields, typeId, recordBinding, doc, modifiers)
    Lazy {
      new Show[A] {
        private lazy val resolvedShows: IndexedSeq[Show[Any]] =
          fields.map(f => D.instance(f.value.metadata).asInstanceOf[Lazy[Show[Any]]].force)
        def show(a: A): String = {
          val regs = Registers(recordReflect.usedRegisters)
          recordBinding.deconstructor.deconstruct(regs, RegisterOffset.Zero, a)
          val fieldStrs = fields.indices.map { i =>
            val v = recordReflect.registers(i).get(regs, RegisterOffset.Zero)
            s"${fields(i).name} = ${resolvedShows(i).show(v)}"
          }
          s"${typeId.name}(${fieldStrs.mkString(", ")})"
        }
      }
    }
  }

  // ... deriveVariant, deriveSequence, deriveMap, deriveDynamic, deriveWrapper
  // (see the DeriveShowExample in the examples module for full implementation)
}

// Derive Show for any type
case class Person(name: String, age: Int)
object Person {
  implicit val schema: Schema[Person] = Schema.derived[Person]
  implicit val show: Show[Person]     = schema.derive(DeriveShow)
}

Person.show.show(Person("Alice", 30))  // Person(name = "Alice", age = 30)
```

---

## Migrating Validation

### Before (ZIO Schema 1.x)

ZIO Schema uses a composable `Validation[A]` ADT as an annotation, attached via `@validate(...)` or `.validation(...)`:

```scala

case class User(
  @validate(Validation.greaterThan(0)) age: Int,
  @validate(Validation.minLength(3))   name: String
)
object User {
  implicit val schema: Schema[User] = DeriveSchema.gen
}

schema.validate(User(-1, "Al"))
// Returns Chunk[ValidationError] with violations
```

### After (ZIO Blocks Schema)

ZIO Blocks Schema has a simpler, non-composable `Validation[A]` that is embedded inside `PrimitiveType[A]` and checked during `DynamicSchema.check`. It is not composable with `And`/`Or`/`Not`:

```scala

// Validation is checked during DynamicSchema.check — not during fromDynamicValue
val dynSchema = Schema[Int].toDynamicSchema

val valid   = DynamicValue.Primitive(PrimitiveValue.Int(5))
val invalid = DynamicValue.Primitive(PrimitiveValue.Int(-1))

dynSchema.conforms(valid)    // true
dynSchema.conforms(invalid)  // true (no validation constraint on the base Int schema)
```

For validated types, use `Schema[A].transform` with a throwing `to` function, which signals failure during `fromDynamicValue`:

```scala

// Validated positive integer
val positiveIntSchema: Schema[Int] =
  Schema[Int].transform(
    to   = n => if (n > 0) n else throw SchemaError.validationFailed("Must be positive"),
    from = identity
  )

positiveIntSchema.fromDynamicValue(
  DynamicValue.Primitive(PrimitiveValue.Int(-1))
)
// Left(SchemaError: Must be positive)
```

For struct-level validation across multiple fields, implement validation in the `to` function of a wrapper:

```scala

final case class AgeRange(min: Int, max: Int)
object AgeRange {
  implicit val schema: Schema[AgeRange] = Schema.derived[AgeRange]
  // Schema-level validation is handled through the derived schema's
  // DynamicSchema.check, or by adding custom validation in a wrapping transform.
}
```

:::info
If you rely heavily on ZIO Schema's composable validation (chaining `And`, `Or`, `Not`, `Transform` validators), you will need to implement that logic in the `to` function of a `Schema.transform` wrapper, or in application-level validation code. ZIO Blocks Schema's built-in `Validation` is deliberately simpler: it covers the most common primitive constraints without the complexity of a full combinator library.
:::

---

## Migrating the Fail Schema

ZIO Schema provides `Schema.fail[A](message: String)` to represent the absence of schema information:

```scala
// ZIO Schema 1.x
val missing: Schema[MyType] = Schema.fail("No schema available for MyType")
```

ZIO Blocks Schema has no equivalent `Fail` schema node. The recommended approach is to leave the implicit schema undefined and let the compiler report the missing instance, or to throw from a type class derivation:

```scala
// ZIO Blocks Schema — no Schema.fail; use a compile error or a runtime exception approach
// If you need a runtime sentinel, use Schema[DynamicValue] or create a minimal placeholder:
val placeholder: Schema[DynamicValue] = Schema[DynamicValue]
```

---

## Summary of Missing Features

The following ZIO Schema features do not yet have equivalents in ZIO Blocks Schema:

| Feature | Status |
|---|---|
| `Schema.fail` / fail schemas | Not available |
| `Schema.migrate[B]` / `Schema.coerce[B]` | Not available — schema migration is planned |
| `MetaSchema` / schema serialization | Partial — `DynamicSchema` covers structural inspection; full schema round-trip is not available |
| `Fallback[A, B]` schema | Not available |
| `NonEmptyChunk` / `NonEmptyMap` schemas | Not available — use wrapper types |
| `Schema.Singleton` / singleton schemas | Not available |
| `DynamicValue.BothValue` / `DynamicValue.DynamicAst` | Not available |
| Composable `Validation` (`And`, `Or`, `Not`) | Not available — use `transform` with throwing functions |
| Streaming codec methods (`streamEncoder`, `streamDecoder`) | Not available — wrap codecs in your effect system |
| ZIO `Chunk` (from `zio-core`) | Replaced by `zio.blocks.chunk.Chunk` |

---

## Running the Examples

All code from this guide is available as runnable examples in the `schema-examples` module.

**1. Clone the repository and navigate to the project:**

```bash
git clone https://github.com/zio/zio-blocks.git
cd zio-blocks
```

**2. Run individual examples with sbt:**

```bash
# Step 1: Schema derivation, primitives, and DynamicValue roundtrip
sbt "schema-examples/runMain ziosschemamigration.Step1SchemaDerivedAndPrimitives"

# Step 2: Modifiers and transform (annotations, newtypes)
sbt "schema-examples/runMain ziosschemamigration.Step2ModifiersAndTransform"

# Step 3: Optics (Lens, Prism) and DynamicSchema validation
sbt "schema-examples/runMain ziosschemamigration.Step3OpticsAndDynamicSchema"

# Step 4: Diff and patch
sbt "schema-examples/runMain ziosschemamigration.Step4DiffAndPatch"

# Complete example: end-to-end e-commerce domain
sbt "schema-examples/runMain ziosschemamigration.CompleteMigrationExample"

# Type class derivation — deriving Show from a Schema
sbt "schema-examples/runMain typeclassderivation.DeriveShowExample"

# Type class derivation — deriving a random generator from a Schema
sbt "schema-examples/runMain typeclassderivation.DeriveGenExample"
```

**3. Or compile all examples at once:**

```bash
sbt "schema-examples/compile"
```

---

## Going Further

- [Schema Reference](../reference/schema.md) — full `Schema[A]` API
- [Reflect Reference](../reference/reflect.md) — the `Reflect[F, A]` node types
- [Binding Reference](../reference/binding.md) — constructors, deconstructors, and the register system
- [Optics Reference](../reference/optics.md) — `Lens`, `Prism`, `Optional`, `Traversal`
- [Type Class Derivation Guide](../reference/type-class-derivation.md) — implementing `Deriver[TC]`
- [Codec Reference](../reference/codec.md) — the `Format` and `Codec` infrastructure
- [DynamicValue Reference](../reference/dynamic-value.md) — the `DynamicValue` API
- [Validation Reference](../reference/validation.md) — built-in validation constraints
