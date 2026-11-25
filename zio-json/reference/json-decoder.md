# JsonDecoder

> Reference for the JsonDecoder type class: decodeJson, the fromJson extension methods, error handling, the combinators that build new decoders, and hand-written low-level decoders.

`JsonDecoder[A]` is the type class that reads JSON text and produces a value of type `A`, or a human-readable error.
Use it directly when you write a decoder by hand or build one from another decoder.
Most code calls `fromJson` and lets the compiler find the decoder in the implicit scope.
Instances for the standard types live in the `JsonDecoder` companion object, and `JsonDecoder.apply[A]` returns the one in scope.
The full list of types that have one is in [Built-in Instances](built-in-instances.md).
Deriving an instance for your own type is covered in [Getting Started](../getting-started.md), and decoding JSON whose keys do not match your fields in [Decode JSON Whose Shape Differs from Your Case Class](../how-to/decoding.md).

| Method                                                        | What it does                                             | Details                                                 |
|---------------------------------------------------------------|----------------------------------------------------------|---------------------------------------------------------|
| `decodeJson(...)`                                             | Decodes a `CharSequence`, `Chunk[Byte]` or `Array[Byte]` | [decodeJson](#decodejson)                               |
| `fromJson`                                                    | Extension: decodes a string or bytes                     | [Extension Methods](#extension-methods)                 |
| `fromJsonAST`                                                 | Decodes from a `Json` tree                               | [fromJsonAST](#fromjsonast)                             |
| `map`, `mapOrFail`                                            | Transforms a decoded value, optionally failing           | [map and mapOrFail](#map-and-maporfail)                 |
| `widen`                                                       | Retypes a decoder for a supertype                        | [widen](#widen)                                         |
| `zip` (`<*>`), `zipLeft` (`<*`), `zipRight` (`*>`), `zipWith` | Decodes a JSON array of two values                       | [zip](#zip-zipleft-zipright-and-zipwith)                |
| `both`, `bothLeft`, `bothRight`, `bothWith`                   | Decodes the same value twice                             | [both](#both-bothleft-bothright-and-bothwith)           |
| `orElse` (`<>`), `orElseEither` (`<+>`)                       | Falls back to another decoder on failure                 | [orElse](#orelse-and-orelseeither)                      |
| `JsonDecoder.peekChar`                                        | Chooses a decoder by the next character                  | [peekChar](#peekchar)                                   |
| `unsafeDecodeMissing`                                         | Value used when a field is absent                        | [Missing Fields](#missing-fields)                       |
| `JsonDecoder.suspend`                                         | Defers construction for recursive types                  | [Recursive Decoders](#recursive-decoders)               |
| `JsonDecoder.keyValueChunk`                                   | Decodes an object into ordered key/value pairs           | [Map Keys](#map-keys)                                   |
| `unsafeDecode`, `JsonDecoder.AbstractJsonDecoder`             | The low-level method a hand-written decoder implements   | [Writing a Decoder by Hand](#writing-a-decoder-by-hand) |
| `unsafeFromJsonAST`                                           | The low-level method behind `fromJsonAST`                | [Decoding from the AST](#decoding-from-the-ast)         |
| `JsonDecoder.UnsafeJson`                                      | The exception a low-level decoder throws                 | [The Decoding Contract](#the-decoding-contract)         |
| `JsonDecoder.derived`                                         | Scala 3 `derives JsonDecoder`                            | [Scala 2 vs Scala 3](#scala-2-vs-scala-3)               |

## Decoding Values

The methods in this section are the entry points: they take input and return `Either[String, A]`.

### `decodeJson`

`decodeJson` has three overloads, one per input type:

```scala
trait JsonDecoder[A] {
  def decodeJson(str: CharSequence): Either[String, A]
  def decodeJson(bytes: Chunk[Byte]): Either[String, A]
  def decodeJson(bytes: Array[Byte]): Either[String, A]
}
```

All three overloads return `Left(message)` for input that is malformed or does not match `A`.
Exceptions thrown by your own functions, for example inside `map`, are not caught.
The two byte overloads read UTF-8 bytes directly, without first copying them into a `String`.
The `Array[Byte]` overload wraps the array in a `Chunk` and calls the `Chunk` one.

The examples on this page share one `Person` type with a derived decoder:

```scala
import zio.Chunk
import zio.json._

final case class Person(name: String, age: Int)

object Person {
  implicit val decoder: JsonDecoder[Person] = DeriveJsonDecoder.gen[Person]
}
```

Each overload decodes the same value from a different input:

```scala
JsonDecoder[Person].decodeJson("""{"name":"Ada","age":36}""")
// res0: Either[String, Person] = Right(value = Person(name = "Ada", age = 36))

JsonDecoder[Int].decodeJson(Chunk.fromArray("42".getBytes("UTF-8")))
// res1: Either[String, Int] = Right(value = 42)

JsonDecoder[Int].decodeJson("42".getBytes("UTF-8"))
// res2: Either[String, Int] = Right(value = 42)
```

A function passed to `JsonDecoder#map` that throws makes `JsonDecoder#decodeJson` throw too, so catch such exceptions yourself, or return a `Left` from `JsonDecoder#mapOrFail` instead:

```scala
scala.util.Try(JsonDecoder[Int].map(n => 10 / n).decodeJson("0"))
// res3: util.Try[Either[String, Int]] = Failure(
//   exception = java.lang.ArithmeticException: / by zero
// )
```

### Decoding from Bytes

JSON usually arrives as bytes: an HTTP response body, a file, a Kafka record.
The `Chunk[Byte]` and `Array[Byte]` overloads of `decodeJson`, and the `fromJson` extensions on those types, turn bytes into characters as the parser consumes them, so peak memory is the input plus a constant.
Converting to a `String` first, with `new String(chunk.toArray, UTF_8)`, holds the chunk, an array copy of it and the `String` at the same time.

A chunk backed by a single array, which is what `Chunk.fromArray` produces, is read in place with no copy at all.
An `Array[Byte]` is wrapped this way, so it is not copied either.
A chunk of any other shape, such as one concatenated from several network reads, is copied through a fixed window of 8 KiB at a time.

The input must be UTF-8, as [RFC 8259](https://www.rfc-editor.org/rfc/rfc8259#section-8.1) requires for JSON exchanged between systems.
A malformed UTF-8 sequence decodes to the replacement character U+FFFD on every platform, matching what `new String(bytes, UTF_8)` produces on the JVM, and is not an error:

```scala
Array[Byte]('"', 'a', 0xff.toByte, 'b', '"').fromJson[String].map(_.map(c => f"U+${c.toInt}%04X").mkString(" "))
// res4: Either[String, String] = Right(value = "U+0061 U+FFFD U+0062")
```

Errors are reported in the same format as for a `CharSequence`.
For input that does not fit in memory, decode from a `ZStream` of bytes instead (JVM only, see [Streaming](#streaming)).

### Trailing Input Is Ignored

`decodeJson` and `fromJson` read one complete JSON value from the start of the input and stop there.
They do not check that the rest of the input is empty, so whatever follows the first value is ignored.
For numbers this means a top-level number stops at the first character that cannot continue it, and the rest is dropped without an error:

```scala
"1e3".fromJson[Int]
// res5: Either[String, Int] = Right(value = 1)

"12x".fromJson[Int]
// res6: Either[String, Int] = Right(value = 12)

"""{"name":"Ada","age":36} and more""".fromJson[Person]
// res7: Either[String, Person] = Right(value = Person(name = "Ada", age = 36))
```

Inside an object or an array the same input fails, because the decoder then expects a `,` or a closing bracket after the value:

```scala
"[1e3]".fromJson[List[Int]]
// res8: Either[String, List[Int]] = Left(
//   value = "(expected ',' or ']' got 'e')"
// )

"""{"a":12x}""".fromJson[Map[String, Int]]
// res9: Either[String, Map[String, Int]] = Left(
//   value = "(expected ',' or '}' got 'x')"
// )
```

[How Strict Is the Decoder](../explanation/decoder-strictness.md) explains this and the other leniencies, and how to get stricter checks.

### Extension Methods

`import zio.json._` adds `fromJson[A]` to `CharSequence`, `Chunk[Byte]` and `Array[Byte]`.
They are defined in `DecoderOps`, `DecoderBytesOps` and `DecoderByteArrayOps` in the package object, and each delegates to the matching `decodeJson` overload:

```scala
"""{"name":"Ada","age":36}""".fromJson[Person]
// res10: Either[String, Person] = Right(
//   value = Person(name = "Ada", age = 36)
// )

"""{"name":"Ada","age":36}""".getBytes("UTF-8").fromJson[Person]
// res11: Either[String, Person] = Right(
//   value = Person(name = "Ada", age = 36)
// )
```

### Errors

The error type is a plain `String`.
It is rendered from a path to the failure, in a format close to `jq`.
Input that ends too early gives `Unexpected end of input`, and input nested too deeply gives `Unexpected structure`.
See [Errors](errors.md) for the full format.

A missing field and a value of the wrong type each produce an error that names the field:

```scala
"""{"name":"Ada"}""".fromJson[Person]
// res12: Either[String, Person] = Left(value = ".age(missing)")

"""{"name":"Ada","age":"old"}""".fromJson[Person]
// res13: Either[String, Person] = Left(value = ".age(expected an Int)")
```

### `fromJsonAST`

`fromJsonAST` decodes from an already parsed [`Json`](json-ast.md) tree instead of text:

```scala
trait JsonDecoder[A] {
  def fromJsonAST(json: Json): Either[String, A]
}
```

Errors have the same shape as `decodeJson`.
`Json#as[A]` calls this method.

A `Json.Num` decodes as an `Int`, and a `Json.Str` does not:

```scala
import zio.json.ast.Json

JsonDecoder[Int].fromJsonAST(Json.Num(1))
// res14: Either[String, Int] = Right(value = 1)

JsonDecoder[Int].fromJsonAST(Json.Str("one"))
// res15: Either[String, Int] = Left(value = "(expected a Int)")
```

Some messages from the AST path are worded differently from the text path, for example `expected a Int` instead of `expected an Int`.
How the AST path works, and why it can be slower for some types, is covered in [Decoding from the AST](#decoding-from-the-ast).

## Transforming a Decoder

These methods build a new decoder from an existing one without touching the JSON layout.

### `map` and `mapOrFail`

`map` applies a function to the decoded value, and `mapOrFail` applies one that can reject it:

```scala
trait JsonDecoder[A] {
  def map[B](f: A => B): JsonDecoder[B]
  def mapOrFail[B](f: A => Either[String, B]): JsonDecoder[B]
}
```

Use `map` when the conversion cannot fail and `mapOrFail` when it can.
A `Left` from `mapOrFail` becomes the decode error at the position of the value.
The walkthrough is in [Build an Encoder or Decoder from an Existing One](../how-to/manual-instances.md).

To wrap an `Int` in a type and to reject non-positive numbers:

```scala
final case class UserId(value: Int)

val userId: JsonDecoder[UserId] = JsonDecoder[Int].map(UserId(_))

val positive: JsonDecoder[Int] =
  JsonDecoder[Int].mapOrFail(n => if (n > 0) Right(n) else Left("must be positive"))
```

The first decoder wraps the value, and the second turns the `Left` into an error:

```scala
"7".fromJson[UserId](userId)
// res16: Either[String, UserId] = Right(value = UserId(value = 7))

"-1".fromJson[Int](positive)
// res17: Either[String, Int] = Left(value = "(must be positive)")
```

### `widen`

`widen` returns the same decoder typed for a supertype `B`:

```scala
trait JsonDecoder[A] {
  def widen[B >: A]: JsonDecoder[B]
}
```

It is an unchecked cast and creates no new decoder.

## Combining Decoders

These methods combine two decoders, either one after the other, both over the same value, or as alternatives.

### `zip`, `zipLeft`, `zipRight` and `zipWith`

The `zip` family decodes a JSON array whose elements are read by the two decoders in turn:

```scala
trait JsonDecoder[A] {
  def zip[B](that: => JsonDecoder[B]): JsonDecoder[(A, B)]
  def zipLeft[B](that: => JsonDecoder[B]): JsonDecoder[A]
  def zipRight[B](that: => JsonDecoder[B]): JsonDecoder[B]
  def zipWith[B, C](that: => JsonDecoder[B])(f: (A, B) => C): JsonDecoder[C]
}
```

The operators are aliases: `<*>` is `zip`, `<*` is `zipLeft`, and `*>` is `zipRight`.
Zipping reads the same positional layout that the tuple instances use: a JSON array with one element per decoder.
`zipLeft` and `zipRight` still decode both elements, then discard one.

To decode a `[name, count]` pair:

```scala
val nameAndCount = JsonDecoder[String] <*> JsonDecoder[Int]
```

Each variant reads the same two-element array and keeps a different part of it:

```scala
nameAndCount.decodeJson("""["apples",3]""")
// res18: Either[String, (String, Int)] = Right(value = ("apples", 3))

(JsonDecoder[String] *> JsonDecoder[Int]).decodeJson("""["apples",3]""")
// res19: Either[String, Int] = Right(value = 3)

(JsonDecoder[String] <* JsonDecoder[Int]).decodeJson("""["apples",3]""")
// res20: Either[String, String] = Right(value = "apples")

JsonDecoder[String].zipWith(JsonDecoder[Int])((s, n) => s * n).decodeJson("""["ab",3]""")
// res21: Either[String, String] = Right(value = "ababab")
```

### `both`, `bothLeft`, `bothRight` and `bothWith`

The `both` family runs two decoders over the same JSON value:

```scala
trait JsonDecoder[A] {
  def both[B](that: => JsonDecoder[B]): JsonDecoder[(A, B)]
  def bothLeft[B](that: => JsonDecoder[B]): JsonDecoder[A]
  def bothRight[B](that: => JsonDecoder[B]): JsonDecoder[B]
  def bothWith[B, C](that: => JsonDecoder[B])(f: (A, B) => C): JsonDecoder[C]
}
```

It rewinds the input between the two decoders, instead of reading one value after another as `zip` does.
Use it to decode two views of one object.
The input is buffered while the first decoder runs, so this costs memory on large values.

Two decoders that each read one field of the same object:

```scala
final case class Name(name: String)
final case class Age(age: Int)

val nameDecoder: JsonDecoder[Name] = DeriveJsonDecoder.gen[Name]
val ageDecoder: JsonDecoder[Age]   = DeriveJsonDecoder.gen[Age]
```

Combined with `both`, they read the object once each:

```scala
nameDecoder.both(ageDecoder).decodeJson("""{"name":"Ada","age":36}""")
// res22: Either[String, (Name, Age)] = Right(
//   value = (Name(name = "Ada"), Age(age = 36))
// )
```

### `orElse` and `orElseEither`

`orElse` and `orElseEither` try a second decoder when the first one fails:

```scala
trait JsonDecoder[A] {
  def orElse[A1 >: A](that: => JsonDecoder[A1]): JsonDecoder[A1]
  def orElseEither[B](that: => JsonDecoder[B]): JsonDecoder[Either[A, B]]
}
```

If this decoder fails, the input is rewound and `that` is tried.
`<>` is an alias for `orElse`, and `<+>` is an alias for `orElseEither`.
`orElseEither` wraps the result in `Left` or `Right`, so the two result types can differ.

To accept either a number or a string:

```scala
val intOrString = JsonDecoder[Int] <+> JsonDecoder[String]
```

A number lands in `Left` and a string in `Right`:

```scala
intOrString.decodeJson("1")
// res23: Either[String, Either[Int, String]] = Right(value = Left(value = 1))

intOrString.decodeJson("\"one\"")
// res24: Either[String, Either[Int, String]] = Right(
//   value = Right(value = "one")
// )
```

:::warning[Fallback can use unbounded memory]
Because the decoder rewinds, it must buffer everything it has read.
The source documents this as unsafe against denial of service. Prefer `peekChar` when one character tells the cases apart.
:::

### `peekChar`

`JsonDecoder.peekChar` is on the companion object:

```scala
object JsonDecoder {
  def peekChar[A](partialFunction: PartialFunction[Char, JsonDecoder[A]]): JsonDecoder[A]
}
```

It reads the next non-whitespace character, puts it back, and picks the decoder for that character.
If no case matches, decoding fails with ``(missing case in `peekChar` for 'x'')``, where `x` is the character read.

To pick the `Boolean` decoder for `t` or `f` and the `Int` decoder otherwise:

```scala
val boolOrInt: JsonDecoder[AnyVal] = JsonDecoder.peekChar[AnyVal] {
  case 't' | 'f' => JsonDecoder[Boolean].widen
  case _         => JsonDecoder[Int].widen
}
```

The first character of each input selects the decoder:

```scala
boolOrInt.decodeJson("true")
// res25: Either[String, AnyVal] = Right(value = true)

boolOrInt.decodeJson("5")
// res26: Either[String, AnyVal] = Right(value = 5)
```

## Missing Fields

A derived decoder asks the field's decoder for a value when the field is absent from a JSON object:

```scala
trait JsonDecoder[A] {
  def unsafeDecodeMissing(trace: List[JsonError]): A
}
```

The default fails with `missing`.
The `Option` decoder overrides it to return `None`, which is why an `Option` field may be left out.
Collection decoders (`List`, `Map`, `Chunk`, ...) return an empty collection here, but derived decoders only use it when `explicitEmptyCollections` has `decoding = false`.
By default a missing collection field is an error.
`map`, `mapOrFail`, `orElse` and `suspend` forward this call to the decoder they wrap.
`zip`, the `both` family and `peekChar` do not, so they fail with `missing`.

A field with a default value never reaches `JsonDecoder#unsafeDecodeMissing`: the derived decoder uses the default when the field is absent or its value is `null`.
The default expression is evaluated again on every decode, so a default such as `java.util.UUID.randomUUID()` gives a fresh value each time.
This holds on Scala 2 and Scala 3.

A derived decoder recognizes a collection field by its decoder, looking through `JsonDecoder#map` and `JsonDecoder#mapOrFail` to the decoder they wrap.
A collection decoder combined with `JsonDecoder#orElse` is not recognized, so an absent field calls `JsonDecoder#unsafeDecodeMissing`, which `JsonDecoder#orElse` forwards to the collection decoder, and the field decodes to an empty collection even with the default settings.
[Accept a Field That Arrives in Two Shapes](../how-to/decoding.md#accept-a-field-that-arrives-in-two-shapes) shows this case.

See [Optional and Default Fields](../how-to/optional-and-default-fields.md) for the user-facing rules, and [Annotations](annotations.md) and [JsonCodecConfiguration](json-codec-configuration.md) for field defaults and null handling.

To make a field decode as `0` when it is absent, override `unsafeDecodeMissing` in a decoder that otherwise delegates to the `Int` decoder:

```scala
import zio.json.internal.RetractReader

final case class Counter(name: String, count: Int)

object Counter {
  private val zeroIfMissing: JsonDecoder[Int] = new JsonDecoder.AbstractJsonDecoder[Int] {
    def unsafeDecode(trace: List[JsonError], in: RetractReader): Int =
      JsonDecoder.int.unsafeDecode(trace, in)

    override def unsafeFromJsonAST(trace: List[JsonError], json: Json): Int =
      JsonDecoder.int.unsafeFromJsonAST(trace, json)

    override def unsafeDecodeMissing(trace: List[JsonError]): Int = 0
  }

  implicit val decoder: JsonDecoder[Counter] = {
    implicit val count: JsonDecoder[Int] = zeroIfMissing
    DeriveJsonDecoder.gen[Counter]
  }
}
```

The derived decoder now fills the absent `count` field, while a present field is decoded as usual:

```scala
"""{"name":"visits"}""".fromJson[Counter]
// res27: Either[String, Counter] = Right(
//   value = Counter(name = "visits", count = 0)
// )

"""{"name":"visits","count":3}""".fromJson[Counter]
// res28: Either[String, Counter] = Right(
//   value = Counter(name = "visits", count = 3)
// )
```

## Recursive Decoders

`JsonDecoder.suspend` defers building a decoder until first use, so a decoder can refer to itself:

```scala
object JsonDecoder {
  def suspend[A](decoder0: => JsonDecoder[A]): JsonDecoder[A]
}
```

A tree whose children are trees of the same type:

```scala
final case class Tree(value: Int, children: List[Tree])

object Tree {
  implicit val decoder: JsonDecoder[Tree] =
    JsonDecoder.suspend(DeriveJsonDecoder.gen[Tree])
}
```

## Map Keys

A JSON object decodes into a `Map[K, V]`, `HashMap`, `mutable.Map`, `SortedMap` or `ListMap` when `V` has a `JsonDecoder` and `K` has a `JsonFieldDecoder`.
Built-in key decoders exist for `String`, `Int`, `Long` and `UUID`, and on Scala 3 also for any subtype of `String` that has a `JsonDecoder`, such as a string-literal union.
When the same key appears twice, the last value wins and no error is reported.
[JsonFieldEncoder and JsonFieldDecoder](json-field-codecs.md) covers key types in full, including how to make one for your own type.

`JsonDecoder.keyValueChunk` decodes an object into a `Chunk` of key/value pairs instead, keeping the order of the keys and every duplicate:

```scala
object JsonDecoder {
  def keyValueChunk[K, A](implicit K: JsonFieldDecoder[K], A: JsonDecoder[A]): JsonDecoder[Chunk[(K, A)]]
}
```

It is not implicit, because a `Chunk[(K, A)]` already has a decoder that reads a JSON array of pairs, so call it by name:

```scala
JsonDecoder.keyValueChunk[String, Int].decodeJson("""{"b":1,"a":2,"b":3}""")
// res29: Either[String, Chunk[(String, Int)]] = Right(
//   value = IndexedSeq(("b", 1), ("a", 2), ("b", 3))
// )

"""{"b":1,"a":2,"b":3}""".fromJson[Map[String, Int]]
// res30: Either[String, Map[String, Int]] = Right(
//   value = Map("b" -> 3, "a" -> 2)
// )
```

## Writing a Decoder by Hand

You rarely need this: `map`, `mapOrFail` and derivation cover almost every type.
It is an advanced API for decoders that must be as fast as the built-in ones, or that read a JSON shape no combinator can express.
A hand-written decoder implements `JsonDecoder#unsafeDecode`, which reads straight from the input with the helpers in `zio.json.internal.Lexer`.
For a complete worked codec, see [Write a Fast Encoder or Decoder by Hand](../how-to/low-level-codecs.md).

:::warning[No compatibility guarantee]
`JsonDecoder#unsafeDecode` is part of the public `JsonDecoder` trait, but `Lexer`, `RetractReader`, `StringMatrix` and the rest of the `zio.json.internal` package are internal.
They can change in any release without notice, so pin the version and test hand-written decoders when you upgrade.
:::

### The Decoding Contract

Every decoder is built on one abstract method:

```scala
trait JsonDecoder[A] {
  def unsafeDecode(trace: List[JsonError], in: RetractReader): A
}
```

The rules for implementing it are:

- **Start before the value.** `in` is positioned just before the value, which may be preceded by whitespace. Skip it with `in.nextNonWhitespace()` or a `Lexer` helper.
- **Leave the reader after the value.** Consume the value completely and nothing after it, because the caller reads the next `,`, `}` or `]`. `in.retract()` puts back the last character read, and may be called only once after each read.
- **Fail by throwing.** Report an error with `Lexer.error(message, trace)`, which throws `JsonDecoder.UnsafeJson`. Never return `null` or a placeholder.
- **Extend the trace for children.** Before decoding an object field or an array element, prepend `JsonError.ObjectAccess(name)` or `JsonError.ArrayAccess(index)` to `trace` and pass the result to the child decoder, so an error inside it renders as `.name(...)` or `[index](...)`.

`JsonDecoder.UnsafeJson` is a `case class` with one field, `trace: List[JsonError]`.
It extends `Exception` with `NoStackTrace`, so throwing it is cheap.
`Lexer.error(msg, trace)` throws it with `JsonError.Message(msg)` added to the front of `trace`.
`JsonDecoder#decodeJson`, `fromJson` and `JsonDecoder#fromJsonAST` catch it and render the trace with `JsonError.render`.
They also catch the library's end-of-input exception, which `RetractReader#readChar` throws when the input runs out, and turn it into `Unexpected end of input`.

The reader passed in is a `RetractReader`:

```scala
sealed trait RetractReader extends java.io.Reader {
  def readChar(): Char
  def nextNonWhitespace(): Char
  def retract(): Unit
}
```

`RetractReader#readChar` and `RetractReader#nextNonWhitespace` come from a library-private parent trait, `OneCharReader`.
Several `Lexer` methods name that parent as their parameter type; pass them the `RetractReader` you received.

To get a small, plain class, extend `JsonDecoder.AbstractJsonDecoder[A]` instead of the `JsonDecoder` trait.
It is an abstract class that adds nothing, and its only purpose is to reduce the size of the class files your anonymous decoder classes produce.

### Lexer Helpers

`Lexer` has a method for each step of reading JSON.
These are the ones a hand-written decoder uses most:

```scala
object Lexer {
  def error(msg: String, trace: List[JsonError]): Nothing
  def error(c: Char, got: Char, trace: List[JsonError]): Nothing

  def char(trace: List[JsonError], in: OneCharReader, c: Char): Unit
  def string(trace: List[JsonError], in: OneCharReader): CharSequence

  def firstField(in: RetractReader): Boolean
  def nextField(trace: List[JsonError], in: OneCharReader): Boolean
  def field(trace: List[JsonError], in: OneCharReader, matrix: StringMatrix): Int

  def firstArrayElement(in: RetractReader): Boolean
  def nextArrayElement(trace: List[JsonError], in: OneCharReader): Boolean

  def skipValue(trace: List[JsonError], in: RetractReader): Unit
}
```

| Method                        | What it does                                                                                                     |
|-------------------------------|------------------------------------------------------------------------------------------------------------------|
| `error(msg, trace)`           | Throws `UnsafeJson` with `msg` as the message.                                                                   |
| `error(c, got, trace)`        | Throws with the message `expected 'c' got 'got'`.                                                                |
| `char(trace, in, c)`          | Skips whitespace and reads `c`, or fails with `expected 'c' got '...'`.                                          |
| `string(trace, in)`           | Skips whitespace and reads a JSON string, unescaped, or fails with `expected string`.                            |
| `firstField(in)`              | Call after `{`. Returns `false` for an empty object (and consumes the `}`), `true` otherwise.                    |
| `field(trace, in, matrix)`    | Reads a key and its `:`, and returns the key's index in `matrix`, or `-1` for an unknown key.                    |
| `nextField(trace, in)`        | Call after a field's value. Returns `true` after a `,` and `false` after `}`, and fails on anything else.        |
| `firstArrayElement(in)`       | Call after `[`. Returns `false` for an empty array (and consumes the `]`), `true` otherwise.                     |
| `nextArrayElement(trace, in)` | Call after an element. Returns `true` after a `,` and `false` after `]`, and fails on anything else.             |
| `skipValue(trace, in)`        | Skips one value of any type, for example the value of an unknown field. It does not validate the value it skips. |

To read an object, a decoder reads the `{` with `Lexer.char`, and when `Lexer.firstField` returns `true` it repeats three steps until `Lexer.nextField` returns `false`: `Lexer.field` to read the key, the field's decoder (or `Lexer.skipValue` for `-1`) to read the value, and `Lexer.nextField`. An array follows the same pattern with `Lexer.firstArrayElement` and `Lexer.nextArrayElement`. `Lexer` does not track which keys it has seen, so the decoder reports a missing key itself; derived decoders use the message `missing`, as in `Lexer.error("missing", JsonError.ObjectAccess(name) :: trace)`.

`Lexer` also has readers for single values, such as `Lexer.boolean`, `Lexer.int`, `Lexer.long`, `Lexer.double`, `Lexer.bigDecimal`, `Lexer.uuid` and readers for the `java.time` types. Delegating to the built-in decoders (`JsonDecoder.int.unsafeDecode(...)`) is simpler, and accepts the same inputs as they do, such as quoted numbers.

`StringMatrix` is the structure `Lexer.field` matches keys against.
`new StringMatrix(names: Array[String], aliases: Array[(String, Int)] = Array.empty)` takes the expected keys and, optionally, extra keys that map to the index of one of them.
The keys must be non-empty and there can be at most 64 of them, aliases included.
Matching happens character by character as the key is read, without building a `String` or hashing it.

### `WithRetractReader`

`zio.json.internal.WithRetractReader(in: java.io.Reader)` wraps any `java.io.Reader` in a `RetractReader`, so a test can call `JsonDecoder#unsafeDecode` directly and then read from the same reader to see where the decoder stopped:

```scala
final class WithRetractReader(in: java.io.Reader) extends RetractReader with AutoCloseable
```

Application code should not call `JsonDecoder#unsafeDecode`: it throws `JsonDecoder.UnsafeJson` instead of returning an `Either`.

### Decoding from the AST

`fromJsonAST` and `Json#as` call a second low-level method:

```scala
trait JsonDecoder[A] {
  def unsafeFromJsonAST(trace: List[JsonError], json: Json): A
}
```

Its default implementation encodes the `Json` value back to text and runs `JsonDecoder#unsafeDecode` on that text.
That is correct for every decoder, including hand-written ones, but slower than reading the tree directly.
The decoders for primitives, `java.time` types, `UUID`, `Option`, `Chunk`, tuples and the `Json` types, derived decoders, and decoders built from them with `map`, `mapOrFail` or `orElse` read the tree directly.
Others, including the `List`, `Vector`, `Set`, `Map` and `Either` decoders, `JsonDecoder.peekChar`, and your hand-written decoders, fall back to the default.

To read the tree directly, override `unsafeFromJsonAST` and follow the same rules as for `JsonDecoder#unsafeDecode`: throw with `Lexer.error`, and extend the trace for children. Step 4 of [Write a Fast Encoder or Decoder by Hand](../how-to/low-level-codecs.md#4-build-the-json-tree-directly) shows a decoder that overrides both methods.

## Scala 2 vs Scala 3

`DeriveJsonDecoder.gen` works on both.
Scala 3 also has `JsonDecoder.derived`, which is what a `derives JsonDecoder` clause calls:

```scala
object JsonDecoder {
  inline def derived[A: deriving.Mirror.Of](using config: JsonCodecConfiguration): JsonDecoder[A]
}
```

It takes a `JsonCodecConfiguration` as a `using` parameter.
Without a given of your own, `JsonCodecConfiguration.default` from the companion object is used, so nothing has to be in scope.
On Scala 3, a case class derives its decoder like this:

```scala
final case class Person(name: String, age: Int) derives JsonDecoder
```

Decoding `{"name":"Ada","age":36}` with it gives `Right(Person(Ada,36))`.
To change the configuration for a `derives` clause, put a given `JsonCodecConfiguration` in scope where the type is defined:

```scala
given JsonCodecConfiguration = JsonCodecConfiguration(allowExtraFields = false)

final case class Strict(name: String) derives JsonDecoder
```

With extra fields forbidden, `{"name":"Ada","age":36}` decodes as `Strict` to `Left((invalid extra field))`.

A few messages differ between the two versions.
For example, a sum type read from a `Json.Obj` with more than one key fails with `expected single field object` on Scala 2 and `expected object with a single field` on Scala 3.
[Errors](errors.md#common-messages) lists them.

## Platform Notes

The decoding methods and instances on this page are available on the JVM, Scala.js and Scala Native, with the exceptions below.

### Streaming

`decodeJsonStream`, `decodeJsonStreamInput` and `decodeJsonPipeline` exist only on the JVM, on the `JsonDecoderPlatformSpecific` trait.
On Scala.js and Scala Native that trait is empty.
See [Streaming API](streaming.md).

### Files and Other Platform Differences

The file helpers in the `zio.json` package, `readJsonAs` and `readJsonLinesAs`, also exist only on the JVM.
On Scala.js and Scala Native, decoding `ZoneId` and `ZonedDateTime` needs the time-zone database, which you add as a separate dependency (see the [installation notes](../index.md)).

## See Also

- [Write a Fast Encoder or Decoder by Hand](../how-to/low-level-codecs.md) for a worked hand-written codec
- [Decode JSON Whose Shape Differs from Your Case Class](../how-to/decoding.md) for JSON whose keys or layout do not match your fields
- [Build an Encoder or Decoder from an Existing One](../how-to/manual-instances.md) for `map` and `mapOrFail` in practice
- [JsonEncoder](json-encoder.md) and [JsonCodec](json-codec.md)
- [JsonFieldEncoder and JsonFieldDecoder](json-field-codecs.md) for map keys
- [Built-in Instances](built-in-instances.md) for every type that has a decoder
- [Errors](errors.md) for the error message format
- [JSON AST](json-ast.md) for the `Json` type accepted by `fromJsonAST`
- [How Strict Is the Decoder](../explanation/decoder-strictness.md) for what the decoder accepts
