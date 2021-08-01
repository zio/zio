---
id: community 
title:  "Community ZIO Libraries"
---

List of first-class ZIO support libraries from the community:

- [caliban](https://github.com/ghostdogpr/caliban) — Functional GraphQL backend in Scala
- [distage](https://github.com/7mind/izumi) — Staged, transparent and debuggable runtime & compile-time Dependency Injection Framework
- [idealingua](https://izumi.7mind.io/idealingua/) — API Definition, Data Modeling and RPC Language, optimized for fast prototyping – like gRPC, but with a human face
- [logstage](https://izumi.7mind.io/logstage/) — Automatic structural logs from Scala string interpolations
- [munit-zio](https://github.com/poslegm/munit-zio) — Lightweight bridge between ZIO and MUnit testing library
- [rezilience](https://github.com/svroonland/rezilience) — Utilities for resilience and handling of transient errors
- [slf4zio](https://github.com/mlangc/slf4zio) — Simple convenience layer on top of SLF4J for ZIO
- [tranzactio](https://github.com/gaelrenoux/tranzactio) — ZIO wrapper for data access libraries like Doobie or Anorm
- [ZIO Arrow](https://github.com/zio-mesh/zio-arrow) — Haskell Arrow meets ZIO. A deep composition and high performance applications
- [zio-amqp](https://github.com/svroonland/zio-amqp) — ZIO Streams based RabbitMQ client
- [zio-aws](https://github.com/vigoo/zio-aws) — Low-level AWS wrapper for ZIO for all AWS services using the AWS Java SDK v2
- [zio-aws-s3](https://github.com/Neurodyne/zio-aws-s3) — A lean, simple and efficient ZIO wrapper for AWS Java v2 S3 API by Boris V.Kuznetsov
- [zio-email](https://github.com/funcit/zio-email) — Purely functional email client
- [zio-grpc](https://github.com/scalapb/zio-grpc) — A native gRPC support for ZIO
- [zio-http](https://github.com/dream11/zio-http) — A scala library to write Http apps.
- [zio-k8s](https://github.com/coralogix/zio-k8s) — An idiomatic ZIO client for the Kubernetes API
- [zio-kinesis](https://github.com/svroonland/zio-kinesis) — ZIO Streams based AWS Kinesis client
- [zio-magic](https://github.com/kitlangton/zio-magic/) — Construct ZLayers automagically (w/ helpful compile-time errors) 
- [zio-pulsar](https://github.com/jczuchnowski/zio-pulsar) — Apache Pulsar client for Scala with ZIO and ZIO Streams integration.
- [zio-saga](https://github.com/VladKopanev/zio-saga) — Purely functional transaction management with Saga pattern
- [zio-slick-interop](https://github.com/ScalaConsultants/zio-slick-interop) — Slick interop for ZIO
- [zio-test-akka-http](https://github.com/senia-psm/zio-test-akka-http) — Akka-HTTP Route TestKit for zio-test
- [ZparkIO](https://github.com/leobenkel/ZparkIO) — Boiler plate framework to use Spark and ZIO together

If you know a useful library that has first-class ZIO support, please consider [submitting a pull request](https://github.com/zio/zio/pulls) to add it to this list.

## Caliban

[Caliban](https://ghostdogpr.github.io/caliban/) is a purely functional library for creating GraphQL servers and clients in Scala.

### Introduction

Key features of Caliban
- **Purely Functional** — All interfaces are pure and types are referentially transparent.
- **Type Safety** — Schemas are type safe and derived at compile time.
- **Minimal Boilerplate** — No need to manually define a schema for every type in your API.
- **Excellent Interoperability** — Out-of-the-box support for major HTTP server libraries, effect types, JSON libraries, and more.

### Installation

In order to use this library, we need to add the following line in our `build.sbt` file:

```scala
libraryDependencies += "com.github.ghostdogpr" %% "caliban" % "1.1.0"
```

Caliban also have lots of optional modules to inter-operate with other various libraries:

```scala
libraryDependencies += "com.github.ghostdogpr" %% "caliban-http4s"     % "1.1.0" // routes for http4s
libraryDependencies += "com.github.ghostdogpr" %% "caliban-akka-http"  % "1.1.0" // routes for akka-http
libraryDependencies += "com.github.ghostdogpr" %% "caliban-play"       % "1.1.0" // routes for play
libraryDependencies += "com.github.ghostdogpr" %% "caliban-finch"      % "1.1.0" // routes for finch
libraryDependencies += "com.github.ghostdogpr" %% "caliban-zio-http"   % "1.1.0" // routes for zio-http
libraryDependencies += "com.github.ghostdogpr" %% "caliban-cats"       % "1.1.0" // interop with cats effect
libraryDependencies += "com.github.ghostdogpr" %% "caliban-monix"      % "1.1.0" // interop with monix
libraryDependencies += "com.github.ghostdogpr" %% "caliban-tapir"      % "1.1.0" // interop with tapir
libraryDependencies += "com.github.ghostdogpr" %% "caliban-federation" % "1.1.0" // interop with apollo federation
```

### Example

First, to define Caliban API, we should define data models using case classes and ADTs. Then the Caliban can derive the whole GraphQL schema from these data models:

```scala modc:silent:nest
import caliban.GraphQL.graphQL
import caliban.schema.Annotations.GQLDescription
import caliban.{RootResolver, ZHttpAdapter}
import zhttp.http._
import zhttp.service.Server
import zio.{ExitCode, ZEnv, ZIO}

import scala.language.postfixOps

sealed trait Role

object Role {
  case object SoftwareDeveloper       extends Role
  case object SiteReliabilityEngineer extends Role
  case object DevOps                  extends Role
}

case class Employee(
    name: String,
    role: Role
)

case class EmployeesArgs(role: Role)
case class EmployeeArgs(name: String)

case class Queries(
    @GQLDescription("Return all employees with specific role")
    employees: EmployeesArgs => List[Employee],
    @GQLDescription("Find an employee by its name")
    employee: EmployeeArgs => Option[Employee]
)
object CalibanExample extends zio.App {

  val employees = List(
    Employee("Alex", Role.DevOps),
    Employee("Maria", Role.SoftwareDeveloper),
    Employee("James", Role.SiteReliabilityEngineer),
    Employee("Peter", Role.SoftwareDeveloper),
    Employee("Julia", Role.SiteReliabilityEngineer),
    Employee("Roberta", Role.DevOps)
  )

  val myApp = for {
    interpreter <- graphQL(
      RootResolver(
        Queries(
          args => employees.filter(e => args.role == e.role),
          args => employees.find(e => e.name == args.name)
        )
      )
    ).interpreter
    _ <- Server
      .start(
        port = 8088,
        http = Http.route { case _ -> Root / "api" / "graphql" =>
          ZHttpAdapter.makeHttpService(interpreter)
        }
      )
      .forever
  } yield ()

  override def run(args: List[String]): ZIO[ZEnv, Nothing, ExitCode] =
    myApp.exitCode

}
```

Now let's query all software developers using GraphQL query language:

```graphql
query{
  employees(role: SoftwareDeveloper){
    name
    role
  }
}
```

Here is the _curl_ request of this query:

```bash
curl 'http://localhost:8088/api/graphql' --data-binary '{"query":"query{\n employees(role: SoftwareDeveloper){\n name\n role\n}\n}"}'
```

And the response:

```json
{
  "data" : {
    "employees" : [
      {
        "name" : "Maria",
        "role" : "SoftwareDeveloper"
      },
      {
        "name" : "Peter",
        "role" : "SoftwareDeveloper"
      }
    ]
  }
}
```

## ZIO gRPC

[ZIO-gRPC](https://scalapb.github.io/zio-grpc/) lets us write purely functional gRPC servers and clients.

### Introduction

Key features of ZIO gRPC:
- **Functional and Type-safe** — Use the power of Functional Programming and the Scala compiler to build robust, correct and fully featured gRPC servers.
- **Support for Streaming** — Use ZIO's feature-rich `ZStream`s to create server-streaming, client-streaming, and bi-directionally streaming RPC endpoints.
- **Highly Concurrent** — Leverage the power of ZIO to build asynchronous clients and servers without deadlocks and race conditions.
- **Resource Safety** — Safely cancel an RPC call by interrupting the effect. Resources on the server will never leak!
- **Scala.js Support** — ZIO gRPC comes with Scala.js support, so we can send RPCs to our service from the browser.

### Installation

First of all we need to add following lines to the `project/plugins.sbt` file:

```scala
addSbtPlugin("com.thesamet" % "sbt-protoc" % "1.0.2")

libraryDependencies +=
  "com.thesamet.scalapb.zio-grpc" %% "zio-grpc-codegen" % "0.5.0"
```

Then in order to use this library, we need should add the following line in our `build.sbt` file:

```scala
PB.targets in Compile := Seq(
  scalapb.gen(grpc = true) -> (sourceManaged in Compile).value / "scalapb",
  scalapb.zio_grpc.ZioCodeGenerator -> (sourceManaged in Compile).value / "scalapb"
)

libraryDependencies ++= Seq(
  "io.grpc" % "grpc-netty" % "1.39.0",
  "com.thesamet.scalapb" %% "scalapb-runtime-grpc" % scalapb.compiler.Version.scalapbVersion
)
```

### Example

In this section, we are going to implement a simple server and client for the following gRPC _proto_ file:

```protobuf
syntax = "proto3";

option java_multiple_files = true;
option java_package = "io.grpc.examples.helloworld";
option java_outer_classname = "HelloWorldProto";
option objc_class_prefix = "HLW";

package helloworld;

// The greeting service definition.
service Greeter {
  rpc SayHello (HelloRequest) returns (HelloReply) {}
}

// The request message containing the user's name.
message HelloRequest {
  string name = 1;
}

// The response message containing the greetings
message HelloReply {
  string message = 1;
}
```

The hello world server would be like this:

```scala
import io.grpc.ServerBuilder
import io.grpc.examples.helloworld.helloworld.ZioHelloworld.ZGreeter
import io.grpc.examples.helloworld.helloworld.{HelloReply, HelloRequest}
import io.grpc.protobuf.services.ProtoReflectionService
import scalapb.zio_grpc.{ServerLayer, ServiceList}
import zio.console.putStrLn
import zio.{ExitCode, URIO, ZEnv, ZIO}

object HelloWorldServer extends zio.App {

  val helloService: ZGreeter[ZEnv, Any] =
    (request: HelloRequest) =>
      putStrLn(s"Got request: $request") *>
        ZIO.succeed(HelloReply(s"Hello, ${request.name}"))


  val myApp = for {
    _ <- putStrLn("Server is running. Press Ctrl-C to stop.")
    _ <- ServerLayer
      .fromServiceList(
        ServerBuilder
          .forPort(9000)
          .addService(ProtoReflectionService.newInstance()),
        ServiceList.add(helloService))
      .build.useForever
  } yield ()

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    myApp.exitCode
}
```

And this is an example of using its client:

```scala
import io.grpc.ManagedChannelBuilder
import io.grpc.examples.helloworld.helloworld.HelloRequest
import io.grpc.examples.helloworld.helloworld.ZioHelloworld.GreeterClient
import scalapb.zio_grpc.ZManagedChannel
import zio.console._
import zio.{ExitCode, URIO}

object HelloWorldClient extends zio.App {
  def myApp =
    for {
      r <- GreeterClient.sayHello(HelloRequest("World"))
      _ <- putStrLn(r.message)
    } yield ()

  val clientLayer =
    GreeterClient.live(
      ZManagedChannel(
        ManagedChannelBuilder.forAddress("localhost", 9000).usePlaintext()
      )
    )

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    myApp.provideCustomLayer(clientLayer).exitCode
}
```

## Distage

[Distage](https://izumi.7mind.io/distage/) is a compile-time safe, transparent, and debuggable Dependency Injection framework for pure FP Scala.

### Introduction

By using _Distage_ we can auto-wire all components of our application.
- We don't need to manually link components together
- We don't need to manually specify the order of allocation and allocation of dependencies. This will be derived automatically from the dependency order.
- We can override any component within the dependency graph.
- It helps us to create different configurations of our components for different use cases.

### Installation

In order to use this library, we need to add the following line in our `build.sbt` file:

```scala
libraryDependencies += "io.7mind.izumi" %% "distage-core" % "1.0.8"
```

### Example

In this example we create a `RandomApp` comprising two `Random` and `Logger` services. By using `ModuleDef` we _bind_ services to their implementations:

```scala mdoc:silent:nest
import distage.{Activation, Injector, ModuleDef, Roots}
import izumi.distage.model.Locator
import izumi.distage.model.definition.Lifecycle
import zio.{ExitCode, Task, UIO, URIO, ZIO}

import java.time.LocalDateTime

trait Random {
  def nextInteger: UIO[Int]
}

final class ScalaRandom extends Random {
  override def nextInteger: UIO[Int] =
    ZIO.effectTotal(scala.util.Random.nextInt())
}

trait Logger {
  def log(name: String): Task[Unit]
}

final class ConsoleLogger extends Logger {
  override def log(line: String): Task[Unit] = {
    val timeStamp = LocalDateTime.now()
    ZIO.effect(println(s"$timeStamp: $line"))
  }
}

final class RandomApp(random: Random, logger: Logger) {
  def run: Task[Unit] = for {
    random <- random.nextInteger
    _ <- logger.log(s"random number generated: $random")
  } yield ()
}

object DistageExample extends zio.App {
  def RandomAppModule: ModuleDef = new ModuleDef {
    make[Random].from[ScalaRandom]
    make[Logger].from[ConsoleLogger]
    make[RandomApp] // `.from` is not required for concrete classes
  }
  
  val resource: Lifecycle[Task, Locator] = Injector[Task]().produce(
    plan = Injector[Task]().plan(
      bindings = RandomAppModule,
      activation = Activation.empty,
      roots = Roots.target[RandomApp]
    )
  )

  val myApp: Task[Unit] = resource.use(locator => locator.get[RandomApp].run)

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    myApp.exitCode
}
```

## LogStage

[LogStage](https://izumi.7mind.io/logstage/) is a zero-cost structural logging framework for Scala & Scala.js.

### Introduction

Some key features of _LogStage_:

1. LogStage extracts structure from ordinary string interpolations in your log messages with zero changes to code.
2. LogStage uses macros to extract log structure, it is faster at runtime than a typical reflective structural logging frameworks
3. Log contexts
4. Console, File, and SLF4J sinks included, File sink supports log rotation,
5. Human-readable output and JSON output included,
6. Method-level logging granularity. Can configure methods com.example.Service.start and com.example.Service.doSomething independently,
7. Slf4J adapters: route legacy Slf4J logs into LogStage router

### Installation

In order to use this library, we need to add the following line in our `build.sbt` file:

```scala
// LogStage core library
libraryDependencies += "io.7mind.izumi" %% "logstage-core" % "1.0.8"
```

There are also some optional modules:

```scala
libraryDependencies ++= Seq(
  // Json output
  "io.7mind.izumi" %% "logstage-rendering-circe" % "1.0.8",
  // Router from Slf4j to LogStage
  "io.7mind.izumi" %% "logstage-adapter-slf4j" % "1.0.8",
  // LogStage integration with DIStage
  "io.7mind.izumi" %% "distage-extension-logstage" % "1.0.8",
  // Router from LogStage to Slf4J
  "io.7mind.izumi" %% "logstage-sink-slf4j " % "1.0.8",
)
```

### Example

Let's try a simple example of using _LogStage_:

```scala mdoc:silent:nest
import izumi.fundamentals.platform.uuid.UUIDGen
import logstage.LogZIO.log
import logstage.{IzLogger, LogIO2, LogZIO}
import zio.{Has, URIO, _}

object LogStageExample extends zio.App {
  val myApp = for {
    _ <- log.info("I'm logging with logstage!")
    userId = UUIDGen.getTimeUUID()
    _ <- log.info(s"Current $userId")
    _ <- log.info("I'm logging within the same fiber!")
    f <- log.info("I'm logging within a new fiber!").fork
    _ <- f.join
  } yield ()

  val loggerLayer: ULayer[Has[LogIO2[IO]]] =
    ZLayer.succeed(LogZIO.withFiberId(IzLogger()))

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    myApp.provideLayer(loggerLayer).exitCode
}
```

The output of this program would be something like this:

```
I 2021-07-26T21:27:35.164 (LogStageExample.scala:8)  …mpty>.LogStageExample.myApp [14:zio-default-async-1] fiberId=Id(1627318654646,1) I'm logging with logstage!
I 2021-07-26T21:27:35.252 (LogStageExample.scala:10)  <.LogStageExample.myApp.8 [14:zio-default-async-1] fiberId=Id(1627318654646,1) Current userId=93546810-ee32-11eb-a393-11bc5b145beb
I 2021-07-26T21:27:35.266 (LogStageExample.scala:11)  <.L.myApp.8.10 [14:zio-default-async-1] fiberId=Id(1627318654646,1) I'm logging within the same fiber!
I 2021-07-26T21:27:35.270 (LogStageExample.scala:12)  <.L.m.8.10.11 [16:zio-default-async-2] fiberId=Id(1627318655269,2) I'm logging within a new fiber!
```

## MUnit ZIO

[MUnit ZIO](https://github.com/poslegm/munit-zio) is an integration library between MUnit and ZIO.

### Introduction

[MUnit](https://scalameta.org/munit/) is a Scala testing library that is implemented as a JUnit runner. It has _actionable errors_, so the test reports are colorfully pretty-printed, stack traces are highlighted, error messages are pointed to the source code location where the failure happened.

The MUnit ZIO enables us to write tests that return `ZIO` values without needing to call any unsafe methods (e.g. `Runtime#unsafeRun`).

### Installation

In order to use this library, we need to add the following lines in our `build.sbt` file:

```scala
libraryDependencies += "org.scalameta" %% "munit" % "0.7.27" % Test
libraryDependencies += "com.github.poslegm" %% "munit-zio" % "0.0.2" % Test
```

If we are using a version of sbt lower than 1.5.0, we will also need to add:

```scala
testFrameworks += new TestFramework("munit.Framework")
```

### Example

Here is a simple MUnit spec that is integrated with the `ZIO` effect:

```scala mdoc:silent:nest
import munit._
import zio._

class SimpleZIOSpec extends ZSuite {
  testZ("1 + 1 = 2") {
    for {
      a <- ZIO(1)
      b <- ZIO(1)
    }
    yield assertEquals(a + b, 2)
  }
}
```

## Rezilience

[Rezilience](https://github.com/svroonland/rezilience) is a ZIO-native library for making resilient distributed systems.

### Introduction

Rezilience is a ZIO-native fault tolerance library with a collection of policies for making asynchronous systems more resilient to failures inspired by Polly, Resilience4J, and Akka. It does not have external library dependencies other than ZIO.

It comprises these policies:
- **CircuitBreaker** — Temporarily prevent trying calls after too many failures
- **RateLimiter** — Limit the rate of calls to a system
- **Bulkhead** — Limit the number of in-flight calls to a system
- **Retry** — Try again after transient failures
- **Timeout** — Interrupt execution if a call does not complete in time

### Installation

In order to use this library, we need to add the following line in our `build.sbt` file:

```scala
libraryDependencies += "com.github.ghostdogpr" %% "caliban" % "1.1.0"
```

### Example

Let's try an example of writing _Circuit Breaker_ policy for calling an external API:

```scala mdoc:silent:nest
import nl.vroste.rezilience.CircuitBreaker.{CircuitBreakerCallError, State}
import nl.vroste.rezilience._
import zio._
import zio.clock.Clock
import zio.console.{Console, putStrLn}
import zio.duration._

object CircuitBreakerExample extends zio.App {

  def callExternalSystem: ZIO[Console, String, Nothing] =
    putStrLn("External service called, but failed!").orDie *>
      ZIO.fail("External service failed!")

  val myApp: ZIO[Console with Clock, Nothing, Unit] =
    CircuitBreaker.withMaxFailures(
      maxFailures = 10,
      resetPolicy = Schedule.exponential(1.second),
      onStateChange = (state: State) =>
        ZIO(println(s"State changed to $state")).orDie
    ).use { cb =>
      for {
        _ <- ZIO.foreach_(1 to 10)(_ => cb(callExternalSystem).either)
        _ <- cb(callExternalSystem).catchAll(errorHandler)
        _ <- ZIO.sleep(2.seconds)
        _ <- cb(callExternalSystem).catchAll(errorHandler)
      } yield ()
    }

  def errorHandler: CircuitBreakerCallError[String] => URIO[Console, Unit] = {
    case CircuitBreaker.CircuitBreakerOpen =>
      putStrLn("Circuit breaker blocked the call to our external system").orDie
    case CircuitBreaker.WrappedError(error) =>
      putStrLn(s"External system threw an exception: $error").orDie
  }
  
  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    myApp.exitCode
}
```

## TranzactIO

[TranzactIO](https://github.com/gaelrenoux/tranzactio) is a ZIO wrapper for some Scala database access libraries, currently for [Doobie](https://github.com/tpolecat/doobie) and [Anorm](https://github.com/playframework/anorm).

### Introduction

Using functional effect database access libraries like _Doobie_ enforces us to use their specialized monads like `ConnectionIO` for _Doobie_. The goal of _TranzactIO_ is to provide seamless integration with these libraries to help us to stay in the `ZIO` world.

### Installation

In order to use this library, we need to add the following line in our `build.sbt` file:

```scala
libraryDependencies += "io.github.gaelrenoux" %% "tranzactio" % "2.1.0"
```

In addition, we need to declare the database access library we are using. For example, for the next example we need to add following dependencies for _Doobie_ integration:

```scala
libraryDependencies += "org.tpolecat" %% "doobie-core" % "0.13.4"
libraryDependencies += "org.tpolecat" %% "doobie-h2"   % "0.13.4"
```

### Example

Let's try an example of simple _Doobie_ program:

```scala mdoc:silent:nest
import doobie.implicits._
import io.github.gaelrenoux.tranzactio.doobie
import io.github.gaelrenoux.tranzactio.doobie.{Connection, Database, TranzactIO, tzio}
import org.h2.jdbcx.JdbcDataSource
import zio.blocking.Blocking
import zio.clock.Clock
import zio.console.{Console, putStrLn}
import zio.{ExitCode, Has, URIO, ZIO, ZLayer, blocking}

import javax.sql.DataSource

object TranzactIOExample extends zio.App {

  val query: ZIO[Connection with Console, Throwable, Unit] = for {
    _ <- PersonQuery.setup
    _ <- PersonQuery.insert(Person("William", "Stewart"))
    _ <- PersonQuery.insert(Person("Michelle", "Streeter"))
    _ <- PersonQuery.insert(Person("Johnathon", "Martinez"))
    users <- PersonQuery.list
    _ <- putStrLn(users.toString)
  } yield ()

  val myApp: ZIO[zio.ZEnv, Throwable, Unit] =
    Database.transactionOrWidenR(query).provideCustomLayer(services.database)

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    myApp.exitCode
}

case class Person(firstName: String, lastName: String)

object PersonQuery {
  def list: TranzactIO[List[Person]] = tzio {
    sql"""SELECT first_name, last_name FROM person""".query[Person].to[List]
  }

  def setup: TranzactIO[Unit] = tzio {
    sql"""
        CREATE TABLE person (
          first_name VARCHAR NOT NULL,
          last_name VARCHAR NOT NULL
        )
        """.update.run.map(_ => ())
  }

  def insert(p: Person): TranzactIO[Unit] = tzio {
    sql"""INSERT INTO person (first_name, last_name) VALUES (${p.firstName}, ${p.lastName})""".update.run
      .map(_ => ())
  }
}

object services {
  val datasource: ZLayer[Blocking, Throwable, Has[DataSource]] =
    ZLayer.fromEffect(
      blocking.effectBlocking {
        val ds = new JdbcDataSource
        ds.setURL(s"jdbc:h2:mem:mydb;DB_CLOSE_DELAY=10")
        ds.setUser("sa")
        ds.setPassword("sa")
        ds
      }
    )

  val database: ZLayer[Any, Throwable, doobie.Database.Database] =
    (Blocking.live >>> datasource ++ Blocking.live ++ Clock.live) >>> Database.fromDatasource
}
```

## ZIO Arrow

[ZIO Arrow](https://github.com/zio-mesh/zio-arrow/) provides the `ZArrow` effect, which is a high-performance composition effect for the ZIO ecosystem.

### Introduction

`ZArrow[E, A, B]` is an effect representing a computation parametrized over the input (`A`), and the output (`B`) that may fail with an `E`. Arrows focus on **composition** and **high-performance computation**. They are like simple functions, but they are lifted into the `ZArrow` context.

`ZArrow` delivers three main capabilities:

- ** High-Performance** — `ZArrow` exploits `JVM` internals to dramatically decrease the number of allocations and dispatches, yielding an unprecedented runtime performance.

- **Abstract interface** — `Arrow` is a more abstract data type, than ZIO Monad. It's more abstract than ZIO Streams. In a nutshell, `ZArrow` allows a function-like interface that can have both different inputs and different outputs.

- **Easy Integration** — `ZArrow` can both input and output `ZIO Monad` and `ZIO Stream`, simplifying application development with different ZIO Effect types.

### Installation

In order to use this library, we need to add the following line in our `build.sbt` file:

```scala
libraryDependencies += "io.github.neurodyne" %% "zio-arrow" % "0.2.1"
```

### Example

In this example we are going to write a repetitive task of reading a number from standard input and then power by 2 and then print the result:

```scala mdoc:silent:nest
import zio.arrow.ZArrow
import zio.arrow.ZArrow._
import zio.console._
import zio.{ExitCode, URIO}

import java.io.IOException

object ArrowExample extends zio.App {

  val isPositive : ZArrow[Nothing, Int, Boolean]     = ZArrow((_: Int) > 0)
  val toStr      : ZArrow[Nothing, Any, String]      = ZArrow((i: Any) => i.toString)
  val toInt      : ZArrow[Nothing, String, Int]      = ZArrow((i: String) => i.toInt)
  val getLine    : ZArrow[IOException, Any, String]  = ZArrow.liftM((_: Any) => getStrLn.provideLayer(Console.live))
  val printStr   : ZArrow[IOException, String, Unit] = ZArrow.liftM((line: String) => putStr(line).provideLayer(Console.live))
  val printLine  : ZArrow[IOException, String, Unit] = ZArrow.liftM((line: String) => putStrLn(line).provideLayer(Console.live))
  val power2     : ZArrow[Nothing, Int, Double]      = ZArrow((i: Int) => Math.pow(i, 2))
  val enterNumber: ZArrow[Nothing, Unit, String]     = ZArrow((_: Unit) => "Enter positive number (-1 to exit): ")
  val goodbye    : ZArrow[Nothing, Any, String]      = ZArrow((_: Any) => "Goodbye!")

  val app: ZArrow[IOException, Unit, Boolean] =
    enterNumber >>> printStr >>> getLine >>> toInt >>>
      ifThenElse(isPositive)(
        power2 >>> toStr >>> printLine >>> ZArrow((_: Any) => true)
      )(
        ZArrow((_: Any) => false)
      )

  val myApp = whileDo(app)(ZArrow(_ => ())) >>> goodbye >>> printLine

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    myApp.run(()).exitCode
}
```

Let's see an example of running this program:

```
Enter positive number (-1 to exit): 25
625.0
Enter positive number (-1 to exit): 8
64.0
Enter positive number (-1 to exit): -1
Goodbye!
```

## ZIO AMQP

[ZIO AMQP](https://github.com/svroonland/zio-amqp) is a ZIO-based AMQP client for Scala.

### Introduction

ZIO AMQP is a ZIO-based wrapper around the RabbitMQ client. It provides a streaming interface to AMQP queues and helps to prevent us from shooting ourselves in the foot with thread-safety issues.

### Installation

In order to use this library, we need to add the following line in our `build.sbt` file:

```scala
libraryDependencies += "nl.vroste" %% "zio-amqp" % "0.2.0"
```

### Example

First, let's create an instance of RabbitMQ:

```
docker run -d --name some-rabbit -p 5672:5672 -p 5673:5673 -p 15672:15672 rabbitmq:3-management
```

Then we need to create `my_exchange` and `my_queue` and bind the queue to the exchange via the RabbitMQ management dashboard (`localhost:15672`).

Now we can run the example below:

```scala mdoc:silent:reset
import nl.vroste.zio.amqp._
import zio._
import zio.blocking._
import zio.clock.Clock
import zio.console._
import zio.duration.durationInt
import zio.random.Random

import java.net.URI

object ZIOAMQPExample extends zio.App {

  val channelM: ZManaged[Blocking, Throwable, Channel] = for {
    connection <- Amqp.connect(URI.create("amqp://localhost:5672"))
    channel <- Amqp.createChannel(connection)
  } yield channel

  val myApp: ZIO[Blocking with Console with Clock with Random, Throwable, Unit] =
    channelM.use { channel =>
      val producer: ZIO[Blocking with Random with Clock, Throwable, Long] =
        zio.random.nextUUID
          .flatMap(uuid =>
            channel.publish("my_exchange", uuid.toString.getBytes)
              .map(_ => ())
          ).schedule(Schedule.spaced(1.seconds))

      val consumer: ZIO[Blocking with Console, Throwable, Unit] = channel
        .consume(queue = "my_queue", consumerTag = "my_consumer")
        .mapM { record =>
          val deliveryTag = record.getEnvelope.getDeliveryTag
          putStrLn(s"Received $deliveryTag: ${new String(record.getBody)}") *>
            channel.ack(deliveryTag)
        }
        .runDrain

      for {
        p <- producer.fork
        c <- consumer.fork
        _ <- p.zip(c).join
      } yield ()
    }

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    myApp.exitCode
}
```
