package zio

import scala.concurrent.Future
import zio.LatchOps._
import zio.clock.Clock
import zio.duration._
import zio.test.Assertion._
import zio.test._

object FiberSpec extends ZIOBaseSpec {

  def spec = suite("FiberSpec")(
    suite("Create a new Fiber and")(testM("lift it into Managed") {
      for {
        ref <- Ref.make(false)
        fiber <- withLatch { release =>
                  (release *> IO.unit).bracket_(ref.set(true))(IO.never).fork
                }
        _     <- fiber.toManaged.use(_ => IO.unit)
        _     <- fiber.await
        value <- ref.get
      } yield assert(value, isTrue)
    }),
    suite("`inheritLocals` works for Fiber created using:")(
      testM("`map`") {
        for {
          fiberRef <- FiberRef.make(initial)
          child <- withLatch { release =>
                    (fiberRef.set(update) *> release).fork
                  }
          _     <- child.map(_ => ()).inheritRefs
          value <- fiberRef.get
        } yield assert(value, equalTo(update))
      },
      testM("`orElse`") {
        for {
          fiberRef <- FiberRef.make(initial)
          latch1   <- Promise.make[Nothing, Unit]
          latch2   <- Promise.make[Nothing, Unit]
          child1   <- (fiberRef.set("child1") *> latch1.succeed(())).fork
          child2   <- (fiberRef.set("child2") *> latch2.succeed(())).fork
          _        <- latch1.await *> latch2.await
          _        <- child1.orElse(child2).inheritRefs
          value    <- fiberRef.get
        } yield assert(value, equalTo("child1"))
      },
      testM("`zip`") {
        for {
          fiberRef <- FiberRef.make(initial)
          latch1   <- Promise.make[Nothing, Unit]
          latch2   <- Promise.make[Nothing, Unit]
          child1   <- (fiberRef.set("child1") *> latch1.succeed(())).fork
          child2   <- (fiberRef.set("child2") *> latch2.succeed(())).fork
          _        <- latch1.await *> latch2.await
          _        <- child1.zip(child2).inheritRefs
          value    <- fiberRef.get
        } yield assert(value, equalTo("child1"))
      }
    ),
    suite("`Fiber.join` on interrupted Fiber")(
      testM("is inner interruption") {
        val fiberId = Fiber.Id(0L, 123L)

        for {
          exit <- Fiber.interruptAs(fiberId).join.run
        } yield assert(exit, equalTo(Exit.interrupt(fiberId)))
      }
    ),
    suite("if one composed fiber fails then all must fail")(
      testM("`await`") {
        for {
          exit <- Fiber.fail("fail").zip(Fiber.never).await
        } yield assert(exit, fails(equalTo("fail")))
      },
      testM("`await(timeout)` for forked IO that runs forever, should be interrupted") {
        (for {
          f    <- IO(1).forever.fork
          exit <- f.await(1.second)
        } yield assert(exit, isInterrupted)).provide(Clock.Live)
      },
      testM("`await(timeout)` for forked IO that takes less time, should succeed") {
        (for {
          f    <- IO(1).fork
          exit <- f.await(100.millis)
        } yield assert(exit, succeeds(equalTo(1)))).provide(Clock.Live)
      },
      testM("`await(timeout)` for a Fiber that is done, should succeed") {
        assertM(Fiber.done(Exit.Success(1)).await(100.millis).provide(Clock.Live), succeeds(equalTo(1)))
      },
      testM("`await(timeout)` for a Future that will take more time to complete, should be interrupted") {
        assertM(
          Fiber
            .fromFuture(Future { Thread.sleep(2000); println("Hello") }(concurrent.ExecutionContext.global))
            .await(1.second)
            .provide(Clock.Live),
          isInterrupted
        )
      },
      testM("`await(timeout)` for a Future that will complete before the timeout, should succeed") {
        assertM(
          Fiber
            .fromFuture(Future(1)(concurrent.ExecutionContext.global))
            .await(1.second)
            .provide(Clock.Live),
          succeeds(equalTo(1))
        )
      },
      testM("`join`") {
        for {
          exit <- Fiber.fail("fail").zip(Fiber.never).join.run
        } yield assert(exit, fails(equalTo("fail")))
      },
      testM("`awaitAll`") {
        for {
          exit <- Fiber.awaitAll(Fiber.fail("fail") :: List.fill(100)(Fiber.never)).run
        } yield assert(exit, succeeds(isUnit))
      },
      testM("`joinAll`") {
        for {
          exit <- Fiber.awaitAll(Fiber.fail("fail") :: List.fill(100)(Fiber.never)).run
        } yield assert(exit, succeeds(isUnit))
      },
      testM("shard example") {
        def shard[R, E, A](queue: Queue[A], n: Int, worker: A => ZIO[R, E, Unit]): ZIO[R, E, Nothing] = {
          val worker1 = queue.take.flatMap(a => worker(a).uninterruptible).forever
          ZIO.forkAll(List.fill(n)(worker1)).flatMap(_.join) *> ZIO.never
        }
        for {
          queue  <- Queue.unbounded[Int]
          _      <- queue.offerAll(1 to 100)
          worker = (n: Int) => if (n == 100) ZIO.fail("fail") else queue.offer(n).unit
          exit   <- shard(queue, 4, worker).run
          _      <- queue.shutdown
        } yield assert(exit, fails(equalTo("fail")))
      }
    )
  )

  val (initial, update) = ("initial", "update")
}
