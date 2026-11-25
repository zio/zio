# Decode JSON Whose Shape Differs from Your Case Class

> Decode JSON whose keys or layout do not match your case class: rename fields with annotations, decode through an intermediate class, or read a Json tree by hand or with cursors.

Goal: decode JSON into a case class whose field names, or layout, do not match the JSON you receive.

The running example is a stock quote from a third-party API. Its keys are not valid Scala identifiers:

```json
{
  "01. symbol": "IBM",
  "02. open": "182.4300",
  "03. high": "182.8000"
}
```

The case class we want to fill uses plain names:

```scala
import zio.json._

final case class Quote(symbol: String, open: String, high: String)

object Quote {
  implicit val decoder: JsonDecoder[Quote] = DeriveJsonDecoder.gen[Quote]
}
```

A derived decoder looks for the keys `symbol`, `open` and `high`, so it fails on the first one it cannot find:

```scala
"""{"01. symbol":"IBM","02. open":"182.4300","03. high":"182.8000"}""".fromJson[Quote]
// res1: Either[String, Quote] = Left(value = ".symbol(missing)")
```

Each step below is a separate option, and each one makes this input decode to `Quote(IBM,182.4300,182.8000)`. Pick the one that fits your situation:

| Option                              | Pick it when                                                                                   | Error messages                                                     |
|-------------------------------------|------------------------------------------------------------------------------------------------|--------------------------------------------------------------------|
| 1. Rename fields with `@jsonField`  | You can edit the case class, and only the key names differ                                     | Derived, with the field path                                       |
| 2. Decode an intermediate class     | The domain class must stay free of JSON details, and the JSON is a flat object with fixed keys | Derived, with the field path                                       |
| 3. Decode to `Json` and match on it | The layout differs, not only the names, or you compute values from several fields              | The messages you write                                             |
| 4. Decode to `Json` with cursors    | As option 3, but the paths are reusable values and reach into nested objects in one line       | The cursor's, which name a missing key but not a wrongly typed one |

Options 1 and 2 decode in one pass, straight from the text. Options 3 and 4 first build the whole document as a [`Json`](../reference/json-ast.md) tree and then read from it, which costs more time and memory. When only one field differs, because it arrives in two shapes, see [Accept a Field That Arrives in Two Shapes](#accept-a-field-that-arrives-in-two-shapes) instead.

## 1. Rename Fields with `@jsonField`

`@jsonField` on a field gives the key the derived decoder looks for. This works only when you can change the case class:

```scala
import zio.json._

final case class Quote(
  @jsonField("01. symbol") symbol: String,
  @jsonField("02. open") open: String,
  @jsonField("03. high") high: String
)

object Quote {
  implicit val decoder: JsonDecoder[Quote] = DeriveJsonDecoder.gen[Quote]
}
```

The input now decodes, and a wrong value is reported at its JSON key:

```scala
"""{"01. symbol":"IBM","02. open":"182.4300","03. high":"182.8000"}""".fromJson[Quote]
// res3: Either[String, Quote] = Right(
//   value = Quote(symbol = "IBM", open = "182.4300", high = "182.8000")
// )

"""{"01. symbol":"IBM","02. open":182.43,"03. high":"182.8000"}""".fromJson[Quote]
// res4: Either[String, Quote] = Left(value = ".02. open(expected string)")
```

The annotation also renames the key on encoding. When many keys follow one pattern, such as `snake_case`, use `@jsonMemberNames` instead (see [Field Names](field-names.md)).

## 2. Decode an Intermediate Case Class

A private case class can mirror the JSON exactly, and `JsonDecoder#map` converts it into the domain class. Keys that are not valid Scala identifiers go in backticks:

```scala
import zio.json._

final case class Quote(symbol: String, open: String, high: String)

object Quote {
  private final case class JsonQuote(
    `01. symbol`: String,
    `02. open`: String,
    `03. high`: String
  )

  implicit val decoder: JsonDecoder[Quote] =
    DeriveJsonDecoder
      .gen[JsonQuote]
      .map { case JsonQuote(s, o, h) => Quote(s, o, h) }
}
```

`Quote` itself has no JSON annotations, and the errors are those of the derived `JsonQuote` decoder:

```scala
"""{"01. symbol":"IBM","02. open":"182.4300","03. high":"182.8000"}""".fromJson[Quote]
// res6: Either[String, Quote] = Right(
//   value = Quote(symbol = "IBM", open = "182.4300", high = "182.8000")
// )

"""{"01. symbol":"IBM","03. high":"182.8000"}""".fromJson[Quote]
// res7: Either[String, Quote] = Left(value = ".02. open(missing)")
```

Use `JsonDecoder#mapOrFail` instead of `JsonDecoder#map` when the conversion can fail, for example to validate a value (see [Validate Data While Decoding](validate-on-decode.md)).

## 3. Decode to `Json` and Match on It

`JsonDecoder[Json]` reads any valid JSON into a `Json` tree, and `JsonDecoder#mapOrFail` turns that tree into a `Quote` or an error message. `Json#asString` returns the text of a `Json.Str` and `None` for any other case, so a field that holds a number or an object is rejected. Do not use `toString` here: it renders the node as JSON, so a string would keep its quotes:

```scala
import zio.json._
import zio.json.ast.Json

final case class Quote(symbol: String, open: String, high: String)

object Quote {
  implicit val decoder: JsonDecoder[Quote] = JsonDecoder[Json]
    .mapOrFail {
      case Json.Obj(fields) =>
        def findField(name: String): Either[String, String] =
          fields
            .find(_._1 == name)
            .flatMap(_._2.asString)
            .toRight(left = s"Field '$name' is missing or not a string")

        for {
          symbol <- findField("01. symbol")
          open   <- findField("02. open")
          high   <- findField("03. high")
        } yield Quote(symbol, open, high)
      case _ =>
        Left("Not a JSON record")
    }
}
```

The input decodes, and a failure carries the message you wrote, at the position of the whole object:

```scala
"""{"01. symbol":"IBM","02. open":"182.4300","03. high":"182.8000"}""".fromJson[Quote]
// res9: Either[String, Quote] = Right(
//   value = Quote(symbol = "IBM", open = "182.4300", high = "182.8000")
// )

"""{"01. symbol":"IBM","03. high":"182.8000"}""".fromJson[Quote]
// res10: Either[String, Quote] = Left(
//   value = "(Field '02. open' is missing or not a string)"
// )
```

## 4. Decode to `Json` with Cursors

A [cursor](../reference/json-ast.md#cursors) is a path into a `Json` tree, built once and applied with `Json#get`. `JsonCursor.field` selects a key, `>>>` continues the path, and `JsonCursor.isString` rejects a value that is not a string, as `Json#asString` does in option 3:

```scala
import zio.json._
import zio.json.ast.{Json, JsonCursor}

final case class Quote(symbol: String, open: String, high: String)

object Quote {
  private val symbolC = JsonCursor.field("01. symbol") >>> JsonCursor.isString
  private val openC   = JsonCursor.field("02. open") >>> JsonCursor.isString
  private val highC   = JsonCursor.field("03. high") >>> JsonCursor.isString

  implicit val decoder: JsonDecoder[Quote] = JsonDecoder[Json]
    .mapOrFail { c =>
      for {
        symbol <- c.get(symbolC)
        open   <- c.get(openC)
        high   <- c.get(highC)
      } yield Quote(symbol.value, open.value, high.value)
    }
}
```

The input decodes. A missing key is named in the error, but a value of the wrong type is not:

```scala
"""{"01. symbol":"IBM","02. open":"182.4300","03. high":"182.8000"}""".fromJson[Quote]
// res12: Either[String, Quote] = Right(
//   value = Quote(symbol = "IBM", open = "182.4300", high = "182.8000")
// )

"""{"01. symbol":"IBM","03. high":"182.8000"}""".fromJson[Quote]
// res13: Either[String, Quote] = Left(value = "(No such field: '02. open')")

"""{"01. symbol":"IBM","02. open":182.43,"03. high":"182.8000"}""".fromJson[Quote]
// res14: Either[String, Quote] = Left(value = "(expected string)")
```

For values deeper in the document, extend the path, adding a `JsonCursor#isObject` step before each further `JsonCursor#field`, for example `JsonCursor.field("data").isObject.field("symbol").isString`. [Cursor Errors](../reference/json-ast.md#cursor-errors) lists the messages a cursor produces.

## Accept a Field That Arrives in Two Shapes

Sometimes only one field is irregular. Here some producers send `categories` as a JSON array, and others as one comma-separated string. Keep the derived decoder and give that field a decoder that tries both shapes with `JsonDecoder#orElse` (`<>`):

```scala
import zio.json._

final case class Animal(name: String, categories: List[String])

object Animal {
  private val commaSeparated: JsonDecoder[List[String]] =
    JsonDecoder[String].map(_.split(',').map(_.trim).toList)

  implicit val decoder: JsonDecoder[Animal] = {
    implicit val categories: JsonDecoder[List[String]] =
      JsonDecoder.list[String] <> commaSeparated
    DeriveJsonDecoder.gen[Animal]
  }
}
```

The local implicit is visible only to `DeriveJsonDecoder.gen` in that block, so other `List[String]` fields in your program keep the standard decoder. Both shapes now decode:

```scala
"""{"name": "Dog", "categories": "Warm-blooded, Mammal"}""".fromJson[Animal]
// res16: Either[String, Animal] = Right(
//   value = Animal(name = "Dog", categories = List("Warm-blooded", "Mammal"))
// )

"""{"name": "Snake", "categories": ["Cold-blooded", "Reptile"]}""".fromJson[Animal]
// res17: Either[String, Animal] = Right(
//   value = Animal(name = "Snake", categories = List("Cold-blooded", "Reptile"))
// )
```

A missing `categories` key decodes to an empty list instead of failing: the derived decoder no longer sees a plain collection decoder, so it asks the combined one for a value, and `JsonDecoder#orElse` passes the request on to the list decoder (see [Missing Fields](../reference/json-decoder.md#missing-fields)):

```scala
"""{"name": "Cat"}""".fromJson[Animal]
// res18: Either[String, Animal] = Right(
//   value = Animal(name = "Cat", categories = List())
// )
```

`JsonDecoder#orElse` buffers the input while the first decoder runs, so prefer `JsonDecoder.peekChar` when the first character tells the shapes apart (see [`peekChar`](../reference/json-decoder.md#peekchar)).

## See Also

- [Field Names](field-names.md) for renaming many keys at once, aliases and naming conventions
- [Validate Data While Decoding](validate-on-decode.md) for rejecting decoded values with `JsonDecoder#mapOrFail`
- [JSON AST](../reference/json-ast.md) for the `Json` type and cursors
- [JsonDecoder](../reference/json-decoder.md) for decoding from bytes, `JsonDecoder#map`, `JsonDecoder#mapOrFail`, `JsonDecoder#orElse` and missing fields
- [Annotations](../reference/annotations.md) for `@jsonField` and the other field annotations
- [Getting Started](../getting-started.md) for deriving a first decoder
