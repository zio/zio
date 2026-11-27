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
import eu.timepit.refined.internal.WitnessAs
import eu.timepit.refined.numeric.{Greater, Less}
import zio.test.Gen
import zio.test.magnolia.DeriveGen

/**
 * Brings the instances of `NumericInstances` into scope: `import
 * zio.test.refined.numeric._`.
 */
object numeric extends NumericInstances

/**
 * Instances for the `Greater[N]` and `Less[N]` predicates of
 * `eu.timepit.refined.numeric` on `Int`, `Long`, `Short`, `Byte` and `Double`.
 * `N` is the literal type that `WitnessAs` reads as the bound.
 */
trait NumericInstances {

  /**
   * A generator of `Int Refined Greater[N]` built with `Gen.int(N,
   * Int.MaxValue)`: from N to Int.MaxValue, both inclusive. It can produce N
   * itself, which does not satisfy `Greater[N]`.
   */
  def intGreaterThanGen[N](implicit
    wn: WitnessAs[N, Int]
  ): Gen[Any, Refined[Int, Greater[N]]] = Gen.int(wn.snd, Int.MaxValue).map(Refined.unsafeApply)

  /**
   * A generator of `Long Refined Greater[N]` built with `Gen.long(N,
   * Long.MaxValue)`: from N to Long.MaxValue, both inclusive. It can produce N
   * itself, which does not satisfy `Greater[N]`.
   */
  def longGreaterThanGen[N](implicit
    wn: WitnessAs[N, Long]
  ): Gen[Any, Refined[Long, Greater[N]]] = Gen.long(wn.snd, Long.MaxValue).map(Refined.unsafeApply)

  /**
   * A generator of `Short Refined Greater[N]` built with `Gen.short(N,
   * Short.MaxValue)`: from N to Short.MaxValue, both inclusive. It can produce
   * N itself, which does not satisfy `Greater[N]`.
   */
  def shortGreaterThanGen[N](implicit
    wn: WitnessAs[N, Short]
  ): Gen[Any, Refined[Short, Greater[N]]] = Gen.short(wn.snd, Short.MaxValue).map(Refined.unsafeApply)

  /**
   * A generator of `Byte Refined Greater[N]` built with `Gen.byte(N,
   * Byte.MaxValue)`: from N to Byte.MaxValue, both inclusive. It can produce N
   * itself, which does not satisfy `Greater[N]`.
   */
  def byteGreaterThanGen[N](implicit
    wn: WitnessAs[N, Byte]
  ): Gen[Any, Refined[Byte, Greater[N]]] = Gen.byte(wn.snd, Byte.MaxValue).map(Refined.unsafeApply)

  /**
   * A generator of `Double Refined Greater[N]` built with `Gen.double(N,
   * Double.MaxValue)`: from N (inclusive) to Double.MaxValue (exclusive).
   */
  def doubleGreaterThanGen[N](implicit
    wn: WitnessAs[N, Double]
  ): Gen[Any, Refined[Double, Greater[N]]] = Gen.double(wn.snd, Double.MaxValue).map(Refined.unsafeApply)

  /**
   * A generator of `Int Refined Less[N]` built with `Gen.int(Int.MinValue, N)`:
   * from Int.MinValue to N, both inclusive. It can produce N itself, which does
   * not satisfy `Less[N]`.
   */
  def intLessThanGen[N](implicit
    wn: WitnessAs[N, Int]
  ): Gen[Any, Refined[Int, Less[N]]] = Gen.int(Int.MinValue, wn.snd).map(Refined.unsafeApply)

  /**
   * A generator of `Long Refined Less[N]` built with `Gen.long(Long.MinValue,
   * N)`: from Long.MinValue to N, both inclusive. It can produce N itself,
   * which does not satisfy `Less[N]`.
   */
  def longLessThanGen[N](implicit
    wn: WitnessAs[N, Long]
  ): Gen[Any, Refined[Long, Less[N]]] = Gen.long(Long.MinValue, wn.snd).map(Refined.unsafeApply)

  /**
   * A generator of `Short Refined Less[N]` built with
   * `Gen.short(Short.MinValue, N)`: from Short.MinValue to N, both inclusive.
   * It can produce N itself, which does not satisfy `Less[N]`.
   */
  def shortLessThanGen[N](implicit
    wn: WitnessAs[N, Short]
  ): Gen[Any, Refined[Short, Less[N]]] = Gen.short(Short.MinValue, wn.snd).map(Refined.unsafeApply)

  /**
   * A generator of `Byte Refined Less[N]` built with `Gen.byte(Byte.MinValue,
   * N)`: from Byte.MinValue to N, both inclusive. It can produce N itself,
   * which does not satisfy `Less[N]`.
   */
  def byteLessThanGen[N](implicit
    wn: WitnessAs[N, Byte]
  ): Gen[Any, Refined[Byte, Less[N]]] = Gen.byte(Byte.MinValue, wn.snd).map(Refined.unsafeApply)

  /**
   * A generator of `Double Refined Less[N]` built with
   * `Gen.double(Double.MinValue, N)`: from Double.MinValue (inclusive) to N
   * (exclusive).
   */
  def doubleLessThanGen[N](implicit
    wn: WitnessAs[N, Double]
  ): Gen[Any, Refined[Double, Less[N]]] = Gen.double(Double.MinValue, wn.snd).map(Refined.unsafeApply)

  /**
   * The implicit `DeriveGen` for `Int Refined Greater[N]`; it wraps
   * `intGreaterThanGen`.
   */
  implicit def intGreaterThan[N](implicit
    wn: WitnessAs[N, Int]
  ): DeriveGen[Int Refined Greater[N]] = DeriveGen.instance(intGreaterThanGen)

  /**
   * The implicit `DeriveGen` for `Long Refined Greater[N]`; it wraps
   * `longGreaterThanGen`.
   */
  implicit def longGreaterThan[N](implicit
    wn: WitnessAs[N, Long]
  ): DeriveGen[Long Refined Greater[N]] =
    DeriveGen.instance(longGreaterThanGen)

  /**
   * The implicit `DeriveGen` for `Short Refined Greater[N]`; it wraps
   * `shortGreaterThanGen`.
   */
  implicit def shortGreaterThan[N](implicit
    wn: WitnessAs[N, Short]
  ): DeriveGen[Short Refined Greater[N]] =
    DeriveGen.instance(shortGreaterThanGen)

  /**
   * The implicit `DeriveGen` for `Byte Refined Greater[N]`; it wraps
   * `byteGreaterThanGen`.
   */
  implicit def byteGreaterThan[N](implicit
    wn: WitnessAs[N, Byte]
  ): DeriveGen[Byte Refined Greater[N]] =
    DeriveGen.instance(byteGreaterThanGen)

  /**
   * The implicit `DeriveGen` for `Double Refined Greater[N]`; it wraps
   * `doubleGreaterThanGen`.
   */
  implicit def doubleGreaterThan[N](implicit
    wn: WitnessAs[N, Double]
  ): DeriveGen[Double Refined Greater[N]] =
    DeriveGen.instance(doubleGreaterThanGen)

  /**
   * The implicit `DeriveGen` for `Int Refined Less[N]`; it wraps
   * `intLessThanGen`.
   */
  implicit def intLessThan[N](implicit
    wn: WitnessAs[N, Int]
  ): DeriveGen[Int Refined Less[N]] =
    DeriveGen.instance(intLessThanGen)

  /**
   * The implicit `DeriveGen` for `Long Refined Less[N]`; it wraps
   * `longLessThanGen`.
   */
  implicit def longLessThan[N](implicit
    wn: WitnessAs[N, Long]
  ): DeriveGen[Long Refined Less[N]] =
    DeriveGen.instance(longLessThanGen)

  /**
   * The implicit `DeriveGen` for `Short Refined Less[N]`; it wraps
   * `shortLessThanGen`.
   */
  implicit def shortLessThan[N](implicit
    wn: WitnessAs[N, Short]
  ): DeriveGen[Short Refined Less[N]] =
    DeriveGen.instance(shortLessThanGen)

  /**
   * The implicit `DeriveGen` for `Byte Refined Less[N]`; it wraps
   * `byteLessThanGen`.
   */
  implicit def byteLessThan[N](implicit
    wn: WitnessAs[N, Byte]
  ): DeriveGen[Byte Refined Less[N]] =
    DeriveGen.instance(byteLessThanGen)

  /**
   * The implicit `DeriveGen` for `Double Refined Less[N]`; it wraps
   * `doubleLessThanGen`.
   */
  implicit def doubleLessThan[N](implicit
    wn: WitnessAs[N, Double]
  ): DeriveGen[Double Refined Less[N]] =
    DeriveGen.instance(doubleLessThanGen)

}
