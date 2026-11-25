# Write a Codec by Hand

> Derivation works for case classes whose BSON form matches their Scala shape. Some types need a different form: a value stored as one string, a number that must be checked, or a class you do not own, such as one from a library. This guide shows three ways to write a codec for those cases, from the simplest to the most work.

## Problem

## Wrap an Existing Codec

If the BSON form of your type is the same as a type you already have a codec for, reuse that codec with `transform`. Use `transformOrFail` when reading can fail. This example stores an email address as a string and rejects values without an `@`:

```scala
import zio.bson._
import zio.bson.BsonBuilder._

final case class Email(value: String)

object Email {
  implicit val codec: BsonCodec[Email] =
    BsonCodec[String].transformOrFail[Email] { s =>
      if (s.contains("@")) Right(Email(s))
      else Left(s"Expected an email address, but got '$s'.")
    }(_.value)
}
```

```scala
println(BsonEncoder[Email].toBsonValue(Email("ada@example.com")))
// BsonString{value='ada@example.com'}
println(doc("contact" -> str("not-an-email")).as[Map[String, Email]])
// Left(.contact: Expected an email address, but got 'not-an-email'.)
```

The `Left` message becomes the error for the field, so the path `.contact` appears when the value sits in a document.

## Store a Type as a Different BSON Form

Some types have a natural string form that differs from the default. This example stores `Money` as a single string such as `"12.50 USD"`. The encode side builds the string, and the decode side parses it. `scala.util.Try` turns a bad number into a `Left`:

```scala
import zio.bson._
import zio.bson.BsonBuilder._
import scala.util.Try

final case class Money(amount: BigDecimal, currency: String)

object Money {
  implicit val codec: BsonCodec[Money] =
    BsonCodec[String].transformOrFail[Money] { s =>
      s.split(" ").toList match {
        case amount :: currency :: Nil =>
          Try(Money(BigDecimal(amount), currency)).toEither.left.map(_.getMessage)
        case _                         =>
          Left(s"Expected '<amount> <currency>', but got '$s'.")
      }
    }(m => s"${m.amount} ${m.currency}")
}
```

```scala
println(BsonEncoder[Money].toBsonValue(Money(BigDecimal("12.50"), "USD")))
// BsonString{value='12.50 USD'}
println(BsonDecoder[Money].fromBsonValue(str("3 EUR")))
// Right(Money(3,EUR))
println(BsonDecoder[Money].fromBsonValue(str("twelve EUR")).left.map(_.render))
// Left(: Character t is neither a decimal digit number, decimal point, nor "e" notation exponential mark.)
```

## Implement an Encoder and Decoder Directly

When there is no existing codec to reuse, implement `BsonEncoder` and `BsonDecoder` yourself. An encoder needs two methods: `encode`, which writes to a `BsonWriter`, and `toBsonValue`, which builds a `BsonValue`. The example below stores a `Celsius` temperature as a `DOUBLE`:

```scala
import zio.bson._
import zio.bson.BsonBuilder._
import org.bson.{BsonDouble, BsonValue, BsonWriter}

final case class Celsius(degrees: Double)

object Celsius {
  implicit val encoder: BsonEncoder[Celsius] = new BsonEncoder[Celsius] {
    def encode(writer: BsonWriter, value: Celsius, ctx: BsonEncoder.EncoderContext): Unit =
      writer.writeDouble(value.degrees)

    def toBsonValue(value: Celsius): BsonValue =
      new BsonDouble(value.degrees)
  }

  implicit val decoder: BsonDecoder[Celsius] =
    BsonDecoder[Double].mapOrFail { d =>
      if (d >= -273.15) Right(Celsius(d))
      else Left(s"Temperature $d is below absolute zero.")
    }
}
```

```scala
println(BsonEncoder[Celsius].toBsonValue(Celsius(21.5)))
// BsonDouble{value=21.5}
println(BsonDecoder[Celsius].fromBsonValue(double(-300.0)).left.map(_.render))
// Left(: Temperature -300.0 is below absolute zero.)
```

The decoder is built from `BsonDecoder[Double]` with `mapOrFail`, rather than written from scratch. This keeps the BSON type checks of the built-in decoder, so an `Int` or `Long` value is also accepted.

## Use the Codec

A hand-written codec is found the same way as a derived one. Put it in the companion object as an `implicit val`, and any code that needs `BsonEncoder[Celsius]` or `BsonDecoder[Celsius]` finds it.

To check that the codec works with the MongoDB driver, see [Use zio-bson with the MongoDB Driver](./how-to-use-with-mongodb-driver.md).

## Related Pages

- [BsonCodec](./bson-codec.md) describes `transform` and `transformOrFail` in full.
- [BsonEncoder](./bson-encoder.md) and [BsonDecoder](./bson-decoder.md) describe the methods used above.
- [Errors and BsonTrace](./bson-errors.md) explains the messages returned by `mapOrFail` and `transformOrFail`.
