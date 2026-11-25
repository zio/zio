# Use Your Own Type as a Map Key

> Encode and decode a Map whose keys are dates, wrapper types or enumerations by writing a JsonFieldEncoder and JsonFieldDecoder pair.

Goal: write and read a `Map` whose keys are your own types, such as `java.time.LocalDate`, a wrapper around an `Int`, or a sealed-trait enumeration.

A JSON object key is always a string, so ZIO JSON writes a `Map[K, V]` as an object only when it knows how to turn a `K` into a `String` and back. Out of the box it knows this for `String`, `Int`, `Long` and `java.util.UUID` keys. For any other key type you write two small instances, a `JsonFieldEncoder[K]` and a `JsonFieldDecoder[K]`, usually in two lines each. In this guide we build a sales report with three maps, keyed by day, by store and by sales channel, and then check that it survives a round trip.

Steps 2 to 4 are alternatives, one per kind of key. Follow the one that matches your key type, then continue with steps 5 and 6.

## What You Need

Only the core module:

```scala
libraryDependencies += "dev.zio" %% "zio-json" % "1.1.3"
```

All code on this page uses these imports:

```scala
import zio.json._

import java.time.LocalDate
import scala.util.Try
```

The guide assumes you know how to derive a codec for a case class (see the [Getting Started](../getting-started.md) tutorial). The [JsonFieldEncoder and JsonFieldDecoder](../reference/json-field-codecs.md) reference page describes both type classes in full.

## 1. Recognize the Missing Key Instance

The first sign of a missing key instance is a compile error. Encoding or decoding a `Map[LocalDate, Int]` does not compile, because `LocalDate` has no key instances:

```scala
Map(LocalDate.of(2024, 3, 1) -> 12).toJson

"""{"2024-03-01":12}""".fromJson[Map[LocalDate, Int]]
// error: could not find implicit value for parameter encoder: zio.json.JsonEncoder[scala.collection.immutable.Map[java.time.LocalDate,Int]]
// Map(LocalDate.of(2024, 3, 1) -> 12).toJson
// ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
// error: could not find implicit value for parameter decoder: zio.json.JsonDecoder[Map[java.time.LocalDate,Int]]
// """{"2024-03-01":12}""".fromJson[Map[LocalDate, Int]]
// ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
```

The error names the encoder or decoder for the whole map, `JsonEncoder[Map[LocalDate,Int]]`, and not the `JsonFieldEncoder[LocalDate]` that is actually missing. The values are not the problem: `Int` has a `JsonEncoder`. When the map is a field of a case class, the Scala 2 derivation macro reports the field instead, for example `magnolia: could not find zio.json.DeriveJsonEncoder.Typeclass[Map[java.time.LocalDate,Int]] in parameter 'byDay'`. In both cases the fix is the same: provide a `JsonFieldEncoder` and a `JsonFieldDecoder` for the key type.

## 2. Write a Key Codec for `LocalDate`

A key encoder turns a key into the string written as the object key, and a key decoder turns that string back into a key or fails. Both are easiest to build from the `String` instances. `JsonFieldEncoder#contramap` takes a function from your key to `String`, and `JsonFieldDecoder#mapOrFail` takes a function from `String` to `Either[String, K]`, where a `Left` is the error message:

```scala
implicit val localDateKeyEncoder: JsonFieldEncoder[LocalDate] =
  JsonFieldEncoder[String].contramap(_.toString)

implicit val localDateKeyDecoder: JsonFieldDecoder[LocalDate] =
  JsonFieldDecoder[String].mapOrFail { s =>
    Try(LocalDate.parse(s)).toOption.toRight(s"expected a date like 2024-03-01, got '$s'")
  }
```

`LocalDate#toString` and `LocalDate.parse` both use the ISO format, the same text the built-in `JsonEncoder[LocalDate]` writes for values, so keys and values look alike. With the two instances in scope, the map encodes and decodes, and an invalid date fails with our message at the key's path:

```scala
println(Map(LocalDate.of(2024, 3, 1) -> 12, LocalDate.of(2024, 3, 2) -> 9).toJson)
// {"2024-03-01":12,"2024-03-02":9}

"""{"2024-03-01":12,"2024-03-02":9}""".fromJson[Map[LocalDate, Int]]
// res3: Either[String, Map[LocalDate, Int]] = Right(
//   value = Map(2024-03-01 -> 12, 2024-03-02 -> 9)
// )

"""{"2024-02-30":12}""".fromJson[Map[LocalDate, Int]]
// res4: Either[String, Map[LocalDate, Int]] = Left(
//   value = ".2024-02-30(expected a date like 2024-03-01, got '2024-02-30')"
// )
```

`LocalDate` belongs to the JDK, so its key instances cannot go in its companion object. Keep them in an object of your own and import them where you need them, as [Encode a Type You Do Not Own](third-party-types.md) describes.

## 3. Write a Key Codec for a Wrapper Type

Wrapper types (sometimes called newtypes) give an `Int` or a `String` a name of its own, so the compiler keeps a store id apart from a quantity. A wrapper reuses the key instances of the type it wraps: `JsonFieldEncoder#contramap` unwraps, and `JsonFieldDecoder#map` wraps. Since we own `StoreId`, the instances go in its companion object, where the compiler finds them without an import:

```scala
final case class StoreId(value: Int)

object StoreId {
  implicit val keyEncoder: JsonFieldEncoder[StoreId] = JsonFieldEncoder[Int].contramap(_.value)
  implicit val keyDecoder: JsonFieldDecoder[StoreId] = JsonFieldDecoder[Int].map(StoreId(_))
}
```

`JsonFieldDecoder#map` cannot fail, but the `Int` key decoder underneath still checks that the key is a number:

```scala
println(Map(StoreId(7) -> 40, StoreId(12) -> 25).toJson)
// {"7":40,"12":25}

"""{"7":40,"12":25}""".fromJson[Map[StoreId, Int]]
// res6: Either[String, Map[StoreId, Int]] = Right(
//   value = Map(StoreId(value = 7) -> 40, StoreId(value = 12) -> 25)
// )

"""{"seven":40}""".fromJson[Map[StoreId, Int]]
// res7: Either[String, Map[StoreId, Int]] = Left(
//   value = ".seven(Invalid Int: seven)"
// )
```

The same two lines work when `StoreId` is a value class (`final case class StoreId(value: Int) extends AnyVal`). To also validate the wrapped value, for example to reject negative ids, use `JsonFieldDecoder#mapOrFail` instead of `JsonFieldDecoder#map`.

## 4. Write a Key Codec for a Sealed-Trait Enumeration

For a sealed trait of case objects, the key encoder writes each case's name and the key decoder looks the name up. An unknown name must fail, and a good error message lists the names that are accepted:

```scala
sealed trait Channel

object Channel {
  case object Web   extends Channel
  case object Store extends Channel
  case object Phone extends Channel

  val values: List[Channel] = List(Web, Store, Phone)

  def name(channel: Channel): String = channel.toString.toLowerCase

  implicit val keyEncoder: JsonFieldEncoder[Channel] =
    JsonFieldEncoder[String].contramap(name)

  implicit val keyDecoder: JsonFieldDecoder[Channel] =
    JsonFieldDecoder[String].mapOrFail { s =>
      values.find(name(_) == s).toRight(s"unknown channel '$s', expected one of: ${values.map(name).mkString(", ")}")
    }
}
```

Known names decode to the case objects, and the first unknown name stops decoding with our message:

```scala
println(Map[Channel, Int](Channel.Web -> 30, Channel.Store -> 35).toJson)
// {"web":30,"store":35}

"""{"web":30,"store":35}""".fromJson[Map[Channel, Int]]
// res9: Either[String, Map[Channel, Int]] = Right(
//   value = Map(Web -> 30, Store -> 35)
// )

"""{"web":30,"fax":1}""".fromJson[Map[Channel, Int]]
// res10: Either[String, Map[Channel, Int]] = Left(
//   value = ".fax(unknown channel 'fax', expected one of: web, store, phone)"
// )
```

Matching is exact, so `"Web"` is an unknown key here. If you use Enumeratum, its interop module builds these instances for you (see [Enumeratum Interop](../interop/enumeratum.md)).

## 5. Use the Keys in a Derived Codec

Derivation picks up the key instances for every `Map` field, so the report needs nothing beyond the instances from the previous steps:

```scala
final case class SalesReport(
  byDay: Map[LocalDate, Int],
  byStore: Map[StoreId, Int],
  byChannel: Map[Channel, Int]
)

object SalesReport {
  implicit val codec: JsonCodec[SalesReport] = DeriveJsonCodec.gen[SalesReport]
}

val report = SalesReport(
  byDay = Map(LocalDate.of(2024, 3, 1) -> 12, LocalDate.of(2024, 3, 2) -> 9),
  byStore = Map(StoreId(7) -> 15, StoreId(12) -> 6),
  byChannel = Map(Channel.Web -> 13, Channel.Store -> 8)
)
```

The report encodes to nested objects, and a bad key deep inside it is reported with the full path, `.byChannel.fax`:

```scala
println(report.toJsonPretty)
// {
//   "byDay" : {
//     "2024-03-01" : 12,
//     "2024-03-02" : 9
//   },
//   "byStore" : {
//     "7" : 15,
//     "12" : 6
//   },
//   "byChannel" : {
//     "web" : 13,
//     "store" : 8
//   }
// }

"""{"byDay":{},"byStore":{},"byChannel":{"fax":1}}""".fromJson[SalesReport]
// res12: Either[String, SalesReport] = Left(
//   value = ".byChannel.fax(unknown channel 'fax', expected one of: web, store, phone)"
// )
```

## 6. Check the Round Trip

A key encoder and key decoder must agree: every string the encoder writes must decode back to the same key. The quickest check is to encode a value and decode the result:

```scala
report.toJson.fromJson[SalesReport] == Right(report)
// res13: Boolean = true
```

Run the same check in your tests, with keys that cover every case of an enumeration and the edge cases of your format, such as the first and last day of a year. A property-based test that generates keys catches a mismatch that a handful of examples miss.

## Pitfalls

A map round-trips only when its keys and values do, and three cases break that silently, without a compile error or a decoding error. The [Behaviour Notes](../reference/json-field-codecs.md#behaviour-notes) on the reference page list them with the other rules for map keys.

### Duplicate Keys: The Last One Wins

JSON allows the same key twice in an object. Decoding into a `Map` keeps the last value and reports no error:

```scala
"""{"byDay":{"2024-03-01":12,"2024-03-01":20},"byStore":{},"byChannel":{}}""".fromJson[SalesReport]
// res14: Either[String, SalesReport] = Right(
//   value = SalesReport(
//     byDay = Map(2024-03-01 -> 20),
//     byStore = Map(),
//     byChannel = Map()
//   )
// )
```

If duplicates must be rejected or kept, decode the object with `JsonDecoder.keyValueChunk` or into a `Json.Obj`, and check the entries yourself.

### Keys That Collide After Decoding

Two different strings can decode to the same key. The `Int` key decoder that `StoreId` reuses reads `"7"`, `"07"` and `"+7"` as the same number, so these entries collapse into one, and the last one wins again:

```scala
"""{"7":15,"07":4}""".fromJson[Map[StoreId, Int]]
// res15: Either[String, Map[StoreId, Int]] = Right(
//   value = Map(StoreId(value = 7) -> 4)
// )
```

A strict format avoids this: `LocalDate.parse` accepts only one spelling of each date. For a numeric key, use `JsonFieldDecoder#mapOrFail` and reject a key whose text differs from the encoded form of the number it decodes to.

### `None` Values Are Dropped on Encode

The map encoder skips every entry whose value encodes to nothing, which for an `Option` value means `None`. The key disappears from the JSON, so the decoded map has fewer entries than the original:

```scala
val withGap = Map(LocalDate.of(2024, 3, 1) -> Some(12), LocalDate.of(2024, 3, 2) -> None)
// withGap: Map[LocalDate, Option[Int]] = Map(
//   2024-03-01 -> Some(value = 12),
//   2024-03-02 -> None
// )

println(withGap.toJson)
// {"2024-03-01":12}

withGap.toJson.fromJson[Map[LocalDate, Option[Int]]]
// res17: Either[String, Map[LocalDate, Option[Int]]] = Right(
//   value = Map(2024-03-01 -> Some(value = 12))
// )
```

Use `Map#getOrElse` or `Map#get` when reading such a map, so a missing key and a `None` value mean the same thing, or store a non-optional placeholder instead of `None`.

## Scala 3: String Subtypes Need No Key Decoder

On Scala 3, a key type that is a subtype of `String` and has a `JsonDecoder` needs no key instances at all. The encoder side is covered because `JsonFieldEncoder` is contravariant, so the `String` key encoder also serves every subtype of `String`. The decoder side is covered by a low-priority Scala 3 instance that decodes the key with the type's `JsonDecoder`.

A union of string literals has a built-in `JsonDecoder` on Scala 3, so it works as a key type as it is:

```scala
type Channel = "web" | "store" | "phone"

final case class ChannelSales(byChannel: Map[Channel, Int]) derives JsonCodec
```

An opaque type declared with an upper bound of `String` is a subtype of `String` too, so a `JsonDecoder` for it is enough. Its validation runs on keys as well as on values:

```scala
object Skus {
  opaque type Sku <: String = String

  def apply(code: String): Either[String, Sku] =
    if (code.matches("[A-Z]{3}-[0-9]{3}")) Right(code) else Left(s"not a SKU: $code")

  given JsonDecoder[Sku] = JsonDecoder.string.mapOrFail(apply)
}
import Skus.Sku

final case class Stock(bySku: Map[Sku, Int]) derives JsonCodec
```

Running the example prints:

```text
union encode: {"byChannel":{"web":13,"store":8}}
union decode: Right(ChannelSales(Map(web -> 13, store -> 8)))
union bad key: Left(.byChannel.fax(expected one of: web, store, phone))
opaque encode: {"bySku":{"ABC-123":4}}
opaque decode: Right(Stock(Map(ABC-123 -> 4)))
opaque bad key: Left(.bySku.abc(not a SKU: abc))
```

This instance is Scala 3 only. On Scala 2, every key type other than `String`, `Int`, `Long` and `UUID` needs the instances shown in the steps above, and a Scala 3 `enum` or an opaque type without the `<: String` bound needs them too.

This instance does not escape the key again before decoding it, so a key that contains `"` or `\` is not read correctly. For example, the key `a"b` is cut to `a`. If your keys can contain these characters, write a `JsonFieldDecoder` with `mapOrFail` as in the steps above. See [Scala 3: Keys That Are Subtypes of `String`](../reference/json-field-codecs.md#scala-3-keys-that-are-subtypes-of-string).

## See Also

- [JsonFieldEncoder and JsonFieldDecoder](../reference/json-field-codecs.md) for the full API, the built-in key types and the map types that use key instances.
- [Built-in Instances](../reference/built-in-instances.md#maps) for the map types and how they are written.
- [Encode a Type You Do Not Own](third-party-types.md) for where to put instances of a type from another library.
- [Errors](../reference/errors.md) for how error paths such as `.byChannel.fax` are built.
- [Enumeratum Interop](../interop/enumeratum.md) and [Refined Interop](../interop/refined.md) for key instances those modules provide.
