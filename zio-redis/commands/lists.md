# Lists

> A Redis list is an ordered sequence of values that supports fast pushes and pops at both ends. This page lists every list command on `Redis`, together with the option types `Side`, `Position`, `Rank` and `ListMaxLen`.

Keys and elements are encoded with the configured [`CodecSupplier`](../codecs.md). Commands that return elements end with `returning[A]`, as described in [Getting Started](../getting-started.md).

## Command Overview

| Purpose              | Commands                                                              |
| -------------------- | --------------------------------------------------------------------- |
| Push                 | `Redis#lPush`, `Redis#rPush`, `Redis#lPushX`, `Redis#rPushX`          |
| Pop                  | `Redis#lPop`, `Redis#rPop`                                            |
| Blocking pop         | `Redis#blPop`, `Redis#brPop`                                          |
| Move between lists   | `Redis#lMove`, `Redis#blMove`                                         |
| Read                 | `Redis#lIndex`, `Redis#lRange`, `Redis#lLen`                          |
| Search               | `Redis#lPos`, `Redis#lPosCount`                                       |
| Modify               | `Redis#lSet`, `Redis#lInsert`, `Redis#lRem`, `Redis#lTrim`            |

## Option Types

The option types come from `zio.redis._`:

| Type         | Values or shape                          | Used by                                   |
| ------------ | ---------------------------------------- | ----------------------------------------- |
| `Side`       | `Side.Left`, `Side.Right`                | `Redis#lMove`, `Redis#blMove`             |
| `Position`   | `Position.Before`, `Position.After`      | `Redis#lInsert`                           |
| `Rank`       | `Rank(rank: Long)`                       | `Redis#lPos`, `Redis#lPosCount`           |
| `ListMaxLen` | `ListMaxLen(count: Long)`                | `Redis#lPos`, `Redis#lPosCount`           |
| `Count`      | `Count(count: Long)`                     | `Redis#lPosCount`                         |

## Pushing and Popping

`Redis#lPush` prepends and `Redis#rPush` appends. Both accept several elements, create the list if needed, and return the new length. `Redis#lPushX` and `Redis#rPushX` do the same but do nothing when the list does not exist. `Redis#lPop` and `Redis#rPop` remove one element and return an `Option`:

```scala
import zio._
import zio.redis._

def queue(redis: Redis): IO[RedisError, Option[String]] =
  for {
    _    <- redis.rPush("jobs", "a", "b", "c")
    _    <- redis.lPushX("jobs", "urgent")
    head <- redis.lPop("jobs").returning[String]
  } yield head
```

## Blocking Pops

`Redis#blPop` and `Redis#brPop` wait until one of the given lists has an element. The timeout is in a second parameter list, and a zero duration blocks indefinitely. The result is `Option[(K, V)]`, holding the key that supplied the element, or `None` when the timeout expires:

```scala
import zio._
import zio.redis._

def worker(redis: Redis): IO[RedisError, Option[(String, String)]] =
  redis.blPop("high", "low")(5.seconds).returning[String]
```

## Moving Elements

`Redis#lMove` atomically pops from one side of a source list and pushes onto one side of a destination list. `Redis#blMove` is the blocking form and takes a `timeout: Duration` as its last argument. Both return an `Option` with the moved element:

```scala
import zio._
import zio.redis._

def claim(redis: Redis): IO[RedisError, Option[String]] =
  for {
    _     <- redis.lMove("pending", "working", Side.Left, Side.Right).returning[String]
    moved <- redis.blMove("pending", "working", Side.Left, Side.Right, 1.second).returning[String]
  } yield moved
```

## Reading Elements

`Redis#lIndex` returns the element at a zero-based index, where negative indices count from the tail. `Redis#lRange` takes a Scala `Range` and returns a `Chunk`. Redis treats `-1` as the last element, so `0 to -1` selects the whole list. `Redis#lLen` returns the length:

```scala
import zio._
import zio.redis._

def peek(redis: Redis): IO[RedisError, (Long, Option[String], Chunk[String])] =
  for {
    size  <- redis.lLen("jobs")
    last  <- redis.lIndex("jobs", -1L).returning[String]
    first <- redis.lRange("jobs", 0 to 9).returning[String]
  } yield (size, last, first)
```

## Searching

`Redis#lPos` returns the index of the first match as an `Option[Long]`. `Redis#lPosCount` takes a required `Count` and returns up to that many indexes as a `Chunk[Long]`. Both accept an optional `Rank` to select the nth match and an optional `ListMaxLen` to limit comparisons:

```scala
import zio._
import zio.redis._

def find(redis: Redis): IO[RedisError, (Option[Long], Chunk[Long])] =
  for {
    first <- redis.lPos("jobs", "b")
    all   <- redis.lPosCount("jobs", "b", Count(10L), Some(Rank(1L)), Some(ListMaxLen(1000L)))
  } yield (first, all)
```

## Modifying a List

| Method                                          | Effect                                                               |
| ----------------------------------------------- | -------------------------------------------------------------------- |
| `Redis#lSet(key, index, element)`               | Replaces the element at an index                                     |
| `Redis#lInsert(key, position, pivot, element)`  | Inserts next to the first `pivot`, returns the new length or `-1`    |
| `Redis#lRem(key, count, element)`               | Removes matching elements and returns how many were removed          |
| `Redis#lTrim(key, range)`                       | Keeps only the elements in the range                                 |

For `Redis#lRem`, a positive `count` removes from the head, a negative one from the tail, and zero removes all matches. The `element` parameter is a `String`, not a generic value:

```scala
import zio._
import zio.redis._

def tidy(redis: Redis): IO[RedisError, Long] =
  for {
    _       <- redis.lSet("jobs", 0L, "first")
    _       <- redis.lInsert("jobs", Position.After, "first", "second")
    removed <- redis.lRem("jobs", 0L, "stale")
    _       <- redis.lTrim("jobs", 0 to 99)
  } yield removed
```

## Next Steps

- [Sets](./sets.md) stores unique members without order.
- [Streams](./streams.md) offers consumer groups for work queues.
- [Keys](./keys.md) covers expiry for list keys.
