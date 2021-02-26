package zio.test.refined

import eu.timepit.refined.api.Refined
import eu.timepit.refined.collection.{NonEmpty, Size}
import zio.{Chunk, NonEmptyChunk}
import zio.random.Random
import zio.test.{Gen, Sized}
import zio.test.magnolia.DeriveGen

object collection extends CollectionInstances

trait CollectionInstances {
  implicit def nonEmptyChunkRefinedDeriveGen[C, T](implicit
    deriveGenT: DeriveGen[T]
  ): DeriveGen[Refined[NonEmptyChunk[T], NonEmpty]] =
    DeriveGen.instance(
      nonEmptyChunkRefinedGen(deriveGenT.derive)
    )

  implicit def nonEmptyListRefinedDeriveGen[T](implicit
    deriveGenT: DeriveGen[T]
  ): DeriveGen[Refined[List[T], NonEmpty]] =
    DeriveGen.instance(
      nonEmptyChunkDeriveGen(deriveGenT).derive
        .map(v => Refined.unsafeApply(v.toList))
    )

  implicit def nonEmptyVectorRefinedDeriveGen[T](implicit
    deriveGenT: DeriveGen[T]
  ): DeriveGen[Refined[Vector[T], NonEmpty]] =
    DeriveGen.instance(
      nonEmptyChunkGen(deriveGenT.derive).map(r => Refined.unsafeApply(r.toVector))
    )

  implicit def sizedChunkRefinedDeriveGen[T, P](implicit
    deriveGenT: DeriveGen[T],
    deriveGenSize: DeriveGen[Int Refined P]
  ): DeriveGen[Refined[Chunk[T], Size[P]]] =
    DeriveGen.instance(
      sizedChunkDeriveGen(deriveGenT, deriveGenSize).derive
        .map(r => Refined.unsafeApply(r))
    )

  implicit def listSizeRefinedDeriveGen[T, P](implicit
    deriveGenT: DeriveGen[T],
    deriveGenSize: DeriveGen[Int Refined P]
  ): DeriveGen[Refined[List[T], Size[P]]] =
    DeriveGen.instance(
      sizedChunkGen(deriveGenT.derive, deriveGenSize.derive).map(r => Refined.unsafeApply(r.toList))
    )

  implicit def vectorSizeRefinedDeriveGen[T: DeriveGen, P](implicit
    deriveGenT: DeriveGen[T],
    deriveGenSize: DeriveGen[Int Refined P]
  ): DeriveGen[Refined[Vector[T], Size[P]]] =
    DeriveGen.instance(
      sizedChunkGen(deriveGenT.derive, deriveGenSize.derive).map(r => Refined.unsafeApply(r.toVector))
    )

  private[refined] def nonEmptyChunkGen[T](
    arbT: Gen[Random with Sized, T]
  ): Gen[Random with Sized, NonEmptyChunk[T]] =
    Gen.chunkOf1(arbT)

  private[refined] def sizedChunkGen[T, P](
    genT: Gen[Random with Sized, T],
    sizeGen: Gen[Random with Sized, Int Refined P]
  ): Gen[Random with Sized, Chunk[T]] =
    sizeGen.flatMap { n =>
      Gen.chunkOfN(n.value)(genT)
    }

}
