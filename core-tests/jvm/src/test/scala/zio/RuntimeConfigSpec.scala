package zio

import zio.test.Assertion._
import zio.test._

object RuntimeSpecJVM extends ZIOBaseSpec {

  def spec = suite("RuntimeSpecJVM")(
    suite("Runtime.default isFatal:")(
      test("Runtime.isFatal should identify a nonFatal exception") {
        val nonFatal = new Exception
        assert(Runtime.default.isFatal(nonFatal))(isFalse)
      },
      test("Runtime.isFatal should identify a fatal exception") {
        val fatal = new OutOfMemoryError
        assert(Runtime.default.isFatal(fatal))(isTrue)
      }
    )
  )
}
