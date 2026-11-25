# Build an Encoder or Decoder from an Existing One

> Adapt an existing JsonDecoder, JsonEncoder or JsonCodec to your own type with map, mapOrFail, contramap, transform and transformOrFail.

Goal: give one of your types JSON support by adapting an encoder or decoder that already exists, instead of deriving a new one or writing one by hand.

This works whenever your type's JSON is the JSON of a type that already has an instance: a wrapper written as a bare number, a string that must pass a check, or a case class written as an array. Steps 1 to 3 build a decoder and an encoder separately, step 4 bundles them into codecs, and step 5 uses those codecs in a derived case class. Steps 6 and 7 apply the same methods to arrays and to map keys. When no existing instance has the shape you need, write the instance by hand instead, as shown in [Write a Fast Encoder or Decoder by Hand](./low-level-codecs.md).

## What You Need

The core module. Steps 2, 4 and 5 also use the [refined](https://github.com/fthomas/refined) library for a validation function, but any function that returns an `Either[String, A]` works the same way:

```scala
libraryDependencies ++= Seq(
  "dev.zio"    %% "zio-json" % "1.1.3",
  "eu.timepit" %% "refined"  % "0.11.4" // only for the validation in steps 2, 4 and 5
)
```

The examples model a fruit stock. A `FruitCount` wraps an `Int`, and a `FruitName` is a string that must not be empty:

```scala
import zio.json._

import eu.timepit.refined.refineV
import eu.timepit.refined.api.Refined
import eu.timepit.refined.collection.NonEmpty

final case class FruitCount(value: Int)

type FruitName = String Refined NonEmpty
```

## 1. Reuse a Decoder with `JsonDecoder#map`

A derived decoder for `FruitCount` expects an object, `{"value":3}`. To read a bare `3` instead, start from the `Int` decoder and wrap each result with `JsonDecoder#map`:

```scala
val countDecoder: JsonDecoder[FruitCount] = JsonDecoder[Int].map(FruitCount(_))
```

The decoder now reads a plain number:

```scala
countDecoder.decodeJson("3")
// res1: Either[String, FruitCount] = Right(value = FruitCount(value = 3))
```

Use `JsonDecoder#map` only when the conversion cannot fail. A function that throws is not turned into a decode error: the exception escapes from `JsonDecoder#decodeJson`.

## 2. Reject Bad Values with `JsonDecoder#mapOrFail`

When the conversion can fail, use `JsonDecoder#mapOrFail`. Its function returns an `Either[String, B]`, and a `Left` becomes the decode error. Refined's `refineV` already has that shape:

```scala
val nameDecoder: JsonDecoder[FruitName] = JsonDecoder[String].mapOrFail(refineV[NonEmpty](_))
```

A non-empty string decodes, and an empty one fails with refined's message:

```scala
nameDecoder.decodeJson("\"apple\"")
// res2: Either[String, FruitName] = Right(value = apple)

nameDecoder.decodeJson("\"\"")
// res3: Either[String, FruitName] = Left(
//   value = "(Predicate isEmpty() did not fail.)"
// )
```

[Validate Data While Decoding](./validate-on-decode.md) covers validation in full: hand-written checks, rules across several fields, and how to read the error path. For refined types you do not need to write these instances at all: the `zio-json-interop-refined` module supplies them for every `A Refined P`, see [Refined Interop](../interop/refined.md).

## 3. Build the Encoder Side with `JsonEncoder#contramap`

Encoders go the other way. `JsonEncoder#contramap` takes a function from your type to a type that already has an encoder:

```scala
val countEncoder: JsonEncoder[FruitCount] = JsonEncoder[Int].contramap(_.value)

val nameEncoder: JsonEncoder[FruitName] = JsonEncoder[String].contramap(_.value)
```

The count is written as a bare number, the same JSON the decoder from step 1 reads:

```scala
println(countEncoder.encodeJson(FruitCount(3), None))
// 3
```

The function passed to `JsonEncoder#contramap` cannot fail, so every value of your type must have a JSON form.

## 4. Combine Both Halves into a Codec with `JsonCodec#transform`

`JsonCodec#transform` takes both functions at once: the first one maps the decoded value, as `JsonDecoder#map` does, and the second one feeds the encoder, as `JsonEncoder#contramap` does. `JsonCodec#transformOrFail` is the same, except that the first function may fail, as in `JsonDecoder#mapOrFail`. For a type you own, put the codec in its companion object. `FruitName` is a type alias with no companion, so here both codecs go in an object that the next step imports:

```scala
object StockCodecs {
  implicit val countCodec: JsonCodec[FruitCount] =
    JsonCodec[Int].transform(FruitCount(_), _.value)

  implicit val nameCodec: JsonCodec[FruitName] =
    JsonCodec[String].transformOrFail(refineV[NonEmpty](_), _.value)
}
```

When you already have both halves, as after steps 1 and 3, `JsonCodec(countEncoder, countDecoder)` builds the same codec from them.

Keep the encoder and the decoder as separate instances instead when the two directions differ, for example when the decoder accepts several input shapes but the encoder always writes one, or when a type is only ever encoded or only ever decoded.

## 5. Use the Instances in a Derived Codec

A case class whose fields have these types derives its codec as usual, once the instances are in scope:

```scala
final case class Stock(name: FruitName, count: FruitCount)

object Stock {
  import StockCodecs._

  implicit val codec: JsonCodec[Stock] = DeriveJsonCodec.gen[Stock]
}
```

The fields are read and written as bare values, and an empty name fails with the path of its field:

```scala
"""{"name":"apple","count":3}""".fromJson[Stock].map(_.toJson)
// res5: Either[String, String] = Right(
//   value = "{\"name\":\"apple\",\"count\":3}"
// )

"""{"name":"","count":3}""".fromJson[Stock]
// res6: Either[String, Stock] = Left(
//   value = ".name(Predicate isEmpty() did not fail.)"
// )
```

## 6. Write a Case Class as an Array

Tuples have codecs that read and write JSON arrays, so transforming through a tuple turns a case class into an array of its fields:

```scala
final case class Crate(fruit: String, count: Int, organic: Boolean)

object Crate {
  implicit val codec: JsonCodec[Crate] =
    JsonCodec[(String, Int, Boolean)].transform(
      { case (fruit, count, organic) => Crate(fruit, count, organic) },
      c => (c.fruit, c.count, c.organic)
    )
}
```

The crate is written and read as a three-element array:

```scala
println(Crate("apple", 12, true).toJson)
// ["apple",12,true]

"""["pear",6,false]""".fromJson[Crate]
// res8: Either[String, Crate] = Right(
//   value = Crate(fruit = "pear", count = 6, organic = false)
// )
```

An array is smaller than an object with named fields, but it is a strict format: adding a field changes the length of the array, so readers built for the old length fail.

## 7. Adapt Key Instances for Map Keys

A type used as a `Map` key needs a `JsonFieldEncoder` and a `JsonFieldDecoder` instead, because JSON object keys are always strings. They are built the same way, with `JsonFieldEncoder#contramap`, `JsonFieldDecoder#map` and `JsonFieldDecoder#mapOrFail`:

```scala
implicit val countKeyEncoder: JsonFieldEncoder[FruitCount] = JsonFieldEncoder[Int].contramap(_.value)

implicit val countKeyDecoder: JsonFieldDecoder[FruitCount] = JsonFieldDecoder[Int].map(FruitCount(_))
```

A map keyed by `FruitCount` now encodes and decodes:

```scala
println(Map(FruitCount(3) -> "apple").toJson)
// {"3":"apple"}

"""{"3":"apple"}""".fromJson[Map[FruitCount, String]]
// res10: Either[String, Map[FruitCount, String]] = Right(
//   value = Map(FruitCount(value = 3) -> "apple")
// )
```

[Use Your Own Type as a Map Key](./custom-map-keys.md) walks through key types such as dates and enumerations.

## See Also

- [JsonDecoder: `map` and `mapOrFail`](../reference/json-decoder.md#map-and-maporfail), [JsonEncoder: `contramap`](../reference/json-encoder.md#contramap) and [JsonCodec: Transforming a Codec](../reference/json-codec.md#transforming-a-codec) describe each method.
- [Encode a Type You Do Not Own](./third-party-types.md) uses these methods for a class from another library and shows where to put the instances.
- [Control the JSON a Type Encodes To](./encoding.md) covers the encoder-only choices, including annotations and sealed traits.
- [JsonFieldEncoder and JsonFieldDecoder](../reference/json-field-codecs.md) describes the key instances.
