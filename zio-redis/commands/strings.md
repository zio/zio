# Strings

> Strings are the simplest Redis value: a byte sequence that can hold text, a number or an encoded case class. This page lists every string command on the `Redis` service, grouped by purpose, with the option types they take.

## Reading and Writing Values

The basic commands read and write one key at a time. Methods that return user data, such as `Redis#get`, finish with `returning[A]`, as explained in [Getting Started](../getting-started.md):

| Command           | Result                | Description                                                  |
| ----------------- | --------------------- | ------------------------------------------------------------ |
| `Redis#get`       | `Option[A]`           | Returns the value of a key, or `None` when it does not exist |
| `Redis#getDel`    | `Option[A]`           | Returns the value of a key and deletes the key               |
| `Redis#getSet`    | `Option[A]`           | Sets a new value and returns the old one                     |
| `Redis#set`       | `Boolean`             | Sets a value, with optional expiry and condition             |
| `Redis#setGet`    | `Option[V]`           | Like `set`, and returns the old value in the same call       |
| `Redis#setNx`     | `Boolean`             | Sets a value only when the key does not exist                |
| `Redis#append`    | `Long`                | Appends to a value and returns the new length                |
| `Redis#strLen`    | `Long`                | Returns the length of a value                                |
| `Redis#getRange`  | `Option[A]`           | Returns the substring selected by a Scala `Range`            |
| `Redis#setRange`  | `Long`                | Overwrites part of a value from an offset                    |

To write a value and read it back:

```scala
import zio._
import zio.redis._

def readWrite(redis: Redis): IO[RedisError, Option[String]] =
  for {
    _     <- redis.set("greeting", "hello")
    _     <- redis.append("greeting", ", world")
    value <- redis.get("greeting").returning[String]
  } yield value
```

`Redis#getSet` and `Redis#getDel` return the previous value through `returning`, while `Redis#setGet` fixes the result type to the value type `V`. To replace a value and keep the old one:

```scala
import zio._
import zio.redis._

def swap(redis: Redis): IO[RedisError, Option[String]] =
  redis.setGet("token", "new-token")
```

## Conditional Writes and Expiry

`Redis#set` and `Redis#setGet` share the same optional parameters:

| Parameter    | Type               | Effect                                                  |
| ------------ | ------------------ | ------------------------------------------------------- |
| `expireTime` | `Option[Duration]` | Sets a time to live, sent to Redis in milliseconds      |
| `update`     | `Option[Update]`   | Sets the key only if it exists or only if it is missing |
| `keepTtl`    | `Option[KeepTtl]`  | Keeps the time to live the key already has              |

`Update` has four cases. `Update.SetExisting` sends `XX` and `Update.SetNew` sends `NX`. The remaining cases, `Update.SetLessThan` and `Update.SetGreaterThan`, are shared with other commands and are not valid for `SET`. `KeepTtl` is a single case object.

`Redis#set` returns `false` when the condition prevents the write. To store a value only if the key is missing, and keep it for ten minutes:

```scala
import zio._
import zio.redis._

def acquire(redis: Redis): IO[RedisError, Boolean] =
  redis.set("lock:job", "owner-1", Some(10.minutes), Some(Update.SetNew))
```

To overwrite a value without touching its expiry:

```scala
import zio._
import zio.redis._

def refresh(redis: Redis): IO[RedisError, Boolean] =
  redis.set("session", "v2", keepTtl = Some(KeepTtl))
```

Dedicated commands combine a write with an expiry in one call:

| Command         | Result    | Description                                           |
| --------------- | --------- | ----------------------------------------------------- |
| `Redis#setEx`   | `Unit`    | Sets a value that expires after a `Duration`          |
| `Redis#pSetEx`  | `Unit`    | Sets a value that expires after a `Duration`, in ms   |
| `Redis#setNx`   | `Boolean` | Sets a value only when the key does not exist         |

## Reading with an Expiry

`Redis#getEx` reads a value and changes its time to live in one step. It has three overloads:

| Overload                                  | Effect                                               |
| ----------------------------------------- | ---------------------------------------------------- |
| `getEx(key, expire, expireTime)`          | Sets a relative expiry with an `Expire` and `Duration` |
| `getEx(key, expiredAt, timestamp)`        | Sets an absolute expiry with an `ExpiredAt` and `Instant` |
| `getEx(key, persist)`                     | Removes the expiry when `persist` is `true`          |

`Expire` selects the unit of the relative form: `Expire.SetExpireSeconds` sends `EX` and `Expire.SetExpireMilliseconds` sends `PX`. `ExpiredAt` does the same for the absolute form: `ExpiredAt.SetExpireAtSeconds` sends `EXAT` and `ExpiredAt.SetExpireAtMilliseconds` sends `PXAT`.

To read a value and extend its lifetime, then read it and make it permanent:

```scala
import zio._
import zio.redis._

def touchAndPersist(redis: Redis): IO[RedisError, Option[String]] =
  for {
    _     <- redis.getEx("session", Expire.SetExpireSeconds, 30.minutes).returning[String]
    value <- redis.getEx("session", persist = true).returning[String]
  } yield value
```

## Multiple Keys

The multi-key commands take at least one key or pair, followed by any number of extra ones:

| Command         | Result                 | Description                                         |
| --------------- | ---------------------- | --------------------------------------------------- |
| `Redis#mGet`    | `Chunk[Option[A]]`     | Returns the values of several keys                  |
| `Redis#mSet`    | `Unit`                 | Sets several key-value pairs at once                |
| `Redis#mSetNx`  | `Boolean`              | Sets all pairs only if none of the keys exist       |

`Redis#mGet` returns one entry per key, in order, with `None` for missing keys. To set two keys and read them back:

```scala
import zio._
import zio.redis._

def multi(redis: Redis): IO[RedisError, Chunk[Option[String]]] =
  for {
    _      <- redis.mSet("a" -> "1", "b" -> "2")
    values <- redis.mGet("a", "b", "missing").returning[String]
  } yield values
```

## Counters

Counters treat a value as a 64-bit integer, or as a float for `Redis#incrByFloat`. Redis creates a missing key with the value `0` before applying the change:

| Command              | Result   | Description                              |
| -------------------- | -------- | ---------------------------------------- |
| `Redis#incr`         | `Long`   | Adds one and returns the new value       |
| `Redis#incrBy`       | `Long`   | Adds a `Long` and returns the new value  |
| `Redis#incrByFloat`  | `Double` | Adds a `Double` and returns the new value|
| `Redis#decr`         | `Long`   | Subtracts one and returns the new value  |
| `Redis#decrBy`       | `Long`   | Subtracts a `Long` and returns the new value |

To count page views and add a fractional score:

```scala
import zio._
import zio.redis._

def counters(redis: Redis): IO[RedisError, (Long, Double)] =
  for {
    views <- redis.incr("page:views")
    score <- redis.incrByFloat("page:score", 1.5)
  } yield (views, score)
```

## Bit Operations

Bit commands treat a string as an array of bits. `getBit` and `setBit` address one bit by its offset:

| Command           | Result                  | Description                                            |
| ----------------- | ----------------------- | ------------------------------------------------------ |
| `Redis#getBit`    | `Long`                  | Returns the bit at an offset                           |
| `Redis#setBit`    | `Boolean`               | Sets or clears the bit at an offset, returns the old bit |
| `Redis#bitCount`  | `Long`                  | Counts bits set to 1, optionally within a `Range`      |
| `Redis#bitPos`    | `Long`                  | Finds the first set or cleared bit, optionally in a `BitPosRange` |
| `Redis#bitOp`     | `Long`                  | Applies a `BitOperation` to source keys and stores the result |
| `Redis#bitField`  | `Chunk[Option[Long]]`   | Runs one or more `BitFieldCommand` values              |

`BitOperation` has the cases `AND`, `OR`, `XOR` and `NOT`. `BitPosRange(start, end)` takes a start offset and an optional end offset.

To track daily visits per user and combine two days:

```scala
import zio._
import zio.redis._

def visits(redis: Redis): IO[RedisError, Long] =
  for {
    _     <- redis.setBit("visits:mon", 7L, value = true)
    _     <- redis.setBit("visits:tue", 7L, value = true)
    _     <- redis.bitOp(BitOperation.AND, "visits:both", "visits:mon", "visits:tue")
    count <- redis.bitCount("visits:both")
  } yield count
```

`Redis#bitCount` accepts a Scala `Range`, and `Redis#bitPos` accepts a `BitPosRange`. To restrict both to the first bytes of a key:

```scala
import zio._
import zio.redis._

def inFirstBytes(redis: Redis): IO[RedisError, (Long, Long)] =
  for {
    ones  <- redis.bitCount("flags", Some(0 to 3))
    first <- redis.bitPos("flags", bit = true, Some(BitPosRange(0L, Some(3L))))
  } yield (ones, first)
```

### Bit Fields

`Redis#bitField` treats a string as a sequence of integers of any width. It takes one `BitFieldCommand` and any number of further ones, and returns one `Option[Long]` per command:

| Type                           | Fields                                  | Description                                  |
| ------------------------------ | --------------------------------------- | -------------------------------------------- |
| `BitFieldCommand.BitFieldGet`  | `type`, `offset`                        | Reads an integer                             |
| `BitFieldCommand.BitFieldSet`  | `type`, `offset`, `value`               | Writes an integer                            |
| `BitFieldCommand.BitFieldIncr` | `type`, `offset`, `increment`           | Adds to an integer                           |
| `BitFieldCommand.BitFieldOverflow` | `Fail`, `Sat`, `Wrap`               | Selects overflow behavior for later commands |

The integer type is a `BitFieldType`, either `BitFieldType.UnsignedInt(size)` or `BitFieldType.SignedInt(size)`. To write and increment an unsigned 8-bit counter with saturating overflow:

```scala
import zio._
import zio.redis._
import zio.redis.BitFieldCommand._

def bitFields(redis: Redis): IO[RedisError, Chunk[Option[Long]]] =
  redis.bitField(
    "counters",
    BitFieldSet(BitFieldType.UnsignedInt(8), 0, 250L),
    BitFieldOverflow.Sat,
    BitFieldIncr(BitFieldType.UnsignedInt(8), 0, 10L)
  )
```

## Longest Common Subsequence

`Redis#lcs` compares the values of two keys. It takes an optional `LcsQueryType` and returns an `Lcs`:

| `LcsQueryType`                                | Result                                              |
| --------------------------------------------- | --------------------------------------------------- |
| none                                          | `Lcs.PlainLcs(lcs)` with the common subsequence     |
| `LcsQueryType.Len`                            | `Lcs.Length(length)` with its length                |
| `LcsQueryType.Idx(minMatchLength, withMatchLength)` | `Lcs.Matches(matches, length)` with match ranges |

Each `Match` holds two `MatchIdx(start, end)` ranges and, when `withMatchLength` is set, a `matchLength`. `minMatchLength` defaults to `1` and `withMatchLength` to `false`. To print the length of the shared text:

```scala
import zio._
import zio.redis._

def commonLength(redis: Redis): IO[RedisError, Long] =
  redis.lcs("key1", "key2", Some(LcsQueryType.Len)).map {
    case Lcs.Length(length)   => length
    case Lcs.PlainLcs(lcs)    => lcs.length.toLong
    case Lcs.Matches(_, size) => size
  }
```
