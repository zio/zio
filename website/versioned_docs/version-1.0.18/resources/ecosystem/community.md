---
id: community
title: "Community ZIO Libraries"
---

In this section we are going to introduce some of the most important libraries that have first-class ZIO support from the community.

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

```scala
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

```scala
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

```scala
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
libraryDependencies += "nl.vroste" %% "rezilience" % "0.7.0"
```

### Example

Let's try an example of writing _Circuit Breaker_ policy for calling an external API:

```scala
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

## Tamer

[Tamer](https://github.com/laserdisc-io/tamer) is a multi-functional Kafka connector for producing data based on [ZIO Kafka](https://github.com/zio/zio-kafka).

### Introduction

Tamer is a completely customizable source connector that produces to Kafka. It ships with preconfigured modules for SQL, cloud storage and REST API, but you can provide your own functions and Tamer will take care of the rest.

### Installation

Depending on the source you have at hand you can add the correct dependency in your `build.sbt`:

```scala
libraryDependencies += "io.laserdisc" %% "tamer-db"                % "0.16.1"
libraryDependencies += "io.laserdisc" %% "tamer-oci-objectstorage" % "0.16.1"
libraryDependencies += "io.laserdisc" %% "tamer-rest"              % "0.16.1"
libraryDependencies += "io.laserdisc" %% "tamer-s3"                % "0.16.1"
```

### Example

Let's say you have a inventory DB that's compatible with [Doobie](https://github.com/tpolecat/doobie), you can get all of your items with just a few lines of code:

```scala
import tamer._
import tamer.db._

import doobie.implicits.legacy.instant._
import doobie.syntax.string._
import zio._
import zio.duration._
import zio.json._

import java.time.Instant

case class Row(id: String, name: String, description: Option[String], modifiedAt: Instant)
    extends tamer.db.Timestamped(modifiedAt)

object Row {
  implicit val rowJsonCodec = DeriveJsonCodec.gen[Row]
}

object DatabaseSimple extends zio.App {
  // Here we'll go with zio-json codec, you can use avro, circe and jsoniter
  // out-of-the box or plug yours!
  implicit val stateKeyJsonCodec = DeriveJsonCodec.gen[tamer.Tamer.StateKey]
  implicit val windowJsonCodec = DeriveJsonCodec.gen[tamer.db.Window]

  val program: RIO[ZEnv, Unit] = tamer.db.DbSetup
    .tumbling(window =>
      sql"""SELECT id, name, description, modified_at 
           |FROM users 
           |WHERE modified_at > ${window.from} AND modified_at <= ${window.to}""".stripMargin
        .query[Row]
    )(
      recordKey = (_, v) => v.id,
      from = Instant.parse("2020-01-01T00:00:00.00Z"),
      tumblingStep = 5.days
    )
    .runWith(dbLayerFromEnvironment ++ tamer.kafkaConfigFromEnvironment)

  override final def run(args: List[String]): URIO[ZEnv, ExitCode] =
    program.exitCode

  // If you have other codecs like circe in the classpath you have to disambiguate
  implicit lazy val stateKeyCodec: Codec[Tamer.StateKey] = Codec.optionalZioJsonCodec
  implicit lazy val windowCodec: Codec[tamer.db.Window] = Codec.optionalZioJsonCodec
  implicit lazy val stringCodec: Codec[String] = Codec.optionalZioJsonCodec
}
```
See full example [on the GitHub repo](https://github.com/laserdisc-io/tamer/blob/4e1a7646fb44041648d9aa3ba089decb81ebe487/example/src/main/scala/tamer/db/DatabaseSimple.scala)

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

```scala
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

```scala
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

```scala
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

## ZIO AWS

[ZIO AWS](https://github.com/vigoo/zio-aws) is a low-level AWS wrapper for ZIO for all the AWS services using the AWS Java SDK v2.

### Introduction

The goal is to have access to all AWS functionality for cases when only simple, direct access is needed from a ZIO application, or to be used as a building block for higher-level wrappers around specific services.

Key features of ZIO AWS:

- Common configuration layer
- ZIO module layer per AWS service
- Wrapper for all operations on all services
- HTTP service implementations for functional Scala HTTP libraries, injected through ZIO’s module system
- ZStream wrapper around paginated operations
- Service-specific extra configuration
- More idiomatic Scala request and response types wrapping the Java classes

### Installation

There are tones of artifacts [published](https://vigoo.github.io/zio-aws/docs/artifacts.html) for each AWS service. We can pick whichever services we need.

### Example

The following example uses the _ElasticBeanstalk_ and _EC2_ APIs:

```scala
libraryDependencies += "io.github.vigoo" %% "zio-aws-core"             % "3.17.8.4",
libraryDependencies += "io.github.vigoo" %% "zio-aws-ec2"              % "3.17.8.4",
libraryDependencies += "io.github.vigoo" %% "zio-aws-elasticbeanstalk" % "3.17.8.4",
libraryDependencies += "io.github.vigoo" %% "zio-aws-netty"            % "3.17.8.4"
```

And here is the example code:

```scala
import io.github.vigoo.zioaws.core.AwsError
import io.github.vigoo.zioaws.ec2.Ec2
import io.github.vigoo.zioaws.ec2.model._
import io.github.vigoo.zioaws.elasticbeanstalk.ElasticBeanstalk
import io.github.vigoo.zioaws.elasticbeanstalk.model._
import io.github.vigoo.zioaws.{core, ec2, elasticbeanstalk, netty}
import zio.console._
import zio.stream._
import zio.{console, _}

object ZIOAWSExample extends zio.App {
  val program: ZIO[Console with Ec2 with ElasticBeanstalk, AwsError, Unit] =
    for {
      appsResult <- elasticbeanstalk.describeApplications(
        DescribeApplicationsRequest(applicationNames = Some(List("my-service")))
      )
      app <- appsResult.applications.map(_.headOption)
      _ <- app match {
        case Some(appDescription) =>
          for {
            applicationName <- appDescription.applicationName
            _ <- console.putStrLn(
              s"Got application description for $applicationName"
            ).ignore

            envStream = elasticbeanstalk.describeEnvironments(
              DescribeEnvironmentsRequest(applicationName =
                Some(applicationName)
              )
            )

            _ <- envStream.run(Sink.foreach { env =>
              env.environmentName.flatMap { environmentName =>
                (for {
                  environmentId <- env.environmentId
                  _ <- console.putStrLn(
                    s"Getting the EB resources of $environmentName"
                  ).ignore

                  resourcesResult <-
                    elasticbeanstalk.describeEnvironmentResources(
                      DescribeEnvironmentResourcesRequest(environmentId =
                        Some(environmentId)
                      )
                    )
                  resources <- resourcesResult.environmentResources
                  _ <- console.putStrLn(
                    s"Getting the EC2 instances in $environmentName"
                  ).ignore
                  instances <- resources.instances
                  instanceIds <- ZIO.foreach(instances)(_.id)
                  _ <- console.putStrLn(
                    s"Instance IDs are ${instanceIds.mkString(", ")}"
                  ).ignore

                  reservationsStream = ec2.describeInstances(
                    DescribeInstancesRequest(instanceIds = Some(instanceIds))
                  )
                  _ <- reservationsStream.run(Sink.foreach { reservation =>
                    reservation.instances
                      .flatMap { instances =>
                        ZIO.foreach(instances) { instance =>
                          for {
                            id <- instance.instanceId
                            typ <- instance.instanceType
                            launchTime <- instance.launchTime
                            _ <- console.putStrLn(s"  instance $id:").ignore
                            _ <- console.putStrLn(s"    type: $typ").ignore
                            _ <- console.putStrLn(
                              s"    launched at: $launchTime"
                            ).ignore
                          } yield ()
                        }
                      }
                  })
                } yield ()).catchAll { error =>
                  console.putStrLnErr(
                    s"Failed to get info for $environmentName: $error"
                  ).ignore
                }
              }
            })
          } yield ()
        case None =>
          ZIO.unit
      }
    } yield ()

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = { //
    val httpClient = netty.default
    val awsConfig  = httpClient >>> core.config.default
    val aws        = awsConfig >>> (ec2.live ++ elasticbeanstalk.live)

    program
      .provideCustomLayer(aws)
      .either
      .flatMap {
        case Left(error) =>
          console.putStrErr(s"AWS error: $error").ignore.as(ExitCode.failure)
        case Right(_) =>
          ZIO.unit.as(ExitCode.success)
      }
  }
}
```

## ZIO AWS S3

[ZIO AWS S3](https://github.com/zio-mesh/zio-aws-s3) is a ZIO integration with AWS S3 SDK.

### Introduction

This project aims to ease ZIO integration with AWS S3, providing a clean, simple and efficient API.

### Installation

In order to use this library, we need to add the following line in our `build.sbt` file:

```scala
libraryDependencies += "io.github.neurodyne" %% "zio-aws-s3" % "0.4.13"
```

### Example

```scala
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3AsyncClient
import zio.{ExitCode, URIO, _}
import zio_aws_s3.AwsApp.AwsLink
import zio_aws_s3.{AwsAgent, AwsApp}

import scala.jdk.CollectionConverters._

object ZIOAWSS3Example extends zio.App {
  val BUCKET = "<bucket name>"

  val awsEnv: ZLayer[S3AsyncClient, Throwable, AwsLink] =
    AwsApp.ExtDeps.live >>> AwsApp.AwsLink.live

  val app: ZIO[Any, Throwable, Unit] = for {
    s3 <- AwsAgent.createClient(Region.US_WEST_2, "<endpoint>")
    response <- AwsApp.listBuckets().provideLayer(awsEnv).provide(s3)
    buckets <- Task(response.buckets.asScala.toList.map(_.name))
    _ = buckets.foreach(println)
  } yield ()

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    app.exitCode
}
```

## ZIO HTTP

[ZIO HTTP](https://github.com/dream11/zio-http) is a scala library to write HTTP applications.

### Introduction

ZIO HTTP is a Scala library for building HTTP applications. It is powered by ZIO and netty and aims at being the defacto solution for writing, highly scalable, and performant web applications using idiomatic scala.

### Installation

In order to use this library, we need to add the following lines in our `build.sbt` file:

```scala
libraryDependencies += "io.d11" %% "zhttp"      % "1.0.0.0-RC13"
libraryDependencies += "io.d11" %% "zhttp-test" % "1.0.0.0-RC13" % Test
```

### Example

```scala
import zhttp.http._
import zhttp.service.Server
import zio._

object ZIOHTTPExample extends zio.App {

  // Create HTTP route
  val app: HttpApp[Any, Nothing] = HttpApp.collect {
    case Method.GET -> Root / "text" => Response.text("Hello World!")
    case Method.GET -> Root / "json" => Response.jsonString("""{"greetings": "Hello World!"}""")
  }

  // Run it like any simple app
  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    Server.start(8090, app.silent).exitCode
}
```

## ZIO K8s

[ZIO K8S](https://github.com/coralogix/zio-k8s) is an idiomatic ZIO client for the Kubernetes API.

### Introduction

This library provides a client for the full Kubernetes API as well as providing code generator support for custom resources and higher-level concepts such as operators, taking full advantage of the ZIO library.

Using ZIO K8S we can talk to the Kubernetes API that helps us to:
- Write an operator for our custom resource types
- Schedule some jobs in our cluster
- Query the cluster for monitoring purposes
- Write some cluster management tools

### Installation

In order to use this library, we need to add the following line in our `build.sbt` file:

```scala
libraryDependencies += "com.coralogix" %% "zio-k8s-client" % "1.3.3"
```

And then we need to choose the proper sttp backend:

```scala
"com.softwaremill.sttp.client3" %% "httpclient-backend-zio" % "3.1.1",
"com.softwaremill.sttp.client3" %% "slf4j-backend"          % "3.1.1"
```

Or the asynchronous version:

```scala
"com.softwaremill.sttp.client3" %% "async-http-client-backend-zio" % "3.1.1"
"com.softwaremill.sttp.client3" %% "slf4j-backend"                 % "3.1.1"
```

### Example

This is an example of printing the tail logs of a container:

```scala
import com.coralogix.zio.k8s.client.K8sFailure
import com.coralogix.zio.k8s.client.config.httpclient._
import com.coralogix.zio.k8s.client.model.K8sNamespace
import com.coralogix.zio.k8s.client.v1.pods
import com.coralogix.zio.k8s.client.v1.pods.Pods
import zio._
import zio.console.Console

import scala.languageFeature.implicitConversions

object ZIOK8sLogsExample extends zio.App {
  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = (args match {
    case List(podName) => tailLogs(podName, None)
    case List(podName, containerName) => tailLogs(podName, Some(containerName))
    case _ => console.putStrLnErr("Usage: <podname> [containername]")
  })
    .provideCustomLayer(k8sDefault >>> Pods.live)
    .exitCode

  def tailLogs(podName: String,
               containerName: Option[String]
              ): ZIO[Pods with Console, K8sFailure, Unit] =
    pods
      .getLog(
        name = podName,
        namespace = K8sNamespace.default,
        container = containerName,
        follow = Some(true)
      )
      .tap { line: String =>
        console.putStrLn(line).ignore
      }
      .runDrain
}
```

## ZIO Kinesis

[ZIO Kinesis](https://github.com/svroonland/zio-kinesis) is a ZIO-based AWS Kinesis client for Scala.

### Introduction

ZIO Kinesis is an interface to Amazon Kinesis Data Streams for consuming and producing data. This library is built on top of [ZIO AWS](https://github.com/vigoo/zio-aws), a library of automatically generated ZIO wrappers around AWS SDK methods.

### Installation

In order to use this library, we need to add the following line in our `build.sbt` file:

  ```scala
libraryDependencies += "nl.vroste" %% "zio-kinesis" % "0.20.0"
```

### Example

This is an example of consuming a stream from Amazon Kinesis:

```scala mdoc:silent:reset
import nl.vroste.zio.kinesis.client.serde.Serde
import nl.vroste.zio.kinesis.client.zionative.Consumer
import zio.clock.Clock
import zio.console.{Console, putStrLn}
import zio.duration._
import zio.logging.Logging
import zio.{ExitCode, URIO, _}

object ZIOKinesisConsumerExample extends zio.App {
  val loggingLayer: ZLayer[Any, Nothing, Logging] =
    (Console.live ++ Clock.live) >>>
      Logging.console() >>>
      Logging.withRootLoggerName(getClass.getName)

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    Consumer
      .consumeWith(
        streamName = "my-stream",
        applicationName = "my-application",
        deserializer = Serde.asciiString,
        workerIdentifier = "worker1",
        checkpointBatchSize = 1000L,
        checkpointDuration = 5.minutes
      )(record => putStrLn(s"Processing record $record"))
      .provideCustomLayer(Consumer.defaultEnvironment ++ loggingLayer)
      .exitCode
}
```

## ZIO Pulsar

[ZIO Pulsar](https://github.com/apache/pulsar) is the _Apache Pulsar_ client for Scala with ZIO and ZIO Streams integration.

### Introduction

ZIO Pulsar is a purely functional Scala wrapper over the official Pulsar client. Some key features of this library:

- **Type-safe** — Utilizes Scala type system to reduce runtime exceptions present in the official Java client.
- **Streaming-enabled** — Naturally integrates with ZIO Streams.
- **ZIO integrated** — Uses common ZIO primitives like ZIO effect and `ZManaged` to reduce the boilerplate and increase expressiveness.

### Installation

In order to use this library, we need to add the following line in our `build.sbt` file for _Scala 3_:

```scala
libraryDependencies += "com.github.jczuchnowski" %% "zio-pulsar" % "0.1"
```

### Example

First of all we need to create an instance of _Apache Pulsar_ and run that:

```
docker run -it \
  -p 6650:6650 \
  -p 8080:8080 \
  --mount source=pulsardata,target=/pulsar/data \
  --mount source=pulsarconf,target=/pulsar/conf \
  --network pulsar \
  apachepulsar/pulsar:2.7.0 \
  bin/pulsar standalone
```

Now we can run the following example:

```scala
import org.apache.pulsar.client.api.{PulsarClientException, Schema}
import zio._
import zio.blocking._
import zio.clock._
import zio.console._
import zio.pulsar._
import zio.stream._

import java.nio.charset.StandardCharsets

object StreamingExample extends zio.App {
  val topic = "my-topic"

  val producer: ZManaged[Has[PulsarClient], PulsarClientException, Unit] =
    for {
      sink <- Producer.make(topic, Schema.STRING).map(_.asSink)
      _ <- Stream.fromIterable(0 to 100).map(i => s"Message $i").run(sink).toManaged_
    } yield ()

  val consumer: ZManaged[Has[PulsarClient] with Blocking with Console, PulsarClientException, Unit] =
    for {
      builder <- ConsumerBuilder.make(Schema.STRING).toManaged_
      consumer <- builder
        .subscription(Subscription("my-subscription", SubscriptionType.Exclusive))
        .topic(topic)
        .build
      _ <- consumer.receiveStream.take(10).foreach { e =>
        consumer.acknowledge(e.getMessageId) *>
          putStrLn(new String(e.getData, StandardCharsets.UTF_8)).orDie
      }.toManaged_
    } yield ()

  val myApp =
    for {
      f <- consumer.fork
      _ <- producer
      _ <- f.join.toManaged_
    } yield ()

  def run(args: List[String]): URIO[ZEnv, ExitCode] =
    myApp
      .provideCustomLayer(
        (Console.live ++ Clock.live) >+>
          PulsarClient.live("localhost", 6650)
      ).useNow.exitCode
}
```

## ZIO Saga

[ZIO Saga](https://github.com/VladKopanev/zio-saga) is a distributed transaction manager using Saga Pattern.

### Introduction

Sometimes when we are architecting the business logic using microservice architecture we need distributed transactions that are across services. 

The _Saga Pattern_ lets us manage distributed transactions by sequencing local transactions with their corresponding compensating actions. A _Saga Pattern_ runs all operations. In the case of failure, it guarantees us to undo all previous works by running the compensating actions.

ZIO Saga allows us to compose our requests and compensating actions from the Saga pattern in one transaction with no boilerplate.

ZIO Saga adds a simple abstraction called `Saga` that takes the responsibility of proper composition of effects and associated compensating actions.

### Installation

In order to use this library, we need to add the following line in our `build.sbt` file:

```scala
libraryDependencies += "com.vladkopanev" %% "zio-saga-core" % "0.4.0"
```

### Example

In the following example, all API requests have a compensating action. We compose all them together and then run the whole as one transaction:


```scala
import com.vladkopanev.zio.saga.Saga
import zio.{IO, UIO, URIO, ZIO}

import com.vladkopanev.zio.saga.Saga._

val transaction: Saga[Any, String, Unit] =
  for {
    _ <- bookHotel compensate cancelHotel
    _ <- bookTaxi compensate cancelTaxi
    _ <- bookFlight compensate cancelFlight
  } yield ()

val myApp: ZIO[Any, String, Unit] = transaction.transact
```

## ZIO Slick Interop

[ZIO Slick Interop](https://github.com/ScalaConsultants/zio-slick-interop) is a small library, that provides interop between Slick and ZIO.

### Installation

In order to use this library, we need to add the following line in our `build.sbt` file:

```scala
libraryDependencies += "io.scalac" %% "zio-slick-interop" % "0.4.0"
```

### Example

To run this example we should also add the _HikariCP integration for Slick_ in our `build.sbt` file:

```scala
libraryDependencies += "com.typesafe.slick" %% "slick-hikaricp" % "3.3.3"
```

Here is a full working example of creating database-agnostic Slick repository:

```scala
import com.typesafe.config.ConfigFactory
import slick.interop.zio.DatabaseProvider
import slick.interop.zio.syntax._
import slick.jdbc.H2Profile.api._
import slick.jdbc.JdbcProfile
import zio.console.Console
import zio.interop.console.cats.putStrLn
import zio.{ExitCode, Has, IO, URIO, ZIO, ZLayer}

import scala.jdk.CollectionConverters._

case class Item(id: Long, name: String)

trait ItemRepository {
  def add(name: String): IO[Throwable, Long]

  def getById(id: Long): IO[Throwable, Option[Item]]

  def upsert(name: String): IO[Throwable, Long]
}

object ItemsTable {
  class Items(tag: Tag) extends Table[Item](
    _tableTag = tag,
    _tableName = "ITEMS"
  ) {
    def id = column[Long]("ID", O.PrimaryKey, O.AutoInc)

    def name = column[String]("NAME")

    def * = (id, name) <> ((Item.apply _).tupled, Item.unapply _)
  }

  val table = TableQuery[ItemsTable.Items]
}

object SlickItemRepository {
  val live: ZLayer[Has[DatabaseProvider], Throwable, Has[ItemRepository]] =
    ZLayer.fromServiceM { db =>
      db.profile.flatMap { profile =>
        import profile.api._

        val initialize = ZIO.fromDBIO(ItemsTable.table.schema.createIfNotExists)

        val repository = new ItemRepository {
          private val items = ItemsTable.table

          def add(name: String): IO[Throwable, Long] =
            ZIO
              .fromDBIO((items returning items.map(_.id)) += Item(0L, name))
              .provide(Has(db))

          def getById(id: Long): IO[Throwable, Option[Item]] = {
            val query = items.filter(_.id === id).result

            ZIO.fromDBIO(query).map(_.headOption).provide(Has(db))
          }

          def upsert(name: String): IO[Throwable, Long] =
            ZIO
              .fromDBIO { implicit ec =>
                (for {
                  itemOpt <- items.filter(_.name === name).result.headOption
                  id <- itemOpt.fold[DBIOAction[Long, NoStream, Effect.Write]](
                    (items returning items.map(_.id)) += Item(0L, name)
                  )(item => (items.map(_.name) update name).map(_ => item.id))
                } yield id).transactionally
              }
              .provide(Has(db))
        }

        initialize.as(repository).provide(Has(db))
      }
    }
}


object Main extends zio.App {

  private val config = ConfigFactory.parseMap(
    Map(
      "url" -> "jdbc:h2:mem:test1;DB_CLOSE_DELAY=-1",
      "driver" -> "org.h2.Driver",
      "connectionPool" -> "disabled"
    ).asJava
  )

  private val env: ZLayer[Any, Throwable, Has[ItemRepository]] =
    (ZLayer.succeed(config) ++ ZLayer.succeed[JdbcProfile](
      slick.jdbc.H2Profile
    )) >>> DatabaseProvider.live >>> SlickItemRepository.live

  val myApp: ZIO[Console with Has[ItemRepository], Throwable, Unit] =
    for {
      repo <- ZIO.service[ItemRepository]
      aId1 <- repo.add("A")
      _ <- repo.add("B")
      a <- repo.getById(1L)
      b <- repo.getById(2L)
      aId2 <- repo.upsert("A")
      _ <- putStrLn(s"$aId1 == $aId2")
      _ <- putStrLn(s"A item: $a")
      _ <- putStrLn(s"B item: $b")
    } yield ()

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    myApp.provideCustomLayer(env).exitCode
}
```

## ZIO Test Akka HTTP

[ZIO Test Akka HTTP](https://github.com/senia-psm/zio-test-akka-http) is an Akka-HTTP Route TestKit for zio-test.

### Installation

In order to use this library, we need to add the following line in our `build.sbt` file:

```scala
libraryDependencies += "info.senia" %% "zio-test-akka-http" % "1.0.2"
```

### Example

An example of writing Akka HTTP Route test spec:

```scala
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.server.Directives.complete
import zio.test.Assertion._
import zio.test._
import zio.test.akkahttp.DefaultAkkaRunnableSpec

object MySpec extends DefaultAkkaRunnableSpec {
  def spec =
    suite("MySpec")(
      testM("my test") {
        assertM(Get() ~> complete(HttpResponse()))(
          handled(
            response(equalTo(HttpResponse()))
          )
        )
      }
    )
}
```

## ZparkIO

[ZParkIO](https://github.com/leobenkel/ZparkIO) is a boilerplate framework to use _Spark_ and _ZIO_ together.

### Introduction

_ZparkIO_ enables us to:
- Wrap asynchronous and synchronous operations smoothly. So everything is wrapped in ZIO.
- Have ZIO features in our spark jobs, like forking and joining fibers, parallelizing tasks, retrying, and timing-out.
- Make our spark job much easier to debug

### Installation

In order to use this library, we need to add the following line in our `build.sbt` file:

```scala
libraryDependencies += "com.leobenkel" %% "zparkio" % "[SPARK_VERSION]_[VERSION]"
```

### Example

Using _ZparkIO_ we can write jobs like the following example:

```scala
import com.leobenkel.zparkio.Services.SparkModule
import com.leobenkel.zparkio.Services.SparkModule.SparkModule
import com.leobenkel.zparkio.ZparkioApplicationTimeoutException
import org.apache.spark.sql.DataFrame
import zio.clock.Clock
import zio.duration.durationInt
import zio.{Schedule, Task, ZIO}

def readParquetFile[A](parquetPath: String): ZIO[Clock with SparkModule, Throwable, DataFrame] =
  for {
    spark <- SparkModule()
    dataset <- Task(spark.read.parquet(parquetPath))
      .retry(
        Schedule.recurs(3) && Schedule.exponential(2.seconds)
      )
      .timeoutFail(ZparkioApplicationTimeoutException())(5.minutes)
  } yield dataset
```

## Quill

[Quil](https://github.com/getquill/quill) is a Compile-time Language Integrated Queries for Scala.

### Introduction

Quill allows us to create SQL out of a Scala code during the **compile-time**. It provides the _Quoted Domain Specific Language (QDSL)_ to express queries in Scala and execute them in a target language.

- **Boilerplate-free mapping** — The database schema is mapped using simple case classes.
- **Quoted DSL** — Queries are defined inside a quote block. Quill parses each quoted block of code (quotation) at compile-time and translates them to an internal Abstract Syntax Tree (AST)
- **Compile-time query generation** — The `ctx.run` call reads the quotation’s AST and translates it to the target language at compile-time, emitting the query string as a compilation message. As the query string is known at compile-time, the runtime overhead is very low and similar to using the database driver directly.
- **Compile-time query validation** — If configured, the query is verified against the database at compile-time and the compilation fails if it is not valid. The query validation does not alter the database state.

### Installation

In order to use this library with ZIO, we need to add the following lines in our `build.sbt` file:

```scala
// Provides Quill contexts for ZIO.
libraryDependencies += "io.getquill" %% "quill-zio" % "3.9.0"

// Provides Quill context that execute MySQL, PostgreSQL, SQLite, H2, SQL Server and Oracle queries inside of ZIO.
libraryDependencies += "io.getquill" %% "quill-jdbc-zio" % "3.9.0" 

// Provides Quill context that executes Cassandra queries inside of ZIO.
libraryDependencies += "io.getquill" %% "quill-cassandra-zio" % "3.9.0"
```

### Example

First, to run this example, we should create the `Person` table at the database initialization. Let's put the following lines into the `h2-schema.sql` file at the`src/main/resources` path:

```sql
CREATE TABLE IF NOT EXISTS Person(
    name VARCHAR(255),
    age int
);
```

In this example, we use in-memory database as our data source. So we just put these lines into the `application.conf` at the `src/main/resources` path:

```hocon
myH2DB {
  dataSourceClassName = org.h2.jdbcx.JdbcDataSource
  dataSource {
    url = "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1;INIT=RUNSCRIPT FROM 'classpath:h2-schema.sql'"
    user = sa
  }
}
```

Now we are ready to run the example below:

```scala
import io.getquill._
import io.getquill.context.ZioJdbc._
import zio.console.{Console, putStrLn}
import zio.{ExitCode, Has, URIO, ZIO}

import java.io.Closeable
import javax.sql

object QuillZIOExample extends zio.App {
  val ctx = new H2ZioJdbcContext(Literal)

  import ctx._

  case class Person(name: String, age: Int)

  val myApp: ZIO[Console with Has[sql.DataSource with Closeable], Exception, Unit] =
    for {
      _ <- ctx.run(
        quote {
          liftQuery(List(Person("Alex", 25), Person("Sarah", 23)))
            .foreach(r =>
              query[Person].insert(r)
            )
        }
      ).onDS
      result <- ctx.run(
        quote(query[Person].filter(p => p.name == "Sarah"))
      ).onDS
      _ <- putStrLn(result.toString)
    } yield ()

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    myApp
      .provideCustomLayer(DataSourceLayer.fromPrefix("myH2DB"))
      .exitCode
}
```
