# Smart Newtypes

> The new types in the earlier guides accept any value of their underlying type. A `SequenceNumber` built on `Int` can be `42` or `-1`. In many cases that is fine: we just want to say that a value is a sequence number and not an account number.

In other cases we want to restrict the values the underlying type can take. For instance, a sequence number should never be negative. A smart newtype does this by defining an `assertion` that describes which underlying values are valid.

## Defining an Assertion

We create a smart newtype by extending `Newtype` or `Subtype` and defining an `assertion` method on the new type object. The syntax differs between Scala 2 and Scala 3 because the macro API changed, so the definition is shown in tabs. Both tabs use the `greaterThanOrEqualTo` assertion, which requires the value to be zero or more:

  

**Scala 2**

In Scala 2, the assertion is written as a `def` that calls `assert`:

```scala
import zio.prelude.{Subtype, Validation}
import zio.prelude.Assertion._

object SequenceNumber extends Subtype[Int] {
  override def assertion = assert {
    greaterThanOrEqualTo(0)
  }
}
type SequenceNumber = SequenceNumber.Type
```

  
  

**Scala 3**

In Scala 3, the assertion is an `inline def` that returns an `Assertion`:

```scala
import zio.prelude.{Subtype, Validation}
import zio.prelude.Assertion._

object SequenceNumber extends Subtype[Int] {
  override inline def assertion: Assertion[Int] =
    greaterThanOrEqualTo(0)
}
type SequenceNumber = SequenceNumber.Type
```

  

The examples below use the Scala 2 definition, which the docs build compiles. We define the same object again here so that the following blocks can use it:

```scala
import zio.prelude.{Subtype, Validation}
import zio.prelude.Assertion._

object SequenceNumber extends Subtype[Int] {
  override def assertion = assert {
    greaterThanOrEqualTo(0)
  }
}
type SequenceNumber = SequenceNumber.Type
```

## Validating Literals at Compile Time

When we construct a value with `SequenceNumber.apply` from a literal, the assertion is checked at compile time:

```scala
val valid: SequenceNumber = SequenceNumber(42)
// valid: SequenceNumber = 42
```

If the literal does not satisfy the assertion, the program does not compile and the error explains which condition failed:

```scala
val oops: SequenceNumber = SequenceNumber(-10)
// error:
// [1m[31m[7m Newtype Assertion Failed [0m
// [2massertion = [0m[33mgreaterThanOrEqualTo(0)[0m
// 
// [31m• [34m-10[0m did not satisfy [33mgreaterThanOrEqualTo(0)[0m
// 
// val oops: SequenceNumber = SequenceNumber(-10)
//                            ^^^^^^^^^^^^^^^^^^^
```

Compile-time validation only works for literals. In Scala 2, passing a variable or a run-time value to `SequenceNumber.apply` is a compilation error as well, because the compiler cannot check it. For those values we use `SequenceNumber.make` instead.

## Validating Run-Time Values

The `SequenceNumber.make` and `SequenceNumber.makeAll` constructors check the assertion at run time. They return a `Validation` that is either a success with a valid value or a failure with an error message explaining why the value was rejected.

To validate a value that is only known at run time:

```scala
val a = 10
// a: Int = 10

val validatedSequenceNumber: Validation[String, SequenceNumber] =
  SequenceNumber.make(a)
// validatedSequenceNumber: Validation[String, SequenceNumber] = Success(
//   log = IndexedSeq(),
//   value = 10
// )
```

To validate a whole collection of run-time values, we use `SequenceNumber.makeAll`. It applies the same assertion to each element and returns a single `Validation` over the collection, so either every element is valid or the result is a failure.

## Using a More Complex Assertion

An assertion can express much more than a range check. For example, `matches` accepts a regular expression and can validate a string such as an email address:

```scala
import zio.prelude.{Subtype, Validation}
import zio.prelude.Assertion._

object EmailAddress extends Subtype[String] {
  override def assertion = assert {
    matches(".+@.+\\..+")
  }
}
type EmailAddress = EmailAddress.Type
```

Next, we validate two strings against the email assertion:

```scala
EmailAddress.make("reader@example.com")
// res4: zio.prelude.ZValidation[Nothing, String, EmailAddress.Type] = Success(
//   log = IndexedSeq(),
//   value = "reader@example.com"
// )

EmailAddress.make("not an email")
// res5: zio.prelude.ZValidation[Nothing, String, EmailAddress.Type] = Failure(
//   log = IndexedSeq(),
//   errors = NonEmptyChunk(not an email did not satisfy matches(.+@.+\..+))
// )
```

## Bypassing the Check Inside the Object

The `SequenceNumber.wrap` operator converts a value to the new type without checking the assertion. In Scala 2 it is `protected`, so only code inside the new type object can call it. This lets the object's own operators create values that they know are valid, even when the compiler cannot prove it.

For example, the `next` operator on a sequence number cannot produce a negative value when the input is valid, so it can skip the check:

```scala
import zio.prelude.{Subtype, Validation}
import zio.prelude.Assertion._

object SequenceNumber extends Subtype[Int] {
  override def assertion = assert {
    greaterThanOrEqualTo(0)
  }

  implicit final class SequenceNumberOps(private val self: SequenceNumber) {
    def next: SequenceNumber =
      wrap(self + 1)
  }
}
type SequenceNumber = SequenceNumber.Type
```

When we know a value is valid at the call site, we can expose a constructor that skips the check. The caller takes responsibility for the value being valid:

```scala
import zio.prelude.{Subtype, Validation}
import zio.prelude.Assertion._

object SequenceNumber extends Subtype[Int] {
  override def assertion = assert {
    greaterThanOrEqualTo(0)
  }

  def unsafeMake(n: Int): SequenceNumber =
    SequenceNumber.wrap(n)
}
type SequenceNumber = SequenceNumber.Type
```

The `SequenceNumber.unsafeMake` name signals that no check runs. Use it only when the value is already valid, for example when it comes from a trusted source or when performance matters.

## Combining the Pieces

A smart newtype can offer several constructors, each with its own trade-off. Compile-time `SequenceNumber.apply` gives the strongest guarantee for literals, `SequenceNumber.make` and `SequenceNumber.makeAll` handle run-time values safely, and `SequenceNumber.unsafeMake` skips the check when the caller can vouch that the value is valid. Together they give us full control over how we build a new type, without changing the underlying type.
