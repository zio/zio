# IdentityEither

> `IdentityEither[F]` describes an associative way to combine two values `F[A]` and `F[B]` into a value `F[Either[A, B]]` with an identity value `none` of type `F[Nothing]`.

`IdentityEither[F]` describes an associative way to combine two values `F[A]` and `F[B]` into a value `F[Either[A, B]]` with an identity value `none` of type `F[Nothing]`.

Its signature is:

```scala
trait AssociativeEither[F[_]] {
  def either[A, B](fa: => F[A], fb: => F[B]): F[Either[A, B]]
}

trait IdentityEither[F[_]] extends AssociativeEither[F] {
  def none: F[Nothing]
}
```

The `none` value must be an identity element with respect to the `either` operator so that, after eliminating cases of `Either` that can never contain values, the following property holds:

```scala
fa <+> none === fa
none <+> fa === fa
```

This is the same as the law for the `Identity` abstraction for concrete types but lifted into the context of parameterized types.

To satisfy these properties, rhe `none` value  must must be a value that fails with no useful information.

To see what this means, let's look at the `IdentityEither` instance for `Option`.

```scala

implicit val OptionIdentityEither: IdentityEither[Option] =
  new IdentityEither[Option] {
    def either[A, B](fa: => Option[A], fb: => Option[B]): Option[Either[A, B]] =
      fa match {
        case None =>
          fb match {
            case None    => None
            case Some(b) => Some(Right(b))
          }
        case Some(a) => Some(Left(a))
      }
    val none: Option[Nothing] =
      None
  }
// OptionIdentityEither: IdentityEither[Option] = repl.MdocSession$MdocApp0$$anon$1@6646477d
```

If we think of an `Option[A]` as representing either a success with a value of type `A` or a failure with no useful information, then the implementation of `none` is `None`. Combining any `Option[A]` with `None` with `orElseEither` won't change its result.

Now let's compare that to `Either` to see why we can't define a `IdentityEither` instance for `Either`.

We said that the identity value must be a value that fails with no useful information but `Either` is polymorphic in its error type so we can't define what a value that contains no information would be for an arbitrary type. We don't even know how to construct an value of that type.

```scala
def none[E]: Either[E, Nothing] =
  Left(???)
```

To define an identity value we would have to have the concept of an empty error. For example, if we created a data type that could fail without any error then the empty failure would be the `none` value.

```scala

val noneOption: Either[Option[Nothing], Nothing] =
  Left(None)
// noneOption: Either[Option[Nothing], Nothing] = Left(value = None)

val noneChunk: Either[Chunk[Nothing], Nothing] =
  Left(Chunk.empty)
// noneChunk: Either[Chunk[Nothing], Nothing] = Left(value = IndexedSeq())
```

Having this `none` value can be useful in certain situations. For example, it can help us to define a `filter` operator on a data type in combination with some of the other functional abstractions in ZIO Prelude.

However, supporting a `none` value requires allowing the possibility of that a value can fail without containing any errors, which is unattractive in many cases.

For example, `ZIO` and most effect types such as `ZManaged` and `ZStream` do not have an `IdentityEither` instance defined for them because is they fail they always fail with a failure `E`. Otherwise when we used an error handling operator we would have to deal with the error as well as the possibility that there is a failure but no error at all.

Note that in the case of `ZIO` a workflow can also die with a cause that can potentially be `Cause.empty`, but since the associative operator `orElseEither` does not recover from `Die` failures that doesn't help us with our problem here.

The other data types that do tend to have `IdentityEither` instances are collection types that can be empty. For example, the `IdentityEither` instance for `Chunk` looks like this.

```scala
implicit val ChunkIdentityEither: IdentityEither[Chunk] =
  new IdentityEither[Chunk] {
    def either[A, B](as: => Chunk[A], bs: => Chunk[B]): Chunk[Either[A, B]] =
      as.map(Left(_)) ++ bs.map(Right(_))
    val none: Chunk[Nothing] =
      Chunk.empty
  }
// ChunkIdentityEither: IdentityEither[Chunk] = repl.MdocSession$MdocApp0$$anon$2@576a0071
```

Here the identity element is just the empty chunk. If we think of running a collection as producing a set of values and then failing when the collection is empty, `none` is the collection that doesn't produce any values and fails immediately.

Another more mathematical way to think about it is that if concatenation corresponds to addition then the empty collection corresponds to zero. or this reason, we can also see that collections that cannot be empty, such as `NonEmptyChunk` from ZIO or `NonEmptyList` from ZIO Prelude, cannot have `IdentityEither` instances defined for them.

We can also define `none` values for contravariant types, though these tend to be less interesting.

For contravariant types, the `none` value corresponds to a value that can never be run because it has `Nothing` as its input type and there are no values of type `Nothing`. There the existence of the `none` value and our ability to compose it with the `orElseEither` operator in implies that values can be run independently of each other.

For example, let's look at the `IdentityEither` instance for `Predicate`.

```scala
trait Predicate[-A] {
  def run(a: A): Boolean
}

object Predicate {
  implicit val PredicateIdentityEither: IdentityEither[Predicate] =
    new IdentityEither[Predicate] {
      def either[A, B](left: => Predicate[A], right: => Predicate[B]): Predicate[Either[A, B]] =
        new Predicate[Either[A, B]] {
          def run(either: Either[A, B]): Boolean =
            either match {
              case Left(a) => left.run(a)
              case Right(b) => right.run(b)
            }
        }
      val none: Predicate[Nothing] =
        new Predicate[Nothing] {
          def run(a: Nothing): Boolean =
            a
    }
  }
}
```

The `none` predicate can never be run, but we can still safely compose it with other predicates with the `either` operator because since there can never be a value of type `Nothing`, there can also never be a case where the `either` operator tries to run the `none` predicate.

Overall the `IdentityEither` abstraction is a useful one to generalize over the concept of a value of a parameterized type that always fails with no information, complementing the concept of a value that always succeeds with no information from `IdentityBoth`.

For concrete types that have such a `none` value there tend to be existing operators defined on the data type that already take advantage of this this functionality, so `IdentityEither` tends to be less helpful when working with concrete data types.

For defining your own data types thinking about whether there is a `none` value can be helpful for API design. As discussed above, there is often a trade off between having an identity element, which is generally a good thing, and providing the guarantee that all failures contain some useful information.

Defining an `IdentityEither` instance for your own data type will also let you work with generic code in ZIO Prelude that uses this abstraction.

Finally `IdentityEither` can be useful when you are writing generic code to describe the concept that a data type must have this failure element with no information to provide a "default" failure value.
