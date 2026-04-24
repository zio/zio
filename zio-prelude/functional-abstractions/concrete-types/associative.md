# Associative

> `Associative[A]` describes a way of combining two values of type `A` that is associative.

`Associative[A]` describes a way of combining two values of type `A` that is associative.

```scala
trait Associative[A] {
  def combine(left: => A, right: => A): A
}
```

If we import `zio.prelude._` we can use the `<>` operator to combine any two values of type `A` that have an `Associative` instance defined for them.

The `combine` operator must be associative, meaning that if we combine `a` with `b` and then combine the result with `c` we must get the same value as if we combine `b` with `c` and then combine `a` with the result.

```scala
(a <> b) <> c === a <> (b <> c)
```

The `Associative` abstraction allows us to combine values of a data type to build a new value of that data type with richer structure.

A variety of data types can be combined in an associative way:

```scala

val string: String =
  "Hello, " <> "world!"
// string: String = "Hello, world!"

val chunk: Chunk[Int] =
  Chunk(1, 2, 3) <> Chunk(4, 5, 6)
// chunk: Chunk[Int] = IndexedSeq(1, 2, 3, 4, 5, 6)
```

The `Associative` abstraction provides several advantages over using existing operators like the `++` operator on `Chunk` directly.

First, `Associative` allows us to combine more complex data types as long as the data types they are composed of can be combined in an associative way.

```scala
case class Topic(value: String)

case class Votes(value: Int)

object Votes {
  implicit val VotesAssociative: Associative[Votes] =
    new Associative[Votes] {
      def combine(left: => Votes, right: => Votes): Votes =
        Votes(left.value + right.value)
    }
}

case class VoteMap(map: Map[Topic, Votes])

object VoteMap {
  def combine(left: VoteMap, right: VoteMap): VoteMap =
    VoteMap(left.map <> right.map)
}
```

If we didn't have ZIO Prelude we would have to implement the operator to combine two `VoteMap` values ourselves. This would require some relatively low level logic that would look like this.

```scala
def combine(left: VoteMap, right: VoteMap): VoteMap =
  VoteMap(right.map.foldLeft(left.map) { case (map, (k, v)) =>
    map + (k -> map.get(k).fold(v)(v1 => Votes(v.value + v1.value)))
  })
```

This code isn't the worst. It uses operators like `foldLeft` to do this combining of the two maps in a relatively high level way.

But we're still having to implement our own collection operators, taking our attention away from implementing our business logic. And it would be hard to implement this without at least having to take a minute and to check our logic, especially if we are less familiar with immutable collection operators.

ZIO Prelude lets us avoid all of this because it knows how to combine two maps as long as there is a way to combine the map values. And it was quite simple to describe how we combine the map values, we just add them.

Second, the `Associative` abstraction lets us generalize over different ways of combining values if we want to.

For example, we could define an operator for reducing any `NonEmptyChunk` to a summary value like this.

```scala

def reduce[A: Associative](as: NonEmptyChunk[A]): A =
  as.reduce(_ <> _)
```

We can then reduce any `NonEmptyChunk` to a summary value as long as there is a way of combining the elements of the `NonEmptyChunk`, whether those elements are strings or vote maps.

So far we have not described some very basic ways of combining that are associative, such as integer addition.

The reason for this is that some data types support more than one way of combining them that is associative. For example, both integer addition and multiplication are associative.

This creates a potential issue when using the type class pattern because the Scala compiler looks up implicit values based on their type, so if there are two different implicit values of a given type the Scala compiler does not know which one to use.

For example, here is what happens if we try to define `Associative` instances for `Int` for both addition and multiplication.

```scala
implicit val IntSumAssociative: Associative[Int] =
  new Associative[Int] {
    def combine(left: => Int, right: => Int): Int =
      left + right
  }

implicit val IntProdAssociative: Associative[Int] =
  new Associative[Int] {
    def combine(left: => Int, right: => Int): Int =
      left * right
  }

2 <> 3
// error: ambiguous implicit values:
//  both value IntSumAssociative in object MdocApp of type zio.prelude.Associative[Int]
//  and value IntProdAssociative in object MdocApp of type zio.prelude.Associative[Int]
//  match expected type zio.prelude.Associative[Int]
// 2 <> 3
// ^^^^^^
```

This makes sense because there are indeed two different instances of `Associative[Int]` and there is no basis for choosing between them.

We could just define one of these instances as "primary" like some other functional programming libraries do and relegate the other to second class status but that would be arbitrary and reflect a lack of compositionality.

Instead we solve the problem in a way that fits with Scala's implicit resolution mechanism. If the Scala compiler looks up implicit values based on their types, then if we want two values we need two types.

We can easily do this with ZIO Prelude's new type functionality.

New types allow us to define new types that "wrap" existing types in a way that has no overhead at runtime. We can also define these new types so that the Scala compiler actually knows that the new type is a subtype of the underlying type.

Using this technique, we can define new types `Sum` and `Prod` that can be combined using addition and multiplication, respectively. ZIO Prelude provides these and similar new types such as `And` and `Or` for logical conjunction and disjunction in the `zio.prelude.newtypes` package.

We can wrap any existing type in a new type using the `apply` or `wrap` operators on the new type object.

```scala

val sumInt: Sum[Int] =
  Sum(1)
// sumInt: Sum[Int] = 1

val prodInt: Prod[Int] =
  Prod.wrap(2)
// prodInt: Prod[Int] = 2
```

We can unwrap any new type to get back the original type using the `unwrap` operator on the new type object.

```scala
val int: Int =
  Sum.unwrap(sumInt)
// int: Int = 1
```

However, we will typically not need to do that for new types like `Sum` and `Prod` because they are subtypes of `Int`.

```scala
val int: Int =
  prodInt
// int: Int = 2
```

Let's use these types to solve our problem from above.

```scala
val sum: Int =
  Sum(2) <> Sum(3)
// sum: Int = 5

val product: Int =
  Prod(2) <> Prod(3)
// product: Int = 6
```

These variants don't just work for `Int`, they work for any numeric data type in the Scala standard library.

```scala
val sum: Double =
  Sum(2.0) <> Sum(3.0)
// sum: Double = 5.0

val product: Double =
  Prod(2.0) <> Prod(3.0)
// product: Double = 6.0
```

Note that the associativity of addition and multiplication for `Double` values is subject to floating point rounding errors. ZIO Prelude assumes that if we are working with "lossy" data types like this we are aware of these issues and provides these instances for us, unlike some other functional programming libraries.

ZIO Prelude provides several other new types that you can use to define how you want to combine various data types.

The `And` and `Or` new types mentioned above let us define `Boolean` values that can be combined using logical conjunction and disjunction.

```scala
val and: Boolean =
  And(true) <> And(false)
// and: Boolean = false

val or: Boolean =
  Or(true) <> Or(false)
// or: Boolean = true
```

Another way of combining two values that is associative is taking the first or the last of two values.

```scala
val first: String =
  First("Hello") <> First("World")
// first: String = "Hello"

val last: String =
  Last("Hello") <> Last("World")
// last: String = "World"
```

The minimum and maximum of two values for which an ordering is defined also constitutes an associative `combine` operator.

```scala
val min: Int =
  Min(1) <> Min(2)
// min: Int = 1

val max: Int =
  Max(1) <> Max(2)
// max: Int = 2
```

These new types are particularly useful when dealing with more complex data types to specify how we want to combine part of them.

For example, let's go to our example with the `VoteMap` but say that now we are not going to define the additional type for `Topic` and `Votes`.

```scala
case class VoteMap(map: Map[String, Int])
```

We need to tell ZIO Prelude how we want to combine `Int` values. In this case we want to use the sum.

We do this by using the `wrapAll` operator. Just like `wrap` wraps a single value in a new type, `wrapAll` wraps a whole collection of values in a new type.

It does this without traversing the collection because ZIO Prelude knows internally that the new type and the underlying type are the same. This way we avoid any runtime overhead for using these abstractions.

```scala
object VoteMap {
  implicit val VoteMapAssociative: Associative[VoteMap] =
    new Associative[VoteMap] {
      def combine(left: => VoteMap, right: => VoteMap): VoteMap =
        VoteMap(Sum.wrapAll(left.map) <> Sum.wrapAll(right.map))
    }
}
```

You can see the documentation on new types for additional information about new types in general. But the material here should give you what you need to combine values of even complex data types and define your own instances of the `Associative` abstraction for your own data types.

If you are interested in combining values of collections it is also worth checking out the `ForEach` functional abstraction, which describes ways to iterate over collection types. The `ForEach` abstraction comes with a variety of built in operators for combining collection types using an associative operator.

For example, using `ForEach`, `Associative`, and new types we could count the total number of words in a collection of lines like this:

```scala

def wordCount(lines: NonEmptyChunk[String]): Int =
  lines.reduceMap(line => Sum(line.split(" ").length))
```

This maps each element in a collection to a new data type for which an `Associative` instance is defined and then reduces all of those values to a single summary value with the `combine` operator.

We could instead count the number of occurrences of each word like this:

```scala
def wordCount(lines: NonEmptyChunk[String]): Map[String, Int] =
  lines.reduceMap { line =>
    Sum.wrapAll(line.split(" ").groupBy(identity).view.mapValues(_.length).toMap)
  }
```

This version is exactly the same except we mapped the elements to a different value. ZIO Prelude automatically applied the appropriate `combine` operator.

This is a great example of how these abstractions can make it easy to combine values of different data types, cutting the boilerplate out of our code and reducing opportunities for bugs.
