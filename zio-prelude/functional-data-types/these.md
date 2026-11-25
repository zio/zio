# These

> `These[A, B]` is a data type that models a value that can be either a `Left` with a value of type `A`, a `Right` with a value of type `B`, or a `Both` with both a value of type `A` and a value of type `B`.

```scala
sealed trait These[+A, +B]

object These {
  final case class Left[+A](value: A)              extends These[A, Nothing]
  final case class Right[+B](value: B)             extends These[Nothing, B]
  final case class Both[+A, +B](left: A, right: B) extends These[A, B]
}
```

The three cases live in the companion object, so we refer to them as `These.Left`, `These.Right` and `These.Both`. The smart constructors `These.left`, `These.right` and `These.both` return the widened types `These[A, Nothing]`, `These[Nothing, B]` and `These[A, B]`, and are usually the better choice because they help type inference.

You can think of `These` as like `Either` except that it has one additional case. Whereas an `Either` contains either an `A` or a `B` but never both, a `These` can contain both an `A` and a `B`.

## Motivation

`These` is also a way to model a computation that can succeed and still report problems. When we read the `Right` as a success and the `Left` as a failure, `Both` is a success that carries warnings. A configuration loader that falls back to a default but wants to tell the user about it, or a parser that recovers from a malformed field, cannot be expressed with `Either`, which forces a choice between the value and the diagnostics. `These` keeps both. Use `Either` when exactly one outcome is possible, and [ZValidation](./zvalidation.md) when you need to accumulate errors from independent checks together with a log. Use `These` when you only need the three cases, or a simple "value plus errors" result.

A simple example of a situation where `These` might arise is in merging two upstreams in a streaming application. We could be signaled when at least one upstream has data for us and could have a situation where either only the left upstream has data for us, only the right upstream has data for us, or both upstreams have data for us.

Another example of using the `These` data type is in implementing a variant of the `zipAll` operator on collections.

When we zip two collections, if the two collections have different sizes the default `zipWith` operator will just drop the "extra" elements from the larger collection.

```scala
import zio.Chunk
import zio.prelude.These

val left: Chunk[Int] =
  Chunk(1, 2, 3)
// left: Chunk[Int] = IndexedSeq(1, 2, 3)

val right: Chunk[Int] =
  Chunk(4, 5, 6, 7, 8)
// right: Chunk[Int] = IndexedSeq(4, 5, 6, 7, 8)

val zip: Chunk[Int] =
  left.zipWith(right)(_ + _)
// zip: Chunk[Int] = IndexedSeq(5, 7, 9)
```

We can use the `zipAll` operator to allow the caller to specify how they want to handle the situation where the two collections have different sizes. Normally the signature would look something like this:

```scala
def zipAllWith[A, B, C](
  as: Chunk[A],
  bs: Chunk[B]
)(left: A => C, right: B => C, both: (A, B) => C): Chunk[C] =
  ???
```

Now the `both` function will be called as long as there are elements of both collections to zip together. If the left collection is longer then the `left` function will be called for the extra elements of the left collection, and if the right collection is longer then the `right` operator will be called for the extra elements of the right collection.

This works and is basically the signature of the `zipAllWith` operator on `Chunk`, but there is something a little less than ideal here that now we need to spread out the logic for handling these cases across three different functions. It would be nice if we could describe this as a single function.

With `These` we can do just that. We could rewrite the signature of `zipAllWith` like this:

```scala
def zipAllWith[A, B, C](
  as: Chunk[A],
  bs: Chunk[B]
)(f: These[A, B] => C): Chunk[C] =
  ???
```

Now the function `f` bundles up all the logic that was previously in `left`, `right`, and `both`. The caller gets to provide a single function that handles all three of these cases.

The `These` data type is a relatively modest one, but it captures a situation that can arise where we can have both values in addition to one or the other. Otherwise, we would have to create our own data type for each of these situations, but now we can simply use `These`.

## Construction

The smart constructors build a `These` from its parts or from other types. We use `These.left`, `These.right` and `These.both` for the three cases, and `These.fromEither` and `These.fromOption` to convert from standard types. `These.fromOptions` takes two options and returns an `Option[These[A, B]]`, which is `None` only when both options are empty:

```scala
import zio.NonEmptyChunk
import zio.prelude._

val onlyLeft: These[String, Int]  = These.left("missing")
// onlyLeft: These[String, Int] = Left(value = "missing")
val onlyRight: These[String, Int] = These.right(42)
// onlyRight: These[String, Int] = Right(value = 42)
val bothSides: These[String, Int] = These.both("defaulted", 42)
// bothSides: These[String, Int] = Both(left = "defaulted", right = 42)

These.fromEither(Left("bad"): Either[String, Int])
// res1: These[String, Int] = Left(value = "bad")

These.fromOptions(Some("a"), Option.empty[Int])
// res2: Option[These[String, Int]] = Some(value = Left(value = "a"))
```

## Accumulating Errors

When `These` models a result with errors, the `Left` type must be able to combine errors. `These#flatMap`, `These#zip` and the related operators require an `Associative` instance for the error type and combine the errors of both sides with it. `NonEmptyChunk` has such an instance, and `These.leftNonEmptyChunk` and `These.bothNonEmptyChunk` wrap a single error in one. The following function parses a port, failing on text that is not a number and warning about ports that need privileges:

```scala
def parsePort(text: String): These[NonEmptyChunk[String], Int] =
  text.toIntOption match {
    case None                  => These.leftNonEmptyChunk(s"not a number: $text")
    case Some(port) if port < 1024 => These.bothNonEmptyChunk(s"port $port needs privileges", port)
    case Some(port)            => These.right(port)
  }
```

Next, we parse two ports together. If both are numbers we get a value, and every warning from either side is kept:

```scala
parsePort("8080").zip(parsePort("8081"))
// res3: These[NonEmptyChunk[String], (Int, Int)] = Right(value = (8080, 8081))

parsePort("80").zip(parsePort("443"))
// res4: These[NonEmptyChunk[String], (Int, Int)] = Both(
//   left = NonEmptyChunk(port 80 needs privileges, port 443 needs privileges),
//   right = (80, 443)
// )

parsePort("80").zip(parsePort("x"))
// res5: These[NonEmptyChunk[String], (Int, Int)] = Left(
//   value = NonEmptyChunk(port 80 needs privileges, not a number: x)
// )
```

A `Left` stops the computation, like `Either`, but a `Both` carries on with its value and keeps its errors. `These#toEither` and `These#toOption` drop the errors of a `Both` and keep the value. `These#fold` handles the three cases with three functions, and `These#map`, `These#mapLeft` and `These#bimap` transform the values without changing the shape.

## Writing zipAllWith

The earlier signature with a single function over `These` can be implemented using the three-function form of `Chunk#zipAllWith`. Each of the three cases becomes one case of `These`:

```scala
import zio.Chunk

def zipAllThese[A, B, C](as: Chunk[A], bs: Chunk[B])(f: These[A, B] => C): Chunk[C] =
  as.zipAllWith(bs)(a => f(These.left(a)), b => f(These.right(b)))((a, b) => f(These.both(a, b)))

zipAllThese(Chunk(1, 2, 3), Chunk("a"))(_.fold(n => s"only $n", s => s"only $s")((n, s) => s"$n$s"))
// res6: Chunk[String] = IndexedSeq("1a", "only 2", "only 3")
```

ZIO Prelude's own `ForEach#zipAll` and `ForEach#zipAllWith` use this shape for any collection that has a `ForEach` instance, passing a `These` to the function in place of three separate callbacks.

## Instances

`These[A, _]` has `Covariant`, `ForEach`, `IdentityBoth` and `IdentityFlatten` instances, among others, when `A` has an `Associative` instance, so it works with the generic operators of ZIO Prelude. `These[A, B]` itself has `Equal`, `Hash`, `Debug`, `Associative` and `Commutative` instances when the component types do.

## Related Abstractions

`Either` is the two-case relative of `These`. [ZValidation](./zvalidation.md) is the data type to use when a computation accumulates errors and logs and a failure must stop it. [Associative](../functional-abstractions/concrete-types/associative.md) describes how the errors of two `These` values combine.
