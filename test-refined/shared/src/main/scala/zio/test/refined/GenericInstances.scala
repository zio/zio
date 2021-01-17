package zio.test.refined

import eu.timepit.refined.api.Refined
import eu.timepit.refined.generic.Equal
import shapeless.Witness
import zio.test.Gen
import zio.test.magnolia.DeriveGen

object generic extends GenericInstances

trait GenericInstances {
  implicit def equalArbitrary[T, U <: T](
    implicit
    wu: Witness.Aux[U]
  ): DeriveGen[Refined[T, Equal[U]]] =
    DeriveGen.instance(Gen.const(wu.value).map(Refined.unsafeApply))
}
