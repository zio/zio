package zio.metrics.jvm

import zio._
import zio.internal.stacktracer.Tracer
import zio.stacktracer.TracingImplicits.disableAutoTrace

import com.github.ghik.silencer.silent

trait JvmMetrics { self =>
  type Feature
  val featureTag: Tag[Feature]

  protected def collectionSchedule(implicit trace: ZTraceElement): Schedule[Any, Any, Unit]

  def collectMetrics(implicit trace: ZTraceElement): ZManaged[Clock with System, Throwable, Feature]

  /**
   * A service builder that when constructed forks a fiber that periodically
   * updates the JVM metrics
   */
  lazy val live: ZServiceBuilder[Clock with System, Throwable, Feature] = {
    implicit val trace: ZTraceElement = Tracer.newTrace
    collectMetrics.toServiceBuilder(featureTag, trace)
  }

  /** A ZIO application that periodically updates the JVM metrics */
  lazy val app: ZIOApp = new ZIOApp {
    @silent private implicit val ftag: zio.Tag[Feature] = featureTag
    private implicit val trace: ZTraceElement           = Tracer.newTrace
    override val tag: Tag[Environment]                  = Tag[Environment]
    override type Environment = Clock with System with Feature
    override val serviceBuilder: ZServiceBuilder[ZIOAppArgs, Any, Environment] = {
      Clock.live ++ System.live >+> live
    }
    override def run: ZIO[Environment with ZIOAppArgs, Any, Any] = ZIO.unit
  }
}

object JvmMetrics {
  def defaultSchedule(implicit trace: ZTraceElement): Schedule[Any, Any, Unit] = Schedule.fixed(10.seconds).unit

  trait DefaultSchedule {
    self: JvmMetrics =>
    override protected def collectionSchedule(implicit trace: ZTraceElement): Schedule[Any, Any, Unit] = defaultSchedule
  }
}
