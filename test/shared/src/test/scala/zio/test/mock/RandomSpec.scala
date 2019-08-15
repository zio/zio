package zio.test.mock

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Random => SRandom }

import zio.{ Chunk, DefaultRuntime, UIO }
import zio.test.mock.MockRandom.{ DefaultData, Mock }
import zio.test.TestUtils.label

object RandomSpec extends DefaultRuntime {

  def run(implicit ec: ExecutionContext): List[Future[(Boolean, String)]] = List(
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

  def referentiallyTransparent(implicit ec: ExecutionContext): Future[Boolean] = {
    val mock = MockRandom.makeMock(DefaultData)
    val x    = unsafeRunToFuture(mock.flatMap[Any, Nothing, Int](_.nextInt))
    val y    = unsafeRunToFuture(mock.flatMap[Any, Nothing, Int](_.nextInt))
    x.zip(y).map { case (x, y) => x == y }
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
}
