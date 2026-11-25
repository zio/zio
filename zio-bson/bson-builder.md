# BsonBuilder

> `BsonBuilder` is a small object of helpers that build `BsonValue` instances from Scala values. It is useful for writing MongoDB filters and updates by hand and for building the expected documents in tests. Except for `BsonBuilder.date`, which uses the `Instant` encoder, each helper wraps one constructor of the MongoDB Java driver, so no encoding or codec is involved.

To bring the helpers into scope:

```scala
import zio.bson._
import zio.bson.BsonBuilder._
```

## Definition

The object has one helper per common BSON type:

```scala
object BsonBuilder {
  def doc(kv: (String, BsonValue)*): BsonDocument
  def element(k: String, v: BsonValue): BsonElement
  def array(elements: BsonValue*): BsonArray
  def str(s: String): BsonString
  def int(i: Int): BsonInt32
  def long(l: Long): BsonInt64
  def bool(b: Boolean): BsonBoolean
  def double(d: Double): BsonDouble
  def date(instant: Instant): BsonValue
  val bNull: BsonNull
}
```

## Building Documents and Arrays

To build a nested document, pass pairs of a key and a `BsonValue` to `BsonBuilder.doc`, and use `BsonBuilder.array` for lists:

```scala
val order = doc(
  "id"    -> long(42L),
  "items" -> array(str("apple"), str("pear")),
  "paid"  -> bool(true),
  "note"  -> bNull
)
// order: org.bson.BsonDocument = {"id": 42, "items": ["apple", "pear"], "paid": true, "note": null}
```

`BsonBuilder.element` builds one `BsonElement`, a key with its value, which some driver APIs take instead of a pair.

## Numbers

`BsonBuilder.int` writes `INT32`, `BsonBuilder.long` writes `INT64`, and `BsonBuilder.double` writes `DOUBLE`. Decoders accept several numeric BSON types, as the [type mappings](./types.md) list. An `Int` decoder reads all three values below, because each one is a whole number that fits:

```scala
doc("a" -> int(1), "b" -> long(1L), "c" -> double(1.0)).as[Map[String, Int]]
// res0: Either[String, Map[String, Int]] = Right(
//   value = Map("a" -> 1, "b" -> 1, "c" -> 1)
// )
```

## Dates

`BsonBuilder.date` converts an `Instant` with the same encoder as `Instant#toBsonValue`, so it writes a `DATE_TIME` with millisecond precision:

```scala
import java.time.Instant

date(Instant.ofEpochMilli(1700000000000L))
// res1: org.bson.BsonValue = BsonDateTime{value=1700000000000}
```

## Related Pages

- [BsonEncoder](./bson-encoder.md) converts any Scala value to a `BsonValue`.
- [BsonDecoder](./bson-decoder.md) reads a `BsonValue` back with `as`.
- [Type Mappings](./types.md) lists the BSON type of each Scala type.
