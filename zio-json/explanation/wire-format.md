# JSON Shapes and Round Trips

> How derived codecs write each kind of sealed-trait and enum case on Scala 2 and Scala 3, and which values do not survive an encode followed by a decode.

The *wire format* of a type is the exact JSON its codec writes and reads. Other programs depend on it, so it pays to know what a derived codec produces before you publish an API, and to know when a value changes on the way through. A *round trip* is an encode followed by a decode. For most types the round trip gives back the value you started with, but a few combinations of types and settings do not.

This page first shows how the cases of a sealed trait or enum are written under each strategy, on Scala 2 and on Scala 3, and then lists the round-trip pitfalls.

## How Sum Type Cases Are Written

A case class is always written as a JSON object of its fields. A sealed trait or a Scala 3 `enum` (a *sum type*) also needs a *hint* that tells the decoder which case it is reading. ZIO JSON has three ways to write the hint:

- The wrapper object, the default, puts the case inside an object with a single key named after the case: `{"Circle":{"r":1.0}}`.
- A discriminator field, set with `@jsonDiscriminator("type")` or `SumTypeHandling.DiscriminatorField("type")`, adds the hint as a field of the case's own object: `{"type":"Circle","r":1.0}`.
- `enumValuesAsStrings = true` writes a case as a plain JSON string, `"Red"`, but only in a family where no case has fields.

The [sealed traits guide](../how-to/sealed-traits.md) shows how to set each one, and [JsonCodecConfiguration](../reference/json-codec-configuration.md#sumtypehandling) documents the options.

### The Shapes at a Glance

Every cell below comes from running the codecs on both Scala versions. Where the versions differ, the cell says so:

| Kind of case                                                   | Wrapper object                 | Discriminator field `type`          | `enumValuesAsStrings = true`             |
|----------------------------------------------------------------|--------------------------------|-------------------------------------|------------------------------------------|
| Case class with fields, `Circle(1.0)`                          | `{"Circle":{"r":1.0}}`         | `{"type":"Circle","r":1.0}`         | `{"Circle":{"r":1.0}}`                   |
| Case object next to cases with fields, `Unknown`               | `{"Unknown":{}}`               | `{"type":"Unknown"}`                | `{"Unknown":{}}`                         |
| Case object in a family of case objects only, `Red`            | `{"Red":{}}`                   | `{"type":"Red"}`                    | `"Red"`                                  |
| Empty case class in a family without fields, `Off()`           | `{"Off":{}}`                   | `{"type":"Off"}`                    | Scala 2: `"Off"`, Scala 3: `{"Off":{}}`  |
| Case object next to an empty case class, `On`                  | `{"On":{}}`                    | `{"type":"On"}`                     | Scala 2: `"On"`, Scala 3: `{"On":{}}`    |
| Scala 3 enum case with fields, `Banana(0.5)`                   | `{"Banana":{"curvature":0.5}}` | `{"type":"Banana","curvature":0.5}` | `{"Banana":{"curvature":0.5}}`           |
| Scala 3 parameterless case next to cases with fields, `Apple`  | `{"Apple":{}}`                 | `{"type":"Apple"}`                  | `{"Apple":{}}`                           |
| Scala 3 parameterless case in an enum of such cases, `Small`   | `{"Small":{}}`                 | `{"type":"Small"}`                  | `"Small"`                                |

Four rules explain the table:

- The discriminator always wins. With a discriminator, every case is an object, whatever `enumValuesAsStrings` says.
- The string form applies to a whole family or not at all. One case with fields keeps every case of the family in the object form, the case objects included.
- An empty case class counts as a fieldless case on Scala 2 only. On Scala 3, a family with a `final case class Off()` is not an enumeration, so `enumValuesAsStrings` has no effect on it.
- The defaults differ. `enumValuesAsStrings` is `false` by default on Scala 2 and `true` on Scala 3. Without any configuration, Scala 2 uses the wrapper column and Scala 3 uses the last column.

The last two rules are the reason the same model can produce different JSON on the two versions. If you cross-build a library, or move it from Scala 2 to Scala 3, set `enumValuesAsStrings` explicitly and use case objects rather than empty case classes. [JsonCodecConfiguration](../reference/json-codec-configuration.md#scala-2-and-scala-3-differences) lists the remaining differences between the versions.

### The Same Cases on Scala 2

The wrapper column is the Scala 2 default. We define one family with a field, one of case objects only, and one that mixes a case object with an empty case class:

```scala
import zio.json._

sealed trait Shape
final case class Circle(r: Double) extends Shape
case object Unknown                extends Shape

object Shape {
  implicit val codec: JsonCodec[Shape] = DeriveJsonCodec.gen[Shape]
}

sealed trait Color
case object Red   extends Color
case object Green extends Color

object Color {
  implicit val codec: JsonCodec[Color] = DeriveJsonCodec.gen[Color]
}

sealed trait Signal
case object On         extends Signal
final case class Off() extends Signal

object Signal {
  implicit val codec: JsonCodec[Signal] = DeriveJsonCodec.gen[Signal]
}
```

Every case is a wrapper object, including the case objects:

```scala
println((Circle(1.0): Shape).toJson)
// {"Circle":{"r":1.0}}
println((Unknown: Shape).toJson)
// {"Unknown":{}}
println((Red: Color).toJson)
// {"Red":{}}
println((Off(): Signal).toJson)
// {"Off":{}}
```

With `enumValuesAsStrings = true`, the families without fields become strings. On Scala 2 that includes the family with the empty case class:

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

sealed trait Signal
case object On         extends Signal
final case class Off() extends Signal

object Signal {
  implicit val codec: JsonCodec[Signal] = DeriveJsonCodec.gen[Signal]
}
```

The case objects and the empty case class are now plain strings:

```scala
println((Red: Color).toJson)
// "Red"
println((On: Signal).toJson)
// "On"
println((Off(): Signal).toJson)
// "Off"
```

A discriminator turns every case into an object again, even with `enumValuesAsStrings = true` in scope:

```scala
import zio.json._

implicit val config: JsonCodecConfiguration =
  JsonCodecConfiguration(enumValuesAsStrings = true)

@jsonDiscriminator("type")
sealed trait Shape
final case class Circle(r: Double) extends Shape
case object Unknown                extends Shape

object Shape {
  implicit val codec: JsonCodec[Shape] = DeriveJsonCodec.gen[Shape]
}

@jsonDiscriminator("type")
sealed trait Color
case object Red   extends Color
case object Green extends Color

object Color {
  implicit val codec: JsonCodec[Color] = DeriveJsonCodec.gen[Color]
}
```

The hint is now a field of each case's object:

```scala
println((Circle(1.0): Shape).toJson)
// {"type":"Circle","r":1.0}
println((Unknown: Shape).toJson)
// {"type":"Unknown"}
println((Red: Color).toJson)
// {"type":"Red"}
```

### The Same Cases on Scala 3

On Scala 3, `derives JsonCodec` uses the default configuration, where `enumValuesAsStrings` is on. The example file defines the same families, plus two enums:

```scala
object Defaults {
  sealed trait Shape derives JsonCodec
  final case class Circle(r: Double) extends Shape
  case object Unknown                extends Shape

  sealed trait Color derives JsonCodec
  case object Red   extends Color
  case object Green extends Color

  sealed trait Signal derives JsonCodec
  case object On         extends Signal
  final case class Off() extends Signal

  enum Fruit derives JsonCodec {
    case Banana(curvature: Double)
    case Apple
  }

  enum Size derives JsonCodec {
    case Small, Large
  }
}
```

Running the file prints the following for these families. Only `Red` and `Small` are strings, because `Shape`, `Fruit` and `Signal` each have a case with fields or an empty case class:

```text
Defaults, case class: {"Circle":{"r":1.0}}, decodes back to Right(Circle(1.0))
Defaults, case object next to a case class: {"Unknown":{}}, decodes back to Right(Unknown)
Defaults, case object in an all-object family: "Red", decodes back to Right(Red)
Defaults, case object next to an empty case class: {"On":{}}, decodes back to Right(On)
Defaults, empty case class: {"Off":{}}, decodes back to Right(Off())
Defaults, enum case with fields: {"Banana":{"curvature":0.5}}, decodes back to Right(Banana(0.5))
Defaults, parameterless case in a mixed enum: {"Apple":{}}, decodes back to Right(Apple)
Defaults, parameterless case in an all-parameterless enum: "Small", decodes back to Right(Small)
```

To get the Scala 2 default on Scala 3, put a `given` configuration with `enumValuesAsStrings = false` in scope where the codecs are derived:

```scala
object StringsOff {
  given JsonCodecConfiguration = JsonCodecConfiguration(enumValuesAsStrings = false)

  sealed trait Color derives JsonCodec
  case object Red   extends Color
  case object Green extends Color

  enum Size derives JsonCodec {
    case Small, Large
  }
}
```

`StringsOff.Red` is then written as `{"Red":{}}` and `StringsOff.Size.Small` as `{"Small":{}}`.

A discriminator works on enums as it does on sealed traits:

```scala
object Discriminated {
  @jsonDiscriminator("type")
  sealed trait Signal derives JsonCodec
  case object On         extends Signal
  final case class Off() extends Signal

  @jsonDiscriminator("type")
  enum Fruit derives JsonCodec {
    case Banana(curvature: Double)
    case Apple
  }

  @jsonDiscriminator("type")
  enum Size derives JsonCodec {
    case Small, Large
  }
}
```

Every case becomes an object with a `type` field: `{"type":"On"}`, `{"type":"Off"}`, `{"type":"Banana","curvature":0.5}`, `{"type":"Apple"}` and `{"type":"Small"}`.

## Round-Trip Pitfalls

Each codec in the table above reads back what it writes. The pitfalls below are the cases where it does not: the value that comes back differs from the one that went in, or the decoder rejects the JSON its own encoder wrote. They behave the same on Scala 2 and Scala 3.

### An `Option` Field With a `Some` Default

An encoder leaves a `None` field out of the JSON. A decoder fills a missing field with its default value, and only falls back to `None` when there is no default. So when the default is not `None`, a `None` comes back as the default:

```scala
import zio.json._
import zio.json.ast.Json

final case class Timeout(seconds: Option[Int] = Some(30))

object Timeout {
  implicit val codec: JsonCodec[Timeout] = DeriveJsonCodec.gen[Timeout]
}
```

`Timeout(None)` is written as `{}` and read back as `Timeout(Some(30))`:

```scala
println(Timeout(None).toJson)
// {}

Timeout(None).toJson.fromJson[Timeout]
// res18: Either[String, Timeout] = Right(
//   value = Timeout(seconds = Some(value = 30))
// )
```

`@jsonExplicitNull` does not help, because the decoder treats `null` like a missing field and uses the default too. If `None` must mean something other than the default, make `None` the default, or use a type of your own that can say "not set". The [optional and default fields guide](../how-to/optional-and-default-fields.md#6-keep-round-trips-in-mind) shows the rules behind this.

### A Nested `Option[Option[A]]`

An `Option[Option[A]]` field looks like a way to tell "absent" apart from "explicitly `null`". The encoder treats both `None` and `Some(None)` as "nothing", so it writes the same JSON for them, and the decoder reads that JSON as `None`:

```scala
final case class Update(nickname: Option[Option[String]])

object Update {
  implicit val codec: JsonCodec[Update] = DeriveJsonCodec.gen[Update]
}
```

`Some(None)` does not survive the trip:

```scala
println(Update(Some(None)).toJson)
// {}

Update(Some(None)).toJson.fromJson[Update]
// res20: Either[String, Update] = Right(value = Update(nickname = None))
```

To tell the three states apart, decode the object to the [`Json` AST](../reference/json-ast.md) and look at its keys yourself.

### Omitting Empty Collections Without Filling Them In

`ExplicitEmptyCollections(encoding = false)` makes the encoder leave out a field whose collection is empty. The `decoding` flag is separate and defaults to `true`, which means a missing collection field is an error. With only the encoding side turned off, the codec rejects its own output:

```scala
@jsonExplicitEmptyCollections(encoding = false)
final case class Basket(items: List[String])

object Basket {
  implicit val codec: JsonCodec[Basket] = DeriveJsonCodec.gen[Basket]
}
```

The empty list is left out, and the missing field then fails to decode:

```scala
println(Basket(Nil).toJson)
// {}

Basket(Nil).toJson.fromJson[Basket]
// res22: Either[String, Basket] = Left(value = ".items(missing)")
```

Turn off both directions together, so that a missing field decodes as an empty collection:

```scala
@jsonExplicitEmptyCollections(encoding = false, decoding = false)
final case class Cart(items: List[String])

object Cart {
  implicit val codec: JsonCodec[Cart] = DeriveJsonCodec.gen[Cart]
}
```

Now the round trip works:

```scala
Cart(Nil).toJson.fromJson[Cart]
// res23: Either[String, Cart] = Right(value = Cart(items = List()))
```

The same applies to `JsonCodecConfiguration(explicitEmptyCollections = ...)`, described in [JsonCodecConfiguration](../reference/json-codec-configuration.md#explicitemptycollections).

### A Discriminator With Strict Field Checks

With a discriminator, the decoder passes the whole object, discriminator field included, to the decoder of the case. When that case decoder rejects unknown fields, because of `allowExtraFields = false` or `@jsonNoExtraFields` on the case class, it rejects the discriminator too. Nothing warns at compile time:

```scala
object Strict {
  implicit val config: JsonCodecConfiguration =
    JsonCodecConfiguration(
      sumTypeHandling = JsonCodecConfiguration.SumTypeHandling.DiscriminatorField("type"),
      allowExtraFields = false
    )

  sealed trait Shape
  final case class Square(side: Double) extends Shape
  case object Point                     extends Shape

  implicit val codec: JsonCodec[Shape] = DeriveJsonCodec.gen[Shape]
}
```

Neither case decodes the JSON that the encoder writes for it:

```scala
println((Strict.Square(2.0): Strict.Shape).toJson)
// {"type":"Square","side":2.0}

(Strict.Square(2.0): Strict.Shape).toJson.fromJson[Strict.Shape]
// res25: Either[String, Strict.Shape] = Left(
//   value = "(Square)(invalid extra field)"
// )

(Strict.Point: Strict.Shape).toJson.fromJson[Strict.Shape]
// res26: Either[String, Strict.Shape] = Left(
//   value = "(Point)(expected '}' got '\"')"
// )
```

Keep `allowExtraFields = true` for a family with a discriminator, or use the wrapper object when you need unknown fields rejected. The [sealed traits guide](../how-to/sealed-traits.md#3-use-a-discriminator-field-with-jsondiscriminator) has the same warning.

### Comparing Numbers in the `Json` AST

`Json.Num` holds a `java.math.BigDecimal` and compares with `BigDecimal#equals`, which also compares the scale (see [Equality](../reference/json-ast.md#equality)). `1` and `1.0` decode to the same `Double`, but as `Json` values they are different. This bites in tests that compare an encoder's output with expected JSON as trees:

```scala
final case class Weight(kg: Double)

object Weight {
  implicit val codec: JsonCodec[Weight] = DeriveJsonCodec.gen[Weight]
}
```

The `Double` encoder writes `1.0`, so the tree differs from one parsed from `{"kg":1}`, although both decode to the same `Weight`:

```scala
Weight(1.0).toJsonAST == """{"kg":1}""".fromJson[Json]
// res27: Boolean = false

"""{"kg":1}""".fromJson[Weight] == Right(Weight(1.0))
// res28: Boolean = true
```

Compare decoded values, or write the expected JSON with the scale the encoder produces. A `BigDecimal` keeps the scale it was given, so `BigDecimal("1.50")` is written as `1.50`, not `1.5`.

### Non-Finite Doubles Become Strings

JSON has no literal for `NaN` or infinity. The `Double` and `Float` encoders write them as the strings `"NaN"`, `"Infinity"` and `"-Infinity"`, and the decoders accept those strings back:

```scala
println(Weight(Double.NaN).toJson)
// {"kg":"NaN"}

Weight(Double.NaN).toJson.fromJson[Weight]
// res30: Either[String, Weight] = Right(value = Weight(kg = NaN))
```

The round trip works within ZIO JSON, but another program that reads the JSON finds a string where it expects a number. `NaN` also never equals itself, so a round-trip test that compares with `==` fails for it even though the value came back. `JsonEncoder#toJsonAST` throws on these values instead of producing a string; [Non-Finite Numbers](../reference/json-encoder.md#non-finite-numbers) has the details.

## Pin the Format With Golden Tests

A change to a type or a setting can change the wire format without breaking any compile or any round-trip test: renaming a case renames its hint, adding a case with fields switches a whole family out of the string form, and moving to Scala 3 flips the `enumValuesAsStrings` default. A round-trip test still passes in all these cases, because the codec reads its own new output. Only the clients that depend on the old JSON break.

A *golden test* catches this. On its first run it writes the encoder's output for a set of generated sample values to a file for you to review and accept. From then on it fails whenever the output differs from the accepted file. The `zio-json-golden` module provides `goldenTest` for ZIO Test, and the [golden testing guide](../how-to/golden-testing.md) shows how to set it up. Add one for every type whose JSON leaves your program, and review a changed golden file as you would review a change to a public API.

## See Also

- [Work With Sealed Traits](../how-to/sealed-traits.md) to choose and configure the hint
- [Optional, Default and Null Fields](../how-to/optional-and-default-fields.md) for the rules on missing and `null` fields
- [JsonCodecConfiguration](../reference/json-codec-configuration.md) for every derivation option and its defaults
- [How Strict Is the Decoder](decoder-strictness.md) for what the decoder accepts beyond what the encoder writes
