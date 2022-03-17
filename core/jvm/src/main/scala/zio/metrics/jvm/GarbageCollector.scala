package zio.metrics.jvm

import com.github.ghik.silencer.silent

import zio._
import zio.metrics._
import zio.metrics.Metric.Gauge
import zio.stacktracer.TracingImplicits.disableAutoTrace

import java.lang.management.{GarbageCollectorMXBean, ManagementFactory}

import scala.collection.JavaConverters._

trait GarbageCollector extends JvmMetrics {
  override type Feature = GarbageCollector
  override val featureTag = Tag[GarbageCollector]

  /** Time spent in a given JVM garbage collector in seconds. */
  private def gcCollectionSecondsSum(gc: String): Gauge[Long] =
    Metric
      .gauge("jvm_gc_collection_seconds_sum")
      .tagged(MetricLabel("gc", gc))
      .contramap((ms: Long) => ms.toDouble / 1000.0)

  private def gcCollectionSecondsCount(gc: String): Gauge[Long] =
    Metric.gauge("jvm_gc_collection_seconds_count").tagged(MetricLabel("gc", gc)).contramap(_.toDouble)

  private def reportGarbageCollectionMetrics(
    garbageCollectors: List[GarbageCollectorMXBean]
  )(implicit trace: ZTraceElement): ZIO[Any, Throwable, Unit] =
    ZIO.foreachParDiscard(garbageCollectors) { gc =>
      for {
        name <- ZIO.attempt(gc.getName)
        _    <- gcCollectionSecondsCount(name).set(gc.getCollectionCount)
        _    <- gcCollectionSecondsSum(name).set(gc.getCollectionTime)
      } yield ()
    }

  @silent("JavaConverters")
  def collectMetrics(implicit trace: ZTraceElement): ZIO[Clock with Scope, Throwable, GarbageCollector] =
    for {
      classLoadingMXBean <- ZIO.attempt(ManagementFactory.getGarbageCollectorMXBeans.asScala.toList)
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
