---
id: index
title: "Introduction to ZIO's Control Flow Operators"
description: "Control-flow operators in ZIO: conditional branching (when, unless, cond), looping (loop, iterate, foreach), and resource bracketing."
keywords:
  - "Control Flow"
  - "Conditional Operators"
  - "Loop Operators"
  - "foreach"
  - "ZIO.cond"
  - "Resource Bracketing"
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

ZIO provides several conditional combinators that let you branch on a boolean predicate or pattern-match an effectful value — the functional equivalents of Scala's `if` and `match` expressions.

### `ZIO.when` / `ZIO#when`

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

When the result of the conditional effect is not needed, use `ZIO#whenDiscard` or `ZIO.whenDiscard` — they return `Unit` instead of `Option[A]`, skipping the allocation. The effectful-predicate counterpart is `ZIO#whenZIODiscard`. The following example records an audit event only when the acting user is an administrator:

```scala mdoc:compile-only
import zio._

def recordAuditEvent(event: String): ZIO[Any, Nothing, Unit] = ZIO.unit // placeholder

def handleRequest(isAdmin: Boolean, event: String): ZIO[Any, Nothing, Unit] =
  recordAuditEvent(event).whenDiscard(isAdmin)
```

### `ZIO.unless` / `ZIO#unless`

`ZIO#unless` runs an effect when a condition is **false** and returns `Option[A]` — the negated dual of `ZIO#when`. Reach for it any time you would write `effect.when(!condition)`, because `unless` expresses the intent as natural prose.

A common pattern is a guard that skips side-effectful work when a condition is already satisfied. For example, sending a welcome notification only when the user has not opted out of emails reads clearly with `ZIO#unlessZIODiscard`:

```scala mdoc:compile-only
import zio._

trait NotificationService {
  def sendWelcome(userId: Long): ZIO[Any, Nothing, Unit]
}

trait UserRepository {
  def hasOptedOut(userId: Long): ZIO[Any, Nothing, Boolean]
}

def onboardUser(
  notifications: NotificationService,
  users: UserRepository,
  userId: Long
): ZIO[Any, Nothing, Unit] =
  notifications.sendWelcome(userId).unlessZIODiscard(users.hasOptedOut(userId))
```

When the result does not matter, prefer the `Discard` variants — they return `Unit` and skip the `Option` allocation:

| Predicate type | Result needed | Right choice |
|----------------|---------------|--------------|
| Pure           | Yes           | `ZIO#unless` |
| Pure           | No            | `ZIO#unlessDiscard` |
| Effectful      | Yes           | `ZIO#unlessZIO` |
| Effectful      | No            | `ZIO#unlessZIODiscard` |

The companion-object forms (`ZIO.unless(p)(effect)` and `ZIO.unlessZIO(p)(effect)`) accept the effect as a second argument instead of as the receiver — useful when the effect is not naturally expressed as a method chain.

### `ZIO.ifZIO`

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

### `ZIO.cond`

`ZIO.cond` lifts a pure predicate into an effect that either succeeds with `result` or fails with `error` — a concise alternative to writing `if (predicate) ZIO.succeed(result) else ZIO.fail(error)`. Use it for validation logic that has a clear success path and a typed failure (see the [Error Management](../error-management/index.md) reference for how typed failures compose). It differs from `ZIO.when`, which returns `Option` rather than failing, and from `ZIO.ifZIO`, which takes an effectful predicate and effectful branches:

```scala
object ZIO {
  def cond[E, A](predicate: => Boolean, result: => A, error: => E): IO[E, A]
}
```

A typical use case is input validation before calling a downstream service. The following example rejects a withdrawal when the account balance is insufficient:

```scala mdoc:compile-only
import zio._

case class Account(id: Long, balance: Double)
case class InsufficientFunds(requested: Double, available: Double)

def withdraw(account: Account, amount: Double): IO[InsufficientFunds, Account] =
  ZIO.cond(
    account.balance >= amount,
    account.copy(balance = account.balance - amount),
    InsufficientFunds(requested = amount, available = account.balance)
  )
```

The table below contrasts three conditional-branching operators — `ZIO.cond` and `ZIO.when` accept a pure `Boolean` predicate, while `ZIO.ifZIO` accepts an effectful one:

| Operator      | Predicate | On false              | Return type              |
|---------------|-----------|-----------------------|--------------------------|
| `ZIO.cond`    | pure      | fail with typed error | `IO[E, A]`               |
| `ZIO.when`    | pure      | return `None`         | `ZIO[R, E, Option[A]]`   |
| `ZIO.ifZIO`   | effectful | run `onFalse` effect  | `ZIO[R, E, A]`           |

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

In this example, we wrote a recursive function that prints numbers from 1 to 3. While the last effort doesn't use a mutable variable, it's not a pure solution. We have a `println` statement inside our solution, calling this function is not pure so the whole solution is not pure. We know that we can model effectful functions using the ZIO effect system. So let's rewrite that using ZIO:

```scala mdoc:compile-only
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

```scala mdoc:compile-only
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

### `ZIO.loop`

The `ZIO.loop` operator takes an initial state, then repeatedly changes the state based on the given `inc` function, until the given `cont` function evaluates to true:

```scala
object ZIO {
  def loop[R, E, A, S](
    initial: => S
  )(cont: S => Boolean, inc: S => S)(body: S => ZIO[R, E, A]): ZIO[R, E, List[A]]

  def loopDiscard[R, E, S](
    initial: => S
  )(cont: S => Boolean, inc: S => S)(body: S => ZIO[R, E, Any]): ZIO[R, E, Unit]
}
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

### `ZIO.iterate`

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

### `ZIO.foreach`

`ZIO.foreach` transforms every element of an existing collection by running an effect on each one in sequence, preserving the collection's shape in the result. Use `ZIO.foreach` when you already have an `Iterable`, `Set`, `Array`, `Map`, or `Option`; reach for `ZIO.loop` or `ZIO.iterate` only when the iteration range is computed at call time rather than given by an existing collection.

The most commonly used variants are:

```scala
object ZIO {
  def foreach[R, E, A, B](in: Iterable[A])(f: A => ZIO[R, E, B]): ZIO[R, E, List[B]]
  def foreachDiscard[R, E, A](in: Iterable[A])(f: A => ZIO[R, E, Any]): ZIO[R, E, Unit]
  def foreachPar[R, E, A, B](in: Iterable[A])(f: A => ZIO[R, E, B]): ZIO[R, E, List[B]]
  def foreachParDiscard[R, E, A](in: Iterable[A])(f: A => ZIO[R, E, Any]): ZIO[R, E, Unit]
}
```

For example, collecting three user-entered names reads more naturally with `ZIO.foreach` than with a `loop` that manually tracks an index state:

```scala mdoc:compile-only
import zio._

Console.printLine("Please enter three names:") *>
  ZIO.foreach(1 to 3) { index =>
    Console.print(s"$index. ") *> Console.readLine
  }.debug
```

When the individual effects are independent and can proceed concurrently, use `ZIO.foreachPar`. It runs each element on its own [fiber](../fiber/index.md) and returns results in the same order as the input, regardless of completion order. If any fiber fails, all remaining fibers are interrupted:

```scala mdoc:compile-only
import zio._

case class UserId(value: Long)
case class UserProfile(id: UserId, name: String)

def fetchProfile(id: UserId): ZIO[Any, String, UserProfile] =
  ZIO.succeed(UserProfile(id, s"User ${id.value}"))

val userIds = List(UserId(1L), UserId(2L), UserId(3L))

val profiles: ZIO[Any, String, List[UserProfile]] =
  ZIO.foreachPar(userIds)(fetchProfile)
```

When only side effects matter and results are not needed, `ZIO.foreachDiscard` and `ZIO.foreachParDiscard` skip building the result collection and return `Unit` directly, avoiding the allocation cost.

## `try`/`catch`/`finally`

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
