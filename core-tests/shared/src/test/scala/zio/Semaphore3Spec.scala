package zio

import zio.test._
import zio.test.TestAspect._

object Semaphore3Spec extends ZIOSpecDefault {
  def spec = suite("Semaphore3Spec")(
    test("acquire and release a permit") {
      for {
        sem <- Semaphore3.make(1)
        _   <- sem.withPermit(ZIO.unit)
      } yield assertCompletes
    } @@ timeout(10.seconds),
    test("block when no permits available") {
      for {
        sem  <- Semaphore3.make(1)
        _    <- sem.withPermit(ZIO.unit)
        done <- sem.withPermit(ZIO.unit).fork
        _    <- TestClock.adjust(1.second)
        _    <- done.interrupt
      } yield assertCompletes
    } @@ timeout(10.seconds),
    test("serve multiple waiters") {
      for {
        sem <- Semaphore3.make(1)
        // Create 5 fibers that will wait for permits
        fibers <- ZIO.foreach(1 to 5)(_ => sem.withPermit(ZIO.unit).fork)
        // Release 5 permits
        _ <- sem.release(5)
        // Wait for all fibers to complete
        _ <- ZIO.foreach(fibers)(_.join)
      } yield assertCompletes
    } @@ timeout(10.seconds),
    test("release multiple permits") {
      for {
        sem <- Semaphore3.make(0)
        // Create 5 fibers that will wait for permits
        fibers <- ZIO.foreach(1 to 5)(_ => sem.withPermit(ZIO.unit).fork)
        // Give fibers time to start and enqueue
        _ <- TestClock.adjust(1.day)
        // Release 5 permits directly
        _ <- sem.release(5)
        // Give time for fibers to acquire permits
        _ <- TestClock.adjust(1.day)
        // Wait for all fibers to complete
        _ <- ZIO.foreach(fibers)(_.join)
      } yield assertCompletes
    } @@ timeout(10.seconds)
  )
}
