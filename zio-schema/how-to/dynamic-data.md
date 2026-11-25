# How to Work with Schemaless Data

> Decode JSON of unknown shape into a DynamicValue, read and change it, bind it to a case class, and store it in another format.

In this guide, we handle data whose shape we do not know at compile time: webhook events from a third-party service, where every event type carries a different `data` object. We decode the JSON into a `DynamicValue`, read and redact fields in it, turn parts of it into a case class when we know their type, and store the whole event in a binary format.

`DynamicValue` is ZIO Schema's format-neutral value tree. Records keep their field names, sequences keep their elements, and every leaf keeps its standard type, so the same tree can come from JSON and go out as MessagePack, or become a typed value through a schema.

## The Problem

A service that receives arbitrary JSON usually parses it into the JSON library's own syntax tree and digs through it by hand. For example, reading the plan of a customer event with the ZIO JSON tree looks like this:

```scala
import zio.json.ast.Json

val raw = """{"id":"evt_1","type":"customer.created","data":{"email":"ana@example.com","plan":"pro"}}"""

val plan: Option[String] =
  Json.decoder.decodeJson(raw).toOption.flatMap {
    case Json.Obj(fields) =>
      fields.collectFirst { case ("data", Json.Obj(data)) => data }.flatMap { data =>
        data.collectFirst { case ("plan", Json.Str(plan)) => plan }
      }
    case _                => None
  }
```

That tree belongs to one format. Turning the payload into a case class, or writing it to a binary store, means another hand-written conversion for every target. With `DynamicValue`, the JSON codec builds the tree, and the same schemas and codecs that serve typed data take it from there.

## Prerequisites

The examples use the core module with the JSON and MessagePack codecs:

```scala
libraryDependencies ++= Seq(
  "dev.zio" %% "zio-schema"          % "1.9.2",
  "dev.zio" %% "zio-schema-json"     % "1.9.2",
  "dev.zio" %% "zio-schema-msg-pack" % "1.9.2"
)
```

The [DynamicValue](../operations/dynamic-data-representation.md) reference lists every case of the tree, and [Dynamic Data Representation](../operations/dynamic-data-representation.md) explains the idea behind it.

## Decode Unknown JSON into a DynamicValue

`Schema.dynamicValue` is the schema of a `DynamicValue`. By default, codecs write it in a self-describing form that records the name of every case, which is not what a third-party service sends. The `directDynamicMapping` annotation switches the JSON codec to plain JSON: objects become records, arrays become sequences, and `null` becomes `NoneValue`. We build that schema and its codec once:

```scala
import zio.schema._
import zio.schema.annotation.directDynamicMapping
import zio.schema.codec.{JsonCodec, MessagePackCodec}

val anyJson: Schema[DynamicValue] = Schema.dynamicValue.annotate(directDynamicMapping())

val anyJsonCodec = JsonCodec.jsonCodec(anyJson)

val raw: String =
  """{"id":"evt_1","type":"customer.created","data":{"email":"ana@example.com","plan":"pro","seats":3,"tags":["beta"]}}"""
```

Decoding the event gives a tree of records, sequences, and primitives:

```scala
val decoded: Either[String, DynamicValue] = anyJsonCodec.decodeJson(raw)
// decoded: Either[String, DynamicValue] = Right(
//   value = Record(
//     id = Structural,
//     values = ListMap(
//       "id" -> Primitive(value = "evt_1", standardType = string),
//       "type" -> Primitive(value = "customer.created", standardType = string),
//       "data" -> Record(
//         id = Structural,
//         values = ListMap(
//           "email" -> Primitive(value = "ana@example.com", standardType = string),
//           "plan" -> Primitive(value = "pro", standardType = string),
//           "seats" -> Primitive(value = 3, standardType = bigDecimal),
//           "tags" -> Sequence(
//             values = IndexedSeq(
//               Primitive(value = "beta", standardType = string)
//             )
//           )
//         )
//       )
//     )
//   )
// )
```

JSON has no field types, so every object becomes a `Record` with the `TypeId.Structural` id, every string a `String` primitive, every boolean a `Boolean` primitive, and every number a `java.math.BigDecimal` primitive. We keep the decoded event for the next sections:

```scala
val event: DynamicValue = decoded.getOrElse(DynamicValue.NoneValue)
```

Without `directDynamicMapping`, the same codec expects the self-describing form. It wraps each value in an object named after its case:

```scala
println(JsonCodec.jsonCodec(Schema.dynamicValue).encodeJson(DynamicValue("pro"), None))
// {"String":"pro"}
```

Use the plain `Schema.dynamicValue` when both sides use ZIO Schema and the tree must keep every detail. Use the annotated schema for JSON that other systems produce or read.

## Read Fields from the Tree

`DynamicValue` is a sealed trait, so reading it is pattern matching. The following helper follows a path of field names through nested records, and a second one reads a string leaf:

```scala
def field(value: DynamicValue, path: String*): Option[DynamicValue] =
  path.foldLeft(Option(value)) {
    case (Some(DynamicValue.Record(_, fields)), name) => fields.get(name)
    case _                                            => None
  }

def text(value: DynamicValue): Option[String] =
  value match {
    case DynamicValue.Primitive(s: String, _) => Some(s)
    case _                                    => None
  }
```

With these helpers, a service can route an event by its type and read the fields it cares about, without a class for each event type:

```scala
field(event, "type").flatMap(text)
// res2: Option[String] = Some(value = "customer.created")
field(event, "data", "plan").flatMap(text)
// res3: Option[String] = Some(value = "pro")
field(event, "data", "seats")
// res4: Option[DynamicValue] = Some(
//   value = Primitive(value = 3, standardType = bigDecimal)
// )
field(event, "data", "missing")
// res5: Option[DynamicValue] = None
```

## Change Values in the Tree

A `DynamicValue` is immutable, so a change builds a new tree. The following function masks every field whose name is in a set, at any depth, before the event goes to a log:

```scala
def redact(value: DynamicValue, secret: Set[String]): DynamicValue =
  value match {
    case DynamicValue.Record(id, fields) =>
      DynamicValue.Record(
        id,
        fields.map { case (name, fieldValue) =>
          val updated: DynamicValue =
            if (secret.contains(name)) DynamicValue.Primitive("***", StandardType.StringType)
            else redact(fieldValue, secret)
          name -> updated
        }
      )
    case DynamicValue.Sequence(values)   => DynamicValue.Sequence(values.map(redact(_, secret)))
    case DynamicValue.SomeValue(inner)   => DynamicValue.SomeValue(redact(inner, secret))
    case other                           => other
  }

val redacted: DynamicValue = redact(event, Set("email"))
```

The same codec writes the changed tree back as plain JSON, with the fields in their original order:

```scala
println(anyJsonCodec.encodeJson(redacted, None))
// {"id":"evt_1","type":"customer.created","data":{"email":"***","plan":"pro","seats":3,"tags":["beta"]}}
```

Plain JSON has no place for some kinds of node, so in direct mode the JSON encoder throws for them; [Schema.Dynamic](../operations/dynamic-data-representation.md#schemadynamic) lists which. A tree decoded from JSON never contains them. A tree built from a typed value can, for example from a sealed trait or a `Map`; encode such a tree with the plain `Schema.dynamicValue` instead.

## Convert Between Typed and Dynamic Values

When we do know the type, `Schema#toDynamic` turns a value into a tree and `Schema#fromDynamic` turns it back. The tree works with the same helpers as the JSON one, so the redaction above also applies to typed values:

```scala
final case class Customer(email: String, plan: String, seats: Int)

object Customer {
  implicit val schema: Schema[Customer] = DeriveSchema.gen[Customer]
}

val customerTree: DynamicValue = Customer.schema.toDynamic(Customer("ana@example.com", "pro", 3))
```

Redacting the tree and converting it back gives a typed value with the masked field:

```scala
Customer.schema.fromDynamic(redact(customerTree, Set("email")))
// res7: Either[String, Customer] = Right(
//   value = Customer(email = "***", plan = "pro", seats = 3)
// )
```

:::caution[Types That Do Not Convert Back]
For most types, `schema.fromDynamic(schema.toDynamic(x))` returns `Right(x)`. It returns a `Left` for `NonEmptyChunk`, ZIO Prelude's `NonEmptySet` and `NonEmptyMap`, and `Fallback`, including when they appear as fields of a case class. Keep such fields out of data that passes through `DynamicValue`, or model them as `Chunk`, `Set`, `Map`, or `Either` there and convert at the edges. [Round-Trip Limits](../operations/dynamic-data-representation.md#round-trip-limits) shows the failure.
:::

## Bind Dynamic JSON to a Case Class

Once a service knows which event type it has, it often wants the `data` object as a case class. `Schema#fromDynamic` requires each primitive to have exactly the standard type of the target field, and the JSON tree stores every number as a `BigDecimal`, so binding it directly fails on the `seats` field:

```scala
field(event, "data").map(data => Customer.schema.fromDynamic(data))
// res8: Option[Either[String, Customer]] = Some(
//   value = Left(
//     value = "Failed to cast Primitive(3,bigDecimal) to schema Primitive(int,Chunk())"
//   )
// )
```

The reliable route goes through JSON once more: encode the subtree with the direct codec, and decode the text with the codec of the target type. The typed JSON decoder parses numbers as the field types require and ignores fields the case class does not have, such as `tags`:

```scala
def bind[A](value: DynamicValue)(implicit schema: Schema[A]): Either[String, A] =
  JsonCodec.jsonCodec(schema).decodeJson(anyJsonCodec.encodeJson(value, None))
```

The same `data` object now binds to a `Customer`:

```scala
field(event, "data").map(data => bind[Customer](data))
// res9: Option[Either[String, Customer]] = Some(
//   value = Right(
//     value = Customer(email = "ana@example.com", plan = "pro", seats = 3)
//   )
// )
```

Use `Schema#fromDynamic` directly for trees that came from `Schema#toDynamic`, where the standard types already match, and the JSON route for trees that came from JSON.

## Store the Tree in a Binary Format

The plain `Schema.dynamicValue` works with every codec, so a `DynamicValue` can move to a binary format without knowing its shape. The following codec stores the event as MessagePack in the self-describing form:

```scala
val storedCodec = MessagePackCodec.messagePackCodec(Schema.dynamicValue)

val stored = storedCodec.encode(event)
```

Decoding the bytes gives back the same tree, which the direct JSON codec can write as JSON again:

```scala
storedCodec.decode(stored) == Right(event)
// res10: Boolean = true

storedCodec.decode(stored).foreach(tree => println(anyJsonCodec.encodeJson(tree, None)))
// {"id":"evt_1","type":"customer.created","data":{"email":"ana@example.com","plan":"pro","seats":3,"tags":["beta"]}}
```

:::caution[Byte Values in the Self-Describing Form]
The self-describing form, which every codec uses for the plain `Schema.dynamicValue`, has no case for a `Primitive` that holds a `Byte`. The value is lost: the JSON codec, for example, writes it as `{}`. A tree decoded from JSON never holds a `Byte`, but one built with `Schema#toDynamic` from a type with a `Byte` field does. Convert such fields to `Short` or `Int` before storing the tree, or, for JSON, use the annotated schema from the start of this guide, which writes a `Byte` as a number that reads back as a `BigDecimal`.
:::

## Going Further

- [DynamicValue](../operations/dynamic-data-representation.md) lists every case of the tree and the round-trip limits.
- [Schema Migration](../operations/schema-migration.md) changes a `DynamicValue` from one schema version to another with migration steps.
- [How to Send a Schema over the Wire](send-schema-over-the-wire.md) sends the structure of a type along with the data, so the receiver can decode it without the class.
- [JSON Codecs](../derivations/codecs/json.md) and [MessagePack Codecs](../derivations/codecs/message-pack.md) describe the codecs used here.
