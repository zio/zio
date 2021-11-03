package fix

import scalafix.v1._

import scala.meta.Name
import scala.meta._

class Zio2ZIOSpec extends SemanticRule("ZIOSpecMigration"){
  val zio2UpgradeRule = new Zio2Upgrade()
  val AbstractRunnableSpecRenames = zio2UpgradeRule.Renames(
    List("zio.test.DefaultRunnableSpec" /* TODO What other types here? */),
    Map(
      "Failure"            -> "Any",
    )
  )
  
  override def fix(implicit doc: SemanticDocument): Patch =
    replaceSymbols + doc.tree.collect {
      case AbstractRunnableSpecRenames.Matcher(patch) => patch

      case t @ q"override def spec: $tpe = $body" if tpe.toString().contains("ZSpec[Environment, Failure]") =>
        println("spec type: " + tpe.toString())
        
        Patch.replaceTree(t, s"override def spec = $body")
        
//      case t =>
//        Patch.fromIterable(
//          t.tokens.sliding(3).collect { tokens =>
//            case (c: Token.Colon, ???, openBrace: Token.LeftBrace) => ???
//            
//          }
//        )

    }.asPatch

  def replaceSymbols(implicit doc: SemanticDocument) = Patch.replaceSymbols(
    "zio.test.DefaultRunnableSpec" -> "zio.test.ZIOSpecDefault"
  )

}
