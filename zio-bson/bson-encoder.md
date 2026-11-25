# BsonEncoder

> `BsonEncoder[A]` is the typeclass that turns a Scala value of type `A` into BSON. ZIO Bson uses it everywhere a value is written, whether into a `BsonWriter` for the MongoDB driver or into an in-memory `BsonValue`.

## Overview

You rarely write an encoder by hand. Most of the time you get one from a `BsonCodec` (see [BsonCodec](./bson-codec.md)), either a built-in one or one derived with `DeriveBsonCodec.derive`. This page describes the methods every encoder has and the operations that build new encoders from existing ones.

## Definition

The trait has four members that matter to users:

```scala
trait BsonEncoder[A] {
  def encode(writer: BsonWriter, value: A, ctx: BsonEncoder.EncoderContext): Unit
  def toBsonValue(value: A): BsonValue
  def isAbsent(value: A): Boolean
  def contramap[B](f: B => A): BsonEncoder[B]
}
```

`encode` writes the value to a `BsonWriter`. `toBsonValue` builds a `BsonValue` directly. `isAbsent` tells the caller that a value can be left out of a document, and it returns `false` by default. `contramap` builds an encoder for a new type `B` by converting each `B` to an `A` first.

`EncoderContext` is a small case class with one flag, `inlineNextObject`. The derived encoders use it to avoid writing an extra document wrapper for a sum-type discriminator. `BsonEncoder.EncoderContext.default` has the flag off. It is the context to pass when calling `BsonEncoder#encode` directly, as the MongoDB driver integration does.

## Encoding a Value to a BsonValue

The syntax method `toBsonValue` from the `zio.bson` package calls the encoder for you. The encoder is found through implicit search:

```scala
import zio.bson._
import zio.bson.BsonBuilder._
```

To convert a primitive value:

```scala
println(42.toBsonValue)
// BsonInt32{value=42}
println("zio".toBsonValue)
// BsonString{value='zio'}
```

To get the encoder itself, call `BsonEncoder.apply` with the type. This is useful when you want the encoder without the syntax:

```scala
val intEncoder: BsonEncoder[Int] = BsonEncoder[Int]
```

```scala
println(intEncoder.toBsonValue(7))
// BsonInt32{value=7}
```

## Building an Encoder with contramap

`contramap` lets you reuse an existing encoder for a type that wraps or converts to it. The function you pass runs before the original encoder, so the result is an encoder for the new type. For example, to encode a `Celsius` value as a plain `Double`:

```scala
final case class Celsius(degrees: Double)

val celsiusEncoder: BsonEncoder[Celsius] = BsonEncoder[Double].contramap(_.degrees)
```

Use it the same way as any other encoder:

```scala
println(celsiusEncoder.toBsonValue(Celsius(21.5)))
// BsonDouble{value=21.5}
```

For a full encoder and decoder pair, use `BsonCodec.transform`, described in [BsonCodec](./bson-codec.md).

## Absent Values

An encoder reports absence through `isAbsent`. The derived case-class encoder checks it on each field. When `skipNullsInCaseClass` is `true` (the default), a field whose encoder says the value is absent is left out of the document entirely. Only `Option` says so today: `None` is absent, and `Some(x)` is not.

```scala
import zio.bson.magnolia._

final case class Profile(name: String, nickname: Option[String])

object Profile {
  implicit val codec: BsonCodec[Profile] = DeriveBsonCodec.derive
}
```

```scala
println(BsonEncoder[Profile].toBsonValue(Profile("ada", None)))
// {"name": "ada"}
```

With the default configuration, the `None` field is omitted from the document. See [BsonCodecConfiguration](./bson-codec-configuration.md) for how to change this.

## Predefined Instances

The `BsonEncoder` companion object has encoders for these types:

- `Boolean`, `String`, `Char`, `Symbol`, `UUID`, `org.bson.types.ObjectId`
- `Byte`, `Short`, `Int`, `Long`, `Float`, `Double`
- `scala.math.BigDecimal`, `java.math.BigDecimal`, `scala.math.BigInt`, `java.math.BigInteger`
- `java.time` types: `Instant`, `LocalDate`, `LocalDateTime`, `LocalTime`, `OffsetDateTime`, `OffsetTime`, `ZonedDateTime`, `Duration`, `Period`, `Year`, `YearMonth`, `MonthDay`, `DayOfWeek`, `Month`, `ZoneId`, `ZoneOffset`
- `java.util.Currency`
- `Option[A]` when `A` has an encoder
- `Array[Byte]` and any `Iterable[Byte]` subtype, as `BINARY`
- `Array[A]`, `Iterable` subtypes, and `NonEmptyChunk[A]` for other `A`, as `ARRAY`
- `Map[K, V]` and other `Map` subtypes, when keys have a [BsonFieldEncoder](./bson-field-codecs.md)
- Any `T <: BsonValue`, passed through unchanged

The exact BSON type each one produces is listed in [Type Mappings](./types.md).

## Field Encoders

Map keys are strings in BSON, so an encoder for `Map[K, V]` also needs a `BsonFieldEncoder[K]`. See [Field Codecs](./bson-field-codecs.md).

## Related Pages

- [BsonDecoder](./bson-decoder.md) reads values back.
- [BsonCodec](./bson-codec.md) bundles an encoder and a decoder.
- [Type Mappings](./types.md) lists the BSON type of each built-in encoder.
