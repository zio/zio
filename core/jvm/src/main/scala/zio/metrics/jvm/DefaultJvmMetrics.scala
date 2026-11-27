package zio.metrics.jvm

import zio._

/**
 * JVM metrics, compatible with the prometheus-hotspot library, with
 * configurable schedule. Extend it and define `jvmMetricsSchedule` to use other
 * schedules, or use the `DefaultJvmMetrics` object for the default ones.
 *
 * {{{
 * import zio._
 *
 * val program: ZIO[Any, Throwable, Unit] =
 *   ZIO.sleep(1.minute).provideLayer(DefaultJvmMetrics.liveV2.unit)
 * }}}
 *
 * @see
 *   [[https://zio.dev/reference/observability/metrics/jvm/ JVM Metrics]]
 */
trait DefaultJvmMetrics {

  /**
   * The schedules that the JVM metrics layers of this trait use.
   */
  protected def jvmMetricsSchedule: ULayer[JvmMetricsSchedule]

  /**
   * A `ZIOAppDefault` whose `bootstrap` layer is `live` and whose `run` does
   * nothing, to combine with another app using `<>` so that the JVM metrics are
   * collected alongside it.
   */
  @deprecated(
    "This app exposes JVM metrics with the non-OpenMetrics-compliant names used in prometheus client_java 0.16: https://github.com/prometheus/client_java/blob/main/docs/content/migration/simpleclient.md#jvm-metrics. Use `appV2` instead, it uses names compatible with the client_java 1.0 library"
  )
  lazy val app = withMetrics(live)

  /**
   * A `ZIOAppDefault` whose `bootstrap` layer is `liveV2` and whose `run` does
   * nothing, to combine with another app using `<>` so that the JVM metrics are
   * collected alongside it.
   */
  lazy val appV2 = withMetrics(liveV2)

  /** A ZIO application that periodically updates the JVM metrics */
  private def withMetrics(metricsLayer: ZLayer[Any, Any, Any]): ZIOAppDefault = new ZIOAppDefault {
    override val bootstrap: ZLayer[ZIOAppArgs, Any, Any]         = metricsLayer
    override def run: ZIO[Environment with ZIOAppArgs, Any, Any] = ZIO.unit
  }

  /**
   * A layer that collects all the JVM metrics of this package, with the memory
   * metric names of the Prometheus Java client 0.16 (`MemoryPools.live`).
   */
  @deprecated(
    "This layer exposes JVM metrics with the non-OpenMetrics-compliant names used in prometheus client_java 0.16: https://github.com/prometheus/client_java/blob/main/docs/content/migration/simpleclient.md#jvm-metrics. Use the `liveV2` layer instead, it uses names compatible with the client_java 1.0 library"
  )
  lazy val live
    : ZLayer[Any, Throwable, Reloadable[BufferPools] with ClassLoading with GarbageCollector with MemoryAllocation with MemoryPools with Standard with Thread with VersionInfo] =
    withMemoryPoolsLayer(MemoryPools.live)

  /**
   * A layer that collects all the JVM metrics of this package, with the memory
   * metric names of the Prometheus Java client 1.0 (`MemoryPools.liveV2`).
   */
  lazy val liveV2
    : ZLayer[Any, Throwable, Reloadable[BufferPools] with ClassLoading with GarbageCollector with MemoryAllocation with MemoryPools with Standard with Thread with VersionInfo] =
    withMemoryPoolsLayer(MemoryPools.liveV2)

  /**
   * Layer that starts collecting the JVM metrics of `BufferPools`,
   * `ClassLoading`, `GarbageCollector`, `MemoryAllocation`, `Standard`,
   * `Thread` and `VersionInfo`, plus the specified memory pools layer. The
   * polled metrics use the schedules of `jvmMetricsSchedule`;
   * `MemoryAllocation` is updated after garbage collections, and `VersionInfo`
   * once, when the layer is built. The metric names follow the Prometheus Java
   * client's hotspot exporters.
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
      (BufferPools.live <*>
        ClassLoading.live <*>
        GarbageCollector.live <*>
        MemoryAllocation.live <*>
        memoryPools <*>
        Standard.live <*>
        Thread.live <*>
        VersionInfo.live)
}

/** JVM metrics, compatible with the prometheus-hotspot library */
object DefaultJvmMetrics extends DefaultJvmMetrics {
  override protected def jvmMetricsSchedule: ULayer[JvmMetricsSchedule] = JvmMetricsSchedule.default
}
