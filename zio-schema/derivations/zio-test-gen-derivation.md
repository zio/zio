# Derivation of ZIO Test Generators

> ZIO Test supports property-based testing with the `Gen` type. A `Gen[R, A]` is a random generator of values of type `A`, and a property test checks that a property holds for many values that the generator produces. The `zio-schema-zio-test` module provides a way to derive a `Gen[R, A]` from a `Schema[A]`, so we do not have to write a generator for each type by hand.

## Introduction

## Installation

To derive a generator from a schema, we add the following dependency to our `build.sbt` file:

```scala
libraryDependencies += "dev.zio" %% "zio-schema-zio-test" % "1.9.2" % Test
```

## DeriveGen

The `DeriveGen` object in the `zio.schema` package provides the `DeriveGen.gen` operator. It takes a `Schema[A]` implicitly and returns a `Gen[Sized, A]`:

```scala
object DeriveGen {
  def gen[A](implicit schema: Schema[A]): Gen[Sized, A]
}
```

The `Sized` environment lets ZIO Test control the size of generated strings and sets. Sequences (`List`, `Vector`, `Chunk`) and maps do not follow it: each generated sequence or map is either empty or has exactly two elements.

## Example

To generate `Person` values, we first derive the schema, then derive the generator from it. We keep both in the companion object:

```scala
import zio.schema.{DeriveGen, DeriveSchema, Schema}
import zio.test.{Gen, Sized}

case class Person(name: String, age: Int)

object Person {
  implicit val schema: Schema[Person] = DeriveSchema.gen
  val gen: Gen[Sized, Person]         = DeriveGen.gen
}
```

Next, we write a property test that checks a round trip through the schema's dynamic representation. Every generated `Person` must survive the conversion unchanged:

```scala
import zio.test._

object PersonRoundTripSpec extends ZIOSpecDefault {
  def spec: Spec[Sized, Nothing] =
    test("a generated Person survives a DynamicValue round trip") {
      check(Person.gen) { p =>
        assertTrue(Person.schema.fromDynamic(Person.schema.toDynamic(p)) == Right(p))
      }
    }
}
```

The `check` operator runs the property with many generated values. If any value fails the assertion, ZIO Test reports the smallest failing input it finds. [How to Property-Test Codecs with DeriveGen](../how-to/property-test-codecs.md) applies the same pattern to round trips through JSON and Protobuf, and shows how to generate values that pass validation.

## Limitations

The derived generator follows the structure of the schema, not its rules, so a few kinds of schema need extra care:

- Generated values ignore `@validate` rules. Filter them with `Gen#filter` and `Schema.validate` when a property needs valid values.
- A `Transform` schema generates a value of the underlying type and then applies the transformation. When the transformation fails, that sample is dropped instead of reported, so a transformation that rejects most inputs can leave `check` with few or no samples.
- Sequences and maps have zero or two elements, as described above. Write a generator by hand when a property depends on larger collections.
- A `Schema.Fail` schema generates no values at all.

:::caution
`DeriveGen.gen` throws an exception with the message "Missing handler for generating value of schema ..." when the schema is, or contains, a `NonEmptyChunk`, a `NonEmptyMap`, or a `Fallback`. For a type that contains one of these, write its generator by hand and derive generators for the other parts. For example, `Gen.chunkOf1(DeriveGen.gen[Int])` generates `NonEmptyChunk[Int]` values.
:::
