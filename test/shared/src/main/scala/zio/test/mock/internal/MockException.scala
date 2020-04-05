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

package zio.test.mock.internal

import zio.Has
import zio.test.mock.{ Capability, Expectation }

/**
 * A `MockException` is used internally by the mock framework to signal
 * failed expectations to the test framework.
 */
sealed trait MockException extends Throwable

object MockException {

  final case class UnsatisfiedExpectationsException[R <: Has[_]](
    expectation: Expectation[R]
  ) extends MockException

  final case class UnexpectedCallExpection[R <: Has[_], I >: Nothing, E >: Nothing, A >: Nothing](
    capability: Capability[R, I, E, A],
    args: Any
  ) extends MockException

  final case class InvalidRangeException(
    range: Range
  ) extends MockException

  final case class InvalidCallException(
    failedMatches: List[InvalidCall]
  ) extends MockException
}
