---
id: index
title: "Introduction to ZIO's Control Flow Operators"
sidebar_label: "Control Flow"
---

Although we have access to built-in Scala control flow structures, ZIO has several control flow combinators. In this section, we are going to introduce different ways of controlling flows in ZIO applications.

## `if` Expression

When working with ZIO values, we can use built-in Scala if-then-else expressions:

```scala mdoc:compile-only
import zio._

def validateWeightOption(weight: Double): ZIO[Any, Nothing, Option[Double]] =
  if (weight >= 0)
    ZIO.some(weight)
  else
    ZIO.none
```

Also, we can encode invalid inputs using the error channel:

```scala mdoc:compile-only
import zio._

def validateWeightOrFail(weight: Double): ZIO[Any, String, Double] =
  if (weight >= 0)
    ZIO.succeed(weight)
  else
    ZIO.fail(s"negative input: $weight")
```

Even if the input has side effects, we can use `ZIO#flatMap` to access the raw value and write the if-then-else expression:

```scala mdoc:compile-only
import zio._

def validateWeightOrFailZIO[R](weight: ZIO[R, Nothing, Double]): ZIO[R, String, Double] =
  weight.flatMap { w =>
    if (w >= 0)
      ZIO.succeed(w)
    else
      ZIO.fail(s"negative input: $w")
  }
```

## Conditional Operators

### `when`

We can also use ZIO's combinators that are the moral equivalent to these expressions:

Instead of `if (p) expression` we can use the `ZIO.when` or `ZIO#when` operator:

```scala mdoc:compile-only
import zio._

def validateWeightOption(weight: Double): ZIO[Any, Nothing, Option[Double]] =
  ZIO.when(weight > 0)(ZIO.succeed(weight))
```

If the predicate is effectful, we can use `ZIO.whenZIO` or `ZIO#whenZIO` operators.

For example, the following function creates a random option of int value:

```scala mdoc:compile-only
import zio._

def randomIntOption: ZIO[Any, Nothing, Option[Int]] =
  Random.nextInt.whenZIO(Random.nextBoolean)
```

Another nice variant of the `when` operator is `ZIO.whenCase` and also the `ZIO.whenCaseZIO`. Using these operators, we can run an effect when our provided effectful `PartialFunction` matches the given raw or effectful input. The important note regarding this operator is that it is safe, so it will do nothing if the value does not match.

Let's try to write a game, which asks users to choose which game to play:

```scala mdoc:compile-only
import zio._

def minesweeper(level: String)     = ZIO.attempt(???)
def ticTacToe                      = ZIO.attempt(???)
def snake(rows: Int, columns: Int) = ZIO.attempt(???)

def myApp =
  ZIO.whenCaseZIO {
    (Console.print(
      "Please choose one game (minesweeper, snake, tictactoe)? "
    ) *> Console.readLine).orDie
  } {
    case "minesweeper" =>
      Console.print(
        "Please enter the level of the game (easy/hard/medium)?"
      ) *> Console.readLine.flatMap(minesweeper)
    case "snake" =>
      Console.printLine(
        "Please enter the size of the game: "
      ) *> Console.readLine.mapAttempt(_.toInt).flatMap(n => snake(n, n))
    case "tictactoe" => ticTacToe
  }
```

### `unless`

The `ZIO.unless` and `ZIO#unless` operators are like `when` operators, but they are moral equivalent for the `if (!p) expression` construct.

### `ifZIO`

This operator takes an _effectful predicate_, if that predicate is evaluated to true, it will run the `onTrue` effect, otherwise it will run the `onFalse` effect.

Let's try to write a simple virtual flip function:

```scala mdoc:compile-only
import java.io.IOException
import zio._

def flipTheCoin: ZIO[Any, IOException, Unit] =
  ZIO.ifZIO(Random.nextBoolean)(
    onTrue = Console.printLine("Head"),
    onFalse = Console.printLine("Tail")
  )
```

## Loop Operators

In imperative Scala code bases, sometimes we may use `while(condition) { statement }` or `do { statement } while (condition)` constructs to perform loops:

```scala mdoc:compile-only
object MainApp extends scala.App {
  def printNumbers(from: Int, to: Int): Unit = {
    var i = from
    while (i <= to) {
      println(s"$i")
      i = i + 1
    }
  }

  printNumbers(1, 3)
}
// 1
// 2
// 3
```

But in functional Scala, we tend to avoid mutable variables. So to have a loop, we would like to use recursion. Let's rewrite the previous example using recursion:

```scala mdoc:compile-only
import scala.annotation.tailrec

object MainApp extends scala.App {
  @tailrec
  def printNumbers(from: Int, to: Int): Unit = {
    if (from <= to) {
      println(s"$from")
      printNumbers(from + 1, to)
    } else ()
  }

  printNumbers(1, 3)
}
// 1
// 2
// 3
```

In this example, we wrote a recursive function that prints numbers from 1 to 3. While the last effort doesn't use a mutable variable, it's not a pure solution. We have a `println` statement inside our solution, calling this function is not pure so the whole solution is not pure. We know that we can model effectful functions using the ZIO effect system. So let's try rewrite that using ZIO:

```scala
import zio._
import java.io.IOException

object MainApp extends ZIOAppDefault {
  def printNumbers(from: Int, to: Int): ZIO[Any, IOException, Unit] = {
    if (from <= to)
      Console.printLine(s"$from") *>
        printNumbers(from + 1, to)
    else ZIO.unit
  }

  def run = printNumbers(1, 5)
}
```

ZIO provides some loop combinators that help us avoid the need to write explicit recursions. This means that we can do almost anything we want to do without using explicit recursions. Let's rewrite the last solution using `ZIO.loopDiscard`:

```scala
import zio._

import java.io.IOException

object MainApp extends ZIOAppDefault {
  def printNumbers(from: Int, to: Int): ZIO[Any, IOException, Unit] = {
    ZIO.loopDiscard(from)(_ <= to, _ + 1)(i => Console.printLine(i))
  }

  def run = printNumbers(1, 3)
}
```

After this short introduction to writing loops in functional Scala, now let us go further into ZIO-specific combinators for writing loops:

### `loop`

The `ZIO.loop` operator takes an initial state, then repeatedly changes the state based on the given `inc` function, until the given `cont` function evaluates to true:

```scala
object ZIO {
  def loop[R, E, A, S](
    initial: => S
  )(cont: S => Boolean, inc: S => S)(body: S => ZIO[R, E, A]): ZIO[R, E, List[A]]

  def loopDiscard[R, E, S](
    initial: => S
  )(cont: S => Boolean, inc: S => S)(body: S => ZIO[R, E, Any]): ZIO[R, E, Unit]
```

`ZIO.loop` collects all intermediate states in a list and returns it finally, while the `ZIO.loopDiscard` discards all results.

We can think of `ZIO.loop` as a moral equivalent of the following while loop:

```scala
var s  = initial
var as = List.empty[A]

while (cont(s)) {
  as = body(s) :: as
  s  = inc(s)
}

as.reverse
```

Let's try some examples:

```scala mdoc:compile-only
import java.io.IOException
import zio._

val r1: ZIO[Any, Nothing, List[Int]] =
  ZIO.loop(1)(_ <= 5, _ + 1)(n => ZIO.succeed(n)).debug
// List(1, 2, 3, 4, 5)

val r2: ZIO[Any, Nothing, List[Int]] =
  ZIO.loop(1)(_ <= 5, _ + 1)(n => ZIO.succeed(n * 2)).debug
// List(2, 4, 6, 8, 10)

val r3: ZIO[Any, IOException, List[Unit]] =
  ZIO.loop(1)(_ <= 5, _ + 1) { index =>
    Console.printLine(s"Currently at index $index")
  }.debug
// Currently at index 1
// Currently at index 2
// Currently at index 3
// Currently at index 4
// Currently at index 5
// List((), (), (), (), ())

val r4: ZIO[Any, IOException, Unit] =
  ZIO.loopDiscard(1)(_ <= 5, _ + 1) { index =>
    Console.printLine(s"Currently at index $index")
  }.debug
// Currently at index 1
// Currently at index 2
// Currently at index 3
// Currently at index 4
// Currently at index 5
// ()

val r5: ZIO[Any, IOException, List[String]] =
  Console.printLine("Please enter three names: ") *>
    ZIO.loop(1)(_ <= 3, _ + 1) { n =>
      Console.print(s"$n. ") *> Console.readLine
    }.debug
// Please enter three names:
// 1. John
// 2. Jane
// 3. Joe
// List(John, Jane, Joe)
```

### `iterate`

To iterate with the given effectful operation we can use the `ZIO.iterate` combinator. During each iteration, it uses an effectful `body` operation to change the state, and it will continue the iteration while the `cont` function evaluates to true:

```scala
object ZIO {
  def iterate[R, E, S](
    initial: => S
  )(cont: S => Boolean)(body: S => ZIO[R, E, S]): ZIO[R, E, S]
}
```

This operator is a moral equivalent of the following while loop:

```scala
var s = initial
while (cont(s)) {
  s = body(s)
}
s
```

Let's try some examples:

```scala mdoc:compile-only
import zio._

val r1 = ZIO.iterate(1)(_ <= 5)(s => ZIO.succeed(s + 1)).debug
// 6

val r2 = ZIO.iterate(1)(_ <= 5)(s => ZIO.succeed(s * 2).debug).debug("result")
// 2
// 4
// 8
// result: 8
```

Here's another example. Assume we want to take many names from the user using the terminal. We don't know how many names the user is going to enter. We can ask the user to write "exit" when all inputs are finished. To write such an application, we can use recursion like below:

```scala mdoc:compile-only
import java.io.IOException
import zio._

def getNames: ZIO[Any, IOException, List[String]] =
  Console.print("Please enter all names") *>
    Console.printLine(" (enter \"exit\" to indicate end of the list):") *> {
      def loop(
          names: List[String]
      ): ZIO[Any, IOException, List[String]] = {
        Console.print(s"${names.length + 1}. ") *> Console.readLine
          .flatMap {
            case "exit" => ZIO.succeed(names)
            case name   => loop(names.appended(name))
          }
      }
      loop(List.empty[String])
    }
// Please enter all names (enter "exit" to indicate end of the list):
// 1. John
// 2. Jane
// 3. Joe
// 4. exit
// List(John, Jane, Joe)
```

Instead of manually writing recursions, we can rely on well-tested ZIO combinators. So let's rewrite this application using the `ZIO.iterate` operator:

```scala mdoc:compile-only
import java.io.IOException
import zio._

def getNames: ZIO[Any, IOException, List[String]] =
  Console.print("Please enter all names") *>
    Console.printLine(" (enter \"exit\" to indicate end of the list):") *>
    ZIO.iterate((List.empty[String], true))(_._2) { case (names, _) =>
      Console.print(s"${names.length + 1}. ") *>
        Console.readLine.map {
          case "exit" => (names, false)
          case name   => (names.appended(name), true)
        }
    }
    .map(_._1)
    .debug
// Please enter all names (enter "exit" to indicate end of the list):
// 1. John
// 2. Jane
// 3. Joe
// 4. exit
// List(John, Jane, Joe)
```

### `foreach`

Note that, in several cases, we can avoid these low-level operators and instead use high-level ones. For example, let's try to rewrite the `r5` with `ZIO.foreach`:

```scala mdoc:compile-only
import zio._

Console.printLine("Please enter three names:") *>
  ZIO.foreach(1 to 3) { index =>
    Console.print(s"$index. ") *> Console.readLine
  }.debug
// Please enter three names:
// 1. John
// 2. Jane
// 3. Joe
// Vector(John, Jane, Joe)
```

## try/catch/finally

When working with resources, just like Scala's `try`/`catch`/`finally` construct, in ZIO we have a similar operator called `acquireRelease` and also `ensuring`. We discussed them in more detail in the [resource management section](../resource/index.md). But, for now, we want to focus on their control flow behaviors.

Let's learn about the `ZIO.acquireReleaseWith` operator. This operator takes three effects:

1. **`acquire`**, an effect that describes the resource acquisition
2. **`release`**, an effect that describes the release of the resource
3. **`use`**, an effect that describes resource usage

```scala mdoc:compile-only
import zio._

ZIO.acquireReleaseWith(acquire = ???)(release = ???)(use = ???)
```

This operator guarantees us that if the _resource acquisition (acquire)_ succeeds, the _release_ effect will be executed whether the _use_ effect succeeded or not:

```scala mdoc:compile-only
import java.io.IOException
import scala.io.Source
import zio._

def wordCount(fileName: String): ZIO[Any, Throwable, Int] = {
  def openFile(name: => String): ZIO[Any, IOException, Source] =
    ZIO.attemptBlockingIO(Source.fromFile(name))

  def closeFile(source: => Source): ZIO[Any, Nothing, Unit] =
    ZIO.succeedBlocking(source.close())

  def wordCount(source: => Source): ZIO[Any, Throwable, Int] =
    ZIO.attemptBlocking(source.getLines().length)

  ZIO.acquireReleaseWith(openFile(fileName))(closeFile(_))(wordCount(_))
}
```

Let's try a simple `acquireRelease` workflow to see how its control flow works:

```scala mdoc:compile-only
import zio._

object MainApp extends ZIOAppDefault {
  def run =
    ZIO.acquireReleaseWith {
      ZIO.succeed("resource").tap(r => ZIO.debug(s"$r acquired"))
    } { i =>
      ZIO.debug(s"$i released")
    } { i =>
      ZIO.debug(s"start using $i")
    }
}
// Output:
// resource acquired
// start using resource
// resource released
```
