package zio.test

import zio.test.AssertionSyntax.AssertionOps

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

  def assertImpl(expr: c.Tree, exprs: c.Tree*): Expr[TestResult] =
    exprs.map(assertSingle).foldLeft(assertSingle(expr)) { (acc, assert) =>
      c.Expr(q"$acc && $assert")
    }

  def assertSingle(expr: c.Tree): Expr[TestResult] = {
    val (stmts, expr0) = expr match {
      case Block(others, expr) => (others, expr)
      case other               => (List.empty, other)
    }

    println("RAW")
    println(showRaw(expr))

    val (delta, start, codeString) = text(expr0)
    implicit val renderContext: RenderContext =
      RenderContext(codeString, start).shift(-delta, delta)

    val result = composeAssertions(expr0, false)

    println("RESULT")
    println(showCode(result.tree))

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
      // TODO: Reset render context after recursive calle. renderContext.shift() { subtree }
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

    def negate(tree: c.Tree) = if (negated) q"$tree.smartNegate" else tree

    val (target, assertion) = expr match {
      case q"$lhs > $rhs" =>
        val text = renderContext.textAfter(expr, lhs)
        generateAssertion(lhs, negate(q"$Assertion.isGreaterThan($rhs).withCode($text)"))
      case q"$lhs < $rhs" =>
        val text = renderContext.textAfter(expr, lhs)
        generateAssertion(lhs, negate(q"$Assertion.isLessThan($rhs).withCode($text)"))
      case q"$lhs >= $rhs" =>
        val text = renderContext.textAfter(expr, lhs)
        generateAssertion(lhs, negate(q"$Assertion.isGreaterThanOrEqualTo($rhs).withCode($text)"))
      case q"$lhs <= $rhs" =>
        val text = renderContext.textAfter(expr, lhs)
        generateAssertion(lhs, negate(q"$Assertion.isLessThanOrEqualTo($rhs).withCode($text)"))
      case q"$lhs == $rhs" =>
        val text = renderContext.textAfter(expr, lhs)
        generateAssertion(lhs, negate(q"$Assertion.equalTo($rhs).withCode($text)"))
      case q"$lhs != $rhs" =>
        val text = renderContext.textAfter(expr, lhs)
        generateAssertion(lhs, negate(q"$Assertion.equalTo($rhs).withCode($text).smartNegate"))
      case lhs =>
        generateAssertion(lhs, negate(q"$Assertion.isTrue.withCode(${""})"))
    }

    val targetCode = CleanCodePrinter.show(c)(target)

    c.Expr[TestResult](
      q"_root_.zio.test.CompileVariants.smartAssertProxy($target, $targetCode, $text, $srcLocation)($assertion)"
    )
  }

//  @tailrec
  private def generateAssertion(expr: c.Tree, assertion: c.Tree)(implicit
    renderContext: RenderContext
  ): (c.Tree, c.Tree) = {
    val LensMatcher = Method.Matcher(assertion, renderContext)

    expr match {
      // Unwrap implicit conversions/classes
      case q"$lhs($rhs)" if lhs.symbol.isImplicit =>
        generateAssertion(rhs, assertion)
      case Method.withAssertion(lhs, assertion) =>
        val text         = renderContext.textAfter(expr, lhs)
        val newAssertion = q"$assertion.withCode($text)"
        generateAssertion(lhs, newAssertion)
      // TODO: Add custom error message for `forall` and `exists`
      case Method.exists(lhs, args) =>
        val text         = renderContext.textAfter(expr, lhs)
        val newAssertion = q"$Assertion.smartExists($args).withCode($text)"
        generateAssertion(lhs, newAssertion)
//      case Method.exists(lhs, args) =>
//        val text = renderContext.textAfter(expr, lhs)
//        val body = args match {
//          case q"($a => $b)" => b
//        }
//        val (lhs2, nested) = generateAssertion(body, assertion)
//        println("____")
//        println(lhs2)
//        println("____")
//        println(nested)
//        val newAssertion = q"$Assertion.exists($nested).withCode($text)"
//        generateAssertion(lhs, newAssertion)
      case LensMatcher(lhs, newAssertion) =>
        generateAssertion(lhs, newAssertion)
      case Select(lhs, name) =>
        val tpe = lhs match {
          case q"$lhs($rhs)" if lhs.symbol.isImplicit => rhs.tpe.widen.finalResultType.widen
          case _                                      => lhs.tpe.widen.finalResultType.widen
        }
        val nameString   = name.toString
        val select       = q"((x: $tpe) => x.${TermName(nameString)})"
        val text         = renderContext.textAfter(expr, lhs)
        val newAssertion = q"$Assertion.hasField($nameString, $select, $assertion).withCode($text)"
        generateAssertion(lhs, newAssertion)
      case IsConstructor(_) =>
        (expr, assertion)
      case MethodCall(lhs, name, args) =>
        val text         = renderContext.textAfter(expr, lhs)
        val newAssertion = makeApplyAssertion(assertion, lhs, name, args)
        generateAssertion(lhs, q"$newAssertion.withCode($text)")
      case _ =>
        (expr, assertion)
    }
  }

  /**
   * # Terminal assertions
   * - isEmpty
   *   - lhs.isEmpty
   *   - isEmpty
   *
   * # Nullary assertions
   * - get
   *   - lhs.get
   *   - isSome(assertion)
   *
   * # Unary assertions
   * - intersect(arg)
   *   - lhs.intersect(that)
   *   - hasIntersection(arg)(assertion)
   * - containsIterable
   * - containsString
   * - startsWithSeq
   * - startsWithString
   * - endsWithSeq
   * - endsWithString
   *
   * Method[T]("isEmpty")
   */

  case class Method[T](
    name: String,
    assertionName: String,
    hasArgs: Boolean,
    isRecursive: Boolean
  )(implicit tpe: WeakTypeTag[T]) {
    self =>

    def use(assertion: c.Tree, renderContext: RenderContext): PartialFunction[c.Tree, (c.Tree, c.Tree)] = {
      case expr @ self(lhs, args) =>
        val text              = renderContext.textAfter(expr, lhs)
        val assertionNameTree = TermName(assertionName)

        val newAssertion =
          (hasArgs, isRecursive) match {
            case (true, true)   => q"$Assertion.$assertionNameTree($args)($assertion).withCode($text)"
            case (false, true)  => q"$Assertion.$assertionNameTree($assertion).withCode($text)"
            case (true, false)  => q"$Assertion.$assertionNameTree($args).withCode($text)"
            case (false, false) => q"$Assertion.$assertionNameTree.withCode($text)"
          }
        (lhs, newAssertion)
    }

    def unapply(tree: c.Tree): Option[(c.Tree, c.Tree)] = tree match {
      case q"$lhs.$name0($value)" if name0.toString == name && lhs.tpe <:< tpe.tpe =>
        Some((lhs, value))
      case q"$lhs.$name0[..$_]($value)" if name0.toString == name && lhs.tpe <:< tpe.tpe =>
        Some((lhs, value))
      case q"$lhs.$name0[..$_]" if name0.toString == name && lhs.tpe <:< tpe.tpe =>
        Some((lhs, q"()"))
      case q"$lhs.$name0" if name0.toString == name && lhs.tpe <:< tpe.tpe =>
        Some((lhs, q"()"))
      case lhs =>
        None
    }
  }

  object Method {
    def apply[T: c.WeakTypeTag](name: String, hasArgs: Boolean = false, isRecursive: Boolean = false) =
      new Method[T](name, name, hasArgs, isRecursive)

    case class Matcher(assertion: c.Tree, renderContext: RenderContext) {
      def unapply(expr: c.Tree): Option[(c.Tree, c.Tree)] = {
        val result = Method.methods.find { value =>
          value.use(assertion, renderContext).isDefinedAt(expr)
        }.flatMap(_.use(assertion, renderContext).lift(expr))

        result
      }
    }

    lazy val withAssertion: Method[AssertionOps[_]] = Method[AssertionOps[_]]("withAssertion", true, false)

    lazy val containsIterable: Method[Iterable[_]] = Method[Iterable[_]]("contains", true, false)
    lazy val containsString: Method[String]        = Method[String]("contains", "containsString", true, false)

    lazy val startsWithSeq: Method[Seq[_]]    = Method[Seq[_]]("startsWith", true, false)
    lazy val startsWithString: Method[String] = Method[String]("startsWith", "startsWithString", true, false)
    lazy val endsWithSeq: Method[Seq[_]]      = Method[Seq[_]]("endsWith", true, false)
    lazy val endsWithString: Method[String]   = Method[String]("endsWith", "endsWithString", true, false)

    lazy val hasAt: Method[Seq[_]] = Method[Seq[_]]("apply", "hasAt", true, true)

    lazy val exists: Method[Iterable[_]] =
      Method[Iterable[_]]("exists", false, true)

    lazy val isEmpty: Method[Iterable[_]] =
      Method[Iterable[_]]("isEmpty", false, false)

    lazy val nonEmpty: Method[Iterable[_]] =
      Method[Iterable[_]]("nonEmpty", "isNonEmpty", false, false)

    lazy val head: Method[Iterable[_]] =
      Method[Iterable[_]]("head", "hasFirst", false, true)

    lazy val get: Method[Option[_]] =
      Method[Option[_]]("get", "isSome", false, true)

    lazy val rightGet: Method[Either[_, _]] =
      Method[Either[_, _]]("right.get", "isRight", false, true)

    lazy val length: Method[Iterable[_]] =
      Method[Iterable[_]]("length", "hasSize", false, true)

    lazy val intersect: Method[Iterable[_]] =
      Method[Iterable[_]]("intersect", "hasIntersection", true, true)

    lazy val methods: List[Method[_]] =
      List(
        get,
        rightGet,
        length,
        withAssertion,
        containsString,
        containsIterable,
        startsWithSeq,
        startsWithString,
        endsWithSeq,
        endsWithString,
        hasAt,
        isEmpty,
        nonEmpty,
        head,
        intersect
      )
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
    def isConstructor(tree: c.Tree): Boolean = {
      println(s"HEY $tree")
      println(showRaw(tree))

      tree match {
        case Select(Select(s, _), TermName("apply")) if s.symbol.isModule || s.symbol.isSynthetic || s.symbol.isClass =>
          true
        case Select(s, TermName("apply")) if s.symbol.isModule || s.symbol.isSynthetic || s.symbol.isClass => true

        case TypeApply(s, _) => isConstructor(s)
        case Apply(s, _)     => isConstructor(s)
        case _               => false
      }
    }
  }

  private def makeApplyAssertion(assertion: c.Tree, lhs: c.Tree, name: TermName, args: Seq[c.Tree]): c.Tree = {
    val tpe = lhs match {
      case q"$lhs($rhs)" if lhs.symbol.isImplicit => rhs.tpe.widen.finalResultType.widen
      case _                                      => lhs.tpe.widen.finalResultType.widen
    }
    val nameString = name.toString

    val select       = q"((a: $tpe) => a.${TermName(nameString)}(..$args))"
    val applyString  = s"$nameString(${args.toList.map(showCode(_)).mkString(", ")})"
    val newAssertion = q"$Assertion.hasField($applyString, $select, $assertion)"
    newAssertion
  }

  // Pilfered (with immense gratitude & minor modifications)
  // from https://github.com/com-lihaoyi/sourcecode
  private def text[T: c.WeakTypeTag](tree: c.Tree): (Int, Int, String) = {
    val fileContent = new String(tree.pos.source.content)
    var start = tree.collect { case treeVal =>
      treeVal.pos match {
        case NoPosition => Int.MaxValue
        case p          => p.startOrPoint
      }
    }.min
    val initialStart = start

    // Moves to the true beginning of the expression, in the case where the
    // internal expression is wrapped in parens.
    while ((start - 2) >= 0 && fileContent(start - 2) == '(') {
      start -= 1
    }

    val g      = c.asInstanceOf[reflect.macros.runtime.Context].global
    val parser = g.newUnitParser(fileContent.drop(start))
    parser.expr()
    val end = parser.in.lastOffset
    (initialStart - start, start, fileContent.slice(start, start + end))
  }

}
