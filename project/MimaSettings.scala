import sbt._
import sbt.Keys.{name, organization}

import com.typesafe.tools.mima.plugin.MimaKeys._
import com.typesafe.tools.mima.core._
import com.typesafe.tools.mima.core.ProblemFilters._

object MimaSettings {
  lazy val bincompatVersionToCompare = "1.0.5"

  def mimaSettings(failOnProblem: Boolean) =
    Seq(
      mimaPreviousArtifacts := Set(organization.value %% name.value % bincompatVersionToCompare),
      mimaBinaryIssueFilters ++= Seq(
        exclude[Problem]("zio.internal.*"),
        exclude[DirectMissingMethodProblem]("zio.ZManaged.reserve"),
        exclude[DirectMissingMethodProblem]("zio.ZIO#Fork.this"),
        exclude[IncompatibleResultTypeProblem]("zio.stm.TSemaphore.assertNonNegative$extension"),
        exclude[MissingClassProblem]("zio.ZIO$Lock"),
        exclude[DirectMissingMethodProblem]("zio.ZIO#Tags.Lock"),
        exclude[DirectMissingMethodProblem]("zio.clock.package#Clock.globalScheduler"),
        exclude[ReversedMissingMethodProblem]("zio.clock.package#Clock#Service.timer"),
        exclude[DirectMissingMethodProblem]("zio.clock.PlatformSpecific.globalScheduler"),
        exclude[ReversedMissingMethodProblem](
          "zio.clock.PlatformSpecific.zio$clock$PlatformSpecific$_setter_$globalTimer_="
        ),
        exclude[ReversedMissingMethodProblem]("zio.clock.PlatformSpecific.globalTimer"),
        exclude[ReversedMissingMethodProblem](
          "zio.ZManagedPlatformSpecific.zio$ZManagedPlatformSpecific$_setter_$blocking_="
        ),
        exclude[ReversedMissingMethodProblem]("zio.ZManagedPlatformSpecific.blocking")
      ),
      mimaFailOnProblem := failOnProblem
    )
}
