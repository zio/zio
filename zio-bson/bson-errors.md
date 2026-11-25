# Errors and BsonTrace

> When decoding fails, ZIO Bson reports the reason and the location of the failing value. The location is a path such as `.address.postalCode` or `.tags[2]`, built from `BsonTrace` steps. This page describes the error type, the path format, and the messages you can expect.

## Overview

## BsonDecoder.Error

Every decoding failure is a `BsonDecoder.Error`, a case class with two fields:

```scala
case class Error(trace: List[BsonTrace], message: String) extends RuntimeException with NoStackTrace
```

`trace` lists the steps from the root to the failing value, with the most recent step first. `message` describes the problem. The `render` method joins both into one string in the form `<path>: <message>`. The exception message itself is different: it reads `Path: <path>, error: <message>` and ends with a hint not to use the `Unsafe` methods. The error has no stack trace.

`BsonDecoder#fromBsonValue` and `BsonDecoder#decode` return `Either[BsonDecoder.Error, A]`, so we can inspect the trace. The `as` syntax from the `zio.bson` package returns `Either[String, A]`, where the `String` is `render` applied to the error. The next example shows the rendered form, with a field name in the path:

```scala
import zio.bson._
import zio.bson.BsonBuilder._
import zio.bson.magnolia._

final case class Banana(curvature: Double)

object Banana {
  implicit val codec: BsonCodec[Banana] = DeriveBsonCodec.derive
}
```

```scala
println(doc("curvature" -> Array[Byte](1, 2, 3).toBsonValue).as[Banana])
// Left(.curvature: Expected BSON type Double, but got BINARY.)
```

The message says that the `Double` decoder received a `BINARY` value at the path `.curvature`.

Using `fromBsonValue` gives the structured error instead:

```scala
val document = doc("curvature" -> bool(true))
// document: org.bson.BsonDocument = {"curvature": true}
println(BsonDecoder[Banana].fromBsonValue(document).left.map(_.trace))
// Left(List(Field(curvature)))
```

## BsonTrace

`BsonTrace` is a sealed trait with two cases:

- `BsonTrace.Field(name)` stands for a field of a document. It renders as `.name`.
- `BsonTrace.Array(idx)` stands for an element of an array, counting from zero. It renders as `[idx]`.

`BsonTrace.render` turns a list of steps into the path string:

```scala
val steps: List[BsonTrace] = List(BsonTrace.Array(2), BsonTrace.Field("tags"))
```

```scala
println(BsonTrace.render(steps))
// .tags[2]
```

The list is stored with the most recent step first, which is why `render` reverses it before printing. Here `Field("tags")` is the outer step and `Array(2)` is the element inside it, so the path reads `.tags[2]`.

An array element error shows the index in the path:

```scala
println(array(int(1), bool(true)).as[List[Int]])
// Left([1]: Expected BSON type Int, but got BOOLEAN.)
```

## Common Messages

The table lists the messages most often seen, with the decoder that produces them.

| Message                                                    | Produced by                                   |
| ---------------------------------------------------------- | --------------------------------------------- |
| `Expected BSON type <Type>, but got <BSON type>.`          | Any decoder that receives the wrong BSON type |
| `missing`                                                  | A required field that is absent               |
| `Invalid extra field.`                                     | A case class with unknown fields and extra fields disallowed |
| `Missing disambiguator <name>.`                            | A sealed trait with a missing discriminator   |
| `Invalid disambiguator <name>.`                            | A sealed trait with an unknown subtype name   |
| `Could not convert <BSON type>(<value>) to a <Type> without losing precision.` | A numeric value that does not fit the target type |
| `Expected one character, but got a string of length <n>.`  | `Char` decoder                                |
| `Invalid UUID: <reason>`                                   | `UUID` decoder                                |

The `missing` message comes from a field that is not present and has no default value. The example below derives a codec for a case class and omits the `y` field:

```scala
import zio.bson.magnolia._

final case class Point(x: Int, y: Int)

object Point {
  implicit val codec: BsonCodec[Point] = DeriveBsonCodec.derive
}
```

```scala
println(doc("x" -> int(1)).as[Point])
// Left(.y: missing)
```

The path `.y` names the missing field. Derivation is described in [BsonCodecConfiguration](./bson-codec-configuration.md).

## Reading an Error in Code

Use the `trace` and `message` fields when you need to act on the failing location, such as logging the path with the reason:

```scala
val result = BsonDecoder[Int].fromBsonValue(bool(false))
// result: Either[BsonDecoder.Error, Int] = Left(
//   value = Error(
//     trace = List(),
//     message = "Expected BSON type Int, but got BOOLEAN."
//   )
// )

result match {
  case Left(err) => println(s"path=${BsonTrace.render(err.trace)} message=${err.message}")
  case Right(v)  => println(v)
}
// path= message=Expected BSON type Int, but got BOOLEAN.
```

## Related Pages

- [BsonDecoder](./bson-decoder.md) describes the methods that return these errors.
- [Type Mappings](./types.md) lists the BSON types each decoder accepts.
