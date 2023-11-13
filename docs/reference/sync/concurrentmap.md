---
id: concurrentmap
title: "ConcurrentMap"
---

A `ConcurrentMap` is a wrapper over `java.util.concurrent.ConcurrentHashMap`.

## Motivation

The `HashMap` in the Scala standard library is not thread-safe. This means that if multiple fibers are accessing the same key, and trying to modify the value, this can lead to inconsistent results.

For example, assume we have a `HashMap` with a key `foo` and a value of `0`. Let's see what happens if we perform the `inc` workflow 100 times concurrently:

```scala mdoc:compile-only
import zio._

import scala.collection.mutable

object MainApp extends ZIOAppDefault {

  def inc(ref: Ref[mutable.HashMap[String, Int]], key: String) =
    for {
      _ <- ref.get
      _ <- ref.update { map =>
        map.updateWith(key)(_.map(_ + 1))
        map
      }
    } yield ()

  def run =
    for {
      ref <- Ref.make(mutable.HashMap(("foo", 0)))
      _ <- ZIO.foreachParDiscard(1 to 100)(_ => inc(ref, "foo"))
      _ <- ref.get.map(_.get("foo")).debug("The final value of foo is")
    } yield ()

}
// Different outputs on different executions:
// The final value of foo is Some(72)
// The final value of foo is Some(84)
// The final value of foo is Some(78)
// ...
```

Since the `HashMap` is not thread-safe, every time we run this program, we might get different results, which is not desirable.

So we need a concurrent data structure that can be used safely in concurrent environments, which the `ConcurrentMap` does for us:

```scala mdoc:compile-only
import zio._
import zio.concurrent.ConcurrentMap

object MainApp extends ZIOAppDefault {
  def run =
    for {
      map <- ConcurrentMap.make(("foo", 0), ("bar", 1), ("baz", 2))
      _ <- ZIO.foreachParDiscard(1 to 100)(_ =>
        map.computeIfPresent("foo", (_, v) => v + 1)
      )
      _ <- map.get("foo").debug("The final value of foo is")
    } yield ()
}
// Output:
// The final value of foo is Some(100)
```

## Creation

To make an empty `ConcurrentMap` we use `ConcurrentMap.empty`:

```scala mdoc:compile-only
import zio.concurrent.ConcurrentMap

val empty = ConcurrentMap.empty[String, Int]
```

And to make a `ConcurrentMap` with some initial values we use `ConcurrentMap.make` or `ConcurrentMap.fromIterable`:

```scala mdoc:compile-only
import zio.concurrent.ConcurrentMap

val map1 = ConcurrentMap.make(("foo", 0), ("bar", 1), ("baz", 2))
val map2 = ConcurrentMap.fromIterable(List(("foo", 0), ("bar", 1), ("baz", 2)))
```

## Update Operations

Basic operations are provided to manipulate the values in the `ConcurrentMap`:

### Putting values

| Method                                          | Definition                                                                                                 |
|-------------------------------------------------|------------------------------------------------------------------------------------------------------------|
| `put(key: K, value: V): UIO[Option[V]]`         | Adds a new key-value pair and optionally returns previously bound value.                                   |
| `putAll(keyValues: (K, V)*): UIO[Unit]`         | Adds all new key-value pairs.                                                                              |                                                                              |
| `putIfAbsent(key: K, value: V): UIO[Option[V]]` | Adds a new key-value pair, unless the key is already bound to some other value.                            |                            

### Removing values

| Method                                      | Definition                                                                                                 |
|---------------------------------------------|------------------------------------------------------------------------------------------------------------|
| `remove(key: K): UIO[Option[V]]`            | Removes the entry for the given key, optionally returning value associated with it.                        |                        
| `remove(key: K, value: V): UIO[Boolean]`    | Removes the entry for the given key if it is mapped to a given value.                                      |                                      
| `removeIf(p: (K, V) => Boolean): UIO[Unit]` | Removes all elements which do not satisfy the given predicate.                                             |
| `retainIf(p: (K, V) => Boolean): UIO[Unit]` | Removes all elements which do not satisfy the given predicate.                                             |
| `clear: UIO[Unit]`                          | Removes all elements.                                                                                      |

### Replacing values

| Method                                                    | Definition                                                                                                 |
|-----------------------------------------------------------|------------------------------------------------------------------------------------------------------------|
| `replace(key: K, value: V): UIO[Option[V]]`               | Replaces the entry for the given key only if it is mapped to some value.                                   |                                   
| `replace(key: K, oldValue: V, newValue: V): UIO[Boolean]` | Replaces the entry for the given key only if it was previously mapped to a given value.                    |

### Remapping Values

| Method                                                         | Definition                                                                                                 |
|----------------------------------------------------------------|------------------------------------------------------------------------------------------------------------|
| `compute(key: K, remap: (K, V) => V): UIO[Option[V]]`          | Attempts to compute a mapping for the given key and its current mapped value.                              |
| `def computeIfAbsent(key: K, map: K => V): UIO[V]`             | Computes a value of a non-existing key.                                                                    |
| `computeIfPresent(key: K, remap: (K, V) => V): UIO[Option[V]]` | Attempts to compute a new mapping of an existing key.                                                      |

## Retrieval Operations

| Method                                                            | Definition                                                                                                 |
|-------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------|
| `get(key: K): UIO[Option[V]]`                                     | Retrieves the value associated with the given key.                                                         |
| `exists(p: (K, V) => Boolean): UIO[Boolean]`                      | Tests whether a given predicate holds true for at least one element in a map.                              |
| `collectFirst[B](pf: PartialFunction[(K, V), B]): UIO[Option[B]]` | Finds the first element of a map for which the partial function is defined and applies the function to it. | 
| `fold[S](zero: S)(f: (S, (K, V)) => S): UIO[S]`                   | Folds the elements of a map using the given binary operator.                                               |
| `forall(p: (K, V) => Boolean): UIO[Boolean]`                      | Tests whether a predicate is satisfied by all elements of a map.                                           |
| `isEmpty: UIO[Boolean]`                                           | True if there are no elements in this map.                                                                 |
| `toChunk: UIO[Chunk[(K, V)]]`                                     | Collects all entries into a chunk.                                                                         |                                                                         
| `toList: UIO[List[(K, V)]]`                                       | Collects all entries into a list.                                                                          |                                                                          

## Example Usage

Given:

```scala mdoc:silent
import zio.concurrent.ConcurrentMap
import zio.{Chunk, ZIO}

for {
  emptyMap <- ConcurrentMap.empty[Int, String]
  data     <- ZIO.succeed(Chunk(1 -> "A", 2 -> "B", 3 -> "C"))
  mapA     <- ConcurrentMap.fromIterable(data)
  map100   <- ConcurrentMap.make(1 -> 100)
  mapB     <- ConcurrentMap.make(("A", 1), ("B", 2), ("C", 3))
} yield ()
```

| Operation                                                | Result  |
|----------------------------------------------------------|---------|
| `mapA.collectFirst { case (3, _) => "Three" }`           | "Three" |
| `mapA.collectFirst { case (4, _) => "Four" }`            | Empty   |
| `map100.compute(1, _+_).get(1)`                          | 101     |
| `emptyMap.computeIfAbsent("abc", _.length).get("abc")`   | 3       |
| `map100.computeIfPresent(1, _+_).get(1)`                 | 101     |
| `mapA.exists((k, _) => k % 2 == 0)`                      | true    |
| `mapA.exists((k, _) => k == 4)`                          | false   |
| `mapB.fold(0) { case (acc, (_, value)) => acc + value }` | 6       |
| `mapB.forall((_, v) => v < 4)`                           | true    |
| `emptyMap.get(1)`                                        | None    |
| `emptyMap.put(1, "b").get(1)`                            | "b"     |
| `mapA.putIfAbsent(2, "b").get(2)`                        | "B"     |
| `emptyMap.putAll((1, "A"), (2, "B"), (3, "C")).get(1)`   | "A"     |
| `mapA.remove(1).get(1)`                                  | None    |
| `mapA.remove(1,"b").get(1)`                              | "A"     |
| `mapA.removeIf((k, _) => k != 1).get(1)`                 | "A"     |
| `mapA.removeIf((k, _) => k != 1).get(2)`                 | None    |
| `mapA.retainIf((k, _) => k == 1).get(1)`                 | "A"     |
| `mapA.retainIf((k, _) => k == 1).get(2)`                 | None    |
| `mapA.clear.isEmpty`                                     | true    |
