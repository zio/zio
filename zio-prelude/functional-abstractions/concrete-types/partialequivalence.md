# PartialEquivalence

> `PartialEquivalence[A, B, E1, E2]` describes a conversion between two types `A` and `B` in both directions, where either direction can fail. It generalizes [Equivalence](../../functional-data-types/equivalence.md), which requires both conversions to always succeed.

For example, a string such as `"42"` can be parsed into an `Int`, but a string such as `"forty-two"` cannot. Parsing can fail, and converting an `Int` back into a string always succeeds. A `PartialEquivalence` describes that situation directly.

`Equivalence[A, B]` is the special case where neither conversion can fail: it extends `PartialEquivalence[A, B, Nothing, Nothing]`, so any `Equivalence` can be used wherever a `PartialEquivalence` is expected. `Equivalence.fromPartial` goes the other way, building an `Equivalence` from two conversions whose error types are provably `Nothing`.

The type has two functions. `PartialEquivalence#toPartial` converts an `A` into an `Either[E1, B]`, and `PartialEquivalence#fromPartial` converts a `B` into an `Either[E2, A]`.

## Motivation

Converting between two representations is rarely fully reversible in practice. Parsing text into a number, decoding an identifier into a record, and converting a wide numeric type into a narrow one can all fail in one direction while the opposite direction always works. Modelling that with a pair of plain functions loses the error information and makes it easy to forget a failure case, while a full `Equivalence` claims that both directions always succeed. `PartialEquivalence` keeps both directions and their error types together, so a conversion can be flipped, chained with another one, and checked for a round trip.

Use `PartialEquivalence` when at least one direction can fail and you want to carry the error. Use [Equivalence](../../functional-data-types/equivalence.md) when both directions always succeed.

## Defining a PartialEquivalence

We create an instance with `PartialEquivalence.apply`, which takes the two conversions. Here the conversion from a string to an `Int` reports an error message when the string is not a number:

```scala
import zio.prelude._

val intFromString: PartialEquivalence[String, Int, String, String] =
  PartialEquivalence(
    (s: String) => s.toIntOption.toRight(s"not a number: $s"),
    (i: Int) => Right(i.toString)
  )
```

Converting a valid string succeeds, and converting an invalid one returns the error:

```scala
intFromString.toPartial("42")
// res1: Either[String, Int] = Right(value = 42)

intFromString.toPartial("forty-two")
// res2: Either[String, Int] = Left(value = "not a number: forty-two")
```

Converting back from an `Int` to a string always succeeds:

```scala
intFromString.fromPartial(42)
// res3: Either[String, String] = Right(value = "42")
```

## Checking a Round Trip

A conversion is well behaved when a value that survives a round trip comes back unchanged. `PartialEquivalence#canonicalLeft` converts an `A` to a `B` and back, and returns `Some` of the result or `None` if either step fails. A value that is already in the canonical form comes back equal to itself, and a value written differently comes back in canonical form:

```scala
intFromString.canonicalLeft("42")
// res4: Option[String] = Some(value = "42")

intFromString.canonicalLeft("042")
// res5: Option[String] = Some(value = "42")

intFromString.canonicalLeft("forty-two")
// res6: Option[String] = None
```

`PartialEquivalence#canonicalRight` does the same starting from a `B`. The variants `PartialEquivalence#canonicalLeftOrError` and `PartialEquivalence#canonicalRightOrError` return the error instead of `None`.

## Reversing and Chaining

`PartialEquivalence#flip` swaps the two directions, so the result converts from `B` to `A`. The operator `>>>` chains two partial equivalences, so the output of the first becomes the input of the second. A chain fails as soon as one of its steps fails:

```scala
val stringFromInt: PartialEquivalence[Int, String, String, String] =
  intFromString.flip

val doubled: PartialEquivalence[Int, Int, String, Nothing] =
  PartialEquivalence(
    (i: Int) => Right(i * 2),
    (i: Int) => Right(i / 2)
  )
```

Next, we chain the two conversions and run a valid string through them:

```scala
(intFromString >>> doubled).toPartial("21")
// res7: Either[String, Int] = Right(value = 42)
```

`PartialEquivalence#andThen` is the named form of `>>>`, and `PartialEquivalence#compose` chains in the opposite order, so `a compose b` equals `b >>> a`.

## Related Abstractions

[Equivalence](../../functional-data-types/equivalence.md) is the total version, in which neither direction can fail. `PartialEquivalence` has no type class laws in the laws module, so there is nothing to check besides the round-trip property above. [Ord](./ord.md) and [Equal](./equal.md) describe relations on values of one type, while `PartialEquivalence` relates values of two types.
