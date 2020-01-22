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
 * A `MockException` is used internally by the mock framework to
 * signal failed expectations to the test framework.
 */
sealed trait MockException extends Throwable

object MockException {

  final case class InvalidArgumentsException[M, I, A](
    method: Method[M, I, A],
    args: Any,
    assertion: Assertion[Any]
  ) extends MockException

  final case class InvalidMethodException[M0, I0, A0, M1, I1, A1](
    method: Method[M0, I0, A0],
    expectedMethod: Method[M1, I1, A1],
    assertion: Assertion[I1]
  ) extends MockException

  final case class UnmetExpectationsException[M, I >: Nothing, A >: Nothing](
    expectations: List[(Method[M, I, A], Assertion[I])]
  ) extends MockException

  final case class UnexpectedCallExpection[M, I >: Nothing, A >: Nothing](
    method: Method[M, I, A],
    args: Any
  ) extends MockException
}
