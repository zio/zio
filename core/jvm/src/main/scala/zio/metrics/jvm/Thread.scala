package zio.metrics.jvm

import zio.metrics._
import zio._
import zio.stacktracer.TracingImplicits.disableAutoTrace

import java.lang.management.{ManagementFactory, ThreadMXBean}

trait Thread extends JvmMetrics {
  import ZIOMetric.Gauge

  override type Feature = Thread
  override val featureTag: Tag[Thread] = Tag[Thread]

  /** Current thread count of a JVM */
  private val threadsCurrent: Gauge[Int] =
    ZIOMetric.gauge("jvm_threads_current").contramap(_.toDouble)

  /** Daemon thread count of a JVM */
  private val threadsDaemon: Gauge[Int] =
    ZIOMetric.gauge("jvm_threads_daemon").contramap(_.toDouble)

  /** Peak thread count of a JVM */
  private val threadsPeak: Gauge[Int] =
    ZIOMetric.gauge("jvm_threads_peak").contramap(_.toDouble)

  /** Started thread count of a JVM */
  private val threadsStartedTotal: Gauge[Long] =
    ZIOMetric
      .gauge("jvm_threads_started_total")
      .contramap(
        _.toDouble
      ) // NOTE: this is a counter in the prometheus hotspot library (but explicitly set to an actual value)

  /**
   * Cycles of JVM-threads that are in deadlock waiting to acquire object
   * monitors or ownable synchronizers
   */
  private val threadsDeadlocked: Gauge[Int] =
    ZIOMetric.gauge("jvm_threads_deadlocked").contramap(_.toDouble)

  /**
   * Cycles of JVM-threads that are in deadlock waiting to acquire object
   * monitors
   */
  private val threadsDeadlockedMonitor: Gauge[Int] =
    ZIOMetric.gauge("jvm_threads_deadlocked_monitor").contramap(_.toDouble)

  /** Current count of threads by state */
  private def threadsState(state: java.lang.Thread.State): Gauge[Long] =
    ZIOMetric.gauge("jvm_threads_state").tagged(MetricLabel("state", state.name())).contramap(_.toDouble)

  private def getThreadStateCounts(
    threadMXBean: ThreadMXBean
  )(implicit trace: ZTraceElement): Task[Map[java.lang.Thread.State, Long]] =
    for {
      allThreads <- ZIO.attempt(threadMXBean.getThreadInfo(threadMXBean.getAllThreadIds, 0))
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
      _ <- threadsCurrent.set(threadMXBean.getThreadCount)
      _ <- threadsDaemon.set(threadMXBean.getDaemonThreadCount)
      _ <- threadsPeak.set(threadMXBean.getPeakThreadCount)
      _ <- threadsStartedTotal.set(threadMXBean.getTotalStartedThreadCount)
      _ <- threadsDeadlocked.set(
             Option(threadMXBean.findDeadlockedThreads()).map(_.length).getOrElse(0)
           )
      _ <- threadsDeadlockedMonitor.set(
             Option(threadMXBean.findMonitorDeadlockedThreads()).map(_.length).getOrElse(0)
           )
      threadStateCounts <- getThreadStateCounts(threadMXBean)
      _ <- ZIO.foreachDiscard(threadStateCounts) { case (state, count) =>
             threadsState(state).set(count)
           }
    } yield ()

  override def collectMetrics(implicit trace: ZTraceElement): ZIO[Clock with System with Scope, Throwable, Thread] =
    for {
      threadMXBean <- ZIO.attempt(ManagementFactory.getThreadMXBean)
      _ <-
        reportThreadMetrics(threadMXBean).repeat(collectionSchedule).interruptible.forkScoped
    } yield this
}

object Thread extends Thread with JvmMetrics.DefaultSchedule {
  def withSchedule(schedule: Schedule[Any, Any, Unit]): Thread = new Thread {
    override protected def collectionSchedule(implicit trace: ZTraceElement): Schedule[Any, Any, Unit] = schedule
  }
}
