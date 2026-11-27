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
import eu.timepit.refined.string.{EndsWith, StartsWith, Uuid}
import shapeless.Witness
import zio.test.magnolia.DeriveGen
import zio.test.Gen

/**
 * Brings the instances of `StringInstances` into scope: `import
 * zio.test.refined.string._`.
 */
object string extends StringInstances

/**
 * Instances for the `EndsWith[S]`, `StartsWith[S]` and `Uuid` predicates of
 * `eu.timepit.refined.string`.
 *
 * For `EndsWith[S]` and for `StartsWith[S]` there are three implicit
 * `DeriveGen` members (plain, `1` and `N`) with the same result type, so on
 * Scala 2 an implicit search for `DeriveGen[Refined[String, EndsWith[S]]]` or
 * `DeriveGen[Refined[String, StartsWith[S]]]` with all of them in scope, for
 * example through `import zio.test.refined.all._`, reports an ambiguous
 * implicit. On Scala 3, implicit search finds none of them for a literal `S`
 * such as `StartsWith["ab"]`. Call the `*Gen` members to pick one.
 */
trait StringInstances {

  /**
   * A generator of `String Refined EndsWith[S]`: a string from
   * `Gen.string(charGen)`, possibly empty, followed by the witness `S`.
   */
  def endsWithStringGen[R, S <: String](implicit
    ws: Witness.Aux[S],
    charGen: Gen[R, Char]
  ): Gen[R, Refined[String, EndsWith[S]]] = Gen.string(charGen).map(v => Refined.unsafeApply(v + ws.value))

  /**
   * A generator of `String Refined EndsWith[S]`: a non-empty string from
   * `Gen.string1(charGen)` followed by the witness `S`.
   */
  def endsWithString1Gen[R, S <: String](implicit
    ws: Witness.Aux[S],
    charGen: Gen[R, Char]
  ): Gen[R, Refined[String, EndsWith[S]]] =
    Gen.string1(charGen).map(v => Refined.unsafeApply(v + ws.value)) // Only String can call `+` since scala 2.13.0.

  /**
   * A generator of `String Refined EndsWith[S]`: a random part of `n`
   * characters from `charGen`, with `n` drawn from `sizeGen`, followed by the
   * witness `S`. The random part is empty when `n` is zero or negative, so the
   * string is then only `S`.
   */
  def endsWithStringNGen[R, S <: String, P](implicit
    ws: Witness.Aux[S],
    charGen: Gen[R, Char],
    sizeGen: Gen[R, Int Refined P]
  ): Gen[R, Refined[String, EndsWith[S]]] =
    sizeGen.flatMap(s => Gen.stringN(s.value)(charGen).map(v => Refined.unsafeApply(v + ws.value)))

  /**
   * A generator of `String Refined StartsWith[S]`: the witness `S` followed by
   * a string from `Gen.string(charGen)`, possibly empty.
   */
  def startsWithStringGen[R, S <: String](implicit
    ws: Witness.Aux[S],
    charGen: Gen[R, Char]
  ): Gen[R, Refined[String, StartsWith[S]]] = Gen.string(charGen).map(v => Refined.unsafeApply(ws.value + v))

  /**
   * A generator of `String Refined StartsWith[S]`: the witness `S` followed by
   * a non-empty string from `Gen.string1(charGen)`.
   */
  def startsWithString1Gen[R, S <: String](implicit
    ws: Witness.Aux[S],
    charGen: Gen[R, Char]
  ): Gen[R, Refined[String, StartsWith[S]]] = Gen.string1(charGen).map(v => Refined.unsafeApply(ws.value + v))

  /**
   * A generator of `String Refined StartsWith[S]`: the witness `S` followed by
   * a random part of `n` characters from `charGen`, with `n` drawn from
   * `sizeGen`. The random part is empty when `n` is zero or negative, so the
   * string is then only `S`.
   */
  def startsWithStringNGen[R, S <: String, P](implicit
    ws: Witness.Aux[S],
    charGen: Gen[R, Char],
    sizeGen: Gen[R, Int Refined P]
  ): Gen[R, Refined[String, StartsWith[S]]] =
    sizeGen.flatMap(s => Gen.stringN(s.value)(charGen).map(v => Refined.unsafeApply(ws.value + v)))

  /**
   * A generator of `String Refined Uuid` built with `Gen.uuid`: the `toString`
   * form of a generated `java.util.UUID`.
   */
  val uuidStringGen: Gen[Any, Refined[String, Uuid]] =
    Gen.uuid.map(value => Refined.unsafeApply(value.toString))

  /**
   * The implicit `DeriveGen` for `String Refined Uuid`; it wraps
   * `uuidStringGen`.
   */
  implicit val uuidStringDeriveGen: DeriveGen[Refined[String, Uuid]] =
    DeriveGen.instance(uuidStringGen)

  /**
   * The implicit `DeriveGen` for `String Refined EndsWith[S]`; it wraps
   * `endsWithStringGen`. The characters come from the `DeriveGen` of `Char`.
   */
  implicit def endsWithStringDeriveGen[S <: String](implicit
    ws: Witness.Aux[S],
    deriveGenT: DeriveGen[Char]
  ): DeriveGen[Refined[String, EndsWith[S]]] =
    DeriveGen.instance(endsWithStringGen(ws, deriveGenT.derive))

  /**
   * The implicit `DeriveGen` for `String Refined StartsWith[S]`; it wraps
   * `startsWithStringGen`. The characters come from the `DeriveGen` of `Char`.
   */
  implicit def startsWithStringDeriveGen[S <: String](implicit
    ws: Witness.Aux[S],
    deriveGenT: DeriveGen[Char]
  ): DeriveGen[Refined[String, StartsWith[S]]] =
    DeriveGen.instance(startsWithStringGen(ws, deriveGenT.derive))

  /**
   * The implicit `DeriveGen` for `String Refined EndsWith[S]`; it wraps
   * `endsWithString1Gen`. The characters come from the `DeriveGen` of `Char`.
   */
  implicit def endsWithString1DeriveGen[S <: String](implicit
    ws: Witness.Aux[S],
    deriveGenT: DeriveGen[Char]
  ): DeriveGen[Refined[String, EndsWith[S]]] =
    DeriveGen.instance(endsWithString1Gen(ws, deriveGenT.derive))

  /**
   * The implicit `DeriveGen` for `String Refined StartsWith[S]`; it wraps
   * `startsWithString1Gen`. The characters come from the `DeriveGen` of `Char`.
   */
  implicit def startsWithString1DeriveGen[S <: String](implicit
    ws: Witness.Aux[S],
    deriveGenT: DeriveGen[Char]
  ): DeriveGen[Refined[String, StartsWith[S]]] =
    DeriveGen.instance(startsWithString1Gen(ws, deriveGenT.derive))

  /**
   * The implicit `DeriveGen` for `String Refined EndsWith[S]`; it wraps
   * `endsWithStringNGen`. The characters come from the `DeriveGen` of `Char`
   * and the length from the `DeriveGen` of `Int Refined P`.
   */
  implicit def endsWithStringNDeriveGen[S <: String, P](implicit
    ws: Witness.Aux[S],
    deriveGenT: DeriveGen[Char],
    deriveGenSize: DeriveGen[Int Refined P]
  ): DeriveGen[Refined[String, EndsWith[S]]] =
    DeriveGen.instance(endsWithStringNGen(ws, deriveGenT.derive, deriveGenSize.derive))

  /**
   * The implicit `DeriveGen` for `String Refined StartsWith[S]`; it wraps
   * `startsWithStringNGen`. The characters come from the `DeriveGen` of `Char`
   * and the length from the `DeriveGen` of `Int Refined P`.
   */
  implicit def startsWithStringNDeriveGen[S <: String, P](implicit
    ws: Witness.Aux[S],
    deriveGenT: DeriveGen[Char],
    deriveGenSize: DeriveGen[Int Refined P]
  ): DeriveGen[Refined[String, StartsWith[S]]] =
    DeriveGen.instance(startsWithStringNGen(ws, deriveGenT.derive, deriveGenSize.derive))

}
