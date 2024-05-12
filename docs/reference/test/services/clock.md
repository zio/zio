---
id: clock
title: "TestClock"
---

In most cases we want unit tests to be as fast as possible. Waiting for real time to pass by is a real killer for this. 

ZIO exposes a `TestClock` that can control the time. We can deterministically and efficiently **test effects involving the passage of time** without actually having to wait for the full amount of time to pass.

Calls to `sleep` and methods derived from it will semantically block until the clock time is set/adjusted to on or after the time the effect is scheduled to run.

Instead of waiting for actual time to pass, `sleep` and methods implemented in terms of it schedule effects to take place at a given clock time. Users can adjust the clock time using the `adjust` and `setTime` methods, and all effects scheduled to take place on or before that time will automatically be run in order.

For example, here is how we can test `ZIO#timeout` using `TestClock`:

```scala mdoc:compile-only
import zio._
import zio.test._

for {
  fiber  <- ZIO.sleep(5.minutes).timeout(1.minute).fork
  _      <- TestClock.adjust(1.minute)
  result <- fiber.join
} yield assertTrue(result.isEmpty)
```

Note how we forked the fiber that `sleep` was invoked on. Calls to `sleep` and methods derived from it will semantically block until the time is set to on or after the time they are scheduled to run. 

If we didn't fork the fiber on which we called sleep we would never get to set the time on the line below. Thus, a useful pattern when using `TestClock` is to fork the effect being tested, then adjust the clock time, and finally verify that the expected effects have been performed.

Clock time is just like a clock on the wall, except that in our `TestClock`, the clock is broken. Instead of moving by itself, the clock time only changes when adjusted or set by the user, using the `adjust` and `setTime` methods. The clock time never changes by itself.

When the clock is adjusted, any effects scheduled to run on or before the new clock time will automatically be run, in order.

For example, here is how we can test an effect that recurs with a fixed delay:

```scala mdoc:compile-only
import zio._
import zio.Queue
import zio.test._

for {
  q <- Queue.unbounded[Unit]
  _ <- q.offer(()).delay(60.minutes).forever.fork
  a <- q.poll.map(_.isEmpty)
  _ <- TestClock.adjust(60.minutes)
  b <- q.take.as(true)
  c <- q.poll.map(_.isEmpty)
  _ <- TestClock.adjust(60.minutes)
  d <- q.take.as(true)
  e <- q.poll.map(_.isEmpty)
} yield assertTrue(a && b && c && d && e)
``` 

Here we verify that no effect is performed before the recurrence period, that an effect is performed after the recurrence period, and that the effect is performed exactly once. 

The key thing to note here is that after each recurrence the next recurrence is scheduled to occur at the appropriate time in the future, so when we adjust the clock by 60 minutes exactly one value is placed in the queue, and when we adjust the clock by another 60 minutes exactly one more value is placed in the queue.

## Examples

### Example 1

Thanks to the call to `TestClock.adjust(1.minute)` we moved the time instantly 1 minute.

```scala mdoc:compile-only
import zio._
import zio.test.{test, _}
import java.util.concurrent.TimeUnit
import zio.Clock.currentTime
import zio.test.Assertion.isGreaterThanEqualTo

test("One can move time very fast") {
  for {
    startTime <- currentTime(TimeUnit.SECONDS)
    _         <- TestClock.adjust(1.minute)
    endTime   <- currentTime(TimeUnit.SECONDS)
  } yield assertTrue((endTime - startTime) >= 60L)
}
```

### Example 2

`TestClock` affects also all code running asynchronously that is scheduled to run after a certain time:

```scala mdoc:compile-only
import zio._
import zio.test.{test, _}
import zio.test.Assertion.equalTo

test("One can control time as he see fit") {
  for {
    promise <- Promise.make[Unit, Int]
    _       <- (ZIO.sleep(10.seconds) *> promise.succeed(1)).fork
    _       <- TestClock.adjust(10.seconds)
    readRef <- promise.await
  } yield assertTrue(1 == readRef)
}
```

The above code creates a write-once cell that will be set to "1" after 10 seconds asynchronously from a different thread thanks to the call to `fork`. In the end, we wait on the promise until it is set.

With the call to `TestClock.adjust(10.seconds)` we simulate the passing of 10 seconds of time. Because of it, we don't need to wait for the real 10 seconds to pass and thus our unit test can run faster.

This is a pattern that will very often be used when `sleep` and `TestClock` are being used for testing of effects that are based on time. The fiber that needs to sleep will be forked and `TestClock` will used to adjust the time so that all expected effects are run in the forked fiber.

### Example 3

A more complex example leveraging dependencies and multiple services is shown below:

```scala mdoc:compile-only
import zio.test.Assertion._
import zio.test._
import zio.{test => _, _}

trait SchedulingService {
  def schedule(promise: Promise[Unit, Int]): ZIO[Any, Exception, Boolean]
}

trait LoggingService {
  def log(msg: String): ZIO[Any, Exception, Unit]
}

val schedulingLayer: ZLayer[LoggingService, Nothing, SchedulingService] =
  ZLayer.fromFunction { (loggingService: LoggingService) =>
    new SchedulingService {
      def schedule(promise: Promise[Unit, Int]): ZIO[Any, Exception, Boolean] =
        (ZIO.sleep(10.seconds) *> promise.succeed(1))
          .tap(b => loggingService.log(b.toString))
    }
}

test("One can control time for failing effects too") {
  val failingLogger = ZLayer.succeed(new LoggingService {
    override def log(msg: String): ZIO[Any, Exception, Unit] = ZIO.fail(new Exception("BOOM"))
  })

  val layer = failingLogger >>> schedulingLayer

  val testCase =
    for {
      promise <- Promise.make[Unit, Int]
      result <- ZIO.serviceWithZIO[SchedulingService](_.schedule(promise)).exit.fork
      _ <- TestClock.adjust(10.seconds)
      readRef <- promise.await
      result <- result.join
    } yield assertTrue((1 == readRef) && result.isFailure)
  testCase.provideLayer(layer)
}
```

In this case, we want to test an effect with dependencies that can potentially fail with an error. To do this we need to run the effect and use assertions that expect an `Exit` value.

### Example 4

The pattern with `Promise` and `await` can be generalized when we need to wait for multiple values using a `Queue`. We simply need to put multiple values into the queue and progress the clock multiple times and there is no need to create multiple promises.

Even if you have a non-trivial flow of data from multiple streams that can produce at different intervals and would like to test snapshots of data at a particular point in time `Queue` can help with that.

```scala mdoc:compile-only
import zio._
import zio.test.{test, _}
import zio.stream._
import zio.test.Assertion.equalTo

test("zipLatest") {
  val s1 = ZStream.iterate(0)(_ + 1).schedule(Schedule.fixed(100.milliseconds))
  val s2 = ZStream.iterate(0)(_ + 1).schedule(Schedule.fixed(70.milliseconds))
  val s3 = s1.zipLatest(s2)

  for {
    q      <- Queue.unbounded[(Int, Int)]
    _      <- s3.foreach(q.offer).fork
    fiber  <- ZIO.collectAll(ZIO.replicate(4)(q.take)).fork
    _      <- TestClock.adjust(1.second)
    result <- fiber.join
  } yield assertTrue(result == List(0 -> 0, 0 -> 1, 1 -> 1, 1 -> 2))
}
```
### Example 5

In ZIO 2.x, the default services were removed from the environment in favor of integrating these services directly into the ZIO runtime. This means that `TestClock` can be accessed in the live scope using the `withLiveClock` test aspect as shown below:

```scala mdoc:compile-only
import zio._
import zio.test.Assertion._
import zio.test.TestAspect._

test("Test using live Clock service") {
  for {
    _ <- TestClock.timeZone
  } yield assertCompletes
} @@ withLiveClock
```

