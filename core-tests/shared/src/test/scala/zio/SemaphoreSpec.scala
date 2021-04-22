// Copyright (C) 2017-2021 Łukasz Biały, Paul Chiusano, Michael Pilquist,
// Oleg Pyzhcov, Fabio Labella, Alexandru Nedelcu, Pavel Chlupacek. All rights reserved.

package zio

import zio.test.Assertion._
import zio.test._

object SemaphoreSpec extends ZIOBaseSpec {

  import ZIOTag._

  def spec: Spec[Any, TestFailure[Any], TestSuccess] = suite("SemaphoreSpec")(
    suite("Make a Semaphore and verify that")(
      testM("`acquire` permits sequentially") {
        val n = 20L
        for {
          semaphore <- Semaphore.make(n)
          available <- IO.foreach((0L until n).toList)(_ => semaphore.withPermit(semaphore.available))
        } yield assert(available)(forall(equalTo(19L)))
      },
      testM("`acquire` permits in parallel") {
        val n = 20L
        for {
          semaphore <- Semaphore.make(n)
          available <- IO.foreachPar((0L until n).toList)(_ => semaphore.withPermit(semaphore.available))
        } yield assert(available)(forall(isLessThan(20L)))
      },
      testM("`acquireN`s can be parallel with `releaseN`s") {
        offsettingWithPermits((s, permits) => IO.foreach(permits)(s.withPermits(_)(IO.unit)).unit)
      },
      testM("individual `acquireN`s can be parallel with individual `releaseN`s") {
        offsettingWithPermits((s, permits) => IO.foreachPar(permits)(s.withPermits(_)(IO.unit)).unit)
      },
      testM("semaphores and fibers play ball together") {
        val n = 1L
        for {
          s <- Semaphore.make(n)
          _ <- s.withPermit(IO.unit).fork
          _ <- s.withPermit(IO.unit)
        } yield assertCompletes
      },
      testM("`withPermit` doesn't leak permits upon failure") {
        val n = 1L
        for {
          s       <- Semaphore.make(n)
          _       <- s.withPermit(IO.fail("fail")).either
          permits <- s.available
        } yield assert(permits)(equalTo(1L))
      } @@ zioTag(errors),
      testM("`withPermit` does not leak fibers or permits upon cancellation") {
        val n = 1L
        for {
          s       <- Semaphore.make(n)
          fiber   <- s.withPermit(IO.never).fork
          _       <- fiber.interrupt
          permits <- s.available
        } yield assert(permits)(equalTo(1L))
      } @@ zioTag(interruption),
      testM("`withPermitManaged` does not leak fibers or permits upon cancellation") {
        for {
          s       <- Semaphore.make(1L)
          fiber   <- s.withPermitManaged.use(_ => IO.never).fork
          _       <- fiber.interrupt
          permits <- s.available
        } yield assert(permits)(equalTo(1L))
      }
    )
  )

  def offsettingWithPermits(withPermits: (Semaphore, Vector[Long]) => UIO[Unit]): ZIO[Any, Nothing, TestResult] = {
    val permits = Vector(1L, 0L, 20L, 4L, 0L, 5L, 2L, 1L, 1L, 3L)

    for {
      semaphore <- Semaphore.make(20L)
      fiber     <- withPermits(semaphore, permits).fork
      _         <- fiber.join
      count     <- semaphore.available
    } yield assert(count)(equalTo(20L))
  }
}
