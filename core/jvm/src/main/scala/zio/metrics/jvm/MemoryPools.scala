package zio.metrics.jvm

import zio._
import zio.metrics.MetricKeyType.Gauge
import zio.metrics._

import java.lang.management.{ManagementFactory, MemoryPoolMXBean, MemoryUsage}
import scala.annotation.nowarn
import scala.collection.JavaConverters._

final case class MemoryPools(
  memoryBytesUsed: PollingMetric[Any, Throwable, Chunk[MetricState.Gauge]],
  memoryBytesCommitted: PollingMetric[Any, Throwable, Chunk[MetricState.Gauge]],
  memoryBytesMax: PollingMetric[Any, Throwable, Chunk[MetricState.Gauge]],
  memoryBytesInit: PollingMetric[Any, Throwable, Chunk[MetricState.Gauge]],
  poolBytesUsed: PollingMetric[Any, Throwable, Chunk[MetricState.Gauge]],
  poolBytesCommitted: PollingMetric[Any, Throwable, Chunk[MetricState.Gauge]],
  poolBytesMax: PollingMetric[Any, Throwable, Chunk[MetricState.Gauge]],
  poolBytesInit: PollingMetric[Any, Throwable, Chunk[MetricState.Gauge]]
)

object MemoryPools {
  sealed private trait Area { val label: String }
  private case object Heap    extends Area { override val label: String = "heap"    }
  private case object NonHeap extends Area { override val label: String = "nonheap" }

  private def pollingMemoryMetric(
    name: String,
    pollHeap: ZIO[Any, Throwable, Long],
    pollNonHeap: ZIO[Any, Throwable, Long]
  ): PollingMetric[Any, Throwable, Chunk[MetricState.Gauge]] =
    PollingMetric.collectAll(
      Seq(
        PollingMetric(
          Metric
            .gauge(name)
            .tagged("area", Heap.label)
            .contramap[Long](_.toDouble),
          pollHeap
        ),
        PollingMetric(
          Metric
            .gauge(name)
            .tagged("area", NonHeap.label)
            .contramap[Long](_.toDouble),
          pollNonHeap
        )
      )
    )

  private def pollingPoolMetric(
    poolBean: MemoryPoolMXBean,
    name: String,
    collectionName: String,
    getter: MemoryUsage => Long
  ): Seq[PollingMetric[Any, Throwable, MetricState.Gauge]] =
    Seq(
      PollingMetric(
        Metric
          .gauge(name)
          .tagged("pool", poolBean.getName)
          .contramap[Long](_.toDouble),
        ZIO.attempt {
          val usage = poolBean.getUsage
          if (usage ne null)
            getter(usage)
          else 0L
        }
      ),
      PollingMetric(
        Metric
          .gauge(collectionName)
          .tagged("pool", poolBean.getName)
          .contramap[Long](_.toDouble),
        ZIO.attempt {
          val usage = poolBean.getCollectionUsage
          if (usage ne null)
            getter(usage)
          else 0L
        }
      )
    )

  @nowarn("msg=JavaConverters")
  val live: ZLayer[JvmMetricsSchedule, Throwable, MemoryPools] =
    ZLayer.scoped {
      for {
        memoryMXBean <- ZIO.attempt(ManagementFactory.getMemoryMXBean)
        poolMXBeans  <- ZIO.attempt(ManagementFactory.getMemoryPoolMXBeans.asScala.toList)

        objectsPendingFinalization = PollingMetric(
                                       Metric
                                         .gauge("jvm_memory_objects_pending_finalization")
                                         .contramap[Int](_.toDouble),
                                       ZIO.attempt(memoryMXBean.getObjectPendingFinalizationCount)
                                     )
        memoryBytesUsed = pollingMemoryMetric(
                            "jvm_memory_bytes_used",
                            ZIO.attempt(memoryMXBean.getHeapMemoryUsage.getUsed),
                            ZIO.attempt(memoryMXBean.getNonHeapMemoryUsage.getUsed)
                          )
        memoryBytesCommitted = pollingMemoryMetric(
                                 "jvm_memory_bytes_committed",
                                 ZIO.attempt(memoryMXBean.getHeapMemoryUsage.getCommitted),
                                 ZIO.attempt(memoryMXBean.getNonHeapMemoryUsage.getCommitted)
                               )
        memoryBytesMax = pollingMemoryMetric(
                           "jvm_memory_bytes_max",
                           ZIO.attempt(memoryMXBean.getHeapMemoryUsage.getMax),
                           ZIO.attempt(memoryMXBean.getNonHeapMemoryUsage.getMax)
                         )
        memoryBytesInit = pollingMemoryMetric(
                            "jvm_memory_bytes_init",
                            ZIO.attempt(memoryMXBean.getHeapMemoryUsage.getInit),
                            ZIO.attempt(memoryMXBean.getNonHeapMemoryUsage.getInit)
                          )

        poolBytesUsed = PollingMetric.collectAll(poolMXBeans.flatMap { poolBean =>
                          pollingPoolMetric(
                            poolBean,
                            "jvm_memory_pool_bytes_used",
                            "jvm_memory_pool_collection_used_bytes",
                            _.getUsed
                          )
                        })
        poolBytesCommitted = PollingMetric.collectAll(poolMXBeans.flatMap { poolBean =>
                               pollingPoolMetric(
                                 poolBean,
                                 "jvm_memory_pool_bytes_committed",
                                 "jvm_memory_pool_collection_committed_bytes",
                                 _.getCommitted
                               )
                             })
        poolBytesMax = PollingMetric.collectAll(poolMXBeans.flatMap { poolBean =>
                         pollingPoolMetric(
                           poolBean,
                           "jvm_memory_pool_bytes_max",
                           "jvm_memory_pool_collection_max_bytes",
                           _.getMax
                         )
                       })
        poolBytesInit = PollingMetric.collectAll(poolMXBeans.flatMap { poolBean =>
                          pollingPoolMetric(
                            poolBean,
                            "jvm_memory_pool_bytes_init",
                            "jvm_memory_pool_collection_init_bytes",
                            _.getInit
                          )
                        })

        schedule <- ZIO.service[JvmMetricsSchedule]
        _        <- objectsPendingFinalization.launch(schedule.updateMetrics)
        _        <- memoryBytesUsed.launch(schedule.updateMetrics)
        _        <- memoryBytesCommitted.launch(schedule.updateMetrics)
        _        <- memoryBytesMax.launch(schedule.updateMetrics)
        _        <- memoryBytesInit.launch(schedule.updateMetrics)
        _        <- poolBytesUsed.launch(schedule.updateMetrics)
        _        <- poolBytesCommitted.launch(schedule.updateMetrics)
        _        <- poolBytesMax.launch(schedule.updateMetrics)
        _        <- poolBytesInit.launch(schedule.updateMetrics)
      } yield MemoryPools(
        memoryBytesUsed,
        memoryBytesCommitted,
        memoryBytesMax,
        memoryBytesInit,
        poolBytesUsed,
        poolBytesCommitted,
        poolBytesMax,
        poolBytesInit
      )
    }
}
