# How to Test with Testcontainers

> ZIO AMQP is a thin layer over a real broker, so the most reliable way to test code that uses it is to run against a real RabbitMQ. This guide shows how to start a throwaway RabbitMQ container for a ZIO Test suite with [Testcontainers for Scala](https://github.com/testcontainers/testcontainers-scala), the approach the library's own test suite uses, and how to point the same tests at a broker that is already running, as on a CI server.

The test code on this page needs libraries that are not dependencies of ZIO AMQP itself, so the snippets are shown without being compiled by the documentation build. They are taken from the library's own test, [`AmqpClientSpec.scala`](https://github.com/zio/zio-amqp/blob/master/zio-amqp/src/test/scala/zio/amqp/AmqpClientSpec.scala), which compiles and runs in CI. Docker must be running on the machine that runs the tests.

## Add the Test Dependencies

Add ZIO Test and the Testcontainers module for RabbitMQ to the test scope, and register the ZIO Test framework with sbt:

```scala
libraryDependencies ++= Seq(
  "dev.zio"      %% "zio-test"                      % "2.1.26" % Test,
  "dev.zio"      %% "zio-test-sbt"                  % "2.1.26" % Test,
  "com.dimafeng" %% "testcontainers-scala-rabbitmq" % "0.44.1"        % Test
)

testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")
```

The versions above are the ones this repository's build uses at the time of writing. Check Maven Central for newer releases.

## Start a Container as a Layer

A container should start once per test run and stop afterwards, even if a test fails. Model it as a scoped `ZLayer` that acquires the container with `ZIO.acquireRelease`, and expose only what the tests need, the host and the AMQP port:

```scala
import com.dimafeng.testcontainers.RabbitMQContainer
import zio._

final case class ContainerDetails(host: String, amqpPort: Int)

val rabbitContainerDetails: ZLayer[Scope, Throwable, ContainerDetails] =
  ZLayer.fromZIO(
    ZIO
      .acquireRelease(ZIO.attempt(RabbitMQContainer.Def().start()))(c => ZIO.attempt(c.stop()).orDie)
      .map(c => ContainerDetails(c.host, c.amqpPort))
  )
```

`RabbitMQContainer.Def().start()` pulls the module's default RabbitMQ image on first use (at the time of writing `rabbitmq:3.7-management-alpine`; the CI workflow of this repository runs `rabbitmq:3.13`) and waits until the broker accepts connections. Testcontainers maps the AMQP port to a random free host port, which is why the layer reads `amqpPort` instead of assuming 5672.

## Write a Test Against the Broker

A test connects with the container's host and port, using the `ConnectionFactory` overload of `Amqp.connect`, and uses unique queue names so that tests do not interfere with each other. The suite shares one container with `provideSomeShared`:

```scala
import com.rabbitmq.client.ConnectionFactory
import zio._
import zio.amqp._
import zio.amqp.model._
import zio.test._

import java.util.UUID

object PublishConsumeSpec extends ZIOSpecDefault {
  def spec: Spec[Scope, Throwable] =
    suite("PublishConsumeSpec")(
      test("a published message is consumed") {
        val queue = QueueName(s"queue-${UUID.randomUUID()}")

        ZIO.service[ContainerDetails].flatMap { container =>
          val factory = new ConnectionFactory()
          factory.setHost(container.host)
          factory.setPort(container.amqpPort)

          Amqp
            .connect(factory)
            .flatMap(Amqp.createChannel)
            .flatMap { channel =>
              for {
                _      <- channel.queueDeclare(queue)
                _      <- channel.publish(ExchangeName(""), "ping".getBytes("UTF-8"), RoutingKey(QueueName.unwrap(queue)))
                bodies <- channel
                            .consume(queue, ConsumerTag("test"))
                            .take(1)
                            .runCollect
                            .map(_.map(d => new String(d.getBody, "UTF-8")))
                _      <- channel.queueDelete(queue)
              } yield assertTrue(bodies == Chunk("ping"))
            }
        }
      } @@ TestAspect.timeout(60.seconds)
    ).provideSomeShared[Scope](rabbitContainerDetails)
}
```

The connection and the channel are scoped, and ZIO Test's `Scope` environment closes them when the test ends. Always put a `TestAspect.timeout` aspect on a test that consumes: a stream that waits for a message that never arrives would otherwise hang the build.

## Wait for Asynchronous Effects

The broker updates some state asynchronously. For example, `Channel#messageCount` right after a publish can still return the old value, and `Channel#consumerCount` right after forking a consumer can return 0. The library's tests delay the assertion by a second, which needs the real clock because ZIO Test runs with a simulated one:

```scala
import zio._
import zio.test._

test("messageCount sees a published message") {
  // ... declare queue, publish ...
  channel.messageCount(queue).delay(1.second).map(count => assertTrue(count == 1L))
} @@ TestAspect.withLiveClock
```

## Use a Broker That Is Already Running

On a CI server, starting a container per job is slow, and the job may not be allowed to use Docker at all. The library's test suite switches on the `CI` environment variable: when it is set, it uses a RabbitMQ at `localhost:5672`, started by the CI workflow with the repository's `docker-compose.yml` (`docker compose up -d`), and otherwise starts a container:

```scala
val rabbitContainerDetails: ZLayer[Scope, Throwable, ContainerDetails] =
  ZLayer.fromZIO(
    zio.System.env("CI").flatMap {
      case Some(_) => ZIO.succeed(ContainerDetails("localhost", 5672))
      case None    =>
        ZIO
          .acquireRelease(ZIO.attempt(RabbitMQContainer.Def().start()))(c => ZIO.attempt(c.stop()).orDie)
          .map(c => ContainerDetails(c.host, c.amqpPort))
    }
  )
```

Because the broker is shared by every test in that run, keep the unique queue names and delete what each test creates.
