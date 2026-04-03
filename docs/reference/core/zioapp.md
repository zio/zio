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

When a ZIO application (e.g. one extending `ZIOAppDefault`) receives an external interruption signal such as **SIGINT** when pressing **Ctrl+C**, the runtime will attempt to run all finalizers (cleanup logic) before exiting. By default, `gracefulShutdownTimeout` set to `Duration.Infinity`, which means ZIO will wait indefinitely for finalizers unless you override it.

Below are two examples: one where cleanup finishes within the timeout and one where cleanup deliberately exceeds it.

### Example 1: Finalizer completes within the timeout

```scala mdoc:compile-only
import zio._

object MyApp extends ZIOAppDefault {
  // Wait at most 30 seconds for all finalizers to complete on SIGINT
  override def gracefulShutdownTimeout: Duration = 30.seconds

  val run: ZIO[ZIOAppArgs with Scope, Any, Any] =
    ZIO.acquireReleaseWith(
      acquire = ZIO.logInfo("Acquiring resource...").as("MyResource")
    )(release =
      _ =>
        ZIO.logInfo("Releasing resource (3s) ...") *> ZIO.sleep(3.seconds) *>
          ZIO.logInfo("Cleanup done")
    ) { resource =>
      ZIO.logInfo(s"Running with $resource, press Ctrl+C to interrupt") *> ZIO.never
    }
}
```

In this example, `MyApp` starts and logs `Acquiring resource...`. When you press Ctrl+C (sending SIGINT), ZIO interrupts the main fiber and immediately runs the finalizer which logs `Releasing resource (3s) ...` and then sleeps for three seconds. Because the finalizer completes its work well within the 30s timeout, the runtime finishes cleanup and the process exits normally.

### Example 2: Finalizer exceeds the timeout
```scala mdoc:compile-only
import zio._

object MyAppTimeout extends ZIOAppDefault {
  // Wait at most 5 seconds for finalizers to complete on SIGINT
  override def gracefulShutdownTimeout: Duration = 5.seconds

  val run: ZIO[ZIOAppArgs with Scope, Any, Any] =
    ZIO.acquireReleaseWith(
      acquire = ZIO.logInfo("Acquiring resource...").as("MyResource")
    )(release =
      _ =>
        ZIO.logInfo("Releasing resource (20s) ...") *> ZIO.sleep(20.seconds) *>
          ZIO.logInfo("Cleanup done")
    ) { resource =>
      ZIO.logInfo(s"Running with $resource, press Ctrl+C to interrupt") *> ZIO.never
    }
}
```
Here, `MyAppTimeout` starts and logs `Acquiring resource...`. If you press Ctrl+C after a few seconds, ZIO interrupts the main fiber and starts running the finalizer, logging `Releasing resource (20s) ...` before sleeping for twenty seconds. However, `gracefulShutdownTimeout` is set to just five seconds, ZIO waits those five seconds and then prints exactly:

```bash
**** WARNING ****
Timed out waiting for ZIO application to shut down after 5 seconds. You can adjust your application's shutdown timeout by overriding the `shutdownTimeout` method
```

At that point, the JVM process exits immediately even though the 20-second finalizer has not yet finished.

:::note
Currently, `gracefulShutdownTimeout` is implemented for the **JVM** and **Scala Native** only
:::
