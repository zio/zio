# BsonCodecConfiguration and Annotations

> `DeriveBsonCodec.derive` builds a codec for a case class or sealed trait from its shape. Two things control the result: a `BsonCodecConfiguration` value that sets defaults for the whole derivation, and annotations that override those defaults for one field, one subtype, or one sealed trait.

## Overview

Derivation is available from the `zio.bson.magnolia` package, which is the `zio-bson-magnolia` module. It supports Scala 2.13 and Scala 3.

## Definition

`BsonCodecConfiguration` is a case class with five fields. Each one has a default:

```scala
case class BsonCodecConfiguration(
  sumTypeHandling: BsonCodecConfiguration.SumTypeHandling = SumTypeHandling.WrapperWithClassNameField,
  skipNullsInCaseClass: Boolean = true,
  classNameMapping: String => String = identity,
  fieldNameMapping: String => String = identity,
  allowExtraFields: Boolean = true
)
```

The companion object also provides an implicit default value, so derivation works without any configuration.

| Field                  | Default                     | Effect                                                                                          |
| ---------------------- | --------------------------- | ----------------------------------------------------------------------------------------------- |
| `sumTypeHandling`      | `WrapperWithClassNameField` | How a sealed trait is written. See [Sum Types](#sum-types).                                     |
| `skipNullsInCaseClass` | `true`                      | When `true`, a `None` field is left out of the document. When `false`, it is written as `null`. |
| `classNameMapping`     | `identity`                  | Converts a subtype's class name before it is used as a key or discriminator value.              |
| `fieldNameMapping`     | `identity`                  | Converts a field's name before it is used as a BSON key.                                        |
| `allowExtraFields`     | `true`                      | When `false`, a field that is not in the case class makes decoding fail.                        |

Annotations on a field, subtype, or sealed trait always win over the configuration. For example, `bsonField` replaces whatever `fieldNameMapping` would produce.

## Providing a Configuration

Derivation reads the configuration as an implicit value in the scope where `DeriveBsonCodec.derive` is written. The examples on this page use a small function that takes the configuration and derives the codec from it. Any implicit `BsonCodecConfiguration` in scope takes precedence over the default one, so the same configuration can also be an `implicit val` in the companion object. Writing it as a function keeps each example self-contained:

```scala
import zio.bson._
import zio.bson.BsonBuilder._
import zio.bson.magnolia._
import zio.bson.magnolia.BsonCodecConfiguration.{SumTypeHandling, toSnakeCase, toUpperSnakeCase}

final case class Address(streetName: String, postalCode: Option[String])

def addressCodec(config: BsonCodecConfiguration): BsonCodec[Address] = {
  implicit val configuration: BsonCodecConfiguration = config
  DeriveBsonCodec.derive
}

val snakeAddress: BsonCodec[Address] =
  addressCodec(BsonCodecConfiguration(fieldNameMapping = toSnakeCase))
```

```scala
println(snakeAddress.encoder.toBsonValue(Address("Main Street", None)))
// {"street_name": "Main Street"}
```

The field `streetName` is written as `street_name`. The `None` field is omitted because `skipNullsInCaseClass` is still `true`.

When you have only one configuration for a type, you can also declare it as an `implicit val` in the scope where the codec is derived, and call `DeriveBsonCodec.derive` there. In Scala 3, the same value is declared with `given` instead of `implicit val`.

## Field Names

`fieldNameMapping` changes every field name in a derived codec. The following example writes `None` fields as `null` and uses upper snake case for keys:

```scala
import zio.bson._
import zio.bson.BsonBuilder._
import zio.bson.magnolia._
import zio.bson.magnolia.BsonCodecConfiguration.{SumTypeHandling, toSnakeCase, toUpperSnakeCase}

final case class Address(streetName: String, postalCode: Option[String])

def addressCodec(config: BsonCodecConfiguration): BsonCodec[Address] = {
  implicit val configuration: BsonCodecConfiguration = config
  DeriveBsonCodec.derive
}

val nullAddress: BsonCodec[Address] =
  addressCodec(
    BsonCodecConfiguration(
      fieldNameMapping = toUpperSnakeCase,
      skipNullsInCaseClass = false
    )
  )
```

```scala
println(nullAddress.encoder.toBsonValue(Address("Main Street", None)))
// {"STREET_NAME": "Main Street", "POSTAL_CODE": null}
```

The mapping functions are listed in [Name Mappers](#name-mappers).

## Class Names and Sum Types

A sealed trait needs a way to tell its subtypes apart in BSON. `classNameMapping` changes the name that is written for each subtype. The next example uses the default wrapper style, where each subtype name is a key that holds the subtype's fields:

```scala
import zio.bson._
import zio.bson.BsonBuilder._
import zio.bson.magnolia._
import zio.bson.magnolia.BsonCodecConfiguration.{SumTypeHandling, toSnakeCase, toUpperSnakeCase}

sealed trait Shape

object Shape {
  final case class Circle(radius: Double) extends Shape
  final case class Square(side: Double)   extends Shape

  implicit val config: BsonCodecConfiguration =
    BsonCodecConfiguration(classNameMapping = toSnakeCase)

  implicit val codec: BsonCodec[Shape] = DeriveBsonCodec.derive
}
```

```scala
println(BsonEncoder[Shape].toBsonValue(Shape.Square(2.0)))
// {"square": {"side": 2.0}}
```

The output key is `square`. The name comes from the class name, so `Square` becomes `square` under `toSnakeCase`.

## Sum Types

`sumTypeHandling` picks one of two encodings for sealed traits:

- `SumTypeHandling.WrapperWithClassNameField` (the default) writes a document with one key, the subtype name, whose value holds the subtype's fields. For example, `{ Circle: { radius: 1.0 } }`.
- `SumTypeHandling.DiscriminatorField(name)` writes the subtype fields in the same document as a string field called `name`. For example, `{ radius: 1.0, kind: "Circle" }` when `name` is `"kind"`.

The next example uses a discriminator field called `kind`:

```scala
import zio.bson._
import zio.bson.BsonBuilder._
import zio.bson.magnolia._
import zio.bson.magnolia.BsonCodecConfiguration.{SumTypeHandling, toSnakeCase, toUpperSnakeCase}

sealed trait Pet

object Pet {
  final case class Dog(name: String) extends Pet
  final case class Cat(lives: Int)   extends Pet

  implicit val config: BsonCodecConfiguration =
    BsonCodecConfiguration(sumTypeHandling = SumTypeHandling.DiscriminatorField("kind"))

  implicit val codec: BsonCodec[Pet] = DeriveBsonCodec.derive
}
```

```scala
println(BsonEncoder[Pet].toBsonValue(Pet.Dog("rex")))
// {"name": "rex", "kind": "Dog"}
println(BsonDecoder[Pet].fromBsonValue(doc("kind" -> str("Cat"), "lives" -> int(9))))
// Right(Cat(9))
```

When the discriminator is given as an annotation with `bsonDiscriminator`, that annotation wins over the configuration for that sealed trait. See [Annotations](#annotations).

The decoder requires the discriminator to be present. If it is missing, decoding fails with `Missing disambiguator kind.`

## Extra Fields

With the default `allowExtraFields = true`, a document may contain fields the case class does not know, and they are skipped. Setting it to `false` makes decoding fail on any unknown field:

```scala
import zio.bson._
import zio.bson.BsonBuilder._
import zio.bson.magnolia._

final case class Point(x: Int, y: Int)

def pointCodec(config: BsonCodecConfiguration): BsonCodec[Point] = {
  implicit val configuration: BsonCodecConfiguration = config
  DeriveBsonCodec.derive
}

val strictPoint: BsonCodec[Point] =
  pointCodec(BsonCodecConfiguration(allowExtraFields = false))
```

```scala
println(strictPoint.decoder.fromBsonValue(doc("x" -> int(1), "y" -> int(2), "z" -> int(3))))
// Left(zio.bson.BsonDecoder$Error: Path: .z, error: Invalid extra field.. Don't use `decodeUnsafe` and `fromBsonValueUnsafe` methods.)
```

The error names the extra field, with the path to it. The `bsonNoExtraFields` annotation applies the same rule to one case class regardless of the configuration.

## Annotations

Annotations are classes in the `zio.bson` package. Import them with `import zio.bson._`, which is in the examples above.

| Annotation                | Target           | Effect                                                                                       |
| ------------------------- | ---------------- | -------------------------------------------------------------------------------------------- |
| `bsonField(name)`         | case class field | Uses `name` as the BSON key for this field, overriding `fieldNameMapping`.                   |
| `bsonDiscriminator(name)` | sealed trait     | Uses `name` as the discriminator field for this sealed trait, overriding `sumTypeHandling`. |
| `bsonHint(name)`          | subtype          | Uses `name` as the subtype's key or discriminator value, overriding `classNameMapping`.      |
| `bsonExclude`             | case class field | Leaves the field out when writing. See [Excluded Fields](#excluded-fields).                  |
| `bsonNoExtraFields`       | case class       | Rejects unknown fields when decoding this case class, even if `allowExtraFields` is `true`.  |

A field annotation and a subtype annotation work the same way as the configuration, but only for that one element. The next example renames a field and a subtype. It uses the default configuration, so the companion object's implicit codec is enough:

```scala
import zio.bson._
import zio.bson.BsonBuilder._
import zio.bson.magnolia._

sealed trait Fruit

object Fruit {
  final case class Banana(curvature: Double) extends Fruit

  @bsonHint("custom_type_name")
  final case class Apple(@bsonField("is_poisoned") poison: Boolean) extends Fruit

  implicit val codec: BsonCodec[Fruit] = DeriveBsonCodec.derive
}
```

```scala
println(BsonEncoder[Fruit].toBsonValue(Fruit.Apple(poison = true)))
// {"custom_type_name": {"is_poisoned": true}}
```

The output key is `custom_type_name`, and the field is `is_poisoned`. Neither depends on the configuration.

### Excluded Fields

`bsonExclude` leaves a field out of the output when writing. The field's default value is used when reading, because the document does not contain it. A field marked `bsonExclude` without a default value cannot be decoded, because there is nothing to fall back to.

```scala
import zio.bson._
import zio.bson.BsonBuilder._
import zio.bson.magnolia._

final case class Session(user: String, @bsonExclude attempts: Int = 0)

object Session {
  implicit val codec: BsonCodec[Session] = DeriveBsonCodec.derive
}
```

```scala
println(BsonEncoder[Session].toBsonValue(Session("ada", 3)))
// {"user": "ada"}
println(BsonDecoder[Session].fromBsonValue(doc("user" -> str("ada"))))
// Right(Session(ada,0))
```

The decoder does not check the annotation. If a document does contain the excluded field, the decoder reads it. Only the writing side skips the field.

## Name Mappers

`BsonCodecConfiguration` provides five functions that convert a camel-case name. They can be used for `fieldNameMapping` or `classNameMapping`.

| Function               | Example input | Example output |
| ---------------------- | ------------- | -------------- |
| `toSnakeCase`          | `someField`   | `some_field`   |
| `toKebabCase`          | `someField`   | `some-field`   |
| `toScreamingSnakeCase` | `someField`   | `SOME_FIELD`   |
| `toUpperSnakeCase`     | `someField`   | `SOME_FIELD`   |
| `toUpperKebabCase`     | `someField`   | `SOME-FIELD`   |

`toUpperSnakeCase` is another name for `toScreamingSnakeCase`. Acronyms are split as well: `ABcDEFgHi` becomes `a_bc_de_fg_hi` under `toSnakeCase`.

## Scala 2 and Scala 3

The annotations and configuration are the same in both versions. The configuration is declared as an `implicit val` in Scala 2 and as a `given` in Scala 3. The derivation entry points also differ, as described in [Derivation](./bson-derivation.md).

## Related Pages

- [Derivation](./bson-derivation.md) describes the derivation entry points for Scala 2 and Scala 3.
- [BsonCodec](./bson-codec.md) describes the value that derivation returns.
- [Use zio-bson with the MongoDB Driver](./how-to-use-with-mongodb-driver.md) shows derivation in an application.
- [Type Mappings](./types.md) lists the BSON type for each field type.
