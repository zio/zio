# Introduction to ZIO SQS

> This library is a [ZIO](https://github.com/zio/zio)-powered client for AWS SQS. It is built on top of the [AWS SDK for Java 2.0](https://docs.aws.amazon.com/sdk-for-java/v2/developer-guide/basics.html) via the automatically generated wrappers from [zio-aws](https://github.com/vigoo/zio-aws).

This library is a [ZIO](https://github.com/zio/zio)-powered client for AWS SQS. It is built on top of the [AWS SDK for Java 2.0](https://docs.aws.amazon.com/sdk-for-java/v2/developer-guide/basics.html) via the automatically generated wrappers from [zio-aws](https://github.com/vigoo/zio-aws).

## Add the dependency

To use `zio-sqs`, add the following line in your `build.sbt` file:

```
libraryDependencies += "dev.zio" %% "zio-sqs" % "0.5.0"
```

## How to use

In order to use the connector, you need to provide your program with a configured SQS client as an `Sqs` ZLayer. You can use `io.github.vigoo.zioaws.sqs.live` to use default AWS SDK settings or use `.customized` (refer to the [AWS SDK Documentation](https://docs.aws.amazon.com/sdk-for-java/v2/developer-guide/creating-clients.html) if you need help customizing it). See also the [ZIO documentation](https://zio.dev/docs/howto/howto_use_layers) on how to use layers.

### Publish messages

Use `Producer.make` to instantiate an instance of `Producer` trait that can be used to publish objects of type `T` to the queue.

```scala
def make[R, T](
    queueUrl: String,
    serializer: Serializer[T],
    settings: ProducerSettings = ProducerSettings()
  ): ZManaged[R with Sqs with Clock, Throwable, Producer[T]]
```

where:

- `queueUrl: String` - an SQS queue URL
- `serializer: Serializer[T]` - an instance of `zio.sqs.serialization.Serializer` that can be used to convert an object of type `T` to a string.

  ```scala
    trait Serializer[T] {
      def apply(t: T): String
    }
  ```
  If a published message is already a string, `Serializer.serializeString` can be used.
- `settings: ProducerSettings` - a set of settings (`ProducerSettings`) used to configure the producer.
    - `batchSize: Int` - The size of the batch to use, [1-10] (default: 10).
    - `duration: Duration` - Time to wait for the batch to be full (have the specified batchSize) (default: 500 milliseconds).
    - `parallelism: Int` - The number of concurrent requests to make to SQS (default: 16).
    - `retryDelay: Duration` - Time to wait before retrying event republishing if it failed with a recoverable error (default: 250 milliseconds).
      The errors returned from SQS could either recoverable or not. An example of recoverable error -- when the server returned the code: `ServiceUnavailable`
    - `retryMaxCount: Int` - The number of retries to make for a posted event (default: 10).

#### Producer

`Producer` contains two set of methods:
- methods that fail the resulting *Task* or *Stream* if SQS server returns an error for a published event.
    - `def produce(e: ProducerEvent[T]): Task[ProducerEvent[T]]` - Publishes a single event and fails the task.
      Fails the `Task` if the server returns an error.
    - `def produceBatch(es: Iterable[ProducerEvent[T]]): Task[List[ProducerEvent[T]]]` - Publishes a batch of events.
      Fails the `Task` if the server returns an error for any of the provided events.
    - `def sendStream: Stream[Throwable, ProducerEvent[T]] => ZStream[Any, Throwable, ProducerEvent[T]]` - Stream that takes the events and produces a stream with published events.
      Fails if the server returns an error for any of the published events.
    - `def sendSink: ZSink[Any, Throwable, Nothing, Iterable[ProducerEvent[T]], Unit]` - Sink that can be used to publish events.
      Fails if the server returns an error for any of the published events.

- methods that do not fail the operation but return `ErrorOrEvent[T]` (defied as `Either[ProducerError[T], ProducerEvent[T]]`).
    - `def sendStreamE: Stream[Throwable, ProducerEvent[T]] => ZStream[Any, Throwable, ErrorOrEvent[T]]` - Stream that takes the events and produces a stream with the results.
      Doesn't fail if the server returns an error for any of the published events.
    - `def produceBatchE(es: Iterable[ProducerEvent[T]]): Task[List[ErrorOrEvent[T]]]` - Publishes a batch of events. Completes when all input events were processed (published to the server or failed with an error).
      Doesn't fail the `Task` if the server returns an error for any of the provided events.

Producer tries to accumulate messages in batches and send them to the server.
If messages should be sent one by one and batching is not expected, set `ProducerSettings.batchSize` to `1`.

#### ProducerEvent

`ProducerEvent[T]` is an event that is published to SQS and contains the following parameters that could be configured:
- `data: T` - Object to publish to SQS. A serializer for this type should be provided when a `Producer` is instantiated.
- `attributes: Map[String, MessageAttributeValue]` - A map of [attributes](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-message-attributes.html) to set.
- `groupId: Option[String]` - Assigns a specific [message group](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/using-messagegroupid-property.html) to the message.
- `deduplicationId: Option[String]` - Token used for [deduplication](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/using-messagededuplicationid-property.html) of sent messages.

If a plain string should be published without any additional attributes a `ProducerEvent` can be created directly:

```scala
val str: String = "message to publish"
val event: ProducerEvent = ProducerEvent(str)
```

#### ProducerError

`ProducerError[T]` represents an [error details]((https://docs.aws.amazon.com/AWSSimpleQueueService/latest/APIReference/API_BatchResultErrorEntry.html)) that were returned from the server.
- `senderFault: Boolean` - Specifies whether the error happened due to the caller of the batch API action.
- `code: String` - An error code representing why the action failed on this entry.
- `message: Option[String]` - A message explaining why the action failed on this entry.
- `event: ProducerEvent[T]` - An event that triggered this error on the server.

#### Publish Example

```scala

object PublishExample extends zio.App {

  val client: ZLayer[Any, Throwable, Sqs] = zioaws.netty.default >>>
    zioaws.core.config.default >>>
    zioaws.sqs.live

  val events                                                = List("message1", "message2").map(ProducerEvent(_))
  val queueName                                             = "TestQueue"
  val program: RIO[Clock with Sqs, Either[Throwable, Unit]] = for {
    queueUrl    <- Utils.getQueueUrl(queueName)
    producer     = Producer.make(queueUrl, Serializer.serializeString)
    errOrResult <- producer.use(p => p.sendStream(ZStream(events: _*)).runDrain.either)
  } yield errOrResult

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    program.provideCustomLayer(client).exitCode
}
```

### Consume messages

Use `SqsStream.apply` to get a stream of messages from a queue. It returns a ZIO `Stream` that you can consume with all the operators available.

```scala
def apply(
  queueUrl: String,
  settings: SqsStreamSettings = SqsStreamSettings()
): ZStream[Sqs, Throwable, Message]
```

`SqsStreamSettings` allows your to configure a number of things:

- `autoDelete`: if `true`, messages will be automatically deleted from the queue when they're consumed by the stream, if `false` you have to delete them explicitly by calling `SqsStream.deleteMessage` (default `true`)
- `stopWhenQueueEmpty`: if `true` the stream will close when there the queue is empty, if `false` the stream will go on forever (default `false`)
- `attributeNames`: see the [related page on AWS docs](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/APIReference/API_ReceiveMessage.html)
- `maxNumberOfMessages`: number of messages to query at once from SQS (default `1`)
- `messageAttributeNames`: see the [related page on AWS docs](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/APIReference/API_ReceiveMessage.html)
- `visibilityTimeout`: see the [related page on AWS docs](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-visibility-timeout.html) (default `Some(30)`. If set to `None`, the queue's value will be used.)
- `waitTimeSeconds`: see the [related page on AWS docs](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-long-polling.html) (default `Some(20)`. If set to `None`, the queue's value will be used.)

**Example:**

```scala

SqsStream(
  queueUrl,
  SqsStreamSettings(stopWhenQueueEmpty = true, waitTimeSeconds = Some(3))
).foreach(msg => UIO(println(msg.body)))
```

### Full example

```scala

object TestApp extends zio.App {
  val queueName = "TestQueue"

  val client: ZLayer[Any, Throwable, Sqs] = zioaws.netty.default ++
    ZLayer.succeed(
      CommonAwsConfig(
        region = Some(Region.of("ap-northeast-2")),
        credentialsProvider = StaticCredentialsProvider.create(AwsBasicCredentials.create("key", "key")),
        endpointOverride = None,
        commonClientConfig = None
      )
    ) >>>
    zioaws.core.config.configured() >>>
    zioaws.sqs.live

  val program: RIO[Sqs with Clock, Unit] = for {
    _        <- Utils.createQueue(queueName)
    queueUrl <- Utils.getQueueUrl(queueName)
    producer  = Producer.make(queueUrl, Serializer.serializeString)
    _        <- producer.use { p =>
                  p.produce(ProducerEvent("hello"))
                }
    _        <- SqsStream(
                  queueUrl,
                  SqsStreamSettings(stopWhenQueueEmpty = true, waitTimeSeconds = Some(3))
                ).foreach(msg => UIO(println(msg.body)))
  } yield ()

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    program.provideCustomLayer(client).exitCode
}
```
