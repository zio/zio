---
id: getting-started
title: "Getting Started with ZIO"
sidebar_label: "Getting Started"
slug: "getting-started"
description: "Get started with ZIO, a powerful functional effect system for Scala that enables asynchronous, concurrent, and parallel programming."
keywords:
  - "ZIO Getting Started"
  - "Scala Functional Programming"
  - "Effect System"
  - "Asynchronous Programming"
  - "Concurrent Programming"
---

## Installation

Include ZIO in your project by adding the following to your `build.sbt` file:

```scala mdoc:passthrough
println(s"""```""")
println(s"""libraryDependencies += "dev.zio" %% "zio" % "${zio.BuildInfo.version.split('+').head}"""")
println(s"""```""")
```

If you want to use ZIO streams, you should also include the following dependency:

```scala mdoc:passthrough
println(s"""```""")
println(s"""libraryDependencies += "dev.zio" %% "zio-streams" % "${zio.BuildInfo.version.split('+').head}"""")
println(s"""```""")
```

## Main

Your application can extend `ZIOAppDefault`, which provides a complete runtime system and allows you to write your whole program using ZIO:

```scala mdoc:compile-only
import zio._
import zio.Console._

object MyApp extends ZIOAppDefault {

  def run = myAppLogic

  val myAppLogic =
    for {
      _    <- printLine("Hello! What is your name?")
      name <- readLine
      _    <- printLine(s"Hello, ${name}, welcome to ZIO!")
    } yield ()
}
```

---

If you are integrating ZIO into an existing application, using dependency injection, or do not control your main function, then you can create a runtime system in order to execute your ZIO programs:

```scala mdoc:compile-only
import zio._

object IntegrationExample {
  val runtime = Runtime.default

  Unsafe.unsafe { implicit unsafe =>
    runtime.unsafe.run(ZIO.attempt(println("Hello World!"))).getOrThrowFiberFailure()
  }
}
```

Ideally, your application should have a _single_ runtime, because each runtime has its own resources (including thread pool and unhandled error reporter).

## Console

ZIO provides a [Console](../reference/services/console.md) service for interacting with the console.

If you need to print text to the console, you can use `print` and `printLine`:

```scala mdoc:compile-only
import zio._

// Print without trailing line break
Console.print("Hello World")

// Print string and include trailing line break
Console.printLine("Hello World")
```

If you need to read input from the console, you can use `readLine`:

```scala mdoc:compile-only
import zio._

val echo = Console.readLine.flatMap(line => Console.printLine(line))
```

## Next Steps

Now that you've got ZIO installed and running, the next step is to learn about the [`ZIO` data type](summary.md).
