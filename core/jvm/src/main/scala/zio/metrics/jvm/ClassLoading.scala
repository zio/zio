package zio.metrics.jvm

import zio.ZIOMetric.Gauge
import zio._

import java.lang.management.{ClassLoadingMXBean, ManagementFactory}

/** Exports metrics related to JVM class loading */
object ClassLoading extends JvmMetrics {

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
  ): ZIO[Any, Throwable, Unit] =
    for {
      _ <- Task(classLoadingMXBean.getLoadedClassCount) @@ loadedClassCount
      _ <- Task(classLoadingMXBean.getTotalLoadedClassCount) @@ totalLoadedClassCount
      _ <- Task(classLoadingMXBean.getUnloadedClassCount) @@ unloadedClassCount
    } yield ()

  val collectMetrics: ZManaged[Has[Clock], Throwable, Unit] =
    ZManaged.acquireReleaseWith {
      for {
        classLoadingMXBean <-
          Task(ManagementFactory.getPlatformMXBean(classOf[ClassLoadingMXBean]))
        fiber <- reportClassLoadingMetrics(classLoadingMXBean)
                   .repeat(collectionSchedule)
                   .interruptible
                   .forkDaemon
      } yield fiber
    }(_.interrupt).unit
}
