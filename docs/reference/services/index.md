---
id: index
title: "Introduction to ZIO's Built-in Services"
---

ZIO already provides four built-in services:

1. **[Console](console.md)** — Operations for reading/writing strings from/to the standard input, output, and error console.
2. **[Clock](clock.md)** — Contains some functionality related to time and scheduling.
3. **[Random](random.md)** — Provides utilities to generate random numbers.
4. **[System](system.md)** — Contains several useful functions related to system environments and properties.

When we use these services we don't need to provide their corresponding environment explicitly. ZIO provides built-in live version of ZIO services to our effects, so we do not need to provide them manually.

```scala mdoc:compile-only
import zio._

import java.io.IOException

object MainApp extends ZIOAppDefault {
  val myApp: ZIO[Any, IOException, Unit] = 
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
