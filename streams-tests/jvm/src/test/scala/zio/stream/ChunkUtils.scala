package zio.stream

import scala.reflect.ClassTag
import zio.Chunk
import zio.random.Random
import zio.test.{ Gen, Sized }

trait ChunkUtils {
  def chunkGen[R <: Random, A: ClassTag](a: Gen[R, A]): Gen[R with Sized, Chunk[A]] =
    Gen.oneOf(
      Gen.const(Chunk.empty),
      a.map(Chunk.succeed),
      Gen.listOf(a).map(as => Chunk.fromArray(as.toArray)),
      Gen.suspend(for {
        arr  <- chunkGen(a)
        left <- Gen.int(0, arr.length)
      } yield arr.take(left)),
      Gen.suspend(for {
        left  <- chunkGen(a)
        right <- chunkGen(a)
      } yield left ++ right)
    )

  val chunkIxGen: Gen[Random with Sized, (Chunk[Int], Int)] = for {
    chunk <- chunkGen(Gen.anyInt).filter(_.length > 0)
    len   <- Gen.int(0, chunk.length - 1)
  } yield (chunk, len)
}

object ChunkUtils extends ChunkUtils with GenUtils
