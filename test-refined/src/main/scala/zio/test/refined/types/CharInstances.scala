package zio.test.refined.types

import eu.timepit.refined.api.Refined
import eu.timepit.refined.types.char._
import zio.test.Gen
import zio.test.magnolia.DeriveGen

/**
 * Brings the instances of `CharInstances` into scope: `import zio.test.refined.types.char._`.
 */
object char extends CharInstances

/**
 * Instances for the `LowerCaseChar` and `UpperCaseChar` aliases of `eu.timepit.refined.types.char`.
 */
trait CharInstances {
  /**
   * A generator of `LowerCaseChar` built with `Gen.alphaChar` and `toLower`: the characters 'a' to 'z'.
   */
  val lowerCaseCharGen: Gen[Any, LowerCaseChar] = Gen.alphaChar.map(v => Refined.unsafeApply(v.toLower))

  /**
   * A generator of `UpperCaseChar` built with `Gen.alphaChar` and `toUpper`: the characters 'A' to 'Z'.
   */
  val upperCaseCharGen: Gen[Any, UpperCaseChar] = Gen.alphaChar.map(v => Refined.unsafeApply(v.toUpper))

  /**
   * The implicit `DeriveGen` for `LowerCaseChar`; it wraps `lowerCaseCharGen`.
   */
  implicit val lowerCaseCharDeriveGen: DeriveGen[LowerCaseChar] = DeriveGen.instance(lowerCaseCharGen)

  /**
   * The implicit `DeriveGen` for `UpperCaseChar`; it wraps `upperCaseCharGen`.
   */
  implicit val upperCaseCharDeriveGen: DeriveGen[UpperCaseChar] = DeriveGen.instance(upperCaseCharGen)
}
