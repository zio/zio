/*
 * Copyright 2019-2022 John A. De Goes and the ZIO Contributors
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

import zio.{ZIO, ZTraceElement}
import zio.stacktracer.TracingImplicits.disableAutoTrace

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

sealed abstract class AssertionZIOData {
  type Value
  def value: Value
  val assertion: AssertionZIO[Value]

  lazy val asFailure: AssertResult = BoolAlgebra.failure(AssertionValue(assertion, value, result = asFailure))
  lazy val asSuccess: AssertResult = BoolAlgebra.success(AssertionValue(assertion, value, result = asSuccess))

  def asFailureZIO(implicit trace: ZTraceElement): AssertResultZIO = BoolAlgebraZIO(ZIO.succeed(asFailure))
  def asSuccessZIO(implicit trace: ZTraceElement): AssertResultZIO = BoolAlgebraZIO(ZIO.succeed(asSuccess))
}

object AssertionZIOData {
  def apply[A](assertion0: AssertionZIO[A], value0: => A): AssertionZIOData =
    new AssertionZIOData {
      type Value = A
      lazy val value: Value            = value0
      val assertion: AssertionZIO[Value] = assertion0
    }
}
