package zio.metrics.jvm

import zio.ZIOMetric.Gauge
import zio._

object VersionInfo extends JvmMetrics {

  /** JVM version info */
  def jvmInfo(version: String, vendor: String, runtime: String): Gauge[Unit] =
    ZIOMetric.setGaugeWith(
      "jvm_info",
      MetricLabel("version", version),
      MetricLabel("vendor", vendor),
      MetricLabel("runtime", runtime)
    )(_ => 1.0)

  private def reportVersions(): ZIO[Has[System], Throwable, Unit] =
    for {
      version <- System.propertyOrElse("java.runtime.version", "unknown")
      vendor  <- System.propertyOrElse("java.vm.vendor", "unknown")
      runtime <- System.propertyOrElse("java.runtime.name", "unknown")
      _       <- ZIO.unit @@ jvmInfo(version, vendor, runtime)
    } yield ()

  override val collectMetrics: ZManaged[Has[Clock] with Has[System], Throwable, Unit] =
    reportVersions().repeat(collectionSchedule).interruptible.forkManaged.unit
}
