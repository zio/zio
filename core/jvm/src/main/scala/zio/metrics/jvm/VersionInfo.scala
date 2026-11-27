package zio.metrics.jvm

import zio._
import zio.metrics._

/**
 * The version of the running JVM, read from the system properties
 * `java.runtime.version`, `java.vm.vendor` and `java.runtime.name`, each
 * `"unknown"` when the property is not set.
 */
final case class VersionInfo(version: String, vendor: String, runtime: String)

/**
 * The layer that reports the JVM version.
 */
object VersionInfo {

  /**
   * JVM version info: the gauge `jvm_info`, tagged with `version`, `vendor` and
   * `runtime`, which any update sets to 1.0.
   */
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

  private def reportVersions()(implicit trace: Trace): ZIO[Any, Throwable, VersionInfo] =
    for {
      version <- System.propertyOrElse("java.runtime.version", "unknown")
      vendor  <- System.propertyOrElse("java.vm.vendor", "unknown")
      runtime <- System.propertyOrElse("java.runtime.name", "unknown")
      _       <- jvmInfo(version, vendor, runtime).set(())
    } yield VersionInfo(version, vendor, runtime)

  /**
   * A layer that reads the JVM version once, sets the `jvm_info` gauge and
   * provides the [[VersionInfo]].
   */
  val live: ZLayer[Any, Throwable, VersionInfo] = ZLayer(reportVersions())
}
