# Ordering Derivation

> The Scala standard library provides a type class called `Ordering[A]` that compares values of type `A`. Every schema has a `Schema#ordering` method that builds an `Ordering[A]` from the structure the schema describes, so a type with a derived schema gets an ordering without a hand-written instance:

```scala
sealed trait Schema[A] {
  def ordering: Ordering[A]
}
```

The following example uses the derived ordering to sort a list of `Person` values:

```scala
import zio.schema._

case class Person(name: String, age: Int)

object Person {
  implicit val schema: Schema[Person] = DeriveSchema.gen[Person]
}
```

Records compare field by field, so the people are sorted by `name` first:

```scala
List(
  Person("John", 42),
  Person("Jane", 34),
  Person("Jane", 20)
).sorted(Person.schema.ordering)
// res0: List[Person] = List(
//   Person(name = "Jane", age = 20),
//   Person(name = "Jane", age = 34),
//   Person(name = "John", age = 42)
// )
```

## How Values Compare

The ordering follows the structure of the schema:

- Primitives use the `compare` method of their standard type, such as numeric order for numbers and lexicographic order for strings.
- Records compare field by field, in declaration order. The first field that differs decides the result.
- Enums (sealed traits) compare by case first, in the order the cases are declared. Two values of the same case compare by their contents.
- `Option` values put `None` before any `Some`, and `Either` values put any `Left` before any `Right`.
- Tuples compare by their first element, then their second.
- Sequences such as `List`, `Vector`, and `Chunk` compare element by element, and a shorter sequence comes before a longer one that starts with it.

Each comparison converts both values to [`DynamicValue`](../operations/dynamic-data-representation.md) first, so a derived ordering is slower than a hand-written one. For sorting large collections in a hot path, a hand-written `Ordering` is faster.

:::caution
`Schema#ordering` returns 0 (equal) for any two `Set`, `Map`, `NonEmptyChunk`, `NonEmptyMap`, or `Fallback` values, whatever their contents. A record that contains such a field ignores that field when comparing. A binary primitive (`StandardType.BinaryType`) compares by the sum of its bytes, so two different byte chunks with the same sum are equal. To order by one of these types, write the `Ordering` yourself. For a `Set`, one way is to sort its elements and compare them as a `List`:

```scala
import zio.schema._

val setOrdering: Ordering[Set[Int]] =
  Schema[List[Int]].ordering.on[Set[Int]](_.toList.sorted)
```
:::
