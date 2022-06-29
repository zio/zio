package zio

import zio.test._

object UnsafeSpec extends ZIOSpecDefault {

  def spec = suite("UnsafeSpec") {
    suite("unsafely")(
      test("provides capability to function") {
        Unsafe.unsafely { implicit unsafe =>
          doSomethingUnsafe()
        }
        assertCompletes
      }
    )
  }

  def doSomethingUnsafe()(implicit unsafe: Unsafe): Unit =
    ()
}
