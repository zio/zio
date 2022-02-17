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

```scala mdoc:invisible
import zio._
import java.io.IOException
```

```scala mdoc:silent
val readLine: ZIO[Any, IOException, String] =
  Console.readLine
```

`ZIO` values are immutable, and all `ZIO` functions produce new `ZIO` values, enabling `ZIO` to be reasoned about and used like any ordinary Scala immutable data structure.

`ZIO` values do not actually _do_ anything; they are just values that _model_ or _describe_ effectful interactions.

`ZIO` can be _interpreted_ by the ZIO runtime system into effectful interactions with the external world. Ideally, this occurs at a single time, in our application's `main` function. The `App` class provides this functionality automatically.

## Creation

In this section we explore some of the common ways to create ZIO effects from values, from common Scala types, and from both synchronous and asynchronous side-effects. Here is the summary list of them:


### Success Values

| Function  | Input Type | Output Type |
|-----------|------------|-------------|
| `succeed` | `A`        | `UIO[A]`    |

Using the `ZIO.succeed` method, we can create an effect that succeeds with the specified value:

```scala mdoc:silent
val s1 = ZIO.succeed(42)
```

We can also use methods in the companion objects of the `ZIO` type aliases:

```scala mdoc:silent
val s2: Task[Int] = Task.succeed(42)
```

### Failure Values

| Function | Input Type | Output Type      |
|----------|------------|------------------|
| `fail`   | `E`        | `IO[E, Nothing]` |

Using the `ZIO.fail` method, we can create an effect that models failure:

```scala mdoc:silent
val f1 = ZIO.fail("Uh oh!")
```

For the `ZIO` data type, there is no restriction on the error type. We may use strings, exceptions, or custom data types appropriate for our application.

Many applications will model failures with classes that extend `Throwable` or `Exception`:

```scala mdoc:silent
val f2 = Task.fail(new Exception("Uh oh!"))
```

Note that unlike the other effect companion objects, the `UIO` companion object does not have `UIO.fail`, because `UIO` values cannot fail.

### Defects

By providing a `Throwable` value to the `ZIO.die` constructor, we can describe a dying effect:

```scala
object ZIO {
  def die(t: => Throwable): ZIO[Any, Nothing, Nothing]
}
```

Here is an example of such effect, which will die because of encountering _divide by zero_ defect:

```scala mdoc:compile-only
val dyingEffect: ZIO[Any, Nothing, Nothing] = 
  ZIO.die(new ArithmeticException("divide by zero"))
```

The result is the creation of a ZIO effect whose error channel and success channel are both 'Nothing'. In other words, this effect cannot fail and does not produce anything. Instead, it is an effect describing a _defect_ or an _unexpected error_.

Let's see what happens if we run this effect:

```scala mdoc:compile-only
import zio._

object MainApp extends ZIOAppDefault {
  def run = ZIO.die(new ArithmeticException("divide by zero"))
}
```

If we run this effect, the ZIO runtime will print the stack trace that belongs to this defect. So, here is the output:

```scala
timestamp=2022-02-16T13:02:44.057191215Z level=ERROR thread=#zio-fiber-0 message="Exception in thread "zio-fiber-2" java.lang.ArithmeticException: divide by zero
	at MainApp$.$anonfun$run$1(MainApp.scala:4)
	at zio.ZIO$.$anonfun$die$1(ZIO.scala:3384)
	at zio.internal.FiberContext.runUntil(FiberContext.scala:255)
	at zio.internal.FiberContext.run(FiberContext.scala:115)
	at zio.internal.ZScheduler$$anon$1.run(ZScheduler.scala:151)
	at <empty>.MainApp.run(MainApp.scala:4)"
```

The `ZIO.die` constructor is used to manually describe a dying effect because of a defect inside the code.

For example, assume we want to write a `divide` function that takes two numbers and divides the first number by the second. We know that the `divide` function is not defined for zero dominators. Therefore, we should signal an error if division by zero occurs.

We have two choices to implement this function using the ZIO effect:

1. We can divide the first number by the second, and if the second number was zero, we can fail the effect using `ZIO.fail` with the `ArithmeticException` failure value:

```scala mdoc:compile-only
def divide(a: Int, b: Int): ZIO[Any, ArithmeticException, Int] =
  if (b == 0)
    ZIO.fail(new ArithmeticException("divide by zero"))
  else
    ZIO.succeed(a / b)
```

2. We can divide the first number by the second. In the case of zero for the second number, we use `ZIO.die` to kill the effect by sending a signal of `ArithmeticException` as the defect signal:

```scala mdoc:compile-only
def divide(a: Int, b: Int): ZIO[Any, Nothing, Int] =
  if (b == 0)
    ZIO.die(new ArithmeticException("divide by zero")) // Unexpected error
  else
    ZIO.succeed(a / b)
```

So what is the difference between these two approaches? Let's compare the function signature:

```scala
def divide(a: Int, b: Int): ZIO[Any, ArithmeticException, Int]   // using ZIO.fail
def divide(a: Int, b: Int): ZIO[Any, Nothing,             Int]   // using ZIO.die
```

1. The first approach, models the _divide by zero_ error by _failing_ the effect. We call these failures _expected errors_or _typed error_.
2. While the second approach models the _divide by zero_ error by _dying_ the effect. We call these kinds of errors _unexpected errors_, _defects_ or _untyped errors_.

We use the first method when we are handling errors as we expect them, and thus we know how to handle them. In contrast, the second method is used when we aren't expecting those errors in our domain, and we don't know how to handle them. Therefore, we use the _let it crash_ philosophy.

In the second approach, we can see that the `divide` function indicates that it cannot fail. But, it doesn't mean that this function hasn't any defects. ZIO defects are not typed, so they cannot be seen in type parameters.

Note that to create an effect that will die, we shouldn't throw an exception inside the `ZIO.die` constructor, although it works. Instead, the idiomatic way of creating a dying effect is to provide a `Throwable` value into the `ZIO.die` constructor:

```scala mdoc:compile-only
import zio._

val defect1 = ZIO.die(new ArithmeticException("divide by zero"))       // recommended
val defect2 = ZIO.die(throw new ArithmeticException("divide by zero")) // not recommended 
```

Also, if we import a code that may throw an exception, all the exceptions will be translated to the ZIO defect:

```scala mdoc:compile-only
import zio._

val defect3 = ZIO.succeed(throw new Exception("boom!"))
```

Therefore, in the second approach of the `divide` function, we do not require to die the effect in case of the _dividing by zero_ because the JVM itself throws an `ArithmeticException` when the denominator is zero. When we import any code into the `ZIO` effect if any exception is thrown inside that code, will be translated to _ZIO defects_ by default. So the following program is the same as the previous example:

```scala mdoc:compile-only
def divide(a: Int, b: Int): ZIO[Any, Nothing, Int] = 
  ZIO.succeed(a / b)
```

Another important note is that if we `map`/`flatMap` a ZIO effect and then accidentally throw an exception inside the map operation, that exception will be translated to the ZIO defect:

```scala mdoc:compile-only
import zio._

val defect4 = ZIO.succeed(???).map(_ => throw new Exception("Boom!"))
val defect5 = ZIO.attempt(???).map(_ => throw new Exception("Boom!"))
```

### Fatal Errors

The `VirtualMachineError` and all its subtypes are considered fatal errors by the ZIO runtime. So if during the running application, the JVM throws any of these errors like `StackOverflowError`, the ZIO runtime considers it as a catastrophic fatal error. So it will interrupt the whole application immediately without safe resource interruption. None of the `ZIO#catchAll` and `ZIO#catchAllDefects` will catch this fatal error. At most, if the `RuntimeConfig.reportFatal` is enabled, the application will log the stack trace before interrupting the whole application.

Here is an example of manually creating a fatal error. Although we are ignoring all expected and unexpected errors, the fatal error interrupts the whole application.

```scala mdoc:compile-only
import zio._

object MainApp extends ZIOAppDefault {
  def run =
    ZIO
      .attempt(
        throw new StackOverflowError(
          "The call stack pointer exceeds the stack bound."
        )
      )
      .catchAll(_ => ZIO.unit)       // ignoring all expected errors
      .catchAllDefect(_ => ZIO.unit) // ignoring all unexpected errors
}
```

The output will be something like this:

```scala
java.lang.StackOverflowError: The call stack pointer exceeds the stack bound.
	at MainApp$.$anonfun$run$1(MainApp.scala:8)
	at zio.ZIO$.$anonfun$attempt$1(ZIO.scala:2946)
	at zio.internal.FiberContext.runUntil(FiberContext.scala:247)
	at zio.internal.FiberContext.run(FiberContext.scala:115)
	at zio.internal.ZScheduler$$anon$1.run(ZScheduler.scala:151)
**** WARNING ****
Catastrophic error encountered. Application not safely interrupted. Resources may be leaked. Check the logs for more details and consider overriding `RuntimeConfig.reportFatal` to capture context.
```

### Example

Let's write an application that takes numerator and denominator from the user and then print the result back to the user:

```scala mdoc:compile-only
import zio._

object MainApp extends ZIOAppDefault {
  def run =
    for {
      a <- readNumber("Enter the first number  (a): ")
      b <- readNumber("Enter the second number (b): ")
      r <- divide(a, b)
      _ <- Console.printLine(s"a / b: $r").orDie
    } yield ()
    
  def readNumber(msg: String): ZIO[Console, Nothing, Int] =
    Console.print(msg).orDie *> Console.readLine.orDie.map(_.toInt)

  def divide(a: Int, b: Int): ZIO[Any, Nothing, Int] =
    if (b == 0)
      ZIO.die(new ArithmeticException("divide by zero")) // unexpected error
    else
      ZIO.succeed(a / b)
}
```

Now let's try to enter the zero for the second number and see what happens:

```scala
Please enter the first number  (a): 5
Please enter the second number (b): 0
timestamp=2022-02-14T09:39:53.981143209Z level=ERROR thread=#zio-fiber-0 message="Exception in thread "zio-fiber-2" java.lang.ArithmeticException: divide by zero
at MainApp$.$anonfun$divide$1(MainApp.scala:16)
at zio.ZIO$.$anonfun$die$1(ZIO.scala:3384)
at zio.internal.FiberContext.runUntil(FiberContext.scala:255)
at zio.internal.FiberContext.run(FiberContext.scala:115)
at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1130)
at java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:630)
at java.base/java.lang.Thread.run(Thread.java:831)
at <empty>.MainApp.divide(MainApp.scala:16)"
```

As we see, because we entered the zero for the denominator, the `ArithmeticException` defect, makes the application crash.

Defects are any _unexpected errors_ that we are not going to handle. They will propagate through our application stack until they crash the whole.

Defects have many roots, most of them are from a programming error. Errors will happen when we haven't written the application with best practices. For example, one of these practices is that we should validate the inputs before providing them to the `divide` function. So if the user entered the zero as the denominator, we can retry and ask the user to return another number:

```scala mdoc:compile-only
import zio._

object MainApp extends ZIOAppDefault {
  def run =
    for {
      a <- readNumber("Enter the first number  (a): ")
      b <- readNumber("Enter the second number (b): ").repeatUntil(_ != 0)
      r <- divide(a, b)
      _ <- Console.printLine(s"a / b: $r").orDie
    } yield ()
    
  def readNumber(msg: String): ZIO[Console, Nothing, Int] =
    Console.print(msg).orDie *> Console.readLine.orDie.map(_.toInt)

  def divide(a: Int, b: Int): ZIO[Any, Nothing, Int] = ZIO.succeed(a / b)
}
```

Another note about defects is that they are invisible, and they are not typed. We cannot expect what defects will happen by observing the typed error channel. In the above example, when we run the application and enter noninteger input, another defect, which is called `NumberFormatException` will crash the application:

```scala
Enter the first number: five
timestamp=2022-02-14T13:28:03.223395129Z level=ERROR thread=#zio-fiber-0 message="Exception in thread "zio-fiber-2" java.lang.NumberFormatException: For input string: "five"
at java.base/java.lang.NumberFormatException.forInputString(NumberFormatException.java:67)
at java.base/java.lang.Integer.parseInt(Integer.java:660)
at java.base/java.lang.Integer.parseInt(Integer.java:778)
at scala.collection.StringOps$.toInt$extension(StringOps.scala:910)
at MainApp$.$anonfun$run$3(MainApp.scala:7)
at MainApp$.$anonfun$run$3$adapted(MainApp.scala:7)
  ...
at <empty>.MainApp.run(MainApp.scala:7)"
```

The cause of this defect is also is a programming error, which means we haven't validated input when parsing it. So let's try to validate the input, and make sure that is it is a number. We know that if the entered input does not contain a parsable `Int` the `String#toInt` throws the `NumberFormatException` exception. As we want this exception to be typed, we import the `String#toInt` function using the `ZIO.attempt` constructor. Using this constructor the function signature would be as follows:

```scala mdoc:compile-only
import zio._ 

def parseInput(input: String): ZIO[Any, Throwable, Int] =
  ZIO.attempt(input.toInt)
```

To be more specific, we would like to narrow down the error channel to the `NumberFormatException`, so we can use the `refineToOrDie` operator:

```scala mdoc:compile-only
import zio._

def parseInput(input: String): ZIO[Any, NumberFormatException, Int] =
  ZIO.attempt(input.toInt).refineToOrDie[NumberFormatException]
```

Since it is an expected error, and we want to handle it, we typed the error channel as `NumberFormatException`:

```scala mdoc:compile-only
import zio._

object MainApp extends ZIOAppDefault {
  def run =
    for {
      a <- readNumber("Enter the first number  (a): ")
      b <- readNumber("Enter the second number (b): ").repeatUntil(_ != 0)
      r <- divide(a, b)
      _ <- Console.printLine(s"a / b: $r").orDie
    } yield ()
    
  def parseInput(input: String): ZIO[Any, NumberFormatException, Int] =
    ZIO.attempt(input.toInt).refineToOrDie[NumberFormatException]

  def readNumber(msg: String): ZIO[Console, Nothing, Int] =
    (Console.print(msg) *> Console.readLine.flatMap(parseInput))
      .retryUntil(!_.isInstanceOf[NumberFormatException])
      .orDie

  def divide(a: Int, b: Int): ZIO[Any, Nothing, Int] = ZIO.succeed(a / b)
}
```

### From Values
ZIO contains several constructors which help us to convert various data types into `ZIO` effects.

#### Option

| Function        | Input Type               | Output Type              |
|-----------------|--------------------------|--------------------------|
| `fromOption`    | `Option[A]`              | `IO[Option[Nothing], A]` |
| `some`          | `A`                      | `UIO[Option[A]]`         |
| `none`          |                          | `UIO[Option[Nothing]]`   |
| `getOrFail`     | `Option[A]`              | `Task[A]`                |
| `getOrFailUnit` | `Option[A]`              | `IO[Unit, A]`            |
| `getOrFailWith` | `e:=> E, v:=> Option[A]` | `IO[E, A]`               |

An `Option` can be converted into a ZIO effect using `ZIO.fromOption`:

```scala mdoc:silent
val zoption: IO[Option[Nothing], Int] = ZIO.fromOption(Some(2))
```

The error type of the resulting effect is `Option[Nothing]`, which provides no information on why the value is not there. We can change the `Option[Nothing]` into a more specific error type using `ZIO#mapError`:

```scala mdoc:silent
val zoption2: IO[String, Int] = zoption.mapError(_ => "It wasn't there!")
```

We can also readily compose it with other operators while preserving the optional nature of the result (similar to an `OptionT`):

```scala mdoc:invisible
trait Team
```

```scala mdoc:silent
val maybeId: IO[Option[Nothing], String] = ZIO.fromOption(Some("abc123"))
def getUser(userId: String): IO[Throwable, Option[User]] = ???
def getTeam(teamId: String): IO[Throwable, Team] = ???


val result: IO[Throwable, Option[(User, Team)]] = (for {
  id   <- maybeId
  user <- getUser(id).some
  team <- getTeam(user.teamId).asSomeError 
} yield (user, team)).unsome 
```

#### Either

| Function     | Input Type     | Output Type               |
|--------------|----------------|---------------------------|
| `fromEither` | `Either[E, A]` | `IO[E, A]`                |
| `left`       | `A`            | `UIO[Either[A, Nothing]]` |
| `right`      | `A`            | `UIO[Either[Nothing, A]]` |

An `Either` can be converted into a ZIO effect using `ZIO.fromEither`:

```scala mdoc:silent
val zeither = ZIO.fromEither(Right("Success!"))
```

The error type of the resulting effect will be whatever type the `Left` case has, while the success type will be whatever type the `Right` case has.

#### Try

| Function  | Input Type          | Output Type |
|-----------|---------------------|-------------|
| `fromTry` | `scala.util.Try[A]` | `Task[A]`   |

A `Try` value can be converted into a ZIO effect using `ZIO.fromTry`:

```scala mdoc:silent
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

```scala mdoc:silent
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

```scala mdoc:invisible
import scala.util.{Success, Failure}
import zio.Fiber
```

```scala mdoc:silent
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

```scala mdoc:silent
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

```scala mdoc:silent
import scala.io.StdIn

val getLine: Task[String] =
  ZIO.attempt(StdIn.readLine())
```

The error type of the resulting effect will always be `Throwable`, because side-effects may throw exceptions with any value of type `Throwable`. 

If a given side-effect is known to not throw any exceptions, then the side-effect can be converted into a ZIO effect using `ZIO.succeed`:

```scala mdoc:silent
def printLine(line: String): UIO[Unit] =
  ZIO.succeed(println(line))

val succeedTask: UIO[Long] =
  ZIO.succeed(java.lang.System.nanoTime())
```

We should be careful when using `ZIO.succeed`—when in doubt about whether or not a side-effect is total, prefer `ZIO.attempt` to convert the effect.

If this is too broad, the `refineOrDie` method of `ZIO` may be used to retain only certain types of exceptions, and to die on any other types of exceptions:

```scala mdoc:silent
import java.io.IOException

val printLine2: IO[IOException, String] =
  ZIO.attempt(StdIn.readLine()).refineToOrDie[IOException]
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

```scala mdoc:silent

val sleeping =
  ZIO.attemptBlocking(Thread.sleep(Long.MaxValue))
```

The resulting effect will be executed on a separate thread pool designed specifically for blocking effects.

Blocking side-effects can be interrupted by invoking `Thread.interrupt` using the `attemptBlockingInterrupt` method.

Some blocking side-effects can only be interrupted by invoking a cancellation effect. We can convert these side-effects using the `attemptBlockingCancelable` method:

```scala mdoc:silent
import java.net.ServerSocket
import zio.UIO

def accept(l: ServerSocket) =
  ZIO.attemptBlockingCancelable(l.accept())(UIO.succeed(l.close()))
```

If a side-effect has already been converted into a ZIO effect, then instead of `attemptBlocking`, the `blocking` method can be used to ensure the effect will be executed on the blocking thread pool:

```scala mdoc:silent
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
| `asyncInterrupt` | `(ZIO[R, E, A] => Unit) => Either[Canceler[R], ZIO[R, E, A]]` | `ZIO[R, E, A]` |

An asynchronous side-effect with a callback-based API can be converted into a ZIO effect using `ZIO.async`:

```scala mdoc:invisible
trait User { 
  def teamId: String
}
trait AuthError
```

```scala mdoc:silent
object legacy {
  def login(
    onSuccess: User => Unit,
    onFailure: AuthError => Unit): Unit = ???
}

val login: IO[AuthError, User] =
  IO.async[Any, AuthError, User] { callback =>
    legacy.login(
      user => callback(IO.succeed(user)),
      err  => callback(IO.fail(err))
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

```scala mdoc:silent
val suspendedEffect: RIO[Any, ZIO[Any, IOException, Unit]] =
  ZIO.suspend(ZIO.attempt(Console.printLine("Suspended Hello World!")))
```

## Imperative vs. Functional Error Handling

When practicing imperative programming in Scala, we have the `try`/`catch` language construct for handling errors. Using them, we can wrap regions that may throw exceptions, and handle them in the catch block.

Let's try an example. In the following code we have an age validation function that may throw two exceptions:

```scala mdoc:silent
sealed trait AgeValidationException extends Exception
case class NegativeAgeException(age: Int) extends AgeValidationException
case class IllegalAgeException(age: Int)  extends AgeValidationException

def validate(age: Int): Int = {
  if (age < 0)
    throw NegativeAgeException(age)
  else if (age < 18) 
    throw IllegalAgeException(age)
  else age
}
```

Using `try`/`catch` we can handle exceptions: 

```scala
try {
  validate(17)
} catch {
  case NegativeAgeException(age) => ???
  case IllegalAgeException(age) =>  ???
}
```

There are some issues with error handling using exceptions and `try`/`catch`/`finally` statement:

1. **It lacks type safety on errors** — There is no way to know what errors can be thrown by looking the function signature. The only way to find out in which circumstance a method may throw an exception is to read and investigate its implementation. So the compiler cannot prevent us from type errors. It is also hard for a developer to read the documentation event through reading the documentation is not suffice as it may be obsolete, or it may don't reflect the exact exceptions.

```scala mdoc:invisible:reset

```

```scala mdoc:silent
import zio._

sealed trait AgeValidationException extends Exception
case class NegativeAgeException(age: Int) extends AgeValidationException
case class IllegalAgeException(age: Int)  extends AgeValidationException

def validate(age: Int): ZIO[Any, AgeValidationException, Int] =
  if (age < 0)
    ZIO.fail(NegativeAgeException(age))
  else if (age < 18)
    ZIO.fail(IllegalAgeException(age))
  else ZIO.succeed(age)
```

We can handle errors using `catchAll`/`catchSome` methods:

```scala mdoc:compile-only
validate(17).catchAll {
  case NegativeAgeException(age) => ???
  case IllegalAgeException(age)  => ???
}
```

2. **It doesn't help us to write total functions** — When we use `try`/`catch` the compiler doesn't know about errors at compile-time, so if we forgot to handle one of the exceptions the compiler doesn't help us to write total functions. This code will crash at runtime because we forgot to handle the `IllegalAgeException` case:

```scala
try {
  validate(17)
} catch {
  case NegativeAgeException(age) => ???
  //  case IllegalAgeException(age) => ???
}
```

When we are using typed errors we can have exhaustive checking support of the compiler. So, for example, when we are catching all errors if we forgot to handle one of the cases, the compiler warns us about that:

```scala mdoc
validate(17).catchAll {
  case NegativeAgeException(age) => ???
}

// match may not be exhaustive.
// It would fail on the following input: IllegalAgeException(_)
```

This helps us cover all cases and write _total functions_ easily.

> **Note:**
>
> When a function is defined for all possible input values, it is called a _total function_ in functional programming.

3. **Its error model is broken and lossy** — The error model based on the `try`/`catch`/`finally` statement is broken. Because if we have the combinations of these statements we can throw many exceptions, and then we are only able to catch one of them. All the other ones are lost. They are swallowed into a black hole, and also the one that we catch is the wrong one. It is not the primary cause of the failure. 

To be more specific, if the `try` block throws an exception, and the `finally` block throws an exception as well, then, if these are caught at a higher level, only the finalizer's exception will be caught normally, not the exception from the try block.

In the following example, we are going to show this behavior:

```scala mdoc:silent
 try {
    try throw new Error("e1")
    finally throw new Error("e2")
 } catch {
   case e: Error => println(e) 
 }
 
// Output:
// e2
```

The above program just prints the `e2`, which is lossy. The `e2` is not the primary cause of failure.

In ZIO, all the errors will still be reported. So even though we are only able to catch one error, the other ones will be reported which we have full control over them. They don't get lost.

Let's write a ZIO version:

```scala mdoc:silent
ZIO.fail("e1")
  .ensuring(ZIO.succeed(throw new Exception("e2")))
  .catchAll {
    case "e1" => Console.printLine("e1")
    case "e2" => Console.printLine("e2")
  }

// Output:
// e1
```

ZIO guarantees that no errors are lost. It has a _lossless error model_. This guarantee is provided via a hierarchy of supervisors and information made available via data types such as `Exit` and `Cause`. All errors will be reported. If there's a bug in the code, ZIO enables us to find about it.

## Expected Errors (Errors) vs Unexpected Errors (Defects)

Inside an application, there are two distinct categories of errors:

1. **Expected Errors**— They are also known as _recoverable errors_, _declared errors_ or _errors_.

Expected errors are those errors in which we expected them to happen in normal circumstances, and we can't prevent them. They can be predicted upfront, and we can plan for them. We know when, where, and why they occur. So we know when, where, and how to handle these errors. By handling them we can recover from the failure, this is why we say they are _recoverable errors_. All domain errors, business errors are expected once because we talk about them in workflows and user stories, so we know about them in the context of business flows.

For example, when accessing an external database, that database might be down for some short period of time, so we retry to connect again, or after some number of attempts, we might decide to use an alternative solution, e.g. using an in-memory database.

2. **Unexpected Errors**— _non-recoverable errors_, _defects_.

We know there is a category of things that we are not going to expect and plan for. These are the things we don't expect but of course, we know they are going to happen. We don't know what is the exact root of these errors at runtime, so we have no idea how to handle them. They are actually going to bring down our production application, and then we have to figure out what went wrong to fix them.

For example, the corrupted database file will cause an unexpected error. We can't handle that in runtime. It may be necessary to shut down the whole application in order to prevent further damage.

Most of the unexpected errors are rooted in programming errors. This means, we have just tested the _happy path_, so in case of _unhappy path_ we encounter a defect. When we have defects in our code we have no way of knowing about them otherwise we investigate, test, and fix them.

One of the common programming errors is forgetting to validate unexpected errors that may occur when we expect an input but the input is not valid, while we haven't validated the input. When the user inputs the invalid data, we might encounter the divide by zero exception or might corrupt our service state or a cause similar defect. These kinds of defects are common when we upgrade our service with the new data model for its input, while one of the other services is not upgraded with the new data contract and is calling our service with the deprecated data model. If we haven't a validation phase, they will cause defects!

Another example of defects is memory errors like buffer overflows, stack overflows, out-of-memory, invalid access to null pointers, and so forth. Most of the time these unexpected errors are occurs when we haven't written a memory-safe and resource-safe program, or they might occur due to hardware issues or uncontrollable external problems. We as a developer don't know how to cope with these types of errors at runtime. We should investigate to find the exact root cause of these defects.

As we cannot handle unexpected errors, we should instead log them with their respective stack traces and contextual information. So later we could investigate the problem and try to fix them. The best we can do with unexpected errors is to _sandbox_ them to limit the damage that they do to the overall application. For example, an unexpected error in browser extension shouldn't crash the whole browser.

So the best practice for each of these errors is as follows:

1. **Expected Errors** — we handle expected errors with the aid of the Scala compiler, by pushing them into the type system. In ZIO there is the error type parameter called `E`, and this error type parameter is for modeling all the expected errors in the application.

A ZIO value has a type parameter `E` which is the type of _declared errors_ it can fail with. `E` only covers the errors which were specified at the outset. The same ZIO value could still throw exceptions in unforeseen ways. These unforeseen situations are called _defects_ in a ZIO program, and they lie outside `E`.

Bringing abnormal situations from the domain of defects into that of `E` enables the compiler to help us keep a tab on error conditions throughout the application, at compile time. This helps ensure the handling of domain errors in domain-specific ways.

2. **Unexpected Errors** — We handle unexpected errors by not reflecting them to the type system because there is no way we could do it, and it wouldn't provide any value if we could. At best as we can, we simply sandbox that to some well-defined area of the application.

Note that _defects_, can creep silently to higher levels in our application, and, if they get triggered at all, their handling might eventually be in more general ways.

So for ZIO, expected errors are reflected in the type of the ZIO effect, whereas unexpected errors are not so reflective, and that is the distinction.

That is the best practice. It helps us write better code. The code that we can reason about its error properties and potential expected errors. We can look at the ZIO effect and know how it is supposed to fail.

So to summarize
1. Unexpected errors are impossible to recover and they will eventually shut down the application but expected errors can be recovered by handling them.
2. We do not type unexpected errors, but we type expected errors either explicitly or using general `Throwable` error type.
3. Unexpected errors mostly is a sign of programming errors, but expected errors part of domain errors.

## Don't Type Unexpected Errors

When we first discover typed errors, it may be tempting to put every error into the error type parameter. That is a mistake because we can't recover from all types of errors. When we encounter unexpected errors we can't do anything in those cases. We should let the application die. Let it crash is the erlang philosophy. It is a good philosophy for all unexpected errors. At best, we can sandbox it, but we should let it crash.

The context of a domain determines whether an error is expected or unexpected. When using typed errors, sometimes it is necessary to make a typed-error un-typed because in that case, we can't handle the error, and we should let the application crash.

For example, in the following example, we don't want to handle the `IOException` so we can call `ZIO#orDie` to make the effect's failure unchecked. This will translate effect's failure to the death of the fiber running it:

```scala mdoc:compile-only
import zio._

Console.printLine("Hello, World") // ZIO[Console, IOException, Unit]
  .orDie                          // ZIO[Console, Nothing, Unit]
```

If we have an effect that fails for some `Throwable` we can pick certain recoverable errors out of that, and then we can just let the rest of them kill the fiber that is running that effect. The ZIO effect has a method called `ZIO#refineOrDie` that allows us to do that.

In the following example, calling `ZIO#refineOrDie` on an effect that has an error type `Throwable` allows us to refine it to have an error type of `TemporaryUnavailable`:

```scala mdoc:invisible
import java.net.URL
trait TemporaryUnavailable extends Throwable

trait Response

object httpClient {
  def fetchUrl(url: URL): Response = ???
}

val url = new URL("https://zio.dev")
```

```scala mdoc:compile-only
val response: ZIO[Clock, Nothing, Response] =
  ZIO
    .attemptBlocking(
      httpClient.fetchUrl(url)
    ) // ZIO[Any, Throwable, Response]
    .refineOrDie[TemporaryUnavailable] {
      case e: TemporaryUnavailable => e
    } // ZIO[Any, TemporaryUnavailable, Response]
    .retry(
      Schedule.fibonacci(1.second)
    ) // ZIO[Clock, TemporaryUnavailable, Response]
    .orDie // ZIO[Clock, Nothing, Response]
```

In this example, we are importing the `fetchUrl` which is a blocking operation into a `ZIO` value. We know that in case of a service outage it will throw the `TemporaryUnavailable` exception. This is an expected error, so we want that to be typed. We are going to reflect that in the error type. We only expect it, so we know how to recover from it.

Also, this operation may throw unexpected errors like `OutOfMemoryError`, `StackOverflowError`, and so forth. Therefore, we don't include these errors since we won't be handling them at runtime. They are defects, and in case of unexpected errors, we should let the application crash.

Therefore, it is quite common to import a code that may throw exceptions, whether that uses expected errors for error handling or can fail for a wide variety of unexpected errors like disk unavailable, service unavailable, and so on. Generally, importing these operations end up represented as a `Task` (`ZIO[Any, Throwable, A]`). So in order to make recoverable errors typed, we use the `ZIO#refineOrDie` method.

## Sandboxing

To expose full cause of a failure we can use `ZIO#sandbox` operator:

```scala mdoc:silent
import zio._
def validateCause(age: Int) =
  validate(age) // ZIO[Any, AgeValidationException, Int]
    .sandbox    // ZIO[Any, Cause[AgeValidationException], Int]
```

Now we can see all the failures that occurred, as a type of `Case[E]` at the error channel of the `ZIO` data type. So we can use normal error-handling operators:

```scala mdoc:compile-only
import zio._

validateCause(17).catchAll {
  case Cause.Fail(error: AgeValidationException, _) => ZIO.debug("Caught AgeValidationException failure")
  case Cause.Die(otherDefects, _)                   => ZIO.debug(s"Caught unknown defects: $otherDefects")
  case Cause.Interrupt(fiberId, _)                  => ZIO.debug(s"Caught interruption of a fiber with id: $fiberId")
  case otherCauses                                  => ZIO.debug(s"Caught other causes: $otherCauses")
}
```

Using the `sandbox` operation we are exposing the full cause of an effect. So then we have access to the underlying cause in more detail. After accessing and using causes, we can undo the `sandbox` operation using the `unsandbox` operation. It will submerge the full cause (`Case[E]`) again:

```scala mdoc:compile-only
import zio._

validate(17)  // ZIO[Any, AgeValidationException, Int]
  .sandbox    // ZIO[Any, Cause[AgeValidationException], Int]
  .unsandbox  // ZIO[Any, AgeValidationException, Int]
```

## Model Domain Errors Using Algebraic Data Types

It is best to use _algebraic data types (ADTs)_ when modeling errors within the same domain or subdomain.

Sealed traits allow us to introduce an error type as a common supertype and all errors within a domain are part of that error type by extending that:

```scala
sealed trait UserServiceError extends Exception

case class InvalidUserId(id: ID) extends UserServiceError
case class ExpiredAuth(id: ID)   extends UserServiceError
```


In this case, the super error type is `UserServiceError`. We sealed that trait, and we extend it by two cases, `InvalidUserId` and `ExpiredAuth`. Because it is sealed, if we have a reference to a `UserServiceError` we can match against it and the Scala compiler knows there are two possibilities for a `UserServiceError`:

```scala
userServiceError match {
  case InvalidUserId(id) => ???
  case ExpiredAuth(id)   => ???
}
```

This is a sum type, and also an enumeration. The Scala compiler knows only two of these `UserServiceError` exist. If we don't match on all of them, it is going to warn us. We can add the `-Xfatal-warnings` compiler option which treats warnings as errors. By turning on the fatal warning, we will have type-safety control on expected errors. So sealing these traits gives us great power. 

Also extending all of our errors from a common supertype helps the ZIO's combinators like flatMap to auto widen to the most specific error type.

Let's say we have this for-comprehension here that calls the `userAuth` function, and it can fail with `ExpiredAuth`, and then we call `userProfile` that fails with `InvalidUserID`, and then we call `generateEmail` that can't fail at all, and finally we call `sendEmail` which can fail with `EmailDeliveryError`. We have got a lot of different errors here:

```scala
val myApp: IO[Exception, Receipt] = 
  for {
    service <- userAuth(token)                // IO[ExpiredAuth, UserService]
    profile <- service.userProfile(userId)    // IO[InvalidUserId, Profile]
    body    <- generateEmail(orderDetails)    // IO[Nothing, String]
    receipt <- sendEmail("Your order detail", 
       body, profile.email)                   // IO[EmailDeliveryError, Unit]
  } yield receipt
```

In this example, the flatMap operations auto widens the error type to the most specific error type possible. As a result, the inferred error type of this for-comprehension will be `Exception` which gives us the best information we could hope to get out of this. We have lost information about the particulars of this. We no longer know which of these error types it is. We know it is some type of `Exception` which is more information than nothing. 

## Use Union Types to Be More Specific About Error Types

In Scala 3, we have an exciting new feature called union types. By using the union operator, we can encode multiple error types. Using this facility, we can have more precise information on typed errors. 

Let's see an example of `Storage` service which have `upload`, `download` and `delete` api:

```scala
import zio._

type Name = String

enum StorageError extends Exception {
  case ObjectExist(name: Name)            extends StorageError
  case ObjectNotExist(name: Name)         extends StorageError
  case PermissionDenied(cause: String)    extends StorageError
  case StorageLimitExceeded(limit: Int)   extends StorageError
  case BandwidthLimitExceeded(limit: Int) extends StorageError
}

import StorageError.*

trait Storage {
  def upload(
      name: Name,
      obj: Array[Byte]
  ): ZIO[Any, ObjectExist | StorageLimitExceeded, Unit]

  def download(
      name: Name
  ): ZIO[Any, ObjectNotExist | BandwidthLimitExceeded, Array[Byte]]

  def delete(name: Name): ZIO[Any, ObjectNotExist | PermissionDenied, Unit]
}
```

Union types allow us to get rid of the requirement to extend some sort of common error types like `Exception` or `Throwable`. This allows us to have completely unrelated error types.

In the following example, the `FooError` and `BarError` are two distinct error. They have no super common type like `FooBarError` and also they are not extending `Exception` or `Throwable` classes:

```scala
import zio.*

// Two unrelated errors without having a common supertype
trait FooError
trait BarError

def foo: IO[FooError, Nothing] = ZIO.fail(new FooError {})
def bar: IO[BarError, Nothing] = ZIO.fail(new BarError {})

val myApp: ZIO[Any, FooError | BarError, Unit] = for {
  _ <- foo
  _ <- bar
} yield ()
```

## Don't Reflexively Log Errors

In modern async concurrent applications with a lot of subsystems, if we do not type errors, we are not able to see what section of our code fails with what error. Therefore, this can be very tempting to log errors when they happen. So when we lose type-safety in the whole application it makes us be more sensitive and program defensively. Therefore, whenever we are calling an API we tend to catch its errors, log them as below:

```scala
import zio._

sealed trait UploadError extends Exception
case class FileExist(name: String)          extends UploadError
case class FileNotExist(name: String)       extends UploadError
case class StorageLimitExceeded(limit: Int) extends UploadError

/**
 * This API fail with `FileExist` failure when the provided file name exist.
 */ 
def upload(name: String): Task[Unit] = {
    if (...)  
      ZIO.fail(FileExist(name))
    else if (...)
      ZIO.fail(StorageLimitExceeded(limit)) // This error is undocumented unintentionally
    else
      ZIO.attempt(...)
}

upload("contacts.csv").catchAll {
  case FileExist(name) => delete("contacts.csv") *> upload("contacts.csv")
  case _ =>
    for {
      _ <- ZIO.log(error.toString) // logging the error
      _ <- ZIO.fail(error) // failing again (just like rethrowing exceptions in OOP)
    } yield ()
}
```

In the above code when we see the `upload`'s return type we can't find out what types of error it may fail with. So as a programmer we need to read the API documentation, and see in what cases it may fail. Due to the fact that the documents may be outdated and they may not provide all error cases, we tend to add another case to cover all the other errors. Expert developers may prefer to read the implementation to find out all expected errors, but it is a tedious task to do.

We don't want to lose any errors. So if we do not use typed errors, it makes us defensive to log every error, regardless of whether they will occur or not.

When we are programming with typed errors, that allows us to never lose any errors. Even if we don't handle all, the error channel of our effect type demonstrate the type of remaining errors:

```scala
val myApp: ZIO[Any, UploadError, Unit] = 
  upload("contacts.csv")
    .catchSome { 
      case FileExist(name) => delete(name) *> upload(name)
    }
```

It is still going to be sent an unhandled error type as a result. Therefore, there is no way to lose any errors, and they propagate automatically through all the different subsystems in our application, which means we don't have to be fearful anymore. It will be handled by higher-level code, or if it doesn't it will be passed off to something that can.

If we handle all errors using `ZIO#catchAll` the type of error channel become `Nothing` which means there is no expected error remaining to handle:

```scala
val myApp: ZIO[Any, Nothing, Unit] = 
  upload("contacts.csv")
    .catchAll {
      case FileExist(name) =>
        ZIO.unit // handling FileExist error case
      case StorageLimitExceeded(limit) =>
        ZIO.unit // handling StorageLimitExceeded error case
    }
```

When we type errors, we know that they can't be lost. So typed errors give us the ability to log less. 

## Exceptional and Unexceptional Effects

Besides the `IO` type alias, ZIO has four different type aliases which can be categorized into two different categories:
- **Exceptional Effect** — `Task` and `RIO` are two effects whose error parameter is fixed to `Throwable`, so we call them exceptional effects.
- **Unexceptional Effect** - `UIO` and `URIO` have error parameters that are fixed to `Nothing`, indicating that they are unexceptional effects. So they can't fail, and the compiler knows about it.

So when we compose different effects together, at any point of the codebase we can determine this piece of code can fail or cannot. As a result, typed errors offer a compile-time transition point between this can fail and this can't fail.

For example, the `ZIO.acquireReleaseWith` API asks us to provide three different inputs: _require_, _release_, and _use_. The `release` parameter requires a function from `A` to `URIO[R, Any]`. So, if we put an exceptional effect, it will not compile:

```scala
def acquireReleaseWith[R, E, A, B](
  acquire: => ZIO[R, E, A],
  release: A => URIO[R, Any],
  use: A => ZIO[R, E, B]
): ZIO[R, E, B]
```

## `ZIO#either` and `ZIO#absolve`

The `ZIO#either` convert a `ZIO[R, E, A]` effect to another effect in which its failure (`E`) and success (`A`) channel have been lifted into an `Either[E, A]` data type as success channel of the `ZIO` data type:

```scala mdoc:compile-only
val age: Int = ???

val res: URIO[Any, Either[AgeValidationException, Int]] = validate(age).either 
```

The resulting effect is an unexceptional effect and cannot fail, because the failure case has been exposed as part of the `Either` success case. The error parameter of the returned `ZIO` is `Nothing`, since it is guaranteed the `ZIO` effect does not model failure.

This method is useful for recovering from `ZIO` effects that may fail:

```scala mdoc:compile-only
import zio._
import java.io.IOException

val myApp: ZIO[Console, IOException, Unit] =
  for {
    _ <- Console.print("Please enter your age: ")
    age <- Console.readLine.map(_.toInt)
    res <- validate(age).either
    _ <- res match {
      case Left(error) => ZIO.debug(s"validation failed: $error")
      case Right(age) => ZIO.debug(s"The $age validated!")
    }
  } yield ()
```

The `ZIO#abolve` operator does the inverse. It submerges the error case of an `Either` into the `ZIO`:

```scala mdoc:compile-only
import zio._

val age: Int = ???
validate(age) // ZIO[Any, AgeValidationException, Int]
  .either     // ZIO[Any, Either[AgeValidationException, Int]]
  .absolve    // ZIO[Any, AgeValidationException, Int]
```

## Transform `Option` and `Either` values

It's typical that you work with `Option` and `Either` values inside your application. You either fetch a record from the database which might be there or not (`Option`) or parse a file which might return decode errors `Either`. ZIO has already functions built-in to transform these values into `ZIO` values.

### Either

|from|to|transform|code|
|--|--|--|--|
|`Either[B, A]`|`ZIO[Any, E, A]`|`ifLeft: B => E`|`ZIO.fromEither(from).mapError(ifLeft)`
|`ZIO[R, E, Either[B, A]]`|`ZIO[R, E, A]`|`ifLeft: B => E`|`from.flatMap(ZIO.fromEither(_).mapError(ifLeft))`
|`ZIO[R, E, Either[E, A]]`|`ZIO[R, E, A]`|-|`from.rightOrFail`
|`ZIO[R, E, Either[B, A]]`|`ZIO[R, E, A]`|`f: B => E`|`from.rightOrFailWith(f)`
|`ZIO[R, E, Either[A, E]]`|`ZIO[R, E, A]`|-|`from.leftOrFail`
|`ZIO[R, E, Either[A, B]]`|`ZIO[R, E, A]`|`f: B => E`|`from.leftOrFailWith(f)`
|`ZIO[R, Throwable, Either[Throwable, A]]`|`ZIO[R, Throwable, A]`|-|`from.absolve`

### Option

|from|to|transform|code|
|--|--|--|--|
|`Option[A]`|`ZIO[Any, E, A]`|`ifEmpty: E`|`ZIO.fromOption(from).orElseFail(ifEmpty)`
|`ZIO[R, E, Option[A]]`|`ZIO[R, E, A]`|`ifEmpty: E`|`from.someOrFail(ifEmpty)`

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

Also, we can directly import a synchronous effect that does blocking IO into ZIO effect by using `attemptBlocking`:

```scala mdoc:silent:nest
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

```scala mdoc:silent:nest
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

```scala mdoc:silent:nest
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

```scala mdoc:silent:nest
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

```scala mdoc:silent:nest
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

```scala mdoc:silent:nest
import java.net.{Socket, ServerSocket}
def accept(ss: ServerSocket): Task[Socket] =
  ZIO.attemptBlockingCancelable(ss.accept())(UIO.succeed(ss.close()))
```

## Mapping

### map
We can change an `IO[E, A]` to an `IO[E, B]` by calling the `map` method with a function `A => B`. This lets us transform values produced by actions into other values.

```scala mdoc:silent
import zio.{ UIO, IO }

val mappedValue: UIO[Int] = IO.succeed(21).map(_ * 2)
```

### mapError
We can transform an `IO[E, A]` into an `IO[E2, A]` by calling the `mapError` method with a function `E => E2`.  This lets us transform the failure values of effects:

```scala mdoc:silent
val mappedError: IO[Exception, String] = 
  IO.fail("No no!").mapError(msg => new Exception(msg))
```

> _**Note:**_
>
> Note that mapping over an effect's success or error channel does not change the success or failure of the effect, in the same way that mapping over an `Either` does not change whether the `Either` is `Left` or `Right`.

### mapAttempt
`mapAttempt` returns an effect whose success is mapped by the specified side-effecting `f` function, translating any thrown exceptions into typed failed effects.

Converting literal "Five" String to Int by calling `toInt` is side-effecting because it throws a `NumberFormatException` exception:

```scala mdoc:silent
val task: RIO[Any, Int] = ZIO.succeed("hello").mapAttempt(_.toInt)
```   

`mapAttempt` converts an unchecked exception to a checked one by returning the `RIO` effect.

## Chaining

We can execute two actions in sequence with the `flatMap` method. The second action may depend on the value produced by the first action.

```scala mdoc:silent
val chainedActionsValue: UIO[List[Int]] = IO.succeed(List(1, 2, 3)).flatMap { list =>
  IO.succeed(list.map(_ + 1))
}
```

If the first effect fails, the callback passed to `flatMap` will never be invoked, and the composed effect returned by `flatMap` will also fail.

In _any_ chain of effects, the first failure will short-circuit the whole chain, just like throwing an exception will prematurely exit a sequence of statements.

Because the `ZIO` data type supports both `flatMap` and `map`, we can use Scala's _for comprehensions_ to build sequential effects:

```scala mdoc:silent
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

```scala mdoc:silent
val zipped: UIO[(String, Int)] = 
  ZIO.succeed("4").zip(ZIO.succeed(2))
```

Note that `zip` operates sequentially: the effect on the left side is executed before the effect on the right side.

In any `zip` operation, if either the left or right-hand sides fail, then the composed effect will fail, because _both_ values are required to construct the tuple.

### zipLeft and zipRight

Sometimes, when the success value of an effect is not useful (for example, it is `Unit`), it can be more convenient to use the `zipLeft` or `zipRight` functions, which first perform a `zip`, and then map over the tuple to discard one side or the other:

```scala mdoc:silent
val zipRight1 = 
  Console.printLine("What is your name?").zipRight(Console.readLine)
```

The `zipRight` and `zipLeft` functions have symbolic aliases, known as `*>` and `<*`, respectively. Some developers find these operators easier to read:

```scala mdoc:silent
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

```scala mdoc:silent
for {
  winner <- IO.succeed("Hello").race(IO.succeed("Goodbye"))
} yield winner
```

If we want the first success or failure, rather than the first success, then we can use `left.either race right.either`, for any effects `left` and `right`.

## Timeout

ZIO lets us timeout any effect using the `ZIO#timeout` method, which returns a new effect that succeeds with an `Option`. A value of `None` indicates the timeout elapsed before the effect completed.

```scala mdoc:silent
IO.succeed("Hello").timeout(10.seconds)
```

If an effect times out, then instead of continuing to execute in the background, it will be interrupted so no resources will be wasted.

## Error Management

### Either

| Function      | Input Type                | Output Type             |
|---------------|---------------------------|-------------------------|
| `ZIO#either`  | `ZIO[R, E, A]`            | `URIO[R, Either[E, A]]` |
| `ZIO.absolve` | `ZIO[R, E, Either[E, A]]` | `ZIO[R, E, A]`          |

We can surface failures with `ZIO#either`, which takes a `ZIO[R, E, A]` and produces a `ZIO[R, Nothing, Either[E, A]]`.

```scala mdoc:silent:nest
val zeither: UIO[Either[String, Int]] = 
  IO.fail("Uh oh!").either
```

We can submerge failures with `ZIO.absolve`, which is the opposite of `either` and turns a `ZIO[R, Nothing, Either[E, A]]` into a `ZIO[R, E, A]`:

```scala mdoc:silent
def sqrt(io: UIO[Double]): IO[String, Double] =
  ZIO.absolve(
    io.map(value =>
      if (value < 0.0) Left("Value must be >= 0.0")
      else Right(Math.sqrt(value))
    )
  )
```

### Catching

| Function              | Input Type                                              | Output Type       |
|-----------------------|---------------------------------------------------------|-------------------|
| `ZIO#catchAll`        | `E => ZIO[R1, E2, A1]`                                  | `ZIO[R1, E2, A1]` |
| `ZIO#catchAllCause`   | `Cause[E] => ZIO[R1, E2, A1]`                           | `ZIO[R1, E2, A1]` |
| `ZIO#catchAllDefect`  | `Throwable => ZIO[R1, E1, A1]`                          | `ZIO[R1, E1, A1]` |
| `ZIO#catchAllTrace`   | `((E, Option[ZTrace])) => ZIO[R1, E2, A1]`              | `ZIO[R1, E2, A1]` |
| `ZIO#catchSome`       | `PartialFunction[E, ZIO[R1, E1, A1]]`                   | `ZIO[R1, E1, A1]` |
| `ZIO#catchSomeCause`  | `PartialFunction[Cause[E], ZIO[R1, E1, A1]]`            | `ZIO[R1, E1, A1]` |
| `ZIO#catchSomeDefect` | `PartialFunction[Throwable, ZIO[R1, E1, A1]]`           | `ZIO[R1, E1, A1]` |
| `ZIO#catchSomeTrace`  | `PartialFunction[(E, Option[ZTrace]), ZIO[R1, E1, A1]]` | `ZIO[R1, E1, A1]` |

#### Catching All Errors 
If we want to catch and recover from all types of errors and effectfully attempt recovery, we can use the `catchAll` method:

```scala mdoc:invisible
import java.io.{ FileNotFoundException, IOException }
def readFile(s: String): IO[IOException, Array[Byte]] = 
  ZIO.attempt(???).refineToOrDie[IOException]
```

```scala mdoc:silent
val z: IO[IOException, Array[Byte]] = 
  readFile("primary.json").catchAll(_ => 
    readFile("backup.json"))
```

In the callback passed to `catchAll`, we may return an effect with a different error type (or perhaps `Nothing`), which will be reflected in the type of effect returned by `catchAll`.
#### Catching Some Errors

If we want to catch and recover from only some types of exceptions and effectfully attempt recovery, we can use the `catchSome` method:

```scala mdoc:silent
val data: IO[IOException, Array[Byte]] = 
  readFile("primary.data").catchSome {
    case _ : FileNotFoundException => 
      readFile("backup.data")
  }
```

Unlike `catchAll`, `catchSome` cannot reduce or eliminate the error type, although it can widen the error type to a broader class of errors.

### Fallback

| Function         | Input Type                | Output Type                 |
|------------------|---------------------------|-----------------------------|
| `orElse`         | `ZIO[R1, E2, A1]`         | `ZIO[R1, E2, A1]`           |
| `orElseEither`   | `ZIO[R1, E2, B]`          | `ZIO[R1, E2, Either[A, B]]` |
| `orElseFail`     | `E1`                      | `ZIO[R, E1, A]`             |
| `orElseOptional` | `ZIO[R1, Option[E1], A1]` | `ZIO[R1, Option[E1], A1]`   |
| `orElseSucceed`  | `A1`                      | `URIO[R, A1]`              |

We can try one effect, or, if it fails, try another effect, with the `orElse` combinator:

```scala mdoc:silent
val primaryOrBackupData: IO[IOException, Array[Byte]] = 
  readFile("primary.data").orElse(readFile("backup.data"))
```

### Folding

| Function       | Input Type                                                                       | Output Type      |
|----------------|----------------------------------------------------------------------------------|------------------|
| `fold`         | `failure: E => B, success: A => B`                                               | `URIO[R, B]`     |
| `foldCause`    | `failure: Cause[E] => B, success: A => B`                                        | `URIO[R, B]`     |
| `foldZIO`      | `failure: E => ZIO[R1, E2, B], success: A => ZIO[R1, E2, B]`                     | `ZIO[R1, E2, B]` |
| `foldCauseZIO` | `failure: Cause[E] => ZIO[R1, E2, B], success: A => ZIO[R1, E2, B]`              | `ZIO[R1, E2, B]` |
| `foldTraceZIO` | `failure: ((E, Option[ZTrace])) => ZIO[R1, E2, B], success: A => ZIO[R1, E2, B]` | `ZIO[R1, E2, B]` |

Scala's `Option` and `Either` data types have `fold`, which lets us handle both failure and success at the same time. In a similar fashion, `ZIO` effects also have several methods that allow us to handle both failure and success.

The first fold method, `fold`, lets us non-effectfully handle both failure and success by supplying a non-effectful handler for each case:

```scala mdoc:silent
lazy val DefaultData: Array[Byte] = Array(0, 0)

val primaryOrDefaultData: UIO[Array[Byte]] = 
  readFile("primary.data").fold(
    _    => DefaultData,
    data => data)
```

The second fold method, `foldZIO`, lets us effectfully handle both failure and success by supplying an effectful (but still pure) handler for each case:

```scala mdoc:silent
val primaryOrSecondaryData: IO[IOException, Array[Byte]] = 
  readFile("primary.data").foldZIO(
    _    => readFile("secondary.data"),
    data => ZIO.succeed(data))
```

Nearly all error handling methods are defined in terms of `foldZIO`, because it is both powerful and fast.

In the following example, `foldZIO` is used to handle both failure and success of the `readUrls` method:

```scala mdoc:invisible
sealed trait Content
case class NoContent(t: Throwable) extends Content
case class OkContent(s: String) extends Content
def readUrls(file: String): Task[List[String]] = IO.succeed("Hello" :: Nil)
def fetchContent(urls: List[String]): UIO[Content] = IO.succeed(OkContent("Roger"))
```
```scala mdoc:silent
val urls: UIO[Content] =
  readUrls("urls.json").foldZIO(
    error   => IO.succeed(NoContent(error)), 
    success => fetchContent(success)
  )
```

### Retrying

| Function            | Input Type                                                           | Output Type                            |
|---------------------|----------------------------------------------------------------------|----------------------------------------|
| `retry`             | `Schedule[R1, E, S]`                                                 | `ZIO[R1, E, A]`             |
| `retryN`            | `n: Int`                                                             | `ZIO[R, E, A]`                         |
| `retryOrElse`       | `policy: Schedule[R1, E, S], orElse: (E, S) => ZIO[R1, E1, A1]`      | `ZIO[R1, E1, A1]`           |
| `retryOrElseEither` | `schedule: Schedule[R1, E, Out], orElse: (E, Out) => ZIO[R1, E1, B]` | `ZIO[R1, E1, Either[B, A]]` |
| `retryUntil`        | `E => Boolean`                                                       | `ZIO[R, E, A]`                         |
| `retryUntilEquals`  | `E1`                                                                 | `ZIO[R, E1, A]`                        |
| `retryUntilZIO`     | `E => URIO[R1, Boolean]`                                             | `ZIO[R1, E, A]`                        |
| `retryWhile`        | `E => Boolean`                                                       | `ZIO[R, E, A]`                         |
| `retryWhileEquals`  | `E1`                                                                 | `ZIO[R, E1, A]`                        |
| `retryWhileZIO`     | `E => URIO[R1, Boolean]`                                             | `ZIO[R1, E, A]`                        |

When we are building applications we want to be resilient in the face of a transient failure. This is where we need to retry to overcome these failures.

There are a number of useful methods on the ZIO data type for retrying failed effects. 

The most basic of these is `ZIO#retry`, which takes a `Schedule` and returns a new effect that will retry the first effect if it fails according to the specified policy:

```scala mdoc:silent
val retriedOpenFile: ZIO[Any, IOException, Array[Byte]] = 
  readFile("primary.data").retry(Schedule.recurs(5))
```

The next most powerful function is `ZIO#retryOrElse`, which allows specification of a fallback to use if the effect does not succeed with the specified policy:

```scala mdoc:silent
readFile("primary.data").retryOrElse(
  Schedule.recurs(5), 
  (_, _:Long) => ZIO.succeed(DefaultData)
)
```

The final method, `ZIO#retryOrElseEither`, allows returning a different type for the fallback.

## Resource Management

ZIO's resource management features work across synchronous, asynchronous, concurrent, and other effect types, and provide strong guarantees even in the presence of failure, interruption, or defects in the application.

### Finalizing

Scala has a `try` / `finally` construct which helps us to make sure we don't leak resources because no matter what happens in the `try`, the `finally` block will be executed. So we can open files in the `try` block, and then we can close them in the `finally` block, and that gives us the guarantee that we will not leak resources.

#### Asynchronous Try / Finally
The problem with the `try` / `finally` construct is that it only applies to synchronous code, meaning it doesn't work for asynchronous code. ZIO gives us a method called `ensuring` that works with either synchronous or asynchronous actions. So we have a functional `try` / `finally` even for asynchronous regions of our code.

Like `try` / `finally`, the `ensuring` operation guarantees that if an effect begins executing and then terminates (for whatever reason), then the finalizer will begin executing:

```scala mdoc
val finalizer = 
  UIO.succeed(println("Finalizing!"))

val finalized: IO[String, Unit] = 
  IO.fail("Failed!").ensuring(finalizer)
```

The finalizer is not allowed to fail, which means that it must handle any errors internally.

Like `try` / `finally`, finalizers can be nested, and the failure of any inner finalizer will not affect outer finalizers. Nested finalizers will be executed in reverse order, and linearly (not in parallel).

Unlike `try` / `finally`, `ensuring` works across all types of effects, including asynchronous and concurrent effects.

Here is another example of ensuring that our clean-up action is called before our effect is done:

```scala mdoc:silent
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

```scala mdoc:silent
import zio.{ UIO, IO }
```

```scala mdoc:invisible
import java.io.{ File, IOException }

def openFile(s: String): IO[IOException, File] = IO.attempt(???).refineToOrDie[IOException]
def closeFile(f: File): UIO[Unit] = IO.succeed(???)
def decodeData(f: File): IO[IOException, Unit] = IO.unit
def groupData(u: Unit): IO[IOException, Unit] = IO.unit
```

```scala mdoc:silent
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


## ZIO Aspect

There are two types of concerns in an application, _core concerns_, and _cross-cutting concerns_. Cross-cutting concerns are shared among different parts of our application. We usually find them scattered and duplicated across our application, or they are tangled up with our primary concerns. This reduces the level of modularity of our programs.

A cross-cutting concern is more about _how_ we do something than _what_ we are doing. For example, when we are downloading a bunch of files, creating a socket to download each one is the core concern because it is a question of _what_ rather than the _how_, but the following concerns are cross-cutting ones:
 - Downloading files _sequentially_ or in _parallel_
 - _Retrying_ and _timing out_ the download process
 - _Logging_ and _monitoring_ the download process

So they don't affect the return type of our workflows, but they add some new aspects or change their behavior.

To increase the modularity of our applications, we can separate cross-cutting concerns from the main logic of our programs. ZIO supports this programming paradigm, which is called _ aspect-oriented programming_.

The `ZIO` effect has a data type called `ZIOAspect`, which allows modifying a `ZIO` effect and convert it into a specialized `ZIO` effect. We can add a new aspect to a `ZIO` effect with `@@` syntax like this:

```scala mdoc:silent:nest
val myApp: ZIO[Any, Throwable, String] =
  ZIO.attempt("Hello!") @@ ZIOAspect.debug
```

As we see, the `debug` aspect doesn't change the return type of our effect, but it adds a new debugging aspect to our effect.

`ZIOAspect` is like a transformer of the `ZIO` effect, which takes a `ZIO` effect and converts it to another `ZIO` effect. We can think of a `ZIOAspect` as a function of type `ZIO[R, E, A] => ZIO[R, E, A]`.

To compose multiple aspects, we can use `@@` operator:

```scala mdoc:compile-only
def download(url: String): ZIO[Any, Throwable, Chunk[Byte]] = ZIO.succeed(???)

ZIO.foreachPar(List("zio.dev", "google.com")) { url =>
  download(url) @@
    ZIOAspect.retry(Schedule.fibonacci(1.seconds)) @@
    ZIOAspect.loggedWith[Chunk[Byte]](file => s"Downloaded $url file with size of ${file.length} bytes")
}
```

The order of aspect composition matters. Therefore, if we change the order, the behavior may change.

## Debugging

When we are writing an application using the ZIO effect, we are writing workflows as data transformers. So there are lots of cases where we need to debug our application by seeing how the data transformed through the workflow. We can add or remove debugging capability without changing the signature of our effect:

```scala mdoc:silent:nest
ZIO.ifZIO(
  Random.nextIntBounded(10)
    .debug("random number")
    .map(_ % 2)
    .debug("remainder")
    .map(_ == 0)
)(
  onTrue = ZIO.succeed("Success"),
  onFalse = ZIO.succeed("Failure")
).debug.repeatWhile(_ != "Success")
``` 

The following could be one of the results of this program:

```
random number: 5
remainder: 1
Failure
random number: 1
remainder: 1
Failure
random number: 2
remainder: 0
Success
```
## Logging

ZIO has built-in logging functionality. This allows us to log within our application without adding new dependencies. ZIO logging doesn't require any services from the environment. 

We can easily log inside our application using the `ZIO.log` function:

```scala mdoc:silent:nest
ZIO.log("Application started!")
```

The output would be something like this:

```bash
[info] timestamp=2021-10-06T07:23:29.974297029Z level=INFO thread=#2 message="Application started!" file=ZIOLoggingExample.scala line=6 class=zio.examples.ZIOLoggingExample$ method=run
```

To log with a specific log-level, we can use the `ZIO.logLevel` combinator:

```scala mdoc:silent:nest
ZIO.logLevel(LogLevel.Warning) {
  ZIO.log("The response time exceeded its threshold!")
}
```
Or we can use the following functions directly:

* `ZIO.logDebug`
* `ZIO.logError`
* `ZIO.logFatal`
* `ZIO.logInfo`
* `ZIO.logWarning`

```scala mdoc:silent:nest
ZIO.logError("File does not exist: ~/var/www/favicon.ico")
```

It also supports logging spans:

```scala mdoc:silent:nest
ZIO.logSpan("myspan") {
  ZIO.sleep(1.second) *> ZIO.log("The job is finished!")
}
```

ZIO Logging calculates and records the running duration of the span and includes that in logging data:

```bash
[info] timestamp=2021-10-06T07:29:57.816775631Z level=INFO thread=#2 message="The job is done!" myspan=1013ms file=ZIOLoggingExample.scala line=8 class=zio.examples.ZIOLoggingExample$ method=run
```
