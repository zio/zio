package zio.metrics.jvm

import zio._

/** JVM metrics, compatible with the prometheus-hotspot library, with configurable schedule */
trait DefaultJvmMetrics extends MultipleJvmMetrics {
  protected val collectionSchedule: Schedule[Any, Any, Unit]

  override protected lazy val collectors: NonEmptyChunk[JvmMetrics] =
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

  /** Layer that starts collecting the same JVM metrics as the Prometheus Java client's default exporters */
  lazy val live: ZLayer[Has[Clock] with Has[System], Throwable, Has[BufferPools] with Has[ClassLoading] with Has[
    GarbageCollector
  ] with Has[MemoryAllocation] with Has[MemoryPools] with Has[Standard] with Has[Thread] with Has[VersionInfo]] = {
    BufferPools.live ++
      ClassLoading.live ++
      GarbageCollector.live ++
      MemoryAllocation.live ++
      MemoryPools.live ++
      Standard.live ++
      Thread.live ++
      VersionInfo.live
  }
}

/** JVM metrics, compatible with the prometheus-hotspot library */
object DefaultJvmMetrics extends DefaultJvmMetrics {
  override protected val collectionSchedule: Schedule[Any, Any, Unit] = JvmMetrics.defaultSchedule
}
