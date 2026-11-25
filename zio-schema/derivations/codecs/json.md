# JSON Codecs

> JSON (JavaScript Object Notation) is a widely used data interchange format for transmitting and storing data. The `zio-schema-json` module derives JSON codecs from a ZIO Schema, built on top of [ZIO JSON](https://github.com/zio/zio-json). It runs on the JVM, Scala.js, and Scala Native. This page shows how to derive the codecs, how to configure them, and how they encode streams.

## Introduction

## Installation

To derive JSON codecs from a ZIO Schema, add the following dependency to your `build.sbt` file:

```scala
libraryDependencies += "dev.zio" %% "zio-schema-json" % "1.9.2"
```

For a Scala.js or Scala Native project, use `%%%` instead, so sbt picks the artifact for the platform:

```scala
libraryDependencies += "dev.zio" %%% "zio-schema-json" % "1.9.2"
```

## Deriving a Codec

The `JsonCodec` object in the `zio.schema.codec` package derives two kinds of codec from a schema. A ZIO JSON codec plugs into code that already uses ZIO JSON, and a `BinaryCodec` gives the same API as the other format modules. Each entry point also has an overload that takes a `JsonCodec.Configuration`, described in [Configuration](#configuration):

```scala
object JsonCodec {
  def jsonCodec[A](schema: Schema[A]): zio.json.JsonCodec[A]
  def jsonCodec[A](cfg: Configuration)(schema: Schema[A]): zio.json.JsonCodec[A]

  def jsonEncoder[A](schema: Schema[A]): zio.json.JsonEncoder[A]
  def jsonEncoder[A](cfg: Configuration)(schema: Schema[A]): zio.json.JsonEncoder[A]

  def jsonDecoder[A](schema: Schema[A]): zio.json.JsonDecoder[A]
  def jsonDecoder[A](cfg: Configuration)(schema: Schema[A]): zio.json.JsonDecoder[A]

  implicit def schemaBasedBinaryCodec[A](implicit schema: Schema[A]): BinaryCodec[A]
  def schemaBasedBinaryCodec[A](cfg: Configuration)(implicit schema: Schema[A]): BinaryCodec[A]
}
```

### ZIO JSON Codecs

`JsonCodec.jsonCodec` returns a `zio.json.JsonCodec[A]`. Once it is in implicit scope, the ZIO JSON syntax, such as `toJson` and `fromJson`, works for the type. The following program encodes a `Person` to a JSON string and decodes it back:

```scala
import zio._
import zio.json._
import zio.schema.{DeriveSchema, Schema}

case class Person(name: String, age: Int)

object Person {
  implicit val schema: Schema[Person] =
    DeriveSchema.gen
  implicit val jsonCodec: zio.json.JsonCodec[Person] =
    zio.schema.codec.JsonCodec.jsonCodec(schema)
}

object Main extends ZIOAppDefault {
  def run = for {
    _ <- ZIO.debug("JSON Codec Example:")
    person: Person  = Person("John", 42)
    encoded: String = person.toJson
    _       <- ZIO.debug(s"person object encoded to JSON string: $encoded")
    decoded <- ZIO.fromEither(Person.jsonCodec.decodeJson(encoded))
    _       <- ZIO.debug(s"JSON object decoded to Person class: $decoded")
  } yield ()
}
```

When you need only one direction, `JsonCodec.jsonEncoder` and `JsonCodec.jsonDecoder` derive a `zio.json.JsonEncoder` or a `zio.json.JsonDecoder` on its own. To derive a decoder for an incoming payload:

```scala
import zio.json.JsonDecoder
import zio.schema.codec.JsonCodec
import zio.schema.{DeriveSchema, Schema}

case class Person(name: String, age: Int)

object Person {
  implicit val schema: Schema[Person]       = DeriveSchema.gen
  implicit val decoder: JsonDecoder[Person] = JsonCodec.jsonDecoder(schema)
}

val parsed: Either[String, Person] = Person.decoder.decodeJson("""{"name":"John","age":42}""")
```

### Binary Codecs

`JsonCodec.schemaBasedBinaryCodec` returns a `BinaryCodec[A]` that encodes a value to UTF-8 JSON bytes and decodes the bytes back. The following program encodes a `Person` and prints the bytes in hexadecimal:

```scala
import zio._
import zio.schema.codec.BinaryCodec
import zio.schema.{DeriveSchema, Schema}

case class Person(name: String, age: Int)

object Person {
  implicit val schema: Schema[Person] =
    DeriveSchema.gen
  implicit val jsonBinaryCodec: BinaryCodec[Person] =
    zio.schema.codec.JsonCodec.schemaBasedBinaryCodec(schema)
}

object Main extends ZIOAppDefault {
  def run = for {
    _ <- ZIO.debug("JSON Codec Example:")
    person: Person = Person("John", 42)
    encoded: Chunk[Byte] = Person.jsonBinaryCodec.encode(person)
    _ <- ZIO.debug(s"person object encoded to Binary JSON: ${toHex(encoded)}")
    decoded <- ZIO.fromEither(Person.jsonBinaryCodec.decode(encoded))
    _ <- ZIO.debug(s"JSON object decoded to Person class: $decoded")
  } yield ()

  def toHex(bytes: Chunk[Byte]): String =
    bytes.map("%02x".format(_)).mkString(" ")
}
```

The program prints the following lines:

```
JSON Codec Example:
person object encoded to Binary JSON: 7b 22 6e 61 6d 65 22 3a 22 4a 6f 68 6e 22 2c 22 61 67 65 22 3a 34 32 7d
JSON object decoded to Person class: Person(John,42)
```

If you already have a hand-written `zio.json.JsonCodec[A]` in implicit scope, the implicit `JsonCodec.zioJsonBinaryCodec` turns it into a `BinaryCodec[A]`, so the type can be used wherever a `BinaryCodec` is expected.

## Configuration

`JsonCodec.Configuration` controls how a codec encodes and decodes. Pass it to `JsonCodec.jsonCodec(config)`, `JsonCodec.jsonEncoder(config)`, `JsonCodec.jsonDecoder(config)`, or `JsonCodec.schemaBasedBinaryCodec(config)`. `JsonCodec.Configuration.default` applies when you pass none.

### Configuration Fields

`JsonCodec.Configuration` is a case class, so you set only the fields you want to change. It has the following fields:

| Field                      | Type                   | Default                                               | Effect                                                                             |
| -------------------------- | ---------------------- | ----------------------------------------------------- | ---------------------------------------------------------------------------------- |
| `explicitEmptyCollections` | `ExplicitConfig`       | `ExplicitConfig(encoding = true, decoding = false)`   | Whether empty collections are written, and whether they are required when decoding |
| `explicitNulls`            | `ExplicitConfig`       | `ExplicitConfig(encoding = true, decoding = false)`   | Whether `None` is written as `null`, and whether it is required when decoding      |
| `discriminatorSettings`    | `DiscriminatorSetting` | `DiscriminatorSetting.ClassName(NameFormat.Identity)` | How the case of a sealed trait is recorded                                         |
| `fieldNameFormat`          | `NameFormat`           | `NameFormat.Identity`                                 | Rewrites field names, for example to snake case                                    |
| `treatStreamsAsArrays`     | `Boolean`              | `false`                                               | Encodes a stream as one JSON array instead of one value per line                   |
| `rejectExtraFields`        | `Boolean`              | `false`                                               | Fails decoding when the input has a field the type does not declare                |

The `NameFormat` values (`Identity`, `SnakeCase`, `CamelCase`, `PascalCase`, `KebabCase`, and `Custom`) are described on the [NameFormat and TypeId](../../reference/name-format-and-type-id.md) page.

### Empty Values and ExplicitConfig

`JsonCodec.ExplicitConfig(encoding, decoding)` sets the two directions independently for `None` values and for empty collections. With `encoding = true`, the encoder writes `null` or `[]`; with `encoding = false`, it leaves the field out. With `decoding = false`, a missing field decodes as `None` or as an empty collection; with `decoding = true`, a missing field is an error.

To write a `Profile` with snake-case field names, and to omit empty `Option` values and empty lists:

```scala
import zio.schema.codec.JsonCodec
import zio.schema.{DeriveSchema, NameFormat, Schema}

final case class Profile(userName: String, nickname: Option[String], tags: List[String])

object Profile {
  implicit val schema: Schema[Profile] = DeriveSchema.gen
}

val config = JsonCodec.Configuration(
  explicitEmptyCollections = JsonCodec.ExplicitConfig(encoding = false),
  explicitNulls = JsonCodec.ExplicitConfig(encoding = false),
  fieldNameFormat = NameFormat.SnakeCase
)
```

Encoding a profile with no nickname and no tags leaves out both optional parts:

```scala
println(JsonCodec.jsonCodec(config)(Profile.schema).encodeJson(Profile("alice", None, Nil), None).toString)
// {"user_name":"alice"}
```

With the default configuration, the decoder accepts input that leaves out the nickname and the tags:

```scala
JsonCodec.jsonDecoder(Profile.schema).decodeJson("""{"userName":"alice"}""")
// res5: Either[String, Profile] = Right(
//   value = Profile(userName = "alice", nickname = None, tags = List())
// )
```

Setting `decoding = true` for `explicitNulls` makes the same kind of input fail, because the `nickname` field is now required:

```scala
val strictNulls = JsonCodec.Configuration(explicitNulls = JsonCodec.ExplicitConfig(decoding = true))
// strictNulls: JsonCodec.Configuration = Configuration(
//   explicitEmptyCollections = ExplicitConfig(encoding = true, decoding = false),
//   explicitNulls = ExplicitConfig(encoding = true, decoding = true),
//   discriminatorSettings = ClassName(format = <function1>),
//   fieldNameFormat = <function1>,
//   treatStreamsAsArrays = false,
//   rejectExtraFields = false
// )

JsonCodec.jsonDecoder(strictNulls)(Profile.schema).decodeJson("""{"userName":"alice","tags":[]}""")
// res6: Either[String, Profile] = Left(value = ".nickname(missing)")
```

### Field Name Format

`fieldNameFormat` rewrites the name of every field when encoding, and the decoder expects the rewritten names. It is a quick way to match a snake-case or kebab-case API without annotating each field.

:::caution
`fieldNameFormat` also rewrites fields that you renamed with `@fieldName`, because derivation stores the `@fieldName` value as the field's name. With `NameFormat.SnakeCase`, a field annotated `@fieldName("UserID")` is written as `user_id`, not `UserID`. To keep an explicit name, pass a `NameFormat.Custom` function that returns that name unchanged, or rename the fields with `@fieldName` and leave `fieldNameFormat` at `NameFormat.Identity`.
:::

The following schema renames one field explicitly and formats every other field as snake case:

```scala
import zio.schema.annotation.fieldName
import zio.schema.codec.JsonCodec
import zio.schema.{DeriveSchema, NameFormat, Schema}

final case class Account(@fieldName("UserID") id: Int, displayName: String)

object Account {
  implicit val schema: Schema[Account] = DeriveSchema.gen
}

val keepUserId = JsonCodec.Configuration(
  fieldNameFormat = NameFormat.Custom(name => if (name == "UserID") name else NameFormat.SnakeCase(name))
)
```

The custom format keeps `UserID` and still turns `displayName` into `display_name`:

```scala
println(JsonCodec.jsonEncoder(keepUserId)(Account.schema).encodeJson(Account(1, "Alice"), None).toString)
// {"UserID":1,"display_name":"Alice"}
```

### Sum Type Encoding

By default, a sealed trait is written as an object with one key, the case name, that wraps the case's fields. `discriminatorSettings` takes a `JsonCodec.DiscriminatorSetting`, which has three forms:

- `DiscriminatorSetting.ClassName(format)` wraps the case in an object keyed by the case name, formatted with `format`. This is the default, with `NameFormat.Identity`.
- `DiscriminatorSetting.Name(name, format)` writes the case name, formatted with `format`, into a field called `name` next to the case's own fields.
- `DiscriminatorSetting.NoDiscriminator` writes only the case's fields. The decoder tries each case in turn until one succeeds.

A sealed trait whose cases are all case objects is written as a plain string, whatever the setting, because derivation marks it with `@simpleEnum`.

To compare the forms, we define a sealed trait with two cases and a helper that encodes a value with a given configuration:

```scala
import zio.schema.codec.JsonCodec
import zio.schema.codec.JsonCodec.{Configuration, DiscriminatorSetting}
import zio.schema.{DeriveSchema, NameFormat, Schema}

sealed trait Shape

object Shape {
  final case class Circle(radius: Double) extends Shape
  final case class Square(side: Double)   extends Shape

  implicit val schema: Schema[Shape] = DeriveSchema.gen
}

def encodeShape(shape: Shape, config: Configuration): String =
  JsonCodec.jsonEncoder(config)(Shape.schema).encodeJson(shape, None).toString
```

Each line encodes the same circle with a different setting: the default wrapper, a wrapper with a snake-case key, a `type` field, and no discriminator:

```scala
println(encodeShape(Shape.Circle(1.5), Configuration.default))
// {"Circle":{"radius":1.5}}
println(encodeShape(Shape.Circle(1.5), Configuration(discriminatorSettings = DiscriminatorSetting.ClassName(NameFormat.SnakeCase))))
// {"circle":{"radius":1.5}}
println(encodeShape(Shape.Circle(1.5), Configuration(discriminatorSettings = DiscriminatorSetting.Name("type"))))
// {"type":"Circle","radius":1.5}
println(encodeShape(Shape.Circle(1.5), Configuration(discriminatorSettings = DiscriminatorSetting.NoDiscriminator)))
// {"radius":1.5}
```

On a single sealed trait, `@discriminatorName` and `@noDiscriminator` take precedence over `discriminatorSettings`, and `@caseName` replaces the case name before the format is applied. The [Schema Annotations](../../schema-annotations.md) page shows the JSON each one produces.

### Rejecting Extra Fields

By default, the decoder skips fields that the type does not declare. `rejectExtraFields = true` makes it fail instead, for every type the codec decodes. The `@rejectExtraFields` annotation switches rejection on for a single type; it cannot switch it off when the configuration turns it on.

## Streaming

The binary codec from `JsonCodec.schemaBasedBinaryCodec` has a `BinaryCodec#streamEncoder` and a `BinaryCodec#streamDecoder`. By default, the encoder writes one JSON value per line (newline-delimited JSON), and the decoder finds the boundaries between values itself, so it decodes correctly however the bytes are split into chunks. With `treatStreamsAsArrays = true`, the encoder writes the whole stream as one JSON array, and the decoder reads the elements of one. The JSON codec is the only built-in codec that streams several values reliably; the [How to Encode and Decode Streams](../../integration-with-zio-streams.md) guide has worked examples.

:::caution
With `treatStreamsAsArrays = true`, the stream decoder dies with an `ArrayIndexOutOfBoundsException` when the array arrives split across chunks of more than one byte. Use the default newline-delimited format for streams read from a socket or a file, or collect the bytes and decode them as one chunk.
:::

## Annotations

The JSON codec reads more of the `zio.schema.annotation` annotations than any other codec: field and case renaming and aliases, discriminators, `@simpleEnum`, transient fields and cases, optional fields and default values, and `@rejectExtraFields`. The [Schema Annotations](../../schema-annotations.md) page shows the JSON for each one, and the [annotation support table](index.md#annotation-support-by-codec) compares the codecs.
