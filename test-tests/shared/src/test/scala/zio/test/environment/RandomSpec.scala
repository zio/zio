package zio.test.environment

import zio.test.Assertion._
import zio.test.environment.RandomSpecUtil._
import zio.test.environment.TestRandom.DefaultData
import zio.test.{ assert, suite, test, testM, TestResult, ZIOBaseSpec }
import zio._

object RandomSpec
    extends ZIOBaseSpec(
      suite("RandomSpec")(
        testM("check clearBooleans")(checkClear(_.nextBoolean)(_.feedBooleans(_: _*))(_.clearBooleans)(_.nextBoolean)),
        testM("check clearBytes")(checkClear(nextBytes(1))(_.feedBytes(_: _*))(_.clearBytes)(_.nextBytes(1))),
        testM("check clearChars")(
          checkClear(_.nextPrintableChar)(_.feedChars(_: _*))(_.clearChars)(_.nextPrintableChar)
        ),
        testM("check clearDoubles")(checkClear(_.nextDouble)(_.feedDoubles(_: _*))(_.clearDoubles)(_.nextDouble)),
        testM("check clearFloats")(checkClear(_.nextFloat)(_.feedFloats(_: _*))(_.clearFloats)(_.nextFloat)),
        testM("check clearInts")(checkClear(_.nextInt)(_.feedInts(_: _*))(_.clearInts)(_.nextInt)),
        testM("check clearLongs")(checkClear(_.nextLong)(_.feedLongs(_: _*))(_.clearLongs)(_.nextLong)),
        testM("check clearStrings")(checkClear(_.nextString(1))(_.feedStrings(_: _*))(_.clearStrings)(_.nextString(1))),
        testM("check feedBooleans")(checkFeed(_.nextBoolean)(_.feedBooleans(_: _*))(_.nextBoolean)),
        testM("check feedBytes")(checkFeed(nextBytes(1))(_.feedBytes(_: _*))(_.nextBytes(1))),
        testM("check feedChars")(checkFeed(_.nextPrintableChar)(_.feedChars(_: _*))(_.nextPrintableChar)),
        testM("check feedDoubles")(checkFeed(_.nextDouble)(_.feedDoubles(_: _*))(_.nextDouble)),
        testM("check feedFloats")(checkFeed(_.nextFloat)(_.feedFloats(_: _*))(_.nextFloat)),
        testM("check feedInts")(checkFeed(_.nextInt)(_.feedInts(_: _*))(_.nextInt)),
        testM("check feedLongs")(checkFeed(_.nextLong)(_.feedLongs(_: _*))(_.nextLong)),
        testM("check feedStrings")(checkFeed(_.nextString(1))(_.feedStrings(_: _*))(_.nextString(1))),
        testM("check nextBoolean")(forAllEqual(_.nextBoolean)(_.nextBoolean())),
        testM("check nextBytes")(forAllEqualBytes),
        testM("check nextDouble")(forAllEqual(_.nextDouble)(_.nextDouble())),
        testM("check nextFloat")(forAllEqual(_.nextFloat)(_.nextFloat())),
        testM("check nextGaussian")(forAllEqualGaussian),
        testM("check nextInt")(forAllEqual(_.nextInt)(_.nextInt())),
        testM("check nextLong")(forAllEqual(_.nextLong)(_.nextLong())),
        testM("check nextPrintableChar")(forAllEqual(_.nextPrintableChar)(_.nextPrintableChar())),
        testM("check nextString")(forAllEqualN(_.nextString(_))(_.nextString(_))),
        testM("bounded nextInt")(forAllEqualN(_.nextInt(_))(_.nextInt(_))),
        testM("bounded nextInt generates values within the bounds")(forAllBounded(_.nextInt)(_.nextInt(_))),
        testM("bounded nextLong generates values within the bounds")(forAllBounded(_.nextLong)(_.nextLong(_))),
        testM("shuffle")(forAllEqualShuffle(_.shuffle(_))(_.shuffle(_))),
        test("referential transparency") {
          val rt   = new DefaultRuntime {}
          val test = TestRandom.makeTest(DefaultData)
          val x    = rt.unsafeRun(test.flatMap[Any, Nothing, Int](_.nextInt))
          val y    = rt.unsafeRun(test.flatMap[Any, Nothing, Int](_.nextInt))
          assert(x, equalTo(y))
        }
      )
    )

object RandomSpecUtil {

  import scala.util.{ Random => SRandom }
  import zio.test.environment.TestRandom.Test

  def checkClear[A](generate: SRandom => A)(
    feed: (Test, List[A]) => UIO[Unit]
  )(clear: Test => UIO[Unit])(extract: Test => UIO[A]): ZIO[TestRandom, Nothing, TestResult] = {
    val seed    = SRandom.nextLong()
    val sRandom = new SRandom(seed)
    for {
      testRandom <- TestRandom.makeTest(DefaultData)
      _          <- testRandom.setSeed(seed)
      value      = generate(sRandom)
      _          <- feed(testRandom, List(value))
      _          <- clear(testRandom)
      random     <- extract(testRandom)
    } yield assert(random, equalTo(generate(new SRandom(seed))))
  }
  def checkFeed[A](
    generate: SRandom => A
  )(feed: (Test, List[A]) => UIO[Unit])(extract: Test => UIO[A]): ZIO[TestRandom, Nothing, TestResult] = {
    val seed    = SRandom.nextLong()
    val sRandom = new SRandom(seed)
    for {
      testRandom <- TestRandom.makeTest(DefaultData)
      _          <- testRandom.setSeed(seed)
      values     = List.fill(100)(generate(sRandom))
      _          <- feed(testRandom, values)
      results    <- UIO.foreach(List.range(0, 100))(_ => extract(testRandom))
      random     <- extract(testRandom)
    } yield {
      assert(results, equalTo(values)) &&
      assert(random, equalTo(generate(new SRandom(seed))))
    }
  }

  def nextBytes(n: Int)(random: SRandom): Chunk[Byte] = {
    val arr = new Array[Byte](n)
    random.nextBytes(arr)
    Chunk.fromArray(arr)
  }

  def forAllEqual[A](f: Test => UIO[A])(g: SRandom => A): ZIO[Any, Nothing, TestResult] = {
    val seed    = SRandom.nextLong()
    val sRandom = new SRandom(seed)
    for {
      testRandom <- TestRandom.makeTest(DefaultData)
      _          <- testRandom.setSeed(seed)
      actual     <- UIO.foreach(List.fill(100)(()))(_ => f(testRandom))
      expected   = List.fill(100)(g(sRandom))
    } yield assert(actual, equalTo(expected))
  }

  def forAllEqualBytes: ZIO[Any, Nothing, TestResult] = {
    val seed    = SRandom.nextLong()
    val sRandom = new SRandom(seed)
    for {
      testRandom <- TestRandom.makeTest(DefaultData)
      _          <- testRandom.setSeed(seed)
      actual     <- UIO.foreach(List.range(0, 100))(testRandom.nextBytes(_))
      expected = List.range(0, 100).map(new Array[Byte](_)).map { arr =>
        sRandom.nextBytes(arr)
        Chunk.fromArray(arr)
      }
    } yield assert(actual, equalTo(expected))
  }

  def forAllEqualGaussian: ZIO[Any, Nothing, TestResult] = {
    val seed    = SRandom.nextLong()
    val sRandom = new SRandom(seed)
    for {
      testRandom <- TestRandom.makeTest(DefaultData)
      _          <- testRandom.setSeed(seed)
      actual     <- UIO.foreach(List.fill(100)(()))(_ => testRandom.nextGaussian)
      expected   = List.fill(100)(sRandom.nextGaussian)
    } yield assert(actual.zip(expected).forall { case (x, y) => math.abs(x - y) < 0.01 }, equalTo(true))
  }

  def forAllEqualN[A](f: (Test, Int) => UIO[A])(g: (SRandom, Int) => A): ZIO[Any, Nothing, TestResult] = {
    val seed    = SRandom.nextLong()
    val sRandom = new SRandom(seed)
    for {
      testRandom <- TestRandom.makeTest(DefaultData)
      _          <- testRandom.setSeed(seed)
      actual     <- UIO.foreach(1 to 100)(f(testRandom, _))
      expected   = (1 to 100).map(g(sRandom, _))
    } yield assert(actual, equalTo(expected))
  }

  def forAllEqualShuffle(
    f: (Test, List[Int]) => UIO[List[Int]]
  )(g: (SRandom, List[Int]) => List[Int]): ZIO[Any, Nothing, TestResult] = {
    val seed    = SRandom.nextLong()
    val sRandom = new SRandom(seed)
    for {
      testRandom <- TestRandom.makeTest(DefaultData)
      _          <- testRandom.setSeed(seed)
      actual     <- UIO.foreach(List.range(0, 100).map(List.range(0, _)))(f(testRandom, _))
      expected   = List.range(0, 100).map(List.range(0, _)).map(g(sRandom, _))
    } yield assert(actual, equalTo(expected))
  }

  def forAllBounded[A: Numeric](bound: SRandom => A)(f: (Test, A) => UIO[A]): ZIO[Any, Nothing, TestResult] = {
    val num = implicitly[Numeric[A]]
    import num._
    val seed    = SRandom.nextLong()
    val sRandom = new SRandom(seed)
    for {
      testRandom <- TestRandom.makeTest(DefaultData)
      _          <- testRandom.setSeed(seed)
      bounds     = List.fill(100)(num.abs(bound(sRandom)) max one)
      actual     <- UIO.foreach(bounds)(f(testRandom, _))
    } yield assert(actual.zip(bounds).forall { case (a, n) => zero <= a && a < n }, equalTo(true))
  }
}
