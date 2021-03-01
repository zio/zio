package zio.test.refined

import eu.timepit.refined.api.Refined
import eu.timepit.refined.generic.Equal
import shapeless.Witness
import zio.random.Random
import zio.test.magnolia.DeriveGen
import zio.test.{Gen, Sized}

object generic extends GenericInstances

trait GenericInstances {

  def equalArbitraryGen[T, U <: T](implicit wu: Witness.Aux[U]): Gen[Random with Sized, Refined[T, Equal[U]]] =
    Gen.const(wu.value).map(Refined.unsafeApply)

  implicit def equalArbitrary[T, U <: T](implicit
    wu: Witness.Aux[U]
  ): DeriveGen[Refined[T, Equal[U]]] =
    DeriveGen.instance(equalArbitraryGen(wu))
}
