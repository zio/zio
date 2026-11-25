# NonEmptySet

> `NonEmptySet` is a `Set` that is guaranteed to contain at least one element. Like [NonEmptyList](./nonemptylist.md) and [NonEmptyMap](./nonemptymap.md), it records in the type a fact that we already know, so the code does not need to handle an empty set that cannot occur.

For example, the set of roles a user has must contain at least one role. A `NonEmptySet` lets us say so in the type, so a function that needs a role can take a `NonEmptySet` and never has to deal with the empty case.

We import `NonEmptySet` from the `zio.prelude` package:

```scala
import zio.prelude.NonEmptySet
```

## Constructing a NonEmptySet

The easiest way to construct a `NonEmptySet` is with `apply`, which takes one element and any number of further elements. Duplicate elements are removed:

```scala
val roles: NonEmptySet[String] =
  NonEmptySet("reader", "writer", "reader")
// roles: NonEmptySet[String] = NonEmptySet(writer, reader)

roles.toSet
// res1: Set[String] = Set("writer", "reader")
```

When we already have a `Set`, `NonEmptySet.fromSetOption` returns `Some` if the set has an element and `None` otherwise. The result is an `Option` because the original set may be empty:

```scala
NonEmptySet.fromSetOption(Set("admin"))
// res2: Option[NonEmptySet[String]] = Some(value = NonEmptySet(admin))

NonEmptySet.fromSetOption(Set.empty[String])
// res3: Option[NonEmptySet[String]] = None
```

`NonEmptySet.fromNonEmptyChunk` converts a `NonEmptyChunk` into a `NonEmptySet` and returns a value directly rather than an `Option`, because a `NonEmptyChunk` cannot be empty.

## Reading a NonEmptySet

`head` returns an element that is always present, and `tail` returns the remaining elements as a plain `Set`, which may be empty:

```scala
roles.head
// res4: String = "writer"

roles.tail
// res5: Set[String] = Set("reader")
```

`tailNonEmpty` returns the remaining elements as an `Option[NonEmptySet]`, so the empty case is handled explicitly:

```scala
roles.tailNonEmpty
// res6: Option[NonEmptySet[String]] = Some(value = NonEmptySet(reader))
```

`toNonEmptyChunk` and `toNonEmptyList` convert the set to another non-empty collection, which is useful when an API needs a `NonEmptyChunk` or a `NonEmptyList`:

```scala
roles.toNonEmptyChunk
// res7: zio.NonEmptyChunk[String] = NonEmptyChunk(writer, reader)
```

## Updating a NonEmptySet

`+` adds an element, and `++` adds several elements at once. Both return a `NonEmptySet`, because adding elements cannot make the set empty:

```scala
(roles + "owner").toSet
// res8: Set[String] = Set("writer", "reader", "owner")
```

Removing an element can make the set empty, so `remove` returns a plain `Set`, while `removeNonEmpty` returns an `Option[NonEmptySet]`:

```scala
roles.remove("reader")
// res9: Set[String] = Set("writer")

roles.removeNonEmpty("reader").map(_.toSet)
// res10: Option[Set[String]] = Some(value = Set("writer"))

roles.removeNonEmpty("reader") match {
  case Some(_) => "still has roles"
  case None    => "no roles left"
}
// res11: String = "still has roles"
```

`map` transforms each element and returns a `NonEmptySet`, because mapping one element yields one element. Mapping can merge two elements that become equal, so the result can have fewer elements than the original:

```scala
roles.map(_.length).toSet
// res12: Set[Int] = Set(6)
```

## Instances

`NonEmptySet` provides the instances that the functional abstractions in ZIO Prelude require. For example, `NonEmptySet` has an `Invariant` instance, so it can be mapped in the same way as other data types that implement `Invariant`. Its `DeriveEqual` instance makes two sets equal when they contain the same elements.
