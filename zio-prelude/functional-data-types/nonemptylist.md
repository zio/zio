# NonEmptyList

> `NonEmptyList` represents a `List` that is guaranteed not to be empty.

`NonEmptyList` represents a `List` that is guaranteed not to be empty.

This is useful to allow more accurate domain modeling in situations where we know a collection is not empty.

For example, we might be handling a collection of errors. If an error has occurred at all then we know the collection is not empty.

However, if we are working with a collection type from the Scala standard library such as `List`, `Vector`, or `Iterable` then the Scala compiler does not know that. It will force us to handle the case where the collection is empty even though we know that cannot occur.

Or perhaps we are implementing a batched API and need to handle a collection of requests. We know the collection is not empty, or we would not have been called at all, but now we have to handle the case of an empty collection.

Sometimes there are sensible defaults in these cases, but other times we are forced to resort to potentially unsafe operators like `head` or to throw exceptions in branches of logic we know should never occur. This wastes developer time, makes code harder to understand, and encourages bad coding practices.

To avoid these problems we want a data type that is specific enough to describe at the type level what we know to be true, in this case that a collection is not empty.

We can try to represent this using the `::` case of `List` from the Scala standard library but unfortunately `::` itself has extremely poor ergonomics. It is hard to construct `::` values, and it is easy for operators to forget the more specific type. This is where `NonEmptyList` comes in.

A `NonEmptyList` looks like this:

```scala
sealed trait NonEmptyList[+A]

object NonEmptyList {
  case class Single[A](head: A)                      extends NonEmptyList[A]
  case class Cons[A](head: A, tail: NonEmptyList[A]) extends NonEmptyList[A]
}
```

We can see that `NonEmptyList` looks very similar to `List` except that there is no `Nil` case. A `NonEmptyList` is either a `Single` with a single value of type `A` or a `Cons` with a value of type `A` and a `NonEmptyList` of further values of type `A`.

Generally you should just be able to use higher level operators on `NonEmptyList` but if you ever want to implement your own operators on `NonEmptyList` you can do so by pattern matching on a `NonEmptyList` just like you would on a `List`.

## Constructing NonEmptyList Values

The easiest way to construct a `NonEmptyList` is with the `apply` operator.

```scala
trait NonEmptyList[+A]

object NonEmptyList {
  def apply[A](a: A, as: A*): NonEmptyList[A] =
    ???
}
```

If we already have another collection type we can convert it into a `NonEmptyList` using the `fromIterableOption` operator.

```scala
object NonEmptyList {
  def fromIterableOption[A](as: Iterable[A]): Option[NonEmptyList[A]] =
    ???
}
```

Notice that `fromIterableOption` returns an `Option[NonEmptyList[A]]` because the original collection might be empty, and so we might not be able to construct a `NonEmptyList` from it.

If we know the collection is not empty we can use the `fromIterable` operator and provide the first element of the collection to establish that it is not empty.

```scala
object NonEmptyList {
  def fromIterable[A](a: A, as: Iterable[A]): NonEmptyList[A] =
    ???
}
```

We can also construct a `NonEmptyList` from the `::` case of a `List`.

```scala
object NonEmptyList {
  def fromCons[A](as: ::[A]): NonEmptyList[A] =
    ???
}
```

This is useful if we already have a `::` value, for example from a `ZIO` operator like `ZIO.validate` that returns `::[E]` if it fails.

In addition to these operators for constructing `NonEmptyList` values from other values there are a couple of operators that are useful for building up `NonEmptyList` values.

The `single` operator lets us construct a `NonEmptyList` from a single value. The `cons` operator lets us construct a new `NonEmptyList` from an existing `NonEmptyList` and a new head value.

```scala
object NonEmptyList {
  def cons[A](a: A, as: NonEmptyList[A]): NonEmptyList[A] =
    ???
  def single[A](a: A): NonEmptyList[A] =
    ???
}
```

These operators let us build larger `NonEmptyList` values from smaller ones in recursive algorithms much like the `::` operator and `Nil` allow us to do for `List` values.

## Working With NonEmptyList Values

The operators on `NonEmptyList` values are essentially the same as those on `List`. So if you know how to work with a `List` you know how to work with a `NonEmptyList`.

The main difference between a `NonEmptyList` and a `List` is that many operators are safe on `NonEmptyList` that are not safe on `List`.

For example, we would generally not consider it a good practice to call `head` on a `List` because that could throw an exception. However, it is fine to call `head` on a `NonEmptyList` because it is always guaranteed to contain at most one value.

Similarly, we can use `reduceLeft` or `reduceRight` to reduce the elements of a `NonEmptyList` to a summary value even though this would not be safe to do on a `List` because we know there is always at least one element.

The other difference between a `NonEmptyList` and a `List` is that operators on `NonEmptyList` will try to preserve the knowledge that the list is not empty whenever possible.

For example, the `map` operator on `NonEmptyList` looks like this:

```scala
trait NonEmptyList[+A] {
  def map[B](f: A => B): NonEmptyList[B]
}
```

The return type is also a `NonEmptyList` because `map` transforms each element with a function but does not change the number of elements, so if the original list was not empty then we know that the resulting list is not empty.

However, sometimes it is not possible to preserve this information because of the nature of the operator.

```scala
trait NonEmptyList[+A] {
  def dropWhile(f: A => Boolean): List[A]
}
```

The `dropWhile` operator returns a `List[A]` instead of a `NonEmptyList[A]` because it could drop all of the elements of the list, resulting in a list that is empty. So we can no longer return a `NonEmptyList`.

The final difference between `NonEmptyList` and `List` is some additional operators are provided that take advantage of the fact that we know the list is not empty.

The most notable of these are `reduceMapLeft` and `reduceMapRight`, which take the place of `foldLeft` and `foldRight`.

```scala
trait NonEmptyList[+A] {
  def reduceMapLeft[B](map: A => B)(reduce: (B, A) => B): B
  def reduceMapRight[B](map: A => B)(reduce: (A, B) => B): B
}
```

On standard collections `foldLeft` and `foldRight` are fundamental collection operators that allow us to "tear down" a collection to a summary value by providing an initial value to use if the collection is empty and a way to update that initial value for every element in the collection.

Since we know that a `NonEmptyList` contains at least one value, we don't need to provide an initial value for when the list is empty and can use the `reduceLeft` and `reduceRight` operators. However, these operators don't provide us quite the power we need in a lot of situations because they don't allow the type of the summary value to differ from the type of the elements in the collection.

The `reduceMapLeft` and `reduceMapRight` operators allow us to transform the first value in the `NonEmptyList` to the summary value and then combine each other value with that summary value. This is useful because it allows us to preserve the information that the collection is not empty.

For example, here is how we could use `reduceMapLeft` to convert a `NonEmptyList` to a `NonEmptyChunk`.

```scala

def toNonEmptyChunk[A](as: NonEmptyList[A]): NonEmptyChunk[A] =
  as.reduceMapLeft(a => NonEmptyChunk(a))(_ appended _)
```

This would have been difficult to express with `foldLeft` because we would have had to start with an initial value that was an empty chunk and thus would have gotten back a `Chunk` instead of a `NonEmptyChunk`.

Of course, we could have just used the `toNonEmptyChunk` operator on `NonEmptyList`.

```scala
def toNonEmptyChunk[A](as: NonEmptyList[A]): NonEmptyChunk[A] =
  as.toNonEmptyChunk
```

## Interoperability With Scala Collections

One of the great features of the `NonEmptyList` data type is its excellent integration with the Scala standard library collections framework.

A `NonEmptyList` is implicitly convertible into the `::` case of a `List` so anywhere you need a `List` and provide a `NonEmptyList` it will just work.

```scala

def myExistingAPI(as: List[Int]): String =
  "Some fancy stuff"

val nonEmptyList: NonEmptyList[Int] =
  NonEmptyList(1, 2, 3)
// nonEmptyList: NonEmptyList[Int] = Cons(
//   head = 1,
//   tail0 = Cons(head = 2, tail0 = Single(head = 3))
// )

myExistingAPI(nonEmptyList)
// res2: String = "Some fancy stuff"
```

This is also extremely convenient because it means all operators available on `List` are available on `NonEmptyList`.

For example, the `filter` operator is not implemented directly on `NonEmptyList`. But you can still filter a `NonEmptyList` because we can always view a `NonEmptyList` as a `List`.

```scala
val filtered: List[Int] =
  nonEmptyList.filter(_ % 2 == 0)
// filtered: List[Int] = List(2)
```

The return type of `List[Int]` is as specific as possible because the `filter` operator could filter out all the elements of the list and return a list that is empty.

Thus, `NonEmptyList` lets us work in a way that is as close as possible to being as if `NonEmptyList` was a subtype of `List`, which conceptually it is.
