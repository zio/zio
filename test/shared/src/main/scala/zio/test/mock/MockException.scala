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

import zio.test.Assertion

sealed trait MockException extends Throwable

object MockException {

  final case class InvalidArgumentsException[A, B](
    method: Method[A, B],
    args: Any,
    assertion: Assertion[Any]
  ) extends MockException

  final case class InvalidMethodException[A0, B0, A1, B1](
    method: Method[A0, B0],
    expectation: Expectation[A1, B1]
  ) extends MockException

  final case class UnmetExpectationsException[A >: Nothing, B >: Nothing](
    expectations: List[Expectation[A, B]]
  ) extends MockException
}
