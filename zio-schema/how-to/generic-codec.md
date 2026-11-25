# How to Write a Schema-Driven Codec

> Walk the cases of a Schema to build one encoder and decoder for a key=value text format that works for every type with a schema.

In this guide, we write a codec for a simple `key=value` text format, one entry per line, that works for any type with a schema. We write it once, by pattern matching on the cases of `Schema`, and then use it for an order with nested records, a list, an optional field, and a sealed trait, and for an unrelated configuration type, without writing anything per type.

This is how the built-in codecs work: each one walks the schema of a type and reads or writes the matching shape. The [Write a Custom Codec](custom-codec.md) guide writes a codec for one fixed type instead, and [Write a Custom Deriver](custom-deriver.md) shows a macro-based alternative that builds a type class instance for each type at compile time.

## The Problem

A hand-written codec knows one type. Writing the `key=value` format for an order by hand looks like this:

```scala
final case class LineItem(sku: String, quantity: Int)
final case class Order(id: Long, customer: String, items: List[LineItem], note: Option[String])

def encodeOrder(order: Order): String = {
  val itemLines = order.items.zipWithIndex.flatMap { case (item, i) =>
    List(s"items.$i.sku=${item.sku}", s"items.$i.quantity=${item.quantity}")
  }
  val noteLines = order.note.map(note => s"note=$note").toList

  (List(s"id=${order.id}", s"customer=${order.customer}") ++ itemLines ++ noteLines).mkString("\n")
}
```

Every new type needs another such function and its decoder, and every new field means editing both. The decoder side repeats the same field names as strings, so a renamed field breaks the format without a compile error.

A `Schema` already describes the fields, cases, and element types of a value. A codec that reads the schema instead of the type handles every type that has one.

## Prerequisites

The examples only use the core module:

```scala
libraryDependencies += "dev.zio" %% "zio-schema" % "1.9.2"
```

The examples derive schemas with `DeriveSchema`, from `zio-schema-derivation`, which on Scala 2 also needs `scala-reflect` at compile time. The [Installation](../index.md#installation) section shows both lines. The [Schema Data Types](../basic-building-blocks.md) page lists every case of `Schema` that the codec matches on.

## Define the Model

We use an order whose fields cover the common shapes: primitives, a list of records, an optional field, and a sealed trait with a case class and a case object:

```scala
import zio._
import zio.stream._
import zio.schema._
import zio.schema.codec.{Codec, DecodeError}

final case class LineItem(sku: String, quantity: Int)

object LineItem {
  implicit val schema: Schema[LineItem] = DeriveSchema.gen[LineItem]
}

sealed trait Delivery

object Delivery {
  final case class Courier(street: String, city: String) extends Delivery
  case object Pickup                                     extends Delivery

  implicit val schema: Schema[Delivery] = DeriveSchema.gen[Delivery]
}

final case class Order(id: Long, customer: String, items: List[LineItem], note: Option[String], delivery: Delivery)

object Order {
  implicit val schema: Schema[Order] = DeriveSchema.gen[Order]
}

val order: Order =
  Order(1001L, "ana", List(LineItem("TEA-01", 2), LineItem("MUG-07", 1)), Some("leave at door"), Delivery.Courier("Main St 1", "Oslo"))
```

## Design the Format

Each line holds one primitive value. Its key is the path from the root to the value, with the parts joined by dots: a record field adds its name, and a list element adds its index. An optional field writes nothing when it is empty. A sealed trait writes the name of its case under the `type` key, followed by the fields of the case. The order above becomes:

```text
id=1001
customer=ana
items.0.sku=TEA-01
items.0.quantity=2
items.1.sku=MUG-07
items.1.quantity=1
note=leave at door
delivery.type=Courier
delivery.street=Main St 1
delivery.city=Oslo
```

One helper builds a key from a path and a name, so the root has no leading dot:

```scala
def key(path: String, name: String): String =
  if (path.isEmpty) name else s"$path.$name"
```

## Write the Encoder

The encoder takes a schema and a value and returns the entries for that value. It matches on the kind of schema, and for every kind that contains other values, it calls itself with the inner schema and the inner value.

Inside a generic function, the type of the value is unknown, so the function takes the schema as `Schema[_]` and the value as `Any`. Each branch casts the schema to a version with `Any` type parameters, which is safe because the value always comes from the same schema:

```scala
def entries(path: String, schema: Schema[_], value: Any): Chunk[(String, String)] =
  schema match {
    case lazySchema: Schema.Lazy[_] =>
      entries(path, lazySchema.schema, value)

    case Schema.Primitive(_, _) =>
      Chunk(path -> value.toString)

    case Schema.Optional(inner, _) =>
      value match {
        case Some(present) => entries(path, inner, present)
        case _             => Chunk.empty
      }

    case Schema.Transform(inner, _, g, _, _) =>
      g.asInstanceOf[Any => Either[String, Any]](value) match {
        case Right(underlying) => entries(path, inner, underlying)
        case Left(_)           => Chunk.empty
      }

    case sequence: Schema.Sequence[_, _, _] =>
      val seq = sequence.asInstanceOf[Schema.Sequence[Any, Any, Any]]
      seq.toChunk(value).zipWithIndex.flatMap { case (element, index) =>
        entries(key(path, index.toString), seq.elementSchema, element)
      }

    case record: Schema.Record[_] =>
      record.asInstanceOf[Schema.Record[Any]].fields.flatMap { field =>
        entries(key(path, field.fieldName), field.schema, field.get(value))
      }

    case enumSchema: Schema.Enum[_] =>
      enumSchema.asInstanceOf[Schema.Enum[Any]].cases.find(_.isCase(value)) match {
        case Some(matching) =>
          Chunk(key(path, "type") -> matching.caseName) ++ entries(path, matching.schema, matching.deconstruct(value))
        case None           => Chunk.empty
      }

    case _ =>
      Chunk(path -> value.toString)
  }

def encode[A](value: A)(implicit schema: Schema[A]): String =
  entries("", schema, value).map { case (k, v) => s"$k=$v" }.mkString("\n")
```

Each branch handles one kind of schema:

- `Schema.Lazy` wraps a schema that is built on first use. Derived schemas put one around most field schemas, and recursive types need it, so the encoder unwraps one layer and continues.
- `Schema.Primitive` is a leaf, so the encoder writes the value as text.
- `Schema.Optional` writes the inner value when there is one.
- `Schema.Transform` wraps a type that is stored as another type, such as a newtype around a `String`. Its `g` function turns the value into the underlying type, which the encoder then writes.
- `Schema.Sequence` covers `List`, `Vector`, `Chunk`, and other sequences. Its `toChunk` function lists the elements.
- `Schema.Record` covers case classes. Each field has a name, a schema, and a getter. `Field#fieldName` honours a `@fieldName` annotation.
- `Schema.Enum` covers sealed traits. `Case#isCase` finds the case of the value, and `Case#deconstruct` gives the value as that case.

Every other kind of schema, such as a `Map`, a `Set`, an `Either`, or a tuple, falls back to the value's `toString`. A complete codec gives each of them a branch of its own.

The encoder now writes the order in the format we designed:

```scala
println(encode(order))
// id=1001
// customer=ana
// items.0.sku=TEA-01
// items.0.quantity=2
// items.1.sku=MUG-07
// items.1.quantity=1
// note=leave at door
// delivery.type=Courier
// delivery.street=Main St 1
// delivery.city=Oslo
```

## Write the Decoder

The decoder walks the same schema, but builds values instead of taking them apart. It reads from a map of all the entries and returns a `Left` with a message when an entry is missing or cannot be parsed.

First, two helpers: one parses the text into a map, and one checks whether any entry exists at or below a path, which tells an optional field or a list element whether it is present:

```scala
def parse(text: String): Map[String, String] =
  text.linesIterator
    .filter(_.contains('='))
    .map { line =>
      val (k, v) = line.span(_ != '=')
      k -> v.drop(1)
    }
    .toMap

def present(path: String, props: Map[String, String]): Boolean =
  props.keys.exists(k => k == path || k.startsWith(path + "."))
```

Primitives are the only place where the decoder reads text. This version supports the standard types that the examples use and rejects the rest:

```scala
def readPrimitive(path: String, standardType: StandardType[_], text: String): Either[String, Any] = {
  val parsed: Option[Any] = standardType match {
    case StandardType.StringType => Some(text)
    case StandardType.IntType    => text.toIntOption
    case StandardType.LongType   => text.toLongOption
    case StandardType.DoubleType => text.toDoubleOption
    case StandardType.BoolType   => text.toBooleanOption
    case _                       => None
  }
  parsed.toRight(s"cannot read $path=$text as $standardType")
}
```

A last helper runs a step for each item of a chunk and stops at the first `Left`:

```scala
def collectAll[A](items: Chunk[A])(step: A => Either[String, Any]): Either[String, Chunk[Any]] =
  items.foldLeft[Either[String, Chunk[Any]]](Right(Chunk.empty)) { (acc, item) =>
    for {
      done <- acc
      next <- step(item)
    } yield done :+ next
  }
```

The decoder itself mirrors the encoder branch for branch:

```scala
def read(path: String, schema: Schema[_], props: Map[String, String]): Either[String, Any] =
  schema match {
    case lazySchema: Schema.Lazy[_] =>
      read(path, lazySchema.schema, props)

    case Schema.Primitive(standardType, _) =>
      props.get(path).toRight(s"missing key $path").flatMap(readPrimitive(path, standardType, _))

    case Schema.Optional(inner, _) =>
      if (present(path, props)) read(path, inner, props).map(Some(_)) else Right(None)

    case Schema.Transform(inner, f, _, _, _) =>
      read(path, inner, props).flatMap(f.asInstanceOf[Any => Either[String, Any]])

    case sequence: Schema.Sequence[_, _, _] =>
      val seq  = sequence.asInstanceOf[Schema.Sequence[Any, Any, Any]]
      val size = Iterator.from(0).takeWhile(index => present(key(path, index.toString), props)).size
      collectAll(Chunk.fromIterable(0 until size))(index => read(key(path, index.toString), seq.elementSchema, props))
        .map(seq.fromChunk)

    case record: Schema.Record[_] =>
      collectAll(record.fields)(field => read(key(path, field.fieldName), field.schema, props))
        .flatMap(values => Unsafe.unsafe { implicit unsafe => record.construct(values) })

    case enumSchema: Schema.Enum[_] =>
      val typeKey = key(path, "type")
      for {
        name     <- props.get(typeKey).toRight(s"missing key $typeKey")
        matching <- enumSchema.caseOf(name).toRight(s"unknown case $name at $typeKey")
        value    <- read(path, matching.schema, props)
      } yield matching.asInstanceOf[Schema.Case[Any, Any]].construct(value)

    case other =>
      Left(s"unsupported schema at $path: ${other.getClass.getSimpleName}")
  }

def decode[A](text: String)(implicit schema: Schema[A]): Either[String, A] =
  read("", schema, parse(text)).map(_.asInstanceOf[A])
```

The new calls are the constructors on the schema: `Schema.Sequence#fromChunk` builds the collection from its elements, `Schema.Record#construct` builds a case class from its field values in field order, and `Schema.Case#construct` turns a case value into the sealed trait. `Schema.Record#construct` takes an implicit `Unsafe`, because it receives the field values untyped, as a `Chunk[Any]`; the decoder makes sure each value has the right type by reading each field with the field's own schema. `Schema.Enum#caseOf` finds a case by the name the encoder wrote.

The decoder reads the encoded order back:

```scala
decode[Order](encode(order)) == Right(order)
// res2: Boolean = true
```

A missing entry or a value of the wrong type gives a `Left` that names the key:

```scala
decode[LineItem]("sku=TEA-01")
// res3: Either[String, LineItem] = Left(value = "missing key quantity")
decode[LineItem]("sku=TEA-01\nquantity=two")
// res4: Either[String, LineItem] = Left(
//   value = "cannot read quantity=two as int"
// )
```

## Use It for Any Type

The codec contains no reference to `Order`. Any type with a schema works, such as a configuration class written long after the codec:

```scala
final case class ServerConfig(host: String, port: Int, debug: Boolean, tags: Vector[String])

object ServerConfig {
  implicit val schema: Schema[ServerConfig] = DeriveSchema.gen[ServerConfig]
}

val config: ServerConfig = ServerConfig("localhost", 8080, debug = true, Vector("blue", "eu"))
```

The same two functions encode and decode it:

```scala
println(encode(config))
// host=localhost
// port=8080
// debug=true
// tags.0=blue
// tags.1=eu

decode[ServerConfig](encode(config)) == Right(config)
// res6: Boolean = true
```

The case object of `Delivery` also round-trips, since its schema is a record with no fields:

```scala
decode[Order](encode(order.copy(note = None, delivery = Delivery.Pickup)))
// res7: Either[String, Order] = Right(
//   value = Order(
//     id = 1001L,
//     customer = "ana",
//     items = List(
//       LineItem(sku = "TEA-01", quantity = 2),
//       LineItem(sku = "MUG-07", quantity = 1)
//     ),
//     note = None,
//     delivery = Pickup
//   )
// )
```

## Package It as a Codec

To plug the format into code that expects ZIO Schema's `Codec` interface, we wrap the two functions in a class that takes the schema implicitly. The stream methods treat each element as one whole encoded value, like the hand-written codec in [Write a Custom Codec](custom-codec.md):

```scala
final class KeyValueCodec[A](implicit schema: Schema[A]) extends Codec[String, String, A] {

  override def encode(value: A): String =
    entries("", schema, value).map { case (k, v) => s"$k=$v" }.mkString("\n")

  override def decode(text: String): Either[DecodeError, A] =
    read("", schema, parse(text))
      .map(_.asInstanceOf[A])
      .left
      .map(message => DecodeError.ReadError(Cause.empty, message))

  override def streamEncoder: ZPipeline[Any, Nothing, A, String] =
    ZPipeline.map(encode)

  override def streamDecoder: ZPipeline[Any, DecodeError, String, A] =
    ZPipeline.mapZIO(text => ZIO.fromEither(decode(text)))
}
```

One class now serves every type:

```scala
new KeyValueCodec[ServerConfig].decode("host=example.org\nport=443\ndebug=false")
// res8: Either[DecodeError, ServerConfig] = Right(
//   value = ServerConfig(
//     host = "example.org",
//     port = 443,
//     debug = false,
//     tags = Vector()
//   )
// )
```

## Choose Between Schema and DynamicValue

Instead of walking the schema and the value together, an encoder can first turn the value into a `DynamicValue` with `Schema#toDynamic` and walk that tree. The tree already holds the field names, case names, and elements, so the encoder needs no casts:

```scala
def dynamicEntries(path: String, value: DynamicValue): Chunk[(String, String)] =
  value match {
    case DynamicValue.Primitive(v, _)               => Chunk(path -> v.toString)
    case DynamicValue.SomeValue(inner)              => dynamicEntries(path, inner)
    case DynamicValue.NoneValue                     => Chunk.empty
    case DynamicValue.Sequence(values)              =>
      values.zipWithIndex.flatMap { case (v, index) => dynamicEntries(key(path, index.toString), v) }
    case DynamicValue.Record(_, fields)             =>
      Chunk.fromIterable(fields).flatMap { case (name, v) => dynamicEntries(key(path, name), v) }
    case DynamicValue.Enumeration(_, (caseName, v)) =>
      Chunk(key(path, "type") -> caseName) ++ dynamicEntries(path, v)
    case other                                      => Chunk(path -> other.toString)
  }
```

It produces the same entries for the order:

```scala
dynamicEntries("", Order.schema.toDynamic(order)) == entries("", Order.schema, order)
// res9: Boolean = true
```

The `DynamicValue` route is shorter and fine for tools, logs, and debugging output. Walking the schema directly avoids building the intermediate tree for every value, which matters in a codec on a hot path. A decoder follows the schema in either case, because it starts from text and has no value to convert: only the schema says which primitive type each entry holds.

## Where to Go Next

- [Schema Data Types](../basic-building-blocks.md) documents every case of `Schema` with its fields, including the ones this codec leaves to the fallback branch.
- [How to Define Schemas for Recursive Types](recursive-schemas.md) explains the `Schema.Lazy` nodes that the codec unwraps.
- [Codec Core Types](../reference/codec-core.md) describes the `Codec`, `Encoder`, and `Decoder` traits.
- [Write a Custom Deriver](custom-deriver.md) builds a type class instance per type at compile time instead of interpreting the schema at runtime.
