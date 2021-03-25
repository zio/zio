---
id: zio
title: "ZIO"
---

A `ZIO[R, E, A]` value is an immutable value that lazily describes a workflow or job. The workflow requires some environment `R`, and may fail with an error of type `E`, or succeed with a value of type `A`.

A value of type `ZIO[R, E, A]` is like an effectful version of the following function type:

```scala
R => Either[E, A]
```

This function, which requires an `R`, might produce either an `E`, representing failure, or an `A`, representing success. ZIO effects are not actually functions, of course, because they model complex effects, like asynchronous and concurrent effects.

ZIO effects model resourceful interaction with the outside world, including synchronous, asynchronous, concurrent, and parallel interaction.

ZIO effects use a fiber-based concurrency model, with built-in support for
scheduling, fine-grained interruption, structured concurrency, and high scalability.

The `ZIO[R, E, A]` data type has three type parameters:

 - **`R` - Environment Type**. The effect requires an environment of type `R`. If this type parameter is `Any`, it means the effect has no requirements, because you can run the effect with any value (for example, the unit value `()`).
 - **`E` - Failure Type**. The effect may fail with a value of type `E`. Some applications will use `Throwable`. If this type parameter is `Nothing`, it means the effect cannot fail, because there are no values of type `Nothing`.
 - **`A` - Success Type**. The effect may succeed with a value of type `A`. If this type parameter is `Unit`, it means the effect produces no useful information, while if it is `Nothing`, it means the effect runs forever (or until failure).

In the following example, the `getStrLn` function requires the `Console` service, it may fail with value of type `IOException`, or may succeed with a value of type `String`:

```scala mdoc:silent
import zio.ZIO
import zio.console._
import java.io.IOException
val getStrLn: ZIO[Console, IOException, String] =
  ZIO.accessM(_.get.getStrLn)
```

`ZIO` values are immutable, and all `ZIO` functions produce new `ZIO` values, enabling `ZIO` to be reasoned about and used like any ordinary Scala immutable data structure.

`ZIO` values do not actually _do_ anything; they are just values that _model_ or _describe_ effectful interactions.

`ZIO` can be _interpreted_ by the ZIO runtime system into effectful interactions with the external world. Ideally, this occurs at a single time, in your application's `main` function. The `App` class provides this functionality automatically.

## Pure Values

You can lift pure values into `ZIO` with `ZIO.succeed`:

```scala mdoc:silent
import zio.{ UIO, ZIO }

val value: UIO[String] = ZIO.succeed("Hello World")
```

You should never use either constructor for importing impure code into `ZIO`. The result of doing so is undefined.

## Infallible ZIO

`ZIO` values of type `UIO[A]` (where the error type is `Nothing`) are considered _infallible_,
because the `Nothing` type is _uninhabitable_, i.e. there can be no actual values of type `Nothing`. Values of this type may produce an `A`, but will never fail with an `E`.

## Unproductive IO

`ZIO` values of type `IO[E, Nothing]` (where the value type is `Nothing`) are considered _unproductive_,
because the `Nothing` type is _uninhabitable_, i.e. there can be no actual values of type `Nothing`. Values of this type may fail with an `E`, but will never produce a value.

## Impure Code

You can use the `effectTotal` method of `ZIO` to import effectful synchronous code into your purely functional program:

```scala mdoc:invisible
import zio.UIO
```
```scala mdoc:silent
val effectTotalTask: UIO[Long] = ZIO.effectTotal(System.nanoTime())
```

```scala mdoc:invisible
import java.io.File
import org.apache.commons.io.FileUtils
import java.io.IOException
```

The resulting effect may fail for any `Throwable`.

If this is too broad, the `refineOrDie` method of `ZIO` may be used to retain only certain types of exceptions, and to die on any other types of exceptions:

```scala mdoc:silent
def readFile(name: String): ZIO[Nothing, IOException, Array[Byte]] =
  ZIO.effect(FileUtils.readFileToByteArray(new File(name))).refineToOrDie[IOException]
```

You can use the `effectAsync` method of `IO` to import effectful asynchronous code into your purely functional program:

```scala mdoc:invisible
case class HttpException()
case class Request()
case class Response()

object Http {
  def req(req: Request, k: ZIO[Nothing, HttpException, Response] => Unit): Unit =
    k(ZIO.succeed(Response()))
}
```

```scala mdoc:silent
def makeRequest(req: Request): ZIO[Nothing, HttpException, Response] =
  ZIO.effectAsync[Nothing, HttpException, Response](k => Http.req(req, k))
```

In this example, it's assumed the `Http.req` method will invoke the specified callback when the result has been asynchronously computed.

## Mapping

You can change an `IO[E, A]` to an `IO[E, B]` by calling the `map` method with a function `A => B`. This lets you transform values produced by actions into other values.

```scala mdoc:silent
import zio.{ UIO, IO }

val mappedValue: UIO[Int] = IO.succeed(21).map(_ * 2)
```

You can transform an `IO[E, A]` into an `IO[E2, A]` by calling the `mapError` method with a function `E => E2`:

```scala mdoc:silent
val mappedError: IO[Exception, String] = IO.fail("No no!").mapError(msg => new Exception(msg))
```

## Chaining

You can execute two actions in sequence with the `flatMap` method. The second action may depend on the value produced by the first action.

```scala mdoc:silent
val chainedActionsValue: UIO[List[Int]] = IO.succeed(List(1, 2, 3)).flatMap { list =>
  IO.succeed(list.map(_ + 1))
}
```

You can use Scala's `for` comprehension syntax to make this type of code more compact:

```scala mdoc:silent
val chainedActionsValueWithForComprehension: UIO[List[Int]] = for {
  list <- IO.succeed(List(1, 2, 3))
  added <- IO.succeed(list.map(_ + 1))
} yield added
```

## Resource Management

ZIO's resource management features work across synchronous, asynchronous, concurrent, and other effect types, and provide strong guarantees even in the presence of failure, interruption, or defects in the application.

### Finalizing

Scala has a `try` / `finally` construct which helps us to make sure we don't leak resources because no matter what happens in the try, the `finally` block will be executed. So we can open files in the try block, and then we can close them in the `finally` block, and that gives us the guarantee that we will not leak resources.

The problem with the `try` / `finally` construct is that it only applies with synchronous code, they don't work for asynchronous code. ZIO gives us a method called `ensuring` that works with either synchronous or asynchronous actions. So we have a functional try/finally but across the async region of our code, also our finalizer could have async regions.

Like `try` / `finally`, the `ensuring` operation guarantees that if an effect begins executing and then terminates (for whatever reason), then the finalizer will begin executing:

```scala mdoc
val finalizer = 
  UIO.effectTotal(println("Finalizing!"))

val finalized: IO[String, Unit] = 
  IO.fail("Failed!").ensuring(finalizer)
```

The finalizer is not allowed to fail, which means that it must handle any errors internally.

Like `try` / `finally`, finalizers can be nested, and the failure of any inner finalizer will not affect outer finalizers. Nested finalizers will be executed in reverse order, and linearly (not in parallel).

Unlike `try` / `finally`, `ensuring` works across all types of effects, including asynchronous and concurrent effects.

Here is another example of ensuring that our clean-up action called before our effect is done:

```scala mdoc:silent
import zio.Task
var i: Int = 0
val action: Task[String] = Task.effectTotal(i += 1) *> Task.fail(new Throwable("Boom!"))
val cleanupAction: UIO[Unit] = UIO.effectTotal(i -= 1)
val composite = action.ensuring(cleanupAction)
```

### Brackets

In Scala the `try` / `finally` is often used to manage resources. A common use for `try` / `finally` is safely acquiring and releasing resources, such as new socket connections or opened files:

```scala 
val handle = openFile(name)

try {
  processFile(handle)
} finally closeFile(handle)
```

ZIO encapsulates this common pattern with `ZIO#bracket`, which allows you to specify an _acquire_ effect, which acquires a resource; a _release_ effect, which releases it; and a _use_ effect, which uses the resource. Bracket lets us open a file and close the file and no matter what happens when we are using that resource.
 
The release action is guaranteed to be executed by the runtime system, even if the utilize action throws an exception or the executing fiber is interrupted.

Brackets are a built-in primitive that let us safely acquire and release resources. They are used for a similar purpose as `try/catch/finally`, only brackets work with synchronous and asynchronous actions, work seamlessly with fiber interruption, and are built on a different error model that ensures no errors are ever swallowed.

Brackets consist of an *acquire* action, a *utilize* action (which uses the acquired resource), and a *release* action.

```scala mdoc:silent
import zio.{ UIO, IO }
```

```scala mdoc:invisible
import java.io.{ File, IOException }

def openFile(s: String): IO[IOException, File] = IO.effect(???).refineToOrDie[IOException]
def closeFile(f: File): UIO[Unit] = IO.effectTotal(???)
def decodeData(f: File): IO[IOException, Unit] = IO.unit
def groupData(u: Unit): IO[IOException, Unit] = IO.unit
```

```scala mdoc:silent
val groupedFileData: IO[IOException, Unit] = openFile("data.json").bracket(closeFile(_)) { file =>
  for {
    data    <- decodeData(file)
    grouped <- groupData(data)
  } yield grouped
}
```

Brackets have compositional semantics, so if a bracket is nested inside another bracket, and the outer bracket acquires a resource, then the outer bracket's release will always be called, even if, for example, the inner bracket's release fails.

Let's look at a full working example on using brackets:

```scala mdoc:silent
import zio.{ ExitCode, Task, UIO }
import java.io.{ File, FileInputStream }
import java.nio.charset.StandardCharsets

object Main extends App {

  // run my bracket
  def run(args: List[String]) =
    mybracket.orDie.as(ExitCode.success)

  def closeStream(is: FileInputStream) =
    UIO(is.close())

  // helper method to work around in Java 8
  def readAll(fis: FileInputStream, len: Long): Array[Byte] = {
    val content: Array[Byte] = Array.ofDim(len.toInt)
    fis.read(content)
    content
  }

  def convertBytes(is: FileInputStream, len: Long) =
    Task.effect(println(new String(readAll(is, len), StandardCharsets.UTF_8))) // Java 8
  //Task.effect(println(new String(is.readAllBytes(), StandardCharsets.UTF_8))) // Java 11+

  // mybracket is just a value. Won't execute anything here until interpreted
  val mybracket: Task[Unit] = for {
    file   <- Task(new File("/tmp/hello"))
    len    = file.length
    string <- Task(new FileInputStream(file)).bracket(closeStream)(convertBytes(_, len))
  } yield string
}
```
