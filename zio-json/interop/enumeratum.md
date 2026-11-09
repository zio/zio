# Enumeratum Interop

> ```scala
> libraryDependencies ++= Seq(
>   "dev.zio" %% "zio-json-interop-enumeratum" % "1.1.2"
> )
> ```

## Installation

```scala
libraryDependencies ++= Seq(
  "dev.zio" %% "zio-json-interop-enumeratum" % "1.1.2"
)
```

## Imports

Unlike other interop modules which require a `zio.json.interop.*` import (e.g. `import zio.json.interop.refined._`), the enumeratum integration lives in the `enumeratum` package itself. The traits are available directly via the standard enumeratum imports:

```scala
import enumeratum._         // for string enums (ZioJsonEnum, ZioJsonKeyEnum)
import enumeratum.values._  // for value enums (IntZioJsonEnum, LongZioJsonEnum, etc.)
```

## String Enums

Mix in `ZioJsonEnum` to get implicit `JsonEncoder` and `JsonDecoder` instances for your enum entries. Add `ZioJsonKeyEnum` if you also need to use them as JSON object keys.

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

```scala
ShirtSize.Small.toJson
// "Small"

""""Large"""".fromJson[ShirtSize]
// Right(Large)

""""XLarge"""".fromJson[ShirtSize]
// Left('XLarge' is not a member of enum ShirtSize)
```

Lowercase/uppercase encoders and decoders are also available:

```scala
implicit val enc: JsonEncoder[ShirtSize] = ZioJson.encoderLowercase(ShirtSize)
implicit val dec: JsonDecoder[ShirtSize] = ZioJson.decoderLowercaseOnly(ShirtSize)
```

## Value Enums

For value-based enums (`IntEnum`, `LongEnum`, `StringEnum`, etc.), mix in the corresponding trait:

| Value Type | Trait                |
|------------|----------------------|
| `Int`      | `IntZioJsonEnum`     |
| `Long`     | `LongZioJsonEnum`    |
| `Short`    | `ShortZioJsonEnum`   |
| `String`   | `StringZioJsonEnum`  |
| `Char`     | `CharZioJsonEnum`    |
| `Byte`     | `ByteZioJsonEnum`    |

Each trait provides implicit `JsonEncoder`, `JsonDecoder`, `JsonFieldEncoder`, and `JsonFieldDecoder` instances.

```scala
import enumeratum.values._
import zio.json._

sealed abstract class LibraryItem(val value: Int, val name: String) extends IntEnumEntry

case object LibraryItem extends IntEnum[LibraryItem] with IntZioJsonEnum[LibraryItem] {
  case object Book     extends LibraryItem(value = 1, name = "book")
  case object Movie    extends LibraryItem(value = 2, name = "movie")
  case object Magazine extends LibraryItem(value = 3, name = "magazine")
  case object CD       extends LibraryItem(value = 4, name = "cd")

  val values = findValues
}
```

```scala
LibraryItem.Book.toJson
// 1

"3".fromJson[LibraryItem]
// Right(Magazine)

"999".fromJson[LibraryItem]
// Left('999' is not a member of enum LibraryItem)
```
