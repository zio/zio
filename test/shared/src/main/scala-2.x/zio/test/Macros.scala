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

  private val fieldInAnonymousClassPrefix = "$anon.this."

  def assert_impl(c: blackbox.Context)(expr: c.Tree)(assertion: c.Tree): c.Tree = {
    import c.universe._
    val fileName = c.enclosingPosition.source.path
    val line     = c.enclosingPosition.line
    val code     = s"${showCode(expr).stripPrefix(fieldInAnonymousClassPrefix)}"
    val label    = s"expression: `$code` (at $fileName:$line))"
    q"_root_.zio.test.assertRuntime($expr)($assertion.label($label))"
  }
}
