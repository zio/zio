# Equal

> `Equal[A]` describes the ability to compare two values of type `A` for equality.

`Equal[A]` describes the ability to compare two values of type `A` for equality.

Its signature is:

```scala
trait Equal[-A] {
  def equal(left: A, right: A): Boolean
}
```

If we import `zio.prelude._` we can use the `===` and `!==` operators to compare any two values of type `A` for equality as long as there is an `Equal` instance defined for them.

This has several advantages over using the `==` and `!=` defined in the Scala standard library.

First, using `Equal` prevents bugs because it stops nonsensical code that attempts to compare unrelated types from compiling.

Using `==` we can compare any two values for equality, even if they are unrelated types. For example, this code has a bug.

```scala
final case class SequenceNumber(value: Int)

def isInitial(sequenceNumber: SequenceNumber): Boolean =
  sequenceNumber == 0
// warning: comparing values of types repl.MdocSession.MdocApp.SequenceNumber and Int using `==` will always yield false
//   sequenceNumber == 0
//   ^^^^^^^^^^^^^^^^^^^
```

We're trying to determine if this is the initial sequence number , but we're comparing the `sequenceNumber`, which is a `SequenceNumber`, with `0`, which is an `Int`. So this comparison will always be false even if the `sequenceNumber` is `SequenceNumber(0)` which is what we are trying to test for.

This snippet may generate a warning but it is still valid Scala code.

In contrast, ZIO Prelude's `Equal` abstraction catches the bug and prevents this code from even compiling.

```scala

final case class SequenceNumber(value: Int)

object SequenceNumber {
  implicit val SequenceNumberEqual: Equal[SequenceNumber] =
    Equal.default
}

def isInitial(sequenceNumber: SequenceNumber): Boolean =
  sequenceNumber === 0
// error: No implicit Equal defined for Any.
//   sequenceNumber === 0
//   ^^^^^^^^^^^^^^^^^^^^
```

To get the code to compile we have to fix the bug.

```scala

case class SequenceNumber(value: Int)

object SequenceNumber {

  val initial: SequenceNumber =
    SequenceNumber(0)

  implicit val SequenceNumberEqual: Equal[SequenceNumber] =
    Equal.default
}

def isFirst(sequenceNumber: SequenceNumber): Boolean =
  sequenceNumber === SequenceNumber.initial
```

A second advantage is catching bugs when we try to compare two values of a type that doesn't have a meaningful notion of equality. For example, it doesn't make sense to compare two Scala functions for equality, but we can still do it with `==`.

In contrast, comparing two functions with `===` will result in a compilation error, preventing us from making this mistake.

A third advantage of the `Equal` abstraction is that it lets us use our own notion of equality for data types that are not under our control. We may be working with data types from other libraries that do not implement a reasonable notion of equality, for example using reference equality instead of value equality.

If we are using `==` we can't do anything about that because `==` is always based on how the data type implements `equals`. But if we are using `===` from ZIO Prelude we can define our own `Equal` instance that implements a more sensible notion of equality.

## Defining Equal Instances

Equal instances are automatically available for data types from the Scala library and ZIO with a meaningful notion of equality, as well as data types made up of those types.

We can always check if an instance of a type class exists for a given data type by using the `apply` operator to tell the Scala compiler to find the appropriate instance for us. This will result in a compilation error if the instance cannot be found.

```scala
Equal[List[Either[String, Int]]]
// res3: Equal[List[Either[String, Int]]] = zio.prelude.Equal$$anonfun$make$2@320f2e42
```

```scala
Equal[String => Int]
```

Defining `Equal` instances for your own data types is generally quite easy.

If you have already defined a sensible `equals` method for your data type then you can just use the `default` operator to construct an `Equal` instance as we saw in the example above involving the `SequenceNumber`.

Since the `SequenceNumber` is a case class it already has a well defined `equals` method as long as each value in the case class also has a well defined `equals` method. Equality for `Int` values is definitely well behaved so there is nothing we have to do here other than use the `default` operator.

If we don't want to use the default definition of equality we can use the `make` operator to define a new `Equal` instance based on our own definition of equality.

This is particularly useful if we are defining an `Equal` instance for a parameterized type and want to compare the types it is parameterized on for equality using their own `Equal` instances.

For example, say we have a class class to represent a pair of values.

```scala
case class Pair[A, B](first: A, second: B)
```

The `equals` method on a case class delegates to the `equals` method on each of its fields and we don't know if `A` or `B` have sensible definitions of `equals` since they are generic. So we want to instead define our own `Equal` instance directly like this:

```scala
object Pair {
  implicit def PairEqual[A: Equal, B: Equal]: Equal[Pair[A, B]] =
    Equal.make { (left, right) =>
      left.first === right.first && left.second === right.second
    }
}
```

Notice how we had to require that `Equal` instances existed for `A` and `B`. We can't just compare any `Pair` for equality, we can only do it if each field of the pair can also be compared for equality.

Including `Equal` after the definition of the `A` and `B` type parameters here is called a _context_ bound_ and is another way of saying that an implicit instance of `Equal[A]` and `Equal[B]` must exist. It can make our code a little cleaner when we don't need to do anything with the instance other than require that it exists, in this case for the `===` operator.

If we can convert our data type to another data type for which an `Equal` instance is already defined we can make this even simpler using the `contramap` operator.

In the example above, we can convert any `Pair` into a tuple without losing any information. And ZIO Prelude already defines an `Equal` instance for any tuple of data types for which `Equal` instances are defined.

So we can rewrite our implementation of `PairEqual` like this:

```scala
object Pair {
  implicit def PairEqual[A: Equal, B: Equal]: Equal[Pair[A, B]] =
    Equal[(A, B)].contramap(pair => (pair.first, pair.second))
}
```

The `contramap` operator essentially says if we can compare two tuples for equality, and we can turn a pair into a tuple, then we can always compare two pairs for equality by turning them both into tuples and comparing the tuples for equality.

The `contramap` operator can be useful to create `Equal` instances from existing ones but the way we "work backwards" from the type we know how to compare for equality to the `Equal` instance we are trying to make can be confusing initially. So it is fine to just use the `make` operator as discussed above, which essentially does the same thing.

With the instances already defined for all types in ZIO and the Scala standard library with meaningful definitions of equality and the ability to define instances for your own data types with `make` and `contramap` it is easy to take advantage of the additional type safety that using `===` provides.
