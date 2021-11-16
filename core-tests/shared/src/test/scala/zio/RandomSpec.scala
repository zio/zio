package zio

import zio.test.Assertion._
import zio.test._

object RandomSpec extends ZIOBaseNewSpec {

  implicit val DoubleOrdering: Ordering[Double] =
    (l, r) => java.lang.Double.compare(l, r)

  implicit val FloatOrdering: Ordering[Float] =
    (l, r) => java.lang.Float.compare(l, r)

  def spec = suite("RandomSpec")(
    test("nextDoubleBetween generates doubles in specified range") {
      check(genDoubles) { case (min, max) =>
        for {
          n <- Live.live(Random.nextDoubleBetween(min, max))
        } yield assert(n)(isGreaterThanEqualTo(min)) &&
          assert(n)(isLessThan(max))
      }
    },
    test("nextFloatBetween generates floats in specified range") {
      check(genFloats) { case (min, max) =>
        for {
          n <- Live.live(Random.nextFloatBetween(min, max))
        } yield assert(n)(isGreaterThanEqualTo(min)) &&
          assert(n)(isLessThan(max))
      }
    },
    test("nextIntBetween generates integers in specified range") {
      check(genInts) { case (min, max) =>
        for {
          n <- Live.live(Random.nextIntBetween(min, max))
        } yield assert(n)(isGreaterThanEqualTo(min)) &&
          assert(n)(isLessThan(max))
      }
    },
    test("nextLongBetween generates longs in specified range") {
      check(genLongs) { case (min, max) =>
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
    },
    test("scalaRandom") {
      val serviceBuilder = ZServiceBuilder.fromZIO(ZIO.succeed(new scala.util.Random)) >>> Random.scalaRandom
      val sample         = ZIO.replicateZIO(5)((Random.setSeed(91) *> Random.nextInt).provideSomeServices(serviceBuilder.fresh))
      for {
        values <- ZIO.collectAllPar(ZIO.replicate(5)(sample))
      } yield assertTrue(values.toSet.size == 1)
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
