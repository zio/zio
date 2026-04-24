# NonEmptyForEach

> `NomEmptyForEach[F]` describes a parameterized type `F[A]` that contains one or more values of type `A`.

`NomEmptyForEach[F]` describes a parameterized type `F[A]` that contains one or more values of type `A`.

Its signature is:

```scala
trait ForEach[F[+_]] {
  def forEach[G[+_]: IdentityBoth: Covariant, A, B](fa: F[A])(f: A => G[B]): G[F[B]]
}

trait NonEmptyForEach[F[+_]] extends ForEach[F] {
  def forEach1[G[+_]: AssociativeBoth : Covariant, A, B](fa: F[A])(f: A => G[B]): G[F[B]]
  final def forEach[G[+_]: IdentityBoth: Covariant, A, B](fa: F[A])(f: A => G[B]): G[F[B]] =
    forEach1(fa)(f)
}

trait Covariant[F[+_]] {
  def map[A, B](f: A => B): F[A] => F[B]
}

trait AssociativeBoth[F[_]] {
  def both[A, B](fa: => F[A], b: => F[B]): F[(A, B)]
}

trait IdentityBoth[F[_]] extends AssociativeBoth[F] {
  def any: F[Any]
}
```

The `NonEmptyForEach` functional abstraction builds on the `ForEach` abstraction to describe a type that contains one or more `A` values rather than zero or more `A` values.

For example `NonEmptyChunk` has a `NonEmptyForEach` instance defined for it because it always contains at least one value. In contrast `Chunk` only has a `ForEach` instance defined for it because it could be empty.

`NonEmptyForEach` generalizes over collection types and types that always contain at least one value, such as `NonEmptyChunk`, `NonEmptyList`, and certain tree data structures.

The defining operator of the `NonEmptyForEach` abstraction is `forEach1`, which has the same signature as the `forEach` operator defined by the `ForEach` abstraction except that it doesn't require an identity element with respect to the combining operator for `G` values.

Recall that if instances of `Covariant` and `AssociativeBoth` exist for a data type we can define the `map` and `zipWith` operators and if an instance of `IdentityBoth` exists as well we can define the `succeed` operator.

```scala
def map[F[+_], A, B](fa: F[A])(f: A => B)(implicit covariant: Covariant[F]): F[B] =
  covariant.map(f)(fa)

def succeed[F[+_], A](a: => A)(implicit covariant: Covariant[F], both: IdentityBoth[F]): F[A] =
  covariant.map[Any, A](_ => a)(both.any)

def zipWith[F[+_], A, B, C](
  fa: F[A],
  fb: F[B]
)(f: (A, B) => C)(implicit covariant: Covariant[F], both: AssociativeBoth[F]): F[C] =
  covariant.map(f.tupled)(both.both(fa, fb))
```

To see why we don't need an identity element for the combining operator when the collection contains at least one value let's compare the implementation of the `ForEach` instance for `List` with the implementation of the `NonEmptyForEach` instance for the `NonEmptyList` data type from ZIO Prelude.

```scala

implicit val ListForEach: ForEach[List] =
  new ForEach[List] {
    def forEach[G[+_]: IdentityBoth: Covariant, A, B](fa: List[A])(f: A => G[B]): G[List[B]] =
      fa.foldRight(succeed[G, List[B]](List.empty)) { (a, gbs) =>
        zipWith(f(a), gbs)(_ :: _)
      }
  }
// ListForEach: ForEach[List] = repl.MdocSession$MdocApp$$anon$1@20557b40

implicit val NonEmptyListNonEmptyForEach: ForEach[NonEmptyList] =
  new NonEmptyForEach[NonEmptyList] {
    def forEach1[G[+_]: AssociativeBoth: Covariant, A, B](fa: NonEmptyList[A])(f: A => G[B]): G[NonEmptyList[B]] =
      fa.reduceMapRight(a => map(f(a))(NonEmptyList.single))((a, gbs) => zipWith(f(a), gbs)(NonEmptyList.cons))
  }
// NonEmptyListNonEmptyForEach: ForEach[NonEmptyList] = repl.MdocSession$MdocApp$$anon$2@4bfe794b
```

In our implementation of `ForEach` for `List` we needed to use the `succeed` operator to handle the case where the collection was empty, because in that case we had to be able to lift an empty collection into the context of `G`. The `succeed` operator requires an `IdentityBoth` instance because we have to be able to construct a neutral value of type `G` that we can then fill with the value we are lifting using the `map` operator.

In contrast, in our implementation of `NonEmptyForEach` we never need to call `succeed` because there is always at least one one element in the collection. So we can just apply the function `f` to each element in the collection and then use the `zipWith` operator to combine the results.

The fact that the collection can never be empty allows us to relax constraints on other operators as well.

For example, we can define an operator called `reduceMapLeft` that is a more powerful version of `foldLeft` that does not require an initial value.

```scala

def reduceMapLeft[F[+_]: ForEach, A, S](as: F[A])(map: A => S)(reduce: (S, S) => S): S =
  as.foldLeft[Option[S]](None) {
    case (Some(s), a) => Some(reduce(s, map(a)))
    case (None, a) => Some(map(a))
  }.get
```

We know it is safe to call `get` here because the collection is guaranteed to have at least one element.

The `reduceMapLeft` operator allows us to define additional operators for reducing a collection to a summary value that would not be safe to call on a collection that might be empty.

In particular, we can define a more powerful version of the `foldMap` operator defined on the `ForEach` abstraction called `reduceMap`.

```scala
def reduceMap[F[+_]: ForEach, A, B: Associative](as: F[A])(f: A => B): B =
  reduceMapLeft(as)(f)(_ <> _)
```

Since we know the collection contains at least one element we do not need an `identity` value with respect to the associative `combine` operator, just like we did not need an `any` value with respect to the `both` operator in `forEach1`.

With this we can easily do something like calculate the sum, product, min, and max of a collection in a single pass.

```scala

def stats[F[+_]: ForEach, A](as: F[A])(
  implicit sum: Associative[Sum[A]],
  prod: Associative[Prod[A]],
  min: Associative[Min[A]],
  max: Associative[Max[A]]
): (A, A, A, A) =
  reduceMap(as)(a => (Sum[A](a), Prod[A](a), Min[A](a), Max[A](a)))
```

This is a very nice way to describe reducing a collection to a summary value and gives us additional flexibility to use ways of combining that are associative but do not have an identity element relative to the `foldMap` operator.

As we can see, the `NonEmptyForEach` functional abstraction builds on the `ForEach` functional abstraction to describe parameterized types that contain one or more values of the type they are parameterized on. This lets us define a wide variety of operators, even more than the ones we could define for the `ForEach` abstraction.

So if you are defining your own data type like a collection and it will never be empty you should definitely define a `NonEmptyForEach` instance for it so you can take advantage of all the nice operators that are defined in terms of it.
