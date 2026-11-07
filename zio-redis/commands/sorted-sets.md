# Sorted Sets

> A sorted set stores unique members, each with a floating point score, and keeps them ordered by score. This page documents the sorted set commands of ZIO Redis and the option types they accept.

All commands are methods on the `Redis` service. Keys and members are encoded with the configured [`CodecSupplier`](../codecs.md). Commands that return members need `returning[A]`, as explained in [Getting Started](../getting-started.md).

## Commands

The commands are grouped below by purpose:

| Group          | Methods                                                                                                  |
| -------------- | -------------------------------------------------------------------------------------------------------- |
| Add and update | `Redis#zAdd`, `Redis#zAddWithIncr`, `Redis#zIncrBy`                                                      |
| Size and score | `Redis#zCard`, `Redis#zCount`, `Redis#zLexCount`, `Redis#zScore`, `Redis#zMScore`                        |
| Rank           | `Redis#zRank`, `Redis#zRankWithScore`, `Redis#zRevRank`, `Redis#zRevRankWithScore`                       |
| Range queries  | `Redis#zRange`, `Redis#zRangeWithScores`, `Redis#zRangeStore`                                            |
| Random members | `Redis#zRandMember`, `Redis#zRandMemberWithScores`                                                       |
| Remove         | `Redis#zRem`, `Redis#zRemRangeByLex`, `Redis#zRemRangeByRank`, `Redis#zRemRangeByScore`                  |
| Pop            | `Redis#zPopMin`, `Redis#zPopMax`, `Redis#zmPopMin`, `Redis#zmPopMax`                                     |
| Blocking pop   | `Redis#bzPopMin`, `Redis#bzPopMax`, `Redis#bZmPopMin`, `Redis#bZmPopMax`                                 |
| Set algebra    | `Redis#zDiff`, `Redis#zDiffWithScores`, `Redis#zDiffStore`, `Redis#zInter`, `Redis#zInterWithScores`, `Redis#zInterStore`, `Redis#zInterCard`, `Redis#zUnion`, `Redis#zUnionWithScores`, `Redis#zUnionStore` |
| Iteration      | `Redis#zScan`                                                                                            |

The deprecated methods `Redis#zRevRangeWithScores`, `Redis#zRangeByScoreWithScores` and `Redis#zRevRangeByScoreWithScores`, and the overloads of `Redis#zRange` and `Redis#zRangeWithScores` that take a Scala `Range`, are replaced by the `SortedSetRange` versions described below.

## Option Types

The following types are available after `import zio.redis._`:

| Type                | Shape                                                                       | Used by                                   |
| ------------------- | --------------------------------------------------------------------------- | ----------------------------------------- |
| `MemberScore[M]`    | `MemberScore(member: M, score: Double)`                                     | `zAdd` and every `WithScores` result      |
| `RankScore`         | `RankScore(rank: Long, score: Double)`                                      | `zRankWithScore`, `zRevRankWithScore`     |
| `Update`            | `SetExisting` (`XX`), `SetNew` (`NX`), `SetLessThan` (`LT`), `SetGreaterThan` (`GT`) | `zAdd`, `zAddWithIncr`            |
| `Changed`           | Singleton `Changed`, makes the result count changed members                 | `zAdd`, `zAddWithIncr`                    |
| `Increment`         | Singleton `Increment`, makes `ZADD` act like `ZINCRBY`                      | `zAddWithIncr`                            |
| `Aggregate`         | `Aggregate.Sum`, `Aggregate.Min` or `Aggregate.Max`                         | `zInter*`, `zUnion*`                      |
| `Limit`             | `Limit(offset: Long, count: Long)`                                          | `zRange`, `zRangeWithScores`, `zRangeStore` |
| `Count`             | `Count(count: Long)`                                                        | `zScan`                                   |
| `SortedSetRange`    | `Range`, `ScoreRange` or `LexRange`                                         | `zRange`, `zRangeWithScores`, `zRangeStore` |

`SortedSetRange` has three cases, and each one carries its own bound types:

| Case                                  | Bound types                                                                                   | Queries by |
| ------------------------------------- | --------------------------------------------------------------------------------------------- | ---------- |
| `SortedSetRange.Range(min, max)`      | `RangeMinimum(value: Long)`, `RangeMaximum.Inclusive(value)`, `RangeMaximum.Exclusive(value)`  | Rank       |
| `SortedSetRange.ScoreRange(min, max)` | `ScoreMinimum.Closed`, `ScoreMinimum.Open`, `ScoreMinimum.Infinity`, `ScoreMaximum.Closed`, `ScoreMaximum.Open`, `ScoreMaximum.Infinity` | Score |
| `SortedSetRange.LexRange(min, max)`   | `LexMinimum.Closed`, `LexMinimum.Open`, `LexMinimum.UnboundedNegative`, `LexMinimum.UnboundedPositive`, and the same four on `LexMaximum` | Lexicographic order |

`Closed` includes the bound and `Open` excludes it. `ScoreMinimum.Infinity` means `-inf` and `ScoreMaximum.Infinity` means `+inf`. For lexicographic bounds, `UnboundedNegative` is `-` and `UnboundedPositive` is `+`.

## Adding and Updating Members

`Redis#zAdd` adds members with scores and returns the number of new members. The optional `update` parameter restricts the write, and `change` makes the result count every member whose score changed:

```scala
import zio._
import zio.redis._

def addScores(redis: Redis): IO[RedisError, Long] =
  redis.zAdd("leaderboard", Some(Update.SetNew), Some(Changed))(
    MemberScore("alice", 120.0),
    MemberScore("bob", 95.5)
  )
```

`Redis#zAddWithIncr` adds the given score to the existing one and returns the new score, or `None` if an `update` condition aborted the write. `Redis#zIncrBy` does the same with a whole number increment and always returns the new score:

```scala
import zio._
import zio.redis._

def bumpScores(redis: Redis): IO[RedisError, (Option[Double], Double)] =
  for {
    a <- redis.zAddWithIncr("leaderboard")(Increment, MemberScore("alice", 10.0))
    b <- redis.zIncrBy("leaderboard", 5L, "bob")
  } yield (a, b)
```

## Size, Score and Rank

The size and score commands answer questions without returning members. `Redis#zCount` takes a Scala `Range` of whole number scores, and `Redis#zLexCount` takes a `SortedSetRange.LexRange`:

```scala
import zio._
import zio.redis._

def sizes(redis: Redis): IO[RedisError, (Long, Long, Long)] =
  for {
    total   <- redis.zCard("leaderboard")
    inScore <- redis.zCount("leaderboard", 0 to 100)
    inLex   <- redis.zLexCount("names", SortedSetRange.LexRange(LexMinimum.Closed("a"), LexMaximum.Open("n")))
  } yield (total, inScore, inLex)
```

`Redis#zScore` returns the score of one member, and `Redis#zMScore` returns one optional score per member. Its first argument is the sorted set key, followed by the members, and all of them share the single type `K`. The rank commands return `None` for a missing member, and the `Rev` variants count from the highest score:

```scala
import zio._
import zio.redis._

def lookups(redis: Redis): IO[RedisError, (Option[Double], Option[Long], Option[RankScore])] =
  for {
    score <- redis.zScore("leaderboard", "alice")
    rank  <- redis.zRank("leaderboard", "alice")
    both  <- redis.zRevRankWithScore("leaderboard", "alice")
  } yield (score, rank, both)
```

## Range Queries

`Redis#zRange` and `Redis#zRangeWithScores` take a `SortedSetRange`, an optional `rev` flag and an optional `Limit`. The first returns members, and the second returns `MemberScore` values. To read the top three members by rank, with scores:

```scala
import zio._
import zio.redis._

def topThree(redis: Redis): IO[RedisError, Chunk[MemberScore[String]]] =
  redis
    .zRangeWithScores(
      "leaderboard",
      SortedSetRange.Range(RangeMinimum(0L), RangeMaximum.Inclusive(2L)),
      rev = true
    )
    .returning[String]
```

To query by score and page through the matches, use `SortedSetRange.ScoreRange` with a `Limit`:

```scala
import zio._
import zio.redis._

def byScore(redis: Redis): IO[RedisError, Chunk[String]] =
  redis
    .zRange(
      "leaderboard",
      SortedSetRange.ScoreRange(ScoreMinimum.Closed(50.0), ScoreMaximum.Infinity),
      limit = Some(Limit(0L, 10L))
    )
    .returning[String]
```

When `rev` is `true`, the bounds are still given as minimum then maximum, and the library reorders them for score and lexicographic queries. To copy a range into another key, use `Redis#zRangeStore`, which returns the size of the destination:

```scala
import zio._
import zio.redis._

def copyRange(redis: Redis): IO[RedisError, Long] =
  redis.zRangeStore(
    "leaderboard:top",
    "leaderboard",
    SortedSetRange.Range(RangeMinimum(0L), RangeMaximum.Inclusive(9L)),
    rev = true
  )
```

## Random Members

`Redis#zRandMember(key)` returns an `Option` of one member. The overload with a `count` returns a `Chunk`, and `Redis#zRandMemberWithScores` includes the scores. A negative count allows repeated members:

```scala
import zio._
import zio.redis._

def sample(redis: Redis): IO[RedisError, (Option[String], Chunk[MemberScore[String]])] =
  for {
    one  <- redis.zRandMember("leaderboard").returning[String]
    many <- redis.zRandMemberWithScores("leaderboard", 3L).returning[String]
  } yield (one, many)
```

## Removing Members

The remove commands return the number of members removed. `Redis#zRemRangeByRank` takes a Scala `Range`, `Redis#zRemRangeByScore` takes a `SortedSetRange.ScoreRange`, and `Redis#zRemRangeByLex` takes a `SortedSetRange.LexRange`:

```scala
import zio._
import zio.redis._

def prune(redis: Redis): IO[RedisError, Long] =
  for {
    a <- redis.zRem("leaderboard", "bob", "carol")
    b <- redis.zRemRangeByRank("leaderboard", 0 to 1)
    c <- redis.zRemRangeByScore("leaderboard", SortedSetRange.ScoreRange(ScoreMinimum.Infinity, ScoreMaximum.Open(10.0)))
  } yield a + b + c
```

## Popping Members

`Redis#zPopMin` and `Redis#zPopMax` remove members from a single key and return them with their scores. `Redis#zmPopMin` and `Redis#zmPopMax` take several keys and pop from the first non-empty one, returning that key together with the popped members. The `count` is the first parameter list:

```scala
import zio._
import zio.redis._

def popLowest(redis: Redis): IO[RedisError, Option[(String, Chunk[MemberScore[String]])]] =
  redis.zmPopMin(Some(2L))("queue:high", "queue:low").returning[String]
```

## Blocking Pops

The blocking variants wait until a member is available or the `timeout` expires. `Redis#bzPopMin` and `Redis#bzPopMax` return the key and a single `MemberScore`, and `Redis#bZmPopMin` and `Redis#bZmPopMax` mirror `zmPopMin` and `zmPopMax` with a timeout. All of them return `None` when the timeout expires:

```scala
import zio._
import zio.redis._

def nextJob(redis: Redis): IO[RedisError, Option[(String, MemberScore[String])]] =
  redis.bzPopMin(5.seconds, "queue:high", "queue:low").returning[String]
```

## Set Algebra

The set algebra commands combine several sorted sets. Each family has a plain form that returns members, a `WithScores` form that returns `MemberScore` values, and a `Store` form that writes into a destination key and returns its size. `Redis#zDiff` has no weights or aggregate, while `Redis#zInter` and `Redis#zUnion` accept both, and `Redis#zInterCard` only counts. The weights have the type `Option[::[Double]]`, a non-empty list with one weight per input key:

```scala
import zio._
import zio.redis._

def combine(redis: Redis): IO[RedisError, (Chunk[MemberScore[String]], Long, Long)] =
  for {
    inter <- redis
               .zInterWithScores("scores:a", "scores:b")(Some(Aggregate.Max), Some(::(2.0, List(1.0))))
               .returning[String]
    union <- redis.zUnionStore("scores:all", "scores:a", "scores:b")(None, Some(Aggregate.Sum))
    card  <- redis.zInterCard(Some(100L))("scores:a", "scores:b")
  } yield (inter, union, card)
```

Note that `Redis#zInter` takes `aggregate` before `weights`, while `Redis#zUnion` and `Redis#zUnionStore` take `weights` before `aggregate`. `Redis#zDiffStore` takes the destination first, then the source keys.

## Iterating

`Redis#zScan` walks a sorted set in batches. Start with cursor `0L`, and continue with the returned cursor until it is `0L` again. The optional `pattern` filters members, and `count` is a hint for the batch size:

```scala
import zio._
import zio.redis._

def scanAll(redis: Redis, cursor: Long = 0L): IO[RedisError, Chunk[MemberScore[String]]] =
  redis.zScan("leaderboard", cursor, Some("a*"), Some(Count(100L))).returning[String].flatMap {
    case (0L, batch)   => ZIO.succeed(batch)
    case (next, batch) => scanAll(redis, next).map(batch ++ _)
  }
```

## Next Steps

- [Geo](./geo.md) builds on sorted sets to store coordinates.
- [Sets](./sets.md) covers unordered unique members.
- [Keys](./keys.md) covers expiry and deletion for sorted set keys.
