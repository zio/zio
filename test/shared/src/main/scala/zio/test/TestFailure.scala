/*
 * Copyright 2019-2020 John A. De Goes and the ZIO Contributors
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

import zio.Cause

sealed trait TestFailure[+E]

object TestFailure {
  final case class Assertion(result: TestResult) extends TestFailure[Nothing]
  final case class Runtime[+E](cause: Cause[E])  extends TestFailure[E]

  /**
   * Constructs a new assertion failure with the specified result.
   */
  def assertion(result: TestResult): TestFailure[Nothing] =
    Assertion(result)

  /**
   * Constructs a new runtime failure with the specified cause.
   */
  def die[E](cause: Cause[E]): TestFailure[E] =
    Runtime(cause)

  /**
   * Constructs a new runtime failure with the specified error.
   */
  def fail[E](e: E): TestFailure[E] =
    Runtime(Cause.fail(e))
}
