package zio.metrics.jvm

import zio.ZIOMetric.Gauge
import zio._
import zio.stacktracer.TracingImplicits.disableAutoTrace

trait VersionInfo extends JvmMetrics {
  override type Feature = VersionInfo
  override val featureTag: Tag[VersionInfo] = Tag[VersionInfo]

  /** JVM version info */
  def jvmInfo(version: String, vendor: String, runtime: String): Gauge[Unit] =
    ZIOMetric.setGaugeWith(
      "jvm_info",
      MetricLabel("version", version),
      MetricLabel("vendor", vendor),
      MetricLabel("runtime", runtime)
    )(_ => 1.0)

  private def reportVersions()(implicit trace: ZTraceElement): ZIO[Has[System], Throwable, Unit] =
    for {
      version <- System.propertyOrElse("java.runtime.version", "unknown")
      vendor  <- System.propertyOrElse("java.vm.vendor", "unknown")
      runtime <- System.propertyOrElse("java.runtime.name", "unknown")
      _       <- ZIO.unit @@ jvmInfo(version, vendor, runtime)
    } yield ()

  override def collectMetrics(implicit
    trace: ZTraceElement
  ): ZManaged[Has[Clock] with Has[System], Throwable, VersionInfo] =
    reportVersions().repeat(collectionSchedule).interruptible.forkManaged.as(this)
}

object VersionInfo extends VersionInfo with JvmMetrics.DefaultSchedule {
  def withSchedule(schedule: Schedule[Any, Any, Unit])(implicit trace: ZTraceElement): VersionInfo = new VersionInfo {
    override protected def collectionSchedule(implicit trace: ZTraceElement): Schedule[Any, Any, Unit] = schedule
  }
}
