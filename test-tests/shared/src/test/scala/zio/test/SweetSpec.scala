package zio.test
import zio._

object SweetSpec extends ZIOSpecDefault {

  def spec: Spec[Any, Any] =
    suiteAll("SweetSpec!") {
      val hello = "hello"

      test("test")(
        assertTrue(hello.startsWith("h"))
      )

      val cool = 123

      suiteAll("Nested") {
        test("test 2")(
          ZIO.service[Int].map { int =>
            assertTrue(cool == int)
          }
        )

        test("test 3")(
          ZIO.service[Int].map { int =>
            assertTrue(cool == int)
          }
        )
      }
    }
      .provide(ZLayer.succeed(123))

}
