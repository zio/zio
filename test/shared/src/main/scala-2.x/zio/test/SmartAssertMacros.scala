package zio.test

import scala.annotation.tailrec
import scala.reflect.macros.blackbox

object Expect {
  def assert(expr: Boolean): TestResult = macro SmartAssertMacros.assertImpl
}

class SmartAssertMacros(val c: blackbox.Context) {
  import c.universe._

  def assertImpl(expr: c.Tree): Expr[TestResult] = {
//        println(s"NEW $expr")
//        println(s"NEW ${showRaw(expr)}")
    val (target, assertion) = expr match {
      case q"$lhs > $rhs"  => generateAssertion(lhs, q"zio.test.Assertion.isGreaterThan($rhs)")
      case q"$lhs < $rhs"  => generateAssertion(lhs, q"zio.test.Assertion.isLessThan($rhs)")
      case q"$lhs == $rhs" => generateAssertion(lhs, q"zio.test.Assertion.equalTo($rhs)")
      case q"$lhs == $rhs" => generateAssertion(lhs, q"zio.test.Assertion.equalTo($rhs)")
      case q"$lhs != $rhs" => generateAssertion(lhs, q"zio.test.Assertion.equalTo($rhs).negate")
      case q"!$lhs"        => generateAssertion(lhs, q"zio.test.Assertion.isFalse")
      case lhs             => generateAssertion(lhs, q"zio.test.Assertion.isTrue")
    }
//        println("HEY")
//        println(showCode(tree))
//        println(showRaw(tree))
    c.Expr[TestResult](q"zio.test.assertDummy($target)($assertion)")
  }

  def generateAssertion(expr: c.Tree, assertion: c.Tree): (c.Tree, c.Tree) =
    expr match {
      // TODO: Improve safety. Unwrap AssertionOps
      case Method.withAssertion(lhs, assertion) =>
        lhs match {
          case q"$assertionOps($lhs)" => generateAssertion(lhs, assertion)
          case _                      => c.abort(c.enclosingPosition, "FATAL ERROR")
        }
      case Method.containsIterable(lhs, value) =>
        val newAssertion = q"zio.test.Assertion.contains($value)"
        generateAssertion(lhs, newAssertion)
      case Method.containsString(lhs, value) =>
        val newAssertion = q"zio.test.Assertion.containsString($value)"
        generateAssertion(lhs, newAssertion)
      case Method.containsString(lhs, value) =>
        val newAssertion = q"zio.test.Assertion.containsString($value)"
        generateAssertion(lhs, newAssertion)
      case Method.endsWithSeq(lhs, value) =>
        val newAssertion = q"zio.test.Assertion.endsWith($value)"
        generateAssertion(lhs, newAssertion)
      case Method.endsWithString(lhs, value) =>
        val newAssertion = q"zio.test.Assertion.endsWithString($value)"
        generateAssertion(lhs, newAssertion)
      case Method.hasAt(lhs, value) =>
        val newAssertion = q"zio.test.Assertion.hasAt($value)($assertion)"
        generateAssertion(lhs, newAssertion)
      case Method.intersect(lhs, value) =>
        val newAssertion = q"zio.test.Assertion.hasIntersection($value)($assertion)"
        generateAssertion(lhs, newAssertion)
      case Method.length(lhs, _) =>
        val newAssertion = q"zio.test.Assertion.hasSize($assertion)"
        generateAssertion(lhs, newAssertion)
      case Method.isEmpty(lhs, _) =>
        val newAssertion = q"zio.test.Assertion.isEmpty"
        generateAssertion(lhs, newAssertion)
      case Method.nonEmpty(lhs, _) =>
        val newAssertion = q"zio.test.Assertion.isNonEmpty"
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
      case IsConstructor(_) =>
        (expr, assertion)
      case MethodCall(lhs, name, args) =>
        val newAssertion = makeApplyAssertion(assertion, lhs, name, args)
        generateAssertion(lhs, newAssertion)
      case _ =>
        (expr, assertion)
    }

  case class Method[T: c.WeakTypeTag](name: String) {
    def unapply(tree: c.Tree): Option[(c.Tree, c.Tree)] = tree match {
      case q"$lhs.$name0($value)" if name0.toString == name && lhs.tpe <:< weakTypeOf[T] =>
        Some((lhs, value))
      case q"$lhs.$name0[..$_]($value)" if name0.toString == name && lhs.tpe <:< weakTypeOf[T] =>
        Some((lhs, value))
      case q"$lhs.$name0[..$_]" if name0.toString == name && lhs.tpe <:< weakTypeOf[T] =>
        Some((lhs, q"()"))
      case q"$lhs.$name0" if name0.toString == name && lhs.tpe <:< weakTypeOf[T] =>
        Some((lhs, q"()"))
      case _ =>
        None
    }
  }

  object Method {
    val withAssertion: Method[Any] = Method[Any]("withAssertion")

    val containsIterable: Method[Iterable[_]] = Method[Iterable[_]]("contains")
    val containsString: Method[String]        = Method[String]("contains")

    val endsWithSeq: Method[Seq[_]]    = Method[Seq[_]]("endsWith")
    val endsWithString: Method[String] = Method[String]("endsWith")

    val hasAt: Method[Seq[_]] = Method[Seq[_]]("apply")

    val isEmpty: Method[Iterable[_]]   = Method[Iterable[_]]("isEmpty")
    val nonEmpty: Method[Iterable[_]]  = Method[Iterable[_]]("nonEmpty")
    val head: Method[Iterable[_]]      = Method[Iterable[_]]("head")
    val length: Method[Iterable[_]]    = Method[Iterable[_]]("length")
    val intersect: Method[Iterable[_]] = Method[Iterable[_]]("intersect")
  }

  object MethodCall {
    def unapply(tree: c.Tree): Option[(c.Tree, TermName, List[c.Tree])] =
      tree match {
        case q"$lhs.$name(..$args)" =>
          Some((lhs, name, args))
        case q"$lhs.$name[..$_](..$args)" =>
          Some((lhs, name, args))
        case _ => None
      }
  }

  object IsConstructor {
    def unapply(tree: c.Tree): Option[c.Tree] =
      tree match {
        case Apply(_, _) | TypeApply(_, _) if isModuleApply(tree) => Some(tree)
        case _                                                    => None
      }

    @tailrec
    def isModuleApply(tree: c.Tree): Boolean =
      tree match {
        case x: Select if x.symbol.isModule => true
        case Select(s, _)                   => isModuleApply(s)
        case TypeApply(s, _)                => isModuleApply(s)
        case Apply(s, _)                    => isModuleApply(s)
        case _                              => false
      }
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
