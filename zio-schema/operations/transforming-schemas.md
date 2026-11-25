# Transforming Schemas

> Using the `Schema#transform` method, we can transform a `Schema[A]` into a `Schema[B]` by supplying two functions that convert between `A` and `B`. In normal Scala code this would be the equivalent of `map`, but the conversion has to work in both directions, because a schema both produces and consumes values. When a conversion can fail, `Schema#transformOrFail` takes two functions that return an `Either` instead:

```scala
sealed trait Schema[A] {
  def transform[B](f: A => B, g: B => A)(implicit loc: SourceLocation): Schema[B]

  def transformOrFail[B](
    f: A => Either[String, B],
    g: B => Either[String, A]
  )(implicit loc: SourceLocation): Schema[B]
}
```

Therefore, if we have a schema for `A`, and a pair of conversions between `A` and `B`, we can derive a schema for `B` in terms of `Schema[A]`. The compiler fills in the implicit `SourceLocation` parameter with the place in the source code where the method is called.

:::note
In type theory, isomorphism refers to a relationship between two types that have a bijective correspondence or mapping between their elements. More specifically, if two types, let's say Type `A` and Type `B`, are isomorphic, it means that there exists a pair of functions—one going from A to B (often called the forward function) and another going from B to A (often called the backward function)—that satisfy certain properties.
:::

## The `Transform` Schema

Both methods return a `Schema.Transform`, a case class that stores the underlying schema and the two conversions:

```scala
object Schema {
  final case class Transform[A, B, I](
    schema: Schema[A],
    f: A => Either[String, B],
    g: B => Either[String, A],
    annotations: Chunk[Any],
    identity: I
  ) extends Schema[B]
}
```

`Schema#transform` wraps its plain functions so that they always succeed. The `annotations` start as a copy of the annotations of the underlying schema, and `identity` holds the `SourceLocation` of the call. Two transforms created at different places in the code are therefore different schemas, even when they wrap the same underlying schema.

## Transforming with Validation

`Schema#transformOrFail` is the right choice when only some values of `A` form a valid `B`. The forward function returns `Left` with a message for a value it rejects, and codecs report that message as a decoding error.

For example, assume we have a wrapper class `Age` that wraps an `Int` value, and the age must be between 0 and 120. We can define a `Schema[Age]` with `Schema#transformOrFail`:

```scala
import zio.schema._

case class Age(i: Int)

object Age {
  implicit val schema: Schema[Age] =
    Schema[Int].transformOrFail(
      (i: Int) =>
        if (i >= 0 && i <= 120)
          Right(Age(i))
        else
          Left("Age must be between 0 and 120"),
      (age: Age) => Right(age.i)
    )
}
```

Converting a value through `DynamicValue` runs the forward function, so an out-of-range age is rejected:

```scala
Age.schema.fromDynamic(Schema[Int].toDynamic(30))
// res0: Either[String, Age] = Right(value = Age(i = 30))
Age.schema.fromDynamic(Schema[Int].toDynamic(130))
// res1: Either[String, Age] = Left(value = "Age must be between 0 and 120")
```

## How Other Operations See a Transform

Most operations work on the underlying schema and convert with the two functions:

- `Schema#defaultValue` takes the default of the underlying schema and passes it through the forward function, so `Age.schema.defaultValue` is `Right(Age(0))`.
- `Schema#toDynamic` stores the underlying value, so a `DynamicValue` for an `Age` is a plain integer.
- `Schema#ast` describes only the underlying schema. The conversion functions cannot be serialized, so a receiver of the tree sees an `Int`.
- `Schema#validate` converts the value with the backward function and checks the underlying value.
