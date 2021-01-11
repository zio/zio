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

import zio.{UIO, ZIO}

trait CompileVariants {

  /**
   * Returns either `Right` if the specified string type checks as valid Scala
   * code or `Left` with an error message otherwise. Dies with a runtime
   * exception if specified string cannot be parsed or is not a known value at
   * compile time.
   */
  final def typeCheck(code: String): UIO[Either[String, Unit]] =
    macro Macros.typeCheck_impl

  private[test] def assertImpl[A](
    value: => A,
    expression: Option[String] = None,
    sourceLocation: Option[String] = None
  )(assertion: Assertion[A]): TestResult

  /**
   * Checks the assertion holds for the given effectfully-computed value.
   */
  private[test] def assertMInternal[R, E, A](effect: ZIO[R, E, A], sourceLocation: Option[String] = None)(
    assertion: AssertionM[A]
  ): ZIO[R, E, TestResult]

  /**
   * Checks the assertion holds for the given value.
   */
  def assert[A](expr: => A)(assertion: Assertion[A]): TestResult = macro Macros.assert_impl

  /**
   * Checks the assertion holds for the given effectfully-computed value.
   */
  def assertM[R, E, A](effect: ZIO[R, E, A])(assertion: AssertionM[A]): ZIO[R, E, TestResult] =
    macro Macros.assertM_impl

  private[zio] def sourcePath: String = macro Macros.sourcePath_impl

  private[zio] def showExpression[A](expr: => A): String = macro Macros.showExpression_impl
}

object CompileVariants {

  /**
   * just a proxy to call package private assertRuntime from the macro
   */
  def assertImpl[A](value: => A, expression: String, sourceLocation: String)(assertion: Assertion[A]): TestResult =
    zio.test.assertImpl(value, Some(expression), Some(sourceLocation))(assertion)

  def assertMInternal[R, E, A](effect: ZIO[R, E, A], sourceLocation: String)(
    assertion: AssertionM[A]
  ): ZIO[R, E, TestResult] =
    zio.test.assertMInternal(effect, Some(sourceLocation))(assertion)
}
