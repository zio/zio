package zio.metrics.jvm

import zio._

trait DefaultJvmMetrics

/** JVM metrics, compatible with the prometheus-hotspot library */
object DefaultJvmMetrics {
  val collectDefaultJvmMetrics: ZManaged[Has[Clock] with Has[System], Throwable, Unit] =
    (
      BufferPools.collectMetrics <&>
        ClassLoading.collectMetrics <&>
        GarbageCollector.collectMetrics <&>
        MemoryAllocation.collectMetrics <&>
        MemoryPools.collectMetrics <&>
        Standard.collectMetrics <&>
        Thread.collectMetrics <&>
        VersionInfo.collectMetrics
    ).unit

  /** Layer that starts collecting the same JVM metrics as the Prometheus Java client's default exporters */
  val live: ZLayer[Has[Clock] with Has[System], Throwable, Has[DefaultJvmMetrics]] =
    collectDefaultJvmMetrics.as(new DefaultJvmMetrics {}).toLayer
}
