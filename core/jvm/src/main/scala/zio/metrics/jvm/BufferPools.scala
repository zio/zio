package zio.metrics.jvm

import zio._
import zio.metrics._

import java.lang.management.{BufferPoolMXBean, ManagementFactory}
import scala.jdk.CollectionConverters._

/**
 * The polling metrics of the JVM buffer pools (`BufferPoolMXBean`). Each one is
 * a gauge per buffer pool, tagged with `pool` and the name of the pool.
 *
 * @param bufferPoolUsedBytes
 *   `jvm_buffer_pool_used_bytes`: the memory that each pool uses, in bytes
 * @param bufferPoolCapacityBytes
 *   `jvm_buffer_pool_capacity_bytes`: the total capacity of the buffers in each
 *   pool, in bytes
 * @param bufferPoolUsedBuffers
 *   `jvm_buffer_pool_used_buffers`: the number of buffers in each pool
 */
final case class BufferPools(
  bufferPoolUsedBytes: PollingMetric[Any, Throwable, Chunk[MetricState.Gauge]],
  bufferPoolCapacityBytes: PollingMetric[Any, Throwable, Chunk[MetricState.Gauge]],
  bufferPoolUsedBuffers: PollingMetric[Any, Throwable, Chunk[MetricState.Gauge]]
)

/**
 * The layer that collects the buffer pool metrics.
 */
object BufferPools {

  /**
   * A layer that polls the buffer pool metrics on the `updateMetrics` schedule
   * of [[JvmMetricsSchedule]]. The layer is rebuilt on the
   * `reloadDynamicMetrics` schedule, which reads the list of buffer pools
   * again, so it provides a `Reloadable[BufferPools]`.
   */
  val live: ZLayer[JvmMetricsSchedule, Throwable, Reloadable[BufferPools]] =
    ZLayer.scoped {
      for {
        bufferPoolMXBeans <- ZIO.attempt(ManagementFactory.getPlatformMXBeans(classOf[BufferPoolMXBean]).asScala)
        bufferPoolUsedBytes = PollingMetric.collectAll(bufferPoolMXBeans.map { mxBean =>
                                PollingMetric(
                                  Metric
                                    .gauge("jvm_buffer_pool_used_bytes")
                                    .tagged("pool", mxBean.getName)
                                    .contramap[Long](_.toDouble),
                                  ZIO.attempt(mxBean.getMemoryUsed)
                                )
                              })
        bufferPoolCapacityBytes = PollingMetric.collectAll(bufferPoolMXBeans.map { mxBean =>
                                    PollingMetric(
                                      Metric
                                        .gauge("jvm_buffer_pool_capacity_bytes")
                                        .tagged("pool", mxBean.getName)
                                        .contramap[Long](_.toDouble),
                                      ZIO.attempt(mxBean.getTotalCapacity)
                                    )
                                  })
        bufferPoolUsedBuffers = PollingMetric.collectAll(bufferPoolMXBeans.map { mxBean =>
                                  PollingMetric(
                                    Metric
                                      .gauge("jvm_buffer_pool_used_buffers")
                                      .tagged("pool", mxBean.getName)
                                      .contramap[Long](_.toDouble),
                                    ZIO.attempt(mxBean.getCount)
                                  )
                                })

        schedule <- ZIO.service[JvmMetricsSchedule]
        // use interruptible to ensure the forked fibers can be interrupted
        // (reloadableAutoFromConfig causes this layer to be in an uninterruptible region)
        _ <- bufferPoolUsedBytes.launch(schedule.updateMetrics).interruptible
        _ <- bufferPoolCapacityBytes.launch(schedule.updateMetrics).interruptible
        _ <- bufferPoolUsedBuffers.launch(schedule.updateMetrics).interruptible
      } yield BufferPools(bufferPoolUsedBytes, bufferPoolCapacityBytes, bufferPoolUsedBuffers)
    }.reloadableAutoFromConfig[JvmMetricsSchedule](config => config.get.reloadDynamicMetrics)
}
