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

import zio.{UIO, ZIO}
import zio.test.macros._

import scala.quoted._

object SmartAssertMacros {
  def smartAssertSingle(expr: Expr[Boolean])(using ctx: Quotes): Expr[TestResult] =
    new SmartAssertMacros(ctx).smartAssertSingle_impl(expr)

  def smartAssert(expr: Expr[Boolean], exprs: Expr[Seq[Boolean]])(using ctx: Quotes): Expr[TestResult] =
    new SmartAssertMacros(ctx).smartAssert_impl(expr, exprs)
}

class SmartAssertMacros(val ctx: Quotes) extends Scala3 {
  given Quotes = ctx
  import quotes.reflect._

  // inline def assert[A](inline value: => A)(inline assertion: Assertion[A]): TestResult = ${Macros.assert_impl('value)('assertion)}
  def smartAssertSingle_impl(value: Expr[Boolean]): Expr[TestResult] = {
    import quotes.reflect._
    println("SINGLE")
    println(value.show)


    val (path, line) = Macros.location(ctx)
    val code = Macros.showExpr(value)
    val srcLocation = s"$path:$line"

    val (lhs, assertion) = generateAssertion(value.asTerm, '{zio.test.Assertion.isTrue}.asTerm)

    val result = lhs.tpe.widen.asType match {
      case '[a] => 
        '{_root_.zio.test.CompileVariants.smartAssertProxy(${lhs.asExprOf[a]}, ${Expr(code)}, ${Expr(code)}, ${Expr(srcLocation)})(${assertion.asExprOf[Assertion[a]]})}
    }

    println(result.show)
    result
  }

  object Unseal {
    def unapply(expr: Expr[_]): Option[Term] = Some(expr.asTerm)
  }

  def generateAssertion(expr: Term, assertion: Term): (Term, Term) = {


    val M = Cross.Matchers
    val rightGet = M.rightGet
    println("RECURSING")
    println(expr.show)
    println(assertion.show)
    println(Macros.showExpr(expr.asExpr))
    println("")

    expr match {
      case Inlined(_, _, lhs) => generateAssertion(lhs, assertion)
      case rightGet(lhs) => 
        println(lhs)
        // throw new Error("GREAT SUCCESS!")
        (expr, assertion)

      case app @ Apply(s @ Select(lhs, ident), args) => 
        val lhsText = Macros.showExpr(lhs.asExpr)
        val rhsText = Macros.showExpr(expr.asExpr)
        val text = rhsText.drop(lhsText.length)


        val newAssertion = (lhs.tpe.widen.asType, app.tpe.widen.asType) match {
          case ('[a], '[b]) =>
            val select = '{(x: a) => ${Apply(Select.copy(s)('x.asTerm, ident), args).asExprOf[b]}}
           '{
zio.test.Assertion.hasField[a,b](${Expr(ident)}, ${select.asExprOf[a => b]}, ${assertion.asExprOf[Assertion[b]]}.withCode(${Expr(text)})).withCode(${Expr(text)})
  }
        }

        generateAssertion(lhs, newAssertion.asTerm)
      case _ => (expr, assertion)
    }
  }

  // inline def assert[A](inline value: => A)(inline assertion: Assertion[A]): TestResult = ${Macros.assert_impl('value)('assertion)}
  def smartAssert_impl(value: Expr[Boolean], values: Expr[Seq[Boolean]]): Expr[TestResult] = {
    import quotes.reflect._

    val term = value.asTerm
    val treeType = Cross.getTreeType(term)
    println("HOWDY")
    println(treeType)

    val (path, line) = Macros.location(ctx)
    val code = Macros.showExpr(value)
    val srcLocation = s"$path:$line"
    '{_root_.zio.test.CompileVariants.smartAssertProxy($value, ${Expr(code)}, ${Expr(code)}, ${Expr(srcLocation)})(_root_.zio.test.Assertion.isTrue)}
  }
}

object Macros {

  def location(ctx: Quotes): (String, Int) = {
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

  // inline def assert[A](inline value: => A)(inline assertion: Assertion[A]): TestResult = ${Macros.assert_impl('value)('assertion)}
  def assert_impl[A](value: Expr[A])(assertion: Expr[Assertion[A]])(using ctx: Quotes, tp: Type[A]): Expr[TestResult] = {
    import quotes.reflect._
    val (path, line) = location(ctx)
    val code = showExpr(value)
    val srcLocation = s"$path:$line"
    '{_root_.zio.test.CompileVariants.assertProxy($value, ${Expr(code)}, ${Expr(srcLocation)})($assertion)}
  }


  def showExpr[A](expr: Expr[A])(using ctx: Quotes): String = {
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

trait Scala3 { this: SmartAssertMacros =>
  // val ctx: Quotes
  import quotes.reflect._
  // given Quotes = ctx
  
  object Cross extends CrossVersionSmartAssertionMacroUtils[Term, TypeRepr] { 
    val AnyType: TypeRepr = TypeRepr.of[Any]

    val Assertion: Term = '{zio.test.Assertion}.asTerm

    val EitherType: TypeRepr = TypeRepr.of[Either[_,_]]

    def applyApply[A](desc: A => Term, a: (A, Seq[Term])): Term =
      a match {
        case (a, args) =>
          val lhs = desc.apply(a)
          Apply(lhs, args.toList)
      }

    def applySelect[A](desc: A => Term, a: A, name: String): Term = {
      val lhs = desc.apply(a)
      Select.unique(lhs, name)
    }
    

    def getTreeType(tree: Term): TypeRepr =
      tree.asExpr match {
        case '{$t: tpe} => TypeRepr.of[tpe]
      }

    def isSubtype(t1: TypeRepr, t2: TypeRepr): Boolean =
      t1 <:< t2

    case class UnapplyF[A, B](f: A => Option[B]) {
      def unapply(a: A): Option[B] = f(a)
    }

    object Unseal {
      def unapply(expr: Expr[_]): Option[Term] = Some(expr.asTerm)
    }

    def unapplyApply[A](desc: Term => Option[A], tree: Term): Option[(A, Seq[Term])] = {
      val unApplyDesc: UnapplyF[Term, A]= UnapplyF(desc)
      tree.asExpr match {
        case Unseal(Apply(unApplyDesc(a), args)) => Some(a, args)
        case Unseal(TypeApply(Apply(unApplyDesc(a), args), _)) => Some(a, args)
        case _ => None
      }
    }

    def unapplySelect[A](desc: Term => Option[A], name: String, tree: Term): Option[A] = {
      val unApplyDesc: UnapplyF[Term, A] = UnapplyF(desc)
      tree.asExpr match {
        case Unseal(Select(unApplyDesc(lhs), a)) if a.toString == name => Some(lhs)
        case _ => None
      }
    }
  }
}