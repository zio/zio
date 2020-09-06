import sbt._
import sbt.Keys.{ moduleName, organization }
import com.typesafe.tools.mima.plugin.MimaKeys._
import com.typesafe.tools.mima.core._
import com.typesafe.tools.mima.core.ProblemFilters._

object MimaSettings {
  lazy val bincompatVersionToCompare = "1.0.1"

  def mimaSettings(failOnProblem: Boolean) =
    Seq(
      mimaPreviousArtifacts := Set(organization.value %% moduleName.value % bincompatVersionToCompare),
      mimaBinaryIssueFilters ++= Seq(
        exclude[Problem]("zio.internal.*"),
        exclude[DirectMissingMethodProblem]("zio.ZManaged.reserve"),
        exclude[DirectMissingMethodProblem]("zio.ZIO#Fork.this"),
        exclude[ReversedMissingMethodProblem]("zio.Cause.equalsWith"),
        exclude[ReversedMissingMethodProblem]("zio.Cause.hashWith")
      ),
      mimaFailOnProblem := failOnProblem
    )
}
