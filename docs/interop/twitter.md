---
id: interop_twitter
title: "Twitter"
---

`interop-twitter` module provides capability to convert Twitter `Future` into ZIO `Task`.

### Example

```scala
import com.twitter.util.Future
import scalaz.zio.{ App, Task }
import scalaz.zio.console._
import scalaz.zio.interop.twitter._

object Example extends App {
  def run(args: List[String]) = {
    val program =
      for {
        _        <- putStrLn("Hello! What is your name?")
        name     <- getStrLn
        greeting <- Task.fromTwitterFuture(greet(name))
        _        <- putStrLn(greeting)
      } yield ()

    program.fold(_ => 1, _ => 0)
  }

  private def greet(name: String): Future[String] = Future.value(s"Hello, $name!")
}
```
