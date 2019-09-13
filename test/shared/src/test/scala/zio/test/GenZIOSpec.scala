package zio.test

import scala.concurrent.Future

import zio.random.Random
import zio.test.GenUtils.{ checkSampleEffect, partitionEither }
import zio.test.TestUtils.label

object GenZIOSpec {

  val run: List[Async[(Boolean, String)]] = List(
    label(ioGeneratesIOEffects, "io generates IO effects"),
    label(rioGeneratesRIOEffects, "rio generates RIO effects"),
    label(taskGeneratesTaskEffects, "task generates Task effects"),
    label(uioGeneratesUIOEffects, "uio generates UIO effects"),
    label(urioGeneratesURIOEffects, "urio generates URIO effects"),
    label(zioGeneratesZIOEffects, "zio generates ZIO effects")
  )

  def ioGeneratesIOEffects: Future[Boolean] = {
    val gen = Gen.io(Gen.string(Gen.alphaNumericChar), Gen.int(-10, 10))
    checkSampleEffect(gen) { results =>
      val (failures, successes) = partitionEither(results)
      failures.nonEmpty &&
      successes.nonEmpty &&
      successes.forall(n => -10 <= n && n <= 10)
    }
  }

  def rioGeneratesRIOEffects: Future[Boolean] = {
    val gen = Gen.rio(Gen.int(-10, 10))
    checkSampleEffect(gen) { results =>
      val (failures, successes) = partitionEither(results)
      failures.nonEmpty &&
      successes.nonEmpty &&
      successes.forall(n => -10 <= n && n <= 10)
    }
  }

  def taskGeneratesTaskEffects: Future[Boolean] = {
    val gen = Gen.task(Gen.int(-10, 10))
    checkSampleEffect(gen) { results =>
      val (failures, successes) = partitionEither(results)
      failures.nonEmpty &&
      successes.nonEmpty &&
      successes.forall(n => -10 <= n && n <= 10)
    }
  }

  def uioGeneratesUIOEffects: Future[Boolean] = {
    val gen = Gen.uio(Gen.int(-10, 10))
    checkSampleEffect(gen) { results =>
      val (failures, successes) = partitionEither(results)
      failures.isEmpty &&
      successes.nonEmpty &&
      successes.forall(n => -10 <= n && n <= 10)
    }
  }

  def urioGeneratesURIOEffects: Future[Boolean] = {
    val gen = Gen.urio(Gen.int(-10, 10))
    checkSampleEffect(gen) { results =>
      val (failures, successes) = partitionEither(results)
      failures.isEmpty &&
      successes.nonEmpty &&
      successes.forall(n => -10 <= n && n <= 10)
    }
  }

  def zioGeneratesZIOEffects: Future[Boolean] = {
    val gen = Gen.zio[Random with Sized, Any, String, Int](
      Gen.string(Gen.alphaNumericChar),
      Gen.int(-10, 10)
    )
    checkSampleEffect(gen) { results =>
      val (failures, successes) = partitionEither(results)
      failures.nonEmpty &&
      successes.nonEmpty &&
      successes.forall(n => -10 <= n && n <= 10)
    }
  }
}
