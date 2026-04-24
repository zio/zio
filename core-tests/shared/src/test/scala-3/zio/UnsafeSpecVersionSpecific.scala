package zio

import zio.test._

object UnsafeSpecVersionSpecific extends ZIOBaseSpec {

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
      },
      test("provides capability to implicit function with multiple parameters") {
        val result = Unsafe.unsafely {
          Runtime.default.unsafe.run(ZIO.succeed(42)).getOrThrowFiberFailure()
        }
        assertTrue(result == 42)
      }
    )
  }

  def doSomethingUnsafe()(implicit unsafe: Unsafe): Unit =
    ()
}
