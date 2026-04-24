# Configuration

> By default, the field names of a case class are used as the JSON fields, but it is easy to override this with an annotation `@jsonField`.

## Field naming

By default, the field names of a case class are used as the JSON fields, but it is easy to override this with an annotation `@jsonField`.

Moreover, you can also mark a whole case class with a member name transformation that will be applied to all members using `@jsonMemberNames` annotation. It takes an argument of type `JsonMemberFormat` which encodes the transformation that will be applied to member names.

Four most popular transformations are already provided: KebabCase, SnakeCase, PascalCase and CamelCase. If you require something more specific you can also use CustomCase which takes a function of shape `String => String` as an argument and can be used to perform any arbitrary transformation. `@jsonField` annotation takes priority over the transformation defined by `@jsonMemberNames`.

Here's an example json with most fields snake_cased and one kebab-cased:
```json
{
  "passion_fruit": true,
  "granny_smith": true,
  "dragon_fruit": true,
  "blood-orange": false
}
```

And here's the target case class:

```scala

@jsonMemberNames(SnakeCase)
case class FruitBasket(
  passionFruit: Boolean, 
  grannySmith: Boolean, 
  dragonFruit: Boolean, 
  @jsonField("blood-orange") bloodOrange: Boolean
)
```

Notice that all fields are camelCased in Scala and will be both encoded and decoded correctly to snake_case in JSON except `bloodOrange` field that is annotated with a `@jsonField` override that will force it to become `"blood-orange"` after serialization.

It is also possible to change the type hint that is used to discriminate case classes with `@jsonHint`.

For example, these annotations change the expected JSON of our `Fruit` family

```scala

sealed trait Fruit

@jsonHint("banaani") case class Banana(
  @jsonField("bendiness") curvature: Double
) extends Fruit

@jsonHint("omena") case class Apple(
  @jsonField("bad") poison: Boolean
) extends Fruit

object Fruit {
  implicit val codec: JsonCodec[Fruit] =
    DeriveJsonCodec.gen[Fruit]
}

val banana: Fruit = Banana(0.5)
// banana: Fruit = Banana(curvature = 0.5)
val apple: Fruit = Apple(false)
// apple: Fruit = Apple(poison = false)
```

from

```json
{"Banana":{"curvature":0.5}}
{"Apple":{"poison":false}}
```

to

```scala
banana.toJson
// res1: String = {"banaani":{"bendiness":0.5}}
```

```scala
apple.toJson
// res2: String = {"omena":{"bad":false}}
```

Another way of changing type hint is using `@jsonHintNames` annotation on sealed class. It allows to apply transformation
to all type hint values in hierarchy. Same transformations are provided as for `@jsonMemberNames` annotation.

Here's an example:

```scala

@jsonHintNames(SnakeCase)
sealed trait FruitKind

case class GoodFruit(good: Boolean) extends FruitKind

case class BadFruit(bad: Boolean) extends FruitKind

object FruitKind {
  implicit val codec: JsonCodec[FruitKind] =
    DeriveJsonCodec.gen[FruitKind]
}

val goodFruit: FruitKind = GoodFruit(true)
// goodFruit: FruitKind = GoodFruit(good = true)
val badFruit: FruitKind = BadFruit(true)
// badFruit: FruitKind = BadFruit(bad = true)

goodFruit.toJson
// res3: String = "{\"good_fruit\":{\"good\":true}}"
badFruit.toJson
// res4: String = "{\"bad_fruit\":{\"bad\":true}}"
```

Note that with this code, you can't directly decode the subclasses of `FruitKind`. You would need to create a dedicated decoder for each subclass.

```scala
object GoodFruit {
  implicit val codec: JsonCodec[GoodFruit] =
    DeriveJsonCodec.gen[GoodFruit]
}
```

Since `GoodFruit` is only a case class, it will not require any kind of discriminator to be decoded.
    
```scala
"""{"good":true}""".fromJson[GoodFruit]
// res5: Either[String, GoodFruit] = Right(value = GoodFruit(good = true))
```

If you want for some reason to decode only for a specific type of `FruitKind` that has a discriminator, don't derive the codec for the subtype, but transform the `FruitKind` codec.

```scala
object BadFruit {
  implicit val decoder: JsonDecoder[BadFruit] =
    FruitKind.codec.decoder.mapOrFail {
        case GoodFruit(_) => Left("Expected BadFruit, got GoodFruit")
        case BadFruit(bad) => Right(BadFruit(bad))
      }
}
```

## jsonDiscriminator

A popular alternative way to encode sealed traits:

```json
{"type":"banaani", "bendiness":0.5}

{"type":"omena", "bad":false}
```

is discouraged for performance reasons. However, if we have no choice in the matter, it may be accommodated with the `@jsonDiscriminator` annotation

```scala
@jsonDiscriminator("type") sealed trait Fruit
```

## Extra fields

We can raise an error if we encounter unexpected fields by using the `@jsonNoExtraFields` annotation on a case class.

```scala
@jsonNoExtraFields case class Watermelon(pips: Int)

object Watermelon {
  implicit val decoder: JsonDecoder[Watermelon] =
    DeriveJsonDecoder.gen[Watermelon]
}
```

```scala
"""{ "pips": 32 }""".fromJson[Watermelon]
// res7: Either[String, Watermelon] = Right(value = Watermelon(pips = 32))
```

```scala
"""{ "pips": 32, "color": "yellow" }""".fromJson[Watermelon]
// res8: Either[String, Watermelon] = Left(value = "(invalid extra field)")
```

## Aliases

    Since zio-json 0.4.3.

After a case class field has changed name, you may still want to read JSON documents that use the old name. This is supported by the `@jsonAliases` annotation.

```scala
case class Strawberry(
  @jsonAliases("seeds") seedCount: Int
)

object Strawberry {
  implicit val decoder: JsonDecoder[Strawberry] =
    DeriveJsonDecoder.gen[Strawberry]
}
```

The following two expressions result in an equal value:
```scala
"""{ "seeds": 32 }""".fromJson[Strawberry]
// res9: Either[String, Strawberry] = Right(value = Strawberry(seedCount = 32))
"""{ "seedCount": 32 }""".fromJson[Strawberry]
// res10: Either[String, Strawberry] = Right(
//   value = Strawberry(seedCount = 32)
// )
```

The `@jsonAliases` annotation supports multiple aliases. The annotation has no effect on encoding.

## Nulls, explicitNulls

By default `null` values are omitted from the JSON output. This behavior can be changed by using the `@jsonExplicitNull` annotation on a case class, field or setting `JsonCodecConfiguration.explicitNulls` to `true`.
Missing nulls on decoding are always allowed.

```scala
@jsonExplicitNull
case class Mango(ripeness: Option[Int])

object Mango {
  implicit val codec: JsonCodec[Mango] = DeriveJsonCodec.gen[Mango]
}
```
The following expression results in a JSON document with a `null` value:
```scala
Mango(None).toJson
// res11: String = "{\"ripeness\":null}"
"""{}""".fromJson[Mango]
// res12: Either[String, Mango] = Right(value = Mango(ripeness = None))
```

## Empty Collections, explicitEmptyCollections

By default `empty collections` (all supported collection types and case classes) are included from the JSON output an decoding requires empty collections to be present. This behavior can be changed by using the `@jsonExplicitEmptyCollections(encoding = false, decoding = false)` annotation on a case class, field or setting `JsonCodecConfiguration.explicitEmptyCollections` to `ExplicitEmptyCollections(encoding = false, decoding = false)`. The result is that empty collections are omitted from the JSON output and when decoding empty collections are created. It is also possible to have different values for encoding and decoding by using `@jsonExplicitEmptyCollections(encoding = true, decoding = false)` or `@jsonExplicitEmptyCollections(encoding = false, decoding = true)`.

```scala
@jsonExplicitEmptyCollections(encoding = false, decoding = false)
case class Pineapple(leaves: List[String])

object Pineapple {
  implicit val codec: JsonCodec[Pineapple] = DeriveJsonCodec.gen[Pineapple]
}
```
The following expression results in a JSON document with an empty collection:
```scala
Pineapple(Nil).toJson
// res13: String = "{}"
"""{}""".fromJson[Pineapple]
// res14: Either[String, Pineapple] = Right(value = Pineapple(leaves = List()))
```

## @jsonDerive

**Requires zio-json-macros**

`@jsonDerive` allows to reduce that needs to be written using an annotation macro to generate JsonDecoder/JsonEncoder at build-time.

For generating both Encoder and Decoder, simply use jsonDerive

For example: 

```scala

@jsonDerive case class Watermelon(pips: Int)
```
It is equivalent to:

```scala

case class Watermelon(pips: Int)

object Watermelon {
  implicit val codec: JsonCodec[Watermelon] =
    DeriveJsonCodec.gen[Watermelon]
}
```

To generate only an encoder, we can set it as config parameter:

For example:

```scala

@jsonDerive(JsonDeriveConfig.Encoder) case class Watermelon(pips: Int)
```
It is equivalent to:

```scala

case class Watermelon(pips: Int)

object Watermelon {
  implicit val encoder: JsonEncoder[Watermelon] =
    DeriveJsonEncoder.gen[Watermelon]
}
```

To generate only a decoder, we can set it as config parameter:

For example:

```scala modc:compile-only

@jsonDerive(JsonDeriveConfig.Decoder) case class Watermelon(pips: Int)
```
It is equivalent to:

```scala

case class Watermelon(pips: Int)

object Watermelon {
  implicit val decoder: JsonDecoder[Watermelon] =
    DeriveJsonDecoder.gen[Watermelon]
}
```
