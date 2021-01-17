package zio.test.refined

import eu.timepit.refined.api.Refined
import zio.test.BoolAlgebra.Or
import zio.test.Gen
import zio.test.magnolia.DeriveGen

object boolean extends BooleanInstances

trait BooleanInstances {
  implicit def orDeriveGen[T, A, B](
    implicit
    A: DeriveGen[Refined[T, A]],
    B: DeriveGen[Refined[T, B]]
  ): DeriveGen[Refined[T, A Or B]] = {
    val genA = A.derive.map(Refined.unsafeApply)
    val genB = B.derive.map(Refined.unsafeApply)
    DeriveGen.instance(Gen.oneOf(genA, genB))
  }
}
