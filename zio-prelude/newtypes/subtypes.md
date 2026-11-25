# Subtypes

> When we use `Newtype`, the new type is completely distinct from its underlying type. This is useful when we want to hide the underlying representation and unwrap only when we say so.

The cost is boilerplate. If we want to use an operator that already exists on the underlying type, we have to unwrap the value or reimplement the operator for the new type.

For example, say we want to test whether one `SequenceNumber` comes after another. With a plain `Newtype` this does not compile:

```scala
import zio.prelude.Newtype

object SequenceNumber extends Newtype[Int]
type SequenceNumber = SequenceNumber.Type
```

Attempting the comparison now fails to compile:

```scala
SequenceNumber(2) > SequenceNumber(1)
// error: No implicit PartialOrd defined for MdocApp0.this.SequenceNumber.Type.
// SequenceNumber(2) > SequenceNumber(1)
//                   ^
```

The `>` operator exists on `Int`, and the compiler sees `SequenceNumber` and `Int` as unrelated types. We could unwrap each value or define our own `>` on sequence numbers, but in both cases we are not using the fact that every `SequenceNumber` is an `Int`.

We keep that fact by extending `Subtype` instead of `Newtype`:

```scala
import zio.prelude.Subtype

object SequenceNumber extends Subtype[Int]
type SequenceNumber = SequenceNumber.Type
```

Now the comparison compiles and returns the result of `Int` comparison:

```scala
SequenceNumber(2) > SequenceNumber(1)
// res3: Boolean = true
```

`SequenceNumber` is still a different type from `Int`, but it is a subtype of `Int`. Because of that, a `SequenceNumber` works anywhere an `Int` is required, and every operator on `Int` works on `SequenceNumber` directly:

```scala
val asInt: Int =
  SequenceNumber(3)
// asInt: Int = 3

asInt + 1
// res4: Int = 4
```

We keep the type safety of the new type. An `Int` or an `AccountNumber` still cannot stand in for a `SequenceNumber`.

## Operations Return the Underlying Type

The operators of `Int` know nothing about `SequenceNumber`, so they return plain `Int` values. Adding one to a sequence number gives an `Int`, and the compiler does not check that the result is still a valid `SequenceNumber`. To get a `SequenceNumber` back, we wrap the result again with `SequenceNumber.apply`:

```scala
val next: Int =
  SequenceNumber(3) + 1
// next: Int = 4

val wrapped: SequenceNumber =
  SequenceNumber(next)
// wrapped: SequenceNumber = 4
```

This is the trade-off of a subtype. Any value of the new type can leave its type behind, which is what makes the operators work, and nothing stops a value from being re-wrapped. When the new type carries an invariant, such as a smart newtype that only accepts non-negative numbers, the invariant holds when a value is created and not after arithmetic on it. See [Smart Newtypes](./smart-newtypes.md) for how validation is attached.

## Type Class Instances

Type classes in ZIO Prelude such as `Equal` and `Ord` are contravariant, so an instance for `Int` is also an instance for any subtype of `Int`. A `Subtype` therefore uses the `Int` instances without any definitions of its own:

```scala
import zio.prelude._

SequenceNumber(1) === SequenceNumber(1)
// res5: Boolean = true

SequenceNumber(1) =?= SequenceNumber(2)
// res6: Ordering = LessThan
```

A `Newtype` does not inherit instances from the underlying type, which is how it hides the underlying operators and lets us give the new type different behavior. The predefined newtypes such as `Sum` and `Prod` use this: they are subtypes that carry their own `Associative` instances. See [Operators and Instances](./operators-and-instances.md) and [Predefined Newtypes](./predefined-newtypes.md).

## Choosing Between Newtype and Subtype

The following table summarizes the difference:

| Question                                                  | `Newtype` | `Subtype` |
| --------------------------------------------------------- | --------- | --------- |
| Can a value be passed where the underlying type is wanted | No        | Yes       |
| Do the operators of the underlying type work              | No        | Yes       |
| Are the instances of the underlying type reused           | No        | Yes       |
| Can an underlying value stand in for the new type         | No        | No        |

Use `Subtype` when we want to keep the underlying operators. Use `Newtype` when those operators would leak meaning we do not want, such as arithmetic on an account number.
