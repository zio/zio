/*
 * Copyright 2017-2019 John A. De Goes and the ZIO Contributors
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

import zio.IO

/**
 * A `ReturnExpectation[-I, E, +A]` represents an expectation on output for capability of module `M`
 * that given input arguments `I` returns an effect that may fail with an error `E` or produce a single `A`.
 */
sealed trait ReturnExpectation[-I, +E, +A] {
  val io: I => IO[E, A]
}

object ReturnExpectation {

  private[mock] final case class Succeed[I, +A](io: I => IO[Nothing, A]) extends ReturnExpectation[I, Nothing, A]
  private[mock] final case class Fail[I, +E](io: I => IO[E, Nothing])    extends ReturnExpectation[I, E, Nothing]
}
