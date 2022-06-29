package zio

import zio.test._

object UnsafeSpec extends ZIOSpecDefault {

  def spec = suite("UnsafeSpec") {
    suite("unsafe")(
      test("provides capability to function") {
        Unsafe.unsafe { implicit unsafe =>
          doSomethingUnsafe()
        }
        assertCompletes
      }
    )
  }

  def doSomethingUnsafe()(implicit unsafe: Unsafe): Unit =
    ()
}
