# Streams

> A Redis stream is an append-only log of entries, each made of an ID and a set of field-value pairs. This page covers the 32 stream commands on `Redis`, from appending and reading entries to consumer groups, claiming and trimming.

Stream IDs have the form `<milliseconds>-<sequence>`. Pass `"*"` to `Redis#xAdd` to let the server generate the ID, and use `"-"` and `"+"` as the smallest and largest possible IDs in range queries.

Every command here takes the key, group, consumer and ID as values with a ZIO Schema `Schema`, which are encoded by the [`CodecSupplier`](../codecs.md). In the examples they are plain `String`s.

## Appending Entries

`Redis#xAdd` appends an entry made of one or more field-value pairs. The result type is the type of the generated ID, so we finish with `returning[A]`, and the result is `None` when `noMakeStream` is set and the stream does not exist:

```scala
import zio._
import zio.redis._

def addClick(redis: Redis): IO[RedisError, Option[String]] =
  redis.xAdd("clicks", "*")("page" -> "/home", "user" -> "42").returning[String]
```

The following table lists the commands that append entries:

| Command                  | Purpose                                                           |
| ------------------------ | ----------------------------------------------------------------- |
| `Redis#xAdd`             | Appends an entry                                                  |
| `Redis#xAddWithMaxLen`   | Appends an entry and trims the stream to at most `count` entries  |
| `Redis#xAddWithMinId`    | Appends an entry and evicts entries with an ID lower than `minId` |

`Redis#xAddWithMaxLen` and `Redis#xAddWithMinId` take the same `approximate` and `limit` parameters, described under [Trimming Streams](#capped-streams). The following example keeps roughly the latest 1000 entries:

```scala
import zio._
import zio.redis._

def addCapped(redis: Redis): IO[RedisError, Option[String]] =
  redis
    .xAddWithMaxLen("clicks", "*", count = 1000L, approximate = true)("page" -> "/home")
    .returning[String]
```

## Reading Entries

Range queries return the entries between two IDs, inclusive. They return `StreamEntries[I, K, V]`, which is a `Chunk[StreamEntry[I, K, V]]`, where `StreamEntry` holds an `id` and a `fields` map:

| Command             | Purpose                                                      |
| ------------------- | ------------------------------------------------------------ |
| `Redis#xRange`      | Returns entries from `start` to `end`, optionally `count`    |
| `Redis#xRevRange`   | Returns entries from `end` down to `start`, optionally `count` |
| `Redis#xLen`        | Returns the number of entries                                |
| `Redis#xDel`        | Deletes entries by ID and returns how many were deleted      |

Note that `Redis#xRevRange` takes `end` before `start`. The following example reads the ten oldest entries:

```scala
import zio._
import zio.redis._

def firstTen(redis: Redis): IO[RedisError, StreamEntries[String, String, String]] =
  redis.xRange("clicks", "-", "+", 10L).returning[String, String]
```

`Redis#xRead` reads from one or more streams, starting after the given ID in each. Pass `"0-0"` to read from the beginning, or `"$"` together with `block` to wait for entries that arrive later. It returns `StreamChunks[SK, I, K, V]`, a `Chunk[StreamChunk]` with the stream `name` and its `entries`:

```scala
import zio._
import zio.redis._

def waitForNew(redis: Redis): IO[RedisError, StreamChunks[String, String, String, String]] =
  redis.xRead(count = Some(10L), block = Some(5.seconds))("clicks" -> "$").returning[String, String]
```

## Capped Streams

A stream grows without bound unless it is capped. The trimming commands and options use `CappedStreamType` internally, with the cases `MaxLenApprox`, `MaxLenExact`, `MinIdApprox` and `MinIdExact`. Public methods expose the same choice through plain parameters.

| Command                  | Trims by                          |
| ------------------------ | --------------------------------- |
| `Redis#xTrimWithMaxLen`  | Number of entries (`threshold`)   |
| `Redis#xTrimWithMinId`   | Smallest ID to keep (`threshold`) |

Both commands take `approximate: Boolean = false` and `limit: Option[Long] = None`. With `approximate = true`, Redis may keep slightly more entries than requested, which is cheaper, and `limit` bounds the work done per call. Both return the number of entries removed:

```scala
import zio._
import zio.redis._

def trim(redis: Redis): IO[RedisError, Long] =
  redis.xTrimWithMaxLen("clicks", threshold = 10000L, approximate = true, limit = Some(100L))
```

## Consumer Groups

A consumer group lets several consumers share the work of reading one stream. Redis delivers each entry to one consumer of the group and remembers it in the group's pending entries list until the consumer acknowledges it.

The following table lists the commands that manage groups and consumers:

| Command                          | Purpose                                                       |
| -------------------------------- | ------------------------------------------------------------- |
| `Redis#xGroupCreate`             | Creates a group that starts after the given ID                |
| `Redis#xGroupCreateLastEntry`    | Creates a group that starts after the current last entry      |
| `Redis#xGroupSetId`              | Moves the group's last delivered ID                           |
| `Redis#xGroupSetIdLastEntry`     | Moves the group's last delivered ID to the last entry         |
| `Redis#xGroupDestroy`            | Deletes a group and returns whether it existed                |
| `Redis#xGroupCreateConsumer`     | Creates a consumer and returns whether it was created         |
| `Redis#xGroupDelConsumer`        | Deletes a consumer and returns how many pending entries it had |

`Redis#xGroupCreate` and `Redis#xGroupCreateLastEntry` accept `mkStream: Boolean = false`, which creates the stream when it does not exist, and `entriesRead: Option[Long] = None`. The `*LastEntry` variants pass `"$"` for you. The following example creates a group on a new stream and registers a consumer:

```scala
import zio._
import zio.redis._

def setUp(redis: Redis): IO[RedisError, Boolean] =
  redis.xGroupCreate("orders", "billing", "0-0", mkStream = true) *>
    redis.xGroupCreateConsumer("orders", "billing", "worker-1")
```

## The Stream Reading Flow

The usual flow with a consumer group has four steps. A producer appends entries with `Redis#xAdd`. A consumer calls `Redis#xReadGroup` with the ID `">"`, which asks for entries never delivered to any consumer of the group. After handling an entry, the consumer calls `Redis#xAck`. Entries that were never acknowledged are recovered with `Redis#xPending`, `Redis#xClaim` or `Redis#xAutoClaim`.

`Redis#xReadGroup` takes `count`, `block` and `noAck`. With `noAck = true` entries are not added to the pending list, so no acknowledgement is needed. If you pass an ID such as `"0"` instead of `">"`, Redis returns the consumer's own pending entries.

`Redis#xAck` takes a group and one or more IDs and returns how many entries were acknowledged. The following example reads a batch, handles each entry and acknowledges it:

```scala
import zio._
import zio.redis._

def consume(redis: Redis): IO[RedisError, Unit] =
  for {
    chunks <- redis
                .xReadGroup("billing", "worker-1", count = Some(10L), block = Some(2.seconds))("orders" -> ">")
                .returning[String, String]
    _      <- ZIO.foreachDiscard(chunks.flatMap(_.entries)) { entry =>
                ZIO.debug(s"handling ${entry.id}: ${entry.fields}") *>
                  redis.xAck("orders", "billing", entry.id)
              }
  } yield ()
```

## Recovering Pending Entries

When a consumer crashes, its entries stay in the pending list. The following table lists the commands that inspect and take over pending entries:

| Command                         | Purpose                                                          |
| ------------------------------- | ---------------------------------------------------------------- |
| `Redis#xPending` (group only)   | Returns a `PendingInfo` summary                                  |
| `Redis#xPending` (with range)   | Returns a `Chunk[PendingMessage]` for a range of IDs             |
| `Redis#xClaim`                  | Transfers named entries to a consumer and returns them           |
| `Redis#xClaimWithJustId`        | Same, but returns only the IDs                                   |
| `Redis#xAutoClaim`              | Scans the pending list and claims idle entries, returning them   |
| `Redis#xAutoClaimWithJustId`    | Same, but returns only the IDs                                   |

`PendingInfo` holds `total`, the `first` and `last` pending IDs and a `consumers` map of pending counts. Each `PendingMessage` holds `id`, `owner`, `lastDelivered` and `counter`. The ranged `Redis#xPending` also accepts `consumer: Option[SC]` and `idle: Option[Duration]` to filter the result:

```scala
import zio._
import zio.redis._

def inspect(redis: Redis): IO[RedisError, (PendingInfo, Chunk[PendingMessage])] =
  for {
    summary <- redis.xPending("orders", "billing")
    details <- redis.xPending("orders", "billing", "-", "+", 10L, Some("worker-1"), Some(1.minute))
  } yield (summary, details)
```

`Redis#xClaim` takes the `minIdleTime` an entry must have exceeded, plus the optional `idle`, `time`, `retryCount`, `force` and `lastId` parameters that set the new delivery state. `Redis#xAutoClaim` takes `minIdleTime` and an optional `count`, and the `start` ID in the second parameter list. It returns a `StreamClaimedEntries` with `streamId`, the `entries` and the `deletedIds`. The `WithJustId` variant returns a `StreamClaimedIdChunk` with `streamId`, `claimedIds` and `deletedIds`. Use the `streamId` as the next `start`:

```scala
import zio._
import zio.redis._

def takeOver(redis: Redis): IO[RedisError, StreamClaimedEntries[String, String, String, String]] =
  redis
    .xAutoClaim("orders", "billing", "worker-2", 5.minutes, count = Some(Count(10L)))("0-0")
    .returning[String, String, String]
```

## Stream Information

The info commands return typed summaries instead of raw replies:

| Command                      | Returns                                                           |
| ---------------------------- | ----------------------------------------------------------------- |
| `Redis#xInfoStream`          | `StreamInfo[I, K, V]` with length, last ID, first and last entry  |
| `Redis#xInfoStreamFull`      | `FullStreamInfo[I, K, V]` with entries, groups and consumers      |
| `Redis#xInfoGroups`          | `Chunk[StreamGroupsInfo]`                                         |
| `Redis#xInfoConsumers`       | `Chunk[StreamConsumersInfo]`                                      |

`Redis#xInfoStreamFull` has an overload that takes `count: Long` to limit the number of entries and pending records reported. The `FullStreamInfo` and its `ConsumerGroups`, `Consumers`, `GroupPel` and `ConsumerPel` types live in `StreamInfoWithFull`. The following example prints the lag of each group:

```scala
import zio._
import zio.redis._

def lags(redis: Redis): IO[RedisError, Chunk[(String, Long)]] =
  redis.xInfoGroups("orders").map(_.map(g => g.name -> g.lag))
```

## Option Types

The following table summarizes the types from `options/Streams.scala` that appear in command signatures and results:

| Type                                   | Used by                                         |
| -------------------------------------- | ----------------------------------------------- |
| `StreamEntry[I, K, V]`                 | `id` and `fields: Map[K, V]` of one entry       |
| `StreamChunk[N, I, K, V]`              | Entries of one stream read by `xRead`           |
| `StreamClaimedEntries[SI, I, K, V]`    | Result of `xAutoClaim`                          |
| `StreamClaimedIdChunk[SI, I]`          | Result of `xAutoClaimWithJustId`                |
| `PendingInfo`, `PendingMessage`        | Results of `xPending`                           |
| `StreamInfo`, `StreamGroupsInfo`, `StreamConsumersInfo` | Results of the `xInfo*` commands |

`XGroupCommand`, `WithForce`, `WithJustId`, `MkStream`, `NoMkStream`, `NoAck`, `LastId` and `WithEntriesRead` encode the command arguments for you and are not needed in application code.

## Related Pages

- [Lists](./lists.md) offer a simpler queue when consumer groups are not needed.
- [Codecs](../codecs.md) explains how keys, fields and values are encoded.
- [Error Handling](../error-handling.md) lists the errors raised, such as `NOGROUP` when a group does not exist.
