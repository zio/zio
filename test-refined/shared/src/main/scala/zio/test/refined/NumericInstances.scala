package zio.test.refined

import eu.timepit.refined.api.Refined
import eu.timepit.refined.internal.WitnessAs
import eu.timepit.refined.numeric.{Greater, Less}
import zio.random.Random
import zio.test.Gen

object numeric extends NumericInstances

trait NumericInstances {

  def intGreaterThan[N](implicit
    wn: WitnessAs[N, Int]
  ): Gen[Random, Int Refined Greater[N]] =
    Gen.int(wn.snd, Int.MaxValue).map(Refined.unsafeApply)

  def longGreaterThan[N](implicit
    wn: WitnessAs[N, Long]
  ): Gen[Random, Long Refined Greater[N]] =
    Gen.long(wn.snd, Long.MaxValue).map(Refined.unsafeApply)

  def shortGreaterThan[N](implicit
    wn: WitnessAs[N, Short]
  ): Gen[Random, Short Refined Greater[N]] =
    Gen.short(wn.snd, Short.MaxValue).map(Refined.unsafeApply)

  def byteGreaterThan[N](implicit
    wn: WitnessAs[N, Byte]
  ): Gen[Random, Byte Refined Greater[N]] =
    Gen.byte(wn.snd, Byte.MaxValue).map(Refined.unsafeApply)

  def doubleGreaterThan[N](implicit
    wn: WitnessAs[N, Double]
  ): Gen[Random, Double Refined Greater[N]] =
    Gen.double(wn.snd, Double.MaxValue).map(Refined.unsafeApply)

  def intLessThan[N](implicit
    wn: WitnessAs[N, Int]
  ): Gen[Random, Int Refined Less[N]] =
    Gen.int(Int.MinValue, wn.snd).map(Refined.unsafeApply)

  def longLessThan[N](implicit
    wn: WitnessAs[N, Long]
  ): Gen[Random, Long Refined Less[N]] =
    Gen.long(Long.MinValue, wn.snd).map(Refined.unsafeApply)

  def shortLessThan[N](implicit
    wn: WitnessAs[N, Short]
  ): Gen[Random, Short Refined Less[N]] =
    Gen.short(Short.MinValue, wn.snd).map(Refined.unsafeApply)

  def byteLessThan[N](implicit
    wn: WitnessAs[N, Byte]
  ): Gen[Random, Byte Refined Less[N]] =
    Gen.byte(Byte.MinValue, wn.snd).map(Refined.unsafeApply)

  def doubleLessThan[N](implicit
    wn: WitnessAs[N, Double]
  ): Gen[Random, Double Refined Less[N]] =
    Gen.double(Double.MinValue, wn.snd).map(Refined.unsafeApply)

}
