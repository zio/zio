package zio.stream

import scala.annotation.tailrec
import scala.reflect.ClassTag
import zio.random.Random
import zio.test.{ Gen, Sized }
import zio.{ Chunk, IO }

trait StreamChunkUtils extends StreamUtils {
  def streamChunkGen[R <: Random, A: ClassTag](a: Gen[R, A]): Gen[R with Sized, StreamChunk[String, A]] =
    Gen.oneOf(
      failingStreamGen(chunkGen(a)).map(StreamChunk(_)),
      pureStreamGen(chunkGen(a)).map(StreamChunk(_)),
      failingStreamEffectGen(chunkGen(a)).map(StreamEffectChunk(_)),
      pureStreamEffectGen(chunkGen(a)).map(StreamEffectChunk(_))
    )

  def succeededStreamChunkGen[R <: Random, A: ClassTag](a: Gen[R, A]): Gen[R with Sized, StreamChunk[Nothing, A]] =
    Gen.oneOf(
      pureStreamGen(chunkGen(a)).map(StreamChunk(_)),
      pureStreamEffectGen(chunkGen(a)).map(StreamEffectChunk(_))
    )
}

object StreamChunkUtils extends StreamChunkUtils with GenUtils {
  def slurp[E, A](s: StreamChunk[E, A]): IO[E, Seq[A]] =
    s.foldChunks(Chunk.empty: Chunk[A])(_ => true)((acc, el) => IO.succeed(acc ++ el))
      .map(_.toSeq)

  def foldLazyList[S, T](list: List[T], zero: S)(cont: S => Boolean)(f: (S, T) => S): S = {
    @tailrec
    def loop(xs: List[T], state: S): S = xs match {
      case head :: tail if cont(state) => loop(tail, f(state, head))
      case _                           => state
    }
    loop(list, zero)
  }

  val chunksOfInts    = succeededStreamChunkGen(intGen)
  val chunksOfStrings = succeededStreamChunkGen(stringGen)
}
