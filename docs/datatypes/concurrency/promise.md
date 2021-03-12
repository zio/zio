---
id: promise
title: "Promise"
---

A `Promise[E, A]` is a variable of `IO[E, A]` type that can be set exactly once.

Promises are used to build higher level concurrency primitives, and are often used in situations where multiple `Fiber`s
need to coordinate passing values to each other.

## Creation

Promises can be created using `Promise.make[E, A]`, which returns `UIO[Promise[E, A]]`. This is a description of creating a promise, but not the actual promise. Promises cannot be created outside of IO, because creating them involves allocating mutable memory, which is an effect and must be safely captured in IO.

## Operations

### Completing

You can complete a `Promise[E, A]` in few different ways:
* successfully with a value of type `A` using `succeed`
* with `Exit[E, A]` using `done` - each `await` will get this exit propagated
+ with result of effect `IO[E, A]` using `complete` - the effect will be executed once and the result will be propagated to all waiting fibers
* with effect `IO[E, A]` using `completeWith` - first fiber that calls `completeWith` wins and sets effect that **will be executed by each `await`ing fiber**, so be careful when using `p.completeWith(someEffect)` and rather use `p.complete(someEffect` unless executing `someEffect` by each `await`ing fiber is intent
* simply fail with `E` using `fail`
* simply defect with `Throwable` using `die`
* fail or defect with `Cause[E]` using `halt`
* interrupt it with `interrupt`

Following example shows usage of all of them:
```scala mdoc:silent
import zio._

val race: IO[String, Int] = for {
    p     <- Promise.make[String, Int]
    _     <- p.succeed(1).fork
    _     <- p.complete(ZIO.succeed(2)).fork
    _     <- p.completeWith(ZIO.succeed(3)).fork
    _     <- p.done(Exit.succeed(4)).fork
    _     <- p.fail("5")
    _     <- p.halt(Cause.die(new Error("6")))
    _     <- p.die(new Error("7"))
    _     <- p.interrupt.fork
    value <- p.await
  } yield value
```

The act of completing a Promise results in an `UIO[Boolean]`, where
the `Boolean` represents whether the promise value has been set (`true`) or whether it was already set (`false`).
This is demonstrated below:

```scala mdoc:silent
val ioPromise1: UIO[Promise[Exception, String]] = Promise.make[Exception, String]
val ioBooleanSucceeded: UIO[Boolean] = ioPromise1.flatMap(promise => promise.succeed("I'm done"))
```

Another example with `fail(...)`:

```scala mdoc:silent
val ioPromise2: UIO[Promise[Exception, Nothing]] = Promise.make[Exception, Nothing]
val ioBooleanFailed: UIO[Boolean] = ioPromise2.flatMap(promise => promise.fail(new Exception("boom")))
```

To re-iterate, the `Boolean` tells us whether or not the operation took place successfully (`true`) i.e. the Promise
was set with the value or the error.

### Awaiting
You can get a value from a Promise using `await`, calling fiber will suspend until Promise is completed.

```scala mdoc:silent
val ioPromise3: UIO[Promise[Exception, String]] = Promise.make[Exception, String]
val ioGet: IO[Exception, String] = ioPromise3.flatMap(promise => promise.await)
```

### Polling
The computation will suspend (in a non-blocking fashion) until the Promise is completed with a value or an error.
If you don't want to suspend and you only want to query the state of whether or not the Promise has been completed,
you can use `poll`:

```scala mdoc:silent
val ioPromise4: UIO[Promise[Exception, String]] = Promise.make[Exception, String]
val ioIsItDone: UIO[Option[IO[Exception, String]]] = ioPromise4.flatMap(p => p.poll)
val ioIsItDone2: IO[Option[Nothing], IO[Exception, String]] = ioPromise4.flatMap(p => p.poll.get)
```

If the Promise was not completed when you called `poll` then the IO will fail with the `Unit` value otherwise,
you obtain an `IO[E, A]`, where `E` represents if the Promise completed with an error and `A` indicates
that the Promise successfully completed with an `A` value.

`isDone` returns `UIO[Boolean]` that evaluates to `true` if promise is already completed.

## Example Usage
Here is a scenario where we use a `Promise` to hand-off a value between two `Fiber`s

```scala mdoc:silent
import java.io.IOException
import zio.console._
import zio.duration._
import zio.clock._

val program: ZIO[Console with Clock, IOException, Unit] = 
  for {
    promise         <-  Promise.make[Nothing, String]
    sendHelloWorld  =   (IO.succeed("hello world") <* sleep(1.second)).flatMap(promise.succeed)
    getAndPrint     =   promise.await.flatMap(putStrLn(_))
    fiberA          <-  sendHelloWorld.fork
    fiberB          <-  getAndPrint.fork
    _               <-  (fiberA zip fiberB).join
    } yield ()
```

In the example above, we create a Promise and have a Fiber (`fiberA`) complete that promise after 1 second and a second
Fiber (`fiberB`) will call `await` on that Promise to obtain a `String` and then print it to screen. The example prints
`hello world` to the screen after 1 second. Remember, this is just a description of the program and not the execution
itself.
