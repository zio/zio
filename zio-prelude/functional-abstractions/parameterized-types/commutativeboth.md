# CommutativeBoth

> `CommutativeBoth[F]` describes a way of combining two values `F[A]` and `F[B]` into a value `F[(A, B)]` that is commutative.

`CommutativeBoth[F]` describes a way of combining two values `F[A]` and `F[B]` into a value `F[(A, B)]` that is commutative.

Its signature is:

```scala
trait AssociativeBoth[F[_]] {
  def both[A, B](fa: => F[A], fb: => F[B]): F[(A, B)]
}

trait CommutativeBoth[F[_]] extends AssociativeBoth[F]
```

If we import `zio.prelude._` we can use the `zipPar` operator or its symbolic alias `<&>` to combine any two values of a parameterized type for which a `CommutativeBoth` instance exists in a commutative way.

The `both` operator must be commutative, so if we combine `fa` and `fb` that must be the same as combining `fb` and `fa`. After rearranging the order of tuples:

```scala
fa <&> fb === fb <&> fa
```

This is the same law as defined for the `Commutative` functional abstraction for concrete types, just lifted into the context of parameterized types.

As the name of the `zipPar` operator implies, the `both` operator of the `CommutativeBoth` abstraction corresponds to running the first value and running the second value in parallel.

We can see this because to produce a tuple `(A, B)` we need to run both the left value and the right value and to do it in a way that order doesn't matter we need to run the left value and the right value in parallel. This doesn't necessarily mean we are actually forking separate fibers to run the left and right values but it does mean that we run the left and right values in a way that the result does not depend on the order in which they are run.

To get a better sense of what it means for a combining operation on parameterized types to be commutative, let's start with looking at why the `zip` operator on `ZIO` is not commutative.

```scala

val helloZIO: ZIO[Console, IOException, Unit] =
  Console.printLine("Hello") <*> Console.printLine("ZIO")
// helloZIO: ZIO[Console, IOException, Unit] = FlatMap(
//   trace = "repl.MdocSession.MdocApp0.helloZIO(commutativeboth.md:29)",
//   first = Stateful(
//     trace = "repl.MdocSession.MdocApp0.helloZIO(commutativeboth.md:29)",
//     onState = zio.FiberRef$unsafe$PatchFiber$$Lambda$17053/0x0000000804975840@650a72a5
//   ),
//   successK = zio.ZIO$$Lambda$17055/0x0000000804974040@2efa6013
// )

val zioHello: ZIO[Console, IOException, Unit] =
  Console.printLine("ZIO") <*> Console.printLine("Hello")
// zioHello: ZIO[Console, IOException, Unit] = FlatMap(
//   trace = "repl.MdocSession.MdocApp0.zioHello(commutativeboth.md:33)",
//   first = Stateful(
//     trace = "repl.MdocSession.MdocApp0.zioHello(commutativeboth.md:33)",
//     onState = zio.FiberRef$unsafe$PatchFiber$$Lambda$17053/0x0000000804975840@49299a49
//   ),
//   successK = zio.ZIO$$Lambda$17055/0x0000000804974040@34ae8187
// )
```

If the `zip` operator was commutative these two `ZIO` workflows would be the same. But they obviously are not.

The first will print `Hello` to the console on one line followed by `ZIO` on the next whereas the second will print `ZIO` to the console followed by `Hello`. We can easily observe this by running the program ourselves or using a test implementation of the `Console` service like `TestConsole` from ZIO Test.

We can also see this in the context of failures.

```scala
val failZIO: ZIO[Console, IOException, Unit] =
  ZIO.fail(new IOException("Fail")) <*> Console.printLine("ZIO")
// failZIO: ZIO[Console, IOException, Unit] = FlatMap(
//   trace = "repl.MdocSession.MdocApp0.failZIO(commutativeboth.md:40)",
//   first = Stateful(
//     trace = "repl.MdocSession.MdocApp0.failZIO(commutativeboth.md:40)",
//     onState = zio.ZIO$$$Lambda$17057/0x0000000804972840@7eb0fd9d
//   ),
//   successK = zio.ZIO$$Lambda$17055/0x0000000804974040@3ed2f975
// )

val zioFail: ZIO[Console, IOException, (Unit, Unit)] =
  Console.printLine("ZIO") <*> ZIO.fail(new IOException("Fail"))
// zioFail: ZIO[Console, IOException, (Unit, Unit)] = FlatMap(
//   trace = "repl.MdocSession.MdocApp0.zioFail(commutativeboth.md:44)",
//   first = Stateful(
//     trace = "repl.MdocSession.MdocApp0.zioFail(commutativeboth.md:44)",
//     onState = zio.FiberRef$unsafe$PatchFiber$$Lambda$17053/0x0000000804975840@459d3c86
//   ),
//   successK = zio.ZIO$$Lambda$17055/0x0000000804974040@6c2d7c11
// )
```

Again if the `zip` operator was commutative these two `ZIO` workflows should be the same but they are not. The first program will fail immediately and never print `ZIO` to the console, whereas the second will print `ZIO` to the console and then fail.

What would a commutative version of this operator be? It would have to run both the left and right values at the same time so they both had the same opportunity to run.

The `zipPar` operator on `ZIO` does just this.

```scala
val helloZIOPar: ZIO[Console, IOException, Unit] =
  Console.printLine("Hello") <&> Console.printLine("ZIO")
// helloZIOPar: ZIO[Console, IOException, Unit] = DynamicNoBox(
//   trace = "repl.MdocSession.MdocApp0.helloZIOPar(commutativeboth.md:51)",
//   update = 1L,
//   f = zio.ZIO$$Lambda$17117/0x00000008049eb440@a4bbe1c
// )
```

Now `Hello` and `World` will be printed to the console at the same time. For any given execution one will appear before the other but they each have the same opportunity to appear first and both this and the version with the order reversed describe the same `ZIO` workflow.

This interpretation of `zipPar` as running the left and right values on separate fibers is the most natural one but not the only one. The left and right values can be run in parallel as long as the result of running one does not depend on the result of running the other and information from both has equal opportunity to be included in the result.

To see this, let's look at a slightly simplified version of the `Validation` data type from ZIO Prelude and how we can define the `zipPar` operator for it.

```scala

sealed trait Validation[+E, +A] { self =>

  def zipPar[E1 >: E, B](that: Validation[E1, B]): Validation[E1, (A, B)] =
    (self, that) match {
      case (Success(a), Success(b))    => Success((a, b))
      case (Success(_), Failure(e1s))  => Failure(e1s)
      case (Failure(es), Success(_))   => Failure(es)
      case (Failure(es), Failure(e1s)) => Failure(es ++ e1s)
    }
}

object Validation {
  case class Success[+A](value: A) extends Validation[Nothing, A]
  case class Failure[+E](errors: NonEmptyChunk[E]) extends Validation[E, Nothing]
}
```

We are not forking any fibers here, but notice how in our implementation of `zipPar` we are matching on the left and right sides at the same time and preserving the information from both errors in the case where both fail. As long as we implement equality in a way that does not depend on the order of errors, combining two `Validation` values with `zipPar` will always give the same result regardless of the order in which we combine them.

Another interpretation of what it means to run two values in parallel comes from collections. We can combine values from two collections in parallel by zipping them together pairwise.

```scala

implicit val ListCommutativeBoth: CommutativeBoth[List] =
  new CommutativeBoth[List] {
    def both[A, B](as: => List[A], bs: => List[B]): List[(A, B)] =
      as.zip(bs)
  }
// ListCommutativeBoth: CommutativeBoth[List] = repl.MdocSession$MdocApp0$$anon$1@4244c06c
```

Again we are not forking any fibers here but values from the left list and the right list have an equal opportunity to be included in the zipped list.

We can see this if we zip together two lists of different lengths.

```scala
val left: List[Int] =
  List(1, 2, 3)
// left: List[Int] = List(1, 2, 3)

val right: List[Int] =
  List(4, 5, 6, 7, 8)
// right: List[Int] = List(4, 5, 6, 7, 8)

val leftRight: List[(Int, Int)] =
  left.zip(right)
// leftRight: List[(Int, Int)] = List((1, 4), (2, 5), (3, 6))

val rightLeft: List[(Int, Int)] =
  right.zip(left)
// rightLeft: List[(Int, Int)] = List((4, 1), (5, 2), (6, 3))
```

The zipped lists are identical after rearranging the order of tuples.

Some instances of `CommutativeBoth` actually combine these interpretations. For example the `CommutativeBoth` instance for `ZStream` is implemented in terms of the `zip` operator on `ZStream`, which creates a new stream that pulls values from the left and the right streams pairwise, evaluating effects in the left and right streams on separate fibers.

```scala

def both[R, E, A, B](left: => ZStream[R, E, A], right: ZStream[R, E, B]): ZStream[R, E, (A, B)] =
  left.zip(right)
```

Just like the `AssociativeBoth` abstraction, `CommutativeBoth` is not limited to covariant types. For example we could send inputs to multiple sinks in parallel, having each sink run on a separate fiber.

When the parameterized type is covariant the `zipWithPar` operator is also defined on it.

```scala
def zipWithPar[F[+_]: CommutativeBoth : Covariant, A, B, C](
  fa: => F[A],
  fb: => F[B]
)(f: (A, B) => C): F[C] =
  fa.zipPar(fb).map(f.tupled)
```

This is just like the `zipWith` operator defined on data types that have an `AssociativeBoth` instance except it runs the left and right values in parallel instead of running the left value and then the right value. It is very useful when you want to do two things in parallel and then combine the results.

A version of this is available for contravariant data types called `bothWithPar`.

```scala
def bothWithPar[F[-_]: CommutativeBoth : Contravariant, A, B, C](
  fa: => F[A],
  fb: => F[B]
)(f: C => (A, B)): F[C] =
  fa.zipPar(fb).contramap(f)
```

The `bothWithPar` operator splits a data type `C` up into two parts `A` and `B`, running the left value with `A` and the right value with `B` in parallel.

The `CommutativeBoth` functional abstraction, like the `AssociativeBoth` abstraction, describes a basic way of combining two data types, this time by running them in parallel instead of running the left one and then the right one. The `zipPar` operator, or more domain specific names for it for certain basic types, is also very practically useful and we saw that it corresponded to common operators on these data types that we were probably already familiar with.

For this reason, however, the `CommutativeBoth` abstraction may be less useful if you are working with data types from ZIO or the Scala standard library. They generally already implement this functionality directly, for example in the `zipPar` operator on `ZIO` and the `zip` operator on `ZStream`.

This abstraction is more likely to be useful when you are defining your own data type. If your data type supports a way of combining that is commutative, such as the ones we have discussed here, then defining a `CommutativeBoth` instance will let you express that and work with other operators that are defined on data types with `CommutativeBoth` instances.

Finally, this abstraction is likely to be valuable for those writing generic code to describe running values in parallel. As with the `AssociativeBoth` abstraction the ability to generalize over covariant, contravariant, and invariant data types is likely to be particularly valuable here.
