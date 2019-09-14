// Copyright (C) 2017-2018 Łukasz Biały, Paul Chiusano, Michael Pilquist,
// Oleg Pyzhcov, Fabio Labella, Alexandru Nedelcu, Pavel Chlupacek. All rights reserved.

package zio

class SemaphoreSpec(implicit ee: org.specs2.concurrent.ExecutionEnv) extends TestRuntime {

  def is =
    "SemaphoreSpec".title ^ s2"""
    Make a Semaphore and
    verify that
      `withPermit` performed sequentially $e1
      `withPermit` performed in parallel $e2
      multiple `withPermits` can be performed in sequence $e3
      multiple `withPermits` can be performed in parallel $e4
      semaphores and fibers play ball together $e5
      `withPermit` doesn't leak permits upon failure $e6
      `withPermit` does not leak permits upon cancellation $e7
      `withPermitManaged` does not leak permits upon cancellation $e8
    """

  def e1 = {
    val n = 20L
    unsafeRun(for {
      semaphore <- Semaphore.make(n)
      available <- IO.foreach((0L until n).toList)(_ => semaphore.withPermit(semaphore.available))
    } yield available.forall(_ == 19))
  }

  def e2 = {
    val n = 20L
    unsafeRun(for {
      semaphore <- Semaphore.make(n)
      available <- IO.foreachPar((0L until n).toList)(_ => semaphore.withPermit(semaphore.available))
    } yield available.forall(_ < 20))
  }

  def e3 =
    offsettingWithPermits(
      (s, permits) => IO.foreach(permits)(s.withPermits(_)(IO.unit)).unit
    )

  def e4 =
    offsettingWithPermits(
      (s, permits) => IO.foreachPar(permits)(s.withPermits(_)(IO.unit)).unit
    )

  def e5 = {
    val n = 1L
    unsafeRun(for {
      s <- Semaphore.make(n)
      _ <- s.withPermit(IO.unit).fork
      _ <- s.withPermit(IO.unit)
    } yield () must_=== (()))
  }

  /**
   * Ported from @mpilquist work in Cats Effect (https://github.com/typelevel/cats-effect/pull/403)
   */
  def e6 = {
    val n = 1L
    unsafeRun(for {
      s       <- Semaphore.make(n)
      _       <- s.withPermit(IO.fail("fail")).either
      permits <- s.available
    } yield permits) must_=== 1
  }

  /**
   * Ported from @mpilquist work in Cats Effect (https://github.com/typelevel/cats-effect/pull/403)
   */
  def e7 = {
    val n = 1L
    unsafeRun(for {
      s       <- Semaphore.make(n)
      fiber   <- s.withPermit(IO.never).fork
      _       <- fiber.interrupt.either
      permits <- s.available
    } yield permits must_=== 1L)
  }

  private def e8 = {
    val n = 1L
    val test = for {
      s       <- Semaphore.make(n)
      fiber   <- s.withPermitManaged.use(_ => IO.never).fork
      _       <- fiber.interrupt.either
      permits <- s.available
    } yield permits must_=== 1L

    unsafeRun(test)
  }

  def offsettingWithPermits(
    withPermits: (Semaphore, Vector[Long]) => UIO[Unit]
  ) = {
    val permits = Vector(1L, 0L, 20L, 4L, 0L, 5L, 2L, 1L, 1L, 3L)

    unsafeRun(for {
      semaphore <- Semaphore.make(20L)
      fiber     <- withPermits(semaphore, permits).fork
      _         <- fiber.join
      count     <- semaphore.available
    } yield count must_=== 20L)
  }

}
