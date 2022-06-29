package zio

import zio.test._

object UnsafeSpecVersionSpecific extends ZIOSpecDefault {

  def spec = suite("UnsafeSpecVersionSpecific") {
    suite("unsafely")(
      test("provides capability to method with implicit parameter") {
        Unsafe.unsafely(doSomethingUnsafe())
        assertCompletes
      },
      test("provides capability to implicit function") {
        def succeed[A](block: Unsafe ?=> A): ZIO[Any, Nothing, A] =
          ZIO.succeed(Unsafe.unsafely(block))
        assertCompletes
      }
    )
  }

  def doSomethingUnsafe()(implicit unsafe: Unsafe): Unit =
    ()
}
