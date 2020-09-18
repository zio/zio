package zio

import zio.clock.Clock
import zio.random.Random
import zio.test.Assertion._
import zio.test._
import zio.test.environment.{ Live, TestClock, TestConsole, TestRandom, TestSystem }

object RandomSpec extends ZIOBaseSpec {

  implicit val DoubleOrdering: Ordering[Double] =
    (l, r) => java.lang.Double.compare(l, r)

  implicit val FloatOrdering: Ordering[Float] =
    (l, r) => java.lang.Float.compare(l, r)

  def spec: Spec[Has[Annotations.Service] with Has[Live.Service] with Has[Sized.Service] with Has[
    TestClock.Service
  ] with Has[TestConfig.Service] with Has[TestConsole.Service] with Has[TestRandom.Service] with Has[
    TestSystem.Service
  ] with Has[Clock.Service] with Has[zio.console.Console.Service] with Has[zio.system.System.Service] with Has[
    Random.Service
  ], TestFailure[Any], TestSuccess] = suite("RandomSpec")(
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
