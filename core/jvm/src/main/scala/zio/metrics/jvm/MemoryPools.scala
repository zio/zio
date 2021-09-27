package zio.metrics.jvm

import com.github.ghik.silencer.silent

import zio.ZIOMetric.Gauge
import zio._

import java.lang.management.{ManagementFactory, MemoryMXBean, MemoryPoolMXBean, MemoryUsage}

import scala.collection.JavaConverters._

object MemoryPools extends JvmMetrics {

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

  private def reportMemoryUsage(usage: MemoryUsage, area: Area): ZIO[Any, Nothing, Unit] =
    for {
      _ <- UIO(usage.getUsed) @@ memoryBytesUsed(area)
      _ <- UIO(usage.getCommitted) @@ memoryBytesCommitted(area)
      _ <- UIO(usage.getMax) @@ memoryBytesMax(area)
      _ <- UIO(usage.getInit) @@ memoryBytesInit(area)
    } yield ()

  private def reportPoolUsage(usage: MemoryUsage, pool: String): ZIO[Any, Nothing, Unit] =
    for {
      _ <- UIO(usage.getUsed) @@ poolBytesUsed(pool)
      _ <- UIO(usage.getCommitted) @@ poolBytesCommitted(pool)
      _ <- UIO(usage.getMax) @@ poolBytesMax(pool)
      _ <- UIO(usage.getInit) @@ poolBytesInit(pool)
    } yield ()

  private def reportMemoryMetrics(
    memoryMXBean: MemoryMXBean,
    poolMXBeans: List[MemoryPoolMXBean]
  ): ZIO[Any, Throwable, Unit] =
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
  val collectMetrics: ZManaged[Has[Clock], Throwable, Unit] =
    for {
      memoryMXBean <- Task(ManagementFactory.getMemoryMXBean).toManaged
      poolMXBeans  <- Task(ManagementFactory.getMemoryPoolMXBeans.asScala.toList).toManaged
      _ <- reportMemoryMetrics(memoryMXBean, poolMXBeans)
             .repeat(collectionSchedule)
             .interruptible
             .forkManaged
    } yield ()
}
