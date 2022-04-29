package zio

import zio.test.Assertion._
import zio.test._

object RuntimeConfigSpec extends ZIOBaseSpec {

  def spec = suite("RuntimeConfigSpec")(
    suite("RuntimeConfigLive fatal:")(
      test("RuntimeConfig.fatal should identify a nonFatal exception") {
        val nonFatal = new Exception
        assert(Runtime.default.isFatal(nonFatal))(isFalse)
      },
      test("RuntimeConfig.fatal should identify a fatal exception") {
        val fatal = new OutOfMemoryError
        assert(Runtime.default.isFatal(fatal))(isTrue)
      }
    )
  )
}
