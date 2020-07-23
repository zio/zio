import sbt._
import sbt.librarymanagement.VersionNumber
import Ordering.Implicits._
import java.lang.String.CASE_INSENSITIVE_ORDER

object DottyFullCompat {
  implicit class ModuleIDOps(val moduleID: ModuleID) extends AnyVal {
    def withDottyFullCompat(scalaVersion: String): ModuleID =
      if (scalaVersion.startsWith("2.")) moduleID cross CrossVersion.full
      else moduleID cross dotty2scala(VersionNumber(scalaVersion))
  }

  private val dotty2scalaMap = Seq(
    "0.19.0-RC1"                          -> "0.24.0-bin-20200422-0db5976-NIGHTLY" -> "2.13.1",
    "0.24.0-bin-20200423-38cb5e3-NIGHTLY" -> "0.26.0-bin-20200627-50df4d2-NIGHTLY" -> "2.13.2",
    "0.26.0-bin-20200629-8c5a58f-NIGHTLY" -> "0.26.0"                              -> "2.13.3"
  ).map { case ((x, y), v) => VersionNumber(x) -> VersionNumber(y) -> CrossVersion.constant(v) }

  private def dotty2scala(scalaVersion: VersionNumber) =
    dotty2scalaMap.collectFirst {
      case ((x, y), v) if x <= scalaVersion && scalaVersion <= y => v
    }.getOrElse(sys.error(s"Please update withDottyFullCompat for $scalaVersion in project/DottyFullCompat.scala"))

  /** Need this so that "bin" < "RC1" */
  private implicit val stringOrdering: Ordering[String] = Ordering.comparatorToOrdering(CASE_INSENSITIVE_ORDER)

  private implicit val dottyVersionOrdering: Ordering[VersionNumber] = Ordering.fromLessThan { (x, y) =>
    x.numbers < y.numbers ||
    x.numbers == y.numbers && (
      if (y.tags.isEmpty) x.tags.nonEmpty     // 0.24.0-RC1 < 0.24.0
      else x.tags.nonEmpty && x.tags < y.tags // 0.24.0-bin-xx < 0.24.0-RC1
    )
  }
}
