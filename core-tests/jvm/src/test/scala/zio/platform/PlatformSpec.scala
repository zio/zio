package zio.platform

import zio.ZIOBaseSpec
import zio.internal.Platform
import zio.test._

object PlatformSpec extends ZIOBaseSpec {
  val default = Platform.default

  def spec: ZSpec[Environment, Failure] = suite("PlatformSpec")(
    suite("PlatformLive fatal:")(
      test("Platform.fatal should identify a nonFatal exception") {
        val nonFatal = new Exception
        assertTrue(!default.fatal(nonFatal))
      },
      test("Platform.fatal should identify a fatal exception") {
        val fatal = new OutOfMemoryError
        assertTrue(default.fatal(fatal))
      }
    )
  )
}
