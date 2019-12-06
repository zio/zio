package zio.platform

import zio.internal.PlatformLive
import zio.test._
import zio.test.Assertion._
import zio.ZIOBaseSpec

object PlatformSpec extends ZIOBaseSpec {

  def spec = suite("PlatformSpec")(
    suite("PlatformLive fatal:")(
      test("Platform.fatal should identify a nonFatal exception") {
        val nonFatal = new Exception
        assert(PlatformLive.Default.fatal(nonFatal), isFalse)
      },
      test("Platform.fatal should identify a fatal exception") {
        val fatal = new OutOfMemoryError
        assert(PlatformLive.Default.fatal(fatal), isTrue)
      }
    )
  )
}
