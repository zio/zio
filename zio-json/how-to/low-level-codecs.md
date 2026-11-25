# Write a Fast Encoder or Decoder by Hand

> Hand-write a JsonEncoder and JsonDecoder with unsafeEncode, unsafeDecode, Write and Lexer, for hot paths and JSON shapes derivation cannot express.

Goal: write a `JsonEncoder` and a `JsonDecoder` by hand, directly against the writer and reader that ZIO JSON uses internally, and package them as one `JsonCodec`.

You rarely need this. Derived codecs and combinators such as `JsonEncoder#contramap`, `JsonDecoder#map` and `JsonCodec#transform` cover almost every type, and they are fast. Hand-writing pays off in two cases: a codec on a hot path, where profiling shows that the extra allocations and function calls of a combinator chain matter, and a JSON shape that derivation cannot produce. In this guide we write a codec for a geographic position that is stored as a two-element array, `[longitude, latitude]`, the way GeoJSON stores coordinates. We build it one piece at a time: a compact encoder, a decoder with its own validation and error messages, pretty printing, direct conversion to and from the `Json` tree, and finally the packaged codec and its tests.

:::warning[No compatibility guarantee]
`JsonEncoder#unsafeEncode` and `JsonDecoder#unsafeDecode` are part of the public traits, but `Write`, `Lexer`, `RetractReader` and everything else in the `zio.json.internal` package are internal. They can change in any release without a deprecation period. Keep hand-written codecs small, cover them with tests, and run those tests when you upgrade ZIO JSON.
:::

## What You Need

Only the core module:

```scala
libraryDependencies += "dev.zio" %% "zio-json" % "1.1.3"
```

The steps use these imports and this type:

```scala
import zio.json._
import zio.json.ast.Json
import zio.json.internal.{ Lexer, RetractReader, Write }

final case class Position(lon: Double, lat: Double)

val berlin = Position(13.405, 52.52)
val paris  = Position(2.35, 48.86)
```

The guide assumes you know `JsonEncoder`, `JsonDecoder` and `JsonCodec`. The reference pages describe the low-level APIs in full: [Writing an Encoder by Hand](../reference/json-encoder.md#writing-an-encoder-by-hand) and [Writing a Decoder by Hand](../reference/json-decoder.md#writing-a-decoder-by-hand).

## When Hand-Writing Is Worth It

A derived codec writes `Position` as an object with one field per constructor parameter, not as the array we need:

```scala
println(DeriveJsonEncoder.gen[Position].encodeJson(berlin))
// {"lon":13.405,"lat":52.52}
```

Before writing anything by hand, try the combinators. The built-in codec for a tuple writes an array, and `JsonCodec#transform` maps it to `Position` and back:

```scala
val viaTuple: JsonCodec[Position] =
  JsonCodec(JsonEncoder[(Double, Double)], JsonDecoder[(Double, Double)])
    .transform[Position](t => Position(t._1, t._2), p => (p.lon, p.lat))
```

This codec writes the right shape, and for most programs it is the right choice:

```scala
println(viaTuple.encoder.encodeJson(berlin))
// [13.405,52.52]

viaTuple.decoder.decodeJson("[13.405]")
// res3: Either[String, Position] = Left(value = "(expected ',' got ']')")
```

It has two costs. Every position goes through an intermediate `Tuple2` and an extra function call in each direction, which adds up when a document holds millions of coordinates. And its errors describe the tuple, not the position: `expected ',' got ']'` does not tell the sender that a latitude is missing, and nothing checks that a latitude lies between -90 and 90. The hand-written codec in the following steps fixes both.

## 1. Write the Encoder

An encoder implements one method, `JsonEncoder#unsafeEncode`, which appends the JSON for a value to `out`, a `Write`. `Write` is a minimal, unsynchronized writer: its `Write#write` overloads copy characters to the output as they are, without quoting or escaping. For each number, we call the built-in `Double` encoder, so the formatting matches the rest of the library:

```scala
val compactEncoder: JsonEncoder[Position] = new JsonEncoder.AbstractJsonEncoder[Position] {
  def unsafeEncode(p: Position, indent: Option[Int], out: Write): Unit = {
    out.write('[')
    JsonEncoder.double.unsafeEncode(p.lon, indent, out)
    out.write(',')
    JsonEncoder.double.unsafeEncode(p.lat, indent, out)
    out.write(']')
  }
}
```

`JsonEncoder.AbstractJsonEncoder` is an abstract class that extends `JsonEncoder` and adds nothing. Extending it instead of the trait compiles to smaller class files. Encoding a position gives the array:

```scala
println(compactEncoder.encodeJson(berlin))
// [13.405,52.52]
```

Delegating to `JsonEncoder.double` matters for more than formatting. `Double#toString` writes `NaN` and `Infinity`, which are not valid JSON, while `JsonEncoder.double` writes them as the quoted strings `"NaN"` and `"Infinity"` (see [Non-Finite Numbers](../reference/json-encoder.md#non-finite-numbers)). The same rule applies to text: write a JSON string with `JsonEncoder.string`, which adds the quotes and escapes `"`, `\` and control characters, never with `out.write(s)`.

:::warning[Exclusive end index]
`Write#write(cs: Array[Char], from: Int, to: Int)` takes an exclusive end index `to`, not a length as `java.io.Writer#write(cbuf, off, len)` does. It writes `cs(from)` up to `cs(to - 1)`. Passing a length writes the wrong slice without any error. The [`Write` interface](../reference/json-encoder.md#the-write-interface) section lists the other overloads, including the `Short` overloads that write two packed characters rather than a number.
:::

## 2. Write the Decoder

A decoder implements `JsonDecoder#unsafeDecode`. It receives the reader, a `RetractReader`, positioned just before the value, and must leave it just after the value. It reports an error by throwing, with `Lexer.error(message, trace)`, where `trace` is the path to the current value. Before decoding a child value it prepends that child's position to `trace`, so an error inside the child points at it. The [decoding contract](../reference/json-decoder.md#the-decoding-contract) states these rules in full.

The decoder below reads the brackets and the comma itself, delegates both numbers to the built-in `Double` decoder, and then checks the ranges. The range check lives in its own function, because the `Json` decoder in step 4 reuses it:

```scala
val lonTrace: JsonError = JsonError.ArrayAccess(0)
val latTrace: JsonError = JsonError.ArrayAccess(1)

def validated(lon: Double, lat: Double, trace: List[JsonError]): Position = {
  if (!(lon >= -180 && lon <= 180)) Lexer.error("longitude must be between -180 and 180", lonTrace :: trace)
  if (!(lat >= -90 && lat <= 90)) Lexer.error("latitude must be between -90 and 90", latTrace :: trace)
  Position(lon, lat)
}

val textDecoder: JsonDecoder[Position] = new JsonDecoder.AbstractJsonDecoder[Position] {
  def unsafeDecode(trace: List[JsonError], in: RetractReader): Position = {
    Lexer.char(trace, in, '[')
    if (!Lexer.firstArrayElement(in)) Lexer.error("expected [longitude, latitude]", trace)
    val lon = JsonDecoder.double.unsafeDecode(lonTrace :: trace, in)
    if (in.nextNonWhitespace() != ',') Lexer.error("expected [longitude, latitude]", trace)
    val lat = JsonDecoder.double.unsafeDecode(latTrace :: trace, in)
    if (in.nextNonWhitespace() != ']') Lexer.error("expected [longitude, latitude]", trace)
    validated(lon, lat, trace)
  }
}
```

Each call moves the reader forward:

- `Lexer.char` skips whitespace and reads the `[`, or fails with `expected '[' got '...'`.
- `Lexer.firstArrayElement` reads the next non-whitespace character. For `]` it returns `false`, which means the array is empty. Otherwise it puts the character back with `RetractReader#retract` and returns `true`.
- `JsonDecoder.double` reads the number. A number has no closing character, so the decoder reads one character past it and retracts it. Afterwards, the next character read is the one right after the number.
- `RetractReader#nextNonWhitespace` skips whitespace and returns the next character, which must be the `,` or the `]`.

`RetractReader#retract` can put back only the last character read, and only once after each read. When your decoder needs to look ahead, read one character, decide, and retract at most that one character.

The range checks are written as `!(lon >= -180 && lon <= 180)` rather than `lon < -180 || lon > 180`, because every comparison with `NaN` is `false`. The `Double` decoder accepts the quoted string `"NaN"`, and only the first form rejects it. The decoder accepts valid positions with any whitespace, and every other input fails with a message that names the problem:

```scala
textDecoder.decodeJson(" [ 13.405 , 52.52 ] ")
// res5: Either[String, Position] = Right(
//   value = Position(lon = 13.405, lat = 52.52)
// )

textDecoder.decodeJson("[]")
// res6: Either[String, Position] = Left(
//   value = "(expected [longitude, latitude])"
// )

textDecoder.decodeJson("[13.405]")
// res7: Either[String, Position] = Left(
//   value = "(expected [longitude, latitude])"
// )

textDecoder.decodeJson("[13.405, 52.52, 34]")
// res8: Either[String, Position] = Left(
//   value = "(expected [longitude, latitude])"
// )

textDecoder.decodeJson("[200, 52.52]")
// res9: Either[String, Position] = Left(
//   value = "[0](longitude must be between -180 and 180)"
// )

textDecoder.decodeJson("""["NaN", 52.52]""")
// res10: Either[String, Position] = Left(
//   value = "[0](longitude must be between -180 and 180)"
// )
```

The out-of-range longitude error starts with `[0]`, the path we prepended to `trace`. Wrong element types fail inside the `Double` decoder, at the same path, with its own message:

```scala
textDecoder.decodeJson("""["east", 52.52]""")
// res11: Either[String, Position] = Left(value = "[0](expected a Double)")
```

## 3. Support Pretty Printing

`toJsonPretty` calls `JsonEncoder#unsafeEncode` with `indent = Some(0)`, and nested encoders receive the nesting level of their value. The compact encoder from step 1 ignores `indent`, so its pretty output is still one line. Two helpers on the `JsonEncoder` companion object handle indentation: `JsonEncoder.bump` returns the indent one level deeper, and `JsonEncoder.pad` writes a newline and two spaces per level, or nothing at all in compact mode. Like the built-in array encoders, this encoder puts each element on its own line:

```scala
val prettyEncoder: JsonEncoder[Position] = new JsonEncoder.AbstractJsonEncoder[Position] {
  def unsafeEncode(p: Position, indent: Option[Int], out: Write): Unit = {
    val inner = JsonEncoder.bump(indent)
    out.write('[')
    JsonEncoder.pad(inner, out)
    JsonEncoder.double.unsafeEncode(p.lon, inner, out)
    out.write(',')
    JsonEncoder.pad(inner, out)
    JsonEncoder.double.unsafeEncode(p.lat, inner, out)
    JsonEncoder.pad(indent, out)
    out.write(']')
  }
}
```

The elements are padded at the inner level, and the closing bracket at the position's own level, so the output lines up wherever the position appears. Compact output does not change, because `JsonEncoder.pad` writes nothing when `indent` is `None`:

```scala
println(prettyEncoder.encodeJson(berlin, None))
// [13.405,52.52]

println(prettyEncoder.encodeJson(berlin, Some(0)))
// [
//   13.405,
//   52.52
// ]

println(JsonEncoder.list(prettyEncoder).encodeJson(List(berlin, paris), Some(0)))
// [
//   [
//     13.405,
//     52.52
//   ],
//   [
//     2.35,
//     48.86
//   ]
// ]
```

Keeping a short array on one line is also valid: the built-in tuple encoders write `[13.405, 52.52]` in pretty mode. Whichever layout you choose, pass the bumped indent to child encoders that write objects or arrays, so their own lines are indented correctly.

## 4. Build the `Json` Tree Directly

`JsonEncoder#toJsonAST` turns a value into a `Json` tree, and `JsonDecoder#unsafeFromJsonAST` reads a value from one. They are what the `toJsonAST` extension method, `JsonDecoder#fromJsonAST` and `Json#as` call. Their default implementations go through text: the encoder writes JSON text and parses it, and the decoder writes the tree as text and decodes that. Overriding them skips the detour.

To keep this step short, the new instances delegate the text path to the ones from the previous steps, and add the two overrides:

```scala
val positionEncoder: JsonEncoder[Position] = new JsonEncoder.AbstractJsonEncoder[Position] {
  def unsafeEncode(p: Position, indent: Option[Int], out: Write): Unit =
    prettyEncoder.unsafeEncode(p, indent, out)

  override def toJsonAST(p: Position): Either[String, Json] =
    if (java.lang.Double.isFinite(p.lon) && java.lang.Double.isFinite(p.lat))
      Right(Json.Arr(Json.Num(p.lon), Json.Num(p.lat)))
    else
      Left("a Position needs finite coordinates")
}

val positionDecoder: JsonDecoder[Position] = new JsonDecoder.AbstractJsonDecoder[Position] {
  def unsafeDecode(trace: List[JsonError], in: RetractReader): Position =
    textDecoder.unsafeDecode(trace, in)

  override def unsafeFromJsonAST(trace: List[JsonError], json: Json): Position =
    json match {
      case Json.Arr(elements) if elements.length == 2 =>
        val lon = JsonDecoder.double.unsafeFromJsonAST(lonTrace :: trace, elements(0))
        val lat = JsonDecoder.double.unsafeFromJsonAST(latTrace :: trace, elements(1))
        validated(lon, lat, trace)
      case _ =>
        Lexer.error("expected [longitude, latitude]", trace)
    }
}
```

The encoder returns a `Left` for a non-finite coordinate, because a `Json.Num` cannot hold `NaN` or an infinity: `Json.Num(Double.NaN)` throws a `NumberFormatException`. The decoder follows the same rules as `JsonDecoder#unsafeDecode`: it throws with `Lexer.error`, prepends the element's position to `trace`, and reuses `validated`, so both paths report the same errors:

```scala
positionEncoder.toJsonAST(berlin)
// res15: Either[String, Json] = Right(
//   value = Arr(elements = IndexedSeq(Num(value = 13.405), Num(value = 52.52)))
// )

positionEncoder.toJsonAST(Position(Double.NaN, 0))
// res16: Either[String, Json] = Left(
//   value = "a Position needs finite coordinates"
// )

positionDecoder.fromJsonAST(Json.Arr(Json.Num(13.405), Json.Num(52.52)))
// res17: Either[String, Position] = Right(
//   value = Position(lon = 13.405, lat = 52.52)
// )

positionDecoder.fromJsonAST(Json.Arr(Json.Num(13.405), Json.Num(95)))
// res18: Either[String, Position] = Left(
//   value = "[1](latitude must be between -90 and 90)"
// )

positionDecoder.fromJsonAST(Json.Arr(Json.Num(13.405)))
// res19: Either[String, Position] = Left(
//   value = "(expected [longitude, latitude])"
// )
```

Keep the text methods and the tree methods consistent: both must describe the same JSON and accept the same input.

## 5. Package the Codec

A `JsonCodec` pairs an encoder with a decoder, and putting it in the companion object of `Position` lets the compiler find it everywhere without an import. The complete codec below combines the previous steps, with each half written as one class instead of delegating:

```scala
import zio.json._
import zio.json.ast.Json
import zio.json.internal.{ Lexer, RetractReader, Write }

final case class Position(lon: Double, lat: Double)

object Position {
  private val lonTrace: JsonError = JsonError.ArrayAccess(0)
  private val latTrace: JsonError = JsonError.ArrayAccess(1)

  private def validated(lon: Double, lat: Double, trace: List[JsonError]): Position = {
    if (!(lon >= -180 && lon <= 180)) Lexer.error("longitude must be between -180 and 180", lonTrace :: trace)
    if (!(lat >= -90 && lat <= 90)) Lexer.error("latitude must be between -90 and 90", latTrace :: trace)
    Position(lon, lat)
  }

  private val encoder: JsonEncoder[Position] = new JsonEncoder.AbstractJsonEncoder[Position] {
    def unsafeEncode(p: Position, indent: Option[Int], out: Write): Unit = {
      val inner = JsonEncoder.bump(indent)
      out.write('[')
      JsonEncoder.pad(inner, out)
      JsonEncoder.double.unsafeEncode(p.lon, inner, out)
      out.write(',')
      JsonEncoder.pad(inner, out)
      JsonEncoder.double.unsafeEncode(p.lat, inner, out)
      JsonEncoder.pad(indent, out)
      out.write(']')
    }

    override def toJsonAST(p: Position): Either[String, Json] =
      if (java.lang.Double.isFinite(p.lon) && java.lang.Double.isFinite(p.lat))
        Right(Json.Arr(Json.Num(p.lon), Json.Num(p.lat)))
      else
        Left("a Position needs finite coordinates")
  }

  private val decoder: JsonDecoder[Position] = new JsonDecoder.AbstractJsonDecoder[Position] {
    def unsafeDecode(trace: List[JsonError], in: RetractReader): Position = {
      Lexer.char(trace, in, '[')
      if (!Lexer.firstArrayElement(in)) Lexer.error("expected [longitude, latitude]", trace)
      val lon = JsonDecoder.double.unsafeDecode(lonTrace :: trace, in)
      if (in.nextNonWhitespace() != ',') Lexer.error("expected [longitude, latitude]", trace)
      val lat = JsonDecoder.double.unsafeDecode(latTrace :: trace, in)
      if (in.nextNonWhitespace() != ']') Lexer.error("expected [longitude, latitude]", trace)
      validated(lon, lat, trace)
    }

    override def unsafeFromJsonAST(trace: List[JsonError], json: Json): Position =
      json match {
        case Json.Arr(elements) if elements.length == 2 =>
          val lon = JsonDecoder.double.unsafeFromJsonAST(lonTrace :: trace, elements(0))
          val lat = JsonDecoder.double.unsafeFromJsonAST(latTrace :: trace, elements(1))
          validated(lon, lat, trace)
        case _ =>
          Lexer.error("expected [longitude, latitude]", trace)
      }
  }

  implicit val codec: JsonCodec[Position] = JsonCodec(encoder, decoder)
}
```

Hand-written and derived codecs mix freely. A derived codec for a type that contains positions picks up `Position.codec` like any other instance:

```scala
final case class Route(name: String, path: List[Position])

object Route {
  implicit val codec: JsonCodec[Route] = DeriveJsonCodec.gen[Route]
}

val route = Route("Berlin to Paris", List(Position(13.405, 52.52), Position(2.35, 48.86)))
```

The route encodes with the position arrays inside it, in both modes:

```scala
println(route.toJson)
// {"name":"Berlin to Paris","path":[[13.405,52.52],[2.35,48.86]]}

println(route.toJsonPretty)
// {
//   "name" : "Berlin to Paris",
//   "path" : [
//     [
//       13.405,
//       52.52
//     ],
//     [
//       2.35,
//       48.86
//     ]
//   ]
// }
```

## 6. Test the Round Trip and the Error Messages

A hand-written codec needs the tests that derivation makes unnecessary. Start with round trips: every value must decode back to itself, through compact text, pretty text and the `Json` tree. Include edge cases such as the extreme coordinates, very small numbers and `-0.0`:

```scala
val samples = List(
  Position(13.405, 52.52),
  Position(-180, -90),
  Position(180, 90),
  Position(0.1, 1e-7),
  Position(-0.0, 0)
)
```

Each round trip must hold for every sample:

```scala
samples.forall(p => p.toJson.fromJson[Position] == Right(p))
// res23: Boolean = true

samples.forall(p => p.toJsonPretty.fromJson[Position] == Right(p))
// res24: Boolean = true

samples.forall(p => p.toJsonAST.flatMap(_.as[Position]) == Right(p))
// res25: Boolean = true

samples.toJson.fromJson[List[Position]] == Right(samples)
// res26: Boolean = true
```

The last check decodes a list, which catches a decoder that leaves the reader in the wrong place: if it consumed too much or too little after a position, the list decoder would find no `,` or `]` where it expects one. To check where the reader stops directly, call `JsonDecoder#unsafeDecode` on a [`WithRetractReader`](../reference/json-decoder.md#withretractreader).

Then pin the error messages, because the clients that send you JSON read them. An error inside a route carries the full path to the bad coordinate:

```scala
"""{"name":"x","path":[[13.405,52.52],[2.35,95]]}""".fromJson[Route]
// res27: Either[String, Route] = Left(
//   value = ".path[1][1](latitude must be between -90 and 90)"
// )

"""{"lon":13.405,"lat":52.52}""".fromJson[Position]
// res28: Either[String, Position] = Left(value = "(expected '[' got '{')")

"[13.405]".fromJson[Position]
// res29: Either[String, Position] = Left(
//   value = "(expected [longitude, latitude])"
// )
```

A test suite asserts these exact `Left` values, so a change to a message, whether yours or one coming from a new ZIO JSON release, shows up as a failing test rather than as a surprise for a client.

## See Also

- [Writing an Encoder by Hand](../reference/json-encoder.md#writing-an-encoder-by-hand) for the full `Write` interface, string escaping and `WriteWriter`.
- [Writing a Decoder by Hand](../reference/json-decoder.md#writing-a-decoder-by-hand) for the decoding contract, every `Lexer` helper, `StringMatrix` and `WithRetractReader`.
- [JsonCodec](../reference/json-codec.md) for `JsonCodec#transform` and the other combinators to try before writing a codec by hand.
- [Errors](../reference/errors.md) for how `JsonError` paths are rendered.
- [Built-in Instances](../reference/built-in-instances.md) for the instances a hand-written codec can delegate to.
