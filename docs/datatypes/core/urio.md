---
id: urio
title: "URIO"
---

`URIO[R, A]` is a type alias for `ZIO[R, Nothing, A]`, which represents an effect that requires an `R`, and cannot fail, but can succeed with an `A`.

Let's see how the `URIO` type alias is defined:

```scala mdoc:invisible
import zio.ZIO
```

```scala mdoc:silent
type URIO[-R, +A] = ZIO[R, Nothing, A]
```

So the `URIO` just equal to `ZIO` which requires `R` and cannot fail because in the Scala the `Nothing` type has no inhabitant, we can't create an instance of type `Nothing`. It succeeds with `A`.

In following example, the type of `putStrLn` is `URIO[Console, Unit]` which means, it requires `Console` service as an environment, and it succeeds with `Unit` value:

```scala mdoc:invisible:reset
import zio._
import zio.console._
```

```scala mdoc:silent
def putStrLn(line: => String): URIO[Console, Unit] =
  ZIO.accessM(_.get putStrLn line)
```
