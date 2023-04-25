package zio

import zio.test._
import zio.test.TestAspect._

object ZIOAspectSpec extends ZIOBaseSpec {
  import ZIOAspect._

  def spec = suite("ZIOAspectSpec")(
    test("nested nests configuration under the specified name") {
      for {
        config <- ZIO.config(Config.string("key")) @@ nested("nested")
      } yield assertTrue(config == "value")
    } @@ withConfigProvider(ConfigProvider.fromMap(Map("nested.key" -> "value")))
  ) @@ TestAspect.exceptNative
}
