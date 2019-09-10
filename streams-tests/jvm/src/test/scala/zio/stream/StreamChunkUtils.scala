package zio.stream

import scala.reflect.ClassTag
import zio.random.Random
import zio.test.{ Gen, Sized }
import zio.{ Chunk, IO }

trait StreamChunkUtils extends StreamUtils {
  def streamChunkGen[R <: Random, A: ClassTag](a: Gen[R, A]): Gen[R with Sized, StreamChunk[String, A]] =
    Gen.oneOf(
      impureStreamGen(chunkGen(a)).map(StreamChunk(_)),
      pureStreamGen(chunkGen(a)).map(StreamChunk(_))
    )

  def succeededStreamChunkGen[R <: Random, A: ClassTag](a: Gen[R, A]): Gen[R with Sized, StreamChunk[Nothing, A]] =
    pureStreamGen(chunkGen(a)).map(StreamChunk(_))

  def slurp[E, A](s: StreamChunk[E, A]): IO[E, Seq[A]] =
    s.foldChunks(Chunk.empty: Chunk[A])(_ => true)((acc, el) => IO.succeed(acc ++ el))
      .map(_.toSeq)
}

object StreamChunkUtils extends StreamChunkUtils with GenUtils
