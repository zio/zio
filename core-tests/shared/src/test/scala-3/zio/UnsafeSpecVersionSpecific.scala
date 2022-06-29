package zio

import zio.test._

object UnsafeSpecVersionSpecific extends ZIOSpecDefault {

  def spec = suite("UnsafeSpecVersionSpecific") {
    suite("unsafe")(
      test("provides capability to method with implicit parameter") {
        Unsafe.unsafe(doSomethingUnsafe())
        assertCompletes
      },
      test("provides capability to implicit function") {
        def succeed[A](block: Unsafe ?=> A): ZIO[Any, Nothing, A] =
          ZIO.succeed(Unsafe.unsafe(block))
        assertCompletes
      }
    )
  }

  def doSomethingUnsafe()(implicit unsafe: Unsafe): Unit =
    ()
}
