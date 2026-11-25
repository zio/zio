# How to Publish and Subscribe to Typed Events

> This guide shows how to publish case classes to Redis channels and consume them with a pattern subscription. The plain-string basics, callbacks and unsubscribing are covered in [Pub/Sub](../concepts/pub-sub.md).

## Define the Event and a Codec

Case classes need a structural codec, because `CodecSupplier.utf8` encodes primitives only. The publisher and the subscriber must use the same supplier. This guide uses Protobuf from `zio-schema-protobuf`; see [Codecs](../concepts/codecs.md):

```scala
import zio._
import zio.redis._
import zio.schema.{DeriveSchema, Schema}
import zio.schema.codec.{BinaryCodec, ProtobufCodec}

final case class OrderPlaced(orderId: Long, total: Double)

object OrderPlaced {
  implicit val schema: Schema[OrderPlaced] = DeriveSchema.gen[OrderPlaced]
}

object ProtobufSupplier extends CodecSupplier {
  def get[A: Schema]: BinaryCodec[A] = ProtobufCodec.protobufCodec
}
```

## Provide Both Layers

Publishing uses `Redis`; listening uses `RedisSubscription`, which owns its own connection. Both layers are described in [Pub/Sub](../concepts/pub-sub.md):

```scala
val layers: ZLayer[Any, RedisError.IOError, Redis & RedisSubscription] =
  ZLayer.make[Redis & RedisSubscription](
    ZLayer.succeed(RedisConfig.Local),
    ZLayer.succeed[CodecSupplier](ProtobufSupplier),
    Redis.singleNode,
    RedisSubscription.singleNode
  )
```

## Publish an Event

Use one channel per event kind, for example `orders.placed`. `Redis#publish` returns how many clients received the message:

```scala
def placeOrder(redis: Redis, event: OrderPlaced): IO[RedisError, Long] =
  redis.publish("orders.placed", event)
```

## Consume Every Order Event

Subscribe to the pattern `orders.*` to receive events from all order channels. Each element is a pair of the channel the message was published to and the decoded event:

```scala
val consume: ZIO[RedisSubscription, RedisError, Unit] =
  ZIO.serviceWithZIO[RedisSubscription] { subscription =>
    subscription
      .pSubscribe("orders.*")
      .returning[OrderPlaced]
      .foreach { case (channel, event) => ZIO.debug(s"$channel: ${event.orderId}") }
  }
```

Pub/Sub does not store messages. A subscriber that is not connected when an event is published never sees it. When events must not be lost, use [Streams](../reference/commands/streams.md) instead.
