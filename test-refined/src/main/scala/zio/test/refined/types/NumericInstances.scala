package zio.test.refined.types

import eu.timepit.refined.api.Refined
import eu.timepit.refined.types.numeric._
import zio.test.Gen
import zio.test.magnolia.DeriveGen

/**
 * Brings the instances of `NumericInstances` into scope: `import
 * zio.test.refined.types.numeric._`.
 */
object numeric extends NumericInstances

/**
 * Instances for the `Pos`, `NonNeg`, `Neg` and `NonPos` aliases of
 * `eu.timepit.refined.types.numeric` on `Byte`, `Short`, `Int`, `Long`,
 * `Float`, `Double`, `BigInt` and `BigDecimal`, and for `NonNaNFloat` and
 * `NonNaNDouble`.
 */
trait NumericInstances {

  /**
   * A generator of `PosByte` built with `Gen.byte(1, Byte.MaxValue)`: from 1 to
   * Byte.MaxValue, both inclusive.
   */
  val posByteGen: Gen[Any, PosByte] = Gen.byte(1, Byte.MaxValue).map(Refined.unsafeApply)

  /**
   * A generator of `NonNegByte` built with `Gen.byte(0, Byte.MaxValue)`: from 0
   * to Byte.MaxValue, both inclusive.
   */
  val nonNegByteGen: Gen[Any, NonNegByte] = Gen.byte(0, Byte.MaxValue).map(Refined.unsafeApply)

  /**
   * A generator of `NegByte` built with `Gen.byte(Byte.MinValue, -1)`: from
   * Byte.MinValue to -1, both inclusive.
   */
  val negByteGen: Gen[Any, NegByte] = Gen.byte(Byte.MinValue, -1).map(Refined.unsafeApply)

  /**
   * A generator of `NonPosByte` built with `Gen.byte(Byte.MinValue, 0)`: from
   * Byte.MinValue to 0, both inclusive.
   */
  val nonPosByteGen: Gen[Any, NonPosByte] = Gen.byte(Byte.MinValue, 0).map(Refined.unsafeApply)

  /**
   * A generator of `PosShort` built with `Gen.short(1, Short.MaxValue)`: from 1
   * to Short.MaxValue, both inclusive.
   */
  val posShortGen: Gen[Any, PosShort] = Gen.short(1, Short.MaxValue).map(Refined.unsafeApply)

  /**
   * A generator of `NonNegShort` built with `Gen.short(0, Short.MaxValue)`:
   * from 0 to Short.MaxValue, both inclusive.
   */
  val nonNegShortGen: Gen[Any, NonNegShort] = Gen.short(0, Short.MaxValue).map(Refined.unsafeApply)

  /**
   * A generator of `NegShort` built with `Gen.short(Short.MinValue, -1)`: from
   * Short.MinValue to -1, both inclusive.
   */
  val negShortGen: Gen[Any, NegShort] = Gen.short(Short.MinValue, -1).map(Refined.unsafeApply)

  /**
   * A generator of `NonPosShort` built with `Gen.short(Short.MinValue, 0)`:
   * from Short.MinValue to 0, both inclusive.
   */
  val nonPosShortGen: Gen[Any, NonPosShort] = Gen.short(Short.MinValue, 0).map(Refined.unsafeApply)

  /**
   * A generator of `PosInt` built with `Gen.int(1, Int.MaxValue)`: from 1 to
   * Int.MaxValue, both inclusive.
   */
  val posIntGen: Gen[Any, PosInt] = Gen.int(1, Int.MaxValue).map(Refined.unsafeApply)

  /**
   * A generator of `NonNegInt` built with `Gen.int(0, Int.MaxValue)`: from 0 to
   * Int.MaxValue, both inclusive.
   */
  val nonNegIntGen: Gen[Any, NonNegInt] = Gen.int(0, Int.MaxValue).map(Refined.unsafeApply)

  /**
   * A generator of `NegInt` built with `Gen.int(Int.MinValue, -1)`: from
   * Int.MinValue to -1, both inclusive.
   */
  val negIntGen: Gen[Any, NegInt] = Gen.int(Int.MinValue, -1).map(Refined.unsafeApply)

  /**
   * A generator of `NonPosInt` built with `Gen.int(Int.MinValue, 0)`: from
   * Int.MinValue to 0, both inclusive.
   */
  val nonPosIntGen: Gen[Any, NonPosInt] = Gen.int(Int.MinValue, 0).map(Refined.unsafeApply)

  /**
   * A generator of `PosLong` built with `Gen.long(1, Long.MaxValue)`: from 1 to
   * Long.MaxValue, both inclusive.
   */
  val posLongGen: Gen[Any, PosLong] = Gen.long(1, Long.MaxValue).map(Refined.unsafeApply)

  /**
   * A generator of `NonNegLong` built with `Gen.long(0, Long.MaxValue)`: from 0
   * to Long.MaxValue, both inclusive.
   */
  val nonNegLongGen: Gen[Any, NonNegLong] = Gen.long(0, Long.MaxValue).map(Refined.unsafeApply)

  /**
   * A generator of `NegLong` built with `Gen.long(Long.MinValue, -1)`: from
   * Long.MinValue to -1, both inclusive.
   */
  val negLongGen: Gen[Any, NegLong] = Gen.long(Long.MinValue, -1).map(Refined.unsafeApply)

  /**
   * A generator of `NonPosLong` built with `Gen.long(Long.MinValue, 0)`: from
   * Long.MinValue to 0, both inclusive.
   */
  val nonPosLongGen: Gen[Any, NonPosLong] = Gen.long(Long.MinValue, 0).map(Refined.unsafeApply)

  /**
   * A generator of `PosBigInt` built with `Gen.bigInt(1, Long.MaxValue)`: from
   * 1 to Long.MaxValue, both inclusive.
   */
  val posBigIntGen: Gen[Any, PosBigInt] = Gen.bigInt(1, Long.MaxValue).map(Refined.unsafeApply)

  /**
   * A generator of `NonNegBigInt` built with `Gen.bigInt(0, Long.MaxValue)`:
   * from 0 to Long.MaxValue, both inclusive.
   */
  val nonNegBigIntGen: Gen[Any, NonNegBigInt] = Gen.bigInt(0, Long.MaxValue).map(Refined.unsafeApply)

  /**
   * A generator of `NegBigInt` built with `Gen.bigInt(Long.MinValue, -1)`: from
   * Long.MinValue to -1, both inclusive.
   */
  val negBigIntGen: Gen[Any, NegBigInt] = Gen.bigInt(Long.MinValue, -1).map(Refined.unsafeApply)

  /**
   * A generator of `NonPosBigInt` built with `Gen.bigInt(Long.MinValue, 0)`:
   * from Long.MinValue to 0, both inclusive.
   */
  val nonPosBigIntGen: Gen[Any, NonPosBigInt] = Gen.bigInt(Long.MinValue, 0).map(Refined.unsafeApply)

  /**
   * A generator of `PosFloat` built with `Gen.double(1, Float.MaxValue.toDouble)` and converted to `Float`: from 1 to Float.MaxValue.
   */
  val posFloatGen: Gen[Any, PosFloat] = Gen
    .double(1, Float.MaxValue.toDouble)
    .map(v => Refined.unsafeApply(v.toFloat))

  /**
   * A generator of `NonNegFloat` built with `Gen.double(0, Float.MaxValue.toDouble)` and converted to `Float`: from 0 to Float.MaxValue.
   */
  val nonNegFloatGen: Gen[Any, NonNegFloat] = Gen
    .double(0, Float.MaxValue.toDouble)
    .map(v => Refined.unsafeApply(v.toFloat))

  /**
   * A generator of `NegFloat` built with `Gen.double(Float.MinValue.toDouble, -1)` and converted to `Float`: from Float.MinValue to -1.
   */
  val negFloatGen: Gen[Any, NegFloat] = Gen
    .double(Float.MinValue.toDouble, -1)
    .map(v => Refined.unsafeApply(v.toFloat))

  /**
   * A generator of `NonPosFloat` built with `Gen.double(Float.MinValue.toDouble, 0)` and converted to `Float`: from Float.MinValue to 0.
   */
  val nonPosFloatGen: Gen[Any, NonPosFloat] = Gen
    .double(Float.MinValue.toDouble, 0)
    .map(v => Refined.unsafeApply(v.toFloat))

  /**
   * A generator of `PosDouble` built with `Gen.double(1, Double.MaxValue)`:
   * from 1 (inclusive) to Double.MaxValue (exclusive).
   */
  val posDoubleGen: Gen[Any, PosDouble] = Gen.double(1, Double.MaxValue).map(Refined.unsafeApply)

  /**
   * A generator of `NonNegDouble` built with `Gen.double(0, Double.MaxValue)`:
   * from 0 (inclusive) to Double.MaxValue (exclusive).
   */
  val nonNegDoubleGen: Gen[Any, NonNegDouble] = Gen.double(0, Double.MaxValue).map(Refined.unsafeApply)

  /**
   * A generator of `NegDouble` built with `Gen.double(Double.MinValue, -1)`:
   * from Double.MinValue (inclusive) to -1 (exclusive).
   */
  val negDoubleGen: Gen[Any, NegDouble] = Gen.double(Double.MinValue, -1).map(Refined.unsafeApply)

  /**
   * A generator of `NonPosDouble` built with `Gen.double(Double.MinValue, 0)`:
   * from Double.MinValue (inclusive) to 0 (exclusive).
   */
  val nonPosDoubleGen: Gen[Any, NonPosDouble] = Gen.double(Double.MinValue, 0).map(Refined.unsafeApply)

  /**
   * A generator of `PosBigDecimal` built with `Gen.bigDecimal(1,
   * Double.MaxValue)`: the generated values run from 1 to about 8.99E307, half
   * of Double.MaxValue, not up to Double.MaxValue.
   */
  val posBigDecimalGen: Gen[Any, PosBigDecimal] = Gen.bigDecimal(1, Double.MaxValue).map(Refined.unsafeApply)

  /**
   * A generator of `NonNegBigDecimal` built with `Gen.bigDecimal(0,
   * Double.MaxValue)`: the generated values run from 0 to about 8.99E307, half
   * of Double.MaxValue, not up to Double.MaxValue.
   */
  val nonNegBigDecimalGen: Gen[Any, NonNegBigDecimal] =
    Gen.bigDecimal(0, Double.MaxValue).map(Refined.unsafeApply)

  /**
   * A generator of `NegBigDecimal` built with `Gen.bigDecimal(Double.MinValue,
   * -1)`: the generated values run from Double.MinValue to about -8.99E307,
   * half of Double.MinValue, not up to -1.
   */
  val negBigDecimalGen: Gen[Any, NegBigDecimal] = Gen.bigDecimal(Double.MinValue, -1).map(Refined.unsafeApply)

  /**
   * A generator of `NonPosBigDecimal` built with
   * `Gen.bigDecimal(Double.MinValue, 0)`: the generated values run from
   * Double.MinValue to about -8.99E307, half of Double.MinValue, not up to 0.
   */
  val nonPosBigDecimalGen: Gen[Any, NonPosBigDecimal] =
    Gen.bigDecimal(Double.MinValue, 0).map(Refined.unsafeApply)

  /**
   * A generator of `NonNaNFloat` built with `Gen.float`: floats from 0.0
   * (inclusive) to 1.0 (exclusive).
   */
  val nonNanFloatGen: Gen[Any, NonNaNFloat] = Gen.float.map(Refined.unsafeApply)

  /**
   * A generator of `NonNaNDouble` built with `Gen.double`: doubles from 0.0
   * (inclusive) to 1.0 (exclusive).
   */
  val nonNanDoubleGen: Gen[Any, NonNaNDouble] = Gen.double.map(Refined.unsafeApply)

  /**
   * The implicit `DeriveGen` for `PosByte`; it wraps `posByteGen`.
   */
  implicit val posByteDeriveGen: DeriveGen[PosByte] = DeriveGen.instance(posByteGen)

  /**
   * The implicit `DeriveGen` for `NonNegByte`; it wraps `nonNegByteGen`.
   */
  implicit val nonNegByteDeriveGen: DeriveGen[NonNegByte] = DeriveGen.instance(nonNegByteGen)

  /**
   * The implicit `DeriveGen` for `NegByte`; it wraps `negByteGen`.
   */
  implicit val negByteDeriveGen: DeriveGen[NegByte] = DeriveGen.instance(negByteGen)

  /**
   * The implicit `DeriveGen` for `NonPosByte`; it wraps `nonPosByteGen`.
   */
  implicit val nonPosByteDeriveGen: DeriveGen[NonPosByte] = DeriveGen.instance(nonPosByteGen)

  /**
   * The implicit `DeriveGen` for `PosShort`; it wraps `posShortGen`.
   */
  implicit val posShortDeriveGen: DeriveGen[PosShort] = DeriveGen.instance(posShortGen)

  /**
   * The implicit `DeriveGen` for `NonNegShort`; it wraps `nonNegShortGen`.
   */
  implicit val nonNegShortDeriveGen: DeriveGen[NonNegShort] = DeriveGen.instance(nonNegShortGen)

  /**
   * The implicit `DeriveGen` for `NegShort`; it wraps `negShortGen`.
   */
  implicit val negShortDeriveGen: DeriveGen[NegShort] = DeriveGen.instance(negShortGen)

  /**
   * The implicit `DeriveGen` for `NonPosShort`; it wraps `nonPosShortGen`.
   */
  implicit val nonPosShortDeriveGen: DeriveGen[NonPosShort] = DeriveGen.instance(nonPosShortGen)

  /**
   * The implicit `DeriveGen` for `PosInt`; it wraps `posIntGen`.
   */
  implicit val posIntDeriveGen: DeriveGen[PosInt] = DeriveGen.instance(posIntGen)

  /**
   * The implicit `DeriveGen` for `NonNegInt`; it wraps `nonNegIntGen`.
   */
  implicit val nonNegIntDeriveGen: DeriveGen[NonNegInt] = DeriveGen.instance(nonNegIntGen)

  /**
   * The implicit `DeriveGen` for `NegInt`; it wraps `negIntGen`.
   */
  implicit val negIntDeriveGen: DeriveGen[NegInt] = DeriveGen.instance(negIntGen)

  /**
   * The implicit `DeriveGen` for `NonPosInt`; it wraps `nonPosIntGen`.
   */
  implicit val nonPosIntDeriveGen: DeriveGen[NonPosInt] = DeriveGen.instance(nonPosIntGen)

  /**
   * The implicit `DeriveGen` for `PosLong`; it wraps `posLongGen`.
   */
  implicit val posLongDeriveGen: DeriveGen[PosLong] = DeriveGen.instance(posLongGen)

  /**
   * The implicit `DeriveGen` for `NonNegLong`; it wraps `nonNegLongGen`.
   */
  implicit val nonNegLongDeriveGen: DeriveGen[NonNegLong] = DeriveGen.instance(nonNegLongGen)

  /**
   * The implicit `DeriveGen` for `NegLong`; it wraps `negLongGen`.
   */
  implicit val negLongDeriveGen: DeriveGen[NegLong] = DeriveGen.instance(negLongGen)

  /**
   * The implicit `DeriveGen` for `NonPosLong`; it wraps `nonPosLongGen`.
   */
  implicit val nonPosLongDeriveGen: DeriveGen[NonPosLong] = DeriveGen.instance(nonPosLongGen)

  /**
   * The implicit `DeriveGen` for `PosBigInt`; it wraps `posBigIntGen`.
   */
  implicit val posBigIntDeriveGen: DeriveGen[PosBigInt] = DeriveGen.instance(posBigIntGen)

  /**
   * The implicit `DeriveGen` for `NonNegBigInt`; it wraps `nonNegBigIntGen`.
   */
  implicit val nonNegBigIntDeriveGen: DeriveGen[NonNegBigInt] = DeriveGen.instance(nonNegBigIntGen)

  /**
   * The implicit `DeriveGen` for `NegBigInt`; it wraps `negBigIntGen`.
   */
  implicit val negBigIntDeriveGen: DeriveGen[NegBigInt] = DeriveGen.instance(negBigIntGen)

  /**
   * The implicit `DeriveGen` for `NonPosBigInt`; it wraps `nonPosBigIntGen`.
   */
  implicit val nonPosBigIntDeriveGen: DeriveGen[NonPosBigInt] = DeriveGen.instance(nonPosBigIntGen)

  /**
   * The implicit `DeriveGen` for `PosFloat`; it wraps `posFloatGen`.
   */
  implicit val posFloatDeriveGen: DeriveGen[PosFloat] = DeriveGen.instance(posFloatGen)

  /**
   * The implicit `DeriveGen` for `NonNegFloat`; it wraps `nonNegFloatGen`.
   */
  implicit val nonNegFloatDeriveGen: DeriveGen[NonNegFloat] = DeriveGen.instance(nonNegFloatGen)

  /**
   * The implicit `DeriveGen` for `NegFloat`; it wraps `negFloatGen`.
   */
  implicit val negFloatDeriveGen: DeriveGen[NegFloat] = DeriveGen.instance(negFloatGen)

  /**
   * The implicit `DeriveGen` for `NonPosFloat`; it wraps `nonPosFloatGen`.
   */
  implicit val nonPosFloatDeriveGen: DeriveGen[NonPosFloat] = DeriveGen.instance(nonPosFloatGen)

  /**
   * The implicit `DeriveGen` for `PosDouble`; it wraps `posDoubleGen`.
   */
  implicit val posDoubleDeriveGen: DeriveGen[PosDouble] = DeriveGen.instance(posDoubleGen)

  /**
   * The implicit `DeriveGen` for `NonNegDouble`; it wraps `nonNegDoubleGen`.
   */
  implicit val nonNegDoubleDeriveGen: DeriveGen[NonNegDouble] = DeriveGen.instance(nonNegDoubleGen)

  /**
   * The implicit `DeriveGen` for `NegDouble`; it wraps `negDoubleGen`.
   */
  implicit val negDoubleDeriveGen: DeriveGen[NegDouble] = DeriveGen.instance(negDoubleGen)

  /**
   * The implicit `DeriveGen` for `NonPosDouble`; it wraps `nonPosDoubleGen`.
   */
  implicit val nonPosDoubleDeriveGen: DeriveGen[NonPosDouble] = DeriveGen.instance(nonPosDoubleGen)

  /**
   * The implicit `DeriveGen` for `PosBigDecimal`; it wraps `posBigDecimalGen`.
   */
  implicit val posBigDecimalDeriveGen: DeriveGen[PosBigDecimal] = DeriveGen.instance(posBigDecimalGen)

  /**
   * The implicit `DeriveGen` for `NonNegBigDecimal`; it wraps
   * `nonNegBigDecimalGen`.
   */
  implicit val nonNegBigDecimalDeriveGen: DeriveGen[NonNegBigDecimal] = DeriveGen.instance(nonNegBigDecimalGen)

  /**
   * The implicit `DeriveGen` for `NegBigDecimal`; it wraps `negBigDecimalGen`.
   */
  implicit val negBigDecimalDeriveGen: DeriveGen[NegBigDecimal] = DeriveGen.instance(negBigDecimalGen)

  /**
   * The implicit `DeriveGen` for `NonPosBigDecimal`; it wraps
   * `nonPosBigDecimalGen`.
   */
  implicit val nonPosBigDecimalDeriveGen: DeriveGen[NonPosBigDecimal] = DeriveGen.instance(nonPosBigDecimalGen)

  /**
   * The implicit `DeriveGen` for `NonNaNFloat`; it wraps `nonNanFloatGen`.
   */
  implicit val nonNanFloatDeriveGen: DeriveGen[NonNaNFloat] = DeriveGen.instance(nonNanFloatGen)

  /**
   * The implicit `DeriveGen` for `NonNaNDouble`; it wraps `nonNanDoubleGen`.
   */
  implicit val nonNanDoubleDeriveGen: DeriveGen[NonNaNDouble] = DeriveGen.instance(nonNanDoubleGen)
}
