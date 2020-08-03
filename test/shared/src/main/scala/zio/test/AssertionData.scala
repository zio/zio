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

import zio.ZIO

sealed abstract class AssertionData {
  type Value
  def value: Value
  val assertion: Assertion[Value]

  lazy val asFailure: AssertResult = BoolAlgebra.failure(AssertionValue(assertion, value, result = asFailure))
  lazy val asSuccess: AssertResult = BoolAlgebra.success(AssertionValue(assertion, value, result = asSuccess))
}

object AssertionData {
  def apply[A](assertion0: Assertion[A], value0: => A): AssertionData =
    new AssertionData {
      type Value = A
      lazy val value: Value           = value0
      val assertion: Assertion[Value] = assertion0
    }
}

sealed abstract class AssertionMData {
  type Value
  def value: Value
  val assertion: AssertionM[Value]

  lazy val asFailure: AssertResult = BoolAlgebra.failure(AssertionValue(assertion, value, result = asFailure))
  lazy val asSuccess: AssertResult = BoolAlgebra.success(AssertionValue(assertion, value, result = asSuccess))

  def asFailureM: AssertResultM = BoolAlgebraM(ZIO.succeed(asFailure))
  def asSuccessM: AssertResultM = BoolAlgebraM(ZIO.succeed(asSuccess))
}

object AssertionMData {
  def apply[A](assertion0: AssertionM[A], value0: => A): AssertionMData =
    new AssertionMData {
      type Value = A
      lazy val value: Value            = value0
      val assertion: AssertionM[Value] = assertion0
    }
}
