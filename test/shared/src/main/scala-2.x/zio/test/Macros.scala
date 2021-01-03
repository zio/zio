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

import zio.UIO

import scala.annotation.tailrec
import scala.reflect.macros.{TypecheckException, blackbox}

private[test] object Macros {

  def typeCheck_impl(c: blackbox.Context)(code: c.Expr[String]): c.Expr[UIO[Either[String, Unit]]] = {
    import c.universe._
    try {
      c.typecheck(c.parse(c.eval(c.Expr[String](c.untypecheck(code.tree)))))
      c.Expr(q"zio.UIO.succeed(Right(()))")
    } catch {
      case e: TypecheckException => c.Expr(q"zio.UIO.succeed(Left(${e.getMessage}))")
      case t: Throwable          => c.Expr(q"""zio.UIO.die(new RuntimeException("Compilation failed: " + ${t.getMessage}))""")
    }
  }

  private[test] val fieldInAnonymousClassPrefix = "$anon.this."

  private[test] def location(c: blackbox.Context): (String, Int) = {
    val path = c.enclosingPosition.source.path
    val line = c.enclosingPosition.line
    (path, line)
  }

  def assertImpl[A](value: => A, label: String, location: String)(assertion: Assertion[A]): TestResult =
    zio.test.assertImpl(value, Some(label), Some(location))(assertion)

  def assertM_impl(c: blackbox.Context)(effect: c.Tree)(assertion: c.Tree): c.Tree = {
    import c.universe._
    val (fileName, line) = location(c)
    val srcLocation      = s"$fileName:$line"
    q"_root_.zio.test.CompileVariants.assertMInternal($effect, $srcLocation)($assertion)"
  }

  def assert_impl(c: blackbox.Context)(expr: c.Tree)(assertion: c.Tree): c.Tree = {
    import c.universe._
    val (fileName, line) = location(c)
    val srcLocation      = s"$fileName:$line"
    val code             = showExpr(c)(expr)
    q"_root_.zio.test.CompileVariants.assertImpl($expr, $code, $srcLocation)($assertion)"
  }

  def sourcePath_impl(c: blackbox.Context): c.Tree = {
    import c.universe._
    q"${c.enclosingPosition.source.path}"
  }

  private def showExpr(c: blackbox.Context)(expr: c.Tree) = {
    import c.universe._
    dropQuotes(
      showCode(expr)
        .stripPrefix(fieldInAnonymousClassPrefix)
        // for scala 3 compatibility
        .replace(".`package`.", ".")
        // reduce clutter
        .replaceAll("""scala\.([a-zA-Z0-9_]+)""", "$1")
        .replaceAll("""\.apply(\s*[\[(])""", "$1")
    )
  }

  @tailrec
  private def dropQuotes(str: String): String =
    if (str.startsWith("\"") && str.endsWith("\"")) {
      dropQuotes(str.slice(1, str.length - 1))
    } else str

  def showExpression_impl(c: blackbox.Context)(expr: c.Tree): c.Tree = {
    import c.universe._
    q"${showExpr(c)(expr)}"
  }
}
