package zio

import zio.test.Assertion._
import zio.test.TestAspect._
import zio.test._

object SemaphoreSpec extends ZIOBaseSpec {
  override def spec = suite("SemaphoreSpec")(
    suite("fair")((common(Semaphore.make(_)) ++ fairOnly): _*),
    suite("unfair")((common(Semaphore.makeUnfair(_)) ++ unfairOnly): _*)
  ) @@ exceptJS(nonFlaky)

  /** Tests that hold for both the fair and the unfair semaphore. */
  private def common(make: Long => UIO[Semaphore]): List[Spec[Any, Any]] = List(
    test("withPermit automatically releases the permit if the effect is interrupted") {
      for {
        promise   <- Promise.make[Nothing, Unit]
        semaphore <- make(1)
        effect     = semaphore.withPermit(promise.succeed(()) *> ZIO.never)
        fiber     <- effect.fork
        _         <- promise.await
        _         <- fiber.interrupt
        permits   <- semaphore.available
      } yield assert(permits)(equalTo(1L))
    },
    test("withPermit acquire is interruptible") {
      for {
        semaphore <- make(0L)
        effect     = semaphore.withPermit(ZIO.unit)
        fiber     <- effect.fork
        _         <- fiber.interrupt
      } yield assertCompletes
    },
    test("withPermitsScoped releases same number of permits") {
      for {
        semaphore <- make(2L)
        _         <- ZIO.scoped(semaphore.withPermitsScoped(2))
        permits   <- semaphore.available
      } yield assertTrue(permits == 2L)
    },
    test("tryWithPermits acquires and releases same number of permits") {
      for {
        sem     <- make(3L)
        ans     <- sem.tryWithPermits(2L)(ZIO.unit)
        permits <- sem.available
      } yield assertTrue(permits == 3L && ans.isDefined)
    },
    test("tryWithPermits if 0 permits requested") {
      for {
        sem     <- make(3L)
        ans     <- sem.tryWithPermits(0L)(ZIO.succeed("I got executed"))
        permits <- sem.available
      } yield assertTrue(permits == 3L && ans.contains("I got executed"))
    },
    test("tryWithPermits returns None if no permits available") {
      for {
        sem     <- make(3L)
        ans     <- sem.tryWithPermits(4L)(ZIO.succeed("Shouldn't get executed"))
        permits <- sem.available
      } yield assertTrue(permits == 3L && ans.isEmpty)
    },
    test("tryWithPermit acquires and releases same number of permits") {
      for {
        sem     <- make(3L)
        ans     <- sem.tryWithPermit(ZIO.unit)
        permits <- sem.available
      } yield assertTrue(permits == 3L && ans.isDefined)
    },
    test("tryWithPermits fails if requested permits in negative number") {
      for {
        sem <- make(3L)
        ans <- sem.tryWithPermits(-1L)(ZIO.unit).exit
      } yield assert(ans)(dies(isSubtype[IllegalArgumentException](anything)))
    },
    test("tryWithPermits restores permits after failure") {
      for {
        sem     <- make(3L)
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
        semaphore    <- make(1)
        promise      <- Promise.make[Nothing, Unit]
        _            <- ZIO.foreachDiscard(1 to 11)(_ => semaphore.withPermit(promise.await).fork)
        waitingStart <- semaphore.awaiting.repeatUntil(_ == 10)
        _            <- promise.succeed(())
        waitingEnd   <- semaphore.awaiting.repeatUntil(_ == 0)
      } yield assertTrue(waitingStart == 10, waitingEnd == 0)
    } @@ timeout(10.seconds),
    test("withPermit provides mutual exclusion under contention") {
      val active    = new java.util.concurrent.atomic.AtomicInteger(0)
      val violation = new java.util.concurrent.atomic.AtomicInteger(0)
      val body = ZIO.succeed {
        if (active.incrementAndGet() > 1) violation.incrementAndGet()
        active.decrementAndGet()
      }
      for {
        sem   <- make(1L)
        _     <- ZIO.foreachParDiscard(1 to 10)(_ => sem.withPermit(body).repeatN(999))
        after <- sem.available
      } yield assertTrue(violation.get == 0, after == 1L)
    } @@ timeout(30.seconds),
    test("withPermits never over-allocates permits under contention") {
      val active    = new java.util.concurrent.atomic.AtomicInteger(0)
      val violation = new java.util.concurrent.atomic.AtomicInteger(0)
      def body(n: Int) = ZIO.succeed {
        if (active.addAndGet(n) > 5) violation.incrementAndGet()
        active.addAndGet(-n)
      }
      for {
        sem <- make(5L)
        _   <- ZIO.foreachParDiscard(1 to 10)(i => sem.withPermits((i % 5 + 1).toLong)(body(i % 5 + 1)).repeatN(499))
      } yield assertTrue(violation.get == 0)
    } @@ timeout(30.seconds),
    test("withPermits waits for all requested permits and releases them") {
      for {
        sem      <- make(2L)
        gate     <- Promise.make[Nothing, Unit]
        holder   <- sem.withPermits(2L)(gate.await).fork
        _        <- sem.available.repeatUntil(_ == 0L)
        waiter   <- sem.withPermits(2L)(ZIO.unit).fork
        _        <- sem.awaiting.repeatUntil(_ == 1L)
        before   <- sem.available
        _        <- gate.succeed(())
        _        <- holder.join
        _        <- waiter.join
        after    <- sem.available
        awaiting <- sem.awaiting
      } yield assertTrue(before == 0L, after == 2L, awaiting == 0L)
    } @@ timeout(10.seconds),
    test("interrupting a waiting fiber removes it from the queue without losing permits") {
      for {
        sem      <- make(1L)
        gate     <- Promise.make[Nothing, Unit]
        holder   <- sem.withPermit(gate.await).fork
        _        <- sem.available.repeatUntil(_ == 0L)
        waiter   <- sem.withPermit(ZIO.unit).fork
        _        <- sem.awaiting.repeatUntil(_ == 1L)
        _        <- waiter.interrupt
        awaiting <- sem.awaiting
        _        <- gate.succeed(())
        _        <- holder.join
        after    <- sem.available
      } yield assertTrue(awaiting == 0L, after == 1L)
    } @@ timeout(10.seconds),
    test("interrupting the head waiter lets later waiters proceed") {
      for {
        sem    <- make(2L)
        gate   <- Promise.make[Nothing, Unit]
        holder <- sem.withPermits(2L)(gate.await).fork
        _      <- sem.available.repeatUntil(_ == 0L)
        big    <- sem.withPermits(2L)(ZIO.unit).fork
        _      <- sem.awaiting.repeatUntil(_ == 1L)
        small  <- sem.withPermit(ZIO.unit).fork
        _      <- sem.awaiting.repeatUntil(_ == 2L)
        _      <- big.interrupt
        _      <- gate.succeed(())
        _      <- holder.join
        _      <- small.join
        after  <- sem.available
      } yield assertTrue(after == 2L)
    } @@ timeout(10.seconds),
    test("interrupting waiters in the middle and at the back of the queue keeps the rest queued") {
      for {
        sem    <- make(1L)
        gate   <- Promise.make[Nothing, Unit]
        ran    <- Ref.make(Set.empty[Int])
        holder <- sem.withPermit(gate.await).fork
        _      <- sem.available.repeatUntil(_ == 0L)
        fibers <- ZIO.foreach(1 to 5) { i =>
                    sem.withPermit(ran.update(_ + i)).fork <* sem.awaiting.repeatUntil(_ == i.toLong)
                  }
        _        <- fibers(1).interrupt *> fibers(3).interrupt *> fibers(4).interrupt
        awaiting <- sem.awaiting
        _        <- gate.succeed(())
        _        <- holder.join
        _        <- fibers(0).join *> fibers(2).join
        result   <- ran.get
        after    <- sem.available
      } yield assertTrue(awaiting == 2L, result == Set(1, 3), after == 1L)
    } @@ timeout(10.seconds),
    test("withPermitScoped is interruptible while waiting and does not leak permits") {
      for {
        sem    <- make(1L)
        gate   <- Promise.make[Nothing, Unit]
        holder <- sem.withPermit(gate.await).fork
        _      <- sem.available.repeatUntil(_ == 0L)
        waiter <- ZIO.scoped(sem.withPermitScoped *> ZIO.never).fork
        _      <- sem.awaiting.repeatUntil(_ == 1L)
        _      <- waiter.interrupt
        _      <- gate.succeed(())
        _      <- holder.join
        after  <- sem.available
      } yield assertTrue(after == 1L)
    } @@ timeout(10.seconds),
    // In the next two tests each waiter's body needs the other waiter to be
    // running, so they finish only if the released permits wake both.
    test("a multi-permit release wakes every waiter it can satisfy") {
      for {
        sem    <- make(2L)
        gate   <- Promise.make[Nothing, Unit]
        ranA   <- Promise.make[Nothing, Unit]
        ranB   <- Promise.make[Nothing, Unit]
        holder <- sem.withPermits(2L)(gate.await).fork
        _      <- sem.available.repeatUntil(_ == 0L)
        a      <- sem.withPermit(ranA.succeed(()) *> ranB.await).fork
        _      <- sem.awaiting.repeatUntil(_ == 1L)
        b      <- sem.withPermit(ranB.succeed(()) *> ranA.await).fork
        _      <- sem.awaiting.repeatUntil(_ == 2L)
        _      <- gate.succeed(())
        _      <- holder.join *> a.join *> b.join
        after  <- sem.available
      } yield assertTrue(after == 2L)
    } @@ timeout(10.seconds),
    test("simultaneous releases wake every waiter they can satisfy") {
      for {
        sem     <- make(2L)
        gate    <- Promise.make[Nothing, Unit]
        ranA    <- Promise.make[Nothing, Unit]
        ranB    <- Promise.make[Nothing, Unit]
        holders <- ZIO.foreach(1 to 2)(_ => sem.withPermit(gate.await).fork)
        _       <- sem.available.repeatUntil(_ == 0L)
        a       <- sem.withPermit(ranA.succeed(()) *> ranB.await).fork
        _       <- sem.awaiting.repeatUntil(_ == 1L)
        b       <- sem.withPermit(ranB.succeed(()) *> ranA.await).fork
        _       <- sem.awaiting.repeatUntil(_ == 2L)
        _       <- gate.succeed(())
        _       <- ZIO.foreachDiscard(holders)(_.join) *> a.join *> b.join
        after   <- sem.available
      } yield assertTrue(after == 2L)
    } @@ timeout(10.seconds),
    test("a scoped waiter that stops waiting does not hold up other waiters") {
      for {
        sem    <- make(1L)
        gate   <- Promise.make[Nothing, Unit]
        scope  <- Scope.make
        holder <- sem.withPermit(gate.await).fork
        _      <- sem.available.repeatUntil(_ == 0L)
        dead   <- scope.extend[Any](sem.withPermitScoped).fork
        _      <- sem.awaiting.repeatUntil(_ == 1L)
        live   <- sem.withPermit(ZIO.unit).fork
        _      <- sem.awaiting.repeatUntil(_ == 2L)
        _      <- dead.interrupt
        _      <- gate.succeed(())
        _      <- holder.join *> live.join
        _      <- scope.close(Exit.unit)
        after  <- sem.available
      } yield assertTrue(after == 1L)
    } @@ timeout(10.seconds),
    test("interrupting a scoped waiter as it is granted neither loses nor duplicates permits") {
      for {
        sem    <- make(1L)
        gate   <- Promise.make[Nothing, Unit]
        scope  <- Scope.make
        holder <- sem.withPermit(gate.await).fork
        _      <- sem.available.repeatUntil(_ == 0L)
        waiter <- scope.extend[Any](sem.withPermitScoped).fork
        _      <- sem.awaiting.repeatUntil(_ == 1L)
        _      <- gate.succeed(()) <&> waiter.interrupt
        _      <- holder.join
        _      <- scope.close(Exit.unit)
        after  <- sem.available
      } yield assertTrue(after == 1L)
    } @@ timeout(10.seconds),
    test("closing a scoped waiter's scope as it is woken does not leak permits") {
      for {
        sem    <- make(1L)
        gate   <- Promise.make[Nothing, Unit]
        scope  <- Scope.make
        holder <- sem.withPermit(gate.await).fork
        _      <- sem.available.repeatUntil(_ == 0L)
        waiter <- scope.extend[Any](sem.withPermitScoped).fork
        _      <- sem.awaiting.repeatUntil(_ == 1L)
        _      <- gate.succeed(()) <&> scope.close(Exit.unit)
        _      <- holder.join
        _      <- waiter.interrupt
        after  <- sem.available.repeatUntil(_ == 1L)
      } yield assertTrue(after == 1L)
    } @@ timeout(10.seconds),
    test("closing a scoped waiter's scope while it waits interrupts it") {
      for {
        sem      <- make(1L)
        gate     <- Promise.make[Nothing, Unit]
        scope    <- Scope.make
        holder   <- sem.withPermit(gate.await).fork
        _        <- sem.available.repeatUntil(_ == 0L)
        waiter   <- scope.extend[Any](sem.withPermitScoped).fork
        _        <- sem.awaiting.repeatUntil(_ == 1L)
        _        <- scope.close(Exit.unit)
        exit     <- waiter.await
        awaiting <- sem.awaiting
        _        <- gate.succeed(())
        _        <- holder.join
        after    <- sem.available
      } yield assertTrue(exit.isInterrupted, awaiting == 0L, after == 1L)
    } @@ timeout(10.seconds),
    test("closing a scoped waiter's scope does not disturb the other waiters") {
      for {
        sem      <- make(1L)
        gate     <- Promise.make[Nothing, Unit]
        scope    <- Scope.make
        holder   <- sem.withPermit(gate.await).fork
        _        <- sem.available.repeatUntil(_ == 0L)
        closed   <- scope.extend[Any](sem.withPermitScoped).fork
        _        <- sem.awaiting.repeatUntil(_ == 1L)
        live     <- sem.withPermit(ZIO.unit).fork
        _        <- sem.awaiting.repeatUntil(_ == 2L)
        _        <- scope.close(Exit.unit)
        exit     <- closed.await
        awaiting <- sem.awaiting
        _        <- gate.succeed(())
        _        <- holder.join *> live.join
        after    <- sem.available
      } yield assertTrue(exit.isInterrupted, awaiting == 1L, after == 1L)
    } @@ timeout(10.seconds),
    stressTest(make, 1L, 8),
    stressTest(make, 3L, 8),
    stressTest(make, 1L, 32),
    test("tryWithPermit succeeds whenever a permit is free and nobody is queued") {
      // One permit is always spare: two permits, and only `contender` ever
      // holds one once the holders have finished.
      def round(sem: Semaphore): UIO[Boolean] =
        for {
          gate      <- Promise.make[Nothing, Unit]
          holders   <- ZIO.foreach(1 to 2)(_ => sem.withPermit(gate.await).fork)
          _         <- sem.available.repeatUntil(_ == 0L)
          contender <- sem.withPermit(ZIO.unit).fork
          _         <- gate.succeed(())
          _         <- ZIO.foreachDiscard(holders)(_.join)
          tried     <- sem.tryWithPermit(ZIO.unit)
          _         <- contender.join
        } yield tried.isDefined
      for {
        sem     <- make(2L)
        results <- ZIO.foreach(1 to 200)(_ => round(sem))
      } yield assertTrue(!results.contains(false))
    } @@ timeout(60.seconds)
  )

  /** Behaviour that only the unfair semaphore has. */
  private def unfairOnly: List[Spec[Any, Any]] = List(
    test("interrupting a woken waiter before it retries passes the wakeup on") {
      // The interrupt can reach `first` just before the release wakes it, so
      // that `first` is the retrier by the time it cleans up. Its wakeup must
      // then go to `second`, or `second` waits forever with the permit free.
      // Nothing forces that interleaving, so the race runs many times here
      // rather than relying on the suite's `nonFlaky` for repetition.
      def round: UIO[Long] =
        for {
          sem    <- Semaphore.makeUnfair(1L)
          gate   <- Promise.make[Nothing, Unit]
          holder <- sem.withPermit(gate.await).fork
          _      <- sem.available.repeatUntil(_ == 0L)
          first  <- sem.withPermit(ZIO.unit).fork
          _      <- sem.awaiting.repeatUntil(_ == 1L)
          second <- sem.withPermit(ZIO.unit).fork
          _      <- sem.awaiting.repeatUntil(_ == 2L)
          _      <- gate.succeed(()) <&> first.interrupt
          _      <- holder.join *> second.join
          after  <- sem.available
        } yield after
      ZIO.foreach(1 to 20)(_ => round).map(afters => assertTrue(afters.forall(_ == 1L)))
    } @@ timeout(30.seconds),
    test("available counts the free permits while a fiber is queued") {
      for {
        sem    <- Semaphore.makeUnfair(2L)
        gate   <- Promise.make[Nothing, Unit]
        holder <- sem.withPermit(gate.await).fork
        _      <- sem.available.repeatUntil(_ == 1L)
        waiter <- sem.withPermits(2L)(ZIO.unit).fork
        _      <- sem.awaiting.repeatUntil(_ == 1L)
        during <- sem.available
        _      <- gate.succeed(())
        _      <- holder.join *> waiter.join
        after  <- sem.available
      } yield assertTrue(during == 1L, after == 2L)
    } @@ timeout(10.seconds),
    test("a queued small request is not stuck behind a large one when permits are free") {
      for {
        sem   <- Semaphore.makeUnfair(3L)
        gateA <- Promise.make[Nothing, Unit]
        gateB <- Promise.make[Nothing, Unit]
        ran   <- Promise.make[Nothing, Unit]
        two   <- sem.withPermits(2L)(gateA.await).fork
        one   <- sem.withPermit(gateB.await).fork
        _     <- sem.available.repeatUntil(_ == 0L)
        big   <- sem.withPermits(3L)(ZIO.unit).fork
        _     <- sem.awaiting.repeatUntil(_ == 1L)
        small <- sem.withPermit(ran.succeed(())).fork
        _     <- sem.awaiting.repeatUntil(_ == 2L)
        _     <- gateB.succeed(())
        _     <- ran.await
        _     <- gateA.succeed(())
        _     <- two.join *> one.join *> big.join *> small.join
        after <- sem.available
      } yield assertTrue(after == 3L)
    } @@ timeout(10.seconds),
    test("a small request that queues after a release satisfied nobody is still woken") {
      for {
        sem   <- Semaphore.makeUnfair(3L)
        gateA <- Promise.make[Nothing, Unit]
        gateB <- Promise.make[Nothing, Unit]
        gateC <- Promise.make[Nothing, Unit]
        ran   <- Promise.make[Nothing, Unit]
        two   <- sem.withPermits(2L)(gateA.await).fork
        one   <- sem.withPermit(gateB.await).fork
        _     <- sem.available.repeatUntil(_ == 0L)
        big   <- sem.withPermits(3L)(ZIO.unit).fork
        _     <- sem.awaiting.repeatUntil(_ == 1L)
        // One permit comes back and satisfies nobody: only `big` is queued.
        _ <- gateB.succeed(()) *> one.join
        // It is taken again, so the next small request has to queue.
        again <- sem.withPermit(gateC.await).fork
        _     <- sem.available.repeatUntil(_ == 0L)
        small <- sem.withPermit(ran.succeed(())).fork
        _     <- sem.awaiting.repeatUntil(_ == 2L)
        _     <- gateC.succeed(())
        _     <- ran.await
        _     <- gateA.succeed(())
        _     <- two.join *> again.join *> big.join *> small.join
        after <- sem.available
      } yield assertTrue(after == 3L)
    } @@ timeout(10.seconds)
  )

  /** Ordering and `available` guarantees that only the fair semaphore makes. */
  private def fairOnly: List[Spec[Any, Any]] = List(
    test("available is 0 while a fiber is queued, even with permits free") {
      for {
        sem    <- Semaphore.make(2L)
        gate   <- Promise.make[Nothing, Unit]
        holder <- sem.withPermit(gate.await).fork
        _      <- sem.available.repeatUntil(_ == 1L)
        waiter <- sem.withPermits(2L)(ZIO.unit).fork
        _      <- sem.awaiting.repeatUntil(_ == 1L)
        during <- sem.available
        _      <- gate.succeed(())
        _      <- holder.join *> waiter.join
        after  <- sem.available
      } yield assertTrue(during == 0L, after == 2L)
    } @@ timeout(10.seconds),
    test("waiting fibers are granted permits in FIFO order") {
      for {
        sem    <- Semaphore.make(1L)
        gate   <- Promise.make[Nothing, Unit]
        order  <- Ref.make(List.empty[Int])
        holder <- sem.withPermit(gate.await).fork
        _      <- sem.available.repeatUntil(_ == 0L)
        fibers <- ZIO.foreach(1 to 5) { i =>
                    sem.withPermit(order.update(i :: _)).fork <* sem.awaiting.repeatUntil(_ == i.toLong)
                  }
        _      <- gate.succeed(())
        _      <- holder.join
        _      <- ZIO.foreachDiscard(fibers)(_.join)
        result <- order.get
      } yield assertTrue(result.reverse == List(1, 2, 3, 4, 5))
    } @@ timeout(10.seconds),
    test("interrupting every other waiter keeps the rest in FIFO order") {
      for {
        sem    <- Semaphore.make(1L)
        gate   <- Promise.make[Nothing, Unit]
        order  <- Ref.make(List.empty[Int])
        holder <- sem.withPermit(gate.await).fork
        _      <- sem.available.repeatUntil(_ == 0L)
        fibers <- ZIO.foreach(1 to 100) { i =>
                    sem.withPermit(order.update(i :: _)).fork <* sem.awaiting.repeatUntil(_ == i.toLong)
                  }
        _        <- ZIO.foreachDiscard(fibers.indices.filter(_ % 2 == 1))(i => fibers(i).interrupt)
        awaiting <- sem.awaiting
        _        <- gate.succeed(())
        _        <- holder.join
        _        <- ZIO.foreachDiscard(fibers.indices.filter(_ % 2 == 0))(i => fibers(i).join)
        result   <- order.get
        after    <- sem.available
        left     <- sem.awaiting
      } yield assertTrue(awaiting == 50L, result.reverse == (1 to 100 by 2).toList, after == 1L, left == 0L)
    } @@ timeout(10.seconds),
    test("tryWithPermits does not jump ahead of waiting fibers") {
      for {
        sem    <- Semaphore.make(3L)
        gate   <- Promise.make[Nothing, Unit]
        holder <- sem.withPermits(2L)(gate.await).fork
        _      <- sem.available.repeatUntil(_ == 1L)
        waiter <- sem.withPermits(2L)(ZIO.unit).fork
        _      <- sem.awaiting.repeatUntil(_ == 1L)
        tried  <- sem.tryWithPermit(ZIO.unit)
        _      <- gate.succeed(())
        _      <- holder.join
        _      <- waiter.join
        after  <- sem.available
      } yield assertTrue(tried.isEmpty, after == 3L)
    } @@ timeout(10.seconds)
  )

  /**
   * Runs against the compact layout at one permit and the padded one above
   * that. More fibers keep the queue longer, so that waiters are removed from
   * its middle and an unfair waiter that lost its retry is cancelled.
   */
  private def stressTest(make: Long => UIO[Semaphore], total: Long, fibers: Int): Spec[Any, Any] =
    test(
      s"random operations and interruptions conserve permits ($total permit${if (total == 1L) "" else "s"}, $fibers fibers)"
    ) {
      val active    = new java.util.concurrent.atomic.AtomicLong(0L)
      val violation = new java.util.concurrent.atomic.AtomicInteger(0)
      def body(k: Long, yields: Int): UIO[Unit] =
        ZIO.acquireReleaseWith(ZIO.succeed(active.addAndGet(k)))(_ => ZIO.succeed(active.addAndGet(-k))) { held =>
          ZIO.succeed(if (held > total) { violation.incrementAndGet(); () }) *> ZIO.yieldNow.repeatN(yields)
        }
      // Interrupts `zio` after a few yields, wherever it happens to be by then.
      def cut[A](zio: UIO[A], after: Int): UIO[Any] =
        zio.fork.flatMap(fiber => ZIO.yieldNow.repeatN(after) *> fiber.interrupt)
      def step(sem: Semaphore, random: scala.util.Random): UIO[Any] = {
        val k      = 1L + random.nextInt(total.toInt)
        val yields = random.nextInt(3)
        val after  = random.nextInt(4)
        random.nextInt(8) match {
          case 0 => sem.withPermits(k)(body(k, yields))
          case 1 => cut(sem.withPermits(k)(body(k, yields)), after)
          case 2 => sem.tryWithPermits(k)(body(k, yields))
          case 3 => ZIO.scoped(sem.withPermitsScoped(k) *> body(k, yields))
          case 4 => cut(ZIO.scoped(sem.withPermitsScoped(k) *> body(k, yields)), after)
          case 5 => cut(sem.tryWithPermits(k)(body(k, yields)), after)
          case 6 =>
            // Another fiber closes the scope wherever the acquisition is by
            // then. The acquisition must end, by acquiring or by being
            // interrupted, rather than hang. No body runs: once the scope is
            // closed the permits are gone while it would still be running.
            Scope.make.flatMap { scope =>
              scope.extend[Any](sem.withPermitsScoped(k)).fork.flatMap { fiber =>
                ZIO.yieldNow.repeatN(after) *> scope.close(Exit.unit) *> fiber.await
              }
            }
          case _ =>
            // A scope that outlives the wait: the waiter is abandoned first and
            // its scope closed later.
            Scope.make.flatMap { scope =>
              cut(scope.extend[Any](sem.withPermitsScoped(k)), after) *>
                ZIO.yieldNow.repeatN(yields) *> scope.close(Exit.unit)
            }
        }
      }
      for {
        sem <- make(total)
        _ <- ZIO.foreachParDiscard(1 to fibers) { i =>
               val random = new scala.util.Random(i.toLong)
               ZIO.suspendSucceed(step(sem, random)).repeatN(149)
             }
        after    <- sem.available
        awaiting <- sem.awaiting
      } yield assertTrue(violation.get == 0, active.get == 0L, after == total, awaiting == 0L)
    } @@ timeout(60.seconds)
}
