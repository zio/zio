package zio.metrics.jvm

import zio.metrics._
import zio._
import zio.stacktracer.TracingImplicits.disableAutoTrace

trait VersionInfo extends JvmMetrics {
  override type Feature = VersionInfo
  override val featureTag: Tag[VersionInfo] = Tag[VersionInfo]

  /** JVM version info */
  def jvmInfo(version: String, vendor: String, runtime: String): ZIOMetric.Gauge[Unit] =
    ZIOMetric
      .gauge(
        "jvm_info"
      )
      .tagged(
        MetricLabel("version", version),
        MetricLabel("vendor", vendor),
        MetricLabel("runtime", runtime)
      )
      .contramap[Unit](_ => 1.0)

  private def reportVersions()(implicit trace: ZTraceElement): ZIO[System, Throwable, Unit] =
    for {
      version <- System.propertyOrElse("java.runtime.version", "unknown")
      vendor  <- System.propertyOrElse("java.vm.vendor", "unknown")
      runtime <- System.propertyOrElse("java.runtime.name", "unknown")
      _       <- ZIO.unit @@ jvmInfo(version, vendor, runtime)
    } yield ()

  override def collectMetrics(implicit
    trace: ZTraceElement
  ): ZManaged[Clock with System, Throwable, VersionInfo] =
    reportVersions().repeat(collectionSchedule).interruptible.forkManaged.as(this)
}

object VersionInfo extends VersionInfo with JvmMetrics.DefaultSchedule {
  def withSchedule(schedule: Schedule[Any, Any, Unit])(implicit trace: ZTraceElement): VersionInfo = new VersionInfo {
    override protected def collectionSchedule(implicit trace: ZTraceElement): Schedule[Any, Any, Unit] = schedule
  }
}
