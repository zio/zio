package zio.test.refined

import eu.timepit.refined.api.Refined
import eu.timepit.refined.collection.{NonEmpty, Size}
import zio.random.Random
import zio.test.Gen.{listOfN, vectorOfN}
import zio.test.magnolia.DeriveGen
import zio.test.{Gen, Sized}
import zio.{Chunk, NonEmptyChunk}

object collection extends CollectionInstances

trait CollectionInstances {

  def nonEmptyChunkRefinedGen[R <: Random with Sized, T](implicit
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
    sizeGen: Gen[R, Int Refined P]
  ): Gen[R, Refined[Chunk[T], Size[P]]] =
    sizeGen.flatMap { n =>
      Gen.chunkOfN(n.value)(genT)
    }.map(r => Refined.unsafeApply(r))

  def listSizeRefinedGen[R <: Random with Sized, T, P](implicit
    genT: Gen[R, T],
    sizeGen: Gen[R, Int Refined P]
  ): Gen[R, Refined[List[T], Size[P]]] =
    sizeGen.flatMap { n =>
      listOfN(n.value)(genT)
    }.map(Refined.unsafeApply)

  def vectorSizeRefinedGen[R <: Random with Sized, T, P](implicit
    genT: Gen[R, T],
    sizeGen: Gen[R, Int Refined P]
  ): Gen[R, Refined[Vector[T], Size[P]]] = sizeGen.flatMap { n =>
    vectorOfN(n.value)(genT)
  }.map(Refined.unsafeApply)

  implicit def nonEmptyChunkRefinedDeriveGen[C, T](implicit
    deriveGenT: DeriveGen[T]
  ): DeriveGen[Refined[NonEmptyChunk[T], NonEmpty]] =
    DeriveGen.instance(
      nonEmptyChunkRefinedGen(deriveGenT.derive)
    )

  implicit def nonEmptyListRefinedDeriveGen[T](implicit
    deriveGenT: DeriveGen[T]
  ): DeriveGen[Refined[List[T], NonEmpty]] = DeriveGen.instance(
    nonEmptyListRefinedGen(deriveGenT.derive)
  )

  implicit def nonEmptyVectorRefinedDeriveGen[T](implicit
    deriveGenT: DeriveGen[T]
  ): DeriveGen[Refined[Vector[T], NonEmpty]] =
    DeriveGen.instance(
      nonEmptyVectorRefinedGen(deriveGenT.derive)
    )

  implicit def sizedChunkRefinedDeriveGen[T, P](implicit
    deriveGenT: DeriveGen[T],
    deriveGenSize: DeriveGen[Int Refined P]
  ): DeriveGen[Refined[Chunk[T], Size[P]]] =
    DeriveGen.instance(
      sizedChunkRefinedGen(deriveGenT.derive, deriveGenSize.derive)
    )

  implicit def listSizeRefinedDeriveGen[T, P](implicit
    deriveGenT: DeriveGen[T],
    deriveGenSize: DeriveGen[Int Refined P]
  ): DeriveGen[Refined[List[T], Size[P]]] =
    DeriveGen.instance(
      listSizeRefinedGen(deriveGenT.derive, deriveGenSize.derive)
    )

  implicit def vectorSizeRefinedDeriveGen[T, P](implicit
    deriveGenT: DeriveGen[T],
    deriveGenSize: DeriveGen[Int Refined P]
  ): DeriveGen[Refined[Vector[T], Size[P]]] =
    DeriveGen.instance(
      vectorSizeRefinedGen(deriveGenT.derive, deriveGenSize.derive)
    )
}
