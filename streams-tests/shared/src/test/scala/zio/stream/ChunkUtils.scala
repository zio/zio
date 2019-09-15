package zio.stream

import scala.reflect.ClassTag
import zio.Chunk
import zio.random.Random
import zio.test.{ Gen, Sized }

trait ChunkUtils {
  def chunkIxGen[R <: Random, A](a: Gen[R, A]): Gen[R with Sized, (Chunk[A], Int)] =
    for {
      n      <- Gen.int(1, 100)
      i      <- Gen.int(0, n - 1)
      vector <- Gen.vectorOfN(n)(a)
      chunk  = Chunk.fromIterable(vector)
    } yield (chunk, i)

  def chunkGen[R <: Random, A: ClassTag](a: Gen[R, A]): Gen[R with Sized, Chunk[A]] =
    Gen.oneOf(
      Gen.const(Chunk.empty),
      a.map(Chunk.succeed),
      chunkIxGen(a).map(_._1),
      Gen.suspend(for {
        arr  <- chunkGen(a)
        left <- Gen.int(0, arr.length)
      } yield arr.take(left)),
      Gen.suspend(for {
        left  <- chunkGen(a)
        right <- chunkGen(a)
      } yield left ++ right)
    )
}

object ChunkUtils extends ChunkUtils with GenUtils
