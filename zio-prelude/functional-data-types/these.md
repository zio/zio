# These

> `These[A, B]` is a data type that models a value that can be either a `Left` with a value of type `A`, a `Right` with a value of type `B`, or a `Both` with both a value of type `A` and a value of type `B`.

`These[A, B]` is a data type that models a value that can be either a `Left` with a value of type `A`, a `Right` with a value of type `B`, or a `Both` with both a value of type `A` and a value of type `B`.

```scala
sealed trait These[+A, +B] {
  case class Left[+A](a: A)           extends These[A, Nothing]
  case class Right[+B](b: B)          extends These[Nothing, B]
  case class Both[+A, +B](a: A, b: B) extends These[A, B]
}
```

You can think of `These` as like `Either` except that it has one additional case. Whereas an `Either` contains either an `A` or a `B` but never both, a `These` can contain both an `A` and a `B`.

A simple example of a situation where `These` might arise is in merging two upstreams in a streaming application. We could be signaled when at least one upstream has data for us and could have a situation where either only the left upstream has data for us, only the right upstream has data for us, or both upstreams have data for us.

Another example of using the `These` data type is in implementing a variant of the `zipAll` operator on collections.

When we zip two collections, if the two collections have different sizes the default `zipWith` operator will just drop the "extra" elements from the larger collection.

```scala

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
