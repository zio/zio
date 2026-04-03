---
id: exit
title: "Exit"
---

An `Exit[E, A]` value describes how fibers end life. It has two possible values:
- `Exit.Success` contain a success value of type `A`. 
- `Exit.Failure` contains a failure [Cause](cause.md) of type `E`.

We can call `run` on our effect to determine the Success or Failure of our fiber:

```scala
import zio._
import zio.console._
for {
  successExit <- ZIO.succeed(1).run
  _ <- successExit match {
    case Exit.Success(value) =>
      putStrLn(s"exited with success value: ${value}")
    case Exit.Failure(cause) =>
      putStrLn(s"exited with failure state: $cause")
  }
} yield ()
```