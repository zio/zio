# JsonCodec

> Reference for JsonCodec, which pairs a JsonEncoder and a JsonDecoder for one type, with transform, transformOrFail and zip combinators.

`JsonCodec[A]` is a case class that holds a `JsonEncoder[A]` and a `JsonDecoder[A]` for the same type.
Use it when one value should describe both directions: you define the type's JSON shape once and get both halves.
Its instances should round-trip: encoding a value and decoding the result gives the value back.
Unlike `JsonEncoder` and `JsonDecoder`, `JsonCodec` is a `final case class`, not a trait.

| Member                                             | What it does                                             | Details                                         |
|----------------------------------------------------|----------------------------------------------------------|-------------------------------------------------|
| `JsonCodec(encoder, decoder)`                      | Builds a codec from two instances                        | [Construction](#construction)                   |
| `encoder`, `decoder`                               | The two halves                                           | [Construction](#construction)                   |
| `encodeJson(a, indent)`                            | Encodes with the encoder                                 | [Encoding and Decoding](#encoding-and-decoding) |
| `decodeJson(str)`                                  | Decodes with the decoder                                 | [Encoding and Decoding](#encoding-and-decoding) |
| `transform`, `transformOrFail`                     | Maps both directions, the decode side optionally failing | [Transforming a Codec](#transforming-a-codec)   |
| `orElse` (`<>`), `orElseEither` (`<+>`)            | Falls back to another codec when decoding                | [orElse](#orelse-and-orelseeither)              |
| `zip` (`<*>`), `zipLeft` (`<*`), `zipRight` (`*>`) | Pairs two codecs as a JSON array                         | [zip](#zip-zipleft-and-zipright)                |

## Construction

`JsonCodec` is a case class with one field for each half:

```scala
final case class JsonCodec[A](encoder: JsonEncoder[A], decoder: JsonDecoder[A])
```

The companion object offers three ways to get a codec:

- `JsonCodec(encoder, decoder)` builds one from two existing instances.
- `JsonCodec[A]` summons the implicit instance.
- `DeriveJsonCodec.gen[A]` derives both halves together (Scala 2 and Scala 3). Scala 3 also has `JsonCodec.derived`, used by `derives JsonCodec`.

The companion also declares `implicit def fromEncoderDecoder[A](encoder: JsonEncoder[A], decoder: JsonDecoder[A]): JsonCodec[A]`, which is the same as `JsonCodec(encoder, decoder)`.
Its two parameters are ordinary, not implicit, so implicit search never uses it to assemble a codec from an encoder and a decoder in scope. Call it, or `JsonCodec(encoder, decoder)`, explicitly.

The companion has codec instances for the primitive types, `Option`, `Either`, tuples of 1 to 22 elements, the common collections (`List`, `Vector`, `Seq`, `Set`, `Chunk`, `NonEmptyChunk`, `Array`, `Map` and others), `java.time` types, `UUID` and `Currency`.
A few types have an encoder and a decoder but no codec: `Unit`, `IndexedSeq`, `LinearSeq`, `ListSet`, `TreeSet` and `mutable.Map`. Build those with `JsonCodec(JsonEncoder[T], JsonDecoder[T])`.
[Built-in Instances](built-in-instances.md) lists every type with its JSON shape and says which of the three instances it has.

The examples on this page use a `Person` type with a derived codec:

```scala
import zio.json._

final case class Person(name: String, age: Int)

object Person {
  implicit val codec: JsonCodec[Person] = DeriveJsonCodec.gen[Person]
}
```

A `JsonCodec[A]` in implicit scope also supplies a `JsonEncoder[A]` and a `JsonDecoder[A]`.
Both companions define `fromCodec` for this, so `toJson` and `fromJson` work with only the codec defined:

```scala
Person("Ada", 36).toJson
// res1: String = "{\"name\":\"Ada\",\"age\":36}"

"""{"name":"Ada","age":36}""".fromJson[Person]
// res2: Either[String, Person] = Right(value = Person(name = "Ada", age = 36))
```

## Encoding and Decoding

The codec's own `encodeJson` and `decodeJson` delegate to its encoder and decoder:

```scala
final def encodeJson(a: A, indent: Option[Int]): CharSequence
final def decodeJson(str: CharSequence): Either[String, A]
```

See [JsonEncoder](json-encoder.md#encodejson) and [JsonDecoder](json-decoder.md#decodejson) for the behavior of `indent` and for the `Left(String)` errors.

:::note[No default for indent]
`JsonEncoder#encodeJson` defaults `indent` to `None`. `JsonCodec#encodeJson` has no default, so you must pass it.
The codec has only the `CharSequence` overload of `decodeJson`. To decode bytes, use `codec.decoder.decodeJson(bytes)` or `fromJson`.
:::

Calling both methods on the `Person` codec, with `None` for compact output:

```scala
Person.codec.encodeJson(Person("Ada", 36), None)
// res3: CharSequence = "{\"name\":\"Ada\",\"age\":36}"

Person.codec.decodeJson("""{"name":"Ada","age":36}""")
// res4: Either[String, Person] = Right(value = Person(name = "Ada", age = 36))
```

## Transforming a Codec

`transform` and `transformOrFail` turn a codec for `A` into a codec for `B`:

```scala
final def transform[B](f: A => B, g: B => A): JsonCodec[B]
final def transformOrFail[B](f: A => Either[String, B], g: B => A): JsonCodec[B]
```

`transform` maps the decoded value with `f` and contramaps the encoder with `g`.
`transformOrFail` is the same, but `f` may fail with an error message, as in `JsonDecoder#mapOrFail`.
`g` cannot fail: the type `B` must always be encodable.
Internally they are `JsonCodec(encoder.contramap(g), decoder.map(f))` and `JsonCodec(encoder.contramap(g), decoder.mapOrFail(f))`.

To decode only strings that contain `@` into an `Email`, and to write an `Email` back as its string:

```scala
final case class Email(value: String)

val emailCodec: JsonCodec[Email] =
  JsonCodec[String].transformOrFail[Email](
    s => if (s.contains("@")) Right(Email(s)) else Left("not an email address"),
    _.value
  )
```

The encoder writes the plain string, and the decoder rejects a string without `@`:

```scala
emailCodec.encodeJson(Email("ada@example.com"), None)
// res5: CharSequence = "\"ada@example.com\""

emailCodec.decodeJson("\"ada\"")
// res6: Either[String, Email] = Left(value = "(not an email address)")
```

## Combining Codecs

Two codecs combine either as alternatives, where decoding falls back from one to the other, or as the elements of a JSON array.

### `orElse` and `orElseEither`

Both methods take the fallback codec by name:

```scala
final def orElse(that: => JsonCodec[A]): JsonCodec[A]
final def orElseEither[B](that: => JsonCodec[B]): JsonCodec[Either[A, B]]
```

`<>` is an alias for `orElse`, and `<+>` is an alias for `orElseEither`.
The decoder tries this codec first and falls back to `that` if it fails.
For `orElseEither`, the encoder writes a `Left` with this codec's encoder and a `Right` with the other, with no tag in the JSON.
`orElse` is `orElseEither` followed by `transform`. It decodes with either codec, but its transform encodes every value as a `Right`, so it always writes with `that`, not with this codec.
The fallback rewinds the input, so it has the same memory caveat as [`JsonDecoder#orElse`](json-decoder.md#orelse-and-orelseeither).

To accept either a number or a string:

```scala
val intOrString: JsonCodec[Either[Int, String]] = JsonCodec[Int] <+> JsonCodec[String]
```

The `Left` is written with the `Int` codec and carries no tag, and a string falls through to the second decoder:

```scala
intOrString.encodeJson(Left(1), None)
// res7: CharSequence = "1"

intOrString.decodeJson("\"one\"")
// res8: Either[String, Either[Int, String]] = Right(
//   value = Right(value = "one")
// )
```

To see which codec `orElse` writes with, we pair the plain `Int` codec with a fallback whose encoder doubles the number:

```scala
val doubling: JsonCodec[Int] = JsonCodec(JsonEncoder[Int].contramap[Int](_ * 2), JsonDecoder[Int])

val primaryFirst: JsonCodec[Int] = JsonCodec[Int] <> doubling
```

Decoding succeeds with the first codec, but encoding uses the fallback's encoder:

```scala
primaryFirst.decodeJson("5")
// res9: Either[String, Int] = Right(value = 5)

primaryFirst.encodeJson(5, None)
// res10: CharSequence = "10"
```

### `zip`, `zipLeft` and `zipRight`

The three methods pair this codec with another one:

```scala
final def zip[B](that: => JsonCodec[B]): JsonCodec[(A, B)]
final def zipLeft(that: => JsonCodec[Unit]): JsonCodec[A]
final def zipRight[B](that: => JsonCodec[B])(implicit ev: Unit <:< A): JsonCodec[B]
```

`<*>` is `zip`, `<*` is `zipLeft`, and `*>` is `zipRight`.
`zip` pairs two codecs as a two-element JSON array.
`zipLeft` and `zipRight` drop the `Unit` side, which still takes up a slot in the array.
`zipRight` is only available when `Unit` is a subtype of `A` (usually `A` is `Unit`), because it encodes `()` for the dropped side.
There is no implicit `JsonCodec[Unit]`, so you build one from `JsonEncoder.unit` and `JsonDecoder.unit`.

A codec for a pair, and one that keeps only the count of a `[unit, count]` array:

```scala
val pair: JsonCodec[(String, Int)] = JsonCodec[String] <*> JsonCodec[Int]

val unitCodec: JsonCodec[Unit] = JsonCodec(JsonEncoder.unit, JsonDecoder.unit)

val countOnly: JsonCodec[Int] = unitCodec *> JsonCodec[Int]
```

`pair` writes a two-element array, and `countOnly` writes `{}` in the `Unit` slot and skips that slot when decoding:

```scala
pair.encodeJson(("apples", 3), None)
// res11: CharSequence = "[\"apples\",3]"

countOnly.encodeJson(3, None)
// res12: CharSequence = "[{},3]"

countOnly.decodeJson("""[{},3]""")
// res13: Either[String, Int] = Right(value = 3)
```

## Scala 2 vs Scala 3

In Scala 2, derive with `DeriveJsonCodec.gen`, as above. It works on Scala 3 too.
In Scala 3, `derives JsonCodec` calls `JsonCodec.derived`.
It takes a `JsonCodecConfiguration` as a `using` parameter. The default configuration is found automatically, so a `derives` clause is all that is needed:

```scala
final case class Person(name: String, age: Int) derives JsonCodec
```

Define a given of your own only to change the defaults. A given in the enclosing scope is used by every `derives` clause that can see it:

```scala
given JsonCodecConfiguration = JsonCodecConfiguration(fieldNameMapping = SnakeCase)

final case class Account(accountId: Int, displayName: String) derives JsonCodec
```

Running the example file on Scala 3 prints:

```text
default configuration: {"name":"Ada","age":36}
decoded: Right(Person(Ada,36))
snake_case given: {"account_id":1,"display_name":"Ada"}
```

Scala 2.13 and Scala 3 also have a codec for `immutable.ArraySeq`, and Scala 3 has one for `IArray`. A union of string literal types has an encoder and a decoder on Scala 3, but no `JsonCodec`. See [JsonEncoder](json-encoder.md#scala-2-vs-scala-3).

## See Also

- [JsonEncoder](json-encoder.md) and [JsonDecoder](json-decoder.md), the two halves of a codec
- [Control the JSON a Type Encodes To](../how-to/encoding.md) and [Decoding](../how-to/decoding.md) for derivation options
- [Build an Encoder or Decoder from an Existing One](../how-to/manual-instances.md) for `contramap`, `map`, `mapOrFail`, `transform` and `transformOrFail`
- [JsonCodecConfiguration](json-codec-configuration.md) and [Annotations](annotations.md) for the derivation options
- [Built-in Instances](built-in-instances.md) for every type that has a codec out of the box
