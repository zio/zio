---
id: zio
title: "ZIO"
---

A `ZIO[R, E, A]` value is an immutable value that lazily describes a workflow or job. The workflow requires some environment `R`, and may fail with an error of type `E`, or succeed with a value of type `A`.

A value of type `ZIO[R, E, A]` is like an effectful version of the following function type:

```scala
R => Either[E, A]
```

This function, which requires an `R`, might produce either an `E`, representing failure, or an `A`, representing success. ZIO effects are not actually functions, of course, they can model synchronous, asynchronous, concurrent, parallel, and resourceful computations.

ZIO effects use a fiber-based concurrency model, with built-in support for
scheduling, fine-grained interruption, structured concurrency, and high scalability.

The `ZIO[R, E, A]` data type has three type parameters:

 - **`R` - Environment Type**. The effect requires an environment of type `R`. If this type parameter is `Any`, it means the effect has no requirements, because we can run the effect with any value (for example, the unit value `()`).
 - **`E` - Failure Type**. The effect may fail with a value of type `E`. Some applications will use `Throwable`. If this type parameter is `Nothing`, it means the effect cannot fail, because there are no values of type `Nothing`.
 - **`A` - Success Type**. The effect may succeed with a value of type `A`. If this type parameter is `Unit`, it means the effect produces no useful information, while if it is `Nothing`, it means the effect runs forever (or until failure).

In the following example, the `readLine` function does not require any services, it may fail with value of type `IOException`, or may succeed with a value of type `String`:

```scala mdoc:compile-only
import zio._
import java.io.IOException

val readLine: ZIO[Any, IOException, String] =
  Console.readLine
```

`ZIO` values are immutable, and all `ZIO` functions produce new `ZIO` values, enabling `ZIO` to be reasoned about and used like any ordinary Scala immutable data structure.

`ZIO` values do not actually _do_ anything; they are just values that _model_ or _describe_ effectful interactions.

`ZIO` can be _interpreted_ by the ZIO runtime system into effectful interactions with the external world. Ideally, this occurs at a single time, in our application's `main` function. The `App` class provides this functionality automatically.

## Creation

In this section we explore some of the common ways to create ZIO effects from values, from common Scala types, and from both synchronous and asynchronous side-effects. Here is the summary list of them:


### Success Values

Using the `ZIO.succeed` method, we can create an effect that succeeds with the specified value:

```scala mdoc:compile-only
import zio._

val s1 = ZIO.succeed(42)
```

We can also use methods in the companion objects of the `ZIO` type aliases:

```scala mdoc:compile-only
import zio._

val s2: Task[Int] = Task.succeed(42)
```

### Failure Values

Using the `ZIO.fail` method, we can create an effect that models failure:

```scala mdoc:compile-only
import zio._

val f1 = ZIO.fail("Uh oh!")
```

For the `ZIO` data type, there is no restriction on the error type. We may use strings, exceptions, or custom data types appropriate for our application.

Many applications will model failures with classes that extend `Throwable` or `Exception`:

```scala mdoc:compile-only
import zio._

val f2 = Task.fail(new Exception("Uh oh!"))
```

Note that unlike the other effect companion objects, the `UIO` companion object does not have `UIO.fail`, because `UIO` values cannot fail.


### From Values
ZIO contains several constructors which help us to convert various data types into `ZIO` effects.

#### Option

1. **`ZIO.fromOption`**— An `Option` can be converted into a ZIO effect using `ZIO.fromOption`:

```scala mdoc:silent
import zio._

val zoption: IO[Option[Nothing], Int] = ZIO.fromOption(Some(2))
```

The error type of the resulting effect is `Option[Nothing]`, which provides no information on why the value is not there. We can change the `Option[Nothing]` into a more specific error type using `ZIO#mapError`:

```scala mdoc:compile-only
import zio._

val zoption2: IO[String, Int] = zoption.mapError(_ => "It wasn't there!")
```

```scala mdoc:invisible:reset

```

We can also readily compose it with other operators while preserving the optional nature of the result (similar to an `OptionT`):

```scala mdoc:invisible
trait Team
```

```scala mdoc:compile-only
import zio._

val maybeId: IO[Option[Nothing], String] = ZIO.fromOption(Some("abc123"))
def getUser(userId: String): IO[Throwable, Option[User]] = ???
def getTeam(teamId: String): IO[Throwable, Team] = ???


val result: IO[Throwable, Option[(User, Team)]] = (for {
  id   <- maybeId
  user <- getUser(id).some
  team <- getTeam(user.teamId).asSomeError 
} yield (user, team)).unsome 
```

2. **`ZIO.some`**/**`ZIO.none`**— These constructors can be used to directly create ZIO of optional values:

```scala mdoc:compile-only
import zio._

val someInt: ZIO[Any, Nothing, Option[Int]]     = ZIO.some(3)
val noneInt: ZIO[Any, Nothing, Option[Nothing]] = ZIO.none
``` 

3. **`ZIO.getOrFail`**— We can lift an `Option` into a `ZIO` and if the option is not defined we can fail the ZIO with the proper error type:
  - `ZIO.getOrFail` fails with `Throwable` error type.
  - `ZIO.getOrFailUnit` fails with `Unit` error type.
  - `ZIO.getOrFailWith` fails with custom error type.

```scala mdoc:compile-only
import zio._

def parseInt(input: String): Option[Int] = input.toIntOption

// If the optional value is not defined it fails with Throwable error type: 
val r1: ZIO[Any, Throwable, Int] =
  ZIO.getOrFail(parseInt("1.2"))

// If the optional value is not defined it fails with Unit error type:
val r2: ZIO[Any, Unit, Int] =
  ZIO.getOrFailUnit(parseInt("1.2"))

// If the optional value is not defined it fail with given error type:
val r3: ZIO[Any, NumberFormatException, Int] =
  ZIO.getOrFailWith(new NumberFormatException("invalid input"))(parseInt("1.2"))
```

4. **`ZIO.nonOrFail`**— It lifts an option into a ZIO value. If the option is empty it succeeds with `Unit` and if the option is defined it fails with a proper error type: 
  - `ZIO.nonOrFail` fails with the content of the optional value.
  - `ZIO.nonOrFailUnit` fails with the `Unit` error type.
  - `ZIO.nonOrFailWith` fails with custom error type.

```scala mdoc:compile-only
import zio._ 

val optionalValue: Option[String] = ???

// If the optional value is empty it succeeds with Unit
// If the optional value is defined it will fail with the content of the optional value
val r1: ZIO[Any, String, Unit] = 
  ZIO.noneOrFail(optionalValue)

// If the optional value is empty it succeeds with Unit
// If the optional value is defined, it will fail by applying the error function to it:
val r2: ZIO[Any, NumberFormatException, Unit] = 
  ZIO.noneOrFailWith(optionalValue)(e => new NumberFormatException(e))
```

#### Either

| Function     | Input Type     | Output Type               |
|--------------|----------------|---------------------------|
| `fromEither` | `Either[E, A]` | `IO[E, A]`                |
| `left`       | `A`            | `UIO[Either[A, Nothing]]` |
| `right`      | `A`            | `UIO[Either[Nothing, A]]` |

An `Either` can be converted into a ZIO effect using `ZIO.fromEither`:

```scala mdoc:compile-only
import zio._

val zeither = ZIO.fromEither(Right("Success!"))
```

The error type of the resulting effect will be whatever type the `Left` case has, while the success type will be whatever type the `Right` case has.

#### Try

| Function  | Input Type          | Output Type |
|-----------|---------------------|-------------|
| `fromTry` | `scala.util.Try[A]` | `Task[A]`   |

A `Try` value can be converted into a ZIO effect using `ZIO.fromTry`:

```scala mdoc:compile-only
import zio._
import scala.util.Try

val ztry = ZIO.fromTry(Try(42 / 0))
```

The error type of the resulting effect will always be `Throwable`, because `Try` can only fail with values of type `Throwable`.

#### Future

| Function              | Input Type                                       | Output Type        |
|-----------------------|--------------------------------------------------|--------------------|
| `fromFuture`          | `ExecutionContext => scala.concurrent.Future[A]` | `Task[A]`          |
| `fromFutureJava`      | `java.util.concurrent.Future[A]`                 | `RIO[Blocking, A]` |
| `fromFunctionFuture`  | `R => scala.concurrent.Future[A]`                | `RIO[R, A]`        |
| `fromFutureInterrupt` | `ExecutionContext => scala.concurrent.Future[A]` | `Task[A]`          |

A `Future` can be converted into a ZIO effect using `ZIO.fromFuture`:

```scala mdoc:compile-only
import zio._
import scala.concurrent.Future

lazy val future = Future.successful("Hello!")

val zfuture: Task[String] =
  ZIO.fromFuture { implicit ec =>
    future.map(_ => "Goodbye!")
  }
```

The function passed to `fromFuture` is passed an `ExecutionContext`, which allows ZIO to manage where the `Future` runs (of course, we can ignore this `ExecutionContext`).

The error type of the resulting effect will always be `Throwable`, because `Future` can only fail with values of type `Throwable`.

#### Promise
| Function           | Input Type                    | Output Type |
|--------------------|-------------------------------|-------------|
| `fromPromiseScala` | `scala.concurrent.Promise[A]` | `Task[A]`   |

A `Promise` can be converted into a ZIO effect using `ZIO.fromPromiseScala`:

```scala mdoc:compile-only
import zio._
import scala.util._

val func: String => String = s => s.toUpperCase
for {
  promise <- ZIO.succeed(scala.concurrent.Promise[String]())
  _ <- ZIO.attempt {
    Try(func("hello world from future")) match {
      case Success(value) => promise.success(value)
      case Failure(exception) => promise.failure(exception)
    }
  }.fork
  value <- ZIO.fromPromiseScala(promise)
  _ <- Console.printLine(s"Hello World in UpperCase: $value")
} yield ()
```

#### Fiber

| Function       | Input Type           | Output Type |
|----------------|----------------------|-------------|
| `fromFiber`    | `Fiber[E, A]`        | `IO[E, A]`  |
| `fromFiberZIO` | `IO[E, Fiber[E, A]]` | `IO[E, A]`  |

A `Fiber` can be converted into a ZIO effect using `ZIO.fromFiber`:

```scala mdoc:compile-only
import zio._

val io: IO[Nothing, String] = ZIO.fromFiber(Fiber.succeed("Hello from Fiber!"))
```

### From Side-Effects

ZIO can convert both synchronous and asynchronous side-effects into ZIO effects (pure values).

These functions can be used to wrap procedural code, allowing us to seamlessly use all features of ZIO with legacy Scala and Java code, as well as third-party libraries.


#### Synchronous

| Function  | Input Type | Output Type | Note                                        |
|-----------|------------|-------------|---------------------------------------------|
| `succeed` | `A`        | `UIO[A]`    | Imports a total synchronous effect          |
| `attempt` | `A`        | Task[A]     | Imports a (partial) synchronous side-effect |

A synchronous side-effect can be converted into a ZIO effect using `ZIO.attempt`:

```scala mdoc:compile-only
import zio._
import scala.io.StdIn

val getLine: Task[String] =
  ZIO.attempt(StdIn.readLine())
```

The error type of the resulting effect will always be `Throwable`, because side-effects may throw exceptions with any value of type `Throwable`. 

If a given side-effect is known to not throw any exceptions, then the side-effect can be converted into a ZIO effect using `ZIO.succeed`:

```scala mdoc:compile-only
import zio._

def printLine(line: String): UIO[Unit] =
  ZIO.succeed(println(line))

val succeedTask: UIO[Long] =
  ZIO.succeed(java.lang.System.nanoTime())
```

We should be careful when using `ZIO.succeed`—when in doubt about whether or not a side-effect is total, prefer `ZIO.attempt` to convert the effect.

If this is too broad, the `refineOrDie` method of `ZIO` may be used to retain only certain types of exceptions, and to die on any other types of exceptions:

```scala mdoc:compile-only
import zio._
import java.io.IOException

val printLine2: IO[IOException, String] =
  ZIO.attempt(scala.io.StdIn.readLine()).refineToOrDie[IOException]
```

##### Blocking Synchronous Side-Effects

| Function                   | Input Type                          | Output Type                     |
|----------------------------|-------------------------------------|---------------------------------|
| `blocking`                 | `ZIO[R, E, A]`                      | `ZIO[R, E, A]`                  |
| `attemptBlocking`          | `A`                                 | `RIO[Blocking, A]`              |
| `attemptBlockingCancelable` | `effect: => A`, `cancel: UIO[Unit]` | `RIO[Blocking, A]`              |
| `attemptBlockingInterrupt`  | `A`                                 | `RIO[Blocking, A]`              |
| `attemptBlockingIO`         | `A`                                 | `ZIO[Blocking, IOException, A]` |

Some side-effects use blocking IO or otherwise put a thread into a waiting state. If not carefully managed, these side-effects can deplete threads from our application's main thread pool, resulting in work starvation.

ZIO provides the `zio.blocking` package, which can be used to safely convert such blocking side-effects into ZIO effects.

A blocking side-effect can be converted directly into a ZIO effect blocking with the `attemptBlocking` method:

```scala mdoc:compile-only
import zio._

val sleeping =
  ZIO.attemptBlocking(Thread.sleep(Long.MaxValue))
```

The resulting effect will be executed on a separate thread pool designed specifically for blocking effects.

Blocking side-effects can be interrupted by invoking `Thread.interrupt` using the `attemptBlockingInterrupt` method.

Some blocking side-effects can only be interrupted by invoking a cancellation effect. We can convert these side-effects using the `attemptBlockingCancelable` method:

```scala mdoc:compile-only
import zio._
import java.net.ServerSocket

def accept(l: ServerSocket) =
  ZIO.attemptBlockingCancelable(l.accept())(UIO.succeed(l.close()))
```

If a side-effect has already been converted into a ZIO effect, then instead of `attemptBlocking`, the `blocking` method can be used to ensure the effect will be executed on the blocking thread pool:

```scala mdoc:compile-only
import zio._
import scala.io.{ Codec, Source }

def download(url: String) =
  Task.attempt {
    Source.fromURL(url)(Codec.UTF8).mkString
  }

def safeDownload(url: String) =
  ZIO.blocking(download(url))
```

#### Asynchronous

| Function         | Input Type                                                    | Output Type    |
|------------------|---------------------------------------------------------------|----------------|
| `async`          | `(ZIO[R, E, A] => Unit) => Any`                               | `ZIO[R, E, A]` |
| `asyncZIO`       | `(ZIO[R, E, A] => Unit) => ZIO[R, E, Any]`                    | `ZIO[R, E, A]` |
| `asyncMaybe`     | `(ZIO[R, E, A] => Unit) => Option[ZIO[R, E, A]]`              | `ZIO[R, E, A]` |
| `asyncInterrupt` | `(ZIO[R, E, A] => Unit) => Either[URIO[R, Any], ZIO[R, E, A]]` | `ZIO[R, E, A]` |

An asynchronous side-effect with a callback-based API can be converted into a ZIO effect using `ZIO.async`:

```scala mdoc:invisible
trait User { 
  def teamId: String
}
trait AuthError
```

```scala mdoc:silent
import zio._

object legacy {
  def login(
    onSuccess: User => Unit,
    onFailure: AuthError => Unit): Unit = ???
}

val login: ZIO[Any, AuthError, User] =
  ZIO.async[Any, AuthError, User] { callback =>
    legacy.login(
      user => callback(ZIO.succeed(user)),
      err  => callback(ZIO.fail(err))
    )
  }
```

Asynchronous ZIO effects are much easier to use than callback-based APIs, and they benefit from ZIO features like interruption, resource-safety, and superior error handling.

### Creating Suspended Effects

| Function                 | Input Type                                 | Output Type    |
|--------------------------|--------------------------------------------|----------------|
| `suspend`                | `RIO[R, A]`                                | `RIO[R, A]`    |
| `suspendSucceed`         | `ZIO[R, E, A]`                             | `ZIO[R, E, A]` |
| `suspendSucceedWith`     | `(RuntimeConfig, FiberId) => ZIO[R, E, A]` | `ZIO[R, E, A]` |
| `suspendWith`            | `(RuntimeConfig, FiberId) => RIO[R, A]`    | `RIO[R, A]`    |

A `RIO[R, A]` effect can be suspended using `suspend` function:

```scala mdoc:compile-only
import zio._
import java.io.IOException

val suspendedEffect: RIO[Any, ZIO[Any, IOException, Unit]] =
  ZIO.suspend(ZIO.attempt(Console.printLine("Suspended Hello World!")))
```

## Control Flows

Although we have access to built-in scala control flow structures, ZIO has several control flow combinators. In this section, we are going to introduce different ways of controlling flows in ZIO applications.

### If Expressions and Operators

When working with ZIO values, we can also work with built-in scala if-then-else expressions:

````scala mdoc:compile-only
import zio._

def validateWeightOption(weight: Double): ZIO[Any, Nothing, Option[Double]] =
  if (weight >= 0)
    ZIO.some(weight)
  else
    ZIO.none
````

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

We can also use ZIO's combinators that are the moral equivalent to these expressions:

1. **`ZIO.when`/`ZIO#when`**— Instead of `if (p) expression` we can use the `ZIO.when` or `ZIO#when` operator:

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

2. **`ZIO.unless`/`ZIO#unless`**— These operators are like `when` operators, but they are moral equivalent for the `if (!p) expression` construct.

3. **`ZIO#ifZIO`**— This operator takes an _effectful predicate_, if that predicate is evaluated to true, it will run the `onTrue` effect, otherwise it will run the `onFalse` effect.

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

### Loops

In imperative scala code base, sometime we may use `while(condition) { statement }` or `do { statement } while (condition)` structs to perform loops:

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

But in functional scala, we tend to not use mutable variable. So to have a loop, we would like to use recursions. Let's rewrite the previous example, using recursion:

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

After this very short introduction to writing loops in functional scala, now let us go deep into ZIO specific combinators for writing loops:

1. **`ZIO.loop`/`ZIO.loopDiscard`**— It takes an initial state, then repeatedly change the state based on the given `inc` function, until the given `cont` function is evaluated to true:

```scala
object ZIO {
  def loop[R, E, A, S](
    initial: => S
  )(cont: S => Boolean, inc: S => S)(body: S => ZIO[R, E, A]): ZIO[R, E, List[A]]
  
  def loopDiscard[R, E, S](
    initial: => S
  )(cont: S => Boolean, inc: S => S)(body: S => ZIO[R, E, Any]): ZIO[R, E, Unit]
```

The `ZIO#loop` collects all intermediate states in a list and returns it finally, while the `loopDiscard` discards all results.

We can think of `ZIO#loop` as a moral equivalent of the following while loop:

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

2. **`ZIO.iterate`**— To iterate with the given effectful operation we can use this combinator. During each iteration, it uses an effectful `body` operation to change the state, and it will continue the iteration while the `cont` function evaluates to true:

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

Note that, in several cases, we can avoid these low-level operators and instead use the high-level ones. For example, let's try to rewrite the `r5` with `ZIO.foreach`:

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

### try/catch/finally

When working with resources, just like the scala's `try`/`catch`/`finally` construct, in ZIO we have a similar operator called `acquireRelease` and also `ensuring`. We discussed them in more detail in the [resource management section](#resource-management). But, for now, we want to focus on their control flow behaviors.

Let's learn about the `ZIO.acquireReleaseWith` operator. This operator takes three effects:
1. **`acquire`** an effect that describes the resource acquisition
2. **`release`** an effect that describes the release of the resource
3. **`use`** an effect that describes resource usage

```scala mdoc:compile-only
ZIO.acquireReleaseWith(
  acquire = ???,
  release = ???,
  use       = ???
)
```

This operator guarantees us that if the _resource acquisition (acquire)_  the _release_ effect will be executed whether the _use_ effect succeeded or not:

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

  ZIO.acquireReleaseWith(
    acquire = openFile(fileName),
    release = (s: Source) => closeFile(s),
    use     = (s: Source) => wordCount(s)
  )
}
```

Let's try a simple `acquireRelease` workflow to see how its control flow works:

```scala mdoc:compile-only
object MainApp extends ZIOAppDefault {
  def run =
    ZIO.acquireReleaseWith(
      acquire = ZIO.succeed("resource").tap(r => ZIO.debug(s"$r acquired")),
      release = (i: String) => ZIO.debug(s"$i released"),
      use = (i: String) => ZIO.debug(s"start using $i")
    )
}
// Output:
// resource acquired
// start using resource
// resource released
```

## Blocking Operations

ZIO provides access to a thread pool that can be used for performing blocking operations, such as thread sleeps, synchronous socket/file reads, and so forth.

By default, ZIO is asynchronous and all effects will be executed on a default primary thread pool which is optimized for asynchronous operations. As ZIO uses a fiber-based concurrency model, if we run **Blocking I/O** or **CPU Work** workloads on a primary thread pool, they are going to monopolize all threads of **primary thread pool**.

In the following example, we create 20 blocking tasks to run parallel on the primary async thread pool. Assume we have a machine with an 8 CPU core, so the ZIO creates a thread pool of size 16 (2 * 8). If we run this program, all of our threads got stuck, and the remaining 4 blocking tasks (20 - 16) haven't any chance to run on our thread pool:

```scala mdoc:silent
import zio._

def blockingTask(n: Int): UIO[Unit] =
  Console.printLine(s"running blocking task number $n").orDie *>
    ZIO.succeed(Thread.sleep(3000)) *>
    blockingTask(n)

val program = ZIO.foreachPar((1 to 100).toArray)(blockingTask)
```

### Creating Blocking Effects

ZIO has a separate **blocking thread pool** specially designed for **Blocking I/O** and, also **CPU Work** workloads. We should run blocking workloads on this thread pool to prevent interfering with the primary thread pool.

The contract is that the thread pool will accept unlimited tasks (up to the available memory) and continuously create new threads as necessary.

The `blocking` operator takes a ZIO effect and return another effect that is going to run on a blocking thread pool:

```scala mdoc:invisible:nest
val program = ZIO.foreachPar((1 to 100).toArray)(t => ZIO.blocking(blockingTask(t)))
```

```scala mdoc:invisible:reset

```

Also, we can directly import a synchronous effect that does blocking IO into ZIO effect by using `attemptBlocking`:

```scala mdoc:compile-only
import zio._

def blockingTask(n: Int) = ZIO.attemptBlocking {
  do {
    println(s"Running blocking task number $n on dedicated blocking thread pool")
    Thread.sleep(3000) 
  } while (true)
}
```

### Interruption of Blocking Operations

By default, when we convert a blocking operation into the ZIO effects using `attemptBlocking`, there is no guarantee that if that effect is interrupted the underlying effect will be interrupted.

Let's create a blocking effect from an endless loop:

```scala mdoc:compile-only
import zio._

for {
  _ <- Console.printLine("Starting a blocking operation")
  fiber <- ZIO.attemptBlocking {
    while (true) {
      Thread.sleep(1000)
      println("Doing some blocking operation")
    }
  }.ensuring(
    Console.printLine("End of a blocking operation").orDie
  ).fork
  _ <- fiber.interrupt.schedule(
    Schedule.delayed(
      Schedule.duration(1.seconds)
    )
  )
} yield ()
```

When we interrupt this loop after one second, it will still not stop. It will only stop when the entire JVM stops. So the `attemptBlocking` doesn't translate the ZIO interruption into thread interruption (`Thread.interrupt`).

Instead, we should use `attemptBlockingInterrupt` to create interruptible blocking effects:

```scala mdoc:compile-only
import zio._

for {
  _ <- Console.printLine("Starting a blocking operation")
  fiber <- ZIO.attemptBlockingInterrupt {
    while(true) {
      Thread.sleep(1000)
      println("Doing some blocking operation")
    }
  }.ensuring(
     Console.printLine("End of the blocking operation").orDie
   ).fork
  _ <- fiber.interrupt.schedule(
    Schedule.delayed(
      Schedule.duration(3.seconds)
    )
  )
} yield ()
```

Notes:

1. If we are converting a blocking I/O to the ZIO effect, it would be better to use `attemptBlockingIO` which refines the error type to the `java.io.IOException`.

2. The `attemptBlockingInterrupt` method adds significant overhead. So for performance-sensitive applications, it is better to handle interruptions manually using `attemptBlockingCancelable`.

### Cancellation of Blocking Operation

Some blocking operations do not respect `Thread#interrupt` by swallowing `InterruptedException`. So, they will not be interrupted via `attemptBlockingInterrupt`. Instead, they may provide us an API to signal them to _cancel_ their operation.

The following `BlockingService` will not be interrupted in case of `Thread#interrupt` call, but it checks the `released` flag constantly. If this flag becomes true, the blocking service will finish its job:

```scala mdoc:silent
import zio._
import java.util.concurrent.atomic.AtomicReference

final case class BlockingService() {
  private val released = new AtomicReference(false)

  def start(): Unit = {
    while (!released.get()) {
      println("Doing some blocking operation")
      try Thread.sleep(1000)
      catch {
        case _: InterruptedException => () // Swallowing InterruptedException
      }
    }
    println("Blocking operation closed.")
  }

  def close(): Unit = {
    println("Releasing resources and ready to be closed.")
    released.getAndSet(true)
  }
}
```

So, to translate ZIO interruption into cancellation of these types of blocking operations we should use `attemptBlockingCancelable`. This method takes a `cancel` effect which is responsible to signal the blocking code to close itself when ZIO interruption occurs:

```scala mdoc:compile-only
import zio._

val myApp =
  for {
    service <- ZIO.attempt(BlockingService())
    fiber   <- ZIO.attemptBlockingCancelable(
      effect = service.start()
    )(
      cancel = UIO.succeed(service.close())
    ).fork
    _       <- fiber.interrupt.schedule(
      Schedule.delayed(
        Schedule.duration(3.seconds)
      )
    )
  } yield ()
```

Here is another example of the cancelation of a blocking operation. When we `accept` a server socket, this blocking operation will never be interrupted until we close that using `ServerSocket#close` method:

```scala mdoc:compile-only
import java.net.{Socket, ServerSocket}
import zio._

def accept(ss: ServerSocket): Task[Socket] =
  ZIO.attemptBlockingCancelable(ss.accept())(UIO.succeed(ss.close()))
```

## Mapping

### map
We can change an `IO[E, A]` to an `IO[E, B]` by calling the `map` method with a function `A => B`. This lets us transform values produced by actions into other values.

```scala mdoc:compile-only
import zio._

val mappedValue: UIO[Int] = IO.succeed(21).map(_ * 2)
```


## Tapping

Using `ZIO.tap` we can peek into a success value and perform any effectful operation, without changing the returning value of the original effect:

```scala
trait ZIO[-R, +E, +A] {
  def tap[R1 <: R, E1 >: E](f: A => ZIO[R1, E1, Any]): ZIO[R1, E1, A]
  def tapSome[R1 <: R, E1 >: E](f: PartialFunction[A, ZIO[R1, E1, Any]]): ZIO[R1, E1, A]
}
```

```scala mdoc:compile-only
import zio._

import java.io.IOException

object MainApp extends ZIOAppDefault {
  def isPrime(n: Int): Boolean =
    if (n <= 1) false else (2 until n).forall(i => n % i != 0)

  val myApp: ZIO[Any, IOException, Unit] =
    for {
      ref <- Ref.make(List.empty[Int])
      prime <-
        Random
          .nextIntBetween(0, Int.MaxValue)
          .tap(random => ref.update(_ :+ random))
          .repeatUntil(isPrime)
      _ <- Console.printLine(s"found a prime number: $prime")
      tested <- ref.get
      _ <- Console.printLine(
        s"list of tested numbers: ${tested.mkString(", ")}"
      )
    } yield ()

  def run = myApp
}
```

## Chaining

We can execute two actions in sequence with the `flatMap` method. The second action may depend on the value produced by the first action.

```scala mdoc:compile-only
import zio._

val chainedActionsValue: UIO[List[Int]] = IO.succeed(List(1, 2, 3)).flatMap { list =>
  IO.succeed(list.map(_ + 1))
}
```

If the first effect fails, the callback passed to `flatMap` will never be invoked, and the composed effect returned by `flatMap` will also fail.

In _any_ chain of effects, the first failure will short-circuit the whole chain, just like throwing an exception will prematurely exit a sequence of statements.

Because the `ZIO` data type supports both `flatMap` and `map`, we can use Scala's _for comprehensions_ to build sequential effects:

```scala mdoc:compile-only
import zio._

val program = 
  for {
    _    <- Console.printLine("Hello! What is your name?")
    name <- Console.readLine
    _    <- Console.printLine(s"Hello, ${name}, welcome to ZIO!")
  } yield ()
```

_For comprehensions_ provide a more procedural syntax for composing chains of effects.

## Zipping

We can combine two effects into a single effect with the `zip` method. The resulting effect succeeds with a tuple that contains the success values of both effects:

```scala mdoc:compile-only
import zio._

val zipped: UIO[(String, Int)] = 
  ZIO.succeed("4").zip(ZIO.succeed(2))
```

Note that `zip` operates sequentially: the effect on the left side is executed before the effect on the right side.

In any `zip` operation, if either the left or right-hand sides fail, then the composed effect will fail, because _both_ values are required to construct the tuple.

### zipLeft and zipRight

Sometimes, when the success value of an effect is not useful (for example, it is `Unit`), it can be more convenient to use the `zipLeft` or `zipRight` functions, which first perform a `zip`, and then map over the tuple to discard one side or the other:

```scala mdoc:compile-only
import zio._

val zipRight1 = 
  Console.printLine("What is your name?").zipRight(Console.readLine)
```

The `zipRight` and `zipLeft` functions have symbolic aliases, known as `*>` and `<*`, respectively. Some developers find these operators easier to read:

```scala mdoc:compile-only
import zio._

val zipRight2 = 
  Console.printLine("What is your name?") *>
  Console.readLine
```

## Parallelism

ZIO provides many operations for performing effects in parallel. These methods are all named with a `Par` suffix that helps us identify opportunities to parallelize our code.

For example, the ordinary `ZIO#zip` method zips two effects together, sequentially. But there is also a `ZIO#zipPar` method, which zips two effects together in parallel.

The following table summarizes some of the sequential operations and their corresponding parallel versions:

| **Description**              | **Sequential**    | **Parallel**         |
| ---------------------------: | :---------------: | :------------------: |
| Zip two effects into one    | `ZIO#zip`         | `ZIO#zipPar`         |
| Zip two effects into one    | `ZIO#zipWith`     | `ZIO#zipWithPar`     |
| Collect from many effects   | `ZIO.collectAll`  | `ZIO.collectAllPar`  |
| Effectfully loop over values | `ZIO.foreach`     | `ZIO.foreachPar`     |
| Reduce many values          | `ZIO.reduceAll`   | `ZIO.reduceAllPar`   |
| Merge many values           | `ZIO.mergeAll`    | `ZIO.mergeAllPar`    |

For all the parallel operations, if one effect fails, then others will be interrupted, to minimize unnecessary computation.

If the fail-fast behavior is not desired, potentially failing effects can be first converted into infallible effects using the `ZIO#either` or `ZIO#option` methods.

### Racing

ZIO lets us race multiple effects in parallel, returning the first successful result:

```scala mdoc:compile-only
import zio._

for {
  winner <- IO.succeed("Hello").race(IO.succeed("Goodbye"))
} yield winner
```

If we want the first success or failure, rather than the first success, then we can use `left.either race right.either`, for any effects `left` and `right`.

## Resource Management

ZIO's resource management features work across synchronous, asynchronous, concurrent, and other effect types, and provide strong guarantees even in the presence of failure, interruption, or defects in the application.

### Finalizing

Scala has a `try` / `finally` construct which helps us to make sure we don't leak resources because no matter what happens in the `try`, the `finally` block will be executed. So we can open files in the `try` block, and then we can close them in the `finally` block, and that gives us the guarantee that we will not leak resources.

#### Asynchronous Try / Finally
The problem with the `try` / `finally` construct is that it only applies to synchronous code, meaning it doesn't work for asynchronous code. ZIO gives us a method called `ensuring` that works with either synchronous or asynchronous actions. So we have a functional `try` / `finally` even for asynchronous regions of our code.

Like `try` / `finally`, the `ensuring` operation guarantees that if an effect begins executing and then terminates (for whatever reason), then the finalizer will begin executing:

```scala mdoc:compile-only
import zio._

val finalizer = 
  UIO.succeed(println("Finalizing!"))

val finalized: IO[String, Unit] = 
  IO.fail("Failed!").ensuring(finalizer)
```

The finalizer is not allowed to fail, which means that it must handle any errors internally.

Like `try` / `finally`, finalizers can be nested, and the failure of any inner finalizer will not affect outer finalizers. Nested finalizers will be executed in reverse order, and linearly (not in parallel).

Unlike `try` / `finally`, `ensuring` works across all types of effects, including asynchronous and concurrent effects.

Here is another example of ensuring that our clean-up action is called before our effect is done:

```scala mdoc:compile-only
import zio._

import zio.Task
var i: Int = 0
val action: Task[String] =
  Task.succeed(i += 1) *>
    Task.fail(new Throwable("Boom!"))
val cleanupAction: UIO[Unit] = UIO.succeed(i -= 1)
val composite = action.ensuring(cleanupAction)
```

> _**Note:**_
> 
> Finalizers offer very powerful guarantees, but they are low-level, and should generally not be used for releasing resources. For higher-level logic built on `ensuring`, see `ZIO#acquireReleaseWith` in the acquire release section.

#### Unstoppable Finalizers

In Scala when we nest `try` / `finally` finalizers, they cannot be stopped. If we have nested finalizers and one of them fails for some sort of catastrophic reason the ones on the outside will still be run and in the correct order. 

```scala 
try {
  try {
    try {
      ...
    } finally f1
  } finally f2
} finally f3
```

Also in ZIO like `try` / `finally`, the finalizers are unstoppable. This means if we have a buggy finalizer that is going to leak some resources, we will leak the minimum amount of resources because all other finalizers will still be run in the correct order.

```scala
val io = ???
io.ensuring(f1)
 .ensuring(f2)
 .ensuring(f3)
```

### AcquireRelease

In Scala the `try` / `finally` is often used to manage resources. A common use for `try` / `finally` is safely acquiring and releasing resources, such as new socket connections or opened files:

```scala 
val handle = openFile(name)

try {
  processFile(handle)
} finally closeFile(handle)
```

ZIO encapsulates this common pattern with `ZIO#acquireRelease`, which allows us to specify an _acquire_ effect, which acquires a resource; a _release_ effect, which releases it; and a _use_ effect, which uses the resource. Acquire release lets us open a file and close the file and no matter what happens when we are using that resource.
 
The release action is guaranteed to be executed by the runtime system, even if the utilize action throws an exception or the executing fiber is interrupted.

Acquire release is a built-in primitive that let us safely acquire and release resources. It is used for a similar purpose as `try` / `catch` / `finally`, only acquire release work with synchronous and asynchronous actions, work seamlessly with fiber interruption, and is built on a different error model that ensures no errors are ever swallowed.

Acquire release consist of an *acquire* action, a *utilize* action (which uses the acquired resource), and a *release* action.

```scala mdoc:invisible
import zio._
import java.io.{ File, IOException }

def openFile(s: String): IO[IOException, File] = IO.attempt(???).refineToOrDie[IOException]
def closeFile(f: File): UIO[Unit] = IO.succeed(???)
def decodeData(f: File): IO[IOException, Unit] = IO.unit
def groupData(u: Unit): IO[IOException, Unit] = IO.unit
```

```scala mdoc:compile-only
import zio._

val groupedFileData: IO[IOException, Unit] = openFile("data.json").acquireReleaseWith(closeFile(_)) { file =>
  for {
    data    <- decodeData(file)
    grouped <- groupData(data)
  } yield grouped
}
```

Acquire releases have compositional semantics, so if an acquire release is nested inside another acquire release, and the outer resource is acquired, then the outer release will always be called, even if, for example, the inner release fails.

Let's look at a full working example on using acquire release:

```scala mdoc:compile-only
import zio._
import java.io.{ File, FileInputStream }
import java.nio.charset.StandardCharsets

object Main extends ZIOAppDefault {

  // run my acquire release
  def run = myAcquireRelease

  def closeStream(is: FileInputStream) =
    ZIO.succeed(is.close())

  def convertBytes(is: FileInputStream, len: Long) =
    Task.attempt {
      val buffer = new Array[Byte](len.toInt)
      is.read(buffer)
      println(new String(buffer, StandardCharsets.UTF_8))
    }

  // myAcquireRelease is just a value. Won't execute anything here until interpreted
  val myAcquireRelease: Task[Unit] = for {
    file   <- ZIO.attempt(new File("/tmp/hello"))
    len    = file.length
    string <- ZIO.attempt(new FileInputStream(file)).acquireReleaseWith(closeStream)(convertBytes(_, len))
  } yield string
}
```

```scala mdoc:invisible:reset

```

## ZIO Aspect

There are two types of concerns in an application, _core concerns_, and _cross-cutting concerns_. Cross-cutting concerns are shared among different parts of our application. We usually find them scattered and duplicated across our application, or they are tangled up with our primary concerns. This reduces the level of modularity of our programs.

A cross-cutting concern is more about _how_ we do something than _what_ we are doing. For example, when we are downloading a bunch of files, creating a socket to download each one is the core concern because it is a question of _what_ rather than the _how_, but the following concerns are cross-cutting ones:
 - Downloading files _sequentially_ or in _parallel_
 - _Retrying_ and _timing out_ the download process
 - _Logging_ and _monitoring_ the download process

So they don't affect the return type of our workflows, but they add some new aspects or change their behavior.

To increase the modularity of our applications, we can separate cross-cutting concerns from the main logic of our programs. ZIO supports this programming paradigm, which is called _ aspect-oriented programming_.

The `ZIO` effect has a data type called `ZIOAspect`, which allows modifying a `ZIO` effect and convert it into a specialized `ZIO` effect. We can add a new aspect to a `ZIO` effect with `@@` syntax like this:

```scala mdoc:compile-only
import zio._

val myApp: ZIO[Any, Throwable, String] =
  ZIO.attempt("Hello!") @@ ZIOAspect.debug
```

As we see, the `debug` aspect doesn't change the return type of our effect, but it adds a new debugging aspect to our effect.

`ZIOAspect` is like a transformer of the `ZIO` effect, which takes a `ZIO` effect and converts it to another `ZIO` effect. We can think of a `ZIOAspect` as a function of type `ZIO[R, E, A] => ZIO[R, E, A]`.

To compose multiple aspects, we can use `@@` operator:

```scala mdoc:compile-only
import zio._

def download(url: String): ZIO[Any, Throwable, Chunk[Byte]] = ZIO.succeed(???)

ZIO.foreachPar(List("zio.dev", "google.com")) { url =>
  download(url) @@
    ZIOAspect.retry(Schedule.fibonacci(1.seconds)) @@
    ZIOAspect.loggedWith[Chunk[Byte]](file => s"Downloaded $url file with size of ${file.length} bytes")
}
```

The order of aspect composition matters. Therefore, if we change the order, the behavior may change.
