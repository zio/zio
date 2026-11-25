# Dynamic Data Representation

> DynamicValue, the schema-less tree that holds any value ZIO Schema can describe: how to create it, convert it to and from typed values, migrate it, and encode it.

`DynamicValue` is a **self-describing value tree** that can hold any value a `Schema` can describe, without the Scala type that the value came from. Each node records both the data and its shape: a record keeps its field names, an enum keeps the name of its case, and a primitive keeps its `StandardType`. Code that does not know the type at compile time can therefore inspect, rewrite, and migrate the value, and a schema turns it back into a typed value later.

`DynamicValue` has no type parameter. The type information that a typed value carries in its Scala type is stored in the tree itself, so one `DynamicValue` type covers every value. The two conversions are `Schema#toDynamic`, which erases the type, and `Schema#fromDynamic` (or `DynamicValue#toTypedValue`), which restores it.

This differs from the relational database model, where the database schema holds the structure of the data and the data itself lives in a separate location. Document formats such as JSON or XML store the data and its structure together: a JSON document can carry type information next to the data, so there is no need for a separate schema. `DynamicValue` brings the same self-describing model to any value that has a `Schema`.

## Usage

A typed value becomes a `DynamicValue` through its schema. We define a `Person` and convert one value:

```scala
import zio.Chunk
import zio.schema._
import zio.schema.meta.{Migration, NodePath}

final case class Person(name: String, age: Int)

object Person {
  implicit val schema: Schema[Person] = DeriveSchema.gen[Person]
}

final case class Employee(name: String, age: Int)

object Employee {
  implicit val schema: Schema[Employee] = DeriveSchema.gen[Employee]
}

val alice: DynamicValue = DynamicValue(Person("Alice", 42))
```

Printing the dynamic value shows the mixture of the data and its structure:

```scala
println(alice)
// Record(Nominal(Chunk(repl),Chunk(MdocSession,MdocApp),Person),ListMap(name -> Primitive(Alice,string), age -> Primitive(42,int)))
```

The record keeps the `TypeId` of `Person`, and each field keeps its value together with its standard type. Decoding matches record fields by name and ignores the `TypeId`, so the dynamic form decodes into `Employee`, a different case class with the same fields, as well as into `Person`. A migration step can also change the value before it is decoded:

```scala
alice.toTypedValue[Person]
// res1: Either[String, Person] = Right(
//   value = Person(name = "Alice", age = 42)
// )
alice.toTypedValue[Employee]
// res2: Either[String, Employee] = Right(
//   value = Employee(name = "Alice", age = 42)
// )
alice.transform(Chunk(Migration.DeleteNode(NodePath.root / "age")))
// res3: Either[String, DynamicValue] = Right(
//   value = Record(
//     id = Nominal(
//       packageName = IndexedSeq("repl"),
//       objectNames = IndexedSeq("MdocSession", "MdocApp"),
//       typeName = "Person"
//     ),
//     values = ListMap(
//       "name" -> Primitive(value = "Alice", standardType = string)
//     )
//   )
// )
```

Rewriting a value's structure this way, for example removing a field or making it optional, is dynamic value migration. [Applying Migrations](#applying-migrations) describes `DynamicValue#transform`, and [Schema Migration](schema-migration.md) shows how ZIO Schema computes the steps between two versions of a schema.

## Cases

`DynamicValue` is a sealed trait with one case per kind of schema node:

```scala
sealed trait DynamicValue

object DynamicValue {
  final case class Record(id: TypeId, values: ListMap[String, DynamicValue]) extends DynamicValue
  final case class Enumeration(id: TypeId, value: (String, DynamicValue))   extends DynamicValue
  final case class Sequence(values: Chunk[DynamicValue])                     extends DynamicValue
  final case class Dictionary(entries: Chunk[(DynamicValue, DynamicValue)])  extends DynamicValue
  final case class SetValue(values: Set[DynamicValue])                       extends DynamicValue
  sealed case class Primitive[A](value: A, standardType: StandardType[A])    extends DynamicValue
  sealed case class Singleton[A](instance: A)                                extends DynamicValue
  final case class SomeValue(value: DynamicValue)                            extends DynamicValue
  case object NoneValue                                                      extends DynamicValue
  sealed case class Tuple(left: DynamicValue, right: DynamicValue)           extends DynamicValue
  final case class LeftValue(value: DynamicValue)                            extends DynamicValue
  final case class RightValue(value: DynamicValue)                           extends DynamicValue
  final case class BothValue(left: DynamicValue, right: DynamicValue)        extends DynamicValue
  final case class DynamicAst(ast: MetaSchema)                               extends DynamicValue
  final case class Error(message: String)                                    extends DynamicValue
}
```

The table lists which schemas produce each case when `Schema#toDynamic` converts a value:

| Case          | Produced by `Schema#toDynamic` for                                                     |
| ------------- | -------------------------------------------------------------------------------------- |
| `Record`      | Case classes and `GenericRecord`; transient fields are left out                        |
| `Enumeration` | Sealed traits and other `Schema.Enum`s; the pair is the case name and the case's value |
| `Sequence`    | `List`, `Vector`, `Chunk`, and non-empty sequences such as `NonEmptyChunk`             |
| `Dictionary`  | `Map` and `NonEmptyMap`                                                                |
| `SetValue`    | `Set`                                                                                  |
| `Primitive`   | Every standard type, such as `Int`, `String`, `UUID`, and the `java.time` types        |
| `SomeValue`   | `Some`                                                                                 |
| `NoneValue`   | `None`                                                                                 |
| `Tuple`       | Pairs; larger tuples become nested pairs                                               |
| `LeftValue`   | `Left` of an `Either`, and `Fallback.Left`                                             |
| `RightValue`  | `Right` of an `Either`, and `Fallback.Right`                                           |
| `BothValue`   | `Fallback.Both`                                                                        |
| `Error`       | A `Schema.Fail`, or a `Schema#transformOrFail` whose reverse function returns `Left`   |
| `Singleton`   | Nothing; build it by hand                                                              |
| `DynamicAst`  | Nothing; build it by hand to carry a schema as data                                    |

A case object of a sealed trait becomes an `Enumeration` whose value is a `Record` with no fields. `DynamicAst` holds a [MetaSchema](serialization-of-the-schema-itself.md), the serializable description of a schema. When no other decoding rule matches the schema, a `Tuple` whose right side is a `DynamicAst` decodes into a pair of the left value and the schema rebuilt from the `MetaSchema`.

Code that processes a `DynamicValue` usually matches on these cases. The following function renders any tree as text:

```scala
def render(value: DynamicValue): String =
  value match {
    case DynamicValue.Record(_, fields)            =>
      fields.map { case (name, v) => s"$name = ${render(v)}" }.mkString("{", ", ", "}")
    case DynamicValue.Enumeration(_, (caseName, v)) => s"$caseName${render(v)}"
    case DynamicValue.Sequence(values)              => values.map(render).mkString("[", ", ", "]")
    case DynamicValue.SetValue(values)              => values.map(render).mkString("Set(", ", ", ")")
    case DynamicValue.Dictionary(entries)           =>
      entries.map { case (k, v) => s"${render(k)} -> ${render(v)}" }.mkString("Map(", ", ", ")")
    case DynamicValue.Primitive(v, _)               => v.toString
    case DynamicValue.Singleton(instance)           => instance.toString
    case DynamicValue.SomeValue(v)                  => render(v)
    case DynamicValue.NoneValue                     => "null"
    case DynamicValue.Tuple(l, r)                   => s"(${render(l)}, ${render(r)})"
    case DynamicValue.LeftValue(v)                  => s"Left(${render(v)})"
    case DynamicValue.RightValue(v)                 => s"Right(${render(v)})"
    case DynamicValue.BothValue(l, r)               => s"Both(${render(l)}, ${render(r)})"
    case DynamicValue.DynamicAst(_)                 => "<schema>"
    case DynamicValue.Error(message)                => s"<error: $message>"
  }
```

The same function handles a record, a map of lists, and an optional pair:

```scala
println(render(alice))
// {name = Alice, age = 42}
println(render(DynamicValue(Map("scores" -> List(1, 2)))))
// Map(scores -> [1, 2])
println(render(DynamicValue(Option(("x", 1)))))
// (x, 1)
```

## Creating Values

A `DynamicValue` comes either from a typed value and its schema, or from the case constructors directly.

### From a Typed Value

The usual way to get a `DynamicValue` is to let a schema convert a typed value. Every entry point below calls the same conversion, so they all give the same result:

```scala
sealed trait Schema[A] {
  def toDynamic(value: A): DynamicValue
}

object Schema {
  def toDynamic[A](a: A)(implicit schema: Schema[A]): DynamicValue
}

object DynamicValue {
  def apply[A](a: A)(implicit ev: Schema[A]): DynamicValue
  def fromSchemaAndValue[A](schema: Schema[A], value: A): DynamicValue
}
```

`Schema#toDynamic` and `DynamicValue.fromSchemaAndValue` take the schema explicitly, while `DynamicValue.apply` and `Schema.toDynamic` find it implicitly. The conversion works for any schema, including collections and options:

```scala
Person.schema.toDynamic(Person("Bob", 30)) == DynamicValue(Person("Bob", 30))
// res7: Boolean = true

DynamicValue.fromSchemaAndValue(Schema[Option[Int]], Some(1))
// res8: DynamicValue = SomeValue(
//   value = Primitive(value = 1, standardType = int)
// )

Schema.toDynamic(List("a", "b"))
// res9: DynamicValue = Sequence(
//   values = IndexedSeq(
//     Primitive(value = "a", standardType = string),
//     Primitive(value = "b", standardType = string)
//   )
// )
```

The `zio.schema.syntax` object adds a `dynamic` extension method to any value that has an implicit schema:

```scala
import zio.schema.syntax._

Person("Carol", 25).dynamic
// res10: DynamicValue = Record(
//   id = Nominal(
//     packageName = IndexedSeq("repl"),
//     objectNames = IndexedSeq("MdocSession", "MdocApp"),
//     typeName = "Person"
//   ),
//   values = ListMap(
//     "name" -> Primitive(value = "Carol", standardType = string),
//     "age" -> Primitive(value = 25, standardType = int)
//   )
// )
```

### By Hand

Every case is a public case class, so we can also build a tree directly. This is how code that reads an untyped source, such as a configuration file or a message from another service, produces a value for a schema to decode. The following tree has the same shape as a `Person`:

```scala
import scala.collection.immutable.ListMap

def int(i: Int): DynamicValue       = DynamicValue.Primitive(i, StandardType.IntType)
def str(s: String): DynamicValue    = DynamicValue.Primitive(s, StandardType.StringType)

val handMade: DynamicValue =
  DynamicValue.Record(
    TypeId.parse("example.Person"),
    ListMap("name" -> str("Dave"), "age" -> int(51))
  )
```

Because decoding ignores the `TypeId`, the hand-made record decodes as a `Person`:

```scala
handMade.toTypedValue[Person]
// res11: Either[String, Person] = Right(
//   value = Person(name = "Dave", age = 51)
// )
```

The other cases follow the shape of their schema. A sequence decodes into any sequence schema, `NoneValue` into an option, `RightValue` into an `Either`, and a `Dictionary` of key and value pairs into a `Map`:

```scala
Schema[List[Int]].fromDynamic(DynamicValue.Sequence(Chunk(int(1), int(2))))
// res12: Either[String, List[Int]] = Right(value = List(1, 2))

Schema[Option[String]].fromDynamic(DynamicValue.NoneValue)
// res13: Either[String, Option[String]] = Right(value = None)

Schema[Either[String, Int]].fromDynamic(DynamicValue.RightValue(int(7)))
// res14: Either[String, Either[String, Int]] = Right(value = Right(value = 7))

Schema[Map[String, Int]].fromDynamic(DynamicValue.Dictionary(Chunk(str("a") -> int(1))))
// res15: Either[String, Map[String, Int]] = Right(value = Map("a" -> 1))
```

## Core Operations

The operations on a `DynamicValue` either decode it back into a typed value or rewrite it with migration steps.

### Converting to Typed Values

Four methods decode a `DynamicValue` with a schema. They differ only in how they report a failure: `DynamicValue#toTypedValue` and `Schema#fromDynamic` return the error message as a `String`, `DynamicValue#toValue` returns the full `DecodeError`, and `DynamicValue#toTypedValueOption` drops the error:

```scala
sealed trait DynamicValue {
  def toTypedValue[A](implicit schema: Schema[A]): Either[String, A]
  def toValue[A](implicit schema: Schema[A]): Either[DecodeError, A]
  def toTypedValueOption[A](implicit schema: Schema[A]): Option[A]
}

sealed trait Schema[A] {
  def fromDynamic(value: DynamicValue): Either[String, A]
}
```

A string primitive cannot become an `Int`, so each method reports the failure in its own way:

```scala
val notAnInt = DynamicValue.Primitive("forty-two", StandardType.StringType)
// notAnInt: DynamicValue.Primitive[String] = Primitive(
//   value = "forty-two",
//   standardType = string
// )

notAnInt.toTypedValue[Int]
// res16: Either[String, Int] = Left(
//   value = "Failed to cast Primitive(forty-two,string) to schema Primitive(int,Chunk())"
// )
notAnInt.toValue[Int]
// res17: Either[codec.DecodeError, Int] = Left(
//   value = CastError(
//     value = Primitive(value = "forty-two", standardType = string),
//     schema = Primitive(standardType = int, annotations = IndexedSeq())
//   )
// )
notAnInt.toTypedValueOption[Int]
// res18: Option[Int] = None
```

`DynamicValue#toValue` reports failures with `DecodeError`, the same error type the codecs use; the [Codec Core Types](../reference/codec-core.md) page describes its cases. A record fails with `DecodeError.IncompatibleShape` when it has a field that the target schema does not know, so a case class that stores the same data under another field name cannot decode the dynamic form of a `Person`:

```scala
case class Customer(fullName: String, age: Int)

object Customer {
  implicit val schema: Schema[Customer] = DeriveSchema.gen[Customer]
}

alice.toTypedValue[Customer].isLeft
// res19: Boolean = true
```

The decoder walks the schema and the tree together. A primitive must have the same `StandardType` as the schema, a record decodes field by field, an enumeration looks up its case by name, and a `Schema.Transform` decodes the underlying value and then applies the transformation's forward function. A `DynamicValue.Error` always fails with its message.

`DynamicValue.decodeStructure` is the record step on its own. It decodes a map of field values against a list of `Schema.Field`s and returns the decoded fields in a `ListMap`:

```scala
object DynamicValue {
  def decodeStructure(
    values: ListMap[String, DynamicValue],
    structure: Chunk[Schema.Field[_, _]]
  ): Either[DecodeError, ListMap[String, _]]
}
```

### Applying Migrations

`DynamicValue#transform` applies a `Chunk` of `Migration` steps to the value and stops at the first step that fails:

```scala
sealed trait DynamicValue {
  def transform(transforms: Chunk[Migration]): Either[String, DynamicValue]
}
```

A migration step names the node it changes with a `NodePath`. The following steps make the `age` field optional, then try two orders of `Optional` and `Require` on the same field:

```scala
val age = NodePath.root / "age"
// age: NodePath = IndexedSeq("age")

alice.transform(Chunk(Migration.Optional(age)))
// res20: Either[String, DynamicValue] = Right(
//   value = Record(
//     id = Nominal(
//       packageName = IndexedSeq("repl"),
//       objectNames = IndexedSeq("MdocSession", "MdocApp"),
//       typeName = "Person"
//     ),
//     values = ListMap(
//       "name" -> Primitive(value = "Alice", standardType = string),
//       "age" -> SomeValue(value = Primitive(value = 42, standardType = int))
//     )
//   )
// )

alice.transform(Chunk(Migration.Optional(age), Migration.Require(age)))
// res21: Either[String, DynamicValue] = Left(
//   value = "Failed to require leaf at path age: Expected optional value at lead"
// )

alice.transform(Chunk(Migration.Require(age), Migration.Optional(age)))
// res22: Either[String, DynamicValue] = Right(
//   value = Record(
//     id = Nominal(
//       packageName = IndexedSeq("repl"),
//       objectNames = IndexedSeq("MdocSession", "MdocApp"),
//       typeName = "Person"
//     ),
//     values = ListMap(
//       "name" -> Primitive(value = "Alice", standardType = string),
//       "age" -> Primitive(value = 42, standardType = int)
//     )
//   )
// )
```

:::caution[Steps run from last to first]
`DynamicValue#transform` folds the chunk from the right, so the last step in the chunk runs first. In the example above, `Chunk(Optional, Require)` fails because `Require` runs first on a field that is not optional yet, while `Chunk(Require, Optional)` succeeds. List the steps in reverse order of how they should apply.
:::

The [Migration Operations](../reference/migration-operations.md) page describes each `Migration` case, and [Schema Migration](schema-migration.md) shows how `Migration.derive` computes the steps between two schemas.

## Schemas for DynamicValue

A `DynamicValue` can itself be a field of a typed value, and codecs can encode it. Two schemas describe it, and they serve different purposes.

### Schema.Dynamic

`Schema.Dynamic` is the schema of a value whose shape is not known in advance. The implicit `Schema.dynamicValue` is an instance of it, so `Schema[DynamicValue]` resolves to `Schema.Dynamic`:

```scala
object Schema {
  implicit val dynamicValue: Schema[DynamicValue] = Schema.Dynamic()

  final case class Dynamic(annotations: Chunk[Any] = Chunk.empty) extends Schema[DynamicValue]
}
```

With this schema, `Schema#toDynamic` returns the value unchanged and `Schema#fromDynamic` accepts any tree except an `Error`. Its default value is `DynamicValue.NoneValue`. A case class can therefore carry a field of free-form data:

```scala
final case class Event(kind: String, payload: DynamicValue)

object Event {
  implicit val schema: Schema[Event] = DeriveSchema.gen[Event]
}

val signup = Event("signup", alice)
```

The payload survives the round trip through the dynamic representation unchanged:

```scala
Event.schema.fromDynamic(Event.schema.toDynamic(signup)) == Right(signup)
// res23: Boolean = true
```

By default, codecs encode a `Schema.Dynamic` value through `DynamicValue.schema`, described next, so the output records every case name. The JSON codec can instead map the tree to plain JSON when the schema carries the `directDynamicMapping` annotation from `zio.schema.annotation`. Records become JSON objects, sequences and sets become arrays, and `NoneValue` becomes `null`. When decoding, objects become records with `TypeId.Structural`, and numbers become `java.math.BigDecimal` primitives:

```scala
import zio.schema.annotation.directDynamicMapping
import zio.schema.codec.JsonCodec

val plainJson: Schema[DynamicValue] = Schema.dynamicValue.annotate(directDynamicMapping())
// plainJson: Schema[DynamicValue] = Dynamic(
//   annotations = IndexedSeq(directDynamicMapping())
// )

println(JsonCodec.jsonCodec(plainJson).encodeJson(alice, None))
// {"name":"Alice","age":42}

JsonCodec.jsonCodec(plainJson).decodeJson("""{"name":"Bob","tags":["a","b"]}""")
// res25: Either[String, DynamicValue] = Right(
//   value = Record(
//     id = Structural,
//     values = ListMap(
//       "name" -> Primitive(value = "Bob", standardType = string),
//       "tags" -> Sequence(
//         values = IndexedSeq(
//           Primitive(value = "a", standardType = string),
//           Primitive(value = "b", standardType = string)
//         )
//       )
//     )
//   )
// )
```

In direct mode, the JSON encoder throws for `Enumeration`, `Dictionary`, `Tuple`, `LeftValue`, `RightValue`, and `DynamicAst`, because plain JSON has no place to record them. An `Error` also throws, with its message. The [JSON Codecs](../derivations/codecs/json.md) page covers the codec itself.

### DynamicValue.schema

`DynamicValue.schema` is an explicit enum schema that describes the structure of `DynamicValue` itself, with one case per `DynamicValue` case except `BothValue`, and one case per standard type of `Primitive` except `Byte`. Codecs use it to write a `DynamicValue` in a self-describing form. `DynamicValue.typeId` is the `TypeId` of that enum:

```scala
object DynamicValue {
  lazy val schema: Schema[DynamicValue]
  lazy val typeId: TypeId
}
```

:::caution[Values that DynamicValue.schema cannot encode]
`DynamicValue.schema` has no case for a `Primitive` that holds a `Byte`, and no case for `BothValue`. A codec that goes through this schema cannot write those values: the JSON codec, for example, writes a `Byte` primitive as `{}`, which no longer holds the value. This also applies to a `Schema.Dynamic` field encoded without `directDynamicMapping`. Convert such values before encoding, for example a `Byte` to a `Short`. The `Singleton` case is encoded without its `instance`, which decodes as `()`.
:::

The `DynamicValue` companion also defines implicit `StandardType` instances for `Instant`, `LocalDate`, `LocalTime`, `LocalDateTime`, `OffsetTime`, `OffsetDateTime`, and `ZonedDateTime`, which point to the matching `StandardType` objects.

## Round-Trip Limits

For most schemas, `schema.fromDynamic(schema.toDynamic(x))` returns `Right(x)`. A few schema kinds have a dynamic form but no decoding rule, so the round trip fails:

```scala
import zio.NonEmptyChunk

val nonEmptySchema = Schema[NonEmptyChunk[Int]]
// nonEmptySchema: Schema[NonEmptyChunk[Int]] = NonEmptySequence(
//   elementSchema = Primitive(standardType = int, annotations = IndexedSeq()),
//   fromChunkOption = zio.schema.Schema$$$Lambda$25231/0x00007d2db3a7ecf0@36dc3e1e,
//   toChunk = zio.schema.Schema$$$Lambda$25232/0x00007d2db3a7f850@26c30d14,
//   annotations = IndexedSeq(),
//   identity = "NonEmptyChunk"
// )

nonEmptySchema.fromDynamic(nonEmptySchema.toDynamic(NonEmptyChunk(1, 2)))
// res26: Either[String, NonEmptyChunk[Int]] = Left(
//   value = "Failed to cast Sequence(Chunk(Primitive(1,int),Primitive(2,int))) to schema NonEmptySequence(Primitive(int,Chunk()), NonEmptyChunk)"
// )
```

:::caution[Schemas that do not round-trip]
`Schema#fromDynamic` cannot decode values of non-empty collections (`NonEmptyChunk`, `NonEmptySet`), `NonEmptyMap`, or `Fallback`, even though `Schema#toDynamic` converts them: the round trip returns `Left` with a cast error. The same failure appears in every operation built on the round trip, such as `Schema#migrate`, `Schema#coerce`, and applying a record patch, when a value contains one of these types. Keep such fields out of data that must pass through `DynamicValue`, or convert them to `Chunk`, `Map`, or `Either` first. To keep a non-empty collection that round-trips, describe it as a transformed `Chunk`, for example `Schema[Chunk[Int]].transformOrFail(c => NonEmptyChunk.fromChunk(c).toRight("empty chunk"), nec => Right(nec.toChunk))`.
:::

## Integration

`DynamicValue` is the common currency of several ZIO Schema features:

- [Schema Migration](schema-migration.md) converts values between schema versions by turning them into `DynamicValue`s and applying `Migration` steps.
- [Migration Operations](../reference/migration-operations.md) lists the steps that `DynamicValue#transform` applies.
- [MetaSchema](serialization-of-the-schema-itself.md) describes the structure of a schema as data, and `DynamicValue.DynamicAst` carries one inside a value.
