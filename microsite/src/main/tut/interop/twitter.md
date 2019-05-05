---
layout: docs
section: interop
title: "Twitter Future"
---

# {{page.title}}

`interop-twitter` module provides capability for converting Twitter's future into ZIO `Task`.

### Example

```scala
import com.twitter.util.Future
import scalaz.zio.{ App, Task }
import scalaz.zio.interop.twitter._

object Example extends App {
  def run(args: List[String]) =     
    for {
      res <- Task.fromFuture(unsafe)
    } yield res

  private def unsafe: Future[Int] = ???
}
```