# Inverse

> `Inverse[A]` describes a type that has a `combine` operator and also has an `inverse` operator that is the inverse of the `combine` operator.

`Inverse[A]` describes a type that has a `combine` operator and also has an `inverse` operator that is the inverse of the `combine` operator.

Its signature is:

```scala
trait Associative[A] {
  def combine(left: => A, right: => A): A
}

trait Identity[A] extends Associative[A] {
  def identity: A
}

trait Inverse[A] extends Identity[A] {
  def inverse(left: => A, right: => A): A
}
```

For example, subtraction is an `inverse` operator with addition being the `combine` operator and `0` being the `identity` value.

While the `combine` operator adds structure, the `inverse` operator takes it away, undoing the structure that the `combine` operator added. As a result, applying the `inverse` operator to a value and itself takes away all the structure, returning the identity element.

```scala
inverse(a, a) === identity
```

It is important to note that `inverse` is a binary operator rather than a unary operator.

When we think of an inverse with respect to integer addition it is tempting to think of the inverse as the unary operator of negation. This does indeed have the attractive property that the sum of any value and its inverse is zero.

However, this definition turns out to be quite limiting because it does not allow us to describe an inverse for types that do not contain negative values.

To see this, consider the case of natural numbers, represented by the `Natural` new type in ZIO Prelude. By definition there are no negative natural numbers and thus we cannot define a unary inverse operator for `Natural`.

However, natural numbers have a well defined notion of subtraction so it feels like we are missing something here. The solution is to view `inverse` as a binary operator.

We can then define an `Inverse` instance for `Natural` like this:

```scala

implicit val NaturalInverse: Inverse[Natural] =
  new Inverse[Natural] {
    def combine(left: => Natural, right: => Natural): Natural =
      Natural.plus(left, right)
    val identity: Natural =
      Natural.zero
    def inverse(left: => Natural, right: => Natural): Natural =
      Natural.minus(left, right)
  }
// NaturalInverse: Inverse[Natural] = repl.MdocSession$MdocApp0$$anon$1@347e848c
```

This also lets us define `Inverse` instances for other data types that we would not otherwise be able to. For example, what is the inverse of `Set(1, 2, 3)`?

Logically it is the set of all integers other than `1`, `2`, and `3` but we have no way of efficiently representing that because a `Set` is a collection of concrete values. So we would not be able to define an `Inverse` instance for `Set` this way.

On the other hand, by defining `Inverse` as a binary operator we can quite easily do so.

```scala
implicit def SetInverse[A]: Inverse[Set[A]] =
  new Inverse[Set[A]] {
    def combine(left: => Set[A], right: => Set[A]): Set[A] =
      left | right
    val identity: Set[A] =
      Set.empty
    def inverse(left: => Set[A], right: => Set[A]): Set[A] =
      left &~ right
  }
```

The `inverse` operator is set difference, with the `combine` operator being set union and the empty set being the `identity` value.

The `inverse` abstraction tends to be used less frequently than the other abstractions for combining values. In writing programs, we typically want to build up more complex values from simpler ones, even if it is just tearing down one data type to build another.

The `Inverse` abstraction is most useful when we want to generalize over the notion of "subtraction" for different types. For example, if we are working with maps we might be interested in defining an operation that lets us return the difference between one map and another.

```scala
def diff[A, B: Equal : Inverse](left: Map[A, B], right: Map[A, B]): Map[A, B] =
  right.foldLeft(left) { case (map, (a, b)) =>
    val b1 = Inverse[B].inverse(map.getOrElse(a, Identity[B].identity), b)
    if (b1 === Identity[B].identity) map - a else map + (a -> b1)
  }
```

We look up the value for every key in the `right` map in the `left` map, returning the `identity` value if it does not exist. We then compute the difference using the `inverse` operator.

If the difference is equal to the `identity` value we remove the key from the map. Otherwise we add it.

Let's see it in action.

```scala
val peopleWhoOweMe: Map[String, Sum[Int]] =
  Map("Alice" -> Sum(1000), "Bob" -> Sum(1000))
// peopleWhoOweMe: Map[String, Sum[Int]] = Map("Alice" -> 1000, "Bob" -> 1000)

val peopleIOwe: Map[String, Sum[Int]] =
  Map("Alice" -> Sum(1000), "Carol" -> Sum(1000))
// peopleIOwe: Map[String, Sum[Int]] = Map("Alice" -> 1000, "Carol" -> 1000)

val myNetFinancialPosition: Map[String, Int] =
  diff(peopleWhoOweMe, peopleIOwe)
// myNetFinancialPosition: Map[String, Int] = Map(
//   "Bob" -> 1000,
//   "Carol" -> -1000
// )

val friends: Map[String, Set[String]] =
  Map("Alice" -> Set("Bob", "Carol"), "Bob" -> Set("Alice", "Carol"))
// friends: Map[String, Set[String]] = Map(
//   "Alice" -> Set("Bob", "Carol"),
//   "Bob" -> Set("Alice", "Carol")
// )

val unfriends: Map[String, Set[String]] =
  Map("Alice" -> Set("Bob", "Carol"), "Bob" -> Set("Alice"))
// unfriends: Map[String, Set[String]] = Map(
//   "Alice" -> Set("Bob", "Carol"),
//   "Bob" -> Set("Alice")
// )

val updatedFriends: Map[String, Set[String]] =
  diff(friends, unfriends)
// updatedFriends: Map[String, Set[String]] = Map("Bob" -> Set("Carol"))
```

In the first example the two maps represent assets and liabilities. The difference between them is our net financial position with each of our counterparties.

In the second example the first map represents the friends of users of a social networking site and the second represents friend removals for each user that need to be processed. The difference between them is the updated friends for each user we should return.

Often you won't need to use the `Inverse` abstraction directly. For example, functionality similar to the above is provided by the `ZSet` data type in ZIO Prelude.

However, when you do need to work some notion of subtraction `Inverse` is here and will let you describe that shared structure in a composable way.
