package fix

import scalafix.v1._

import scala.meta._

import Assertion._

class AssertToAssertTrue extends SemanticRule("AssertToAssertTrue") {

  val assert = SymbolMatcher.normalized(
    "zio.test.CompileVariants#assert"
//  "zio.test.CompileVariants#assertM"
  )

  override def fix(implicit doc: SemanticDocument): Patch =
    doc.tree.collect {
      case t @ assert(Term.Apply(Term.Apply(_, List(value)), List(Assertion(assertion)))) =>
//        println("---")
//        println(t.syntax)
        val string = assertion.render
//        println(string)
        Patch.replaceTree(t, s"assertTrue(($value)$string)")
      case _ =>
        Patch.empty

    }.asPatch

}

sealed trait Assertion { self =>
  def name: String

  def render: String

  def recursive(assertion: Assertion): Assertion =
    RecursiveAssertion(self, assertion)

  def applied(args: List[Tree]): Assertion =
    AppliedAssertion(self, args)

  def collectLenses: Assertion =
    self match {
      case lens: LensAssertion =>
        LensWrapper(List(lens))
      case RecursiveAssertion(_: LensAssertion, _) =>
        val (flattened, next) = self.flattenRecursiveLenses
        next match {
          case Some(value) => RecursiveAssertion(LensWrapper(flattened), value)
          case None        => LensWrapper(flattened)
        }
      case AppliedAssertion(parent, args) =>
        parent.collectLenses.mapHead(a => AppliedAssertion(a, args))
      case RecursiveAssertion(assertion, child) =>
        RecursiveAssertion(assertion, child.collectLenses)
      case other => other
    }

  def mapHead(f: Assertion => Assertion): Assertion =
    self match {
      case AppliedAssertion(parent, args) =>
        AppliedAssertion(f(parent), args)
      case RecursiveAssertion(parent, child) =>
        RecursiveAssertion(f(parent), child)

      case LensWrapper(head :: tail) =>
        LensWrapper(f(head) :: tail)
      case other =>
        f(other)
    }

  def flattenRecursiveLenses: (List[LensAssertion], Option[Assertion]) =
    self match {
      case RecursiveAssertion(parent: LensAssertion, child) =>
        val (lenses, assertion) = child.flattenRecursiveLenses
        (parent :: lenses, assertion)
      case lens: LensAssertion => (List(lens), None)
      case other               => (Nil, Some(other))
    }
}

object Assertion {

  def getAssertion(tree: Tree)(implicit doc: SemanticDocument): Assertion = tree match {
    case q"equalTo($value)" =>
      EqualTo(value)

    case q"isGreaterThan($value)" =>
      GreaterThan(value)

    case q"isGreaterThanEqualTo($value)" =>
      GreaterThanEq(value)

    case q"isLessThan($value)" =>
      LessThan(value)

    case q"isLessThanEqualTo($value)" =>
      LessThanEq(value)

    case q"isSubtype[$tpe]($assertion)" =>
      LensAssertion("isSubtype", s"subtype[$tpe]").recursive(getAssertion(assertion))

    //    // TODO: IGNORE
    //    case q"isCase[..$tpes](..$args)" =>
    //      MethodAssertion("Oops")
    //      MethodAssertion
    //      MethodAssertion(method.syntax).recursive(getAssertion(assertion))

    case q"hasField[..$tpes]($_name, _.$method, $assertion)" =>
      MethodAssertion(method.syntax).recursive(getAssertion(assertion))

    case q"hasField($_name, $method, $assertion)" =>
      MethodAssertion(method.syntax).recursive(getAssertion(assertion))

    case q"hasField($_name, ${Term.Apply(_, List(method))}, $assertion)" =>
      MethodAssertion(method.syntax).recursive(getAssertion(assertion))

    case q"matchesRegex($regex)" =>
      MethodAssertion("matchesRegex", s"matches($regex)")

    case q"hasAt($value)($assertion)" =>
      MethodAssertion("hasAt", "apply")
        .applied(List(value))
        .recursive(getAssertion(assertion))

    case q"hasThrowableCause($assertion)" =>
      LensAssertion("cause", "cause")
        .recursive(getAssertion(assertion))

    //    case q"$_ ?? $label" =>
    //      // TODO: IGNORE
    //      MethodAssertion("LABEL")

    case q"anything" =>
      LensAssertion("anything", "anything")

    case q"$left && $right" =>
      And(getAssertion(left), getAssertion(right))

    case q"$left || $right" =>
      Or(getAssertion(left), getAssertion(right))

    case q"$name(..$args)($assertion)" if Assertion.recursiveAppliedMethodMap.contains(name.syntax) =>
      Assertion
        .recursiveAppliedMethodMap(name.syntax)
        .applied(args.toList)
        .recursive(getAssertion(assertion))

    case q"$name(..$args)" if Assertion.methods.contains(name.syntax) =>
      Assertion.methods(name.syntax).applied(args.toList)

    case q"$name($assertion)" if Assertion.assertions.contains(name.syntax) =>
      Assertion.assertions(name.syntax).recursive(getAssertion(assertion))

    case q"$name" if Assertion.assertions.contains(name.syntax) =>
      Assertion.assertions(name.syntax)

    case q"$name($assertion)" if Assertion.recursiveMethodMap.contains(name.syntax) =>
      Assertion.recursiveMethodMap(name.syntax).recursive(getAssertion(assertion))

    case q"$name" if Assertion.finalMethodMap.contains(name.syntax) =>
      Assertion.finalMethodMap(name.syntax)

    case q"$name[..$_]" if Assertion.finalMethodMap.contains(name.syntax) =>
      Assertion.finalMethodMap(name.syntax)

    case _ =>
      //      println(tree.structure)
      throw new Error(s"Unsupported\n\n $tree\n${tree.structure}")
  }

  def unapply(tree: Tree)(implicit doc: SemanticDocument): Option[Assertion] =
    scala.util.Try(getAssertion(tree)).toOption.map(_.collectLenses)

  //    case class IsRight(assertion: Assertion) extends Assertion
  //    case class IsSome(assertion: Assertion)  extends Assertion
  //    case object IsNone                       extends Assertion
  final case class And(left: Assertion, right: Assertion) extends Assertion {
    def name                    = "&&"
    override def render: String = s"${left.render} && ${right.render}"
  }

  final case class Or(left: Assertion, right: Assertion) extends Assertion {
    def name                    = "||"
    override def render: String = s"${left.render} || ${right.render}"
  }

  final case class EqualTo(value: Tree) extends Assertion {
    def name                    = "equalTo"
    override def render: String = s" == $value"
  }

  final case class GreaterThan(value: Tree) extends Assertion {
    def name                    = "greaterThan"
    override def render: String = s" > $value"
  }

  final case class GreaterThanEq(value: Tree) extends Assertion {
    def name                    = "greaterThanEq"
    override def render: String = s" >= $value"
  }

  final case class LessThan(value: Tree) extends Assertion {
    def name                    = "lessThan"
    override def render: String = s" < $value"
  }

  final case class LessThanEq(value: Tree) extends Assertion {
    def name                    = "lessThanEq"
    override def render: String = s" <= $value"
  }

  final case class MethodAssertion(name: String, target: String) extends Assertion {
    override def render: String = s".$target"
  }

  object MethodAssertion {
    def apply(name: String) =
      new MethodAssertion(name, name)
  }

  final case class LensWrapper(lenses: List[Assertion]) extends Assertion {
    def name                    = "LensWrapper"
    override def render: String = s".is(_${lenses.map(_.render).mkString("")})"
  }

  final case class LensAssertion(name: String, is: String) extends Assertion {
    override def render: String = s".$is"
  }

  final case class AppliedAssertion(parent: Assertion, args: List[Tree]) extends Assertion {
    def name                    = "applied"
    override def render: String = s"${parent.render}(${args.mkString(", ")})"
  }

  final case class RecursiveAssertion(parent: Assertion, child: Assertion) extends Assertion {
    def name                    = "recursive"
    override def render: String = parent.render + child.render
  }

  val lensAssertionsList =
    List(
      LensAssertion("isRight", "right"),
      LensAssertion("isLeft", "left"),
      LensAssertion("isSome", "some"),
      LensAssertion("anything", "anything"),
      LensAssertion("dies", "die"),
      LensAssertion("isFailure", "failure"),
      LensAssertion("isSuccess", "success"),
      LensAssertion("succeeds", "success"),
      LensAssertion("throws", "throwing"),
      LensAssertion("fails", "failure"),
      LensAssertion("hasMessage", "message"),
      LensAssertion("isSorted", "sorted"),
      LensAssertion("isSortedReverse", "sortedReverse")
    )

  val assertions: Map[String, LensAssertion] =
    lensAssertionsList.map(lens => lens.name -> lens).toMap

  // contains
  // equalTo
  val appliedMethodList =
    List(
      MethodAssertion("hasKey", "apply"),
      MethodAssertion("contains"),
      LensAssertion("isWithin", "within"),
      MethodAssertion("containsString", "contains"),
      MethodAssertion("startsWith"),
      MethodAssertion("endsWith"),
      MethodAssertion("startsWithString", "startsWith"),
      MethodAssertion("endsWithString", "endsWith"),
      MethodAssertion("equalsIgnoreCase"),
      MethodAssertion("exists"),
      MethodAssertion("forall"),
      MethodAssertion("hasSameElementsDistinct"), // TODO: this is not a lens
      MethodAssertion("hasAtLeastOneOf"),         // TODO: this is not a lens
      MethodAssertion("hasAtMostOneOf"),          // TODO: this is not a lens
      MethodAssertion("hasNoneOf"),               // TODO: this is not a lens
      MethodAssertion("hasOneOf"),                // TODO: this is not a lens
      MethodAssertion("hasSameElements"),         // TODO: this is not a lens
      MethodAssertion("hasSubset"),               // TODO: this is not a lens
      MethodAssertion("isOneOf")                  // TODO: this is not a lens
    )

  val finalMethodList =
    List(
      MethodAssertion("isEmpty"),
      MethodAssertion("isLeft"),
      MethodAssertion("isRight"),
      MethodAssertion("isNull", "==(null)"),
      MethodAssertion("isNone", "isEmpty"),
      MethodAssertion("isSome", "isDefined"),
      MethodAssertion("isNonEmpty", "nonEmpty"),
      MethodAssertion("isNonEmptyString", "nonEmpty"),
      MethodAssertion("isNegative", "<(0)"),
      MethodAssertion("nonNegative", ">=(0)"),
      MethodAssertion("nonPositive", "<=(0)"),
      MethodAssertion("isPositive", ">(0)"),
      MethodAssertion("isUnit", "==(())"),
      MethodAssertion("isZero", "==(0)"),
      MethodAssertion("isFailure", "is(_.failure.anything)"),
      MethodAssertion("isSuccess", "is(_.success.anything)"),
      MethodAssertion("isFalse", "==(false)"),
      MethodAssertion("isTrue", "==(true)"),
      MethodAssertion("isEmptyString", "isEmpty"),
      // TODO: Add
      MethodAssertion("isDistinct", "isDistinct")
//      MethodAssertion("nothing")
    )

  val recursiveMethodList =
    List(
      MethodAssertion("hasFirst", "head"),
      MethodAssertion("hasLast", "last"),
      MethodAssertion("hasSize", "length"),
      MethodAssertion("hasSizeString", "length"),
      MethodAssertion("hasKeys", "keys"),
      MethodAssertion("hasValues", "values")
    )

  val recursiveAppliedMethodList =
    List(
      MethodAssertion("hasIntersection", "intersect"),
      MethodAssertion("hasKey", "apply")
    )

  val recursiveMethodMap =
    recursiveMethodList.map(lens => lens.name -> lens).toMap

  val recursiveAppliedMethodMap =
    recursiveAppliedMethodList.map(lens => lens.name -> lens).toMap

  val finalMethodMap =
    finalMethodList.map(lens => lens.name -> lens).toMap

  val methods = appliedMethodList.map(m => m.name -> m).toMap

}
