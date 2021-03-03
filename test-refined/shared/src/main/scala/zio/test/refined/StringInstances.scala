package zio.test.refined

import eu.timepit.refined.api.Refined
import eu.timepit.refined.string.{EndsWith, StartsWith, Uuid}
import shapeless.Witness
import zio.random.Random
import zio.test.magnolia.DeriveGen
import zio.test.{Gen, Sized}

object string extends StringInstances

trait StringInstances {

  def endsWithStringGen[R <: Random with Sized, S <: String](implicit
    ws: Witness.Aux[S],
    charGen: Gen[R, Char]
  ): Gen[R, Refined[String, EndsWith[S]]] = Gen.string(charGen).map(v => Refined.unsafeApply(v + ws.value))

  def endsWithString1Gen[R <: Random with Sized, S <: String](implicit
    ws: Witness.Aux[S],
    charGen: Gen[R, Char]
  ): Gen[R, Refined[String, EndsWith[S]]] =
    Gen.string1(charGen).map(v => Refined.unsafeApply(v + ws.value)) //Only String can call `+` since scala 2.13.0.

  def endsWithStringNGen[R <: Random with Sized, S <: String, P](implicit
    ws: Witness.Aux[S],
    charGen: Gen[R, Char],
    sizeGen: Gen[R, Int Refined P]
  ): Gen[R, Refined[String, EndsWith[S]]] =
    sizeGen.flatMap(s => Gen.stringN(s.value)(charGen).map(v => Refined.unsafeApply(v + ws.value)))

  def startsWithStringGen[R <: Random with Sized, S <: String](implicit
    ws: Witness.Aux[S],
    charGen: Gen[R, Char]
  ): Gen[R, Refined[String, StartsWith[S]]] = Gen.string(charGen).map(v => Refined.unsafeApply(ws.value + v))

  def startsWithString1Gen[R <: Random with Sized, S <: String](implicit
    ws: Witness.Aux[S],
    charGen: Gen[R, Char]
  ): Gen[R, Refined[String, StartsWith[S]]] = Gen.string1(charGen).map(v => Refined.unsafeApply(ws.value + v))

  def startsWithStringNGen[R <: Random with Sized, S <: String, P](implicit
    ws: Witness.Aux[S],
    charGen: Gen[R, Char],
    sizeGen: Gen[R, Int Refined P]
  ): Gen[R, Refined[String, StartsWith[S]]] =
    sizeGen.flatMap(s => Gen.stringN(s.value)(charGen).map(v => Refined.unsafeApply(ws.value + v)))

  val uuidStringGen: Gen[Random, Refined[String, Uuid]] = Gen.anyUUID.map(value => Refined.unsafeApply(value.toString))

  implicit val uuidStringDeriveGen: DeriveGen[Refined[String, Uuid]] =
    DeriveGen.instance(uuidStringGen)

  implicit def endsWithStringDeriveGen[S <: String](implicit
    ws: Witness.Aux[S],
    deriveGenT: DeriveGen[Char]
  ): DeriveGen[Refined[String, EndsWith[S]]] =
    DeriveGen.instance(endsWithStringGen(ws, deriveGenT.derive))

  implicit def startsWithStringDeriveGen[S <: String](implicit
    ws: Witness.Aux[S],
    deriveGenT: DeriveGen[Char]
  ): DeriveGen[Refined[String, StartsWith[S]]] =
    DeriveGen.instance(startsWithStringGen(ws, deriveGenT.derive))

  implicit def endsWithString1DeriveGen[S <: String](implicit
    ws: Witness.Aux[S],
    deriveGenT: DeriveGen[Char]
  ): DeriveGen[Refined[String, EndsWith[S]]] =
    DeriveGen.instance(endsWithString1Gen(ws, deriveGenT.derive))

  implicit def startsWithString1DeriveGen[S <: String](implicit
    ws: Witness.Aux[S],
    deriveGenT: DeriveGen[Char]
  ): DeriveGen[Refined[String, StartsWith[S]]] =
    DeriveGen.instance(startsWithString1Gen(ws, deriveGenT.derive))

  implicit def endsWithStringNDeriveGen[S <: String, P](implicit
    ws: Witness.Aux[S],
    deriveGenT: DeriveGen[Char],
    deriveGenSize: DeriveGen[Int Refined P]
  ): DeriveGen[Refined[String, EndsWith[S]]] =
    DeriveGen.instance(endsWithStringNGen(ws, deriveGenT.derive, deriveGenSize.derive))

  implicit def startsWithStringNDeriveGen[S <: String, P](implicit
    ws: Witness.Aux[S],
    deriveGenT: DeriveGen[Char],
    deriveGenSize: DeriveGen[Int Refined P]
  ): DeriveGen[Refined[String, StartsWith[S]]] =
    DeriveGen.instance(startsWithStringNGen(ws, deriveGenT.derive, deriveGenSize.derive))

}
