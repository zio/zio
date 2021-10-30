/*
 * Copyright 2021 John A. De Goes and the ZIO Contributors
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
import zio.internal.macros.CleanCodePrinter

import scala.reflect.macros.{TypecheckException, blackbox}

private[test] object Macros {

  def typeCheck_impl(c: blackbox.Context)(code: c.Expr[String]): c.Expr[UIO[Either[String, Unit]]] = {
    import c.universe._
    try {
      c.typecheck(c.parse(c.eval(c.Expr[String](c.untypecheck(code.tree)))))
      c.Expr(q"zio.UIO.succeed(scala.util.Right(()))")
    } catch {
      case e: TypecheckException => c.Expr(q"zio.UIO.succeed(scala.util.Left(${e.getMessage}))")
      case t: Throwable          => c.Expr(q"""zio.UIO.die(new RuntimeException("Compilation failed: " + ${t.getMessage}))""")
    }
  }

  private[test] val fieldInAnonymousClassPrefix = "$anon.this."

  private[test] def location(c: blackbox.Context): (String, Int) = {
    val path = c.enclosingPosition.source.path
    val line = c.enclosingPosition.line
    (path, line)
  }

  def assertM_impl(c: blackbox.Context)(effect: c.Tree)(assertion: c.Tree): c.Tree = {
    import c.universe._
    val (fileName, line) = location(c)
    val srcLocation      = s"$fileName:$line"
    q"_root_.zio.test.CompileVariants.assertMProxy($effect, $srcLocation)($assertion)"
  }

  def assert_impl(c: blackbox.Context)(expr: c.Tree)(assertion: c.Tree): c.Tree = {
    import c.universe._
    val (fileName, line) = location(c)
    val srcLocation      = s"$fileName:$line"
    val code             = CleanCodePrinter.show(c)(expr)
    q"_root_.zio.test.CompileVariants.assertProxy($expr, $code, $srcLocation)($assertion)"
  }

  def sourceLocation_impl(c: blackbox.Context): c.Expr[SourceLocation] = {
    import c.universe._
    val (path, line) = location(c)
    c.Expr[SourceLocation](q"""${c.prefix}($path, $line)""")
  }

  def sourcePath_impl(c: blackbox.Context): c.Tree = {
    import c.universe._
    q"${c.enclosingPosition.source.path}"
  }

  def showExpression_impl(c: blackbox.Context)(expr: c.Tree): c.Tree = {
    import c.universe._
    q"${CleanCodePrinter.show(c)(expr)}"
  }
}
