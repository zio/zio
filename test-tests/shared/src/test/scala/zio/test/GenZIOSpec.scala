package zio.test

import zio.test.Assertion._
import zio.test.Gen._
import zio.test.GenUtils.{partitionExit, sampleEffect}

object GenZIOSpec extends ZIOBaseSpec {

  def spec: ZSpec[Environment, Failure] = suite("GenZIOSpec")(
    testM("died generates died effects") {
      val gen = died(Gen.throwable)
      for {
        sample               <- sampleEffect(gen)
        (failures, successes) = partitionExit(sample)
      } yield {
        assert(successes)(isEmpty) &&
        assert(failures)(isNonEmpty && forall(dies(anything)))
      }
    },
    testM("failures generates failed effects") {
      val gen = failures(anyString)
      for {
        sample               <- sampleEffect(gen)
        (failures, successes) = partitionExit(sample)
      } yield {
        assert(successes)(isEmpty) &&
        assert(failures)(isNonEmpty && forall(fails(anything)))
      }
    },
    testM("successes generates successful effects") {
      val gen = successes(int(-10, 10))
      for {
        sample               <- sampleEffect(gen)
        (failures, successes) = partitionExit(sample)
      } yield {
        assert(successes)(isNonEmpty && forall(isWithin(-10, 10))) &&
        assert(failures)(isEmpty)
      }
    }
  )
}
