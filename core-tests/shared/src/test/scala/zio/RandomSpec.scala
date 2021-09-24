package zio

import zio.test.Assertion._
import zio.test._
import zio.test.environment.Live

object RandomSpec extends ZIOBaseSpec {

  implicit val DoubleOrdering: Ordering[Double] =
    (l, r) => java.lang.Double.compare(l, r)

  implicit val FloatOrdering: Ordering[Float] =
    (l, r) => java.lang.Float.compare(l, r)

  def spec: ZSpec[Environment, Failure] = suite("RandomSpec")(
    test("nextDoubleBetween generates doubles in specified range") {
      checkM(genDoubles) { case (min, max) =>
        for {
          n <- Live.live(Random.nextDoubleBetween(min, max))
        } yield assert(n)(isGreaterThanEqualTo(min)) &&
          assert(n)(isLessThan(max))
      }
    },
    test("nextFloatBetween generates floats in specified range") {
      checkM(genFloats) { case (min, max) =>
        for {
          n <- Live.live(Random.nextFloatBetween(min, max))
        } yield assert(n)(isGreaterThanEqualTo(min)) &&
          assert(n)(isLessThan(max))
      }
    },
    test("nextIntBetween generates integers in specified range") {
      checkM(genInts) { case (min, max) =>
        for {
          n <- Live.live(Random.nextIntBetween(min, max))
        } yield assert(n)(isGreaterThanEqualTo(min)) &&
          assert(n)(isLessThan(max))
      }
    },
    test("nextLongBetween generates longs in specified range") {
      checkM(genLongs) { case (min, max) =>
        for {
          n <- Live.live(Random.nextLongBetween(min, max))
        } yield assert(n)(isGreaterThanEqualTo(min)) &&
          assert(n)(isLessThan(max))
      }
    },
    test("nextUUID generates universally unique identifiers") {
      check(Gen.fromZIO(Live.live(Random.nextUUID))) { uuid =>
        assert(uuid.variant)(equalTo(2))
      }
    }
  )

  val genDoubles: Gen[Has[Random], (Double, Double)] =
    for {
      a <- Gen.double
      b <- Gen.double if a != b
    } yield if (b > a) (a, b) else (b, a)

  val genFloats: Gen[Has[Random], (Float, Float)] =
    for {
      a <- Gen.float
      b <- Gen.float if a != b
    } yield if (b > a) (a, b) else (b, a)

  val genInts: Gen[Has[Random], (Int, Int)] =
    for {
      a <- Gen.int
      b <- Gen.int if a != b
    } yield if (b > a) (a, b) else (b, a)

  val genLongs: Gen[Has[Random], (Long, Long)] =
    for {
      a <- Gen.long
      b <- Gen.long if a != b
    } yield if (b > a) (a, b) else (b, a)
}
