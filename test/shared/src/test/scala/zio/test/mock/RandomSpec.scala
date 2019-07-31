package zio.test.mock

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
    SAssert(forAllEqual(_.nextGaussian)(_.nextGaussian()), "MockRandom nextGaussian")
    SAssert(forAllEqual(_.nextInt)(_.nextInt()), "MockRandom nextInt")
    SAssert(forAllEqualN(_.nextInt(_))(_.nextInt(_)), "MockRandom nextIntN")
    SAssert(forAllEqual(_.nextLong)(_.nextLong()), "MockRandom nextLong")
    SAssert(forAllEqual(_.nextPrintableChar)(_.nextPrintableChar()), "MockRandom nextPrintableChar")
    SAssert(forAllEqualN(_.nextString(_))(_.nextString(_)), "MockRandom nextString")
    SAssert(forAllEqualShuffle(_.shuffle(_))(_.shuffle(_)), "MockRandom shuffle")
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
          Chunk(arr: _*)
        }
      } yield actual == expected
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
}
