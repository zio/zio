# Getting Started with ZIO Prelude

> This page installs ZIO Prelude and walks through a first program that uses three of its features: an `Equal` instance, an `Associative` instance, and `Validation`.

## Installation

Add the core module to your `build.sbt` file. The [Introduction](index.md#installation) lists the Scala versions, and the optional modules for testing, derivation and more:

```scala
libraryDependencies += "dev.zio" %% "zio-prelude" % "1.0.0-RC49"
```

Then import everything with a single line:

```scala
import zio.prelude._
```

## Comparing Values Safely with Equal

The `===` operator compares two values only when they have an `Equal` instance, so comparing unrelated types is a compile error instead of a silent `false`:

```scala
final case class SequenceNumber(value: Int)

object SequenceNumber {
  implicit val equal: Equal[SequenceNumber] =
    Equal.default
}

SequenceNumber(1) === SequenceNumber(1)
// res1: Boolean = true
```

See [Equal](functional-abstractions/concrete-types/equal.md) for the full story.

## Combining Values with Associative

`Associative` describes types that have an associative way of combining two values. The `<>` operator uses it:

```scala
"Hello, " <> "world!"
// res2: String = "Hello, world!"

List(1, 2) <> List(3)
// res3: List[Int] = List(1, 2, 3)
```

If we give our own type an `Associative` instance, it works with `<>` and with the instances ZIO Prelude derives for maps and other containers:

```scala
final case class Votes(value: Int)

object Votes {
  implicit val associative: Associative[Votes] =
    new Associative[Votes] {
      def combine(left: => Votes, right: => Votes): Votes =
        Votes(left.value + right.value)
    }
}

Map("zio-http" -> Votes(4)) <> Map("zio-http" -> Votes(2), "zio-json" -> Votes(1))
// res4: Map[String, Votes] = Map(
//   "zio-http" -> Votes(value = 6),
//   "zio-json" -> Votes(value = 1)
// )
```

See [Associative](functional-abstractions/concrete-types/associative.md).

## Accumulating Errors with Validation

`Either` stops at the first error. `Validation` keeps going and reports all of them:

```scala
final case class Person(name: String, age: Int)

def validateName(name: String): Validation[String, String] =
  if (name.isEmpty) Validation.fail("Name was empty")
  else Validation.succeed(name)

def validateAge(age: Int): Validation[String, Int] =
  if (age <= 0) Validation.fail(s"Age $age was not positive")
  else Validation.succeed(age)

Validation.validateWith(validateName(""), validateAge(-1))(Person)
// res5: ZValidation[Nothing, String, Person] = Failure(
//   log = IndexedSeq(),
//   errors = NonEmptyChunk(Name was empty, Age -1 was not positive)
// )
```

See [Validation](functional-data-types/validation.md).

## Next Steps

- Read the [Concepts](explanation/index.md) to see why ZIO Prelude is organized this way.
- Follow the [tutorial](tutorial/validate-an-order.md) to build a small validated order model step by step.
- Browse the [reference](functional-abstractions/index.md) for every abstraction and data type.
- Use the [how-to guides](how-to/define-equal-ord-hash-debug-instances.md) when you have a specific task.
