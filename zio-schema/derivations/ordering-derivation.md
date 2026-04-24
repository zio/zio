# Ordering Derivation

> Standard Scala library provides a type class called `Ordering[A]` that allows us to compare values of type `A`. ZIO Schema provides a method called `ordering` that generates an `Ordering[A]` instance for the underlying type described by the schema:

Standard Scala library provides a type class called `Ordering[A]` that allows us to compare values of type `A`. ZIO Schema provides a method called `ordering` that generates an `Ordering[A]` instance for the underlying type described by the schema:

```scala
sealed trait Schema[A] {
  def ordering: Ordering[A]
}
```

Here is an example, where it helps us to sort the list of `Person`:

```scala

case class Person(name: String, age: Int)

object Person {
  implicit val schema: Schema[Person] = DeriveSchema.gen[Person]
}

val sortedList: Seq[Person] =
  List(
    Person("John", 42),
    Person("Jane", 34)
  ).sorted(Person.schema.ordering)
```
