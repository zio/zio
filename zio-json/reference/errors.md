# Errors

> Reference for ZIO JSON decoding errors: JsonError variants, the path render format, common messages and JVM streaming behavior.

A failed decode returns a `String` that says what went wrong and where.
ZIO JSON builds it from a list of `JsonError` values called the trace.
This page lists the error types, the string format, and the messages you will meet most often.

## JsonError

`JsonError` is a sealed class with four variants.
Three of them are steps on the path to the failure, and one is the failure itself.

| Variant               | Rendered as | Meaning                   |
|-----------------------|-------------|---------------------------|
| `ObjectAccess(field)` | `.field`    | Went into an object key   |
| `ArrayAccess(i)`      | `[i]`       | Went into an array index  |
| `SumType(cons)`       | `{cons}`    | Went into a sum type case |
| `Message(txt)`        | `(txt)`     | The failure itself        |

`JsonError.render(trace: List[JsonError]): String` joins the pieces.
The decoder keeps the trace with the innermost step first, and `render` reverses it, so the string reads from the outside in.
The message comes last, as this trace of three steps shows:

```scala
import zio.json._

JsonError.render(
  List(JsonError.Message("missing"), JsonError.ArrayAccess(1), JsonError.ObjectAccess("items"))
)
// res0: String = ".items[1](missing)"
```

No decoder in the library adds a `SumType` step today.
Errors inside a sum type case show the case name as an ordinary `.Name` object key, because the default encoding wraps the case in an object.
With a discriminator field, the case name is added as a message step instead, so the path reads `(Circle).radius(expected a Double)`.
[Sum Types](#sum-types) shows both.

## Where Errors Appear

`decodeJson`, `fromJson` and `fromJsonAST` return `Either[String, A]`.
The string is the rendered trace.
Two failures are not part of the trace and return a fixed string: `Unexpected end of input` when the input stops in the middle of a value, and `Unexpected structure` when the decoder runs out of stack on deeply nested input.

Code that implements `JsonDecoder#unsafeDecode` by hand reports an error with `Lexer.error(msg, trace)`.
It throws `JsonDecoder.UnsafeJson` with `Message(msg)` added to the front of the trace, and the public methods catch it and render it.
`UnsafeJson` is a case class with one field, `trace: List[JsonError]`, and it has no stack trace.
Application code should not call `JsonDecoder#unsafeDecode` directly.
[Writing a Decoder by Hand](json-decoder.md#writing-a-decoder-by-hand) covers the rules.

### Streaming on the JVM

The streaming decoders live in the JVM module only.
They fail the `ZIO` or `ZStream` with a plain `java.lang.Exception` instead of returning a `Left`.
The exception message is the rendered trace, so it looks the same as the string from `decodeJson`.

- `decodeJsonStream` and `decodeJsonStreamInput` fail with `new Exception(JsonError.render(trace))`.
- `decodeJsonPipeline` fails the stream with the same exception at the first element that does not decode. Elements after it are not emitted, and values decoded in the same batch before it can be lost as well. See [Errors in a Decoding Pipeline](streaming.md#errors-in-a-decoding-pipeline).
- When `decodeJsonStream` or `decodeJsonStreamInput` hits the end of the input, the message is `unexpected end of input` in lower case, unlike `Unexpected end of input` from `decodeJson`.

See [Streaming API](streaming.md) for how to decode streams.

## Common Messages

The table lists the messages in the order a decoder tends to meet them.
Each one is shown in the parentheses of a full error, for example `.age(expected an Int)`.
When the failure is at the top level, the path is empty and only the parentheses remain.

| Message                                       | Produced when                                                                                                                                                                                                                                                      |
|-----------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `missing`                                     | A required field is absent. Appears as `.field(missing)`. Any decoder that has no value for an absent field reports it.                                                                                                                                            |
| `duplicate`                                   | A derived case class sees the same key twice. Shown at the object's path, without the key name. The `Either` decoder reports it at the key, for example `.Left(duplicate)`. `Map` decoders do not check this: the last value wins.                                 |
| `invalid extra field`                         | An unknown key arrives and extra fields are not allowed. Shown at the object's path, without the key name.                                                                                                                                                         |
| `expected object`                             | A case class, or a sum type with a discriminator field, is read from a `Json` value that is not an object. From text, the error is `expected '{' got '...'` instead.                                                                                               |
| `expected 'null'`                             | A value starts with `n` but is not `null`, where an `Option` or a field with a default value is read.                                                                                                                                                              |
| `invalid disambiguator`                       | The sum type tag names no known case.                                                                                                                                                                                                                              |
| `missing hint 'type'`                         | A sum type with a discriminator field gets an object without that field. The quoted name is the discriminator field's name.                                                                                                                                        |
| `invalid enumeration value`                   | A case object string names no known case.                                                                                                                                                                                                                          |
| `expected non-empty object`                   | A sum type is read from `{}`.                                                                                                                                                                                                                                      |
| `expected single field object`                | Scala 2: a wrapper-encoded sum type is read from a `Json.Obj` that does not have exactly one key.                                                                                                                                                                  |
| `expected object with a single field`         | Scala 3: the same case as `expected single field object`.                                                                                                                                                                                                          |
| `expected one of: A, B`                       | A Scala 3 union of string literals gets any other string.                                                                                                                                                                                                          |
| `expected an Int`                             | A number or string is not an `Int`. Same shape for `Long`, `Double`, and so on.                                                                                                                                                                                    |
| `expected a Boolean`                          | The value is not `true` or `false`.                                                                                                                                                                                                                                |
| `expected a 256-bit BigInt`                   | An integer needs more than 256 bits. `BigInteger` says `expected a 256-bit BigInteger`.                                                                                                                                                                            |
| `expected a BigDecimal with 256-bit mantissa` | A decimal's digits need more than 256 bits.                                                                                                                                                                                                                        |
| `expected string`                             | A string is expected and another kind of value arrives.                                                                                                                                                                                                            |
| `expected single character string`            | A `Char` is read from a string that does not hold exactly one character.                                                                                                                                                                                           |
| `expected a UUID`                             | A string is not a valid `UUID`. Same shape for `Instant`, `LocalDate`, and so on, and for a `UUID` map key.                                                                                                                                                        |
| `Invalid Int: k`                              | A map key `k` is not an `Int`. `Long` keys say `Invalid Long: k`. Keys longer than 50 characters are cut off with `...`.                                                                                                                                           |
| `Chunk was empty`                             | A `NonEmptyChunk` is read from `[]`.                                                                                                                                                                                                                               |
| `missing fields`                              | An `Either` is read from an object that has none of the keys `a`, `Left`, `left`, `b`, `Right`, `right`.                                                                                                                                                           |
| `ambiguous either, zip present`               | An `Either` is read from an object that has both a left key and a right key.                                                                                                                                                                                       |
| ``missing case in `peekChar` for 'x''``       | A decoder built with `JsonDecoder.peekChar` has no case for the character `x`.                                                                                                                                                                                     |
| `Expected array of size 2`                    | A tuple is read from a `Json.Arr` of the wrong length. The number is the tuple's size. From text, a short array gives `expected ',' got ']'`.                                                                                                                      |
| `Not an object`                               | A `Json.Obj` is read from another kind of `Json` value. `Json.Arr`, `Json.Num`, `Json.Str` and `Json.Bool` say `Not an array`, `Not a number`, `Not a string value` and `Not a bool value`. A `Chunk` read from a non-array `Json` value also says `Not an array`. |
| `expected '{' got '['`                        | The wrong opening character. The `got` part is the character read.                                                                                                                                                                                                 |
| `expected ',' or '}' got '"'`                 | Two members are not separated by a comma.                                                                                                                                                                                                                          |
| `expected ',' or ']' got '2'`                 | Two array elements are not separated by a comma.                                                                                                                                                                                                                   |
| `unexpected '?'`                              | A value starts with a character that cannot start JSON, for example in a `Json` value or in the value of an unknown field that is being skipped.                                                                                                                   |
| `invalid control in string`                   | A raw control character such as a newline sits inside a string.                                                                                                                                                                                                    |
| `invalid '\q' in string`                      | A string has an unknown escape sequence.                                                                                                                                                                                                                           |
| `invalid charcode in string`                  | A `\u` escape is not followed by four hexadecimal digits.                                                                                                                                                                                                          |
| `Unexpected end of input`                     | The input ends in the middle of a value.                                                                                                                                                                                                                           |
| `Unexpected structure`                        | Nesting is so deep that the decoder overflows the stack.                                                                                                                                                                                                           |

`invalid extra field` and the enumeration and sum type messages come from derived codecs.
`missing` comes from any decoder asked for an absent field, and `duplicate` also from the `Either` decoder.
The `expected ...` messages come from the lexer and the built-in decoders.
A message you return from `mapOrFail` ends up in the same position, in parentheses.

Decoding from a `Json` value with `fromJsonAST` or `Json#as` uses slightly different wording for some types: `expected a Int` instead of `expected an Int`, and `expected boolean` instead of `expected a Boolean`.

## Error Paths

Apart from the two fixed strings described in [Where Errors Appear](#where-errors-appear), an error string is the path to the value that failed, followed by the message in parentheses.
The examples in this section decode an order with a list of items:

```scala
import zio.json._

case class Item(name: String, qty: Int)
case class Order(id: Int, items: List[Item])

object Item {
  implicit val codec: JsonCodec[Item] = DeriveJsonCodec.gen[Item]
}

object Order {
  implicit val codec: JsonCodec[Order] = DeriveJsonCodec.gen[Order]
}
```

The second item has a string where a number belongs:

```scala
"""{"id":1,"items":[{"name":"a","qty":1},{"name":"b","qty":"two"}]}""".fromJson[Order]
// res2: Either[String, Order] = Left(value = ".items[1].qty(expected an Int)")
```

Read `.items[1].qty(expected an Int)` from left to right: the `items` field, its element at index 1, the `qty` field, and then the failure.

A missing field is reported the same way:

```scala
"""{"id":1,"items":[{"name":"a"}]}""".fromJson[Order]
// res3: Either[String, Order] = Left(value = ".items[0].qty(missing)")
```

Syntax errors have no useful path, because the input is not valid JSON at that point:

```scala
"""{"id":1 "items":[]}""".fromJson[Order]
// res4: Either[String, Order] = Left(value = "(expected ',' or '}' got '\"')")

"""{"id":1,"items":[""".fromJson[Order]
// res5: Either[String, Order] = Left(value = "Unexpected end of input")
```

### Sum Types

A sum type adds the case name to the path, and how it shows depends on the encoding.
The default wrapper encoding nests the case in an object, and a discriminator field puts the case name next to the other fields:

```scala
sealed trait Fruit
final case class Banana(curvature: Double) extends Fruit
final case class Apple(poison: Boolean)    extends Fruit

object Fruit {
  implicit val decoder: JsonDecoder[Fruit] = DeriveJsonDecoder.gen[Fruit]
}

@jsonDiscriminator("type")
sealed trait Shape
final case class Circle(radius: Double) extends Shape
final case class Square(side: Double)   extends Shape

object Shape {
  implicit val decoder: JsonDecoder[Shape] = DeriveJsonDecoder.gen[Shape]
}
```

With the wrapper encoding the case name is an object key, `.Banana`, and with a discriminator it is a message step, `(Circle)`:

```scala
"""{"Banana":{"curvature":"bent"}}""".fromJson[Fruit]
// res6: Either[String, Fruit] = Left(
//   value = ".Banana.curvature(expected a Double)"
// )

"""{"type":"Circle","radius":"big"}""".fromJson[Shape]
// res7: Either[String, Shape] = Left(
//   value = "(Circle).radius(expected a Double)"
// )

"""{"radius":1.0}""".fromJson[Shape]
// res8: Either[String, Shape] = Left(value = "(missing hint 'type')")
```

### Map Keys and Values

A map value is reported under its key, and so is a key that its `JsonFieldDecoder` rejects:

```scala
"""{"a":1,"b":"two"}""".fromJson[Map[String, Int]]
// res9: Either[String, Map[String, Int]] = Left(value = ".b(expected an Int)")

"""{"1":1,"two":2}""".fromJson[Map[Int, Int]]
// res10: Either[String, Map[Int, Int]] = Left(
//   value = ".two(Invalid Int: two)"
// )
```

### Decoding from the AST

`Json#as` and `fromJsonAST` report the same paths when they decode an already parsed [`Json`](json-ast.md) value:

```scala
import zio.json.ast.Json

Json.Obj("id" -> Json.Num(1), "items" -> Json.Arr(Json.Obj("name" -> Json.Str("a")))).as[Order]
// res11: Either[String, Order] = Left(value = ".items[0].qty(missing)")
```

### Only the First Error

The decoder stops at the first error.
It does not collect every problem in the input, so fix one and decode again.
