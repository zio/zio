# Identity

> `Identity[A]` describes a data type with an associative combining operation that also has an identity element.

`Identity[A]` describes a data type with an associative combining operation that also has an identity element.

Its signature is:

```scala
trait Associative[A] {
  def combine(left: => A, right: => A): A
}

trait Identity[A] extends Associative[A] {
  def identity: A
}
```

The identity element is a neutral value that does not have any structure itself and so combining it with any other value just leaves the structure of the other value unchanged. For instance, an empty `Chunk` is an identity element with respect to the `++` operator.

It is important to note here that while `Identity` describes a data type with an identity element, that identity element only has meaning in relation to the binary operator. Specifically, combining any value of type `A` with the identity element with the `combine` operator must return the value unchanged.

```scala
a <> identity === a
identity <> a === a
```

For example, zero is an identity element with respect to addition, but not with respect to multiplication. Similarly, `true` is an identity element with respect to logical conjunction, but `false` is an identity element with respect to logical disjunction.

Many associative combinining operations also have an identity element, but some do not.

For example the minimum or maximum of two values on which a total ordering is defined does not have an identity element. Neither does the first or last of two values or the concatenation of collections that cannot be empty.

If we import `zio.prelude._` we can use the same `<>` operator as we did to combine two values for which an `Associative` instance was defined.

Since the `identity` value is a single value and not an operator we access it slightly different. Typically we will use the `apply` method on the `Identity` companion object.

```scala

val zero: Int =
  Identity[Sum[Int]].identity
// zero: Int = 0
```

The `apply` operator on the `Identity` companion object "looks up" the instance of the type class for the specified type, failing with a compilation error if the instance cannot be found. This operator is available on every functional abstraction in ZIO Prelude and is sometimes referred to as its "summoner".

Notice also here that we are using the `Sum` new type to specify that we are interested in the identity element with respect to addition rather than another operator such as multiplication. See the section on new types in the documentation for the `Associative` abstraction for a discussion of the use of new types to disambiguate type class instances.

The main value the identity element adds over just having the associative combining operation is the ability to handle cases where we do not have any value of the type at all.

With the `Associative` abstraction we know how to combine any two values of type `A`. So if we have one or more values of type `A` we can always combine them all together by repeatedly applying the `combine` operator.

However, we need at least one `A` value to get the process started. If we don't have any `A` value initially we have no way to create one with the `Associative` abstraction alone.

To see this, consider the example of computing the minimum value in a collection.

If the collection has exactly one element we can just return it. And if it has more than one element we can combine them using the `min` operator until we only have a single value.

But what do we do if the collection has no values? We can't do anything so we return an `Option` that is a `Some` with the minimum if there is at least one element and `None` otherwise.

```scala

def min[A: Ord](as: List[A]): Option[A] =
  Max.wrapAll(as) match {
    case h :: t => Some(t.foldLeft(h)(_ <> _))
    case Nil    => None
  }
```

Compare this to computing the sum of a collection. Now If the collection is empty we can just return the identity value.

```scala
def sum[A](as: List[A])(implicit identity: Identity[Sum[A]]): A =
  Sum.wrapAll(as).foldLeft(identity.identity)(identity.combine(_, _))
```

The identity element handles the case where the collection is empty for us, allowing us to always return a summary value.

If we don't have an identity value we can only guarantee that we can return a summary value if the collection is known not to be empty, such as a `NonEmptyChunk` or a `NonEmptyList`. This is why these data types can be valuable.

If the collection could be empty and we only have a `combine` operator we may not be able to return a summary value and the best we can do is an `Option`.

One way to think of this is that the `None` case of the `Option` handles the possibility of failure. Another is that the `None` case of the `Option` provides the identity element for the `combine` operator, which we can express like this:

```scala
implicit def OptionIdentity[A: Associative]: Identity[Option[A]] =
  new Identity[Option[A]] {
    def combine(left: => Option[A], right: => Option[A]): Option[A] =
      (left, right) match {
        case (Some(a1), Some(a2)) => Some(a1 <> a2)
        case (_, None)            => left
        case (None, _)            => right
        case (_, _)               => None
      }
    def identity: Option[A] = None
  }
```

This says that we can define an `Identity` instance for any `Option[A]` as long as there is an `Associative` instance defined for `A`. The `Option` provides the "free" structure of an identity element to go from just having an associative `combine` operator to having an `identity` element.

This can be useful to keep in mind if you are dealing with a collection that could be empty and need an `Identity` instance but only have an `Associative` instance. You can always map your collection type to an `Option` and then you will have an `Identity` instance.

Data types with an associative `combine` operator and an `identity` element are very common. With an `Identity` instance defined for them and the other tools in ZIO Prelude you are in a good position to handle working with even complex data types.
