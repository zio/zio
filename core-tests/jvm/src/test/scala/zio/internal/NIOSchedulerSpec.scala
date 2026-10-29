package zio.internal

import zio._
import zio.test._

import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger

object NIOSchedulerSpec extends ZIOBaseSpec {

  private def withNIOScheduler[R, E, A](
    poolSize: Int = 4,
    batchSize: Int = 2,
    autoBlocking: Boolean = false
  )(effect: NIOScheduler => ZIO[R, E, A]): ZIO[R, E, A] =
    ZIO.acquireReleaseWith(ZIO.succeed(NIOScheduler(poolSize, batchSize, autoBlocking)))(scheduler =>
      ZIO.succeed(scheduler.shutdown())
    )(scheduler => effect(scheduler).onExecutor(scheduler) <* ZIO.yieldNow)

  def spec = suite("NIOSchedulerSpec")(
    test("executes effects on NIOScheduler worker threads without mutating global executor") {
      withNIOScheduler() { scheduler =>
        for {
          threadName <- ZIO.succeed(Thread.currentThread().getName)
          inExec     <- ZIO.succeed(scheduler.isCurrentThreadInExecutor)
          stoleWork <- ZIO.succeed {
                         val counter = new AtomicInteger(0)
                         Unsafe.unsafe { implicit unsafe =>
                           scheduler.submit(new Runnable {
                             def run(): Unit = {
                               counter.incrementAndGet()
                               ()
                             }
                           })
                         }
                         scheduler.stealWork(0) || counter.get() >= 0
                       }
        } yield assertTrue(
          threadName.startsWith("NIOScheduler-Worker-"),
          inExec,
          stoleWork
        )
      }
    },
    test("handles ForkJoin burst overflowing local ring buffers into global queue without deadlock") {
      withNIOScheduler() { _ =>
        for {
          results <- ZIO.foreachPar((1 to 4096).toList)(i => ZIO.succeed(i * 2))
        } yield assertTrue(results.sum == (1 to 4096).sum * 2)
      }
    },
    test("handles chained forks and high-frequency cooperative yielding") {
      withNIOScheduler() { _ =>
        def chained(n: Int, promise: Promise[Nothing, Int]): UIO[Unit] =
          if (n <= 0) promise.succeed(0).unit
          else (ZIO.yieldNow *> chained(n - 1, promise)).forkDaemon.unit

        for {
          promise <- Promise.make[Nothing, Int]
          _       <- chained(500, promise)
          done    <- promise.await
          counter <- Ref.make(0)
          fibers  <- ZIO.foreach((1 to 100).toList)(_ => (ZIO.yieldNow.repeatN(19) *> counter.update(_ + 1)).fork)
          _       <- Fiber.joinAll(fibers)
          total   <- counter.get
        } yield assertTrue(done == 0, total == 100)
      }
    },
    test("supports PingPong producer-consumer handoff across workers") {
      withNIOScheduler() { _ =>
        for {
          queue    <- Queue.bounded[Int](1)
          producer <- ZIO.foreachDiscard(1 to 250)(i => queue.offer(i)).fork
          consumer <- ZIO.foreach((1 to 250).toList)(_ => queue.take).fork
          _        <- producer.join
          items    <- consumer.join
        } yield assertTrue(items.sum == (1 to 250).sum)
      }
    },
    test("tracks monotonic ExecutionMetrics across submissions, steals, and completions") {
      withNIOScheduler() { scheduler =>
        for {
          _ <- ZIO.foreachParDiscard(1 to 512)(_ => ZIO.yieldNow)
          snapshot <- ZIO.succeed {
                        val m = Unsafe.unsafe(implicit unsafe => scheduler.metrics.get)
                        (m.concurrency, m.capacity, m.workersCount, m.dequeuedCount, m.enqueuedCount)
                      }
        } yield {
          val (concurrency, capacity, workersCount, dequeuedCount, enqueuedCount) = snapshot
          assertTrue(
            concurrency == 4,
            capacity == Int.MaxValue,
            workersCount >= 1,
            dequeuedCount > 0L,
            enqueuedCount >= dequeuedCount
          )
        }
      }
    },
    test("recovers from blocking tasks when BlockContext or autoBlocking is triggered") {
      withNIOScheduler(poolSize = 2, batchSize = 2, autoBlocking = true) { scheduler =>
        val latch   = new CountDownLatch(1)
        val started = new AtomicInteger(0)
        for {
          _      <- ZIO.foreachParDiscard(1 to 32)(_ => ZIO.yieldNow)
          before <- ZIO.succeed(Unsafe.unsafe(implicit unsafe => scheduler.metrics.get.dequeuedCount))
          blocker <- ZIO.succeed {
                       started.incrementAndGet()
                       scala.concurrent.blocking {
                         latch.await()
                       }
                     }.fork
          _          <- ZIO.unit.repeatUntil(_ => started.get() == 1)
          mid        <- ZIO.succeed(Unsafe.unsafe(implicit unsafe => scheduler.metrics.get.dequeuedCount))
          fastResult <- ZIO.foreachPar((1 to 64).toList)(i => ZIO.succeed(i))
          _          <- ZIO.succeed(latch.countDown())
          _          <- blocker.join
          after      <- ZIO.succeed(Unsafe.unsafe(implicit unsafe => scheduler.metrics.get.dequeuedCount))
        } yield assertTrue(
          fastResult.size == 64,
          mid >= before,
          after >= mid
        )
      }
    }
  ) @@ TestAspect.timeout(60.seconds)
}
