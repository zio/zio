# Example: a JSON Syntax

> This example builds a complete JSON syntax. One definition parses JSON text into a small AST and prints the AST back to text. It is adapted from the example in the library's tests.

```scala
import zio.Chunk
import zio.parser.*
```

## The AST

```scala
sealed abstract class Json
object Json {
  final case class Obj(fields: Chunk[(String, Json)]) extends Json
  final case class Arr(elements: Chunk[Json])         extends Json
  final case class Bool(value: Boolean)               extends Json
  final case class Str(value: String)                 extends Json
  final case class Num(value: BigDecimal)             extends Json
  case object Null                                    extends Json
}
```

## Whitespace

JSON allows any amount of whitespace between tokens. The parser accepts all of it, and the printer writes a single space:

```scala
val whitespace: Syntax[String, Char, Char, Char]  = Syntax.charIn(' ', '\t', '\r', '\n')
val whitespaces: Syntax[String, Char, Char, Unit] = whitespace.*.asPrinted((), Chunk(' '))
```

## Scalar Values

Every scalar syntax produces its own subtype of `Json`:

```scala
val nul: Syntax[String, Char, Char, Json.Null.type] =
  Syntax.string("null", Json.Null)

val bool: Syntax[String, Char, Char, Json.Bool] =
  Syntax.string("true", Json.Bool(true)) | Syntax.string("false", Json.Bool(false))

val quote: Syntax[String, Char, Char, Unit] = Syntax.char('"')

val str: Syntax[String, Char, Char, Json.Str] =
  (quote ~> Syntax.charNotIn('"').*.string <~ quote)
    .transform(Json.Str.apply, (s: Json.Str) => s.value)

val digits: Syntax[String, Char, Char, Chunk[Char]] = Syntax.digit.repeat

val num: Syntax[String, Char, Char, Json.Num] =
  (Syntax.char('-').? ~ digits ~ (Syntax.char('.') ~> digits).?).string
    .transform(s => Json.Num(BigDecimal(s)), (n: Json.Num) => n.value.toString)
```

The string syntax does not handle escape sequences, to keep the example short.

## Arrays and Objects

Arrays and objects contain `Json` values, so these syntaxes are recursive. Define them as `lazy val`:

```scala
val listSep: Syntax[String, Char, Char, Unit] =
  Syntax.char(',').surroundedBy(whitespaces)

lazy val list: Syntax[String, Char, Char, Json.Arr] =
  (Syntax.char('[') ~> json.repeatWithSep0(listSep) <~ Syntax.char(']'))
    .transform(Json.Arr.apply, (arr: Json.Arr) => arr.elements)

val keyValueSep: Syntax[String, Char, Char, Unit] =
  Syntax.char(':').surroundedBy(whitespaces)

lazy val keyValue: Syntax[String, Char, Char, (String, Json)] =
  (str ~ keyValueSep ~ json).transform[(String, Json)](
    { case (key, value) => (key.value, value) },
    { case (key, value) => (Json.Str(key), value) }
  )

lazy val obj: Syntax[String, Char, Char, Json.Obj] =
  (Syntax.char('{') ~> keyValue.repeatWithSep0(listSep).surroundedBy(whitespaces) <~ Syntax.char('}'))
    .transform(Json.Obj.apply, (o: Json.Obj) => o.fields)
```

## Putting It Together

`widen[Json]` turns each alternative into a `Syntax[..., Json]`. The parser tries them in order, and the printer uses the type of the value to find the alternative to print with:

```scala
lazy val json: Syntax[String, Char, Char, Json] =
  (nul.widen[Json] | bool.widen[Json] | str.widen[Json] | num.widen[Json] |
    list.widen[Json] | obj.widen[Json]) ?? "json"
```

## Parsing

```scala
val input = """{ "name": "zio-parser", "stars": [1, 2, 3], "stable": false, "next": null }"""
// input: String = "{ \"name\": \"zio-parser\", \"stars\": [1, 2, 3], \"stable\": false, \"next\": null }"

val parsed = json.parseString(input)
// parsed: Either[StringParserError[String], Json] = Right(
//   Obj(
//     IndexedSeq(
//       ("name", Str("zio-parser")),
//       ("stars", Arr(IndexedSeq(Num(1), Num(2), Num(3)))),
//       ("stable", Bool(false)),
//       ("next", Null)
//     )
//   )
// )
```

## Printing

The same `json` value prints the AST back:

```scala
parsed.flatMap(json.printString(_))
// res0: Either[StringParserError[String] | String, String] = Right(
//   "{ \"name\" : \"zio-parser\" , \"stars\" : [1 , 2 , 3] , \"stable\" : false , \"next\" : null }"
// )
```

Whitespace in the input is normalized, because `whitespaces` always prints a single space. Parsing the printed output gives the same AST:

```scala
parsed.flatMap(json.printString(_)).flatMap(json.parseString(_)) == parsed
// res1: Boolean = true
```

You can also build a document by hand and print it:

```scala
json.printString(Json.Arr(Chunk(Json.Num(BigDecimal(1)), Json.Bool(true), Json.Null)))
// res2: Either[String, String] = Right("[1 , true , null]")
```

## Errors

Parse failures are reported like in any other syntax. Because `json` tries six alternatives, `AllBranchesFailed` carries one failure per branch. See [Error handling](errors.md) for how to read them.
