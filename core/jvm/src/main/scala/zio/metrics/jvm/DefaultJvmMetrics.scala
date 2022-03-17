package zio.metrics.jvm

import zio._

import zio.stacktracer.TracingImplicits.disableAutoTrace

/**
 * JVM metrics, compatible with the prometheus-hotspot library, with
 * configurable schedule
 */
trait DefaultJvmMetrics extends MultipleJvmMetrics {
  protected def collectionSchedule(implicit trace: ZTraceElement): Schedule[Any, Any, Unit]

  override protected def collectors(implicit trace: ZTraceElement): NonEmptyChunk[JvmMetrics] =
    NonEmptyChunk(
      BufferPools.withSchedule(collectionSchedule),
      ClassLoading.withSchedule(collectionSchedule),
      GarbageCollector.withSchedule(collectionSchedule),
      MemoryAllocation.withSchedule(collectionSchedule),
      MemoryPools.withSchedule(collectionSchedule),
      Standard.withSchedule(collectionSchedule),
      Thread.withSchedule(collectionSchedule),
      VersionInfo.withSchedule(collectionSchedule)
    )

  /**
   * Layer that starts collecting the same JVM metrics as the Prometheus Java
   * client's default exporters
   */
  lazy val live: ZLayer[
    Clock with System,
    Throwable,
    BufferPools with ClassLoading with GarbageCollector with MemoryAllocation with MemoryPools with Standard with Thread with VersionInfo
  ] =
    BufferPools.live ++
      ClassLoading.live ++
      GarbageCollector.live ++
      MemoryAllocation.live ++
      MemoryPools.live ++
      Standard.live ++
      Thread.live ++
      VersionInfo.live
}

/** JVM metrics, compatible with the prometheus-hotspot library */
object DefaultJvmMetrics extends DefaultJvmMetrics {
  override protected def collectionSchedule(implicit trace: ZTraceElement): Schedule[Any, Any, Unit] =
    JvmMetrics.defaultSchedule
}
