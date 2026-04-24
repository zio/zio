# AssociativeBoth

> `AssociativeBoth[F]` describes a way of combining two values `F[A]` and `F[B]` into a value `F[(A, B)]` that is associative.

`AssociativeBoth[F]` describes a way of combining two values `F[A]` and `F[B]` into a value `F[(A, B)]` that is associative.

Its signature is:

```scala
trait AssociativeBoth[F[_]] {
  def both[A, B](fa: => F[A], fb: => F[B]): F[(A, B)]
}
```

If we import `zio.prelude._` we can use the `zip` operator or its symbolic alias `<*>` to combine any two values of a parameterized type `F` that have an `AssociativeBoth` instance defined for them.

The `both` operator must be associative, so if we combine `fa` and `fb` with `both` and then combine the result with `fc` we must get the same value as if we combine `fb` and `fc` and then combine `fa` with the result. That is, after reassociating nested tuples:

```scala
(fa <*> fb) <*> fc === fa <*> (fb <*> fc)
```

Notice that this is the same as the associative law for concrete types described by the `Associative` abstraction, just lifted into the context of parameterized types.

The `both` operator corresponds to running the left value and then running the right value.

We can see this because to produce a tuple `(A, B)` we need to run both the left value and the right value and to do it in a way that is associative we need to do it in a consistent order. Technically running the right value and then running the left value would also be associative but since we can always flip the order of the arguments it is helpful to adopt the convention that the first value goes on the left and the second value goes on the right.

For example, in the context of `ZIO` the `zip` operator returns a new `ZIO` workflow that describes running the workflow on the left, then running the workflow on the right, and then returning a tuple of their results.

```scala

val helloZIO: ZIO[Console, IOException, Unit] =
  Console.printLine("Hello") <*> Console.printLine("ZIO")
// helloZIO: ZIO[Console, IOException, Unit] = FlatMap(
//   trace = "repl.MdocSession.MdocApp0.helloZIO(associativeboth.md:26)",
//   first = Stateful(
//     trace = "repl.MdocSession.MdocApp0.helloZIO(associativeboth.md:26)",
//     onState = zio.FiberRef$unsafe$PatchFiber$$Lambda$17053/0x0000000804975840@4dae1e15
//   ),
//   successK = zio.ZIO$$Lambda$17055/0x0000000804974040@7f0719d6
// )
```

This will print `Hello` to the console on the first line and then `ZIO` on the second line.

If the first workflow fails then the second workflow will never be run.

```scala
val failZIO: ZIO[Console, IOException, Unit] =
  ZIO.fail(new IOException("Fail")) <*> Console.printLine("ZIO")
// failZIO: ZIO[Console, IOException, Unit] = FlatMap(
//   trace = "repl.MdocSession.MdocApp0.failZIO(associativeboth.md:33)",
//   first = Stateful(
//     trace = "repl.MdocSession.MdocApp0.failZIO(associativeboth.md:33)",
//     onState = zio.ZIO$$$Lambda$17057/0x0000000804972840@15f43912
//   ),
//   successK = zio.ZIO$$Lambda$17055/0x0000000804974040@1bbbed2c
// )
```

Now `ZIO` will never be printed to the console.

What it means to run the left value and run the right value will vary between data types.

For example, for `Either` and `Option` it corresponds to running the left value and then running the right value if the left value is a success.

```scala
def both[E, A, B](fa: => Either[E, A], fb: => Either[E, B]): Either[E, (A, B)] =
  fa match {
    case Left(e)  => Left(e)
    case Right(a) => fb match {
      case Left(e)  => Left(e)
      case Right(b) => Right((a, b))
    }
  }

def both[E, A, B](fa: => Option[A], fb: => Option[B]): Option[(A, B)] =
  fa match {
    case None    => None
    case Some(a) =>
      fb match {
        case None    => None
        case Some(b) => Some((a, b))
      }
  }
```

For collection types it corresponds to the Cartesian product of the left and right values.

```scala
def both[A, B](fa: => List[A], fb: => List[B]): List[(A, B)] =
  for {
    a <- fa
    b <- fb
  } yield (a, b)
```

If we think of a collection as representing a set of possible states then we can see this as the set of all possible states from taking each element in the left collection and then combining it with each element in the right collection.

For a parser it corresponds to running the left parser and then running the right parser with the updated state if the first parser succeeds.

So far we have been using covariant types that produced values in our example, but there is no reason we have to limit ourselves to that. The `AssociativeBoth` functional abstraction can describe associative ways of combining contravariant and invariant parameterized types as well.

This is a significant advantage over other functional programming libraries, which conflate abstractions for describing ways of combining paramaterized types with abstractions for describing the variance of parameterized types. This requires defining many additional interfaces to try to accomplish the same thing and obscures the fundamental symmetry of the underlying structure here.

To see this, let's start with a `Predicate`.

```scala
trait Predicate[-A] {
  def run(a: A): Boolean
}
```

A `Predicate` is naturally contravariant in the `A` type parameter because `A` appears as an input to `run` but never as an output. We can prove that to ourselves by implementing a `contramap` operator for `Predicate` like this:

```scala
trait Predicate[-A] { self =>
  def run(a: A): Boolean
  def contramap[B](f: B => A): Predicate[B] =
    new Predicate[B] {
      def run(b: B): Boolean =
        self.run(f(b))
    }
}
```

We would like to be able to combine predicates so that if we have predicates about types `A` and `B` we can combine them into a predicate about the tuple `(A, B)`. Especially in combination with the `contramap` operator, which we could use to transform a predicate about a tuple to a predicate about a class class, we could then define predicates about user defined data types in terms of predicates about primitive types.

We can do that with `AssociativeBoth` like this:

```scala

object Predicate {
  implicit val PredicateAssociativeBoth: AssociativeBoth[Predicate] =
    new AssociativeBoth[Predicate] {
      def both[A, B](left: => Predicate[A], right: => Predicate[B]): Predicate[(A, B)] =
        new Predicate[(A, B)] {
          def run(tuple: (A, B)): Boolean =
            left.run(tuple._1) && right.run(tuple._2)
        }
    }

  implicit val PredicateContravariant: Contravariant[Predicate] =
    new Contravariant[Predicate] {
      def contramap[A, B](f: B => A): Predicate[A] => Predicate[B] =
        _.contramap(f)
    }
}
```

Here what the `both` operator means is to run the left predicate and then if it passes to run the right predicate.

When the parameterized type is covariant there are some additional useful operators we can define for it.

```scala
def zipWith[F[+_]: AssociativeBoth : Covariant, A, B, C](
  fa: => F[A],
  fb: => F[B]
)(f: (A, B) => C): F[C] =
  fa.zip(fb).map(f.tupled)

def zipLeft[F[+_]: AssociativeBoth : Covariant, A, B, C](
  fa: => F[A],
  fb: => F[B]
): F[A] =
  zipWith(fa, fb)((a, _) => a)
  
def zipRight[F[+_]: AssociativeBoth : Covariant, A, B, C](
  fa: => F[A],
  fb: => F[B]
): F[B] =
  zipWith(fa, fb)((_, b) => b)
```

The `zipWith` operator uses `zip` to combine two parameterized types and then `map` to combine the outputs with a function. For example, we can generate two random numbers with `ZIO` and then combine their results by adding them.

The `zipLeft` and `zipRight` operators and their symbolic aliases `<*` and `*>` are specialized variants of this that throw away the left or right results. These are useful when we want to include a value of a parameterized type in the larger data type we are building but don't care about its result.

For example, we could avoid creating unnecessary nested tuples when we combine `ZIO` workflows like this:

```scala
val helloFromAssociativeBoth: ZIO[Console, IOException, Unit] =
  Console.printLine("Hello") *>
    Console.printLine("From") *>
    Console.printLine("AssociativeBoth")
// helloFromAssociativeBoth: ZIO[Console, IOException, Unit] = FlatMap(
//   trace = "repl.MdocSession.MdocApp0.<local MdocApp0>.helloFromAssociativeBoth(associativeboth.md:145)",
//   first = FlatMap(
//     trace = "repl.MdocSession.MdocApp0.<local MdocApp0>.helloFromAssociativeBoth(associativeboth.md:144)",
//     first = Stateful(
//       trace = "repl.MdocSession.MdocApp0.<local MdocApp0>.helloFromAssociativeBoth(associativeboth.md:144)",
//       onState = zio.FiberRef$unsafe$PatchFiber$$Lambda$17053/0x0000000804975840@7410af1b
//     ),
//     successK = zio.ZIO$$Lambda$17058/0x0000000804980840@39030bab
//   ),
//   successK = zio.ZIO$$Lambda$17058/0x0000000804980840@4ea8c4d
// )
```

There is also a variant of `zipWith` for contravariant types called `bothWith` that combines `zip` and `contramap`.

```scala
def bothWith[F[-_]: AssociativeBoth : Contravariant, A, B, C](
  fa: => F[A],
  fb: => F[B]
)(f: C => (A, B)): F[C] =
  fa.zip(fb).contramap(f)
```

Notice again the symmetry here between the `zipWith` and `bothWith` operators.

With this we can combine contravariant data types such as `Predicate` the same way we can with covariant data types.

```scala
case class Email(lines: List[String], sender: String)

val noSuspiciousWords: Predicate[List[String]] =
  new Predicate[List[String]] {
    def run(words: List[String]): Boolean =
      !words.exists(_.contains("get rich"))
  }
// noSuspiciousWords: Predicate[List[String]] = repl.MdocSession$MdocApp0$$anon$5@1fe3c4ca

val noSuspiciousSender: Predicate[String] =
  new Predicate[String] {
    def run(sender: String): Boolean =
      !sender.contains("unclaimedfunds")
  }
// noSuspiciousSender: Predicate[String] = repl.MdocSession$MdocApp0$$anon$6@2e9cc4b5

val spamFilter: Predicate[Email] =
  noSuspiciousWords.bothWith(noSuspiciousSender) { 
    case Email(lines, sender) => (lines, sender)
  }
// spamFilter: Predicate[Email] = repl.MdocSession$MdocApp0$Predicate$1$$anon$1@23096abf
```

The composed predicate will extract the lines and the sender, sending the lines to the `nonSuspiciousWords` predicate and then if it passes sending the sender to the `nonSuspiciousSender` predicate.

We can use the `bothWith` operator with other contravariant types as well. For example, we could combine two sinks to produce a new sink that sends all inputs first to one sink and then to the other.

The `AssociativeBoth` functional abstraction describes a very basic pattern of running one value and then running another value. In addition, the operators that can be defined in terms of it such as `zip` and `zipWith` are very common and practically useful.

If you are working with data types from ZIO or the Scala standard library the value you get from the `AssociativeBoth` abstraction itself may be limited because most of these data types already define these operators directly since they are so useful.

However, there are some cases where these operators are missing such as the `zip` operator on `Either`. In these cases if you bring in ZIO Prelude you will get these missing operators automatically, which can help clean up your code.

If you are defining your own parameterized data type then asking if you can define an `AssociativeBoth` instance for it can be a very helpful exercise.

Associative ways of combining are fundamental to building up more complex structures from simpler ones, so if your data type does not support an associative combining operation it can be worth asking why that is and if there is a way it could be refactored to support such an operation. If it does, defining an `AssociativeBoth` instance will get you a lot of useful operators, especially in combination with the `Covariant` or `Contravariant` and `ForEach` abstractions from ZIO Prelude.

Finally, if you are doing generic programming with the abstractions in ZIO Prelude then `AssociativeBoth` will again be an important operator to describe running one value and then another in a generic way. The orthagonal way that abstractions are described in ZIO Prelude can be particularly valuable here to help you avoid confusing unrelated concepts and describe your shared structure in as general a way as possbile.
