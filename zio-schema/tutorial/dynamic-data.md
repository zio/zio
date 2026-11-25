# Working with Dynamic Data

> Learn to turn a typed value into a DynamicValue, inspect and change it, convert it back, and read JSON whose shape is unknown.

Welcome to the third ZIO Schema tutorial! It is for Scala developers who have finished [Your First Schema](first-schema.md) and [Evolve a Data Model Safely](evolve-a-data-model.md), so you already know how to derive a schema, encode a value as JSON, and run migration steps on a `DynamicValue`. This tutorial looks inside that `DynamicValue`. You don't need any prior experience with dynamic data to follow along, and the same project setup, the core module plus `zio-schema-json`, covers every example.

## Learning Objectives

By the end of this tutorial, you will understand:

- What a `DynamicValue` is and how a schema turns a typed value into one
- How to inspect a `DynamicValue` by pattern matching on its cases
- How to change a field and turn the result back into a typed value
- How to encode a `DynamicValue` as JSON and read JSON whose shape is not known in advance

We'll learn these concepts through six sections:

1. Turning a Typed Value into a `DynamicValue`
2. Inspecting the Tree
3. Changing a Field
4. Turning the Tree Back into a Typed Value
5. Encoding a `DynamicValue` as JSON
6. Reading JSON of Unknown Shape

We recommend reading from top to bottom, because each section builds on the previous one.

## Background

A typed value such as `Order(1, "Ada", ...)` carries its structure in its Scala type: the compiler knows that an `Order` has an `id`, a `customer`, and a `status`. That is exactly what we want in most code, but some code has to work with values whose type it does not know at compile time. A generic audit log, an admin tool that edits any record, and a service that forwards messages it does not own all fall into this group.

A `DynamicValue` moves the structure from the Scala type into the data. It is a tree: a record node keeps its field names, an enum node keeps the name of its case, and a leaf keeps both its value and its primitive type. Because the tree describes itself, generic code can walk it, change it, and hand it back to a schema, which turns it into a typed value again.

## 1. Turning a Typed Value into a `DynamicValue`

We start with a small order model. A `Status` is either `Pending` or `Shipped` with a tracking number:

```scala
import zio.schema._

sealed trait Status

object Status {
  case object Pending                              extends Status
  final case class Shipped(trackingNumber: String) extends Status

  implicit val schema: Schema[Status] = DeriveSchema.gen[Status]
}

final case class Order(id: Int, customer: String, status: Status)

object Order {
  implicit val schema: Schema[Order] = DeriveSchema.gen[Order]
}
```

The code above does the following:

- `Status` is a sealed trait with two cases, so its schema is an enum schema.
- `Order` is a case class whose `status` field holds a `Status`.
- Each companion object derives a schema with `DeriveSchema.gen`, as in the first tutorial.

`Schema#toDynamic` turns a typed value into a `DynamicValue`. Let's convert one order and look at the result:

```scala
val order = Order(1, "Ada", Status.Shipped("TRACK-42"))
// order: Order = Order(
//   id = 1,
//   customer = "Ada",
//   status = Shipped(trackingNumber = "TRACK-42")
// )

val dynamicOrder: DynamicValue = Order.schema.toDynamic(order)
// dynamicOrder: DynamicValue = Record(
//   id = Nominal(
//     packageName = IndexedSeq("repl"),
//     objectNames = IndexedSeq("MdocSession", "MdocApp"),
//     typeName = "Order"
//   ),
//   values = ListMap(
//     "id" -> Primitive(value = 1, standardType = int),
//     "customer" -> Primitive(value = "Ada", standardType = string),
//     "status" -> Enumeration(
//       id = Nominal(
//         packageName = IndexedSeq("MdocApp", "this"),
//         objectNames = IndexedSeq(),
//         typeName = "Status"
//       ),
//       value = (
//         "Shipped",
//         Record(
//           id = Nominal(
//             packageName = IndexedSeq("repl"),
//             objectNames = IndexedSeq("MdocSession", "MdocApp", "Status"),
//             typeName = "Shipped"
//           ),
//           values = ListMap(
//             "trackingNumber" -> Primitive(value = "TRACK-42", standardType = string)
//           )
//         )
//       )
//     )
//   )
// )
```

The output is long, but its shape is simple. The top node is a `Record` that holds a `TypeId`, which names the original type, and a `ListMap` from field names to child nodes. The `id` and `customer` fields are `Primitive` nodes that pair a value with its `StandardType`, such as `int` or `string`. The `status` field is an `Enumeration` node that holds the case name `Shipped` and that case's own `Record`.

## 2. Inspecting the Tree

Code that processes a `DynamicValue` usually pattern matches on its cases. The following function turns any tree into a short line of text. It handles the three cases that our order uses and falls back to `toString` for the rest:

```scala
def describe(value: DynamicValue): String =
  value match {
    case DynamicValue.Record(_, fields) =>
      fields.map { case (name, child) => s"$name = ${describe(child)}" }.mkString("{ ", ", ", " }")
    case DynamicValue.Enumeration(_, (caseName, child)) =>
      s"$caseName ${describe(child)}"
    case DynamicValue.Primitive(v, standardType) =>
      s"$v: ${standardType.tag}"
    case other =>
      other.toString
  }
```

The code above does the following:

- The `Record` case ignores the `TypeId`, describes each field as `name = child`, and wraps the fields in braces.
- The `Enumeration` case prints the name of the case, followed by the description of the case's value.
- The `Primitive` case prints the value and the tag of its `StandardType`, such as `int` or `string`.
- The last case covers the other kinds of node, such as sequences and options, which our order does not contain.

Let's describe our order:

```scala
println(describe(dynamicOrder))
// { id = 1: int, customer = Ada: string, status = Shipped { trackingNumber = TRACK-42: string } }
```

Notice that `describe` never mentions `Order` or `Status`. The same function describes any value of any type that has a schema, such as a pending order:

```scala
println(describe(Order.schema.toDynamic(Order(2, "Grace", Status.Pending))))
// { id = 2: int, customer = Grace: string, status = Pending {  } }
```

A case object becomes an `Enumeration` whose value is a `Record` with no fields, which is why `Pending` is followed by empty braces.

## 3. Changing a Field

Because the tree is plain data, we can build a changed copy of it. The following function replaces one field of a record and leaves every other field, and the order of the fields, as it is:

```scala
def setField(value: DynamicValue, name: String, newValue: DynamicValue): DynamicValue =
  value match {
    case DynamicValue.Record(id, fields) =>
      DynamicValue.Record(
        id,
        fields.map {
          case (`name`, _) => name -> newValue
          case field       => field
        }
      )
    case other =>
      other
  }
```

The code above does the following:

- The `Record` case keeps the same `TypeId` and maps over the fields.
- The pattern `` (`name`, _) `` matches the field whose name equals the `name` parameter and pairs it with the new value.
- Every other field passes through unchanged, and `ListMap#map` keeps the fields in their original order.
- A value that is not a record comes back unchanged.

`DynamicValue.apply` builds the new leaf from a plain Scala value and its schema. Let's give the order a new customer:

```scala
val graceOrder = setField(dynamicOrder, "customer", DynamicValue("Grace"))
```

We print the changed tree with the `describe` function from section 2:

```scala
println(describe(graceOrder))
// { id = 1: int, customer = Grace: string, status = Shipped { trackingNumber = TRACK-42: string } }
```

Only the `customer` leaf is different. The `id` and the nested `status` record are the same nodes as before.

## 4. Turning the Tree Back into a Typed Value

`Schema#fromDynamic` is the reverse of `Schema#toDynamic`. It walks the schema and the tree together and builds a typed value, or returns a `Left` with an error message. Let's turn the changed tree back into an `Order`:

```scala
Order.schema.fromDynamic(graceOrder)
// res3: Either[String, Order] = Right(
//   value = Order(
//     id = 1,
//     customer = "Grace",
//     status = Shipped(trackingNumber = "TRACK-42")
//   )
// )
```

That's the full round trip: a typed value becomes a tree, generic code changes the tree, and the schema turns it back into a typed value. The schema also checks the tree while it decodes. If we put a number where the schema expects a `String`, decoding fails:

```scala
Order.schema.fromDynamic(setField(dynamicOrder, "customer", DynamicValue(42)))
// res4: Either[String, Order] = Left(
//   value = "Failed to cast Primitive(42,int) to schema Primitive(string,Chunk())"
// )
```

The error message shows the value that does not match and the schema that rejects it. Generic code can therefore change a tree freely, and the conversion back to a typed value catches any change that breaks the type.

:::caution
For most schemas, `schema.fromDynamic(schema.toDynamic(x))` returns `Right(x)`. Values of non-empty collections such as `NonEmptyChunk`, of `NonEmptyMap`, and of `Fallback` are the exception: `Schema#toDynamic` converts them, but `Schema#fromDynamic` cannot decode the result. Keep such fields out of data that must pass through `DynamicValue`, or convert them to `Chunk`, `Map`, or `Either` first.
:::

## 5. Encoding a `DynamicValue` as JSON

A `DynamicValue` has a schema of its own, `Schema.dynamicValue`, so any codec can encode it. With this schema, the JSON codec writes a self-describing document: every node is wrapped in the name of its case, such as `Record`, `Enumeration`, `Int`, or `String`. A reader can rebuild the exact tree from it without knowing the original type.

We create a JSON codec for `DynamicValue` and encode our order tree:

```scala
import zio.schema.codec.JsonCodec

val dynamicJsonCodec = JsonCodec.jsonCodec(Schema.dynamicValue)
val orderJson        = dynamicJsonCodec.encodeJson(dynamicOrder, None).toString
```

Let's look at the document:

```scala
println(orderJson)
// {"Record":{"id":"repl.MdocSession.MdocApp.Order","values":[["id",{"Int":1}],["customer",{"String":"Ada"}],["status",{"Enumeration":{"id":"MdocApp.this.Status","value":["Shipped",{"Record":{"id":"repl.MdocSession.MdocApp.Status.Shipped","values":[["trackingNumber",{"String":"TRACK-42"}]]}}]}}]]}}
```

The document is longer than the JSON for a plain `Order`, because it records the type of every node. In return, decoding it gives back the tree, and the `Order` schema then gives back the typed value:

```scala
dynamicJsonCodec.decodeJson(orderJson).flatMap(Order.schema.fromDynamic)
// res6: Either[String, Order] = Right(
//   value = Order(
//     id = 1,
//     customer = "Ada",
//     status = Shipped(trackingNumber = "TRACK-42")
//   )
// )
```

:::caution
Codecs encode `Schema.dynamicValue` through `DynamicValue.schema`, which has no case for a `Primitive` that holds a `Byte`. A codec therefore cannot write such a leaf: the JSON codec writes it as an empty object, `{}`, which no longer holds the value. Convert a `Byte` to a wider type, such as `Short`, before it goes into a `DynamicValue` that you encode.
:::

## 6. Reading JSON of Unknown Shape

So far, the JSON comes from a `DynamicValue` that we build ourselves. Often the JSON comes from somewhere else, such as a webhook or a third-party API, and we do not have a case class for it. The `directDynamicMapping` annotation tells the JSON codec to read and write plain JSON for a `DynamicValue`: objects become records, arrays become sequences, strings and booleans become primitives, and `null` becomes `NoneValue`.

We annotate the schema and decode a document we have never seen before:

```scala
import zio.schema.annotation.directDynamicMapping

val plainJson: Schema[DynamicValue] = Schema.dynamicValue.annotate(directDynamicMapping())

val incoming = """{"event":"signup","user":{"name":"Ada","age":36},"tags":["new","beta"]}"""

val parsed: Either[String, DynamicValue] = JsonCodec.jsonDecoder(plainJson).decodeJson(incoming)
```

The code above does the following:

- `Schema#annotate` adds `directDynamicMapping()` to the schema, and the JSON codec reads that annotation.
- `incoming` stands in for a document from an outside service. Its shape is not described by any case class.
- `JsonCodec.jsonDecoder` builds a decoder from the annotated schema, and `JsonDecoder#decodeJson` from ZIO JSON returns the tree or an error message.

To explore the tree, we list every leaf together with its path. This function handles records and sequences, which are the containers that JSON produces:

```scala
def leaves(value: DynamicValue, path: String): List[String] =
  value match {
    case DynamicValue.Record(_, fields) =>
      fields.toList.flatMap { case (name, child) =>
        leaves(child, if (path.isEmpty) name else s"$path.$name")
      }
    case DynamicValue.Sequence(items) =>
      items.toList.zipWithIndex.flatMap { case (child, index) => leaves(child, s"$path[$index]") }
    case DynamicValue.Primitive(v, standardType) =>
      List(s"$path = $v: ${standardType.tag}")
    case DynamicValue.NoneValue =>
      List(s"$path = null")
    case other =>
      List(s"$path = $other")
  }
```

Let's print the leaves of the incoming document:

```scala
parsed.foreach(tree => leaves(tree, "").foreach(println))
// event = signup: string
// user.name = Ada: string
// user.age = 36: bigDecimal
// tags[0] = new: string
// tags[1] = beta: string
```

Notice the type of `age`. JSON has one kind of number, so the decoder reads every number as a `java.math.BigDecimal` primitive, whatever its value. This matters when we decode part of the tree into a case class: a field that holds a JSON number needs the type `BigDecimal`, because a field of type `Int` does not match a `BigDecimal` primitive.

As a final step, we pull the `user` object out of the untyped document and decode it into a typed value:

```scala
final case class User(name: String, age: BigDecimal)

object User {
  implicit val schema: Schema[User] = DeriveSchema.gen[User]
}

val userNode: Either[String, DynamicValue] =
  parsed.flatMap {
    case DynamicValue.Record(_, fields) => fields.get("user").toRight("no user field")
    case _                              => Left("not a JSON object")
  }
```

The `user` node is an ordinary record, so the `User` schema decodes it:

```scala
userNode.flatMap(User.schema.fromDynamic)
// res8: Either[String, User] = Right(value = User(name = "Ada", age = 36))
```

The document as a whole has no type, yet we read one typed piece out of it. We decode only the `user` node, so the rest of the document, whose shape we never declared, does not get in the way. Inside that node, the tree must match the case class: `Schema#fromDynamic` fails on a field that `User` does not declare, and it passes the fields to the case class in the order they appear in the tree, so the JSON object must list them in the same order as the case class parameters.

## What You've Learned

In this tutorial, you learned:

- What a `DynamicValue` is and how `Schema#toDynamic` turns a typed value into one
- How to inspect a `DynamicValue` by pattern matching on `Record`, `Enumeration`, and `Primitive`
- How to change one field of a record and turn the tree back into a typed value with `Schema#fromDynamic`
- How to encode a `DynamicValue` as self-describing JSON, and how `directDynamicMapping` reads plain JSON of unknown shape

You now have the tools to write code that works with any value that has a schema. This completes the tutorial series. The next step is to apply these tools to a real task, such as handling webhook events whose shape varies from one event to the next.

## Where to Go Next

- **Apply it to a real task:** [How to Work with Schemaless Data](../how-to/dynamic-data.md) decodes webhook events of unknown shape, redacts fields, and stores them in a binary format.
- **Look up every case and operation:** the [DynamicValue](../operations/dynamic-data-representation.md) reference lists all cases, the conversions, and the round-trip limits.
- **Rewrite trees with ready-made steps:** [Migration Operations](../reference/migration-operations.md) describes the `Migration` steps that `DynamicValue#transform` applies.
- **Convert values between versions:** [Schema Migration](../operations/schema-migration.md) shows how `Schema#migrate` uses `DynamicValue` under the hood.
