/*
 * Copyright 2019 John A. De Goes and the ZIO Contributors
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

package zio

package object test {

  /**
   * Builds a suite containing a number of other specs.
   */
  final def suite[R, E](label: String)(specs: Spec[R, E]*): Spec[R, E] = Spec.Suite(label, specs.toVector)

  /**
   * Builds a spec with a single test.
   */
  final def test[R, E](label: String)(assertion: ZIO[R, E, AssertResult]): Spec[R, E] = Spec.Test(label, assertion)

  /**
   * Asserts the given value satisfies the given predicate.
   */
  final def assert[A](value: => A, predicate: Predicate[A]): AssertResult = predicate.run(value)
}
