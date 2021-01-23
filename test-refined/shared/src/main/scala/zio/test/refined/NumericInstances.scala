package zio.test.refined

import eu.timepit.refined.api.Refined
import eu.timepit.refined.internal.WitnessAs
import eu.timepit.refined.numeric.{Greater, Less}
import zio.test.Gen
import zio.test.magnolia.DeriveGen

object numeric extends NumericInstances

trait NumericInstances {

  implicit def intGreaterThan[N](implicit
    wn: WitnessAs[N, Int]
  ): DeriveGen[Int Refined Greater[N]] =
    DeriveGen.instance(Gen.int(wn.snd, Int.MaxValue).map(Refined.unsafeApply))

  implicit def longGreaterThan[N](implicit
    wn: WitnessAs[N, Long]
  ): DeriveGen[Long Refined Greater[N]] =
    DeriveGen.instance(Gen.long(wn.snd, Long.MaxValue).map(Refined.unsafeApply))

  implicit def shortGreaterThan[N](implicit
    wn: WitnessAs[N, Short]
  ): DeriveGen[Short Refined Greater[N]] =
    DeriveGen.instance(Gen.short(wn.snd, Short.MaxValue).map(Refined.unsafeApply))

  implicit def byteGreaterThan[N](implicit
    wn: WitnessAs[N, Byte]
  ): DeriveGen[Byte Refined Greater[N]] =
    DeriveGen.instance(Gen.byte(wn.snd, Byte.MaxValue).map(Refined.unsafeApply))

  implicit def doubleGreaterThan[N](implicit
    wn: WitnessAs[N, Double]
  ): DeriveGen[Double Refined Greater[N]] =
    DeriveGen.instance(Gen.double(wn.snd, Double.MaxValue).map(Refined.unsafeApply))

  implicit def intLessThan[N](implicit
    wn: WitnessAs[N, Int]
  ): DeriveGen[Int Refined Less[N]] =
    DeriveGen.instance(Gen.int(Int.MinValue, wn.snd).map(Refined.unsafeApply))

  implicit def longLessThan[N](implicit
    wn: WitnessAs[N, Long]
  ): DeriveGen[Long Refined Less[N]] =
    DeriveGen.instance(Gen.long(Long.MinValue, wn.snd).map(Refined.unsafeApply))

  implicit def shortLessThan[N](implicit
    wn: WitnessAs[N, Short]
  ): DeriveGen[Short Refined Less[N]] =
    DeriveGen.instance(Gen.short(Short.MinValue, wn.snd).map(Refined.unsafeApply))

  implicit def byteLessThan[N](implicit
    wn: WitnessAs[N, Byte]
  ): DeriveGen[Byte Refined Less[N]] =
    DeriveGen.instance(Gen.byte(Byte.MinValue, wn.snd).map(Refined.unsafeApply))

  implicit def doubleLessThan[N](implicit
    wn: WitnessAs[N, Double]
  ): DeriveGen[Double Refined Less[N]] =
    DeriveGen.instance(Gen.double(Double.MinValue, wn.snd).map(Refined.unsafeApply))

}
