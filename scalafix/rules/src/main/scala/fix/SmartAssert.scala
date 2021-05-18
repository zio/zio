package fix

import scalafix.v1._
import scala.meta._

class SmartAssert extends SemanticRule("SmartAssert") {

  val assert = SymbolMatcher.normalized(
    "zio/test/CompileVariants#assert",
    "zio.test.package.assert",
    "zio.test.package.assertM"
  )

  val equalTo = SymbolMatcher.normalized("zio/test/AssertionVariants#equalTo")
  val isSome  = SymbolMatcher.normalized("zio/test/Assertion#isSome")
  val isRight = SymbolMatcher.normalized("zio/test/Assertion#isRight")

  def processAssertion(term: Term, acc: List[String])(implicit doc: SemanticDocument): List[String] =
    term match {
      case equalTo(Term.Apply(_, List(value))) =>
        s" == $value" :: acc
      case isSome(Term.Apply(_, List(assertion))) =>
        processAssertion(assertion, ".get" :: acc)
      case isRight(Term.Apply(_, List(assertion))) =>
        processAssertion(assertion, ".$asRight" :: acc)
      case other =>
        println(s"Assertion: ${other.symbol}")
        acc
    }

  override def fix(implicit doc: SemanticDocument): Patch =
    doc.tree.collect {
      case t @ assert(Term.Apply(Term.Apply(name, List(value)), List(assertion))) =>
        println("\n")
        println(s"MATCHED $t")
        println(assertion)
        val res = processAssertion(assertion, List.empty).reverse.mkString("")
        println("Result:", res)
        Patch.replaceTree(t, name + "(" + value + res + ")")
      case _ =>
        Patch.empty
    }.asPatch
}
