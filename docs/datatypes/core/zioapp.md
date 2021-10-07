---
id: zioapp 
title: "ZIOApp"
---

The `ZIOApp` trait is an entry point for a ZIO application that allows sharing layers between applications. It also
provides us the ability to compose multiple ZIO applications.

There is another simpler version of `ZIOApp` called `ZIOAppDefault`. We usually use `ZIOAppDefault` which uses the default ZIO environment (`ZEnv`).

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
