# CommutativeEither

> `CommutativeEither[F]` describes a way of combining two parameterized types `F[A]` and `F[B]` that is commutative.

`CommutativeEither[F]` describes a way of combining two parameterized types `F[A]` and `F[B]` that is commutative.

Its signature is:

```scala
trait AssociativeEither[F[_]] {
  def either(fa: => F[A], fb: => F[B]): F[Either[A, B]]
}

trait CommutativeEither[F[_]] extends AssociativeEither[F]
```

If we import `zio.prelude._` we can use the `orElseEitherPar` operator or its symbolic alias `<|>` to combine two values of a parameterized type that has a `CommutativeEither` instance defined for it in a commutative way.

The `either` operator must be commutative. So after rearranging the order of `Either` values the following property must hold:

```scala
fa <|> fb === fb <|> fa
```

This is the same as the law defined by the `Commutative` abstraction for concrete types, but lifted into the context of parameterized types.

The `either` operator runs the left value and the right value in parallel, returning the result from either the left value or the right value.

We can see this by noting that the requirement that the `either` operator is commutative places significant limitations on our implementation. We must choose on some basis between the left and right values but we can't run one and then if it fails run the other because that would in general not be commutative.

`ZIO` provides a good example of why running the left value and then running the right value if it fails is not commutative and what a commutative operator would look like.

```scala

val helloZIO: ZIO[Console, IOException, Either[Unit, Unit]] =
  Console.printLine("Hello").orElseEither(Console.printLine("ZIO"))
// helloZIO: ZIO[Console, IOException, Either[Unit, Unit]] = FoldZIO(
//   trace = "repl.MdocSession.MdocApp.helloZIO(commutativeeither.md:15)",
//   first = Stateful(
//     trace = "repl.MdocSession.MdocApp.helloZIO(commutativeeither.md:15)",
//     onState = zio.FiberRef$unsafe$PatchFiber$$Lambda$17053/0x0000000804975840@7a648899
//   ),
//   successK = zio.ZIO$$$Lambda$17041/0x0000000804965040@49c1fa09,
//   failureK = zio.ZIO$$Lambda$17130/0x0000000804a15040@51fa8201
// )

val zioHello: ZIO[Console, IOException, Either[Unit, Unit]] =
  Console.printLine("ZIO").orElseEither(Console.printLine("Hello"))
// zioHello: ZIO[Console, IOException, Either[Unit, Unit]] = FoldZIO(
//   trace = "repl.MdocSession.MdocApp.zioHello(commutativeeither.md:19)",
//   first = Stateful(
//     trace = "repl.MdocSession.MdocApp.zioHello(commutativeeither.md:19)",
//     onState = zio.FiberRef$unsafe$PatchFiber$$Lambda$17053/0x0000000804975840@42a2c962
//   ),
//   successK = zio.ZIO$$$Lambda$17041/0x0000000804965040@49c1fa09,
//   failureK = zio.ZIO$$Lambda$17130/0x0000000804a15040@47cda1bd
// )
```

If the `orElseEither` operator was commutative then these two programs should be the same. But they obviously are not.

The first program will print `Hello` to the console on one line but never print `ZIO`. Since the left `ZIO` workflow was successful the right `ZIO` workflow will never be run.

Similarly the second program will print `ZIO` to the console on one line but will never print `Hello`.

We have to return either of the values but the order can't matter. The `raceEither` operator on `ZIO` does just that.

It runs both effects concurrently, returning the first one to complete successfully and safely interrupting the other. With it we can make our example above satisfy the commutative law.

```scala

val helloZIO: ZIO[Console, IOException, Either[Unit, Unit]] =
  Console.printLine("Hello").raceEither(Console.printLine("ZIO"))
// helloZIO: ZIO[Console, IOException, Either[Unit, Unit]] = Stateful(
//   trace = "repl.MdocSession.MdocApp.<local MdocApp>.helloZIO(commutativeeither.md:33)",
//   onState = zio.ZIO$$Lambda$17135/0x0000000804a28040@7f183625
// )

val zioHello: ZIO[Console, IOException, Either[Unit, Unit]] =
  Console.printLine("ZIO").raceEither(Console.printLine("Hello"))
// zioHello: ZIO[Console, IOException, Either[Unit, Unit]] = Stateful(
//   trace = "repl.MdocSession.MdocApp.<local MdocApp>.zioHello(commutativeeither.md:37)",
//   onState = zio.ZIO$$Lambda$17135/0x0000000804a28040@47b0e781
// )
```

Now both `ZIO` workflows will begin execution at the same time.

For an individual execution of this workflow either `Hello` or `ZIO` may be printed to the console, or possibly both if the loser is not interrupted before it runs. But both the left and right workflows have an equal opportunity to be run and we can say that both `helloZIO` and `zioHello` describe the same program.

We can also see this interpretation of `orElseEitherPar` in terms of racing in its implementation for `ZStream`.

```scala

def orElseEitherPar[R, E, A, B](left: => ZStream[R, E, A], right: => ZStream[R, E, B]): ZStream[R, E, Either[A, B]] =
  left.mergeEither(right)
```

We still run both streams concurrently but now instead of cancelling the loser we just emit elements from either stream whenever they are available. This is commutative because whatever order we merge the streams they will still be evaluated concurrently.

The difference in the implementations here reflects the fact that a `ZIO` always succeeds with exactly one value, whereas a `ZStream` can succeed with zero or more values.

So in the case of `ZIO` if one fiber completes execution successfully first we have nothing to do with the value from the other and so just interrupt it. In contrast, a `ZStream` can succeed with multiple values so we can just emit the values from either stream as they are available.

In many cases the implementation of the `orElseEitherPar` will involve actual concurrency but it does not need to.

To see this, consider the following instance of the `CommutativeEither` abstraction for `Set`.

```scala

implicit val SetCommutativeEither: CommutativeEither[Set] =
  new CommutativeEither[Set] {
    def either[A, B](left: => Set[A], right: => Set[B]): Set[Either[A, B]] =
      left.map(Left(_)) ++ right.map(Right(_))
  }
// SetCommutativeEither: CommutativeEither[Set] = repl.MdocSession$MdocApp$$anon$1@36d200f
```

In this case there is no actual concurrency going on but we are combining the left `Set` and right `Set`, wrapping their values in `Left` and `Right` respectively.

This is like the interpretation of `orElseEither` as concatenating two collections except normally concatenation would not be commutative since order matters. In the case of a `Set` the order of elements doesn't matter.

We can think of this as running each `Set` to get its elements until it fails by being empty.

As with the other operators for describing ways of combining parameterized types, we can also define `CommutativeEither` instances for data types that are contravariant or invariant. These also often do not require any actual concurrency because the logical parallelism is handled by running either the left value or the right value depending on the input.

For example, here is the instance of `CommutativeEither` for the `Predicate` data type.

```scala
trait Predicate[-A] {
  def run(a: A): Boolean
}

object Predicate {
  implicit val PredicateCommutativeEither: CommutativeEither[Predicate] =
    new CommutativeEither[Predicate] {
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

It is commutative because no matter what order we combine the two predices, we will always send `A` values to the predicate that can handle `A` values and `B` values to the predicate that can handle `B` values. If we did something different with the `A` and `B` values other than just sending them to the predicate based on whether they were a `Left` or a `Right` then we would be at risk of violating the laws.

When a data type for which a `CommutativeEither` instance is defined is also covariant we can use the `orElsePar` operator as well.

```scala
def orElsePar[F[+_]: CommutativeEither : Covariant, A](left: => F[A], right: => F[A]): F[A] =
  left.orElseEitherPar(right).map(_.merge)
```

The `orElsePar` operator just maps over the output of the `orElseEitherPar` operator and merges the `Either` values when both the `left` and `right` values are parameterized on the same types.

This is quite useful and typically has more domain specific names. For example this is the `race` operator on `ZIO` and the `merge` operator on `ZStream`. We can think of this as just like `orElseEither` but eliminating the `Either` when the types that the left and right values are parameterized on are the same.

There is also an additional operator we can use for contravariant types called `eitherWithPar`.

```scala
def orElseEitherWithPar[F[-_]: CommutativeEither : Contravariant, A, B, C](
  left: => F[A],
  right: => F[B]
)(f: C => Either[A, B]): F[C] =
  left.orElseEitherPar(right).contramap(f)
```

This is like the `orElseEitherWith` defined on data types that have an `AssociativeEither` and `Contravariant` instance except it guarantees that the `F[A]` and `F[B]` values will be combined in a way that is commutative in addition to being associative.

The `CommutativeEither` functional abstraction, along with the `CommutativeBoth` abstraction, describe the two fundamental ways of combining values in parallel. We can either run both values and return both their results, as in the `zipPar` operator on `ZIO`, or we can run both values and return either of their results, as in the `raceEither` operator on `ZIO`.

As with some of the other functional abstractions for parameterized types, `CommutativeEither` tends to be most useful when you are defining your own parameterized types or writing generic code. If you are working with existing data types like `ZIO` or `ZStream` the operators described by this abstraction tend to already be available to you with more domain specific names.

If you are defining your own data type then exploring whether there is a way to run both values in parallel to produce either result can be a useful exercise.

If your data type describes some kind of effect then there is often a very obvious interpretation in terms of concurrency. If not it may require more thought.

Defining a `CommutativeEither` instance, if it exists, is a good practice to allow you to use other operators in ZIO Prelude that are defined for data types with `CommutativeEither` instances. It will also allow you to test that your implementation satisfies the expected laws, which can be tricky with some of these more complex types.

Finally, if you are writing generic code the `CommutativeBoth` and `CommutativeEither` abstractions describe the fundamental binary operators for combining values in parallel. So if you want to describe parallel or concurrent operators in a generic way you will definitely want to use these abstractions.
