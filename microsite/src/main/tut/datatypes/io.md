---
layout: docs
section: datatypes
title:  "IO"
---

# {{page.title}}

A value of type `IO[E, A]` describes an effect that may fail with an `E`, run forever, or produce a single `A`.

`IO` values are immutable, and all `IO` functions produce new `IO` values, enabling `IO` to be reasoned about and used like any ordinary Scala immutable data structure.

`IO` values do not actually _do_ anything; they are just values that _model_ or _describe_ effectful interactions.

`IO` can be _interpreted_ by the ZIO runtime system into effectful interactions with the external world. Ideally, this occurs at a single time, in your application's `main` function. The `App` class provides this functionality automatically.

# Pure Values

You can lift pure values into `IO` with `IO.succeedLazy`:

```tut:silent
import scalaz.zio._

val z: IO[Nothing, String] = IO.succeedLazy("Hello World")
```

The constructor uses non-strict evaluation, so the parameter will not be evaluated until when and if the `IO` action is executed at runtime, which is useful if the construction is costly and the value may never be needed.

Alternately, you can use the `IO.succeed` constructor to perform strict evaluation of the value:

```tut:silent
val z: IO[Nothing, String] = IO.succeed("Hello World")
```

You should never use either constructor for importing impure code into `IO`. The result of doing so is undefined.

# Infallible IO

`IO` values of type `IO[Nothing, A]` (where the error type is `Nothing`) are considered _infallible_,
because the `Nothing` type is _uninhabitable_, i.e. there can be no actual values of type `Nothing`. Values of this type may produce an `A`, but will never fail with an `E`.

# Unproductive IO

`IO` values of type `IO[E, Nothing]` (where the value type is `Nothing`) are considered _unproductive_,
because the `Nothing` type is _uninhabitable_, i.e. there can be no actual values of type `Nothing`. Values of this type may fail with an `E`, but will never produce a value.

# Impure Code

You can use the `sync` method of `IO` to import effectful synchronous code into your purely functional program:

```tut:silent
val z: IO[Nothing, Long] = IO.sync(System.nanoTime())
```

If you are importing effectful code that may throw exceptions, you can use the `syncException` method of `IO`:

```tut:silent
import java.io.File
import org.apache.commons.io.FileUtils

def readFile(name: String): IO[Exception, Array[Byte]] =
  IO.syncException(FileUtils.readFileToByteArray(new File(name)))
```

Alternately, `syncThrowable` will catch all `Throwable`, not just exceptions. Finally, the `syncCatch` method is more general, allowing you to catch and optionally translate any type of `Throwable` into a custom error type:

```tut:silent
import java.io.IOException

def readFile(name: String): IO[String, Array[Byte]] =
  IO.syncCatch(FileUtils.readFileToByteArray(new File(name))) {
    case e : IOException => "Could not read file"
  }
```

You can use the `async` method of `IO` to import effectful asynchronous code into your purely functional program:

```tut:invisible
case class HttpException()
case class Request()
case class Response()

object Http {
  def req(req: Request, k: IO[HttpException, Response] => Unit): Unit =
    k(IO.succeed(Response()))
}
```

```tut:silent
def makeRequest(req: Request): IO[HttpException, Response] =
  IO.async[HttpException, Response](k => Http.req(req, k))
```

In this example, it's assumed the `Http.req` method will invoke the specified callback when the result has been asynchronously computed.

# Mapping

You can change an `IO[E, A]` to an `IO[E, B]` by calling the `map` method with a function `A => B`. This lets you transform values produced by actions into other values.

```tut:silent
import scalaz.zio._

val z: IO[Nothing, Int] = IO.succeedLazy(21).map(_ * 2)
```

You can transform an `IO[E, A]` into an `IO[E2, A]` by calling the `leftMap` method with a function `E => E2`:

```tut:silent
val z: IO[Exception, String] = IO.fail("No no!").leftMap(msg => new Exception(msg))
```

# Chaining

You can execute two actions in sequence with the `flatMap` method. The second action may depend on the value produced by the first action.

```tut:silent
val z: IO[Nothing, List[Int]] = IO.succeedLazy(List(1, 2, 3)).flatMap { list =>
  IO.succeedLazy(list.map(_ + 1))
}
```

You can use Scala's `for` comprehension syntax to make this type of code more compact:

```tut:silent
val z: IO[Nothing, List[Int]] = for {
  list <- IO.succeedLazy(List(1, 2, 3))
  added <- IO.succeedLazy(list.map(_ + 1))
} yield added
```

# Brackets

Brackets are a built-in primitive that let you safely acquire and release resources.

Brackets are used for a similar purpose as try/catch/finally, only brackets work with synchronous and asynchronous actions, work seamlessly with fiber interruption, and are built on a different error model that ensures no errors are ever swallowed.

Brackets consist of an *acquire* action, a *utilize* action (which uses the acquired resource), and a *release* action.

The release action is guaranteed to be executed by the runtime system, even if the utilize action throws an exception or the executing fiber is interrupted.

```tut:silent
import scalaz.zio._
```

```tut:invisible
import java.io.{ File, IOException }

def openFile(s: String): IO[IOException, File] = IO.succeedLazy(???)
def closeFile(f: File): IO[Nothing, Unit] = IO.succeedLazy(???)
def decodeData(f: File): IO[IOException, Unit] = IO.unit
def groupData(u: Unit): IO[IOException, Unit] = IO.unit
```

```tut:silent
val z: IO[IOException, Unit] = openFile("data.json").bracket(closeFile(_)) { file =>
  for {
    data    <- decodeData(file)
    grouped <- groupData(data)
  } yield grouped
}
```

Brackets have compositional semantics, so if a bracket is nested inside another bracket, and the outer bracket acquires a resource, then the outer bracket's release will always be called, even if, for example, the inner bracket's release fails.

A helper method called `ensuring` provides a simpler analogue of `finally`:

```tut:silent
var i: Int = 0
val action: IO[Throwable, String] = IO.sync(i += 1) *> IO.fail(new Throwable("Boom!"))
val cleanupAction: IO[Nothing, Unit] = IO.sync(i -= 1)
val composite = action.ensuring(cleanupAction)
```
