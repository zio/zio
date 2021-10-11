package zio.metrics.jvm

import zio.ZIOMetric.Gauge
import zio._

import java.lang.management.{ClassLoadingMXBean, ManagementFactory}

trait ClassLoading extends JvmMetrics {
  override type Feature = ClassLoading
  override val featureTag: Tag[ClassLoading] = Tag[ClassLoading]

  /** The number of classes that are currently loaded in the JVM */
  private val loadedClassCount: Gauge[Int] =
    ZIOMetric.setGaugeWith("jvm_classes_loaded")(_.toDouble)

  /** The total number of classes that have been loaded since the JVM has started execution */
  private val totalLoadedClassCount: Gauge[Long] =
    ZIOMetric.setGaugeWith("jvm_classes_loaded_total")(_.toDouble)

  /** The total number of classes that have been unloaded since the JVM has started execution */
  private val unloadedClassCount: Gauge[Long] =
    ZIOMetric.setGaugeWith("jvm_classes_unloaded_total")(_.toDouble)

  private def reportClassLoadingMetrics(
    classLoadingMXBean: ClassLoadingMXBean
  )(implicit trace: ZTraceElement): ZIO[Any, Throwable, Unit] =
    for {
      _ <- Task(classLoadingMXBean.getLoadedClassCount) @@ loadedClassCount
      _ <- Task(classLoadingMXBean.getTotalLoadedClassCount) @@ totalLoadedClassCount
      _ <- Task(classLoadingMXBean.getUnloadedClassCount) @@ unloadedClassCount
    } yield ()

  def collectMetrics(implicit trace: ZTraceElement): ZManaged[Has[Clock], Throwable, ClassLoading] =
    for {
      classLoadingMXBean <-
        Task(ManagementFactory.getPlatformMXBean(classOf[ClassLoadingMXBean])).toManaged
      _ <- reportClassLoadingMetrics(classLoadingMXBean)
             .repeat(collectionSchedule)
             .interruptible
             .forkManaged
    } yield this
}

/** Exports metrics related to JVM class loading */
object ClassLoading extends ClassLoading with JvmMetrics.DefaultSchedule {
  def withSchedule(schedule: Schedule[Any, Any, Unit]): ClassLoading = new ClassLoading {
    override protected def collectionSchedule(implicit trace: ZTraceElement): Schedule[Any, Any, Unit] = schedule
  }
}
