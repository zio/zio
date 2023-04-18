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
libraryDependencies += "dev.vhonta" %% "zio-temporal-core" % "0.2.0-M3"
```

## Example

Here we have one ZIO app with two "modules", one which is the worker that executes the workflow activity and the other is the Client which sends the request to the Temporal platform. To run the sample, it's required to download [Temporal cli](https://github.com/temporalio/cli) (recommended for development).


Run the Temporal server on one shell:

```sh
temporal server start-dev --ip 0.0.0.0 --db-filename /tmp/temporal.db
```

Now we can run the sample application which is based on Temporal Java SDK docs using [scala-cli](https://scala-cli.virtuslab.org):

```scala
//> using scala "3.3.0-RC3" // We use Scala 3.3 to leverage SIP-44 (FewerBraces)

//> using lib "dev.zio::zio:2.0.13"
//> using lib "dev.vhonta::zio-temporal-core:0.2.0-M3"
//> using lib "dev.zio::zio-logging:2.1.12"
//> using lib "dev.zio::zio-logging-slf4j2-bridge:2.1.12"
//> using option "-source:future", "-Wunused:imports", "-Wvalue-discard"

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

// Main Application
object Main extends ZIOAppDefault:
  val taskQueue = "echo-queue"

  // Worker implementation
  val worker = ZWorkerFactory.newWorker(taskQueue) @@
    ZWorker.addWorkflow[EchoWorkflowImpl].fromClass

  // Client implementation
  def invokeWorkflow(msg: String) = ZIO.serviceWithZIO[ZWorkflowClient]: client =>
    for
      uuid <- Random.nextUUID
      workflowID = s"echo-$uuid"
      echoWorkflow <- client
                        .newWorkflowStub[EchoWorkflow]
                        .withTaskQueue(taskQueue)
                        .withWorkflowId(workflowID)
                        .withWorkflowRunTimeout(2.seconds)
                        .withRetryOptions(ZRetryOptions.default.withMaximumAttempts(3))
                        .build
      _   <- ZIO.logInfo(s"Will submit message \"$msg\" with workflow ID $workflowID")
      res <- ZWorkflowStub.execute(echoWorkflow.echo(msg))
      _   <- ZIO.logInfo(s"Greeting received: $res")
    yield res

  // Logging configuration
  val logFilter: LogFilter[String] = LogFilter.logLevelByName(
    LogLevel.Debug,
    "io.grpc.netty" -> LogLevel.Warning,
    "io.netty"      -> LogLevel.Warning,
    "io.temporal"   -> LogLevel.Error,
  )
  override val bootstrap: ZLayer[ZIOAppArgs, Any, Any] =
    Runtime.removeDefaultLoggers >>> consoleLogger(ConsoleLoggerConfig(LogFormat.colored, logFilter))

  // ZIO Main Program
  def run: ZIO[ZIOAppArgs & Scope, Any, Any] =
    val program =
      for
        args           <- getArgs
        msg             = if args.isEmpty then "testMsg" else args.mkString(" ")
        _              <- worker
        _              <- ZWorkerFactory.setup
        _              <- ZWorkflowServiceStubs.setup()
        workflowResult <- invokeWorkflow(msg)
        _              <- ZIO.logInfo(s"The workflow result: $workflowResult")
      yield ExitCode.success

    program
      .provideSome[ZIOAppArgs & Scope](
        ZLayer.succeed(ZWorkflowServiceStubsOptions.default),
        ZLayer.succeed(ZWorkflowClientOptions.default),
        ZLayer.succeed(ZWorkerFactoryOptions.default),
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
timestamp=2023-04-18T17:33:59.970297-03:00 level=INFO thread=zio-fiber-4 message="ZWorkerFactory started"
timestamp=2023-04-18T17:34:00.010519-03:00 level=INFO thread=zio-fiber-4 message="Will submit message "testMsg" with workflow ID echo-81ef73da-d54d-492a-8f91-78e888dcebc8"
timestamp=2023-04-18T17:34:00.27055-03:00  level=INFO thread=zio-fiber-4 message="Greeting received: ACK: testMsg"
timestamp=2023-04-18T17:34:00.271691-03:00 level=INFO thread=zio-fiber-4 message="The workflow result: ACK: testMsg"
timestamp=2023-04-18T17:34:00.325834-03:00 level=INFO thread=zio-fiber-4 message="ZWorkerFactory shutdownNow initiated..."
```

Results of the execution can also be seen in the [Temporal UI](http://localhost:8233) running locally or the [tctl](https://github.com/temporalio/tctl) tool:

```sh
❯ tctl workflow observe --workflow_id echo-81ef73da-d54d-492a-8f91-78e888dcebc8
Progress:
  1, 2023-04-18T20:34:00Z, WorkflowExecutionStarted
  2, 2023-04-18T20:34:00Z, WorkflowTaskScheduled
  3, 2023-04-18T20:34:00Z, WorkflowTaskStarted
  4, 2023-04-18T20:34:00Z, WorkflowTaskCompleted
  5, 2023-04-18T20:34:00Z, WorkflowExecutionCompleted

Result:
  Run Time: 1 seconds
  Status: COMPLETED
  Output: ["ACK: testMsg"]
```
