package zio.stm.random

import zio.ZIOBaseSpec
import zio.random.Random
import zio.test.Assertion._
import zio.test._

object TRandomSpec extends ZIOBaseSpec {

  implicit val DoubleOrdering: Ordering[Double] =
    (l, r) => java.lang.Double.compare(l, r)

  implicit val FloatOrdering: Ordering[Float] =
    (l, r) => java.lang.Float.compare(l, r)

  def spec: ZSpec[Environment, Failure] = suite("TRandomSpec")(
    testM("nextDoubleBetween generates doubles in specified range") {
      checkM(genDoubles) { case (min, max) =>
        for {
          n <- nextDoubleBetween(min, max).commit
        } yield assert(n)(isGreaterThanEqualTo(min)) &&
          assert(n)(isLessThan(max))
      }
    },
    testM("nextFloatBetween generates floats in specified range") {
      checkM(genFloats) { case (min, max) =>
        for {
          n <- nextFloatBetween(min, max).commit
        } yield assert(n)(isGreaterThanEqualTo(min)) &&
          assert(n)(isLessThan(max))
      }
    },
    testM("nextIntBetween generates integers in specified range") {
      checkM(genInts) { case (min, max) =>
        for {
          n <- nextIntBetween(min, max).commit
        } yield assert(n)(isGreaterThanEqualTo(min)) &&
          assert(n)(isLessThan(max))
      }
    },
    testM("nextLongBetween generates longs in specified range") {
      checkM(genLongs) { case (min, max) =>
        for {
          n <- nextLongBetween(min, max).commit
        } yield assert(n)(isGreaterThanEqualTo(min)) &&
          assert(n)(isLessThan(max))
      }
    }
  ).provideCustomLayerManual(TRandom.live)

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
