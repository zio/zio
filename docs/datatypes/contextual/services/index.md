---
id: index
title: "Introduction"
---

ZIO already provides four build-in services:

1. **[Console](console.md)** — Operations for reading/writing strings from/to the standard input, output, and error console.
2. **[Clock](clock.md)** — Contains some functionality related to time and scheduling.
3. **[Random](random.md)** — Provides utilities to generate random numbers.
4. **[System](system.md)** — Contains several useful functions related to system environments and properties.

The `ZEnv` is a type alias for all of these services:

```scala
type ZEnv = Clock & Console & System & Random
```

When we use these services we don't need to provide their corresponding environment explicitly. ZIO provides built-in live version of ZIO services to our effects, so we do not need to provide them manually.

So instead of writing the following snippet:

```scala mdoc:compile-only
import zio._

import java.io.IOException

object MainApp extends ZIOAppDefault {
  val myApp: ZIO[Console & Clock, IOException, Unit] = 
    for {
      date <- Clock.currentDateTime
      _    <- ZIO.logInfo(s"Application started at $date")
      _    <- Console.print("Enter your name: ")
      name <- Console.readLine
      _    <- Console.printLine(s"Hello, $name!")
    } yield ()

  def run = myApp.provide(Console.live, Clock.live)
}
```

We write as below:

```scala mdoc:compile-only
import zio._

import java.io.IOException

object MainApp extends ZIOAppDefault {
  val myApp: ZIO[Console & Clock, IOException, Unit] = 
    for {
      date <- Clock.currentDateTime
      _    <- ZIO.logInfo(s"Application started at $date")
      _    <- Console.print("Enter your name: ")
      name <- Console.readLine
      _    <- Console.printLine(s"Hello, $name!")
    } yield ()

  def run = myApp
}
```
