// Copyright (C) 2017-2018 Łukasz Biały, Paul Chiusano, Michael Pilquist,
// Oleg Pyzhcov, Fabio Labella, Alexandru Nedelcu, Pavel Chlupacek. All rights reserved.

package zio

import zio.duration._
import zio.test.Assertion._
import zio.test._
import zio.test.environment.TestClock
import SemaphoreSpecData._

object SemaphoreSpecData {
  def offsettingReleasesAcquires(
    acquires: (Semaphore, Vector[Long]) => UIO[Unit],
    releases: (Semaphore, Vector[Long]) => UIO[Unit]
  ) = {
    val permits = Vector(1L, 0L, 20L, 4L, 0L, 5L, 2L, 1L, 1L, 3L)

    for {
      semaphore     <- Semaphore.make(0L)
      acquiresFiber <- acquires(semaphore, permits).fork
      releasesFiber <- releases(semaphore, permits).fork
      _             <- acquiresFiber.join
      _             <- releasesFiber.join
      count         <- semaphore.available
    } yield assert(count, equalTo(0L))
  }
}

object SemaphoreSpec
    extends ZIOBaseSpec(
      suite("SemaphoreSpec")(
        suite("Make a Semaphore and verify that")(
          testM("`acquire` permits sequentially") {
            val n = 20L
            for {
              semaphore <- Semaphore.make(n)
              available <- IO.foreach((0L until n).toList)(_ => semaphore.acquire) *> semaphore.available
            } yield assert(available, equalTo(0L))
          },
          testM("`acquire` permits in parallel") {
            val n = 20L
            for {
              semaphore <- Semaphore.make(n)
              available <- IO.foreachPar((0L until n).toList)(_ => semaphore.acquire) *> semaphore.available
            } yield assert(available, equalTo(0L))
          },
          testM("`acquireN`s can be parallel with `releaseN`s") {
            offsettingReleasesAcquires(
              (s, permits) => IO.foreach(permits)(s.acquireN).unit,
              (s, permits) => IO.foreach(permits.reverse)(s.releaseN).unit
            )
          },
          testM("individual `acquireN`s can be parallel with individual `releaseN`s") {
            offsettingReleasesAcquires(
              (s, permits) => IO.foreachPar(permits)(amount => s.acquireN(amount)).unit,
              (s, permits) => IO.foreachPar(permits.reverse)(amount => s.releaseN(amount)).unit
            )
          },
          testM("semaphores and fibers play ball together") {
            val n = 1L
            for {
              s <- Semaphore.make(n).tap(_.acquire)
              _ <- s.release.fork
              _ <- s.acquire
            } yield assertCompletes
          },
          /**
           * Ported from @mpilquist work in Cats Effect (https://github.com/typelevel/cats-effect/pull/403)
           */
          testM("`acquire` doesn't leak permits upon cancellation") {
            val n = 1L
            for {
              s           <- Semaphore.make(n)
              acquireFork <- s.acquireN(2).timeout(1.milli).either.fork
              _           <- TestClock.adjust(1.milli) *> acquireFork.join
              permitsFork <- (s.release *> clock.sleep(10.millis) *> s.available).fork
              permits     <- TestClock.adjust(10.millis) *> permitsFork.join
            } yield assert(permits, equalTo(2L))
          },
          /**
           * Ported from @mpilquist work in Cats Effect (https://github.com/typelevel/cats-effect/pull/403)
           */
          testM("`withPermit` does not leak fibers or permits upon cancellation") {
            val n = 0L
            for {
              s           <- Semaphore.make(n)
              acquireFork <- s.withPermit(s.release).timeout(1.milli).either.fork
              _           <- TestClock.adjust(1.milli) *> acquireFork.join
              permitsFork <- (s.release *> clock.sleep(10.millis) *> s.available).fork
              permits     <- TestClock.adjust(10.millis) *> permitsFork.join
            } yield assert(permits, equalTo(1L))
          },
          testM("`withPermitManaged` does not leak fibers or permits upon cancellation") {
            for {
              s           <- Semaphore.make(0)
              acquireFork <- s.withPermitManaged.use(_ => s.release).timeout(1.millisecond).either.fork
              _           <- TestClock.adjust(1.milli) *> acquireFork.join
              permitsFork <- (s.release *> clock.sleep(10.milliseconds) *> s.available).fork
              permits     <- TestClock.adjust(10.millis) *> permitsFork.join
            } yield assert(permits, equalTo(1L))
          }
        )
      )
    )
