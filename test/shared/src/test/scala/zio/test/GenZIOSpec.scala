package zio.test

import scala.concurrent.Future

import zio.test.Gen._
import zio.test.GenUtils.{ checkSampleEffect, partitionEither }
import zio.test.TestUtils.label

object GenZIOSpec extends BaseSpec {

  val run: List[Async[(Boolean, String)]] = List(
    label(failuresGeneratesFailedEffects, "died generates died effects"),
    label(failuresGeneratesFailedEffects, "failures generates failed effects"),
    label(successesGeneratesSuccessfulEffects, "successes generates successful effects")
  )

  def diedGeneratesDiedEffects: Future[Boolean] = {
    val gen = died(Gen.throwable)
    checkSampleEffect(gen) { results =>
      val (failures, successes) = partitionEither(results)
      failures.nonEmpty &&
      successes.isEmpty &&
      failures.forall { case _: Throwable => true }
    }
  }

  def failuresGeneratesFailedEffects: Future[Boolean] = {
    val gen = failures(anyString)
    checkSampleEffect(gen) { results =>
      val (failures, successes) = partitionEither(results)
      failures.nonEmpty &&
      successes.isEmpty
    }
  }

  def successesGeneratesSuccessfulEffects: Future[Boolean] = {
    val gen = successes(int(-10, 10))
    checkSampleEffect(gen) { results =>
      val (failures, successes) = partitionEither(results)
      failures.isEmpty &&
      successes.nonEmpty &&
      successes.forall(n => -10 <= n && n <= 10)
    }
  }
}
