package zio.metrics.jvm

import com.github.ghik.silencer.silent

import zio._
import zio.metrics._
import zio.metrics.ZIOMetric.Gauge
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
      _ <- memoryBytesUsed(area).set(usage.getUsed)
      _ <- memoryBytesCommitted(area).set(usage.getCommitted)
      _ <- memoryBytesMax(area).set(usage.getMax)
      _ <- memoryBytesInit(area).set(usage.getInit)
    } yield ()

  private def reportPoolUsage(usage: MemoryUsage, pool: String)(implicit
    trace: ZTraceElement
  ): ZIO[Any, Nothing, Unit] =
    for {
      _ <- poolBytesUsed(pool).set(usage.getUsed)
      _ <- poolBytesCommitted(pool).set(usage.getCommitted)
      _ <- poolBytesMax(pool).set(usage.getMax)
      _ <- poolBytesInit(pool).set(usage.getInit)
    } yield ()

  private def reportMemoryMetrics(
    memoryMXBean: MemoryMXBean,
    poolMXBeans: List[MemoryPoolMXBean]
  )(implicit trace: ZTraceElement): ZIO[Any, Throwable, Unit] =
    for {
      heapUsage    <- ZIO.attempt(memoryMXBean.getHeapMemoryUsage)
      nonHeapUsage <- ZIO.attempt(memoryMXBean.getNonHeapMemoryUsage)
      _            <- reportMemoryUsage(heapUsage, Heap)
      _            <- reportMemoryUsage(nonHeapUsage, NonHeap)
      _ <- ZIO.foreachParDiscard(poolMXBeans) { pool =>
             for {
               name  <- ZIO.attempt(pool.getName)
               usage <- ZIO.attempt(pool.getUsage)
               _     <- reportPoolUsage(usage, name)
             } yield ()
           }
    } yield ()

  @silent("JavaConverters")
  def collectMetrics(implicit trace: ZTraceElement): ZIO[Clock with Scope, Throwable, MemoryPools] =
    for {
      memoryMXBean <- ZIO.attempt(ManagementFactory.getMemoryMXBean)
      poolMXBeans  <- ZIO.attempt(ManagementFactory.getMemoryPoolMXBeans.asScala.toList)
      _ <- reportMemoryMetrics(memoryMXBean, poolMXBeans)
             .repeat(collectionSchedule)
             .interruptible
             .forkScoped
    } yield this
}

object MemoryPools extends MemoryPools with JvmMetrics.DefaultSchedule {
  def withSchedule(schedule: Schedule[Any, Any, Unit]): MemoryPools = new MemoryPools {
    override protected def collectionSchedule(implicit trace: ZTraceElement): Schedule[Any, Any, Unit] = schedule
  }
}
