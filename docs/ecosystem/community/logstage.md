---
id: logstage
title: "LogStage"
---

[LogStage](https://izumi.7mind.io/logstage/) is a zero-cost structural logging framework for Scala & Scala.js.

## Introduction

Some key features of _LogStage_:

1. LogStage extracts structure from ordinary string interpolations in your log messages with zero changes to code.
2. LogStage uses macros to extract log structure, it is faster at runtime than a typical reflective structural logging frameworks
3. Log contexts
4. Console, File, and SLF4J sinks included, File sink supports log rotation,
5. Human-readable output and JSON output included,
6. Method-level logging granularity. Can configure methods com.example.Service.start and com.example.Service.doSomething independently,
7. Slf4J adapters: route legacy Slf4J logs into LogStage router

## Installation

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

## Example

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
    myApp.provide(loggerLayer).exitCode
}
```

The output of this program would be something like this:

```
I 2021-07-26T21:27:35.164 (LogStageExample.scala:8)  …mpty>.LogStageExample.myApp [14:zio-default-async-1] fiberId=Id(1627318654646,1) I'm logging with logstage!
I 2021-07-26T21:27:35.252 (LogStageExample.scala:10)  <.LogStageExample.myApp.8 [14:zio-default-async-1] fiberId=Id(1627318654646,1) Current userId=93546810-ee32-11eb-a393-11bc5b145beb
I 2021-07-26T21:27:35.266 (LogStageExample.scala:11)  <.L.myApp.8.10 [14:zio-default-async-1] fiberId=Id(1627318654646,1) I'm logging within the same fiber!
I 2021-07-26T21:27:35.270 (LogStageExample.scala:12)  <.L.m.8.10.11 [16:zio-default-async-2] fiberId=Id(1627318655269,2) I'm logging within a new fiber!
```
