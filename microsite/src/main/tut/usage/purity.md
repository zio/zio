---
layout: docs
section: usage
title:  "Purity"
---

# Pure Values

You can lift pure values into `IO` with `IO.point`:

```tut:silent
import scalaz.zio._

val z: IO[Nothing, String] = IO.point("Hello World")
```

The constructor uses non-strict evaluation, so the parameter will not be evaluated until when and if the `IO` action is executed at runtime, which is useful if the construction is costly and the value may never be needed.

Alternately, you can use the `IO.now` constructor to perform strict evaluation of the value:

```tut:silent
val z: IO[Nothing, String] = IO.now("Hello World")
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
  def req(req: Request, cb: ExitResult[HttpException, Response] => Unit): Unit =
    cb(ExitResult.succeeded(Response()))
}
```

```tut:silent
def makeRequest(req: Request): IO[HttpException, Response] =
  IO.async[HttpException, Response](cb => Http.req(req, cb))
```

In this example, it's assumed the `Http.req` method will invoke the specified callback when the result has been asynchronously computed.
