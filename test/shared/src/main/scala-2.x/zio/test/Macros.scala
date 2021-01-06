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
    val cleanedAst = TreeCleaner.clean(c)(expr)
    val code       = showCode(cleanedAst)
    TreeCleaner.postProcess(code)
  }

  def showExpression_impl(c: blackbox.Context)(expr: c.Tree): c.Tree = {
    import c.universe._
    q"${showExpr(c)(expr)}"
  }
}

/**
 * removes visual clutter from scala reflect Trees:
 */
object TreeCleaner {
  private val magicQuote = "-- $%^*"
  private val startQuote = s"`$magicQuote"
  private val endQuote   = s"$magicQuote`"

  def postProcess(code: String): String =
    code
      .replace(startQuote, "\"")
      .replace(endQuote, "\"")

  def clean(c: blackbox.Context)(expr: c.Tree): c.Tree = {
    import c.universe._
    object PackageSelects {
      def unapply(tree: c.Tree): Option[String] = packageSelects(c)(tree)
    }
    expr match {
      // remove type parameters from methods: foo[Int](args) => foo(args)
      case Apply(TypeApply(t, _), args) => Apply(clean(c)(t), args.map(clean(c)(_)))
      case Apply(t, args)               => Apply(clean(c)(t), args.map(clean(c)(_)))
      // foo.apply => foo
      case Select(PackageSelects(n), TermName("apply")) => Ident(TermName(cleanTupleTerm(n)))
      case Select(This(_), tn)                          => Ident(tn)
      case Select(left, TermName("apply"))              => clean(c)(left)
      case PackageSelects(n)                            => Ident(TermName(cleanTupleTerm(n)))
      case Select(t, n)                                 => Select(clean(c)(t), n)
      case l @ Literal(Constant(s: String)) =>
        if (s.contains("\n")) Ident(TermName(s"$magicQuote${s.replace("\n", "\\n")}$magicQuote"))
        else l
      case t => t
    }
  }

  private def cleanTupleTerm(n: String) =
    if (n.matches("Tuple\\d+")) "" else n

  private def packageSelects(c: blackbox.Context)(select: c.universe.Tree): Option[String] = {
    import c.universe._
    select match {
      case Select(nested @ Select(_, _), TermName(name))                => packageSelects(c)(nested).map(_ => name)
      case Select(id @ Ident(_), TermName(what)) if id.symbol.isPackage => Some(what)
      case _                                                            => None
    }
  }
}
