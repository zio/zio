import com.typesafe.tools.mima.plugin.MimaKeys._
import com.typesafe.tools.mima.core._
import com.typesafe.tools.mima.core.ProblemFilters._

object MimaSettings {

  val mimaSettings =
    Seq(
      mimaBinaryIssueFilters ++= Seq(
        exclude[Problem]("zio.internal.*"),
        exclude[Problem]("zio.ZQueue#internal#*"),
        exclude[ReversedMissingMethodProblem](
          "zio.ZManagedPlatformSpecific.zio$ZManagedPlatformSpecific$_setter_$blocking_="
        ),
        exclude[ReversedMissingMethodProblem]("zio.ZManagedPlatformSpecific.blocking"),
        exclude[ReversedMissingMethodProblem]("zio.ZIO.catchNonFatalOrDie")
      )
    )
}
