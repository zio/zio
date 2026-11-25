# Operators and Instances for New Types

> A new type on its own only lets us wrap and unwrap values. Most of the time we also want operators on the new type and instances of functional abstractions such as `Equal` or `Ord`. This guide shows where to define them so they work wherever the new type appears, without an import. For constructing and unwrapping values, see [Constructing and Unwrapping](./constructing.md).

## Extension Methods

The easiest way to add an operator to a new type is to define it as an extension method inside the new type object. Because the new type object is part of the implicit scope of the new type, these methods are available on its values without any import.

To add a `next` operator to a `SequenceNumber`:

```scala
import zio.Chunk
import zio.prelude.Newtype

object SequenceNumber extends Newtype[Int] {
  implicit final class SequenceNumberOps(private val self: SequenceNumber) {
    def next: SequenceNumber =
      SequenceNumber.wrap(SequenceNumber.unwrap(self) + 1)
  }
}
type SequenceNumber = SequenceNumber.Type
```

Because `SequenceNumberOps` lives inside the `SequenceNumber` object, a value of the new type can call `next` directly:

```scala
val sequenceNumber: SequenceNumber =
  SequenceNumber(1)
// sequenceNumber: SequenceNumber = 1

val nextSequenceNumber: SequenceNumber =
  sequenceNumber.next
// nextSequenceNumber: SequenceNumber = 2
```

## Companion Operators

Some operators work on a collection of values rather than on a single value. For these, we define a plain method on the new type object. For example, a `SequenceNumber.continuous` operator returns whether a collection of sequence numbers has no gaps:

```scala
object SequenceNumber extends Newtype[Int] {
  def continuous(sequenceNumbers: Iterable[SequenceNumber]): Boolean = {
    val ints = SequenceNumber.unwrapAll(sequenceNumbers.toList)
    ints.zip(ints.drop(1)).forall { case (previous, current) => current == previous + 1 }
  }
}
type SequenceNumber = SequenceNumber.Type
```

Calling it through the companion object reads like any other operator on a companion object:

```scala
val continuous: Boolean =
  SequenceNumber.continuous(Chunk(SequenceNumber(1), SequenceNumber(2), SequenceNumber(3)))
// continuous: Boolean = true
```

## Defining Instances

We can also give a new type instances of functional abstractions, such as `Equal`, `Ord`, or `Associative`. Defining these instances in the new type object means they are found automatically, so we do not have to import them.

To give `AccountNumber` an `Equal` instance that uses the default equality of its underlying type:

```scala
import zio.prelude._

object AccountNumber extends Newtype[Int] {
  implicit val AccountNumberEqual: Equal[AccountNumber] =
    Equal.default[AccountNumber]
}
type AccountNumber = AccountNumber.Type
```

Now the equality operator `===` works on account numbers:

```scala
AccountNumber(1) === AccountNumber(1)
// res2: Boolean = true

AccountNumber(1) === AccountNumber(2)
// res3: Boolean = false
```

Comparing an account number with a plain `Int` is a compilation error, because the two types are not related:

```scala
AccountNumber(1) === 1
// error: No implicit Equal defined for Any.
```

This matters because at runtime an `AccountNumber` and an `Int` are the same value. Operators that are not strongly typed, such as `==` and `println`, do not see the difference. The functional abstractions in ZIO Prelude do, so they let the compiler catch these mistakes.

## Deriving Instances

When the underlying type already has an instance, we reuse it for the new type through `Age.derive`. The type class takes a type constructor, and the new type object looks up the underlying instance for us.

The `Age.derive` operator is `protected` in Scala 2, so only code inside the object can call it. In Scala 3 it is public.

  

**Scala 2**

In Scala 2, the object derives the instance inside its body:

```scala
import zio.prelude._

object Age extends Newtype[Int] {
  implicit val ageOrd: Ord[Age] =
    derive[Ord]
}
type Age = Age.Type
```

  
  

**Scala 3**

In Scala 3, the same instance is derived from outside the object:

```scala
import zio.prelude._

object Age extends Newtype[Int]
type Age = Age.Type

implicit val ageOrd: Ord[Age] =
  Age.derive[Ord]
```

  

The derived instance is only correct when the underlying ordering matches what we want for the new type. The caller is responsible for that choice, which is why `Age.derive` is not public in Scala 2.
