import com.typesafe.tools.mima.core.*
import com.typesafe.tools.mima.core.ProblemFilters.*
import com.typesafe.tools.mima.plugin.MimaKeys.*
import sbt.*
import sbt.Keys.{name, organization}
import sbtdynver.DynVerPlugin.autoImport.*

object MimaSettings {
  def mimaSettings(failOnProblem: Boolean) =
    Seq(
      mimaPreviousArtifacts ++= previousStableVersion.value.map(organization.value %% name.value % _).toSet,
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
        exclude[ReversedMissingMethodProblem]("zio.Fiber#Runtime#UnsafeAPI.poll"),
        exclude[IncompatibleResultTypeProblem]("zio.stream.ZChannel#MergeState#BothRunning.*"),
        exclude[DirectMissingMethodProblem]("zio.stream.ZChannel#MergeState#BothRunning.copy"),
        exclude[DirectMissingMethodProblem]("zio.stream.ZChannel#MergeState#BothRunning.*"),
        exclude[IncompatibleResultTypeProblem]("zio.DurationOps.asScala$extension"),
        exclude[IncompatibleResultTypeProblem]("zio.DurationOps.asScala"),
        exclude[IncompatibleResultTypeProblem]("zio.DurationOps.asScala$extension"),
        exclude[IncompatibleMethTypeProblem]("zio.Queue#Strategy*"),
        exclude[ReversedMissingMethodProblem]("zio.Queue#Strategy*"),
        exclude[MissingClassProblem]("zio.stream.ZChannel$QRes$"),
        exclude[MissingClassProblem]("zio.stream.ZChannel$QRes"),
        exclude[DirectMissingMethodProblem]("zio.stream.ZChannel.mergeAllWith0$default$4"),
        exclude[DirectMissingMethodProblem]("zio.stream.ZChannel.mergeAllWith0$default$3"),
        exclude[DirectMissingMethodProblem]("zio.stream.ZChannel.mergeAllWith0"),
        exclude[DirectMissingMethodProblem]("zio.stream.ZChannel.mapOutZIOParUnordered1$default$2"),
        exclude[DirectMissingMethodProblem]("zio.stream.ZChannel.mapOutZIOParUnordered1"),
        exclude[DirectMissingMethodProblem]("zio.stream.ZChannel.mergeAllWith0"),
        exclude[DirectMissingMethodProblem]("zio.stream.ZChannel.mergeAllWith0$default$3"),
        exclude[DirectMissingMethodProblem]("zio.stream.ZChannel.mergeAllWith0$default$4")
      ),
      mimaFailOnProblem := failOnProblem
    )
}
