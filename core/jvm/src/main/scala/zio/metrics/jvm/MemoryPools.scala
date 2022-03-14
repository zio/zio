package zio.metrics.jvm

import com.github.ghik.silencer.silent

import zio.ZIOMetric.Gauge
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
    ZIOMetric.setGaugeWith("jvm_memory_bytes_used", MetricLabel("area", area.label))(_.toDouble)

  /** Committed (bytes) of a given JVM memory area. */
  private def memoryBytesCommitted(area: Area): Gauge[Long] =
    ZIOMetric.setGaugeWith("jvm_memory_bytes_committed", MetricLabel("area", area.label))(_.toDouble)

  /** Max (bytes) of a given JVM memory area. */
  private def memoryBytesMax(area: Area): Gauge[Long] =
    ZIOMetric.setGaugeWith("jvm_memory_bytes_max", MetricLabel("area", area.label))(_.toDouble)

  /** Initial bytes of a given JVM memory area. */
  private def memoryBytesInit(area: Area): Gauge[Long] =
    ZIOMetric.setGaugeWith("jvm_memory_bytes_init", MetricLabel("area", area.label))(_.toDouble)

  /** Used bytes of a given JVM memory pool. */
  private def poolBytesUsed(pool: String): Gauge[Long] =
    ZIOMetric.setGaugeWith("jvm_memory_pool_bytes_used", MetricLabel("pool", pool))(_.toDouble)

  /** Committed bytes of a given JVM memory pool. */
  private def poolBytesCommitted(pool: String): Gauge[Long] =
    ZIOMetric.setGaugeWith("jvm_memory_pool_bytes_committed", MetricLabel("pool", pool))(_.toDouble)

  /** Max bytes of a given JVM memory pool. */
  private def poolBytesMax(pool: String): Gauge[Long] =
    ZIOMetric.setGaugeWith("jvm_memory_pool_bytes_max", MetricLabel("pool", pool))(_.toDouble)

  /** Initial bytes of a given JVM memory pool. */
  private def poolBytesInit(pool: String): Gauge[Long] =
    ZIOMetric.setGaugeWith("jvm_memory_pool_bytes_init", MetricLabel("pool", pool))(_.toDouble)

  private def reportMemoryUsage(usage: MemoryUsage, area: Area)(implicit
    trace: ZTraceElement
  ): ZIO[Any, Nothing, Unit] =
    for {
      _ <- ZIO.succeed(usage.getUsed) @@ memoryBytesUsed(area)
      _ <- ZIO.succeed(usage.getCommitted) @@ memoryBytesCommitted(area)
      _ <- ZIO.succeed(usage.getMax) @@ memoryBytesMax(area)
      _ <- ZIO.succeed(usage.getInit) @@ memoryBytesInit(area)
    } yield ()

  private def reportPoolUsage(usage: MemoryUsage, pool: String)(implicit
    trace: ZTraceElement
  ): ZIO[Any, Nothing, Unit] =
    for {
      _ <- ZIO.succeed(usage.getUsed) @@ poolBytesUsed(pool)
      _ <- ZIO.succeed(usage.getCommitted) @@ poolBytesCommitted(pool)
      _ <- ZIO.succeed(usage.getMax) @@ poolBytesMax(pool)
      _ <- ZIO.succeed(usage.getInit) @@ poolBytesInit(pool)
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
  def collectMetrics(implicit trace: ZTraceElement): ZManaged[Clock, Throwable, MemoryPools] =
    for {
      memoryMXBean <- ZIO.attempt(ManagementFactory.getMemoryMXBean).toManaged
      poolMXBeans  <- ZIO.attempt(ManagementFactory.getMemoryPoolMXBeans.asScala.toList).toManaged
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
