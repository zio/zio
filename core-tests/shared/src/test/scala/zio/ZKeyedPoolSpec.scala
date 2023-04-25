package zio

import zio.test._
import zio.test.TestAspect._

object ZKeyedPoolSpec extends ZIOBaseSpec {
  override def spec: Spec[TestEnvironment with Scope, Any] =
    suite("ZKeyedPoolSpec")(
      test("acquire release many successfully while other key is blocked") {
        for {
          pool <- ZKeyedPool.make((key: String) => ZIO.succeed(key), size = 4)
          _    <- pool.get("key1").repeatN(3).unit
          fiber <-
            ZIO
              .foreachParDiscard(1 to 400) { _ =>
                ZIO.scoped {
                  pool.get("key2") *> Clock.sleep(10.millis)
                }
              }
              .fork
          _ <- TestClock.adjust((10 * 400).millis)
          _ <- fiber.join
        } yield assertCompletes
      },
      test("acquire release many with invalidates") {
        for {
          counter <- Ref.make(0)
          pool    <- ZKeyedPool.make((key: String) => counter.modify(n => (s"$key-$n", n + 1)), size = 4)
          fiber <-
            ZIO
              .foreachParDiscard(1 to 400) { _ =>
                ZIO.scoped {
                  pool.get("key1").flatMap { value =>
                    pool.invalidate(value).whenZIO(Random.nextBoolean) *>
                      Random.nextIntBounded(15).flatMap(n => Clock.sleep(n.millis))
                  }
                }
              }
              .fork
          _ <- TestClock.adjust((15 * 400).millis)
          _ <- fiber.join
        } yield assertCompletes
      }
    ) @@ jvmOnly
}
