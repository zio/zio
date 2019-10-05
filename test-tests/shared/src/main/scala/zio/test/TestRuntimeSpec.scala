package zio.test

import scala.concurrent.Future

import zio.test.TestUtils.label
import zio.UIO
import zio.stream.ZStream
import zio.random.Random
import zio.Exit.Success

object TestRuntimeSpec extends ZIOBaseSpec {
  val run: List[Async[(Boolean, String)]] = List(
    label(simpleSuccess, "returns simple success"),
    label(never, "allow to use never"),
    label(simpleRace, "find both winner in a simple race")
  )

  def simpleSuccess: Future[Boolean] = {
    val gen = TestRuntime.paths(UIO.succeed(1))

    expectToSee(gen)(Some(Success(1)))
  }

  def never: Future[Boolean] = {
    val gen = TestRuntime.paths(UIO.never)

    expectToSee(gen)(None)
  }

  def simpleRace: Future[Boolean] = {
    val race = UIO.succeed(1) race UIO.succeed(2)

    val gen = TestRuntime.paths(race)

    expectToSee(gen)(Some(Success(1)), Some(Success(2)))
  }

  private def expectToSee[A](gen: ZStream[Random, Nothing, A])(expected: A*): Future[Boolean] =
    unsafeRunToFuture(gen.take(100).fold(Set.empty[A])(_ + _).map(_ == expected.toSet))
}
