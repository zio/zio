package zio.test

import scala.reflect.macros.blackbox

object Expect {
  def assert(expr: Boolean): TestResult = macro SmartAssertMacros.assertImpl
}

class SmartAssertMacros(val c: blackbox.Context) {
  import c.universe._

  def assertImpl(expr: c.Tree): Expr[TestResult] = {
    val tree = expr match {
      case q"$lhs > $rhs" =>
        val (target, assertion) =
          generateAssertion(lhs, q"zio.test.Assertion.isGreaterThan($rhs)")
        q"zio.test.assertDummy($target)($assertion)"
      case q"$lhs < $rhs" =>
        val (target, assertion) =
          generateAssertion(lhs, q"zio.test.Assertion.isLessThan($rhs)")
        q"zio.test.assertDummy($target)($assertion)"
      case q"$lhs == $rhs" =>
        val (target, assertion) =
          generateAssertion(lhs, q"zio.test.Assertion.equalTo($rhs)")
        q"zio.test.assertDummy($target)($assertion)"
      case q"$lhs == $rhs" =>
        val (target, assertion) =
          generateAssertion(lhs, q"zio.test.Assertion.equalTo($rhs)")

        q"zio.test.assertDummy($target)($assertion)"
      case q"$lhs != $rhs" =>
        val (target, assertion) =
          generateAssertion(lhs, q"zio.test.Assertion.equalTo($rhs).negate")

        q"zio.test.assertDummy($target)($assertion)"
      case q"!$lhs" =>
        val (target, assertion) =
          generateAssertion(lhs, q"zio.test.Assertion.isFalse")
        q"zio.test.assertDummy($target)($assertion)"
      case lhs =>
        val (target, assertion) =
          generateAssertion(lhs, q"zio.test.Assertion.isTrue")
        q"zio.test.assertDummy($target)($assertion)"
    }

//    println("HEY")
//    println(showCode(tree))
//    println(showRaw(tree))
    c.Expr[TestResult](tree)
  }

  def generateAssertion(expr: c.Tree, assertion: c.Tree): (c.Tree, c.Tree) =
    expr match {
      case q"$lhs.contains[..$_]($value)" if lhs.tpe <:< weakTypeOf[Iterable[_]] =>
        val newAssertion = q"zio.test.Assertion.contains($value)"
        generateAssertion(lhs, newAssertion)
      case q"$lhs.endsWith[..$_]($value)" if lhs.tpe <:< weakTypeOf[Seq[_]] =>
        val newAssertion = q"zio.test.Assertion.endsWith($value)"
        generateAssertion(lhs, newAssertion)
      case q"$lhs.contains($value)" if lhs.tpe <:< weakTypeOf[String] =>
        val newAssertion = q"zio.test.Assertion.containsString($value)"
        generateAssertion(lhs, newAssertion)
      case q"$lhs.get" =>
        val newAssertion = q"zio.test.Assertion.isSome($assertion)"
        generateAssertion(lhs, newAssertion)
      case Select(lhs, name) =>
        val tpe          = lhs.tpe.finalResultType.widen
        val nameString   = name.toString
        val select       = q"((a: $tpe) => a.${TermName(nameString)})"
        val newAssertion = q"zio.test.Assertion.hasField($nameString, $select, $assertion)"
        generateAssertion(lhs, newAssertion)
      // TODO: Write custom unapply methods
      case q"$lhs.$name(..$args)" =>
        val newAssertion = makeApplyAssertion(assertion, lhs, name, args)
        generateAssertion(lhs, newAssertion)
      case q"$lhs.$name[..$t](..$args)" =>
        val newAssertion = makeApplyAssertion(assertion, lhs, name, args)
        generateAssertion(lhs, newAssertion)
      case other =>
        (expr, assertion)
    }

  private def makeApplyAssertion(assertion: c.Tree, lhs: c.Tree, name: TermName, args: Seq[c.Tree]) = {
    val tpe        = lhs.tpe.finalResultType.widen
    val nameString = name.toString

    val select       = q"((a: $tpe) => a.${TermName(nameString)}(..$args))"
    val applyString  = s"$nameString(${args.toList.map(showCode(_)).mkString(", ")})"
    val newAssertion = q"zio.test.Assertion.hasField($applyString, $select, $assertion)"
    newAssertion
  }
}
