# BsonDecoder

> `BsonDecoder[A]` is the typeclass that reads BSON and produces a Scala value of type `A`. Every decoding call either returns the value or a `BsonDecoder.Error` that says where in the document the problem is.

## Overview

As with encoders, you usually get a decoder from a `BsonCodec`. This page covers the public methods, the error type, and the operations that build new decoders from existing ones.

## Definition

The trait has these members that users call:

```scala
trait BsonDecoder[A] {
  def decode(reader: BsonReader): Either[BsonDecoder.Error, A]
  def fromBsonValue(value: BsonValue): Either[BsonDecoder.Error, A]
  def map[B](f: A => B): BsonDecoder[B]
  def mapOrFail[B](f: A => Either[String, B]): BsonDecoder[B]
}
```

`decode` reads from a `BsonReader`, the streaming API used by the MongoDB driver. `fromBsonValue` reads from an in-memory `BsonValue`. Both return `Left` with a `BsonDecoder.Error` when the input does not match.

The members with `Unsafe` in their names, such as `decodeUnsafe` and `fromBsonValueUnsafe`, throw instead of returning `Left`. They exist for the derived decoders and for code that needs to avoid allocation. Use the safe methods in application code.

## Decoding a BsonValue

The syntax method `as` from the `zio.bson` package calls `fromBsonValue` and converts the error to a `String`:

```scala
import zio.bson._
import zio.bson.BsonBuilder._
```

To read an `Int` from a `BsonValue`:

```scala
println(int(42).as[Int])
// Right(42)
```

A `BsonDecoder` also accepts other numeric BSON types for the same Scala type. For example, a `DOUBLE` with no fractional part decodes to an `Int`:

```scala
println(double(7.0).as[Int])
// Right(7)
println(double(7.5).as[Int])
// Left(: Could not convert DOUBLE(7.5) to a Int without losing precision.)
```

The second line fails because `7.5` cannot be stored in an `Int` without losing precision. The full list of accepted BSON types for each Scala type is in [Type Mappings](./types.md).

## Decoding from a BsonReader

To decode directly from the driver's stream, call `decode` with a `BsonReader`. The example below reads the value of the `count` field from a `BsonDocumentReader`, which is a reader over an in-memory document. The reader must be positioned on the value, so the code first moves past the start of the document and the field name:

```scala
import org.bson.BsonDocumentReader

val reader = new BsonDocumentReader(doc("count" -> int(3)))
reader.readStartDocument()
reader.readName()
```

```scala
println(BsonDecoder[Int].decode(reader))
// Right(3)
```

For real code, the derived codec for a case class already calls `decode` for you when the MongoDB driver is configured. See [Use zio-bson with the MongoDB Driver](./how-to-use-with-mongodb-driver.md).

## Transforming Decoders

`map` changes the result type of a decoder. The function you pass runs after the original decoder and cannot fail:

```scala
final case class Celsius(degrees: Double)

val celsiusDecoder: BsonDecoder[Celsius] = BsonDecoder[Double].map(Celsius(_))
```

```scala
println(double(21.5).as(celsiusDecoder))
// Right(Celsius(21.5))
```

`mapOrFail` works the same way, but the function returns `Either[String, B]`. Use it when a value must pass a check, such as a range:

```scala
final case class Percent(value: Int)

val percentDecoder: BsonDecoder[Percent] =
  BsonDecoder[Int].mapOrFail { n =>
    if (n >= 0 && n <= 100) Right(Percent(n))
    else Left(s"Expected a percentage between 0 and 100, but got $n.")
  }
```

```scala
println(int(150).as(percentDecoder))
// Left(: Expected a percentage between 0 and 100, but got 150.)
```

When `mapOrFail` returns `Left`, the message becomes a `BsonDecoder.Error` at the current path, so the caller sees the field name as well.

For a full encoder and decoder pair, use `BsonCodec.transformOrFail`, described in [BsonCodec](./bson-codec.md).

## Missing Fields

A decoder can say what happens when a field is absent from a document. The default is an error. `Option` overrides this and returns `None`. The derived case-class decoder calls this method for each field that is missing from the input. You can override `decodeMissingUnsafe` only when you write a decoder by hand.

## Errors

Every failure is a `BsonDecoder.Error`, a case class with two fields:

- `trace`: a list of `BsonTrace` steps that points from the root of the value to the failing part.
- `message`: a human-readable reason.

The `render` method combines both into one string. See [Errors and BsonTrace](./bson-errors.md) for the full format.

## Predefined Instances

The `BsonDecoder` companion object has a decoder for every type listed in [BsonEncoder](./bson-encoder.md), with the same set of accepted BSON types per type. Each one is documented in [Type Mappings](./types.md).

## Decoder Context and Helpers

You rarely need these directly; they matter when writing a decoder with `decodeUnsafe` and `fromBsonValueUnsafe` instead of wrapping an existing one with `BsonDecoder#map` or `BsonDecoder#mapOrFail`.

`BsonDecoder.BsonDecoderContext` carries the state that a decoder passes down to nested decoders. Its only field, `ignoreExtraField`, names one field that the enclosing derived decoder must not report as unexpected. Derived decoders use it to skip the discriminator field when `bsonNoExtraFields` is active. `BsonDecoder.BsonDecoderContext.default` is the empty context that `BsonDecoder#decode` and `BsonDecoder#fromBsonValue` start with:

```scala
object BsonDecoder {
  case class BsonDecoderContext(ignoreExtraField: Option[String] = None)
}
```

The `DecoderUtils` object in the `zio.bson` package has three helpers for failing with the right trace:

```scala
object DecoderUtils {
  def unsafeCall[R](trace: List[BsonTrace])(call: => R): R
  def throwInvalidType(trace: List[BsonTrace], expected: BsonType, actual: BsonType): Nothing
  def assumeType[T](trace: List[BsonTrace])(tpe: BsonType, value: BsonValue)(f: BsonValue => T): T
}
```

`DecoderUtils.unsafeCall` runs a reader call and turns a driver `BsonInvalidOperationException` or `IllegalStateException` into a `BsonDecoder.Error` with the given trace. `DecoderUtils.throwInvalidType` throws the `Expected BSON type X, but got Y.` error. `DecoderUtils.assumeType` checks the BSON type of a value before running a conversion.

## Related Pages

- [BsonEncoder](./bson-encoder.md) writes values.
- [BsonCodec](./bson-codec.md) bundles an encoder and a decoder.
- [Errors and BsonTrace](./bson-errors.md) explains the error format.
