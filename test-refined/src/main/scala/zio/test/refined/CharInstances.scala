/*
 * Copyright 2021-2024 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.test.refined

import eu.timepit.refined.api.Refined
import eu.timepit.refined.char._
import zio.test.Gen
import zio.test.magnolia.DeriveGen

/**
 * Brings the instances of `CharInstances` into scope: `import
 * zio.test.refined.char._`.
 */
object char extends CharInstances

/**
 * Instances for the `Char` predicates of `eu.timepit.refined.char`: `Digit`,
 * `Letter`, `LowerCase`, `UpperCase` and `Whitespace`.
 */
trait CharInstances {

  /**
   * A generator of `Char Refined Digit` built with `Gen.numericChar`: the
   * characters '0' to '9'.
   */
  val digitGen: Gen[Any, Refined[Char, Digit]] = Gen.numericChar.map(value => Refined.unsafeApply(value))

  /**
   * A generator of `Char Refined Letter` built with `Gen.alphaChar`: the
   * characters 'A' to 'Z' and 'a' to 'z'.
   */
  val letterGen: Gen[Any, Refined[Char, Letter]] = Gen.alphaChar.map(value => Refined.unsafeApply(value))

  /**
   * A generator of `Char Refined LowerCase` built with `Gen.alphaChar` and
   * `toLower`: the characters 'a' to 'z'.
   */
  val lowerCaseGen: Gen[Any, Refined[Char, LowerCase]] =
    Gen.alphaChar.map(value => Refined.unsafeApply(value.toLower))

  /**
   * A generator of `Char Refined UpperCase` built with `Gen.alphaChar` and
   * `toUpper`: the characters 'A' to 'Z'.
   */
  val upperCaseGen: Gen[Any, Refined[Char, UpperCase]] =
    Gen.alphaChar.map(value => Refined.unsafeApply(value.toUpper))

  /**
   * A generator of `Char Refined Whitespace` built with `Gen.whitespaceChars`:
   * the characters for which `Char#isWhitespace` is true, such as the space,
   * tab and line feed.
   */
  val whitespaceGen: Gen[Any, Refined[Char, Whitespace]] =
    Gen.whitespaceChars.map(char => Refined.unsafeApply(char))

  /**
   * The implicit `DeriveGen` for `Char Refined Digit`, built with `Gen.numericChar` like `digitGen`.
   */
  implicit def digitArbitrary: DeriveGen[Refined[Char, Digit]] =
    DeriveGen.instance(Gen.numericChar.map(value => Refined.unsafeApply(value)))

  /**
   * The implicit `DeriveGen` for `Char Refined Letter`; it wraps `letterGen`.
   */
  implicit def letterDeriveGen: DeriveGen[Refined[Char, Letter]] =
    DeriveGen.instance(letterGen)

  /**
   * The implicit `DeriveGen` for `Char Refined LowerCase`; it wraps
   * `lowerCaseGen`.
   */
  implicit def lowerCaseDeriveGen: DeriveGen[Refined[Char, LowerCase]] =
    DeriveGen.instance(lowerCaseGen)

  /**
   * The implicit `DeriveGen` for `Char Refined UpperCase`; it wraps
   * `upperCaseGen`.
   */
  implicit def upperCaseDeriveGen: DeriveGen[Refined[Char, UpperCase]] =
    DeriveGen.instance(upperCaseGen)

  /**
   * The implicit `DeriveGen` for `Char Refined Whitespace`; it wraps
   * `whitespaceGen`.
   */
  implicit def whitespaceDeriveGen: DeriveGen[Refined[Char, Whitespace]] =
    DeriveGen.instance(whitespaceGen)
}
