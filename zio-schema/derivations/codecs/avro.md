# Apache Avro Codecs

> Apache Avro is a popular data serialization format used in distributed systems, particularly in the Apache Hadoop and Apache Kafka ecosystems. An Avro payload is always read and written together with an Avro schema, which is a JSON document that describes the data. The `zio-schema-avro` module derives both from a ZIO Schema: it converts a `Schema[A]` to an Avro schema, and it encodes and decodes values in Avro's binary format.

## Introduction

## Installation

To use the Avro codecs, add the following dependency to your `build.sbt` file. The module is published for the JVM only:

```scala
libraryDependencies += "dev.zio" %% "zio-schema-avro" % "1.9.2"
```

## Codecs

The module has two codecs, both in the `zio.schema.codec` package. `AvroSchemaCodec` converts a `Schema[A]` to an Avro schema in JSON format, and an Avro schema back to a `Schema.GenericRecord`. `AvroCodec` encodes and decodes values in Avro's binary format.

### AvroSchemaCodec

The `AvroSchemaCodec` provides methods to encode a `Schema[_]` to an Avro JSON schema and to decode an Avro JSON schema to a `Schema[_]` ([`Schema.GenericRecord`](../../operations/dynamic-data-representation.md)):

```scala
trait AvroSchemaCodec {
  def encode(schema: Schema[_]): scala.util.Either[String, String]
  def decode(bytes: Chunk[Byte]): scala.util.Either[String, Schema[_]]
}
```

The `AvroSchemaCodec.encode` method takes a `Schema[_]` and returns an `Either[String, String]` where the `Right` side contains the Avro schema in JSON format.

The `AvroSchemaCodec.decode` method takes a `Chunk[Byte]` that holds the Avro JSON schema as UTF-8 text, and returns an `Either[String, Schema[_]]` where the `Right` side contains the ZIO Schema in `GenericRecord` format.

The following program converts the `Person` schema to Avro and back:

```scala
import zio._
import zio.schema.Schema
import zio.schema.DeriveSchema
import zio.schema.codec.AvroSchemaCodec

case class Person(name: String, age: Int)

object Person {
  implicit val schema: Schema[Person] = DeriveSchema.gen
}

object Main extends ZIOAppDefault {
  def run =
    for {
      _          <- ZIO.debug("AvroSchemaCodec Example:")
      avroSchema <- ZIO.fromEither(AvroSchemaCodec.encode(Person.schema))
      _ <- ZIO.debug(s"The person schema in Avro Schema JSON format: $avroSchema")
      avroSchemaBinary = Chunk.fromArray(avroSchema.getBytes)
      zioSchema <- ZIO.fromEither(AvroSchemaCodec.decode(avroSchemaBinary))
      _ <- ZIO.debug(s"The person schema in ZIO Schema GenericRecord format: $zioSchema")
    } yield ()
}
```

The program prints the following lines:

```
AvroSchemaCodec Example:
The person schema in Avro Schema JSON format: {"type":"record","name":"Person","fields":[{"name":"name","type":"string"},{"name":"age","type":"int"}]}
The person schema in ZIO Schema GenericRecord format: GenericRecord(Nominal(Chunk(),Chunk(),Person),Field(name,Primitive(string,Chunk())) :*: Field(age,Primitive(int,Chunk())) :*: Empty,Chunk(name(Person)))
```

The program converts the `Schema[Person]` to the Avro schema JSON format, and then converts it back to the ZIO Schema `GenericRecord` format.

When you work with the Apache Avro Java library directly, `AvroSchemaCodec.encodeToApacheAvro` returns an `org.apache.avro.Schema` instead of its JSON text, and `AvroSchemaCodec.decodeFromApacheAvro` converts an `org.apache.avro.Schema` to a ZIO Schema.

### AvroCodec

We can create a codec for any type `A` that has a `Schema[A]` instance using `AvroCodec.schemaBasedBinaryCodec`. It returns an `AvroCodec.ExtendedBinaryCodec[A]`, which is a `BinaryCodec[A]` with extra methods for Avro `GenericRecord` values:

```scala
object AvroCodec {
  trait ExtendedBinaryCodec[A] extends BinaryCodec[A] {
    def encodeGenericRecord(value: A)(implicit schema: Schema[A]): GenericData.Record
    def decodeGenericRecord(value: GenericRecord)(implicit schema: Schema[A]): Either[DecodeError, A]
  }

  implicit def schemaBasedBinaryCodec[A](implicit schema: Schema[A]): ExtendedBinaryCodec[A]
}
```

The following program encodes a `Person` to Avro binary and decodes it back:

```scala
import zio._
import zio.schema.Schema
import zio.schema.DeriveSchema
import zio.schema.codec.{AvroCodec, BinaryCodec}

case class Person(name: String, age: Int)

object Person {
  implicit val schema: Schema[Person] = DeriveSchema.gen
  implicit val binaryCodec: BinaryCodec[Person] =
    AvroCodec.schemaBasedBinaryCodec[Person]
}

object Main extends ZIOAppDefault {
  def run =
    for {
      _ <- ZIO.debug("AvroCodec Example:")
      encodedPerson = Person.binaryCodec.encode(Person("John", 42))
      _ <- ZIO.debug(s"encoded person object: ${toHex(encodedPerson)}")
      decodedPerson <- ZIO.fromEither(
        Person.binaryCodec.decode(encodedPerson)
      )
      _ <- ZIO.debug(s"decoded person object: $decodedPerson")
    } yield ()

  def toHex(bytes: Chunk[Byte]): String =
    bytes.map("%02x".format(_)).mkString(" ")
}
```

The program prints the following lines:

```
AvroCodec Example:
encoded person object: 08 4a 6f 68 6e 54
decoded person object: Person(John,42)
```

The bytes hold only the values, not the schema, so the reader must use the same Avro schema as the writer, or a compatible one.

:::caution
The `BinaryCodec#decode` method of the Avro codec throws an exception, such as an `EOFException`, on malformed or truncated input, instead of returning a `Left`. When the bytes come from outside your program, call it inside `ZIO.attempt`, or catch the exception and turn it into a `DecodeError`.
:::

To decode untrusted bytes without exceptions, wrap the call and turn a thrown exception into a `DecodeError.ReadError`:

```scala
import scala.util.control.NonFatal

import zio.{Cause, Chunk}
import zio.schema.codec.{BinaryCodec, DecodeError}

def safeDecode[A](codec: BinaryCodec[A], bytes: Chunk[Byte]): Either[DecodeError, A] =
  try codec.decode(bytes)
  catch {
    case NonFatal(e) => Left(DecodeError.ReadError(Cause.fail(e), e.getMessage))
  }
```

### Generic Records

Many Avro tools, such as the Kafka Avro serializers, exchange `org.apache.avro.generic.GenericRecord` values instead of bytes. `ExtendedBinaryCodec#encodeGenericRecord` converts a value to a `GenericData.Record`, and `ExtendedBinaryCodec#decodeGenericRecord` converts a `GenericRecord` back to a value:

```scala
import org.apache.avro.generic.GenericData
import zio.schema.codec.AvroCodec
import zio.schema.{DeriveSchema, Schema}

final case class Person(name: String, age: Int)

object Person {
  implicit val schema: Schema[Person] = DeriveSchema.gen
}

val avroCodec: AvroCodec.ExtendedBinaryCodec[Person] = AvroCodec.schemaBasedBinaryCodec[Person]

val record: GenericData.Record = avroCodec.encodeGenericRecord(Person("John", 42))
```

The record prints as Avro's JSON view of the value, and decoding it gives the `Person` back:

```scala
println(record)
// {"name": "John", "age": 42}
avroCodec.decodeGenericRecord(record)
// res5: Either[zio.schema.codec.DecodeError, Person] = Right(
//   value = Person(name = "John", age = 42)
// )
```

## Annotations

The Apache Avro specification supports some attributes for describing the data which are not part of the default ZIO Schema. To support these extra metadata, we can use annotations defined in the `zio.schema.codec.AvroAnnotations` object. They change the generated Avro schema.

The following annotations go on a case class, a sealed trait, or a field:

- `@AvroAnnotations.name(name: String)`: changes the name of a record, an enum, or a field.
- `@AvroAnnotations.namespace(namespace: String)`: sets the namespace of a record or an enum.
- `@AvroAnnotations.doc(doc: String)`: adds documentation to a record, an enum, or a field.
- `@AvroAnnotations.aliases(aliases: Set[String])`: holds the aliases of a record or a field. `AvroSchemaCodec.decode` adds it when the Avro schema it reads has aliases; `AvroSchemaCodec.encode` does not write aliases into the generated schema.
- `@AvroAnnotations.avroEnum()`: writes a sealed trait as an Avro enum. Every case must be a case object; otherwise the sealed trait is written as a union of records.
- `@AvroAnnotations.default(javaDefaultObject: java.lang.Object)`: sets the default value of a field in the Avro schema, which Avro readers use when the writer's schema lacks the field.
- `@AvroAnnotations.fieldOrder(fieldOrderType: FieldOrderType)`: sets the sort order of a field, as `FieldOrderType.Ascending` (the default), `FieldOrderType.Descending`, or `FieldOrderType.Ignore`.

The annotations that change how a single value is stored are read from the primitive schema of the value's type, not from the field, so we attach them with `Schema#annotate`, for example `Schema.primitive[java.math.BigDecimal].annotate(AvroAnnotations.scale(10))`. The same goes for `AvroAnnotations.error` and `AvroAnnotations.formatToString`, which are objects and cannot be written as `@` annotations:

- `AvroAnnotations.scale(scale: Int = 24)` and `AvroAnnotations.precision(precision: Int = 48)`: set the scale and precision of a decimal.
- `AvroAnnotations.decimal(decimalType: DecimalType)`: sets how a `BigInteger` or `BigDecimal` is stored, as `DecimalType.Bytes` (the default) or `DecimalType.Fixed(size)`.
- `AvroAnnotations.bytes(bytesType: BytesType)`: sets how a binary (`Chunk[Byte]`) value is stored, as `BytesType.Bytes` (the default) or `BytesType.Fixed(size, name)`.
- `AvroAnnotations.formatToString`: writes a `LocalDate`, `LocalTime`, `LocalDateTime`, or `Instant` as a string instead of an Avro logical type.
- `AvroAnnotations.timeprecision(timeprecisionType: TimePrecisionType)`: sets the precision of the Avro `time`, `timestamp`, and `local-timestamp` logical types, as `TimePrecisionType.Millis` (the default) or `TimePrecisionType.Micros`.
- `AvroAnnotations.error`: on a record schema, writes the record as an Avro error record.

The Avro codec ignores most of the ZIO Schema annotations in `zio.schema.annotation`, such as `@caseName`, `@transientField`, and `@fieldDefaultValue`. It honours `@fieldName` through derivation. The [annotation support table](index.md#annotation-support-by-codec) lists what each codec reads.

For example, to change the name of a record in the Avro schema, we can use the `AvroAnnotations.name` annotation:

```scala
import zio.schema.codec.{AvroAnnotations, AvroSchemaCodec}
import zio.schema.{DeriveSchema, Schema}

@AvroAnnotations.name("User")
case class Person(name: String, age: Int)

object Person {
  implicit val schema: Schema[Person] = DeriveSchema.gen
}
```

When we generate the Avro schema for the `Person` class, the name of the record is `User` instead of `Person`:

```scala
AvroSchemaCodec.encode(Person.schema).foreach(println)
// {"type":"record","name":"User","fields":[{"name":"name","type":"string"},{"name":"age","type":"int"}]}
```

:::caution
`@AvroAnnotations.name` on a **field** renames the field in the Avro schema, but `AvroCodec` still writes values under the Scala field name, so `BinaryCodec#encode` throws an `AvroRuntimeException` ("Not a valid schema field"). To rename a field for both the schema and the codec, use `@fieldName` from `zio.schema.annotation` instead. `@AvroAnnotations.name` on a record, as above, works with both.
:::

To rename a field so that the schema and the encoded values agree:

```scala
import zio.schema.annotation.fieldName
import zio.schema.codec.AvroCodec
import zio.schema.{DeriveSchema, Schema}

case class Customer(@fieldName("full_name") name: String)

object Customer {
  implicit val schema: Schema[Customer] = DeriveSchema.gen
}

val bytes = AvroCodec.schemaBasedBinaryCodec[Customer].encode(Customer("John"))
```

## Streaming

`AvroCodec` has a `BinaryCodec#streamEncoder` and a `BinaryCodec#streamDecoder`, but Avro binary values carry no length prefix, and the stream encoder writes the values back to back.

:::caution
The `BinaryCodec#streamDecoder` of the Avro codec decodes each upstream chunk as exactly one value. When a chunk holds several encoded values, only the first is decoded, and a value split across chunks fails to decode. Encode and decode each value with `BinaryCodec#encode` and `BinaryCodec#decode`, and send each one as its own message.
:::

The [How to Encode and Decode Streams](../../integration-with-zio-streams.md) guide shows the per-message pattern and a length-prefixed framing that works for Avro.
