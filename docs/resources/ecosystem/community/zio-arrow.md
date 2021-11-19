---
id: zio-arrow
title: "ZIO Arrow"
---

[ZIO Arrow](https://github.com/zio-mesh/zio-arrow/) provides the `ZArrow` effect, which is a high-performance composition effect for the ZIO ecosystem.

## Introduction

`ZArrow[E, A, B]` is an effect representing a computation parametrized over the input (`A`), and the output (`B`) that may fail with an `E`. Arrows focus on **composition** and **high-performance computation**. They are like simple functions, but they are lifted into the `ZArrow` context.

`ZArrow` delivers three main capabilities:

- ** High-Performance** — `ZArrow` exploits `JVM` internals to dramatically decrease the number of allocations and dispatches, yielding an unprecedented runtime performance.

- **Abstract interface** — `Arrow` is a more abstract data type, than ZIO Monad. It's more abstract than ZIO Streams. In a nutshell, `ZArrow` allows a function-like interface that can have both different inputs and different outputs.

- **Easy Integration** — `ZArrow` can both input and output `ZIO Monad` and `ZIO Stream`, simplifying application development with different ZIO Effect types.

## Installation

In order to use this library, we need to add the following line in our `build.sbt` file:

```scala
libraryDependencies += "io.github.neurodyne" %% "zio-arrow" % "0.2.1"
```

## Example

In this example we are going to write a repetitive task of reading a number from standard input and then power by 2 and then print the result:

```scala
import zio.arrow.ZArrow
import zio.arrow.ZArrow._
import zio.console._
import zio.{ExitCode, URIO}

import java.io.IOException

object ArrowExample extends zio.App {

  val isPositive : ZArrow[Nothing, Int, Boolean]     = ZArrow((_: Int) > 0)
  val toStr      : ZArrow[Nothing, Any, String]      = ZArrow((i: Any) => i.toString)
  val toInt      : ZArrow[Nothing, String, Int]      = ZArrow((i: String) => i.toInt)
  val getLine    : ZArrow[IOException, Any, String]  = ZArrow.liftM((_: Any) => getStrLn.provide(Console.live))
  val printStr   : ZArrow[IOException, String, Unit] = ZArrow.liftM((line: String) => putStr(line).provide(Console.live))
  val printLine  : ZArrow[IOException, String, Unit] = ZArrow.liftM((line: String) => putStrLn(line).provide(Console.live))
  val power2     : ZArrow[Nothing, Int, Double]      = ZArrow((i: Int) => Math.pow(i, 2))
  val enterNumber: ZArrow[Nothing, Unit, String]     = ZArrow((_: Unit) => "Enter positive number (-1 to exit): ")
  val goodbye    : ZArrow[Nothing, Any, String]      = ZArrow((_: Any) => "Goodbye!")

  val app: ZArrow[IOException, Unit, Boolean] =
    enterNumber >>> printStr >>> getLine >>> toInt >>>
      ifThenElse(isPositive)(
        power2 >>> toStr >>> printLine >>> ZArrow((_: Any) => true)
      )(
        ZArrow((_: Any) => false)
      )

  val myApp = whileDo(app)(ZArrow(_ => ())) >>> goodbye >>> printLine

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    myApp.run(()).exitCode
}
```

Let's see an example of running this program:

```
Enter positive number (-1 to exit): 25
625.0
Enter positive number (-1 to exit): 8
64.0
Enter positive number (-1 to exit): -1
Goodbye!
```

## Resources

- [Blazing Fast, Pure Effects without Monads](https://www.youtube.com/watch?v=L8AEj6IRNEE) by John De Goes (Dec 2018)
