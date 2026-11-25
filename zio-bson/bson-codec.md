# BsonCodec

> `BsonCodec[A]` holds one `BsonEncoder[A]` and one `BsonDecoder[A]` together. Most user code needs only the codec: you define one in the companion object of a type, and the library finds it wherever an encoder or decoder for that type is needed.

## Overview

Two things make a codec useful: it is what `DeriveBsonCodec.derive` produces for case classes and sealed traits, and it is what you write by hand when a type needs a custom format.

## Definition

`BsonCodec` is a case class with two fields and two transform methods:

```scala
final case class BsonCodec[A](encoder: BsonEncoder[A], decoder: BsonDecoder[A]) {
  def transform[B](to: A => B)(from: B => A): BsonCodec[B]
  def transformOrFail[B](to: A => Either[String, B])(from: B => A): BsonCodec[B]
}
```

`transform` builds a codec for a new type `B`. It takes two functions: `to` converts an `A` read from BSON into a `B`, and `from` converts a `B` into an `A` before writing. The `to` function runs after decoding and cannot fail. `transformOrFail` is the same, except that `to` returns `Either[String, B]`, so it can reject a value.

## Building a Codec from an Encoder and a Decoder

The companion object has an `apply` that takes an encoder and a decoder:

```scala
import zio.bson._
import zio.bson.BsonBuilder._
```

```scala
val stringCodec: BsonCodec[String] = BsonCodec(BsonEncoder[String], BsonDecoder[String])
```

```scala
println(stringCodec.encoder.toBsonValue("hello"))
// BsonString{value='hello'}
```

You do not need this for the built-in types, because `BsonCodec` already has them in its companion object. Use the two-argument `apply` when you have an encoder and a decoder from separate sources.

## Summoning a Codec

`BsonCodec.apply` with a single implicit argument returns the codec in scope for a type. This is handy for checking which codec the compiler will use:

```scala
println(BsonCodec[Int].encoder.toBsonValue(5))
// BsonInt32{value=5}
```

## Transforming a Codec

`transform` reuses an existing codec for a new type. This example stores a `Username` as a plain string:

```scala
final case class Username(value: String)

val usernameCodec: BsonCodec[Username] =
  BsonCodec[String].transform(Username(_))(_.value)
```

The two functions run in opposite directions: the first builds a `Username` after reading, and the second unwraps it before writing.

```scala
println(usernameCodec.encoder.toBsonValue(Username("ada")))
// BsonString{value='ada'}
println(usernameCodec.decoder.fromBsonValue(str("ada")))
// Right(Username(ada))
```

## Transforming a Codec with Validation

`transformOrFail` is the variant to use when reading can fail. Here a `Port` must be between 1 and 65535:

```scala
final case class Port(number: Int)

val portCodec: BsonCodec[Port] =
  BsonCodec[Int].transformOrFail { n =>
    if (n >= 1 && n <= 65535) Right(Port(n))
    else Left(s"Port must be between 1 and 65535, but got $n.")
  }(_.number)
```

```scala
println(portCodec.decoder.fromBsonValue(int(8080)))
// Right(Port(8080))
println(portCodec.decoder.fromBsonValue(int(70000)).left.map(_.render))
// Left(: Port must be between 1 and 65535, but got 70000.)
```

The failure is returned as a `BsonDecoder.Error`, so the message is paired with the path at which the decoder ran.

## Codecs in Companion Objects

For a type you own, put its codec in the companion object as an `implicit val` (or `implicit lazy val`). The compiler then finds it without an import:

```scala
final case class Tag(name: String)

object Tag {
  implicit val codec: BsonCodec[Tag] = BsonCodec[String].transform(Tag(_))(_.name)
}
```

```scala
println(BsonEncoder[Tag].toBsonValue(Tag("scala")))
// BsonString{value='scala'}
```

For case classes and sealed traits, the codec usually comes from `DeriveBsonCodec.derive` instead. See [Use zio-bson with the MongoDB Driver](./how-to-use-with-mongodb-driver.md) for an example, and [BsonCodecConfiguration](./bson-codec-configuration.md) for the derivation options.

## Predefined Instances

The `BsonCodec` companion object has codecs for the same types as the encoder and decoder companions: primitives, the `java.time` types, `UUID`, `ObjectId`, `Currency`, `Option`, `Array`, `Iterable`, `Map`, `NonEmptyChunk`, and any `BsonValue` subtype. The exact list is in [BsonEncoder](./bson-encoder.md) and [Type Mappings](./types.md).

## Related Pages

- [BsonEncoder](./bson-encoder.md)
- [BsonDecoder](./bson-decoder.md)
- [BsonCodecConfiguration](./bson-codec-configuration.md)
- [Field Codecs](./bson-field-codecs.md) for map keys
