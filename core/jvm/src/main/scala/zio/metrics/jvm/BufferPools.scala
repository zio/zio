package zio.metrics.jvm

import zio._

import java.lang.management.{BufferPoolMXBean, ManagementFactory}

object BufferPools extends JvmMetrics {

  /** Used bytes of a given JVM buffer pool. */
  private def bufferPoolUsedBytes(pool: String): ZIOMetric.Gauge[Long] =
    ZIOMetric.setGaugeWith("jvm_buffer_pool_used_bytes", MetricLabel("pool", pool))(_.toDouble)

  /** Bytes capacity of a given JVM buffer pool. */
  private def bufferPoolCapacityBytes(pool: String): ZIOMetric.Gauge[Long] =
    ZIOMetric.setGaugeWith("jvm_buffer_pool_capacity_bytes", MetricLabel("pool", pool))(_.toDouble)

  /** Used buffers of a given JVM buffer pool. */
  private def bufferPoolUsedBuffers(pool: String): ZIOMetric.Gauge[Long] =
    ZIOMetric.setGaugeWith("jvm_buffer_pool_used_buffers", MetricLabel("pool", pool))(_.toDouble)

  private def reportBufferPoolMetrics(
    bufferPoolMXBeans: List[BufferPoolMXBean]
  ): ZIO[Any, Throwable, Unit] =
    ZIO.foreachParDiscard(bufferPoolMXBeans) { bufferPoolMXBean =>
      for {
        name <- Task(bufferPoolMXBean.getName)
        _    <- Task(bufferPoolMXBean.getMemoryUsed) @@ bufferPoolUsedBytes(name)
        _    <- Task(bufferPoolMXBean.getTotalCapacity) @@ bufferPoolCapacityBytes(name)
        _    <- Task(bufferPoolMXBean.getCount) @@ bufferPoolUsedBuffers(name)
      } yield ()
    }

  val collectMetrics: ZManaged[Has[Clock], Throwable, Unit] =
    ZManaged.acquireReleaseWith {
      for {
        bufferPoolMXBeans <-
          Task(fromJavaList(ManagementFactory.getPlatformMXBeans(classOf[BufferPoolMXBean])).toList)
        fiber <-
          reportBufferPoolMetrics(bufferPoolMXBeans)
            .repeat(collectionSchedule)
            .interruptible
            .forkDaemon
      } yield fiber
    }(_.interrupt).unit
}
