package zio.metrics.jvm

import zio._

/** JVM metrics, compatible with the prometheus-hotspot library */
object DefaultJvmMetrics {

  /**
   * While acquired it starts fibers periodically updating the same JVM metrics
   * as the Prometheus Java client's default exporters
   */
  val collectDefaultJvmMetrics: ZManaged[Has[Clock] with Has[System], Throwable, Unit] =
    ZManaged.foreachParDiscard(
      Seq(
        BufferPools,
        ClassLoading,
        GarbageCollector,
        MemoryAllocation,
        MemoryPools,
        Standard,
        Thread,
        VersionInfo
      )
    )(_.collectMetrics)

  /** Layer that starts collecting the same JVM metrics as the Prometheus Java client's default exporters */
  val live: ZLayer[Has[Clock] with Has[System], Throwable, Has[BufferPools] with Has[ClassLoading] with Has[
    GarbageCollector
  ] with Has[MemoryAllocation] with Has[MemoryPools] with Has[Standard] with Has[Thread] with Has[VersionInfo]] =
    BufferPools.live ++
      ClassLoading.live ++
      GarbageCollector.live ++
      MemoryAllocation.live ++
      MemoryPools.live ++
      Standard.live ++
      Thread.live ++
      VersionInfo.live

  /** A ZIO application that collects the same JVM metrics as the Prometheus Java client's default exporters. */
  val app: ZIOApp =
    BufferPools.app <>
      ClassLoading.app <>
      GarbageCollector.app <>
      MemoryAllocation.app <>
      MemoryPools.app <>
      Standard.app <>
      Thread.app <>
      VersionInfo.app
}
