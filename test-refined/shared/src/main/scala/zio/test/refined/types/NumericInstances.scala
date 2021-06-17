package zio.test.refined.types

import eu.timepit.refined.api.Refined
import eu.timepit.refined.types.numeric._
import zio.random.Random
import zio.test.Gen
import zio.test.magnolia.DeriveGen

object numeric extends NumericInstances

trait NumericInstances {
  val posByteGen: Gen[Random, PosByte]           = Gen.byte(1, Byte.MaxValue).map(Refined.unsafeApply)
  val nonNegByteGen: Gen[Random, NonNegByte]     = Gen.byte(0, Byte.MaxValue).map(Refined.unsafeApply)
  val negByteGen: Gen[Random, NegByte]           = Gen.byte(Byte.MinValue, -1).map(Refined.unsafeApply)
  val nonPosByteGen: Gen[Random, NonPosByte]     = Gen.byte(Byte.MinValue, 0).map(Refined.unsafeApply)
  val posShortGen: Gen[Random, PosShort]         = Gen.short(1, Short.MaxValue).map(Refined.unsafeApply)
  val nonNegShortGen: Gen[Random, NonNegShort]   = Gen.short(0, Short.MaxValue).map(Refined.unsafeApply)
  val negShortGen: Gen[Random, NegShort]         = Gen.short(Short.MinValue, -1).map(Refined.unsafeApply)
  val nonPosShortGen: Gen[Random, NonPosShort]   = Gen.short(Short.MinValue, 0).map(Refined.unsafeApply)
  val posIntGen: Gen[Random, PosInt]             = Gen.int(1, Int.MaxValue).map(Refined.unsafeApply)
  val nonNegIntGen: Gen[Random, NonNegInt]       = Gen.int(0, Int.MaxValue).map(Refined.unsafeApply)
  val negIntGen: Gen[Random, NegInt]             = Gen.int(Int.MinValue, -1).map(Refined.unsafeApply)
  val nonPosIntGen: Gen[Random, NonPosInt]       = Gen.int(Int.MinValue, 0).map(Refined.unsafeApply)
  val posLongGen: Gen[Random, PosLong]           = Gen.long(1, Long.MaxValue).map(Refined.unsafeApply)
  val nonNegLongGen: Gen[Random, NonNegLong]     = Gen.long(0, Long.MaxValue).map(Refined.unsafeApply)
  val negLongGen: Gen[Random, NegLong]           = Gen.long(Long.MinValue, -1).map(Refined.unsafeApply)
  val nonPosLongGen: Gen[Random, NonPosLong]     = Gen.long(Long.MinValue, 0).map(Refined.unsafeApply)
  val posBigIntGen: Gen[Random, PosBigInt]       = Gen.bigInt(1, Long.MaxValue).map(Refined.unsafeApply)
  val nonNegBigIntGen: Gen[Random, NonNegBigInt] = Gen.bigInt(0, Long.MaxValue).map(Refined.unsafeApply)
  val negBigIntGen: Gen[Random, NegBigInt]       = Gen.bigInt(Long.MinValue, -1).map(Refined.unsafeApply)
  val nonPosBigIntGen: Gen[Random, NonPosBigInt] = Gen.bigInt(Long.MinValue, 0).map(Refined.unsafeApply)
  val posFloatGen: Gen[Random, PosFloat] = Gen
    .double(1, Float.MaxValue.toDouble)
    .map(v => Refined.unsafeApply(v.toFloat))
  val nonNegFloatGen: Gen[Random, NonNegFloat] = Gen
    .double(0, Float.MaxValue.toDouble)
    .map(v => Refined.unsafeApply(v.toFloat))
  val negFloatGen: Gen[Random, NegFloat] = Gen
    .double(Float.MinValue.toDouble, -1)
    .map(v => Refined.unsafeApply(v.toFloat))
  val nonPosFloatGen: Gen[Random, NonPosFloat] = Gen
    .double(Float.MinValue.toDouble, 0)
    .map(v => Refined.unsafeApply(v.toFloat))
  val posDoubleGen: Gen[Random, PosDouble]               = Gen.double(1, Double.MaxValue).map(Refined.unsafeApply)
  val nonNegDoubleGen: Gen[Random, NonNegDouble]         = Gen.double(0, Double.MaxValue).map(Refined.unsafeApply)
  val negDoubleGen: Gen[Random, NegDouble]               = Gen.double(Double.MinValue, -1).map(Refined.unsafeApply)
  val nonPosDoubleGen: Gen[Random, NonPosDouble]         = Gen.double(Double.MinValue, 0).map(Refined.unsafeApply)
  val posBigDecimalGen: Gen[Random, PosBigDecimal]       = Gen.bigDecimal(1, Double.MaxValue).map(Refined.unsafeApply)
  val nonNegBigDecimalGen: Gen[Random, NonNegBigDecimal] = Gen.bigDecimal(0, Double.MaxValue).map(Refined.unsafeApply)
  val negBigDecimalGen: Gen[Random, NegBigDecimal]       = Gen.bigDecimal(Double.MinValue, -1).map(Refined.unsafeApply)
  val nonPosBigDecimalGen: Gen[Random, NonPosBigDecimal] = Gen.bigDecimal(Double.MinValue, 0).map(Refined.unsafeApply)
  val nonNanFloatGen: Gen[Random, NonNaNFloat]           = Gen.anyFloat.map(Refined.unsafeApply)
  val nonNanDoubleGen: Gen[Random, NonNaNDouble]         = Gen.anyDouble.map(Refined.unsafeApply)

  implicit val posByteDeriveGen: DeriveGen[PosByte]                   = DeriveGen.instance(posByteGen)
  implicit val nonNegByteDeriveGen: DeriveGen[NonNegByte]             = DeriveGen.instance(nonNegByteGen)
  implicit val negByteDeriveGen: DeriveGen[NegByte]                   = DeriveGen.instance(negByteGen)
  implicit val nonPosByteDeriveGen: DeriveGen[NonPosByte]             = DeriveGen.instance(nonPosByteGen)
  implicit val posShortDeriveGen: DeriveGen[PosShort]                 = DeriveGen.instance(posShortGen)
  implicit val nonNegShortDeriveGen: DeriveGen[NonNegShort]           = DeriveGen.instance(nonNegShortGen)
  implicit val negShortDeriveGen: DeriveGen[NegShort]                 = DeriveGen.instance(negShortGen)
  implicit val nonPosShortDeriveGen: DeriveGen[NonPosShort]           = DeriveGen.instance(nonPosShortGen)
  implicit val posIntDeriveGen: DeriveGen[PosInt]                     = DeriveGen.instance(posIntGen)
  implicit val nonNegIntDeriveGen: DeriveGen[NonNegInt]               = DeriveGen.instance(nonNegIntGen)
  implicit val negIntDeriveGen: DeriveGen[NegInt]                     = DeriveGen.instance(negIntGen)
  implicit val nonPosIntDeriveGen: DeriveGen[NonPosInt]               = DeriveGen.instance(nonPosIntGen)
  implicit val posLongDeriveGen: DeriveGen[PosLong]                   = DeriveGen.instance(posLongGen)
  implicit val nonNegLongDeriveGen: DeriveGen[NonNegLong]             = DeriveGen.instance(nonNegLongGen)
  implicit val negLongDeriveGen: DeriveGen[NegLong]                   = DeriveGen.instance(negLongGen)
  implicit val nonPosLongDeriveGen: DeriveGen[NonPosLong]             = DeriveGen.instance(nonPosLongGen)
  implicit val posBigIntDeriveGen: DeriveGen[PosBigInt]               = DeriveGen.instance(posBigIntGen)
  implicit val nonNegBigIntDeriveGen: DeriveGen[NonNegBigInt]         = DeriveGen.instance(nonNegBigIntGen)
  implicit val negBigIntDeriveGen: DeriveGen[NegBigInt]               = DeriveGen.instance(negBigIntGen)
  implicit val nonPosBigIntDeriveGen: DeriveGen[NonPosBigInt]         = DeriveGen.instance(nonPosBigIntGen)
  implicit val posFloatDeriveGen: DeriveGen[PosFloat]                 = DeriveGen.instance(posFloatGen)
  implicit val nonNegFloatDeriveGen: DeriveGen[NonNegFloat]           = DeriveGen.instance(nonNegFloatGen)
  implicit val negFloatDeriveGen: DeriveGen[NegFloat]                 = DeriveGen.instance(negFloatGen)
  implicit val nonPosFloatDeriveGen: DeriveGen[NonPosFloat]           = DeriveGen.instance(nonPosFloatGen)
  implicit val posDoubleDeriveGen: DeriveGen[PosDouble]               = DeriveGen.instance(posDoubleGen)
  implicit val nonNegDoubleDeriveGen: DeriveGen[NonNegDouble]         = DeriveGen.instance(nonNegDoubleGen)
  implicit val negDoubleDeriveGen: DeriveGen[NegDouble]               = DeriveGen.instance(negDoubleGen)
  implicit val nonPosDoubleDeriveGen: DeriveGen[NonPosDouble]         = DeriveGen.instance(nonPosDoubleGen)
  implicit val posBigDecimalDeriveGen: DeriveGen[PosBigDecimal]       = DeriveGen.instance(posBigDecimalGen)
  implicit val nonNegBigDecimalDeriveGen: DeriveGen[NonNegBigDecimal] = DeriveGen.instance(nonNegBigDecimalGen)
  implicit val negBigDecimalDeriveGen: DeriveGen[NegBigDecimal]       = DeriveGen.instance(negBigDecimalGen)
  implicit val nonPosBigDecimalDeriveGen: DeriveGen[NonPosBigDecimal] = DeriveGen.instance(nonPosBigDecimalGen)
  implicit val nonNanFloatDeriveGen: DeriveGen[NonNaNFloat]           = DeriveGen.instance(nonNanFloatGen)
  implicit val nonNanDoubleDeriveGen: DeriveGen[NonNaNDouble]         = DeriveGen.instance(nonNanDoubleGen)
}
