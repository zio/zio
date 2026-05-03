package zio.concurrent

import zio._
import zio.test.Assertion._
import zio.test.TestAspect._
import zio.test._

import java.util.concurrent.atomic.AtomicInteger

object CyclicBarrierSpec extends ZIOSpecDefault {
  private val parties = 100

  val spec =
    suite("CyclicBarrierSpec")(
      test("Construction") {
        for {
          barrier  <- CyclicBarrier.make(parties)
          isBroken <- barrier.isBroken
          waiting  <- barrier.waiting
        } yield assert(barrier.parties)(equalTo(parties)) &&
          assert(isBroken)(equalTo(false)) &&
          assert(waiting)(equalTo(0))
      } @@ exceptJS(nonFlaky(10000)),
      test("Releases the barrier") {
        for {
          barrier <- CyclicBarrier.make(2)
          f1      <- barrier.await.fork
          _       <- f1.status.repeatWhile(!_.isInstanceOf[Fiber.Status.Suspended])
          f2      <- barrier.await.fork
          ticket1 <- f1.join
          ticket2 <- f2.join
        } yield assert(ticket1)(equalTo(1)) &&
          assert(ticket2)(equalTo(0))
      } @@ exceptJS(nonFlaky(10000)),
      test("Releases the barrier and performs the action") {
        for {
          promise    <- Promise.make[Nothing, Unit]
          barrier    <- CyclicBarrier.make(2, promise.succeed(()))
          f1         <- barrier.await.fork
          _          <- f1.status.repeatWhile(!_.isInstanceOf[Fiber.Status.Suspended])
          f2         <- barrier.await.fork
          _          <- f1.join
          _          <- f2.join
          isComplete <- promise.isDone
        } yield assert(isComplete)(isTrue)
      } @@ exceptJS(nonFlaky(10000)),
      test("Releases the barrier and cycles") {
        for {
          barrier <- CyclicBarrier.make(2)
          f1      <- barrier.await.fork
          _       <- f1.status.repeatWhile(!_.isInstanceOf[Fiber.Status.Suspended])
          f2      <- barrier.await.fork
          ticket1 <- f1.join
          ticket2 <- f2.join
          f3      <- barrier.await.fork
          _       <- f3.status.repeatWhile(!_.isInstanceOf[Fiber.Status.Suspended])
          f4      <- barrier.await.fork
          ticket3 <- f3.join
          ticket4 <- f4.join
        } yield assert(ticket1)(equalTo(1)) &&
          assert(ticket2)(equalTo(0)) &&
          assert(ticket3)(equalTo(1)) &&
          assert(ticket4)(equalTo(0))
      } @@ exceptJS(nonFlaky(10000)),
      test("Breaks on reset") {
        for {
          barrier <- CyclicBarrier.make(parties)
          f1      <- barrier.await.fork
          f2      <- barrier.await.fork
          _       <- f1.status.repeatWhile(!_.isInstanceOf[Fiber.Status.Suspended])
          _       <- f2.status.repeatWhile(!_.isInstanceOf[Fiber.Status.Suspended])
          _       <- barrier.reset
          res1    <- f1.await
          res2    <- f2.await
        } yield assert(res1)(fails(isUnit)) && assert(res2)(fails(isUnit))
      } @@ exceptJS(nonFlaky(1000)),
      test("Breaks on party interruption") {
        for {
          latch     <- Promise.make[Nothing, Unit]
          barrier   <- CyclicBarrier.make(parties)
          f1        <- barrier.await.timeout(1.second).tap(_ => latch.succeedUnit).fork
          f2        <- barrier.await.fork
          _         <- f1.status.repeatWhile(!_.isInstanceOf[Fiber.Status.Suspended])
          _         <- f2.status.repeatWhile(!_.isInstanceOf[Fiber.Status.Suspended])
          isBroken1 <- barrier.isBroken
          _         <- TestClock.adjust(1.second)
          _         <- latch.await
          isBroken2 <- barrier.isBroken
          res1      <- f1.await
          res2      <- f2.await
        } yield assert(isBroken1)(isFalse) &&
          assert(isBroken2)(isTrue) &&
          assert(res1)(succeeds(isNone)) &&
          assert(res2)(fails(isUnit))
      } @@ exceptJS(nonFlaky),
      suite("is stable under high contention")(
        test("when fibers == parties") {
          final class Counter {
            private val ref = new AtomicInteger(0)

            def inc(n: Int): Unit = {
              val newN = ref.updateAndGet(_ + n)
              if (newN < -1 || newN > 1) throw new IllegalStateException(s"invalid ref state: $newN")
            }

            def get: Int = ref.get
          }

          def make1(cb: CyclicBarrier, ref: Counter)(n: Int) =
            cb.await.as(ref.inc(n)).repeatN(1000)

          for {
            ref <- ZIO.succeed(new Counter)
            cb  <- CyclicBarrier.make(2)
            make = make1(cb, ref)(_)
            _   <- ZIO.collectAllParDiscard(List(make(1), make(-1)))
            v    = ref.get
          } yield assertTrue(v == 0)
        },
        test("when fibers > parties") {
          val nFibers  = 20
          val nParties = 4
          for {
            cb <- CyclicBarrier.make(4)
            // We can't guarantee that the last n - 1 fibers will be completed
            latch <- CountdownLatch.make(nFibers - nParties + 1)
            f     <- ZIO.foreachParDiscard(1 to nFibers)(_ => cb.await.repeatN(500) *> latch.countDown).fork
            _     <- latch.await
            _     <- f.interrupt
          } yield assertCompletes
        }
      ) @@ jvm(nonFlaky) @@ timeout(30.seconds)
    ) @@ timed

}
