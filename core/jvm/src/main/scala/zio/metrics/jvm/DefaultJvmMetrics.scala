package zio.metrics.jvm

import zio._

/**
 * JVM metrics, compatible with the prometheus-hotspot library, with
 * configurable schedule
 */
trait DefaultJvmMetrics {
  protected def jvmMetricsSchedule: ULayer[JvmMetricsSchedule]

  @deprecated(
    "This app exposes JVM metrics with the non-OpenMetrics-compliant names used in prometheus client_java 0.16: https://github.com/prometheus/client_java/blob/main/docs/content/migration/simpleclient.md#jvm-metrics. Use `appV2` instead, it uses names compatible with the client_java 1.0 library"
  )
  lazy val app   = withMetrics(live)
  lazy val appV2 = withMetrics(liveV2)

  /** A ZIO application that periodically updates the JVM metrics */
  private def withMetrics(metricsLayer: ZLayer[Any, Any, Any]): ZIOAppDefault = new ZIOAppDefault {
    override val bootstrap: ZLayer[ZIOAppArgs, Any, Any]         = metricsLayer
    override def run: ZIO[Environment with ZIOAppArgs, Any, Any] = ZIO.unit
  }

  @deprecated(
    "This layer exposes JVM metrics with the non-OpenMetrics-compliant names used in prometheus client_java 0.16: https://github.com/prometheus/client_java/blob/main/docs/content/migration/simpleclient.md#jvm-metrics. Use the `liveV2` layer instead, it uses names compatible with the client_java 1.0 library"
  )
  lazy val live
    : ZLayer[Any, Throwable, Reloadable[BufferPools] with ClassLoading with GarbageCollector with MemoryAllocation with MemoryPools with Standard with Thread with VersionInfo] =
    withMemoryPoolsLayer(MemoryPools.live)
  lazy val liveV2
    : ZLayer[Any, Throwable, Reloadable[BufferPools] with ClassLoading with GarbageCollector with MemoryAllocation with MemoryPools with Standard with Thread with VersionInfo] =
    withMemoryPoolsLayer(MemoryPools.liveV2)

  /**
   * Layer that starts collecting the same JVM metrics as the Prometheus Java
   * client's default exporters
   */
  def withMemoryPoolsLayer(memoryPools: ZLayer[JvmMetricsSchedule, Throwable, MemoryPools]): ZLayer[
    Any,
    Throwable,
    Reloadable[BufferPools]
      with ClassLoading
      with GarbageCollector
      with MemoryAllocation
      with MemoryPools
      with Standard
      with Thread
      with VersionInfo
  ] =
    jvmMetricsSchedule >>>
      (BufferPools.live ++
        ClassLoading.live ++
        GarbageCollector.live ++
        MemoryAllocation.live ++
        memoryPools ++
        Standard.live ++
        Thread.live ++
        VersionInfo.live)
}

/** JVM metrics, compatible with the prometheus-hotspot library */
object DefaultJvmMetrics extends DefaultJvmMetrics {
  override protected def jvmMetricsSchedule: ULayer[JvmMetricsSchedule] = JvmMetricsSchedule.default
}
