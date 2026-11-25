# How Strict Is the Decoder

> What ZIO JSON decoders accept beyond strict JSON, why they trade strictness for speed, and how to add stricter checks where you need them.

ZIO JSON decoders check everything they need to build your value, and little else. Some input that is not valid JSON under [RFC 8259](https://www.rfc-editor.org/rfc/rfc8259) therefore decodes without an error. This page explains which input that is, why the decoders work this way, and how to get stricter checks when you need them.

All examples on this page use these imports and types:

```scala
import zio.json._
import zio.json.ast.Json

final case class Reading(value: Int)

object Reading {
  implicit val codec: JsonCodec[Reading] = DeriveJsonCodec.gen[Reading]
}

final case class Name(name: String)

object Name {
  implicit val codec: JsonCodec[Name] = DeriveJsonCodec.gen[Name]
}
```

## Why the Decoder Is Lenient

A `JsonDecoder` reads characters and builds your value directly, without building a syntax tree first (see [How ZIO JSON Works](how-it-works.md#decoding-reads-straight-into-your-types)). Each decoder reads only the part of the input that belongs to its value. An `Int` decoder reads digits, a case class decoder reads the fields it knows, and nothing is checked twice.

That design makes decoding fast and keeps memory use low, but it means no step looks at the whole document. Three things follow from it:

- The top-level decoder stops after the first complete value and never looks at the rest of the input.
- Values of fields the decoder does not know are skipped with a quick scan that does not validate them.
- Some built-in decoders accept common non-standard forms, such as numbers in quotes, so that JSON written by other tools still decodes.

None of these leniencies lets a wrongly typed value into your program. They decide only which *extra* text is tolerated around and between the values you asked for. That extra text can still change the meaning of a top-level number, as the next section shows: `1e3` decodes as the `Int` `1`. When you need the input itself to be valid JSON, for example because you store it or pass it on, add one of the checks in [Getting Stricter Checks](#getting-stricter-checks).

## Input After the First Value Is Ignored

`JsonDecoder#decodeJson`, and the `fromJson` extension method that calls it, read one complete JSON value from the start of the input and then stop. They do not check that the rest of the input is empty.

For a top-level number, the value ends at the first character that cannot continue it. The `Int` decoder reads digits only, so it stops at the `e` of `1e3` or the `.` of `1.5` and ignores the rest. The same holds for other scalar values and for whole objects:

```scala
"1e3".fromJson[Int]
// res1: Either[String, Int] = Right(value = 1)

"1.5".fromJson[Int]
// res2: Either[String, Int] = Right(value = 1)

"12x".fromJson[Int]
// res3: Either[String, Int] = Right(value = 12)

"truex".fromJson[Boolean]
// res4: Either[String, Boolean] = Right(value = true)

"""{"value":1} {"value":2}""".fromJson[Reading]
// res5: Either[String, Reading] = Right(value = Reading(value = 1))
```

Inside an object or an array the same text fails, because after a value the decoder expects a `,` or a closing bracket:

```scala
"""{"value":1e3}""".fromJson[Reading]
// res6: Either[String, Reading] = Left(
//   value = "(expected ',' or '}' got 'e')"
// )

"[1.5]".fromJson[List[Int]]
// res7: Either[String, List[Int]] = Left(
//   value = "(expected ',' or ']' got '.')"
// )
```

So the trailing-input leniency matters mostly when a whole message is a single number, string or boolean, or when a second document follows the first one by mistake. The [JsonDecoder reference](../reference/json-decoder.md#trailing-input-is-ignored) states the rule as part of the API.

## Unknown Fields Are Skipped Without Validation

A derived case class decoder ignores fields it does not know, unless you forbid them (see [Reject Unknown Fields](#reject-unknown-fields)). To ignore a field, it skips over the field's value. Skipping is a fast scan that only looks for where the value ends. It does not check that the value is well-formed JSON, so a malformed value in an unknown field is accepted:

```scala
"""{"name":"Ada","x":tzzz}""".fromJson[Name]
// res8: Either[String, Name] = Right(value = Name(name = "Ada"))

"""{"name":"Ada","x":1-2e+}""".fromJson[Name]
// res9: Either[String, Name] = Right(value = Name(name = "Ada"))
```

The scan builds nothing and does not recurse, so an unknown field costs little time and memory even when it is large or deeply nested. That is what keeps decoding fast on payloads with many fields you do not need, and it is also why unknown fields are a cheap path for an attacker to send padding. [Security](security.md#redundant-data) discusses that side.

## Leniencies of the Built-in Decoders

Some decoders for standard types accept more than their encoders write. This keeps them compatible with JSON written by other libraries and languages. The full list is in [Built-in Instances](../reference/built-in-instances.md#leniencies); the number-related ones are the most visible.

Every number decoder accepts a number inside a string, and the integer decoders accept leading zeros:

```scala
"\"42\"".fromJson[Int]
// res10: Either[String, Int] = Right(value = 42)

"""{"value":"42"}""".fromJson[Reading]
// res11: Either[String, Reading] = Right(value = Reading(value = 42))

"007".fromJson[Int]
// res12: Either[String, Int] = Right(value = 7)
```

`Float` and `Double` accept the quoted strings `"NaN"`, `"Infinity"`, `"+Infinity"` and `"-Infinity"`, which are also what their encoders write for those values:

```scala
"\"NaN\"".fromJson[Double]
// res13: Either[String, Double] = Right(value = NaN)

"\"+Infinity\"".fromJson[Double]
// res14: Either[String, Double] = Right(value = Infinity)

"\"-Infinity\"".fromJson[Float]
// res15: Either[String, Float] = Right(value = -InfinityF)
```

`Map` keys go through a separate key decoder (see [JsonFieldEncoder and JsonFieldDecoder](../reference/json-field-codecs.md)). The `Int` and `Long` key decoders parse the key with `String#toInt` and `String#toLong`, so a key such as `"+5"` is accepted, although the value decoder rejects `+5`:

```scala
"""{"+5":1}""".fromJson[Map[Int, Int]]
// res16: Either[String, Map[Int, Int]] = Right(value = Map(5 -> 1))

"+5".fromJson[Int]
// res17: Either[String, Int] = Left(value = "(expected an Int)")
```

## What the Decoder Checks

Leniency is about tolerated extra text, not about types. Everything that decides what your value contains is checked, and the error names the path to the problem (see [Errors](../reference/errors.md)).

A value of the wrong type fails, and so does a required field that is missing:

```scala
"""{"value":"high"}""".fromJson[Reading]
// res18: Either[String, Reading] = Left(value = ".value(expected an Int)")

"{}".fromJson[Reading]
// res19: Either[String, Reading] = Left(value = ".value(missing)")
```

A derived decoder rejects a field that appears twice, instead of silently keeping one of the values:

```scala
"""{"value":1,"value":2}""".fromJson[Reading]
// res20: Either[String, Reading] = Left(value = "(duplicate)")
```

Strings must be well-formed, so a bad escape or a raw control character fails. Bare `NaN` and a leading `+` on a number are rejected too:

```scala
"\"a\\xb\"".fromJson[String]
// res21: Either[String, String] = Left(value = "(invalid '\\x' in string)")

"NaN".fromJson[Double]
// res22: Either[String, Double] = Left(value = "(expected a Double)")

"+1".fromJson[Int]
// res23: Either[String, Int] = Left(value = "(expected an Int)")
```

Unknown fields are rejected as soon as you ask for it with `@jsonNoExtraFields` or `allowExtraFields = false`, which the next section shows.

## Getting Stricter Checks

Which check you add depends on what you need to be strict about. Parsing to `Json` first validates the syntax of the whole document, forbidding extra fields rejects unknown fields, `JsonDecoder#mapOrFail` validates values, and a short helper rejects trailing input.

### Parse to `Json` First

Decoding to the [`Json` AST](../reference/json-ast.md) parses every value fully, including the values of fields your type does not know. Converting the tree with `Json#as` then gives you your type:

```scala
"""{"name":"Ada","x":tzzz}""".fromJson[Json]
// res24: Either[String, Json] = Left(value = ".x(expected a Boolean)")

"""{"name":"Ada","x":tzzz}""".fromJson[Json].flatMap(_.as[Name])
// res25: Either[String, Name] = Left(value = ".x(expected a Boolean)")
```

Numbers are read in full too, so `1e3` becomes the number one thousand, not `1` followed by ignored text. Converting it to an `Int` then succeeds, while a number with a fraction is rejected:

```scala
"1e3".fromJson[Json]
// res26: Either[String, Json] = Right(value = Num(value = 1E+3))

"1e3".fromJson[Json].flatMap(_.as[Int])
// res27: Either[String, Int] = Right(value = 1000)

"1.5".fromJson[Json].flatMap(_.as[Int])
// res28: Either[String, Int] = Left(value = "(expected a Int)")
```

Parsing to `Json` does not reject trailing input. The `Json` decoder also reads one value and stops, so text after it is still ignored:

```scala
"12x".fromJson[Json]
// res29: Either[String, Json] = Right(value = Num(value = 12))

"""{"name":"Ada"} trailing""".fromJson[Json]
// res30: Either[String, Json] = Right(
//   value = Obj(fields = IndexedSeq(("name", Str(value = "Ada"))))
// )
```

The cost is a full tree in memory before your value is built, which makes decoding slower and uses more memory than decoding directly. The leniencies of the built-in decoders stay too: `Json#as[Int]` still accepts a quoted number. Use this approach where a complete syntax check matters more than speed, for example at a system boundary that stores the raw document.

### Reject Unknown Fields

`@jsonNoExtraFields` on a case class, or `allowExtraFields = false` in a [`JsonCodecConfiguration`](../reference/json-codec-configuration.md#allowextrafields), makes the derived decoder fail at the first field it does not know. The unknown value is then never skipped, so its contents no longer matter:

```scala
@jsonNoExtraFields
final case class StrictName(name: String)

object StrictName {
  implicit val codec: JsonCodec[StrictName] = DeriveJsonCodec.gen[StrictName]
}
```

The same payload as before now fails:

```scala
"""{"name":"Ada","x":tzzz}""".fromJson[StrictName]
// res31: Either[String, StrictName] = Left(value = "(invalid extra field)")
```

This makes your API reject typos in field names and fields from a newer client version, so turn it on only where that is what you want. It also does not combine with a discriminator field, because the discriminator counts as an extra field of the case; [JSON Shapes and Round Trips](wire-format.md#a-discriminator-with-strict-field-checks) shows the failure. The [optional and default fields guide](../how-to/optional-and-default-fields.md#4-reject-unexpected-fields-with-jsonnoextrafields) covers the annotation in more detail.

### Validate Values With `JsonDecoder#mapOrFail`

The decoders check types, not the rules of your domain. `JsonDecoder#mapOrFail` adds such a rule, and a value that breaks it fails with your message and the usual path:

```scala
final case class Percent(value: Int)

object Percent {
  implicit val decoder: JsonDecoder[Percent] =
    JsonDecoder[Int].mapOrFail { n =>
      if (n >= 0 && n <= 100) Right(Percent(n))
      else Left(s"$n is not between 0 and 100")
    }
}
```

A number outside the range now fails:

```scala
"42".fromJson[Percent]
// res32: Either[String, Percent] = Right(value = Percent(value = 42))

"150".fromJson[Percent]
// res33: Either[String, Percent] = Left(
//   value = "(150 is not between 0 and 100)"
// )
```

[Validate on Decode](../how-to/validate-on-decode.md) shows more patterns, including rules that span several fields.

### Check the Trailing Input Yourself

No public option makes `fromJson` reject trailing input. You can check it yourself by decoding from a reader and looking at what is left after the value. The helper below uses `WithRetractReader` and `JsonDecoder#unsafeDecode`:

```scala
import zio.json.internal.WithRetractReader
import scala.util.control.NonFatal

def fromJsonStrict[A](input: String)(implicit decoder: JsonDecoder[A]): Either[String, A] = {
  val in = new WithRetractReader(new java.io.StringReader(input))
  try {
    val value = decoder.unsafeDecode(Nil, in)
    val next  = Iterator.continually(in.read()).dropWhile(c => c == ' ' || c == '\t' || c == '\n' || c == '\r').next()
    if (next == -1) Right(value)
    else Left(s"(unexpected '${next.toChar}' after the value)")
  } catch {
    case NonFatal(_) | (_: StackOverflowError) => decoder.decodeJson(input)
  }
}
```

The helper works like this:

- `decoder.unsafeDecode(Nil, in)` decodes one value and leaves the reader just after it, exactly as `fromJson` does.
- `Iterator.continually(in.read())` reads the characters that follow, skipping the whitespace that JSON allows after a value. `in.read()` returns `-1` at the end of the input.
- Any other character means trailing input, which becomes a `Left`.
- If decoding itself fails, the `catch` runs the ordinary `JsonDecoder#decodeJson`, which reports the error in the usual format.

The earlier examples now fail, while input with only trailing whitespace still decodes:

```scala
fromJsonStrict[Int]("1e3")
// res34: Either[String, Int] = Left(
//   value = "(unexpected 'e' after the value)"
// )

fromJsonStrict[Boolean]("truex")
// res35: Either[String, Boolean] = Left(
//   value = "(unexpected 'x' after the value)"
// )

fromJsonStrict[Reading]("""{"value":1} {"value":2}""")
// res36: Either[String, Reading] = Left(
//   value = "(unexpected '{' after the value)"
// )

fromJsonStrict[Reading]("""{"value":1}  """)
// res37: Either[String, Reading] = Right(value = Reading(value = 1))

fromJsonStrict[Reading]("""{"value":"high"}""")
// res38: Either[String, Reading] = Left(value = ".value(expected an Int)")
```

:::caution
`WithRetractReader` and `JsonDecoder#unsafeDecode` belong to the low-level API. Everything in the `zio.json.internal` package can change in any release, as [Low-Level Codecs](../how-to/low-level-codecs.md) explains. Keep the helper in one place and cover it with a test.
:::

For a stricter check of the whole document, combine the approaches: call `fromJsonStrict[Json]` to validate the syntax and reject trailing input, then convert with `Json#as`.

## Upstream Status

The [JSONTestSuite](https://github.com/nst/JSONTestSuite) collects inputs that a strict parser must accept or reject. ZIO JSON accepts several of the inputs it marks as invalid, mostly for the reasons on this page: trailing input after a value, leading zeros, and number forms such as `2.e3`. GitHub issue [#55](https://github.com/zio/zio-json/issues/55) tracks these results upstream. Until it changes, treat the behaviour on this page as the current contract, and add the checks above where your application needs them.

## See Also

- [JsonDecoder](../reference/json-decoder.md) for the decoding API and its contract
- [Built-in Instances](../reference/built-in-instances.md#leniencies) for the leniencies of each standard type
- [Security](security.md) for how the decoder behaves under hostile input
- [JSON Shapes and Round Trips](wire-format.md) for what the encoder writes and which values survive a round trip
