# New Types

> ZIO Prelude provides zero overhead new types. They make your application more type safe without compromising performance or ergonomics.

A common situation in domain modeling is that several types in our business domain share the same underlying representation in Scala. For example:

```scala
type AccountNumber  = Int
type SequenceNumber = Int
```

This is not a great situation. We can easily mix up values that represent different types in our business domain, for example providing an `AccountNumber` where we are supposed to use a `SequenceNumber` or vice versa.

The Scala compiler will not help us avoid these bugs because from its perspective `AccountNumber` and `SequenceNumber` are the same type, namely `Int`.

To avoid this, we might be tempted to create a separate case class for each of these types:

```scala
final case class AccountNumber(value: Int)
final case class SequenceNumber(value: Int)
```

This is an improvement in type safety because `AccountNumber` and `SequenceNumber` are now separate types. Providing an `AccountNumber` where a `SequenceNumber` is expected results in a compilation error.

However, every account number and sequence number now allocates an additional object, which can add up over the course of a large application. Techniques such as extending `AnyVal` can reduce these allocations, but they are fragile and can make performance worse if we are not careful.

ZIO Prelude gives us the benefits of both approaches. A new type has the same runtime representation as its underlying type, so there is no extra allocation, but the compiler treats it as a separate type.

## Defining a New Type

A new type in ZIO Prelude is a type with the same underlying representation as another type at runtime but a separate type at compile time.

We create a new type by defining an object that extends `Newtype` and specifying the type it wraps. We then export the type by defining a type alias that refers to the `Type` member of that object:

```scala
import zio.prelude.Newtype

object AccountNumber extends Newtype[Int]
type AccountNumber = AccountNumber.Type

object SequenceNumber extends Newtype[Int]
type SequenceNumber = SequenceNumber.Type
```

`AccountNumber` and `SequenceNumber` are now distinct from `Int` at compile time, even though they are all `Int` values at runtime. Here we define a function that accepts both:

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

Passing the arguments in the wrong order is now a compilation error:

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

## Guides in This Section

Each guide covers one part of working with new types:

- [Constructing and Unwrapping](./constructing.md) explains how to create new type values from underlying values and back again.
- [Operators and Instances](./operators-and-instances.md) explains how to add operators to a new type and how to give it instances of functional abstractions such as `Equal`.
- [Subtypes](./subtypes.md) explains how to keep the operators of the underlying type when you still want a separate type.
- [Smart Newtypes](./smart-newtypes.md) explains how to restrict which underlying values a new type accepts, both at compile time and at run time.
- [Predefined Newtypes](./predefined-newtypes.md) lists the new types that ZIO Prelude ships with, such as `Sum` and `Max`.
