# JsonEncoder

> Reference for the JsonEncoder type class: encodeJson, the toJson extension methods, contramap, the combinators that build new encoders, and writing a fast encoder by hand with unsafeEncode.

`JsonEncoder[A]` is the type class that turns a value of type `A` into JSON text.
Use it directly when you write an encoder by hand or build one from another encoder.
Most code never calls it directly: it calls `toJson` or `toJsonPretty`, and the compiler finds the encoder in the implicit scope.
Instances for the standard types live in the `JsonEncoder` companion object, so no import is needed to find them. [Built-in Instances](built-in-instances.md) lists every one of them.
To get a particular JSON shape from your own types, see [Control the JSON a Type Encodes To](../how-to/encoding.md).

| Method                                                | What it does                                                    | Details                                                       |
|-------------------------------------------------------|-----------------------------------------------------------------|---------------------------------------------------------------|
| `unsafeEncode(a, indent, out)`                        | The only abstract member: writes `a` into a `Write`             | [Writing an Encoder by Hand](#writing-an-encoder-by-hand)     |
| `encodeJson(a, indent)`                               | Encodes a value to a `CharSequence`, compact or pretty          | [Encoding Values](#encodejson)                                |
| `toJson`                                              | Extension: compact JSON `String`                                | [Extension Methods](#extension-methods)                       |
| `toJsonPretty`                                        | Extension: pretty JSON `String`                                 | [Extension Methods](#extension-methods)                       |
| `toJsonAST`                                           | Encodes to a `Json` tree; can be overridden                     | [Extension Methods](#extension-methods)                       |
| `contramap`                                           | Builds an encoder for `B` from one for `A`                      | [contramap](#contramap)                                       |
| `narrow`                                              | Retypes an encoder for a subtype                                | [narrow](#narrow)                                             |
| `zip`, `zipWith`                                      | Encodes a pair as a JSON array                                  | [zip and zipWith](#zip-and-zipwith)                           |
| `either`, `orElseEither`, `eitherWith`                | Encodes an `Either`, tagged or untagged                         | [either](#either-orelseeither-and-eitherwith)                 |
| `isNothing`, `isEmpty`                                | Tell derived encoders to omit a field                           | [Missing and Empty Values](#missing-and-empty-values)         |
| `JsonEncoder.suspend`                                 | Defers construction for recursive types                         | [Recursive Encoders](#recursive-encoders)                     |
| `JsonEncoder.AbstractJsonEncoder`                     | Base class for hand-written encoders                            | [Writing an Encoder by Hand](#writing-an-encoder-by-hand)     |
| `JsonEncoder.bump`, `JsonEncoder.pad`                 | Indentation helpers for pretty printing                         | [Pretty Printing](#pretty-printing-with-bump-and-pad)         |
| `JsonEncoder.keyValueIterable`, `keyValueChunk`       | Encodes a sequence of pairs as a JSON object                    | [Map Keys and Key-Value Pairs](#map-keys-and-key-value-pairs) |
| `Double`, `Float` encoders                            | Write `NaN` and the infinities as quoted strings                | [Non-Finite Numbers](#non-finite-numbers)                     |
| `ArraySeq`, `IArray`, string-literal unions           | Scala-version-specific instances                                | [Scala 2 vs Scala 3](#scala-2-vs-scala-3)                     |

## Encoding Values

`encodeJson` is the entry point that every other way of encoding ends up calling. The extension methods wrap it for everyday use.

### `encodeJson`

`JsonEncoder#encodeJson` takes the value and an optional indent:

```scala
final def encodeJson(a: A, indent: Option[Int] = None): CharSequence
```

`indent` controls pretty printing.
`None` produces compact output.
`Some(n)` produces pretty output that starts at nesting level `n`, with two spaces per level, so `Some(0)` is the usual choice.
In pretty output, object fields are written as `"name" : value`, with a space on each side of the colon, and tuples stay on one line with `", "` between their elements.
The result is a `CharSequence`. Call `toString` to get a `String`.

The examples in this section use a `Person` type with a derived encoder:

```scala
import zio.json._

final case class Person(name: String, age: Int)

object Person {
  implicit val encoder: JsonEncoder[Person] = DeriveJsonEncoder.gen[Person]
}
```

Encoding the same value compactly and then pretty-printed at level 0:

```scala
JsonEncoder[Person].encodeJson(Person("Ada", 36)).toString
// res0: String = "{\"name\":\"Ada\",\"age\":36}"

println(JsonEncoder[Person].encodeJson(Person("Ada", 36), Some(0)))
// {
//   "name" : "Ada",
//   "age" : 36
// }
```

### Extension Methods

`import zio.json._` adds three methods to every value that has a `JsonEncoder`. They are defined in `EncoderOps` in the package object.

| Method         | Result                 | Same as                           |
|----------------|------------------------|-----------------------------------|
| `toJson`       | `String`               | `encodeJson(a, None).toString`    |
| `toJsonPretty` | `String`               | `encodeJson(a, Some(0)).toString` |
| `toJsonAST`    | `Either[String, Json]` | `toJsonAST(a)` on the encoder     |

The extension methods called on a `Person`:

```scala
Person("Ada", 36).toJson
// res2: String = "{\"name\":\"Ada\",\"age\":36}"

Person("Ada", 36).toJsonAST
// res3: Either[String, ast.Json] = Right(
//   value = Obj(
//     fields = IndexedSeq(("name", Str(value = "Ada")), ("age", Num(value = 36)))
//   )
// )
```

`toJsonAST` returns a [`Json`](json-ast.md) tree. On the trait it is an ordinary method that an encoder can override:

```scala
trait JsonEncoder[A] {
  def toJsonAST(a: A): Either[String, Json] = Json.decoder.decodeJson(encodeJson(a, None))
}
```

The default implementation encodes to text and parses the text back.
The built-in instances override it to build the tree directly, except the tuple encoders (and so `zip` and `zipWith`) and `JsonEncoder.orElseEither`, which use the default.
`contramap` and `JsonEncoder.suspend` forward `toJsonAST` to the encoder they wrap.
A hand-written encoder can override it too; see [Overriding toJsonAST](#overriding-tojsonast).

## Transforming an Encoder

These two methods give an existing encoder a new input type without writing any JSON yourself.

### `contramap`

`JsonEncoder#contramap` takes a function from the new input type to the old one:

```scala
final def contramap[B](f: B => A): JsonEncoder[B]
```

Builds an encoder for `B` from an encoder for `A` and a function from `B` to `A`.
`isNothing`, `isEmpty` and `toJsonAST` of the new encoder defer to the original one.
The walkthrough is in [Build an Encoder or Decoder from an Existing One](../how-to/manual-instances.md).

To write a `UserId` with the `Int` encoder:

```scala
final case class UserId(value: Int)

implicit val userIdEncoder: JsonEncoder[UserId] = JsonEncoder[Int].contramap(_.value)
```

A `UserId` is now written as its bare number:

```scala
UserId(7).toJson
// res4: String = "7"
```

### `narrow`

`JsonEncoder#narrow` takes only a type parameter:

```scala
final def narrow[B <: A]: JsonEncoder[B]
```

Returns the same encoder typed for a subtype `B` of `A`.
It is an unchecked cast: it does not create a new encoder.

An encoder for `CharSequence` narrowed to `String`:

```scala
val chars: JsonEncoder[CharSequence] = JsonEncoder[String].contramap(_.toString)

val strings: JsonEncoder[String] = chars.narrow[String]
```

## Combining Encoders

These methods build an encoder for a pair or an `Either` out of two encoders:

- `zip`: `def zip[B](that: => JsonEncoder[B]): JsonEncoder[(A, B)]`
- `zipWith`: `def zipWith[B, C](that: => JsonEncoder[B])(f: C => (A, B)): JsonEncoder[C]`
- `either`: `def either[B](that: => JsonEncoder[B]): JsonEncoder[Either[A, B]]`
- `orElseEither`: `def orElseEither[B](that: => JsonEncoder[B]): JsonEncoder[Either[A, B]]`
- `eitherWith`: `def eitherWith[B, C](that: => JsonEncoder[B])(f: C => Either[A, B]): JsonEncoder[C]`

All five are `final`, and `that` is passed by name.

### `zip` and `zipWith`

`zip` encodes a tuple as a JSON array with one element per encoder.
It returns `JsonEncoder.tuple2`; the companion has implicit encoders for tuples of 1 to 22 elements, all written as JSON arrays.
`zipWith` first converts your own type to a tuple with `f`.

An encoder for a pair, and one for an `Entry` that goes through a pair:

```scala
val pair = JsonEncoder[String].zip(JsonEncoder[Int])

final case class Entry(name: String, count: Int)

val entry = JsonEncoder[String].zipWith(JsonEncoder[Int])((e: Entry) => (e.name, e.count))
```

Both encoders write a two-element array:

```scala
pair.encodeJson(("apples", 3))
// res6: CharSequence = "[\"apples\",3]"

entry.encodeJson(Entry("apples", 3))
// res7: CharSequence = "[\"apples\",3]"
```

### `either`, `orElseEither` and `eitherWith`

`either` tags the value with `Left` or `Right`. It is the same as the implicit `Either` encoder, `JsonEncoder.either`.
`orElseEither` writes the value without a tag, so the JSON carries no trace of which side it came from.
The companion has the same untagged encoder as `JsonEncoder.orElseEither[A, B]`, which takes both encoders implicitly. It is not implicit itself, so it is never picked for an `Either` by accident.
`eitherWith` is `either` preceded by `contramap`: you supply `f` to split your own type into `Either[A, B]`.

The three combinators side by side, with `byShape` sending numeric strings to the `Int` side:

```scala
val tagged = JsonEncoder[Int].either(JsonEncoder[String])

val untagged = JsonEncoder[Int].orElseEither(JsonEncoder[String])

val byShape = JsonEncoder[Int].eitherWith(JsonEncoder[String])((s: String) => s.toIntOption.toLeft(s))
```

The tagged form wraps the value in an object, the untagged form does not:

```scala
tagged.encodeJson(Left(1))
// res8: CharSequence = "{\"Left\":1}"

untagged.encodeJson(Left(1))
// res9: CharSequence = "1"

byShape.encodeJson("12")
// res10: CharSequence = "{\"Left\":12}"
```

The untagged form is only safe to decode if the decoder can tell the two shapes apart.
See `orElseEither` in [JsonDecoder](json-decoder.md#orelse-and-orelseeither).

## Missing and Empty Values

Two methods on the trait report whether a value counts as missing or empty:

```scala
trait JsonEncoder[A] {
  def isNothing(a: A): Boolean = false

  def isEmpty(a: A): Boolean = false
}
```

Derived encoders ask each field's encoder these two questions to decide whether to leave the field out of the JSON object.
The default for both is `false`.
The `Option` encoder overrides `isNothing`, so a `None` field is omitted. `Some(None)` also counts as nothing, so an `Option[Option[A]]` field treats `None` and `Some(None)` the same way.
The collection and map encoders (`Array`, `List`, `ArraySeq`, every `Iterable`, `Map` and its variants) override `isEmpty`. `NonEmptyChunk` inherits it through `contramap`.
Which fields are actually skipped depends on the configuration. See [explicitNulls](json-codec-configuration.md#explicitnulls), [explicitEmptyCollections](json-codec-configuration.md#explicitemptycollections) and [Annotations](annotations.md).
The map encoders also use `isNothing`: they drop every entry whose value is nothing (see [Map Keys and Key-Value Pairs](#map-keys-and-key-value-pairs)).

Override these methods only when you write an encoder class by hand and want it to behave like `Option` or a collection.
`contramap` forwards both methods to the wrapped encoder.

## Recursive Encoders

`JsonEncoder.suspend` takes the encoder by name:

```scala
object JsonEncoder {
  def suspend[A](encoder0: => JsonEncoder[A]): JsonEncoder[A]
}
```

`JsonEncoder.suspend` defers building an encoder until it is first used.
It lets an encoder refer to itself, for example for a tree type.
It is on the companion object, not on the trait.

A tree type whose derived encoder refers to itself through `List[Tree]`:

```scala
final case class Tree(value: Int, children: List[Tree])

object Tree {
  implicit val encoder: JsonEncoder[Tree] =
    JsonEncoder.suspend(DeriveJsonEncoder.gen[Tree])
}
```

## Writing an Encoder by Hand

You rarely need this: `contramap`, the combinators above and derivation cover almost every type. Write `unsafeEncode` yourself when you need full control over the output or the fastest possible encoder, for example for a hot path that `contramap` through an intermediate value would slow down.

`JsonEncoder#unsafeEncode` is the only abstract member of `JsonEncoder`. Every other way of encoding, including `JsonEncoder#encodeJson` and the `toJson` and `toJsonPretty` extension methods, ends up calling it:

```scala
trait JsonEncoder[A] {
  def unsafeEncode(a: A, indent: Option[Int], out: zio.json.internal.Write): Unit
}
```

It appends the JSON for `a` to `out` and returns nothing.
`indent` is `None` for compact output and `Some(level)` for pretty output, where `level` is the nesting level of the value being written.

### The `Write` Interface

`out` is a `zio.json.internal.Write`, a minimal, unsynchronized subset of `java.io.Writer`:

```scala
trait Write {
  def write(c: Char): Unit
  def write(s: String): Unit
  def write(cs: Array[Char], from: Int, to: Int): Unit
  def write(c1: Char, c2: Char): Unit
  def write(c1: Char, c2: Char, c3: Char): Unit
  def write(c1: Char, c2: Char, c3: Char, c4: Char): Unit
  def write(c1: Char, c2: Char, c3: Char, c4: Char, c5: Char): Unit
  def write(s: Short): Unit
  def write(s1: Short, s2: Short): Unit
  def write(s1: Short, s2: Short, s3: Short): Unit
  def write(s1: Short, s2: Short, s3: Short, s4: Short): Unit
}
```

The `write(String)` and `Char` overloads write their characters as they are. They do not quote or escape anything.
Three of the overloads behave differently from what the `java.io.Writer` names suggest:

- `write(cs, from, to)` takes an exclusive end index `to`, not a length as `java.io.Writer#write(cbuf, off, len)` does. It writes `cs(from)` up to `cs(to - 1)`.
- The `write(s: Short)` overloads do not write a number. Each `Short` packs two characters, low byte first. The library uses them for indentation; to write a number, call the number's encoder, for example `JsonEncoder.int.unsafeEncode(n, indent, out)`.
- `zio.json.internal.WriteWriter(out: java.io.Writer)` wraps a `java.io.Writer` in a `Write`, so an encoder can write straight into a file or socket writer without building a `String` first.

:::warning[No compatibility guarantee]
`Write` and `WriteWriter` live in the `zio.json.internal` package. The library may change anything in that package in any release, without a deprecation period. Keep hand-written encoders small, and prefer `contramap` when it is fast enough.
:::

To see the exclusive end index, we write a slice of a character array through `WriteWriter` into a `java.io.StringWriter`:

```scala
import zio.json.internal.WriteWriter

val slice = new java.io.StringWriter()
// slice: java.io.StringWriter = cd

new WriteWriter(slice).write("abcdef".toCharArray, 2, 4)

slice.toString
// res12: String = "cd"
```

### `AbstractJsonEncoder`

`JsonEncoder.AbstractJsonEncoder[A]` is an abstract class that extends `JsonEncoder[A]` and adds nothing else.
Every built-in instance extends it, because subclasses of an abstract class compile to smaller class files than anonymous classes of a trait.
Extend it in a hand-written encoder for the same reason; `new JsonEncoder[A] { ... }` works too.

### Escaping Strings

`out.write(s)` copies a `String` verbatim. To write a JSON string, let the `String` encoder do it: `JsonEncoder.string.unsafeEncode(s, indent, out)` adds the surrounding quotes and escapes `"`, `\` and control characters.
The same applies to field names: write them with `JsonEncoder.string` unless you know they contain nothing that needs escaping.
For values of other types, delegate to their encoders in the same way: `JsonEncoder.int`, `JsonEncoder[Instant]`, or any encoder in scope.

### Pretty Printing with `bump` and `pad`

Two public helpers on the companion handle indentation, so a hand-written encoder can support `toJsonPretty`:

```scala
object JsonEncoder {
  def bump(indent: Option[Int]): Option[Int]
  def pad(indent: Option[Int], out: Write): Unit
}
```

`JsonEncoder.bump` returns the indent for one level deeper: `Some(n + 1)` for `Some(n)`, and `None` for `None`.
`JsonEncoder.pad` writes a newline followed by two spaces per level when `indent` is `Some(level)`, and writes nothing when it is `None`.
The built-in object encoders call `pad(bump(indent), out)` before each field, pass the bumped indent to the field's encoder, write `" : "` instead of `":"` between name and value in pretty mode, and call `pad(indent, out)` before the closing bracket.

### Overriding `toJsonAST`

The default `JsonEncoder#toJsonAST` encodes to text and parses it again. A hand-written encoder can override it to build the `Json` tree directly, which avoids the round trip through text:

```scala
trait JsonEncoder[A] {
  def toJsonAST(a: A): Either[String, zio.json.ast.Json]
}
```

Keep the two methods consistent: both should describe the same JSON.

For a complete hand-written codec that uses `JsonEncoder.AbstractJsonEncoder`, the built-in encoders, `JsonEncoder.bump`, `JsonEncoder.pad` and an overridden `JsonEncoder#toJsonAST`, see [Write a Fast Encoder or Decoder by Hand](../how-to/low-level-codecs.md).

## Non-Finite Numbers

JSON has no literal for "not a number" or infinity. The `Double` and `Float` encoders write `NaN`, `Infinity` and `-Infinity` as quoted JSON strings, so the output is still valid JSON. The `Double` and `Float` decoders accept the same quoted strings, so these values round-trip:

```scala
println(Double.NaN.toJson)
// "NaN"

println(Double.NegativeInfinity.toJson)
// "-Infinity"

"\"Infinity\"".fromJson[Double]
// res15: Either[String, Double] = Right(value = Infinity)
```

`JsonEncoder#toJsonAST` does not follow the same rule. A `Json.Num` holds a `java.math.BigDecimal`, which cannot represent these values, so `toJsonAST` on `NaN` or an infinity throws a `NumberFormatException` instead of returning a `Left`. The same holds for `Json.Num(Double.NaN)` and for a derived encoder whose `Double` or `Float` field holds such a value:

```scala
scala.util.Try(Double.NaN.toJsonAST)
// res16: util.Try[Either[String, ast.Json]] = Failure(
//   exception = java.lang.NumberFormatException: Character " is neither a decimal digit number, decimal point, nor "e" notation exponential mark.
// )
```

If your data can contain non-finite numbers and you need a `Json` tree, filter or convert them before calling `JsonEncoder#toJsonAST`.

## Map Keys and Key-Value Pairs

A JSON object's keys are always strings, so the `Map` encoders need a way to turn the key type into a `String`. That job belongs to a separate type class, `JsonFieldEncoder[K]`. The library provides it for `String`, `Int`, `Long` and `UUID`; any other key type needs one of your own, usually `JsonFieldEncoder[String].contramap(...)`. [JsonFieldEncoder and JsonFieldDecoder](json-field-codecs.md) documents `JsonFieldEncoder` and its decoding counterpart `JsonFieldDecoder`.

The encoders for `Map`, `HashMap`, `mutable.Map`, `collection.SortedMap` and `ListMap` write one JSON field per entry. They skip every entry whose value encoder reports `isNothing`, so a `None` value leaves its key out entirely:

```scala
Map(1 -> "one", 2 -> "two").toJson
// res17: String = "{\"1\":\"one\",\"2\":\"two\"}"

Map("a" -> None, "b" -> Some(1)).toJson
// res18: String = "{\"b\":1}"
```

A sequence of pairs, such as `List[(String, Int)]` or `Chunk[(String, Int)]`, is encoded by default as an array of two-element arrays, because tuples have their own encoder. To write such a sequence as a JSON object instead, use one of two companion methods that are deliberately not implicit:

```scala
object JsonEncoder {
  def keyValueIterable[K, A, T[X, Y] <: Iterable[(X, Y)]](implicit
    K: JsonFieldEncoder[K],
    A: JsonEncoder[A]
  ): JsonEncoder[T[K, A]]

  def keyValueChunk[K, A](implicit K: JsonFieldEncoder[K], A: JsonEncoder[A]): JsonEncoder[Chunk[(K, A)]]
}
```

`JsonEncoder.keyValueChunk` is `JsonEncoder.keyValueIterable` fixed to `Chunk`. `JsonEncoder.keyValueIterable` needs a type constructor with two parameters, so for a `List` of pairs we declare a type alias first.
Unlike a `Map`, a sequence of pairs can hold the same key twice, and both entries are written. `JsonDecoder.keyValueChunk` is the matching decoder, and it keeps duplicate keys too.

Both encoders, one for a `Chunk` and one for a `List` through the alias:

```scala
import zio.Chunk

type Pairs[K, V] = List[(K, V)]

val chunkAsObject: JsonEncoder[Chunk[(String, Int)]] = JsonEncoder.keyValueChunk[String, Int]

val listAsObject: JsonEncoder[Pairs[String, Int]] = JsonEncoder.keyValueIterable[String, Int, Pairs]
```

The default tuple encoding next to the two object encodings:

```scala
Chunk("a" -> 1, "b" -> 2).toJson
// res19: String = "[[\"a\",1],[\"b\",2]]"

chunkAsObject.encodeJson(Chunk("a" -> 1, "b" -> 2))
// res20: CharSequence = "{\"a\":1,\"b\":2}"

listAsObject.encodeJson(List("a" -> 1, "a" -> 2))
// res21: CharSequence = "{\"a\":1,\"a\":2}"
```

## Instances and Summoning

`JsonEncoder.apply[A](implicit a: JsonEncoder[A])` returns the implicit instance, so `JsonEncoder[Int]` reads as a lookup.
A `JsonCodec[A]` in scope also provides a `JsonEncoder[A]`: the companion has `fromCodec`.
The companion's instances cover primitives, `Option`, `Either`, tuples, collections, maps, `java.time` types, `UUID` and `Currency`; the full list with the JSON shape of each is in [Built-in Instances](built-in-instances.md).

## Scala 2 vs Scala 3

Derivation and a few built-in instances depend on the Scala version.

`DeriveJsonEncoder.gen` works on both.
Scala 3 also has `JsonEncoder.derived`, which is what `derives JsonEncoder` calls.
It takes a `JsonCodecConfiguration` as a `using` parameter. The default configuration is found automatically, so define your own given only to change it (see [JsonCodecConfiguration](json-codec-configuration.md)).
On Scala 3, a `derives` clause on the case class is all that is needed:

```scala
final case class Person(name: String, age: Int) derives JsonEncoder
```

The built-in instances that exist only on some Scala versions are:

| Instance                            | Scala 2.12 | Scala 2.13 | Scala 3 | JSON shape |
|-------------------------------------|------------|------------|---------|------------|
| `immutable.ArraySeq[A]`             | no         | yes        | yes     | array      |
| `IArray[A]`                         | no         | no         | yes     | array      |
| String-literal union (`"a" \| "b"`) | no         | no         | yes     | string     |

`ArraySeq` and `IArray` need a `ClassTag` for the element type, like `Array`. An `IArray` encodes as a JSON array:

```scala
val primes: IArray[Int] = IArray(2, 3, 5, 7)
println("IArray: " + primes.toJson)
```

A union of string literal types, such as `"red" | "green" | "blue"`, encodes as the plain string. Because `JsonFieldEncoder` is contravariant, the `String` key encoder also serves such a union, so it can be a `Map` key without extra code:

```scala
type Color = "red" | "green" | "blue"

final case class Paint(color: Color, stock: Map[Color, Int]) derives JsonEncoder
```

Running the example file on Scala 3 prints:

```text
derives: {"name":"Ada","age":36}
IArray: [2,3,5,7]
union: "red"
union in a case class: {"color":"green","stock":{"red":1,"blue":2}}
```

## Streaming

`encodeJsonStream`, which returns a `ZStream[Any, Throwable, Char]`, and the other streaming encoders exist only on the JVM.
They are on the `JsonEncoderPlatformSpecific` trait that `JsonEncoder` extends. The JS and Native builds do not have them.
See [Streaming API](streaming.md).

## See Also

- [Write a Fast Encoder or Decoder by Hand](../how-to/low-level-codecs.md) for a worked hand-written codec
- [Built-in Instances](built-in-instances.md) for every type that has an encoder out of the box
- [JsonFieldEncoder and JsonFieldDecoder](json-field-codecs.md) for map keys
- [Control the JSON a Type Encodes To](../how-to/encoding.md) for annotations, `contramap` and sealed-trait tags in practice
- [Build an Encoder or Decoder from an Existing One](../how-to/manual-instances.md) for `contramap` together with the decoder side
- [JsonDecoder](json-decoder.md) and [JsonCodec](json-codec.md)
- [JSON AST](json-ast.md) for the `Json` type returned by `toJsonAST`
- [Streaming API](streaming.md) for the JVM-only stream encoders
