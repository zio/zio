# Derivation of ZIO Test Generators

> ZIO Test supports property-based testing via the `Gen` type. `Gen[R, A]` is a random generator of values of type `A`. Such a generator can be used to produce test cases for a property, which can then be checked for validity. The `zio-schema-zio-test` module provides a way to derive a `Gen[R, A]` from a `Schema[A]`. In this section, we will see how this functionality works.

## Introduction

ZIO Test supports property-based testing via the `Gen` type. `Gen[R, A]` is a random generator of values of type `A`. Such a generator can be used to produce test cases for a property, which can then be checked for validity. The `zio-schema-zio-test` module provides a way to derive a `Gen[R, A]` from a `Schema[A]`. In this section, we will see how this functionality works.

## Installation

In order to derive a generator from a ZIO Schema, we need to add the following dependency to our `build.sbt` file:

```scala
libraryDependencies += "dev.zio" %% "zio-schema-zio-test" % 1.7.5
```

## DriveGen

The `DriveGen` inside `zio.schema` package provides the `gen` operator which takes a `Schema[A]` implicitly and returns a `Gen[Sized, A]`:

```scala
object DeriveGen {
  def gen[A](implicit schema: Schema[A]): Gen[Sized, A] = ???
}
```

## Example

In the following example, we will derive a generator for the `Person` class using the `DeriveGen.gen` operator:

```scala

case class Person(name: String, age: Int)

object Person {
  implicit val schema: Schema[Person] = DeriveSchema.gen
  val gen: Gen[Sized, Person]         = DeriveGen.gen
}

object ExampleSpec extends ZIOSpecDefault {
  def spec =
    test("example test") {
      check(Person.gen) { p =>
        assertTrue(???)
      }
    }
}
```
