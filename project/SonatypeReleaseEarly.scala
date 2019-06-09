import sbt._
import ch.epfl.scala.sbt.release.AutoImported._
import ch.epfl.scala.sbt.release.{ Feedback, ReleaseEarly, ReleaseEarlyPlugin }
import com.typesafe.sbt.SbtPgp.autoImportImpl.PgpKeys
import sbt.Keys.state
import xerial.sbt.Sonatype

/**
 * @author 杨博 (Yang Bo)
 */
object SonatypeReleaseEarly extends AutoPlugin {
  override def trigger  = allRequirements
  override def requires = Sonatype && ReleaseEarlyPlugin

  override def projectSettings: Seq[Def.Setting[_]] = Seq(
    releaseEarlyWith := SonatypePublisher,
    Keys.aggregate in releaseEarly := false
  )
}
