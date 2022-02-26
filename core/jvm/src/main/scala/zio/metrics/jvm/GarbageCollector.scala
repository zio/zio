package zio.metrics.jvm

import com.github.ghik.silencer.silent

import zio.ZIOMetric.Gauge
import zio._
import zio.stacktracer.TracingImplicits.disableAutoTrace

import java.lang.management.{GarbageCollectorMXBean, ManagementFactory}

import scala.collection.JavaConverters._

trait GarbageCollector extends JvmMetrics {
  override type Feature = GarbageCollector
  override val featureTag = Tag[GarbageCollector]

  /** Time spent in a given JVM garbage collector in seconds. */
  private def gcCollectionSecondsSum(gc: String): Gauge[Long] =
    ZIOMetric.setGaugeWith("jvm_gc_collection_seconds_sum", MetricLabel("gc", gc))((ms: Long) => ms.toDouble / 1000.0)

  private def gcCollectionSecondsCount(gc: String): Gauge[Long] =
    ZIOMetric.setGaugeWith("jvm_gc_collection_seconds_count", MetricLabel("gc", gc))(_.toDouble)

  private def reportGarbageCollectionMetrics(
    garbageCollectors: List[GarbageCollectorMXBean]
  )(implicit trace: ZTraceElement): ZIO[Any, Throwable, Unit] =
    ZIO.foreachParDiscard(garbageCollectors) { gc =>
      for {
        name <- Task(gc.getName)
        _    <- Task(gc.getCollectionCount) @@ gcCollectionSecondsCount(name)
        _    <- Task(gc.getCollectionTime) @@ gcCollectionSecondsSum(name)
      } yield ()
    }

  @silent("JavaConverters")
  def collectMetrics(implicit trace: ZTraceElement): ZIO[Clock with Scope, Throwable, GarbageCollector] =
    for {
      classLoadingMXBean <- Task(ManagementFactory.getGarbageCollectorMXBeans.asScala.toList)
      _ <- reportGarbageCollectionMetrics(classLoadingMXBean)
             .repeat(collectionSchedule)
             .interruptible
             .forkScoped
    } yield this
}

/** Exports metrics related to the garbage collector */
object GarbageCollector extends GarbageCollector with JvmMetrics.DefaultSchedule {
  def withSchedule(schedule: Schedule[Any, Any, Unit]): GarbageCollector = new GarbageCollector {
    override protected def collectionSchedule(implicit trace: ZTraceElement): Schedule[Any, Any, Unit] = schedule
  }
}
