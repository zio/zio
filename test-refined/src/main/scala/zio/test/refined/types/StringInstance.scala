package zio.test.refined.types

import eu.timepit.refined.api.Refined
import eu.timepit.refined.collection.{MaxSize, Size}
import eu.timepit.refined.numeric.Interval
import eu.timepit.refined.types.string.{FiniteString, HexString, NonEmptyFiniteString, NonEmptyString, TrimmedString}
import shapeless.Nat._1
import shapeless.Witness
import zio.test.magnolia.DeriveGen
import zio.test.Gen

/**
 * Brings the instances of `StringInstance` into scope: `import
 * zio.test.refined.types.string._`.
 */
object string extends StringInstance

/**
 * Instances for the `FiniteString[N]`, `NonEmptyString`,
 * `NonEmptyFiniteString[N]`, `TrimmedString` and `HexString` aliases of
 * `eu.timepit.refined.types.string`. The trait is named `StringInstance`, in
 * the singular. On Scala 3, implicit search does not find `FiniteString[N]` and
 * `NonEmptyFiniteString[N]` for a literal `N` such as `FiniteString[5]`;
 * `NonEmptyString`, `TrimmedString` and `HexString` are found on Scala 2 and
 * Scala 3.
 */
trait StringInstance {

  /**
   * Generates strings whose length is between `min` and the witness `N`. The
   * `apply` method takes the character generator.
   */
  class FiniteStringPartiallyApplied[N <: Int, P](min: Int) {

    /**
     * A generator of `Refined[String, P]`: a length drawn with `Gen.int(min,
     * N)`, both inclusive, then a string of exactly that length from `charGen`.
     */
    def apply[R](charGen: Gen[R, Char])(implicit ws: Witness.Aux[N]): Gen[R, Refined[String, P]] =
      for {
        i   <- Gen.int(min, ws.value)
        str <- Gen.stringN(i)(charGen)
      } yield Refined.unsafeApply(str)
  }

  /**
   * A generator of `String Refined MaxSize[N]` for `FiniteString[N]`: strings
   * of 0 to N characters, both inclusive. Call it as
   * `finiteStringGen[N](charGen)`.
   */
  def finiteStringGen[N <: Int]: FiniteStringPartiallyApplied[N, MaxSize[N]] =
    new FiniteStringPartiallyApplied[N, MaxSize[N]](0)

  /**
   * A generator of `NonEmptyString` built with `Gen.string1(charGen)`: at least
   * one character, taken from `charGen`.
   */
  def nonEmptyStringGen[R](charGen: Gen[R, Char]): Gen[R, NonEmptyString] =
    Gen.string1(charGen).map(Refined.unsafeApply)

  /**
   * A generator of `String Refined Size[Interval.Closed[_1, N]]` for
   * `NonEmptyFiniteString[N]`: strings of 1 to N characters, both inclusive.
   * Call it as `nonEmptyFiniteStringGen[N](charGen)`.
   */
  def nonEmptyFiniteStringGen[N <: Int]: FiniteStringPartiallyApplied[N, Size[Interval.Closed[_1, N]]] =
    new FiniteStringPartiallyApplied[N, Size[Interval.Closed[_1, N]]](1)

  /**
   * A generator of `TrimmedString`: a string from `Gen.string(charGen)` with
   * `trim` applied, so it can be shorter than the generated string and can be
   * empty.
   */
  def trimmedStringGen[R](charGen: Gen[R, Char]): Gen[R, TrimmedString] =
    Gen.string(charGen).map(s => Refined.unsafeApply(s.trim))

  /**
   * A generator of `HexString`: a string from `Gen.string`, possibly empty,
   * with all characters from `Gen.hexCharLower` or all from `Gen.hexCharUpper`,
   * chosen with equal probability for each value. The empty string, which this
   * generator can produce, does not satisfy `HexString`.
   */
  def hexStringGen: Gen[Any, HexString] =
    Gen.oneOf(Gen.string(Gen.hexCharLower), Gen.string(Gen.hexCharUpper)).map(Refined.unsafeApply)

  /**
   * The implicit `DeriveGen` for `FiniteString[N]`; it wraps `finiteStringGen`.
   * The characters come from the `DeriveGen` of `Char`.
   */
  implicit def finiteStringDeriveGen[N <: Int](implicit
    ws: Witness.Aux[N],
    charGen: DeriveGen[Char]
  ): DeriveGen[FiniteString[N]] =
    DeriveGen.instance(finiteStringGen[N](charGen.derive))

  /**
   * The implicit `DeriveGen` for `NonEmptyString`; it wraps
   * `nonEmptyStringGen`. The characters come from the `DeriveGen` of `Char`.
   */
  implicit def nonEmptyStringDeriveGen[R](implicit charGen: DeriveGen[Char]): DeriveGen[NonEmptyString] =
    DeriveGen.instance(nonEmptyStringGen(charGen.derive))

  /**
   * The implicit `DeriveGen` for `NonEmptyFiniteString[N]`; it wraps
   * `nonEmptyFiniteStringGen`. The characters come from the `DeriveGen` of
   * `Char`.
   */
  implicit def nonEmptyFiniteStringDeriveGen[N <: Int](implicit
    ws: Witness.Aux[N],
    charGen: DeriveGen[Char]
  ): DeriveGen[NonEmptyFiniteString[N]] =
    DeriveGen.instance(nonEmptyFiniteStringGen[N](charGen.derive))

  /**
   * The implicit `DeriveGen` for `TrimmedString`; it wraps `trimmedStringGen`.
   * The characters come from the `DeriveGen` of `Char`.
   */
  implicit def trimmedStringDeriveGen[R](implicit charGen: DeriveGen[Char]): DeriveGen[TrimmedString] =
    DeriveGen.instance(trimmedStringGen(charGen.derive))

  /**
   * The implicit `DeriveGen` for `HexString`; it wraps `hexStringGen`.
   */
  implicit def hexStringDeriveGen[R]: DeriveGen[HexString] = DeriveGen.instance(hexStringGen)
}
