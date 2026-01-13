package zio

import zio.Semaphore.{Job, SemaphoreState}
import zio.test.Assertion._
import zio.test.TestAspect._
import zio.test._

object SemaphoreSpec extends ZIOBaseSpec {

  private def makeJob(permits: Long = 1L): UIO[Job] =
    Promise.make[Nothing, Unit].map(p => Job(p, permits))

  override def spec = suite("SemaphoreSpec")(
    suite("JobQueue")(
      test("enqueue maintains FIFO order") {
        for {
          job1 <- makeJob()
          job2 <- makeJob()
          job3 <- makeJob()
          queue = SemaphoreState.JobQueue(job1).enqueue(job2).enqueue(job3)
          (dequeued1, q1) = queue.dequeueOrNull
          (dequeued2, q2) = q1.dequeueOrNull
          (dequeued3, _)  = q2.dequeueOrNull
        } yield assertTrue(
          dequeued1.promise == job1.promise,
          dequeued2.promise == job2.promise,
          dequeued3.promise == job3.promise
        )
      },
      test("prepend adds to front of queue") {
        for {
          job1 <- makeJob()
          job2 <- makeJob()
          job3 <- makeJob()
          queue = SemaphoreState.JobQueue(job2).enqueue(job3).prepend(job1)
          (dequeued1, q1) = queue.dequeueOrNull
          (dequeued2, q2) = q1.dequeueOrNull
          (dequeued3, _)  = q2.dequeueOrNull
        } yield assertTrue(
          dequeued1.promise == job1.promise,
          dequeued2.promise == job2.promise,
          dequeued3.promise == job3.promise
        )
      },
      test("remove returns job and updated queue") {
        for {
          job1              <- makeJob()
          job2              <- makeJob()
          job3              <- makeJob()
          queue              = SemaphoreState.JobQueue(job1).enqueue(job2).enqueue(job3)
          (removed, newQueue) = queue.remove(job2.promise)
        } yield assertTrue(
          removed.promise == job2.promise,
          newQueue.size == 2
        )
      },
      test("remove returns null for non-existent promise") {
        for {
          job1         <- makeJob()
          job2         <- makeJob()
          nonExistent  <- makeJob()
          queue         = SemaphoreState.JobQueue(job1).enqueue(job2)
          (removed, _)  = queue.remove(nonExistent.promise)
        } yield assertTrue(removed == null)
      },
      test("dequeueOrNull skips tombstones (removed jobs)") {
        for {
          job1 <- makeJob()
          job2 <- makeJob()
          job3 <- makeJob()
          queue = SemaphoreState.JobQueue(job1).enqueue(job2).enqueue(job3)
          // Remove job2, creating a tombstone in the order vector
          (_, queueWithTombstone) = queue.remove(job2.promise)
          // Dequeue should return job1 first
          (dequeued1, q1) = queueWithTombstone.dequeueOrNull
          // Dequeue should skip tombstone and return job3
          (dequeued2, _) = q1.dequeueOrNull
        } yield assertTrue(
          dequeued1.promise == job1.promise,
          dequeued2.promise == job3.promise
        )
      },
      test("dequeueOrNull returns null for empty queue") {
        for {
          job1 <- makeJob()
          queue = SemaphoreState.JobQueue(job1)
          (_, emptyQueue) = queue.dequeueOrNull
        } yield assertTrue(emptyQueue.dequeueOrNull == null)
      },
      test("dequeueOrNull returns null when only tombstones remain") {
        for {
          job1 <- makeJob()
          job2 <- makeJob()
          queue = SemaphoreState.JobQueue(job1).enqueue(job2)
          // Remove both jobs, leaving only tombstones
          (_, q1) = queue.remove(job1.promise)
          (_, q2) = q1.remove(job2.promise)
        } yield assertTrue(q2.dequeueOrNull == null)
      },
      test("size returns count of active jobs excluding tombstones") {
        for {
          job1 <- makeJob()
          job2 <- makeJob()
          job3 <- makeJob()
          queue = SemaphoreState.JobQueue(job1).enqueue(job2).enqueue(job3)
          // Remove job2, creating a tombstone
          (_, queueWithTombstone) = queue.remove(job2.promise)
        } yield assertTrue(
          queue.size == 3,
          queueWithTombstone.size == 2
        )
      },
      test("apply(List[Job]) creates queue with correct order") {
        for {
          job1 <- makeJob()
          job2 <- makeJob()
          job3 <- makeJob()
          queue = SemaphoreState.JobQueue(List(job1, job2, job3))
          (dequeued1, q1) = queue.dequeueOrNull
          (dequeued2, q2) = q1.dequeueOrNull
          (dequeued3, _)  = q2.dequeueOrNull
        } yield assertTrue(
          queue.size == 3,
          dequeued1.promise == job1.promise,
          dequeued2.promise == job2.promise,
          dequeued3.promise == job3.promise
        )
      }
    ),
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
    } @@ timeout(10.seconds)
  ) @@ exceptJS(nonFlaky)
}
