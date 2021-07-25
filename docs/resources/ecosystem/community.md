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
- [ZIO Arrow](https://github.com/Neurodyne/zio-arrow) — Haskell Arrow meets ZIO. A deep composition and high performance applications
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
