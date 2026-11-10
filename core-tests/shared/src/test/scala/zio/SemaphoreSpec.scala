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
    test("withPermitsScoped does not hang if scope is closed while fiber is waiting") {
      for {
        sem    <- Semaphore.make(1L)
        held   <- Promise.make[Nothing, Unit]
        _      <- sem.withPermit(held.succeed(()) *> ZIO.never).fork
        _      <- held.await
        scope  <- Scope.make
        waiter <- scope.extend(sem.withPermitScoped).fork
        _      <- waiter.status.repeatUntil(_.isInstanceOf[Fiber.Status.Suspended])
        _      <- scope.close(Exit.unit)
        result <- Live.live(waiter.join.exit.timeout(5.seconds))
        avail  <- sem.available
        queued <- sem.awaiting
      } yield assertTrue(result.isDefined, result.get.isInterrupted, avail == 0L, queued == 0L)
    },
    test("multiple waiters: withPermitsScoped closing scope wakes only targeted waiter and preserves queue") {
      for {
        sem     <- Semaphore.make(1L)
        held    <- Promise.make[Nothing, Unit]
        holder  <- sem.withPermit(held.succeed(()) *> ZIO.never).fork
        _       <- held.await
        scope1  <- Scope.make
        scope2  <- Scope.make
        waiter1 <- scope1.extend(sem.withPermitScoped).fork
        waiter2 <- scope2.extend(sem.withPermitScoped).fork
        _       <- waiter1.status.repeatUntil(_.isInstanceOf[Fiber.Status.Suspended])
        _       <- waiter2.status.repeatUntil(_.isInstanceOf[Fiber.Status.Suspended])
        _       <- scope1.close(Exit.unit)
        res1    <- Live.live(waiter1.join.exit.timeout(5.seconds))
        queued1 <- sem.awaiting
        _       <- holder.interrupt
        res2    <- Live.live(waiter2.join.exit.timeout(5.seconds))
        queued2 <- sem.awaiting
        _       <- scope2.close(Exit.unit)
        avail   <- sem.available
      } yield assertTrue(
        res1.isDefined,
        res1.get.isInterrupted,
        queued1 == 1L,
        res2.isDefined,
        queued2 == 0L,
        avail == 1L
      )
    },
    test("FIFO queue ordering across partial reservation") {
      for {
        sem     <- Semaphore.make(2L)
        p1      <- Promise.make[Nothing, Unit]
        f1      <- sem.withPermits(2L)(p1.succeed(()) *> ZIO.never).fork
        _       <- p1.await
        p2      <- Promise.make[Nothing, Unit]
        p3      <- Promise.make[Nothing, Unit]
        order   <- Ref.make(List.empty[Int])
        f2      <- sem.withPermits(2L)(p2.succeed(()) *> order.update(2 :: _)).fork
        _       <- sem.awaiting.repeatUntil(_ == 1L)
        f3      <- sem.withPermit(p3.succeed(()) *> order.update(1 :: _)).fork
        _       <- sem.awaiting.repeatUntil(_ == 2L)
        _       <- f1.interrupt
        _       <- p2.await
        _       <- f2.join
        _       <- p3.await
        _       <- f3.join
        result  <- order.get
        permits <- sem.available
      } yield assertTrue(result == List(1, 2), permits == 2L)
    },
    test("high contention acquire/release retains exact permit balance") {
      for {
        sem   <- Semaphore.make(5L)
        total  = 20
        ops    = 50
        count <- Ref.make(0)
        _ <- ZIO.foreachParDiscard(1 to total) { _ =>
               sem.withPermit(count.update(_ + 1)).repeatN(ops)
             }
        finalCount <- count.get
        permits    <- sem.available
        queued     <- sem.awaiting
      } yield assertTrue(
        finalCount == total * (ops + 1),
        permits == 5L,
        queued == 0L
      )
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
    } @@ timeout(10.seconds)
  ) @@ exceptJS(nonFlaky)
}
