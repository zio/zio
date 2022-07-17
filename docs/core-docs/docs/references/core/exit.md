---
id: exit
title: "Exit"
---

An `Exit[E, A]` value describes how fibers end life. It has two possible values:
- `Exit.Success` contain a success value of type `A`. 
- `Exit.Failure` contains a failure [Cause](cause.md) of type `E`.

This is how the `Exit` data type is defined:

```scala
sealed abstract class Exit[+E, +A] extends Product with Serializable { self =>
  // Exit operators
}
object Exit {
  final case class Success[+A](value: A)        extends Exit[Nothing, A]
  final case class Failure[+E](cause: Cause[E]) extends Exit[E, Nothing]
}
```

We can call `ZIO#exit` on our effect to determine the Success or Failure of our fiber:

```scala mdoc:silent
import zio._
import zio.Console._

import java.io.IOException

val result: ZIO[Any, IOException, Unit] = 
  for {
    successExit <- ZIO.succeed(1).exit
    _ <- successExit match {
      case Exit.Success(value) =>
        printLine(s"exited with success value: ${value}")
      case Exit.Failure(cause) =>
        printLine(s"exited with failure state: $cause")
    }
  } yield ()
```