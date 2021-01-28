package zio.test.refined

import eu.timepit.refined.api.Refined
import eu.timepit.refined.string.{EndsWith, StartsWith, Uuid}
import shapeless.Witness
import zio.test.Gen
import zio.test.magnolia.DeriveGen

object string extends StringInstances

trait StringInstances {
  implicit def endsWithStringDeriveGen[S <: String](implicit
    ws: Witness.Aux[S]
  ): DeriveGen[Refined[String, EndsWith[S]]] =
    DeriveGen.instance(
      Gen.anyString.map(value => Refined.unsafeApply(value + ws.value))
    )

  implicit def startsWithStringDeriveGen[S <: String](implicit
    ws: Witness.Aux[S]
  ): DeriveGen[Refined[String, StartsWith[S]]] =
    DeriveGen.instance(
      Gen.anyString.map(value => Refined.unsafeApply(ws.value + value))
    )

  implicit val uuidStringDeriveGen: DeriveGen[Refined[String, Uuid]] =
    DeriveGen.instance(
      Gen.anyUUID.map(value => Refined.unsafeApply(value.toString))
    )
}
