package zio.test.refined

import eu.timepit.refined.api.Refined
import eu.timepit.refined.collection.{NonEmpty, Size}
import zio.test.Gen
import zio.test.magnolia.DeriveGen
import zio.{Chunk, NonEmptyChunk}

object collection extends CollectionInstances

trait CollectionInstances {
  implicit def nonEmptyChunkRefinedDeriveGen[C, T](implicit
    deriveGenT: DeriveGen[T]
  ): DeriveGen[Refined[NonEmptyChunk[T], NonEmpty]] =
    DeriveGen.instance(
      nonEmptyChunkDeriveGen(deriveGenT).derive.map(Refined.unsafeApply)
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
      nonEmptyChunkDeriveGen(deriveGenT).derive
        .map(v => Refined.unsafeApply(v.toVector))
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
      sizedChunkDeriveGen(deriveGenT, deriveGenSize).derive
        .map(r => Refined.unsafeApply(r.toList))
    )

  implicit def vectorSizeRefinedDeriveGen[T: DeriveGen, P](implicit
    deriveGenT: DeriveGen[T],
    deriveGenSize: DeriveGen[Int Refined P]
  ): DeriveGen[Refined[Vector[T], Size[P]]] =
    DeriveGen.instance(
      sizedChunkDeriveGen(deriveGenT, deriveGenSize).derive
        .map(r => Refined.unsafeApply(r.toVector))
    )

  private def nonEmptyChunkDeriveGen[T](implicit
    arbT: DeriveGen[T]
  ): DeriveGen[NonEmptyChunk[T]] =
    DeriveGen.instance(Gen.chunkOf1(arbT.derive))

  private def sizedChunkDeriveGen[T, P](implicit
    deriveGenT: DeriveGen[T],
    deriveGenSize: DeriveGen[Int Refined P]
  ): DeriveGen[Chunk[T]] =
    DeriveGen.instance(deriveGenSize.derive.flatMap { n =>
      Gen.chunkOfN(n.value)(deriveGenT.derive)
    })
}
