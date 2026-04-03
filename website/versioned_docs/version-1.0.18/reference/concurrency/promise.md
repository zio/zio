---
id: promise
title: "Promise"
---

A `Promise[E, A]` is a variable of `IO[E, A]` type that can be set exactly once.

Promise is a **purely functional synchronization primitive** which represents a single value that may not yet be available. When we create a Promise, it always started with an empty value, then it can be completed exactly once at some point, and then it will never become empty or modified again.

Promise is a synchronization primitive. So, it is useful whenever we want to wait for something to happen. Whenever we need to synchronize a fiber with another fiber, we can use Promise. It allows us to have fibers waiting for other fibers to do things. Any time we want to handoff of a work from one fiber to another fiber or anytime we want to suspend a fiber until some other fiber does a certain amount of work, well we need to be using a Promise. Also, We can use `Promise` with `Ref` to build other concurrent primitives, like Queue and Semaphore. 

By calling `await` on the Promise, the current fiber blocks, until that event happens. As we know, blocking thread in ZIO, don't actually block kernel threads. They are semantic blocking, so when a fiber is blocked the underlying thread is free to run all other fibers.

Promise is the equivalent of Scala's Promise. It's almost the same, except it has two type parameters, instead of one. Also instead of calling `future`, we need to call `await` on ZIO Promise to wait for the Promise to be completed.

Promises can be failed with a value of type `E` and succeeded that is completed with success with the value of type `A`. So there are two ways we can complete a Promise, with failure or success and then whoever is waiting on the Promise will get back that failure or success. 


## Operations

### Creation

Promises can be created using `Promise.make[E, A]`, which returns `UIO[Promise[E, A]]`. This is a description of creating a promise, but not the actual promise. Promises cannot be created outside of IO, because creating them involves allocating mutable memory, which is an effect and must be safely captured in IO.

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
```scala
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

The act of completing a Promise results in an `UIO[Boolean]`, where the `Boolean` represents whether the promise value has been set (`true`) or whether it was already set (`false`). This is demonstrated below:

```scala
val ioPromise1: UIO[Promise[Exception, String]] = Promise.make[Exception, String]
val ioBooleanSucceeded: UIO[Boolean] = ioPromise1.flatMap(promise => promise.succeed("I'm done"))
```

Another example with `fail(...)`:

```scala
val ioPromise2: UIO[Promise[Exception, Nothing]] = Promise.make[Exception, Nothing]
val ioBooleanFailed: UIO[Boolean] = ioPromise2.flatMap(promise => promise.fail(new Exception("boom")))
```

To re-iterate, the `Boolean` tells us whether or not the operation took place successfully (`true`) i.e. the Promise
was set with the value or the error.

### Awaiting
We can get a value from a Promise using `await`, calling fiber will suspend until Promise is completed.

```scala
val ioPromise3: UIO[Promise[Exception, String]] = Promise.make[Exception, String]
val ioGet: IO[Exception, String] = ioPromise3.flatMap(promise => promise.await)
```

### Polling
The computation will suspend (in a non-blocking fashion) until the Promise is completed with a value or an error.

If we don't want to suspend, and we only want to query the state of whether or not the Promise has been completed, we can use `poll`:

```scala
val ioPromise4: UIO[Promise[Exception, String]] = Promise.make[Exception, String]
val ioIsItDone: UIO[Option[IO[Exception, String]]] = ioPromise4.flatMap(p => p.poll)
val ioIsItDone2: IO[Option[Nothing], IO[Exception, String]] = ioPromise4.flatMap(p => p.poll.get)
```

If the Promise was not completed when we called `poll` then the IO will fail with the `Unit` value otherwise, we obtain an `IO[E, A]`, where `E` represents if the Promise completed with an error and `A` indicates that the Promise successfully completed with an `A` value.

`isDone` returns `UIO[Boolean]` that evaluates to `true` if promise is already completed.

## Example Usage
Here is a scenario where we use a `Promise` to hand-off a value between two `Fiber`s

```scala
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

In the example above, we create a Promise and have a Fiber (`fiberA`) complete that promise after 1 second and a second Fiber (`fiberB`) will call `await` on that Promise to obtain a `String` and then print it to screen. The example prints `hello world` to the screen after 1 second. Remember, this is just a description of the program and not the execution
itself.
