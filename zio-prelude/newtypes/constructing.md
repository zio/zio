# Constructing and Unwrapping New Types

> A new type has a companion object that provides the operators for creating its values and for converting them back to the underlying type. This guide covers those operators. For the basic definition of a new type, see [New Types](./index.md).

Throughout this guide we use a `SequenceNumber` new type over `Int`:

```scala
import zio.Chunk
import zio.prelude.Newtype

object SequenceNumber extends Newtype[Int]
type SequenceNumber = SequenceNumber.Type
```

## Constructing a Single Value

The easiest way to construct an instance of a new type is to call `SequenceNumber.apply` on the new type object:

```scala
val sequenceNumber: SequenceNumber =
  SequenceNumber(1)
// sequenceNumber: SequenceNumber = 1
```

You can think of the new type object as the companion object of the new type. It comes with `SequenceNumber.apply` so that we can construct values of the new type without any extra ceremony.

When we pass several values to `SequenceNumber.apply`, we get a `NonEmptyChunk` of the new type back. This is convenient when we need a few values at once:

```scala
val sequenceNumbers: zio.NonEmptyChunk[SequenceNumber] =
  SequenceNumber(1, 2, 3)
// sequenceNumbers: zio.NonEmptyChunk[SequenceNumber] = NonEmptyChunk(1, 2, 3)
```

Scala 2 supports this with a single varargs overload, and Scala 3 supports it with overloads for up to six values.

## Constructing a Collection

If we want to construct a collection of new type values from a collection of underlying values, we can use `SequenceNumber.wrapAll`:

```scala
val wrapped: Chunk[SequenceNumber] =
  SequenceNumber.wrapAll(Chunk(3, 4, 5))
// wrapped: Chunk[SequenceNumber] = IndexedSeq(3, 4, 5)
```

`SequenceNumber.wrapAll` does not traverse the collection. Internally the new type and its underlying type have the same runtime representation, so the `Chunk[Int]` is reinterpreted as a `Chunk[SequenceNumber]` without any work.

## Wrapping a Value Directly

The `SequenceNumber.wrap` operator converts a single underlying value to the new type without any other checks. Whether `Meter.wrap` is accessible outside the new type object depends on the Scala version. In Scala 2 it is `protected`, so only code inside the object can call it. In Scala 3 it is public.

  

**Scala 2**

In Scala 2, the object defines the value with `Meter.wrap` directly:

```scala
import zio.prelude.Newtype

object Meter extends Newtype[Double] {
  val oneAndAHalf: Meter =
    wrap(1.5)
}
type Meter = Meter.Type
```

  
  

**Scala 3**

In Scala 3, the same value uses the public `Meter.wrap`:

```scala
import zio.prelude.Newtype

object Meter extends Newtype[Double]
type Meter = Meter.Type

val oneAndAHalf: Meter =
  Meter.wrap(1.5)
```

  

## Extending the Constructors

The new type object is also the natural place for our own constructors. To define `SequenceNumber.initial` in terms of `SequenceNumber.apply`:

```scala
import zio.Chunk
import zio.prelude.Newtype

object SequenceNumber extends Newtype[Int] {
  def initial: SequenceNumber =
    SequenceNumber(0)
}
type SequenceNumber = SequenceNumber.Type
```

Now the constructor is available on the new type just as it would be on the companion object of any ordinary data type, so `SequenceNumber.initial` works anywhere a sequence number is required. The unwrapping example below shows its underlying value.

## Unwrapping a Value

To access the underlying value and work with it, we call `SequenceNumber.unwrap`:

```scala
val zero: Int =
  SequenceNumber.unwrap(SequenceNumber.initial)
// zero: Int = 0
```

To convert a collection of new type values back to underlying values, we call `SequenceNumber.unwrapAll`:

```scala
val ints: Chunk[Int] =
  SequenceNumber.unwrapAll(SequenceNumber.wrapAll(Chunk(1, 2, 3)))
// ints: Chunk[Int] = IndexedSeq(1, 2, 3)
```

Like `SequenceNumber.wrapAll`, `SequenceNumber.unwrapAll` does not traverse the collection, because the underlying values already have the representation the caller needs.
