package zio

import zio.test._

object ProvideSpec extends ZIOBaseSpec {

  def spec = suite("ProvideSpec")(
    suite("provideSomeAuto")(
      test("Should infer the environment type") {
        class A(scope: Scope, string: String, int: Int)
        object A {
          val layer = ZLayer.derive[A]
        }

        class B(a: A)
        object B {
          val layer = ZLayer.derive[B]
        }

        val program = (for {
          _ <- ZIO.service[B]
        } yield ())
          .provideSomeAuto(
            A.layer,
            B.layer
          )

        // Verify correct type
        val p: ZIO[zio.Scope & String & Int, Any, Unit] = program

        assertCompletes
      }
    )
  )

}
