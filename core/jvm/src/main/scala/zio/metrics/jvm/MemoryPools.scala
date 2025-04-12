package zio.metrics.jvm

import zio._
import zio.metrics._

import java.lang.management.{ManagementFactory, MemoryPoolMXBean, MemoryUsage}
import scala.jdk.CollectionConverters._

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

  val liveV2: ZLayer[JvmMetricsSchedule, Throwable, MemoryPools] = withNames(
    jvmMemoryCommittedBytes = "jvm_memory_committed_bytes",
    jvmMemoryInitBytes = "jvm_memory_init_bytes",
    jvmMemoryMaxBytes = "jvm_memory_max_bytes",
    jvmMemoryUsedBytes = "jvm_memory_used_bytes",
    jvmMemoryPoolUsedBytes = "jvm_memory_pool_used_bytes",
    jvmMemoryPoolCommittedBytes = "jvm_memory_pool_committed_bytes",
    jvmMemoryPoolMaxBytes = "jvm_memory_pool_max_bytes",
    jvmMemoryPoolInitBytes = "jvm_memory_pool_init_bytes"
  )

  @deprecated(
    "This layer exposes JVM metrics with the non-OpenMetrics-compliant names used in prometheus client_java 0.16: https://github.com/prometheus/client_java/blob/main/docs/content/migration/simpleclient.md#jvm-metrics. Use the `liveV2` layer instead, it uses names compatible with the client_java 1.0 library"
  )
  val live: ZLayer[JvmMetricsSchedule, Throwable, MemoryPools] = withNames(
    jvmMemoryCommittedBytes = "jvm_memory_bytes_committed",
    jvmMemoryInitBytes = "jvm_memory_bytes_init",
    jvmMemoryMaxBytes = "jvm_memory_bytes_max",
    jvmMemoryUsedBytes = "jvm_memory_bytes_used",
    jvmMemoryPoolUsedBytes = "jvm_memory_pool_bytes_used",
    jvmMemoryPoolCommittedBytes = "jvm_memory_pool_bytes_committed",
    jvmMemoryPoolMaxBytes = "jvm_memory_pool_bytes_max",
    jvmMemoryPoolInitBytes = "jvm_memory_pool_bytes_init"
  )

  private def withNames(
    jvmMemoryCommittedBytes: String,
    jvmMemoryInitBytes: String,
    jvmMemoryMaxBytes: String,
    jvmMemoryUsedBytes: String,
    jvmMemoryPoolUsedBytes: String,
    jvmMemoryPoolCommittedBytes: String,
    jvmMemoryPoolMaxBytes: String,
    jvmMemoryPoolInitBytes: String
  ): ZLayer[JvmMetricsSchedule, Throwable, MemoryPools] =
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
        memoryUsedBytes = pollingMemoryMetric(
                            jvmMemoryUsedBytes,
                            ZIO.attempt(memoryMXBean.getHeapMemoryUsage.getUsed),
                            ZIO.attempt(memoryMXBean.getNonHeapMemoryUsage.getUsed)
                          )
        memoryCommittedBytes = pollingMemoryMetric(
                                 jvmMemoryCommittedBytes,
                                 ZIO.attempt(memoryMXBean.getHeapMemoryUsage.getCommitted),
                                 ZIO.attempt(memoryMXBean.getNonHeapMemoryUsage.getCommitted)
                               )
        memoryMaxBytes = pollingMemoryMetric(
                           jvmMemoryMaxBytes,
                           ZIO.attempt(memoryMXBean.getHeapMemoryUsage.getMax),
                           ZIO.attempt(memoryMXBean.getNonHeapMemoryUsage.getMax)
                         )
        memoryInitBytes = pollingMemoryMetric(
                            jvmMemoryInitBytes,
                            ZIO.attempt(memoryMXBean.getHeapMemoryUsage.getInit),
                            ZIO.attempt(memoryMXBean.getNonHeapMemoryUsage.getInit)
                          )

        poolUsedBytes = PollingMetric.collectAll(poolMXBeans.flatMap { poolBean =>
                          pollingPoolMetric(
                            poolBean,
                            jvmMemoryPoolUsedBytes,
                            "jvm_memory_pool_collection_used_bytes",
                            _.getUsed
                          )
                        })
        poolCommittedBytes = PollingMetric.collectAll(poolMXBeans.flatMap { poolBean =>
                               pollingPoolMetric(
                                 poolBean,
                                 jvmMemoryPoolCommittedBytes,
                                 "jvm_memory_pool_collection_committed_bytes",
                                 _.getCommitted
                               )
                             })
        poolMaxBytes = PollingMetric.collectAll(poolMXBeans.flatMap { poolBean =>
                         pollingPoolMetric(
                           poolBean,
                           jvmMemoryPoolMaxBytes,
                           "jvm_memory_pool_collection_max_bytes",
                           _.getMax
                         )
                       })
        poolInitBytes = PollingMetric.collectAll(poolMXBeans.flatMap { poolBean =>
                          pollingPoolMetric(
                            poolBean,
                            jvmMemoryPoolInitBytes,
                            "jvm_memory_pool_collection_init_bytes",
                            _.getInit
                          )
                        })

        schedule <- ZIO.service[JvmMetricsSchedule]
        _        <- objectsPendingFinalization.launch(schedule.updateMetrics)
        _        <- memoryUsedBytes.launch(schedule.updateMetrics)
        _        <- memoryCommittedBytes.launch(schedule.updateMetrics)
        _        <- memoryMaxBytes.launch(schedule.updateMetrics)
        _        <- memoryInitBytes.launch(schedule.updateMetrics)
        _        <- poolUsedBytes.launch(schedule.updateMetrics)
        _        <- poolCommittedBytes.launch(schedule.updateMetrics)
        _        <- poolMaxBytes.launch(schedule.updateMetrics)
        _        <- poolInitBytes.launch(schedule.updateMetrics)
      } yield MemoryPools(
        memoryUsedBytes,
        memoryCommittedBytes,
        memoryMaxBytes,
        memoryInitBytes,
        poolUsedBytes,
        poolCommittedBytes,
        poolMaxBytes,
        poolInitBytes
      )
    }
}
