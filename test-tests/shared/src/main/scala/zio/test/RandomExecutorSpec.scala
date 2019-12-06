package zio.test

import scala.concurrent.Future
import zio.test.TestUtils.label
import zio.{ Exit, UIO, URIO, ZIO }
import zio.Exit.Success
import zio.clock.Clock
import zio.duration._
import zio.stream.ZStream

object RandomExecutorSpec extends AsyncBaseSpec {
  val run: List[Async[(Boolean, String)]] = List(
    label(simpleSuccess, "returns simple success"),
    label(sleep, "allow to use sleep"),
    label(simpleRace, "find both winner in a simple race")
  )

  def simpleSuccess: Future[Boolean] = {
    val gen = RandomExecutor.paths(UIO.succeed(1))

    expectToSee(gen)(Success(1))
  }

  def sleep: Future[Boolean] = {
    val gen = RandomExecutor.paths(zio.ZIO.sleep(20.milliseconds))

    expectToSee(gen)(Success(()))
  }

  def simpleRace: Future[Boolean] = {
    val race = UIO.succeed(1) race UIO.succeed(2)

    val gen = RandomExecutor.run(race)

    shouldBeASimulation(gen)(race.run)
  }

  private def expectToSee[A](gen: ZStream[Clock, Nothing, A])(expected: A*): Future[Boolean] =
    unsafeRunToFuture(gen.take(100).fold(Set.empty[A])(_ + _).map(_ == expected.toSet))

  private def shouldBeASimulation[E, A](
    gen: ZIO[Clock, E, A]
  )(expected: ZIO[Clock, E, A]): Future[Boolean] = {
    val real = sample(expected)

    unsafeRunToFuture(real.flatMap(collectUntil(gen, _)))
  }

  private def collectUntil[R, E, A](
    gen: ZIO[R, E, A],
    real: Set[Exit[E, A]],
    max: Int = 1000
  ): ZIO[R, Nothing, Boolean] =
    if (real.isEmpty)
      ZIO.succeed(true)
    else if (max <= 0)
      ZIO.succeed(false)
    else
      gen.run.flatMap(probe => collectUntil(gen, real - probe, max - 1))

  private def sample[R, E, A](zio: ZIO[R, E, A]): URIO[R, Set[Exit[E, A]]] =
    ZStream.fromEffect(zio.run).take(10).fold(Set.empty[Exit[E, A]])(_ + _)
}
