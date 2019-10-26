package zio

import zio.test._
import zio.test.Assertion._
import zio.LatchOps._
import FiberSpecData._

object FiberSpec
    extends ZIOBaseSpec(
      suite("FiberSpec")(
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
              _     <- child.map(_ => ()).inheritFiberRefs
              value <- fiberRef.get
            } yield assert(value, equalTo(update))
          },
          testM("`orElse`") {
            for {
              fiberRef  <- FiberRef.make(initial)
              semaphore <- Semaphore.make(2)
              _         <- semaphore.acquireN(2)
              child1    <- (fiberRef.set("child1") *> semaphore.release).fork
              child2    <- (fiberRef.set("child2") *> semaphore.release).fork
              _         <- semaphore.acquireN(2)
              _         <- child1.orElse(child2).inheritFiberRefs
              value     <- fiberRef.get
            } yield assert(value, equalTo("child1"))
          },
          testM("`zip`") {
            for {
              fiberRef  <- FiberRef.make(initial)
              semaphore <- Semaphore.make(2)
              _         <- semaphore.acquireN(2)
              child1    <- (fiberRef.set("child1") *> semaphore.release).fork
              child2    <- (fiberRef.set("child2") *> semaphore.release).fork
              _         <- semaphore.acquireN(2)
              _         <- child1.zipPar(child2).inheritFiberRefs
              value     <- fiberRef.get
            } yield assert(value, equalTo("child1"))
          }
        ),
        suite("`Fiber.join` on interrupted Fiber")(
          testM("is inner interruption") {
            for {
              exit <- Fiber.interrupt.join.run
            } yield assert(exit, equalTo(Exit.interrupt))
          }
        ),
        suite("if one composed fiber fails then all must fail")(
          testM("`await`") {
            for {
              exit <- Fiber.fail("fail").zipPar(Fiber.never).await
            } yield assert(exit, fails(equalTo("fail")))
          },
          testM("`join`") {
            for {
              exit <- Fiber.fail("fail").zipPar(Fiber.never).join.run
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
    )

object FiberSpecData {
  val (initial, update) = ("initial", "update")
}
