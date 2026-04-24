---
id: concurrentSet
title: "ConcurrentSet"
---

A `ConcurrentSet` is a Set wrapper over `java.util.concurrent.ConcurrentHashMap`.

## Operations

### Creation

| Method                                                      | Definition                                                           |
|-------------------------------------------------------------|----------------------------------------------------------------------|
| `empty[A]: UIO[ConcurrentSet[A]]`                           | Makes an empty `ConcurrentSet`                                       |
| `empty[A](initialCapacity: Int): UIO[ConcurrentSet[A]]`     | Makes an empty `ConcurrentSet` with ìnitial capacity                 |
| `fromIterable[A](as: Iterable[(A)]): UIO[ConcurrentSet[A]]` | Makes a new `ConcurrentSet` initialized with the provided collection |
| `make[A](as: A*): UIO[ConcurrentSet[A]]`                    | Makes a new `ConcurrentSet` initialized with the provided elements   |

### Use

| Method                                                        | Definition                                                                                                 |
|---------------------------------------------------------------|------------------------------------------------------------------------------------------------------------|
| `add(x: A): UIO[Boolean]`                                     | Adds a new value.                                                                                          |
| `addAll(xs: Iterable[A]): UIO[Boolean]`                       | Adds all new values.                                                                                       |
| `collectFirst[B](pf: PartialFunction[(A, B)): UIO[Option[B]]` | Finds the first element of a set for which the partial function is defined and applies the function to it. |
| `exists(p: A => Boolean): UIO[Boolean]`                       | Tests whether a given predicate holds true for at least one element in the set.                            |
| `fold[R, E, S](zero: S)(f: (S, A) => S): UIO[S]`              | Folds the elements of a set using the given binary operator.                                               |
| `forall(p: A => Boolean): UIO[Boolean]`                       | Tests whether a predicate is satisfied by all elements of a set.                                           |
| `find[B](p: A => Boolean): UIO[Option[A]]`                    | Retrieves the elements in which predicate is satisfied.                                                    |
| `remove(x: A): UIO[Boolean]`                                  | Removes the entry for the given value if it is mapped to an existing element.                              |
| `removeAll(xs: Iterable[A]): UIO[Boolean]`                    | Removes all the entries for the given values if they are mapped to an existing element.                    |
| `removeIf(p: A => Boolean): UIO[Boolean]`                     | Removes all elements which satisfy the given predicate.                                                    |
| `retainAll(xs: Iterable[A]): UIO[Boolean]`                    | Retain all the entries for the given values if they are mapped to an existing element.                     |
| `retainIf(p: A => Boolean): UIO[Boolean]`                     | Removes all elements which do not satisfy the given predicate.                                             |
| `clear: UIO[Unit]`                                            | Removes all elements.                                                                                      |
| `contains(x: A): UIO[Boolean]`                                | Tests whether if the element is in the set.                                                                |
| `containsAll(xs: Iterable[A]): UIO[Boolean]`                  | Tests if the elements in the collection are a subset of the set.                                           |
| `size: UIO[Int]`                                              | Number of elements in the set.                                                                             |
| `isEmpty: UIO[Boolean]`                                       | True if there are no elements in the set.                                                                  |
| `toSet: UIO[Set[A]]`                                          | Create a concurrent set from a set.                                                                        |
| `transform(f: A => A): UIO[Unit]`                             | Create a concurrent set from a collection.                                                                 |

## Example Usage

Given:

```scala
import zio.concurrent.ConcurrentSet

for {
  emptySet  <- ConcurrentSet.empty[Int] 
  setA      <- ConcurrentSet.make[Int](1, 2, 3, 4)
} yield ()
```

| Operation                                 | Result                |
|-------------------------------------------|-----------------------|
| `emptySet.add(1).toSet`                   | Set(1)                |
| `setA.addAll(Chunk(5, 6).toSet)`          | Set(1, 2, 3, 4, 5, 6) |
| `setA.remove(1).toSet`                    | Set(2, 3, 4)          |
| `setA.removeAll(1, 3).toSet`              | Set(2, 4)             |
| `setA.retainAll(List(1, 3, 5, 6)).toSet`  | Set(1, 3)             |
| `setA.clear.isEmpty`                      | true                  |
| `setA.contains(5)`                        | false                 |
| `setA.containsAll(Chunk(1, 2, 3))`        | true                  |
| `setA.exists(_ > 4)`                      | false                 |
| `setA.forAll(_ < 5)`                      | true                  |
| `setA.removeIf(_ % 2 == 0)`               | Set(2, 4)             |
| `setA.retainIf(_ % 2 == 0)`               | Set(1, 3)             |
| `setA.find(_ > 2)`                        | Set(3, 4)             |
| `setA.collectFirst { case 3 => "Three" }` | Set(3)                |
| `setA.size`                               | 4                     |
| `setA.transform(_ + 10)`                  | Set(11, 12, 13, 14)   |
| `setA.fold(0)(_ + _)`                     | 10                    |
