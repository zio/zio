# Sets

> A Redis set is an unordered collection of unique members. This page lists every set command on `Redis`, grouped by what it does.

Members and keys are encoded with the configured [`CodecSupplier`](../codecs.md). Commands that return members end with `returning[A]`, as described in [Getting Started](../getting-started.md).

## Command Overview

| Purpose              | Commands                                                                |
| -------------------- | ----------------------------------------------------------------------- |
| Add and remove       | `Redis#sAdd`, `Redis#sRem`, `Redis#sPop`, `Redis#sMove`                 |
| Inspect              | `Redis#sCard`, `Redis#sIsMember`, `Redis#sMembers`, `Redis#sRandMember` |
| Combine, return      | `Redis#sUnion`, `Redis#sInter`, `Redis#sDiff`                           |
| Combine, store       | `Redis#sUnionStore`, `Redis#sInterStore`, `Redis#sDiffStore`            |
| Iterate              | `Redis#sScan`                                                           |

## Adding and Removing Members

`Redis#sAdd` and `Redis#sRem` accept one or more members. `Redis#sAdd` returns the number of members that were newly added, and `Redis#sRem` returns the number that were actually removed:

```scala
import zio._
import zio.redis._

def editTags(redis: Redis): IO[RedisError, (Long, Long)] =
  for {
    added   <- redis.sAdd("tags", "scala", "zio", "redis")
    removed <- redis.sRem("tags", "redis", "missing")
  } yield (added, removed)
```

`Redis#sMove(source, destination, member)` moves a member between two sets and returns `true` if the member was found in the source:

```scala
import zio._
import zio.redis._

def archive(redis: Redis): IO[RedisError, Boolean] =
  redis.sMove("tags", "archived-tags", "zio")
```

## Inspecting a Set

These commands read a set without changing it:

| Method                              | Returns      | Meaning                                       |
| ----------------------------------- | ------------ | --------------------------------------------- |
| `Redis#sCard(key)`                  | `Long`       | Number of members, or zero if the key is missing |
| `Redis#sIsMember(key, member)`      | `Boolean`    | Whether the member is in the set              |
| `Redis#sMembers(key)`               | `Chunk[A]`   | All members                                   |

To read the members and check one of them:

```scala
import zio._
import zio.redis._

def inspect(redis: Redis): IO[RedisError, (Long, Boolean, Chunk[String])] =
  for {
    size    <- redis.sCard("tags")
    isScala <- redis.sIsMember("tags", "scala")
    members <- redis.sMembers("tags").returning[String]
  } yield (size, isScala, members)
```

## Random Members

`Redis#sPop` removes random members, and `Redis#sRandMember` returns them without removing. Both take an optional `count: Option[Long]` and always return a `Chunk`, even when `count` is `None`:

```scala
import zio._
import zio.redis._

def pickWinners(redis: Redis): IO[RedisError, (Chunk[String], Chunk[String])] =
  for {
    peek   <- redis.sRandMember("entries", Some(2L)).returning[String]
    winner <- redis.sPop("entries", Some(1L)).returning[String]
  } yield (peek, winner)
```

## Combining Sets

Each operation has two forms. The first returns the resulting members, and the second writes them to a destination key and returns the size of the new set:

| Operation    | Returns members                   | Stores result                                        |
| ------------ | --------------------------------- | ---------------------------------------------------- |
| Union        | `Redis#sUnion(key, keys*)`        | `Redis#sUnionStore(destination, key, keys*)`         |
| Intersection | `Redis#sInter(key, keys*)`        | `Redis#sInterStore(destination, key, keys*)`         |
| Difference   | `Redis#sDiff(key, keys*)`         | `Redis#sDiffStore(destination, key, keys*)`          |

`Redis#sDiff` subtracts every later set from the first one, so the order of keys matters. To compute set algebra over two sets:

```scala
import zio._
import zio.redis._

def combine(redis: Redis): IO[RedisError, (Chunk[String], Chunk[String], Long)] =
  for {
    both   <- redis.sInter("scala-devs", "zio-devs").returning[String]
    onlyA  <- redis.sDiff("scala-devs", "zio-devs").returning[String]
    stored <- redis.sUnionStore("all-devs", "scala-devs", "zio-devs")
  } yield (both, onlyA, stored)
```

## Scanning

`Redis#sScan` iterates the members with a cursor. Start from `0` and continue with the returned cursor until it is `0` again. The optional `pattern` is a glob-style filter and `count` is a `Count` hint:

```scala
import zio._
import zio.redis._

def scanTags(redis: Redis): IO[RedisError, Chunk[String]] = {
  def loop(cursor: Long, acc: Chunk[String]): IO[RedisError, Chunk[String]] =
    redis.sScan("tags", cursor, Some("s*"), Some(Count(50L))).returning[String].flatMap {
      case (0L, items)   => ZIO.succeed(acc ++ items)
      case (next, items) => loop(next, acc ++ items)
    }

  loop(0L, Chunk.empty)
}
```

## Next Steps

- [Sorted Sets](./sorted-sets.md) adds a score to every member.
- [Lists](./lists.md) keeps insertion order and allows duplicates.
- [Hashes](./hashes.md) stores field and value pairs.
