package zio.metrics.jvm

import com.github.ghik.silencer.silent
import zio._

trait JvmMetrics {
  type Feature
  val featureTag: Tag[Feature]

  protected val collectionSchedule: Schedule[Any, Any, Unit] = Schedule.fixed(10.seconds).unit

  val collectMetrics: ZManaged[Has[Clock] with Has[System], Throwable, Feature]

  /** A layer that when constructed forks a fiber that periodically updates the JVM metrics */
  lazy val live: ZLayer[Has[Clock] with Has[System], Throwable, Has[Feature]] =
    collectMetrics.toLayer(featureTag)

  /** A ZIO application that periodically updates the JVM metrics */
  lazy val app: ZIOApp = new ZIOApp {
    @silent private implicit val ftag: zio.Tag[Feature] = featureTag
    override val tag: Tag[Environment]                  = Tag[Environment]
    override type Environment = Has[Clock] with Has[System] with Has[Feature]
    override val layer: ZLayer[Has[ZIOAppArgs], Any, Environment] = {
      Clock.live ++ System.live >+> live
    }
    override def run: ZIO[Environment with Has[ZIOAppArgs], Any, Any] = ZIO.unit
  }
}
