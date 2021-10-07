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
import zio.Console

object MyApp extends ZIOAppDefault {
  def run = for {
    _ <- Console.printLine("Hello! What is your name?")
    n <- Console.readLine
    _ <- Console.printLine("Hello, " + n + ", good to meet you!")
  } yield ()
}
```

## Accessing Command-line Arguments

ZIO has a service that contains command-line arguments of an application called `ZIOAppArgs`. We can access command-line arguments using built-in `getArgs` method, which is a helper method:

```scala mdoc:compile-only
import zio._
object HelloApp extends ZIOAppDefault {
  def run = for {
    args <- args
    _ <-
      if (args.isEmpty)
        Console.printLine("Please provide your name as an argument")
      else
        Console.printLine(s"Hello, ${args.head}!")
  } yield ()
}
```

## Installing Low-level Functionalities

We can hook into the ZIO runtime configuration to install low-level functionalities into the ZIO application, such as _logging_, _profiling_, _tracing configurations_, and other similar foundational pieces of infrastructure.

In the following example, we disabled application tracing in order to improve application performance:

```scala mdoc:invisible
import zio._
val myAppLogic = ZIO.succeed(???)
```

```scala mdoc:compile-only
import zio._
import zio.internal.Tracing

object MyApp extends ZIOAppDefault {
  override def hook: RuntimeConfigAspect =
    RuntimeConfigAspect.setTracing(Tracing.enabled)

  def run = myAppLogic
}
```

## Composing ZIO Applications

To compose ZIO application, we can use `<>` operator:

```scala mdoc:invisible
import zio._
val asyncProfiler, slf4j, loggly, newRelic = RuntimeConfigAspect.identity
```

```scala mdoc:compile-only
import zio._

object MyApp1 extends ZIOAppDefault {
  def run = ZIO.succeed(???)
}

object MyApp2 extends ZIOAppDefault {
  override def hook: RuntimeConfigAspect =
    asyncProfiler >>> slf4j >>> loggly >>> newRelic

  def run = ZIO.succeed(???)
}

object Main extends ZIOAppDefault {
  def run = (MyApp1 <> MyApp2).invoke(Chunk.empty)
}
```

The `<>` operator combines the two layers of applications, composes their hooks (which are `RuntimeConfigAspect`), and then runs the two applications in parallel.
