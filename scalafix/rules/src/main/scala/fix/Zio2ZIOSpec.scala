package fix

import scalafix.v1._

import scala.meta._

object Zio2ZIOSpec extends SemanticRule("ZIOSpecMigration"){
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

      // TODO Check if we really want to do this, or if we want to keep it now that we might have a
      //    meaningful Failure type
      case t @ q"override def spec: $tpe = $body" if tpe.toString().contains("ZSpec[Environment, Failure]") =>
        Patch.replaceTree(t, s"override def spec = $body")
    }.asPatch + replaceSymbols

  def replaceSymbols(implicit doc: SemanticDocument) = Patch.replaceSymbols(
    "zio.test.DefaultRunnableSpec" -> "zio.test.ZIOSpecDefault"
  )

}
