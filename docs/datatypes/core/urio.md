---
id: urio
title: "URIO"
---

`URIO[R, A]` is a type alias for `ZIO[R, Nothing, A]`, which represents an effect that requires an `R`, and cannot fail, but can succeed with an `A`.

In following example, the type of `putStrLn` is `URIO[Console, Unit]` which means, it requires `Console` service as an environment, and it succeeds with `Unit` value:
```scala mdoc:invisible
import zio._
import zio.console._
```

```scala mdoc:silent
def putStrLn(line: => String): URIO[Console, Unit] =
  ZIO.accessM(_.get putStrLn line)
```
