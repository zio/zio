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
```

Since the `HashMap` is not thread-safe, every time we run this program, we might get different results.

So we need a concurrent data structure that can be used safely in concurrent environments, which the `ConcurrentMap` does for us.

## Operations

### Creation

| Method                                                                | Definition                                                                              |
|-----------------------------------------------------------------------|-----------------------------------------------------------------------------------------|
|`empty[K, V]: UIO[ConcurrentMap[K, V]]`                                | Makes an empty `ConcurrentMap`                                                          |
|`fromIterable[K, V](pairs: Iterable[(K, V)]): UIO[ConcurrentMap[K, V]]`| Makes a new `ConcurrentMap` initialized with the provided collection of key-value pairs |
|`make[K, V](pairs: (K, V)*): UIO[ConcurrentMap[K, V]]`                 | Makes a new `ConcurrentMap` initialized with the provided key-value pairs               |

### Use

| Method                                                            | Definition                                                                                                 |
|-------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------|
| `collectFirst[B](pf: PartialFunction[(K, V), B]): UIO[Option[B]]` | Finds the first element of a map for which the partial function is defined and applies the function to it. | 
| `compute(key: K, remap: (K, V) => V): UIO[Option[V]]`             | Attempts to compute a mapping for the given key and its current mapped value.                              |
| `def computeIfAbsent(key: K, map: K => V): UIO[V]`                | Computes a value of a non-existing key.                                                                    |
| `computeIfPresent(key: K, remap: (K, V) => V): UIO[Option[V]]`    | Attempts to compute a new mapping of an existing key.                                                      |
| `exists(p: (K, V) => Boolean): UIO[Boolean]`                      | Tests whether a given predicate holds true for at least one element in a map.                              |
| `fold[S](zero: S)(f: (S, (K, V)) => S): UIO[S]`                   | Folds the elements of a map using the given binary operator.                                               |
| `forall(p: (K, V) => Boolean): UIO[Boolean]`                      | Tests whether a predicate is satisfied by all elements of a map.                                           |
| `get(key: K): UIO[Option[V]]`                                     | Retrieves the value associated with the given key.                                                         |
| `put(key: K, value: V): UIO[Option[V]]`                           | Adds a new key-value pair and optionally returns previously bound value.                                   |
| `putAll(keyValues: (K, V)*): UIO[Unit]`                           | Adds all new key-value pairs.                                                                              |                                                                              |
| `putIfAbsent(key: K, value: V): UIO[Option[V]]`                   | Adds a new key-value pair, unless the key is already bound to some other value.                            |                            
| `remove(key: K): UIO[Option[V]]`                                  | Removes the entry for the given key, optionally returning value associated with it.                        |                        
| `remove(key: K, value: V): UIO[Boolean]`                          | Removes the entry for the given key if it is mapped to a given value.                                      |                                      
| `removeIf(p: (K, V) => Boolean): UIO[Unit]`                       | Removes all elements which do not satisfy the given predicate.                                             |                                             
| `retainIf(p: (K, V) => Boolean): UIO[Unit]`                       | Removes all elements which do not satisfy the given predicate.                                             |                                             
| `replace(key: K, value: V): UIO[Option[V]]`                       | Replaces the entry for the given key only if it is mapped to some value.                                   |                                   
| `replace(key: K, oldValue: V, newValue: V): UIO[Boolean]`         | Replaces the entry for the given key only if it was previously mapped to a given value.                    |                    
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
