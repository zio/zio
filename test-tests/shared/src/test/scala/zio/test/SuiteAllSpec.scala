package zio.test
import zio._
import zio.test.TestAspect.ignore

object SuiteAllSpec extends ZIOSpecDefault {

  def spec =
    suiteAll("SweetSpec!") {

      val hello = "hello"

      test("test 1 ")(
        assertTrue(hello.startsWith("h"))
      )

      val cool = 123

      test("another test")(
        ZIO.service[Int].map { x =>
          assertTrue(x == cool)
        }
      )

      suiteAll("NEST") {
        test("nest test 1")(
          assertTrue(hello.endsWith("o"))
        )

        test("nest test 2")(
          assertCompletes
        )
      }
      suiteAll("a") {
        test("b") {
          ZIO.fail("boom").as(assertCompletes)
        } @@ ignore
      }

      test("kablooey") {
        ???
      }
    }
      .provide(ZLayer.succeed(123))

}
