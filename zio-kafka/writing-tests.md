# Writing Tests with the `zio-kafka-testkit` library

> Zio-kafka provides a `zio-kafka-testkit` library to help you test your code using zio-kafka.

Zio-kafka provides a `zio-kafka-testkit` library to help you test your code using zio-kafka.

To add it in your project, add the following dependency in your `build.sbt`:

```scala
libraryDependencies += "dev.zio" %% "zio-kafka-testkit" % "<latest-version>" % Test
```

Let's study some examples of tests you can write with the `zio-kafka-testkit` and `zio-test` and let's see what this
library provides you.

## Testing a producer

```scala

object ProducerSpec extends ZIOSpecDefault {
  override def spec: Spec[TestEnvironment & Scope, Any] =
    (
      suite("Producer test suite")(
        test("minimal example") {
          for {
            producer <- KafkaTestUtils.makeProducer // (1)
            _ <- producer.produce(new ProducerRecord("topic", "boo", "baa"), Serde.string, Serde.string) // (3)
          } yield assertCompletes // (3)
        }
        // ... more tests ...
      )
        .provideSomeShared[Scope](Kafka.embedded) // (4) Provide an embedded Kafka instance, shared in the entire suite
      ) @@ withLiveClock @@ timeout(2.minutes) // (5)
}
```

The main entry points for zio-kafka test kit are the classes `KafkaTestUtils` and `Kafka`.

(1) A producer is created with lots of default settings. It knows how to connect to the Kafka broker by getting a
`Kafka` instance from the environment (see (4)).

(2) The producer is used to produce a record.

Take a look at the `produce*` methods in `KafkaTestUtils` when you need to produce a lot of records.

(3) The `assertCompletes` assertion from zio-test is used to check that the effect completes without errors.

(4) Provide a `Kafka` instance as a layer shared between all the tests. In this test we provide an **Embedded Kafka**,
meaning that a complete Kafka cluster is started and stopped inside the current JVM.

See further below for more options, such as connecting to an external Kafka cluster.

Note that any services that are _not_ provided at this line, must be given as type parameter to `provideSomeShared`. In
this case we are not providing a `Scope` (it is provided by the test framework).

(5) Zio-kafka requires a live clock.

### Producer layer

In this example above, we decided to make a new `Producer` in each test. We could have decided to share one instance of
`Producer` between all the tests of this suite. For this purpose we can get a Producer layer using
`KafkaTestUtils.producer`. The result is:

```scala
suite("producer test suite")(
  // ... tests ...
).provideSomeShared[Scope](Kafka.embedded, KafkaTestUtils.producer)
```

### More Kafka sharing options

Kafka is slow to start, so it is better to only start it once and share it between all tests of the suite.

However, if we insist on a separate embedded Kafka _per test_, we can provide the `Kafka.embedded` layer with
`provideSome` (instead of `provideSomeShared`), which looks like this:

```scala
suite("producer test suite")(
  // ... tests ...
).provideSome[Scope](Kafka.embedded)
```

We can also share one embedded `Kafka` instance between different test suites (i.e. between different test files) by
mixing in the `ZIOSpecWithKafka` trait, this looks like this:

```scala
object ProducerSpec extends ZIOSpecWithKafka { // (1)
  override def spec: Spec[TestEnvironment & Scope & Kafka, Any] =
    (
      suite("Producer test suite")(
        // ... tests ...
      )
        .provideSome[Scope & Kafka](/* ...other layers... */) // (2)
      )
}
```

(1) Note the `ZIOSpecWithKafka` trait usage here instead of `ZIOSpecDefault`.

(2) When we need to provide additional layers with `provideSome` or `provideSomeShared`, both `Scope` and `Kafka` are
now provided by the test framework. Therefore, we need to include both in the type parameter.

More details about this `ZIOSpecWithKafka` trait [below](#ziospecwithkafka-trait).

## Considerations for sharing Kafka between tests

Zio tests by default all run concurrently. Tests may interfere with each other through the shared Kafka resource. The
best way to prevent interference is by making sure each test uses a different topic. If this is not feasible, we can use
the `sequential` aspect from zio-test to run the tests one by one.

```scala

suite("test suite")(
 // ... tests ...
) @@ sequential
```

## Testing a consumer

```scala

object ConsumerSpec extends ZIOSpecDefault {

  override def spec: Spec[TestEnvironment & Scope, Any] =
    (
      suite("Consumer test suite")(
        test("minimal example") {
          // Records (as key/value pairs) to produce and consume
          val kvs: List[(String, String)] = (1 to 5).toList.map(i => (s"key-$i", s"msg-$i"))
          for {
            topic <- Random.nextUUID.map("topic-" + _.toString) // (1)
            client <- Random.nextUUID.map("client-" + _.toString)
            group <- Random.nextUUID.map("group-" + _.toString)

            _ <- KafkaTestUtils.createCustomTopic(topic, partitionCount = 3) // (2)

            producer <- KafkaTestUtils.makeProducer
            _ <- KafkaTestUtils.produceMany(producer, topic, kvs) // (3)

            consumer <- KafkaTestUtils.makeConsumer(clientId = client, groupId = Some(group)) // (4)
            records <- consumer
              .plainStream(Subscription.topics(topic), Serde.string, Serde.string)
              .take(5)
              .runCollect // (5)
            consumed = records.map(r => (r.record.key, r.record.value)).toList
          } yield assert(consumed)(hasSameElements(kvs))
        }
      )
        .provideSomeShared[Scope](Kafka.embedded) // (6)
      ) @@ withLiveClock @@ timeout(2.minutes)
}
```

(1) Using random values for these parameters is important to avoid conflicts between tests as we share one Kafka
instance between all the tests of the suite.

(2) Here we create a topic with 3 partitions. Note that by default Kafka auto-creates topics. Therefore, creating
a topic explicitly is only needed when we want to control the number of partitions, or when your Kafka cluster does
not allow auto-created topics.

(3) A producer is constructed and 5 records are produced with `KafkaTestUtils.produceMany`.

(4) A consumer is constructed with `KafkaTestUtils.makeConsumer`.

(5) The consumer reads 5 records from the topic.

Be careful with the `take` method, due to pre-fetching, the consumer may have fetched more from the topic than expected.

(6) Similarly as in the producer test above, we provide the `Kafka.embedded` layer.

## More consumer options

If we want to share a consumer between tests, we can use the `KafkaTestUtils.consumer` layer and provide it with the
`provideSomeShared` method (see the producer example above for more details).

## Utilities provided by the `zio-kafka-testkit` library

### `Kafka` service

This trait represents a Kafka instance in your tests. It is used to provide the bootstrap servers to the constructor
methods in `KafkaTestUtils`.

```scala
trait Kafka {
  def bootstrapServers: List[String]

  def stop(): UIO[Unit]
}
```

The companion object provides a few layers to provide a Kafka instance in your tests:

```scala
object Kafka {
  /**
   * Creates an in-memory Kafka instance with a random port.
   */
  val embedded: ZLayer[Any, Throwable, Kafka]

  /**
   * Will connect to a Kafka instance running on localhost:9092 (with Docker, for example).
   */
  val local: ULayer[Kafka]

  /**
   * Creates an in-memory Kafka instance with a random port and SASL authentication configured.
   */
  val saslEmbedded: ZLayer[Any, Throwable, Kafka.Sasl]

  /**
   * Creates an in-memory Kafka instance with a random port and SSL authentication configured.
   */
  val sslEmbedded: ZLayer[Any, Throwable, Kafka]
}
```

The in-memory Kafka instances are created using [embedded-kafka](https://github.com/embeddedkafka/embedded-kafka).

### `KafkaTestUtils` utilities

This object provides several utilities to simplify writing your tests, like constructing a `Producer`, a `Consumer`,
or an `AdminClient`.

It also provides several functions to produce records, and more.

Each utility function is documented in the source code. Please have a look at the source code for more details.     
You can also look at `zio-kafka` tests in the `zio-kafka-test` module to have examples on how to use these utilities.

### `ZIOSpecWithKafka` trait

This trait can be used if you want to share one Kafka instance between different test suites.    
This allows you to speed up your tests by booting a Kafka instance only once for all your test suites using this trait.

Usage example:

```scala
// In `src/test/scala/io/example/producer/ProducerSpec.scala`
object ProducerSpec extends ZIOSpecWithKafka { // Note `ZIOSpecWithKafka`
  override def spec: Spec[TestEnvironment & Scope & Kafka, Any] =
    suite("Producer test suite")(
      // ... tests ...
    ) @@ timeout(2.minutes)
}

// In `src/test/scala/io/example/consumer/ConsumerSpec.scala`
object ConsumerSpec extends ZIOSpecWithKafka { // Note `ZIOSpecWithKafka`
  override def spec: Spec[TestEnvironment & Scope & Kafka, Any] =
    suite("Consumer test suite")(
      // ... tests ...
    ) @@ timeout(2.minutes)
}
```

This is a capability offered by ZIO2.    
See related zio-test documentation: https://zio.dev/reference/test/sharing-layers-between-multiple-files/

### `KafkaRandom` trait

The `KafkaRandom` trait provides a few methods to generate random values.
To use it, you need to mix it in your test suite, like this:

```scala

object MyServiceSpec extends ZIOSpecDefault with KafkaRandom {
  // Required when mixing in the `KafkaRandom` trait
  // The best is to use a different prefix for each test suite
  override def kafkaPrefix: String = "my-service"

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("MyService")(
      test("minimal example") {
        for {
          topic    <- randomTopic  // Comes from `KafkaRandom`
          clientId <- randomClient // Comes from `KafkaRandom`
          groupId  <- randomGroup  // Comes from `KafkaRandom`
          // ... 
        } yield assertCompletes
      }
    ).provideSomeShared[Scope](Kafka.embedded)
}
```
