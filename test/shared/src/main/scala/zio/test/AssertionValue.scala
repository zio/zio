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

/**
 * An `AssertionValue` keeps track of a assertion and a value, existentially
 * hiding the type. This is used internally by the library to provide useful
 * error messages in the event of test failures.
 */
sealed trait AssertionValue {
  type Value

  def value: Value

  def assertion: Assertion[Value]

  def negate: AssertionValue = AssertionValue(assertion.negate, value)
}

object AssertionValue {
  def apply[A](assertion0: Assertion[A], value0: => A): AssertionValue =
    new AssertionValue {
      type Value = A

      lazy val value = value0

      val assertion = assertion0
    }
}
