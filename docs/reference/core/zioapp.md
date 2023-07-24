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
