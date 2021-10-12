package zio.metrics.jvm

import zio.ZIOMetric.Gauge
import zio._
import zio.stacktracer.TracingImplicits.disableAutoTrace

import java.lang.management.{ManagementFactory, ThreadMXBean}

trait Thread extends JvmMetrics {
  override type Feature = Thread
  override val featureTag: Tag[Thread] = Tag[Thread]

  /** Current thread count of a JVM */
  private val threadsCurrent: Gauge[Int] =
    ZIOMetric.setGaugeWith("jvm_threads_current")(_.toDouble)

  /** Daemon thread count of a JVM */
  private val threadsDaemon: Gauge[Int] =
    ZIOMetric.setGaugeWith("jvm_threads_daemon")(_.toDouble)

  /** Peak thread count of a JVM */
  private val threadsPeak: Gauge[Int] =
    ZIOMetric.setGaugeWith("jvm_threads_peak")(_.toDouble)

  /** Started thread count of a JVM */
  private val threadsStartedTotal: Gauge[Long] =
    ZIOMetric.setGaugeWith("jvm_threads_started_total")(
      _.toDouble
    ) // NOTE: this is a counter in the prometheus hotspot library (but explicitly set to an actual value)

  /** Cycles of JVM-threads that are in deadlock waiting to acquire object monitors or ownable synchronizers */
  private val threadsDeadlocked: Gauge[Int] =
    ZIOMetric.setGaugeWith("jvm_threads_deadlocked")(_.toDouble)

  /** Cycles of JVM-threads that are in deadlock waiting to acquire object monitors */
  private val threadsDeadlockedMonitor: Gauge[Int] =
    ZIOMetric.setGaugeWith("jvm_threads_deadlocked_monitor")(_.toDouble)

  /** Current count of threads by state */
  private def threadsState(state: java.lang.Thread.State): Gauge[Long] =
    ZIOMetric.setGaugeWith("jvm_threads_state", MetricLabel("state", state.name()))(_.toDouble)

  private def getThreadStateCounts(
    threadMXBean: ThreadMXBean
  )(implicit trace: ZTraceElement): Task[Map[java.lang.Thread.State, Long]] =
    for {
      allThreads <- Task(threadMXBean.getThreadInfo(threadMXBean.getAllThreadIds, 0))
      initial     = java.lang.Thread.State.values().map(_ -> 0L).toMap
      result = allThreads.foldLeft(initial) { (result, thread) =>
                 if (thread != null) {
                   result.updated(thread.getThreadState, result(thread.getThreadState) + 1)
                 } else result
               }
    } yield result

  private def reportThreadMetrics(
    threadMXBean: ThreadMXBean
  )(implicit trace: ZTraceElement): ZIO[Any, Throwable, Unit] =
    for {
      _ <- Task(threadMXBean.getThreadCount) @@ threadsCurrent
      _ <- Task(threadMXBean.getDaemonThreadCount) @@ threadsDaemon
      _ <- Task(threadMXBean.getPeakThreadCount) @@ threadsPeak
      _ <- Task(threadMXBean.getTotalStartedThreadCount) @@ threadsStartedTotal
      _ <- Task(
             Option(threadMXBean.findDeadlockedThreads()).map(_.length).getOrElse(0)
           ) @@ threadsDeadlocked
      _ <- Task(
             Option(threadMXBean.findMonitorDeadlockedThreads()).map(_.length).getOrElse(0)
           ) @@ threadsDeadlockedMonitor
      threadStateCounts <- getThreadStateCounts(threadMXBean)
      _ <- ZIO.foreachDiscard(threadStateCounts) { case (state, count) =>
             UIO(count) @@ threadsState(state)
           }
    } yield ()

  override def collectMetrics(implicit trace: ZTraceElement): ZManaged[Has[Clock] with Has[System], Throwable, Thread] =
    for {
      threadMXBean <- Task(ManagementFactory.getThreadMXBean).toManaged
      _ <-
        reportThreadMetrics(threadMXBean).repeat(collectionSchedule).interruptible.forkManaged
    } yield this
}

object Thread extends Thread with JvmMetrics.DefaultSchedule {
  def withSchedule(schedule: Schedule[Any, Any, Unit]): Thread = new Thread {
    override protected def collectionSchedule(implicit trace: ZTraceElement): Schedule[Any, Any, Unit] = schedule
  }
}
