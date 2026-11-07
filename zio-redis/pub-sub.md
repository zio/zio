# Pub/Sub

> Redis Pub/Sub lets clients publish messages to named channels and lets other clients receive them as they arrive. ZIO Redis splits the feature in two: publishing is a command on `Redis`, and subscribing lives on a separate service, `RedisSubscription`.

## Why Two Services

A Redis connection that has issued `SUBSCRIBE` or `PSUBSCRIBE` is in subscription mode and may no longer be used for ordinary commands. For that reason `RedisSubscription` opens its own connection, built by its own layer, and `Redis` stays free for everything else. A program that both publishes and subscribes needs both layers:

| Service             | Layer                           | Requires                          | Provides                                  |
| ------------------- | ------------------------------- | --------------------------------- | ----------------------------------------- |
| `Redis`             | `Redis.singleNode`              | `CodecSupplier` and `RedisConfig` | `publish` and the `pubSub*` introspection |
| `RedisSubscription` | `RedisSubscription.singleNode`  | `CodecSupplier` and `RedisConfig` | `subscribe`, `pSubscribe` and friends     |

`RedisSubscription.local` is the counterpart of `Redis.local` and connects to `localhost:6379`. There is no cluster variant of `RedisSubscription`. To build both services against one server:

```scala
import zio._
import zio.redis._

val layers: ZLayer[Any, RedisError.IOError, Redis with RedisSubscription] =
  ZLayer.make[Redis with RedisSubscription](
    ZLayer.succeed(RedisConfig.Local),
    ZLayer.succeed[CodecSupplier](CodecSupplier.utf8),
    Redis.singleNode,
    RedisSubscription.singleNode
  )
```

See [Configuration](./configuration.md) for `RedisConfig` and [Codecs](./codecs.md) for `CodecSupplier`.

## Publishing

`Redis#publish` posts a message to a channel and returns the number of clients that received it. The message can be any type with a `Schema`, and it is encoded with the `CodecSupplier`. To publish a string:

```scala
import zio._
import zio.redis._

def announce(redis: Redis): IO[RedisError, Long] =
  redis.publish("news", "hello")
```

## Subscribing

Every subscribe method returns a result builder. Calling `returning[R]` on it produces a `Stream[RedisError, ...]` of decoded messages, and the subscription starts when the stream is run. The following methods are available on `RedisSubscription`:

| Method                             | Emits                               | Subscribes to           |
| ---------------------------------- | ----------------------------------- | ----------------------- |
| `RedisSubscription#subscribeSingle` | `R`                                 | one channel             |
| `RedisSubscription#subscribe`       | `(String, R)`, keyed by channel     | one or more channels    |
| `RedisSubscription#pSubscribe`      | `(String, R)`, keyed by channel     | one or more patterns    |

The `*With` variants (`subscribeSingleWith`, `subscribeWith`, `pSubscribeWith`) additionally take `onSubscribe` and `onUnsubscribe` callbacks, described below. To receive the first three messages from a single channel:

```scala
import zio._
import zio.redis._

def firstThree(subscription: RedisSubscription): IO[RedisError, Chunk[String]] =
  subscription.subscribeSingle("news").returning[String].take(3).runCollect
```

To listen to several channels at once and learn which channel each message came from:

```scala
import zio._
import zio.redis._

val printAll: ZIO[RedisSubscription, RedisError, Unit] =
  ZIO.serviceWithZIO[RedisSubscription] { subscription =>
    subscription
      .subscribe("news", "weather")
      .returning[String]
      .foreach { case (channel, message) => ZIO.debug(s"$channel: $message") }
  }
```

## Pattern Subscriptions

`RedisSubscription#pSubscribe` takes glob-style patterns, such as `news.*`, and emits every message published to a matching channel. The first element of each pair is the name of the channel the message was published to, not the pattern. To log every message under the `news.` prefix:

```scala
import zio._
import zio.redis._

def watchNews(subscription: RedisSubscription): IO[RedisError, Unit] =
  subscription
    .pSubscribe("news.*")
    .returning[String]
    .foreach { case (channel, message) => ZIO.debug(s"$channel: $message") }
```

## Callbacks and Synchronization

Publishing right after forking a subscriber is racy, because the subscription may not be active yet. The `*With` methods accept callbacks of type `PubSubCallback`, an alias for `(String, Long) => UIO[Unit]`. The callback receives the channel (or pattern) name and the number of subscriptions the connection now holds. Completing a `Promise` in `onSubscribe` is the way to wait for the subscription before publishing:

```scala
import zio._
import zio.redis._

def publishAfterSubscribed(redis: Redis, subscription: RedisSubscription): IO[RedisError, Option[String]] =
  for {
    ready <- Promise.make[Nothing, Unit]
    fiber <- subscription
               .subscribeSingleWith("news")(onSubscribe = (_, _) => ready.succeed(()).unit)
               .returning[String]
               .runHead
               .fork
    _     <- ready.await
    _     <- redis.publish("news", "hello")
    msg   <- fiber.join
  } yield msg
```

The same pattern is used by the integration tests in `PubSubSpec`. Both callbacks default to a no-op.

## Unsubscribing

`RedisSubscription#unsubscribe` and `RedisSubscription#pUnsubscribe` take channel names or patterns, and with no arguments they remove every subscription of that kind. The streams of the removed subscriptions then end. To stop a listener from outside:

```scala
import zio._
import zio.redis._

def stopListening(subscription: RedisSubscription): IO[RedisError, Unit] =
  subscription.unsubscribe("news") *> subscription.pUnsubscribe()
```

Interrupting a stream, for example with `take` or `interruptWhen`, also ends the consumer. Use `onUnsubscribe` to be notified when Redis confirms the removal.

## Inspecting Pub/Sub State

`Redis` offers the `PUBSUB` introspection commands. They run on the normal connection:

| Method                  | Returns                                           |
| ----------------------- | ------------------------------------------------- |
| `Redis#pubSubChannels`  | `Chunk[String]` of active channels matching a pattern |
| `Redis#pubSubNumSub`    | `Map[String, Long]` of subscriber counts per channel  |
| `Redis#pubSubNumPat`    | `Long`, the number of active pattern subscriptions    |

To print the number of subscribers of two channels:

```scala
import zio._
import zio.redis._

def countSubscribers(redis: Redis): IO[RedisError, Map[String, Long]] =
  redis.pubSubNumSub("news", "weather")
```

## Errors

Besides the usual `RedisError` cases, subscription streams can fail with `RedisError.InvalidPubSubCommand` and `RedisError.SubscriptionStreamAlreadyClosed`, both subtypes of `RedisError.PubSubError`. See [Error Handling](./error-handling.md) for the full list.
