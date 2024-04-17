package zio

import zio.test._
import zio.test.Assertion._

object ProvideSomeSpec extends ZIOBaseSpec {

  def spec = suite("ProvideSomeSpec")(
    test("provideSomeLayer") {
      for {
        ref    <- Ref.make(0)
        scope  <- Scope.make
        scoped  = ZIO.acquireRelease(ref.update(_ + 1))(_ => ref.update(_ - 1))
        layer   = ZLayer.scoped(scoped)
        _      <- scope.extend(layer.build.provideSomeLayer(ZLayer.empty))
        before <- ref.get
        _      <- scope.close(Exit.unit)
        after  <- ref.get
      } yield assertTrue(before == 1) &&
        assertTrue(after == 0)
    }
  )
}
