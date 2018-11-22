package scalaz.zio.stream

import org.scalacheck.{Arbitrary, Gen}

import scala.reflect.ClassTag

object ArbitraryChunk {

  implicit def arbChunk[T: ClassTag](implicit a: Arbitrary[T]): Arbitrary[Chunk[T]] =
    Arbitrary {

      val genEmpty = Gen.const(Chunk.empty)
      val genSingleton = for (e <- Arbitrary.arbitrary[T]) yield Chunk.point(e)
      val genArr: Gen[Chunk[T]] = for (e <- Arbitrary.arbitrary[Seq[T]]) yield Chunk.fromArray(e.toArray)
      val genSimple = Gen.oneOf(genEmpty, genSingleton, genArr)

      val genSlice = for {
        arr <- genSimple
        left <- Gen.choose[Int](0, arr.length)
      } yield arr.take(left)

      val genConcat: Gen[Chunk[T]] = for {
        left <- genSimple
        right <- genSimple
      } yield left ++ right

      Gen.oneOf(genEmpty, genSingleton, genSlice, genArr, genConcat)
    }
}
