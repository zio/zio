package zio.test

import scala.concurrent.Future

import zio.test.TestUtils.label
import zio.test.GenUtils.checkSample
import zio.UIO
import zio.Exit

object TestRuntimeSpec extends ZIOBaseSpec {
  val run: List[Async[(Boolean, String)]] = List(
    label(testRuntime, "TestRuntime finds both winner in a race")
  )

  def testRuntime: Future[Boolean] = {
    val race = UIO.succeed(1) race UIO.succeed(2)

    val gen = TestRuntime.analyse(race)

    checkSample(gen)(_.toSet == Set(1, 2).map(result => Some(Exit.Success(result))))
  }
}
