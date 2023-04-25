package zio

import zio.test._

object ZEnvironmentSpec extends ZIOBaseSpec {

  def spec = suite("ZEnvironmentSpec")(
    test("getting from an empty environment should succeed") {
      for {
        _ <- ZIO.succeed(ZEnvironment.empty.get)
      } yield assertCompletes
    }
  ) @@ TestAspect.exceptNative
}
