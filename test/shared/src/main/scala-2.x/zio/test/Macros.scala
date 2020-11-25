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

import scala.reflect.macros.{ TypecheckException, blackbox }

import zio.UIO

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

  def assertImpl[A](value: => A)(assertion: Assertion[A]): TestResult = zio.test.assertImpl(value)(assertion)

  def assert_impl(c: blackbox.Context)(expr: c.Tree)(assertion: c.Tree): c.Tree = {
    import c.universe._
    val fileName = c.enclosingPosition.source.path
    val line     = c.enclosingPosition.line
    val code = showCode(expr)
      .stripPrefix(fieldInAnonymousClassPrefix)
      // for scala 3 compatibility
      .replace(".`package`.", ".")
      // reduce clutter
      .replaceAll("""scala\.([a-zA-Z0-9_]+)""", "$1")
      .replaceAll("""\.apply(\s*[\[(])""", "$1")
    val label = s"assert(`$code`) (at $fileName:$line)"
    q"_root_.zio.test.CompileVariants.assertImpl($expr)($assertion.label($label))"
  }
}
