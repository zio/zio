# Transforming Schemas

> Using the `Schema#transform` method, we can transform a `Schema[A]` into a `Schema[B]` by supplying two functions that can transform between `A` and `B`.  In normal Scala code this would be the equivalent of `map`, but with isomorphism property.

Using the `Schema#transform` method, we can transform a `Schema[A]` into a `Schema[B]` by supplying two functions that can transform between `A` and `B`.  In normal Scala code this would be the equivalent of `map`, but with isomorphism property.

```scala
object Schema {
  def transform[B](f: A => B, g: B => A): Schema[B] = ???
}
```

Therefore, if we have a schema for `A`, and isomorphism between `A` and `B`, we can derive a schema for `B` in terms of `Schema[A].

:::note
In type theory, isomorphism refers to a relationship between two types that have a bijective correspondence or mapping between their elements. More specifically, if two types, let's say Type `A` and Type `B`, are isomorphic, it means that there exists a pair of functions—one going from A to B (often called the forward function) and another going from B to A (often called the backward function)—that satisfy certain properties.
:::

In ZIO Schema this is modelled by the `Transform` type class:

```scala
object Schema {
  final case class Transform[A, B](
      codec: Schema[A],
      f: A => Either[String, B],
      g: B => Either[String, A]
    ) extends Schema[B]
}  
```

For example, assume we have a wrapper class `Age` that wraps an `Int` value, and it has some validation logic, e.g. the age must be between 0 and 120. We can define a `Schema[Age]` by using the `Schema.transform` method:

```scala

case class Age(i: Int)

object Age {
  implicit val schema: Schema[Age] =
    Schema[Int].transformOrFail(
      (i: Int) =>
        if (i >= 0 && i <= 120)
          Right(Age(i))
        else
          Left("Age must be between 1 and 120"),
      (age: Age) => Right(age.i)
    )
}
```
