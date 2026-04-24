# Diffing and Patching

> ZIO Schema provides two methods called `diff` and `patch`:

ZIO Schema provides two methods called `diff` and `patch`:

```scala
sealed trait Schema[A] {
  def diff(thisValue: A, thatValue: A): Patch[A]

  def patch(oldValue: A, diff: Patch[A]): scala.util.Either[String, A]
} 
```

The `diff` method takes two values of the same type `A` and returns a `Patch[A]` value that describes the differences between the two values. conversely, the `patch` method takes a value of type `A` and a `Patch[A]` value and returns a new value of type `A` that is the result of applying the patch to the original value.

Here is a simple example that demonstrate the how to use `diff` and `patch`:

```scala

case class Person(name: String, age: Int)

object Person {
  implicit val schema: Schema[Person] = DeriveSchema.gen[Person]
}

val oldValue = Person("John", 42)
val newValue = Person("John", 43)

val patch: Patch[Person] =
  Person.schema.diff(oldValue, newValue)

assert(
  Person.schema.patch(oldValue, patch) == Right(newValue)
)
```
