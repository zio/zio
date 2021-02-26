package zio.test.refined

import eu.timepit.refined.api.Refined
import eu.timepit.refined.collection.{NonEmpty, Size}
import zio.{Chunk, NonEmptyChunk}
import zio.random.Random
import zio.test.{Gen, Sized}
import zio.test.magnolia.DeriveGen

object collection extends CollectionInstances

trait CollectionInstances {

  def nonEmptyChunkRefinedGen[C, T](implicit
    genT: Gen[Random with Sized, T]
  ): Gen[Random with Sized, Refined[NonEmptyChunk[T], NonEmpty]] =
    nonEmptyChunkGen(genT).map(Refined.unsafeApply)

  def nonEmptyListRefinedGen[T](implicit
    genT: Gen[Random with Sized, T]
  ): Gen[Random with Sized, Refined[List[T], NonEmpty]] =
    nonEmptyChunkGen(genT)
      .map(v => Refined.unsafeApply(v.toList))

  def nonEmptyVectorRefinedGen[T](implicit
    genT: Gen[Random with Sized, T]
  ): Gen[Random with Sized with Sized, Refined[Vector[T], NonEmpty]] =
    nonEmptyChunkGen(genT)
      .map(v => Refined.unsafeApply(v.toVector))

  def sizedChunkRefinedGen[T, P](implicit
    genT: Gen[Random with Sized, T],
    sizeGen: Gen[Random, Int Refined P]
  ): Gen[Random with Sized, Refined[Chunk[T], Size[P]]] =
    sizedChunkGen(genT, sizeGen)
      .map(r => Refined.unsafeApply(r))

  def listSizeRefinedGen[T, P](implicit
    genT: Gen[Random with Sized, T],
    sizeGen: Gen[Random, Int Refined P]
  ): Gen[Random with Sized, Refined[List[T], Size[P]]] =
    sizedChunkGen(genT, sizeGen)
      .map(r => Refined.unsafeApply(r.toList))

  def vectorSizeRefinedGen[T: DeriveGen, P](implicit
    genT: Gen[Random with Sized, T],
    sizeGen: Gen[Random, Int Refined P]
  ): Gen[Random with Sized, Refined[Vector[T], Size[P]]] =
    sizedChunkGen(genT, sizeGen)
      .map(r => Refined.unsafeApply(r.toVector))

  implicit def nonEmptyChunkRefinedDeriveGen[C, T](implicit
    deriveGenT: DeriveGen[T]
  ): DeriveGen[Refined[NonEmptyChunk[T], NonEmpty]] =
    DeriveGen.instance(
      nonEmptyChunkRefinedGen(deriveGenT.derive)
    )

  implicit def nonEmptyListRefinedDeriveGen[T](implicit
    deriveGenT: DeriveGen[T]
  ): DeriveGen[Refined[List[T], NonEmpty]] = DeriveGen.instance(
    nonEmptyChunkGen(deriveGenT.derive).map(r => Refined.unsafeApply(r.toList))
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
      sizedChunkGen(deriveGenT.derive, deriveGenSize.derive).map(r => Refined.unsafeApply(r))
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
