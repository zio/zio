# AssociativeEither

> `AssociativeEither` describes a way of combining two values `F[A]` and `F[B]` into a value `F[Either[A, B]]` that is associative.

`AssociativeEither` describes a way of combining two values `F[A]` and `F[B]` into a value `F[Either[A, B]]` that is associative.

Its signature is:

```scala
trait AssociativeEither[F[_]] {
  def either[A, B](fa: => F[A], fb: => F[B]): F[Either[A, B]]
}
```

If we import `zio.prelude._` we can use the `orElseEither` operator or its symbolic alias `<+>` to combine any two values of a parameterized type `F` that have an `AssociativeEither` instance defined for them.

The `either` operator must be associative, so if we combine two values `fa` and `fb` and then combine the result with `fc`, that must be the same as combining `fb` with `fc` and then combining `fa` with the result. That is, after reassociating `Either` values the following property must hold:

```scala
(fa <+> fb) <+> fc === fa <+> (fb <+> fc)
```

This is the same associative law we saw for concrete types described by the `Associative` abstraction, but lifted to the context of parameterized types.

The `either` operator corresponds to running the left value and if that fails running the right value.

To see this, we can observe that since the `either` operator must return an `Either[A, B]` we must choose on some basis whether to return the left value or the right value. Furthermore, we must choose on some consistent basis so that the order of operations does not matter.

There are a couple of trivial ways we could do this, like always running the left value or always running the right value. However, the way we can do this that preserves information is to run the right value and then if it fails in some way to run the right value.

What it means to run the left value and then if it fails run the right value depends on the parameterized type.

For `ZIO` the meaning is quite straightforward. The `orElseEither` operator runs the left `ZIO` workflow and returns its result if it succeeds, otherwise it runs the right `ZIO` workflow and returns its result.

```scala

def orElseEither[R, E, A, B](left: => ZIO[R, E, A], right: => ZIO[R, E, B]): ZIO[R, E, Either[A, B]] =
  left.foldZIO(_ => right.map(b => Right(b)), a => ZIO.succeed(Left(a)))
```

Here we run the `left` workflow and if it is successful we just wrap it up in a `Left`. If the `left` workflow fails we recover from its failure and run the `Right` effect, packaging its result up in a `Right`.

We can see that this is associative because no matter how many `ZIO` workflows we combine with `orElseEither` the result will always be the first one from left to right to successfully complete execution.

Notice here that if the `left` workflow succeeds we never run the right workflow at all or even need to construct it. The fact that `either` and the other binary operators in ZIO Prelude are by name gives us the freedom to model that instead of having to introduce additional interfaces.

Other data types that model failure have similar implementations of the `orElseEither` operator. For example, here are the implementations of `orElseEither` for `Either` and `Option`.

```scala
def orElseEither[E, A, B](left: => Either[E, A], => right: Either[E, B])]): Either[E, Either[A, B]] =
  left match {
    case Left(e) =>
      that match {
        case Left(e)  => Left(e)
        case Right(b) => Right(Right(b))
      }
    case Right(a) => Right(Left(a))
  }

def orElseEither[A, B](left: => Option[A], => right: Option[B])]): Option[Either[A, B]] =
  left match {
    case None =>
      that match {
        case None     => None
        case Right(b) => Some(Right(b))
      }
    case Some(a) => Some(Left(a))
  }
```

In both cases if the `left` value is a success we return its result in a `Left`. Otherwise if the `right` value is a success we return its result in a `Right` and if it is a failure we fail with that error.

Notice in both cases we did not need to evaluate the `right` value if the `left` value was a success. We could have just made the `right` parameter lazy but we could flip any of these binary operators so for correctness it is important that both arguments be lazy.

Another interpretation of `orElseEither` comes from collections. Consider the following implementation of the `orElseEither` operator for `Chunk`.

```scala

implicit val ChunkAssociativeEither: AssociativeEither[Chunk] =
  new AssociativeEither[Chunk] {
    def either[A, B](as: => Chunk[A], bs: => Chunk[B]): Chunk[Either[A, B]] =
      as.map(Left(_)) ++ bs.map(Right(_))
  }
// ChunkAssociativeEither: AssociativeEither[Chunk] = repl.MdocSession$MdocApp0$$anon$1@481dea0f
```

Here we are concatenating the two `Chunk` values, putting the elements from the left `Chunk` in a `Left` and the elements from the right `Chunk` in a `Right`. We can think of this as running the left `Chunk` until it fails by running out of elements and then running the right `Chunk`.

We can see a similar interpretation in the implementation of the `orElseEither` operator for the `Schedule` data type from `ZIO`. It runs the left schedule while it wants to continue and when it stops runs the right schedule, emitting either a `Left` with the output of the left schedule or a `Right` with the output of the right schedule each time.

The `AssociativeEither` abstraction isn't limited to covariant data types.

Let's see how we can use the `orElseEither` operator to combine values of a contravariant type.

The `Predicate` type knows how to evaluate a value of type `A` to return a `Boolean`. This `Boolean` could describe whether the value of type `A` is valid data for example, or whether we should take some further action.

```scala
trait Predicate[-A] {
  def run(a: A): Boolean
}
```

We could implement an instance of the `AssociativeEither` abstraction for `Predicate` like this:

```scala
object Predicate {
  implicit val PredicateAssociativeEither: AssociativeEither[Predicate] =
    new AssociativeEither[Predicate] {
      def either[A, B](left: => Predicate[A], right: => Predicate[B]): Predicate[Either[A, B]] =
        new Predicate[Either[A, B]] {
          def run(either: Either[A, B]): Boolean =
            either match {
              case Left(a) => left.run(a)
              case Right(b) => right.run(b)
            }
        }
    }
}
```

The interpretation is slightly different here. Now failing means not being able to handle a value at all.

The left `Predicate` knows how to determine whether `A` values satisfy the condition and the right `Predicate` knows how to determine whether `B` values satisfy the condition. So when we get an `Either[A, B]` we have to match on it to determine whether it is an `A` that the left `Predicate` can handle at all.

If so, we send it to the left `Predicate` and return its result. Otherwise we send it to the right `Predicate` and return its result.

Just like the `AssociativeBoth` abstraction, if a data type with an `AssociativeEither` instance is covariant there are additional operators we can define on it.

```scala
def orElse[F[+_]: AssociativeEither : Covariant, A](fa: => F[A], fb: => F[A]): F[A] =
  fa.orElseEither(fb).map(_.merge)
```

The `orElse` operator just combines two `F[A]` values with `orElseEither` and then maps the result to merge the left and right sides of the `Either`. This is probably the version of the `orElse` operator we are most familiar with.

We can also define additional operators if a data type that has a `AssociativeEither` instance defined for it is contravariant.

```scala
def eitherWith[F[-_]: AssociativeEither : Contravariant, A, B, C](fa: F[A], fb: F[B])(
  f: C => Either[A, B]
): F[C] =
  fa.orElseEither(fb).contramap(f)
```

The `eitherWith` operator first converts input of type `C` into `Either[A, B]` values with `contramap` and the function `f`, then runs the `fa` and `fb` values using that input and the `orElseEither` operator.

For example, if our data type was a `Predicate` the `eitherWith` operator could split incoming payloads into one of two types, sending them to the appropriate predicate for evaluation and then returning the results. Of course, if we can handle a payload with two types in this way we can also handle payloads with any number of cases by repeatedly applying the `eitherWith` operator.

The `AssociativeEither` functional abstraction represents the second fundamental way of combining two values of a parameterized type. Whereas `AssociativeBoth` combines the `A` and `B` values into the sum type represented by `(A, B)`, `AssociativeEither` combines them into the product type represented by `Either[A, B]`.

For existing data types the `orElseEither` operator and its variants tend to already be implemented, often with more domain specific names, so there isn't necessarily an immediate benefit if you are working with existing data types from ZIO or the Scala standard library.

However, if you are defining your own parameterized types it can be helpful to think about what it would mean for running a value to fail and what it would mean to run another value instead. There are also operators in ZIO Prelude that are defined on types with an `AssociativeEither` instance, so implementing an instance for your own data type can let you take advantage of that functionality and test your implementation with the laws in ZIO Prelude.

Finally, the `AssociativeEither` abstraction can be quite helpful in some cases for writing generic code in terms of the abstractions in ZIO Prelude.

The combination of the ability to express running the left value and then running the right value with `AssociativeBoth` and running the left value and if it fails running the right value with `AssociativeEither` can be quite powerful. For example, we can define many parsers in this way.
