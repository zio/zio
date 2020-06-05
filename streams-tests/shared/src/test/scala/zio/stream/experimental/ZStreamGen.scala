package zio.stream.experimental

import zio.Chunk
import zio.random.Random
import zio.test.{ Gen, GenZIO }

object ZStreamGen extends GenZIO {

  def tinyChunkOf[R <: Random, A](g: Gen[R, A]): Gen[R, Chunk[A]] =
    Gen.chunkOfBounded(0, 5)(g)
}
