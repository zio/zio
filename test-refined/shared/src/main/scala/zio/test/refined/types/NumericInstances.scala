package zio.test.refined.types

import eu.timepit.refined.api.Refined
import eu.timepit.refined.types.numeric._
import zio.test.Gen
import zio.test.magnolia.DeriveGen
import zio.{Has, Random}

object numeric extends NumericInstances

trait NumericInstances {
  val posByteGen: Gen[Has[Random], PosByte]           = Gen.byte(1, Byte.MaxValue).map(Refined.unsafeApply)
  val nonNegByteGen: Gen[Has[Random], NonNegByte]     = Gen.byte(0, Byte.MaxValue).map(Refined.unsafeApply)
  val negByteGen: Gen[Has[Random], NegByte]           = Gen.byte(Byte.MinValue, -1).map(Refined.unsafeApply)
  val nonPosByteGen: Gen[Has[Random], NonPosByte]     = Gen.byte(Byte.MinValue, 0).map(Refined.unsafeApply)
  val posShortGen: Gen[Has[Random], PosShort]         = Gen.short(1, Short.MaxValue).map(Refined.unsafeApply)
  val nonNegShortGen: Gen[Has[Random], NonNegShort]   = Gen.short(0, Short.MaxValue).map(Refined.unsafeApply)
  val negShortGen: Gen[Has[Random], NegShort]         = Gen.short(Short.MinValue, -1).map(Refined.unsafeApply)
  val nonPosShortGen: Gen[Has[Random], NonPosShort]   = Gen.short(Short.MinValue, 0).map(Refined.unsafeApply)
  val posIntGen: Gen[Has[Random], PosInt]             = Gen.int(1, Int.MaxValue).map(Refined.unsafeApply)
  val nonNegIntGen: Gen[Has[Random], NonNegInt]       = Gen.int(0, Int.MaxValue).map(Refined.unsafeApply)
  val negIntGen: Gen[Has[Random], NegInt]             = Gen.int(Int.MinValue, -1).map(Refined.unsafeApply)
  val nonPosIntGen: Gen[Has[Random], NonPosInt]       = Gen.int(Int.MinValue, 0).map(Refined.unsafeApply)
  val posLongGen: Gen[Has[Random], PosLong]           = Gen.long(1, Long.MaxValue).map(Refined.unsafeApply)
  val nonNegLongGen: Gen[Has[Random], NonNegLong]     = Gen.long(0, Long.MaxValue).map(Refined.unsafeApply)
  val negLongGen: Gen[Has[Random], NegLong]           = Gen.long(Long.MinValue, -1).map(Refined.unsafeApply)
  val nonPosLongGen: Gen[Has[Random], NonPosLong]     = Gen.long(Long.MinValue, 0).map(Refined.unsafeApply)
  val posBigIntGen: Gen[Has[Random], PosBigInt]       = Gen.bigInt(1, Long.MaxValue).map(Refined.unsafeApply)
  val nonNegBigIntGen: Gen[Has[Random], NonNegBigInt] = Gen.bigInt(0, Long.MaxValue).map(Refined.unsafeApply)
  val negBigIntGen: Gen[Has[Random], NegBigInt]       = Gen.bigInt(Long.MinValue, -1).map(Refined.unsafeApply)
  val nonPosBigIntGen: Gen[Has[Random], NonPosBigInt] = Gen.bigInt(Long.MinValue, 0).map(Refined.unsafeApply)
  val posFloatGen: Gen[Has[Random], PosFloat] = Gen
    .double(1, Float.MaxValue.toDouble)
    .map(v => Refined.unsafeApply(v.toFloat))
  val nonNegFloatGen: Gen[Has[Random], NonNegFloat] = Gen
    .double(0, Float.MaxValue.toDouble)
    .map(v => Refined.unsafeApply(v.toFloat))
  val negFloatGen: Gen[Has[Random], NegFloat] = Gen
    .double(Float.MinValue.toDouble, -1)
    .map(v => Refined.unsafeApply(v.toFloat))
  val nonPosFloatGen: Gen[Has[Random], NonPosFloat] = Gen
    .double(Float.MinValue.toDouble, 0)
    .map(v => Refined.unsafeApply(v.toFloat))
  val posDoubleGen: Gen[Has[Random], PosDouble]         = Gen.double(1, Double.MaxValue).map(Refined.unsafeApply)
  val nonNegDoubleGen: Gen[Has[Random], NonNegDouble]   = Gen.double(0, Double.MaxValue).map(Refined.unsafeApply)
  val negDoubleGen: Gen[Has[Random], NegDouble]         = Gen.double(Double.MinValue, -1).map(Refined.unsafeApply)
  val nonPosDoubleGen: Gen[Has[Random], NonPosDouble]   = Gen.double(Double.MinValue, 0).map(Refined.unsafeApply)
  val posBigDecimalGen: Gen[Has[Random], PosBigDecimal] = Gen.bigDecimal(1, Double.MaxValue).map(Refined.unsafeApply)
  val nonNegBigDecimalGen: Gen[Has[Random], NonNegBigDecimal] =
    Gen.bigDecimal(0, Double.MaxValue).map(Refined.unsafeApply)
  val negBigDecimalGen: Gen[Has[Random], NegBigDecimal] = Gen.bigDecimal(Double.MinValue, -1).map(Refined.unsafeApply)
  val nonPosBigDecimalGen: Gen[Has[Random], NonPosBigDecimal] =
    Gen.bigDecimal(Double.MinValue, 0).map(Refined.unsafeApply)
  val nonNanFloatGen: Gen[Has[Random], NonNaNFloat]   = Gen.float.map(Refined.unsafeApply)
  val nonNanDoubleGen: Gen[Has[Random], NonNaNDouble] = Gen.double.map(Refined.unsafeApply)

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
