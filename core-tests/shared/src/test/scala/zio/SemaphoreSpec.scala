package zio

import zio.test.Assertion._
import zio.test.TestAspect._
import zio.test._

object SemaphoreSpec extends ZIOBaseSpec {
  override def spec = suite("SemaphoreSpec")(
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
    } @@ jvm(nonFlaky),
    test("withPermit acquire is interruptible") {
      for {
        semaphore <- Semaphore.make(0L)
        effect     = semaphore.withPermit(ZIO.unit)
        fiber     <- effect.fork
        _         <- fiber.interrupt
      } yield assertCompletes
    } @@ jvm(nonFlaky),
    test("withPermitsScoped releases same number of permits") {
      for {
        semaphore <- Semaphore.make(2L)
        _         <- ZIO.scoped(semaphore.withPermitsScoped(2))
        permits   <- semaphore.available
      } yield assertTrue(permits == 2L)
    },
    test("awaiting returns the count of waiting fibers") {
      for {
        semaphore <- Semaphore.make(1)
        promise   <- Promise.make[Nothing, Unit]
        _         <- semaphore.withPermit(promise.await).fork
        _         <- ZIO.foreach(List.fill(10)(()))(_ => semaphore.withPermit(ZIO.unit).fork)
        awaiting0 <- semaphore.awaiting
                       .flatMap(awaiting => if (awaiting < 10) ZIO.fail(awaiting) else ZIO.succeed(awaiting))
                       .retryUntil(_ == 10)
                       .catchAll(n => ZIO.succeed(n))
        _ <- promise.succeed(())
        //
        awaiting1 <- semaphore.awaiting
                       // We fail to allow for retrying, to give fibers time to acquire a permit and release it
                       .flatMap(awaiting => if (awaiting > 0) ZIO.fail(awaiting) else ZIO.succeed(awaiting))
                       // Fibers may take time to acquire a permit
                       .retryUntil(_ == 0)
                       .catchAll(n => ZIO.succeed(n))
      } yield assertTrue(awaiting0 == 10) && assertTrue(awaiting1 == 0)
    }
  )
}
