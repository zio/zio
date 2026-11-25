# AsyncRedis

> `AsyncRedis` exposes the same commands as `Redis`, but each command returns as soon as it has been queued. This lets a program send many commands before it waits for any reply.

## Return Types

A command on `Redis` has type `IO[RedisError, A]`. The same command on `AsyncRedis` has type `UIO[IO[RedisError, A]]`. The outer effect queues the command and never fails. The inner effect waits for the reply:

```scala
type Redis      = GenRedis[GenRedis.Sync]
type AsyncRedis = GenRedis[GenRedis.Async]
```

`Redis.local`, `Redis.singleNode` produce both services in one layer. `Redis.cluster` produces only `Redis`.

## Pipelining Commands

To send several commands and then wait for all the replies, see [How to Pipeline Commands](../how-to/pipeline-commands.md).
