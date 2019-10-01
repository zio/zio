package zio.test

import scala.concurrent.Future
import scala.math.abs

import zio.{ random, ZIO }
import zio.test.TestUtils.{ label }

object FunSpec extends BaseSpec {

  val run: List[Async[(Boolean, String)]] = List(
    label(funConvertsEffectsIntoPureFunctions, "fun converts effects into pure functions"),
    label(funDoesNotHaveRaceConditions, "fun does not have race conditions"),
    label(funIsShowable, "fun is showable"),
    label(funIsSupportedOnScalaJS, "fun is supported on Scala.js")
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

  def funIsShowable: Future[Boolean] =
    unsafeRunToFuture {
      for {
        f <- Fun.make((_: String) => random.nextBoolean)
        p = f("Scala")
        q = f("Haskell")
      } yield f.toString == s"Fun(Scala -> $p, Haskell -> $q)" ||
        f.toString == s"Fun(Haskell -> $q, Scala -> $p)"
    }

  def funIsSupportedOnScalaJS: Future[Boolean] =
    unsafeRunToFuture {
      for {
        f <- Fun.make((_: Int) => ZIO.foreach(List.range(0, 100000))(ZIO.succeed))
        _ = f(1)
      } yield true
    }
}
