/*
 * Copyright 2017-2020 John A. De Goes and the ZIO Contributors
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

package zio.test.mock

import zio.test.Assertion

/**
 * An `ArgumentExpectation[M, I, A]` represents an expectation on input `I` arguments
 * for capability of module `M` that returns an effect that may produce a single `A`.
 */
final case class ArgumentExpectation[-M, I, +A](method: Method[M, I, A], assertion: Assertion[I]) {

  /**
   * Provides the `ReturnExpectation` to produce the final `Expectation`.
   */
  def returns[A1 >: A, E](returns: ReturnExpectation[I, E, A1]): Expectation[M, E, A1] =
    Expectation.Call[M, I, E, A1](method, assertion, returns.io)
}
