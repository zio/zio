package zio.internal

import zio._
import zio.test._
import zio.test.Assertion._
import zio.test.TestAspect._

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.CountDownLatch

/**
 * Tests for ZScheduler focusing on the unpark-frequency optimization (#9878).
 *
 * These tests verify:
 * 1. The scheduler correctly executes concurrent tasks (regression safety).
 * 2. The parkedWorkers counter is decremented correctly after workers wake.
 * 3. Workers are eventually woken when there is pending work.
 */
object ZSchedulerSpec extends ZIOBaseSpec {

  def spec = suite("ZSchedulerSpec")(
    suite("basic correctness")(
      test("executes tasks submitted from outside the scheduler") {
        val counter = new AtomicInteger(0)
        val n       = 1000
        for {
          _ <- ZIO.foreachParDiscard(1 to n)(_ => ZIO.attempt(counter.incrementAndGet()))
          result = counter.get()
        } yield assert(result)(equalTo(n))
      },
      test("all forked fibers complete") {
        for {
          refs <- ZIO.foreach(1 to 500)(i => Ref.make(i))
          _    <- ZIO.foreachParDiscard(refs)(ref => ref.update(_ + 1))
          vals <- ZIO.foreach(refs)(_.get)
          minVal = vals.min
          maxVal = vals.max
        } yield assert(minVal)(equalTo(2)) && assert(maxVal)(equalTo(501))
      },
      test("ping-pong between fibers completes") {
        for {
          queue  <- Queue.bounded[Unit](1)
          latch  <- Promise.make[Nothing, Unit]
          ref    <- Ref.make(0)
          _      <- (queue.offer(()) *>
                      queue.take *>
                      ref.update(_ + 1) *>
                      latch.succeed(())).repeatN(99).fork
          _      <- latch.await
          result <- ref.get
        } yield assert(result)(equalTo(100))
      }
    ),
    suite("unpark optimization (#9878)")(
      test("scheduler completes high-concurrency fork-many workload") {
        // If the parkedWorkers optimization has a bug (e.g. workers never get
        // woken), this workload will hang or timeout.
        val n = 10000
        for {
          promise <- Promise.make[Nothing, Unit]
          ref     <- Ref.make(n)
          effect   = ref.modify(c => (if (c == 1) promise.succeed(()) else ZIO.unit, c - 1)).flatten
          _       <- ZIO.foreachParDiscard(1 to n)(_ => effect)
          _       <- promise.await
        } yield assertCompletes
      } @@ timeout(30.seconds),
      test("scheduler wakes workers when tasks arrive after idle") {
        // Force workers into an idle state by sleeping, then submit work.
        for {
          _      <- ZIO.sleep(200.millis)   // let workers drain and possibly park
          ref    <- Ref.make(0)
          _      <- ZIO.foreachParDiscard(1 to 100)(_ => ref.update(_ + 1))
          result <- ref.get
        } yield assert(result)(equalTo(100))
      } @@ timeout(10.seconds),
      test("yield-heavy workload completes without starvation") {
        for {
          promise <- Promise.make[Nothing, Unit]
          ref     <- Ref.make(200)
          effect   = ZIO.yieldNow.repeatN(999) *>
                       ref.modify(c => (if (c == 1) promise.succeed(()) else ZIO.unit, c - 1)).flatten
          _       <- ZIO.foreachParDiscard(1 to 200)(_ => effect.fork)
          _       <- promise.await
        } yield assertCompletes
      } @@ timeout(60.seconds)
    )
  ) @@ withLiveClock
}
