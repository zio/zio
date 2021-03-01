package zio.test.refined

import eu.timepit.refined.api.Refined
import eu.timepit.refined.string.{EndsWith, StartsWith, Uuid}
import shapeless.Witness
import zio.random.Random
import zio.test.magnolia.DeriveGen
import zio.test.{Gen, Sized}

object string extends StringInstances

trait StringInstances {

  def endsWithStringGen[S <: String](implicit
    ws: Witness.Aux[S]
  ): Gen[Random with Sized, Refined[String, EndsWith[S]]] =
    Gen.anyString.map(value => Refined.unsafeApply(value + ws.value))

  def startsWithStringGen[S <: String](implicit
    ws: Witness.Aux[S]
  ): Gen[Random with Sized, Refined[String, StartsWith[S]]] =
    Gen.anyString.map(value => Refined.unsafeApply(ws.value + value))

  val uuidStringGen: Gen[Random, Refined[String, Uuid]] = Gen.anyUUID.map(value => Refined.unsafeApply(value.toString))

  implicit def endsWithStringDeriveGen[S <: String](implicit
    ws: Witness.Aux[S]
  ): DeriveGen[Refined[String, EndsWith[S]]] =
    DeriveGen.instance(endsWithStringGen(ws))

  implicit def startsWithStringDeriveGen[S <: String](implicit
    ws: Witness.Aux[S]
  ): DeriveGen[Refined[String, StartsWith[S]]] =
    DeriveGen.instance(startsWithStringGen(ws))

  implicit val uuidStringDeriveGen: DeriveGen[Refined[String, Uuid]] =
    DeriveGen.instance(uuidStringGen)

}
