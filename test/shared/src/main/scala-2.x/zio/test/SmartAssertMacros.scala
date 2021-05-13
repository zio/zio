package zio.test

import com.github.ghik.silencer.silent
import zio.test.AssertionSyntax.AssertionOps
import zio.test.macros.Scala2MacroUtils

import scala.annotation.tailrec
import scala.reflect.macros.blackbox

class SmartAssertMacros(val c: blackbox.Context) extends Scala2MacroUtils {
  import c.universe._

  private val Assertion = q"zio.test.Assertion"
  private val Assert    = q"zio.test.Assert"

  private[test] def location(c: blackbox.Context): (String, Int) = {
    val path = c.enclosingPosition.source.path
    val line = c.enclosingPosition.line
    (path, line)
  }

  def assertImpl(expr: c.Tree, exprs: c.Tree*): Expr[TestResult] =
    exprs.map(assertSingle).foldLeft(assertSingle(expr)) { (acc, assert) =>
      c.Expr(q"$acc && $assert")
    }

  sealed trait AST { self =>
    def span: (Int, Int)
    def withSpan(span0: (Int, Int)): AST =
      self match {
        case not: AST.Not           => not.copy(span = span0)
        case and: AST.And           => and.copy(span = span0)
        case or: AST.Or             => or.copy(span = span0)
        case to: AST.EqualTo        => to.copy(span = span0)
        case select: AST.Select     => select.copy(span = span0)
        case method: AST.Method     => method.copy(span = span0)
        case function: AST.Function => function.copy(span = span0)
        case raw: AST.Raw           => raw.copy(span = span0)
      }
  }

  object AST {
    case class Not(ast: AST, span: (Int, Int), innerSpan: (Int, Int))                                 extends AST
    case class And(lhs: AST, rhs: AST, span: (Int, Int), leftSpan: (Int, Int), rightSpan: (Int, Int)) extends AST
    case class Or(lhs: AST, rhs: AST, span: (Int, Int), leftSpan: (Int, Int), rightSpan: (Int, Int))  extends AST
    case class EqualTo(lhs: AST, rhs: c.Tree, span: (Int, Int))                                       extends AST
    case class Select(lhs: AST, lhsTpe: Type, rhsTpe: Type, tpes: List[Tree], name: String, span: (Int, Int))
        extends AST
    case class Method(lhs: AST, lhsTpe: Type, rhsTpe: Type, name: String, args: List[c.Tree], span: (Int, Int))
        extends AST
    case class Function(lhs: c.Tree, rhs: AST, lhsTpe: Type, span: (Int, Int)) extends AST
    case class Raw(ast: c.Tree, span: (Int, Int))                              extends AST
  }

  def astToAssertion(ast: AST)(implicit positionContext: PositionContext): c.Tree = {
    println(ast)
    ast match {
      case AST.Not(ast, span, innerSpan) =>
        q"!${astToAssertion(ast)}"

      case AST.And(lhs, rhs, pos, ls, rs) =>
        q"${astToAssertion(lhs)}.withParentSpan($ls) && ${astToAssertion(rhs)}.withParentSpan($rs)"

      case AST.Or(lhs, rhs, pos, ls, rs) =>
        q"${astToAssertion(lhs)}.withParentSpan($ls) || ${astToAssertion(rhs)}.withParentSpan($rs)"

      case AST.EqualTo(lhs, rhs, span) =>
        q"${astToAssertion(lhs)} >>> $Assert.equalTo($rhs).span($span)"

      case AST.Select(lhs, lhsTpe, rhsTpe, List(tpe), "throwsA", span) =>
        q"${astToAssertion(lhs)} >>> $Assert.throwsSubtype[$tpe].span($span)"

      case AST.Select(lhs, lhsTpe, rhsTpe, _, "throws", span) =>
        q"${astToAssertion(lhs)} >>> $Assert.throwsError.span($span)"

      case AST.Select(lhs, lhsTpe, rhsTpe, _, "get", span) =>
        q"${astToAssertion(lhs)} >>> $Assert.isSome.span($span)"

      case AST.Select(lhs, lhsTpe, rhsTpe, _, name, span) =>
        val select = c.untypecheck(q"{ (a) => a.${TermName(name)} }")
        q"${astToAssertion(lhs)} >>> $Assert.fromFunction[$lhsTpe, $rhsTpe]($select).span($span)"

      case AST.Method(lhs, lhsTpe, rhsTpe, "get", args, span) =>
        q"${astToAssertion(lhs)} >>> $Assert.isSome.span($span)"

      case AST.Method(lhs, lhsTpe, rhsTpe, "$greater", args, span) =>
        q"${astToAssertion(lhs)} >>> $Assert.greaterThan(${args.head}).span($span)"

      case AST.Method(lhs, lhsTpe, rhsTpe, "$equal", args, span) =>
        q"${astToAssertion(lhs)} >>> $Assert.equalTo(${args.head}).span($span)"

      case AST.Method(lhs, lhsTpe, rhsTpe, "forall", args, span) if lhsTpe <:< weakTypeOf[Iterable[_]] =>
        val assert = astToAssertion(parseExpr(args.head))
        q"${astToAssertion(lhs)} >>> $Assert.forall($assert).span($span)"

      case AST.Method(lhs, lhsTpe, rhsTpe, name, args, span) =>
        println(s"FROM FUNCTION: ${name}")
        val select = c.untypecheck(q"{ (a: $lhsTpe) => a.${TermName(name)}(..$args) }")
        q"${astToAssertion(lhs)} >>> $Assert.fromFunction($select).span($span)"

      case AST.Function(lhs, rhs, lhsTpe, span) =>
//        val tnl       = (lhs.span.end - lhs.span.start) max 1
//        val id        = c.untypecheck(q"{ (a: $lhsTpe) => a }")
        val rhsAssert = astToAssertion(rhs)
        val select    = c.untypecheck(q"{ ($lhs) => $rhsAssert }")
        q"$Assert.suspend($select)"

      case AST.Raw(ast, span) =>
        q"$Assert.succeed($ast).span($span)"
    }
  }

  case class PositionContext(start: Int, codeString: String) {
    def getPos(tree: c.Tree): (Int, Int) = (getStart(tree), getEnd(tree))
    def getEnd(tree: c.Tree): Int        = tree.pos.end - start
    def getStart(tree: c.Tree): Int      = tree.pos.start - start
  }

  def parseExpr(tree: c.Tree)(implicit pos: PositionContext): AST = {
    val end = pos.getEnd(tree)
    tree match {
      // unwrap implicit conversions-they'll be re-triggered post-macro-expansion
      case q"$lhs($rhs)" if lhs.symbol.isImplicit =>
        parseExpr(rhs)

      case q"!($inner)" =>
        AST.Not(parseExpr(inner), pos.getPos(tree), pos.getPos(inner))

      case q"$lhs && $rhs" =>
        AST.And(parseExpr(lhs), parseExpr(rhs), pos.getPos(tree), pos.getPos(lhs), pos.getPos(rhs))

      case q"$lhs || $rhs" =>
        AST.Or(parseExpr(lhs), parseExpr(rhs), pos.getPos(tree), pos.getPos(lhs), pos.getPos(rhs))

      case q"$lhs == $rhs" =>
        AST.EqualTo(parseExpr(lhs), rhs, (pos.getEnd(lhs), end))

      case q"$lhs.$name" if !(tree.symbol.isModule || tree.symbol.isStatic || tree.symbol.isClass) =>
        AST.Select(parseExpr(lhs), lhs.tpe.widen, tree.tpe.widen, List.empty, name.toString, (pos.getEnd(lhs), end))

      case q"$lhs.$name[..$tpes]" if !(tree.symbol.isModule || tree.symbol.isStatic || tree.symbol.isClass) =>
        AST.Select(parseExpr(lhs), lhs.tpe.widen, tree.tpe.widen, tpes, name.toString, (pos.getEnd(lhs), end))

      case MethodCall(lhs, name, args) =>
        AST.Method(parseExpr(lhs), lhs.tpe.widen, tree.tpe.widen, name.toString, args, (pos.getEnd(lhs), end))

      case x @ q"($a) => $b" =>
        val inType = x.tpe.widen.typeArgs.head
        AST.Function(a, parseExpr(b), inType, (pos.getStart(tree), end))
      case other => AST.Raw(other, (pos.getStart(tree), end))
    }
  }

  def assertZoom(expr: Expr[Boolean]): Expr[Assert[Any, Boolean]] = {
    val (stmts, tree) = expr.tree match {
      case Block(others, expr) => (others, expr)
      case other               => (List.empty, other)
    }

    val (_, start, codeString) = text(tree)
    implicit val pos           = PositionContext(start, codeString)
    println("")
    val parsed = parseExpr(tree)
    println(scala.Console.CYAN + parsed + scala.Console.RESET)
    println("")
    val ast = astToAssertion(parsed)

    val block =
      q"""
..$stmts
$ast.withCode($codeString)
        """
    println(scala.Console.BLUE + block + scala.Console.RESET)
    println("")
    c.Expr[Assert[Any, Boolean]](block)
  }

  def assertSingle(expr: c.Tree): Expr[TestResult] = {
    val (stmts, expr0) = expr match {
      case Block(others, expr) => (others, expr)
      case other               => (List.empty, other)
    }

//    println("RAW")
//    println(showRaw(expr))

    val (delta, start, codeString) = text(expr0)
    implicit val renderContext: RenderContext =
      RenderContext(codeString, start).shift(-delta, delta)

    val result = composeAssertions(expr0, false)

//    println("RESULT")
//    println(showCode(result.tree))

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

  sealed trait AssertCompose

  object AssertCompose {
    case class EqualTo(lhs: c.Tree, rhs: c.Tree)
    case class GreaterThan(lhs: c.Tree, rhs: c.Tree)
    case class LessThan(lhs: c.Tree, rhs: c.Tree)
  }

  private def subAssertion(expr: c.Tree, negated: Boolean)(implicit
    renderContext: RenderContext
  ): c.Expr[TestResult] = {
    val (fileName, _) = location(c)
    val srcLocation   = s"$fileName:${expr.pos.line}"

    val text = renderContext.text(expr)

    val (target, assertion) = terminalAssertion(expr, negated)

    val targetCode = CleanCodePrinter.show(c)(target)

    c.Expr[TestResult](
      q"_root_.zio.test.CompileVariants.smartAssertProxy($target, $targetCode, $text, $srcLocation)($assertion)"
    )
  }

  private def terminalAssertion(expr: c.Tree, negated: Boolean)(implicit
    renderContext: RenderContext
  ): (c.Tree, c.Tree) = {
    def negate(tree: c.Tree) = if (negated) q"$tree.smartNegate" else tree

    expr match {
      case q"$lhs > $rhs" =>
        val text = renderContext.textAfter(expr, lhs)
        generateAssertion(lhs, negate(q"$Assertion.isGreaterThan($rhs).withCode($text)"))
      case q"$lhs < $rhs" =>
        val text = renderContext.textAfter(expr, lhs)
        generateAssertion(lhs, negate(q"$Assertion.isLessThan($rhs).withCode($text)"))
      case q"$lhs >= $rhs" =>
        val text = renderContext.textAfter(expr, lhs)
        generateAssertion(lhs, negate(q"$Assertion.isGreaterThanEqualTo($rhs).withCode($text)"))
      case q"$lhs <= $rhs" =>
        val text = renderContext.textAfter(expr, lhs)
        generateAssertion(lhs, negate(q"$Assertion.isLessThanEqualTo($rhs).withCode($text)"))
      case q"$lhs == $rhs" =>
        val text   = renderContext.textAfter(expr, lhs)
        val lhsTpe = lhs.tpe.widen
        generateAssertion(lhs, negate(q"$Assertion.equalTo[$lhsTpe, $lhsTpe]($rhs).withCode($text)"))
      case q"$lhs != $rhs" =>
        val text   = renderContext.textAfter(expr, lhs)
        val lhsTpe = lhs.tpe.widen
        generateAssertion(lhs, negate(q"$Assertion.equalTo[$lhsTpe, $lhsTpe]($rhs).withCode($text).smartNegate"))
      case q"$lhs.$_" =>
        val text = renderContext.textAfter(expr, lhs)
        generateAssertion(expr, negate(q"$Assertion.isTrue.withCode($text)"))
      case MethodCall(lhs, _, _) =>
        val text = renderContext.textAfter(expr, lhs)
        generateAssertion(expr, negate(q"$Assertion.isTrue.withCode($text)"))
      case _ =>
        val text = renderContext.text(expr)
        generateAssertion(expr, negate(q"$Assertion.isTrue.withCode($text)"))
    }
  }

  val Matchers = Cross.Matchers

  //  @tailrec
  private def generateAssertion(expr: c.Tree, assertion: c.Tree)(implicit
    renderContext: RenderContext
  ): (c.Tree, c.Tree) = {
    val LensMatcher = Method.Matcher(assertion, renderContext)

//    println(s"HEY $expr")
//    println(s"HEY $assertion")
//    println("---")

    val leftGet  = Matchers.leftGet
    val rightGet = Matchers.rightGet

    expr match {
      case q"$lhs($rhs)" if lhs.symbol.isImplicit =>
        generateAssertion(rhs, assertion)
      case leftGet(te) =>
        val lhs          = te
        val text         = renderContext.textAfter(expr, lhs)
        val newAssertion = q"${Matchers.isLeft(assertion)}.withCode($text)"
        generateAssertion(lhs, newAssertion)
      case rightGet(te) =>
        val lhs          = te
        val text         = renderContext.textAfter(expr, lhs)
        val newAssertion = q"${Matchers.isRight(assertion)}.withCode($text)"
        generateAssertion(lhs, newAssertion)
      case Method.withAssertion(lhs, assertion) =>
        val text         = renderContext.textAfter(expr, lhs)
        val newAssertion = q"$assertion.withCode($text)"
        generateAssertion(lhs, newAssertion)
        // TODO: Add custom error message for `forall` and `exists`
//      case Method.exists(lhs, args) =>
//        val text         = renderContext.textAfter(expr, lhs)
//        val newAssertion = q"$Assertion.smartExists($args).withCode($text)"
//        generateAssertion(lhs, newAssertion)
//      case Method.forall(lhs, args) =>
//        val text = renderContext.textAfter(expr, lhs)
//        val newAssertion = args match {
////          case q"($a => $body)" if a.symbol.isParameter =>
////            val (lhs2, nested) = generateAssertion(body, assertion)
////            println("PARSED FORALL")
////            println(a, lhs2, nested, lhs2.symbol.isParameter)
////            if (lhs2.symbol.isParameter)
////              q"$Assertion.forall($nested).withCode($text)"
////            else
////              q"$Assertion.smartForall($args).withCode($text)"
//          case args =>
////            println("PARSED SMART FORALL")
////            println(args)
//            q"$Assertion.smartForall($args).withCode($text)"
//        }
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
      case q"$lhs.asInstanceOf[$tpe]" =>
        val lhsTpe = lhs.tpe.widen
        val text   = renderContext.textAfter(expr, lhs)
        val newAssertion =
          q"$Assertion.isCase[$lhsTpe, $tpe](${tpe.toString}, ((c: $lhsTpe) => scala.util.Try(c.asInstanceOf[$tpe]).toOption), $assertion).withCode($text)"
        generateAssertion(lhs, newAssertion)
      case q"$lhs.as[$tpe]" =>
        val lhsTpe = lhs match {
          case q"$_($rhs)" => rhs.tpe.widen
          case _           => lhs.tpe.widen
        }
        val text = renderContext.textAfter(expr, lhs)
        val newAssertion =
          q"$Assertion.isCase[$lhsTpe, $tpe](${tpe.toString}, ((c: $lhsTpe) => scala.util.Try(c.asInstanceOf[$tpe]).toOption), $assertion).withCode($text)"
        generateAssertion(lhs, newAssertion)
      case LensMatcher(lhs, newAssertion) =>
        generateAssertion(lhs, newAssertion)
      case Select(lhs, name) =>
        val tpe = lhs match {
          case q"$lhs($rhs)" if lhs.symbol.isImplicit => rhs.tpe.widen
          case _                                      => lhs.tpe.widen
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
    lazy val containsOption: Method[Option[_]] =
      Method[Option[_]]("contains", "containsOption", true, false)

    lazy val startsWithSeq: Method[Seq[_]]    = Method[Seq[_]]("startsWith", true, false)
    lazy val startsWithString: Method[String] = Method[String]("startsWith", "startsWithString", true, false)
    lazy val endsWithSeq: Method[Seq[_]]      = Method[Seq[_]]("endsWith", true, false)
    lazy val endsWithString: Method[String]   = Method[String]("endsWith", "endsWithString", true, false)

    lazy val hasAt: Method[Seq[_]] = Method[Seq[_]]("apply", "hasAt", true, true)

    lazy val exists: Method[Iterable[_]] =
      Method[Iterable[_]]("exists", false, true)

    lazy val forall: Method[Iterable[_]] =
      Method[Iterable[_]]("forall", false, true)

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
        containsOption,
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
    private def isConstructor(tree: c.Tree): Boolean =
//      println("")
//      println(s"HEY $tree")
//      println(showRaw(tree))
//      println(tree.symbol.isClass)
//      println("")
      tree match {
        case Select(Literal(_), _) => false
        case Select(Select(s, _), TermName("apply"))
            if s.symbol.isModule || s.symbol.isSynthetic || s.symbol.isClass || s.symbol.isStatic =>
          true
        case Select(s, _)
            if s != null && (s.symbol.isModule || s.symbol.isSynthetic || s.symbol.isClass || s.symbol.isStatic) =>
          true
        case TypeApply(s, _) => isConstructor(s)
        case Apply(s, _)     => isConstructor(s)
        case _               => false
      }
  }

  private def makeApplyAssertion(assertion: c.Tree, lhs: c.Tree, name: TermName, args: Seq[c.Tree]): c.Tree = {
    val tpe = lhs match {
      case q"$lhs($rhs)" if lhs.symbol.isImplicit => rhs.tpe.widen
      case _                                      => lhs.tpe.widen
    }
    val nameString = name.toString

//    val lhsTpe       = lhs.tpe.widen
    val select       = q"((a: $tpe) => a.${TermName(nameString)}(..$args))"
    val applyString  = s"$nameString(${args.toList.map(showCode(_)).mkString(", ")})"
    val newAssertion = q"$Assertion.hasField($applyString, $select, $assertion)"
    newAssertion
  }

  // Pilfered (with immense gratitude & minor modifications)
  // from https://github.com/com-lihaoyi/sourcecode
  @silent("Using a deprecated method on purpose")
  private def text[T: c.WeakTypeTag](tree: c.Tree): (Int, Int, String) = {
    val fileContent = new String(tree.pos.source.content)
    var start = tree.collect { case treeVal =>
      treeVal.pos match {
        case NoPosition => Int.MaxValue
        case p          => p.start
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
