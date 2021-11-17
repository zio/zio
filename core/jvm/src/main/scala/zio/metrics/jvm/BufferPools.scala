package zio.metrics.jvm

import com.github.ghik.silencer.silent

import zio._
import zio.stacktracer.TracingImplicits.disableAutoTrace

import java.lang.management.{BufferPoolMXBean, ManagementFactory}

import scala.collection.JavaConverters._

trait BufferPools extends JvmMetrics {
  override type Feature = BufferPools
  override val featureTag: Tag[BufferPools] = Tag[BufferPools]

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
  )(implicit trace: ZTraceElement): ZIO[Any, Throwable, Unit] =
    ZIO.foreachParDiscard(bufferPoolMXBeans) { bufferPoolMXBean =>
      for {
        name <- Task(bufferPoolMXBean.getName)
        _    <- Task(bufferPoolMXBean.getMemoryUsed) @@ bufferPoolUsedBytes(name)
        _    <- Task(bufferPoolMXBean.getTotalCapacity) @@ bufferPoolCapacityBytes(name)
        _    <- Task(bufferPoolMXBean.getCount) @@ bufferPoolUsedBuffers(name)
      } yield ()
    }

  @silent("JavaConverters")
  def collectMetrics(implicit trace: ZTraceElement): ZManaged[Clock, Throwable, BufferPools] =
    for {
      bufferPoolMXBeans <- Task(
                             ManagementFactory.getPlatformMXBeans(classOf[BufferPoolMXBean]).asScala.toList
                           ).toManaged
      _ <- reportBufferPoolMetrics(bufferPoolMXBeans)
             .repeat(collectionSchedule)
             .interruptible
             .forkManaged
    } yield this
}

object BufferPools extends BufferPools with JvmMetrics.DefaultSchedule {
  def withSchedule(schedule: Schedule[Any, Any, Unit]): BufferPools = new BufferPools {
    override protected def collectionSchedule(implicit trace: ZTraceElement): Schedule[Any, Any, Unit] = schedule
  }
}
