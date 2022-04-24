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

import zio.{UIO, ZIO, ZTraceElement}
import zio.stacktracer.TracingImplicits.disableAutoTrace

trait CompileVariants {

  /**
   * Returns either `Right` if the specified string type checks as valid Scala
   * code or `Left` with an error message otherwise. Dies with a runtime
   * exception if specified string cannot be parsed or is not a known value at
   * compile time.
   */
  final def typeCheck(code: String): UIO[Either[String, Unit]] =
    macro Macros.typeCheck_impl

  private[zio] def assertImpl[A](
    value: => A,
    expression: Option[String] = None
  )(assertion: Assertion[A])(implicit trace: ZTraceElement): TestResult

  /**
   * Checks the assertion holds for the given effectfully-computed value.
   */
  private[test] def assertZIOImpl[R, E, A](effect: ZIO[R, E, A])(
    assertion: AssertionZIO[A]
  )(implicit trace: ZTraceElement): ZIO[R, E, TestResult]

  /**
   * Checks the assertion holds for the given value.
   */
  def assertTrue(expr: Boolean, exprs: Boolean*): Assert = macro SmartAssertMacros.assert_impl
  def assertTrue(expr: Boolean): Assert = macro SmartAssertMacros.assertOne_impl

  /**
   * Checks the assertion holds for the given value.
   */
  def assert[A](expr: => A)(assertion: Assertion[A]): TestResult = macro Macros.assert_impl
//  def assert(expr: Boolean): TestResult = assert[Boolean](expr)(Assertion.isTrue)

  /**
   * Checks the assertion holds for the given effectfully-computed value.
   */
  def assertZIO[R, E, A](effect: ZIO[R, E, A])(assertion: AssertionZIO[A]): ZIO[R, E, TestResult] =
    macro Macros.assertZIO_impl

  private[zio] def showExpression[A](expr: => A): String = macro Macros.showExpression_impl
}

/**
 * Proxy methods to call package private methods from the macro
 */
object CompileVariants {

  def assertProxy[A](value: => A, expression: String)(
    assertion: Assertion[A]
  )(implicit trace: ZTraceElement): TestResult =
    zio.test.assertImpl(value, Some(expression))(assertion)

  def assertZIOProxy[R, E, A](effect: ZIO[R, E, A])(
    assertion: AssertionZIO[A]
  )(implicit trace: ZTraceElement): ZIO[R, E, TestResult] =
    zio.test.assertZIOImpl(effect)(assertion)
}
