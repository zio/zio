package zio.test.refined

import eu.timepit.refined.api.Refined
import eu.timepit.refined.boolean.Or
import zio.random
import zio.random.Random
import zio.test.{Gen, Sized}
import zio.test.magnolia.DeriveGen

object boolean extends BooleanInstances

trait BooleanInstances {
  implicit def orDeriveGen[T, A, B](implicit
    raGen: DeriveGen[Refined[T, A]],
    rbGen: DeriveGen[Refined[T, B]]
  ): DeriveGen[Refined[T, A Or B]] = {
    val genA: Gen[random.Random with Sized, T] = raGen.derive.map(_.value)
    val genB: Gen[random.Random with Sized, T] = rbGen.derive.map(_.value)
    DeriveGen.instance(orGen(genA, genB))
  }

  def orGen[T, A, B](implicit
    genA: Gen[random.Random with Sized, T],
    genB: Gen[random.Random with Sized, T]
  ): Gen[Random with Sized, Refined[T, A Or B]] = Gen.oneOf[Random with Sized, T](genA, genB).map(Refined.unsafeApply)
}
