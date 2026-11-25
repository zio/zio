# XML Codecs

> XML (Extensible Markup Language) is a widely used markup language for representing structured data. ZIO Schema provides the `zio-schema-xml` module that derives XML codecs from a ZIO Schema. The XML codec does not depend on an XML library: it has its own XML AST (`Xml`), a hand-written parser (`XmlReader`), and a writer (`XmlWriter`). It runs on the JVM, Scala.js, and Scala Native.

## Introduction

## Installation

To start using XML codecs in ZIO Schema, add the following dependency to your `build.sbt` file:

```scala
libraryDependencies += "dev.zio" %% "zio-schema-xml" % "1.9.2"
```

For a Scala.js or Scala Native project, use `%%%` instead:

```scala
libraryDependencies += "dev.zio" %%% "zio-schema-xml" % "1.9.2"
```

## BinaryCodec

The `XmlCodec` object inside the `zio.schema.codec.xml` package provides `XmlCodec.schemaBasedBinaryCodec`, which allows us to derive XML codecs from a ZIO Schema. The second overload takes an `XmlCodec.Configuration`, described in [Configuration](#configuration):

```scala
object XmlCodec {
  implicit def schemaBasedBinaryCodec[A](implicit schema: Schema[A]): BinaryCodec[A]
  def schemaBasedBinaryCodec[A](config: Configuration)(implicit schema: Schema[A]): BinaryCodec[A]
}
```

## Example: Basic Encoding/Decoding

The following program encodes a `Person` to XML and decodes it back:

```scala
import zio._
import zio.schema.codec.BinaryCodec
import zio.schema.codec.xml.XmlCodec
import zio.schema.{DeriveSchema, Schema}

case class Person(name: String, age: Int)

object Person {
  implicit val schema: Schema[Person] =
    DeriveSchema.gen
  implicit val xmlCodec: BinaryCodec[Person] =
    XmlCodec.schemaBasedBinaryCodec(schema)
}

object Main extends ZIOAppDefault {
  def run = for {
    _ <- ZIO.debug("XML Codec Example:")
    person: Person = Person("John", 42)
    encoded: Chunk[Byte] = Person.xmlCodec.encode(person)
    _ <- ZIO.debug(s"person object encoded to XML: ${new String(encoded.toArray)}")
    decoded <- ZIO.fromEither(Person.xmlCodec.decode(encoded))
    _ <- ZIO.debug(s"XML decoded to Person class: $decoded")
  } yield ()
}
```

The encoded `Person` has the following XML structure:

```xml
<Person><name>John</name><age>42</age></Person>
```

## XML Layout

The codec maps each kind of schema to a fixed XML shape. A record becomes an element named after the type, with one child element per field. To see the shapes for collections, maps, options, and sealed traits, we encode an order that uses all of them:

```scala
import zio.schema.codec.xml.XmlCodec
import zio.schema.{DeriveSchema, Schema}

sealed trait Payment

object Payment {
  final case class Card(number: String) extends Payment
  final case class Cash(amount: Double) extends Payment

  implicit val schema: Schema[Payment] = DeriveSchema.gen
}

final case class Order(
  id: Int,
  items: List[String],
  prices: Map[String, Double],
  note: Option[String],
  payment: Payment
)

object Order {
  implicit val schema: Schema[Order] = DeriveSchema.gen
}

def toXml(order: Order): String =
  new String(XmlCodec.schemaBasedBinaryCodec[Order].encode(order).toArray, "UTF-8")
```

The output shows each shape in turn:

```scala
println(toXml(Order(1, List("apple", "pear"), Map("apple" -> 1.5), None, Payment.Card("4242"))))
// <Order><id>1</id><items><item>apple</item><item>pear</item></items><prices><entry><key>apple</key><value>1.5</value></entry></prices><note/><payment><Card><number>4242</number></Card></payment></Order>
```

The shapes follow these rules:

- A sequence or set becomes an element with one `<item>` child per element.
- A map becomes an element with one `<entry>` child per pair, each holding a `<key>` and a `<value>`.
- An `Option` field that is `None` is left out, and `Some` is written as the value itself.
- An `Either` becomes a `<Left>` or `<Right>` element, and a tuple becomes an element with `<_1>` and `<_2>` children.
- A sealed trait becomes a wrapper element, named after the field or, at the top level, after the sealed trait, that holds one element named after the case. With `@discriminatorName("type")`, the case element also gets a `<type>` child holding the case name, and a top-level value has no wrapper. With `@noDiscriminator`, a top-level value also has no wrapper, and the decoder tries each case in turn. A sealed trait whose cases are all case objects is written as the case name in plain text.

Field names come from `@fieldName` and case names from `@caseName`. The decoder also accepts the names in `@caseNameAliases`, but not those in `@fieldNameAliases`. It ignores child elements the type does not declare, so `@rejectExtraFields` has no effect. The [annotation support table](index.md#annotation-support-by-codec) compares this with the other codecs.

## XML Annotations

The `zio.schema.codec.xml` package has two annotations that only the XML codec reads. They control attributes and namespace prefixes.

### @xmlAttribute

The `@xmlAttribute` annotation marks a field to be encoded as an XML attribute instead of a child element. It works for fields of a primitive type, such as `String` or `Int`, and for an `Option` of a primitive type:

```scala
import zio.schema.codec.xml.{XmlCodec, xmlAttribute}
import zio.schema.{DeriveSchema, Schema}

case class Person(@xmlAttribute id: Int, name: String)

object Person {
  implicit val schema: Schema[Person] = DeriveSchema.gen
}
```

The `id` field becomes an attribute of the `Person` element:

```scala
println(new String(XmlCodec.schemaBasedBinaryCodec[Person].encode(Person(42, "Alice")).toArray, "UTF-8"))
// <Person id="42"><name>Alice</name></Person>
```

A field of any other type, such as a case class or a list, is silently left out when it carries `@xmlAttribute`, so the value does not round-trip.

### @xmlNamespace

The `@xmlNamespace(uri, prefix)` annotation puts a namespace prefix on the element of a record. On a field that is also an `@xmlAttribute`, it puts the prefix on the attribute name. It has no effect on other fields:

```scala
import zio.schema.codec.xml.{XmlCodec, xmlNamespace}
import zio.schema.{DeriveSchema, Schema}

@xmlNamespace("http://example.com/ns", Some("ex"))
case class Item(name: String)

object Item {
  implicit val schema: Schema[Item] = DeriveSchema.gen
}
```

The element name carries the `ex` prefix:

```scala
println(new String(XmlCodec.schemaBasedBinaryCodec[Item].encode(Item("Alice")).toArray, "UTF-8"))
// <ex:Item><name>Alice</name></ex:Item>
```

:::caution
The writer does not emit an `xmlns` declaration, so the output above uses the `ex` prefix without declaring it, and a namespace without a prefix is not written at all. The decoder matches element and attribute names by their local part and ignores prefixes and namespace URIs. If a consumer needs a well-formed namespaced document, add the `xmlns` declaration to the root element yourself after encoding.
:::

## Configuration

`XmlCodec.Configuration` combines a `WriterConfig`, which shapes the output of the encoder, and a `ReaderConfig`, which sets limits for the decoder. Pass it to `XmlCodec.schemaBasedBinaryCodec(config)`.

### WriterConfig

The `WriterConfig` allows customization of XML output:

- `indentStep`: Number of spaces for indentation (0 for compact output, the default).
- `includeDeclaration`: Whether to start the output with an XML declaration, `<?xml version="1.0" encoding="..."?>` (default: `false`).
- `encoding`: The encoding name written into the XML declaration (default: `"UTF-8"`).

The encoder always produces UTF-8 bytes. `encoding` only changes the label in the declaration, so set it to anything other than `"UTF-8"` only if you convert the bytes to that encoding yourself.

The `WriterConfig` companion object has three presets:

```scala
object WriterConfig {
  val default: WriterConfig         // No indentation, no declaration
  val pretty: WriterConfig          // 2-space indentation
  val withDeclaration: WriterConfig // Includes XML declaration
}
```

### ReaderConfig

The `ReaderConfig` provides safety limits for parsing:

- `maxDepth`: Maximum nesting depth (default: 1000).
- `maxAttributes`: Maximum attributes per element (default: 1000).
- `maxTextLength`: Maximum text content length (default: 10,000,000).
- `preserveWhitespace`: Whether to preserve whitespace in text content (default: `false`).

Lower limits protect a service that decodes untrusted XML from deeply nested or oversized input.

### Configuration Example

The following codec writes indented XML with a declaration, and rejects input nested more than 32 levels deep:

```scala
import zio.schema.codec.xml.{ReaderConfig, WriterConfig, XmlCodec}
import zio.schema.{DeriveSchema, Schema}

case class Person(name: String, age: Int)

object Person {
  implicit val schema: Schema[Person] = DeriveSchema.gen
}

val config = XmlCodec.Configuration(
  writerConfig = WriterConfig.pretty.copy(includeDeclaration = true),
  readerConfig = ReaderConfig(maxDepth = 32)
)

val xmlCodec = XmlCodec.schemaBasedBinaryCodec[Person](config)
```

The encoded person is indented and starts with the declaration:

```scala
println(new String(xmlCodec.encode(Person("John", 42)).toArray, "UTF-8"))
// <?xml version="1.0" encoding="UTF-8"?>
// <Person>
//   <name>John</name>
//   <age>42</age>
// </Person>
```

## Streaming

:::caution
The `BinaryCodec#streamDecoder` of the XML codec parses each upstream chunk as one complete XML document. It does not parse incrementally, so it cannot split a large document or a sequence of documents into values. A chunk that holds part of a document fails to decode, and when a chunk holds several documents, only the first is decoded.
:::

To process many XML values, encode each one with `BinaryCodec#encode`, send each as its own message, and decode each message with `BinaryCodec#decode`. To read one large XML document, decode the whole document into a type that holds a collection. The [How to Encode and Decode Streams](../../integration-with-zio-streams.md) guide shows the per-message approach.

## Limitations

The XML codec handles primitives, records, enums, collections, maps, `Either`, tuples, `Option`, and recursive types, with these limits:

- In text, the writer escapes `&`, `<`, and `>`. In attribute values, it also escapes `"` and `'`.
- The reader accepts CDATA sections and the five predefined entities (`&amp;`, `&lt;`, `&gt;`, `&quot;`, `&apos;`). It rejects numeric character references such as `&#233;`.
- The reader accepts only ASCII letters, digits, `_`, `-`, `.`, and `:` in element and attribute names, so a field name with other characters encodes but does not decode.
- An empty `String` encoded as a top-level value cannot be decoded back.
- When a `Schema.Transform` cannot convert a value back during encoding, the encoder writes empty text instead of failing.

A failed parse returns a `DecodeError.ReadError` whose message includes the line and column of the problem.
