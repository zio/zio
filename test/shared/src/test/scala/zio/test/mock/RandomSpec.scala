package zio.test.mock

import scala.concurrent.Future
import scala.util.{ Random => SRandom }

import zio.{ Chunk, UIO }
import zio.test.mock.MockRandom.{ DefaultData, Mock }
import zio.test.Async
import zio.test.TestUtils.label
import zio.test.ZIOBaseSpec

object RandomSpec extends ZIOBaseSpec {

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
    val mock = MockRandom.makeMock(DefaultData)
    val x    = unsafeRun(mock.flatMap[Any, Nothing, Int](_.nextInt))
    val y    = unsafeRun(mock.flatMap[Any, Nothing, Int](_.nextInt))
    Future.successful(x == y)
  }

  def forAllEqual[A](f: Mock => UIO[A])(g: SRandom => A): Future[Boolean] = {
    val seed    = SRandom.nextLong()
    val sRandom = new SRandom(seed)
    unsafeRunToFuture {
      for {
        mockRandom <- MockRandom.makeMock(DefaultData)
        _          <- mockRandom.setSeed(seed)
        actual     <- UIO.foreach(List.fill(100)(()))(_ => f(mockRandom))
        expected   = List.fill(100)(g(sRandom))
      } yield actual == expected
    }
  }

  def forAllEqualBytes: Future[Boolean] = {
    val seed    = SRandom.nextLong()
    val sRandom = new SRandom(seed)
    unsafeRunToFuture {
      for {
        mockRandom <- MockRandom.makeMock(DefaultData)
        _          <- mockRandom.setSeed(seed)
        actual     <- UIO.foreach(List.range(0, 100))(mockRandom.nextBytes(_))
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
        mockRandom <- MockRandom.makeMock(DefaultData)
        _          <- mockRandom.setSeed(seed)
        actual     <- UIO.foreach(List.fill(100)(()))(_ => mockRandom.nextGaussian)
        expected   = List.fill(100)(sRandom.nextGaussian)
      } yield actual.zip(expected).forall { case (x, y) => math.abs(x - y) < 0.01 }
    }
  }

  def forAllEqualN[A](f: (Mock, Int) => UIO[A])(g: (SRandom, Int) => A): Future[Boolean] = {
    val seed    = SRandom.nextLong()
    val sRandom = new SRandom(seed)
    unsafeRunToFuture {
      for {
        mockRandom <- MockRandom.makeMock(DefaultData)
        _          <- mockRandom.setSeed(seed)
        actual     <- UIO.foreach(1 to 100)(f(mockRandom, _))
        expected   = (1 to 100).map(g(sRandom, _))
      } yield actual == expected
    }
  }

  def forAllEqualShuffle(
    f: (Mock, List[Int]) => UIO[List[Int]]
  )(g: (SRandom, List[Int]) => List[Int]): Future[Boolean] = {
    val seed    = SRandom.nextLong()
    val sRandom = new SRandom(seed)
    unsafeRunToFuture {
      for {
        mockRandom <- MockRandom.makeMock(DefaultData)
        _          <- mockRandom.setSeed(seed)
        actual     <- UIO.foreach(List.range(0, 100).map(List.range(0, _)))(f(mockRandom, _))
        expected   = List.range(0, 100).map(List.range(0, _)).map(g(sRandom, _))
      } yield actual == expected
    }
  }

  def forAllBounded[A: Numeric](bound: SRandom => A)(f: (Mock, A) => UIO[A]): Future[Boolean] = {
    val num = implicitly[Numeric[A]]
    import num._
    val seed    = SRandom.nextLong()
    val sRandom = new SRandom(seed)
    unsafeRunToFuture {
      for {
        mockRandom <- MockRandom.makeMock(DefaultData)
        _          <- mockRandom.setSeed(seed)
        bounds     = List.fill(100)(num.abs(bound(sRandom)) max one)
        actual     <- UIO.foreach(bounds)(f(mockRandom, _))
      } yield actual.zip(bounds).forall { case (a, n) => zero <= a && a < n }
    }
  }

  def checkFeed[A](
    generate: SRandom => A
  )(feed: (Mock, List[A]) => UIO[Unit])(extract: Mock => UIO[A]): Future[Boolean] = {
    val seed    = SRandom.nextLong()
    val sRandom = new SRandom(seed)
    unsafeRunToFuture {
      for {
        mockRandom <- MockRandom.makeMock(DefaultData)
        _          <- mockRandom.setSeed(seed)
        values     = List.fill(100)(generate(sRandom))
        _          <- feed(mockRandom, values)
        results    <- UIO.foreach(List.range(0, 100))(_ => extract(mockRandom))
        random     <- extract(mockRandom)
      } yield results == values && random == generate(new SRandom(seed))
    }
  }

  def checkClear[A](
    generate: SRandom => A
  )(feed: (Mock, List[A]) => UIO[Unit])(clear: Mock => UIO[Unit])(extract: Mock => UIO[A]): Future[Boolean] = {
    val seed    = SRandom.nextLong()
    val sRandom = new SRandom(seed)
    unsafeRunToFuture {
      for {
        mockRandom <- MockRandom.makeMock(DefaultData)
        _          <- mockRandom.setSeed(seed)
        value      = generate(sRandom)
        _          <- feed(mockRandom, List(value))
        _          <- clear(mockRandom)
        random     <- extract(mockRandom)
      } yield random == generate(new SRandom(seed))
    }
  }

  def nextBytes(n: Int)(random: SRandom): Chunk[Byte] = {
    val arr = new Array[Byte](n)
    random.nextBytes(arr)
    Chunk.fromArray(arr)
  }
}
