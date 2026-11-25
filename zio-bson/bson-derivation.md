# Derivation

> The `zio-bson-magnolia` module derives encoders, decoders and codecs for case classes and sealed traits. It exposes three objects in the `zio.bson.magnolia` package: `DeriveBsonCodec`, `DeriveBsonEncoder` and `DeriveBsonDecoder`. Most programs only use `DeriveBsonCodec`. This page lists the entry points of each Scala version, because they differ.

## DeriveBsonCodec

`DeriveBsonCodec.derive` builds a `BsonCodec` from an encoder and a decoder derived for the same type. It is the entry point used throughout this documentation:

```scala
import zio.bson._
import zio.bson.magnolia._

case class Point(x: Int, y: Int)

object Point {
  implicit val codec: BsonCodec[Point] = DeriveBsonCodec.derive
}
```

The signature differs by Scala version. In Scala 2 it is a macro that reads the implicit `BsonCodecConfiguration` at the call site. In Scala 3 it requires a `Mirror.Of` instance and the configuration as a `using` parameter:

  

**Scala 2**

```scala
object DeriveBsonCodec {
  def derive[T]: BsonCodec[T] = macro genBoth[T]
}
```

  
  

**Scala 3**

```scala
object DeriveBsonCodec {
  inline def derive[T: Mirror.Of](using BsonCodecConfiguration): BsonCodec[T] = ???
}
```

  

## DeriveBsonEncoder and DeriveBsonDecoder

To derive only one direction, use `DeriveBsonEncoder` or `DeriveBsonDecoder`. The method name differs: Scala 2 uses `DeriveBsonEncoder.derive` and `DeriveBsonDecoder.derive`, and Scala 3 uses `DeriveBsonEncoder.derived` and `DeriveBsonDecoder.derived`.

  

**Scala 2**

```scala
case class Tag(name: String)

object Tag {
  implicit val encoder: BsonEncoder[Tag] = DeriveBsonEncoder.derive
  implicit val decoder: BsonDecoder[Tag] = DeriveBsonDecoder.derive
}
```

  
  

**Scala 3**

```scala
import zio.bson._
import zio.bson.magnolia._

case class Tag(name: String)

object Tag {
  given BsonEncoder[Tag] = DeriveBsonEncoder.derived
  given BsonDecoder[Tag] = DeriveBsonDecoder.derived
}
```

  

The Scala 3 snippet is not compiled with the page, because the documentation is built with Scala 2.13.

## Extension Points

Both versions are built on [Magnolia](https://github.com/softwaremill/magnolia). Code that needs a custom derivation, for instance to change how a case class is written, can reuse the pieces.

In Scala 2, `DeriveBsonEncoder` and `DeriveBsonDecoder` are traits as well as objects. They declare `join` for case classes and `split` for sealed traits, each taking the implicit `BsonCodecConfiguration`, and the objects supply the `DeriveBsonEncoder.derive` and `DeriveBsonDecoder.derive` macros. In Scala 3, `DeriveBsonEncoder.Derive` and `DeriveBsonDecoder.Derive` are classes that take a `BsonCodecConfiguration` and extend Magnolia's `AutoDerivation`. The `DeriveBsonEncoder.Codec` and `DeriveBsonDecoder.Codec` objects have `join` and `split` methods that take the configuration as an explicit argument.

These are advanced APIs. To change the output of derived codecs, prefer the [annotations and configuration](./bson-codec-configuration.md).

## Related Pages

- [Configuration and Annotations](./bson-codec-configuration.md) lists every setting that derivation reads.
- [BsonCodec](./bson-codec.md) describes the value that `DeriveBsonCodec.derive` returns.
- [Write a Codec by Hand](./how-to-write-a-codec-by-hand.md) shows the alternative to derivation.
