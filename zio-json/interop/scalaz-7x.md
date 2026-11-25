# Scalaz 7.x Interop

> Encode and decode Scalaz 7 IList values as JSON arrays with ZIO JSON. Scala 2 only.

The `zio-json-interop-scalaz7x` module provides a `JsonEncoder` and a `JsonDecoder` for [Scalaz 7](https://github.com/scalaz/scalaz)'s immutable list, `IList`. An `IList` is written as a JSON array, exactly like a Scala `List`.

## Installation

Add the module to your `build.sbt`:

```scala
libraryDependencies += "dev.zio" %% "zio-json-interop-scalaz7x" % "1.1.3"
```

The module is published for Scala 2.12 and 2.13 only, on the JVM, Scala.js and Scala Native. There is no Scala 3 artifact. In a Scala.js or Scala Native project, use `%%%` instead of `%%`.

## Instances

The import `zio.json.interop.scalaz7x._` brings two implicit instances into scope:

| Instance           | Type                    | Requires         |
| ------------------ | ----------------------- | ---------------- |
| `ilistEncoder`     | `JsonEncoder[IList[A]]` | `JsonEncoder[A]` |
| `ilistJsonDecoder` | `JsonDecoder[IList[A]]` | `JsonDecoder[A]` |

Both convert through `List`: the encoder writes `IList#toList`, and the decoder reads a `List` and builds the `IList` with `IList.fromList`.

## Encoding

Encoding an `IList` produces a JSON array:

```scala
import zio.json._
import zio.json.interop.scalaz7x._

import scalaz._

IList(1, 2, 3).toJson
// res1: String = "[1,2,3]"
```

## Decoding

Decoding reads a JSON array into an `IList`. An element of the wrong type fails with its index in the error path:

```scala
"[1, 2, 3]".fromJson[IList[Int]]
// res2: Either[String, IList[Int]] = Right(
//   value = ICons(
//     head = 1,
//     tail = ICons(head = 2, tail = ICons(head = 3, tail = []))
//   )
// )

"""[1, "two"]""".fromJson[IList[Int]]
// res3: Either[String, IList[Int]] = Left(value = "[1](expected an Int)")
```

## In a Case Class

With the import in scope, case classes with `IList` fields derive their codecs like any other case class:

```scala
final case class Basket(items: IList[String])

object Basket {
  implicit val codec: JsonCodec[Basket] = DeriveJsonCodec.gen[Basket]
}

Basket(IList("apple", "pear")).toJson
// res4: String = "{\"items\":[\"apple\",\"pear\"]}"

"""{"items":["plum"]}""".fromJson[Basket]
// res5: Either[String, Basket] = Right(
//   value = Basket(items = ICons(head = "plum", tail = []))
// )
```
