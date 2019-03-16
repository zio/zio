package scalaz.zio.stream

import org.scalacheck.{ Arbitrary, Gen }
import scala.reflect.ClassTag
import scalaz.zio.Chunk

object ArbitraryChunk {

  implicit def arbChunk[T: ClassTag](implicit a: Arbitrary[T]): Arbitrary[Chunk[T]] =
    Arbitrary {
      Gen.oneOf(
        Gen.const(Chunk.empty),
        Arbitrary.arbitrary[T].map(Chunk.succeed),
        Arbitrary.arbitrary[Seq[T]].map(seqT => Chunk.fromArray(seqT.toArray)),
        Gen.lzy {
          for {
            arr  <- arbChunk.arbitrary
            left <- Gen.choose[Int](0, arr.length)
          } yield arr.take(left)
        },
        Gen.lzy {
          for {
            left  <- arbChunk.arbitrary
            right <- arbChunk.arbitrary
          } yield left ++ right
        }
      )
    }
}
