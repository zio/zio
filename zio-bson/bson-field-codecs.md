# Field Codecs

> A BSON document has string keys only. When a Scala `Map` has keys of another type, such as `Int`, ZIO Bson needs a way to turn each key into a string and back. Two typeclasses do this: `BsonFieldEncoder[A]` turns a key into a string, and `BsonFieldDecoder[A]` turns a string into a key.

## Overview

You need these only for `Map` keys. Ordinary fields use `BsonEncoder` and `BsonDecoder`.

## Definition

```scala
trait BsonFieldEncoder[-A] {
  def unsafeEncodeField(in: A): String
  def contramap[B](f: B => A): BsonFieldEncoder[B]
}

trait BsonFieldDecoder[+A] {
  def unsafeDecodeField(trace: List[BsonTrace], in: String): A
  def map[B](f: A => B): BsonFieldDecoder[B]
  def mapOrFail[B](f: A => Either[String, B]): BsonFieldDecoder[B]
}
```

The `unsafe` methods throw on failure, which is how the `Map` codecs use them. `contramap` and `map` build new instances from existing ones in the same way as `BsonEncoder` and `BsonDecoder`.

## Predefined Instances

ZIO Bson provides field codecs for three key types:

- `String`: the key is used as is.
- `Int`: the key is written with `toString` and read with `toInt`.
- `Long`: the key is written with `toString` and read with `toLong`.

A `Map[K, V]` encoder and decoder require both a field codec for `K` and a value codec for `V`. The keys in the output are the string forms of the keys:

```scala
import zio.bson._
import zio.bson.BsonBuilder._
```

```scala
println(BsonEncoder[Map[Int, String]].toBsonValue(Map(1 -> "one", 2 -> "two")))
// {"1": "one", "2": "two"}
```

Reading the document back gives the original keys:

```scala
println(BsonDecoder[Map[Int, String]].fromBsonValue(doc("1" -> str("one"), "2" -> str("two"))))
// Right(Map(1 -> one, 2 -> two))
```

A key that is not a number fails with the field's path:

```scala
println(BsonDecoder[Map[Int, String]].fromBsonValue(doc("one" -> str("x"))))
// Left(zio.bson.BsonDecoder$Error: Path: .one, error: Invalid Int: 'one': java.lang.NumberFormatException: For input string: "one". Don't use `decodeUnsafe` and `fromBsonValueUnsafe` methods.)
```

## Custom Key Types

For another key type, build a field encoder and decoder from the `String` instances with `contramap` and `mapOrFail`. The next example uses a `Status` key that is stored by its lowercase name:

```scala
sealed trait Status
object Status {
  case object Active   extends Status
  case object Inactive extends Status
}

implicit val statusFieldEncoder: BsonFieldEncoder[Status] =
  BsonFieldEncoder[String].contramap {
    case Status.Active   => "active"
    case Status.Inactive => "inactive"
  }

implicit val statusFieldDecoder: BsonFieldDecoder[Status] =
  BsonFieldDecoder[String].mapOrFail {
    case "active"   => Right(Status.Active)
    case "inactive" => Right(Status.Inactive)
    case other      => Left(s"Unknown status: $other")
  }
```

```scala
println(BsonEncoder[Map[Status, Int]].toBsonValue(Map(Status.Active -> 3)))
// {"active": 3}
```

The `Map` codec then works for `Map[Status, Int]` with no extra code.

## Related Pages

- [BsonEncoder](./bson-encoder.md) describes the encoder for the values of the map.
- [BsonDecoder](./bson-decoder.md) describes the decoder for the values of the map.
- [Type Mappings](./types.md) lists the BSON type of a `Map`.
