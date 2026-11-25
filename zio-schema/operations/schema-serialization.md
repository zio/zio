# Serialization of the Schema Itself

> How to serialize a schema with MetaSchema, and reference for MetaSchema, ExtensibleMetaSchema, and NodePath.

In distributed systems, we often move computation to the data instead of moving data to the computation. Large data sets are expensive to send over a slow network, so a receiver that already holds a function can be sent the data instead, or the other way around. To do that, both sides need to agree on the structure of the data. ZIO Schema makes the structure of a type a value, so the structure can be sent over the wire like any other data.

## Turning a Schema into Data

A `Schema[A]` holds constructors, getters, and transformation functions, and functions cannot be serialized, so a schema cannot be sent over the network or stored in a database as it is. The `Schema#ast` method returns a `MetaSchema` instead: a **schema described as plain data**, a tree of case classes that records the structure of the schema (its records, fields, enum cases, collections, and primitive types) without any Scala functions. `MetaSchema` has its own schema, `MetaSchema.schema`, so any codec can encode it, and the receiving side rebuilds a working schema with the same structure from it. [Schema Migration](schema-migration.md) also compares two `MetaSchema` values to compute the steps between schema versions.

## Sending a Schema over the Wire

The sender calls `Schema#ast` and encodes the result with a codec for `MetaSchema.schema`. The following example uses the JSON codec:

```scala
import zio.Chunk
import zio.schema._
import zio.schema.codec.{BinaryCodec, DecodeError, JsonCodec}
import zio.schema.meta.MetaSchema

final case class Person(name: String, age: Int, email: Option[String])

object Person {
  implicit val schema: Schema[Person] = DeriveSchema.gen[Person]
}

val personAst: MetaSchema = Person.schema.ast

val astCodec: BinaryCodec[MetaSchema] = JsonCodec.schemaBasedBinaryCodec[MetaSchema](MetaSchema.schema)

val sentAst: Chunk[Byte] = astCodec.encode(personAst)
```

The bytes on the wire are the JSON form of the tree:

```scala
println(new String(sentAst.toArray, java.nio.charset.StandardCharsets.UTF_8))
// {"Product":{"id":"repl.MdocSession.MdocApp.Person","path":[],"fields":[["name",{"Value":{"valueType":"string","path":["name"],"optional":false}}],["age",{"Value":{"valueType":"int","path":["age"],"optional":false}}],["email",{"Value":{"valueType":"string","path":["email"],"optional":true}}]],"optional":false}}
```

The receiver decodes the bytes back into a `MetaSchema` with the same codec:

```scala
val receivedAst: Either[DecodeError, MetaSchema] = astCodec.decode(sentAst)
```

The decoded tree is equal to the tree the sender encoded:

```scala
receivedAst == Right(personAst)
// res1: Boolean = true
```

## Rebuilding a Schema on the Receiver

`ExtensibleMetaSchema#toSchema` turns the tree back into a schema. The receiver does not have the `Person` class, so the rebuilt schema describes the same structure with generic types: a record becomes a `GenericRecord` whose values are `ListMap[String, _]`, and every sequence becomes a `Chunk`. The result has the type `Schema[_]`, because the Scala type is not known on the receiving side.

The rebuilt schema can read data that the sender encodes with the original schema. The following helper decodes JSON with any schema and returns the value as a `DynamicValue`:

```scala
def decodeAsDynamic[A](schema: Schema[A], json: Chunk[Byte]): Either[DecodeError, DynamicValue] =
  JsonCodec.schemaBasedBinaryCodec[A](schema).decode(json).map(schema.toDynamic)

val sentPerson: Chunk[Byte] =
  JsonCodec.schemaBasedBinaryCodec[Person].encode(Person("John", 42, Some("john@example.com")))
```

Next, the receiver rebuilds the schema from the tree and decodes the person with it:

```scala
receivedAst.map(ast => decodeAsDynamic(ast.toSchema, sentPerson)).foreach(println)
// Right(Record(Nominal(Chunk(repl),Chunk(MdocSession,MdocApp),Person),ListMap(name -> Primitive(John,string), age -> Primitive(42,int), email -> SomeValue(Primitive(john@example.com,string)))))
```

The same idea works with any other codec, such as Protobuf, because `MetaSchema.schema` is an ordinary schema.

## The `Schema#serializable` Method

`Schema#serializable` is meant to return a `Schema[Schema[A]]` that converts a schema to and from data in one step:

```scala
sealed trait Schema[A] {
  def serializable: Schema[Schema[A]]
}
```

:::caution
`Schema#serializable` does not work in the current version. It builds the schema of the *values* of `A` and treats it as the schema of a `Schema`. For a primitive schema such as `Schema[Int]`, encoding with the result, or calling `Schema#toDynamic` on it, produces a meaningless value, and for a case class it throws a `ClassCastException`. Use `Schema#ast` with `MetaSchema.schema` (or the implicit `Schema[Schema[A]]` described in [Serializing](#serializing)), and `ExtensibleMetaSchema#toSchema` on the receiving side, as shown above.
:::

## MetaSchema Nodes

`MetaSchema` is a type alias for `ExtensibleMetaSchema[DynamicValue :: End]`. The `BuiltIn` type parameter of `ExtensibleMetaSchema` is a type-level list of types whose schemas both sides already know; `MetaSchema` uses a list with only `DynamicValue` in it. Every node records its position in the tree as a [`NodePath`](#nodepath) and whether it is optional:

```scala
package object meta {
  type MetaSchema = ExtensibleMetaSchema[DynamicValue :: End]
  type NodePath   = NodePath.Type
}

sealed trait ExtensibleMetaSchema[BuiltIn <: TypeList] {
  def builtInInstances: SchemaInstances[BuiltIn]
  def path: NodePath
  def optional: Boolean
  def toSchema: Schema[_]
}

object ExtensibleMetaSchema {
  // Every case also takes (implicit builtInInstances: SchemaInstances[BuiltIn])
  final case class Value[BuiltIn <: TypeList](valueType: StandardType[_], path: NodePath = NodePath.root, optional: Boolean = false)
  final case class Product[BuiltIn <: TypeList](id: TypeId, path: NodePath, fields: Chunk[Labelled[BuiltIn]] = Chunk.empty, optional: Boolean = false)
  final case class Sum[BuiltIn <: TypeList](id: TypeId, path: NodePath, cases: Chunk[Labelled[BuiltIn]] = Chunk.empty, optional: Boolean = false)
  final case class Tuple[BuiltIn <: TypeList](path: NodePath, left: ExtensibleMetaSchema[BuiltIn], right: ExtensibleMetaSchema[BuiltIn], optional: Boolean = false)
  final case class Either[BuiltIn <: TypeList](path: NodePath, left: ExtensibleMetaSchema[BuiltIn], right: ExtensibleMetaSchema[BuiltIn], optional: Boolean = false)
  final case class Fallback[BuiltIn <: TypeList](path: NodePath, left: ExtensibleMetaSchema[BuiltIn], right: ExtensibleMetaSchema[BuiltIn], optional: Boolean = false)
  final case class ListNode[BuiltIn <: TypeList](item: ExtensibleMetaSchema[BuiltIn], path: NodePath, optional: Boolean = false)
  final case class Dictionary[BuiltIn <: TypeList](keys: ExtensibleMetaSchema[BuiltIn], values: ExtensibleMetaSchema[BuiltIn], path: NodePath, optional: Boolean = false)
  final case class FailNode[BuiltIn <: TypeList](message: String, path: NodePath, optional: Boolean = false)
  final case class Ref[BuiltIn <: TypeList](refPath: NodePath, path: NodePath, optional: Boolean = false)
  final case class Known[BuiltIn <: TypeList](typeId: TypeId, path: NodePath, optional: Boolean = false)

  final case class Labelled[BuiltIn <: TypeList](label: String, schema: ExtensibleMetaSchema[BuiltIn])
}
```

## Creating Values

A `MetaSchema` usually comes from an existing schema, but its cases are public case classes that can also be built directly.

### From a Schema

`Schema#ast` and `MetaSchema.fromSchema` both convert a schema into its `MetaSchema`. `ExtensibleMetaSchema.fromSchema` does the same for a custom list of built-in types, described in [Extending the Built-In Types](#extending-the-built-in-types):

```scala
sealed trait Schema[A] {
  def ast: MetaSchema
}

object MetaSchema {
  def fromSchema[A](schema: Schema[A]): MetaSchema
}

object ExtensibleMetaSchema {
  def fromSchema[A, BuiltIn <: TypeList](schema: Schema[A])(implicit builtInInstances: SchemaInstances[BuiltIn]): ExtensibleMetaSchema[BuiltIn]
}
```

`Schema#ast` is shorthand for `MetaSchema.fromSchema`, so the two give equal results:

```scala
MetaSchema.fromSchema(Person.schema) == Person.schema.ast
// res3: Boolean = true
```

A recursive schema does not loop forever. When the conversion meets a record or enum it is already inside, it writes a `Ref` node that points to the path of the earlier node instead of expanding it again.

### By Hand

Each case takes an implicit `SchemaInstances[BuiltIn]` in a second parameter list. For `MetaSchema`, the compiler derives it from the implicit `Schema[DynamicValue]`, so building a node only needs the `TypeList` syntax in scope:

```scala
import zio.constraintless.TypeList._
import zio.schema.meta.ExtensibleMetaSchema

val intNode: MetaSchema = ExtensibleMetaSchema.Value[DynamicValue :: End](StandardType.IntType)
// intNode: MetaSchema = Value(
//   valueType = int,
//   path = IndexedSeq(),
//   optional = false
// )

MetaSchema.fromSchema(Schema[Int]) == intNode
// res4: Boolean = true
```

Equality of nodes compares the fields of the first parameter list only. `ExtensibleMetaSchema.equals` also provides a ZIO Prelude `Equal[MetaSchema]` instance.

## Core Operations

Once we have a `MetaSchema`, we can inspect its nodes, serialize it, and turn it back into a schema.

### Inspecting the Tree

`ExtensibleMetaSchema#toString` renders the tree in a compact text form. A top-level record shows as `product` and a nested one as `record`, enums show as `enum`, sequences as `list`, maps as `map`, and a `?` marks an optional node:

```scala
println(personAst)
// product
// |--name: string
// |--age: int
// |--email: ?string
```

Every node also exposes `ExtensibleMetaSchema#path`, its position from the root, and `ExtensibleMetaSchema#optional`, which is `true` when the schema wrapped the node in `Option`. A `Labelled` pairs a field or case name with the node for it. The fields of a record and the paths of their nodes are read like this:

```scala
personAst match {
  case ExtensibleMetaSchema.Product(_, _, fields, _) =>
    fields.map(field => (field.label, field.schema.path.render, field.schema.optional))
  case _                                             => Chunk.empty
}
// res6: Chunk[(String, String, Boolean)] = IndexedSeq(
//   ("name", "name", false),
//   ("age", "age", false),
//   ("email", "email", true)
// )
```

### Serializing

`MetaSchema.schema` is the `Schema[MetaSchema]` that every codec uses to encode and decode a `MetaSchema`, as in [Sending a Schema over the Wire](#sending-a-schema-over-the-wire). `ExtensibleMetaSchema.schema` builds the same schema for any list of built-in types, and `MetaSchema.Labelled` is the `Labelled` type for `MetaSchema`:

```scala
object MetaSchema {
  type Labelled = ExtensibleMetaSchema.Labelled[DynamicValue :: End]

  val schema: Schema[MetaSchema]
}

object ExtensibleMetaSchema {
  implicit def schema[BuiltIn <: TypeList](implicit builtInInstances: SchemaInstances[BuiltIn]): Schema[ExtensibleMetaSchema[BuiltIn]]
}
```

The schema is also available implicitly, so `Schema[MetaSchema]` finds it. The implicit `Schema[Schema[A]]` that the `Schema` companion provides is built on it: it encodes a schema through its `MetaSchema` and decodes by calling `ExtensibleMetaSchema#toSchema`. The decoded value is typed `Schema[A]`, but it has the rebuilt structure described below, not the original schema:

```scala
object Schema {
  implicit def schemaSchema[A]: Schema[Schema[A]]
}
```

### Rebuilding a Schema

`ExtensibleMetaSchema#toSchema` is the method the receiver calls in [Rebuilding a Schema on the Receiver](#rebuilding-a-schema-on-the-receiver):

```scala
sealed trait ExtensibleMetaSchema[BuiltIn <: TypeList] {
  def toSchema: Schema[_]
}
```

The values of a rebuilt record are `ListMap` instances keyed by field name. The following example converts a `Person` value, in its dynamic form, with the rebuilt schema:

```scala
val rebuilt: Schema[_] = personAst.toSchema

val asListMap: Either[String, Any] =
  rebuilt.fromDynamic(Person.schema.toDynamic(Person("Alice", 42, None)))
```

The result holds the same data as a `ListMap`:

```scala
asListMap
// res7: Either[String, Any] = Right(
//   value = ListMap("name" -> "Alice", "age" -> 42, "email" -> None)
// )
```

Each node becomes one schema:

| Node         | Rebuilt schema                                                                               |
| ------------ | -------------------------------------------------------------------------------------------- |
| `Value`      | `Schema.Primitive` of the same `StandardType`                                                |
| `Product`    | `Schema.GenericRecord` with the same `TypeId` and field names, over `ListMap[String, _]`     |
| `Sum`        | An enum schema with the same `TypeId` and case names; its cases use identity constructors    |
| `Tuple`      | `Schema.Tuple2`                                                                              |
| `Either`     | `Schema.Either`                                                                              |
| `Fallback`   | `Schema.Fallback`                                                                            |
| `ListNode`   | `Schema[Chunk[_]]`                                                                           |
| `Dictionary` | `Schema.Map`                                                                                 |
| `FailNode`   | `Schema.Fail` with the same message                                                          |
| `Ref`        | A lazy schema (`Schema.defer`) that points to the rebuilt node at `refPath`                  |
| `Known`      | The built-in schema with the same `TypeId`, or `Schema.Fail` when no built-in schema matches |

When `ExtensibleMetaSchema#optional` is `true` for a node, `ExtensibleMetaSchema#toSchema` wraps the rebuilt schema in `Schema#optional`.

## What the Round Trip Loses

A `MetaSchema` describes only structure. Converting a schema with `Schema#ast` and rebuilding it with `ExtensibleMetaSchema#toSchema` keeps the shape of the data, including the `TypeId`, field names, and case names of records and enums, while everything that is code or metadata is gone:

| Part of the original schema                                             | What remains after the round trip                                                           |
| ----------------------------------------------------------------------- | ------------------------------------------------------------------------------------------- |
| `Schema#transform`, `Schema#transformOrFail`, and schemas built on them | Only the underlying schema; the functions and the target type are gone                      |
| Annotations on schemas, fields, and cases                               | None, so `@fieldName`, `@caseName`, `@transientField`, default values, and similar are lost |
| Field validations and `@validate`                                       | None                                                                                        |
| Case class types                                                        | A `GenericRecord` over `ListMap[String, _]`                                                 |
| Sealed trait cases                                                      | Enum cases whose values are generic records                                                 |
| `Set`, `NonEmptyChunk`, `NonEmptySet`                                   | A `Chunk`                                                                                   |
| `NonEmptyMap`                                                           | A `Map`                                                                                     |
| `Fallback` below the top level                                          | An `Either`                                                                                 |
| `Schema.Dynamic` with annotations                                       | The full structure of `DynamicValue.schema` instead of `Schema.Dynamic`                     |

A field that the original schema built with `Schema#transform` therefore comes back as its underlying type, the way a newtype such as `Schema[BigDecimal]` arrives as its underlying primitive. For example, a schema for an `Email` wrapper rebuilds as a plain string schema:

```scala
final case class Email(value: String)

val emailSchema: Schema[Email] = Schema[String].transform(Email(_), _.value)
// emailSchema: Schema[Email] = Transform(
//   schema = Primitive(standardType = string, annotations = IndexedSeq()),
//   f = zio.schema.Schema$$Lambda$24422/0x00007d2db38e7070@757e6abd,
//   g = zio.schema.Schema$$Lambda$24423/0x00007d2db38e7440@6122dd37,
//   annotations = IndexedSeq(),
//   identity = SourceLocation(
//     path = "serialization-of-the-schema-itself.md",
//     line = 131,
//     col = 58
//   )
// )

println(emailSchema.ast)
// string
emailSchema.ast.toSchema
// res9: Schema[_] = Primitive(
//   standardType = string,
//   annotations = IndexedSeq()
// )
```

Annotations disappear in the same way, so codec settings that depend on them do not travel with the `MetaSchema`:

```scala
val annotated: Schema[Int] = Schema[Int].annotate("a note")
// annotated: Schema[Int] = Primitive(
//   standardType = int,
//   annotations = IndexedSeq("a note")
// )

annotated.annotations
// res10: Chunk[Any] = IndexedSeq("a note")
annotated.ast.toSchema.annotations
// res11: Chunk[Any] = IndexedSeq()
```

A `Fallback` at the top level of a schema keeps its own `Fallback` node, but `MetaSchema.schema` has no case for that node, so serialize such a schema inside a record or another container.

## NodePath

A `NodePath` is the path from the root of a schema to one of its nodes, as a sequence of labels. Record fields and enum cases use their names as labels, and the other nodes use fixed labels: `item` for the element of a sequence, `keys` and `values` for a map, and `left` and `right` for tuples and `Either`. `NodePath` is a ZIO Prelude subtype of `Chunk[String]`, so it supports every `Chunk` operation, and [Migration Operations](../reference/migration-operations.md) uses it to address the node each step changes.

The `NodePath` companion provides the root path and the operations for building and comparing paths:

```scala
object NodePath {
  val root: NodePath
  val empty: NodePath

  implicit class NodePathSyntax(self: NodePath) {
    def /(label: String): NodePath
    def /(subpath: NodePath): NodePath
    def relativeTo(path: NodePath): NodePath
    def isSubpathOf(path: NodePath): Boolean
    def partitionLeaf: (NodePath, Option[String])
    def render: String
  }
}
```

`NodePath.root` and `NodePath.empty` are both the empty path. `NodePathSyntax#/` appends a label or a whole path. `NodePathSyntax#relativeTo` removes a prefix, and returns the path unchanged when the prefix does not match. `NodePathSyntax#isSubpathOf` checks for a prefix, `NodePathSyntax#partitionLeaf` splits off the last label, and `NodePathSyntax#render` joins the labels with `/`, rendering the empty path as `ROOT`:

```scala
import zio.schema.meta.NodePath

val address = NodePath.root / "address"
// address: NodePath = IndexedSeq("address")
val street  = address / "street"
// street: NodePath = IndexedSeq("address", "street")

street.render
// res12: String = "address/street"
street.relativeTo(address)
// res13: NodePath = IndexedSeq("street")
street.isSubpathOf(address)
// res14: Boolean = true
street.partitionLeaf
// res15: (NodePath, Option[String]) = (
//   IndexedSeq("address"),
//   Some(value = "street")
// )
address / (NodePath.root / "city" / "zip")
// res16: NodePath = IndexedSeq("address", "city", "zip")
NodePath.root.render
// res17: String = "ROOT"
```

`ExtensibleMetaSchema.nodePathSchema` is the implicit `Schema[NodePath]`, which encodes a path as a sequence of strings.

## Extending the Built-In Types

Sometimes both sides of a connection already share some schemas, for example a common library of domain types. Listing those types in the `BuiltIn` parameter of `ExtensibleMetaSchema` keeps them out of the serialized description: `ExtensibleMetaSchema.fromSchema` writes a `Known` node holding only the type's `TypeId`, and `ExtensibleMetaSchema#toSchema` returns the shared schema instance itself, with its transforms and annotations intact.

The type list is built from `zio.constraintless.TypeList`, and the compiler needs an implicit `Schema` for each type in it to derive the `SchemaInstances[BuiltIn]`:

```scala
trait SchemaInstances[As <: TypeList] {
  def withInstance[B, D](use: Schema[B] => D)(implicit ev: B IsElementOf As): D
  def all: List[Schema[_]]
}
```

The following example adds a `Pet` enum to the built-in list:

```scala
import zio.constraintless.TypeList._
import zio.schema._
import zio.schema.meta.ExtensibleMetaSchema

sealed trait Pet

object Pet {
  case object Rock                  extends Pet
  final case class Dog(name: String) extends Pet

  implicit lazy val schema: Schema[Pet] = DeriveSchema.gen[Pet]
}

type PetMetaSchema = ExtensibleMetaSchema[DynamicValue :: Pet :: End]

val petMetaSchemaSchema: Schema[PetMetaSchema] = ExtensibleMetaSchema.schema[DynamicValue :: Pet :: End]

val petMeta: PetMetaSchema = ExtensibleMetaSchema.fromSchema[Pet, DynamicValue :: Pet :: End](Pet.schema)
```

The schema of `Pet` becomes a single `Known` node, and rebuilding it returns the very same schema instance:

```scala
petMeta.isInstanceOf[ExtensibleMetaSchema.Known[_]]
// res19: Boolean = true
petMeta.toSchema eq Pet.schema
// res20: Boolean = true
```

Both sides must use the same type list, and the receiver decodes with `petMetaSchemaSchema` rather than `MetaSchema.schema`. Only record, enum, and `Schema.Dynamic` instances in the list become `Known` nodes, and the lookup compares schemas with `==`, so pass the same schema instance that the list holds.

## Integration

`MetaSchema` connects the structure of a schema to the rest of ZIO Schema:

- [DynamicValue](dynamic-data-representation.md) is the data counterpart of `MetaSchema`, and its `DynamicAst` case carries a `MetaSchema` inside a value.
- [Schema Migration](schema-migration.md) shows how `Migration.derive` compares two `MetaSchema` values to compute migration steps.
- [Migration Operations](../reference/migration-operations.md) lists the steps, several of which take a `NodePath` or a `MetaSchema`.
- [How to Send a Schema over the Wire](../how-to/send-schema-over-the-wire.md) walks through a complete sender and receiver.
