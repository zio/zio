package zio.test.refined

import eu.timepit.refined.api.Refined
import eu.timepit.refined.char.{Digit, Letter, LowerCase, UpperCase, Whitespace}
import zio.random.Random
import zio.test.Gen
import zio.test.magnolia.DeriveGen

object char extends CharInstances

trait CharInstances {
  private val alphaCharGen: Gen[Random, Char] =
    Gen.weighted(Gen.char(65, 90) -> 26, Gen.char(97, 122) -> 26)

  private val numericCharGen: Gen[Random, Char] =
    Gen.weighted(Gen.char(48, 57) -> 10)

  private val whitespaceChars: Seq[Char] =
    (Char.MinValue to Char.MaxValue).filter(_.isWhitespace)

  implicit def digitArbitrary: DeriveGen[Refined[Char, Digit]] =
    DeriveGen.instance(numericCharGen.map(value => Refined.unsafeApply(value)))

  implicit def letterDeriveGen: DeriveGen[Refined[Char, Letter]] =
    DeriveGen.instance(alphaCharGen.map(value => Refined.unsafeApply(value)))

  implicit def lowerCaseDeriveGen: DeriveGen[Refined[Char, LowerCase]] =
    DeriveGen.instance(
      alphaCharGen.map(value => Refined.unsafeApply(value.toLower))
    )

  implicit def upperCaseDeriveGen: DeriveGen[Refined[Char, UpperCase]] =
    DeriveGen.instance(
      alphaCharGen.map(value => Refined.unsafeApply(value.toUpper))
    )

  implicit def whitespaceDeriveGen: DeriveGen[Refined[Char, Whitespace]] = {
    val whiteSpaceGens: Seq[Gen[Random, Char]] =
      whitespaceChars.map(Gen.const(_))

    DeriveGen.instance(
      Gen
        .oneOf[Random, Char](whiteSpaceGens: _*)
        .map(char => Refined.unsafeApply(char))
    )
  }
}
