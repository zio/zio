# Build a Queue Worker

> Build a worker that reads orders from an SQS queue, processes them and publishes receipts, tested against a local SQS server.

Welcome to ZIO SQS! This tutorial is for Scala developers who know the basics of ZIO effects and want to learn how to send and receive messages with Amazon SQS. You don't need any prior experience with SQS or ZIO SQS to follow along.

## Learning Objectives

By the end of this tutorial, you will understand:

- How to run a local SQS server so that a program needs no AWS account
- How to publish typed events to a queue with a `Producer` and a `Serializer`
- How to consume a queue with at-least-once delivery
- How a worker connects two queues

We'll learn these concepts through:

1. Setting up the project
2. Starting a local SQS server
3. Modeling orders and receipts
4. Publishing orders
5. Writing the worker
6. Running everything together

We recommend reading from top to bottom, because each section builds on the previous one.

## Background

A queue worker takes work items from one queue, does something with them, and reports the results. In this tutorial the work items are orders and the results are receipts. SQS holds a message until a consumer deletes it. The worker deletes an order only after it has published the receipt, so an order is never lost when the worker fails half way. The price is that SQS can deliver an order more than once, which is why this is called at-least-once delivery.

## 1. Setting Up the Project

First, add ZIO SQS to the project by copying the block from [Installation](../index.md#installation). The local SQS server that we use for testing is a separate library, so add it to the test dependencies, or to the regular dependencies if you run this tutorial from `src/main`:

```scala
libraryDependencies += "org.elasticmq" %% "elasticmq-rest-sqs" % "1.7.1" % Test
```

The rest of the tutorial uses these imports, which every following block shares:

```scala
import java.net.URI

import org.elasticmq.rest.sqs.{SQSRestServer, SQSRestServerBuilder}
import software.amazon.awssdk.auth.credentials.{AwsBasicCredentials, StaticCredentialsProvider}
import software.amazon.awssdk.regions.Region
import zio._
import zio.aws.core.config.AwsConfig
import zio.aws.netty.NettyHttpClient
import zio.aws.sqs.Sqs
import zio.aws.sqs.model.Message
import zio.sqs.{SqsMessageLifetimeExtensionSettings, SqsStream, SqsStreamSettings, Utils}
import zio.sqs.producer.{Producer, ProducerEvent}
import zio.sqs.serialization.Serializer
```

## 2. Starting a Local SQS Server

ElasticMQ is an in-memory server that speaks the SQS protocol. We start it as a resource, so that it stops when the program ends, and we point the `Sqs` client at it:

```scala
val localServer: ZIO[Scope, Throwable, SQSRestServer] =
  ZIO.acquireRelease(ZIO.attempt(SQSRestServerBuilder.withPort(9324).start()))(server => ZIO.succeed(server.stopAndWait()))

val localClient: ZLayer[Any, Throwable, Sqs] =
  NettyHttpClient.default >>> AwsConfig.default >>> Sqs.customized(
    _.region(Region.US_EAST_1)
      .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("key", "key")))
      .endpointOverride(URI.create("http://localhost:9324"))
  )
```

The code above does two things. `localServer` starts the server on port 9324 and stops it when the surrounding `Scope` closes. `localClient` builds an `Sqs` layer that sends its requests to `http://localhost:9324` with made-up credentials, which the local server accepts. To talk to the real SQS service later, we only replace this layer, as [Getting Started](../getting-started.md) shows.

## 3. Modeling Orders and Receipts

SQS message bodies are strings. A `Serializer` turns our own types into strings when the producer sends them. Let's define an `Order` and a `Receipt`, a serializer for each, and a decoder for the order:

```scala
case class Order(id: Int, item: String)
case class Receipt(orderId: Int, status: String)

val orderSerializer: Serializer[Order] =
  Serializer.serializeString.contramap[Order](o => s"""{"id":${o.id},"item":"${o.item}"}""")

val receiptSerializer: Serializer[Receipt] =
  Serializer.serializeString.contramap[Receipt](r => s"""{"orderId":${r.orderId},"status":"${r.status}"}""")

val orderPattern = """\{"id":(\d+),"item":"([^"]*)"\}""".r

def decodeOrder(message: Message.ReadOnly): Option[Order] =
  message.body.toOption.flatMap {
    case orderPattern(id, item) => Some(Order(id.toInt, item))
    case _                      => None
  }
```

The code above defines the pieces that every later step uses. `Serializer#contramap` builds a serializer for a new type from the string serializer by converting the value to a string first. `decodeOrder` goes the other way, because the library has no deserializer: the consumer reads `Message.ReadOnly#body` and decodes it itself. A body that does not match the pattern decodes to `None`. Hand-written JSON keeps this tutorial short, and a real worker would use a JSON library.

## 4. Publishing Orders

A `Producer` publishes events to one queue. `Producer.make` returns it inside a `Scope`, because the producer runs a background fiber that batches the events. To publish two orders:

```scala
def publishOrders(ordersUrl: String): ZIO[Sqs & Scope, Throwable, Unit] =
  for {
    producer <- Producer.make(ordersUrl, orderSerializer)
    events    = List(Order(1, "book"), Order(2, "pen")).map { order =>
                  ProducerEvent(data = order, attributes = Map.empty, groupId = None, deduplicationId = None)
                }
    _        <- producer.produceBatch(events)
  } yield ()
```

The code above builds one `ProducerEvent` per order and publishes them together with `Producer#produceBatch`. The task completes when SQS has accepted every event, and it fails if SQS rejects one.

## 5. Writing the Worker

The worker is the heart of the tutorial. `SqsStream.consumeChunkAtLeastOnce` receives a chunk of orders, calls our function, and deletes the chunk only when the function succeeds. Our function decodes the orders and publishes one receipt per order:

```scala
def worker(ordersUrl: String, receipts: Producer[Receipt]): RIO[Sqs, Unit] =
  SqsStream.consumeChunkAtLeastOnce(
    queueUrl = ordersUrl,
    settings = SqsStreamSettings.default
      .withVisibilityTimeout(30)
      .withWaitTimeSeconds(1)
      .withStopWhenQueueEmpty(true),
    extensionSettings = SqsMessageLifetimeExtensionSettings.default
  ) { messages =>
    val events = messages.flatMap(decodeOrder(_).toList).map { order =>
      ProducerEvent(
        data = Receipt(order.id, s"packed ${order.item}"),
        attributes = Map.empty,
        groupId = None,
        deduplicationId = None
      )
    }
    receipts.produceBatch(events).unit
  }
```

The code above passes three groups of settings and one function. The stream settings hide a received order for 30 seconds while we work on it, wait at most 1 second for new orders, and end the worker when the queue is empty, so that the tutorial finishes. A production worker would leave `withStopWhenQueueEmpty` at its default of `false` and run forever. If the function fails, the orders stay in the queue and SQS delivers them again after the visibility timeout. Notice that the worker publishes the receipts before the library deletes the orders.

## 6. Running Everything Together

Now we connect the pieces. The program starts the server, creates both queues, publishes the orders, runs the worker, and reads the receipts back:

```scala
object QueueWorker extends ZIOAppDefault {

  val program: ZIO[Sqs & Scope, Throwable, Unit] =
    for {
      _           <- localServer
      _           <- Utils.createQueue("orders")
      _           <- Utils.createQueue("receipts")
      ordersUrl   <- Utils.getQueueUrl("orders")
      receiptsUrl <- Utils.getQueueUrl("receipts")
      _           <- publishOrders(ordersUrl)
      receipts    <- Producer.make(receiptsUrl, receiptSerializer)
      _           <- worker(ordersUrl, receipts)
      results     <- SqsStream(
                       receiptsUrl,
                       SqsStreamSettings.default.withAutoDelete(true).withStopWhenQueueEmpty(true).withWaitTimeSeconds(1)
                     ).runCollect
      _           <- Console.printLine(results.map(_.body.getOrElse("")).mkString("\n"))
    } yield ()

  override def run: Task[Unit] = ZIO.scoped(program).provide(localClient)
}
```

Running this program prints one receipt for each order. The order of the two lines can differ, because SQS does not guarantee the order of a standard queue:

```text
{"orderId":1,"status":"packed book"}
{"orderId":2,"status":"packed pen"}
```

Try changing the function in the worker to fail for the order with the item `pen`, for example with `ZIO.fail(new RuntimeException("out of pens"))`. The worker then stops with that error, so the program fails before it reads the receipts. The library deletes a chunk only after the function succeeds, so every order in the failed chunk stays in the queue, and SQS delivers it again after the visibility timeout.

## What You've Learned

In this tutorial, you learned:

- How to start ElasticMQ and point the `Sqs` client at it, so that a program runs without an AWS account
- How to publish typed events with `Producer.make`, a `Serializer` and `Producer#produceBatch`
- How to consume a queue with at-least-once delivery using `SqsStream.consumeChunkAtLeastOnce`
- How a worker reads from one queue and publishes its results to another

You now have a solid foundation in ZIO SQS. The next step is to apply it to your own queues.

## Where to Go Next

- To read messages as a stream and choose when to delete them, see [Consume Messages from a Queue](../how-to/consume-messages-from-a-queue.md).
- To keep long-running work hidden from other consumers, see [Process Messages at Least Once](../how-to/process-messages-at-least-once.md).
- To react to events that SQS rejects, see [Handle Send Failures](../how-to/handle-send-failures.md).
- To run your own tests against a local server, see [Test Against a Local SQS](../how-to/test-against-a-local-sqs.md).
- For the full API, see [SqsStream](../reference/sqs-stream.md) and [Producer](../reference/producer.md).
