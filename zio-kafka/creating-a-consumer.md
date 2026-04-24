# Creating a zio-kafka Consumer

> Create a consumer with `Consumer.make`, passing some `ConsumerSettings`:

Create a consumer with `Consumer.make`, passing some `ConsumerSettings`:

```scala

val consumerSettings: ConsumerSettings =
  ConsumerSettings(List("localhost:9092")).withGroupId("group")
val consumer: ZIO[Scope, Throwable, Consumer] =
  Consumer.make(consumerSettings)
```

The consumer settings are not only used for passing properties to the underlying Kafka consumer. You can also configure
zio-kafka features such as `rebalanceSafeCommits` (see [preventing duplicates](preventing-duplicates.md)), manual offset
retrieval (see [offset retrieval](partition-assignment-and-offset-retrieval.md)), and the pre-fetch strategy (see
[consumer tuning](consumer-tuning.md)).

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

val consumerLayer: Layer[Any, Throwable, Consumer] =
  ZLayer.scoped { // (1)
    val consumerSettings: ConsumerSettings =
      ConsumerSettings(List("localhost:9092")).withGroupId("group")
    Consumer.make(consumerSettings)
  }
```

(1) By using `ZLayer.scoped`, the consumer is closed when the layer is no longer needed.

## Consumer settings from a layer (zio-kafka 3.x)

If you wish to create the `ConsumerSettings` and the `Consumer` from a layer, you can use `Consumer.live`. It takes
the consumer settings as a layer and produces a consumer.

The `provide` section of the main program could look like this:

```scala
.provide(
  consumerSettingsLayer,
  Consumer.live
)
```

## Consumer settings from a layer (zio-kafka 2.x)

If you wish to create the `ConsumerSettings` and the `Consumer` from a layer, you can use `Consumer.live`. It takes
the consumer settings and diagnostics settings as a layer and produces a consumer.

The `provide` section of the main program could look like this:

```scala
.provide(
  consumerSettingsLayer,
  ZLayer.succeed(Consumer.NoDiagnostics),
  Consumer.live
)
```
