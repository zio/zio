package zio.stream

import zio._
import zio.random.Random
import zio.test.{ Gen, GenZIO, Sized }

object ZStreamGen extends GenZIO {
  def tinyListOf[R <: Random, A](g: Gen[R, A]): Gen[R, List[A]] =
    Gen.listOfBounded(0, 5)(g)

  def tinyChunkOf[R <: Random, A](g: Gen[R, A]): Gen[R, Chunk[A]] =
    Gen.chunkOfBounded(0, 5)(g)

  def streamGen[R <: Random, A](a: Gen[R, A], max: Int): Gen[R with Sized, ZStream[Any, String, A]] =
    Gen.oneOf(failingStreamGen(a, max), pureStreamGen(a, max))

  def pureStreamGen[R <: Random, A](a: Gen[R, A], max: Int): Gen[R with Sized, ZStream[Any, Nothing, A]] =
    max match {
      case 0 => Gen.const(ZStream.empty)
      case n =>
        Gen.oneOf(
          Gen.const(ZStream.empty),
          Gen
            .int(1, n)
            .flatMap(Gen.listOfN(_)(Gen.small(Gen.chunkOfN(_)(a))))
            .map(chunks => ZStream.fromChunks(chunks: _*))
        )
    }

  def failingStreamGen[R <: Random, A](a: Gen[R, A], max: Int): Gen[R with Sized, ZStream[Any, String, A]] =
    max match {
      case 0 => Gen.const(ZStream.fromEffect(IO.fail("fail-case")))
      case _ =>
        Gen
          .int(1, max)
          .flatMap(n =>
            for {
              i  <- Gen.int(0, n - 1)
              it <- Gen.listOfN(n)(a)
            } yield ZStream.unfoldM((i, it)) {
              case (_, Nil) | (0, _) => IO.fail("fail-case")
              case (n, head :: rest) => IO.succeedNow(Some((head, (n - 1, rest))))
            }
          )
    }

  def nPulls[R, E, A](pull: ZIO[R, Option[E], A], n: Int): ZIO[R, Nothing, Iterable[Either[Option[E], A]]] =
    ZIO.foreach(1 to n)(_ => pull.either)

  val streamOfBytes = Gen.bounded(0, 5)(streamGen(Gen.anyByte, _))
  val streamOfInts  = Gen.bounded(0, 5)(streamGen(Gen.anyInt, _))

  val pureStreamOfBytes = Gen.bounded(0, 5)(pureStreamGen(Gen.anyByte, _))
  val pureStreamOfInts  = Gen.bounded(0, 5)(pureStreamGen(Gen.anyInt, _))
}
