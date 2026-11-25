# Field Names

> Rename JSON fields with jsonField, rename all fields with jsonMemberNames, and read old names with jsonAliases in ZIO JSON.

Goal: make the JSON field names differ from the Scala field names of a case class.

By default, the JSON field names are the case class field names. This page shows three ways to change that:

- `@jsonField` renames one field.
- `@jsonMemberNames` renames every field with one rule.
- `@jsonAliases` accepts extra names when decoding.

Each step below is a separate option. Use the ones you need.

## What you need

Only `zio-json` and one import:

```scala
import zio.json._
```

## 1. Rename one field with `@jsonField`

Put `@jsonField` on a field and give it the JSON name:

```scala
case class Banana(@jsonField("bendiness") curvature: Double)

object Banana {
  implicit val codec: JsonCodec[Banana] = DeriveJsonCodec.gen[Banana]
}
```

The field is written and read as `bendiness`:

```scala
println(Banana(0.5).toJson)
// {"bendiness":0.5}
"""{"bendiness":0.5}""".fromJson[Banana]
// res2: Either[String, Banana] = Right(value = Banana(curvature = 0.5))
```

## 2. Rename all fields with `@jsonMemberNames`

Put `@jsonMemberNames` on a case class. It takes a `JsonMemberFormat`, and the format is applied to every field name, for both encoding and decoding.

These formats are available:

| Format           | What it does                             |
|------------------|------------------------------------------|
| `SnakeCase`      | `passionFruit` becomes `passion_fruit`   |
| `KebabCase`      | `passionFruit` becomes `passion-fruit`   |
| `PascalCase`     | `passionFruit` becomes `PassionFruit`    |
| `CamelCase`      | `passion_fruit` becomes `passionFruit`   |
| `IdentityFormat` | Leaves the name as it is                 |
| `CustomCase(f)`  | Applies your own `String => String`      |

A `JsonMemberFormat` is itself a `String => String`, so you can call it to see what it does:

```scala
List[(String, JsonMemberFormat)](
  "SnakeCase"      -> SnakeCase,
  "KebabCase"      -> KebabCase,
  "PascalCase"     -> PascalCase,
  "CamelCase"      -> CamelCase,
  "IdentityFormat" -> IdentityFormat,
  "CustomCase"     -> CustomCase(_.toUpperCase)
).map { case (name, format) => name -> format("passionFruit") }
// res3: List[(String, String)] = List(
//   ("SnakeCase", "passion_fruit"),
//   ("KebabCase", "passion-fruit"),
//   ("PascalCase", "PassionFruit"),
//   ("CamelCase", "passionFruit"),
//   ("IdentityFormat", "passionFruit"),
//   ("CustomCase", "PASSIONFRUIT")
// )
```

Here is a basket where most fields are `snake_case` in JSON:

```scala
@jsonMemberNames(SnakeCase)
case class FruitBasket(
  passionFruit: Boolean,
  grannySmith: Boolean,
  dragonFruit: Boolean,
  @jsonField("blood-orange") bloodOrange: Boolean
)

object FruitBasket {
  implicit val codec: JsonCodec[FruitBasket] = DeriveJsonCodec.gen[FruitBasket]
}
```

Three fields follow the format. `bloodOrange` has its own `@jsonField`, which takes priority over `@jsonMemberNames`:

```scala
println(FruitBasket(true, true, true, false).toJson)
// {"passion_fruit":true,"granny_smith":true,"dragon_fruit":true,"blood-orange":false}
```

The same names are expected when decoding:

```scala
"""{"passion_fruit":true,"granny_smith":true,"dragon_fruit":true,"blood-orange":false}""".fromJson[FruitBasket]
// res5: Either[String, FruitBasket] = Right(
//   value = FruitBasket(
//     passionFruit = true,
//     grannySmith = true,
//     dragonFruit = true,
//     bloodOrange = false
//   )
// )
```

## 3. Use a custom rule

`CustomCase` wraps any `String => String`. Define the function as a normal value, then pass it to the format:

```scala
object Rules {
  val shout: String => String = _.toUpperCase
}

@jsonMemberNames(CustomCase(Rules.shout))
case class Lemon(sourness: Int)

object Lemon {
  implicit val codec: JsonCodec[Lemon] = DeriveJsonCodec.gen[Lemon]
}
```

The field name is written in upper case:

```scala
println(Lemon(7).toJson)
// {"SOURNESS":7}
```

## 4. Set the format for the whole program

Instead of annotating each class, set `fieldNameMapping` in an implicit `JsonCodecConfiguration`. It applies to every codec derived while the configuration is in scope. The `@jsonMemberNames` annotation on a class takes priority over it. To write every field in snake_case without annotating each class:

```scala
implicit val config: JsonCodecConfiguration =
  JsonCodecConfiguration(fieldNameMapping = SnakeCase)

case class Cherry(sweetnessLevel: Int)

implicit val cherryCodec: JsonCodec[Cherry] = DeriveJsonCodec.gen[Cherry]
```

## 5. Read old names with `@jsonAliases`

After a field has been renamed, you may still receive JSON that uses the old name. `@jsonAliases` lists extra names that the decoder accepts for that field. It was added in zio-json 0.5.0. To accept two old names for `seedCount`:

```scala
case class Strawberry(@jsonAliases("seeds", "pips") seedCount: Int)

object Strawberry {
  implicit val decoder: JsonDecoder[Strawberry] = DeriveJsonDecoder.gen[Strawberry]
}
```

The old names and the new name decode to equal values:

```scala
"""{"seeds":32}""".fromJson[Strawberry]
// res8: Either[String, Strawberry] = Right(value = Strawberry(seedCount = 32))
"""{"pips":32}""".fromJson[Strawberry]
// res9: Either[String, Strawberry] = Right(value = Strawberry(seedCount = 32))
"""{"seedCount":32}""".fromJson[Strawberry]
// res10: Either[String, Strawberry] = Right(
//   value = Strawberry(seedCount = 32)
// )
```

Keep these points in mind:

- The annotation has no effect on encoding. The encoder always writes the main name.
- An alias is matched exactly as you wrote it. A `@jsonMemberNames` format is not applied to aliases.
- A field name and its aliases must not collide with another field or alias of the same class. If they do, the code still compiles, but on Scala 2 and Scala 3 the decoder throws an `AssertionError` at runtime when the codec is first initialised.

## See also

- [Annotations](../reference/annotations.md) lists every derivation annotation in one table.
- [Sealed traits](sealed-traits.md) shows how to rename the type hints of a sealed family.
- [Getting Started](../getting-started.md) covers the basics of derived codecs.
