# Hash

> `Hash[A]` describes the ability to hash a value of type `A`.

`Hash[A]` describes the ability to hash a value of type `A`.

The signature is:

```scala
trait Equal[-A] {
  def equal(left: A, right: A): Boolean
}

trait Hash[-A] extends Equal[A] {
  def equal(left: A, right: A): Boolean
  def hash(a: A): Int
}
```

`Hash` builds on `Equal` in a different way than `Ord` by defining a way to `Hash` two values of type `A` in addition to a way to compare them for equality.

If we import `zio.prelude._` We can hash any data type that has a `Hash` instance defined for it using the `hash` operator or its symbolic alias `##`.

In addition to hashing being generally useful, hashing allows us to determine whether a value already exists in a collection of values without individually testing for equality with each value. This allows us to implement some operators that would not otherwise be practical if all we could do was compare values for equality pairwise.

The `Hash` abstraction provides similar functionality as the `hashCode` operator defined on any object in Scala with a few advantages.

First, it allows us to specify whether a data type has a meaningful definition of hash code.

For example, we can call `hashCode` on a Scala function but there is not a meaningful way of comparing Scala functions for equality so the definition of `hashCode` is just based on reference equality.

While this could potentially be useful in some very low level parts of our code this is generally a potential source of bugs if we are not specifically trying to use reference equality. ZIO Prelude just doesn't define a `Hash` instance for functions so this can never happen.

Second, we can use the `Hash` abstraction to define strategies for hashing data types that are not in our control. An existing data type may implement `hashCode` in a way that doesn't make sense or is not what we want for whatever reason (e.g. creates too high a rate of hash collisions, is vulnerable to attacks, etc...).

With just the `hashCode` defined in the Scala standard library we don't have any way of fixing this other than creating a new wrapper for the data type, which has potential performance implications and may require boilerplate in wrapping and unwrapping the new type or reimplementing existing methods. In contrast, with ZIO Prelude defining a different hashing strategy is as simple as implementing a new instance.

Third, the `Hash` abstraction guarantees that our definition of hashing is consistent with our definition of equality. That is, if two values are equal they should always have the same hash code.

This is another common source of errors whenever we implement our own definition of `equals` and `hashCode` and ZIO Prelude can prevent it by automatically checking that all instances of `Hash` define a consistent notion of equality and hashing.

For example, here is how we could test that ZIO Prelude's notions of hashing and equality for strings are consistent.

```scala

object HashSpec extends ZIOSpecDefault {

  def spec = suite("HashSpec") {
    test("StringHash") {
      val stringGen = Gen.string
      checkAllLaws(HashLaws)(stringGen)
    }
  }
}
```

This will automatically generate a large number of string values and test that the definition of hashing is consistent with equality for all of them, reporting any failures.

## Defining Hash Instances

ZIO Prelude automatically includes instances of `Hash` for all data types in ZIO and the Scala standard library that support a meaningful definition of hashing. This also includes types like collections, tuples, and sum types like `Option` and `Either`.

If we are defining our own data types we can use the `default` operator if we want to use the definition of `equals` and `hashCode` already defined on the type. This can be particularly useful if we are defining case classes that are made up of 

```scala
case class Person(name: String, age: Int)

object Person {
  implicit val PersonHash: Hash[Person] =
    Hash.default
}
```

If we are defining a `Hash` instance for our own data type we can use the `make` operator, which allows you to provide your own definition of hashing and equality.

However, hashing can involve some low level logic so for the `Hash` abstraction it can be particularly nice to use the `contramap` operator.

```scala
trait Hash[-A] {
  def contramap[B](f: B => A): Hash[B]
}
```

The `contramap` operator says if we know how to turn a `B` value into an `A` value and we know how to hash an `A` value then we can hash a `B` value simply by turning it into an `A` value and hashing that.

This turns out to be quite useful because we can convert almost any date type we define into some data type in ZIO or the Scala library that there is already a `Hash` instance defined for. The function `f` should be information preserving so if we have two `A` values that are not equal they should map to two `B` values that are not equal and if we have two `A` values that are equal they should map to two `B` values that are equal.

For example, here is how we can use `contramap` to easily define a `Hash` instance for a custom data type to keep track of different topics and the number of votes for each of them.

```scala

case class Topic(value: String)

case class Votes(value: Int)

object Votes {
  implicit val VotesHash: Hash[Votes] =
    Hash.default
}

case class VoteMap(map: Map[Topic, Votes])

object VoteMap {
  implicit val VoteMapHash: Hash[VoteMap] =
    Hash[Map[Topic, Votes]].contramap(_.map)
}
```

ZIO Prelude knows how to hash values of type `Map[A, B]` as long as there is a way of hashing the values in the map. And we know how to hash the values in the map because we defined a `Hash` instance for `Votes`.

So all we have to do is tell ZIO Prelude how to convert our `VoteMap` into a `Map`, which is quite easy because a `VoteMap` just wraps a map!

This strategy turns out to be quite general because there are `Hash` instances defined on so many data types such as collection types, product types like tuples, sum types like `Either` and `Option`, and primitive types. We can almost always convert our data type to some combination of those types.

By doing this we can easily define which data types it makes sense to hash, control the hashing strategies we use, and integrate hashing with the other functional abstractions in ZIO Prelude.
