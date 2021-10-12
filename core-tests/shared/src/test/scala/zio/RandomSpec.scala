package zio

import zio.random.Random
import zio.test.Assertion._
import zio.test._
import zio.test.environment.Live

object RandomSpec extends ZIOBaseSpec {

  implicit val DoubleOrdering: Ordering[Double] =
    (l, r) => java.lang.Double.compare(l, r)

  implicit val FloatOrdering: Ordering[Float] =
    (l, r) => java.lang.Float.compare(l, r)

  def spec: ZSpec[Environment, Failure] = suite("RandomSpec")(
    testM("nextDoubleBetween generates doubles in specified range") {
      checkM(genDoubles) { case (min, max) =>
        for {
          n <- Live.live(random.nextDoubleBetween(min, max))
        } yield assert(n)(isGreaterThanEqualTo(min)) &&
          assert(n)(isLessThan(max))
      }
    },
    testM("nextFloatBetween generates floats in specified range") {
      checkM(genFloats) { case (min, max) =>
        for {
          n <- Live.live(random.nextFloatBetween(min, max))
        } yield assert(n)(isGreaterThanEqualTo(min)) &&
          assert(n)(isLessThan(max))
      }
    },
    testM("nextIntBetween generates integers in specified range") {
      checkM(genInts) { case (min, max) =>
        for {
          n <- Live.live(random.nextIntBetween(min, max))
        } yield assert(n)(isGreaterThanEqualTo(min)) &&
          assert(n)(isLessThan(max))
      }
    },
    testM("nextLongBetween generates longs in specified range") {
      checkM(genLongs) { case (min, max) =>
        for {
          n <- Live.live(random.nextLongBetween(min, max))
        } yield assert(n)(isGreaterThanEqualTo(min)) &&
          assert(n)(isLessThan(max))
      }
    },
    testM("nextUUID generates universally unique identifiers") {
      check(Gen.fromEffect(Live.live(random.nextUUID))) { uuid =>
        assert(uuid.variant)(equalTo(2))
      }
    },
    testM("scalaRandom") {
      val layer  = ZLayer.fromEffect(ZIO.succeed(new scala.util.Random)) >>> Random.scalaRandom
      val sample = ZIO.replicateM(5)((random.setSeed(91) *> random.nextInt).provideSomeLayer(layer.fresh))
      for {
        values <- ZIO.collectAllPar(ZIO.replicate(5)(sample))
      } yield assertTrue(values.toSet.size == 1)
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
