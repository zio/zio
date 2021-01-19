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

object Macros {
  import scala.quoted._

  private def location(ctx: Quotes): (String, Int) = {
    import ctx.reflect._
    val path = Position.ofMacroExpansion.sourceFile.jpath.toString
    val line = Position.ofMacroExpansion.startLine + 1
    (path, line)
  }

  def assertM_impl[R: Type, E: Type, A: Type](effect: Expr[ZIO[R, E, A]])(assertion: Expr[AssertionM[A]])
                                             (using ctx: Quotes): Expr[ZIO[R, E, TestResult]] = {
    import quotes.reflect._
    val (path, line) = location(ctx)
    val srcLocation = s"$path:$line"
    '{_root_.zio.test.CompileVariants.assertMProxy($effect, ${Expr(srcLocation)})($assertion)}
  }

  def assert_impl[A](value: Expr[A])(assertion: Expr[Assertion[A]])(using ctx: Quotes, tp: Type[A]): Expr[TestResult] = {
    import quotes.reflect._
    val (path, line) = location(ctx)
    val code = showExpr(value)
    val srcLocation = s"$path:$line"
    '{_root_.zio.test.CompileVariants.assertProxy($value, ${Expr(code)}, ${Expr(srcLocation)})($assertion)}
  }

  private def showExpr[A](expr: Expr[A])(using ctx: Quotes): String = {
    import quotes.reflect._
    expr.asTerm.pos.sourceCode.get
  }

  def sourceLocation_impl(using ctx: Quotes): Expr[SourceLocation] = {
    import quotes.reflect._
    val (path, line) = location(ctx)
    '{SourceLocation(${Expr(path)}, ${Expr(line)})}
  }

  def sourcePath_impl(using ctx: Quotes): Expr[String] = {
    import quotes.reflect._
    Expr(Position.ofMacroExpansion.sourceFile.jpath.toString)
  }

  def showExpression_impl[A](value: Expr[A])(using ctx: Quotes): Expr[String] = {
    import quotes.reflect._
    Expr(showExpr(value))
  }
}