# BSON Codecs

> BSON (Binary JSON) is the binary document format that MongoDB uses to store and exchange data. The `zio-schema-bson` module derives BSON codecs from a ZIO Schema. The codecs it returns are [zio-bson](https://github.com/zio/zio-bson) codecs, so they work with the zio-bson syntax and with the MongoDB Java driver through zio-bson's codec provider.

## Introduction

Unlike the other format modules, the BSON module does not return a `BinaryCodec`. It reads and writes `org.bson.BsonValue` values and documents, and it has no `streamEncoder` or `streamDecoder`.

## Installation

To use BSON codecs, add the following dependency to your `build.sbt` file. The module is published for the JVM only:

```scala
libraryDependencies += "dev.zio" %% "zio-schema-bson" % "1.9.2"
```

## BsonSchemaCodec

The `BsonSchemaCodec` object in the `zio.schema.codec` package derives a `zio.bson.BsonCodec`, which pairs a `BsonEncoder` and a `BsonDecoder`. It can also derive each half on its own, and every method has an overload that takes a `BsonSchemaCodec.Config`:

```scala
object BsonSchemaCodec {
  def bsonCodec[A](schema: Schema[A]): BsonCodec[A]
  def bsonCodec[A](schema: Schema[A], config: Config): BsonCodec[A]

  def bsonEncoder[A](schema: Schema[A]): BsonEncoder[A]
  def bsonEncoder[A](schema: Schema[A], config: Config): BsonEncoder[A]

  def bsonDecoder[A](schema: Schema[A]): BsonDecoder[A]
  def bsonDecoder[A](schema: Schema[A], config: Config): BsonDecoder[A]
}
```

## Example

The following program derives a BSON codec for a case class, encodes a value to a `BsonValue` with the zio-bson `BsonEncoderOps#toBsonValue` syntax, and decodes it back with `BsonDecoderOps#as`:

```scala
import org.bson.BsonValue
import zio._
import zio.bson._
import zio.schema.codec._
import zio.schema.{DeriveSchema, Schema}

case class Person(name: String, age: Int)

object Person {
  implicit val schema: Schema[Person] = DeriveSchema.gen
  implicit val bsonCodec: BsonCodec[Person] =
    BsonSchemaCodec.bsonCodec(Person.schema)
}

object Main extends ZIOAppDefault {
  def run = for {
    _ <- ZIO.debug("Bson Example:")
    person: Person     = Person("John", 42)
    encoded: BsonValue = person.toBsonValue
    _       <- ZIO.debug(s"person object encoded to BsonValue: $encoded")
    decoded <- ZIO.fromEither(encoded.as[Person])
    _ <- ZIO.debug(s"BsonValue of person object decoded to Person: $decoded")
  } yield ()
}
```

In the example above, `DeriveSchema.gen` derives the schema for `Person`, and `BsonSchemaCodec.bsonCodec` turns it into a `BsonCodec[Person]`. With the codec in implicit scope, zio-bson encodes `Person` values to BSON and decodes BSON values back to `Person`.

## Configuration

`BsonSchemaCodec.Config` controls how sealed traits are written. The `BsonSchemaCodec.Config` object is the default configuration, and you build a custom one by calling its `with` methods. `Config#withSumTypeHandling` sets how the case of a sealed trait is recorded, as a `BsonSchemaCodec.SumTypeHandling` value, and `Config#withClassNameMapping` sets a `String => String` function that rewrites case names, for example to lower case.

`SumTypeHandling` has two forms. `SumTypeHandling.WrapperWithClassNameField`, the default, wraps the case in a document with one field, named after the case. `SumTypeHandling.DiscriminatorField(name)` writes the case name into a field called `name` next to the case's own fields.

To compare the two forms, we define a sealed trait and derive an encoder for each configuration:

```scala
import zio.bson.BsonEncoder
import zio.schema.codec.BsonSchemaCodec
import zio.schema.codec.BsonSchemaCodec.SumTypeHandling
import zio.schema.{DeriveSchema, Schema}

sealed trait Shape

object Shape {
  final case class Circle(radius: Double) extends Shape
  final case class Square(side: Double)   extends Shape

  implicit val schema: Schema[Shape] = DeriveSchema.gen
}

val wrapped: BsonEncoder[Shape] = BsonSchemaCodec.bsonEncoder(Shape.schema)

val withTypeField: BsonEncoder[Shape] = BsonSchemaCodec.bsonEncoder(
  Shape.schema,
  BsonSchemaCodec.Config
    .withSumTypeHandling(SumTypeHandling.DiscriminatorField("type"))
    .withClassNameMapping(_.toLowerCase)
)
```

The default configuration wraps the circle in a `Circle` field, and the custom one writes a lower-case `type` field instead:

```scala
println(wrapped.toBsonValue(Shape.Circle(1.5)))
// {"Circle": {"radius": 1.5}}
println(withTypeField.toBsonValue(Shape.Circle(1.5)))
// {"radius": 1.5, "type": "circle"}
```

A sealed trait whose cases are all case objects is written as a plain string, whatever the configuration.

## Annotations

The BSON codec reads two sets of annotations: the ZIO Schema annotations from `zio.schema.annotation`, and the annotations that zio-bson defines for its own derivation, in `zio.bson`. When both kinds set the same thing, the zio-bson annotation wins.

The ZIO Schema annotations that the BSON codec honours are `@fieldName` (through derivation), `@fieldNameAliases` and `@caseNameAliases` (when decoding), `@caseName`, `@discriminatorName`, `@noDiscriminator`, `@transientField`, `@transientCase`, `@optionalField` and `@fieldDefaultValue` (when decoding), and `@rejectExtraFields`. The [annotation support table](index.md#annotation-support-by-codec) compares this with the other codecs.

The zio-bson annotations are the following:

| Annotation                 | Applies to   | Effect                                                      |
| -------------------------- | ------------ | ----------------------------------------------------------- |
| `@bsonField(name)`         | Field        | Writes and reads the field under `name`                     |
| `@bsonExclude`             | Field        | Leaves the field out, like `@transientField`                |
| `@bsonHint(name)`          | Case         | Uses `name` as the case name, like `@caseName`              |
| `@bsonDiscriminator(name)` | Sealed trait | Writes the case name into a field called `name`             |
| `@bsonNoExtraFields`       | Case class   | Fails decoding on unknown fields, like `@rejectExtraFields` |

The following model uses zio-bson annotations to match an existing MongoDB collection:

```scala
import zio.bson.{bsonDiscriminator, bsonField, bsonHint}
import zio.schema.codec.BsonSchemaCodec
import zio.schema.{DeriveSchema, Schema}

@bsonDiscriminator("kind")
sealed trait Event

object Event {
  @bsonHint("signup")
  final case class SignedUp(@bsonField("user_id") userId: String) extends Event

  @bsonHint("login")
  final case class LoggedIn(@bsonField("user_id") userId: String) extends Event

  implicit val schema: Schema[Event] = DeriveSchema.gen
}
```

The encoded document uses the hint as the case name and the renamed field:

```scala
println(BsonSchemaCodec.bsonEncoder(Event.schema).toBsonValue(Event.SignedUp("u-1")))
// {"user_id": "u-1", "kind": "signup"}
```

## ObjectId

MongoDB documents usually carry an `_id` of type `org.bson.types.ObjectId`. The `zio.schema.codec.bson` package object provides an implicit `Schema[ObjectId]`, named `ObjectIdSchema`, so a case class can have an `ObjectId` field. The BSON codec writes it as a native BSON ObjectId. Other codecs see it as a record with one `$oid` string field. To make the schema available, import it before deriving:

```scala
import org.bson.types.ObjectId
import zio.schema.codec.bson._
import zio.schema.{DeriveSchema, Schema}
import zio.schema.annotation.fieldName

final case class User(@fieldName("_id") id: ObjectId, name: String)

object User {
  implicit val schema: Schema[User] = DeriveSchema.gen
}
```
