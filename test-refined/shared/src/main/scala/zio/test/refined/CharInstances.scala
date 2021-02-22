package zio.test.refined

import eu.timepit.refined.api.Refined
import eu.timepit.refined.char.{Digit, Letter, LowerCase, UpperCase, Whitespace}
import zio.random.Random
import zio.test.Gen
import zio.test.magnolia.DeriveGen

object char extends CharInstances

trait CharInstances {

  implicit def digitArbitrary: DeriveGen[Refined[Char, Digit]] =
    DeriveGen.instance(Gen.numericChar.map(value => Refined.unsafeApply(value)))

  implicit def letterDeriveGen: DeriveGen[Refined[Char, Letter]] =
    DeriveGen.instance(Gen.alphaChar.map(value => Refined.unsafeApply(value)))

  implicit def lowerCaseDeriveGen: DeriveGen[Refined[Char, LowerCase]] =
    DeriveGen.instance(
      Gen.alphaChar.map(value => Refined.unsafeApply(value.toLower))
    )

  implicit def upperCaseDeriveGen: DeriveGen[Refined[Char, UpperCase]] =
    DeriveGen.instance(
      Gen.alphaChar.map(value => Refined.unsafeApply(value.toUpper))
    )

  implicit def whitespaceDeriveGen: DeriveGen[Refined[Char, Whitespace]] = {
    val whiteSpaceGens: Seq[Gen[Random, Char]] =
      Gen.whitespaceChars.map(Gen.const(_))

    DeriveGen.instance(
      Gen
        .oneOf[Random, Char](whiteSpaceGens: _*)
        .map(char => Refined.unsafeApply(char))
    )
  }
}
