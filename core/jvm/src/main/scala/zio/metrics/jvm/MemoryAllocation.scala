package zio.metrics.jvm

import com.github.ghik.silencer.silent

import com.sun.management.GarbageCollectionNotificationInfo
import zio._
import zio.stacktracer.TracingImplicits.disableAutoTrace
import zio.ZIOMetric.Counter

import java.lang.management.ManagementFactory
import javax.management.openmbean.CompositeData
import javax.management.{Notification, NotificationEmitter, NotificationListener}
import scala.collection.mutable
import scala.collection.JavaConverters._

trait MemoryAllocation extends JvmMetrics {
  override type Feature = MemoryAllocation
  override val featureTag: EnvironmentTag[MemoryAllocation] = EnvironmentTag[MemoryAllocation]
  implicit val trace: ZTraceElement                         = ZTraceElement.empty

  /**
   * Total bytes allocated in a given JVM memory pool. Only updated after GC,
   * not continuously.
   */
  private def countAllocations(pool: String): Counter[Long] =
    ZIOMetric.countValueWith("jvm_memory_pool_allocated_bytes_total", MetricLabel("pool", pool))(_.toDouble)

  private class Listener(runtime: Runtime[Any]) extends NotificationListener {
    private val lastMemoryUsage: mutable.Map[String, Long] = mutable.HashMap.empty

    @silent("JavaConverters")
    override def handleNotification(notification: Notification, handback: Any): Unit = {
      val info =
        GarbageCollectionNotificationInfo.from(notification.getUserData.asInstanceOf[CompositeData])
      val gcInfo              = info.getGcInfo
      val memoryUsageBeforeGc = gcInfo.getMemoryUsageBeforeGc
      val memoryUsageAfterGc  = gcInfo.getMemoryUsageAfterGc
      for (entry <- memoryUsageBeforeGc.entrySet.asScala) {
        val memoryPool = entry.getKey
        val before     = entry.getValue.getUsed
        val after      = memoryUsageAfterGc.get(memoryPool).getUsed
        handleMemoryPool(memoryPool, before, after)
      }
    }

    private def handleMemoryPool(memoryPool: String, before: Long, after: Long): Unit = {
      /*
       * Calculate increase in the memory pool by comparing memory used
       * after last GC, before this GC, and after this GC.
       * See ascii illustration below.
       * Make sure to count only increases and ignore decreases.
       * (Typically a pool will only increase between GCs or during GCs, not both.
       * E.g. eden pools between GCs. Survivor and old generation pools during GCs.)
       *
       *                         |<-- diff1 -->|<-- diff2 -->|
       * Timeline: |-- last GC --|             |---- GC -----|
       *                      ___^__        ___^____      ___^___
       * Mem. usage vars:    / last \      / before \    / after \
       */
      // Get last memory usage after GC and remember memory used after for next time
      val last = lastMemoryUsage.getOrElse(memoryPool, 0L)
      lastMemoryUsage.put(memoryPool, after)
      // Difference since last GC
      var diff1 = before - last
      // Difference during this GC
      var diff2 = after - before
      // Make sure to only count increases
      if (diff1 < 0) diff1 = 0
      if (diff2 < 0) diff2 = 0
      val increase = diff1 + diff2
      if (increase > 0) {
        val effect: ZIO[Any, Nothing, Long] = UIO(increase) @@ countAllocations(memoryPool)
        runtime.unsafeRun(effect.unit)
      }
    }
  }

  @silent("JavaConverters")
  override def collectMetrics(implicit
    trace: ZTraceElement
  ): ZManaged[Clock with System, Throwable, MemoryAllocation] =
    ZManaged
      .acquireReleaseWith(
        for {
          runtime                 <- ZIO.runtime[Any]
          listener                 = new Listener(runtime)
          garbageCollectorMXBeans <- Task(ManagementFactory.getGarbageCollectorMXBeans.asScala)
          _ <- ZIO.foreachDiscard(garbageCollectorMXBeans) {
                 case emitter: NotificationEmitter =>
                   Task(emitter.addNotificationListener(listener, null, null))
                 case _ => ZIO.unit
               }
        } yield (listener, garbageCollectorMXBeans)
      ) { case (listener, garbageCollectorMXBeans) =>
        ZIO
          .foreachDiscard(garbageCollectorMXBeans) {
            case emitter: NotificationEmitter =>
              Task(emitter.removeNotificationListener(listener))
            case _ => ZIO.unit
          }
          .orDie
      }
      .as(this)
}

object MemoryAllocation extends MemoryAllocation with JvmMetrics.DefaultSchedule {
  def withSchedule(schedule: Schedule[Any, Any, Unit]): MemoryAllocation = new MemoryAllocation {
    override protected def collectionSchedule(implicit trace: ZTraceElement): Schedule[Any, Any, Unit] = schedule
  }
}
