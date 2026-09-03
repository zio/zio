package zio

import zio.internal.SemaphorePlatform
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
    test("tryWithPermits releases permits if interrupted at the acquisition boundary") {
      // The interrupt has to land after the permits are taken but before the
      // guarded effect starts running, so race it against the acquisition at a
      // range of offsets rather than waiting for the effect to signal.
      def attempt(yields: Int) =
        for {
          sem   <- Semaphore.make(2L)
          fiber <- sem.tryWithPermits(2L)(ZIO.never).fork
          _     <- ZIO.yieldNow.repeatN(yields)
          _     <- fiber.interrupt
          // Deadlocks instead of completing if the permits were not returned.
          _       <- sem.withPermits(2L)(ZIO.unit)
          permits <- sem.available
        } yield permits

      ZIO
        .foreach(0 to 8)(yields => ZIO.foreach(1 to 20)(_ => attempt(yields)))
        .map(results => assertTrue(results.flatten.forall(_ == 2L)))
    } @@ timeout(30.seconds),
    test("withPermits releases permits if interrupted on the uncontended fast path") {
      // `withPermits` takes its permits with a bare CAS when they are free,
      // ahead of any suspension. If that CAS were to run while the fiber is
      // still interruptible, an interrupt landing between it and the
      // installation of the release would lose the permits permanently. Race
      // an interrupt against the acquisition at a range of offsets.
      def attempt(yields: Int) =
        for {
          sem   <- Semaphore.make(2L)
          fiber <- sem.withPermits(2L)(ZIO.never).fork
          _     <- ZIO.yieldNow.repeatN(yields)
          _     <- fiber.interrupt
          // Deadlocks instead of completing if the permits were not returned.
          _       <- sem.withPermits(2L)(ZIO.unit)
          permits <- sem.available
        } yield permits

      ZIO
        .foreach(0 to 8)(yields => ZIO.foreach(1 to 20)(_ => attempt(yields)))
        .map(results => assertTrue(results.flatten.forall(_ == 2L)))
    } @@ timeout(30.seconds),
    test("withPermitsScoped releases permits if interrupted on the uncontended fast path") {
      def attempt(yields: Int) =
        for {
          sem     <- Semaphore.make(2L)
          fiber   <- ZIO.scoped(sem.withPermitsScoped(2L) *> ZIO.never).fork
          _       <- ZIO.yieldNow.repeatN(yields)
          _       <- fiber.interrupt
          _       <- sem.withPermits(2L)(ZIO.unit)
          permits <- sem.available
        } yield permits

      ZIO
        .foreach(0 to 8)(yields => ZIO.foreach(1 to 20)(_ => attempt(yields)))
        .map(results => assertTrue(results.flatten.forall(_ == 2L)))
    } @@ timeout(30.seconds),
    test("withPermits body remains interruptible on the uncontended fast path") {
      // The fast path must not leak the acquisition's uninterruptible region
      // into the guarded effect.
      for {
        sem     <- Semaphore.make(1L)
        started <- Promise.make[Nothing, Unit]
        fiber   <- sem.withPermits(1L)(started.succeed(()) *> ZIO.never).fork
        _       <- started.await
        _       <- fiber.interrupt
        permits <- sem.available
      } yield assertTrue(permits == 1L)
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
    test("cancelled waiters do not accumulate behind a blocked head waiter") {
      // The head waiter asks for more permits than will ever be free, so it
      // never leaves the queue and tombstones are never reaped by reaching the
      // head. Every waiter behind it is then cancelled, as interruption does.
      val sem = new SemaphorePlatform(1L, fair = true)
      ZIO.succeed {
        val head = sem.enqueue(2L)
        var i    = 0
        while (i < 10000) {
          sem.cancel(sem.enqueue(1L))
          i += 1
        }
        (sem.queueSize(), sem.awaiting(), head)
      }.map { case (queued, awaiting, head) =>
        assertTrue(
          // Bounded by the sweep threshold rather than growing with the number
          // of cancellations.
          queued < 100L,
          // Only the blocked head waiter is still live.
          awaiting == 1L,
          !head.isDone
        )
      }
    },
    test("awaiting is unaffected by cancelled waiters") {
      val sem = new SemaphorePlatform(0L, fair = true)
      ZIO.succeed {
        val kept      = List.fill(3)(sem.enqueue(1L))
        val cancelled = List.fill(5)(sem.enqueue(1L))
        cancelled.foreach(sem.cancel)
        val afterCancel = sem.awaiting()
        kept.foreach(sem.cancel)
        (afterCancel, sem.awaiting())
      }.map { case (afterCancel, afterAll) =>
        assertTrue(afterCancel == 3L, afterAll == 0L)
      }
    },
    test("waiters interrupted behind a blocked head waiter are eventually reaped") {
      for {
        sem <- Semaphore.make(2L)
        // Holds one permit forever, so a waiter for two permits can never be
        // satisfied and pins the head of the queue.
        held    <- Promise.make[Nothing, Unit]
        holder  <- sem.withPermit(held.succeed(()) *> ZIO.never).fork
        _       <- held.await
        blocked <- sem.withPermits(2L)(ZIO.unit).fork
        _       <- sem.awaiting.repeatUntil(_ == 1)
        _ <- ZIO.foreachDiscard(1 to 200) { _ =>
               sem.withPermit(ZIO.never).fork.flatMap(_.interrupt)
             }
        // Every interrupted fiber has left, so only the blocked waiter remains.
        awaiting <- sem.awaiting.repeatUntil(_ == 1)
        _        <- blocked.interrupt
        _        <- holder.interrupt
        permits  <- sem.available
      } yield assertTrue(awaiting == 1L, permits == 2L)
    } @@ timeout(30.seconds),
    test("permits are released when the effect fails") {
      for {
        sem     <- Semaphore.make(2L)
        _       <- sem.withPermits(2L)(ZIO.fail("boom")).exit
        permits <- sem.available
      } yield assertTrue(permits == 2L)
    },
    test("withPermits(0) does not consume permits") {
      for {
        sem     <- Semaphore.make(1L)
        result  <- sem.withPermits(0L)(sem.withPermit(ZIO.succeed(42)))
        permits <- sem.available
      } yield assertTrue(result == 42, permits == 1L)
    },
    test("withPermits fails if requested permits is negative") {
      for {
        sem <- Semaphore.make(3L)
        ans <- sem.withPermits(-1L)(ZIO.unit).exit
      } yield assert(ans)(dies(isSubtype[IllegalArgumentException](anything)))
    },
    test("a waiter requesting more permits than are free waits until enough are released") {
      for {
        sem    <- Semaphore.make(2L)
        latch  <- Promise.make[Nothing, Unit]
        held   <- Promise.make[Nothing, Unit]
        fiber1 <- sem.withPermit(held.succeed(()) *> latch.await).fork
        _      <- held.await
        fiber2 <- sem.withPermits(2L)(ZIO.unit).fork
        _      <- sem.awaiting.repeatUntil(_ == 1)
        _      <- latch.succeed(())
        _      <- fiber1.join
        _      <- fiber2.join
        // Both fibers have finished, so all permits must have been returned
        permits <- sem.available
      } yield assertTrue(permits == 2L)
    } @@ timeout(10.seconds),
    test("interrupting a waiting fiber does not lose the permits it was granted") {
      for {
        sem   <- Semaphore.make(1L)
        held  <- Promise.make[Nothing, Unit]
        latch <- Promise.make[Nothing, Unit]
        // Holds the only permit until `latch` completes
        holder <- sem.withPermit(held.succeed(()) *> latch.await).fork
        _      <- held.await
        // Queues up behind the holder, then is interrupted while waiting
        waiter <- sem.withPermit(ZIO.unit).fork
        _      <- sem.awaiting.repeatUntil(_ == 1)
        _      <- waiter.interrupt
        _      <- latch.succeed(())
        _      <- holder.join
        // Whether the waiter was interrupted before or after being granted the
        // permit, the permit must end up back in the semaphore
        permits <- sem.available.repeatUntil(_ == 1L)
      } yield assertTrue(permits == 1L)
    } @@ timeout(10.seconds),
    test("permits are conserved under concurrent acquisition and interruption") {
      val n = 50
      for {
        sem     <- Semaphore.make(4L)
        fibers  <- ZIO.foreach(1 to n)(_ => sem.withPermits(2L)(ZIO.yieldNow).fork)
        _       <- ZIO.foreachDiscard(fibers.take(n / 2))(_.interrupt)
        _       <- ZIO.foreachDiscard(fibers)(_.await)
        permits <- sem.available.repeatUntil(_ == 4L)
      } yield assertTrue(permits == 4L)
    } @@ timeout(30.seconds),
    test("a fair semaphore grants permits in the order they were requested") {
      val n = 20
      for {
        sem   <- Semaphore.make(1L)
        held  <- Promise.make[Nothing, Unit]
        latch <- Promise.make[Nothing, Unit]
        order <- Ref.make(Chunk.empty[Int])
        // Take the only permit so that everyone below has to queue up
        holder <- sem.withPermit(held.succeed(()) *> latch.await).fork
        _      <- held.await
        // Fork the contenders one at a time, waiting until each is queued, so
        // that their arrival order at the semaphore is deterministic
        fibers <- ZIO.foreach(1 to n) { i =>
                    for {
                      fiber <- sem.withPermit(order.update(_ :+ i)).fork
                      _     <- sem.awaiting.repeatUntil(_ == i)
                    } yield fiber
                  }
        _      <- latch.succeed(())
        _      <- holder.join
        _      <- ZIO.foreachDiscard(fibers)(_.join)
        result <- order.get
      } yield assertTrue(result == Chunk.fromIterable(1 to n))
    } @@ timeout(30.seconds),
    test("available reports 0 while a fiber is waiting for more permits than are free") {
      for {
        sem <- Semaphore.make(2L)
        // Queues up for 5 permits, which this semaphore can never satisfy. The
        // 2 free permits are now reserved for this fiber, and in fair mode no
        // other fiber may take them, so none are available.
        waiter <- sem.withPermits(5L)(ZIO.unit).fork
        _      <- sem.awaiting.repeatUntil(_ == 1)
        queued <- sem.available
        taken  <- sem.tryWithPermit(ZIO.unit)
        // Once the waiter gives up, the permits are up for grabs again
        _         <- waiter.interrupt
        quiescent <- sem.available.repeatUntil(_ == 2L)
      } yield assertTrue(queued == 0L, taken.isEmpty, quiescent == 2L)
    } @@ timeout(10.seconds),
    suite("unfair")(
      test("available reports the free permits even while a fiber is waiting") {
        for {
          sem <- Semaphore.makeUnfair(2L)
          // Queues up for 5 permits, which this semaphore can never satisfy.
          // Barging is allowed here, so the 2 free permits really are up for
          // grabs and are reported as available.
          waiter    <- sem.withPermits(5L)(ZIO.unit).fork
          _         <- sem.awaiting.repeatUntil(_ == 1)
          queued    <- sem.available
          taken     <- sem.tryWithPermits(2L)(ZIO.unit)
          _         <- waiter.interrupt
          quiescent <- sem.available.repeatUntil(_ == 2L)
        } yield assertTrue(queued == 2L, taken.isDefined, quiescent == 2L)
      } @@ timeout(10.seconds),
      test("acquires and releases permits") {
        for {
          sem     <- Semaphore.makeUnfair(2L)
          result  <- sem.withPermits(2L)(ZIO.succeed(42))
          permits <- sem.available
        } yield assertTrue(result == 42, permits == 2L)
      },
      test("waiting fibers are eventually granted permits") {
        for {
          sem     <- Semaphore.makeUnfair(1L)
          held    <- Promise.make[Nothing, Unit]
          latch   <- Promise.make[Nothing, Unit]
          holder  <- sem.withPermit(held.succeed(()) *> latch.await).fork
          _       <- held.await
          waiter  <- sem.withPermit(ZIO.succeed(42)).fork
          _       <- sem.awaiting.repeatUntil(_ == 1)
          _       <- latch.succeed(())
          _       <- holder.join
          result  <- waiter.join
          permits <- sem.available
        } yield assertTrue(result == 42, permits == 1L)
      } @@ timeout(10.seconds),
      test("withPermit automatically releases the permit if the effect is interrupted") {
        for {
          promise   <- Promise.make[Nothing, Unit]
          semaphore <- Semaphore.makeUnfair(1)
          effect     = semaphore.withPermit(promise.succeed(()) *> ZIO.never)
          fiber     <- effect.fork
          _         <- promise.await
          _         <- fiber.interrupt
          permits   <- semaphore.available
        } yield assertTrue(permits == 1L)
      },
      test("permits are conserved under concurrent acquisition") {
        for {
          sem     <- Semaphore.makeUnfair(4L)
          _       <- ZIO.foreachParDiscard(1 to 50)(_ => sem.withPermits(2L)(ZIO.yieldNow))
          permits <- sem.available
        } yield assertTrue(permits == 4L)
      } @@ timeout(30.seconds)
    )
  ) @@ exceptJS(nonFlaky)
}
