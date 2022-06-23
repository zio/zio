package zio

import zio.test._

object RuntimeSpecJVM extends ZIOBaseSpec {
  def isFatal(t: Throwable): Boolean = FiberRef.currentFatal.initial.apply(t)

  def spec = suite("RuntimeSpecJVM")(
    suite("Runtime.default isFatal:")(
      test("Runtime.isFatal should identify a nonFatal exception") {
        val nonFatal = new Exception
        assertTrue(!isFatal(nonFatal))
      },
      test("Runtime.isFatal should identify a fatal exception") {
        val fatal = new OutOfMemoryError
        assertTrue(isFatal(fatal))
      }
    )
  )
}
