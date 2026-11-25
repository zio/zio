# Sealed Traits

> Control how ZIO JSON encodes sealed traits: type hints, hint name formats, a discriminator field, and enums as strings.

Goal: change how a sealed trait and its subtypes look in JSON.

By default, a value of a sealed family is written as an object with one key, the short class name, whose value is the encoded subtype:

```json
{"Banana":{"curvature":0.5}}
```

This page shows how to change the key (the type hint), how to use a discriminator field instead, and how case objects can become plain strings. Each step is a separate option. Use the ones you need.

## What you need

Every example on this page uses one import:

```scala
import zio.json._
```

## 1. Change a type hint with `@jsonHint`

Put `@jsonHint` on a subtype to choose its key:

```scala
sealed trait Fruit

@jsonHint("banaani") case class Banana(curvature: Double) extends Fruit
@jsonHint("omena") case class Apple(poison: Boolean)      extends Fruit

object Fruit {
  implicit val codec: JsonCodec[Fruit] = DeriveJsonCodec.gen[Fruit]
}
```

The keys are now `banaani` and `omena`:

```scala
println((Banana(0.5): Fruit).toJson)
// {"banaani":{"curvature":0.5}}
println((Apple(false): Fruit).toJson)
// {"omena":{"poison":false}}
```

## 2. Transform all hints with `@jsonHintNames`

`@jsonHintNames` goes on the sealed trait and applies a `JsonMemberFormat` to every hint in the family. It accepts the same formats as `@jsonMemberNames`, which are listed in [Field names](field-names.md). A `@jsonHint` on a subtype wins over the format. To write every hint of a family in snake_case:

```scala
@jsonHintNames(SnakeCase)
sealed trait FruitKind

case class GoodFruit(good: Boolean) extends FruitKind
case class BadFruit(bad: Boolean)   extends FruitKind

object FruitKind {
  implicit val codec: JsonCodec[FruitKind] = DeriveJsonCodec.gen[FruitKind]
}
```

Each hint is the class name in snake_case:

```scala
println((GoodFruit(true): FruitKind).toJson)
// {"good_fruit":{"good":true}}
println((BadFruit(true): FruitKind).toJson)
// {"bad_fruit":{"bad":true}}
```

The codec of `FruitKind` expects the hint. You cannot decode a bare `{"good":true}` with it, and you cannot encode a `GoodFruit` on its own with it either.

### Decode a single subtype

To read one subtype without a hint, give that subtype its own codec in its companion. A case class does not need a discriminator to be decoded on its own:

```scala
import zio.json._

@jsonHintNames(SnakeCase)
sealed trait Nut

case class Almond(salted: Boolean) extends Nut
case class Cashew(roasted: Boolean) extends Nut

object Almond {
  implicit val codec: JsonCodec[Almond] = DeriveJsonCodec.gen[Almond]
}

object Nut {
  implicit val codec: JsonCodec[Nut] = DeriveJsonCodec.gen[Nut]
}
```

The codec of `Almond` reads the bare object:

```scala
"""{"salted":true}""".fromJson[Almond]
// res6: Either[String, Almond] = Right(value = Almond(salted = true))
```

To accept a subtype only when it arrives inside the sealed family, transform the codec of the family instead of deriving a new one. Keep this decoder out of implicit scope, or the codec of `Nut` would pick it up and call itself:

```scala
val cashewDecoder: JsonDecoder[Cashew] =
  Nut.codec.decoder.mapOrFail {
    case cashew: Cashew => Right(cashew)
    case other          => Left(s"Expected Cashew, got $other")
  }
```

The decoder accepts a cashew and rejects any other member of the family:

```scala
"""{"cashew":{"roasted":true}}""".fromJson[Cashew](cashewDecoder)
// res7: Either[String, Cashew] = Right(value = Cashew(roasted = true))
"""{"almond":{"salted":true}}""".fromJson[Cashew](cashewDecoder)
// res8: Either[String, Cashew] = Left(
//   value = "(Expected Cashew, got Almond(true))"
// )
```

## 3. Use a discriminator field with `@jsonDiscriminator`

Some schemas put the type name inside the object:

```json
{"type":"banaani","curvature":0.5}
```

Use `@jsonDiscriminator` on the sealed trait to get this shape. The value of the field is still the hint, so `@jsonHint` and `@jsonHintNames` work as before. The family from section 1, with a discriminator named `type`:

```scala
@jsonDiscriminator("type")
sealed trait Tropical

@jsonHint("banaani") case class TropicalBanana(curvature: Double) extends Tropical
@jsonHint("omena") case class TropicalApple(poison: Boolean)      extends Tropical

object Tropical {
  implicit val codec: JsonCodec[Tropical] = DeriveJsonCodec.gen[Tropical]
}
```

The encoder writes the hint into the `type` field, and the decoder reads it from there:

```scala
println((TropicalBanana(0.5): Tropical).toJson)
// {"type":"banaani","curvature":0.5}
"""{"type":"omena","poison":false}""".fromJson[Tropical]
// res10: Either[String, Tropical] = Right(
//   value = TropicalApple(poison = false)
// )
```

The encoder always writes the discriminator as the first field. The decoder accepts it anywhere in the object, so input from a producer that writes it last decodes too:

```scala
"""{"poison":false,"type":"omena"}""".fromJson[Tropical]
// res11: Either[String, Tropical] = Right(
//   value = TropicalApple(poison = false)
// )
```

:::warning[Use it only when you must]
A discriminator is slower, uses more memory, and is more exposed to denial-of-service input than the default wrapper object. Use it only to match a schema that you do not control. Do not combine it with `@jsonNoExtraFields` or with `JsonCodecConfiguration(allowExtraFields = false)`, because the discriminator counts as an extra field of the subtype: decoding then fails with an `(invalid extra field)` error.
:::

## 4. Set it for the whole program

The configuration form of the same options is `JsonCodecConfiguration`. Use `SumTypeHandling.DiscriminatorField` for `sumTypeHandling`, and `sumTypeMapping` for the hint format:

```scala
import zio.json._
import zio.json.JsonCodecConfiguration.SumTypeHandling

implicit val config: JsonCodecConfiguration =
  JsonCodecConfiguration(
    sumTypeHandling = SumTypeHandling.DiscriminatorField("type"),
    sumTypeMapping = SnakeCase
  )

sealed trait Berry
case class BlueBerry(sweet: Boolean) extends Berry

object Berry {
  implicit val codec: JsonCodec[Berry] = DeriveJsonCodec.gen[Berry]
}
```

The hint goes into the `type` field, in snake_case:

```scala
println((BlueBerry(true): Berry).toJson)
// {"type":"blue_berry","sweet":true}
```

An annotation on the sealed trait takes priority over the configuration. The default is `SumTypeHandling.WrapperWithClassNameField`, the single-key object.

## 5. Write case objects as strings

A sealed family made only of case objects can be written as a plain JSON string:

```json
"Red"
```

Whether this happens depends on the [`enumValuesAsStrings`](../reference/json-codec-configuration.md#enumvaluesasstrings) setting of `JsonCodecConfiguration`:

| Scala version | Default of `enumValuesAsStrings` |
|---------------|----------------------------------|
| Scala 2       | `false`                          |
| Scala 3       | `true`                           |

The setting has no effect when a discriminator is used. Hints still apply, so `@jsonHint("crimson")` on a case object changes its string.

On Scala 3, a sealed family of case objects and an `enum` with only parameterless cases are both written as strings without any setup.

On Scala 2 the default is the single-key object, `{"Red":{}}`. Turn the setting on with an implicit configuration:

```scala
import zio.json._

implicit val config: JsonCodecConfiguration =
  JsonCodecConfiguration(enumValuesAsStrings = true)

sealed trait Color
case object Red   extends Color
case object Green extends Color

object Color {
  implicit val codec: JsonCodec[Color] = DeriveJsonCodec.gen[Color]
}
```

The case objects are now written and read as strings:

```scala
println((Red: Color).toJson)
// "Red"
""""Green"""".fromJson[Color]
// res16: Either[String, Color] = Right(value = Green)
```

The two Scala versions disagree on what counts as an enumeration value. On Scala 2, a case class with an empty parameter list counts, like a case object. With the setting on, this family is written as strings:

```scala
sealed trait Signal
case object On         extends Signal
final case class Off() extends Signal

object Signal {
  implicit val codec: JsonCodec[Signal] = DeriveJsonCodec.gen[Signal]
}
```

Both cases become strings on Scala 2:

```scala
println((On: Signal).toJson)
// "On"
println((Off(): Signal).toJson)
// "Off"
```

On Scala 3, only case objects and parameterless `enum` cases count. A family that contains a case class with no fields is not an enumeration, so every case, the case objects included, uses the object form. The same family on Scala 3:

```scala
sealed trait Signal derives JsonCodec
case object On         extends Signal
final case class Off() extends Signal
```

Running the example file on Scala 3, `(On: Signal).toJson` gives `{"On":{}}` and `(Off(): Signal).toJson` gives `{"Off":{}}`. A family made only of case objects is written as strings on Scala 3, as the `Color` family in the same file shows:

```scala
sealed trait Color derives JsonCodec
case object Red   extends Color
case object Green extends Color
```

`(Red: Color).toJson` gives `"Red"`. If the JSON must be the same on both versions, use case objects rather than empty case classes. The other Scala 2 and Scala 3 differences are listed in [JsonCodecConfiguration](../reference/json-codec-configuration.md#scala-2-and-scala-3-differences).

## 6. Nest sealed traits

A sealed trait can extend another one. The codec of the outer trait sees only the leaf cases, the case classes and case objects at the bottom of the hierarchy. An intermediate sealed trait gets no hint of its own, so the JSON is the same as for a flat family. This holds on Scala 2 and on Scala 3:

```scala
import zio.json._

sealed trait Animal
sealed trait Pet extends Animal

final case class Dog(name: String) extends Pet
final case class Cat(name: String) extends Pet
final case class Wolf(pack: Int)   extends Animal

object Animal {
  implicit val codec: JsonCodec[Animal] = DeriveJsonCodec.gen[Animal]
}
```

A `Dog` is written with its own name as the hint, and a hint that names the intermediate trait is not accepted:

```scala
println((Dog("Rex"): Animal).toJson)
// {"Dog":{"name":"Rex"}}
"""{"Cat":{"name":"Tom"}}""".fromJson[Animal]
// res23: Either[String, Animal] = Right(value = Cat(name = "Tom"))
"""{"Pet":{"Dog":{"name":"Rex"}}}""".fromJson[Animal]
// res24: Either[String, Animal] = Left(value = "(invalid disambiguator)")
```

Because the leaf names share one namespace, two leaves in different branches must not have the same hint.

## 7. Keep hint names unique

Every case of a family must end up with a distinct hint after `@jsonHint` and `@jsonHintNames` are applied. A clash is not a compile error. The code compiles, and the derived decoder, and with it a derived `JsonCodec`, throws an `AssertionError` at runtime when it is first initialised, on Scala 2 and Scala 3 alike. The message names the family by its fully qualified name. For a family `com.example.Dup` where two cases both carry `@jsonHint("x")`, it reads:

```text
java.lang.AssertionError: Case names in ADT com.example.Dup must be distinct, name(s) x are duplicated
```

An encoder derived on its own with `DeriveJsonEncoder.gen` does not check, and writes the same hint for both cases. A test that decodes one value of each family is enough to catch the clash early.

## See also

- [Annotations](../reference/annotations.md) lists every derivation annotation in one table.
- [Field names](field-names.md) covers `@jsonMemberNames`, whose formats `@jsonHintNames` reuses.
- [Optional, default and null fields](optional-and-default-fields.md) covers `@jsonNoExtraFields`.
