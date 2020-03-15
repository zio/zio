package zio

import zio.random.Random
import zio.test.Assertion._
import zio.test._
import zio.test.environment.Live

object RandomSpec extends ZIOBaseSpec {

  def spec = suite("RandomSpec")(
    testM("between generates doubles in specified range") {
      checkM(genDoubles) {
        case (min, max) =>
          for {
            n <- Live.live(random.between(min, max))
          } yield assert(n)(isGreaterThanEqualTo(min)) &&
            assert(n)(isLessThan(max))
      }
    },
    testM("between generates floats in specified range") {
      checkM(genFloats) {
        case (min, max) =>
          for {
            n <- Live.live(random.between(min, max))
          } yield assert(n)(isGreaterThanEqualTo(min)) &&
            assert(n)(isLessThan(max))
      }
    },
    testM("between generates integers in specified range") {
      checkM(genInts) {
        case (min, max) =>
          for {
            n <- Live.live(random.between(min, max))
          } yield assert(n)(isGreaterThanEqualTo(min)) &&
            assert(n)(isLessThan(max))
      }
    },
    testM("between generates longs in specified range") {
      checkM(genLongs) {
        case (min, max) =>
          for {
            n <- Live.live(random.between(min, max))
          } yield assert(n)(isGreaterThanEqualTo(min)) &&
            assert(n)(isLessThan(max))
      }
    }
  )

  val genDoubles: Gen[Random, (Double, Double)] =
    for {
      a <- Gen.anyDouble
      b <- Gen.anyDouble if a != b
    } yield if (b > a) (a, b) else (b, a)

  val genFloats: Gen[Random, (Float, Float)] =
    for {
      a <- Gen.anyFloat
      b <- Gen.anyFloat if a != b
    } yield if (b > a) (a, b) else (b, a)

  val genInts: Gen[Random, (Int, Int)] =
    for {
      a <- Gen.anyInt
      b <- Gen.anyInt if a != b
    } yield if (b > a) (a, b) else (b, a)

  val genLongs: Gen[Random, (Long, Long)] =
    for {
      a <- Gen.anyLong
      b <- Gen.anyLong if a != b
    } yield if (b > a) (a, b) else (b, a)
}
