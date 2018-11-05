// Copyright (C) 2017-2018 Łukasz Biały, Paul Chiusano, Michael Pilquist,
// Oleg Pyzhcov, Fabio Labella, Alexandru Nedelcu, Pavel Chlupacek. All rights reserved.

package scalaz.zio

import scala.concurrent.duration.DurationLong

class SemaphoreSpec extends AbstractRTSSpec {

  def is =
    "SemaphoreSpec".title ^ s2"""
    Make a Semaphore and
    verify that
      `acquire` permits sequentially $e1
      `acquire` permits in parallel $e2
      `acquireN`s can be parallel with `releaseN`s $e3
      individual `acquireN`s can be parallel with individual `releaseN`s $e4
      semaphores and fibers play ball together $e5
      `acquire` doesn't leak permits upon cancellation $e6
    """

  def e1 = {
    val n = 20L
    unsafeRun(for {
      semaphore <- Semaphore(n)
      available <- IO.traverse((0L until n).toList)(_ => semaphore.acquire) *> semaphore.available
    } yield available must_=== 0)
  }

  def e2 = {
    val n = 20L
    unsafeRun(for {
      semaphore <- Semaphore(n)
      available <- IO.parTraverse((0L until n).toList)(_ => semaphore.acquire) *> semaphore.available
    } yield available must_=== 0)
  }

  def e3 =
    offsettingReleasesAcquires(
      (s, permits) => IO.traverse(permits)(s.acquireN).void,
      (s, permits) => IO.traverse(permits.reverse)(s.releaseN).void
    )

  def e4 =
    offsettingReleasesAcquires(
      (s, permits) => IO.parTraverse(permits)(amount => s.acquireN(amount)).void,
      (s, permits) => IO.parTraverse(permits.reverse)(amount => s.releaseN(amount)).void
    )

  def e5 = {
    val n = 1L
    unsafeRun(for {
      s <- Semaphore(n).peek(_.acquire)
      _ <- s.release.fork
      _ <- s.acquire
    } yield () must_=== (()))
  }

  def e6 = {
    val n = 1L
    unsafeRun(for {
      s <- Semaphore(n)
      _ <- s.acquireN(2).timeoutFail(new Exception("timeout"))(1.milli).attempt
      permits <- IO.sleep(10.millis) *> s.available
    } yield permits) must_=== 0
  }


  def offsettingReleasesAcquires(
    acquires: (Semaphore, Vector[Long]) => IO[Nothing, Unit],
    releases: (Semaphore, Vector[Long]) => IO[Nothing, Unit]
  ) = {
    val permits = Vector(1L, 0L, 20L, 4L, 0L, 5L, 2L, 1L, 1L, 3L)

    unsafeRun(for {
      semaphore     <- Semaphore(0L)
      acquiresFiber <- acquires(semaphore, permits).fork
      releasesFiber <- releases(semaphore, permits).fork
      _             <- acquiresFiber.join
      _             <- releasesFiber.join
      count         <- semaphore.count
    } yield count must_=== 0)
  }

}
