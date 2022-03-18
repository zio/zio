package zio.metrics.jvm

import zio._
import zio.internal.stacktracer.Tracer

/**
 * JVM metrics, compatible with the prometheus-hotspot library, with
 * configurable schedule
 */
trait DefaultJvmMetrics {
  protected def jvmMetricsSchedule: ULayer[JvmMetricsSchedule]

  /** A ZIO application that periodically updates the JVM metrics */
  lazy val app: ZIOApp = new ZIOApp {
    private implicit val trace: ZTraceElement     = Tracer.newTrace
    override val tag: EnvironmentTag[Environment] = EnvironmentTag[Environment]
    override type Environment = Any
    override val layer: ZLayer[ZIOAppArgs, Any, Environment] = {
      live
    }
    override def run: ZIO[Environment with ZIOAppArgs, Any, Any] = ZIO.unit
  }

  /**
   * Layer that starts collecting the same JVM metrics as the Prometheus Java
   * client's default exporters
   */
  lazy val live: ZLayer[
    Any,
    Throwable,
    BufferPools with ClassLoading with GarbageCollector with MemoryAllocation with MemoryPools with Standard with Thread with VersionInfo
  ] =
    jvmMetricsSchedule >>>
      (BufferPools.live ++
        ClassLoading.live ++
        GarbageCollector.live ++
        MemoryAllocation.live ++
        MemoryPools.live ++
        Standard.live ++
        Thread.live ++
        VersionInfo.live)
}

/** JVM metrics, compatible with the prometheus-hotspot library */
object DefaultJvmMetrics extends DefaultJvmMetrics {
  override protected def jvmMetricsSchedule: ULayer[JvmMetricsSchedule] = JvmMetricsSchedule.default
}
