package zio.test
import zio._

object SuiteSpec extends ZIOSpecDefault {

  def spec =
    suiteAll("SweetSpec!") {

      val hello = "hello"

      test("test")(
        assertTrue(hello.startsWith("h"))
      )

      val cool = 123

      test("another test")(
        assertTrue(hello.startsWith("h"))
      )

      suite("Nested") {

        val another = 123

        test("test 2")(
          ZIO.service[Int].map { int =>
            assertTrue(cool == int)
          }
        )

        test("test 3")(
          ZIO.service[Int].map { int =>
            assertTrue(another == int)
          }
        )
      }
    }
      .provide(ZLayer.succeed(123))

}
