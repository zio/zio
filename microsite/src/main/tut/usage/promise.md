---
layout: docs
section: usage
title:  "Promise"
---

# Promise

A `Promise` is a variable that can be set exactly once. The type signature of Promise is `Promise[E, A]` where `E` is
used to indicate an error and `A` is used to indicate that a value has been successfully set

Promises are used to build higher level concurrency primitives and are often used in situations where multiple `Fiber`s
need to coordinate passing values to each other.

## Creation
Promises can be created using `Promise.make[E, A]` which result in a `IO[Nothing, Promise[E, A]]` which is a description
to create a Promise but not the actual creation of a Promise construct. This is because the creation of a Promise is
effectful.

## Operations
You can complete a `Promise[Exception, String]` named `p` successfully with a value using `p.complete(...)`.
For example, `p.complete("I'm done!")`. The act of completing a Promise results in an `IO[Nothing, Boolean]` where
the `Boolean` represents whether the promise value has been set (`true`) or whether it was already set (`false`).
This is demonstrated below:

```tut:silent
import scalaz.zio._
```

```tut:silent
val ioPromise: IO[Nothing, Promise[Exception, String]] = Promise.make[Exception, String]
val ioBoolean: IO[Nothing, Boolean] = ioPromise.flatMap(promise => promise.complete("I'm done"))
```

You can also signal failure using `error(...)`. For example,

```tut:silent
val ioPromise: IO[Nothing, Promise[Exception, Nothing]] = Promise.make[Exception, Nothing]
val ioBoolean: IO[Nothing, Boolean] = ioPromise.flatMap(promise => promise.error(new Exception("boom")))
```

To re-iterate, the `Boolean` tells us whether or not the operation took place successfully (`true`) i.e. the Promise
was set with the value or the error.

As an alternative to using `done(...)` or `error(...)` you can also use `complete(...)` with an `ExitResult[E, A]` where
`E` signals an error and `A` signals a successful value.

You can get a value from a Promise using `get`

```tut:silent
val ioPromise: IO[Nothing, Promise[Exception, String]] = Promise.make[Exception, String]
val ioGet: IO[Exception, String] = ioPromise.flatMap(promise => promise.get)
```

The computation will suspend (in a non-blocking fashion) until the Promise is completed with a value or an error.
If you don't want to suspend and you only want to query the state of whether or not the Promise has been completed,
you can use `poll`

```tut:silent
val ioPromise: IO[Nothing, Promise[Exception, String]] = Promise.make[Exception, String]
val ioIsItDone: IO[Unit, IO[Exception, String]] = ioPromise.flatMap(p => p.poll)
```

If the Promise was not completed when you called `poll` then the IO will fail with the `Unit` value otherwise,
you obtain an `IO[E, A]`, where `E` represents if the Promise completed with an error and `A` indicates
that the Promise successfully completed with an `A` value.

## Example Usage
Here is a scenario where we use a `Promise` to hand-off a value between two `Fiber`s

```tut:silent
import java.io.IOException
import scalaz.zio.console._
import scalaz.zio.duration._

val program: IO[IOException, Unit] = for {
promise         <-  Promise.make[Nothing, String]
sendHelloWorld  =   (IO.now("hello world") <* IO.sleep(1.second)).flatMap(promise.complete)
getAndPrint     =   promise.get.flatMap(putStrLn)
fiberA          <-  sendHelloWorld.fork
fiberB          <-  getAndPrint.fork
_               <-  (fiberA zip fiberB).join
} yield ()
```

In the example above, we create a Promise and have a Fiber (`fiberA`) complete that promise after 1 second and a second
Fiber (`fiberB`) will call `get` on that Promise to obtain a `String` and then print it to screen. The example prints
`hello world` to the screen after 1 second. Remember, this is just a description of the program and not the execution
itself.
