# Refined Interop

> Encode and decode refined types with ZIO JSON: the predicate is checked while decoding, and refined types also work as map keys.

The `zio-json-interop-refined` module provides ZIO JSON instances for [refined](https://github.com/fthomas/refined) types. A refined type `A Refined P` is a value of type `A` that is known to satisfy the predicate `P`, for example `String Refined NonEmpty`. Encoding writes the underlying `A`. Decoding reads an `A` and then checks the predicate, so a payload that breaks it fails to decode.

## Installation

Add the module to your `build.sbt`:

```scala
libraryDependencies += "dev.zio" %% "zio-json-interop-refined" % "1.1.3"
```

The module is published for the JVM, Scala.js and Scala Native, for Scala 2.12, 2.13 and 3. In a Scala.js or Scala Native project, use `%%%` instead of `%%`.

## Instances

One import, `zio.json.interop.refined._`, brings four implicit instances into scope:

| Instance             | Type                            | Requires                                             |
| -------------------- | ------------------------------- | ---------------------------------------------------- |
| `encodeRefined`      | `JsonEncoder[A Refined P]`      | `JsonEncoder[A]`                                     |
| `decodeRefined`      | `JsonDecoder[A Refined P]`      | `JsonDecoder[A]` and refined's `Validate[A, P]`      |
| `encodeFieldRefined` | `JsonFieldEncoder[A Refined P]` | `JsonFieldEncoder[A]`                                |
| `decodeFieldRefined` | `JsonFieldDecoder[A Refined P]` | `JsonFieldDecoder[A]` and refined's `Validate[A, P]` |

The decoders use refined's `refineV`, so the error message is the one refined produces for the failed predicate. The field instances let a refined type be the key of a `Map`; see [Map Keys](../reference/json-field-codecs.md) for how keys are encoded.

## Encoding and Decoding a Case Class

With the import in place, a case class whose fields are refined types derives a full codec like any other case class:

```scala
import zio.json._
import zio.json.interop.refined._

import eu.timepit.refined.api.Refined
import eu.timepit.refined.auto._
import eu.timepit.refined.collection.NonEmpty
import eu.timepit.refined.numeric.Positive

final case class Person(name: String Refined NonEmpty, age: Int Refined Positive)

object Person {
  implicit val codec: JsonCodec[Person] = DeriveJsonCodec.gen[Person]
}
```

Encoding writes the plain values. The `eu.timepit.refined.auto._` import lets us write the literals `"Aurora"` and `32` where refined types are expected, checking them at compile time:

```scala
Person("Aurora", 32).toJson
// res1: String = "{\"name\":\"Aurora\",\"age\":32}"
```

:::note
On Scala 3, refined's `auto` conversion of literals is not available, so `Person("Aurora", 32)` does not compile there. Build the refined values with `refineV` instead, for example `refineV[NonEmpty]("Aurora")`, which returns an `Either`. The ZIO JSON instances from this module work the same on both versions.
:::

Decoding valid input succeeds:

```scala
"""{ "name": "Aurora", "age": 32 }""".fromJson[Person]
// res2: Either[String, Person] = Right(
//   value = Person(name = Aurora, age = 32)
// )
```

When a value breaks its predicate, decoding fails with refined's message and the path of the field:

```scala
"""{ "name": "", "age": 32 }""".fromJson[Person]
// res3: Either[String, Person] = Left(
//   value = ".name(Predicate isEmpty() did not fail.)"
// )

"""{ "name": "Aurora", "age": 0 }""".fromJson[Person]
// res4: Either[String, Person] = Left(
//   value = ".age(Predicate failed: (0 > 0).)"
// )
```

## Refined Types as Map Keys

`encodeFieldRefined` and `decodeFieldRefined` make a refined type usable as a `Map` key, as long as the underlying type has a key codec (`String`, `Int`, `Long` and `UUID` have built-in ones). Here the keys are stock-keeping unit codes that must match a pattern:

```scala
import eu.timepit.refined.string.MatchesRegex

type Sku = String Refined MatchesRegex["[A-Z]{3}-[0-9]{4}"]

val stock: Map[Sku, Int] = Map(("ABC-1234": Sku) -> 5)
```

The key is written as its plain string:

```scala
stock.toJson
// res5: String = "{\"ABC-1234\":5}"
```

Decoding checks every key, and a key that does not match fails with the key in the error path:

```scala
"""{ "ABC-1234": 5 }""".fromJson[Map[Sku, Int]]
// res6: Either[String, Map[Sku, Int]] = Right(value = Map(ABC-1234 -> 5))

"""{ "abc": 5 }""".fromJson[Map[Sku, Int]]
// res7: Either[String, Map[Sku, Int]] = Left(
//   value = ".abc(Predicate failed: \"abc\".matches(\"[A-Z]{3}-[0-9]{4}\").)"
// )
```

## See Also

- [Validate on Decode](../how-to/validate-on-decode.md) shows other ways to reject values while decoding, such as `JsonDecoder#mapOrFail`.
- [Manual Instances](../how-to/manual-instances.md) explains how to write a decoder for a refined type yourself.
