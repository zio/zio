---
layout: page
position: 2
section: home
title:  "Getting Started"
---

# Getting Started

Include ZIO in your project by adding the following to your `build.sbt`:

```tut:evaluated
if (scalaz.zio.BuildInfo.isSnapshot) println(s"""resolvers += Resolver.sonatypeRepo("snapshots")""")
println(s"""libraryDependencies += "org.scalaz" %% "scalaz-zio" % "${scalaz.zio.BuildInfo.version}"""")
```

# Main

Your main function can extend `App`, which provides a complete runtime system and allows you to write your whole program using ZIO:

```tut:silent
import scalaz.zio.{App, IO}
import scalaz.zio.console._

import java.io.IOException

object MyApp extends App {

  def run(args: List[String]): IO[Nothing, ExitStatus] =
    myAppLogic.attempt.map(_.fold(_ => 1, _ => 0)).map(ExitStatus.ExitNow(_))

  def myAppLogic: IO[IOException, Unit] =
    for {
      _ <- putStrLn("Hello! What is your name?")
      n <- getStrLn
      _ <- putStrLn(s"Hello, ${n}, good to meet you!")
    } yield ()
}
```

If you are integrating ZIO into an existing application, using dependency injection, or do not control your main function, then you can use a runtime system in order to execute your `IO` programs:

```tut:silent
import scalaz.zio._
import scalaz.zio.console._

object IntegrationExample {
  val rts = new RTS{}

  rts.unsafeRun(putStrLn("Hello World!"))
}
```

# Console

ZIO provides a few primitives for interacting with the console. These can be imported using the following:

```tut:silent
import scalaz.zio.console._
```

## Printing Output

Printing to the screen is one of the most basic I/O operations.

In order to do so in a purely functional way, we can use `putStr` and `putStrLn`:

```tut
// Print without trailing line break
putStr("Hello World")

// Print string and include trailing line break
putStrLn("Hello World")
```

## Reading Input

If you need to read input from the console, you can use `getStrLn`:

```tut
val echo = getStrLn.flatMap(putStrLn)
```
# Learning More

To learn more about ZIO, see the [Overview](overview/index.html).
