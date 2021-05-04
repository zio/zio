package zio.test

import scala.annotation.tailrec
import scala.reflect.macros.blackbox

class SmartAssertMacros(val c: blackbox.Context) {
  import c.universe._

  private val Assertion = q"zio.test.Assertion"

  private[test] def location(c: blackbox.Context): (String, Int) = {
    val path = c.enclosingPosition.source.path
    val line = c.enclosingPosition.line
    (path, line)
  }

  // Pilfered with immense gratitude from https://github.com/com-lihaoyi/sourcecode
  def text[T: c.WeakTypeTag](tree: c.Tree): String = {
    val fileContent = new String(tree.pos.source.content)
    val start = tree.collect { case treeVal =>
      treeVal.pos match {
        case NoPosition => Int.MaxValue
        case p          => p.startOrPoint
      }
    }.min
    val g      = c.asInstanceOf[reflect.macros.runtime.Context].global
    val parser = g.newUnitParser(fileContent.drop(start))
    parser.expr()
    val end = parser.in.lastOffset
    fileContent.slice(start, start + end)
  }

  private[this] def getPosition(expr: Tree) = expr.pos.asInstanceOf[scala.reflect.internal.util.Position]

  def assertImpl(expr: c.Tree, exprs: c.Tree*): Expr[TestResult] =
    exprs.map(assertSingle).foldLeft(assertSingle(expr)) { (acc, assert) =>
      c.Expr(q"$acc && $assert")
    }

  def assertSingle(expr: c.Tree): Expr[TestResult] = {
    val (stmts, expr0) = expr match {
      case Block(others, expr) => (others, expr)
      case other               => (List.empty, other)
    }

    implicit val renderContext: RenderContext =
      RenderContext(text(expr0), expr0.pos.start)

    val result = composeAssertions(expr0, false)

    c.Expr[TestResult](
      q"""
      ..$stmts
      $result
      """
    )
  }

  def composeAssertions(expr: c.Tree, negated: Boolean)(implicit
    renderContext: RenderContext
  ): c.Expr[TestResult] = expr match {
    case q"$lhs && $rhs" =>
      val lhsAssertion = composeAssertions(lhs, negated)
      val rhsAssertion = composeAssertions(rhs, negated)
      c.Expr(q"$lhsAssertion && $rhsAssertion")
    case q"$lhs || $rhs" =>
      val lhsAssertion = composeAssertions(lhs, negated)
      val rhsAssertion = composeAssertions(rhs, negated)
      c.Expr(q"$lhsAssertion || $rhsAssertion")
    case q"!$lhs" =>
      val lhsAssertion = composeAssertions(lhs, !negated)(renderContext.shift(expr, lhs))
      c.Expr(q"$lhsAssertion")
    case other =>
      subAssertion(other, negated)
  }

  case class RenderContext(fullText: String, startPos: Int, shiftStart: Int = 0, shiftEnd: Int = 0) {
    def shift(start: Int, end: Int): RenderContext = copy(shiftStart = shiftStart + start, shiftEnd = shiftEnd + end)
    def shift(outer: c.Tree, inner: c.Tree): RenderContext =
      shift(outer.pos.start - inner.pos.start, outer.pos.end - inner.pos.end)

    def text(tree: c.Tree): String = {
      val exprSize = tree.pos.end - tree.pos.start
      val start    = tree.pos.start - startPos
      fullText.slice(start + shiftStart, start + exprSize + shiftEnd)
    }

    def textAfter(t1: c.Tree, t2: c.Tree): String = {
      val exprSize = (t1.pos.end - t1.pos.start) - (t2.pos.end - t2.pos.start)
      val start    = t2.pos.end - startPos
      fullText.slice(start, start + exprSize)
    }
  }

  private def subAssertion(expr: c.Tree, negated: Boolean)(implicit
    renderContext: RenderContext
  ): c.Expr[TestResult] = {
    val (fileName, _) = location(c)
    val srcLocation   = s"$fileName:${expr.pos.line}"

    val text = renderContext.text(expr)

    def negate(tree: c.Tree) = if (negated) q"$tree.negate" else tree

    val (target, assertion) = expr match {
      case q"$lhs > $rhs" =>
        val text = renderContext.text(rhs)
        generateAssertion(lhs, negate(q"$Assertion.isGreaterThan($rhs).withField(${s" > $text"})"))
      case q"$lhs < $rhs" =>
        val text = renderContext.text(rhs)
        generateAssertion(lhs, negate(q"$Assertion.isLessThan($rhs).withField(${s" < $text"})"))
      case q"$lhs >= $rhs" =>
        val text = renderContext.text(rhs)
        generateAssertion(lhs, negate(q"$Assertion.isGreaterThanOrEqualTo($rhs).withField(${s" >= $text"})"))
      case q"$lhs <= $rhs" =>
        val text = renderContext.text(rhs)
        generateAssertion(lhs, negate(q"$Assertion.isLessThanOrEqualTo($rhs).withField(${s" <= $text"})"))
      case q"$lhs == $rhs" =>
        val text = renderContext.text(rhs)
        generateAssertion(lhs, negate(q"$Assertion.equalTo($rhs).withField(${s" == $text"})"))
      case q"$lhs != $rhs" =>
        generateAssertion(lhs, negate(q"$Assertion.equalTo($rhs).negate"))
      case lhs => generateAssertion(lhs, q"$Assertion.isTrue")
    }

    val targetCode = CleanCodePrinter.show(c)(target)

    c.Expr[TestResult](
      q"_root_.zio.test.CompileVariants.smartAssertProxy($target, $targetCode, $text, $srcLocation)($assertion)"
    )
  }

  @tailrec
  private def generateAssertion(expr: c.Tree, assertion: c.Tree)(implicit
    renderContext: RenderContext
  ): (c.Tree, c.Tree) =
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
        val text         = renderContext.textAfter(expr, lhs)
        val newAssertion = q"$Assertion.hasAt($value)($assertion).withField($text)"
        generateAssertion(lhs, newAssertion)
      case Method.intersect(lhs, value) =>
        val newAssertion = q"$Assertion.hasIntersection($value)($assertion)"
        generateAssertion(lhs, newAssertion)
      case Method.length(lhs, _) =>
        val text         = renderContext.textAfter(expr, lhs)
        val newAssertion = q"$Assertion.hasSize($assertion).withField($text)"
        generateAssertion(lhs, newAssertion)
      case Method.isEmpty(lhs, _) =>
        val newAssertion = q"$Assertion.isEmpty"
        generateAssertion(lhs, newAssertion)
      case Method.nonEmpty(lhs, _) =>
        val newAssertion = q"$Assertion.isNonEmpty"
        generateAssertion(lhs, newAssertion)
      case Method.head(lhs, _) =>
        val text         = renderContext.textAfter(expr, lhs)
        val newAssertion = q"$Assertion.hasFirst($assertion).withField($text)"
        generateAssertion(lhs, newAssertion)
      case q"$lhs.get" =>
        val text         = renderContext.textAfter(expr, lhs)
        val newAssertion = q"$Assertion.isSome($assertion).withField($text)"
        generateAssertion(lhs, newAssertion)
      case Select(lhs, name) =>
        val tpe          = lhs.tpe.finalResultType.widen
        val nameString   = name.toString
        val select       = q"(x: $tpe) => x.${TermName(nameString)}"
        val text         = renderContext.textAfter(expr, lhs)
        val newAssertion = q"$Assertion.hasField($nameString, $select, $assertion).withField($text)"
        generateAssertion(lhs, newAssertion)
      case IsConstructor(_) =>
        (expr, assertion)
      case MethodCall(lhs, name, args) =>
        val text         = renderContext.textAfter(expr, lhs)
        val newAssertion = makeApplyAssertion(assertion, lhs, name, args)
        generateAssertion(lhs, q"$newAssertion.withField($text)")
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
