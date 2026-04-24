# New Types

> ZIO Prelude provides functionality for creating zero overhead new types to allow you to increase the type safety of your application without compromising performance or ergonomics.

ZIO Prelude provides functionality for creating zero overhead new types to allow you to increase the type safety of your application without compromising performance or ergonomics.

A common situation in domain modeling is that we have multiple types in our business domain with the same underlying representation in Scala. For example:

```scala
type AccountNumber  = Int
type SequenceNumber = Int
```

This is not a great situation. We can easily mix up values that represent different types in our business domain, for example providing an `AccountNumber` where we are supposed to use a `SequenceNumber` or vice versa.

The Scala compiler will not help us avoid bugs in these situations because from the perspective of the compiler `AccountNumber` and `SequenceNumber` are the same type, namely `Int`.

To avoid this, we might be tempted to create a separate case class for each of these types like this:

```scala
final case class AccountNumber(value: Int)
final case class SequenceNumber(value: Int)
```

This is an improvement in type safety because `AccountNumber` and `SequenceNumber` are now separate types so providing an `AccountNumber` when we are supposed to use a `SequenceNumber` will result in a compilation error.

However, it comes with costs of its own. Every one of these account numbers and sequence numbers will now allocate an additional object, which can add up over the course of a large application with a complex domain model.

We can try to minimize these allocations using techniques such as extending `AnyVal`, but these approaches can be extremely fragile and can actually result in worse performance than the original code if we are not careful.

Let's see how ZIO Prelude helps us get the best of both worlds here.

## New Types

A new type in ZIO Prelude is a type that has the same underlying representation as another type at runtime but is a separate type at compile time.

We create new types by creating an object that extends `Newtype` and specifying the type that our new type wraps. We then export that type by defining a new type alias that refers to the `Type` within our new object.

```scala

object AccountNumber extends Newtype[Int]
type AccountNumber = AccountNumber.Type

object SequenceNumber extends Newtype[Int]
type SequenceNumber = SequenceNumber.Type
```

The `AccountNumber` and `SequenceNumber` types are now completely distinct from the `Int` type at compile time, even though at runtime they are all `Int` values. 

```scala
val accountNumber: AccountNumber =
  AccountNumber(1)
// accountNumber: AccountNumber = 1

val sequenceNumber: SequenceNumber =
  SequenceNumber(2)
// sequenceNumber: SequenceNumber = 2

def lookup(accountNumber: AccountNumber, sequenceNumber: SequenceNumber) =
  ???
```

We can see this if we try to supply an `AccountNumber` somewhere that a `SequenceNumber` is expected:

```scala
lookup(sequenceNumber, accountNumber)
// error: type mismatch;
//  found   : SequenceNumber
//     (which expands to)  SequenceNumber.Type
//  required: AccountNumber
//     (which expands to)  AccountNumber.Type
// error: type mismatch;
//  found   : AccountNumber
//     (which expands to)  AccountNumber.Type
//  required: SequenceNumber
//     (which expands to)  SequenceNumber.Type
// lookup(sequenceNumber, accountNumber)
//                        ^^^^^^^^^^^^^
```

This is great but how do we construct `AccountNumber` and `SequenceNumber` values and how do we access the underlying `Int` values to work with them?

## Constructing New Types

The easiest way to construct an instance of the new type is to use the `apply` method on the new type object.

```scala
val accountNumber: AccountNumber =
  AccountNumber(1)
// accountNumber: AccountNumber = 1
```

You can think of the new type object as being the "companion object" of the new type and it comes included with an `apply` method to make it easy for us to construct instances of the new type.

If you want to construct a collection of values of the new type from values of the underlying type you can use the `wrapAll` operator.

```scala

val accountNumbers: Chunk[AccountNumber] =
  AccountNumber.wrapAll(Chunk(3, 4, 5))
// accountNumbers: Chunk[AccountNumber] = IndexedSeq(3, 4, 5)
```

The `wrapAll` operator is extremely efficient because it does not have to traverse the collection at all. Internally we know that `AccountNumber` and `Int` are the same type so we can freely convert a `Chunk[Int]` to a `Chunk[AccountNumber]` without having to traverse the collection.

We can also define our own more specialized constructors for our new type in terms of `apply`, `wrap`, and `wrapAll`. The new type object provides a convenient place for us to do this.

For example, we could define a simple constructor for an initial sequence number like this:

```scala

object SequenceNumber extends Newtype[Int] {
  val initial: SequenceNumber =
    SequenceNumber(0)
}
type SequenceNumber = SequenceNumber.Type

val initial: SequenceNumber =
  SequenceNumber.initial
// initial: SequenceNumber = 0
```

This lets us construct instances of the new type in a very ergonomic way, where constructors for the new type are on the new type companion object just like constructors for any other type would be.

## Deconstructing New Types

In addition to constructing instances of the new type we want to be able to access the value underlying the new type to perform operations on it.

We can do this using the `unwrap` operator on the new type companion object.

```scala
val zero: Int =
  SequenceNumber.unwrap(initial)
// zero: Int = 0
```

We can also use the `unwrapAll` operator to convert a collection of values of the new type into the underlying type.

```scala

val sequenceNumbers: Chunk[SequenceNumber] =
  SequenceNumber.wrapAll(Chunk(1, 2, 3))
// sequenceNumbers: Chunk[SequenceNumber] = IndexedSeq(1, 2, 3)

val ints: Chunk[Int] =
  SequenceNumber.unwrapAll(sequenceNumbers)
// ints: Chunk[Int] = IndexedSeq(1, 2, 3)
```

Again this is extremely efficient because we don't have to traverse the collection at all. Internally we know that a `Chunk[SequenceNumber]` is a `Chunk[Int]` so we can just immediately return the original collection unchanged.

## Defining Operators On New Types

By wrapping and unwrapping values of the new type we can define our own operators on new types.

The easiest way to do this is by defining our operators as extension methods inside the new type companion object. These extension methods will automatically be available when we are working with instances of the new type.

```scala

object SequenceNumber extends Newtype[Int] {

  implicit class SequenceNumberSyntax(private val self: SequenceNumber) extends AnyVal {
    def next: SequenceNumber =
      SequenceNumber.wrap(SequenceNumber.unwrap(self) + 1)
  }
}
type SequenceNumber = SequenceNumber.Type

val sequenceNumber: SequenceNumber =
  SequenceNumber(1)
// sequenceNumber: SequenceNumber = 1

val nextSequenceNumber: SequenceNumber =
  sequenceNumber.next
// nextSequenceNumber: SequenceNumber = 2
```

Notice that the `next` operator was available on `SequenceNumber` without having to do any imports because we defined it as an extension method within the `SequenceNumber` object.

The new type companion object is also a convenient place for other operators that we would normally define on the companion object of a type. For example, we could define a `continuous` operator that returns whether a collection of `SequenceNumber` values are continuous.

```scala

object SequenceNumber extends Newtype[Int] {
  def continuous(sequenceNumbers: Iterable[SequenceNumber]): Boolean =
    SequenceNumber.unwrapAll(sequenceNumbers).foldLeft[(Option[Int], Boolean)]((None, true)) {
      case ((None, continuous), current) => (Some(current), continuous)
      case ((Some(previous), continuous), current) => (Some(current), continuous && current == previous + 1)
    }._2
}
type SequenceNumber = SequenceNumber.Type

val sequenceNumbers: Chunk[SequenceNumber] =
  Chunk(SequenceNumber(1), SequenceNumber(2), SequenceNumber(3))
// sequenceNumbers: Chunk[SequenceNumber] = IndexedSeq(1, 2, 3)

val continuous: Boolean =
  SequenceNumber.continuous(sequenceNumbers)
// continuous: Boolean = true
```

Here `continuous` is an operator on a collection of sequence numbers so it didn't make sense to define it as an extension method on an individual sequence number. Defining it in the new type companion object makes it feel like an operator on the companion object of any ordinary data type.

## Defining Instances Of Functional Abstractions For New Types

We can also define instances of functional abstractions for new types such as `Equal`, `Ord`, and `Associative`.

The new type companion object again provides a convenient place to define these instances and ensures that they will automatically be available so that we don't have to import them.

Let's see how we can do this to define an `Equal` instance for `AccountNumber`:

```scala

object AccountNumber extends Newtype[Int] {
  implicit val AccountNumberEqual: Equal[AccountNumber] =
    Equal.default
}
type AccountNumber = AccountNumber.Type

AccountNumber(1) === AccountNumber(1)
// res4: Boolean = true
AccountNumber(1) === AccountNumber(2)
// res5: Boolean = false
```

Attempting to compare two unrelated types results in a compilation error.

```scala
AccountNumber(1) === 1
```

This can be particularly useful because since the representations of the new type and the underlying types are the same at runtime, operators that are not strongly typed like `println` and `==` will not respect the difference between the new type and the underlying type. Using the functional abstractions in ZIO Prelude we can avoid this problem.

## Subtypes

When we use `Newtype` the new type we are creating is completely distinct from the underlying type. This can be useful when we want to "hide" the underlying representation of the new type except when we explicitly unwrap it.

However, it can lead to boilerplate in some cases where we need to explicitly unwrap the new type or reimplement functionality that already exists on the underlying type.

For example, say we want to test whether one `SequenceNumber` is after another. Right now this will not work:

```scala

object SequenceNumber extends Newtype[Int]
type SequenceNumber = SequenceNumber.Type

SequenceNumber(2) > SequenceNumber(1)
```

The `>` operator is defined on `Int` and as far as the Scala compiler is concerned `SequenceNumber` and `Int` are completely unrelated types. Of course we could unwrap each of our sequence numbers or define a new `>` operator on sequence numbers but here we are not taking advantage of the fact that we know that every `SequenceNumber` is an `Int`.

We can fix this by extending `Subtype` instead of `Newtype` when creating our new type.

```scala

object SequenceNumber extends Subtype[Int]
type SequenceNumber = SequenceNumber.Type

SequenceNumber(2) > SequenceNumber(1)
// res7: Boolean = true
```

Now `SequenceNumber` is a type that is different from `Int` but is still a subtype of `Int`.

This means that we can use a `SequenceNumber` any time we need an `Int` and can use operators defined on `Int` on `SequenceNumber`. However, we still get the type safety of not being able to use an `Int` or `AccountNumber` when a `SequenceNumber` is expected.

## Smart Newtypes

So far, all the new types we have created have been distinct from the underlying types but have not imposed any additional constraints on the values that the underlying type can take. For example, the `SequenceNumber` above could in principle be any `Int` value, whether that is `42` or `-1`.

In many cases that makes sense. We often want to use a new type to denote that this particular `Int` value represents a sequence number and shouldn't be confused with another `Int` value that represents an account number without imposing other constraints.

However, in other cases we may want to restrict the values that the underlying type can take. For instance, perhaps a `SequenceNumber` should never be negative.

We can model this in ZIO Prelude by extending `Newtype` and `Subtype`, just as before, and then defining an additional `def assertion` method that describes the constraints on the underlying value. (The syntax differs slightly between Scala 2 and 3 due to changes in the macro API).

```scala

object SequenceNumber extends Subtype[Int] {

  // Scala 2
  override def assertion = assert { 
    greaterThanOrEqualTo(0)
  }
  
  // Scala 3
  // override inline def assertion = 
  //  greaterThanOrEqualTo(0)
}
type SequenceNumber = SequenceNumber.Type
```

Here we created a simple assertion that requires the value be equal to or greater than zero, but we can use much more complex assertions. For example, we could validate an `Email` with the `matches` assertion, which accept a `Regex`.

Now, when we construct new values using `apply`, they will be validated *at compile time*.

```scala
val valid1: SequenceNumber = SequenceNumber(0)
val valid2: SequenceNumber = SequenceNumber(42)

val oops = SequenceNumber(-10)
// Newtype Assertion Failed 
// • -10 did not satisfy greaterThanOrEqualTo(0)
```

Smart Newtypes can only be validated at compile-time when called with literals, such as `9000` or `"Fancy Pants"`. Attempting to wrap a variable or run-time value with `apply` will result in a compilation error warning you about this.

When wrapping variables or run-time values, you can use the `make` or `makeAll` constructors, which will perform the validation at run-time instead.

```scala

val a = 10
// a: Int = 10
val b = 11
// b: Int = 11
val c = 12
// c: Int = 12

val validatedSequenceNumber: Validation[String, SequenceNumber] =
  SequenceNumber.make(a)
// validatedSequenceNumber: Validation[String, SequenceNumber] = Success(
//   log = IndexedSeq(),
//   value = 10
// )

val validateSequenceNumbers: Validation[String, Chunk[SequenceNumber]] =
  SequenceNumber.makeAll(Chunk(a, b, c))
// validateSequenceNumbers: Validation[String, Chunk[SequenceNumber]] = Success(
//   log = IndexedSeq(),
//   value = IndexedSeq(10, 11, 12)
// )
```

We can see that `make` and `makeAll` return a `Validation` that will either be a success with a valid instance of the newtype or a failure with a helpful error message indicating why a valid instance of the newtype could not be constructed.

Note that the `wrap` operator, which bypasses the compile-time check, is `protected` and will only be accessible within the scope of the newtype object unless we choose to expose them. This allows us to skip the compile-time check when we need to, such as in implementing a `.next` method, which we know will be safe, even if the compiler does not.

```scala

object SequenceNumber extends Subtype[Int] {
  override def assertion = assert { 
    greaterThanOrEqualTo(0)
  }
  
  val initial: SequenceNumber =
    SequenceNumber(0)
    
  implicit final class SequenceNumberOps(val self: SequenceNumber) extends AnyVal {
    def next: SequenceNumber = 
      wrap(self + 1)
  }
}
type SequenceNumber = SequenceNumber.Type
```

As another example, we might want to expose an `unsafeMake` operator for our users to allow them to construct instances of the newtype directly without going through `Validation`. As its name implies this places responsibility on the user to ensure that the underlying data is valid but could be necessary for performance or ergonomics in some situations.

We can do that quite easily like this:.

```scala

object SequenceNumber extends Subtype[Int] {
  override def assertion = assert { 
    greaterThanOrEqualTo(0)
  }

  def unsafeMake(n: Int): SequenceNumber =
    SequenceNumber.wrap(n)
}
type SequenceNumber = SequenceNumber.Type

val aTrustedInt = 1
// aTrustedInt: Int = 1
val sequenceNumber: SequenceNumber =
  SequenceNumber.unsafeMake(aTrustedInt)
// sequenceNumber: SequenceNumber = 1
```

Thus, Smart Newtypes give us full ability to implement our own operators and to expose whatever interface we want for our type, from validating input at compile-time, to using `Validation` at run-time, to allowing users to create instances of the refined newtype directly.
