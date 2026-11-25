# Error Handling

> Every command fails with a `RedisError`. This page lists its cases and shows how to recover from them.

## The RedisError Hierarchy

`RedisError` is a sealed trait, so pattern matches over it are checked for exhaustiveness. Its cases are:

| Case                                  | Meaning                                                               |
| ------------------------------------- | --------------------------------------------------------------------- |
| `ProtocolError(message)`              | The server sent a reply the client cannot interpret                   |
| `CodecError(message)`                 | A value could not be encoded or decoded                               |
| `WrongType(message)`                  | A command ran against a key holding another type                      |
| `BusyGroup(message)`                  | A stream consumer group already exists                                |
| `NoGroup(message)`                    | A stream consumer group does not exist                                |
| `NoScript(message)`                   | A script SHA is not in the script cache                               |
| `NotBusy(message)`                    | `scriptKill` ran while no script was executing                        |
| `IOError(exception)`                  | The connection failed                                                 |
| `CommandNameNotFound(message)`        | The command is unknown to the client                                  |
| `CrossSlot(message)`                  | A cluster command touched keys in different slots                     |
| `Moved(slot, address)`                | A cluster slot lives on another node                                  |
| `Ask(slot, address)`                  | A cluster slot is being migrated to another node                      |
| `InvalidPubSubCommand(command)`       | A command is not valid on a subscription connection                   |
| `SubscriptionStreamAlreadyClosed(key)`| A subscription stream was used after it closed                        |

`Moved` and `Ask` extend `ClusterRedisError`. The cluster executor handles them itself; see [Cluster](./cluster.md). The last two cases extend `PubSubError`; see [Pub/Sub](./pub-sub.md).

## Recovering From Errors

Because the error channel is a plain ZIO error, the usual combinators apply. To treat a type clash as a missing value:

```scala
import zio._
import zio.redis._

def safeGet(redis: Redis, key: String): IO[RedisError, Option[String]] =
  redis.get(key).returning[String].catchSome {
    case RedisError.WrongType(_) => ZIO.none
  }
```

To retry connection failures with a backoff:

```scala
import zio._
import zio.redis._

def resilientGet(redis: Redis, key: String): IO[RedisError, Option[String]] =
  redis.get(key).returning[String].retry(
    Schedule.exponential(100.millis) && Schedule.recurs(3) && Schedule.recurWhile[RedisError] {
      case _: RedisError.IOError => true
      case _                     => false
    }
  )
```
