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

import zio.stacktracer.TracingImplicits.disableAutoTrace
import zio.{UIO, ZIO, ZTraceElement}

import scala.annotation.tailrec
import scala.compiletime.testing.typeChecks

trait CompileVariants {

  /**
   * Returns either `Right` if the specified string type checks as valid Scala
   * code or `Left` with an error message otherwise. Dies with a runtime
   * exception if specified string cannot be parsed or is not a known value at
   * compile time.
   */
  inline def typeCheck(inline code: String): UIO[Either[String, Unit]] =
    try {
      if (typeChecks(code)) UIO.succeedNow(Right(()))
      else UIO.succeedNow(Left(errorMessage))
    } catch {
      case _: Throwable => UIO.die(new RuntimeException("Compilation failed"))
    }

  private val errorMessage =
    "Reporting of compilation error messages on Scala 3 is not currently supported due to instability of the underlying APIs."

  inline def assertTrue(inline exprs: => Boolean*)(implicit trace: ZTraceElement): TestResult =
    ${SmartAssertMacros.smartAssert('exprs, 'trace)}

  inline def assert[A](inline value: => A)(inline assertion: Assertion[A])(implicit trace: ZTraceElement): TestResult =
    ${Macros.assert_impl('value)('assertion, 'trace)}

  inline def assertZIO[R, E, A](effect: ZIO[R, E, A])(assertion: Assertion[A]): ZIO[R, E, TestResult] =
     ${Macros.assertZIO_impl('effect)('assertion)}

  private[zio] inline def showExpression[A](inline value: => A): String = ${Macros.showExpression_impl('value)}
}

/**
 * Proxy methods to call package private methods from the macro
 */
object CompileVariants {

  def assertProxy[A](value: => A, expression: String, assertionCode: String)(
    assertion: Assertion[A]
  )(implicit trace: ZTraceElement): TestResult =
    zio.test.assertImpl(value, Some(expression), Some(assertionCode))(assertion)

  def assertZIOProxy[R, E, A](effect: ZIO[R, E, A], expression: String, assertionCode: String)(
    assertion: Assertion[A],
  )(implicit trace: ZTraceElement): ZIO[R, E, TestResult] =
    zio.test.assertZIOImpl(effect, Some(expression), Some(assertionCode))(assertion)
}
