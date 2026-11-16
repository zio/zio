# Creating a zio-kafka Consumer

> Create a consumer with `Consumer.make`, passing some `ConsumerSettings`:

```scala
import zio._
import zio.kafka.consumer.{ Consumer, ConsumerSettings }

val consumerSettings: ConsumerSettings =
  ConsumerSettings(List("localhost:9092")).withGroupId("group")
val consumer: ZIO[Scope, Throwable, Consumer] =
  Consumer.make(consumerSettings)
```

The consumer settings are not only used for passing properties to the underlying Kafka consumer. You can also configure
zio-kafka features such as `rebalanceSafeCommits` (see [preventing duplicates](preventing-duplicates.md)), external offset
retrieval (see [offset retrieval](partition-assignment-and-offset-retrieval.md)), the pre-fetch strategy (see
[consumer tuning](consumer-tuning.md)), and silent authorization failure detection
(see [detecting authorization failures](detecting-authorization-failures.md)).

For an overview of all consumer settings and operations, see the [Consumer](consumer.md) reference page.

Notice that the consumer requires a `Scope` in the environment. When this scope closes, the consumer closes its
connection with the Kafka cluster. A scope can be created with the `ZIO.scoped` method:

```scala
ZIO.scoped {
  // make the consumer
  // use the consumer
}
// end of scope, consumer has closed connection to Kafka cluster.
```

If the consumer is provided as a layer, you can use a scoped layer (see below).

:::caution
If no explicit `Scope` is provided, the consumer uses the application's scope, and it will only close the connection
with the Kafka cluster when the application exits.
:::

## Providing a consumer as a layer

If your application uses the [ZIO service pattern](https://zio.dev/reference/service-pattern/), the consumer can
be provided as a layer. Here's an example of how to construct a consumer layer:

```scala
import zio._
import zio.kafka.consumer.{ Consumer, ConsumerSettings }

val consumerLayer: ZLayer[Any, Throwable, Consumer] =
  ZLayer.scoped { // (1)
    val consumerSettings: ConsumerSettings =
      ConsumerSettings(List("localhost:9092")).withGroupId("group")
    Consumer.make(consumerSettings)
  }
```

(1) By using `ZLayer.scoped`, the consumer is closed when the layer is no longer needed.

## Consumer settings from a layer

If you wish to create the `ConsumerSettings` and the `Consumer` from a layer, you can use `Consumer.live`. It takes
the consumer settings as a layer and produces a consumer.

The `provide` section of the main program could look like this:

```scala
import zio._
import zio.kafka.consumer.{ Consumer, ConsumerSettings }

val consumerSettingsLayer: ULayer[ConsumerSettings] =
  ZLayer.succeed(ConsumerSettings(List("localhost:9092")).withGroupId("group"))

// Any program that needs a Consumer
val program: ZIO[Consumer, Throwable, Unit] =
  ZIO.serviceWithZIO[Consumer](_.listTopics()).unit

val app: ZIO[Any, Throwable, Unit] =
  program.provide(
    consumerSettingsLayer,
    Consumer.live
  )
```

In zio-kafka 2.x, `Consumer.live` also needed a diagnostics layer. Since zio-kafka 3.0, diagnostics are set with `ConsumerSettings.withDiagnostics` instead; see [migrating to zio-kafka 3](migrating-to-zio-kafka-3.md).
