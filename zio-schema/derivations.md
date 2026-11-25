# Introduction to ZIO Schema Codecs

> Once we have a schema for a type, we can derive a codec for that type. A codec encodes a value of some type `A` to a format, such as JSON or a binary protocol, and decodes it back. ZIO Schema ships one module per format, and every module derives its codec from the same `Schema[A]`, so one data model can be written as JSON, Protobuf, Avro, and the other formats without extra code per format.

## The Codec Model

A codec in ZIO Schema is typed by the value it handles and by the shape of its encoded form. `Encoder` and `Decoder` each have a single-value method and a streaming method, and `Codec` combines them:

```scala
import zio.Chunk
import zio.schema.codec.DecodeError
import zio.stream.ZPipeline

trait Encoder[Whole, Element, -A] {
  def encode(value: A): Whole
  def streamEncoder: ZPipeline[Any, Nothing, A, Element]
}

trait Decoder[Whole, Element, +A] {
  def decode(whole: Whole): Either[DecodeError, A]
  def streamDecoder: ZPipeline[Any, DecodeError, Element, A]
}

trait Codec[Whole, Element, A] extends Encoder[Whole, Element, A] with Decoder[Whole, Element, A]

trait BinaryCodec[A] extends Codec[Chunk[Byte], Byte, A]
```

`Whole` is the type of one encoded value, and `Element` is the type of one item in an encoded stream. Most format modules return a `BinaryCodec[A]`, which encodes a value to a `Chunk[Byte]` and turns a stream of values into a stream of bytes. The [Codec Core Types](../../reference/codec-core.md) page describes each trait and the `DecodeError` cases.

To see the model at work, we derive a JSON codec for a case class, encode a value, and decode the bytes back:

```scala
import zio.Chunk
import zio.schema.codec.{BinaryCodec, JsonCodec}
import zio.schema.{DeriveSchema, Schema}

final case class Person(name: String, age: Int)

object Person {
  implicit val schema: Schema[Person] = DeriveSchema.gen[Person]
}

val codec: BinaryCodec[Person] = JsonCodec.schemaBasedBinaryCodec[Person]
val bytes: Chunk[Byte]         = codec.encode(Person("John", 42))
```

The encoded bytes hold JSON text, and decoding them gives the original value:

```scala
println(new String(bytes.toArray, "UTF-8"))
// {"name":"John","age":42}
codec.decode(bytes)
// res2: Either[zio.schema.codec.DecodeError, Person] = Right(
//   value = Person(name = "John", age = 42)
// )
```

## Available Codecs

Each format lives in its own module, so a project depends only on the formats it uses. The following table lists the modules, the entry point that derives a codec, the platforms each module is published for, and what the codec returns:

| Format      | Module                | Entry point                                                  | Platforms             | Codec type                          |
| ----------- | --------------------- | ------------------------------------------------------------ | --------------------- | ----------------------------------- |
| JSON        | `zio-schema-json`     | `JsonCodec.schemaBasedBinaryCodec`, `JsonCodec.jsonCodec`    | JVM, Scala.js, Native | `BinaryCodec`, `zio.json.JsonCodec` |
| Protobuf    | `zio-schema-protobuf` | `ProtobufCodec.protobufCodec`                                | JVM, Scala.js, Native | `BinaryCodec`                       |
| XML         | `zio-schema-xml`      | `XmlCodec.schemaBasedBinaryCodec`                            | JVM, Scala.js, Native | `BinaryCodec`                       |
| Apache Avro | `zio-schema-avro`     | `AvroCodec.schemaBasedBinaryCodec`, `AvroSchemaCodec.encode` | JVM                   | `AvroCodec.ExtendedBinaryCodec`     |
| BSON        | `zio-schema-bson`     | `BsonSchemaCodec.bsonCodec`                                  | JVM                   | `zio.bson.BsonCodec`                |
| MessagePack | `zio-schema-msg-pack` | `MessagePackCodec.messagePackCodec`                          | JVM                   | `BinaryCodec`                       |
| Thrift      | `zio-schema-thrift`   | `ThriftCodec.thriftCodec`                                    | JVM                   | `BinaryCodec`                       |

All entry points live in the `zio.schema.codec` package, except `XmlCodec`, which lives in `zio.schema.codec.xml`. The BSON codec is the only one that is not a `BinaryCodec`: it returns a [zio-bson](https://github.com/zio/zio-bson) `BsonCodec`, which reads and writes BSON values and documents, and it has no stream pipelines.

The pages for each format cover installation, configuration, and format-specific annotations:

- [JSON](json.md)
- [Protobuf](protobuf.md)
- [XML](xml.md)
- [Apache Avro](avro.md)
- [BSON](bson.md)
- [MessagePack](message-pack.md)
- [Apache Thrift](thrift.md)

## Installation

Each format is a separate dependency. On the JVM, add the module with `%%`, for example:

```scala
libraryDependencies += "dev.zio" %% "zio-schema-json" % "1.9.2"
```

The JSON, Protobuf, and XML modules are also published for Scala.js and Scala Native. In a Scala.js or Scala Native project, declare them with `%%%` so sbt picks the artifact for the platform:

```scala
libraryDependencies += "dev.zio" %%% "zio-schema-json" % "1.9.2"
```

The Avro, BSON, MessagePack, and Thrift modules depend on Java libraries and are published for the JVM only.

## Annotation Support by Codec

The annotations in `zio.schema.annotation`, described on the [Schema Annotations](../../schema-annotations.md) page, are hints that each codec may or may not read. A format that identifies fields and cases by number or by position has no use for names, and some codecs ignore an annotation that their format could support. The following table shows what each codec does with each annotation:

| Annotation          | JSON   | XML     | BSON    | Avro    | MessagePack | Protobuf | Thrift     |
| ------------------- | ------ | ------- | ------- | ------- | ----------- | -------- | ---------- |
| `fieldName`         | Yes    | Yes     | Yes (1) | Yes (1) | Yes (1)     | -        | -          |
| `fieldNameAliases`  | Decode | No      | Decode  | No      | No          | -        | -          |
| `caseName`          | Yes    | Yes     | Yes     | No      | -           | -        | -          |
| `caseNameAliases`   | Decode | Decode  | Decode  | No      | -           | -        | -          |
| `discriminatorName` | Yes    | Yes (2) | Yes     | No      | No          | No       | No         |
| `noDiscriminator`   | Yes    | Yes     | Yes     | No      | No          | No       | No         |
| `simpleEnum`        | Yes    | Yes     | Yes (3) | No (4)  | No          | No       | No         |
| `transientField`    | Yes    | Yes     | Yes     | No      | No          | Yes (5)  | Yes (5)    |
| `transientCase`     | Yes    | Decode  | Yes     | No      | No          | No       | No         |
| `optionalField`     | Yes    | Decode  | Decode  | No      | No          | No       | Decode     |
| `fieldDefaultValue` | Decode | Decode  | Decode  | No (6)  | No          | Decode   | Decode     |
| `rejectExtraFields` | Yes    | No      | Yes     | No      | Always (7)  | No (8)   | Always (7) |

In the table, "Yes" means the codec honours the annotation when it encodes and when it decodes, "Decode" means only the decoder reads it, "No" means the codec ignores it, "Always" means the codec behaves that way whether or not the annotation is present, and "-" means the format never writes that name, because it identifies fields or cases by number or position. The numbered notes explain the special cases:

1. These codecs use the field name that `DeriveSchema` stores in the schema, which is the `fieldName` value. A schema built by hand that only carries the annotation keeps the original name.
2. The XML codec writes the discriminator as a child element, not as an attribute.
3. The BSON codec writes any sealed trait whose cases are all case objects as a string, with or without the annotation.
4. Use `@AvroAnnotations.avroEnum` to write an Avro enum.
5. The field must come after all non-transient fields. A `transientField` placed before another field shifts the field numbers, so the value no longer decodes. The Protobuf decoder fills the field only from a Scala default parameter or `@fieldDefaultValue`, so the field needs one of them.
6. Use `@AvroAnnotations.default` to put a default into the Avro schema.
7. The MessagePack decoder always fails on a field the type does not declare, and on a missing field. The Thrift decoder fails on a field id higher than the number of fields in the type.
8. The Protobuf decoder skips field numbers it does not know, which keeps old readers working with newer writers.

The JSON codec has one more interaction to keep in mind: `JsonCodec.Configuration#fieldNameFormat` is applied to every field, including fields renamed with `@fieldName`. The [JSON](json.md) page explains how to keep an explicit name in that case.

None of the codecs runs the validation rules attached with `@validate` while decoding. Call `Schema#validate` on the decoded value when the input is untrusted.

## Streaming

Every `BinaryCodec` has a `BinaryCodec#streamEncoder` and a `BinaryCodec#streamDecoder`, but only the JSON codec, in its default newline-delimited format, splits a byte stream into separate values reliably. The binary codecs decode each upstream chunk as one complete message. The [How to Encode and Decode Streams](../../integration-with-zio-streams.md) guide shows how to stream each format safely.
