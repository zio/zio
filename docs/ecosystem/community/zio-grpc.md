---
id: zio-grpc
title: "ZIO gRPC"
---

[ZIO-gRPC](https://scalapb.github.io/zio-grpc/) lets us write purely functional gRPC servers and clients.

## Introduction

Key features of ZIO gRPC:
- **Functional and Type-safe** — Use the power of Functional Programming and the Scala compiler to build robust, correct and fully featured gRPC servers.
- **Support for Streaming** — Use ZIO's feature-rich `ZStream`s to create server-streaming, client-streaming, and bi-directionally streaming RPC endpoints.
- **Highly Concurrent** — Leverage the power of ZIO to build asynchronous clients and servers without deadlocks and race conditions.
- **Resource Safety** — Safely cancel an RPC call by interrupting the effect. Resources on the server will never leak!
- **Scala.js Support** — ZIO gRPC comes with Scala.js support, so we can send RPCs to our service from the browser.

## Installation

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

## Example

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
    myApp.provideCustom(clientLayer).exitCode
}
```

## Resources

- [Functional, Type-safe, Testable Microservices with ZIO gRPC](https://www.youtube.com/watch?v=XTkhxRTH1nE) by Nadav Samet (July 2020)
