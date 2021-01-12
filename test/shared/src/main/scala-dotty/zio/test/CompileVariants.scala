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

import zio.test.Macros.location
import zio.{UIO, ZIO}

import scala.annotation.tailrec
import scala.compiletime.testing.typeChecks

trait CompileVariants {

  /**
   * Returns either `Right` if the specified string type checks as valid Scala
   * code or `Left` with an error message otherwise. Dies with a runtime
   * exception if specified string cannot be parsed or is not a known value at
   * compile time.
   */
  inline final def typeCheck(inline code: String): UIO[Either[String, Unit]] =
    try {
      if (typeChecks(code)) UIO.succeedNow(Right(()))
      else UIO.succeedNow(Left(errorMessage))
    } catch {
      case _: Throwable => UIO.die(new RuntimeException("Compilation failed"))
    }

  private val errorMessage =
    "Reporting of compilation error messages on Dotty is not currently supported due to instability of the underlying APIs."

  /**
   * Checks the assertion holds for the given value.
   */
  private[test] def assertImpl[A](value: => A, expression: Option[String] = None, sourceLocation: Option[String] = None)
                                 (assertion: Assertion[A]): TestResult
  /**
   * Checks the assertion holds for the given effectfully-computed value.
   */
  private[test] def assertMInternal[R, E, A](effect: ZIO[R, E, A], sourceLocation: Option[String] = None)
                                            (assertion: AssertionM[A]): ZIO[R, E, TestResult]


  inline def assert[A](inline value: => A)(inline assertion: Assertion[A]): TestResult = ${Macros.assert_impl('value)('assertion)}

  inline def assertM[R, E, A](effect: ZIO[R, E, A])(assertion: AssertionM[A]): ZIO[R, E, TestResult] = ${Macros.assertM_impl('effect)('assertion)}

  private[zio] inline def sourcePath: String = ${Macros.sourcePath_impl}

  private[zio] inline def showExpression[A](inline value: => A): String = ${Macros.showExpression_impl('value)}
}

object CompileVariants {
  /**
   * just a proxy to call package private assertRuntime from the macro
   */
  def assertImpl[A](value: => A, expression: String, sourceLocation: String)(assertion: Assertion[A]): TestResult =
    zio.test.assertImpl(value, Some(expression), Some(sourceLocation))(assertion)

  def assertMInternal[R, E, A](effect: ZIO[R, E, A], sourceLocation: String)
                              (assertion: AssertionM[A]): ZIO[R, E, TestResult] =
    zio.test.assertMInternal(effect, Some(sourceLocation))(assertion)
}

object Macros {
  import scala.quoted._

  private def location(ctx: Quotes): (String, Int) = {
    import ctx.reflect._
    val path = Position.ofMacroExpansion.sourceFile.jpath.toString
    val line = Position.ofMacroExpansion.startLine + 1
    (path, line)
  }

  def assert_impl[A](value: Expr[A])(assertion: Expr[Assertion[A]])(using ctx: Quotes, tp: Type[A]): Expr[TestResult] = {
    import quotes.reflect._
    val (path, line) = location(ctx)
    val code = showExpr(value)
    val srcLocation = s"$path:$line"
    '{_root_.zio.test.CompileVariants.assertImpl[A]($value, ${Expr(code)}, ${Expr(srcLocation)})($assertion)}
  }

  def assertM_impl[R: Type, E: Type, A: Type](effect: Expr[ZIO[R, E, A]])(assertion: Expr[AssertionM[A]])
                           (using ctx: Quotes): Expr[ZIO[R, E, TestResult]] = {
    import quotes.reflect._
    val (path, line) = location(ctx)
    val srcLocation = s"$path:$line"
    '{_root_.zio.test.CompileVariants.assertMInternal($effect, ${Expr(srcLocation)})($assertion)}
  }

  private def showExpr[A](expr: Expr[A])(using ctx: Quotes): String = {
    import quotes.reflect._
    Term.of(expr).pos.sourceCode.get
  }

  def sourcePath_impl(using ctx: Quotes): Expr[String] = {
    import quotes.reflect._
    Expr(Position.ofMacroExpansion.sourceFile.jpath.toString)
  }
  def showExpression_impl[A](value: Expr[A])(using ctx: Quotes) = {
    import quotes.reflect._
    Expr(showExpr(value))
  }
}
