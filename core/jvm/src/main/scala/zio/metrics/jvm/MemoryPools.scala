package zio.metrics.jvm

import com.github.ghik.silencer.silent

import zio.metrics.ZIOMetric
import zio.metrics.ZIOMetric.Gauge
import zio._
import zio.stacktracer.TracingImplicits.disableAutoTrace

import java.lang.management.{ManagementFactory, MemoryMXBean, MemoryPoolMXBean, MemoryUsage}

import scala.collection.JavaConverters._

trait MemoryPools extends JvmMetrics {
  override type Feature = MemoryPools
  override val featureTag = Tag[MemoryPools]

  sealed private trait Area { val label: String }
  private case object Heap    extends Area { override val label: String = "heap"    }
  private case object NonHeap extends Area { override val label: String = "nonheap" }

  /** Used bytes of a given JVM memory area. */
  private def memoryBytesUsed(area: Area): Gauge[Long] =
    ZIOMetric.gauge("jvm_memory_bytes_used").tagged(MetricLabel("area", area.label)).contramap(_.toDouble)

  /** Committed (bytes) of a given JVM memory area. */
  private def memoryBytesCommitted(area: Area): Gauge[Long] =
    ZIOMetric.gauge("jvm_memory_bytes_committed").tagged(MetricLabel("area", area.label)).contramap(_.toDouble)

  /** Max (bytes) of a given JVM memory area. */
  private def memoryBytesMax(area: Area): Gauge[Long] =
    ZIOMetric.gauge("jvm_memory_bytes_max").tagged(MetricLabel("area", area.label)).contramap(_.toDouble)

  /** Initial bytes of a given JVM memory area. */
  private def memoryBytesInit(area: Area): Gauge[Long] =
    ZIOMetric.gauge("jvm_memory_bytes_init").tagged(MetricLabel("area", area.label)).contramap(_.toDouble)

  /** Used bytes of a given JVM memory pool. */
  private def poolBytesUsed(pool: String): Gauge[Long] =
    ZIOMetric.gauge("jvm_memory_pool_bytes_used").tagged(MetricLabel("pool", pool)).contramap(_.toDouble)

  /** Committed bytes of a given JVM memory pool. */
  private def poolBytesCommitted(pool: String): Gauge[Long] =
    ZIOMetric.gauge("jvm_memory_pool_bytes_committed").tagged(MetricLabel("pool", pool)).contramap(_.toDouble)

  /** Max bytes of a given JVM memory pool. */
  private def poolBytesMax(pool: String): Gauge[Long] =
    ZIOMetric.gauge("jvm_memory_pool_bytes_max").tagged(MetricLabel("pool", pool)).contramap(_.toDouble)

  /** Initial bytes of a given JVM memory pool. */
  private def poolBytesInit(pool: String): Gauge[Long] =
    ZIOMetric.gauge("jvm_memory_pool_bytes_init").tagged(MetricLabel("pool", pool)).contramap(_.toDouble)

  private def reportMemoryUsage(usage: MemoryUsage, area: Area)(implicit
    trace: ZTraceElement
  ): ZIO[Any, Nothing, Unit] =
    for {
      _ <- UIO(usage.getUsed) @@ memoryBytesUsed(area)
      _ <- UIO(usage.getCommitted) @@ memoryBytesCommitted(area)
      _ <- UIO(usage.getMax) @@ memoryBytesMax(area)
      _ <- UIO(usage.getInit) @@ memoryBytesInit(area)
    } yield ()

  private def reportPoolUsage(usage: MemoryUsage, pool: String)(implicit
    trace: ZTraceElement
  ): ZIO[Any, Nothing, Unit] =
    for {
      _ <- UIO(usage.getUsed) @@ poolBytesUsed(pool)
      _ <- UIO(usage.getCommitted) @@ poolBytesCommitted(pool)
      _ <- UIO(usage.getMax) @@ poolBytesMax(pool)
      _ <- UIO(usage.getInit) @@ poolBytesInit(pool)
    } yield ()

  private def reportMemoryMetrics(
    memoryMXBean: MemoryMXBean,
    poolMXBeans: List[MemoryPoolMXBean]
  )(implicit trace: ZTraceElement): ZIO[Any, Throwable, Unit] =
    for {
      heapUsage    <- Task(memoryMXBean.getHeapMemoryUsage)
      nonHeapUsage <- Task(memoryMXBean.getNonHeapMemoryUsage)
      _            <- reportMemoryUsage(heapUsage, Heap)
      _            <- reportMemoryUsage(nonHeapUsage, NonHeap)
      _ <- ZIO.foreachParDiscard(poolMXBeans) { pool =>
             for {
               name  <- Task(pool.getName)
               usage <- Task(pool.getUsage)
               _     <- reportPoolUsage(usage, name)
             } yield ()
           }
    } yield ()

  @silent("JavaConverters")
  def collectMetrics(implicit trace: ZTraceElement): ZManaged[Clock, Throwable, MemoryPools] =
    for {
      memoryMXBean <- Task(ManagementFactory.getMemoryMXBean).toManaged
      poolMXBeans  <- Task(ManagementFactory.getMemoryPoolMXBeans.asScala.toList).toManaged
      _ <- reportMemoryMetrics(memoryMXBean, poolMXBeans)
             .repeat(collectionSchedule)
             .interruptible
             .forkManaged
    } yield this
}

object MemoryPools extends MemoryPools with JvmMetrics.DefaultSchedule {
  def withSchedule(schedule: Schedule[Any, Any, Unit]): MemoryPools = new MemoryPools {
    override protected def collectionSchedule(implicit trace: ZTraceElement): Schedule[Any, Any, Unit] = schedule
  }
}
