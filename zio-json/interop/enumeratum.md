# Enumeratum Interop

> Give enumeratum string enums and value enums ZIO JSON codecs and map-key codecs, with traits to mix in or builders that choose the case of the names.

The `zio-json-interop-enumeratum` module connects ZIO JSON to [enumeratum](https://github.com/lloydmeta/enumeratum). It gives enumeratum enums `JsonEncoder` and `JsonDecoder` instances, plus `JsonFieldEncoder` and `JsonFieldDecoder` instances so enum members can be keys of a `Map`. A string enum (`Enum[A]`) is written as the member's name, and a value enum (`IntEnum[A]`, `StringEnum[A]` and the others) is written as the member's value.

## Installation

Add the module to your `build.sbt`:

```scala
libraryDependencies += "dev.zio" %% "zio-json-interop-enumeratum" % "1.1.3"
```

The module is published for the JVM, Scala.js and Scala Native, for Scala 2.12, 2.13 and 3. In a Scala.js or Scala Native project, use `%%%` instead of `%%`. On Scala 3, value enums need an extra compiler option, see [Scala 3](#scala-3).

## Imports

Unlike the other interop modules, which live under `zio.json.interop`, the enumeratum integration lives in enumeratum's own packages. The string-enum traits and builders are in `enumeratum`, and the value-enum ones are in `enumeratum.values`:

```scala
import enumeratum._        // ZioJsonEnum, ZioJsonKeyEnum, ZioJson
import enumeratum.values._ // IntZioJsonEnum, LongZioJsonEnum, ..., ZioJson
```

Both packages contain an object named `ZioJson`. When you import both packages in the same file, refer to the builders as `enumeratum.ZioJson` and `enumeratum.values.ZioJson`, as the examples below do.

## String Enums

The quickest way to give a string enum its codecs is to mix traits into its companion object. `ZioJsonEnum` adds an implicit `JsonEncoder` and `JsonDecoder`, and `ZioJsonKeyEnum` adds an implicit `JsonFieldEncoder` and `JsonFieldDecoder` for map keys:

```scala
import enumeratum._
import zio.json._

sealed trait ShirtSize extends EnumEntry

case object ShirtSize extends Enum[ShirtSize] with ZioJsonEnum[ShirtSize] with ZioJsonKeyEnum[ShirtSize] {
  case object Small  extends ShirtSize
  case object Medium extends ShirtSize
  case object Large  extends ShirtSize

  val values = findValues
}
```

A member is encoded as its `entryName`, which by default is the name of the case object. The instances are for the enum type `ShirtSize`, and `JsonEncoder` is invariant, so a value whose static type is `ShirtSize.Small.type` has no encoder. Widen it to `ShirtSize` first:

```scala
(ShirtSize.Small: ShirtSize).toJson
// res1: String = "\"Small\""

ShirtSize.values.toJson
// res2: String = "[\"Small\",\"Medium\",\"Large\"]"
```

Decoding accepts the exact name only. A name that is not a member fails, and so does a name in the wrong case:

```scala
""""Large"""".fromJson[ShirtSize]
// res3: Either[String, ShirtSize] = Right(value = Large)

""""XLarge"""".fromJson[ShirtSize]
// res4: Either[String, ShirtSize] = Left(
//   value = "('XLarge' is not a member of enum ShirtSize)"
// )

""""large"""".fromJson[ShirtSize]
// res5: Either[String, ShirtSize] = Left(
//   value = "('large' is not a member of enum ShirtSize)"
// )
```

Because the companion also mixes in `ZioJsonKeyEnum`, the enum works as a `Map` key. Keys are matched by exact name too:

```scala
Map[ShirtSize, Int](ShirtSize.Small -> 3, ShirtSize.Large -> 1).toJson
// res6: String = "{\"Small\":3,\"Large\":1}"

"""{"Medium":2}""".fromJson[Map[ShirtSize, Int]]
// res7: Either[String, Map[ShirtSize, Int]] = Right(value = Map(Medium -> 2))
```

## Choosing the Case of Names

The traits always write the exact `entryName` and accept only the exact name. When an API sends names in lower case or upper case, or in any case, build the instances yourself with the builders in `enumeratum.ZioJson`. Each one takes the enum's companion object, which is `this` when you call the builder inside the companion:

| Builder                  | Result                | Behaviour                                                                    |
| ------------------------ | --------------------- | ---------------------------------------------------------------------------- |
| `encoder`                | `JsonEncoder[A]`      | Writes `entryName` (what `ZioJsonEnum` uses).                                |
| `encoderLowercase`       | `JsonEncoder[A]`      | Writes `entryName` in lower case.                                            |
| `encoderUppercase`       | `JsonEncoder[A]`      | Writes `entryName` in upper case.                                            |
| `decoder`                | `JsonDecoder[A]`      | Accepts the exact `entryName` (what `ZioJsonEnum` uses).                     |
| `decoderLowercaseOnly`   | `JsonDecoder[A]`      | Accepts only the lower-case `entryName`.                                     |
| `decoderUppercaseOnly`   | `JsonDecoder[A]`      | Accepts only the upper-case `entryName`.                                     |
| `decoderCaseInsensitive` | `JsonDecoder[A]`      | Accepts `entryName` in any case.                                             |
| `keyEncoder`             | `JsonFieldEncoder[A]` | Writes `entryName` as an object key (what `ZioJsonKeyEnum` uses).            |
| `keyDecoder`             | `JsonFieldDecoder[A]` | Accepts the exact `entryName` as an object key (what `ZioJsonKeyEnum` uses). |

Every decoder fails with the message `'<input>' is not a member of enum <EnumName>`. Use the builders instead of mixing in `ZioJsonEnum`, otherwise the companion object has two implicit encoders for the same type. This enum writes lower-case names and accepts any case:

```scala
sealed trait Color extends EnumEntry

case object Color extends Enum[Color] {
  case object Red       extends Color
  case object DarkGreen extends Color

  val values = findValues

  implicit val encoder: JsonEncoder[Color] = enumeratum.ZioJson.encoderLowercase(this)
  implicit val decoder: JsonDecoder[Color] = enumeratum.ZioJson.decoderCaseInsensitive(this)
}
```

The encoder lower-cases the name, and the decoder accepts every spelling:

```scala
(Color.DarkGreen: Color).toJson
// res8: String = "\"darkgreen\""

""""DARKGREEN"""".fromJson[Color]
// res9: Either[String, Color] = Right(value = DarkGreen)

""""darkGreen"""".fromJson[Color]
// res10: Either[String, Color] = Right(value = DarkGreen)
```

## Value Enums

A value enum is an enumeratum enum whose members carry a value, such as an `Int` or a `String`, and it is encoded as that value. Mix the trait that matches the value type into the companion object:

| Value type | Enum type    | Trait               |
| ---------- | ------------ | ------------------- |
| `Int`      | `IntEnum`    | `IntZioJsonEnum`    |
| `Long`     | `LongEnum`   | `LongZioJsonEnum`   |
| `Short`    | `ShortEnum`  | `ShortZioJsonEnum`  |
| `String`   | `StringEnum` | `StringZioJsonEnum` |
| `Char`     | `CharEnum`   | `CharZioJsonEnum`   |
| `Byte`     | `ByteEnum`   | `ByteZioJsonEnum`   |

Each trait provides an implicit `JsonEncoder`, `JsonDecoder`, `JsonFieldEncoder` and `JsonFieldDecoder`, so value enums work as map keys without a second trait. All six extend the common trait `ZioJsonValueEnum`. Here is an `IntEnum`:

```scala
import enumeratum.values._

sealed abstract class LibraryItem(val value: Int, val name: String) extends IntEnumEntry

case object LibraryItem extends IntEnum[LibraryItem] with IntZioJsonEnum[LibraryItem] {
  case object Book     extends LibraryItem(value = 1, name = "book")
  case object Movie    extends LibraryItem(value = 2, name = "movie")
  case object Magazine extends LibraryItem(value = 3, name = "magazine")
  case object CD       extends LibraryItem(value = 4, name = "cd")

  val values = findValues
}
```

A member is written as its value, and decoding looks the value up. As with string enums, widen a member to the enum type before encoding it:

```scala
(LibraryItem.Book: LibraryItem).toJson
// res11: String = "1"

"3".fromJson[LibraryItem]
// res12: Either[String, LibraryItem] = Right(value = Magazine)

"999".fromJson[LibraryItem]
// res13: Either[String, LibraryItem] = Left(
//   value = "('999' is not a member of enum LibraryItem)"
// )
```

As a map key, the value is written as the key's text:

```scala
Map[LibraryItem, String](LibraryItem.CD -> "shelf 4").toJson
// res14: String = "{\"4\":\"shelf 4\"}"

"""{"2":"shelf 9"}""".fromJson[Map[LibraryItem, String]]
// res15: Either[String, Map[LibraryItem, String]] = Right(
//   value = Map(Movie -> "shelf 9")
// )
```

## Value Enum Builders

To create the instances without a trait, for example to give only some of them to an enum, use the builders in `enumeratum.values.ZioJson`:

| Builder      | Result                        | Requires                      |
| ------------ | ----------------------------- | ----------------------------- |
| `encoder`    | `JsonEncoder[EntryType]`      | `JsonEncoder[ValueType]`      |
| `decoder`    | `JsonDecoder[EntryType]`      | `JsonDecoder[ValueType]`      |
| `keyEncoder` | `JsonFieldEncoder[EntryType]` | `JsonFieldEncoder[ValueType]` |
| `keyDecoder` | `JsonFieldDecoder[EntryType]` | `JsonFieldDecoder[ValueType]` |

ZIO JSON has built-in key codecs for `String`, `Int`, `Long` and `UUID` (see [Map Keys](../reference/json-field-codecs.md)), so `keyEncoder` and `keyDecoder` for a `Short`, `Char` or `Byte` enum need a key codec for that value type in scope. The `ShortZioJsonEnum`, `CharZioJsonEnum` and `ByteZioJsonEnum` traits bring their own. This `ShortEnum` gets only an encoder and a decoder:

```scala
sealed abstract class Priority(val value: Short) extends ShortEnumEntry

case object Priority extends ShortEnum[Priority] {
  case object Low  extends Priority(1)
  case object High extends Priority(9)

  val values = findValues

  implicit val encoder: JsonEncoder[Priority] = enumeratum.values.ZioJson.encoder(this)
  implicit val decoder: JsonDecoder[Priority] = enumeratum.values.ZioJson.decoder(this)
}
```

The builders behave like the trait instances:

```scala
(Priority.High: Priority).toJson
// res16: String = "9"

"9".fromJson[Priority]
// res17: Either[String, Priority] = Right(value = High)

"5".fromJson[Priority]
// res18: Either[String, Priority] = Left(
//   value = "('5' is not a member of enum Priority)"
// )
```

## Scala 3

On Scala 3, enumeratum's `findValues` macro for value enums reads the arguments passed to each member's constructor, which needs the `-Yretain-trees` compiler option. Without it, the compiler stops at `findValues` with `Option -Yretain-trees must be set in scalacOptions`. Add the option to your build:

```scala
scalacOptions += "-Yretain-trees"
```

On Scala 3.9.0, string enums (`Enum[A]`) compile without the option. The module's own build sets it for Scala 3, so setting it in your build as well is the safe choice whenever you use enumeratum on Scala 3.
