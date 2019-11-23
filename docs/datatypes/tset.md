---
id: datatypes_tset
title: "TSet"
---

A `TSet[A]` is transactional data structure built on top of `TMap[A]`.

## Create a TSet

Creating an empty `TSet`:

```scala mdoc:silent
import zio._
import zio.stm._

val emptyTSet: STM[Nothing, TSet[Int]] = TSet.empty[Int]
```

Or creating a `TSet` with specified values:

```scala mdoc:silent
import zio._
import zio.stm._

val specifiedValuesTSet: STM[Nothing, TSet[Int]] = TSet.make(1, 2, 3)
```

Or creating a `TSet` from existing `Iterable` structure:

```scala mdoc:silent
import zio._
import zio.stm._

val iterableTSet: STM[Nothing, TSet[Int]] = TSet.fromIterable(List(1, 2, 3))
```

## Put an element to a `TSet`

Putting new element to a `TSet` extends it if specified element is not already present:

```scala mdoc:silent
import zio._
import zio.stm._

val putElem: UIO[TSet[Int]] = (for {
  tSet <- TSet.make(1, 2)
  _    <- tSet.put(3)
} yield tSet).commit
```

## Remove an element from a `TSet`

The simplest way to remove an element from `TSet` is using `delete` method:

```scala mdoc:silent
import zio._
import zio.stm._

val deleteElem: UIO[TSet[Int]] = (for {
  tSet <- TSet.make(1, 2, 3)
  _    <- tSet.delete(1)
} yield tSet).commit
```

Also, it is possible to remove every element that matches predicate function:

```scala mdoc:silent
import zio._
import zio.stm._

val removedEvenElems: UIO[TSet[Int]] = (for {
  tSet <- TSet.make(1, 2, 3, 4)
  _    <- tSet.removeIf(_ % 2 == 0)
} yield tSet).commit
```

Or you can keep all elements that match predicate function:

```scala mdoc:silent
import zio._
import zio.stm._

val retainedEvenElems: UIO[TSet[Int]] = (for {
  tSet <- TSet.make(1, 2, 3, 4)
  _    <- tSet.retainIf(_ % 2 == 0)
} yield tSet).commit
```

## Union of a `TSet`

Union of the sets A and B represents the set of distinct element belongs to set A or set B, or both.

```scala mdoc:silent
import zio._
import zio.stm._

val unionTSet: UIO[TSet[Int]] = (for {
  tSetA <- TSet.make(1, 2, 3, 4)
  tSetB <- TSet.make(3, 4, 5, 6)
  _     <- tSetA.union(tSetB)
} yield tSetA).commit
```

## Intersection of a `TSet`

Intersection of the sets A and B represents the set of elements belongs to both A and B (set of the common elements in A and B).

```scala mdoc:silent
import zio._
import zio.stm._

val intersectionTSet: UIO[TSet[Int]] = (for {
  tSetA <- TSet.make(1, 2, 3, 4)
  tSetB <- TSet.make(3, 4, 5, 6)
  _     <- tSetA.intersect(tSetB)
} yield tSetA).commit
```

## Difference of a `TSet`

Difference between sets A and B represents the set containing elements of set A but not in B (all elements of A except the element of B).

```scala mdoc:silent
import zio._
import zio.stm._

val diffTSet: UIO[TSet[Int]] = (for {
  tSetA <- TSet.make(1, 2, 3, 4)
  tSetB <- TSet.make(3, 4, 5, 6)
  _     <- tSetA.diff(tSetB)
} yield tSetA).commit
```

## Transform elements of a `TSet`

The transform function `A => A` allows computing a new value for every element in the set: 

```scala mdoc:silent
import zio._
import zio.stm._

val transformTSet: UIO[TSet[Int]] = (for {
  tSet <- TSet.make(1, 2, 3, 4)
  _    <- tSet.transform(a => a * a)
} yield tSet).commit
```

Note that it is possible to shrink a `TSet`:

```scala mdoc:silent
import zio._
import zio.stm._

val shrinkTSet: UIO[TSet[Int]] = (for {
  tSet <- TSet.make(1, 2, 3, 4)
  _    <- tSet.transform(_ => 1)
} yield tSet).commit
```
Resulting set in example above has only one element.

Using `transformM` is similar to `transform` except using pure function `A => A`, effectful function `A => STM[E, A]` is used:

```scala mdoc:silent
import zio._
import zio.stm._

val transformMTSet: UIO[TSet[Int]] = (for {
  tSet <- TSet.make(1, 2, 3, 4)
  _    <- tSet.transformM(a => STM.succeed(a * a))
} yield tSet).commit
```

Folds the elements of a `TSet` using the specified associative binary operator:

```scala mdoc:silent
import zio._
import zio.stm._

val foldTSet: UIO[Int] = (for {
  tSet <- TSet.make(1, 2, 3, 4)
  sum  <- tSet.fold(0)(_ + _)
} yield sum).commit
```

Using `foldM` is similar to `fold` except using pure function `(B, A) => B`, effectful function `(B, A) => STM[E, B]` is used:

```scala mdoc:silent
import zio._
import zio.stm._

val foldMTSet: UIO[Int] = (for {
  tSet <- TSet.make(1, 2, 3, 4)
  sum  <- tSet.foldM(0)((acc, el) => STM.succeed(acc + el))
} yield sum).commit
```

## Perform side-effect for `TSet` elements

`foreach` is used for performing side-effect for each element in set:

```scala mdoc:silent
import zio._
import zio.stm._

val foreachTSet = (for {
  tSet <- TSet.make(1, 2, 3, 4)
  _    <- tSet.foreach(a => STM.succeed(println(a)))
} yield tSet).commit
```

## Check `TSet` membership 

Checking whether element is present in a `TSet`:

```scala mdoc:silent
import zio._
import zio.stm._

val tSetContainsElem: UIO[Boolean] = (for {
  tSet <- TSet.make(1, 2, 3, 4)
  res  <- tSet.contains(3)
} yield res).commit
```

## Convert `TSet` to a `List`

In order to convert a `TSet` to a `List`:

```scala mdoc:silent
import zio._
import zio.stm._

val tSetToList: UIO[List[Int]] = (for {
  tSet <- TSet.make(1, 2, 3, 4)
  list <- tSet.toList
} yield list).commit
```

## Size of a `TSet`

Calculating the size of a `TSet`:

```scala mdoc:silent
import zio._
import zio.stm._

val tSetSize: UIO[Int] = (for {
  tSet <- TSet.make(1, 2, 3, 4)
  size <- tSet.size
} yield size).commit
```
