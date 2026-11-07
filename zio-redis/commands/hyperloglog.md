# HyperLogLog

> A HyperLogLog is a probabilistic structure that estimates how many distinct elements it has seen, using a small and fixed amount of memory. This page documents the three commands that ZIO Redis exposes for it.

All commands are methods on the `Redis` service. Keys and elements are encoded with the configured [`CodecSupplier`](../codecs.md), so any type with a ZIO Schema `Schema` can be used for both. See [Getting Started](../getting-started.md) for how to build the layer.

## Commands

The commands are grouped below by purpose:

| Method           | Redis Command | Result            | Purpose                                              |
| ---------------- | ------------- | ----------------- | ---------------------------------------------------- |
| `Redis#pfAdd`    | `PFADD`       | `Boolean`         | Adds elements to a HyperLogLog                       |
| `Redis#pfCount`  | `PFCOUNT`     | `Long`            | Estimates the number of distinct elements            |
| `Redis#pfMerge`  | `PFMERGE`     | `Unit`            | Merges several HyperLogLogs into a destination key   |

None of these commands needs `returning`, because their results do not depend on a user type.

## Adding Elements

`Redis#pfAdd` takes a key and at least one element. It returns `true` if the internal registers of the HyperLogLog changed, and `false` otherwise:

```scala
import zio._
import zio.redis._

def trackVisitors(redis: Redis): IO[RedisError, Boolean] =
  redis.pfAdd("visitors:today", "alice", "bob", "carol")
```

Adding an element that was already counted usually leaves the registers unchanged, so the result is `false` in that case.

## Counting Elements

`Redis#pfCount` returns the approximate number of distinct elements added to the key. When more than one key is given, it returns the approximate size of the union of all of them:

```scala
import zio._
import zio.redis._

def countVisitors(redis: Redis): IO[RedisError, (Long, Long)] =
  for {
    today <- redis.pfCount("visitors:today")
    both  <- redis.pfCount("visitors:today", "visitors:yesterday")
  } yield (today, both)
```

A key that does not exist counts as an empty HyperLogLog.

## Merging HyperLogLogs

`Redis#pfMerge` takes a destination key followed by at least one source key. It stores the union of the sources in the destination, and the sources are left untouched:

```scala
import zio._
import zio.redis._

def mergeVisitors(redis: Redis): IO[RedisError, Long] =
  for {
    _     <- redis.pfMerge("visitors:week", "visitors:today", "visitors:yesterday")
    count <- redis.pfCount("visitors:week")
  } yield count
```

## Next Steps

- [Sets](./sets.md) store exact members when an approximation is not acceptable.
- [Keys](./keys.md) covers expiry and deletion, which also apply to HyperLogLog keys.
