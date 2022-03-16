package zio.metrics.jvm

import zio.metrics.ZIOMetric
import zio._
import zio.stacktracer.TracingImplicits.disableAutoTrace

import java.lang.management.{ManagementFactory, PlatformManagedObject, RuntimeMXBean}
import java.lang.reflect.Method
import java.nio.charset.StandardCharsets
import scala.util.{Failure, Success, Try}

trait Standard extends JvmMetrics {
  import ZIOMetric.Gauge

  override type Feature = Standard
  override val featureTag = Tag[Standard]

  /** Total user and system CPU time spent in seconds. */
  private val cpuSecondsTotal: Gauge[Long] =
    ZIOMetric.gauge("process_cpu_seconds_total").contramap(_.toDouble / 1.0e09)

  /** Start time of the process since unix epoch in seconds. */
  private val processStartTime: Gauge[Long] =
    ZIOMetric.gauge("process_start_time_seconds").contramap(_.toDouble / 1000.0)

  /** Number of open file descriptors. */
  private val openFdCount: Gauge[Long] =
    ZIOMetric.gauge("process_open_fds").contramap(_.toDouble)

  /** Maximum number of open file descriptors. */
  private val maxFdCount: Gauge[Long] =
    ZIOMetric.gauge("process_max_fds").contramap(_.toDouble)

  /** Virtual memory size in bytes. */
  private val virtualMemorySize: Gauge[Double] =
    ZIOMetric.gauge("process_virtual_memory_bytes")

  /** Resident memory size in bytes. */
  private val residentMemorySize: Gauge[Double] =
    ZIOMetric.gauge("process_resident_memory_bytes")

  class MXReflection(getterName: String, obj: PlatformManagedObject) {
    private val cls: Class[_ <: PlatformManagedObject] = obj.getClass
    private val method: Option[Method]                 = findGetter(Try(cls.getMethod(getterName)))

    def isAvailable: Boolean = method.isDefined

    def unsafeGet(implicit trace: ZTraceElement): Task[Long] =
      method match {
        case Some(getter) => ZIO.attempt(getter.invoke(obj).asInstanceOf[Long])
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
  )(implicit trace: ZTraceElement): ZIO[Any, Throwable, Unit] =
    for {
      _ <- (getProcessCPUTime.unsafeGet @@ cpuSecondsTotal).when(getProcessCPUTime.isAvailable)
      _ <- processStartTime.set(runtimeMXBean.getStartTime)
      _ <- (getOpenFileDescriptorCount.unsafeGet @@ openFdCount).when(
             getOpenFileDescriptorCount.isAvailable
           )
      _ <- (getMaxFileDescriptorCount.unsafeGet @@ maxFdCount).when(
             getMaxFileDescriptorCount.isAvailable
           )
      _ <- collectMemoryMetricsLinux().when(isLinux)
    } yield ()

  private def collectMemoryMetricsLinux()(implicit trace: ZTraceElement): ZIO[Any, Throwable, Unit] =
    ZManaged.readFile("/proc/self/status").use { stream =>
      stream
        .readAll(8192)
        .catchAll {
          case None        => ZIO.succeed(Chunk.empty)
          case Some(error) => ZIO.fail(error)
        }
        .flatMap { bytes =>
          ZIO.attempt(new String(bytes.toArray, StandardCharsets.US_ASCII)).flatMap { raw =>
            ZIO.foreachDiscard(raw.split('\n')) { line =>
              if (line.startsWith("VmSize:")) {
                ZIO.attempt(line.split("\\s+")(1).toDouble * 1024.0) @@ virtualMemorySize
              } else if (line.startsWith("VmRSS:")) {
                ZIO.attempt(line.split("\\s+")(1).toDouble * 1024.0) @@ residentMemorySize
              } else {
                ZIO.unit
              }
            }
          }
        }
    }

  def collectMetrics(implicit trace: ZTraceElement): ZManaged[Clock with System, Throwable, Standard] =
    for {
      runtimeMXBean         <- ZIO.attempt(ManagementFactory.getRuntimeMXBean).toManaged
      operatingSystemMXBean <- ZIO.attempt(ManagementFactory.getOperatingSystemMXBean).toManaged
      getProcessCpuTime      = new MXReflection("getProcessCpuTime", operatingSystemMXBean)
      getOpenFileDescriptorCount =
        new MXReflection("getOpenFileDescriptorCount", operatingSystemMXBean)
      getMaxFileDescriptorCount =
        new MXReflection("getMaxFileDescriptorCount", operatingSystemMXBean)
      isLinux <- ZIO.attempt(operatingSystemMXBean.getName.indexOf("Linux") == 0).toManaged
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
    } yield this
}

object Standard extends Standard with JvmMetrics.DefaultSchedule {
  def withSchedule(schedule: Schedule[Any, Any, Unit]): Standard = new Standard {
    override protected def collectionSchedule(implicit trace: ZTraceElement): Schedule[Any, Any, Unit] = schedule
  }
}
