# PartialInverse

> `PartialInverse[A]` describes an associative operator on `A` that has an identity element and an inverse operator. It is like [Inverse](./inverse.md), but the inverse does not have to exist for every pair of values.

The natural numbers are a case where a total inverse does not exist. Addition has an inverse, subtraction, but subtraction can give a negative number, and the natural numbers do not include negative numbers. So the inverse exists only when the result stays natural.

`PartialInverse` extends `Identity`, so it provides `combine` and an `identity` element. Its own operation is `inverseOption`, which returns `Some` with the inverse when one exists and `None` otherwise. When the inverse of a value with itself exists, it is the identity element.

`PartialInverse` is the weaker parent of [Inverse](./inverse.md): every `Inverse` is a `PartialInverse` whose `inverseOption` always returns `Some`.

## Motivation

Many operations that "undo" another one are not defined everywhere. Dividing by zero is undefined, subtracting a larger count from a smaller one gives a negative stock level, and removing an item that was never added to a multiset has no sensible answer. Representing the inverse as a total function forces a choice between throwing, returning a wrong value, or using a different type. `PartialInverse` keeps the inverse as part of the abstraction and makes the failure case explicit in the type, so generic code that needs to "undo" a combination has to handle it.

Use `PartialInverse` when you have an identity and an operator, and an inverse that is defined only for some pairs. Use [Inverse](./inverse.md) when the inverse always exists, and [Identity](./identity.md) when no inverse is needed at all.

## Defining a PartialInverse

We create an instance with `PartialInverse.make`, which takes the identity element, the operator, and the inverse. For natural numbers, the operator is addition, the identity is zero, and the inverse is subtraction that returns `None` when the result would be negative:

```scala
import zio.prelude._

val naturals: PartialInverse[Int] =
  PartialInverse.make[Int](
    0,
    _ + _,
    (l, r) => if (l >= r) Some(l - r) else None
  )
```

Subtracting a smaller number from a larger one succeeds, and the other way round returns `None`:

```scala
naturals.inverseOption(5, 2)
// res1: Option[Int] = Some(value = 3)

naturals.inverseOption(2, 5)
// res2: Option[Int] = None
```

Combining a value with itself using the inverse gives the identity element, which is zero:

```scala
naturals.inverseOption(7, 7)
// res3: Option[Int] = Some(value = 0)
```

`PartialInverse.makeFrom` builds the same kind of instance from an existing `Identity` instance and an inverse function, when the identity and the operator come from another instance.

## Using Predefined Instances

The library defines `PartialInverse` instances for the product of the numeric types, where the operator is multiplication and the inverse is division that fails for a zero divisor. The infix operator `~?~` calls `PartialInverse#inverseOption`:

```scala
import zio.prelude.newtypes.Prod

Prod(12) ~?~ Prod(4)
// res4: Option[Prod.Type[Int]] = Some(value = 3)

Prod(12) ~?~ Prod(0)
// res5: Option[Prod.Type[Int]] = None
```

Tuples of `PartialInverse` values also have an instance, which returns `Some` only when every component has an inverse. Because `PartialInverse` extends `Identity`, it also has `multiplyOption`. `PartialInverse` overrides it to use the inverse to repeat a value a negative number of times, and it returns `None` when an intermediate step fails.

## Laws

`PartialInverse` instances must satisfy the laws of [Identity](./identity.md) and one more, defined as `partialInverseLaw` in `PartialInverseLaws` in the `zio-laws-laws` module:

| Law             | Statement                                                                                       |
| --------------- | ----------------------------------------------------------------------------------------------- |
| Partial inverse | For every `a`, `inverseOption(a, a)` is either `None` or `Some` of the identity element         |

## Related Abstractions

[Inverse](./inverse.md) is the total version. [Identity](./identity.md) provides the operator and the identity element that `PartialInverse` extends, and [Associative](./associative.md) provides the operator itself.
