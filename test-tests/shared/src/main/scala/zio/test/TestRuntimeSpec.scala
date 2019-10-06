package zio.test

import scala.concurrent.Future

import zio.test.TestUtils.label
import zio.test.TestRuntime.paths
import zio.UIO
import zio.Exit.Success
import zio.clock.Clock
import zio.duration._
import zio.stream.ZStream
import zio.random.Random

object TestRuntimeSpec extends ZIOBaseSpec {
  val run: List[Async[(Boolean, String)]] = List(
    label(simpleSuccess, "returns simple success"),
    // label(sleep, "allow to use sleep"),
    label(simpleRace, "find both winner in a simple race")
  )

  def simpleSuccess: Future[Boolean] = {
    val gen = paths(UIO.succeed(1))

    expectToSee(gen)(Success(1))
  }

  def sleep: Future[Boolean] = {
    val gen = paths(zio.ZIO.sleep(2.seconds))

    expectToSee(gen)(Success(()))
  }

  def simpleRace: Future[Boolean] = {
    val race = UIO.succeed(1) race UIO.succeed(2)

    val gen = paths(race)

    expectToSee(gen)(Success(1), Success(2))
  }

  private def expectToSee[A](gen: ZStream[Clock with Random, Nothing, A])(expected: A*): Future[Boolean] =
    unsafeRunToFuture(gen.take(100).fold(Set.empty[A])(_ + _).map(_ == expected.toSet))
}
