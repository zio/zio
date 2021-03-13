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
import eu.timepit.refined.boolean.Or
import zio.random.Random
import zio.test.magnolia.DeriveGen
import zio.test.{Gen, Sized}
import zio.Has

object boolean extends BooleanInstances

trait BooleanInstances {
  implicit def orDeriveGen[T, A, B](implicit
    raGen: DeriveGen[Refined[T, A]],
    rbGen: DeriveGen[Refined[T, B]]
  ): DeriveGen[Refined[T, A Or B]] = {
    val genA: Gen[Has[Random] with Has[Sized], T] = raGen.derive.map(_.value)
    val genB: Gen[Has[Random] with Has[Sized], T] = rbGen.derive.map(_.value)
    DeriveGen.instance(
      Gen.oneOf[Has[Random] with Has[Sized], T](genA, genB).map(Refined.unsafeApply)
    )
  }

  def orGen[R <: Has[Random], T, A, B](implicit
    genA: Gen[R, T],
    genB: Gen[R, T]
  ): Gen[R, Refined[T, A Or B]] = Gen.oneOf(genA, genB).map(Refined.unsafeApply)
}
