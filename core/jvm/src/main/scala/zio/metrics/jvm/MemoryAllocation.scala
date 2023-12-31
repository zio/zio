package zio.metrics.jvm

import com.sun.management.GarbageCollectionNotificationInfo
import zio._
import zio.metrics.Metric.Counter
import zio.metrics._

import java.lang.management.{GarbageCollectorMXBean, ManagementFactory}
import javax.management.openmbean.CompositeData
import javax.management.{Notification, NotificationEmitter, NotificationListener}
import scala.annotation.nowarn
import scala.collection.JavaConverters._
import scala.collection.mutable

final case class MemoryAllocation(listener: NotificationListener, garbageCollectorMXBeans: List[GarbageCollectorMXBean])

object MemoryAllocation {

  /**
   * Total bytes allocated in a given JVM memory pool. Only updated after GC,
   * not continuously.
   */
  private def countAllocations(pool: String): Counter[Long] =
    Metric.counter("jvm_memory_pool_allocated_bytes_total").tagged(MetricLabel("pool", pool))

  private class Listener(runtime: Runtime[Any]) extends NotificationListener {
    private val lastMemoryUsage: mutable.Map[String, Long] = mutable.HashMap.empty

    @nowarn("msg=JavaConverters")
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
        runtime.unsafe.run(countAllocations(memoryPool).incrementBy(increase))(Trace.empty, Unsafe.unsafe)
      }
    }
  }

  @nowarn("msg=JavaConverters")
  val live: ZLayer[Any, Throwable, MemoryAllocation] =
    ZLayer.scoped {
      ZIO
        .acquireRelease(
          for {
            runtime                 <- ZIO.runtime[Any]
            listener                 = new Listener(runtime)
            garbageCollectorMXBeans <- ZIO.attempt(ManagementFactory.getGarbageCollectorMXBeans.asScala.toList)
            _ <- ZIO.foreachDiscard(garbageCollectorMXBeans) {
                   case emitter: NotificationEmitter =>
                     ZIO.attempt(emitter.addNotificationListener(listener, null, null))
                   case _ => ZIO.unit
                 }
          } yield MemoryAllocation(listener, garbageCollectorMXBeans)
        ) { memoryAllocation =>
          ZIO
            .foreachDiscard(memoryAllocation.garbageCollectorMXBeans) {
              case emitter: NotificationEmitter =>
                ZIO.attempt(emitter.removeNotificationListener(memoryAllocation.listener))
              case _ => ZIO.unit
            }
            .orDie
        }
    }
}
