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
    },
    test("withPermits acquires multiple permits when available") {
      for {
        sem     <- Semaphore.make(5L)
        result  <- sem.withPermits(3L)(ZIO.succeed("acquired"))
        permits <- sem.available
      } yield assertTrue(result == "acquired", permits == 5L)
    },
    test("withPermits blocks when not enough permits available") {
      for {
        sem      <- Semaphore.make(2L)
        started  <- Promise.make[Nothing, Unit]
        blocked  <- Promise.make[Nothing, Unit]
        _        <- sem.withPermits(2L)(started.succeed(()) *> blocked.await).fork
        _        <- started.await
        fiber    <- sem.withPermits(2L)(ZIO.succeed("acquired")).fork
        awaiting <- sem.awaiting.repeatUntil(_ > 0)
        _        <- blocked.succeed(())
        result   <- fiber.join
        permits  <- sem.available
      } yield assertTrue(awaiting > 0, result == "acquired", permits == 2L)
    },
    test("withPermits dies when n > max permits") {
      for {
        sem    <- Semaphore.make(3L)
        result <- sem.withPermits(5L)(ZIO.unit).exit
      } yield assert(result)(dies(isSubtype[IllegalArgumentException](anything)))
    },
    test("withPermitsScoped acquires multiple permits when available") {
      for {
        sem     <- Semaphore.make(5L)
        result  <- ZIO.scoped(sem.withPermitsScoped(3L) *> ZIO.succeed("acquired"))
        permits <- sem.available
      } yield assertTrue(result == "acquired", permits == 5L)
    },
    test("withPermits releases multiple permits on interruption") {
      for {
        sem     <- Semaphore.make(3L)
        started <- Promise.make[Nothing, Unit]
        fiber   <- sem.withPermits(3L)(started.succeed(()) *> ZIO.never).fork
        _       <- started.await
        before  <- sem.available
        _       <- fiber.interrupt
        after   <- sem.available
      } yield assertTrue(before == 0L, after == 3L)
    },
    test("withPermits releases correct permits after partial fulfillment and interruption") {
      for {
        sem      <- Semaphore.make(5L)
        signal1  <- Promise.make[Nothing, Unit]
        started1 <- Promise.make[Nothing, Unit]
        fiber1   <- sem.withPermits(3L)(started1.succeed(()) *> signal1.await).fork
        _        <- started1.await

        started2 <- Promise.make[Nothing, Unit]
        fiber2   <- (started2.succeed(()) *> sem.withPermits(4L)(ZIO.never)).fork
        _        <- started2.await
        _        <- sem.awaiting.repeatUntil(_ > 0)

        _ <- signal1.succeed(())
        _ <- fiber1.join

        _         <- fiber2.interrupt
        available <- sem.available
      } yield assertTrue(available == 5L)
    },
    suite("State")(
      test("apply creates a negative state") {
        val state = Semaphore.State(2, 5)
        assertTrue(state < 0)
      },
      test("available returns permits for positive state") {
        assertTrue(
          Semaphore.State.available(0L) == 0L,
          Semaphore.State.available(5L) == 5L,
          Semaphore.State.available(100L) == 100L
        )
      },
      test("available returns 0 for negative state") {
        val state = Semaphore.State(3, 10)
        assertTrue(Semaphore.State.available(state) == 0L)
      },
      test("waiters extracts the correct waiter count") {
        val state = Semaphore.State(3, 10)
        assertTrue(Semaphore.State.waiters(state) == 3L)
      },
      test("waiters returns 0 for non-negative state") {
        assertTrue(
          Semaphore.State.waiters(0L) == 0L,
          Semaphore.State.waiters(5L) == 0L,
          Semaphore.State.waiters(100L) == 0L
        )
      },
      test("demand extracts the correct permit count") {
        val state = Semaphore.State(3, 10)
        assertTrue(Semaphore.State.demand(state) == 10L)
      },
      test("demand returns 0 for non-negative state") {
        assertTrue(
          Semaphore.State.demand(0L) == 0L,
          Semaphore.State.demand(5L) == 0L,
          Semaphore.State.demand(100L) == 0L
        )
      },
      test("awaited returns true for negative state") {
        val state = Semaphore.State(3, 10)
        assertTrue(Semaphore.State.awaited(state))
      },
      test("awaited returns false for non-negative state") {
        assertTrue(
          !Semaphore.State.awaited(0L),
          !Semaphore.State.awaited(5L),
          !Semaphore.State.awaited(100L)
        )
      },
      test("roundtrip preserves waiters and demand") {
        val waiters = 7L
        val demand  = 42L
        val state   = Semaphore.State(waiters, demand)
        assertTrue(
          Semaphore.State.waiters(state) == waiters,
          Semaphore.State.demand(state) == demand
        )
      },
      test("addWaiter from zero state creates state with 1 waiter") {
        val state = Semaphore.State.addWaiter(0L)(5L)
        assertTrue(
          state < 0,
          Semaphore.State.waiters(state) == 1L,
          Semaphore.State.demand(state) == 5L
        )
      },
      test("addWaiter from positive state consumes available permits") {
        val state = Semaphore.State.addWaiter(10L)(13L)
        assertTrue(
          state < 0,
          Semaphore.State.waiters(state) == 1L,
          Semaphore.State.demand(state) == 3L
        )
      },
      test("addWaiter to negative state increments waiter count and adds permits") {
        val initial = Semaphore.State(2, 5)
        val updated = Semaphore.State.addWaiter(initial)(3L)
        assertTrue(
          Semaphore.State.waiters(updated) == 3L,
          Semaphore.State.demand(updated) == 8L
        )
      },
      test("removeWaiter decrements waiter count and subtracts permits") {
        val initial = Semaphore.State(3, 10)
        val updated = Semaphore.State.removeWaiter(initial)(4L)
        assertTrue(
          Semaphore.State.waiters(updated) == 2L,
          Semaphore.State.demand(updated) == 6L
        )
      },
      test("removeWaiter returns 0 when last waiter is removed") {
        val initial = Semaphore.State(1, 5)
        val updated = Semaphore.State.removeWaiter(initial)(5L)
        assertTrue(updated == 0L)
      },
      test("reduceDemand reduces demand without changing waiter count") {
        val initial = Semaphore.State(2, 10)
        val updated = Semaphore.State.reduceDemand(initial)(3L)
        assertTrue(
          Semaphore.State.waiters(updated) == 2L,
          Semaphore.State.demand(updated) == 7L
        )
      },
      test("release adds permits back to available pool") {
        val result = Semaphore.State.release(5L)(3L, 100L)
        assertTrue(result == 8L)
      },
      test("release caps at maxPermits") {
        val result = Semaphore.State.release(5L)(10L, 10L)
        assertTrue(result == 10L)
      },
      test("handles large waiter counts") {
        val largeWaiters = 1000000L
        val demand       = 5000000L
        val state        = Semaphore.State(largeWaiters, demand)
        assertTrue(
          Semaphore.State.waiters(state) == largeWaiters,
          Semaphore.State.demand(state) == demand
        )
      },
      test("handles maximum values") {
        val state = Semaphore.State(Semaphore.State.MaxWaiters, Semaphore.State.MaxDemand)
        assertTrue(
          Semaphore.State.waiters(state) == Semaphore.State.MaxWaiters,
          Semaphore.State.demand(state) == Semaphore.State.MaxDemand
        )
      }
    )
  ) @@ exceptJS(nonFlaky(25)) @@ timeout(5.seconds)
}
