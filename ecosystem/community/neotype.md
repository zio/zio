# Neotype

> [Neotype](https://github.com/kitlangton/neotype) is a friendly newtype library for Scala 3. It validates values at compile time using plain Scala expressions, and it integrates with several ZIO libraries.

## Introduction

Key features of Neotype:
- **Compile-Time Validation** — Values are checked at compile time using plain Scala expressions, with helpful compilation errors.
- **Zero Runtime Overhead** — Newtypes are built with `inline` and `opaque type`.
- **Runtime Validation** — The `make` and `makeOrThrow` methods validate values that are only known at runtime.
- **Integrations** — Neotype provides codecs and instances for popular libraries, including ZIO JSON, ZIO Quill, ZIO Config, ZIO Schema, and ZIO Test.
- **Comptime** — An optional `comptime` module evaluates expressions at compile time and inlines the results as literals.

## Installation

In order to use this library, we need to add the following line to our `build.sbt` file, using the latest version:

```scala
libraryDependencies += "io.github.kitlangton" %% "neotype" % "0.7.2"
```

The optional compile-time evaluator is a separate artifact:

```scala
libraryDependencies += "io.github.kitlangton" %% "comptime" % "0.7.2"
```

## Example

We define a newtype by extending `Newtype`, and optionally override `validate`. Constructing a value from an invalid literal is a compile error:

```scala
import neotype.*

object NonEmptyString extends Newtype[String]:
  override inline def validate(input: String): Boolean =
    input.nonEmpty

NonEmptyString("Hello") // OK
NonEmptyString("")      // Compile Error
```

Newtypes also prevent mixing up values of the same underlying type, such as the IDs of different entities:

```scala
type UserId = UserId.Type
object UserId extends Newtype[Long]

type OrderId = OrderId.Type
object OrderId extends Newtype[Long]

def getUser(id: UserId): User = ???

getUser(UserId(123))   // Compiles
getUser(OrderId(456))  // Doesn't compile: type mismatch
```

## ZIO JSON

By importing `neotype.interop.ziojson.given`, Neotype generates a `JsonCodec` for the newtype, and the validation runs when decoding:

```scala
import neotype.interop.ziojson.given
import zio.json.*

type NonEmptyString = NonEmptyString.Type
object NonEmptyString extends Newtype[String]:
  override inline def validate(value: String): Boolean | String =
    if value.nonEmpty then true else "String must not be empty"

case class Person(name: NonEmptyString, age: Int) derives JsonCodec

val parsed = """{"name": "Kit", "age": 30}""".fromJson[Person]
// Right(Person(NonEmptyString("Kit"), 30))

val failed = """{"name": "", "age": 30}""".fromJson[Person]
// Left(".name(String must not be empty)")
```

The import has to be in the same file as `Person`, not in the file of `NonEmptyString`. The generated codec is only available in the file that imports it.

The other ZIO integrations are for ZIO Quill, ZIO Config, ZIO Schema, and `DeriveGen` of ZIO Test. See the [Neotype README](https://github.com/kitlangton/neotype) for the complete list of integrations.
