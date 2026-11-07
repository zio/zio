# Cluster

> Redis Cluster spreads keys over several nodes by hashing each key to one of 16384 slots. ZIO Redis supports it through the `Redis.cluster` layer, which gives the same `Redis` service as a single node while routing every command to the node that owns its slot.

## Building the Cluster Layer

`Redis.cluster` requires a `CodecSupplier` and a `RedisClusterConfig`. The config lists one or more startup nodes as `RedisUri` values. The client contacts them in order, asks the first reachable node for the slot map with `CLUSTER SLOTS`, and then opens a connection to every master. To connect to a three-master cluster:

```scala
import zio._
import zio.redis._

val clusterConfig: ULayer[RedisClusterConfig] =
  ZLayer.succeed(
    RedisClusterConfig(
      Chunk(
        RedisUri("localhost", 7000),
        RedisUri("localhost", 7001),
        RedisUri("localhost", 7002)
      )
    )
  )

val redis: ZLayer[Any, RedisError, Redis] =
  ZLayer.make[Redis](
    clusterConfig,
    ZLayer.succeed[CodecSupplier](CodecSupplier.utf8),
    Redis.cluster
  )
```

Listing several startup nodes is only about availability at start-up. If none of them can be reached, the layer fails with a `RedisError.IOError`. Once running, the client learns the other nodes from the cluster itself.

## RedisClusterConfig

`RedisClusterConfig` is a case class with the following fields:

| Field              | Type                       | Default                  | Meaning                                         |
| ------------------ | -------------------------- | ------------------------ | ----------------------------------------------- |
| `addresses`        | `Chunk[RedisUri]`          | none                     | Startup nodes used to discover the cluster      |
| `retry`            | `RetryClusterConfig`       | `RetryClusterConfig.Default` | Backoff used when a command has to be retried |
| `requestQueueSize` | `Int`                      | `16`                     | Request queue size of each node connection      |
| `auth`             | `Option[RedisConfig.Auth]` | `None`                   | Credentials used for every node                 |

`RetryClusterConfig` has three fields: `base: Duration`, `factor: Double` and `maxRecurs: Int`. The default is `RetryClusterConfig(100.millis, 1.5, 5)`, which means an exponential backoff that starts at 100 milliseconds, grows by a factor of 1.5 and gives up after five retries. To use a faster and shorter policy together with authentication:

```scala
import zio._
import zio.redis._

val config: RedisClusterConfig =
  RedisClusterConfig(
    addresses = Chunk(RedisUri("redis-1.example.com:6379")),
    retry = RetryClusterConfig(base = 50.millis, factor = 2.0, maxRecurs = 3),
    auth = Some(RedisConfig.Auth("secret"))
  )
```

## RedisUri

`RedisUri` names a node by host and port, with optional `ssl`, `sni` and `verifyCertificate` settings. It is described in [Configuration](./configuration.md). A TLS node is written as follows:

```scala
import zio.redis._

val secureNode: RedisUri = RedisUri("redis.example.com", 6380, ssl = true)
```

## Redirections

A cluster may answer a command with a redirection. `RedisError.Moved(slot, address)` means the slot now lives on another node, and `RedisError.Ask(slot, address)` means the slot is being migrated and this one command should be sent to the target node. Application code does not need to handle either error, because the cluster executor behind `Redis.cluster` does it:

| Situation                        | What the executor does                                                              |
| -------------------------------- | ----------------------------------------------------------------------------------- |
| Normal command                   | Hashes the first key with CRC16, picks the owning node and sends the command there  |
| `Ask`                            | Sends `ASKING` followed by the command to the node named in the error               |
| `Moved`                          | Reloads the slot map from the cluster, closes the old connections and resends       |
| `IOError` or any cluster error   | Retries the whole attempt with the exponential backoff from `RetryClusterConfig`    |

A command is retried at most `maxRecurs` times, and if it still fails the last error is returned to the caller. Commands without a key, such as `PING`, are sent to the node that owns slot 1.

## CrossSlot Errors

A command that touches several keys works only if all the keys hash to the same slot. Otherwise the cluster replies with `RedisError.CrossSlot`, which `Redis` surfaces as a failure. The executor routes by the first key only, so it cannot fix this for you. To force related keys into one slot, wrap a common part of each name in braces, which is called a hash tag. To write two keys that always share a slot:

```scala
import zio._
import zio.redis._

def copyProfile(redis: Redis): IO[RedisError, Long] =
  redis.sAdd("{user:1}:friends", "ada") *> redis.sAdd("{user:1}:groups", "admins")
```

Only the text between the braces is hashed, so both keys land in the same slot.

## Cluster Commands

The commands below are methods on `Redis`, from the `Cluster` API trait. They are mostly useful for tooling and tests, since the executor already issues `ASKING` and `CLUSTER SLOTS` itself:

| Method                    | Redis command           | Returns            |
| ------------------------- | ----------------------- | ------------------ |
| `Redis#slots`             | `CLUSTER SLOTS`         | `Chunk[Partition]` |
| `Redis#asking`            | `ASKING`                | `Unit`             |
| `Redis#setSlotImporting`  | `CLUSTER SETSLOT ... IMPORTING` | `Unit`     |
| `Redis#setSlotMigrating`  | `CLUSTER SETSLOT ... MIGRATING` | `Unit`     |
| `Redis#setSlotNode`       | `CLUSTER SETSLOT ... NODE`      | `Unit`     |
| `Redis#setSlotStable`     | `CLUSTER SETSLOT ... STABLE`    | `Unit`     |

`Slot`, `Partition`, `Node` and `SlotRange` live in `zio.redis.options.Cluster`. A `Partition` has a `slotRange`, a `master` node and its `slaves`, and each `Node` has an `id` and an `address`. To list the master of every slot range:

```scala
import zio._
import zio.redis._

def masters(redis: Redis): IO[RedisError, Chunk[String]] =
  redis.slots.map(_.map(p => s"${p.slotRange.start}-${p.slotRange.end} -> ${p.master.address}"))
```

The integration test `ClusterSpec` calls `Redis#slots` against a six-node Docker cluster in the same way.

## Limitations

Pub/Sub is not available through `Redis.cluster`; `RedisSubscription` connects to a single node, as described in [Pub/Sub](./pub-sub.md). Every command is routed by its first key, so transactions and scripts that span slots must use hash tags.
