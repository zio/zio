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

package zio.test

/**
 * An `AssertResult` is the result of running a test, which may be pending,
 * success, or failure.
 */
sealed trait AssertResult { self =>
  import AssertResult._

  /**
   * Negates the assertion.
   */
  final def negate: AssertResult = self match {
    case Pending          => Pending
    case Failure(message) => Success(message.negate)
    case Success(message) => Failure(message.negate)
  }
}
object AssertResult {
  case object Pending                        extends AssertResult
  final case class Success(message: Message) extends AssertResult
  final case class Failure(message: Message) extends AssertResult
}
