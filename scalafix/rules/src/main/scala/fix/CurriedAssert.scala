package fix

import scalafix.v1._
import scala.meta._

class CurriedAssert extends SemanticRule("CurriedAssert") {

  val assert = SymbolMatcher.normalized(
    "zio.test.package.assert",
    "zio.test.package.assertM"
  )

  override def fix(implicit doc: SemanticDocument): Patch =
    doc.tree.collect {
      case t @ assert(Term.Apply(name, List(value, assertion))) =>
        Patch.replaceTree(t, name + "(" + value + ")(" + assertion + ")")
      case _ =>
        Patch.empty
    }.asPatch
}
