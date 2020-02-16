package zio.stream

import scala.reflect.ClassTag

import zio.Chunk
import zio.random.Random
import zio.test.{ Gen, Sized }

trait ChunkUtils {
  def chunkWithIndex[R <: Random, A](a: Gen[R, A]): Gen[R with Sized, (Chunk[A], Int)] =
    for {
      n      <- Gen.int(1, 100)
      i      <- Gen.int(0, n - 1)
      vector <- Gen.vectorOfN(n)(a)
      chunk  = Chunk.fromIterable(vector)
    } yield (chunk, i)

  def chunkGen[R <: Random, A: ClassTag](a: Gen[R, A], max: Int): Gen[R with Sized, Chunk[A]] =
    max match {
      case 0 => Gen.const(Chunk.empty)
      case 1 => Gen.oneOf(Gen.const(Chunk.empty), a.map(Chunk.succeed))
      case _ =>
        Gen.oneOf(
          Gen.const(Chunk.empty),
          a.map(Chunk.succeed),
          chunkWithIndex(a).map(_._1),
          Gen.suspend(for {
            arr  <- chunkGen(a, max)
            left <- Gen.int(0, arr.length)
          } yield arr.take(left)),
          Gen.suspend(for {
            left  <- chunkGen(a, max / 2)
            right <- chunkGen(a, max / 2)
          } yield left ++ right)
        )
    }

  def tinyChunks[R <: Random, A: ClassTag](a: Gen[R, A]): Gen[R with Sized, Chunk[A]] =
    for {
      n  <- Gen.int(0, 3)
      xs <- Gen.vectorOfN(n)(a)
    } yield Chunk.fromIterable(xs)

  def smallChunks[R <: Random, A: ClassTag](a: Gen[R, A]): Gen[R with Sized, Chunk[A]] = Gen.small(chunkGen(a, _))

  def mediumChunks[R <: Random, A: ClassTag](a: Gen[R, A]): Gen[R with Sized, Chunk[A]] = Gen.medium(chunkGen(a, _))

  def largeChunks[R <: Random, A: ClassTag](a: Gen[R, A]): Gen[R with Sized, Chunk[A]] = Gen.large(chunkGen(a, _))
}

object ChunkUtils extends ChunkUtils with GenUtils
