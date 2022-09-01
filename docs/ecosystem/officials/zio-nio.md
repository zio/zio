---
id: zio-nio
title: "ZIO NIO"
---

[ZIO NIO](https://zio.github.io/zio-nio/) is a small, unopinionated ZIO interface to NIO.

## Introduction

In Java, there are two packages for I/O operations:

1. Java IO (`java.io`)
    - Standard Java IO API
    - Introduced since Java 1.0
    - Stream-based API
    - **Blocking I/O operation**

2. Java NIO (`java.nio`)
    - Introduced since Java 1.4
    - NIO means _New IO_, an alternative to the standard Java IO API
    - It can operate in a **non-blocking mode** if possible
    - Buffer-based API

The [Java NIO](https://docs.oracle.com/javase/8/docs/api/java/nio/package-summary.html) is an alternative to the Java IO API. Because it supports non-blocking IO, it can be more performant in concurrent environments like web services.

## Installation

ZIO NIO is a ZIO wrapper on Java NIO. It comes in two flavors:

- **`zio.nio.core`** — a small and unopionanted ZIO interface to NIO that just wraps NIO API in ZIO effects,
- **`zio.nio`** — an opinionated interface with deeper ZIO integration that provides more type and resource safety.

In order to use this library, we need to add one of the following lines in our `build.sbt` file:

```scala
libraryDependencies += "dev.zio" %% "zio-nio-core" % "1.0.0-RC11"
libraryDependencies += "dev.zio" %% "zio-nio"      % "1.0.0-RC11" 
```

## Example

Let's try writing a simple server using `zio-nio` module:

```scala
import zio._
import zio.console._
import zio.nio.channels._
import zio.nio.core._
import zio.stream._

object ZIONIOServerExample extends zio.App {
  val myApp =
    AsynchronousServerSocketChannel()
      .use(socket =>
        for {
          addr <- InetSocketAddress.hostName("localhost", 8080)
          _ <- socket.bindTo(addr)
          _ <- putStrLn(s"Waiting for incoming connections on $addr endpoint").orDie
          _ <- ZStream
            .repeatEffect(socket.accept.preallocate)
            .map(_.withEarlyRelease)
            .mapMPar(16) {
              _.use { case (closeConn, channel) =>
                for {
                  _ <- putStrLn("Received connection").orDie
                  data <- ZStream
                    .repeatEffectOption(
                      channel.readChunk(64).eofCheck.orElseFail(None)
                    )
                    .flattenChunks
                    .transduce(ZTransducer.utf8Decode)
                    .run(Sink.foldLeft("")(_ + _))
                  _ <- closeConn
                  _ <- putStrLn(s"Request Received:\n${data.mkString}").orDie
                } yield ()
              }
            }.runDrain
        } yield ()
      ).orDie
   
  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    myApp.exitCode
}
```

Now we can send our requests to the server using _curl_ command:

```
curl -X POST localhost:8080 -d "Hello, ZIO NIO!"
```
