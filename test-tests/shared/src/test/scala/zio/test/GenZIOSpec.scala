package zio.test

import zio.test.Assertion._
import zio.test.Gen._
import zio.test.GenUtils.{partitionExit, sampleEffect}

object GenZIOSpec extends ZIOBaseSpec {

  def spec = suite("GenZIOSpec")(
    test("died generates died effects") {
      val gen = died(Gen.throwable)
      for {
        sample               <- sampleEffect(gen)
        (failures, successes) = partitionExit(sample)
      } yield assert(successes)(isEmpty) &&
        assert(failures)(isNonEmpty && forall(dies(anything)))
    },
    test("failures generates failed effects") {
      val gen = failures(string)
      for {
        sample               <- sampleEffect(gen)
        (failures, successes) = partitionExit(sample)
      } yield assert(successes)(isEmpty) &&
        assert(failures)(isNonEmpty && forall(fails(anything)))
    },
    test("successes generates successful effects") {
      val gen = successes(int(-10, 10))
      for {
        sample               <- sampleEffect(gen)
        (failures, successes) = partitionExit(sample)
      } yield assert(successes)(isNonEmpty && forall(isWithin(-10, 10))) &&
        assert(failures)(isEmpty)
    }
  )
}
