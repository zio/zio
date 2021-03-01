package zio.test.refined

import eu.timepit.refined.api.Refined
import eu.timepit.refined.collection.{NonEmpty, Size}
import zio.{Chunk, NonEmptyChunk}
import zio.random.Random
import zio.test.{Gen, Sized}
import zio.test.magnolia.DeriveGen

object collection extends CollectionInstances

trait CollectionInstances {

  def nonEmptyChunkRefinedGen[R <: Random with Sized, C, T](implicit
    genT: Gen[R, T]
  ): Gen[R, Refined[NonEmptyChunk[T], NonEmpty]] =
    Gen.chunkOf1(genT).map(Refined.unsafeApply)

  def nonEmptyListRefinedGen[R <: Random with Sized, T](implicit
    genT: Gen[R, T]
  ): Gen[R, Refined[List[T], NonEmpty]] = Gen.listOf1(genT).map(Refined.unsafeApply)

  def nonEmptyVectorRefinedGen[R <: Random with Sized, T](implicit
    genT: Gen[R, T]
  ): Gen[R, Refined[Vector[T], NonEmpty]] = Gen.vectorOf1(genT).map(Refined.unsafeApply)

  def sizedChunkRefinedGen[R <: Random with Sized, T, P](implicit
    genT: Gen[R, T],
    sizeGen: Gen[Random, Int Refined P]
  ): Gen[R, Refined[Chunk[T], Size[P]]] =
    sizedChunkGen(genT, sizeGen)
      .map(r => Refined.unsafeApply(r))

  def listSizeRefinedGen[R <: Random with Sized, T, P](implicit
    genT: Gen[R, T],
    sizeGen: Gen[Random, Int Refined P]
  ): Gen[R, Refined[List[T], Size[P]]] =
    sizedChunkGen(genT, sizeGen)
      .map(r => Refined.unsafeApply(r.toList))

  def vectorSizeRefinedGen[R <: Random with Sized, T: DeriveGen, P](implicit
    genT: Gen[R, T],
    sizeGen: Gen[Random, Int Refined P]
  ): Gen[R, Refined[Vector[T], Size[P]]] =
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
    Gen.listOf1(deriveGenT.derive).map(Refined.unsafeApply)
  )

  implicit def nonEmptyVectorRefinedDeriveGen[T](implicit
    deriveGenT: DeriveGen[T]
  ): DeriveGen[Refined[Vector[T], NonEmpty]] =
    DeriveGen.instance(
      Gen.vectorOf1(deriveGenT.derive).map(Refined.unsafeApply)
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

  private[refined] def sizedChunkGen[R <: Random with Sized, T, P](
    genT: Gen[R, T],
    sizeGen: Gen[Random with Sized, Int Refined P]
  ): Gen[R, Chunk[T]] =
    sizeGen.flatMap { n =>
      Gen.chunkOfN(n.value)(genT)
    }

}
