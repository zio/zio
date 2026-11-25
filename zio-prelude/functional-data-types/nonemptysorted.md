# NonEmptySortedMap and NonEmptySortedSet

> `NonEmptySortedMap` and `NonEmptySortedSet` are the sorted versions of [NonEmptyMap](./nonemptymap.md) and [NonEmptySet](./nonemptyset.md). Each guarantees at least one entry or element, and each keeps its entries in the order given by an `Ordering` from the Scala standard library.

Use these types when the non-empty guarantee and a stable sorted order are both part of what the code relies on. For example, the lowest priority in a queue is always the first element of a sorted set, and that element always exists.

## Motivation

Code that needs "the smallest" or "the largest" entry usually calls `head` or `min` on a `SortedSet` or `SortedMap`, and has to handle the empty case that may never happen. These types move that fact into the type: if a value of type `NonEmptySortedSet[A]` exists, `peel` returns the first element and the rest without an `Option`. Typical places are a priority queue whose caller guarantees at least one task, a leaderboard that always has an entry, and an ordered timeline of events. The order is stable and comes from the `Ordering` given at construction, so output such as logs and snapshots is deterministic.

Use [NonEmptySet](./nonemptyset.md) and [NonEmptyMap](./nonemptymap.md) when the iteration order does not matter and the element type has no `Ordering`, and the sorted variants when it does.

We import both types from the `zio.prelude` package:

```scala
import zio.prelude.{NonEmptySortedMap, NonEmptySortedSet}
```

## NonEmptySortedSet

The `NonEmptySortedSet` constructors need an implicit `Ordering` for the element type. For `String`, the standard library provides one:

```scala
val tags: NonEmptySortedSet[String] =
  NonEmptySortedSet("scala", "zio", "docs")
// tags: NonEmptySortedSet[String] = NonEmptyTreeSet(docs, scala, zio)

tags.toSet
// res1: collection.immutable.SortedSet[String] = TreeSet(
//   "docs",
//   "scala",
//   "zio"
// )
```

The elements come out in sorted order, and duplicates are removed. `NonEmptySortedSet.fromSetOption` converts an existing `SortedSet` and returns `None` when it is empty:

```scala
NonEmptySortedSet.fromSetOption(scala.collection.immutable.SortedSet.empty[String])
// res2: Option[NonEmptySortedSet[String]] = None
```

`head` returns the first element in the sorted order, which is always present:

```scala
tags.head
// res3: String = "docs"
```

`removeNonEmpty` removes an element and returns `Some` with the remaining set if there is anything left, otherwise `None`:

```scala
tags.removeNonEmpty("zio").map(_.toSet)
// res4: Option[collection.immutable.SortedSet[String]] = Some(
//   value = TreeSet("docs", "scala")
// )
```

`peelNonEmpty` returns the first element together with `Some` of the remaining set, or `None` when that element was the last. This makes it simple to process a set in order until it is exhausted:

```scala
def drain(tasks: NonEmptySortedSet[Int]): List[Int] =
  tasks.peelNonEmpty match {
    case (first, Some(rest)) => first :: drain(rest)
    case (first, None)       => List(first)
  }

drain(NonEmptySortedSet(3, 1, 2))
// res5: List[Int] = List(1, 2, 3)
```

`NonEmptySortedSet#map` needs an `Ordering` for the result type, and `NonEmptySortedSet#union` takes a `SortedSet` as its argument, so a non-empty set absorbs any other set and stays non-empty. A `NonEmptySortedSet` is converted implicitly to a `SortedSet` when one is expected, which is why operators such as `head` are available on it.

## NonEmptySortedMap

`NonEmptySortedMap` works like `NonEmptyMap`, but its keys are kept in sorted order and its constructors need an implicit `Ordering` for the key type. For example, a map of scores looks like this:

```scala
val scores: NonEmptySortedMap[String, Int] =
  NonEmptySortedMap("carol" -> 7, "alice" -> 9, "bob" -> 4)
// scores: NonEmptySortedMap[String, Int] = NonEmptyTreeMap(alice -> 9, bob -> 4, carol -> 7)

scores.toMap
// res6: collection.immutable.SortedMap[String, Int] = TreeMap(
//   "alice" -> 9,
//   "bob" -> 4,
//   "carol" -> 7
// )
```

`keySet` returns the keys as a `NonEmptySortedSet`, so the sorted order carries over to the keys:

```scala
scores.keySet.toSet
// res7: collection.immutable.SortedSet[String] = TreeSet(
//   "alice",
//   "bob",
//   "carol"
// )
```

`peel` splits the map into its first entry and the rest. The first entry is the one with the smallest key:

```scala
scores.peel
// res8: ((String, Int), collection.immutable.SortedMap[String, Int]) = (
//   ("alice", 9),
//   TreeMap("bob" -> 4, "carol" -> 7)
// )
```

`values` returns the values in key order as a `NonEmptyChunk`, and `mapValues` transforms them while keeping the keys. `peelNonEmpty` and `tailNonEmpty` work as they do for the set. A leaderboard keeps its entries ordered by player name, and the first one is always available:

```scala
scores.values
// res9: zio.NonEmptyChunk[Int] = NonEmptyChunk(9, 4, 7)

scores.mapValues(_ * 10).peel._1
// res10: (String, Int) = ("alice", 90)
```

Updates behave as they do for `NonEmptyMap`. `+` and `++` return a `NonEmptySortedMap`, while `remove` returns a plain `SortedMap`, because removing an entry can make the map empty.

## Instances

`NonEmptySortedSet` has `Equal`, `Hash`, `PartialOrd` and `Debug` instances, and a `Commutative` and `Idempotent` instance that combines two sets by union. Both types convert implicitly to the standard `SortedSet` and `SortedMap`, and the extension methods `toNonEmptySortedSet` and `toNonEmptyMap` turn a possibly empty standard collection into an `Option` of the non-empty type.

## Related Abstractions

[NonEmptySet](./nonemptyset.md) and [NonEmptyMap](./nonemptymap.md) are the unsorted versions, and [NonEmptyList](./nonemptylist.md) is the sequence version of the non-empty guarantee. [ZNonEmptySet](./znonemptyset.md) adds weights to the elements of a non-empty set.
