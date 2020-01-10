package zio.platform

import zio.ZIOBaseSpec
import zio.internal.Platform
import zio.test.Assertion._
import zio.test._
import zio.test._

object PlatformSpec extends ZIOBaseSpec {

  def spec = suite("PlatformSpec")(
    suite("PlatformLive fatal:")(
      test("Platform.fatal should identify a nonFatal exception") {
        val nonFatal = new Exception
        assert(Platform.default.fatal(nonFatal))(isFalse)
      },
      test("Platform.fatal should identify a fatal exception") {
        val fatal = new OutOfMemoryError
        assert(Platform.default.fatal(fatal))(isTrue)
      }
    )
  )
}
