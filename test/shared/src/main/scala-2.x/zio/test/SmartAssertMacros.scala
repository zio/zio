package zio.test

import scala.annotation.tailrec
import scala.reflect.macros.blackbox

object Expect {
  def assert(expr: Boolean): TestResult = macro SmartAssertMacros.assertImpl
}

class SmartAssertMacros(val c: blackbox.Context) {
  import c.universe._

  private val Assertion = q"zio.test.Assertion"

  private[test] def location(c: blackbox.Context): (String, Int) = {
    val path = c.enclosingPosition.source.path
    val line = c.enclosingPosition.line
    (path, line)
  }

  def assertImpl(expr: c.Tree): Expr[TestResult] = {
//        println(s"NEW $expr")
//        println(s"NEW ${showRaw(expr)}")
    val (target, assertion) = expr match {
      case q"$lhs > $rhs"  => generateAssertion(lhs, q"$Assertion.isGreaterThan($rhs)")
      case q"$lhs < $rhs"  => generateAssertion(lhs, q"$Assertion.isLessThan($rhs)")
      case q"$lhs == $rhs" => generateAssertion(lhs, q"$Assertion.equalTo($rhs)")
      case q"$lhs == $rhs" => generateAssertion(lhs, q"$Assertion.equalTo($rhs)")
      case q"$lhs != $rhs" => generateAssertion(lhs, q"$Assertion.equalTo($rhs).negate")
      case q"!$lhs"        => generateAssertion(lhs, q"$Assertion.isFalse")
      case lhs             => generateAssertion(lhs, q"$Assertion.isTrue")
    }

    val (fileName, line) = location(c)
    val srcLocation      = s"$fileName:$line"
    val targetCode       = CleanCodePrinter.show(c)(target)
    val exprCode         = CleanCodePrinter.show(c)(expr)

    c.Expr[TestResult](
      q"_root_.zio.test.CompileVariants.smartAssertProxy($target, $targetCode, $exprCode, $srcLocation)($assertion)"
    )

//        println("HEY")
//        println(showCode(tree))
//        println(showRaw(tree))
    // c.Expr[TestResult](q"zio.test.assertDummy($target)($assertion)")
  }

  def generateAssertion(expr: c.Tree, assertion: c.Tree): (c.Tree, c.Tree) =
    expr match {
      // TODO: Improve safety. Unwrap AssertionOps
      case Method.withAssertion(lhs, assertion) =>
        lhs match {
          case q"$_($lhs)" => generateAssertion(lhs, assertion)
          case _           => c.abort(c.enclosingPosition, "FATAL ERROR")
        }
      case Method.containsIterable(lhs, value) =>
        val newAssertion = q"$Assertion.contains($value)"
        generateAssertion(lhs, newAssertion)
      case Method.containsString(lhs, value) =>
        val newAssertion = q"$Assertion.containsString($value)"
        generateAssertion(lhs, newAssertion)
      case Method.containsString(lhs, value) =>
        val newAssertion = q"$Assertion.containsString($value)"
        generateAssertion(lhs, newAssertion)
      case Method.startsWithSeq(lhs, value) =>
        val newAssertion = q"$Assertion.startsWith($value)"
        generateAssertion(lhs, newAssertion)
      case Method.startsWithString(lhs, value) =>
        val newAssertion = q"$Assertion.startsWithString($value)"
        generateAssertion(lhs, newAssertion)
      case Method.endsWithSeq(lhs, value) =>
        val newAssertion = q"$Assertion.endsWith($value)"
        generateAssertion(lhs, newAssertion)
      case Method.endsWithString(lhs, value) =>
        val newAssertion = q"$Assertion.endsWithString($value)"
        generateAssertion(lhs, newAssertion)
      case Method.hasAt(lhs, value) =>
        val newAssertion = q"$Assertion.hasAt($value)($assertion)"
        generateAssertion(lhs, newAssertion)
      case Method.intersect(lhs, value) =>
        val newAssertion = q"$Assertion.hasIntersection($value)($assertion)"
        generateAssertion(lhs, newAssertion)
      case Method.length(lhs, _) =>
        val newAssertion = q"$Assertion.hasSize($assertion)"
        generateAssertion(lhs, newAssertion)
      case Method.isEmpty(lhs, _) =>
        val newAssertion = q"$Assertion.isEmpty"
        generateAssertion(lhs, newAssertion)
      case Method.nonEmpty(lhs, _) =>
        val newAssertion = q"$Assertion.isNonEmpty"
        generateAssertion(lhs, newAssertion)
      case Method.head(lhs, _) =>
        val newAssertion = q"$Assertion.hasFirst($assertion)"
        generateAssertion(lhs, newAssertion)
      case q"$lhs.get" =>
        val newAssertion = q"$Assertion.isSome($assertion)"
        generateAssertion(lhs, newAssertion)
      case Select(lhs, name) =>
        val tpe          = lhs.tpe.finalResultType.widen
        val nameString   = name.toString
        val select       = q"(x: $tpe) => x.${TermName(nameString)}"
        val newAssertion = q"$Assertion.hasField($nameString, $select, $assertion)"
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

    val startsWithSeq: Method[Seq[_]]    = Method[Seq[_]]("startsWith")
    val startsWithString: Method[String] = Method[String]("startsWith")
    val endsWithSeq: Method[Seq[_]]      = Method[Seq[_]]("endsWith")
    val endsWithString: Method[String]   = Method[String]("endsWith")

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
        case Apply(_, _) | TypeApply(_, _) if isConstructor(tree) => Some(tree)
        case _                                                    => None
      }

    @tailrec
    def isConstructor(tree: c.Tree): Boolean =
      tree match {
        case x: Select if x.symbol.isModule => true
        // Matches Case Class constructors
        case x: Select if x.symbol.isSynthetic => true
        case Select(s, _)                      => isConstructor(s)
        case TypeApply(s, _)                   => isConstructor(s)
        case Apply(s, _)                       => isConstructor(s)
        case _                                 => false
      }
  }

  private def makeApplyAssertion(assertion: c.Tree, lhs: c.Tree, name: TermName, args: Seq[c.Tree]) = {
    val tpe        = lhs.tpe.finalResultType.widen
    val nameString = name.toString

    val select       = q"((a: $tpe) => a.${TermName(nameString)}(..$args))"
    val applyString  = s"$nameString(${args.toList.map(showCode(_)).mkString(", ")})"
    val newAssertion = q"$Assertion.hasField($applyString, $select, $assertion)"
    newAssertion
  }
}
