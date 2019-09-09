package zio.test

import zio.DefaultRuntime

import scala.concurrent.Future
import scala.math.abs

import zio.{ random, ZIO }
import zio.test.TestUtils.{ label }

object FunSpec extends DefaultRuntime {

  val run: List[Async[(Boolean, String)]] = List(
    label(funConvertsEffectsIntoPureFunctions, "fun converts effects into pure functions"),
    label(funDoesNotHaveRaceConditions, "fun does not have race conditions")
  )

  def funConvertsEffectsIntoPureFunctions: Future[Boolean] =
    unsafeRunToFuture {
      for {
        f <- Fun.make((n: Int) => random.nextInt(n))
        n <- random.nextInt.map(abs(_))
      } yield f(n) == f(n)
    }

  def funDoesNotHaveRaceConditions: Future[Boolean] =
    unsafeRunToFuture {
      for {
        f       <- Fun.make((_: Int) => random.nextInt(6))
        results <- ZIO.foreachPar(List.range(0, 1000))(n => ZIO.effectTotal((n % 6, f(n % 6))))
      } yield results.distinct.length == 6
    }
}
