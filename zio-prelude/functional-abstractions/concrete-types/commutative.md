# Commutative

> `Commutative[A]` describes a data type that has a `combine` operator that is commutative and associative.

`Commutative[A]` describes a data type that has a `combine` operator that is commutative and associative.

Its signature is:

```scala
trait Associative[A] {
  def combine(left: => A, right: => A): A
}

trait Commutative[A] extends Associative[A]
```

The `Commutative` abstraction does not define any additional operators beyond the `combine` operator defined by `Associative`. However, it does provide additional structure to the `combine` operator.

Specifically, it says that the `combine` operator is commutative in addition to being associative. This means that not only does the order of operations not matter, but the order in which we combine the values does not matter.

```scala
a <> b === b <> a
```

Only some ways of combining that are associative are commutative.

For example, integer addition is both associative and commutative. `2 + 3` is the same as `3 + 2`.

On the other hand, string concatenation is associative but not commutative. `"a" + "b"` is not the same as `"b" + "a"`.

We can use the `Commutative` abstraction to specify at the type level whether one of our data types can be combined in a way that is associative and commutative or only associative. We can also test for it using the laws testing functionality provided by ZIO Prelude.

For example, here is how we could define and test a `Commutative` instance for a version of the `RunningAverage` data type that we discussed in the introduction to functional abstractions:

```scala

case class RunningAverage(sum: Long, count: Int)

object RunningAverage {
  implicit val RunningAverageCommutative: Commutative[RunningAverage] =
    new Commutative[RunningAverage] {
      def combine(left: => RunningAverage, right: => RunningAverage): RunningAverage =
        RunningAverage(left.sum + right.sum, left.count + right.count)
    }
  implicit val RunningAverageEqual: Equal[RunningAverage] =
    Equal.default
}

object RunningAverageSpec extends ZIOSpecDefault {

  def spec = suite("RunningAverageSpec") {
    test("commutative") {
      val runningAverageGen = Gen.long.zipWith(Gen.int)(RunningAverage(_, _))
      checkAllLaws(CommutativeLaws)(runningAverageGen)
    }
  }
}
```

The main advantage of having a `Commutative` instance is that we don't have to worry about the order in which we combine values because we will get the same result no matter what.

To take a simple example, consider combining the values in a set.

```scala
val fruits: Set[String] =
  Set("apple", "orange", "banana")
// fruits: Set[String] = Set("apple", "orange", "banana")

val ints: Set[Int] =
  Set(1, 2, 3)
// ints: Set[Int] = Set(1, 2, 3)

val notCommutative: String =
  fruits.foldLeft("")(_ + _)
// notCommutative: String = "appleorangebanana"

val commutative: Int =
  ints.foldLeft(0)(_ + _)
// commutative: Int = 6
```

The order of values in a set is arbitrary. When we iterate over the values of a set we must do it in some order, and that order may be the same for a particular implementation of a set, but that is not something we can rely on.

As a result, the value of `notCommutative` is not well defined. It could be `"applebananaorange"`, `"orangebananaapple"`, `"bananaorangeapple"`, among others.

We have to be very careful in dealing with `notCommutative`. Either we must not use it at all or we must use it in very limited ways that will return the same result regardless of the order in which it was constructed (e.g. testing whether it contains the substring `"apple"`).

In contrast, the value of `commutative` is extremely well defined. It will always be `6` no matter what.

Regardless of the order in which we iterate over the elements in the set it will always be `6` because addition is a commutative operation. Even if we tried to get a different value we couldn't come up with an order of iterating over the set elements that gave a different result.

Thus, we can safely work with `commutative` and do anything we want with it because it will always have the same value.

This is a simple example but this property of commutativity can come up in much more complex domains such as concurrent or distributed programming. There, the order in which we receive values from other fibers or nodes is often arbitrary.

In that case having a way of combining values that is commutative can be very useful because it means we don't need coordination mechanisms to ensure that we combine results in a certain order.

For an example of this we can look to the `mergeAllPar` operator on `ZIO`.

```scala

object ZIO {
  def mergeAllPar[R, E, A, B](
    in: Iterable[ZIO[R, E, A]]
  )(zero: B)(f: (B, A) => B): ZIO[R, E, B] =
    ???
}
```

This runs all of the `ZIO` workflows in the `Iterable` in parallel and combines their results with the function `f`, starting from `zero`. Its implementation creates a `Ref` with the `zero` value and then each fiber updates the `Ref` with its result as it completes.

The order in which the fibers complete is not determinate so the function `f` should be associative and commutative, as the documentation for `mergeAllPar` helpfully informs us.

We do not want to force functional abstractions on users so in ZIO itself we state that the function must satisfy these properties and leave it at that. But with ZIO Prelude we can define a version of this operator that expresses at the type level that the `combine` operator must be associative and commutative.

```scala
def mergeAllParCommutative[R, E, A, B: Commutative](
  in: Iterable[ZIO[R, E, A]]
)(zero: B)(f: A => B): ZIO[R, E, B] =
  ZIO.mergeAllPar(in)(zero)((b, a) => b <> f(a))
```

Now we run all the `ZIO` workflows in parallel, mapping their results to a type for which a commutative `combine` operator is defined. This will ensure that our way of combining values really is commutative.

With the `Commutative` abstraction we can describe the properties of our data types precisely, helping us understand what guarantees they provide and what our responsibilities are for working with them. This idea of commutative operators is also a helpful one to keep in mind when we are designing our own data types as we saw in the example above.
