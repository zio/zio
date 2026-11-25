# Built-in Instances

> Every type that ZIO JSON encodes and decodes out of the box, with its JSON shape, which of JsonEncoder, JsonDecoder and JsonCodec it has, and the leniencies of each decoder.

ZIO JSON ships a `JsonEncoder`, a `JsonDecoder` and usually a `JsonCodec` for the standard Scala and Java types. The instances live in the companion objects of the three type classes, so they are found without any import beyond `import zio.json._`. Derived encoders and decoders for your own case classes and sealed traits use these instances for their fields.

Every table below has the same columns. **JSON shape** is what the encoder writes and the decoder expects. **Encoder**, **Decoder** and **Codec** say whether an implicit `JsonEncoder`, `JsonDecoder` or `JsonCodec` exists for the type. A type with an encoder and a decoder but no codec still works with `toJson` and `fromJson`; only `JsonCodec[T]` itself does not resolve, and you can build it with `JsonCodec(JsonEncoder[T], JsonDecoder[T])`. **Notes** list the leniencies of the decoder and any Scala version or platform limits. Unless a note says otherwise, an instance exists on Scala 2.12, 2.13 and 3, on the JVM, Scala.js and Scala Native.

A few of the shapes, written by the built-in encoders:

```scala
import zio.json._
import java.time.Instant

(Left(1): Either[Int, String]).toJson
// res1: String = "{\"Left\":1}"

(1, "a", true).toJson
// res2: String = "[1,\"a\",true]"

Map("a" -> Some(1), "b" -> None).toJson
// res3: String = "{\"a\":1}"

Instant.parse("2024-01-02T03:04:05Z").toJson
// res4: String = "\"2024-01-02T03:04:05Z\""
```

## Text, Booleans and Unit

These types map to JSON strings, booleans and an empty object:

| Scala type | JSON shape              | Encoder | Decoder | Codec | Notes                                                                           |
|------------|-------------------------|---------|---------|-------|---------------------------------------------------------------------------------|
| `String`   | string                  | yes     | yes     | yes   | The encoder escapes `"`, `\` and control characters                             |
| `Char`     | string of one character | yes     | yes     | yes   | Decoding a longer or empty string fails with `expected single character string` |
| `Symbol`   | string                  | yes     | yes     | yes   | Written and read as the symbol's name                                           |
| `Boolean`  | `true` / `false`        | yes     | yes     | yes   |                                                                                 |
| `Unit`     | `{}`                    | yes     | yes     | no    | The decoder accepts and skips any JSON value; see [Leniencies](#leniencies)     |

## Numbers

All number types encode as JSON numbers and accept the same number text when decoding, plus the leniencies listed in [Leniencies](#leniencies):

| Scala type                           | JSON shape | Encoder | Decoder | Codec | Notes                                                                                                                                                                                                    |
|--------------------------------------|------------|---------|---------|-------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `Byte`, `Short`, `Int`, `Long`       | number     | yes     | yes     | yes   | Out-of-range values fail. Quoted numbers and leading zeros are accepted; a leading `+` is not                                                                                                            |
| `Float`, `Double`                    | number     | yes     | yes     | yes   | `NaN` and the infinities are written as the strings `"NaN"`, `"Infinity"`, `"-Infinity"` ([Non-Finite Numbers](json-encoder.md#non-finite-numbers)). Values too large for the type decode as an infinity |
| `BigInt`, `java.math.BigInteger`     | number     | yes     | yes     | yes   | The decoder rejects values larger than 256 bits with `expected a 256-bit BigInt` (`expected a 256-bit BigInteger` for `BigInteger`). The encoder has no limit                                            |
| `BigDecimal`, `java.math.BigDecimal` | number     | yes     | yes     | yes   | The decoder limits the unscaled value (mantissa) to 256 bits; the exponent may be any `Int`. The encoder keeps the scale, so `1.50` stays `1.50`                                                         |

## Option and Either

`Option` and `Either` wrap another type, so they need an instance for each type they hold:

| Scala type     | JSON shape                     | Encoder | Decoder | Codec | Notes                                                                                                                                                    |
|----------------|--------------------------------|---------|---------|-------|----------------------------------------------------------------------------------------------------------------------------------------------------------|
| `Option[A]`    | the value, or `null`           | yes     | yes     | yes   | `None` is `null`. In a derived case class, a `None` field is left out by default and a missing field decodes as `None`. `Some(None)` encodes like `None` |
| `Either[A, B]` | `{"Left": a}` / `{"Right": b}` | yes     | yes     | yes   | See [Either Wire Format](#either-wire-format)                                                                                                            |

## Either Wire Format

The `Either` encoder wraps the value in an object with a single key, `"Left"` or `"Right"`. The decoder accepts more key names, for compatibility with other libraries:

| Side    | Keys accepted by the decoder |
|---------|------------------------------|
| `Left`  | `"Left"`, `"left"`, `"a"`    |
| `Right` | `"Right"`, `"right"`, `"b"`  |

Other keys in the object are skipped. An object with neither side fails with `missing fields`, one with both sides fails with `ambiguous either, zip present`, and one that names the same side twice fails with `duplicate`:

```scala
"""{"right":"x"}""".fromJson[Either[Int, String]]
// res5: Either[String, Either[Int, String]] = Right(
//   value = Right(value = "x")
// )

"""{"Left":1,"Right":"x"}""".fromJson[Either[Int, String]]
// res6: Either[String, Either[Int, String]] = Left(
//   value = "(ambiguous either, zip present)"
// )
```

For an untagged `Either`, use `JsonEncoder#orElseEither` and `JsonDecoder#orElseEither`; see [JsonEncoder](json-encoder.md#either-orelseeither-and-eitherwith).

## Tuples

Tuples of 1 to 22 elements are JSON arrays with one element per tuple member, each encoded with its own instance:

| Scala type            | JSON shape                | Encoder | Decoder | Codec | Notes                                                                                                                                                 |
|-----------------------|---------------------------|---------|---------|-------|-------------------------------------------------------------------------------------------------------------------------------------------------------|
| `Tuple1` to `Tuple22` | array of exactly N values | yes     | yes     | yes   | `(1, "a")` is `[1,"a"]`. Pretty output separates the elements with `", "` instead of putting each on its own line. An array of the wrong length fails |

## Sequences and Sets

Every sequence and set encodes as a JSON array of its elements. They differ in whether a `JsonCodec` exists and in what they need besides the element's instances:

| Scala type                               | JSON shape      | Encoder | Decoder | Codec | Notes                                                                                                                      |
|------------------------------------------|-----------------|---------|---------|-------|----------------------------------------------------------------------------------------------------------------------------|
| `List`, `Vector`, `Seq`                  | array           | yes     | yes     | yes   |                                                                                                                            |
| `Chunk`                                  | array           | yes     | yes     | yes   | `zio.Chunk`                                                                                                                |
| `NonEmptyChunk`                          | non-empty array | yes     | yes     | yes   | `zio.NonEmptyChunk`. An empty array fails with `Chunk was empty`                                                           |
| `Array`                                  | array           | yes     | yes     | yes   | Needs a `ClassTag` for the element type, on the encoder too                                                                |
| `IndexedSeq`, `immutable.LinearSeq`      | array           | yes     | yes     | no    | No `JsonCodec`; build one with `JsonCodec(JsonEncoder[T], JsonDecoder[T])`                                                 |
| `Set`, `immutable.HashSet`               | array           | yes     | yes     | yes   | Duplicate elements in the input collapse silently                                                                          |
| `immutable.SortedSet`                    | array           | yes     | yes     | yes   | Needs an `Ordering` for the element type in both directions                                                                |
| `immutable.ListSet`, `immutable.TreeSet` | array           | yes     | yes     | no    | No `JsonCodec`. The `TreeSet` decoder needs an `Ordering`                                                                  |
| `immutable.ArraySeq`                     | array           | yes     | yes     | yes   | Scala 2.13 and 3 only. Needs a `ClassTag`                                                                                  |
| `IArray`                                 | array           | yes     | yes     | yes   | Scala 3 only. Needs a `ClassTag`                                                                                           |
| `Iterable[A]`                            | array           | yes     | yes     | yes   | The decoder builds a `List`                                                                                                |
| Any other `T[A] <: Iterable[A]`          | array           | yes     | no      | no    | A generic, low-priority encoder covers any `Iterable` subtype, for example `immutable.Queue`. There is no matching decoder |

When a derived decoder meets a missing field whose type is one of these collections, the result depends on the [explicitEmptyCollections](json-codec-configuration.md#explicitemptycollections) setting: by default the field is required.

## Maps

A JSON object's keys are strings, so every map type needs a `JsonFieldEncoder[K]` to write keys and a `JsonFieldDecoder[K]` to read them, in addition to the value's instances. The built-in key types are `String`, `Int`, `Long` and `UUID`; on Scala 3, any subtype of `String` that has a `JsonDecoder`, such as a string-literal union, can also be a decoded key. Any other key type needs instances of your own; [JsonFieldEncoder and JsonFieldDecoder](json-field-codecs.md) shows how.

| Scala type                                                        | JSON shape | Encoder  | Decoder  | Codec | Notes                                                                                                                                                                                                                       |
|-------------------------------------------------------------------|------------|----------|----------|-------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `Map[K, V]`, `immutable.HashMap[K, V]`, `immutable.ListMap[K, V]` | object     | yes      | yes      | yes   | Keys need a `JsonFieldEncoder` / `JsonFieldDecoder`. Entries whose value is `None` are left out when encoding. On duplicate keys, the last value wins                                                                       |
| `collection.SortedMap[K, V]`                                      | object     | yes      | yes      | yes   | As `Map`. The decoder and the codec also need an `Ordering[K]`                                                                                                                                                              |
| `mutable.Map[K, V]`                                               | object     | yes      | yes      | no    | As `Map`, but no `JsonCodec`                                                                                                                                                                                                |
| `Chunk[(K, V)]`, other sequences of pairs                         | object     | explicit | explicit | no    | Only through `JsonEncoder.keyValueChunk`, `JsonEncoder.keyValueIterable` and `JsonDecoder.keyValueChunk`, which are not implicit and keep duplicate keys. Implicitly, a sequence of pairs is an array of two-element arrays |

## java.time

All 16 `java.time` types are written as ISO-8601 strings. The examples in the table are the exact text the encoder writes:

| Scala type       | JSON shape                                  | Encoder | Decoder | Codec | Notes                                                                                            |
|------------------|---------------------------------------------|---------|---------|-------|--------------------------------------------------------------------------------------------------|
| `Instant`        | `"2024-01-02T03:04:05.123Z"`                | yes     | yes     | yes   | The decoder needs a `Z` or an offset, which it converts to UTC                                   |
| `LocalDate`      | `"2024-01-02"`                              | yes     | yes     | yes   |                                                                                                  |
| `LocalTime`      | `"03:04:00"`                                | yes     | yes     | yes   | Seconds are always written, unlike `toString`; the decoder accepts `"03:04"`                     |
| `LocalDateTime`  | `"2024-01-02T03:04:05"`                     | yes     | yes     | yes   | Seconds are always written                                                                       |
| `OffsetTime`     | `"03:04:05+02:00"`                          | yes     | yes     | yes   |                                                                                                  |
| `OffsetDateTime` | `"2024-01-02T03:04:05+02:00"`               | yes     | yes     | yes   |                                                                                                  |
| `ZonedDateTime`  | `"2024-01-02T03:04:05+01:00[Europe/Paris]"` | yes     | yes     | yes   | Scala.js and Scala Native need the time-zone database (see below)                                |
| `ZoneId`         | `"Europe/Paris"`                            | yes     | yes     | yes   | Region ids and offsets such as `"+01:00"`. Scala.js and Scala Native need the time-zone database |
| `ZoneOffset`     | `"+02:00"`, `"Z"`                           | yes     | yes     | yes   |                                                                                                  |
| `Duration`       | `"PT1H30M"`                                 | yes     | yes     | yes   |                                                                                                  |
| `Period`         | `"P1Y2M3D"`                                 | yes     | yes     | yes   | The decoder also accepts weeks, such as `"P1W"`                                                  |
| `Year`           | `"2024"`                                    | yes     | yes     | yes   | A string, not a number. Years after 9999 carry a `+` sign                                        |
| `YearMonth`      | `"2024-01"`                                 | yes     | yes     | yes   |                                                                                                  |
| `MonthDay`       | `"--12-25"`                                 | yes     | yes     | yes   |                                                                                                  |
| `DayOfWeek`      | `"MONDAY"`                                  | yes     | yes     | yes   | The decoder ignores case: `"monday"` works. Abbreviations do not                                 |
| `Month`          | `"JANUARY"`                                 | yes     | yes     | yes   | The decoder ignores case                                                                         |

On Scala.js and Scala Native, `ZoneId` and `ZonedDateTime` with region ids need the time-zone database, `scala-java-time-tzdb`, as an extra dependency. Use the same version as the `scala-java-time` in your dependency tree, as described in the [installation notes](../index.md). For example, when that version is 2.10.0:

```scala
libraryDependencies += "io.github.cquiroz" %%% "scala-java-time-tzdb" % "2.10.0"
```

## UUID and Currency

Two more JDK types from `java.util` are written as strings:

| Scala type           | JSON shape                               | Encoder | Decoder | Codec | Notes                                                                                                                                                |
|----------------------|------------------------------------------|---------|---------|-------|------------------------------------------------------------------------------------------------------------------------------------------------------|
| `java.util.UUID`     | `"123e4567-e89b-12d3-a456-426614174000"` | yes     | yes     | yes   | The decoder also accepts non-canonical forms with shorter groups, as `UUID.fromString` does: `"1-2-3-4-5"` is `00000001-0002-0003-0004-000000000005` |
| `java.util.Currency` | `"EUR"`                                  | yes     | yes     | yes   | The ISO 4217 code, read with `Currency.getInstance`, so it is case-sensitive                                                                         |

## JSON AST

The [`Json`](json-ast.md) type and each of its cases have instances, so a field of type `Json` holds any JSON value as it is:

| Scala type                                                               | JSON shape                                     | Encoder | Decoder | Codec | Notes                                                                                                                      |
|--------------------------------------------------------------------------|------------------------------------------------|---------|---------|-------|----------------------------------------------------------------------------------------------------------------------------|
| `Json`                                                                   | any JSON value                                 | yes     | yes     | yes   | Instances live in the `Json` companion                                                                                     |
| `Json.Obj`, `Json.Arr`, `Json.Str`, `Json.Num`, `Json.Bool`, `Json.Null` | object, array, string, number, boolean, `null` | yes     | yes     | yes   | Each case's companion has its own instances, which reject the other shapes. `Json.Num` decodes with the `BigDecimal` rules |

## Scala 3 Only

Besides `IArray` (see [Sequences and Sets](#sequences-and-sets)), Scala 3 adds instances for unions of string literal types:

| Scala type                                 | JSON shape | Encoder | Decoder | Codec | Notes                                                                                                |
|--------------------------------------------|------------|---------|---------|-------|------------------------------------------------------------------------------------------------------|
| String-literal union, such as `"a" \| "b"` | string     | yes     | yes     | no    | Scala 3 only. Decoding any other string fails with `expected one of: a, b`. Works as a `Map` key too |

[JsonEncoder](json-encoder.md#scala-2-vs-scala-3) shows a union used as a field and as a `Map` key.

## Leniencies

Several decoders accept more than the JSON that the matching encoder writes. This keeps them compatible with JSON produced by other tools, but it also means that some unusual input decodes without an error:

- Every number type accepts a number inside a string: `"42"` decodes as `42`.
- Integer types accept leading zeros: `007` decodes as `7`. A leading `+` is rejected.
- `Float` and `Double` accept the quoted strings `"NaN"`, `"Infinity"`, `"+Infinity"` and `"-Infinity"`. Bare `NaN` and `Infinity`, which are not JSON, are rejected.
- `Unit` accepts any JSON value and skips it without checking that it is well-formed.
- `DayOfWeek` and `Month` ignore case, and `UUID` accepts non-canonical forms (see [java.time](#javatime) and [UUID and Currency](#uuid-and-currency)).
- Sets drop duplicate elements, and maps keep the last value for a duplicate key, without an error.

The first four, decoded at the top level:

```scala
"\"42\"".fromJson[Int]
// res7: Either[String, Int] = Right(value = 42)

"007".fromJson[Int]
// res8: Either[String, Int] = Right(value = 7)

"\"NaN\"".fromJson[Double]
// res9: Either[String, Double] = Right(value = NaN)

"""[1, {"x": 2}]""".fromJson[Unit]
// res10: Either[String, Unit] = Right(value = ())
```

The decoders also have leniencies that are not tied to one type, such as ignoring input after the first complete value at the top level and skipping unknown fields without validating them. [Decoder Strictness](../explanation/decoder-strictness.md) explains them and how to get stricter checks.

## See Also

- [JsonEncoder](json-encoder.md), [JsonDecoder](json-decoder.md) and [JsonCodec](json-codec.md), the three type classes
- [JsonFieldEncoder and JsonFieldDecoder](json-field-codecs.md) for map keys
- [Third-Party Types](../how-to/third-party-types.md) for types that have no built-in instance
- [JSON AST](json-ast.md) for the `Json` type
- [Decoder Strictness](../explanation/decoder-strictness.md) for how strict decoding is overall
