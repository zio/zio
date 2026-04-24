# ForEach

> `ForEach[F]` describes a parameterized type `F[A]` that contains zero or more values of type `A`.

`ForEach[F]` describes a parameterized type `F[A]` that contains zero or more values of type `A`.

Its signature is:

```scala
trait Covariant[F[+_]] {
  def map[A, B](f: A => B): F[A] => F[B]
}

trait ForEach[F[+_]] extends Covariant[F] {
  def forEach[G[+_]: IdentityBoth: Covariant, A, B](fa: F[A])(f: A => G[B]): G[F[B]]
  final def map[A, B](f: A => B): F[A] => F[B] =
    ???
}

trait IdentityBoth[F[_]] {
  def any: F[Any]
  def both[A, B](fa: => F[A], b: => F[B]): F[(A, B)]
}
```

The `ForEach` functional abstraction builds on the `Covariant` abstraction to describe a type that contains zero or more `A` values rather than merely potentially producing `A` values at some point in the future.

For example `Chunk` has a `ForEach` instance because it contains zero or more `A` values. In contrast `ZIO` has a `Covariant` instance but not a `ForEach` instance because it is only a description of a workflow that may produce an `A` value and does not output any actual `A` value until it is run.

`ForEach` generalizes over collection types like `List`, `Map`, and `Chunk`. It also describes data types like `Option` and `Either` that contain zero or one value, which can be thought of as a special case of containing zero or more values.

The defining operator of the `ForEach` abstraction is `forEach`. It lets us take a collection of type `F[A]` and run a function `A => G[B]` for each element in the collection, returning a new collection `F[B]` in the context of `G`.

This is somewhat abstract so let's look at the `foreach` operator on `ZIO`, which is a variant of this, to get a sense of what it means.

```scala
trait ZIO[-R, +E, +A]

object ZIO {
  def foreach[R, E, A, B](as: List[A])(f: A => ZIO[R, E, B]): ZIO[R, E, List[B]] =
    ???
}
```

Here we have specialized the collection type `F` from `ForEach` to `List` and the effect type `G` from `ForEach` to `ZIO`. The interpretation of this is now much more clear.

For each element in the list we construct a new `ZIO[R, E, B]` by applying `f` to the element. Then we combine all of those `ZIO` effects into a single effect that will run each of the individual effects and collect their results in a `List`.

This pattern of doing something for each element in the collection and then collecting the results back into the original collection is common to all implementations of `ForEach`. We can get a better sense for it by looking at a simple implementation of the `foreach` operator on `ZIO`.

```scala
trait ZIO[-R, +E, +A] { self =>
  def zipWith[R1 <: R, E1 >: E, B, C](that: ZIO[R1, E1, B])(f: (A, B) => C): ZIO[R, E, C] =
    ???
}

object ZIO {
  def foreach[R, E, A, B](as: List[A])(f: A => ZIO[R, E, B]): ZIO[R, E, List[B]] =
    as.foldRight[ZIO[R, E, List[B]]](ZIO.succeed(List.empty)) { (a, zio) =>
      f(a).zipWith(zio)(_ :: _)
    }
  def succeed[A](a: => A): ZIO[Any, Nothing, A] =
    ???
}
```

In the implementation of `foreach` the `foldRight` operator on `List` tears down the original list into each of its elements, using the function `f` to produce a new `ZIO` value for each element. Then we use the `zipWith` operator on `ZIO` to combine the resulting `ZIO` values into a single `ZIO` value, putting the values back together into a `List` with the `::` constructor.

The fact that the implementation of such a complex operator can be so simple should have us thinking about how we can make this work for collection types other than `List` and types other than `ZIO`. The `ForEach` functional abstraction does just that.

The `ForEach` abstraction is parameterized on the collection type `F`, so in the implementation of `ForEach` for a collection we will know how to tear down that collection and build it back up. For example, in the implementation of `ForEach` for `List` we will know that we can tear down a `List` using `foldRight` and build it back up using `List.empty` and `::`.

The other piece of information we need is how to put a value into the type `G`, described by the `succeed` operator, and how to combine two `G` values into a single `G` value, described by the `zipWith` operator. Fortunately, that is exactly the functionality that the combination of the `Covariant` and `IdentityBoth` abstractions provide.

Recall that the `Covariant` functional abstraction defines a `map` operator that allows us to transform a `F[A]` into an `F[B]` with a function `A => B`.

```scala
trait Covariant[F[+_]] {
  def map[A, B](f: A => B): F[A] => F[B]
}
```

The `IdentityBoth` abstraction defines a `both` operator that allows us to combine an `F[A]` and an `F[B]` into an `F[(A, B)]` as well as an `any` value of type `F[Any]` that is an identity element for that operator.

```scala
trait AssociativeBoth[F[_]] {
  def both[A, B](fa: => F[A], b: => F[B]): F[(A, B)]
}

trait IdentityBoth[F[_]] extends AssociativeBoth[F] {
  def any: F[Any]

}
```

If instances of these two abstractions exist for a data type we can define the `zipWith` and `succeed` operators that we used in our implementation of `foreach` for `ZIO`.

```scala
def succeed[F[+_], A](a: => A)(implicit covariant: Covariant[F], both: IdentityBoth[F]): F[A] =
  covariant.map[Any, A](_ => a)(both.any)

def zipWith[F[+_], A, B, C](
  fa: F[A],
  fb: F[B]
)(f: (A, B) => C)(implicit covariant: Covariant[F], both: AssociativeBoth[F]): F[C] =
  covariant.map(f.tupled)(both.both(fa, fb))
```

We are now in a position to complete our generalization of the `foreach` operator on `ZIO`. Here is what the implementation of the `ForEach` instance for `List` would look like.

```scala
trait ForEach[F[+_]]{
  def forEach[G[+_]: IdentityBoth: Covariant, A, B](fa: F[A])(f: A => G[B]): G[F[B]]
}

implicit val ListForEach: ForEach[List] =
  new ForEach[List] {
    def forEach[G[+_]: IdentityBoth: Covariant, A, B](fa: List[A])(f: A => G[B]): G[List[B]] =
      fa.foldRight(succeed[G, List[B]](List.empty)) { (a, gbs) =>
        zipWith(f(a), gbs)(_ :: _)
      }
  }
// ListForEach: ForEach[List] = repl.MdocSession$MdocApp$$anon$1@2fa46f19
```

Notice how similar our implementation of the `ForEach` abstraction looks to our initial implementation of the `foreach` operator on `ZIO`. Other than using operators defined on `Covariant` and `IdentityBoth` instead of operators on `ZIO` they are identical.

We have already come a long way with our implementation of `ForEach`. We have a much better understanding now of what the `forEach` operator does and how its signature reflects the minimum possible set of constraints necessary to implement it.

We can now implement instances of `ForEach` for all collection types, like `List`, `Chunk`, and `Map`, as well as types that are like collections, such as `Option` and `Either`. In each case we can perform an effect like a `ZIO` for each element in the collection, returning a new `ZIO` that collects the results back into the original collection type.

This is already quite powerful, but the other aspect of the `ForEach` abstraction that is important is our ability to use other parameterized types for the `G` in the signature of `ForEach`. We have focused so far on `ZIO` because it is the prototypical functional effect and provides a very clear intuition of what it means to do something for each element of a collection, but we can use many other data types as well.

For example, we can use the `forEach` operator with a function that returns a `Validation` value from ZIO Prelude.

`Validation` is a data type that can either succeed with a value or fail with one or more errors. Using `forEach` with `Validation` corresponds to validating all the values in a collection, returning either a new collection of fully validated data or an accumulation of all validation errors that occurred.

```scala

case class Person(name: String, age: Int)

def validateName(name: String): Validation[String, String] =
  Validation.fromPredicateWith("Name was empty")(name)(_.nonEmpty)

def validateAge(age: Int): Validation[String, Int] =
  Validation.fromPredicateWith(s"Age $age was less than zero")(age)(_ >= 0)

def validatePerson(name: String, age: Int): Validation[String, Person] =
  Validation.validateWith(validateName(name), validateAge(age))(Person)

val validData: List[(String, Int)] =
  List(("John", 35), ("Jane", 25))
// validData: List[(String, Int)] = List(("John", 35), ("Jane", 25))

val invalidData: List[(String, Int)] =
  List(("", 35), ("John", -1))
// invalidData: List[(String, Int)] = List(("", 35), ("John", -1))

val success: Validation[String, List[Person]] =
  validData.forEach { case (name, age) => validatePerson(name, age) }
// success: Validation[String, List[Person]] = Success(
//   log = IndexedSeq(),
//   value = List(Person(name = "John", age = 35), Person(name = "Jane", age = 25))
// )

val failure: Validation[String, List[Person]] =
  invalidData.forEach { case (name, age) => validatePerson(name, age) }
// failure: Validation[String, List[Person]] = Failure(
//   log = IndexedSeq(),
//   errors = NonEmptyChunk(Name was empty, Age -1 was less than zero)
// )
```

Using `forEach` with the valid data will return a validation success containing the fully validated list of `Person("John", 35)` and `Person("Jane", 25")`. In contrast, using `forEach` with the invalid data will return a validation failure with the failures `"Name was empty"` and `"Age -1 was less than zero"`.

We can return other data types from the function we provide to `ForEach` to obtain other functionality. For example, if we use `Option` or `Either` we will get either a collection of all the successful results or the first failure to occur, rather than the accumulation of all failures as with `Validation`.

There are also a couple of specialized data types we can use in the return type of `f` that turn out to be particularly important for implementing other operators.

One of these you may have noticed from the very beginning of this section was the `Id` type.

`Id` is the parameterized type `F[A]` where `F` doesn't have any structure itself and just contains exactly one `A` value. This seems quite trivial but it is useful to adapt a function `A => B` to the type `A => G[B]` that `ForEach` is expecting.

This allows us to implement the `map` operator in terms of `forEach` and proves that the `ForEach` abstraction is an extension of the `Covariant` abstraction.

```scala
def map[F[+_]: ForEach, A, B](fa: F[A])(f: A => B): F[B] =
  Id.unwrap(fa.forEach(a => Id.wrap(f(a))))
```

Another data type that is important for implementing other operators is `State`.

The `State` data type from ZIO Prelude describes a state transition function `S => (A, S)` that takes an initial state and returns a value and an updated state. Using `ForEach` with `State` corresponds to composing all of those state transition functions into a single state transition function.

ZIO Prelude provides a specialized operator for using `forEach` with `State` called `mapAccum`.

```scala
def mapAccum[F[+_]: ForEach, S, A, B](fa: F[A])(s: S)(f: (S, A) => (S, B)): (S, F[B]) =
  ???
```

This lets us specify an initial state `S` and then in our function `f` instead of just providing a function `A => B` like in `map` we can update the state as well. The implementation of `mapAccum` takes care of putting each of these functions in the `State` data type for us and then runs the final computation for us with the initial state to produce both the final state and the final value.

This is very useful for implementing other operators because it lets us get information out of the collection type `S`, so now we can implement collection operators that don't just transform the collection but reduce it to a summary value.

To see this, let's implement the `foldLeft` operator from the Scala collection library in terms of `mapAccum`.

```scala
def foldLeft[F[+_]: ForEach, A, S](fa: F[A])(s: S)(f: (S, A) => S): S =
  mapAccum(fa)(s)((s, a) => (f(s, a), ()))._1
```

The `foldLeft` operator simply accumulates the fold state and discards the collection.

This is very powerful because it means that we can implement almost every collection operator in terms of `ForEach`.

This includes any collection operator that reduces a collection to some other value, like `count`, `exists`, `find`, `foldLeft`, `foldRight`, `forall`, `groupBy`, `isEmpty`, `product`, `size`, `sum`, and `toList`.

It also includes any operator that modifies the values of a collection while maintaining the "shape" of the collection such as `zipWithIndex`.

The only collection operators we can't implement in terms of `ForEach` are those that change the shape of the collection like appending a new element to the collection. We don't know enough about the structure of the data type to know what that would mean or even if it would be well defined.

For example, the data type might be a tree that does not have a well defined notion of adding an element without specifying where it should be added. Or it might be a data type like `Option` or `Either` that doesn't support appending at all.

In addition to the standard Scala collection operators, `ForEach` allows us to define additional operators that take advantage of the functional abstractions and data structure in ZIO Prelude.

One variant of `forEach` that you may be familiar with is `flip`.

```scala
def flip[F[+_]: ForEach, G[+_]: IdentityBoth : Covariant, S, A, B](fga: F[G[A]]): G[F[A]] =
  fga.forEach(identity)
```

This is the generalized version of the `collectAll` operator on `ZIO`.

```scala

def collectAll[R, E, A](as: List[ZIO[R, E, A]]): ZIO[R, E, List[A]] =
  ZIO.foreach(as)(identity)
```

This operator already takes a list of `ZIO` values so all it has to do is combine them into a single `ZIO` value and put the results back together into a list.

Another particularly useful operator is `foldMap`, which lets us reduce a collection to a summary value by mapping each value to a value for which an `Identity` instance is defined and combining those values with the `combine` operator.

```scala
def foldMap[F[+_]: ForEach, A, B: Identity](as: F[A])(f: A => B): B =
  foldLeft(as)(Identity[B].identity)((b, a) => b <> f(a))
```

This is a very nice operator that lets us express a variety of ways of reducing a collection to a summary value in a very concise way.

For example we could implement `sum` in terms of `foldMap` like this:

```scala

def sum[F[+_]: ForEach, A](as: F[A])(implicit identity: Identity[Sum[A]]): A =
  foldMap(as)(a => Sum[A](a))
```

Here is how we could implement a fold that computes the sum and product of the values of a collection in a single pass:

```scala
def sumProd[F[+_]: ForEach, A](as: F[A])(implicit sum: Identity[Sum[A]], product: Identity[Prod[A]]): (A, A) =
  foldMap(as)(a => (Sum[A](a), Prod[A](a)))
```

As you can see by using `foldMap` with the functional abstractions in ZIO Prelude for describing ways of combining concrete types we can implement folds like this in an extremely high level and compositional way.

One common variant of a fold that can be particularly useful is `concatenate`, which allows us to combine all the elements of a collection into one using an `Identity` instance already defined for the element type. For example, here is how we could use it to combine a list of strings:

```scala
val strings = List("Hello", ", ", "World", "!")

strings.concatenate
```

Note that as with other extension methods in ZIO Prelude we need to do `import zio.prelude._` to bring these extension methods into scope.

In summary, `ForEach` is one of the most useful abstractions in ZIO Prelude in terms of providing a very large number of practically useful operators for implementing a single method. So if you are implementing your own parameterized type that contains zero or more values of the type it is parameterized on then defining a `ForEach` instance is a great "quick win" to immediately add a lot of functionality to your data type.
