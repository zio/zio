# Introduction

> ZIO Prelude features a next generation approach to functional abstractions. This approach is based on the following ideas:

ZIO Prelude features a next generation approach to functional abstractions. This approach is based on the following ideas:

1. **Algebraic** - Abstractions should describe fundamental algebraic properties.
2. **Compositional** - These abstractions should describe properties that are orthogonal to each other, allowing definition of higher level abstractions as the composition of more basic ones.
3. **Lawful** - Abstractions should be defined in terms of laws.

The functional abstractions in ZIO Prelude can be broadly divided into two categories.

- **[Abstractions For Concrete Types](concrete-types/index.md)** - These abstractions define properties of concrete types, such as `Int` and `String`, as well as ways of combining those values.
- **[Abstractions For Parameterized Types](parameterized-types/index.md)** - These abstractions define properties of parameterized types such as `List` and `ZIO` and ways of combining them.

As we will see, there is a deep symmetry between the abstractions defined on concrete and parameterized types, such as concepts of _associative operations_, _commutative operations_, and _identity_. This reflects the fundamental nature of these algebraic properties and their ability to unify what were previously separate concepts.

## Abstractions

An _abstraction_ describes some common structure that different data types share. In Scala, we can encode this using a trait that describes that common structure in terms of a set of operators as well as laws that those operators must follow.

For example, we can think of many data types that share the structure of having an associative combining operation. Integer addition is associative, as is string concatenation and list concatenation, among others.

We can describe this common structure using the `Associative` trait.

```scala
trait Associative[A] {
  def combine(left: => A, right: => A): A
}
```

We can then define various concrete values that extend this trait to describe how different data types share this common structure.

```scala
val IntAssociative: Associative[Int] =
  new Associative[Int] {
    def combine(left: => Int, right: => Int): Int =
      left + right
  }
// IntAssociative: Associative[Int] = repl.MdocSession$MdocApp$$anon$1@28717b33
```

Note however that the signature of the trait is not sufficient to define the abstraction.

The signature merely says that we must take two `A` values and return an `A` value. It doesn't say anything about what this combining operation is supposed to do with the value.

With just that signature we could do anything we want in the implementation of `combine` such as subtracting one integer from the other, which is definitely not associative.

```scala
val IntNotAssociative: Associative[Int] =
  new Associative[Int] {
    def combine(left: => Int, right: => Int): Int =
      left - right // don't do this
  }
// IntNotAssociative: Associative[Int] = repl.MdocSession$MdocApp$$anon$2@1993d53d
```

This shows that abstractions are not meaningful without laws. Abstractions describe some common structure that is shared between different data types but without laws we don't know what this structure is supposed to be.

In this case the law is that the combining operation must be associative, which we can write in pseudocode as.

```scala
(a <> b) <> c === a <> (b <> c)
```

Here `<>` represents the combining operation and `a`, `b`, and `c` represent any possible combination of values of the given type.

Every abstraction in ZIO Prelude is described by a trait like the one above and is defined in terms of a set of laws.

ZIO Prelude provides **instances for these abstractions** for a variety of types from ZIO and the Scala standard library. ZIO Prelude also provides **tools for testing** that instances of an abstraction satisfy the appropriate laws.

## Using Abstractions

There are several ways you can use the abstractions described in this library.

The first and most direct, which actually does not require depending on ZIO Prelude at all, is to use these abstractions as inspiration for defining operators on your own data type.

The common structure described by these abstractions exists independent of any library. You don't need ZIO Prelude to define an associative combining operation on your own data type.

However, thinking about whether an associative combining operation exists for your data type, and what it would look like, can help you write better code.

As a simple example, say you want to compute the average of values from some large data set, and you would like to split the work up between different concurrent processes or possibly even different nodes in a distributed network.

Your first stab at the accumulator for the running average might look like this:

```scala
case class RunningAverage(value: Double)
```

However, if you think about it for a minute you will realize that this data type does not support an associative combining operation for combining two averages. This is going to be a serious problem because it means the result is not going to be well-defined if you combine averages from different processes or nodes.

Thinking about the abstractions in ZIO Prelude you might come up with a representation like this:

```scala
case class RunningAverage(sum: Double, count: Int) { self =>
  def average: Double =
    sum / count
  def combine(that: RunningAverage): RunningAverage =
    RunningAverage(self.sum + that.sum, self.count + that.count)
}

object RunningAverage {
  val empty: RunningAverage =
    RunningAverage(0.0, 0)
}
```

Now this data type does have an associative combining operation. In fact the combining operation is both associative and commutative and has an identity element.

This will make it much easier for you to solve your problem because now the different processes or nodes can compute the averages for their partitions independently, and you can combine them in any order.

And you didn't need to use any code from ZIO Prelude to do this. ZIO Prelude was hopefully just a good source of ideas of different algebraic properties that can exist and how they can be important.

This is a great way to get started with functional abstractions. Your colleagues don't have to learn anything new, you just get to write better code because you are taking advantage of these algebraic properties.

This is also the approach taken by ZIO ecosystem libraries.

ZIO ecosystem libraries generally do not directly expose any functional abstractions but still expose a highly compositional interface because their design is based on algebraic properties like this. Users don't have to learn about these abstractions unless they want to, they just get to benefit from better library design.

## Using Type Classes

The second way you can use the abstractions in ZIO Prelude is by leveraging the _type classes_ defined in the library to take the boilerplate out of your own code.

Type classes are a way of encoding functional abstractions in Scala and other programming languages. In the type class pattern, we take the same code as above but define the instances of the type class as `implicit`.

```scala

trait Associative[A] {
  def combine(left: => A, right: => A): A
}

object Associative {

  implicit val IntAssociative: Associative[Int] =
    new Associative[Int] {
      def combine(left: => Int, right: => Int): Int =
        left + right
    }

  implicit def ListAssociative[A]: Associative[List[A]] =
    new Associative[List[A]] {
      def combine(left: => List[A], right: => List[A]): List[A] =
        left ::: right
    }
}
```

If the instance of the type class depends on other parameters, like the `A` in `ListAssociative` we define it as an `implicit def`. Otherwise, we define it as an `implicit val`.

We can think of the `implicit` keyword as associating the type `Int` with the value `IntAssociative`. So now if we ask the Scala compiler for the `Associative` instance for `Int` it will be able to find it.

In the type class pattern we also typically define extension methods that will be available on any data type for which an instance of the type class is defined.

```scala
implicit final class AssociativeSyntax[A](private val self: A) {
  def <>(that: => A)(implicit associative: Associative[A]): A =
    associative.combine(self, that)
}
```

This machinery allows us to use the `<>` operator to combine values of any type as long as an `Associative` instance is defined for it.

```scala
val int: Int =
  1 <> 2
// int: Int = 3

val list: List[Int] =
  List(1, 2, 3) <> List(4, 5, 6)
// list: List[Int] = List(1, 2, 3, 4, 5, 6)
```

Of course, we didn't really need all of this machinery to add two numbers or concatenate two lists, but where this pattern gets powerful is when we can use it to combine more complex data types in a principled way.

For example, say we have an application where users can vote on content they are interested in learning more about. We might have a data structure to keep track of the number of votes for different topics like this.

```scala
final case class Topic(value: String)
final case class Votes(value: Int)
final case class VoteMap(map: Map[Topic, Votes])
```

A common thing we might want to do is combine two `VoteMap` values, for example if the user has a local copy of the `VoteMap` and we want to update it with a new batch of votes from the server.

We could do that manually like this.

```scala
final case class Topic(value: String)

final case class Votes(value: Int) { self =>
  def combine(that: Votes): Votes =
    Votes(self.value + that.value)
}

final case class VoteMap(map: Map[Topic, Votes]) { self =>
  def combine(that: VoteMap): VoteMap =
    VoteMap(that.map.foldLeft(self.map) { case (map, (topic, votes)) =>
      map + (topic -> map.getOrElse(topic, Votes(0)).combine(votes))
    })
}
```

This isn't the worst, but it isn't really the kind of code we want to be writing. We want to be thinking about the logic of our application rather than how to combine maps.

This is where ZIO Prelude can help.

The way we're combining these maps actually follows a pattern. If a key is in a single map we include it in the combined map with its associated key and if a key is in both maps we include it in the combined map with the result of combining the values associated with that key.

We might see that ourselves, but it would be hard to generalize that logic in a way that is worth factoring out. How often are we going to combine maps like this and what exactly does it mean to combine the keys?

Let's look at how ZIO Prelude can help us clean this up.

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
  implicit val VoteMapAssociative: Associative[VoteMap] =
    new Associative[VoteMap] {
      def combine(left: => VoteMap, right: => VoteMap): VoteMap =
        VoteMap(left.map <> right.map)
    }
}
```

All of that logic of combining the two maps just goes away!

ZIO Prelude knows that we can define an associative combining operation for any two values of type `Map[A, B]` as long as there is an associative combining operation for the `B` values. All we have to do is tell ZIO Prelude how to combine the `B` values, which in this case is quite simple, and it can do the rest.

This is a great example of the practical value that ZIO Prelude can bring. It took this low level logic of how to combine these two maps and just handled it for us.

## Using Generic Programming

The third way you can use the abstractions in ZIO Prelude is by leveraging type classes to do generic programming at the level of these abstractions.

For example, you might find yourself doing a lot of "map reduce" type operations on collections like this.

```scala
def wordCount(lines: List[String]): Int =
  lines.map(_.split(" ").length).sum
```

You might like this way of working with collections and wonder how you can generalize it.

A first step could be to recognize that `sum` is just a particular combining operation that has an identity element. You could then use the `Identity` abstraction in ZIO Prelude to generalize over data types that support this kind of combining operation with an identity element.

```scala
def mapReduce[A, B](as: List[A])(f: A => B)(implicit identity: Identity[B]): B =
  ???
```

You might want to go even further though and generalize over the collection type. You can clearly implement a similar operator for a `Vector` or another collection type so how do you generalize over that?

You could do that with ZIO Prelude's `ForEach` abstraction, which describes parameterized data types with some structure where the elements in the structure can be replaced while preserving the structure itself.

Using this, you could rewrite your operator like so.

```scala
def mapReduce[F[+_]: ForEach, A, B: Identity](as: F[A])(f: A => B): B =
  ???
```

This example illustrates some of the benefits as well as the pitfalls of generic programming using type classes.

If you want to do generic programming in terms of type classes, ZIO Prelude can go as far as you want to go.

However, there is a definite trade off in these three snippets.

The first one is overly specific but is understandable to any Scala programmer. The last one is beautiful and elegant if you understand the necessary concepts but incomprehensible otherwise.

In addition, there is a danger in using type classes to do generic programming that we reinvent the wheel. The generalized "map reduce" operator we developed is just the existing `foldMap` operator from ZIO Prelude!

```scala
def mapReduce[F[+_]: ForEach, A, B: Identity](as: F[A])(f: A => B): B =
  as.foldMap(f)
```

This illustrates the risk that sufficiently general abstractions or operators are likely to already be defined by a functional programming library.

None of this is meant to argue against this style of generic programming but merely to point out that it is one of several ways to use these functional abstractions and none of them are necessarily "better" than others. The right approach to using these abstractions is the one that works for you and your team.
