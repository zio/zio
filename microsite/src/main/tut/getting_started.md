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

## Main

Your main function can extend `App`, which provides a complete runtime system and allows your entire program to be purely functional.

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
      _ <- putStrLn("Hello, " + n + ", good to meet you!")
    } yield ()
}
```

## Console

ZIO provides a few primitives for interacting with the console.
These can be imported using the following

```tut:silent
import scalaz.zio.console._
```

### Print to screen

Printing to the screen is one of the most basic I/O operations.
In order to do so and preserve referential transparency, we can use `putStr` and `putStrLn`

```tut
// Print without trailing line break
putStr("Hello World")

// Print string and include trailing line break
putStrLn("Hello World")
```

### Read console input

For use cases that require user-provided input via the console, `getStrLn` allows importing
values into a pure program.

```tut
val echo = getStrLn.flatMap(putStrLn)
```