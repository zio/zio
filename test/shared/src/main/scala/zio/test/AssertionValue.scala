/*
 * Copyright 2019-2021 John A. De Goes and the ZIO Contributors
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

import zio.test.FailureRenderer.FailureMessage.{Fragment, Message}
import zio.test.FailureRenderer.{FailureMessage, bold, red}

/**
 * An `AssertionValue` keeps track of a assertion and a value, existentially
 * hiding the type. This is used internally by the library to provide useful
 * error messages in the event of test failures.
 */

sealed abstract class AssertionValue {
  type Value
  def value: Value
  def expression: Option[String]
  def sourceLocation: Option[String]
  protected def assertion: AssertionM[Value]
  def result: AssertResult
  def error: Option[Throwable]

  def codeString: String =
    assertion.render.codeString

  def smartExpression: Option[String]
  def renderErrorMessage: FailureMessage.Message =
    error match {
      case Some(value) =>
        (red("ERROR: ") + bold(value.toString)).toMessage ++
          Message(
            value.getStackTrace
              .takeWhile(!_.getClassName.startsWith("zio.test.Assertion"))
              .map(line => Fragment(line.toString).toLine)
          )
      case None =>
        assertion.render.render(value, result.isSuccess)
    }

  def printAssertion: String = assertion.toString
  def label(string: String): AssertionValue =
    AssertionValue(assertion.label(string), value, result, expression, sourceLocation, error = error)
  def sameAssertion(that: AssertionValue): Boolean = assertion == that.assertion

  def negate: AssertionValue =
    AssertionValue(assertion.negate, value, !result, expression, sourceLocation, error = error)
  def withContext(expr: Option[String], sourceLocation: Option[String]): AssertionValue =
    AssertionValue(assertion, value, result, expr, sourceLocation, error = error)
}

object AssertionValue {
  def apply[A](
    assertion: AssertionM[A],
    value: => A,
    result: => AssertResult,
    expression: Option[String] = None,
    smartExpression: Option[String] = None,
    sourceLocation: Option[String] = None,
    error: Option[Throwable] = None
  ): AssertionValue = {
    def inner(
      assertion0: AssertionM[A],
      value0: => A,
      result0: => AssertResult,
      expression0: Option[String],
      smartExpression0: Option[String],
      sourceLocation0: Option[String],
      error0: Option[Throwable] = None
    ) =
      new AssertionValue {
        type Value = A
        protected val assertion: AssertionM[Value]   = assertion0
        lazy val value: Value                        = value0
        lazy val result: AssertResult                = result0
        override val expression: Option[String]      = expression0
        override val smartExpression: Option[String] = smartExpression0
        override val sourceLocation: Option[String]  = sourceLocation0
        lazy val error: Option[Throwable]            = error0
      }
    inner(assertion, value, result, expression, smartExpression, sourceLocation, error)
  }
}
