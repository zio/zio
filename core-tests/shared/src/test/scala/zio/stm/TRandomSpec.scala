package zio.stm

import zio.stm.TRandom._
import zio.test.Assertion.{isGreaterThanEqualTo, isLessThan}
import zio.test._
import zio.ZIOBaseSpec

object TRandomSpec extends ZIOBaseSpec {

  implicit val DoubleOrdering: Ordering[Double] =
    (l, r) => java.lang.Double.compare(l, r)

  implicit val FloatOrdering: Ordering[Float] =
    (l, r) => java.lang.Float.compare(l, r)

  def spec = suite("TRandomSpec")(
    test("nextDoubleBetween generates doubles in specified range") {
      check(genDoubles) { case (min, max) =>
        for {
          n <- nextDoubleBetween(min, max).commit
        } yield assert(n)(isGreaterThanEqualTo(min)) &&
          assert(n)(isLessThan(max))
      }
    },
    test("nextFloatBetween generates floats in specified range") {
      check(genFloats) { case (min, max) =>
        for {
          n <- nextFloatBetween(min, max).commit
        } yield assert(n)(isGreaterThanEqualTo(min)) &&
          assert(n)(isLessThan(max))
      }
    },
    test("nextIntBetween generates integers in specified range") {
      check(genInts) { case (min, max) =>
        for {
          n <- nextIntBetween(min, max).commit
        } yield assert(n)(isGreaterThanEqualTo(min)) &&
          assert(n)(isLessThan(max))
      }
    },
    test("nextLongBetween generates longs in specified range") {
      check(genLongs) { case (min, max) =>
        for {
          n <- nextLongBetween(min, max).commit
        } yield assert(n)(isGreaterThanEqualTo(min)) &&
          assert(n)(isLessThan(max))
      }
    }
  ).provideLayer(TRandom.live)

  val genDoubles: Gen[Any, (Double, Double)] =
    for {
      a <- Gen.double
      b <- Gen.double if a != b
    } yield if (b > a) (a, b) else (b, a)

  val genFloats: Gen[Any, (Float, Float)] =
    for {
      a <- Gen.float
      b <- Gen.float if a != b
    } yield if (b > a) (a, b) else (b, a)

  val genInts: Gen[Any, (Int, Int)] =
    for {
      a <- Gen.int
      b <- Gen.int if a != b
    } yield if (b > a) (a, b) else (b, a)

  val genLongs: Gen[Any, (Long, Long)] =
    for {
      a <- Gen.long
      b <- Gen.long if a != b
    } yield if (b > a) (a, b) else (b, a)
}
