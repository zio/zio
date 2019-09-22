package zio.platform

import zio.ZIOBaseSpec
import zio.test.Assertion._
import zio.test._
import zio.test.mock.EnvironmentSpec.Platform

object PlatformSpec
    extends ZIOBaseSpec(
      suite("PlatformSpec")(
        suite("PlatformLive fatal:")(
          test("Platform.fatal should identify a nonFatal exception") {
            val nonFatal = new Exception
            assert(Platform.fatal(nonFatal), isFalse)
          },
          test("Platform.fatal should identify a fatal exception") {
            val fatal = new OutOfMemoryError
            assert(Platform.fatal(fatal), isTrue)
          }
        )
      )
    )
