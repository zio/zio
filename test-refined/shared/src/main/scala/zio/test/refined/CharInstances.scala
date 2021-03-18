/*
 * Copyright 2021 John A. De Goes and the ZIO Contributors
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
import zio.Has
import zio.Random
import zio.test.Gen
import zio.test.magnolia.DeriveGen

object char extends CharInstances

trait CharInstances {

  val digitGen: Gen[Has[Random], Refined[Char, Digit]]   = Gen.numericChar.map(value => Refined.unsafeApply(value))
  val letterGen: Gen[Has[Random], Refined[Char, Letter]] = Gen.alphaChar.map(value => Refined.unsafeApply(value))
  val lowerCaseGen: Gen[Has[Random], Refined[Char, LowerCase]] =
    Gen.alphaChar.map(value => Refined.unsafeApply(value.toLower))
  val upperCaseGen: Gen[Has[Random], Refined[Char, UpperCase]] =
    Gen.alphaChar.map(value => Refined.unsafeApply(value.toUpper))
  val whitespaceGen: Gen[Has[Random], Refined[Char, Whitespace]] = Gen
    .oneOf[Has[Random], Char](Gen.whitespaceChars.map(Gen.const(_)): _*)
    .map(char => Refined.unsafeApply(char))

  implicit def digitArbitrary: DeriveGen[Refined[Char, Digit]] =
    DeriveGen.instance(Gen.numericChar.map(value => Refined.unsafeApply(value)))

  implicit def letterDeriveGen: DeriveGen[Refined[Char, Letter]] =
    DeriveGen.instance(letterGen)

  implicit def lowerCaseDeriveGen: DeriveGen[Refined[Char, LowerCase]] =
    DeriveGen.instance(lowerCaseGen)

  implicit def upperCaseDeriveGen: DeriveGen[Refined[Char, UpperCase]] =
    DeriveGen.instance(upperCaseGen)

  implicit def whitespaceDeriveGen: DeriveGen[Refined[Char, Whitespace]] =
    DeriveGen.instance(whitespaceGen)
}
