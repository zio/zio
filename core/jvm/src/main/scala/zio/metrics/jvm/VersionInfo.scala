package zio.metrics.jvm

import zio._
import zio.metrics._

final case class VersionInfo(version: String, vendor: String, runtime: String)

object VersionInfo {

  /** JVM version info */
  def jvmInfo(version: String, vendor: String, runtime: String): Metric.Gauge[Unit] =
    Metric
      .gauge(
        "jvm_info"
      )
      .tagged(
        MetricLabel("version", version),
        MetricLabel("vendor", vendor),
        MetricLabel("runtime", runtime)
      )
      .fromConst(1.0)

  private def reportVersions()(implicit trace: ZTraceElement): ZIO[Any, Throwable, VersionInfo] =
    for {
      version <- System.propertyOrElse("java.runtime.version", "unknown")
      vendor  <- System.propertyOrElse("java.vm.vendor", "unknown")
      runtime <- System.propertyOrElse("java.runtime.name", "unknown")
      _       <- jvmInfo(version, vendor, runtime).set(())
    } yield VersionInfo(version, vendor, runtime)

  val live: ZLayer[Any, Throwable, VersionInfo] = ZLayer(reportVersions())
}
