package zio.test.mock

import java.util.{ Random => JRandom }

import scala.Predef.{ assert => SAssert, _ }
import scala.util.{ Random => SRandom }

import zio.{ Chunk, DefaultRuntime, UIO }
import zio.test.mock.MockRandom.{ Data, Mock }

object RandomSpec extends DefaultRuntime {

  def run(): Unit = {
    SAssert(referentiallyTransparent, "MockRandom referential transparency")
    SAssert(forAllEqual(_.nextBoolean)(_.nextBoolean()), "MockRandom nextBoolean")
    SAssert(forAllEqualBytes, "MockRandom nextBytes")
    SAssert(forAllEqual(_.nextDouble)(_.nextDouble()), "MockRandom nextDouble")
    SAssert(forAllEqual(_.nextFloat)(_.nextFloat()), "MockRandom nextFloat")
    SAssert(forAllEqualGaussian, "MockRandom nextGaussian")
    SAssert(forAllEqual(_.nextInt)(_.nextInt()), "MockRandom nextInt")
    SAssert(forAllEqualN(_.nextInt(_))(_.nextInt(_)), "MockRandom bounded nextInt")
    SAssert(forAllEqual(_.nextLong)(_.nextLong()), "MockRandom nextLong")
    SAssert(forAllEqualLong, "MockRandom bounded nextLong")
    SAssert(forAllEqual(_.nextPrintableChar)(_.nextPrintableChar()), "MockRandom nextPrintableChar")
    SAssert(forAllEqualN(_.nextString(_))(_.nextString(_)), "MockRandom nextString")
    SAssert(forAllEqualShuffle(_.shuffle(_))(_.shuffle(_)), "MockRandom shuffle")

    SAssert(forAllBounded(_.nextInt)(_.nextInt(_)), "MockRandom bounded nextInt generates values within the bounds")
    SAssert(forAllBounded(_.nextLong)(_.nextLong(_)), "MockRandom bounded nextLong generates values within the bounds")
  }

  def referentiallyTransparent: Boolean = {
    val seed = SRandom.nextLong()
    val mock = MockRandom.makeMock(Data(seed))
    val x    = unsafeRun(mock.flatMap[Any, Nothing, Int](_.nextInt))
    val y    = unsafeRun(mock.flatMap[Any, Nothing, Int](_.nextInt))
    x == y
  }

  def forAllEqual[A](f: Mock => UIO[A])(g: SRandom => A): Boolean = {
    val seed    = SRandom.nextLong()
    val sRandom = new SRandom(seed)
    unsafeRun {
      for {
        mockRandom <- MockRandom.makeMock(Data(seed))
        actual     <- UIO.foreach(List.fill(100)(()))(_ => f(mockRandom))
        expected   = List.fill(100)(g(sRandom))
      } yield actual == expected
    }
  }

  def forAllEqualBytes: Boolean = {
    val seed    = SRandom.nextLong()
    val sRandom = new SRandom(seed)
    unsafeRun {
      for {
        mockRandom <- MockRandom.makeMock(Data(seed))
        actual     <- UIO.foreach(List.range(0, 100))(mockRandom.nextBytes(_))
        expected = List.range(0, 100).map(new Array[Byte](_)).map { arr =>
          sRandom.nextBytes(arr)
          Chunk.fromArray(arr)
        }
      } yield actual == expected
    }
  }

  def forAllEqualGaussian: Boolean = {
    val seed    = SRandom.nextLong()
    val sRandom = new SRandom(seed)
    unsafeRun {
      for {
        mockRandom <- MockRandom.makeMock(Data(seed))
        actual     <- UIO.foreach(List.fill(100)(()))(_ => mockRandom.nextGaussian)
        expected   = List.fill(100)(sRandom.nextGaussian)
      } yield actual.zip(expected).forall { case (x, y) => math.abs(x - y) < 0.01 }
    }
  }

  def forAllEqualN[A](f: (Mock, Int) => UIO[A])(g: (SRandom, Int) => A): Boolean = {
    val seed    = SRandom.nextLong()
    val sRandom = new SRandom(seed)
    unsafeRun {
      for {
        mockRandom <- MockRandom.makeMock(Data(seed))
        actual     <- UIO.foreach(1 to 100)(f(mockRandom, _))
        expected   = (1 to 100).map(g(sRandom, _))
      } yield actual == expected
    }
  }

  def forAllEqualLong: Boolean = {
    val jRandom = new JRandom
    val seed    = jRandom.nextLong()
    unsafeRun {
      for {
        mockRandom <- MockRandom.makeMock(Data(seed))
        bounds     = List.fill(100)(math.abs(jRandom.nextLong()) max 1)
        actual     <- UIO.foreach(bounds)(mockRandom.nextLong(_))
        _          = jRandom.setSeed(seed)
        expected   = bounds.map(jRandom.longs(0, _).findFirst.getAsLong)
      } yield actual == expected
    }
  }

  def forAllEqualShuffle(f: (Mock, List[Int]) => UIO[List[Int]])(g: (SRandom, List[Int]) => List[Int]): Boolean = {
    val seed    = SRandom.nextLong()
    val sRandom = new SRandom(seed)
    unsafeRun {
      for {
        mockRandom <- MockRandom.makeMock(Data(seed))
        actual     <- UIO.foreach(List.range(0, 100).map(List.range(0, _)))(f(mockRandom, _))
        expected   = List.range(0, 100).map(List.range(0, _)).map(g(sRandom, _))
      } yield actual == expected
    }
  }

  def forAllBounded[A: Numeric](bound: SRandom => A)(f: (Mock, A) => UIO[A]): Boolean = {
    val num = implicitly[Numeric[A]]
    import num._
    val seed    = SRandom.nextLong()
    val sRandom = new SRandom(seed)
    unsafeRun {
      for {
        mockRandom <- MockRandom.makeMock(Data(seed))
        bounds     = List.fill(100)(num.abs(bound(sRandom)) max one)
        actual     <- UIO.foreach(bounds)(f(mockRandom, _))
      } yield actual.zip(bounds).forall { case (a, n) => zero <= a && a < n }
    }
  }
}
