---
layout: docs
section: usage
title:  "Main"
---

# Main

Your main function can extend `App`, which provides a complete runtime system and allows your entire program to be purely functional.

```tut:silent
import scalaz.zio.{App, IO}
import scalaz.zio.console._

import java.io.IOException

object MyApp extends App {

  def run(args: List[String]): IO[Nothing, ExitStatus] =
    myAppLogic.attempt.map(_.fold(_ => 1, _ => 0)).map(ExitStatus(_))

  def myAppLogic: IO[IOException, Unit] =
    for {
      _ <- putStrLn("Hello! What is your name?")
      n <- getStrLn
      _ <- putStrLn("Hello, " + n + ", good to meet you!")
    } yield ()
}
```
