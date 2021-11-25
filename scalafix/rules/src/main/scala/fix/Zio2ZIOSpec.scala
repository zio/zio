package fix

import scalafix.v1._

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
    doc.tree.collect {
      case AbstractRunnableSpecRenames.Matcher(patch) => patch

      case t @ q"override def spec: $tpe = $body" if tpe.toString().contains("ZSpec[Environment, Failure]") =>
        Patch.replaceTree(t, s"override def spec = $body")
    }.asPatch + replaceSymbols

  def replaceSymbols(implicit doc: SemanticDocument) = Patch.replaceSymbols(
    "zio.test.DefaultRunnableSpec" -> "zio.test.ZIOSpecDefault"
  )

}
