# Keys

> Key commands work on keys regardless of the type of value they hold. This page lists every key command on the `Redis` service, grouped by purpose, with the option types they take.

## Inspecting Keys

These commands check whether keys exist and what they hold:

| Command         | Result       | Description                                              |
| --------------- | ------------ | -------------------------------------------------------- |
| `Redis#exists`  | `Long`       | Counts how many of the given keys exist                  |
| `Redis#typeOf`  | `RedisType`  | Returns the type of the value stored at a key            |
| `Redis#randomKey` | `Option[A]` | Returns a random key, or `None` when the database is empty |
| `Redis#touch`   | `Long`       | Updates the last access time of the given keys           |

`Redis#exists` counts a key once for every time it is listed, so `exists("a", "a")` returns `2` when `a` exists. `Redis#typeOf` returns a `RedisType`, which has the cases `String`, `List`, `Set`, `SortedSet`, `Hash` and `Stream`. To check a key and branch on its type:

```scala
import zio._
import zio.redis._

def describe(redis: Redis): IO[RedisError, String] =
  redis.typeOf("user:1").map {
    case RedisType.Hash => "a hash"
    case other          => s"something else: $other"
  }
```

`Redis#randomKey` needs a result type like any command that returns a key. To pick any key:

```scala
import zio._
import zio.redis._

def anyKey(redis: Redis): IO[RedisError, Option[String]] =
  redis.randomKey.returning[String]
```

## Deleting Keys

Both commands take one key and any number of extra keys, and return how many were removed:

| Command        | Result | Description                                                    |
| -------------- | ------ | -------------------------------------------------------------- |
| `Redis#del`    | `Long` | Removes the keys and frees their memory immediately            |
| `Redis#unlink` | `Long` | Removes the keys and frees their memory in a background thread |

`Redis#unlink` is the better choice for large values because it does not block the server. To delete several keys:

```scala
import zio._
import zio.redis._

def cleanup(redis: Redis): IO[RedisError, Long] =
  redis.unlink("cache:a", "cache:b", "cache:c")
```

`Redis#flushall` deletes every key in every database. Its `sync` parameter defaults to `false`, which sends `ASYNC`, and `true` sends `SYNC`:

```scala
import zio._
import zio.redis._

def reset(redis: Redis): IO[RedisError, Unit] =
  redis.flushall(sync = true)
```

## Expiration

Expiry commands set, read and clear the time to live of a key:

| Command            | Result     | Description                                         |
| ------------------ | ---------- | --------------------------------------------------- |
| `Redis#expire`     | `Boolean`  | Expires the key after a `Duration`, in seconds      |
| `Redis#pExpire`    | `Boolean`  | Expires the key after a `Duration`, in milliseconds |
| `Redis#expireAt`   | `Boolean`  | Expires the key at an `Instant`, in seconds         |
| `Redis#pExpireAt`  | `Boolean`  | Expires the key at an `Instant`, in milliseconds    |
| `Redis#persist`    | `Boolean`  | Removes the expiry from the key                     |
| `Redis#ttl`        | `Duration` | Returns the remaining time to live, in seconds      |
| `Redis#pTtl`       | `Duration` | Returns the remaining time to live, in milliseconds |

The setters return `false` when the key does not exist, and `Redis#persist` returns `false` when the key has no expiry. To expire a key in an hour and read what is left:

```scala
import zio._
import zio.redis._

def expireInAnHour(redis: Redis): IO[RedisError, Duration] =
  for {
    _         <- redis.expire("session:1", 1.hour)
    remaining <- redis.ttl("session:1")
  } yield remaining
```

To expire a key at a fixed point in time with millisecond precision:

```scala
import zio._
import zio.redis._
import java.time.Instant

def expireAtMidnight(redis: Redis, midnight: Instant): IO[RedisError, Boolean] =
  redis.pExpireAt("daily:report", midnight)
```

## Renaming and Moving

These commands change the name or location of a key:

| Command          | Result    | Description                                                      |
| ---------------- | --------- | ---------------------------------------------------------------- |
| `Redis#rename`   | `Unit`    | Renames a key and overwrites the target; fails if the key is missing |
| `Redis#renameNx` | `Boolean` | Renames a key only when the new name does not exist              |
| `Redis#copy`     | `Boolean` | Copies a value to another key, optionally into another database  |
| `Redis#move`     | `Boolean` | Moves a key to another database by index                         |

`Redis#copy` takes two optional parameters: `database: Option[Long]` selects the target database, and `replace: Option[Replace]` overwrites an existing target. `Replace` is a single case object. To copy a key and overwrite the target:

```scala
import zio._
import zio.redis._

def duplicate(redis: Redis): IO[RedisError, Boolean] =
  redis.copy("template", "instance", replace = Some(Replace))
```

## Searching and Scanning

`Redis#keys` returns every key that matches a glob-style pattern, while `Redis#scan` walks the key space in small steps:

| Command        | Result                   | Description                                       |
| -------------- | ------------------------ | ------------------------------------------------- |
| `Redis#keys`   | `Chunk[A]`               | Returns all keys matching a pattern               |
| `Redis#scan`   | `(Long, Chunk[A])`       | Returns the next cursor and one batch of keys     |

`Redis#keys` blocks the server while it scans everything, so prefer `Redis#scan` on large databases. `Redis#scan` takes a cursor and these optional parameters:

| Parameter  | Type                | Effect                                         |
| ---------- | ------------------- | ---------------------------------------------- |
| `pattern`  | `Option[String]`    | Keeps only keys that match the pattern         |
| `count`    | `Option[Count]`     | Hints how many keys to return per call         |
| `type`     | `Option[RedisType]` | Keeps only keys that hold the given type       |

Iteration starts with cursor `0` and ends when the server returns cursor `0`. To collect every hash key whose name starts with `user:`:

```scala
import zio._
import zio.redis._

def allUsers(redis: Redis): IO[RedisError, Chunk[String]] = {
  def loop(cursor: Long, acc: Chunk[String]): IO[RedisError, Chunk[String]] =
    redis
      .scan(cursor, Some("user:*"), Some(Count(100L)), Some(RedisType.Hash))
      .returning[String]
      .flatMap { case (next, keys) =>
        val all = acc ++ keys
        if (next == 0L) ZIO.succeed(all) else loop(next, all)
      }

  loop(0L, Chunk.empty)
}
```

## Sorting

`Redis#sort` sorts a list, set or sorted set and returns the values. `Redis#sortStore` runs the same command but stores the result under a key and returns how many values it stored. Both take these parameters:

| Parameter | Type                              | Effect                                                    |
| --------- | --------------------------------- | --------------------------------------------------------- |
| `by`      | `Option[String]`                  | Sorts by the values of external keys that match a pattern |
| `limit`   | `Option[Limit]`                   | Takes `Limit(offset, count)` values from the sorted result |
| `order`   | `Order`                           | `Order.Ascending` (default) or `Order.Descending`         |
| `get`     | `Option[(String, List[String])]`  | Returns the values of external keys instead of the elements |
| `alpha`   | `Option[Alpha]`                   | Sorts alphabetically instead of numerically               |

`Redis#sortStore` takes an extra `storeAt: Store` after the key, where `Store(key)` names the target key. To sort a list of names alphabetically and keep the first ten:

```scala
import zio._
import zio.redis._

def topNames(redis: Redis): IO[RedisError, Chunk[String]] =
  redis
    .sort("names", limit = Some(Limit(0L, 10L)), order = Order.Descending, alpha = Some(Alpha))
    .returning[String]
```

To store the sorted result under another key:

```scala
import zio._
import zio.redis._

def storeSorted(redis: Redis): IO[RedisError, Long] =
  redis.sortStore("scores", Store("scores:sorted"))
```

## Serialization and Migration

`Redis#dump` and `Redis#restore` copy a value as an opaque, Redis-specific byte string, and `Redis#migrate` moves keys to another server:

| Command          | Result        | Description                                                  |
| ---------------- | ------------- | ------------------------------------------------------------ |
| `Redis#dump`     | `Chunk[Byte]` | Returns the serialized value of a key                        |
| `Redis#restore`  | `Unit`        | Creates a key from a serialized value                        |
| `Redis#migrate`  | `String`      | Transfers one or more keys to another instance               |

`Redis#restore` takes the time to live as a `Long` in milliseconds, where `0` means no expiry, and these optional parameters:

| Parameter  | Type                | Effect                                                  |
| ---------- | ------------------- | ------------------------------------------------------- |
| `replace`  | `Option[Replace]`   | Overwrites an existing key                              |
| `absTtl`   | `Option[AbsTtl]`    | Treats the time to live as an absolute Unix time in ms  |
| `idleTime` | `Option[IdleTime]`  | Sets the idle time, with `IdleTime(seconds)`            |
| `freq`     | `Option[Freq]`      | Sets the access frequency, with `Freq(frequency)`       |

To copy a value through a dump and restore it under another key:

```scala
import zio._
import zio.redis._

def cloneValue(redis: Redis): IO[RedisError, Unit] =
  for {
    bytes <- redis.dump("source")
    _     <- redis.restore("target", 0L, bytes, replace = Some(Replace))
  } yield ()
```

`Redis#migrate` takes the remote `host`, `port`, `key`, `destinationDb` and `timeout`. Its optional parameters are `auth: Option[Auth]`, `copy: Option[Copy]` and `replace: Option[Replace]`. `Auth(username, password)` carries an optional username. The last parameter, `keys: Option[(K, List[K])]`, has no default and must always be passed. To send one key to another server and keep the local copy:

```scala
import zio._
import zio.redis._

def migrateKey(redis: Redis): IO[RedisError, String] =
  redis.migrate(
    host = "replica.example.com",
    port = 6379L,
    key = "report",
    destinationDb = 0L,
    timeout = 5.seconds,
    auth = Some(Auth(None, "secret")),
    copy = Some(Copy),
    keys = None
  )
```

## Waiting for Replicas

`Redis#wait_` blocks until previous writes reach a number of replicas, or until the timeout passes. The method name ends in an underscore because `wait` is reserved on `Object`. It returns how many replicas acknowledged the writes. A `Duration.Zero` timeout blocks forever. To wait for two replicas for at most a second:

```scala
import zio._
import zio.redis._

def replicated(redis: Redis): IO[RedisError, Long] =
  for {
    _        <- redis.set("important", "value")
    replicas <- redis.wait_(2L, 1.second)
  } yield replicas
```
