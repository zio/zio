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

import zio.UIO

trait CompileVariants {

  /**
   * Returns either `Right` if the specified string type checks as valid Scala
   * code or `Left` with an error message otherwise. Dies with a runtime
   * exception if specified string cannot be parsed or is not a known value at
   * compile time.
   */
  final def typeCheck(code: String): UIO[Either[String, Unit]] =
    macro Macros.typeCheck_impl

  private[test] def assertImpl[A](value: => A)(assertion: Assertion[A]): TestResult

  /**
   * Checks the assertion holds for the given value.
   */
  def assert[A](expr: => A)(assertion: Assertion[A]): TestResult = macro Macros.assert_impl

  private[zio] def sourcePath: String = macro Macros.sourcePath_impl

  private[zio] def showExpression[A](expr: => A): String = macro Macros.showExpression_impl
}

object CompileVariants {

  /**
   * just a proxy to call package private assertRuntime from the macro
   */
  def assertImpl[A](value: => A)(assertion: Assertion[A]): TestResult = zio.test.assertImpl(value)(assertion)
}
