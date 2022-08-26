package zio.stream

import zio._
import zio.test.{Gen, GenZIO}

object ZStreamGen extends GenZIO {
  def tinyListOf[R, A](g: Gen[R, A]): Gen[R, List[A]] =
    Gen.listOfBounded(0, 5)(g)

  def tinyChunkOf[R, A](g: Gen[R, A]): Gen[R, Chunk[A]] =
    Gen.chunkOfBounded(0, 5)(g)

  def streamGen[R, A](a: Gen[R, A], max: Int): Gen[R, ZStream[Any, String, A]] =
    Gen.oneOf(failingStreamGen(a, max), pureStreamGen(a, max))

  def pureStreamGen[R, A](a: Gen[R, A], max: Int): Gen[R, ZStream[Any, Nothing, A]] =
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

  def failingStreamGen[R, A](a: Gen[R, A], max: Int): Gen[R, ZStream[Any, String, A]] =
    max match {
      case 0 => Gen.const(ZStream.fromZIO(ZIO.fail("fail-case")))
      case _ =>
        Gen
          .int(1, max)
          .flatMap(n =>
            for {
              i  <- Gen.int(0, n - 1)
              it <- Gen.listOfN(n)(a)
            } yield ZStream.unfoldZIO((i, it)) {
              case (_, Nil) | (0, _) => ZIO.fail("fail-case")
              case (n, head :: rest) => ZIO.succeedNow(Some((head, (n - 1, rest))))
            }
          )
    }

  def nPulls[R, E, A](pull: ZIO[R, Option[E], A], n: Int): ZIO[R, Nothing, Iterable[Either[Option[E], A]]] =
    ZIO.foreach(1 to n)(_ => pull.either)

  val streamOfInts: Gen[Any, ZStream[Any, String, Int]] =
    Gen.bounded(0, 5)(streamGen(Gen.int, _)).zipWith(Gen.function(Gen.boolean))(injectEmptyChunks)

  val pureStreamOfInts: Gen[Any, ZStream[Any, Nothing, Int]] =
    Gen.bounded(0, 5)(pureStreamGen(Gen.int, _)).zipWith(Gen.function(Gen.boolean))(injectEmptyChunks)

  def injectEmptyChunks[R, E, A](stream: ZStream[R, E, A], predicate: Chunk[A] => Boolean): ZStream[R, E, A] =
    stream.mapChunks { chunk =>
      if (predicate(chunk)) chunk
      else Chunk.empty
    }

  def splitChunks[A](chunks: Chunk[Chunk[A]]): Gen[Any, Chunk[Chunk[A]]] = {

    def split(chunks: Chunk[Chunk[A]]): Gen[Any, Chunk[Chunk[A]]] =
      for {
        i     <- Gen.int(0, chunks.length - 1 max 0)
        chunk  = chunks(i)
        j     <- Gen.int(0, chunk.length - 1 max 0)
        (l, r) = chunk.splitAt(j)
        split  = chunks.take(i) ++ Chunk(l) ++ Chunk(r) ++ chunks.drop(i + 1)
      } yield split

    Gen.oneOf(
      Gen.const(chunks),
      split(chunks).flatMap(splitChunks)
    )
  }
}
