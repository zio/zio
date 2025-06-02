---
id: zioapp 
title: "ZIOApp"
---

The `ZIOApp` trait is an entry point for a ZIO application that allows sharing layers between applications. It also
provides us the ability to compose multiple ZIO applications.

There is another simpler version of `ZIOApp` called `ZIOAppDefault`. We usually use `ZIOAppDefault` which uses the default ZIO environment (`ZEnv`).

## Running a ZIO effect

The `ZIOAppDefault` has a `run` function, which is the main entry point for running a ZIO application on the JVM:

```scala mdoc:compile-only
import zio._

object MyApp extends ZIOAppDefault {
  def run = for {
    _ <- Console.printLine("Hello! What is your name?")
    n <- Console.readLine
    _ <- Console.printLine("Hello, " + n + ", good to meet you!")
  } yield ()
}
```

## Accessing Command-line Arguments

ZIO has a service that contains command-line arguments of an application called `ZIOAppArgs`. We can access command-line arguments using the built-in `getArgs` method:

```scala mdoc:compile-only
import zio._

object HelloApp extends ZIOAppDefault {
  def run = for {
    args <- getArgs
    _ <-
      if (args.isEmpty)
        Console.printLine("Please provide your name as an argument")
      else
        Console.printLine(s"Hello, ${args.head}!")
  } yield ()
}
```

## Customized Runtime

In the ZIO app, by overriding its `bootstrap` value, we can map the current runtime to a customized one. Let's customize it by introducing our own executor:

```scala mdoc:invisible
import zio._
val myAppLogic = ZIO.succeed(???)
```

```scala mdoc:compile-only
import zio._
import zio.Executor
import java.util.concurrent.{LinkedBlockingQueue, ThreadPoolExecutor, TimeUnit}

object CustomizedRuntimeZIOApp extends ZIOAppDefault {
  override val bootstrap = Runtime.setExecutor(
    Executor.fromThreadPoolExecutor(
      new ThreadPoolExecutor(
        5,
        10,
        5000,
        TimeUnit.MILLISECONDS,
        new LinkedBlockingQueue[Runnable]()
      )
    )
  )

  def run = myAppLogic
}
```

A detailed explanation of the ZIO runtime system can be found on the [runtime](runtime.md) page.

## Installing Low-level Functionalities

We can hook into the ZIO runtime to install low-level functionalities into the ZIO application, such as _logging_, _profiling_, and other similar foundational pieces of infrastructure.

A detailed explanation can be found on the [runtime](runtime.md) page.

## Composing ZIO Applications

To compose ZIO applications, we can use `<>` operator:

```scala mdoc:invisible
import zio._
val asyncProfiler, slf4j, loggly, newRelic = ZLayer.empty
```

```scala mdoc:compile-only
import zio._

object MyApp1 extends ZIOAppDefault {    
  def run = ZIO.succeed(???)
}

object MyApp2 extends ZIOAppDefault {
  override val bootstrap: ZLayer[Any, Any, Any] =
    asyncProfiler ++ slf4j ++ loggly ++ newRelic

  def run = ZIO.succeed(???)
}

object Main extends ZIOApp.Proxy(MyApp1 <> MyApp2)
```

The `<>` operator combines the layers of the two applications and then runs the two applications in parallel.

## Graceful Shutdown Timeout

When a ZIO application (e.g. one extending `ZIOAppDefault`) receives an external interruption signal such as **SIGINT** when pressing **Ctrl+C**, the runtime will attempt to run all finalizers (cleanup logic) before exiting. By default, `gracefulShutdownTimeout` returns `Duration.Infinity`, which means ZIO will wait forever for finalizers unless you override.

Override `gracefulShutdownTimeout` to bound how long the runtime should wait for finalizers. For example, to wait at most 30 seconds:

```scala mdoc:compile-only
import zio._

object MyApp extends ZIOAppDefault {
  // Wait at most 30 seconds for all finalizers to complete on SIGINT
  override def gracefulShutdownTimeout: Duration = Duration.fromSeconds(30)

  val run =
    ZIO.logInfo("MyApp is running...") *>
    ZIO.never
}
```

:::note
1. This only applies on JVM and Scala Native. Other platforms like Scala.js do not invoke the shutdown hook on external signals.

2. If finalizers take too long when the timeout elapses, the runtime prints exactly:
```
**** WARNING ****
Timed out waiting for ZIO application to shut down after 30 seconds. You can adjust your application's shutdown timeout by overriding the `shutdownTimeout` method
```
:::