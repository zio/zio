# IdentityBoth

> `IdentityBoth[F]` describes an associative way to combine two values `F[A]` and `F[B]` into a value `F[(A, B)]` that also has an identity element of type `F[Any]`.

`IdentityBoth[F]` describes an associative way to combine two values `F[A]` and `F[B]` into a value `F[(A, B)]` that also has an identity element of type `F[Any]`.

Its signature is:

```scala
trait AssociativeBoth[F[_]] {
  def both[A, B](fa: => F[A], fb: => F[B]): F[(A, B)]
}

trait IdentityBoth[F[_]] extends AssociativeBoth[F] {
  def any: F[Any]
}
```

The `any` value must be an identity element with respect to the `both` operator, so that after removing unnecessary tuples the following property holds:

```scala
fa <*> identity === fa
identity <*> fa === fa
```

This is the same as the laws for the `Identity` functional abstraction for concrete types except lifted into the context of parameterized types.

To be an identity element, running the `any` value must not do anything so we can always compose it as many times as we want with the `both` operator without changing the result.

As with the other abstractions for parameterized types we have looked at `ZIO` provides a good initial example.

The `any` value for `ZIO` is `unit`, the workflow that does not do anything, always succeeds, and produces no useful information. We can do nothing before or after another `ZIO` workflow as many times as we want and we will always get a workflow that does the same thing.

```scala

val helloUnit: ZIO[Console, IOException, Unit] =
  Console.printLine("Hello") <*> ZIO.unit
// helloUnit: ZIO[Console, IOException, Unit] = FlatMap(
//   trace = "repl.MdocSession.MdocApp0.helloUnit(identityboth.md:31)",
//   first = Stateful(
//     trace = "repl.MdocSession.MdocApp0.helloUnit(identityboth.md:31)",
//     onState = zio.FiberRef$unsafe$PatchFiber$$Lambda$17053/0x0000000804975840@4b1db8d6
//   ),
//   successK = zio.ZIO$$Lambda$17055/0x0000000804974040@8b9e48f
// )

val unitHello: ZIO[Console, IOException, Unit] =
  ZIO.unit <*> Console.printLine("Hello")
// unitHello: ZIO[Console, IOException, Unit] = FlatMap(
//   trace = "repl.MdocSession.MdocApp0.unitHello(identityboth.md:35)",
//   first = Sync(
//     trace = "",
//     eval = zio.ZIO$$$Lambda$17034/0x0000000804960040@59e5877c
//   ),
//   successK = zio.ZIO$$Lambda$17055/0x0000000804974040@7f984f9e
// )
```

These programs are identical because `ZIO.unit` does not do anything at all and always succeeds.

Similarly, the `any` with respect to data types such as `Either` and `Option` that model failure is a successful value that contains no useful information.

```scala
val anyEither: Either[Nothing, Any] =
  Right(())
// anyEither: Either[Nothing, Any] = Right(value = ())

val anyOption: Option[Any] =
  Some(())
// anyOption: Option[Any] = Some(value = ())
```

Since the `both` operator for these data types corresponds to failing on the first error or returning a success with all the results, the `any` value can never change the result. If `any` is combined with a failed value the result will be that failure, and if it is combined with a successful value the result will be that success.

For collection types the `any` value is a collection with a single value containing no useful information.

```scala
val anyList: List[Any] =
  List(())
// anyList: List[Any] = List(())
```

We might be tempted to think that an empty collection would be the identity value but we can see this is not the case because the `both` operator corresponds to the Cartesian product of two collections and the product of a collection with the empty collection is the empty collection, not the original collection.

If we think of a collection as representing a set of possible states then we can think of this as the state that occurs with certainty. Or if we think of the `both` operator as being the product of two collections the identity element for multiplication is one, not zero.

For a parser the identity element would be a parser that always succeeds with no useful information and does not change the parse state.

We can also define identity values with respect to contravariant types.

For example, consider the `Predicate` data type.

```scala
trait Predicate[-A] {
  def run(a: A): Boolean
}
```

We could define an `IdentityBoth` instance for it like this:

```scala

object Predicate {
  implicit val PredicateIdentityBoth: IdentityBoth[Predicate] =
    new IdentityBoth[Predicate] {
      val any: Predicate[Any] =
        new Predicate[Any] {
          def run(a: Any) =
            true
        }
      def both[A, B](left: => Predicate[A], right: => Predicate[B]): Predicate[(A, B)] =
        new Predicate[(A, B)] {
          def run(tuple: (A, B)): Boolean =
            left.run(tuple._1) && right.run(tuple._2)
        }
    }
}
```

The `both` operator for `Predicate` combines two predicates to return a new predicate that is true if both of the original predicates are true. So we can always combine any predicate with the predicate that is always true without changing the result.

This again shows the value of separating abstractions for defining how parameterized types can be combined from abstractions for describing their variance. Otherwise we would face a proliferation of abstractions to describe the product of these various combinations of properties of the combining operation and the variance of the data type, as other functional programming libraries have experienced.

When a data type has both a `IdentityBoth` and a `Covariant` instance we can define a particularly useful operator for it called `succeed`.

```scala
def succeed[F[+_]: IdentityBoth : Covariant, A](a: => A): F[A] =
  IdentityBoth[F].any.map(_ => a)
```

This says that if a data type is also covariant we can always "lift" any value into the data type by starting with the identity value and using `map` to transform the output type to the specified value.

This is very useful for working with parameterized data types in general because it allows us to take ordinary values and use them in the context of our parameterized type.

For example we can use the `succeed` operator on `ZIO` to wrap any arbitrary block of Scala code in a `ZIO` effect.

```scala
val helloScala: ZIO[Any, Nothing, Unit] =
  ZIO.succeed(println("Hello, Scala!"))
// helloScala: ZIO[Any, Nothing, Unit] = Sync(
//   trace = "repl.MdocSession.MdocApp0.helloScala(identityboth.md:98)",
//   eval = <function0>
// )
```

This is also quite useful in ZIO Prelude in particular because many operators are only defined for data types that have an `IdentityBoth` instance, such as the `forEach` operator on the `ForEach` abstraction.

As with concrete data types, it is quite useful to have an identity element with respect to the combining operator for parameterized types.

Most of the time for working with existing data types from ZIO or the Scala standard library it will be less important directly because operators for that data type will already be defined for you.

However, it can still be helpful to think about what the identity value is with respect to a given operation. For example, you may not have thought of a `Right` with no useful information as being an identity with respect to combining `Either` values.

When defining your own parameterized data types, it can be helpful to think about whether an identity element exists with respect to combining values of your data type. If not, could it be refactored to have such an identity value, or does thinking about that tell you something about why it makes sense that your data type does not have one?

Finally, if you are writing generic code in terms of the functional abstractions in ZIO Prelude this is likely to be another important abstraction. In particular the ability to lift a value into the parameterized type described by the combination of the `CommutativeBoth` and `Covariant` abstractions is often important.
