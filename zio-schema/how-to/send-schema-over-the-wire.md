# How to Send a Schema over the Wire

> Turn a schema into a MetaSchema, send it as JSON, rebuild it on the receiver, and decode data with it as a DynamicValue.

In this guide, we send the structure of a type from one service to another, so the receiver can read data of that type without having its class on its classpath. The sender turns its `Schema` into a `MetaSchema` and encodes it as JSON. The receiver decodes the `MetaSchema`, rebuilds a schema from it, and uses that schema to decode the data into a `DynamicValue`.

This is the pattern behind a schema registry, a generic consumer that indexes every topic it reads, or an admin tool that displays records of any type.

## The Problem

A generic service, such as an indexer that receives orders, invoices, and shipments from different producers, cannot depend on every producer's case classes. Without the structure of the data, it can only treat each message as raw bytes or as untyped JSON, and it cannot check that a message has the fields the producer promised.

A `Schema[A]` describes that structure, but it cannot be serialized as it is: it contains the constructors, getters, and transformation functions of the type, and functions cannot travel over the network. ZIO Schema separates the two parts. `Schema#ast` returns a `MetaSchema`, a plain tree that keeps only the structure, and `MetaSchema` has its own schema, so any codec can encode it.

## Prerequisites

The examples use the core module and the JSON codec:

```scala
libraryDependencies ++= Seq(
  "dev.zio" %% "zio-schema"      % "1.9.2",
  "dev.zio" %% "zio-schema-json" % "1.9.2"
)
```

The [MetaSchema](../operations/serialization-of-the-schema-itself.md) reference describes every node of the tree, and [DynamicValue](../operations/dynamic-data-representation.md) describes the values the receiver works with.

## Define the Sender's Model

The sender owns an `Order` type with a nested record, a list, and an optional field:

```scala
import zio.schema._
import zio.schema.annotation.directDynamicMapping
import zio.schema.codec.JsonCodec
import zio.schema.meta.MetaSchema

final case class LineItem(sku: String, quantity: Int)

object LineItem {
  implicit val schema: Schema[LineItem] = DeriveSchema.gen[LineItem]
}

final case class Order(id: String, items: List[LineItem], note: Option[String])

object Order {
  implicit val schema: Schema[Order] = DeriveSchema.gen[Order]
}
```

## Turn the Schema into Data

`Schema#ast` converts the schema into a `MetaSchema`. Its `toString` renders the tree compactly, so we can see what the receiver will get:

```scala
println(Order.schema.ast)
// product
// |--id: string
// items: list
// |--item: record
// |----sku: string
// |----quantity: int
// |--note: ?string
```

The rendering shows the field names, the primitive types, and a `?` on optional fields; the tree also keeps the type id of every record. It does not contain any Scala function.

## Encode the Schema as JSON

`MetaSchema.schema` is the schema of `MetaSchema` itself, so the JSON codec derives an encoder for it like for any other type. The sender encodes both the schema and an order:

```scala
val metaSchemaCodec = JsonCodec.jsonCodec(MetaSchema.schema)

val schemaMessage: String = metaSchemaCodec.encodeJson(Order.schema.ast, None).toString

val orderMessage: String =
  JsonCodec
    .jsonCodec(Order.schema)
    .encodeJson(Order("o-1", List(LineItem("TEA-01", 2), LineItem("MUG-07", 1)), Some("leave at door")), None)
    .toString
```

The schema message is ordinary JSON, which can go through HTTP, a message broker, or a database column:

```scala
println(schemaMessage)
// {"Product":{"id":"repl.MdocSession.MdocApp.Order","path":[],"fields":[["id",{"Value":{"valueType":"string","path":["id"],"optional":false}}],["items",{"ListNode":{"item":{"Product":{"id":"repl.MdocSession.MdocApp.LineItem","path":["items","item"],"fields":[["sku",{"Value":{"valueType":"string","path":["items","item","sku"],"optional":false}}],["quantity",{"Value":{"valueType":"int","path":["items","item","quantity"],"optional":false}}]],"optional":false}},"path":["items"],"optional":false}}],["note",{"Value":{"valueType":"string","path":["note"],"optional":true}}]],"optional":false}}
```

The type ids in this page start with `repl.MdocSession.MdocApp`, the object that the documentation tool wraps the examples in; in your code, they start with your package name. A real system sends the schema once, for example when a producer registers a topic, and then sends many data messages that refer to it.

:::caution[Do not use Schema#serializable]
`Schema#serializable` looks like a shortcut for this step, but it does not produce a usable schema of schemas: for a primitive such as `Schema[Int]` it encodes a meaningless value, and for a case class it throws a `ClassCastException`. Use `Schema#ast` with `MetaSchema.schema` as shown here.
:::

## Rebuild the Schema on the Receiver

The receiver has no `Order` class. It decodes the message into a `MetaSchema` with the same codec and calls `MetaSchema#toSchema` to get a working schema back. The Scala type is unknown on this side, so the result is a `Schema[_]`:

```scala
val receivedSchema: Either[String, Schema[_]] =
  metaSchemaCodec.decodeJson(schemaMessage).map(_.toSchema)
```

The rebuilt schema has the same structure as `Order.schema`, but it works on generic values: each record becomes a `GenericRecord` over a `ListMap[String, _]` keyed by field name, and each list becomes a `Chunk`.

## Decode Data with the Received Schema

A codec built from the rebuilt schema reads the JSON that the sender wrote with the original schema. The following helper decodes the text and turns the result into a `DynamicValue`, so the rest of the receiver can work with it without knowing its type:

```scala
def readWith[A](schema: Schema[A], json: String): Either[String, DynamicValue] =
  JsonCodec.jsonCodec(schema).decodeJson(json).map(schema.toDynamic)

val receivedOrder: Either[String, DynamicValue] =
  receivedSchema.flatMap(schema => readWith(schema, orderMessage))
```

The decoded order is a tree of records that keeps the field names, the list, and the optional note:

```scala
receivedOrder.foreach(println)
// Record(Nominal(Chunk(repl),Chunk(MdocSession,MdocApp),Order),ListMap(id -> Primitive(o-1,string), items -> Sequence(Chunk(Record(Nominal(Chunk(repl),Chunk(MdocSession,MdocApp),LineItem),ListMap(sku -> Primitive(TEA-01,string), quantity -> Primitive(2,int))),Record(Nominal(Chunk(repl),Chunk(MdocSession,MdocApp),LineItem),ListMap(sku -> Primitive(MUG-07,string), quantity -> Primitive(1,int))))), note -> SomeValue(Primitive(leave at door,string))))
```

The received schema also checks the structure of incoming data. A message whose line item has no `quantity` fails to decode, while the optional `note` may be absent:

```scala
receivedSchema.flatMap(schema => readWith(schema, """{"id":"o-2","items":[{"sku":"TEA-01"}]}"""))
// res3: Either[String, DynamicValue] = Left(
//   value = ".items[0].quantity(missing)"
// )
```

## Use the Decoded Data

The receiver handles the `DynamicValue` like any other dynamic data. A generic indexer, for example, writes it as plain JSON for a search engine, using the JSON codec in `directDynamicMapping` mode:

```scala
val plainJson = JsonCodec.jsonCodec(Schema.dynamicValue.annotate(directDynamicMapping()))
```

The plain JSON has the same shape as the order the sender encoded:

```scala
receivedOrder.foreach(order => println(plainJson.encodeJson(order, None)))
// {"id":"o-1","items":[{"sku":"TEA-01","quantity":2},{"sku":"MUG-07","quantity":1}],"note":"leave at door"}
```

The [How to Work with Schemaless Data](dynamic-data.md) guide shows how to read fields from the tree, change it, and bind parts of it to a case class.

## Know What the Schema Leaves Behind

A `MetaSchema` describes structure only. Everything in the original schema that is code or metadata stays with the sender: the functions of `Schema#transform` and `Schema#transformOrFail`, every annotation, and every field validation. [What the Round Trip Loses](../operations/serialization-of-the-schema-itself.md#what-the-round-trip-loses) lists each part and what the receiver gets instead.

The receiver therefore accepts any value of the underlying type: an `Email` that the sender builds with `Schema#transformOrFail` and a validating function arrives as a plain string, and the receiver does not run the check. Codec behaviour that comes from annotations, such as a case renamed with `@caseName`, is not applied on the receiving side either, so a receiver may fail to read data written with such annotations. Keep the types you send this way free of annotations that change the encoded form, or apply the same settings on the receiver.

## Going Further

- [MetaSchema](../operations/serialization-of-the-schema-itself.md) documents every node of the tree, `NodePath`, and how to extend the built-in types.
- [Serialization of the Schema Itself](../operations/serialization-of-the-schema-itself.md) explains why a schema is worth serializing.
- [Schema Migration](../operations/schema-migration.md) compares two `MetaSchema` values to move data between versions of a type.
- [How to Work with Schemaless Data](dynamic-data.md) continues with the `DynamicValue` the receiver decodes.
