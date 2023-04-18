---
id: zio-temporal
title: "ZIO Temporal"
---

[ZIO Temporal](https://zio-temporal.vhonta.dev/) is a ZIO library for Temporal, a microservice workflow orchestration platform.

## Introduction

ZIO Temporal is a ZIO library based on the Temporal Java-SDK. ZIO Temporal brings first-class Scala & ZIO support with additional compile-time checks that Java SDK lacks.

[Temporal](https://temporal.io/) platform helps to eliminate complex error or retry logic, avoid callbacks, and ensure that every workflow you start, completes. Temporal delivers durable execution for your services and applications.

## Installation

In order to use this library, we need to add the following dependency:

```scala
libraryDependencies += "dev.vhonta" %% "zio-temporal-core" % "0.1.0-RC6"
```

## Example

Here we have one ZIO app with two "modules", one which is the worker that executes the workflow activity and the other is the Client which sends the request to the Temporal platform. To run the sample, it's required to download [Temporal cli](https://github.com/temporalio/cli) (recommended for development).


Run the Temporal server on one shell:

```sh
temporal server start-dev --ip 0.0.0.0 --db-filename /tmp/temporal.db
```

Now we can run the sample application which is based on Temporal Java SDK docs using [scala-cli](https://scala-cli.virtuslab.org):

```scala
//> using scala "3.3.0-RC3" // We use Scala 3.3 here just to leverage SIP-44 (FewerBraces)

//> using lib "dev.zio::zio:2.0.13"
//> using lib "dev.vhonta::zio-temporal-core:0.1.0-RC6"
//> using lib "dev.zio::zio-logging:2.1.11"
//> using lib "dev.zio::zio-logging-slf4j2-bridge:2.1.11"

import java.util.UUID

import zio.*
import zio.temporal.*
import zio.temporal.worker.*
import zio.temporal.workflow.*
import zio.logging.*

// This is our workflow interface
@workflowInterface
trait EchoWorkflow:

  @workflowMethod
  def echo(str: String): String

// Workflow implementation
class EchoWorkflowImpl extends EchoWorkflow:
  override def echo(str: String): String =
    ZIO.logInfo(s"Worker: Received \"$str\"")
    s"ACK: $str"

val echoQueue = "echo-queue"

// Worker implementation
object WorkerModule:
  val worker: URLayer[ZWorkerFactory, Unit] = ZLayer.fromZIO:
    ZIO.serviceWithZIO[ZWorkerFactory]: workerFactory =>
      for
        _      <- ZIO.logInfo("Started sample-worker")
        worker <- workerFactory.newWorker(echoQueue)
        _       = worker.addWorkflow[EchoWorkflow].from(new EchoWorkflowImpl)
      yield ()

// Client implementation
object Client:
  val workflowStubZIO = ZIO.serviceWithZIO[ZWorkflowClient]: workflowClient =>
    workflowClient
      .newWorkflowStub[EchoWorkflow]
      .withTaskQueue(echoQueue)
      .withWorkflowId(s"echo-${UUID.randomUUID().toString}")
      .withWorkflowRunTimeout(2.seconds)
      .withRetryOptions(ZRetryOptions.default.withMaximumAttempts(3))
      .build

  def workflowResultZIO(msg: String) =
    for
      echoWorkflow <- workflowStubZIO
      _            <- ZIO.logInfo(s"Will submit message \"$msg\"")
      result       <- ZWorkflowStub.execute(echoWorkflow.echo(msg))
    yield result

// Main Application
object Main extends ZIOAppDefault:
  val logFilter: LogFilter[String] = LogFilter.logLevelByName(
    LogLevel.Debug,
    "SLF4J-LOGGER"                                -> LogLevel.Warning,
    "io.grpc.netty"                               -> LogLevel.Warning,
    "io.grpc.netty.shaded.io.netty.util.internal" -> LogLevel.Warning,
    "io.netty"                                    -> LogLevel.Warning,
    "io.temporal"                                 -> LogLevel.Error,
    "io.temporal.internal.worker.Poller"          -> LogLevel.Error,
    "zio.temporal.internal"                       -> LogLevel.Info,
  )

  override val bootstrap: ZLayer[ZIOAppArgs, Any, Any] =
    Runtime.removeDefaultLoggers >>> consoleLogger(ConsoleLoggerConfig(LogFormat.colored, logFilter))

  def run: ZIO[ZIOAppArgs & Scope, Any, Any] =
    val program =
      for
        args          <- getArgs
        msg            = if args.isEmpty then "testMsg" else args.mkString(" ")
        workerFactory <- ZIO.service[ZWorkerFactory]
        workflowResult <- workerFactory.use {
                            Client.workflowResultZIO(msg)
                          }
        _ <- ZIO.logInfo(s"The workflow result: $workflowResult")
      yield ExitCode.success

    program
      .provideSome[ZIOAppArgs](
        ZLayer.succeed(ZWorkflowServiceStubsOptions.default),
        ZLayer.succeed(ZWorkflowClientOptions.default),
        ZLayer.succeed(ZWorkerFactoryOptions.default),
        WorkerModule.worker,
        ZWorkflowClient.make,
        ZWorkflowServiceStubs.make,
        ZWorkerFactory.make,
        slf4j.bridge.Slf4jBridge.initialize,
      )
```

Generates the output:

```sh
❯ scli zio-temporal.scala
Compiling project (Scala 3.3.0-RC3, JVM)
Compiled project (Scala 3.3.0-RC3, JVM)
timestamp=2023-04-17T15:59:33.947501-03:00 level=INFO thread=zio-fiber-11 message="Started sample-worker"
timestamp=2023-04-17T15:59:34.259266-03:00 level=INFO thread=zio-fiber-4 message="Will submit message "testMsg""
timestamp=2023-04-17T15:59:34.499747-03:00 level=INFO thread=zio-fiber-4 message="The workflow result: ACK: testMsg"
```
