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

import scala.math.Ordering

/**
 * A `ExpectationState` represents the state of an expectation tree branch.
 */
private[test] sealed abstract class ExpectationState(val value: Int) extends Ordered[ExpectationState] {
  def compare(that: ExpectationState) = Ordering.Int.compare(this.value, that.value)

  lazy val isFailed: Boolean = this < ExpectationState.Satisfied
}

private[test] object ExpectationState {

  /**
   * Expectation that has yet to be satisfied by invocations.
   *
   * The test will fail, if it ends with expectation in this state.
   */
  case object Unsatisfied extends ExpectationState(0)

  /**
   * Expectation that has been partially satisfied (meaning, there is a chained
   * expectation that has not yet completed).
   *
   * The test will fail, if it ends with expectation in this state.
   */
  case object PartiallySatisfied extends ExpectationState(1)

  /**
   * Expectation that has been satisfied, but could potentially match further calls.
   *
   * The test will succeed, if it ends with expectation in this state.
   */
  case object Satisfied extends ExpectationState(2)

  /**
   * Expectation that has been satisfied and saturated - it cannot match further calls.
   * Will short-circuit and skip ahead to the next expectation when looking for matches.
   *
   * The test will succeed, if it ends with expectation in this state.
   */
  case object Saturated extends ExpectationState(3)
}
