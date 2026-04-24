# Apache Avro Codecs

> Apache Avro is a popular data serialization format used in distributed systems, particularly in the Apache Hadoop ecosystem. In this article, we will explore how to work with Apache Avro codecs in Scala using the ZIO Schema. Avro codecs allow us to easily serialize and deserialize data in Avro's binary and JSON formats.

## Introduction

Apache Avro is a popular data serialization format used in distributed systems, particularly in the Apache Hadoop ecosystem. In this article, we will explore how to work with Apache Avro codecs in Scala using the ZIO Schema. Avro codecs allow us to easily serialize and deserialize data in Avro's binary and JSON formats.

## Installation

To use the Avro codecs, we need to add the following dependency to our `build.sbt` file:

```scala
libraryDependencies += "dev.zio" %% "zio-schema-avro" % "1.7.5"
```

## Codecs

It has two codecs:

- An **AvroSchemaCodec** to serialize a `Schema[A]` to Avro JSON schema and deserialize an Avro JSON schema to a `Schema.GenericRecord`.
- An **AvroCodec** to serialize/deserialize the Avro binary serialization format.

### AvroSchemaCodec

The `AvroSchemaCodec` provides methods to encode a `Schema[_]` to Avro JSON schema and decode an Avro JSON schema to a `Schema[_]` ([`Schema.GenericRecord`](../../operations/dynamic-data-representation.md)):

```scala
trait AvroSchemaCodec {
  def encode(schema: Schema[_]): scala.util.Either[String, String]
  def decode(bytes: Chunk[Byte]): scala.util.Either[String, Schema[_]]
}
```

The `encode` method takes a `Schema[_]` and returns an `Either[String, String]` where the `Right` side contains the Avro schema in JSON‌ format.

The `decode` method takes a `Chunk[Byte]` which contains the Avro JSON Schema in binary format and returns an `Either[String, Schema[_]]` where the `Right` side contains the ZIO Schema in `GenericRecord` format.

Here is an example of how to use it:

```scala

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

The output:

```scala
AvroSchemaCodec Example:
The person schema in Avro Schema JSON format: {"type":"record","name":"Person","fields":[{"name":"name","type":"string"},{"name":"age","type":"int"}]}
The person schema in ZIO Schema GenericRecord format: GenericRecord(Nominal(Chunk(),Chunk(),Person),Field(name,Primitive(string,Chunk())) :*: Field(age,Primitive(int,Chunk())) :*: Empty,Chunk(name(Person)))
```

As we can see, we converted the `Schema[Person]` to Avro schema JSON format, and then we converted it back to the ZIO Schema `GenericRecord` format.

### AvroCodec

We can create a `BinaryCodec[A]` for any type `A` that has a `Schema[A]` instance using `AvroCodec.schemaBasedBinaryCodec`:

```scala
object AvroCodec {
  implicit def schemaBasedBinaryCodec[A](implicit schema: Schema[A]): BinaryCodec[A] = ???
}
```

Now, let's write an example and see how it works:

```scala

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

The output:

```scala
AvroCodec Example:
encoded person object: 08 4a 6f 68 6e 54
decoded person object: Person(John,42)
```

## Annotations

The Apache Avro specification supports some attributes for describing the data which are not part of the default ZIO Schema. To support these extra metadata, we can use annotations defined in the `zio.schema.codec.AvroAnnotations` object.

There tons of annotations that we can use. Let's introduce some of them:

- `@AvroAnnotations.name(name: String)`: To change the name of a field or a record.
- `@AvroAnnotations.namespace(namespace: String)`: To add the namespace for a field or a record.
- `@AvroAnnotations.doc(doc: String)`: To add documentation to a field or a record.
- `@AvroAnnotations.aliases(aliases: Set[String])`: To add aliases to a field or a record.
- `@AvroAnnotations.avroEnum`: To treat a sealed trait as an Avro enum.
- `@AvroAnnotations.scale(scale: Int = 24)` and `@AvroAnnotations.precision(precision: Int = 48)`: To describe the scale and precision of a decimal field.
- `@AvroAnnotations.decimal(decimalType: DecimalType)`: Used to annotate a `BigInteger` or `BigDecimal` type to indicate the logical type encoding (avro bytes or avro fixed).
- `@AvroAnnotations.bytes(bytesType: BytesType)`: Used to annotate a Byte type to indicate the avro type encoding (avro bytes or avro fixed).
- `@AvroAnnotations.formatToString`: Used to annotate fields of type `LocalDate`, `LocalTime`, `LocalDateTime` or `Instant` in order to render them as a string using the given formatter instead of rendering them as avro logical types.
- `@AvroAnnotations.timeprecision(timeprecisionType: TimePrecisionType)`: Used to indicate the precision (millisecond precision or microsecond precision) of avro logical types `Time`, `Timestamp` and `Local timestamp`
- `@AvroAnnotations.error`: Used to annotate a record in order to render it as a avro error record
- `@AvroAnnotations.fieldOrder(fieldOrderType: FieldOrderType)`: Used to indicate the avro field order of a record

For example, to change the name of a field in the Avro schema, we can use the `AvroAnnotations.name` annotation:

```scala

@AvroAnnotations.name("User")
case class Person(name: String, age: Int)

object Person {
  implicit val schema: Schema[Person] = DeriveSchema.gen
}
```

Now, if we generate the Avro schema for the `Person` class, we will see that the name of the record is `User` instead of `Person`:

```scala

object Main extends ZIOAppDefault {
  def run =
    for {
      _          <- ZIO.debug("AvroSchemaCodec Example with annotations:")
      avroSchema <- ZIO.fromEither(AvroSchemaCodec.encode(Person.schema))
      _ <- ZIO.debug(s"The person schema in Avro Schema JSON format: $avroSchema")
    } yield ()
}
```

The output:

```scala
The person schema in Avro Schema JSON format: {"type":"record","name":"User","fields":[{"name":"name","type":"string"},{"name":"age","type":{"type":"bytes","logicalType":"decimal","precision":48,"scale":24}}]}
```
