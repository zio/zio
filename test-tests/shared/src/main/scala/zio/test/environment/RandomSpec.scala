package zio.test.environment

import scala.concurrent.Future
import scala.util.{ Random => SRandom }

import zio.{ Chunk, UIO }
import zio.test.environment.TestRandom.{ DefaultData, Test }
import zio.test.Async
import zio.test.TestUtils.label
import zio.test.AsyncBaseSpec

object RandomSpec extends AsyncBaseSpec {

  val run: List[Async[(Boolean, String)]] = List(
    label(checkClear(_.nextBoolean)(_.feedBooleans(_: _*))(_.clearBooleans)(_.nextBoolean), "clearBooleans"),
    label(checkClear(nextBytes(1))(_.feedBytes(_: _*))(_.clearBytes)(_.nextBytes(1)), "clearBytes"),
    label(checkClear(_.nextPrintableChar)(_.feedChars(_: _*))(_.clearChars)(_.nextPrintableChar), "clearChars"),
    label(checkClear(_.nextDouble)(_.feedDoubles(_: _*))(_.clearDoubles)(_.nextDouble), "clearDoubles"),
    label(checkClear(_.nextFloat)(_.feedFloats(_: _*))(_.clearFloats)(_.nextFloat), "clearFloats"),
    label(checkClear(_.nextInt)(_.feedInts(_: _*))(_.clearInts)(_.nextInt), "clearInts"),
    label(checkClear(_.nextLong)(_.feedLongs(_: _*))(_.clearLongs)(_.nextLong), "clearLongs"),
    label(checkClear(_.nextString(1))(_.feedStrings(_: _*))(_.clearStrings)(_.nextString(1)), "clearStrings"),
    label(checkFeed(_.nextBoolean)(_.feedBooleans(_: _*))(_.nextBoolean), "feedBooleans"),
    label(checkFeed(nextBytes(1))(_.feedBytes(_: _*))(_.nextBytes(1)), "feedBytes"),
    label(checkFeed(_.nextPrintableChar)(_.feedChars(_: _*))(_.nextPrintableChar), "feedChars"),
    label(checkFeed(_.nextDouble)(_.feedDoubles(_: _*))(_.nextDouble), "feedDoubles"),
    label(checkFeed(_.nextFloat)(_.feedFloats(_: _*))(_.nextFloat), "feedFloats"),
    label(checkFeed(_.nextInt)(_.feedInts(_: _*))(_.nextInt), "feedInts"),
    label(checkFeed(_.nextLong)(_.feedLongs(_: _*))(_.nextLong), "feedLongs"),
    label(checkFeed(_.nextString(1))(_.feedStrings(_: _*))(_.nextString(1)), "feedStrings"),
    label(referentiallyTransparent, "referential transparency"),
    label(forAllEqual(_.nextBoolean)(_.nextBoolean()), "nextBoolean"),
    label(forAllEqualBytes, "nextBytes"),
    label(forAllEqual(_.nextDouble)(_.nextDouble()), "nextDouble"),
    label(forAllEqual(_.nextFloat)(_.nextFloat()), "nextFloat"),
    label(forAllEqualGaussian, "nextGaussian"),
    label(forAllEqual(_.nextInt)(_.nextInt()), "nextInt"),
    label(forAllEqualN(_.nextInt(_))(_.nextInt(_)), "bounded nextInt"),
    label(forAllEqual(_.nextLong)(_.nextLong()), "nextLong"),
    label(forAllEqual(_.nextPrintableChar)(_.nextPrintableChar()), "nextPrintableChar"),
    label(forAllEqualN(_.nextString(_))(_.nextString(_)), "nextString"),
    label(forAllEqualShuffle(_.shuffle(_))(_.shuffle(_)), "shuffle"),
    label(forAllBounded(_.nextInt)(_.nextInt(_)), "bounded nextInt generates values within the bounds"),
    label(forAllBounded(_.nextLong)(_.nextLong(_)), "bounded nextLong generates values within the bounds")
  )

  def referentiallyTransparent: Future[Boolean] = {
    val test = TestRandom.makeTest(DefaultData)
    val x    = unsafeRun(test.flatMap[Any, Nothing, Int](_.nextInt))
    val y    = unsafeRun(test.flatMap[Any, Nothing, Int](_.nextInt))
    Future.successful(x == y)
  }

  def forAllEqual[A](f: Test => UIO[A])(g: SRandom => A): Future[Boolean] = {
    val seed    = SRandom.nextLong()
    val sRandom = new SRandom(seed)
    unsafeRunToFuture {
      for {
        testRandom <- TestRandom.makeTest(DefaultData)
        _          <- testRandom.setSeed(seed)
        actual     <- UIO.foreach(List.fill(100)(()))(_ => f(testRandom))
        expected   = List.fill(100)(g(sRandom))
      } yield actual == expected
    }
  }

  def forAllEqualBytes: Future[Boolean] = {
    val seed    = SRandom.nextLong()
    val sRandom = new SRandom(seed)
    unsafeRunToFuture {
      for {
        testRandom <- TestRandom.makeTest(DefaultData)
        _          <- testRandom.setSeed(seed)
        actual     <- UIO.foreach(List.range(0, 100))(testRandom.nextBytes(_))
        expected = List.range(0, 100).map(new Array[Byte](_)).map { arr =>
          sRandom.nextBytes(arr)
          Chunk.fromArray(arr)
        }
      } yield actual == expected
    }
  }

  def forAllEqualGaussian: Future[Boolean] = {
    val seed    = SRandom.nextLong()
    val sRandom = new SRandom(seed)
    unsafeRunToFuture {
      for {
        testRandom <- TestRandom.makeTest(DefaultData)
        _          <- testRandom.setSeed(seed)
        actual     <- UIO.foreach(List.fill(100)(()))(_ => testRandom.nextGaussian)
        expected   = List.fill(100)(sRandom.nextGaussian)
      } yield actual.zip(expected).forall { case (x, y) => math.abs(x - y) < 0.01 }
    }
  }

  def forAllEqualN[A](f: (Test, Int) => UIO[A])(g: (SRandom, Int) => A): Future[Boolean] = {
    val seed    = SRandom.nextLong()
    val sRandom = new SRandom(seed)
    unsafeRunToFuture {
      for {
        testRandom <- TestRandom.makeTest(DefaultData)
        _          <- testRandom.setSeed(seed)
        actual     <- UIO.foreach(1 to 100)(f(testRandom, _))
        expected   = (1 to 100).map(g(sRandom, _))
      } yield actual == expected
    }
  }

  def forAllEqualShuffle(
    f: (Test, List[Int]) => UIO[List[Int]]
  )(g: (SRandom, List[Int]) => List[Int]): Future[Boolean] = {
    val seed    = SRandom.nextLong()
    val sRandom = new SRandom(seed)
    unsafeRunToFuture {
      for {
        testRandom <- TestRandom.makeTest(DefaultData)
        _          <- testRandom.setSeed(seed)
        actual     <- UIO.foreach(List.range(0, 100).map(List.range(0, _)))(f(testRandom, _))
        expected   = List.range(0, 100).map(List.range(0, _)).map(g(sRandom, _))
      } yield actual == expected
    }
  }

  def forAllBounded[A: Numeric](bound: SRandom => A)(f: (Test, A) => UIO[A]): Future[Boolean] = {
    val num = implicitly[Numeric[A]]
    import num._
    val seed    = SRandom.nextLong()
    val sRandom = new SRandom(seed)
    unsafeRunToFuture {
      for {
        testRandom <- TestRandom.makeTest(DefaultData)
        _          <- testRandom.setSeed(seed)
        bounds     = List.fill(100)(num.abs(bound(sRandom)) max one)
        actual     <- UIO.foreach(bounds)(f(testRandom, _))
      } yield actual.zip(bounds).forall { case (a, n) => zero <= a && a < n }
    }
  }

  def checkFeed[A](
    generate: SRandom => A
  )(feed: (Test, List[A]) => UIO[Unit])(extract: Test => UIO[A]): Future[Boolean] = {
    val seed    = SRandom.nextLong()
    val sRandom = new SRandom(seed)
    unsafeRunToFuture {
      for {
        testRandom <- TestRandom.makeTest(DefaultData)
        _          <- testRandom.setSeed(seed)
        values     = List.fill(100)(generate(sRandom))
        _          <- feed(testRandom, values)
        results    <- UIO.foreach(List.range(0, 100))(_ => extract(testRandom))
        random     <- extract(testRandom)
      } yield results == values && random == generate(new SRandom(seed))
    }
  }

  def checkClear[A](
    generate: SRandom => A
  )(feed: (Test, List[A]) => UIO[Unit])(clear: Test => UIO[Unit])(extract: Test => UIO[A]): Future[Boolean] = {
    val seed    = SRandom.nextLong()
    val sRandom = new SRandom(seed)
    unsafeRunToFuture {
      for {
        testRandom <- TestRandom.makeTest(DefaultData)
        _          <- testRandom.setSeed(seed)
        value      = generate(sRandom)
        _          <- feed(testRandom, List(value))
        _          <- clear(testRandom)
        random     <- extract(testRandom)
      } yield random == generate(new SRandom(seed))
    }
  }

  def nextBytes(n: Int)(random: SRandom): Chunk[Byte] = {
    val arr = new Array[Byte](n)
    random.nextBytes(arr)
    Chunk.fromArray(arr)
  }
}
