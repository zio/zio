# Idempotent

> `Idempotent[A]` describes an associative operator on `A` where combining a value with an identical value gives that same value back. In symbols, `a <> a` equals `a` for every value `a`.

Set union is idempotent, because a set combined with itself is still the same set. Integer addition is not, because adding a number to itself doubles it.

Idempotent operators are useful for optimization. When an operator is idempotent, combining the same value many times gives the same result as combining it once, so duplicate work can be skipped.

`Idempotent` extends `Associative`, so every idempotent instance also has an associative `combine`.

## Motivation

Merging data that may arrive more than once is a common source of bugs. If two replicas exchange their state and each merge is applied several times, or in a different order, the result should still be the same. An idempotent merge guarantees that duplicates are harmless, so retries and repeated deliveries need no bookkeeping. This is the property that replicated counters and sets rely on, and the same property lets a reducer skip work when it meets equal inputs.

Use `Idempotent` when the operator you are defining satisfies `a <> a === a` and you want that guarantee in the type. If it does not, plain [Associative](./associative.md) is the right abstraction, and if the operator is also order-insensitive, add [Commutative](./commutative.md).

## Using an Existing Instance

The library provides idempotent instances for some types. `Max[Int]` is idempotent, because the maximum of a value and itself is that value:

```scala
import zio.prelude._
import zio.prelude.newtypes._
```

Next, we combine two values with the operator:

```scala
Idempotent[Max[Int]].combine(Max(3), Max(3))
// res1: Max[Int] = 3
```

Boolean disjunction is idempotent as well, because `true` or `true` is `true`, and `false` or `false` is `false`:

```scala
Idempotent[Or].combine(Or(true), Or(true))
// res2: Or = true
```

## Merging Replicas

`Idempotent` instances compose. A map whose values are idempotent is itself idempotent, so merging per-replica maximums gives a state that tolerates repeated merges. Here two replicas each track how far they have counted for each node:

```scala
val replicaA = Map("a" -> Max(3), "b" -> Max(1))

val replicaB = Map("a" -> Max(2), "c" -> Max(5))
```

Next, we merge the replicas, and then merge the result with one of them again. The second merge changes nothing:

```scala
val merged = Idempotent[Map[String, Max[Int]]].combine(replicaA, replicaB)
// merged: Map[String, Max[Int]] = Map("a" -> 3, "b" -> 1, "c" -> 5)

Idempotent[Map[String, Max[Int]]].combine(merged, replicaA) == merged
// res3: Boolean = true
```

## Defining an Idempotent Instance

`Idempotent.make` builds an instance from an operator. The compiler does not check idempotence, so the operator must satisfy the law on its own. Set union is a typical choice:

```scala
val tagUnion: Idempotent[Set[String]] =
  Idempotent.make[Set[String]](_ union _)
```

Combining a set with itself returns it unchanged:

```scala
tagUnion.combine(Set("scala", "zio"), Set("scala", "zio"))
// res4: Set[String] = Set("scala", "zio")
```

`Idempotent.makeFrom` builds an instance from an existing `Associative` instance, when you know that instance is idempotent.

## Skipping Work for Equal Values

`combineIdempotent` is a method on every `Idempotent` instance. It checks whether the two values are equal, and if they are, returns the first one without calling `combine`. Otherwise it combines them normally:

```scala
Idempotent[Max[Int]].combineIdempotent(Max(3), Max(3))
// res5: Max[Int] = 3
```

The `idempotent` method on an `Idempotent` instance returns a new instance whose `combine` uses `combineIdempotent`, so the equality check runs on every combination. This is useful when `combine` is expensive and many equal values are combined.

The shortcut is only valid when the operator is idempotent. On an operator that is not idempotent, `combineIdempotent` returns a different result from `combine` whenever the two values are equal.

## Laws

`Idempotent` instances must satisfy the laws of [Associative](./associative.md) and one more, defined as `idempotentLaw` in `IdempotentLaws` in the `zio-laws-laws` module:

| Law        | Statement                      |
| ---------- | ------------------------------ |
| Idempotent | `(a <> a) === a` for every `a` |

## Related Abstractions

[Associative](./associative.md) is the parent type class, and [Commutative](./commutative.md) adds order-insensitivity. Types that are associative, commutative and idempotent are the semilattices that replicated data types are built from. [Identity](./identity.md) adds a neutral element, which an idempotent operator such as `Max` often has as well.
