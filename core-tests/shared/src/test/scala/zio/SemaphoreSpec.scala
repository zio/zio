package zio

import zio.test.Assertion._
import zio.test.TestAspect._
import zio.test._

object SemaphoreSpec extends ZIOBaseSpec {
  override def spec = suite("SemaphoreSpec")(
    test("withPermit automatically releases the permit if the effect is interrupted") {
      for {
        promise   <- Promise.make[Nothing, Unit]
        semaphore <- Semaphore.make(1)
        effect     = semaphore.withPermit(promise.succeed(()) *> ZIO.never)
        fiber     <- effect.fork
        _         <- promise.await
        _         <- fiber.interrupt
        permits   <- semaphore.available
      } yield assert(permits)(equalTo(1L))
    },
    test("withPermit acquire is interruptible") {
      for {
        semaphore <- Semaphore.make(0L)
        effect     = semaphore.withPermit(ZIO.unit)
        fiber     <- effect.fork
        _         <- fiber.interrupt
      } yield assertCompletes
    },
    test("withPermitsScoped releases same number of permits") {
      for {
        semaphore <- Semaphore.make(2L)
        _         <- ZIO.scoped(semaphore.withPermitsScoped(2))
        permits   <- semaphore.available
      } yield assertTrue(permits == 2L)
    },
    test("tryWithPermits acquires and releases same number of permits") {
      for {
        sem     <- Semaphore.make(3L)
        ans     <- sem.tryWithPermits(2L)(ZIO.unit)
        permits <- sem.available
      } yield assertTrue(permits == 3L && ans.isDefined)
    },
    test("tryWithPermits if 0 permits requested") {
      for {
        sem     <- Semaphore.make(3L)
        ans     <- sem.tryWithPermits(0L)(ZIO.succeed("I got executed"))
        permits <- sem.available
      } yield assertTrue(permits == 3L && ans.contains("I got executed"))
    },
    test("tryWithPermits returns None if no permits available") {
      for {
        sem     <- Semaphore.make(3L)
        ans     <- sem.tryWithPermits(4L)(ZIO.succeed("Shouldn't get executed"))
        permits <- sem.available
      } yield assertTrue(permits == 3L && ans.isEmpty)
    },
    test("tryWithPermit acquires and releases same number of permits") {
      for {
        sem     <- Semaphore.make(3L)
        ans     <- sem.tryWithPermit(ZIO.unit)
        permits <- sem.available
      } yield assertTrue(permits == 3L && ans.isDefined)
    },
    test("tryWithPermits fails if requested permits in negative number") {
      for {
        sem <- Semaphore.make(3L)
        ans <- sem.tryWithPermits(-1L)(ZIO.unit).exit
      } yield assert(ans)(dies(isSubtype[IllegalArgumentException](anything)))
    },
    test("tryWithPermits restores permits after failure") {
      for {
        sem     <- Semaphore.make(3L)
        failure  = ZIO.fail("exception")
        result  <- sem.tryWithPermits(2L)(failure).exit
        permits <- sem.available
      } yield assertTrue(
        permits == 3L,
        result.isFailure,
        result == Exit.fail("exception")
      )
    },
    test("awaiting returns the count of waiting fibers") {
      for {
        semaphore    <- Semaphore.make(1)
        promise      <- Promise.make[Nothing, Unit]
        _            <- ZIO.foreachDiscard(1 to 11)(_ => semaphore.withPermit(promise.await).fork)
        waitingStart <- semaphore.awaiting.repeatUntil(_ == 10)
        _            <- promise.succeed(())
        waitingEnd   <- semaphore.awaiting.repeatUntil(_ == 0)
      } yield assertTrue(waitingStart == 10, waitingEnd == 0)
    } @@ timeout(10.seconds),
    test("interruption of partially satisfied multi-permit waiter wakes subsequent waiters") {
      for {
        semaphore <- Semaphore.make(1L)
        gate      <- Promise.make[Nothing, Unit]
        woken     <- Promise.make[Nothing, Unit]
        fiber1    <- semaphore.withPermits(2L)(ZIO.never).fork
        _         <- semaphore.awaiting.repeatUntil(_ == 1L)
        fiber2    <- semaphore.withPermit(woken.succeed(()) *> gate.await).fork
        _         <- semaphore.awaiting.repeatUntil(_ == 2L)
        _         <- fiber1.interrupt
        _         <- woken.await
        _         <- gate.succeed(())
        _         <- fiber2.join
        permits   <- semaphore.available
        waiting   <- semaphore.awaiting
      } yield assertTrue(permits == 1L, waiting == 0L)
    } @@ timeout(10.seconds),
    test("high contention single-permit semaphore preserves mutual exclusion and permit count") {
      for {
        semaphore <- Semaphore.make(1L)
        counter   <- Ref.make(0L)
        active    <- Ref.make(0L)
        maxActive <- Ref.make(0L)
        workers = ZIO.foreachParDiscard(1 to 8) { _ =>
                    ZIO.replicateZIODiscard(250) {
                      semaphore.withPermit {
                        for {
                          cur <- active.updateAndGet(_ + 1L)
                          _   <- maxActive.update( math.max(_, cur))
                          _   <- counter.update(_ + 1L)
                          _   <- active.update(_ - 1L)
                        } yield ()
                      }
                    }
                  }
        _       <- workers
        total   <- counter.get
        peak    <- maxActive.get
        permits <- semaphore.available
        waiting <- semaphore.awaiting
      } yield assertTrue(total == 2000L, peak == 1L, permits == 1L, waiting == 0L)
    } @@ timeout(10.seconds)
  ) @@ exceptJS(nonFlaky)
}
