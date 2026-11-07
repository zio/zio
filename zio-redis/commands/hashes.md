# Hashes

> A Redis hash maps field names to values under a single key, which makes it a good fit for objects and counters grouped by an owner. This page lists every hash command on `Redis` and shows how to call it.

Keys, fields and values are encoded with the configured [`CodecSupplier`](../codecs.md). Commands that return user data end with `returning[A]`, as described in [Getting Started](../getting-started.md).

## Command Overview

| Purpose              | Commands                                                       |
| -------------------- | -------------------------------------------------------------- |
| Write fields         | `Redis#hSet`, `Redis#hSetNx`                                   |
| Read fields          | `Redis#hGet`, `Redis#hmGet`, `Redis#hGetAll`                   |
| Inspect a hash       | `Redis#hExists`, `Redis#hLen`, `Redis#hStrLen`                 |
| List names or values | `Redis#hKeys`, `Redis#hVals`                                   |
| Remove fields        | `Redis#hDel`                                                   |
| Update numbers       | `Redis#hIncrBy`, `Redis#hIncrByFloat`                          |
| Sample and iterate   | `Redis#hRandField`, `Redis#hScan`                              |

## Writing Fields

`Redis#hSet` takes one or more `(field, value)` pairs and returns the number of fields that were added. `Redis#hSetNx` sets a single field only when it does not exist yet and returns `true` if it wrote the value:

```scala
import zio._
import zio.redis._

def writeUser(redis: Redis): IO[RedisError, Boolean] =
  for {
    _     <- redis.hSet("user:1", "name" -> "Ada", "city" -> "London")
    added <- redis.hSetNx("user:1", "name", "Grace")
  } yield added
```

Here `added` is `false` because `name` already exists.

## Reading Fields

`Redis#hGet` returns an `Option` for one field, and `Redis#hmGet` returns a `Chunk[Option[V]]` with one entry per requested field, in order. `Redis#hGetAll` returns every pair as a `Map`, so `returning` takes two type parameters, the field type and the value type:

```scala
import zio._
import zio.redis._

def readUser(redis: Redis): IO[RedisError, Map[String, String]] =
  for {
    name  <- redis.hGet("user:1", "name").returning[String]
    some  <- redis.hmGet("user:1", "name", "email").returning[String]
    all   <- redis.hGetAll("user:1").returning[String, String]
    _     <- ZIO.debug(s"name = $name, requested = $some")
  } yield all
```

## Inspecting a Hash

These commands answer questions about a hash without returning its data:

| Method                       | Returns   | Meaning                                                  |
| ---------------------------- | --------- | -------------------------------------------------------- |
| `Redis#hExists(key, field)`  | `Boolean` | Whether the field exists                                 |
| `Redis#hLen(key)`            | `Long`    | Number of fields                                         |
| `Redis#hStrLen(key, field)`  | `Long`    | String length of the value, or zero if field or key is missing |

To list the names or the values of a hash, use `Redis#hKeys` and `Redis#hVals`. Both return a `Chunk`:

```scala
import zio._
import zio.redis._

def describe(redis: Redis): IO[RedisError, (Chunk[String], Chunk[String])] =
  for {
    names  <- redis.hKeys("user:1").returning[String]
    values <- redis.hVals("user:1").returning[String]
  } yield (names, values)
```

## Removing Fields

`Redis#hDel` removes one or more fields and returns how many were actually removed:

```scala
import zio._
import zio.redis._

def dropFields(redis: Redis): IO[RedisError, Long] =
  redis.hDel("user:1", "city", "email")
```

## Updating Numbers

`Redis#hIncrBy` adds a `Long` and `Redis#hIncrByFloat` adds a `Double` to a field. A missing field starts from zero, and the command returns the new value. Redis returns an error if the field holds a value of the wrong kind:

```scala
import zio._
import zio.redis._

def countVisit(redis: Redis): IO[RedisError, (Long, Double)] =
  for {
    visits <- redis.hIncrBy("stats", "visits", 1L)
    score  <- redis.hIncrByFloat("stats", "score", 0.5)
  } yield (visits, score)
```

## Sampling Random Fields

`Redis#hRandField` has two overloads. The first returns one random field as an `Option`. The second takes a `count` and a `withValues` flag and returns a `Chunk`:

| Overload                                          | Result type    |
| ------------------------------------------------- | -------------- |
| `hRandField(key)`                                 | `Option[V]`    |
| `hRandField(key, count, withValues = false)`      | `Chunk[V]`     |

A positive `count` returns at most that many distinct fields. A negative `count` returns exactly `abs(count)` fields and may repeat them. When `withValues` is `true`, fields and values are interleaved in the returned chunk, so both must decode to the same type `V`:

```scala
import zio._
import zio.redis._

def sample(redis: Redis): IO[RedisError, (Option[String], Chunk[String])] =
  for {
    one  <- redis.hRandField("user:1").returning[String]
    many <- redis.hRandField("user:1", 2L, withValues = true).returning[String]
  } yield (one, many)
```

## Scanning

`Redis#hScan` iterates a hash with a cursor. Start with cursor `0` and pass the returned cursor into the next call until Redis returns `0` again. The optional `pattern` filters fields with a glob-style pattern, and `count` is a hint of type `Count` for how many entries to examine per call:

```scala
import zio._
import zio.redis._

def scanAll(redis: Redis): IO[RedisError, Chunk[(String, String)]] = {
  def loop(cursor: Long, acc: Chunk[(String, String)]): IO[RedisError, Chunk[(String, String)]] =
    redis.hScan("user:1", cursor, Some("n*"), Some(Count(100L))).returning[String, String].flatMap {
      case (0L, items)   => ZIO.succeed(acc ++ items)
      case (next, items) => loop(next, acc ++ items)
    }

  loop(0L, Chunk.empty)
}
```

## Next Steps

- [Strings](./strings.md) covers plain values and counters.
- [Keys](./keys.md) covers expiry and deletion of the whole hash.
- [Sets](./sets.md) covers unordered collections of unique members.
