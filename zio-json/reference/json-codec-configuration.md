# JsonCodecConfiguration

> Reference for JsonCodecConfiguration: every codec derivation option, its type and default, and the Scala 2 and Scala 3 differences.

`JsonCodecConfiguration` is a case class that sets the defaults for derived codecs. `DeriveJsonCodec.gen`, `DeriveJsonEncoder.gen` and `DeriveJsonDecoder.gen` read it when they derive an instance. Annotations on a single type, such as `@jsonDiscriminator` or `@jsonMemberNames`, override the configuration for that type.

## Options

All options have defaults, so you only name the ones you want to change.

| Option                     | Type                       | Scala 2 default              | Scala 3 default              | What it controls                                   |
|----------------------------|----------------------------|------------------------------|------------------------------|----------------------------------------------------|
| `sumTypeHandling`          | `SumTypeHandling`          | `WrapperWithClassNameField`  | `WrapperWithClassNameField`  | How a sealed trait case is tagged                  |
| `fieldNameMapping`         | `JsonMemberFormat`         | `IdentityFormat`             | `IdentityFormat`             | How case class field names become JSON keys        |
| `allowExtraFields`         | `Boolean`                  | `true`                       | `true`                       | Whether unknown keys are accepted on decode        |
| `sumTypeMapping`           | `JsonMemberFormat`         | `IdentityFormat`             | `IdentityFormat`             | How case names become JSON tags                    |
| `explicitNulls`            | `Boolean`                  | `false`                      | `false`                      | Whether `None` is written as `null`                |
| `explicitEmptyCollections` | `ExplicitEmptyCollections` | `ExplicitEmptyCollections()` | `ExplicitEmptyCollections()` | Whether empty collections are written and required |
| `enumValuesAsStrings`      | `Boolean`                  | `false`                      | `true`                       | Whether case objects are plain JSON strings        |

The only difference in defaults between the two Scala versions is `enumValuesAsStrings`. A few behaviours also differ; see [Scala 2 and Scala 3 differences](#scala-2-and-scala-3-differences).

## Passing a Custom Configuration

The derivation macros take the configuration as an implicit parameter. The library provides `JsonCodecConfiguration.default` in the companion object. To use your own, put an implicit value in scope before the `DeriveJsonCodec.gen` call. A value in scope wins over the companion default. Here the companion of `User` holds a configuration that writes field names in snake case:

```scala
import zio.json._

case class User(firstName: String, lastName: String)

object User {
  implicit val config: JsonCodecConfiguration =
    JsonCodecConfiguration(fieldNameMapping = SnakeCase)

  implicit val codec: JsonCodec[User] = DeriveJsonCodec.gen[User]
}
```

Encoding and decoding both use the snake-case names:

```scala
User("Ada", "Lovelace").toJson
// res1: String = "{\"first_name\":\"Ada\",\"last_name\":\"Lovelace\"}"

"""{"first_name":"Ada","last_name":"Lovelace"}""".fromJson[User]
// res2: Either[String, User] = Right(
//   value = User(firstName = "Ada", lastName = "Lovelace")
// )
```

You can also start from the default and change one option with `copy`:

```scala
import zio.json._

val strict: JsonCodecConfiguration =
  JsonCodecConfiguration.default.copy(allowExtraFields = false)
```

:::warning
`JsonCodecConfiguration#copy` is hand-written, and each of its parameters defaults to the library default, not to the current value. Call it only on `JsonCodecConfiguration.default`. On any other configuration, every option you do not name is reset: `JsonCodecConfiguration(fieldNameMapping = SnakeCase).copy(allowExtraFields = false)` loses the `SnakeCase` mapping. To set several options, name them all in one `JsonCodecConfiguration(...)` call.
:::

In Scala 3, supply it as a `given`. The macros take it as a `using` parameter, and so do `JsonCodec.derived`, `JsonEncoder.derived` and `JsonDecoder.derived`, which a `derives` clause calls. The following Scala 3 code writes `{"first_name":"Ada","last_name":"Lovelace"}` for `User("Ada", "Lovelace")`:

```scala
given JsonCodecConfiguration = JsonCodecConfiguration(fieldNameMapping = SnakeCase)

final case class User(firstName: String, lastName: String) derives JsonCodec
```

A `derives` clause does not need a given of your own. Without one, `JsonCodecConfiguration.default` from the companion object is used, so this Scala 3 class encodes as `{"accountId":7,"ownerName":"Ada"}`:

```scala
final case class Account(accountId: Long, ownerName: String) derives JsonCodec
```

The configuration is read at derivation time. Changing it afterwards has no effect on codecs that already exist.

The subtypes of a sealed trait that have no codec of their own are derived together with the trait, using the configuration in scope at the outer `DeriveJsonCodec.gen` call. Field types are different on the two Scala versions:

- On Scala 2, every field type needs a codec in implicit scope. A case class field whose type has none fails to compile with `magnolia: could not find zio.json.DeriveJsonEncoder.Typeclass[...]`.
- On Scala 3, a field type that has no codec and can be derived is derived with the outer configuration.

On Scala 3, the following writes `{"full_name":"Ada","home_address":{"street_name":"Main St"}}` for `Customer("Ada", Address("Main St"))`, although `Address` has no codec:

```scala
given JsonCodecConfiguration = JsonCodecConfiguration(fieldNameMapping = SnakeCase)

final case class Address(streetName: String)
final case class Customer(fullName: String, homeAddress: Address) derives JsonCodec
```

## sumTypeHandling

Type: `SumTypeHandling`, a sealed trait in `JsonCodecConfiguration.SumTypeHandling` with two values:

- `WrapperWithClassNameField` (the default) wraps the case in an object with one key, the case name.
- `DiscriminatorField(name)` writes the case name into a field called `name` inside the case object itself.

`SumTypeHandling#discriminatorField` returns the field name as an `Option[String]`: `Some(name)` for `DiscriminatorField(name)` and `None` for `WrapperWithClassNameField`.

`@jsonDiscriminator("name")` on a sealed trait does the same for that trait only. The scaladoc warns that a discriminator is slower, uses more memory, and is open to denial of service attacks that the wrapper form is not. Use it only to match an external schema.

The following configuration writes the case name into a `type` field:

```scala
import zio.json._
import zio.json.JsonCodecConfiguration.SumTypeHandling.DiscriminatorField

sealed trait Shape
case class Circle(radius: Double) extends Shape
case class Square(side: Double)   extends Shape

implicit val config: JsonCodecConfiguration =
  JsonCodecConfiguration(sumTypeHandling = DiscriminatorField("type"))

implicit val codec: JsonCodec[Shape] = DeriveJsonCodec.gen[Shape]
```

The `type` field comes first in the output, and the decoder reads it to pick the case:

```scala
(Circle(1.0): Shape).toJson
// res8: String = "{\"type\":\"Circle\",\"radius\":1.0}"

"""{"type":"Square","side":2.0}""".fromJson[Shape]
// res9: Either[String, Shape] = Right(value = Square(side = 2.0))
```

With the default, the same value encodes as `{"Circle":{"radius":1.0}}`. See [sealed traits](../how-to/sealed-traits.md) for the how-to side.

:::warning
Do not combine a discriminator with `allowExtraFields = false`. The discriminator form passes the whole object, discriminator field included, to the decoder of the case class, and that decoder then rejects the discriminator as an unknown field. Every case of the family fails to decode with `(invalid extra field)`, and nothing warns at compile time. The same happens with `@jsonDiscriminator` on the trait and `@jsonNoExtraFields` on a case.
:::

## fieldNameMapping

Type: `JsonMemberFormat`. It rewrites every case class field name. `@jsonMemberNames` overrides it per type, and `@jsonField` overrides it per field. The available formats are `IdentityFormat`, `SnakeCase`, `KebabCase`, `CamelCase`, `PascalCase`, and `CustomCase(f: String => String)`. The `ziojson_03` object holds the `SnakeCase` and `KebabCase` variants from zio-json 0.3.0, which put a separator before digits. The example under [Passing a custom configuration](#passing-a-custom-configuration) uses `SnakeCase`. See [field names](../how-to/field-names.md) for the how-to side.

## allowExtraFields

Type: `Boolean`. When `false`, decoding fails if the JSON has a key that the case class does not declare. `@jsonNoExtraFields` sets it per type. It breaks decoding of sum types that use a discriminator; see [sumTypeHandling](#sumtypehandling).

The configuration below makes a plain case class strict:

```scala
import zio.json._

case class Point(x: Int, y: Int)

implicit val config: JsonCodecConfiguration =
  JsonCodecConfiguration(allowExtraFields = false)

implicit val codec: JsonCodec[Point] = DeriveJsonCodec.gen[Point]
```

The extra `z` key makes decoding fail:

```scala
"""{"x":1,"y":2,"z":3}""".fromJson[Point]
// res11: Either[String, Point] = Left(value = "(invalid extra field)")
```

## sumTypeMapping

Type: `JsonMemberFormat`. It rewrites the case names of a sealed trait, for both the wrapper key and the discriminator value. `@jsonHintNames` overrides it per trait, and `@jsonHint` overrides it per case.

With `KebabCase`, a case named `RoundCircle` is tagged `round-circle`:

```scala
import zio.json._

sealed trait Shape
case class RoundCircle(radius: Double) extends Shape

implicit val config: JsonCodecConfiguration =
  JsonCodecConfiguration(sumTypeMapping = KebabCase)

implicit val codec: JsonCodec[Shape] = DeriveJsonCodec.gen[Shape]
```

The wrapper key uses the rewritten name:

```scala
(RoundCircle(1.0): Shape).toJson
// res13: String = "{\"round-circle\":{\"radius\":1.0}}"
```

## explicitNulls

Type: `Boolean`. When `true`, an `Option` field that is `None` is written as `null`. By default the key is left out. `@jsonExplicitNull` sets it per type or per field. This option only affects encoding.

The configuration below writes `None` as `null`:

```scala
import zio.json._

case class Profile(name: String, nickname: Option[String])

implicit val config: JsonCodecConfiguration =
  JsonCodecConfiguration(explicitNulls = true)

implicit val codec: JsonCodec[Profile] = DeriveJsonCodec.gen[Profile]
```

The `nickname` key is now present with a `null` value:

```scala
Profile("Ada", None).toJson
// res15: String = "{\"name\":\"Ada\",\"nickname\":null}"
```

## explicitEmptyCollections

Type: `ExplicitEmptyCollections`, a case class `ExplicitEmptyCollections(encoding: Boolean = true, decoding: Boolean = true)`.

- `encoding = false` leaves out fields whose collection is empty.
- `decoding = false` lets a missing collection field decode as an empty collection.

With the defaults, empty collections are written, and a missing collection field is an error. `@jsonExplicitEmptyCollections(encoding, decoding)` sets it per type. On a single field it changes encoding only; the `decoding` flag of a field annotation is ignored.

The configuration below turns off both directions:

```scala
import zio.json._

case class Basket(items: List[String])

implicit val config: JsonCodecConfiguration =
  JsonCodecConfiguration(explicitEmptyCollections = ExplicitEmptyCollections(encoding = false, decoding = false))

implicit val codec: JsonCodec[Basket] = DeriveJsonCodec.gen[Basket]
```

The empty list is left out, and a missing `items` key decodes as an empty list:

```scala
Basket(Nil).toJson
// res17: String = "{}"

"{}".fromJson[Basket]
// res18: Either[String, Basket] = Right(value = Basket(items = List()))
```

## enumValuesAsStrings

Type: `Boolean`. It applies to a sealed trait whose cases all have no fields. On Scala 3 that means case objects and parameterless enum cases. On Scala 2 it also includes case classes with an empty parameter list, and it is skipped if any case has its own hand-written codec. When `true`, each case is a plain JSON string. When `false`, each case is a wrapper object with an empty body, as for any other sum type. It has no effect when a discriminator is set, since the discriminator form always uses objects.

This is the one option with a different default per Scala version, so set it explicitly if your JSON must be the same on both. The how-to side is in [Write case objects as strings](../how-to/sealed-traits.md#5-write-case-objects-as-strings).

With all cases as case objects, the cases become strings:

```scala
import zio.json._

sealed trait Color
case object Red   extends Color
case object Green extends Color

implicit val config: JsonCodecConfiguration =
  JsonCodecConfiguration(enumValuesAsStrings = true)

implicit val codec: JsonCodec[Color] = DeriveJsonCodec.gen[Color]
```

Each case is written and read as a JSON string:

```scala
(Red: Color).toJson
// res20: String = "\"Red\""

""""Green"""".fromJson[Color]
// res21: Either[String, Color] = Right(value = Green)
```

With `enumValuesAsStrings = false`, `Red` encodes as `{"Red":{}}`.

A case class with an empty parameter list counts as an enum value on Scala 2 only. On Scala 2, this family is written as strings:

```scala
import zio.json._

sealed trait Signal
case class Stop() extends Signal
case object Go    extends Signal

implicit val config: JsonCodecConfiguration =
  JsonCodecConfiguration(enumValuesAsStrings = true)

implicit val codec: JsonCodec[Signal] = DeriveJsonCodec.gen[Signal]
```

Both cases encode as JSON strings:

```scala
(Stop(): Signal).toJson
// res23: String = "\"Stop\""

(Go: Signal).toJson
// res24: String = "\"Go\""
```

The same family on Scala 3 uses the wrapper form for every case, because `Stop()` is not an enum value there. It writes `{"Stop":{}}` and `{"Go":{}}`:

```scala
given JsonCodecConfiguration = JsonCodecConfiguration(enumValuesAsStrings = true)

sealed trait Signal derives JsonCodec
case class Stop() extends Signal
case object Go    extends Signal
```

To get the same JSON on both versions, use case objects (or, on Scala 3, parameterless enum cases) for the values.

## Scala 2 and Scala 3 Differences

The options and their meaning are the same on both versions. These behaviours differ:

| Behaviour                                   | Scala 2                                     | Scala 3                                                                                            |
|---------------------------------------------|---------------------------------------------|----------------------------------------------------------------------------------------------------|
| Default of `enumValuesAsStrings`            | `false`                                     | `true`                                                                                             |
| Case class with no fields, as an enum value | Counts, so it can be written as a string    | Does not count, so the family uses objects                                                         |
| Case object with a hand-written codec       | String form is skipped for the whole family | String form is used; the encoder ignores the hand-written codec, and the decoder passes `{}` to it |
| Field type without a codec                  | Compile error                               | Derived with the outer configuration                                                               |
| Configuration parameter                     | `implicit` value                            | `given` value, also used by `derives`                                                              |
