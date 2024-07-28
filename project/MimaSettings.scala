import com.typesafe.tools.mima.core.*
import com.typesafe.tools.mima.core.ProblemFilters.*
import com.typesafe.tools.mima.plugin.MimaKeys.*
import sbt.*
import sbt.Keys.{name, organization}

object MimaSettings {
  lazy val bincompatVersionToCompare = "2.1.0"

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
        exclude[MissingClassProblem]("zio.ZIO$EvaluationStep*"),
        exclude[MissingClassProblem]("zio.ZIO$ZIOError*"),
        exclude[MissingClassProblem]("zio.ZIO$OnFailure*"),
        exclude[MissingClassProblem]("zio.ZIO$OnSuccess*"),
        exclude[DirectMissingMethodProblem]("zio.ZEnvironment.zio$ZEnvironment$$<init>$default$3"),
        exclude[DirectMissingMethodProblem]("zio.ZEnvironment.zio$ZEnvironment$$$<init>$default$3"), // Scala 3
        exclude[ReversedMissingMethodProblem]("zio.Fiber#Runtime#UnsafeAPI.poll"),
        exclude[IncompatibleResultTypeProblem]("zio.stream.ZChannel#MergeState#BothRunning.*"),
        exclude[DirectMissingMethodProblem]("zio.stream.ZChannel#MergeState#BothRunning.copy"),
        exclude[DirectMissingMethodProblem]("zio.stream.ZChannel#MergeState#BothRunning.*"),
        ProblemFilters.exclude[IncompatibleResultTypeProblem]("zio.DurationOps.asScala$extension"),
        ProblemFilters.exclude[IncompatibleResultTypeProblem]("zio.DurationOps.asScala"),
        ProblemFilters.exclude[IncompatibleResultTypeProblem]("zio.DurationOps.asScala$extension")
      ),
      mimaFailOnProblem := failOnProblem
    )
}
