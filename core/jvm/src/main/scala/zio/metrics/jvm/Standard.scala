package zio.metrics.jvm

import zio.ZIOMetric.Gauge
import zio._

import java.lang.management.{ManagementFactory, PlatformManagedObject, RuntimeMXBean}
import java.lang.reflect.Method
import java.nio.charset.StandardCharsets
import scala.util.{Failure, Success, Try}

trait Standard

object Standard extends JvmMetrics {
  override type Feature = Standard
  override val featureTag: Tag[Standard] = Tag[Standard]

  /** Total user and system CPU time spent in seconds. */
  private val cpuSecondsTotal: Gauge[Long] =
    ZIOMetric.setGaugeWith("process_cpu_seconds_total")(_.toDouble / 1.0e09)

  /** Start time of the process since unix epoch in seconds. */
  private val processStartTime: Gauge[Long] =
    ZIOMetric.setGaugeWith("process_start_time_seconds")(_.toDouble / 1000.0)

  /** Number of open file descriptors. */
  private val openFdCount: Gauge[Long] =
    ZIOMetric.setGaugeWith("process_open_fds")(_.toDouble)

  /** Maximum number of open file descriptors. */
  private val maxFdCount: Gauge[Long] =
    ZIOMetric.setGaugeWith("process_max_fds")(_.toDouble)

  /** Virtual memory size in bytes. */
  private val virtualMemorySize: Gauge[Double] =
    ZIOMetric.setGauge("process_virtual_memory_bytes")

  /** Resident memory size in bytes. */
  private val residentMemorySize: Gauge[Double] =
    ZIOMetric.setGauge("process_resident_memory_bytes")

  class MXReflection(getterName: String, obj: PlatformManagedObject) {
    private val cls: Class[_ <: PlatformManagedObject] = obj.getClass
    private val method: Option[Method]                 = findGetter(Try(cls.getMethod(getterName)))

    def isAvailable: Boolean = method.isDefined

    def unsafeGet: Task[Long] =
      method match {
        case Some(getter) => Task(getter.invoke(obj).asInstanceOf[Long])
        case None =>
          ZIO.fail(new IllegalStateException(s"MXReflection#get called on unavailable metri"))
      }

    private def findGetter(getter: Try[Method]): Option[Method] =
      getter match {
        case Failure(_) =>
          None
        case Success(method) =>
          try {
            val _ = method.invoke(obj).asInstanceOf[Long]
            Some(method)
          } catch {
            case _: IllegalAccessException =>
              var result: Option[Method] = None
              var idx                    = 0
              val ifaces                 = method.getDeclaringClass.getInterfaces

              while (idx < ifaces.length && result.isEmpty) {
                val iface = ifaces(idx)
                result = findGetter(Try(iface.getMethod(getterName)))
                idx = idx + 1
              }

              result
          }
      }
  }

  private def reportStandardMetrics(
    runtimeMXBean: RuntimeMXBean,
    getProcessCPUTime: MXReflection,
    getOpenFileDescriptorCount: MXReflection,
    getMaxFileDescriptorCount: MXReflection,
    isLinux: Boolean
  ): ZIO[Any, Throwable, Unit] =
    for {
      _ <- (getProcessCPUTime.unsafeGet @@ cpuSecondsTotal).when(getProcessCPUTime.isAvailable)
      _ <- Task(runtimeMXBean.getStartTime) @@ processStartTime
      _ <- (getOpenFileDescriptorCount.unsafeGet @@ openFdCount).when(
             getOpenFileDescriptorCount.isAvailable
           )
      _ <- (getMaxFileDescriptorCount.unsafeGet @@ maxFdCount).when(
             getMaxFileDescriptorCount.isAvailable
           )
      _ <- collectMemoryMetricsLinux().when(isLinux)
    } yield ()

  private def collectMemoryMetricsLinux(): ZIO[Any, Throwable, Unit] =
    ZManaged.readFile("/proc/self/status").use { stream =>
      stream
        .readAll(8192)
        .catchAll {
          case None        => ZIO.succeed(Chunk.empty)
          case Some(error) => ZIO.fail(error)
        }
        .flatMap { bytes =>
          Task(new String(bytes.toArray, StandardCharsets.US_ASCII)).flatMap { raw =>
            ZIO.foreachDiscard(raw.split('\n')) { line =>
              if (line.startsWith("VmSize:")) {
                Task(line.split("\\s+")(1).toDouble * 1024.0) @@ virtualMemorySize
              } else if (line.startsWith("VmRSS:")) {
                Task(line.split("\\s+")(1).toDouble * 1024.0) @@ residentMemorySize
              } else {
                ZIO.unit
              }
            }
          }
        }
    }

  override val collectMetrics: ZManaged[Has[Clock] with Has[System], Throwable, Standard] =
    for {
      runtimeMXBean         <- Task(ManagementFactory.getRuntimeMXBean).toManaged
      operatingSystemMXBean <- Task(ManagementFactory.getOperatingSystemMXBean).toManaged
      getProcessCpuTime      = new MXReflection("getProcessCpuTime", operatingSystemMXBean)
      getOpenFileDescriptorCount =
        new MXReflection("getOpenFileDescriptorCount", operatingSystemMXBean)
      getMaxFileDescriptorCount =
        new MXReflection("getMaxFileDescriptorCount", operatingSystemMXBean)
      isLinux <- Task(operatingSystemMXBean.getName.indexOf("Linux") == 0).toManaged
      _ <-
        reportStandardMetrics(
          runtimeMXBean,
          getProcessCpuTime,
          getOpenFileDescriptorCount,
          getMaxFileDescriptorCount,
          isLinux
        )
          .repeat(collectionSchedule)
          .interruptible
          .forkManaged
    } yield new Standard {}
}
