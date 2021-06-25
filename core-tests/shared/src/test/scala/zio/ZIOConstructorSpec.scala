package zio

import zio.test._

object ZIOConstructorSpec extends ZIOBaseSpec {

  def spec: ZSpec[Environment, Failure] =
    suite("ZIOConstructorSpec")(
      test("either") {
        val either: Either[Int, String] = Left(12)
        val zio                         = ZIO(either)
        val _: ZIO[Any, Int, String]    = zio
        assertCompletes
      },
      test("function") {
        val zio                       = ZIO((x: Int) => x * x)
        val _: ZIO[Int, Nothing, Int] = zio
        assertCompletes
      },
      test("left") {
        val zio                       = ZIO(Left(12))
        val _: ZIO[Any, Int, Nothing] = zio
        assertCompletes
      }
    )
}
