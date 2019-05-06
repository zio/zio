---
layout: docs
section: interop
title: "Twitter"
---

# {{page.title}}

`interop-twitter` module provides capability to convert Twitter `Future` into ZIO `Task`.

### Example

```scala
import com.twitter.util.Future
import scalaz.zio.{ App, Task }
import scalaz.zio.console._
import scalaz.zio.interop.twitter._

object Example extends App {
  def run(args: List[String]) =     
    for {
      _        <- putStrLn("Hello! What is your name?")
      name     <- getStrLn
      greeting <- Task.fromFuture(unsafe)
      _        <- putStrLn(greeting)
    } yield ()

  private def greet(name: String): Future[Int] = Future.const($"Hello, $name!")
}
```