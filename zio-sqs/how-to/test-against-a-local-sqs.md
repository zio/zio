# Test Against a Local SQS

> Programs that use ZIO SQS can be tested without an AWS account. ElasticMQ is an open-source, in-memory server that implements the SQS REST API. The library's own test suite runs against it, and a program can use it in the same way. The client only needs its endpoint pointed at the local server.

## Adding ElasticMQ to the Test Classpath

Add the ElasticMQ REST server to the test dependencies in `build.sbt`, using the same version as the library's own tests:

```scala
libraryDependencies += "org.elasticmq" %% "elasticmq-rest-sqs" % "1.7.1" % Test
```

## Starting a Local SQS Endpoint

`SQSRestServerBuilder.withPort` builds a server that listens on the given port, and `start()` starts it. The server is a resource, so it is wrapped in `ZIO.acquireRelease`. The release step stops the server when the scope closes, even when the test fails:

```scala
import org.elasticmq.rest.sqs.{SQSRestServer, SQSRestServerBuilder}
import zio._

val localServer: ZIO[Scope, Throwable, SQSRestServer] =
  ZIO.acquireRelease(ZIO.attempt(SQSRestServerBuilder.withPort(9324).start()))(server => ZIO.succeed(server.stopAndWait()))
```

## Pointing the Client at the Local Endpoint

The SQS client needs two changes for the local server. The endpoint override sends the requests to `http://localhost:9324` instead of AWS. The credentials can be any static values, because the local server does not check them:

```scala
import java.net.URI
import software.amazon.awssdk.auth.credentials.{AwsBasicCredentials, StaticCredentialsProvider}
import software.amazon.awssdk.regions.Region
import zio._
import zio.aws.core.config.AwsConfig
import zio.aws.netty.NettyHttpClient
import zio.aws.sqs.Sqs

val localClient: ZLayer[Any, Throwable, Sqs] =
  NettyHttpClient.default >>> AwsConfig.default >>> Sqs.customized(
    _.region(Region.US_EAST_1)
      .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("key", "key")))
      .endpointOverride(URI.create("http://localhost:9324"))
  )
```

Programs under test should take the `Sqs` layer as a dependency rather than building it themselves. That way the test can provide `localClient`, and the production code can provide the real client from [Getting Started](../getting-started.md).

## Running a Round Trip

A round trip creates a queue, sends an event, and reads it back. The program below runs against the local server, so the output is printed without any AWS call:

```scala
import zio._
import zio.aws.sqs.Sqs
import zio.sqs.{SqsStream, SqsStreamSettings, Utils}
import zio.sqs.producer.{Producer, ProducerEvent}
import zio.sqs.serialization.Serializer

val roundTrip: ZIO[Sqs & Scope, Throwable, Unit] =
  for {
    _        <- localServer
    _        <- Utils.createQueue("LocalQueue")
    queueUrl <- Utils.getQueueUrl("LocalQueue")
    _        <- ZIO.scoped {
                  Producer.make(queueUrl, Serializer.serializeString).flatMap(_.produce(ProducerEvent("hello from a test")))
                }
    messages <- SqsStream(queueUrl, SqsStreamSettings.default.withStopWhenQueueEmpty(true).withWaitTimeSeconds(1)).runCollect
    _        <- Console.printLine(messages.map(_.body.getOrElse("")).mkString(", "))
  } yield ()
```

The round trip sets `withStopWhenQueueEmpty` to `true` and `withWaitTimeSeconds` to `1`. Without them, the stream would wait for new messages forever, and long polling would hold the test for up to 20 seconds at each empty receive.

The round trip starts its own server, so it only needs the client layer to run:

```scala
object LocalRoundTrip extends ZIOAppDefault {
  override def run: ZIO[Any, Throwable, Unit] =
    ZIO.scoped(roundTrip).provide(localClient)
}
```

## Running Tests Against the Local Server

In a ZIO Test spec, the server and the client are shared layers. Tests that use the same port must not run in parallel, because the second server cannot bind to a port the first one holds. Set `Test / parallelExecution := false` in `build.sbt`, as the library does, and use one queue name per test to keep the tests independent.

ElasticMQ keeps queues in memory, so every run starts with no queues. Stopping the server removes everything it held.

## Limits of a Local Server

ElasticMQ implements the common parts of the SQS API, but it is an approximation. Timing details such as the exact visibility timeout expiry, the throughput limits, and the error codes that AWS returns under load can differ from the real service. Use the local server to check the program's logic, and run a smoke test against a real queue before the code ships.
