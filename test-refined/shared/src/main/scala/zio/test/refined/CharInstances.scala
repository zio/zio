package zio.test.refined

import eu.timepit.refined.api.Refined
import eu.timepit.refined.char._
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

  val digitGen: Gen[Random, Refined[Char, Digit]]   = numericCharGen.map(value => Refined.unsafeApply(value))
  val letterGen: Gen[Random, Refined[Char, Letter]] = alphaCharGen.map(value => Refined.unsafeApply(value))
  val lowerCaseGen: Gen[Random, Refined[Char, LowerCase]] =
    alphaCharGen.map(value => Refined.unsafeApply(value.toLower))
  val upperCaseGen: Gen[Random, Refined[Char, UpperCase]] =
    alphaCharGen.map(value => Refined.unsafeApply(value.toUpper))
  val whitespaceGen: Gen[Random, Refined[Char, Whitespace]] = Gen
    .oneOf[Random, Char](whitespaceChars.map(Gen.const(_)): _*)
    .map(char => Refined.unsafeApply(char))

  implicit def digitArbitrary: DeriveGen[Refined[Char, Digit]] =
    DeriveGen.instance(digitGen)

  implicit def letterDeriveGen: DeriveGen[Refined[Char, Letter]] =
    DeriveGen.instance(letterGen)

  implicit def lowerCaseDeriveGen: DeriveGen[Refined[Char, LowerCase]] =
    DeriveGen.instance(lowerCaseGen)

  implicit def upperCaseDeriveGen: DeriveGen[Refined[Char, UpperCase]] =
    DeriveGen.instance(upperCaseGen)

  implicit def whitespaceDeriveGen: DeriveGen[Refined[Char, Whitespace]] =
    DeriveGen.instance(whitespaceGen)
}
