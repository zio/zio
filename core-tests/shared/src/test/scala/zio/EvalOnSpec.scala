package zio

import zio.test._
import zio.test.TestAspect._

object EvalOnSpec extends ZIOBaseSpec {
  def spec = suite("EvalOnSpec") {
    test("evalOn - promise") {
      for {
        promise <- Promise.make[Nothing, Int]
        fiber   <- ZIO.succeed(12).forever.fork
        _       <- fiber.evalOn(promise.succeed(42), ZIO.unit)
        v       <- promise.await <* fiber.interrupt
      } yield assertTrue(v == 42)
    } @@ nonFlaky +
      test("evalOn - stacked") {
        for {
          ref     <- Ref.make(0)
          started <- Promise.make[Nothing, Unit]
          promise <- Promise.make[Nothing, Unit]
          fiber   <- (started.succeed(()) *> promise.await).fork
          _       <- started.await
          _       <- fiber.evalOn(ref.update(_ + 1), ZIO.unit).repeatN(4)
          _       <- promise.succeed(())
          _       <- fiber.await
          v       <- ref.get
        } yield assertTrue(v == 5)
      } @@ nonFlaky +
      test("evalOn - fiberId") {
        for {
          promise <- Promise.make[Nothing, FiberId]
          fiber   <- ZIO.succeed(12).forever.fork
          _       <- fiber.evalOn(ZIO.fiberId.intoPromise(promise), ZIO.never)
          v       <- promise.await <* fiber.interrupt
        } yield assertTrue((fiber.id: FiberId) == v)
      } @@ nonFlaky +
      test("evalOnZIO orElse") {
        for {
          ref   <- Ref.make(0)
          fiber <- ZIO.succeed(12).fork
          _     <- fiber.await
          _     <- fiber.evalOnZIO(ZIO.unit, ref.set(42))
          v     <- ref.get
        } yield assertTrue(v == 42)
      } @@ nonFlaky
  }
}
