---
layout: docs
position: 3
section: overview
title:  "Creating Effects"
---

# {{page.title}}

This section explores some of the common ways to create ZIO effects from values, common Scala types, and both synchronous and asynchronous side-effects.

```tut:invisible
import scalaz.zio._
```

# From Success Values

Using the `ZIO.succeed` method, you can create an effect that models success:

```tut:silent
val s1 = ZIO.succeed(42)
```

You can also create effects using methods in the companion object of the `ZIO` type aliases:

```tut:silent
val s2: Task[Int] = Task.succeed(42)
```

The `succeed` method is eager, which means the value will be constructed before the method is invoked.

Although `succeed` is the most common way to construct a successful effect, you can achieve lazy construction using the `ZIO.succeedLazy` method:

```tut:silent
lazy val bigList = (0 to 1000000).toList
lazy val bigString = bigList.map(_.toString).mkString("\n")

val s3 = ZIO.succeedLazy(bigString)
```

The successful effect constructed with `ZIO.succeedLazy` will only construct its value if the effect itself ends up being used.

# From Failure Values

Using the `ZIO.fail` method, you can create an effect that models failure:

```tut:silent
val f1 = ZIO.fail("Uh oh!")
```

For the `ZIO` data type, there is no restriction on the error type. You may use strings, exceptions, or custom data types appropriate for your application.

Many applications will choose to model failures with classes that extend `Throwable` or `Exception`:

```tut:silent
val f2 = Task.fail(new Exception("Uh oh!"))
```

Note that the `UIO` companion object does not have `UIO.fail`, because `UIO` values cannot fail.

# From Scala Values

Scala's standard library contains a number of data types that can be converted into ZIO effects.

## Option

An `Option` can be converted into a ZIO effect using `ZIO.fromOption`:

```tut:silent
val zoption: ZIO[Any, Unit, Int] = ZIO.fromOption(Some(2))
```

The error type of the resulting effect is `Unit`, because the `None` case of `Option` provides no information on why the value is not there. You can change that `Unit` into a more specific error type using `ZIO#mapError`.

## Either

An `Either` can be converted in to a ZIO effect using `ZIO.fromEither`:

```tut:silent
val zeither = ZIO.fromEither(Right("Success!"))
```

Unlike `ZIO.fromOption`, the error type of the resulting effect will be whatever type the `Left` case has, while the success type will be whatever type the `Right` case has.

## Try

A `Try` value can be converted can be converted into a ZIO effect using `ZIO.fromTry`:

```tut:silent
import scala.util.Try

val ztry = ZIO.fromTry(Try(42 / 0))
```

The error type of the resulting effect will always be `Throwable`, because a `Try` can fail with any value of type `Throwable`.

## Function

A function `A => B` can be converted into a ZIO effect with `ZIO.fromFunction`:

```tut:silent
val zfun: ZIO[Int, Nothing, Int] = 
  ZIO.fromFunction((i: Int) => i * i)
```

The environment type of the effect is `A` (the input type of the function), because in order to run the effect, it must first be supplied with such a value.

## Future

A `Future` can be converted into a ZIO effect using `ZIO.fromFuture`:

```tut:silent
import scala.concurrent.Future

lazy val future = Future.successful("Hello!")

val zfuture: Task[String] = 
  ZIO.fromFuture { implicit ec => 
    future.map(_ => "Goodbye!")
  }
```

The function passed to `fromFuture` is passed an `ExecutionContext`, which will confine the execution of the future to the appropriate context.

The error type of the resulting effect will always be `Throwable`, because a `Future` can fail with any value of type `Throwable`.

# From Side-Effects

ZIO can convert both synchronous and asynchronous side-effects into ZIO effects (pure values). 

These functions can be used to wrap non-functional code, allowing you to seamlessly use all features of ZIO with legacy Scala and Java code, as well as third-party libraries.

## Synchronous Side-Effects

A synchronous side-effect can be converted into a ZIO effect using `ZIO.effect`:

```tut:silent
import scala.io.StdIn

val getStrLn: Task[Unit] =
  ZIO.effect(StdIn.readLine())
```

The error type of the resulting effect will always be `Throwable`, because side-effects may throw exceptions with any value of type `Throwable`.

If a given side-effect is known not to throw any exceptions, then the side-effect can be converted into a ZIO effect using `ZIO.effectTotal`:

```tut:silent
def putStrLn(line: String): UIO[Unit] =
  ZIO.effectTotal(println(line))
```

If a side-effect is known to throw some specific subtype of `Throwable`, then the `ZIO#refineOrDie` method may be used:

```tut:silent
import java.io.IOException

val getStrLn2: IO[IOException, String] =
  ZIO.effect(StdIn.readLine()).refineOrDie {
    case e : IOException => e
  }
```

## Asynchronous Side-Effects

An asynchronous side-effect with a callback-based API can be converted into a ZIO effect using `ZIO.effectAsync`:

```tut:invisible
trait User
trait AuthError
```

```tut
object legacy {
  def login(
    onSuccess: User      => Unit, onFailure: AuthError => Unit): Unit = ???
}

val login: IO[AuthError, User] = 
  IO.effectAsync[AuthError, User] { callback =>
    legacy.login(
      user => callback(IO.succeed(user)),
      err  => callback(IO.fail(err))
    )
  }
```

Asynchronous ZIO effects are much easier to use than callback-based APIs, and they benefit from ZIO features like interruption, resource-safety, and superior error handling.

# Blocking Synchronous Side-Effects

Some side-effects use blocking IO or otherwise put a thread into a waiting state. If not carefully managed, these side-effects can deplete threads from an application's main thread pool.

ZIO provides the `scalaz.zio.blocking` package, which can be used to safely convert such blocking side-effects into ZIO effects.

A blocking side-effect can be converted directly into an interruptible ZIO effect with the `interruptible` method:

```tut
import scalaz.zio.blocking._

val sleeping = 
  interruptible(Thread.sleep(Long.MaxValue))
```

The resulting effect will be executed on a separate thread pool designed specifically for blocking effects.

If a side-effect has already been converted into a ZIO effect, then instead of `interruptible`, the `blocking` method can be used to shift the effect onto the blocking thread pool:

```tut
import scala.io.{ Codec, Source }

def download(url: String) =
  Task.effect {
    Source.fromURL(url)(Codec.UTF8).mkString
  }

def safeDownload(url: String) = 
  blocking(download(url))
``` 