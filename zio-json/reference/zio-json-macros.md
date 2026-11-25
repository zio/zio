# @jsonDerive Macro Annotation

> Reference for the zio-json-macros module: installing it, what @jsonDerive generates, JsonDeriveConfig, generic classes and the Scala 3 alternative.

`@jsonDerive` is a macro annotation from the `zio-json-macros` module. Placed on a case class or a sealed trait, it adds an implicit codec (or only an encoder, or only a decoder) to the companion object at compile time, so you do not write the `DeriveJsonCodec.gen` line yourself. It is available on Scala 2.12 and 2.13 only; on Scala 3, a `derives` clause does the same job.

The module's public API is one annotation class and one configuration type:

```scala
class jsonDerive(val config: JsonDeriveConfig = JsonDeriveConfig.Codec) extends scala.annotation.StaticAnnotation

sealed abstract class JsonDeriveConfig

object JsonDeriveConfig {
  case object Codec   extends JsonDeriveConfig
  case object Encoder extends JsonDeriveConfig
  case object Decoder extends JsonDeriveConfig
}
```

## Installation

The module is published for Scala 2.12 and 2.13, on the JVM, Scala.js and Scala Native. There is no Scala 3 artifact. Add it next to `zio-json`, and turn on macro annotations in the compiler.

On Scala 2.13, macro annotations are a compiler option:

```scala
libraryDependencies += "dev.zio" %% "zio-json-macros" % "1.1.3"
scalacOptions += "-Ymacro-annotations"
```

On Scala 2.12, they need the macro paradise compiler plugin instead:

```scala
libraryDependencies += "dev.zio" %% "zio-json-macros" % "1.1.3"
addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.1" cross CrossVersion.full)
```

For Scala.js or Scala Native, use `%%%` instead of `%%`.

## Usage

With no argument, `@jsonDerive` generates a `JsonCodec`, so the class can be encoded and decoded right away:

```scala
import zio.json._

@jsonDerive final case class Watermelon(pips: Int)

Watermelon(3).toJson
// res1: String = "{\"pips\":3}"

"""{"pips":5}""".fromJson[Watermelon]
// res2: Either[String, Watermelon] = Right(value = Watermelon(pips = 5))
```

The generated code calls the usual derivation macros, so every [annotation](./annotations.md) on the class and an implicit [`JsonCodecConfiguration`](./json-codec-configuration.md) in scope apply as they would to a hand-written `DeriveJsonCodec.gen` call.

## What `@jsonDerive` Generates

The macro adds one `implicit` member to the companion object of the annotated type. If the type has no companion, the macro creates one; if it has one, the member is added next to the existing definitions. The member name is a prefix followed by the type name:

| `config`                   | Generated member                                                         |
|----------------------------|--------------------------------------------------------------------------|
| `JsonDeriveConfig.Codec`   | `implicit val codecFor<Type>: JsonCodec[<Type>] = DeriveJsonCodec.gen`   |
| `JsonDeriveConfig.Encoder` | `implicit val encode<Type>: JsonEncoder[<Type>] = DeriveJsonEncoder.gen` |
| `JsonDeriveConfig.Decoder` | `implicit val decode<Type>: JsonDecoder[<Type>] = DeriveJsonDecoder.gen` |

So `@jsonDerive final case class Watermelon(pips: Int)` is the same as writing:

```scala
import zio.json._

final case class Watermelon(pips: Int)

object Watermelon {
  implicit val codecForWatermelon: JsonCodec[Watermelon] =
    DeriveJsonCodec.gen[Watermelon]
}
```

The names matter when you refer to the instance explicitly, and when you define a member with the same name in the companion, which then fails to compile as a duplicate definition. The generated codec is an ordinary member:

```scala
Watermelon.codecForWatermelon.encodeJson(Watermelon(1), None)
// res4: CharSequence = "{\"pips\":1}"
```

## Choosing What to Generate

The `config` argument chooses what to generate: `JsonDeriveConfig.Codec` (the default), `JsonDeriveConfig.Encoder` for an encoder only, or `JsonDeriveConfig.Decoder` for a decoder only. It can be passed by position or by name. Write it with its full package, as below. The macro type-checks the argument on its own, before the surrounding code, so it does not see an `import zio.json._` that sits in the same class body or block; the expansion then fails with `not found: value JsonDeriveConfig`. An import at the top of the file works too.

A type with only an encoder can be written but not read:

```scala
@jsonDerive(zio.json.JsonDeriveConfig.Encoder) final case class Melon(pips: Int)

@jsonDerive(config = zio.json.JsonDeriveConfig.Decoder) final case class Gourd(pips: Int)

Melon(2).toJson
// res5: String = "{\"pips\":2}"

"""{"pips":1}""".fromJson[Gourd]
// res6: Either[String, Gourd] = Right(value = Gourd(pips = 1))
```

These two classes get the members `Melon.encodeMelon` and `Gourd.decodeGourd`.

The macro decides what to generate from the static type of the argument, at compile time. If the type is not one of the three `JsonDeriveConfig` objects, for example a `val` typed as `JsonDeriveConfig`, the compiler prints a warning and the macro generates a full codec:

```text
warning: Couldn't determine type of configuration zio.json.JsonDeriveConfig; will produce both encoder and decoder
```

Pass one of the three objects directly so that the choice is the one you intended.

## Sealed Traits

On a sealed trait or sealed abstract class, the generated codec covers the whole family. Subtypes without a codec of their own are derived along with it, and the sum-type annotations such as `@jsonDiscriminator` and `@jsonHint` work as usual. The subtypes come first here, for the reason given below:

```scala
final case class Apple(radius: Int) extends Fruit

@jsonHint("pear")
case object Pear extends Fruit

@jsonDerive
@jsonDiscriminator("kind")
sealed trait Fruit

(Apple(3): Fruit).toJson
// res7: String = "{\"kind\":\"Apple\",\"radius\":3}"

"""{"kind":"pear"}""".fromJson[Fruit]
// res8: Either[String, Fruit] = Right(value = Pear)
```

The generated companion sits right after the trait. At the top level of a file, or in an object at the top level, the subtypes can follow the trait as usual. Inside a class body, a method, or an object nested in a class, the derivation then runs before the compiler has registered the subtypes, and compilation fails with `magnolia: could not find any direct subtypes of trait Fruit`. In those places, define the subtypes before the trait, as in the example above.

## Generic Classes

For a class with type parameters, the macro generates an `implicit def` instead of an `implicit val`. It takes, for every type parameter `A`, an implicit `JsonEncoder[A]` (encoder or codec) and an implicit `JsonDecoder[A]` (decoder or codec). The codec for `Pair[A, B]` is roughly:

```scala
object Pair {
  implicit def codecForPair[A, B](implicit
    decode0: JsonDecoder[A],
    decode1: JsonDecoder[B],
    encode0: JsonEncoder[A],
    encode1: JsonEncoder[B]
  ): JsonCodec[Pair[A, B]] = DeriveJsonCodec.gen[Pair[A, B]]
}
```

Any element types that have an encoder and a decoder then work:

```scala
@jsonDerive final case class Pair[A, B](first: A, second: B)

Pair(1, "one").toJson
// res9: String = "{\"first\":1,\"second\":\"one\"}"

"""{"first":2,"second":"two"}""".fromJson[Pair[Int, String]]
// res10: Either[String, Pair[Int, String]] = Right(
//   value = Pair(first = 2, second = "two")
// )
```

The macro copies the type parameters, variance annotations included, into the generated method. A method cannot declare a covariant or contravariant type parameter, so `@jsonDerive final case class Box[+A](value: A)` does not compile. The error is reported on the annotation:

```text
error: covariant type A occurs in invariant position in type [+A](implicit decode0: zio.json.JsonDecoder[A], encode0: zio.json.JsonEncoder[A]): zio.json.JsonCodec[Box[A]] of method codecForBox
```

For such a class, write the instance by hand in the companion object:

```scala
final case class Crate[+A](value: A)

object Crate {
  implicit def codec[A: JsonEncoder: JsonDecoder]: JsonCodec[Crate[A]] =
    DeriveJsonCodec.gen[Crate[A]]
}

Crate(List(1, 2)).toJson
// res11: String = "{\"value\":[1,2]}"
```

## Limitations

Compilation stops with an error in these cases. The macro reports the first two itself, after checking its target and its own name; the compiler reports the third:

| Situation                                                          | Compile error                                                             |
|--------------------------------------------------------------------|---------------------------------------------------------------------------|
| The annotated class is neither a case class nor sealed             | `Invalid annotation target: must be a case class or a sealed trait/class` |
| The annotation is written with its package, `@zio.json.jsonDerive` | `Unsupported macroname supplied to @zio.json.jsonDerive`                  |
| A type parameter has a variance annotation                         | `covariant type A occurs in invariant position ...`                       |

Import the annotation (`import zio.json._` or `import zio.json.jsonDerive`) and write it as `@jsonDerive`.

## Scala 3 Alternative

`zio-json-macros` is not published for Scala 3, because Scala 3 has no macro annotations. On Scala 3, add a `derives` clause to the type instead. `derives JsonCodec`, `derives JsonEncoder` and `derives JsonDecoder` match the three `JsonDeriveConfig` values, and they also work for classes with variant type parameters:

```scala
final case class Watermelon(pips: Int) derives JsonCodec
final case class Melon(pips: Int) derives JsonEncoder
final case class Gourd(pips: Int) derives JsonDecoder
final case class Box[+A](a: A) derives JsonCodec
```

Run on Scala 3, `Box(List(1, 2)).toJson` writes `{"a":[1,2]}` and `"""{"a":"x"}""".fromJson[Box[String]]` returns `Right(Box(x))`. For code that cross-builds for Scala 2 and Scala 3, a hand-written `implicit val codec: JsonCodec[T] = DeriveJsonCodec.gen[T]` in the companion works on both versions. See [Derive codecs on Scala 3](../how-to/scala-3-derivation.md) for `derives`, enums and the other Scala 3 features.
