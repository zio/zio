package zio.test

import com.github.ghik.silencer.silent
import zio.test.AssertionSyntax.EitherAssertionOps
import zio.test.macros.Scala2MacroUtils

import scala.annotation.tailrec
import scala.reflect.macros.blackbox

class SmartAssertMacros(val c: blackbox.Context) extends Scala2MacroUtils {
  import c.universe._

  private val Assertions = q"_root_.zio.test.Assertions"
  private val Arrow      = q"_root_.zio.test.Arrow"
  private val Assert     = q"_root_.zio.test.Assert"

  private[test] def location(c: blackbox.Context): (String, Int) = {
    val path = c.enclosingPosition.source.path
    val line = c.enclosingPosition.line
    (path, line)
  }

  def assert_impl(expr: c.Expr[Boolean], exprs: c.Expr[Boolean]*): c.Tree =
    exprs.map(assertOne_impl).foldLeft(assertOne_impl(expr)) { (acc, assert) =>
      q"$acc && $assert"
    }

  sealed trait AST { self =>
    def span: (Int, Int)
    def withSpan(span0: (Int, Int)): AST =
      self match {
        case not: AST.Not           => not.copy(span = span0)
        case and: AST.And           => and.copy(span = span0)
        case or: AST.Or             => or.copy(span = span0)
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
    case class Select(lhs: AST, lhsTpe: Type, rhsTpe: Type, tpes: List[Tree], name: String, span: (Int, Int))
        extends AST
    case class Method(
      lhs: AST,
      lhsTpe: Type,
      rhsTpe: Type,
      name: String,
      tpes: List[Type],
      args: List[c.Tree],
      span: (Int, Int)
    )                                                                          extends AST
    case class Function(lhs: c.Tree, rhs: AST, lhsTpe: Type, span: (Int, Int)) extends AST
    case class Raw(ast: c.Tree, span: (Int, Int))                              extends AST
  }

  case class AssertAST(name: String, tpes: List[Type] = List.empty, args: List[c.Tree] = List.empty)

  object AssertAST {
    def toTree(assertAST: AssertAST): c.Tree = assertAST match {
      case AssertAST(name, List(), List()) =>
        q"$Assertions.${TermName(name)}"
      case AssertAST(name, List(), args) =>
        q"$Assertions.${TermName(name)}(..$args)"
      case AssertAST(name, tpes, List()) =>
        q"$Assertions.${TermName(name)}[..$tpes]"
      case AssertAST(name, tpes, args) =>
        q"$Assertions.${TermName(name)}[..$tpes](..$args)"
    }
  }

  def astToAssertion(ast: AST)(implicit positionContext: PositionContext): c.Tree =
    ast match {
      case AST.Not(ast, _, _) =>
        q"!${astToAssertion(ast)}"

      case AST.And(lhs, rhs, _, ls, rs) =>
        q"${astToAssertion(lhs)}.withParentSpan($ls) && ${astToAssertion(rhs)}.withParentSpan($rs)"

      case AST.Or(lhs, rhs, _, ls, rs) =>
        q"${astToAssertion(lhs)}.withParentSpan($ls) || ${astToAssertion(rhs)}.withParentSpan($rs)"

      case AST.Select(lhs, _, _, List(tpe), "throwsA", span) =>
        q"${astToAssertion(lhs)} >>> $Assertions.throwsSubtype[$tpe].span($span)"

      case AST.Select(lhs, _, _, _, "throws", span) =>
        q"${astToAssertion(lhs)} >>> $Assertions.throwsError.span($span)"

      case AST.Method(lhs, lhsTpe, _, "forall", _, args, span) if lhsTpe <:< weakTypeOf[Iterable[_]] =>
        val assertion = astToAssertion(parseExpr(args.head))
        q"${astToAssertion(lhs)} >>> $Assertions.forallIterable($assertion).span($span)"

      case AST.Method(lhs, lhsTpe, _, "exists", _, args, span) if lhsTpe <:< weakTypeOf[Iterable[_]] =>
        val assertion = astToAssertion(parseExpr(args.head))
        q"${astToAssertion(lhs)} >>> $Assertions.existsIterable($assertion).span($span)"

      case ASTConverter.Matcher(lhs, ast, span) =>
        val tree = AssertAST.toTree(ast)
        q"${astToAssertion(lhs)} >>> $tree.span($span)"

      case AST.Select(lhs, _, _, _, "get", span) =>
        q"${astToAssertion(lhs)} >>> $Assertions.isSome.span($span)"

      case AST.Select(lhs, lhsTpe, rhsTpe, _, name, span) =>
        val select = c.untypecheck(q"{ (a) => a.${TermName(name)} }")
        q"${astToAssertion(lhs)} >>> $Arrow.fromFunction[$lhsTpe, $rhsTpe]($select).span($span)"

      case AST.Method(lhs, lhsTpe, _, name, _, args, span) =>
        val select =
          if (args.isEmpty) c.untypecheck(q"{ (a: $lhsTpe) => a.${TermName(name)} }")
          else c.untypecheck(q"{ (a: $lhsTpe) => a.${TermName(name)}(..$args) }")

        q"${astToAssertion(lhs)} >>> $Arrow.fromFunction($select).span($span)"

      case AST.Function(lhs, rhs, _, span) =>
        val rhsAssert = astToAssertion(rhs)
        val select    = c.untypecheck(q"{ ($lhs) => $rhsAssert }")
        q"$Arrow.suspend($select).span($span)"

      case AST.Raw(ast, span) =>
        q"$Arrow.succeed($ast).span($span)"
    }

  case class PositionContext(start: Int, codeString: String) {
    def getPos(tree: c.Tree): (Int, Int) = (getStart(tree), getEnd(tree))
    def getEnd(tree: c.Tree): Int        = tree.pos.end - start
    def getStart(tree: c.Tree): Int      = tree.pos.start - start
  }

  def parseExpr(tree: c.Tree)(implicit pos: PositionContext): AST = {
    val end = pos.getEnd(tree)

    tree match {
//      case MethodCall(lhs, TermName("apply"), _, _) if lhs.symbol.isStatic =>
//        AST.Raw(tree, (pos.getStart(tree), end))
//
//      case _ if tree.symbol.isStatic || tree.symbol.isConstructor =>
//        AST.Raw(tree, (pos.getStart(tree), end))

      case q"!($inner)" =>
        AST.Not(parseExpr(inner), pos.getPos(tree), pos.getPos(inner))

      case q"$lhs && $rhs" =>
        AST.And(parseExpr(lhs), parseExpr(rhs), pos.getPos(tree), pos.getPos(lhs), pos.getPos(rhs))

      case q"$lhs || $rhs" =>
        AST.Or(parseExpr(lhs), parseExpr(rhs), pos.getPos(tree), pos.getPos(lhs), pos.getPos(rhs))

      case MethodCall(lhs, name, tpes, args) =>
        AST.Method(
          parseExpr(lhs),
          lhs.tpe.widen,
          tree.tpe.widen,
          name.toString,
          tpes,
          args,
          (pos.getEnd(lhs), end)
        )

      case x @ q"($a) => $b" =>
        val inType = x.tpe.widen.typeArgs.head
        AST.Function(a, parseExpr(b), inType, (pos.getStart(tree), end))

      case _ => AST.Raw(tree, (pos.getStart(tree), end))
    }
  }

  def assertOne_impl(expr: Expr[Boolean]): c.Tree = {
    val (stmts, tree) = expr.tree match {
      case Block(others, expr) => (others, expr)
      case other               => (List.empty, other)
    }

    val (_, start, codeString) = text(tree)
    implicit val pos           = PositionContext(start, codeString)

    val (file, line)   = location(c)
    val locationString = s"$file:$line"

//    println("")
    val parsed = parseExpr(tree)
//    println(scala.Console.CYAN + parsed + scala.Console.RESET)
//    println("")

    val ast = astToAssertion(parsed)

    val block =
      q"""
..$stmts
$Assert($ast.withCode($codeString).withLocation($locationString))
        """

    println(scala.Console.BLUE + block + scala.Console.RESET)

    block
  }

  object UnwrapImplicit {
    def unapply(tree: c.Tree): Option[c.Tree] =
      tree match {
        case q"$wrapper($lhs)" if wrapper.symbol.isImplicit => Some(lhs)
        case _                                              => Some(tree)
      }
  }

  object MethodCall {
    def unapply(tree: c.Tree): Option[(c.Tree, TermName, List[Type], List[c.Tree])] =
      tree match {
        case q"${UnwrapImplicit(lhs)}.$name[..$tpes]"
            if !(tree.symbol.isModule || tree.symbol.isStatic || tree.symbol.isClass) =>
          Some((lhs, name, tpes.map(_.tpe), List.empty))
        case q"${UnwrapImplicit(lhs)}.$name"
            if !(tree.symbol.isModule || tree.symbol.isStatic || tree.symbol.isClass) =>
          Some((lhs, name, List.empty, List.empty))
        case q"${UnwrapImplicit(lhs)}.$name(..$args)" =>
          Some((lhs, name, List.empty, args))
        case q"${UnwrapImplicit(lhs)}.$name[..$tpes](..$args)" =>
          Some((lhs, name, tpes.map(_.tpe), args))
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

  // Pilfered (with immense gratitude & minor modifications)
  // from https://github.com/com-lihaoyi/sourcecode
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

  sealed trait ASTConverter { self =>
//    def matches: PartialFunction[AST.Method, AssertAST]
//
    final def orElse(that: ASTConverter): ASTConverter = new ASTConverter {
      override def unapply(method: AST.Method): Option[(AST, AssertAST, (Int, Int))] =
        self.unapply(method).orElse(that.unapply(method))
    }

    def unapply(method: AST.Method): Option[(AST, AssertAST, (Int, Int))] //  =
//      matches.lift(method).map { ast =>
//        (method.lhs, ast, method.span)
//      }
  }

  object ASTConverter {
    def make(pf: PartialFunction[AST.Method, AssertAST]): ASTConverter = new ASTConverter {
      override def unapply(method: AST.Method): Option[(AST, AssertAST, (Int, Int))] =
        pf.lift(method).map { result =>
          (method.lhs, result, method.span)
        }
    }

    def makeCustom(pf: PartialFunction[AST.Method, (AST, AssertAST, (Int, Int))]): ASTConverter = new ASTConverter {
      override def unapply(method: AST.Method): Option[(AST, AssertAST, (Int, Int))] =
        pf.unapply(method)
    }

    object Matcher {
      def unapply(method: AST.Method): Option[(AST, AssertAST, (Int, Int))] =
        all.reduce(_ orElse _).unapply(method)
    }

    lazy val all = List(
      equalTo,
      get,
      greaterThan,
      greaterThanOrEqualTo,
      lessThan,
      lessThanOrEqualTo,
      head,
      hasAt,
      isEmptyIterable,
      isNonEmptyIterable,
      isEmptyOption,
      isDefinedOption,
      containsSeq,
      containsOption,
      containsString,
      asSome,
      asRight,
      rightGet,
      leftGet,
      asLeft,
      asInstance
    )

    lazy val asInstance =
      ASTConverter.make {
        case AST.Method(_, lhsTpe, _, "asInstanceOf", List(tpe), _, _) =>
          AssertAST("as", List(lhsTpe, tpe))

        case AST.Method(_, lhsTpe, _, "$as", List(tpe), _, _) =>
          AssertAST("as", List(lhsTpe, tpe))
      }

    lazy val equalTo =
      ASTConverter.make { case AST.Method(_, lhsTpe, _, "$eq$eq", _, args, _) =>
        AssertAST("equalTo", List(lhsTpe), args)
      }

    lazy val get =
      ASTConverter.make {
        case AST.Method(_, lhsTpe, _, "get", _, _, _) if lhsTpe <:< weakTypeOf[Option[_]] =>
          AssertAST("isSome")
      }

    lazy val greaterThan =
      ASTConverter.make { case AST.Method(_, lhsTpe, _, "$greater", _, args, _) =>
        AssertAST("greaterThan", List(lhsTpe), args)
      }

    lazy val greaterThanOrEqualTo =
      ASTConverter.make { case AST.Method(_, lhsTpe, _, "$greater$eq", _, args, _) =>
        AssertAST("greaterThanOrEqualTo", List(lhsTpe), args)
      }

    lazy val lessThan =
      ASTConverter.make { case AST.Method(_, lhsTpe, _, "$less", _, args, _) =>
        AssertAST("lessThan", List(lhsTpe), args)
      }

    lazy val lessThanOrEqualTo =
      ASTConverter.make { case AST.Method(_, lhsTpe, _, "$less$eq", _, args, _) =>
        AssertAST("lessThanOrEqualTo", List(lhsTpe), args)
      }

    lazy val head =
      ASTConverter.make {
        case AST.Method(_, lhsTpe, _, "head", _, _, _) if lhsTpe <:< weakTypeOf[Iterable[_]] =>
          AssertAST("head")
      }

    lazy val hasAt =
      ASTConverter.make {
        case AST.Method(_, lhsTpe, _, "apply", _, args, _) if lhsTpe <:< weakTypeOf[Seq[_]] =>
          AssertAST("hasAt", args = args)
      }

    lazy val isEmptyIterable =
      ASTConverter.make {
        case AST.Method(_, lhsTpe, _, "isEmpty", _, _, _) if lhsTpe <:< weakTypeOf[Iterable[_]] =>
          AssertAST("isEmptyIterable")
      }

    lazy val isNonEmptyIterable =
      ASTConverter.make {
        case AST.Method(_, lhsTpe, _, "nonEmpty", _, _, _) if lhsTpe <:< weakTypeOf[Iterable[_]] =>
          AssertAST("isNonEmptyIterable")
      }

    lazy val isEmptyOption =
      ASTConverter.make {
        case AST.Method(_, lhsTpe, _, "isEmpty", _, _, _) if lhsTpe <:< weakTypeOf[Option[_]] =>
          AssertAST("isEmptyOption")
      }

    lazy val isDefinedOption =
      ASTConverter.make {
        case AST.Method(_, lhsTpe, _, "isDefined", _, _, _) if lhsTpe <:< weakTypeOf[Option[_]] =>
          AssertAST("isDefinedOption")
      }

    lazy val containsSeq =
      ASTConverter.make {
        case AST.Method(_, lhsTpe, _, "contains", _, args, _) if lhsTpe <:< weakTypeOf[Seq[_]] =>
          AssertAST("containsSeq", args = args)
      }

    lazy val containsOption =
      ASTConverter.make {
        case AST.Method(_, lhsTpe, _, "contains", _, args, _) if lhsTpe <:< weakTypeOf[Option[_]] =>
          AssertAST("containsOption", args = args)
      }

    lazy val containsString =
      ASTConverter.make {
        case AST.Method(_, lhsTpe, _, "contains", _, args, _) if lhsTpe <:< weakTypeOf[String] =>
          AssertAST("containsString", args = args)
      }

    // Option
    lazy val asSome =
      ASTConverter.make {
        case AST.Method(_, lhsTpe, _, "get", _, _, _) if lhsTpe <:< weakTypeOf[Option[_]] =>
          AssertAST("isSome")
      }

    // Either
    lazy val asRight =
      ASTConverter.make {
        case AST.Method(_, lhsTpe, _, "$asRight", _, _, _) if lhsTpe <:< weakTypeOf[Either[_, _]] =>
          AssertAST("asRight")
      }

    lazy val rightGet =
      ASTConverter.makeCustom {
        case AST.Method(AST.Method(lhs, lhsTpe, _, "right", _, _, lhsSpan), _, _, "get", _, _, span)
            if lhsTpe <:< weakTypeOf[Either[_, _]] =>
          (lhs, AssertAST("asRight"), lhsSpan._1 -> span._2)
      }

    lazy val asLeft =
      ASTConverter.make {
        case AST.Method(_, lhsTpe, _, "$asLeft", _, _, _) if lhsTpe <:< weakTypeOf[Either[_, _]] =>
          AssertAST("asLeft")
      }

    lazy val leftGet =
      ASTConverter.makeCustom {
        case AST.Method(AST.Method(lhs, lhsTpe, _, "left", _, _, lhsSpan), _, _, "get", _, _, span)
            if lhsTpe <:< weakTypeOf[Either[_, _]] =>
          (lhs, AssertAST("asLeft"), lhsSpan._1 -> span._2)
      }
  }
}
