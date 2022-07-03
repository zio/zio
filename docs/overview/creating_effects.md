---
id: overview_creating_effects
title:  "Creating Effects"
---

This section explores some of the common ways to create ZIO effects from values, from computations, and from common Scala data types.

```scala mdoc:invisible
import zio.{ ZIO, Task, UIO, URIO, IO }
```

## From Values

Using the `ZIO.succeed` method, you can create an effect that, when executed, will succeed with the specified value:

```scala mdoc:silent
val s1 = ZIO.succeed(42)
```

The `succeed` method takes a so-called _by-name parameter_, which ensures that if you pass the method some code to execute, that this code will be stored inside the ZIO effect so that it can be managed by ZIO, and benefit from features like retries, timeouts, and automatic error logging.

## From Failure Values

Using the `ZIO.fail` method, you can create an effect that, when executed, will fail with the specified value:

```scala mdoc:silent
val f1 = ZIO.fail("Uh oh!")
```

For the `ZIO` data type, there is no restriction on the error type. You may use strings, exceptions, or custom data types appropriate for your application.

Many applications will model failures with classes that extend `Throwable` or `Exception`:

```scala mdoc:silent
val f2 = ZIO.fail(new Exception("Uh oh!"))
```

## From Scala Values

Scala's standard library contains a number of data types that can be converted into ZIO effects.

### Option

An `Option` can be converted into a ZIO effect using `ZIO.fromOption`:

```scala mdoc:silent
val zoption: IO[Option[Nothing], Int] = ZIO.fromOption(Some(2))
```

The error type of the resulting effect is `Option[Nothing]`, signifying that if such an effect fails, with will fail with the value `None` (which has type `Option[Nothing]`).

You can transform a failure into some other error value using `orElseFail`, one of many methods that ZIO provides for error management:

```scala mdoc:silent
val zoption2: ZIO[Any, String, Int] = zoption.orElseFail("It wasn't there!")
```

ZIO has a variety of other operators designed to make interfacing with `Option` code easier. In the following advanced example, the operators `some`  and `asSomeError` are used to make it easier to interface with methods returning `Option`, similar to the `OptionT` type in some Scala libraries.

```scala mdoc:invisible
trait Team
```

```scala mdoc:silent
val maybeId: ZIO[Any, Option[Nothing], String] = ZIO.fromOption(Some("abc123"))
def getUser(userId: String): ZIO[Any, Throwable, Option[User]] = ???
def getTeam(teamId: String): ZIO[Any, Throwable, Team] = ???


val result: ZIO[Any, Throwable, Option[(User, Team)]] = (for {
  id   <- maybeId
  user <- getUser(id).some
  team <- getTeam(user.teamId).asSomeError 
} yield (user, team)).unsome 
```

### Either

An `Either` can be converted into a ZIO effect using `ZIO.fromEither`:

```scala mdoc:silent
val zeither: ZIO[Any, Nothing, String] = ZIO.fromEither(Right("Success!"))
```

The error type of the resulting effect will be that of the `Left` case, while the success type will be that of the `Right` case.

### Try

A `Try` value can be converted into a ZIO effect using `ZIO.fromTry`:

```scala mdoc:silent
import scala.util.Try

val ztry = ZIO.fromTry(Try(42 / 0))
```

The error type of the resulting effect will always be `Throwable` because `Try` can only fail with values of type `Throwable`.

### Future

A Scala `Future` can be converted into a ZIO effect using `ZIO.fromFuture`:

```scala mdoc:silent
import scala.concurrent.Future

lazy val future = Future.successful("Hello!")

val zfuture: ZIO[Any, Throwable, String] =
  ZIO.fromFuture { implicit ec =>
    future.map(_ => "Goodbye!")
  }
```

The function passed to `fromFuture` is provided an `ExecutionContext`, which allows ZIO to manage where the `Future` runs (of course, you can ignore this `ExecutionContext`).

The error type of the resulting effect will always be `Throwable`, because `Future` values can only fail with values of type `Throwable`.

## From Code

ZIO can convert any code (such as a call to some method) into an effect, whether that code is so-called _synchronous_ (directly returning a value), or _asynchronous_ (passing a value to callbacks).

If done properly, when you convert code into a ZIO effect, this code will be stored inside the effect so that it can be managed by ZIO, and benefit from features like retries, timeouts, and automatic error logging.

The conversion functions that ZIO has allow you to seamlessly use all features of ZIO with non-ZIO code written in Scala or Java, including third-party libraries.

### Synchronous Code

Synchronous code can be converted into a ZIO effect using `ZIO.attempt`:

```scala mdoc:silent
import scala.io.StdIn

val readLine: ZIO[Any, Throwable, String] =
  ZIO.attempt(StdIn.readLine())
```

The error type of the resulting effect will always be `Throwable`, because synchronous code may throw exceptions with any value of type `Throwable`.

If you know for a fact that some code does not throw exceptions (except perhaps runtime exceptions), you can convert the code into a ZIO effect using `ZIO.succeed`:

```scala mdoc:silent
def printLine(line: String): UIO[Unit] =
  ZIO.succeed(println(line))
```

Sometimes, you may know that code throws a specific exception type, and you may wish to reflect this in the error parameter of your ZIO effect.

For this purpose, you can use the `ZIO#refineToOrDie` method:

```scala mdoc:silent
import java.io.IOException

val readLine2: ZIO[Any, IOException, String] =
  ZIO.attempt(StdIn.readLine()).refineToOrDie[IOException]
```

### Asynchronous Code

Asynchronous code that exposes a callback-based API can be converted into a ZIO effect using `ZIO.async`:

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

val login: ZIO[Any, AuthError, User] =
  ZIO.async[Any, AuthError, User] { callback =>
    legacy.login(
      user => callback(ZIO.succeed(user)),
      err  => callback(ZIO.fail(err))
    )
  }
```

Asynchronous effects are much easier to use than callback-based APIs, and they benefit from ZIO features like interruption, resource-safety, and error management.

## Blocking Synchronous Code

Some synchronous code may engage in so-called _blocking IO_, which puts a thread into a waiting state, as it waits for some operating system call to complete. For maximum throughput, this code should not run on your application's primary thread pool, but rather, in a special thread pool that is dedicated to blocking operations.

ZIO has a blocking thread pool built into the runtime, and lets you execute effects there with `ZIO.blocking`:

```scala mdoc:silent
import scala.io.{ Codec, Source }

def download(url: String) =
  ZIO.attempt {
    Source.fromURL(url)(Codec.UTF8).mkString
  }

def safeDownload(url: String) =
  ZIO.blocking(download(url))
```

As an alternative, if you wish to convert blocking code directly into a ZIO effect, you can use the `ZIO.attemptBlocking` method:

```scala mdoc:silent
val sleeping =
  ZIO.attemptBlocking(Thread.sleep(Long.MaxValue))
```

The resulting effect will be executed on ZIO's blocking thread pool.

If you have some synchronous code that will respond to Java's `Thread.interrupt` (such as `Thread.sleep` or lock-based code), then you can convert this code into an interruptible ZIO effect using the `ZIO.attemptBlockingInterrupt` method.

Some synchronous code can only be cancelled by invoking some other code, which is responsible for canceling the running computation. To convert such code into a ZIO effect, you can use the `ZIO.attemptBlockingCancelable` method:

```scala mdoc:silent
import java.net.ServerSocket
import zio.UIO

def accept(l: ServerSocket) =
  ZIO.attemptBlockingCancelable(l.accept())(ZIO.succeed(l.close()))
```
## Next Steps

If you are comfortable creating effects from values, converting from Scala types into effects, and converting synchronous and asynchronous code into effects, the next step is learning [basic operations](basic_operations.md) on effects.
