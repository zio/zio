import sbt._
import sbt.Keys.{name, organization}

import com.typesafe.tools.mima.plugin.MimaKeys._
import com.typesafe.tools.mima.core._
import com.typesafe.tools.mima.core.ProblemFilters._

object MimaSettings {
  lazy val bincompatVersionToCompare = "2.0.10"

  def mimaSettings(failOnProblem: Boolean) =
    Seq(
      mimaPreviousArtifacts := Set(organization.value %% name.value % bincompatVersionToCompare),
      mimaBinaryIssueFilters ++= Seq(
        exclude[Problem]("zio.internal.*"),
        exclude[FinalMethodProblem]("zio.ZIO#EvaluationStep#*"),
        exclude[IncompatibleResultTypeProblem]("zio.Chunk.iterate"),
        exclude[Problem]("zio.stm.ZSTM#internal*"),
        exclude[Problem]("zio.stm.ZSTM$internal*"),
        exclude[Problem]("zio.stream.internal*"),
        exclude[MissingClassProblem]("zio.stream.ZChannel$ChildExecutorDecision*"),
        exclude[MissingClassProblem]("zio.stream.ZChannel$UpstreamPullRequest*"),
        exclude[MissingClassProblem]("zio.stream.ZChannel$UpstreamPullStrategy*"),
        exclude[Problem]("zio.stream.ZChannel$BracketOut*"),
        exclude[Problem]("zio.stream.ZChannel$Bridge*"),
        exclude[Problem]("zio.stream.ZChannel$ChildExecutorDecision*"),
        exclude[Problem]("zio.stream.ZChannel$Fold*"),
        exclude[Problem]("zio.stream.ZChannel$MergeState*"),
        exclude[Problem]("zio.stream.ZChannel$UpstreamPullRequest*"),
        exclude[Problem]("zio.stream.ZChannel$UpstreamPullStrategy*")
      ),
      mimaFailOnProblem := failOnProblem
    )
}
