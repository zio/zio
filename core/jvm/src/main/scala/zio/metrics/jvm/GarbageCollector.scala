package zio.metrics.jvm

import zio.ZIOMetric.Gauge
import zio._

import java.lang.management.{GarbageCollectorMXBean, ManagementFactory}

import scala.collection.JavaConverters._

/** Exports metrics related to the garbage collector */
object GarbageCollector extends JvmMetrics {

  /** Time spent in a given JVM garbage collector in seconds. */
  private def gcCollectionSecondsSum(gc: String): Gauge[Long] =
    ZIOMetric.setGaugeWith("jvm_gc_collection_seconds_sum", MetricLabel("gc", gc))((ms: Long) => ms.toDouble / 1000.0)

  private def gcCollectionSecondsCount(gc: String): Gauge[Long] =
    ZIOMetric.setGaugeWith("jvm_gc_collection_seconds_count", MetricLabel("gc", gc))(_.toDouble)

  private def reportGarbageCollectionMetrics(
    garbageCollectors: List[GarbageCollectorMXBean]
  ): ZIO[Any, Throwable, Unit] =
    ZIO.foreachParDiscard(garbageCollectors) { gc =>
      for {
        name <- Task(gc.getName)
        _    <- Task(gc.getCollectionCount) @@ gcCollectionSecondsCount(name)
        _    <- Task(gc.getCollectionTime) @@ gcCollectionSecondsSum(name)
      } yield ()
    }

  val collectMetrics: ZManaged[Has[Clock], Throwable, Unit] =
    ZManaged.acquireReleaseWith {
      for {
        classLoadingMXBean <- Task(ManagementFactory.getGarbageCollectorMXBeans.asScala.toList)
        fiber <-
          reportGarbageCollectionMetrics(classLoadingMXBean)
            .repeat(collectionSchedule)
            .interruptible
            .forkDaemon
      } yield fiber
    }(_.interrupt).unit
}
